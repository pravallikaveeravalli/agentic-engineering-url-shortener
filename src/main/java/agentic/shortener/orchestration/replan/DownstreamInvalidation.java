package agentic.shortener.orchestration.replan;

import agentic.shortener.orchestration.gates.Actor;
import agentic.shortener.orchestration.gates.GateDecision;
import agentic.shortener.orchestration.gates.GateOutcome;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.DependencyEdge;
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
 * The downstream-invalidation-and-approval-voiding mechanism, shared by {@code GateOutcomeHandler}'s
 * {@code CHANGES_REQUESTED} path (T059) and {@link ReplanService} (T096) — extracted here rather than
 * duplicated, because a replan triggered by a gate decision and one triggered by an upstream artifact
 * change invalidate downstream work the SAME way: a breadth-first walk of {@code dependency_edge}, each
 * reached node moved to {@code INVALIDATED}, and any still-standing approval on it voided by a superseding
 * {@code gate_decision} row (EC-020) — never a delete or an edit of the original.
 *
 * <h2>EC-019: an in-flight stage is not mutated mid-execution</h2>
 *
 * <p>A downstream node found {@code RUNNING} is reported, never invalidated. Force-transitioning it would
 * corrupt an execution genuinely in progress; plan §3's own words are "it completes or is cancelled at its
 * next checkpoint" — a later concern for whatever calls the transition when that execution actually ends,
 * not something this class can honestly do on its behalf.
 *
 * <p><strong>This class was extracted from {@code GateOutcomeHandler}</strong>, which is why fixing this
 * EC-019 gap here also fixes it there: the original private methods this replaces applied
 * {@code RUNNING -> INVALIDATED} unconditionally, because no test had yet driven a downstream node into
 * {@code RUNNING} before triggering {@code CHANGES_REQUESTED}. Found while building T096, which names
 * EC-019 as one of its own required assertions — the same property {@code GateOutcomeHandler}'s own
 * Validate clause never separately named.
 */
public final class DownstreamInvalidation {

    private final JdbcRunStore runStore;
    private final GateStore gateStore;
    private final ConnectionSource connections;

    public DownstreamInvalidation(JdbcRunStore runStore, GateStore gateStore, ConnectionSource connections) {
        this.runStore = Objects.requireNonNull(runStore, "runStore");
        this.gateStore = Objects.requireNonNull(gateStore, "gateStore");
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /**
     * @param invalidatedNodes node keys actually transitioned to {@code INVALIDATED}
     * @param inFlightSkipped  node keys found {@code RUNNING} and left untouched (EC-019)
     * @param voidedDecisionIds the {@code gate_decision_id}s superseded because their node was invalidated
     *                          (EC-020)
     */
    public record Result(List<String> invalidatedNodes, List<String> inFlightSkipped,
                         List<Long> voidedDecisionIds) {

        public Result {
            invalidatedNodes = List.copyOf(invalidatedNodes);
            inFlightSkipped = List.copyOf(inFlightSkipped);
            voidedDecisionIds = List.copyOf(voidedDecisionIds);
        }
    }

    /**
     * Invalidates every node reachable by following {@code dependency_edge}s forward from
     * {@code fromNodeKey}, itself excluded, and voids any approval attached to one just invalidated.
     *
     * @param reason recorded on every {@code state_transition} row this produces — the caller's own words
     *               for why (a CHANGES_REQUESTED decision, an artifact change, a replanned requirement)
     */
    public Result invalidateDownstreamOf(UUID runId, String fromNodeKey, String reason) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(fromNodeKey, "fromNodeKey");
        Objects.requireNonNull(reason, "reason");

        List<String> invalidated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<Long> voided = new ArrayList<>();

        for (String nodeKey : downstreamOf(runId, fromNodeKey)) {
            StageState from = currentStateOf(runId, nodeKey);
            if (from == StageState.RUNNING) {
                // EC-019: not mutated mid-execution.
                skipped.add(nodeKey);
                continue;
            }
            if (from == StageState.INVALIDATED) {
                // Idempotent: already reached by a wider blast radius.
                continue;
            }
            runStore.transitionNode(runId, nodeKey, from, StageState.INVALIDATED, reason);
            invalidated.add(nodeKey);
            voided.addAll(voidApprovalIfAny(runId, nodeKey));
        }

        return new Result(invalidated, skipped, voided);
    }

    /**
     * EC-020: void by superseding, not by deletion or edit. Every still-standing {@code gate_decision} for
     * this node (one not itself already a superseding record) gets a superseding {@code REJECTED} row.
     *
     * @return the {@code gate_decision_id}s voided
     */
    private List<Long> voidApprovalIfAny(UUID runId, String nodeKey) {
        List<Long> toSupersede = new ArrayList<>();
        try (Connection c = connections.get();
             var ps = c.prepareStatement(
                     "SELECT gd.gate_decision_id FROM gate_decision gd "
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
        return toSupersede;
    }

    private void supersede(UUID runId, String nodeKey, long decisionId) {
        GateDecision voided = gateStore.decisionById(decisionId).orElseThrow();
        GateDecision superseding = new GateDecision(runId, nodeKey, voided.stageNumber(),
                voided.gateClass(), GateOutcome.REJECTED, new Actor("human", voided.actor().identity()),
                voided.decidedAt(), "voided by a replan: the stage it approved was invalidated by a "
                        + "downstream change", voided.repositoryRecordPath(), List.of(), null, decisionId);
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
