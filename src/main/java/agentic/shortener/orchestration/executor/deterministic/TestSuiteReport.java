package agentic.shortener.orchestration.executor.deterministic;

import java.util.Objects;

/**
 * What the real suite reported. Task T071.
 *
 * @param total  tests executed
 * @param failed tests that failed. A count rather than a boolean, because the report routed back to S7 has to
 *               say how much is broken, not merely that something is
 * @param report the human-readable output, carried verbatim
 */
public record TestSuiteReport(int total, int failed, String report) {

    public TestSuiteReport {
        Objects.requireNonNull(report, "report");
        if (total < 0 || failed < 0 || failed > total) {
            throw new IllegalArgumentException("a suite cannot fail " + failed + " of " + total + " tests");
        }
    }
}
