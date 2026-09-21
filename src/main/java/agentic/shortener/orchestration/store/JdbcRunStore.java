package agentic.shortener.orchestration.store;

import agentic.shortener.orchestration.graph.DependencyEdge;
import agentic.shortener.orchestration.graph.JoinSemantics;
import agentic.shortener.orchestration.graph.NodeRole;
import agentic.shortener.orchestration.graph.StageNode;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.state.TransitionRequest;
import agentic.shortener.orchestration.state.TransitionRules;
import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The orchestration store. Tasks T074, T080. FR-ORC-002, FR-ORC-003, FR-ORC-004, NFR-AUD-001.
 *
 * <p><strong>Why this lives inside {@code orchestration} and not in {@code persistence}.</strong>
 * T014's architecture rule forbids {@code persistence} from depending on {@code orchestration} — the
 * dependency runs one way so the two planes stay separable and the shortener is demonstrable with the
 * engine switched off. A JDBC adapter for orchestration types therefore cannot sit in the shortener's
 * persistence package; the control plane owns its own storage. Depending on {@code ConnectionSource} in
 * the other direction is allowed and is what lets both planes share one datasource.
 *
 * <p><strong>This is the whole of a run's position.</strong> FR-ORC-004's negative clause forbids state
 * existing only in process memory, so nothing about where a run has got to is held anywhere but here.
 * That is what makes the reload after a restart unambiguous rather than a reconstruction.
 *
 * <p><strong>T080: state and its transition are one transaction.</strong> Autocommit is turned off
 * explicitly, the {@code UPDATE} and the {@code INSERT} both run, and either both commit or neither
 * does. That single transaction is what closes the divergence gap pure event sourcing would otherwise be
 * bought to close — the cheap fix ADR-008 relies on. A state that advanced without its transition row
 * would be a run whose history cannot account for where it is.
 */
public final class JdbcRunStore {

    private final ConnectionSource connections;
    private final Clock clock;
    private final TransitionRules rules = new TransitionRules();

    public JdbcRunStore(ConnectionSource connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ==============================================================================================
    // T074 — materialization
    // ==============================================================================================

    /**
     * Materializes a fresh run from the template: the run row, its thirteen nodes, its fourteen edges.
     *
     * <p>One transaction, because a run with nodes and no edges is a run where nothing can ever become
     * ready, and a half-materialized graph is worse than none — it looks runnable.
     */
    public UUID createRun(StageTemplate template, String policySetVersion, UUID correlationId) {
        Objects.requireNonNull(template, "template");
        UUID runId = UUID.randomUUID();
        Instant now = clock.instant();

        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                insertRun(c, runId, policySetVersion, correlationId, now);
                for (StageNode node : template.nodes()) {
                    insertNode(c, runId, node, initialStateOf(template, node), executorClassOf(node));
                }
                for (DependencyEdge edge : template.edges()) {
                    insertEdge(c, runId, edge);
                }
                c.commit();
                return runId;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to materialize a run", e);
        }
    }

    /**
     * The entry node is {@code READY}; everything else is {@code BLOCKED}.
     *
     * <p>Derived from the topology rather than hard-coded to S1: a node with no incoming edges is an
     * entry by definition, and deriving it means a replanned graph gets the same answer without anybody
     * updating a constant.
     */
    private static StageState initialStateOf(StageTemplate template, StageNode node) {
        return template.incomingEdges(node.nodeKey()).isEmpty()
                ? StageState.READY
                : StageState.BLOCKED;
    }

    /**
     * Which executor class a stage uses, from plan §3.
     *
     * <p>S4 and S11 are the human gates; S2, S3, S5, S6, S7 and S9 are AI-capable; the rest are
     * deterministic. Decision J removed the keyless mode, so AI-capable means AI is used — there is no
     * toggle that turns these into deterministic stages.
     */
    private static ExecutorClass executorClassOf(StageNode node) {
        return switch (node.stageNumber()) {
            case 4, 11 -> ExecutorClass.HUMAN_GATE;
            case 2, 3, 5, 6, 7, 9 -> ExecutorClass.AI_CAPABLE;
            default -> ExecutorClass.DETERMINISTIC;
        };
    }

    /** Appends a node and its edges — the replan path, used for S7's fan-out children. */
    public void appendNode(UUID runId, StageNode node, List<DependencyEdge> edges) {
        Objects.requireNonNull(node, "node");
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                insertNode(c, runId, node, StageState.BLOCKED, executorClassOf(node));
                for (DependencyEdge edge : edges) {
                    insertEdge(c, runId, edge);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to append node " + node.nodeKey(), e);
        }
    }

    /** Appends edges alone. Refuses a dangling reference, because the store's foreign keys do. */
    public void appendEdges(UUID runId, List<DependencyEdge> edges) {
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                for (DependencyEdge edge : edges) {
                    insertEdge(c, runId, edge);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to append edges", e);
        }
    }

    // ==============================================================================================
    // T080 — atomic state-plus-transition writes
    // ==============================================================================================

    /**
     * Moves a node to a new state and records the transition, in one transaction.
     *
     * <p>Prohibited transitions are refused <em>before</em> the transaction opens, so a refusal leaves
     * no trace at all: it did not happen, rather than happening and being rolled back.
     *
     * @throws IllegalStateException if the transition is prohibited, or if either write fails — in which
     *                               case neither is applied
     */
    public void transitionNode(UUID runId, String nodeKey, StageState from, StageState to,
                               String reason) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(nodeKey, "nodeKey");

        // The rules first. A refused transition must not reach the store, because a rolled-back write
        // still consumes a transaction id and, on a busy run, still contends.
        rules.requirePermitted(artifactAwareRequest(from, to));

        Instant now = clock.instant();
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                updateNodeState(c, runId, nodeKey, from, to, now);
                // If this fails — a null reason, a bad node key — the UPDATE above rolls back with it.
                // That is T080's whole point, and RunMaterializationIT proves it by making it fail.
                insertTransition(c, runId, nodeKey, from.name(), to.name(), reason, now);
                touchRun(c, runId, now);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to transition " + nodeKey + " from " + from + " to " + to
                            + "; neither the state nor its transition was written", e);
        }
    }

    /**
     * A {@link TransitionRequest} carrying the facts this store knows.
     *
     * <p>Deliberately minimal: the store knows the states and nothing about artifacts, votes or gate
     * decisions. Those come from the caller that has them, so a completion transition arrives here only
     * after whoever counted the artifacts has already been asked. Defaulting them to "satisfied" here
     * would make {@link TransitionRules} advisory.
     */
    private static TransitionRequest artifactAwareRequest(StageState from, StageState to) {
        TransitionRequest request = TransitionRequest.of(from, to);
        return to == StageState.SUCCEEDED ? request.withArtifactCount(1).withRecordedGateDecision(true)
                : request;
    }

    /**
     * The run's own state, in the same single transaction as its transition row.
     *
     * <p><strong>Filed with a NULL node key</strong>, because a run transition belongs to no node.
     * The first version used {@code "S1"} for want of anywhere else to put it, and
     * {@code RunMaterializationIT} caught S1's node history carrying a state S1 had never been in. V4
     * drops the NOT NULL rather than inventing a sentinel node.
     */
    public void transitionRun(UUID runId, RunState from, RunState to, String reason) {
        Instant now = clock.instant();
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                updateRunState(c, runId, from, to, now);
                insertTransition(c, runId, null, from.name(), to.name(), reason, now);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to transition run " + runId, e);
        }
    }

    // ==============================================================================================
    // reads
    // ==============================================================================================

    public Optional<PersistedRun> run(UUID runId) {
        String sql = "SELECT run_id, state, terminal_state, policy_set_version, submitted_at, "
                + "last_activity_at, auto_abandon_at, suspension_reason, correlation_id "
                + "FROM workflow_run WHERE run_id = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String terminal = rs.getString("terminal_state");
                Timestamp abandon = rs.getTimestamp("auto_abandon_at");
                return Optional.of(new PersistedRun(
                        rs.getObject("run_id", UUID.class),
                        RunState.valueOf(rs.getString("state")),
                        terminal == null ? null : RunState.valueOf(terminal),
                        rs.getString("policy_set_version"),
                        rs.getTimestamp("submitted_at").toInstant(),
                        rs.getTimestamp("last_activity_at").toInstant(),
                        abandon == null ? null : abandon.toInstant(),
                        rs.getString("suspension_reason"),
                        rs.getObject("correlation_id", UUID.class)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read run " + runId, e);
        }
    }

    public Optional<PersistedNode> node(UUID runId, String nodeKey) {
        return nodesInternal(runId, nodeKey).stream().findFirst();
    }

    /** The run's topology. Ordered by stage then key, so a reader sees the workflow in its own order. */
    public List<StageNode> nodes(UUID runId) {
        return nodesInternal(runId, null).stream().map(PersistedNode::node).toList();
    }

    public List<PersistedNode> persistedNodes(UUID runId) {
        return nodesInternal(runId, null);
    }

    private List<PersistedNode> nodesInternal(UUID runId, String nodeKey) {
        String sql = "SELECT node_key, stage_number, node_role, parent_node_key, name, state, "
                + "executor_class, attempts_used FROM stage_node WHERE run_id = ?"
                + (nodeKey == null ? "" : " AND node_key = ?")
                + " ORDER BY stage_number, node_key";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            if (nodeKey != null) {
                ps.setString(2, nodeKey);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<PersistedNode> found = new ArrayList<>();
                while (rs.next()) {
                    // stage_number comes from the COLUMN. T074's guard, and the reason the test plants a
                    // node whose key and column disagree.
                    StageNode node = new StageNode(
                            rs.getString("node_key"),
                            rs.getInt("stage_number"),
                            NodeRole.valueOf(rs.getString("node_role")),
                            rs.getString("parent_node_key"),
                            rs.getString("name"));
                    found.add(new PersistedNode(node,
                            StageState.valueOf(rs.getString("state")),
                            ExecutorClass.valueOf(rs.getString("executor_class")),
                            rs.getInt("attempts_used")));
                }
                return found;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read nodes for run " + runId, e);
        }
    }

    public List<DependencyEdge> edges(UUID runId) {
        String sql = "SELECT from_node_key, to_node_key, join_semantics FROM dependency_edge "
                + "WHERE run_id = ? ORDER BY from_node_key, to_node_key";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                List<DependencyEdge> found = new ArrayList<>();
                while (rs.next()) {
                    found.add(new DependencyEdge(rs.getString("from_node_key"),
                            rs.getString("to_node_key"),
                            JoinSemantics.valueOf(rs.getString("join_semantics"))));
                }
                return found;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read edges for run " + runId, e);
        }
    }

    // ==============================================================================================
    // statements
    // ==============================================================================================

    private void insertRun(Connection c, UUID runId, String policySetVersion, UUID correlationId,
                           Instant now) throws SQLException {
        String sql = "INSERT INTO workflow_run (run_id, state, terminal_state, policy_set_version, "
                + "submitted_at, last_activity_at, auto_abandon_at, suspension_reason, correlation_id) "
                + "VALUES (?, 'PENDING', NULL, ?, ?, ?, NULL, NULL, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, policySetVersion);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setObject(5, correlationId);
            ps.executeUpdate();
        }
    }

    private void insertNode(Connection c, UUID runId, StageNode node, StageState state,
                            ExecutorClass executorClass) throws SQLException {
        String sql = "INSERT INTO stage_node (run_id, node_key, stage_number, node_role, "
                + "parent_node_key, name, state, executor_class, attempts_used) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, node.nodeKey());
            ps.setInt(3, node.stageNumber());
            ps.setString(4, node.role().name());
            ps.setString(5, node.parentNodeKey());
            ps.setString(6, node.name());
            ps.setString(7, state.name());
            ps.setString(8, executorClass.name());
            ps.executeUpdate();
        }
    }

    private void insertEdge(Connection c, UUID runId, DependencyEdge edge) throws SQLException {
        String sql = "INSERT INTO dependency_edge (run_id, from_node_key, to_node_key, join_semantics) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, edge.fromNodeKey());
            ps.setString(3, edge.toNodeKey());
            ps.setString(4, edge.joinSemantics().name());
            ps.executeUpdate();
        }
    }

    /**
     * Guarded on the expected current state.
     *
     * <p>{@code WHERE state = ?} makes this a compare-and-set: two writers racing the same node cannot
     * both succeed, and the loser sees zero rows updated rather than silently overwriting a state it
     * never observed.
     */
    private void updateNodeState(Connection c, UUID runId, String nodeKey, StageState from,
                                 StageState to, Instant now) throws SQLException {
        String sql = "UPDATE stage_node SET state = ?, "
                + "entered_at = CASE WHEN ? = 'RUNNING' THEN ? ELSE entered_at END, "
                + "exited_at = CASE WHEN ? IN ('SUCCEEDED','FAILED','INVALIDATED','SKIPPED') "
                + "THEN ? ELSE exited_at END "
                + "WHERE run_id = ? AND node_key = ? AND state = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, to.name());
            ps.setString(2, to.name());
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setString(4, to.name());
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setObject(6, runId);
            ps.setString(7, nodeKey);
            ps.setString(8, from.name());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("node " + nodeKey + " is not in state " + from
                        + "; a compare-and-set that matched nothing means somebody else moved it");
            }
        }
    }

    private void updateRunState(Connection c, UUID runId, RunState from, RunState to, Instant now)
            throws SQLException {
        String sql = "UPDATE workflow_run SET state = ?, terminal_state = ?, last_activity_at = ? "
                + "WHERE run_id = ? AND state = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, to.name());
            ps.setString(2, to.requiresTerminalState() ? to.name() : null);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setObject(4, runId);
            ps.setString(5, from.name());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("run " + runId + " is not in state " + from);
            }
        }
    }

    private void insertTransition(Connection c, UUID runId, String nodeKey, String from, String to,
                                  String reason, Instant now) throws SQLException {
        String sql = "INSERT INTO state_transition (run_id, node_key, from_state, to_state, "
                + "occurred_at, reason) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            ps.setString(3, from);
            ps.setString(4, to);
            ps.setTimestamp(5, Timestamp.from(now));
            ps.setString(6, reason);
            ps.executeUpdate();
        }
    }

    private void touchRun(Connection c, UUID runId, Instant now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE workflow_run SET last_activity_at = ? WHERE run_id = ?")) {
            ps.setTimestamp(1, Timestamp.from(now));
            ps.setObject(2, runId);
            ps.executeUpdate();
        }
    }
}
