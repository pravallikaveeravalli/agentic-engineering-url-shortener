package agentic.shortener.orchestration.store;

import agentic.shortener.orchestration.graph.DependencyEdge;
import agentic.shortener.orchestration.graph.JoinSemantics;
import agentic.shortener.orchestration.graph.NodeRole;
import agentic.shortener.orchestration.graph.StageNode;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T074's persistence half, and T080. FR-ORC-002, FR-ORC-003, FR-ORC-004, NFR-AUD-001.
 *
 * <p><strong>The graph is data, so this is where it is proved to be data.</strong>
 * {@code StageTemplateTest} proves the template's shape in memory; that says nothing about whether a run
 * owns its own topology. Per-run materialization is what lets a replanned instance differ from the
 * template and stay queryable, and it is only real once the rows exist.
 *
 * <p><strong>T080's single transaction is the load-bearing part.</strong> Current state and its
 * append-only {@code state_transition} row are written together or not at all. That one transaction is
 * what closes the divergence gap pure event sourcing would otherwise be bought to close — the cheap fix
 * ADR-008 relies on. A state that advanced without its transition row would be a run whose history
 * cannot account for where it is.
 *
 * <p>Integration tier: this is about what the database enforces, and ADR-011 is explicit that a
 * substitute with no constraints cannot be the thing under test.
 */
@DisplayName("T074/T080 per-run materialization and atomic transitions")
class RunMaterializationIT extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-21T11:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ConnectionSource connections;
    private JdbcRunStore store;

    @BeforeEach
    void migrateAndWire() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        store = new JdbcRunStore(connections, CLOCK);
    }

    private UUID materializeFreshRun() {
        return store.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
    }

    // ==============================================================================================
    // T074 — materialization
    // ==============================================================================================

    @Test
    @DisplayName("a fresh run materializes THIRTEEN nodes and FOURTEEN edges, persisted")
    void freshRunMaterializes() throws Exception {
        UUID runId = materializeFreshRun();

        assertEquals(13, countWhere("stage_node", runId), "eleven singletons, S7, S7.join");
        assertEquals(14, countWhere("dependency_edge", runId));
        assertEquals(1, countWhere("workflow_run", runId));
    }

    @Test
    @DisplayName("every stage 1–12 is present exactly once as SINGLETON or FAN_OUT_PARENT")
    void everyStagePresentOnce() throws Exception {
        UUID runId = materializeFreshRun();

        String sql = "SELECT stage_number, COUNT(*) AS n FROM stage_node "
                + "WHERE run_id = ? AND node_role IN ('SINGLETON', 'FAN_OUT_PARENT') "
                + "GROUP BY stage_number ORDER BY stage_number";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                int seen = 0;
                while (rs.next()) {
                    seen++;
                    assertEquals(seen, rs.getInt("stage_number"), "stages must be 1..12 with no gaps");
                    assertEquals(1, rs.getInt("n"),
                            "stage " + rs.getInt("stage_number") + " appears more than once");
                }
                assertEquals(12, seen, "twelve stages (FR-ORC-001)");
            }
        }
    }

    @Test
    @DisplayName("node keys are unique per run, and two runs can share the same keys")
    void nodeKeysAreUniquePerRunNotGlobally() throws Exception {
        UUID first = materializeFreshRun();
        UUID second = materializeFreshRun();

        assertNotEquals(first, second);
        assertEquals(13, countWhere("stage_node", first));
        assertEquals(13, countWhere("stage_node", second),
                "the key is (run_id, node_key): two runs both have an S1, and neither collides");
    }

    @Test
    @DisplayName("T074: stage_number is read from the COLUMN, never parsed from the key")
    void stageNumberComesFromTheColumn() throws Exception {
        // T074's Validate names this and specifies how: a node whose key and column deliberately
        // disagree. Parsing would be correct for every node the template makes and would break silently
        // the first time a replan issued a key that did not encode its stage.
        UUID runId = materializeFreshRun();

        // Append a child under S7 whose key says 7 and whose column says 3. Contrived on purpose.
        store.appendNode(runId, new StageNode("S7.1", 3, NodeRole.FAN_OUT_CHILD, "S7",
                "deliberately inconsistent"), List.of(
                new DependencyEdge("S7", "S7.1", JoinSemantics.ALL),
                new DependencyEdge("S7.1", "S7.join", JoinSemantics.ALL)));

        PersistedNode reloaded = store.node(runId, "S7.1").orElseThrow();
        assertEquals(3, reloaded.stageNumber(),
                "the stored column must win; parsing the key S7.1 would give 7");
        assertEquals("S7.1", reloaded.nodeKey());
    }

    @Test
    @DisplayName("the whole graph reloads with its roles, parents and join semantics intact")
    void graphReloadsFaithfully() {
        UUID runId = materializeFreshRun();
        StageTemplate template = StageTemplate.standard();

        List<StageNode> reloaded = store.nodes(runId);
        assertEquals(13, reloaded.size());

        Set<String> templateKeys = template.nodes().stream()
                .map(StageNode::nodeKey).collect(Collectors.toSet());
        assertEquals(templateKeys,
                reloaded.stream().map(StageNode::nodeKey).collect(Collectors.toSet()));

        PersistedNode join = store.node(runId, "S7.join").orElseThrow();
        assertEquals(NodeRole.JOIN, join.role());
        assertEquals("S7", join.parentNodeKey());
        assertEquals(7, join.stageNumber());

        List<DependencyEdge> edges = store.edges(runId);
        assertEquals(14, edges.size());
        assertTrue(edges.stream().allMatch(e -> e.joinSemantics() == JoinSemantics.ALL));
        assertTrue(edges.stream().anyMatch(
                e -> e.fromNodeKey().equals("S7.join") && e.toNodeKey().equals("S8")));
    }

    @Test
    @DisplayName("edges cannot reference a node that does not exist in the run")
    void edgesAreForeignKeyed() {
        // A dangling edge is a dependency on nothing, which reads as satisfied or as never satisfiable
        // depending on the query. The store refuses it rather than leaving the answer to interpretation.
        UUID runId = materializeFreshRun();

        assertThrows(IllegalStateException.class, () -> store.appendEdges(runId,
                List.of(new DependencyEdge("S1", "S99", JoinSemantics.ALL))));
    }

    @Test
    @DisplayName("a self-loop edge is refused by the STORE as well as by the type")
    void selfLoopRefusedByTheDatabase() throws Exception {
        // DependencyEdge already refuses this, so the only way to try is raw SQL — which is exactly the
        // path a future migration or a manual fix would take.
        UUID runId = materializeFreshRun();
        String sql = "INSERT INTO dependency_edge (run_id, from_node_key, to_node_key, join_semantics) "
                + "VALUES (?, 'S1', 'S1', 'ALL')";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            assertThrows(SQLException.class, ps::executeUpdate);
        }
    }

    // ==============================================================================================
    // T077 — the run-state conditionals, enforced by the database
    // ==============================================================================================

    @Test
    @DisplayName("a terminal run without terminal_state cannot be written")
    void terminalStateConditionalIsEnforced() throws Exception {
        UUID runId = materializeFreshRun();
        assertThrows(SQLException.class, () -> rawUpdateRunState(runId, "COMPLETED", null, null, null));
    }

    @Test
    @DisplayName("a live run carrying terminal_state cannot be written either")
    void terminalStateOnLiveRunRefused() throws Exception {
        // Both directions. The first is an unexplained ending; this is a run that has already been
        // declared finished while still running.
        UUID runId = materializeFreshRun();
        assertThrows(SQLException.class,
                () -> rawUpdateRunState(runId, "RUNNING", "COMPLETED", null, null));
    }

    @Test
    @DisplayName("SAFE_STOP without an auto-abandon time or a reason cannot be written")
    void safeStopConditionalIsEnforced() throws Exception {
        UUID runId = materializeFreshRun();

        assertThrows(SQLException.class,
                () -> rawUpdateRunState(runId, "SAFE_STOP", null, null, "suspended pending decision"),
                "a suspension with no disclosed abandonment time is one nobody can be told about");
        assertThrows(SQLException.class,
                () -> rawUpdateRunState(runId, "SAFE_STOP", null, NOW.plusSeconds(86_400), null),
                "and one with no reason is a suspension nobody can act on");

        // Both present: accepted.
        rawUpdateRunState(runId, "SAFE_STOP", null, NOW.plusSeconds(86_400), "awaiting gate 7");
        assertEquals(RunState.SAFE_STOP, store.run(runId).orElseThrow().state());
    }

    @Test
    @DisplayName("a non-suspended run carrying an auto-abandon time cannot be written")
    void autoAbandonOnLiveRunRefused() throws Exception {
        UUID runId = materializeFreshRun();
        assertThrows(SQLException.class,
                () -> rawUpdateRunState(runId, "RUNNING", null, NOW.plusSeconds(60), null));
    }

    // ==============================================================================================
    // T080 — the atomic state-plus-transition write
    // ==============================================================================================

    @Test
    @DisplayName("the entry node starts READY and every other node starts BLOCKED")
    void initialStatesComeFromTheTopology() {
        // Derived from the graph rather than hard-coded to S1: a node with no incoming edges is an entry
        // by definition, so a replanned graph gets the same answer without anybody updating a constant.
        // This test's first version assumed S1 started BLOCKED and was wrong — the implementation was
        // right, and the premise was corrected rather than the code.
        UUID runId = materializeFreshRun();

        assertEquals(StageState.READY, store.node(runId, "S1").orElseThrow().state(),
                "S1 has no dependencies, so it is ready the moment the run exists");
        for (String blocked : List.of("S2", "S5", "S7", "S7.join", "S11", "S12")) {
            assertEquals(StageState.BLOCKED, store.node(runId, blocked).orElseThrow().state(),
                    blocked + " depends on something and must start blocked");
        }
    }

    @Test
    @DisplayName("T080: a node's state and its transition row are written together")
    void stateAndTransitionWrittenTogether() throws Exception {
        UUID runId = materializeFreshRun();

        store.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "ingestion started");

        assertEquals(StageState.RUNNING, store.node(runId, "S1").orElseThrow().state());
        assertEquals(1, countTransitions(runId, "S1"));
    }

    @Test
    @DisplayName("T080: a state change CANNOT commit without its transition row")
    void stateCannotCommitWithoutItsTransition() throws Exception {
        // The divergence T080 exists to prevent, proved by making the transition insert fail. The state
        // update must roll back with it, or the run's current position would have no history explaining
        // it — and that is precisely the gap event sourcing would otherwise be bought to close.
        UUID runId = materializeFreshRun();

        // The failure is injected on a column the UPDATE does not touch: state_transition.reason is NOT
        // NULL, so the INSERT fails after the UPDATE has already applied within the transaction. If the
        // two were not one unit, the node would be left in its new state with nothing explaining it.
        assertThrows(IllegalStateException.class, () -> store.transitionNode(runId, "S1",
                StageState.READY, StageState.RUNNING, null));

        assertEquals(StageState.READY, store.node(runId, "S1").orElseThrow().state(),
                "the state must have rolled back with the transition that failed");
        assertEquals(0, countTransitions(runId, "S1"), "and no transition row may exist");
    }

    @Test
    @DisplayName("T080: a prohibited transition is refused before anything is written")
    void prohibitedTransitionIsRefusedBeforeWriting() throws Exception {
        UUID runId = materializeFreshRun();
        store.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "started");
        store.transitionNode(runId, "S1", StageState.RUNNING, StageState.FAILED, "failed");

        int before = countTransitions(runId, "S1");

        assertThrows(IllegalStateException.class, () -> store.transitionNode(runId, "S1",
                StageState.FAILED, StageState.SUCCEEDED, "relabelling a failure"));

        assertEquals(StageState.FAILED, store.node(runId, "S1").orElseThrow().state());
        assertEquals(before, countTransitions(runId, "S1"),
                "a refused transition must leave no trace: it did not happen");
    }

    @Test
    @DisplayName("the transition history is append-only and complete")
    void transitionHistoryIsAppendOnlyAndComplete() throws Exception {
        UUID runId = materializeFreshRun();

        store.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "started");
        store.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "one artifact");

        List<String> history = transitionStates(runId, "S1");
        assertEquals(List.of("RUNNING", "SUCCEEDED"), history,
                "every state the node has been in, in order, with none missing");

        // state_transition is INSERT+SELECT only by privilege (V3), so rewriting history is not a
        // matter of nobody trying.
        assertEquals(StageState.SUCCEEDED, store.node(runId, "S1").orElseThrow().state());
    }

    @Test
    @DisplayName("the run's own state transitions are recorded too")
    void runStateTransitionsAreRecorded() throws Exception {
        UUID runId = materializeFreshRun();
        assertEquals(RunState.PENDING, store.run(runId).orElseThrow().state());

        store.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "ingestion started");

        assertEquals(RunState.RUNNING, store.run(runId).orElseThrow().state());

        // A run transition belongs to NO node. The first implementation filed it against "S1" for want
        // of anywhere else, and this assertion caught S1's history carrying a state S1 had never been
        // in. V4 drops state_transition's NOT NULL on node_key rather than inventing a sentinel.
        assertTrue(transitionStates(runId, "S1").isEmpty(),
                "a RUN transition must not appear in any node's history");
        assertEquals(1, countRunLevelTransitions(runId),
                "it must appear exactly once, against the run itself");
    }

    // ==============================================================================================
    // helpers
    // ==============================================================================================

    private long countWhere(String table, UUID runId) throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE run_id = ?")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private int countTransitions(UUID runId, String nodeKey) throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM state_transition WHERE run_id = ? AND node_key = ?")) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countRunLevelTransitions(UUID runId) throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM state_transition WHERE run_id = ? AND node_key IS NULL")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<String> transitionStates(UUID runId, String nodeKey) throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT to_state FROM state_transition WHERE run_id = ? AND node_key = ? "
                             + "ORDER BY state_transition_id")) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> states = new java.util.ArrayList<>();
                while (rs.next()) {
                    states.add(rs.getString(1));
                }
                return states;
            }
        }
    }

    /** Raw SQL, so the database's CHECK constraints are what is under test rather than the type's. */
    private void rawUpdateRunState(UUID runId, String state, String terminalState,
                                   Instant autoAbandonAt, String suspensionReason) throws Exception {
        String sql = "UPDATE workflow_run SET state = ?, terminal_state = ?, auto_abandon_at = ?, "
                + "suspension_reason = ? WHERE run_id = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, state);
            ps.setString(2, terminalState);
            ps.setTimestamp(3, autoAbandonAt == null ? null : java.sql.Timestamp.from(autoAbandonAt));
            ps.setString(4, suspensionReason);
            ps.setObject(5, runId);
            ps.executeUpdate();
        }
    }
}
