package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T088 — the Compensation Register's recording half. FR-ORC-016, EC-022.
 *
 * <p>Against the real store, because the property being proved is one the store owns: {@code
 * compensation_once_per_effect} is what makes "at most one correction per effect" true even when a later retry
 * succeeds. Application-side guarding would hold until two callers raced, which is precisely the situation a
 * recovery path runs in.
 */
@DisplayName("T088 — compensations are recorded, and recorded at most once per effect")
class CompensationRegisterIT extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private CompensationRegister register;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        register = new CompensationRegister(connections, Clock.fixed(NOW, ZoneOffset.UTC));
        runId = new JdbcRunStore(connections, Clock.fixed(NOW, ZoneOffset.UTC))
                .createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
    }

    @Test
    @DisplayName("a recorded compensation carries its effect, its action and its reason")
    void recordsWhatWasDone() throws Exception {
        register.recordApplied(runId, "S7.1", "branch:run/aa/S7.1", RecoveryKind.ROLLBACK,
                "rollback — reset or delete the branch", "S7 failed permanently after the commit landed");

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT node_key, effect_key, compensating_action, reason FROM compensation_record "
                             + "WHERE run_id = ?")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("S7.1", rs.getString("node_key"));
                assertEquals("branch:run/aa/S7.1", rs.getString("effect_key"));
                assertTrue(rs.getString("compensating_action").startsWith("ROLLBACK"),
                        "the kind must be readable from the row: a reviewer has to be able to tell a "
                                + "rollback from a compensation without inferring it from the action's "
                                + "wording. Got: " + rs.getString("compensating_action"));
                assertTrue(rs.getString("reason").contains("permanently"));
                assertEquals(1, countRecords(runId), "exactly one row");
            }
        }
    }

    @Test
    @DisplayName("EC-022: a second compensation for the same effect is refused BY THE STORE")
    void atMostOnePerEffect() {
        register.recordApplied(runId, "S7.1", "branch:run/aa/S7.1", RecoveryKind.ROLLBACK,
                "rollback — reset the branch", "first failure");

        // The case EC-022 names: a retry later succeeds, and something tries to compensate again. The
        // constraint decides, not a read-then-check in the caller — which is a race in the code that exists
        // to keep a recovery path safe.
        CompensationAlreadyAppliedException refused = assertThrows(
                CompensationAlreadyAppliedException.class,
                () -> register.recordApplied(runId, "S7.1", "branch:run/aa/S7.1", RecoveryKind.ROLLBACK,
                        "rollback — reset the branch", "second attempt after a later retry succeeded"));

        assertTrue(refused.getMessage().contains("branch:run/aa/S7.1"), refused.getMessage());
        assertEquals(1, countRecords(runId), "still one row; the second attempt left nothing behind");
    }

    @Test
    @DisplayName("the same effect key in a DIFFERENT run is a different effect")
    void scopedPerRun() {
        UUID other = new JdbcRunStore(connections, Clock.fixed(NOW, ZoneOffset.UTC))
                .createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());

        register.recordApplied(runId, "S7.1", "working-tree:docs/x.md", RecoveryKind.ROLLBACK,
                "rollback — discard", "r1");
        register.recordApplied(other, "S7.1", "working-tree:docs/x.md", RecoveryKind.ROLLBACK,
                "rollback — discard", "r2");

        assertEquals(1, countRecords(runId), "the constraint is (run_id, effect_key)");
        assertEquals(1, countRecords(other), "so the same key in another run is another effect");
    }

    @Test
    @DisplayName("a compensation recorded as ROLLBACK is distinguishable from one recorded as COMPENSATION")
    void kindsAreDistinguishableInHistory() throws Exception {
        // T089/T090's shared guard: compensation MUST NOT be described as rollback or vice versa, and the run
        // history must let a reviewer tell which occurred. That is only true if the label is in the row.
        register.recordApplied(runId, "S7.1", "branch:b", RecoveryKind.ROLLBACK, "reset", "r");
        register.recordApplied(runId, "S11", "gate-decision:41", RecoveryKind.COMPENSATION,
                "write a superseding record", "approval voided by replan");

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT effect_key, compensating_action FROM compensation_record "
                             + "WHERE run_id = ? ORDER BY effect_key")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("branch:b", rs.getString("effect_key"));
                assertTrue(rs.getString("compensating_action").startsWith("ROLLBACK:"));
                assertTrue(rs.next());
                assertEquals("gate-decision:41", rs.getString("effect_key"));
                assertTrue(rs.getString("compensating_action").startsWith("COMPENSATION:"));
            }
        }
    }

    @Test
    @DisplayName("NOTHING_TO_COMPENSATE is still recorded — an absent row is not the same as a decision")
    void nothingToCompensateIsStillRecorded() {
        // Recording it looks redundant until recovery is audited: without a row, "no correction was needed"
        // and "nobody looked" are the same absence. With one, the run says which.
        register.recordApplied(runId, "S5", "ai-invocation:S5/1", RecoveryKind.NOTHING_TO_COMPENSATE,
                "nothing to compensate — the call cannot be un-called and changed no external state",
                "S5 failed permanently");

        assertEquals(1, countRecords(runId));
    }

    @Test
    @DisplayName("NONE_KNOWN is refused as a recorded compensation — nothing was applied")
    void noneKnownIsNotACompensation() {
        // The register must not be able to claim it corrected something it does not know how to correct.
        // The caller's answer is suspension (T091), and a row here would read as a correction that happened.
        assertThrows(IllegalArgumentException.class,
                () -> register.recordApplied(runId, "S7", "mystery:1", RecoveryKind.NONE_KNOWN,
                        "unknown", "no idea"));
        assertEquals(0, countRecords(runId));
    }

    /**
     * Scoped to one run, deliberately.
     *
     * <p>The container is shared across the whole integration tier and {@link MigrationSupport} does not
     * clean, so rows from earlier tests are still there. An unscoped count would make each test's expectation
     * depend on how many ran before it — passing in isolation, failing in the suite, and blamed on whatever
     * changed last.
     */
    private int countRecords(UUID run) {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM compensation_record WHERE run_id = ?")) {
            ps.setObject(1, run);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to count compensation records", e);
        }
    }
}
