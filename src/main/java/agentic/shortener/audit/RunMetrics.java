package agentic.shortener.audit;

import java.time.Duration;
import java.util.Objects;

/**
 * Exposes {@link RunMetricsInput}'s raw counts as the six labelled measures plan §7 names — retrievable
 * per run and in aggregate, by calling this once over a single run's counts or once over an aggregated
 * population. Task T122. FR-ORC-024.
 *
 * <p><strong>Retry frequency, rollback frequency, and compensation frequency share one denominator</strong>
 * — stage executions, not workflow runs — so the three "how often did this happen" measures are directly
 * comparable to one another. This is a documented choice, not the only defensible one; a caller reporting
 * these figures quotes this javadoc alongside them rather than assuming the denominator is self-evident.
 */
public final class RunMetrics {

    private RunMetrics() {
    }

    public static RunMetricsReport expose(RunMetricsInput input, String conditions) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(conditions, "conditions");
        if (input.totalRuns() == 0) {
            throw new IllegalArgumentException(
                    "a success/failure rate is undefined over zero runs — there is no population to "
                            + "compute a rate from");
        }

        double successRate = (double) input.succeededRuns() / input.totalRuns();
        double failureRate = (double) input.failedRuns() / input.totalRuns();
        double retryFrequency = rate(input.retryAttempts(), input.stageExecutions());
        double rollbackFrequency = rate(input.rollbackCount(), input.stageExecutions());
        double compensationFrequency = rate(input.compensationCount(), input.stageExecutions());
        Duration avgLatency = average(input.totalEndToEndLatency(), input.latencySampleCount());
        Duration avgSuspended = average(input.totalSuspendedTime(), input.latencySampleCount());

        return new RunMetricsReport(
                MeasurementLabel.measured(successRate, conditions),
                MeasurementLabel.measured(failureRate, conditions),
                MeasurementLabel.measured(retryFrequency, conditions),
                MeasurementLabel.measured(rollbackFrequency, conditions),
                MeasurementLabel.measured(compensationFrequency, conditions),
                MeasurementLabel.measured(avgLatency, conditions),
                MeasurementLabel.measured(avgSuspended, conditions),
                MeasurementLabel.measured(input.unrecoveredFailureCount(), conditions));
    }

    /** Zero stage executions means no denominator, not an undefined frequency — reported as zero. */
    private static double rate(int count, int denominator) {
        return denominator == 0 ? 0.0 : (double) count / denominator;
    }

    private static Duration average(Duration total, int sampleCount) {
        return sampleCount == 0 ? Duration.ZERO : total.dividedBy(sampleCount);
    }
}
