package agentic.shortener.orchestration.recovery;

import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.state.TransitionRules;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.orchestration.store.PersistedNode;
import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Resumption across an orchestrator-process restart. Task T093. FR-ORC-018, NFR-REL-003, SC-006. EC-015,
 * EC-016.
 *
 * <h2>What "resume" means at this codebase's current maturity</h2>
 *
 * <p>There is no live engine loop yet that dispatches executors on its own — every stage transition so far
 * is driven by an explicit caller. So resumption cannot mean "re-invoke whatever executor was running";
 * there is nothing here that invokes one in the first place. What it DOES mean, and what {@link #resume}
 * does: <strong>reconcile any node the process left mid-execution to the state the persisted record
 * actually substantiates</strong> — never a state assumed from "it was running, so it probably..."
 *
 * <h2>Why this structurally satisfies EC-015 (no duplicated committed effects)</h2>
 *
 * <p>This class never calls a {@code StageExecutor}. It only reads {@code artifact_version} (the
 * durable record of a committed effect — T081, T076a) and {@link JdbcRunStore#transitionNode}. A node
 * cannot be duplicated into re-executing here because nothing here executes anything — the exactly-once
 * property is not a check this class performs, it is a consequence of what this class is capable of doing.
 *
 * <h2>Why this satisfies EC-016 (never report a state it cannot substantiate)</h2>
 *
 * <p>A node found {@code RUNNING} on reload has exactly two honest outcomes, decided by what is actually
 * persisted, never by assumption:
 *
 * <ul>
 *   <li><strong>An {@code artifact_version} row names this node as producer.</strong> The effect WAS
 *       committed before the crash — the crash landed between the effect and the state transition
 *       recording it (EC-016's own scenario). Substantiated by that row, the node moves to
 *       {@code SUCCEEDED}. Re-invoking the executor here would be the duplicated effect EC-015 forbids.
 *   <li><strong>No such row exists.</strong> Nothing was committed. The node moves back to {@code READY} —
 *       safe, because there is no effect to duplicate, and legitimate, because {@link TransitionRules}
 *       (T079) has no prohibited-transition entry for {@code RUNNING -> READY}; the seven named
 *       prohibitions are an enumerated deny-list, and this is not one of them.
 * </ul>
 *
 * <p>Every other stage state ({@code READY}, {@code AWAITING_APPROVAL}, {@code RETRY_WAIT}, terminal
 * states, ...) is left untouched — a crash while a node was NOT actively running left nothing
 * unsubstantiated about it in the first place.
 */
public final class ResumeService {

    /**
     * @param runId              the run resumed
     * @param confirmedSucceeded node keys found {@code RUNNING} with a committed artifact — moved to
     *                           {@code SUCCEEDED} without re-execution
     * @param resetToReady       node keys found {@code RUNNING} with no committed artifact — moved back to
     *                           {@code READY}, safe to re-attempt
     */
    public record ResumeOutcome(UUID runId, List<String> confirmedSucceeded, List<String> resetToReady) {

        public ResumeOutcome {
            confirmedSucceeded = List.copyOf(confirmedSucceeded);
            resetToReady = List.copyOf(resetToReady);
        }
    }

    private final JdbcRunStore runStore;
    private final ConnectionSource connections;
    private final RunLease lease;

    public ResumeService(JdbcRunStore runStore, ConnectionSource connections, RunLease lease) {
        this.runStore = Objects.requireNonNull(runStore, "runStore");
        this.connections = Objects.requireNonNull(connections, "connections");
        this.lease = Objects.requireNonNull(lease, "lease");
    }

    /**
     * Reconciles every node of {@code runId} left {@code RUNNING} by a crashed process to a state its own
     * persisted records substantiate.
     *
     * <p><strong>Empty means "someone else is already resuming this run"</strong> (T095, EC-026) — the
     * lease is acquired first, and a caller that loses the race does no reconciliation work at all rather
     * than racing a concurrent resumer node by node. Released in a {@code finally}, so a resumer that
     * throws partway through still frees the run for a later attempt.
     *
     * <p>Idempotent otherwise: a run with nothing {@code RUNNING} (already resumed, or never crashed) does
     * no work and returns two empty lists — calling this twice in a row, once the first call has released
     * the lease, is safe.
     */
    public Optional<ResumeOutcome> resume(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        runStore.run(runId).orElseThrow(() -> new IllegalStateException("no such run: " + runId));

        UUID holderId = UUID.randomUUID();
        if (!lease.tryAcquire(runId, holderId)) {
            return Optional.empty();
        }
        try {
            List<String> confirmedSucceeded = new ArrayList<>();
            List<String> resetToReady = new ArrayList<>();

            for (PersistedNode node : runStore.persistedNodes(runId)) {
                if (node.state() != StageState.RUNNING) {
                    continue;
                }
                String nodeKey = node.nodeKey();
                if (hasCommittedArtifact(runId, nodeKey)) {
                    runStore.transitionNode(runId, nodeKey, StageState.RUNNING, StageState.SUCCEEDED,
                            "resumed after a process restart: a committed artifact already exists for "
                                    + "this node (EC-016) — its effect is not re-executed (EC-015)");
                    confirmedSucceeded.add(nodeKey);
                } else {
                    runStore.transitionNode(runId, nodeKey, StageState.RUNNING, StageState.READY,
                            "resumed after a process restart: no committed artifact exists for this "
                                    + "node — nothing was committed, so it is safe to re-attempt");
                    resetToReady.add(nodeKey);
                }
            }

            return Optional.of(new ResumeOutcome(runId, confirmedSucceeded, resetToReady));
        } finally {
            lease.release(runId, holderId);
        }
    }

    /** Whether {@code artifact_version} substantiates a committed effect for this node — never guessed. */
    private boolean hasCommittedArtifact(UUID runId, String nodeKey) {
        String sql = "SELECT 1 FROM artifact_version WHERE run_id = ? AND produced_by_node_key = ? LIMIT 1";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to check for a committed artifact for node " + nodeKey, e);
        }
    }
}
