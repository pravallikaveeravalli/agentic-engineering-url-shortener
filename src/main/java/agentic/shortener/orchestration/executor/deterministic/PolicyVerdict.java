package agentic.shortener.orchestration.executor.deterministic;

import java.util.Objects;

/**
 * The policy evaluator's answer. Task T071's type; evaluated by T100.
 *
 * @param blocking whether progression is blocked. EC-025's rule lives on the evaluator's side — an unevaluable
 *                 check is never PASS — so it arrives here as blocking rather than as an absent result
 * @param report   what was evaluated and what it found, carried into the artifact or the failure detail
 */
public record PolicyVerdict(boolean blocking, String report) {

    public PolicyVerdict {
        Objects.requireNonNull(report, "report");
        if (report.isBlank()) {
            throw new IllegalArgumentException(
                    "a verdict with no report leaves a blocked run nobody can unblock");
        }
    }
}
