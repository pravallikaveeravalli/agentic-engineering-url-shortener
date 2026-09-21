package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;

import java.util.Objects;
import java.util.UUID;

/**
 * The no-change-plan gate, with exactly three options. Task T065. FR-ORC-031 (CR-001). EC-040.
 *
 * <p>Deterministic S7 reached with no change plan for the requirement offers three options and no fourth:
 * {@link #governanceOnly}, {@link #humanImplemented}, {@link #abandon}. Every one requires a reason naming
 * who decided and why — <strong>a labelled no-op that flows onward is quiet pretending</strong>; proceeding
 * with nothing implemented requires a recorded human decision, and this class is that decision point.
 *
 * <p>There is no fourth method, and there is no configuration that selects one of these automatically:
 * {@code NoPlanGateIT} asserts the public method count as a closed set, the same shape used throughout this
 * project for a closed vocabulary (six failure categories, one join semantic, ten gate classes).
 */
public final class NoPlanGate {

    private final JdbcRunStore runStore;

    public NoPlanGate(JdbcRunStore runStore) {
        this.runStore = Objects.requireNonNull(runStore, "runStore");
    }

    /**
     * Option 1: governance-only. Downstream continues — {@link StageState#SKIPPED} satisfies a join
     * (T078) — and the implementation is labelled intentionally skipped by human decision, never left to
     * look like it silently never happened.
     */
    public void governanceOnly(UUID runId, String nodeKey, String actor, String reason) {
        requireReason(reason);
        StageState from = currentStateOf(runId, nodeKey);
        runStore.transitionNode(runId, nodeKey, from, StageState.SKIPPED,
                "intentionally skipped by human decision (" + actor + "): " + reason);
    }

    /**
     * Option 2: human-implemented. {@code SUCCEEDED}, not {@code SKIPPED} — real testing (S8) judges what
     * was implemented, exactly as it would judge an AI-authored change. {@code executorKindUsed} is
     * recorded as {@link ExecutorKind#HUMAN}, the one path in this codebase that may ever write it.
     */
    public void humanImplemented(UUID runId, String nodeKey, String actor, String reason) {
        requireReason(reason);
        StageState from = currentStateOf(runId, nodeKey);
        runStore.transitionNode(runId, nodeKey, from, StageState.SUCCEEDED,
                "human-implemented (" + actor + "): " + reason);
        runStore.recordExecutorKind(runId, nodeKey, ExecutorKind.HUMAN);
    }

    /**
     * Option 3: abandon. The run terminates as {@link RunState#ABANDONED} — a human decision, distinct
     * from {@code RetentionPolicy}'s idle-driven abandonment (T092): same terminal state, different
     * actor, different reason, reached through a different call entirely. Not routed through
     * {@code SafeStopHandler}: that class owns the {@code SAFE_STOP} boundary specifically, and this
     * transition never touches {@code SAFE_STOP} at all — it goes directly from {@code RUNNING} to the
     * terminal state, which {@link JdbcRunStore#transitionRun} permits without restriction beyond its own
     * SAFE_STOP-crossing guard.
     */
    public void abandon(UUID runId, String actor, String reason) {
        requireReason(reason);
        runStore.transitionRun(runId, RunState.RUNNING, RunState.ABANDONED,
                "no-change-plan gate, abandoned by human decision (" + actor + "): " + reason);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "a reason is required: proceeding with nothing implemented requires a recorded human "
                            + "decision, not a blank one");
        }
    }

    private StageState currentStateOf(UUID runId, String nodeKey) {
        return runStore.node(runId, nodeKey)
                .orElseThrow(() -> new IllegalStateException("no such node: " + nodeKey))
                .state();
    }
}
