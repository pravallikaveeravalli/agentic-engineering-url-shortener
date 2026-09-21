package agentic.shortener.audit;

import java.time.Duration;

/**
 * The six measures plan §7 names, each labelled (T121) rather than bare. Task T122. FR-ORC-024.
 *
 * <p>Eight fields for six named measures: "rollback and compensation frequencies counted separately" and
 * "end-to-end latency with suspended time reported separately" each name one conceptual measure that
 * splits into two figures — a rollback frequency merged with a compensation frequency, or a latency
 * merged with the suspended time inside it, would be exactly the kind of folding-together T119 already
 * refused for the MTTR denominator.
 */
public record RunMetricsReport(MeasurementLabel<Double> workflowSuccessRate,
                                MeasurementLabel<Double> workflowFailureRate,
                                MeasurementLabel<Double> retryFrequency,
                                MeasurementLabel<Double> rollbackFrequency,
                                MeasurementLabel<Double> compensationFrequency,
                                MeasurementLabel<Duration> endToEndLatency,
                                MeasurementLabel<Duration> suspendedTime,
                                MeasurementLabel<Integer> unrecoveredFailureCount) {
}
