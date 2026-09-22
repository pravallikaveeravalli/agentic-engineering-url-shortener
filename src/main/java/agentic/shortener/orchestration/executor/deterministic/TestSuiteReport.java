package agentic.shortener.orchestration.executor.deterministic;

import java.util.List;
import java.util.Objects;

/**
 * What the real suite reported. Task T071.
 *
 * @param total             tests executed
 * @param failed            tests that failed. A count rather than a boolean, because the report routed back
 *                          to S7 has to say how much is broken, not merely that something is
 * @param report            the human-readable output, carried verbatim
 * @param executedBehaviors CR-065: each test that genuinely ran, named {@code
 *                          "<fully-qualified-class>.<method>"} — the real, per-test record {@code report}'s
 *                          own aggregate counts do not carry. {@link TestingEngine} only ever reaches its own
 *                          succeeded path when {@code failed == 0}, so every entry here genuinely passed.
 *                          {@code DocumentationAiExecutor} (S9) cross-checks its own documentation drift
 *                          guard against exactly this list — never populated before this field existed, since
 *                          no producer had anywhere to put it.
 */
public record TestSuiteReport(int total, int failed, String report, List<String> executedBehaviors) {

    public TestSuiteReport {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(executedBehaviors, "executedBehaviors");
        if (total < 0 || failed < 0 || failed > total) {
            throw new IllegalArgumentException("a suite cannot fail " + failed + " of " + total + " tests");
        }
        executedBehaviors = List.copyOf(executedBehaviors);
    }
}
