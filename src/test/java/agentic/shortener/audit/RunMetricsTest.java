package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task T122 — the six reliability/orchestration measures, hand-computed and each individually labelled.
 * FR-ORC-024.
 */
@DisplayName("T122 — RunMetrics: six measures exposed, each labelled, retry/rollback/compensation isolated")
class RunMetricsTest {

    private static final String CONDITIONS = "seeded test population, single developer machine";

    @Test
    @DisplayName("hand-computed: 8 succeeded / 2 failed of 10 runs; retry/rollback/compensation over 20 "
            + "stage executions; latency averaged over 10 samples")
    void handComputedSixMeasures() {
        RunMetricsInput input = new RunMetricsInput(
                10, 8, 2,
                6, 20,
                3, 1,
                Duration.ofSeconds(200), Duration.ofSeconds(50), 10,
                4);

        RunMetricsReport report = RunMetrics.expose(input, CONDITIONS);

        assertEquals(0.8, report.workflowSuccessRate().value(), "8/10 succeeded");
        assertEquals(0.2, report.workflowFailureRate().value(), "2/10 failed");
        assertEquals(0.3, report.retryFrequency().value(), "6/20 stage executions retried");
        assertEquals(0.15, report.rollbackFrequency().value(), "3/20 stage executions rolled back");
        assertEquals(0.05, report.compensationFrequency().value(), "1/20 stage executions compensated");
        assertEquals(Duration.ofSeconds(20), report.endToEndLatency().value(), "200s / 10 samples");
        assertEquals(Duration.ofSeconds(5), report.suspendedTime().value(),
                "50s of suspended time / 10 samples, reported separately from end-to-end latency");
        assertEquals(4, report.unrecoveredFailureCount().value());

        // Every figure is labelled MEASURED with the same disclosed conditions — none is bare.
        assertEquals(MeasurementLabel.MEASURED, report.workflowSuccessRate().status());
        assertEquals(CONDITIONS, report.endToEndLatency().conditions());
    }

    @Test
    @DisplayName("zero stage executions: retry/rollback/compensation frequencies are zero, not undefined")
    void zeroStageExecutionsYieldsZeroFrequencies() {
        RunMetricsInput input = new RunMetricsInput(5, 5, 0, 0, 0, 0, 0, Duration.ZERO, Duration.ZERO, 0, 0);
        RunMetricsReport report = RunMetrics.expose(input, CONDITIONS);

        assertEquals(0.0, report.retryFrequency().value());
        assertEquals(0.0, report.rollbackFrequency().value());
        assertEquals(0.0, report.compensationFrequency().value());
        assertEquals(Duration.ZERO, report.endToEndLatency().value());
    }

    @Test
    @DisplayName("NEGATIVE: zero total runs is undefined for a success/failure rate")
    void zeroTotalRunsIsUndefined() {
        RunMetricsInput input = new RunMetricsInput(0, 0, 0, 0, 0, 0, 0, Duration.ZERO, Duration.ZERO, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> RunMetrics.expose(input, CONDITIONS));
    }

    @Test
    @DisplayName("NEGATIVE: succeeded + failed exceeding totalRuns is rejected at construction")
    void succeededPlusFailedCannotExceedTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunMetricsInput(5, 4, 3, 0, 0, 0, 0, Duration.ZERO, Duration.ZERO, 0, 0));
    }
}
