package agentic.shortener.orchestration.executor.deterministic;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;
import agentic.shortener.orchestration.reliability.ProviderFailureTranslator;

import java.util.List;
import java.util.Objects;

/**
 * S11 — release-readiness evaluation. Task T071. FR-ORC-029.
 *
 * <p><strong>The evaluation is not the decision.</strong> Plan §3 gives S11 two halves: a deterministic
 * evaluation of the nine blocking conditions, and then the release owner's recorded decision. This engine is
 * the first half only, and the artifact it produces says so in as many words — a report that read as a verdict
 * would let a reviewer, or a later stage, treat the gate as already satisfied. Constitution III: silence is
 * never approval, and neither is a green report.
 *
 * <p>The nine conditions themselves are T109's, behind {@link ReadinessEvaluator}.
 */
public final class ReleaseReadinessEngine implements StageExecutor {

    static final String OUTPUT_KEY = "release-readiness";

    /**
     * Appended to every report, ready or not.
     *
     * <p>On the not-ready path it goes in the failure detail, because the one case where somebody might read
     * a readiness report as a decision is the case where it says everything passed — and the sentence that
     * prevents that has to be there in both, or its absence starts to mean something.
     */
    private static final String NOT_A_DECISION =
            "This is an evaluation, not a release decision. Release requires the release owner's "
                    + "recorded human decision (FR-ORC-013).";

    private final ReadinessEvaluator evaluator;

    public ReleaseReadinessEngine(ReadinessEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public StageOutcome execute(StageInput input) {
        ReadinessVerdict verdict;
        try {
            verdict = evaluator.evaluate(input.inputArtifacts());
        } catch (Exception e) {
            return StageOutcome.failed(ProviderFailureTranslator.forStoreProvider().translate(e));
        }

        if (!verdict.ready()) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "release readiness not met: " + verdict.report() + " " + NOT_A_DECISION, false));
        }

        return StageOutcome.succeeded(
                List.of(new ProducedArtifact(OUTPUT_KEY, verdict.report() + "\n\n" + NOT_A_DECISION,
                        input.inputArtifacts().keySet().stream().sorted().toList())),
                ExecutorKind.DETERMINISTIC);
    }
}
