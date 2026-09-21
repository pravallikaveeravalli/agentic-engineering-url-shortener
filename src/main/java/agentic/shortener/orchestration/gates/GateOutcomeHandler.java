package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.graph.DependencyEdge;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.orchestration.store.PersistedNode;
import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The four submittable outcomes' workflow effects. Task T059. FR-ORC-013. Plan §5.
 *
 * <p>{@code TIMED_OUT}'s effect — suspension — is deliberately absent from this class. T058 made
 * {@code TIMED_OUT} unconstructible as a {@link GateOutcome}, so there is no value this handler could ever
 * receive for it; that effect happens in {@code SafeStopHandler} instead, proven there.
 *
 * <h2>{@code CHANGES_REQUESTED}'s downstream invalidation, and why it is a graph walk</h2>
 *
 * <p>"Downstream" means every node reachable by following {@code dependency_edge}s forward from the
 * decided-on node — not merely its immediate successors. A breadth-first walk over an explicit visited
 * set, the same shape {@code ArtifactProvenanceQuery} already uses for its chain walk, and for the same
 * reason: the graph is not guaranteed acyclic by <em>this</em> class's own logic (the store's
 * {@code CycleDetector} guards that elsewhere), so a walk that assumes acyclicity is a walk that could
 * hang.
 *
 * <p>Each already-{@code INVALIDATED} node is skipped rather than re-invalidated — idempotent, and it
 * keeps the compare-and-set transition from failing on a node a wider blast radius already reached.
 */
public final class GateOutcomeHandler {

    private final JdbcRunStore runStore;
    private final GateStore gateStore;
    private final ConnectionSource connections;

    /**
     * @param connections the same connection source {@code runStore} and {@code gateStore} were built
     *                    from. Needed directly for the one query neither store exposes: finding which
     *                    still-standing decisions to void when a downstream node is invalidated
     */
    public GateOutcomeHandler(JdbcRunStore runStore, GateStore gateStore, ConnectionSource connections) {
        this.runStore = Objects.requireNonNull(runStore, "runStore");
        this.gateStore = Objects.requireNonNull(gateStore, "gateStore");
        this.connections = Objects.requireNonNull(connections, "connections");
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

        for (String downstreamNodeKey : downstreamOf(runId, nodeKey)) {
            invalidate(runId, downstreamNodeKey);
        }
    }

    private void invalidate(UUID runId, String nodeKey) {
        StageState from = currentStateOf(runId, nodeKey);
        if (from == StageState.INVALIDATED) {
            return; // already invalidated by a wider blast radius; nothing to redo
        }
        runStore.transitionNode(runId, nodeKey, from, StageState.INVALIDATED,
                "invalidated by an upstream CHANGES_REQUESTED decision");
        voidApprovalIfAny(runId, nodeKey);
    }

    /**
     * EC-020: void by superseding, not by deletion or edit. Every {@code gate_decision} for this node
     * that is not itself already a superseding record gets a superseding {@code REJECTED} row written
     * against it — the original stays exactly as it was.
     */
    private void voidApprovalIfAny(UUID runId, String nodeKey) {
        List<Long> toSupersede = new ArrayList<>();
        try (Connection c = connections.get();
             var ps = c.prepareStatement(
                     "SELECT gd.gate_decision_id, gd.gate_class, gd.actor_name FROM gate_decision gd "
                             + "WHERE gd.run_id = ? AND gd.gate_id = ? "
                             + "AND gd.gate_decision_id NOT IN ("
                             + "  SELECT supersedes_gate_decision_id FROM gate_decision "
                             + "  WHERE supersedes_gate_decision_id IS NOT NULL)")) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    toSupersede.add(rs.getLong("gate_decision_id"));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to find approvals to void for " + nodeKey, e);
        }

        for (Long decisionId : toSupersede) {
            supersede(runId, nodeKey, decisionId);
        }
    }

    private void supersede(UUID runId, String nodeKey, long decisionId) {
        GateDecision voided = gateStore.decisionById(decisionId).orElseThrow();
        GateDecision superseding = new GateDecision(runId, nodeKey, voided.stageNumber(),
                voided.gateClass(), GateOutcome.REJECTED, new Actor("human", voided.actor().identity()),
                voided.decidedAt(), "voided by a replan: the stage it approved was invalidated by a "
                        + "downstream CHANGES_REQUESTED decision", voided.repositoryRecordPath(),
                List.of(), null, decisionId);
        gateStore.recordDecision(superseding);
    }

    /**
     * Every node reachable by following edges forward from {@code fromNodeKey}, itself excluded.
     * Breadth-first with an explicit visited set, so a cycle elsewhere cannot make this hang.
     */
    private Set<String> downstreamOf(UUID runId, String fromNodeKey) {
        List<DependencyEdge> edges = runStore.edges(runId);
        Map<String, List<String>> outgoing = edges.stream()
                .collect(Collectors.groupingBy(DependencyEdge::fromNodeKey,
                        Collectors.mapping(DependencyEdge::toNodeKey, Collectors.toList())));

        Set<String> visited = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(outgoing.getOrDefault(fromNodeKey, List.of()));
        visited.addAll(pending);

        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            for (String next : outgoing.getOrDefault(current, List.of())) {
                if (visited.add(next)) {
                    pending.addLast(next);
                }
            }
        }
        return visited;
    }

    private StageState currentStateOf(UUID runId, String nodeKey) {
        PersistedNode node = runStore.node(runId, nodeKey).orElseThrow(
                () -> new IllegalStateException("no such node: " + nodeKey));
        return node.state();
    }
}
