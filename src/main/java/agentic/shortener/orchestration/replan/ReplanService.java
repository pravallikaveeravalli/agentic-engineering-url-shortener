package agentic.shortener.orchestration.replan;

import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.CycleDetector;
import agentic.shortener.orchestration.graph.DependencyEdge;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Dynamic replanning with approval voiding. Task T096. FR-ORC-019, NFR-CHG-002, SC-007. EC-019, EC-020,
 * EC-030.
 *
 * <p>Plan §3's own procedure, followed exactly: compute the affected downstream set from the graph;
 * transition each to {@code INVALIDATED}; void any approval attached to an invalidated stage (EC-020);
 * leave unaffected paths untouched; emit {@code ReplanEvent} with cause, invalidated stages, and voided
 * approvals. An in-flight stage is not mutated mid-execution (EC-019). Cycle detection runs before any new
 * subgraph is committed (EC-030).
 *
 * <h2>The affected set must be deterministic and independently recomputable</h2>
 *
 * <p>This task's own Guard: "the affected set decides which human approvals are voided, so it must be
 * deterministic and independently recomputable by a reviewer. An AI-determined set is disqualified — the
 * governed component proposes, it never rules on governance scope." The closure this class computes is a
 * pure graph walk over persisted {@code dependency_edge} rows ({@link DownstreamInvalidation}) — a
 * reviewer with the same rows gets the same answer, every time. Closure over declared edges errs toward
 * over-invalidation, which is a human-attention cost, never a surviving stale artifact.
 */
public final class ReplanService {

    private final DownstreamInvalidation invalidation;
    private final ReplanStore replanStore;
    private final JdbcRunStore runStore;
    private final CycleDetector cycleDetector = new CycleDetector();

    public ReplanService(JdbcRunStore runStore, GateStore gateStore, ConnectionSource connections,
                         Clock clock) {
        this.runStore = Objects.requireNonNull(runStore, "runStore");
        Objects.requireNonNull(gateStore, "gateStore");
        Objects.requireNonNull(connections, "connections");
        this.invalidation = new DownstreamInvalidation(runStore, gateStore, connections);
        this.replanStore = new ReplanStore(connections, Objects.requireNonNull(clock, "clock"));
    }

    /**
     * Replans from {@code triggeringNodeKey}: a material change to that node's own artifact, or a
     * clarification answer that alters the requirement it traces to (plan §3's own two trigger kinds —
     * this method does not distinguish between them, since both invalidate downstream work identically;
     * {@code cause} is where the caller states which one this is).
     *
     * @return the recorded, queryable {@link ReplanEvent}
     */
    public ReplanEvent replan(UUID runId, String triggeringNodeKey, String cause) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(triggeringNodeKey, "triggeringNodeKey");
        Objects.requireNonNull(cause, "cause");

        DownstreamInvalidation.Result result = invalidation.invalidateDownstreamOf(
                runId, triggeringNodeKey, "replanned: " + cause);

        long id = replanStore.record(runId, cause, triggeringNodeKey, result.invalidatedNodes(),
                result.voidedDecisionIds());
        return replanStore.byId(id).orElseThrow();
    }

    /**
     * EC-030: a replanned subgraph is checked against the run's EXISTING topology before it is committed
     * — never after. {@link CycleDetector#requireAcyclic} names the exact cycle it found rather than
     * refusing silently, matching T075's own established discipline for this same check at run
     * materialization time.
     *
     * @throws IllegalArgumentException naming the cycle, if the proposed edges would introduce one
     */
    public void appendReplannedEdges(UUID runId, List<DependencyEdge> proposedEdges) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(proposedEdges, "proposedEdges");

        List<DependencyEdge> existing = runStore.edges(runId);
        cycleDetector.requireAcyclic(existing, proposedEdges);

        runStore.appendEdges(runId, proposedEdges);
    }

    public java.util.Optional<ReplanEvent> replanEvent(long replanEventId) {
        return replanStore.byId(replanEventId);
    }
}
