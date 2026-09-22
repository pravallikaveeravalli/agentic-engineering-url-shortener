package agentic.shortener.orchestration.executor.deterministic;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;
import agentic.shortener.orchestration.reliability.ProviderFailureTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Objects;

/**
 * S8 — testing. Task T071. FR-ORC-029.
 *
 * <p>Deterministic, and <strong>real</strong>: it executes the actual suite through {@link TestSuiteRunner}
 * and reports what came back. There is no mode in which it reports a result it did not get.
 *
 * <h2>Two different failures that must not be confused</h2>
 *
 * <p>This engine's real work is telling them apart, and the distinction decides whether the run retries:
 *
 * <ul>
 *   <li><strong>The suite ran and tests failed.</strong> {@code INVALID_INPUT}, permanent. What was offered
 *       is not acceptable, and re-running the same branch cannot change the answer. Not {@code INTERNAL}:
 *       the defect is in the change under test, not in us. Plan §3 routes it back to S7 within the bound.
 *   <li><strong>The suite could not run.</strong> {@code UNAVAILABLE}, retryable — and S8's declared set is
 *       exactly {@code {UNAVAILABLE}}, so this is the one failure here that is allowed a second attempt.
 * </ul>
 *
 * <p>Collapsing the two would either retry a genuine test failure — spending the bound to fail identically —
 * or refuse to retry a missing toolchain, which is a transient condition nobody would call permanent.
 *
 * <p><strong>The stage fails rather than succeeding with bad news.</strong> A stage that reported SUCCEEDED
 * while carrying a failing report would let S9 document, and S10 clear, a change the suite rejected.
 */
public final class TestingEngine implements StageExecutor {

    static final String INPUT_KEY = "branch";
    static final String OUTPUT_KEY = "test-results";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final TestSuiteRunner runner;

    public TestingEngine(TestSuiteRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @Override
    public StageOutcome execute(StageInput input) {
        String branch = input.inputArtifacts().get(INPUT_KEY);
        if (branch == null || branch.isBlank()) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "no '" + INPUT_KEY + "' artifact naming what to test", false));
        }

        TestSuiteReport report;
        try {
            report = runner.run(branch);
        } catch (Exception e) {
            // Translated rather than classified here: the vendor taxonomy lives in one place (T083), and an
            // engine that recognised its runner's exception types would be the first leak.
            return StageOutcome.failed(ProviderFailureTranslator.forSubprocessProvider().translate(e));
        }

        if (report.failed() > 0) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "the suite ran and rejected the change: " + report.failed() + " failures of "
                            + report.total() + ". " + report.report(),
                    false));
        }

        return StageOutcome.succeeded(
                List.of(new ProducedArtifact(OUTPUT_KEY, toJson(report), List.of(INPUT_KEY))),
                ExecutorKind.DETERMINISTIC);
    }

    /** CR-065: {@code "report"} keeps the existing human-readable summary verbatim — added alongside, never
     * renamed or removed, for any other consumer already reading it — and {@code "executedBehaviors"} is
     * the real, per-test list {@link agentic.shortener.orchestration.executor.ai.stages.
     * DocumentationAiExecutor}'s own drift guard (S9) requires. */
    private static String toJson(TestSuiteReport report) {
        ObjectNode root = JSON.createObjectNode();
        root.put("total", report.total());
        root.put("failed", report.failed());
        root.put("report", report.report());
        ArrayNode behaviors = root.putArray("executedBehaviors");
        report.executedBehaviors().forEach(behaviors::add);
        return root.toString();
    }
}
