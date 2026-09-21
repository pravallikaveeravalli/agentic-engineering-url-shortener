package agentic.shortener.audit;

import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T115 — FailureEventWriter against a real store: the nine fields round-trip, non-null where
 * applicable. FR-ORC-024, plan §7. Mirrors {@code AuditWriterIT}'s structure.
 */
@DisplayName("T115 — FailureEventWriter: nine fields round-trip, non-null where applicable")
class FailureEventWriterIT extends PostgresIntegrationTest {

    private static final Instant DETECTED = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private FailureEventWriter writer;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        writer = new FailureEventWriter(connections);
        runId = UUID.randomUUID();
    }

    @Test
    @DisplayName("a recovered event round-trips with every applicable field non-null")
    void recoveredEventRoundTrips() {
        FailureEvent written = FailureEvent.recovered(runId, "S2", "UNAVAILABLE", DETECTED,
                DETECTED.plusSeconds(1), DETECTED.plusSeconds(31), RecoveryMechanism.RETRY,
                Duration.ofSeconds(5), "recovered after two retries");
        long id = writer.write(written);
        FailureEvent reread = writer.byId(id).orElseThrow();

        assertEquals(runId, reread.runId());
        assertEquals("S2", reread.stageId());
        assertEquals("UNAVAILABLE", reread.failureCategory());
        assertEquals(DETECTED, reread.failureDetectedAt());
        assertNotNull(reread.recoveryStartedAt(), "recovery_started_at must be present for a recovered event");
        assertNotNull(reread.recoveryCompletedAt());
        assertEquals(RecoveryMechanism.RETRY, reread.recoveryMechanism());
        assertEquals(Duration.ofSeconds(5), reread.humanWaitDuration());
        assertEquals(Duration.ofSeconds(26), reread.individualRecoveryDuration(),
                "31s raw elapsed (detected to completed) minus 5s human wait");
        assertTrue(reread.recovered());
        assertNotNull(reread.detail());
    }

    @Test
    @DisplayName("an unrecovered event round-trips with recovery-outcome fields null")
    void unrecoveredEventRoundTrips() {
        FailureEvent written = FailureEvent.unrecovered(runId, "S4", "INVALID_INPUT", DETECTED, null, null,
                Duration.ZERO, "ruled permanent, no retry attempted");
        long id = writer.write(written);
        FailureEvent reread = writer.byId(id).orElseThrow();

        assertFalse(reread.recovered());
        assertNull(reread.recoveryStartedAt());
        assertNull(reread.recoveryCompletedAt());
        assertNull(reread.recoveryMechanism());
        assertNull(reread.individualRecoveryDuration());
        assertEquals(Duration.ZERO, reread.humanWaitDuration(), "always present, even when zero");
    }

    @Test
    @DisplayName("POL: every written row has all nine plan-§7 fields present or correctly null")
    void allNineFieldsPresentOrCorrectlyNull() throws Exception {
        writer.write(FailureEvent.recovered(runId, "S2", "TIMEOUT", DETECTED, DETECTED,
                DETECTED.plusSeconds(10), RecoveryMechanism.RETRY, Duration.ZERO, "recovered"));
        writer.write(FailureEvent.unrecovered(runId, "S5", "INTERNAL", DETECTED, null, null, Duration.ZERO,
                "never recovered"));

        try (var c = connections.get(); var ps = c.prepareStatement(
                "SELECT COUNT(*) FROM failure_event WHERE run_id = ? AND ("
                        + "node_key IS NULL OR btrim(node_key) = '' "
                        + "OR category IS NULL OR btrim(category) = '' "
                        + "OR detected_at IS NULL "
                        + "OR human_wait_duration_ms IS NULL "
                        + "OR detail IS NULL OR btrim(detail) = '' "
                        // recovered=true rows must carry recovery_started_at, recovered_at and the duration;
                        // recovered=false rows must NOT (V3/V13's own CHECK constraints already forbid the
                        // inconsistent combinations — this re-asserts it as a direct query, not by trusting
                        // the constraint alone).
                        + "OR (recovered AND (recovered_at IS NULL OR individual_recovery_duration_ms IS NULL)) "
                        + "OR (NOT recovered AND (recovered_at IS NOT NULL "
                        + "OR individual_recovery_duration_ms IS NOT NULL)))")) {
            ps.setObject(1, runId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1),
                        "every failure_event row must have its nine fields present or correctly null");
            }
        }
    }

    @Test
    @DisplayName("NEGATIVE: the database itself refuses an inconsistent recovered/recovered_at pair")
    void databaseRefusesInconsistentRecoveredFlag() throws Exception {
        // Bypasses FailureEvent's own compact-constructor check entirely, via a raw INSERT — proving the
        // CHECK constraint (V13) is a real backstop, not merely mirrored in Java and never itself exercised.
        try (var c = connections.get(); var ps = c.prepareStatement(
                "INSERT INTO failure_event (run_id, node_key, category, detected_at, recovered_at, detail, "
                        + "recovered) VALUES (?, 'S2', 'TIMEOUT', ?, NULL, 'x', TRUE)")) {
            ps.setObject(1, runId);
            ps.setTimestamp(2, java.sql.Timestamp.from(DETECTED));
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, ps::executeUpdate,
                    "recovered=TRUE with recovered_at NULL violates failure_event_recovered_matches_recovered_at");
        }
    }
}
