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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T090 — compensation of irreversible effects. FR-ORC-016, KE-16, EC-022.
 *
 * <p>Three compensating actions, each against the real store, because each one's correctness <em>is</em> what it
 * leaves in the tables: a superseding gate record, an appended audit correction, and a link set to expired. None
 * of them deletes anything, and that is the property under test as much as the correction itself.
 */
@DisplayName("T090 — corrections are appended, never applied in place, and never twice")
class CompensationHandlerIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private CompensationRegister register;
    private CompensationHandler handler;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        register = new CompensationRegister(connections, clock);
        handler = new CompensationHandler(connections, clock, register);
        runId = new JdbcRunStore(connections, clock)
                .createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
    }

    // ==============================================================================================
    // action 1 — a superseding gate record
    // ==============================================================================================

    @Test
    @DisplayName("a voided approval becomes a SUPERSEDING record; the original is untouched")
    void gateDecisionIsSuperseded() throws Exception {
        long original = insertGateDecision("S6", "APPROVED", "design approved");

        RecoveryOutcome outcome = handler.compensate(runId, "S6", "gate-decision:" + original,
                "the design was invalidated by a replan");

        assertEquals(RecoveryKind.COMPENSATION, outcome.kind());

        // EC-020: the prior decision is superseded BY REFERENCE, never deleted or edited. Both rows must be
        // there afterwards, and the original must still read APPROVED — a reviewer has to be able to see that
        // an approval was given and then voided, which is a different history from one that was never given.
        assertEquals(2, countGateDecisions(), "the original plus its superseding record");
        assertEquals("APPROVED", outcomeOf(original), "the original is not rewritten");

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT outcome, reason, actor_type FROM gate_decision "
                             + "WHERE run_id = ? AND gate_decision_id <> ? ")) {
            ps.setObject(1, runId);
            ps.setLong(2, original);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("REJECTED", rs.getString("outcome"),
                        "a voided approval is not itself an approval");
                assertTrue(rs.getString("reason").contains(String.valueOf(original)),
                        "the superseding record must name what it supersedes, or the reference is a claim "
                                + "nobody can follow: " + rs.getString("reason"));
                assertEquals("human", rs.getString("actor_type"),
                        "the store admits only 'human' here (CR-021); a system-authored void would have to "
                                + "lie about its actor to be written at all");
            }
        }
    }

    // ==============================================================================================
    // action 2 — an appended audit correction
    // ==============================================================================================

    @Test
    @DisplayName("an audit record is corrected by APPENDING, because the grants forbid anything else")
    void auditRecordIsCorrectedByAppending() throws Exception {
        long original = insertAuditRecord("a claim that turned out wrong");

        handler.compensate(runId, "S10", "audit:" + original, "the policy verdict was re-evaluated");

        assertEquals(2, countAuditRecords(), "the original plus the correction");
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT action, result, reason FROM audit_record "
                             + "WHERE run_id = ? AND audit_record_id <> ? ")) {
            ps.setObject(1, runId);
            ps.setLong(2, original);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("COMPENSATION", rs.getString("action"));
                assertTrue(rs.getString("reason").contains(String.valueOf(original)),
                        rs.getString("reason"));
            }
        }
    }

    // ==============================================================================================
    // action 3 — a link set to expired
    // ==============================================================================================

    @Test
    @DisplayName("an unwanted short link is set to EXPIRED, never deleted")
    void shortLinkIsExpiredNotDeleted() throws Exception {
        String code = insertShortLink();

        handler.compensate(runId, "S7", "short-link:" + code, "the link was created by a failed attempt");

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT state, expires_at FROM short_link WHERE short_code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(),
                        "the row must still exist. EX-003 excludes deletion: a deleted code leaves dangling "
                                + "history and can be re-issued to someone else, which is a redirect hijack");
                // The STATE moves, not the timestamp. V1's short_link_expiry_after_creation enforces
                // expires_at > created_at, so expiry cannot be backdated to now — the schema refuses to let a
                // link claim it expired before it was made, which is the retroactive rewrite this whole
                // register exists to avoid. So "set to expired" is a state change, and the original expiry
                // stays as the record of what was promised.
                assertEquals("EXPIRED", rs.getString("state"));
                assertNotNull(rs.getTimestamp("expires_at"), "the original expiry is left as it was");
            }
        }
    }

    // ==============================================================================================
    // EC-022
    // ==============================================================================================

    @Test
    @DisplayName("EC-022: a second correction for the same effect is refused, even after a retry succeeds")
    void atMostOneCorrectionPerEffect() throws Exception {
        long original = insertAuditRecord("first claim");
        handler.compensate(runId, "S10", "audit:" + original, "corrected once");

        // The exact case EC-022 names. A later retry succeeding does not entitle anybody to compensate again;
        // the store's compensation_once_per_effect decides, so two racing callers cannot both proceed.
        assertThrows(CompensationAlreadyAppliedException.class,
                () -> handler.compensate(runId, "S10", "audit:" + original,
                        "a later retry succeeded, so somebody tried to correct it again"));

        assertEquals(2, countAuditRecords(),
                "still the original plus ONE correction; the refused attempt appended nothing");
    }

    @Test
    @DisplayName("an erasable effect is REFUSED here — that is rollback's job, and the labels must not blur")
    void refusesErasableEffects() {
        // The mirror of T089's refusal. Compensating a discardable working-tree change would append a
        // correction for something that should simply have been discarded, and would record COMPENSATION in a
        // history where ROLLBACK was the truth.
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> handler.compensate(runId, "S7", "working-tree:docs/x.md", "should have been discarded"));
        assertTrue(refused.getMessage().contains("ROLLBACK"), refused.getMessage());
    }

    @Test
    @DisplayName("NOTHING_TO_COMPENSATE is recorded and touches nothing")
    void nothingToCompensateIsRecordedWithoutActing() {
        RecoveryOutcome outcome = handler.compensate(runId, "S5", "ai-invocation:S5/1",
                "S5 failed permanently");

        assertEquals(RecoveryKind.NOTHING_TO_COMPENSATE, outcome.kind());
        assertEquals(0, countAuditRecords(),
                "an AI call cannot be un-called and changed no external state, so there is nothing to append "
                        + "as a correction — but the decision itself is still recorded in compensation_record");
        assertEquals(1, countCompensationRecords(),
                "without a row, 'no correction was needed' and 'nobody looked' are the same absence");
    }

    @Test
    @DisplayName("an unclassified effect is compensated, not refused — the default is the safe one")
    void unclassifiedIsCompensated() {
        RecoveryOutcome outcome = handler.compensate(runId, "S7", "something-new:1", "unknown effect");

        assertEquals(RecoveryKind.COMPENSATION, outcome.kind(),
                "FR-ORC-016 rule 4: any effect not matching a register row is compensate-only");
        assertEquals(1, countCompensationRecords());
    }

    // ==============================================================================================

    private long insertGateDecision(String gateId, String outcome, String reason) throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO gate_decision (run_id, gate_id, outcome, actor_type, actor_name, reason, "
                             + "decided_at, repository_record_path) "
                             + "VALUES (?, ?, ?, 'human', 'the owner', ?, ?, "
                             + "'docs/governance/gate-decisions/gate-x.md') "
                             + "RETURNING gate_decision_id")) {
            ps.setObject(1, runId);
            ps.setString(2, gateId);
            ps.setString(3, outcome);
            ps.setString(4, reason);
            ps.setTimestamp(5, Timestamp.from(T0));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertAuditRecord(String reason) throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO audit_record (actor_type, action, occurred_at, affected_artifact, result, "
                             + "reason, run_id, correlation_id) VALUES ('system', 'CLAIM', ?, 'x', 'OK', ?, ?, "
                             + "(SELECT correlation_id FROM workflow_run WHERE run_id = ?)) "
                             + "RETURNING audit_record_id")) {
            ps.setTimestamp(1, Timestamp.from(T0));
            ps.setString(2, reason);
            ps.setObject(3, runId);
            ps.setObject(4, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** A creator and a link, so the expiry compensation has something real to act on. */
    private String insertShortLink() throws Exception {
        UUID creatorId = UUID.randomUUID();
        String code = "Cmp" + Integer.toHexString(creatorId.hashCode()).substring(0, 4);
        try (Connection c = connections.get()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO creator (creator_id, name, api_key_hash, created_at, key_expires_at) "
                            + "VALUES (?, 'compensation-test', ?, ?, ?)")) {
                ps.setObject(1, creatorId);
                // A placeholder digest, not a credential — 64 hex characters derived from the id so it is
                // unique per creator. Nothing authenticates against it, and the column is V1's superseded
                // shape that V2 replaced with creator_credential.
                ps.setString(2, String.format("%064x",
                        java.math.BigInteger.valueOf(Math.abs((long) creatorId.hashCode()))));
                ps.setTimestamp(3, Timestamp.from(T0));
                ps.setTimestamp(4, Timestamp.from(T0.plusSeconds(86_400)));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO short_link (short_code, destination, creator_id, created_at, expires_at, "
                            + "state) VALUES (?, 'https://example.com/x', ?, ?, ?, 'ACTIVE')")) {
                ps.setString(1, code);
                ps.setObject(2, creatorId);
                ps.setTimestamp(3, Timestamp.from(T0));
                ps.setTimestamp(4, Timestamp.from(T0.plusSeconds(604_800)));
                ps.executeUpdate();
            }
        }
        return code;
    }

    private int countGateDecisions() {
        return countScoped("SELECT COUNT(*) FROM gate_decision WHERE run_id = ?");
    }

    private int countAuditRecords() {
        return countScoped("SELECT COUNT(*) FROM audit_record WHERE run_id = ?");
    }

    private int countCompensationRecords() {
        return countScoped("SELECT COUNT(*) FROM compensation_record WHERE run_id = ?");
    }

    /** Always scoped to this run: the container is shared and nothing cleans between tests. */
    private int countScoped(String sql) {
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("count failed", e);
        }
    }

    private String outcomeOf(long gateDecisionId) throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT outcome FROM gate_decision WHERE gate_decision_id = ?")) {
            ps.setLong(1, gateDecisionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
