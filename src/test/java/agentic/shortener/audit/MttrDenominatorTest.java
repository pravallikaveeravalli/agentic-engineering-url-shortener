package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task T119 — unrecovered failures excluded from the MTTR denominator, and counted separately. plan §7.
 *
 * <p>A dedicated proof, distinct from {@code MttrCalculatorTest}'s hand-computed value: a larger seeded
 * population weighted heavily toward unrecovered rows, so a calculation that quietly widened its
 * denominator (folding them back in) would move the resulting {@code mttr} by a wide, unmissable margin
 * rather than a rounding-sized one.
 */
@DisplayName("T119 — MTTR denominator: unrecovered rows excluded, and counted separately")
class MttrDenominatorTest {

    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private static FailureEvent recoveredIn(int seconds) {
        return FailureEvent.recovered(RUN_ID, "S2", "UNAVAILABLE", T0, T0, T0.plusSeconds(seconds),
                RecoveryMechanism.RETRY, Duration.ZERO, "recovered in " + seconds + "s");
    }

    private static FailureEvent unrecovered(String category) {
        return FailureEvent.unrecovered(RUN_ID, "S4", category, T0, null, null, Duration.ZERO,
                "never recovered");
    }

    @Test
    @DisplayName("nine unrecovered rows alongside one recovered row: MTTR is the recovered row's own "
            + "duration, not diluted by a denominator of ten")
    void unrecoveredRowsDoNotDiluteMttr() {
        List<FailureEvent> population = new ArrayList<>();
        population.add(recoveredIn(100));
        for (int i = 0; i < 9; i++) {
            population.add(unrecovered("UNAVAILABLE"));
        }

        MttrResult result = MttrCalculator.calculate(population);

        assertEquals(Duration.ofSeconds(100), result.mttr(),
                "a denominator of 10 (folding the nine unrecovered rows in) would report 10s instead");
        assertEquals(1, result.recoveredCount());
        assertEquals(9, result.unrecoveredCount(), "the nine unrecovered rows are reported, not dropped");
    }

    @Test
    @DisplayName("unrecovered rows of different failure categories are all excluded the same way")
    void allUnrecoveredCategoriesExcludedUniformly() {
        List<FailureEvent> population = List.of(
                recoveredIn(10), recoveredIn(30),
                unrecovered("TIMEOUT"), unrecovered("RATE_LIMITED"), unrecovered("INTERNAL"),
                unrecovered("INVALID_INPUT"), unrecovered("UNKNOWN"));

        MttrResult result = MttrCalculator.calculate(population);

        assertEquals(Duration.ofSeconds(20), result.mttr(), "(10s + 30s) / 2 recovered events");
        assertEquals(2, result.recoveredCount());
        assertEquals(5, result.unrecoveredCount());
    }

    @Test
    @DisplayName("zero unrecovered rows: the separate count is exactly zero, not absent")
    void zeroUnrecoveredIsReportedAsZeroNotOmitted() {
        MttrResult result = MttrCalculator.calculate(List.of(recoveredIn(15)));
        assertEquals(0, result.unrecoveredCount(),
                "a population with no unrecovered rows still reports the count, as zero, rather than the "
                        + "caller having to infer it from the recovered count and population size");
    }
}
