package agentic.shortener.orchestration.executor;

import agentic.shortener.orchestration.reliability.FailureCategory;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * What a stage declares about its own effect, at design time. Task T070.
 * FR-ORC-014 rule 4, FR-ORC-016 rule 3.
 *
 * <p><strong>Design-time, never executor self-certified.</strong> Nothing a running executor returns can
 * reach these fields — {@link StageOutcome} has no component that could carry them, which
 * {@code StageEffectContractTest} asserts as a closed list. An executor certifying its own effect idempotent
 * is how a non-idempotent effect gets applied twice: the party with the incentive to say "safe to retry" is
 * the party whose work failed.
 *
 * <h2>Two validations that fire at LOAD time</h2>
 *
 * <ul>
 *   <li><strong>EC-034.</strong> An irreversible effect with no named compensating action fails to load.
 *       Deliberately at load rather than at the moment of failure: a contract that only breaks when something
 *       goes wrong is discovered during an incident, which is the worst possible time to learn that the
 *       recovery path was never named.
 *   <li><strong>EC-035.</strong> A declaration contradicting the register is <em>flagged</em>, not refused
 *       and not trusted. Refusing it would make a stage unable to raise a disagreement at all; trusting it
 *       would let a stage talk its way out of the register. So it loads, carries
 *       {@link #reviewFlag()}, and {@link #effectivelyReversible()} keeps the structural answer until a human
 *       rules otherwise.
 * </ul>
 *
 * @param retryableCategories   the declared operand of the two-vote rule, enumerated per node in plan §3
 *                              (CR-022). An unspecified set would make the intersection empty under
 *                              default-deny, leaving retry nominally present and actually dead
 * @param declaredReversibility the stage's own claim, or {@code null} to take the register's answer
 */
public record StageEffectContract(int stageNumber, Set<FailureCategory> retryableCategories,
                                  boolean effectIdempotent, EffectClass effectClass,
                                  String compensatingAction,
                                  DeclaredReversibility declaredReversibility) {

    /** Plan §6: the declared operand is drawn from these three and nothing else. */
    private static final Set<FailureCategory> EVER_RETRYABLE = EnumSet.of(
            FailureCategory.TIMEOUT, FailureCategory.UNAVAILABLE, FailureCategory.RATE_LIMITED);

    public StageEffectContract {
        Objects.requireNonNull(effectClass, "effectClass");
        Objects.requireNonNull(retryableCategories, "retryableCategories");
        if (stageNumber < 1 || stageNumber > 12) {
            throw new IllegalArgumentException("stage number must be 1..12, got " + stageNumber);
        }
        retryableCategories = Set.copyOf(retryableCategories);

        Set<FailureCategory> permanent = EnumSet.noneOf(FailureCategory.class);
        permanent.addAll(retryableCategories);
        permanent.removeAll(EVER_RETRYABLE);
        if (!permanent.isEmpty()) {
            throw new IllegalArgumentException(
                    "S" + stageNumber + " declares " + permanent + " retryable. INVALID_INPUT, INTERNAL "
                            + "and UNKNOWN retry nowhere: retrying identical input cannot change the "
                            + "answer, and retrying our own defect cannot either (plan §6)");
        }

        if (retryableCategories.contains(FailureCategory.TIMEOUT) && !effectIdempotent) {
            throw new IllegalArgumentException(
                    "S" + stageNumber + " retries TIMEOUT without declaring its effect idempotent. A "
                            + "timed-out non-idempotent effect may already have happened, so retrying "
                            + "applies it twice (FR-ORC-014 rule 4, EC-033)");
        }

        // EC-034. Neither an erasable effect (rollback IS the action) nor one with nothing to compensate
        // needs a named action; everything else does, and a blank string names nothing.
        boolean actionRequired = !effectClass.reversible() && !effectClass.nothingToCompensate();
        if (actionRequired && (compensatingAction == null || compensatingAction.isBlank())) {
            throw new IllegalArgumentException(
                    "S" + stageNumber + " declares the irreversible effect " + effectClass
                            + " without naming a compensating action; it must fail to load "
                            + "(EC-034, FR-ORC-016 rule 3)");
        }
    }

    /** The common form: no declared override, so the register's answer stands. */
    public StageEffectContract(int stageNumber, Set<FailureCategory> retryableCategories,
                               boolean effectIdempotent, EffectClass effectClass,
                               String compensatingAction) {
        this(stageNumber, retryableCategories, effectIdempotent, effectClass, compensatingAction, null);
    }

    /** EC-035: the stage's claim disagrees with the register's structural answer. */
    public boolean contradictsStructuralRule() {
        if (declaredReversibility == null) {
            return false;
        }
        boolean claimsErasable = declaredReversibility == DeclaredReversibility.ERASABLE;
        return claimsErasable != effectClass.reversible();
    }

    /**
     * What a human has to adjudicate, or {@code null} when there is nothing to adjudicate.
     *
     * <p>Names the effect class, both positions and the basis, because a flag saying only "review this"
     * sends the reader back to the register to work out what the disagreement was.
     */
    public String reviewFlag() {
        if (!contradictsStructuralRule()) {
            return null;
        }
        return "S" + stageNumber + " declares its effect " + declaredReversibility + " but the Compensation "
                + "Register places " + effectClass + " as "
                + (effectClass.reversible() ? "ERASABLE" : "COMPENSATE_ONLY")
                + ". EC-035: this must be flagged for human review, not trusted. The structural answer "
                + "stands until a human rules otherwise.";
    }

    /**
     * What the run acts on.
     *
     * <p>The register wins while a contradiction is outstanding. That is EC-035's whole content: a stage that
     * could override the register by declaring differently would make the register advisory, and the one
     * declaration you would most want reviewed is the one claiming an immutable record can be erased.
     */
    public boolean effectivelyReversible() {
        if (contradictsStructuralRule()) {
            return effectClass.reversible();
        }
        return declaredReversibility == null
                ? effectClass.reversible()
                : declaredReversibility == DeclaredReversibility.ERASABLE;
    }
}
