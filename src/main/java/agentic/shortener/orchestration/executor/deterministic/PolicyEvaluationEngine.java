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
 * S10 — security and policy checks. Task T071. FR-ORC-029.
 *
 * <p>Deterministic because <strong>policy verdicts must be repeatable</strong>. This is the stage the spec
 * uses as the counter-example to S3's acceptable variability: a false positive at S3 costs a question, while a
 * verdict that changed between runs would mean a change could be blocked on Monday and cleared on Tuesday with
 * nothing having happened in between.
 *
 * <p>The evaluation of {@code policy-set-1.1.0} itself is T100's, behind {@link PolicyEvaluator}. What this
 * engine owns is the part that is genuinely the executor's: handing the evaluator the run's artifacts, and
 * turning its verdict into an outcome the orchestrator can act on.
 *
 * <p><strong>A blocking verdict fails the stage and keeps the record.</strong> The verdict's content travels
 * in the failure detail, because a blocked run with no record of what blocked it is a run nobody can unblock.
 * Not retryable: re-evaluating the same evidence gives the same answer, which is what "repeatable" means.
 */
public final class PolicyEvaluationEngine implements StageExecutor {

    static final String OUTPUT_KEY = "policy-results";

    private final PolicyEvaluator evaluator;

    public PolicyEvaluationEngine(PolicyEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public StageOutcome execute(StageInput input) {
        PolicyVerdict verdict;
        try {
            verdict = evaluator.evaluate(input.inputArtifacts());
        } catch (Exception e) {
            // EC-025's shape: unevaluable is never PASS. The translation gives UNAVAILABLE or UNKNOWN, and
            // either way the stage failed — which is the safe direction.
            return StageOutcome.failed(ProviderFailureTranslator.forStoreProvider().translate(e));
        }

        if (verdict.blocking()) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "policy evaluation blocks progression: " + verdict.report(), false));
        }

        return StageOutcome.succeeded(
                List.of(new ProducedArtifact(OUTPUT_KEY, verdict.report(),
                        input.inputArtifacts().keySet().stream().sorted().toList())),
                ExecutorKind.DETERMINISTIC);
    }
}
