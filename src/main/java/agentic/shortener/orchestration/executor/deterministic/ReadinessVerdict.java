package agentic.shortener.orchestration.executor.deterministic;

import java.util.Objects;

/**
 * The release-readiness evaluator's answer. Task T071's type; evaluated by T109.
 *
 * <p>{@code ready} is emphatically not "approved". The nine blocking conditions being met is what makes a
 * release decision <em>possible</em>; the decision is the release owner's, and it is recorded (FR-ORC-013).
 */
public record ReadinessVerdict(boolean ready, String report) {

    public ReadinessVerdict {
        Objects.requireNonNull(report, "report");
        if (report.isBlank()) {
            throw new IllegalArgumentException("a readiness verdict must say what it evaluated");
        }
    }
}
