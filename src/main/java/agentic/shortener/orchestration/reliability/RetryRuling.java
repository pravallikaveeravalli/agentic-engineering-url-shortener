package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.StageEffectContracts;

import java.util.Set;

/**
 * {@code retries = declared retryable set ∩ executor proposal}. Task T084. FR-ORC-014 (CL-006).
 *
 * <h2>The executor holds a veto, never a grant</h2>
 *
 * <p>Both asymmetric cases matter and they fail for different reasons:
 *
 * <ul>
 *   <li><strong>Declared yes, executor no.</strong> The stage's set says this category <em>may</em> be
 *       retried in general; the executor says not this one — it timed out after the commit landed, say. The
 *       executor is closer to the effect, and its no is final.
 *   <li><strong>Executor yes, declared no.</strong> The executor proposes a retry the stage's design does not
 *       permit. If this granted one, a declared set would be documentation — and S7 excluding TIMEOUT
 *       because the git effect is non-idempotent (EC-033) would stop meaning anything.
 * </ul>
 *
 * <p><strong>The orchestrator rules because only it can.</strong> The executor diagnoses one failure; the
 * orchestrator holds attempts consumed, compensation already issued, replan invalidation and blocking state.
 * A component that knows only why this attempt failed is not in a position to decide whether another is
 * allowed.
 *
 * <h2>Default-deny falls out of the intersection</h2>
 *
 * <p>{@link FailureCategory#UNKNOWN} is in no stage's declared set, so EC-031 needs no special case here —
 * the intersection is empty and the answer is permanent. That is deliberate: a rule enforced by the shape of
 * the computation cannot be forgotten in a branch somebody adds later.
 */
public final class RetryRuling {

    /**
     * Rules on one failure.
     *
     * @param envelope the classified failure, or {@code null} when the executor gave none — it threw, or
     *                 returned nothing. Ruling on nothing must be possible and must be permanent (EC-032):
     *                 throwing here would turn one stage's bad behaviour into a run that cannot record why
     *                 it stopped
     */
    public Ruling rule(int stageNumber, FailureEnvelope envelope) {
        // Refused rather than ruled permanent. A stage number the contracts do not know is a programming
        // error, and a silent "permanent" would hide it behind behaviour that looks deliberate.
        Set<FailureCategory> declaredSet =
                StageEffectContracts.forStage(stageNumber).retryableCategories();

        if (envelope == null) {
            return new Ruling(stageNumber, FailureCategory.UNKNOWN, false, false, false,
                    "permanent: the executor returned no classified failure, so neither vote exists. "
                            + "An unclassifiable failure is never retried (EC-032). "
                            + "declared=absent, executor=absent");
        }

        boolean declaredRetryable = declaredSet.contains(envelope.category());
        boolean executorProposed = envelope.executorProposesRetryable();
        boolean retry = declaredRetryable && executorProposed;

        return new Ruling(stageNumber, envelope.category(), declaredRetryable, executorProposed, retry,
                reasonFor(stageNumber, envelope.category(), declaredSet, declaredRetryable,
                        executorProposed, retry));
    }

    /**
     * Names both votes and which one refused.
     *
     * <p>The declared set is printed in full, because "declared=no" leaves a reader guessing whether the
     * category is excluded on purpose or the declaration is simply missing — and those need opposite fixes.
     */
    private static String reasonFor(int stageNumber, FailureCategory category,
                                    Set<FailureCategory> declaredSet, boolean declaredRetryable,
                                    boolean executorProposed, boolean retry) {
        String votes = "declared=" + (declaredRetryable ? "yes" : "no")
                + " (S" + stageNumber + " declares " + declaredSet + "), "
                + "executor=" + (executorProposed ? "yes" : "no");

        if (retry) {
            return "retry: both votes agree that " + category + " is transient here. " + votes;
        }
        if (declaredRetryable) {
            return "permanent: the executor vetoed a retry the stage's declared set would allow. " + votes;
        }
        if (executorProposed) {
            return "permanent: the executor proposed a retry the stage's declared set does not permit; "
                    + "an executor holds a veto, never a grant. " + votes;
        }
        return "permanent: neither vote supports a retry of " + category + ". " + votes;
    }
}
