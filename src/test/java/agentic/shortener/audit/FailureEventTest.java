package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T115 — FailureEvent's nine fields, structurally enforced. FR-ORC-024, plan §7.
 */
@DisplayName("T115 — FailureEvent: nine fields, individualRecoveryDuration excludes human wait")
class FailureEventTest {

    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000cc");
    private static final Instant DETECTED = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    @DisplayName("recovered(): individualRecoveryDuration = (completed - detected) - humanWait")
    void recoveredComputesDurationExcludingHumanWait() {
        Instant started = DETECTED.plusSeconds(1);
        Instant completed = DETECTED.plusSeconds(61);
        FailureEvent event = FailureEvent.recovered(RUN_ID, "S2", "UNAVAILABLE", DETECTED, started, completed,
                RecoveryMechanism.RETRY, Duration.ofSeconds(10), "recovered on the second attempt");

        assertTrue(event.recovered());
        assertEquals(Duration.ofSeconds(51), event.individualRecoveryDuration(),
                "61s raw elapsed (detected to completed) minus 10s of human wait");
    }

    @Test
    @DisplayName("unrecovered(): recovered is false, individualRecoveryDuration is null")
    void unrecoveredHasNoDuration() {
        FailureEvent event = FailureEvent.unrecovered(RUN_ID, "S2", "INVALID_INPUT", DETECTED, null, null,
                Duration.ZERO, "ruled permanent, no retry attempted");

        assertFalse(event.recovered());
        assertNull(event.individualRecoveryDuration());
        assertNull(event.recoveryStartedAt());
        assertNull(event.recoveryCompletedAt());
    }

    @Test
    @DisplayName("NEGATIVE: a recovered event with a wrong individualRecoveryDuration is rejected")
    void wrongComputedDurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FailureEvent(null, RUN_ID, "S2", "TIMEOUT",
                DETECTED, null, DETECTED.plusSeconds(60), RecoveryMechanism.RETRY, Duration.ZERO,
                Duration.ofSeconds(999), true, "wrong duration"));
    }

    @Test
    @DisplayName("NEGATIVE: recovered=true but recoveryCompletedAt is null is rejected")
    void recoveredFlagMustMatchCompletionTime() {
        assertThrows(IllegalArgumentException.class, () -> new FailureEvent(null, RUN_ID, "S2", "TIMEOUT",
                DETECTED, null, null, null, Duration.ZERO, null, true, "inconsistent"));
    }

    @Test
    @DisplayName("NEGATIVE: an unrecognized failureCategory is rejected")
    void unrecognizedCategoryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FailureEvent.unrecovered(RUN_ID, "S2",
                "SOMETHING_MADE_UP", DETECTED, null, null, Duration.ZERO, "x"));
    }

    @Test
    @DisplayName("NEGATIVE: a blank stageId is rejected")
    void blankStageIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FailureEvent.unrecovered(RUN_ID, " ",
                "TIMEOUT", DETECTED, null, null, Duration.ZERO, "x"));
    }

    @Test
    @DisplayName("NEGATIVE: a blank detail is rejected — an unexplained failure_event row is not actionable")
    void blankDetailIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FailureEvent.unrecovered(RUN_ID, "S2",
                "TIMEOUT", DETECTED, null, null, Duration.ZERO, " "));
    }

    @Test
    @DisplayName("NEGATIVE: a negative humanWaitDuration is rejected")
    void negativeHumanWaitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FailureEvent.unrecovered(RUN_ID, "S2",
                "TIMEOUT", DETECTED, null, null, Duration.ofSeconds(-1), "x"));
    }

    @Test
    @DisplayName("NEGATIVE: recoveryStartedAt before failureDetectedAt is rejected")
    void recoveryCannotStartBeforeDetection() {
        assertThrows(IllegalArgumentException.class, () -> FailureEvent.unrecovered(RUN_ID, "S2",
                "TIMEOUT", DETECTED, DETECTED.minusSeconds(1), null, Duration.ZERO, "x"));
    }

    @Test
    @DisplayName("a mechanism may be recorded on an unrecovered event — attempted but did not succeed")
    void unrecoveredMayNameAnAttemptedMechanism() {
        FailureEvent event = FailureEvent.unrecovered(RUN_ID, "S2", "UNAVAILABLE", DETECTED, DETECTED,
                RecoveryMechanism.RETRY, Duration.ZERO, "bound exhausted after three attempts");
        assertEquals(RecoveryMechanism.RETRY, event.recoveryMechanism());
        assertFalse(event.recovered());
    }
}
