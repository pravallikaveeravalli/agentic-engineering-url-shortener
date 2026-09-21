package agentic.shortener.orchestration.executor;

import agentic.shortener.orchestration.reliability.FailureCategory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The twelve loaded stage contracts. Task T070. FR-ORC-014 (CR-022).
 *
 * <p>Each declared retryable set below is the one enumerated in <strong>plan §3's {@code Declared retryable
 * set} column</strong>, and {@code StageEffectContractTest} reads that column out of {@code plan.md} and
 * compares. The test could have listed the same twelve sets; it would then have agreed with this file
 * whatever the plan said, which is the failure mode the artifact-coverage sweep exists to catch — the author
 * of the declaration also writing its check.
 *
 * <p>Loaded eagerly in a static initializer, so EC-034 fires at class-load rather than on first use: a
 * contract that only breaks when something goes wrong is discovered during an incident.
 */
public final class StageEffectContracts {

    private static final Set<FailureCategory> AI_STAGE_SET = Set.of(
            FailureCategory.TIMEOUT, FailureCategory.UNAVAILABLE, FailureCategory.RATE_LIMITED);

    private static final List<StageEffectContract> CONTRACTS = List.of(

            // S1 Ingestion. {UNAVAILABLE}: the store may be briefly unreachable, but creating a run twice
            // would produce two runs for one submission, so TIMEOUT is excluded.
            new StageEffectContract(1, Set.of(FailureCategory.UNAVAILABLE), false,
                    EffectClass.AUDIT_RECORD, "compensate — append a corrective entry"),

            // S2, S3, S5, S6, S9 — the AI-capable stages with idempotent effects. Asking a provider the
            // same question again changes no external state, which is what makes TIMEOUT retryable here.
            aiStage(2), aiStage(3), aiStage(5), aiStage(6), aiStage(9),

            // S4 Human clarification. The empty set is a real declaration, not a missing one: there is no
            // executor, and the stage's "timeout" is the gate wait, which produces SAFE_STOP rather than a
            // retry. A gate decision is the effect, and it is corrected forward by superseding.
            new StageEffectContract(4, Set.of(), false, EffectClass.RECORDED_GATE_DECISION,
                    "compensate — write a superseding record referencing the prior one"),

            // S7 Implementation. TIMEOUT excluded (EC-033): the git effect is non-idempotent, and a timed-out
            // apply may already have committed. Erasable only because nothing is pushed (CL-004).
            new StageEffectContract(7, Set.of(FailureCategory.UNAVAILABLE, FailureCategory.RATE_LIMITED),
                    false, EffectClass.LOCAL_BRANCH_COMMIT, null),

            // S8 Testing. Repeat-safe, yet TIMEOUT is excluded deliberately: at a 1800 s threshold a timeout
            // means something is wrong rather than slow, so a retry spends another half hour failing the
            // same way. Idempotent AND not retrying TIMEOUT is a coherent position, which is why the
            // relationship between the two is an implication and not an equivalence.
            new StageEffectContract(8, Set.of(FailureCategory.UNAVAILABLE), true,
                    EffectClass.WORKING_TREE_WRITE, null),

            // S10 Security and policy. Widened to RATE_LIMITED: the dependency-vulnerability scan calls an
            // external service that throttles. Verdicts must be repeatable, so nothing else is retried.
            new StageEffectContract(10, Set.of(FailureCategory.UNAVAILABLE, FailureCategory.RATE_LIMITED),
                    true, EffectClass.AUDIT_RECORD, "compensate — append a corrective entry"),

            // S11 Release readiness. Deterministic evaluation, then a human decision.
            new StageEffectContract(11, Set.of(FailureCategory.UNAVAILABLE), true,
                    EffectClass.RECORDED_GATE_DECISION,
                    "compensate — write a superseding record referencing the prior one"),

            // S12 Final summary. Assembled from recorded evidence only, so re-assembling is repeat-safe.
            new StageEffectContract(12, Set.of(FailureCategory.TIMEOUT, FailureCategory.UNAVAILABLE), true,
                    EffectClass.WORKING_TREE_WRITE, null));

    private static final Map<Integer, StageEffectContract> BY_STAGE = CONTRACTS.stream()
            .collect(Collectors.toUnmodifiableMap(StageEffectContract::stageNumber, Function.identity()));

    private StageEffectContracts() {
    }

    /** The five AI-capable stages share one declared set; plan §3 lists the same three for each. */
    private static StageEffectContract aiStage(int stageNumber) {
        return new StageEffectContract(stageNumber, AI_STAGE_SET, true,
                EffectClass.AI_PROVIDER_INVOCATION, null);
    }

    public static List<StageEffectContract> all() {
        return CONTRACTS;
    }

    public static StageEffectContract forStage(int stageNumber) {
        StageEffectContract contract = BY_STAGE.get(stageNumber);
        if (contract == null) {
            throw new IllegalArgumentException(
                    "no effect contract for stage " + stageNumber + ". A stage with no declared retryable "
                            + "set has an empty intersection under default-deny, so retry would be "
                            + "nominally present and actually dead (CR-022)");
        }
        return contract;
    }
}
