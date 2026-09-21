package agentic.shortener.audit;

import java.time.Duration;
import java.util.Objects;

/**
 * The raw counts {@link RunMetrics} exposes as labelled figures. Task T122. FR-ORC-024.
 *
 * <p>This task exposes the measures; it does not produce the load these counts come from — PVT-001's
 * measured value comes from T145b (the load itself) with the analytics-append component isolated by
 * T145d, both later tasks. A caller assembles this record from whatever run history, {@code
 * failure_event}, and {@code state_transition} rows it has already read; this type and {@link RunMetrics}
 * do no querying of their own.
 */
public record RunMetricsInput(int totalRuns, int succeededRuns, int failedRuns, int retryAttempts,
                               int stageExecutions, int rollbackCount, int compensationCount,
                               Duration totalEndToEndLatency, Duration totalSuspendedTime,
                               int latencySampleCount, int unrecoveredFailureCount) {

    public RunMetricsInput {
        requireNonNegative(totalRuns, "totalRuns");
        requireNonNegative(succeededRuns, "succeededRuns");
        requireNonNegative(failedRuns, "failedRuns");
        requireNonNegative(retryAttempts, "retryAttempts");
        requireNonNegative(stageExecutions, "stageExecutions");
        requireNonNegative(rollbackCount, "rollbackCount");
        requireNonNegative(compensationCount, "compensationCount");
        Objects.requireNonNull(totalEndToEndLatency, "totalEndToEndLatency");
        Objects.requireNonNull(totalSuspendedTime, "totalSuspendedTime");
        requireNonNegative(latencySampleCount, "latencySampleCount");
        requireNonNegative(unrecoveredFailureCount, "unrecoveredFailureCount");
        if (succeededRuns + failedRuns > totalRuns) {
            throw new IllegalArgumentException(
                    "succeededRuns + failedRuns (" + (succeededRuns + failedRuns)
                            + ") cannot exceed totalRuns (" + totalRuns + ") — other terminal states "
                            + "(INVALIDATED, ABANDONED) may account for the remainder, but success and "
                            + "failure alone cannot outnumber the population they are drawn from");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative, got " + value);
        }
    }
}
