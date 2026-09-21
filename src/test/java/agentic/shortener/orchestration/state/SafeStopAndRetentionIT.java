package agentic.shortener.orchestration.state;

import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.orchestration.store.PersistedRun;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tasks T091 and T092 — safe-stop suspension, and idle retention. FR-ORC-017, FR-ORC-032 (CL-005).
 *
 * <p><strong>One test class because the schema couples them.</strong> V4's
 * {@code run_auto_abandon_iff_safe_stop} makes {@code auto_abandon_at} non-null <em>iff</em> the run is
 * suspended, so there is no way to write a SAFE_STOP row without already knowing the retention period. T092's
 * dependency on T091 is real in the other direction too — the period is only ever computed for a suspended
 * run — so building them apart would mean one of them landing unable to write a legal row.
 *
 * <p>Against the real store, because every property here is a constraint the store owns.
 */
@DisplayName("T091, T092 — suspension is recorded and resumable; retention is idle-based")
class SafeStopAndRetentionIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore store;
    private SafeStopHandler safeStop;
    private RetentionPolicy retention;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        store = storeAt(T0);
        retention = new RetentionPolicy();
        safeStop = new SafeStopHandler(connections, Clock.fixed(T0, ZoneOffset.UTC), retention);

        runId = store.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        store.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
    }

    private JdbcRunStore storeAt(Instant when) {
        return new JdbcRunStore(connections, Clock.fixed(when, ZoneOffset.UTC));
    }

    // ==============================================================================================
    // T091 — suspension
    // ==============================================================================================

    @Test
    @DisplayName("all five trigger classes suspend, and each records its own reason")
    void everyTriggerClassSuspends() {
        List<String> missing = new ArrayList<>();
        for (SuspensionTrigger trigger : EnumSet.allOf(SuspensionTrigger.class)) {
            UUID run = store.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
            store.transitionRun(run, RunState.PENDING, RunState.RUNNING, "started");

            safeStop.suspend(run, trigger, "detail for " + trigger);

            PersistedRun suspended = store.run(run).orElseThrow();
            if (suspended.state() != RunState.SAFE_STOP
                    || suspended.suspensionReason() == null
                    || !suspended.suspensionReason().contains(trigger.name())) {
                missing.add(trigger + " -> " + suspended.state() + " / " + suspended.suspensionReason());
            }
        }
        assertEquals(List.of(), missing,
                "FR-ORC-017 names five triggers and each must produce a suspension naming itself; a run "
                        + "suspended without saying which trigger fired cannot be triaged: " + missing);
        assertEquals(5, SuspensionTrigger.values().length,
                "gate timeout, unrecoverable failure, blocking policy FAIL, unrecognized classification, "
                        + "and an effect with no known compensating action");
    }

    @Test
    @DisplayName("SAFE_STOP is not terminal, and the suspended state is resumable")
    void suspendedStateIsResumable() {
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");

        PersistedRun suspended = store.run(runId).orElseThrow();
        assertEquals(RunState.SAFE_STOP, suspended.state());
        assertEquals(null, suspended.terminalState(),
                "CL-005: the terminal set is exactly COMPLETED, REJECTED, ABANDONED. A terminal_state here "
                        + "would make the run both suspended and finished");
        assertFalse(RunState.SAFE_STOP.terminal());
        assertNotNull(suspended.autoAbandonAt(), "a suspended run must disclose when it will be abandoned");

        // Resumable in the only sense that matters: a human decision moves it back to RUNNING, and the
        // suspension fields are cleared as it goes — otherwise the run would be RUNNING while still
        // carrying a disclosed abandonment time, which the CHECK constraint refuses outright.
        safeStop.resumeOnHumanDecision(runId, "the owner answered the clarification");

        PersistedRun resumed = store.run(runId).orElseThrow();
        assertEquals(RunState.RUNNING, resumed.state());
        assertEquals(null, resumed.suspensionReason());
        assertEquals(null, resumed.autoAbandonAt());
    }

    @Test
    @DisplayName("no component may resume a suspended run on its own authority")
    void onlyAHumanOrRetentionLeavesSafeStop() {
        safeStop.suspend(runId, SuspensionTrigger.UNRECOVERABLE_FAILURE, "S7 failed permanently");

        // The rule TransitionRules already states, enforced at the one place that can actually write the
        // row. FR-ORC-017: a run in SAFE_STOP is left only by a human decision or by retention expiry.
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> store.transitionRun(runId, RunState.SAFE_STOP, RunState.RUNNING, "carrying on"));
        assertTrue(refused.getMessage().contains("SAFE_STOP"), refused.getMessage());

        assertEquals(RunState.SAFE_STOP, store.run(runId).orElseThrow().state());
    }

    @Test
    @DisplayName("the no-compensating-action trigger records the uncompensated effect")
    void uncompensatedEffectIsRecorded() throws Exception {
        // T091's guard: safe-stop MUST NOT leave partially applied effects unrecorded. The register refuses
        // to log NONE_KNOWN as a compensation — correctly, since nothing was applied — so the fact has to
        // land somewhere, and an audit row is where an unresolved effect belongs.
        safeStop.suspendForUncompensatedEffect(runId, "S7.1", "mystery:1",
                "the effect class is unknown and no action is declared");

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT actor_type, action, affected_artifact, result, reason FROM audit_record "
                             + "WHERE run_id = ? AND action = 'SUSPENSION'")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "the suspension must be audited");
                assertEquals("system", rs.getString("actor_type"));
                assertEquals("mystery:1", rs.getString("affected_artifact"),
                        "the effect itself is the affected artifact, so a reviewer can find what was left "
                                + "half-applied without reading prose");
                assertTrue(rs.getString("reason").contains("no known compensating action"),
                        rs.getString("reason"));
            }
        }
        assertEquals(RunState.SAFE_STOP, store.run(runId).orElseThrow().state());
    }

    @Test
    @DisplayName("every suspension is audited, whatever the trigger")
    void everySuspensionIsAudited() throws Exception {
        safeStop.suspend(runId, SuspensionTrigger.BLOCKING_POLICY_FAIL, "SEC-002 is a mandatory FAIL");

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM audit_record WHERE run_id = ? AND action = 'SUSPENSION'")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    // ==============================================================================================
    // T092 — retention
    // ==============================================================================================

    @Test
    @DisplayName("PVT-015: ninety days, measured from LAST ACTIVITY and never from creation")
    void ninetyDaysFromLastActivity() {
        assertEquals(Duration.ofDays(90), RetentionPolicy.IDLE_PERIOD);

        Instant lastActivity = T0.plus(Duration.ofDays(10));
        assertEquals(lastActivity.plus(Duration.ofDays(90)), retention.autoAbandonAt(lastActivity),
                "from last activity. Measured from creation, a run that had been worked on for months "
                        + "would be reaped while somebody was still using it");
    }

    @Test
    @DisplayName("EC-036: activity one day before expiry RESETS the clock")
    void activityResetsTheClock() {
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "awaiting a decision");
        Instant firstDeadline = store.run(runId).orElseThrow().autoAbandonAt();

        // One day before the deadline, somebody touches the run. Because auto_abandon_at is derived from
        // last_activity_at rather than stored once and forgotten, the reset is a consequence of recording
        // activity — not a separate rule something has to remember to apply.
        Instant almostExpired = firstDeadline.minus(Duration.ofDays(1));
        SafeStopHandler later = new SafeStopHandler(connections,
                Clock.fixed(almostExpired, ZoneOffset.UTC), retention);
        later.recordActivity(runId, "the reviewer asked a question on the gate");

        Instant secondDeadline = store.run(runId).orElseThrow().autoAbandonAt();
        assertEquals(almostExpired.plus(Duration.ofDays(90)), secondDeadline);
        assertTrue(secondDeadline.isAfter(firstDeadline),
                "EC-036: the idle clock resets rather than the run being reaped. A run that received "
                        + "attention must not be reaped on age");
        assertEquals(RunState.SAFE_STOP, store.run(runId).orElseThrow().state(),
                "and recording activity does not itself resume the run — only a human decision does");
    }

    @Test
    @DisplayName("expiry abandons the run, citing the policy, as actor system under pre-approved authority")
    void expiryAbandons() throws Exception {
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "awaiting a decision");
        Instant deadline = store.run(runId).orElseThrow().autoAbandonAt();

        RetentionPolicy expiring = new RetentionPolicy();
        SafeStopHandler atExpiry = new SafeStopHandler(connections,
                Clock.fixed(deadline.plusSeconds(1), ZoneOffset.UTC), expiring);
        assertTrue(atExpiry.abandonIfExpired(runId));

        PersistedRun abandoned = store.run(runId).orElseThrow();
        assertEquals(RunState.ABANDONED, abandoned.state());
        assertEquals(RunState.ABANDONED, abandoned.terminalState(), "abandonment is terminal");
        assertEquals(null, abandoned.autoAbandonAt(),
                "no longer suspended, so the disclosed abandonment time is cleared — the constraint "
                        + "requires it, and a terminal run still advertising a future reaping is nonsense");

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT actor_type, reason FROM audit_record "
                             + "WHERE run_id = ? AND action = 'AUTO_ABANDON'")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("system", rs.getString("actor_type"));
                String reason = rs.getString("reason");
                assertTrue(reason.contains("pre-approved retention policy"), reason);
                assertTrue(reason.contains(RetentionPolicy.VERSION), reason);
            }
        }
    }

    @Test
    @DisplayName("a run that has NOT expired is left alone")
    void unexpiredRunIsNotReaped() {
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "awaiting a decision");
        assertFalse(safeStop.abandonIfExpired(runId), "the deadline is ninety days away");
        assertEquals(RunState.SAFE_STOP, store.run(runId).orElseThrow().state());
    }

    @Test
    @DisplayName("EC-037: expiry while a gate is pending abandons terminally and does NOT satisfy the gate")
    void expiryDoesNotSatisfyAPendingGate() throws Exception {
        store.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "ingesting");
        store.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "run created");
        store.transitionNode(runId, "S4", StageState.BLOCKED, StageState.AWAITING_APPROVAL,
                "material ambiguity found");

        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");
        Instant deadline = store.run(runId).orElseThrow().autoAbandonAt();
        new SafeStopHandler(connections, Clock.fixed(deadline.plusSeconds(1), ZoneOffset.UTC), retention)
                .abandonIfExpired(runId);

        assertEquals(RunState.ABANDONED, store.run(runId).orElseThrow().state());

        // The gate is still pending. It was never approved, never rejected and never decided — abandonment
        // is a run-lifecycle outcome, not a gate outcome, and the temptation it must resist is tidying the
        // node away as resolved.
        assertEquals(StageState.AWAITING_APPROVAL, store.node(runId, "S4").orElseThrow().state(),
                "EC-037: abandonment is terminal and is not an approval; the gate is never satisfied by it");

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM gate_decision WHERE run_id = ?")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1),
                        "and no decision was written on the run's behalf. Constitution III: silence is "
                                + "never approval, and neither is a deadline");
            }
        }
    }

    @Test
    @DisplayName("auto-abandonment is a lifecycle transition, NOT a purge — the records stay")
    void abandonmentIsNotAPurge() throws Exception {
        store.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "ingesting");
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "awaiting");
        int transitionsBefore = countTransitions();

        Instant deadline = store.run(runId).orElseThrow().autoAbandonAt();
        new SafeStopHandler(connections, Clock.fixed(deadline.plusSeconds(1), ZoneOffset.UTC), retention)
                .abandonIfExpired(runId);

        assertTrue(countTransitions() > transitionsBefore,
                "abandonment appends its own transition rather than removing history");
        assertEquals(13, store.persistedNodes(runId).size(),
                "every node is still there. CR-017: there is no purge anywhere, and an abandoned run's "
                        + "records stay in the tables like every other record");
    }

    @Test
    @DisplayName("only a suspended run can be abandoned by retention")
    void onlySuspendedRunsAreReaped() {
        // A RUNNING run has no auto_abandon_at at all — the constraint forbids one — so there is nothing to
        // compare against. Reaping on age would mean inventing a deadline for a run nobody disclosed one for.
        assertThrows(IllegalStateException.class, () -> safeStop.abandonIfExpired(runId));
    }

    private int countTransitions() throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM state_transition WHERE run_id = ?")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
