package agentic.shortener.orchestration.executor;

/**
 * The one interface every stage runs behind. Task T069. FR-ORC-028.
 *
 * <p><strong>One abstract method, for all twelve stages.</strong> A second would make this twelve interfaces
 * wearing one name, and {@code StageExecutorContractTest} asserts the count for that reason. Deterministic
 * engines, AI adapters and the test fakes are the same kind of thing from the orchestrator's side — which is
 * what lets every reliability proof in T085–T096 run against injected fakes and still be evidence about the
 * real path (FR-ORC-030).
 *
 * <p><strong>The engine does not know the build.</strong> An implementation sees {@link StageInput} and
 * nothing else: no scenario label, no flag, no caller. FR-ORC-028's negative clause — no executor may branch
 * on recognizing specific demonstration inputs — is enforced structurally by what the input does not contain,
 * and by a source-level check that no executor's string literals name a demonstration scenario.
 *
 * <p>Obligations beyond the signature are in {@code StageExecutorContract}, which every implementation is
 * held to. The one worth repeating here: <strong>a failure is returned, never thrown.</strong>
 */
public interface StageExecutor {

    /**
     * Runs the stage.
     *
     * @return the outcome, never {@code null}. A failure is a {@link StageOutcome#failed} value carrying a
     *         classified envelope, not an exception — see {@code StageExecutorContract}
     */
    StageOutcome execute(StageInput input);
}
