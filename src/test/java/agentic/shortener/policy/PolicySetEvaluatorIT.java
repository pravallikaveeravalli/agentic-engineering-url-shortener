package agentic.shortener.policy;

import agentic.shortener.orchestration.executor.deterministic.PolicyVerdict;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T100 — PolicySetEvaluator against the real repository and a real store. FR-ORC-022.
 *
 * <p>Runs all twelve checks for real: {@code POL-SEC-001} against the real {@code SchemeAllowList},
 * {@code POL-SEC-002} scanning this repository's real {@code src/}, {@code POL-AUD-001} against a real
 * {@code audit_record} table, {@code POL-TRC-001} via the real {@code TraceabilityReporter} (T123/T124)
 * over the real {@code spec.md}/{@code tasks.md}. Not a fixture — this is the same discipline {@code
 * ZeroOrphanTest} (T124) already established for exactly this reason.
 */
@DisplayName("T100 — PolicySetEvaluator: twelve real checks against the real repository and store")
class PolicySetEvaluatorIT extends PostgresIntegrationTest {

    private ConnectionSource connections;
    private PolicySetEvaluator evaluator;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC);
        evaluator = new PolicySetEvaluator(connections, Path.of("."), clock);
    }

    @Test
    @DisplayName("a real evaluation over the real repository and a real store: twelve checks, not blocking")
    void realEvaluationProducesAVerdict() throws Exception {
        PolicyVerdict verdict = evaluator.evaluate(Map.of());
        assertNotNull(verdict);
        assertNotNull(verdict.report());
        assertTrue(verdict.report().contains("12 checks evaluated"), verdict.report());
        assertFalse(verdict.blocking(), "the real project, as committed, must not block: " + verdict.report());
    }

    @Test
    @DisplayName("POL-AUD-001 real check: the database's own NOT NULL/CHECK constraints already make an "
            + "incomplete row impossible to insert — proven directly, not assumed")
    void auditRecordTableStructurallyRefusesAnIncompleteRow() throws Exception {
        // A blank reason is refused by audit_reason_not_blank (V3) before POL-AUD-001's own query would
        // ever see it — confirming the query is a second, independent line of defense over a shape the
        // database already makes impossible, not the only thing standing between an incomplete row and
        // the table. Same argument AuditImmutabilityIT (T028) makes for UPDATE/DELETE, applied to blank
        // fields instead.
        try (Connection c = connections.get(); var ps = c.prepareStatement(
                "INSERT INTO audit_record (run_id, correlation_id, actor_type, action, occurred_at, "
                        + "affected_artifact, result, reason) VALUES (?, ?, 'system', 'RUN_CREATED', ?, "
                        + "'workflow_run', 'SUCCESS', '')")) {
            java.util.UUID runId = java.util.UUID.randomUUID();
            ps.setObject(1, runId);
            ps.setObject(2, runId);
            ps.setTimestamp(3, java.sql.Timestamp.from(Instant.parse("2026-09-21T12:00:00Z")));
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, ps::executeUpdate,
                    "a blank reason must violate audit_reason_not_blank before POL-AUD-001 ever runs");
        }

        PolicyVerdict verdict = evaluator.evaluate(Map.of());
        assertFalse(verdict.report().contains("POL-AUD-001 FAIL"),
                "no incomplete row exists (the database refused to store one), so POL-AUD-001 must pass: "
                        + verdict.report());
    }

    @Test
    @DisplayName("EC-025: POL-AUD-001 unevaluable (store unreachable) is recorded FAIL, never PASS")
    void unevaluableAuditCheckIsRecordedFailNeverPass() {
        PolicySetEvaluator broken = new PolicySetEvaluator(
                () -> { throw new java.sql.SQLException("simulated store outage"); },
                Path.of("."), Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC));

        assertTrue(assertDoesNotThrowAndBlocks(broken), "an unevaluable mandatory check must block — "
                + "EC-025's default-deny rule, never silently PASS");
    }

    private static boolean assertDoesNotThrowAndBlocks(PolicySetEvaluator evaluator) {
        try {
            PolicyVerdict verdict = evaluator.evaluate(Map.of());
            return verdict.blocking();
        } catch (Exception e) {
            throw new IllegalStateException("evaluate() must never throw for a check it cannot evaluate — "
                    + "it must record FAIL instead (EC-025)", e);
        }
    }

    @Test
    @DisplayName("POL-SEC-001 real check: the real SchemeAllowList is non-empty")
    void realSchemeAllowListCheckPasses() throws Exception {
        PolicyVerdict verdict = evaluator.evaluate(Map.of());
        assertFalse(verdict.report().contains("POL-SEC-001 FAIL"),
                "the real scheme allow-list (http, https) must be non-empty: " + verdict.report());
    }
}
