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
 * Task T118 — MTTR calculation over a seeded population with a hand-computed expected value. FR-ORC-024,
 * NFR-REC-002.
 */
@DisplayName("T118 — MttrCalculator: the mandated formula, hand-computed")
class MttrCalculatorTest {

    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ee");
    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    /** individualRecoveryDuration = 10s: 10s raw elapsed, no human wait. */
    private static FailureEvent recovered10s() {
        return FailureEvent.recovered(RUN_ID, "S2", "UNAVAILABLE", T0, T0, T0.plusSeconds(10),
                RecoveryMechanism.RETRY, Duration.ZERO, "recovered in 10s");
    }

    /** individualRecoveryDuration = 20s: 25s raw elapsed minus 5s human wait. */
    private static FailureEvent recovered20s() {
        return FailureEvent.recovered(RUN_ID, "S5", "INTERNAL", T0, T0, T0.plusSeconds(25),
                RecoveryMechanism.COMPENSATION, Duration.ofSeconds(5), "recovered in 20s net of wait");
    }

    /** individualRecoveryDuration = 30s: 40s raw elapsed minus 10s human wait. */
    private static FailureEvent recovered30s() {
        return FailureEvent.recovered(RUN_ID, "S9", "TIMEOUT", T0, T0, T0.plusSeconds(40),
                RecoveryMechanism.HUMAN, Duration.ofSeconds(10), "recovered in 30s net of wait");
    }

    private static FailureEvent unrecovered() {
        return FailureEvent.unrecovered(RUN_ID, "S4", "INVALID_INPUT", T0, null, null, Duration.ZERO,
                "ruled permanent, no retry attempted");
    }

    @Test
    @DisplayName("hand-computed: (10s + 20s + 30s) / 3 = 20s, over a population with two unrecovered events")
    void handComputedMttr() {
        MttrResult result = MttrCalculator.calculate(
                List.of(recovered10s(), recovered20s(), recovered30s(), unrecovered(), unrecovered()));

        assertEquals(Duration.ofSeconds(20), result.mttr());
        assertEquals(3, result.recoveredCount());
        assertEquals(2, result.unrecoveredCount());
    }

    @Test
    @DisplayName("a single recovered event: MTTR equals its own individualRecoveryDuration")
    void singleRecoveredEvent() {
        MttrResult result = MttrCalculator.calculate(List.of(recovered20s()));
        assertEquals(Duration.ofSeconds(20), result.mttr());
        assertEquals(1, result.recoveredCount());
        assertEquals(0, result.unrecoveredCount());
    }

    @Test
    @DisplayName("NEGATIVE: zero RECOVERED events is undefined, not zero")
    void zeroRecoveredEventsIsUndefined() {
        assertThrows(IllegalArgumentException.class,
                () -> MttrCalculator.calculate(List.of(unrecovered(), unrecovered())));
    }

    @Test
    @DisplayName("NEGATIVE: an empty population is undefined")
    void emptyPopulationIsUndefined() {
        assertThrows(IllegalArgumentException.class, () -> MttrCalculator.calculate(List.of()));
    }
}
