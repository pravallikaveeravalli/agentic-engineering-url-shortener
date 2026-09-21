package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task T117 — human-wait exclusion: a recovery spanning a gate wait excludes it from
 * individualRecoveryDuration and records it separately. plan §7's declared exclusion.
 */
@DisplayName("T117 — HumanWaitTracker: gate waits measured and excluded from recovery duration")
class HumanWaitTrackerTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    @DisplayName("a gate wait fully inside the window is counted in full")
    void gateWaitFullyInsideWindowCountedInFull() {
        StateInterval gateWait = new StateInterval("AWAITING_APPROVAL", T0.plusSeconds(10), T0.plusSeconds(40));
        Duration wait = HumanWaitTracker.humanWaitWithin(List.of(gateWait), T0, T0.plusSeconds(50));
        assertEquals(Duration.ofSeconds(30), wait);
    }

    @Test
    @DisplayName("SAFE_STOP is a human-wait state too, counted the same way as AWAITING_APPROVAL")
    void safeStopCountsTheSameWay() {
        StateInterval safeStop = new StateInterval("SAFE_STOP", T0.plusSeconds(5), T0.plusSeconds(15));
        Duration wait = HumanWaitTracker.humanWaitWithin(List.of(safeStop), T0, T0.plusSeconds(20));
        assertEquals(Duration.ofSeconds(10), wait);
    }

    @Test
    @DisplayName("an interval partially before the window is clipped to its overlapping portion")
    void intervalClippedAtWindowStart() {
        StateInterval gateWait = new StateInterval("AWAITING_APPROVAL", T0.minusSeconds(20), T0.plusSeconds(10));
        Duration wait = HumanWaitTracker.humanWaitWithin(List.of(gateWait), T0, T0.plusSeconds(30));
        assertEquals(Duration.ofSeconds(10), wait, "only the portion from T0 to T0+10s falls in the window");
    }

    @Test
    @DisplayName("an interval partially after the window is clipped to its overlapping portion")
    void intervalClippedAtWindowEnd() {
        StateInterval gateWait = new StateInterval("AWAITING_APPROVAL", T0.plusSeconds(20), T0.plusSeconds(50));
        Duration wait = HumanWaitTracker.humanWaitWithin(List.of(gateWait), T0, T0.plusSeconds(30));
        assertEquals(Duration.ofSeconds(10), wait, "only the portion from T0+20s to T0+30s falls in the window");
    }

    @Test
    @DisplayName("an interval entirely outside the window contributes nothing")
    void intervalOutsideWindowContributesNothing() {
        StateInterval gateWait = new StateInterval("AWAITING_APPROVAL", T0.minusSeconds(100), T0.minusSeconds(50));
        Duration wait = HumanWaitTracker.humanWaitWithin(List.of(gateWait), T0, T0.plusSeconds(30));
        assertEquals(Duration.ZERO, wait);
    }

    @Test
    @DisplayName("a non-human-wait state within the window contributes nothing")
    void nonHumanWaitStateContributesNothing() {
        StateInterval retryWait = new StateInterval("RETRY_WAIT", T0.plusSeconds(5), T0.plusSeconds(15));
        Duration wait = HumanWaitTracker.humanWaitWithin(List.of(retryWait), T0, T0.plusSeconds(20));
        assertEquals(Duration.ZERO, wait, "RETRY_WAIT is system-driven backoff, not human wait");
    }

    @Test
    @DisplayName("multiple gate waits across the window sum together")
    void multipleGateWaitsSum() {
        List<StateInterval> intervals = List.of(
                new StateInterval("AWAITING_APPROVAL", T0.plusSeconds(0), T0.plusSeconds(10)),
                new StateInterval("SAFE_STOP", T0.plusSeconds(20), T0.plusSeconds(35)));
        Duration wait = HumanWaitTracker.humanWaitWithin(intervals, T0, T0.plusSeconds(50));
        assertEquals(Duration.ofSeconds(25), wait);
    }

    @Test
    @DisplayName("NEGATIVE: windowEnd before windowStart is rejected")
    void invalidWindowIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> HumanWaitTracker.humanWaitWithin(List.of(), T0.plusSeconds(10), T0));
    }

    // ==============================================================================================
    // End-to-end: the exact Validate clause — a recovery spanning a gate wait excludes it from
    // individualRecoveryDuration and records it in humanWaitDuration.
    // ==============================================================================================

    @Test
    @DisplayName("a recovery spanning a gate wait excludes it from individualRecoveryDuration")
    void recoverySpanningAGateWaitExcludesItFromDuration() {
        UUID runId = UUID.randomUUID();
        Instant detected = T0;
        Instant started = T0;
        Instant completed = T0.plusSeconds(50);
        StateInterval gateWait = new StateInterval("AWAITING_APPROVAL", T0.plusSeconds(10), T0.plusSeconds(40));

        Duration humanWait = HumanWaitTracker.humanWaitWithin(List.of(gateWait), started, completed);
        FailureEvent event = FailureEvent.recovered(runId, "S4", "UNAVAILABLE", detected, started, completed,
                RecoveryMechanism.HUMAN, humanWait, "recovered after a gate approval");

        assertEquals(Duration.ofSeconds(30), event.humanWaitDuration(), "the 30s gate wait, recorded separately");
        assertEquals(Duration.ofSeconds(20), event.individualRecoveryDuration(),
                "50s raw elapsed minus the excluded 30s gate wait");
    }
}
