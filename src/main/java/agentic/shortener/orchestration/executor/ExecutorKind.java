package agentic.shortener.orchestration.executor;

/**
 * What actually ran a stage. Tasks T069, T066. FR-ORC-029, FR-ORC-031.
 *
 * <p>Distinct from {@code ExecutorClass}, which is the <em>design-time</em> declaration and deliberately has
 * no {@code HUMAN} value — confirmed by the owner, because adding one would let a stage be declared
 * human-implemented up front. This enum is what the run records about an execution that happened.
 *
 * <p>Matches {@code contracts/workflow-state.schema.json}'s {@code executorKindUsed} exactly.
 */
public enum ExecutorKind {

    /** One of the five real deterministic engines — S1, S8, S10, S11, S12 (ADR-004-A2). */
    DETERMINISTIC,

    /** An AI-capable stage. Recorded with the model id actually used, read from the provider response. */
    AI,

    /**
     * A human implemented the stage.
     *
     * <p><strong>Never selectable by any caller or configuration.</strong> The only thing that produces it is
     * a no-plan-gate decision (FR-ORC-031, T065), and {@link StageOutcome#succeeded} refuses it — so an
     * executor cannot claim it even by accident. T066 proves that end to end.
     */
    HUMAN
}
