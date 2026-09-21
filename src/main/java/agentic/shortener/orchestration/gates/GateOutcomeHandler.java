package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.replan.DownstreamInvalidation;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.orchestration.store.PersistedNode;
import agentic.shortener.persistence.ConnectionSource;

import java.util.Objects;
import java.util.UUID;

/**
 * The four submittable outcomes' workflow effects. Task T059. FR-ORC-013. Plan §5.
 *
 * <p>{@code TIMED_OUT}'s effect — suspension — is deliberately absent from this class. T058 made
 * {@code TIMED_OUT} unconstructible as a {@link GateOutcome}, so there is no value this handler could ever
 * receive for it; that effect happens in {@code SafeStopHandler} instead, proven there.
 *
 * <h2>{@code CHANGES_REQUESTED}'s downstream invalidation is {@link DownstreamInvalidation} (T096)</h2>
 *
 * <p>Originally this class's own private methods; extracted when T096 needed the identical mechanism for
 * an artifact-change-triggered replan — a gate-triggered replan and an artifact-triggered one invalidate
 * downstream work the same way. Extracting it also fixed a real gap: the original methods applied
 * {@code RUNNING -> INVALIDATED} unconditionally, because no test here had ever driven a downstream node
 * into {@code RUNNING} before requesting changes upstream of it. {@link DownstreamInvalidation} itself now
 * respects EC-019 (an in-flight stage is not mutated mid-execution) — see its own javadoc — which this
 * class inherits for free rather than having to fix twice.
 */
public final class GateOutcomeHandler {

    private final JdbcRunStore runStore;
    private final DownstreamInvalidation invalidation;

    public GateOutcomeHandler(JdbcRunStore runStore, GateStore gateStore, ConnectionSource connections) {
        this.runStore = Objects.requireNonNull(runStore, "runStore");
        Objects.requireNonNull(gateStore, "gateStore");
        Objects.requireNonNull(connections, "connections");
        this.invalidation = new DownstreamInvalidation(runStore, gateStore, connections);
    }

    public void apply(UUID runId, String nodeKey, GateDecision decision) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(decision, "decision");

        switch (decision.outcome()) {
            case APPROVED -> approve(runId, nodeKey);
            case REJECTED -> reject(runId, decision.reason());
            case CHANGES_REQUESTED -> requestChanges(runId, nodeKey);
            case ESCALATED -> {
                // Nothing to do beyond what the caller already did: the decision itself is the effect.
                // Plan §5: "records what exceeds authority" — a governance record for someone with more
                // authority to act on, never itself an automatic transition.
            }
        }
    }

    /** The stage succeeds. */
    private void approve(UUID runId, String nodeKey) {
        StageState from = currentStateOf(runId, nodeKey);
        // TransitionRules requires a recorded gate decision for AWAITING_APPROVAL -> SUCCEEDED, which
        // JdbcRunStore.transitionNode supplies automatically for any transition INTO SUCCEEDED — this
        // call IS that recorded decision, made moments earlier by GateStore.recordDecision.
        runStore.transitionNode(runId, nodeKey, from, StageState.SUCCEEDED, "gate APPROVED");
    }

    /** The run terminates. Plan §5 names only the run here, not the gated node's own state. */
    private void reject(UUID runId, String reason) {
        runStore.transitionRun(runId, RunState.RUNNING, RunState.REJECTED,
                "gate REJECTED: " + reason);
    }

    /**
     * Returns the owning stage to READY, and invalidates everything downstream — voiding any approval
     * attached to an invalidated stage by writing a superseding gate record (EC-020), never editing or
     * deleting the original.
     */
    private void requestChanges(UUID runId, String nodeKey) {
        StageState from = currentStateOf(runId, nodeKey);
        runStore.transitionNode(runId, nodeKey, from, StageState.READY,
                "gate CHANGES_REQUESTED: returned to the owning stage");

        invalidation.invalidateDownstreamOf(runId, nodeKey,
                "invalidated by an upstream CHANGES_REQUESTED decision");
    }

    private StageState currentStateOf(UUID runId, String nodeKey) {
        PersistedNode node = runStore.node(runId, nodeKey).orElseThrow(
                () -> new IllegalStateException("no such node: " + nodeKey));
        return node.state();
    }
}
