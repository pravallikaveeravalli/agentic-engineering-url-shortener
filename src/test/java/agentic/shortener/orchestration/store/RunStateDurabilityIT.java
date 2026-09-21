package agentic.shortener.orchestration.store;

import agentic.shortener.orchestration.graph.DependencyEdge;
import agentic.shortener.orchestration.graph.JoinSemantics;
import agentic.shortener.orchestration.graph.StageNode;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <strong>Slice 4's exit condition.</strong> A run's full state survives both restart classes.
 * FR-ORC-004 (CL-009). EC-016.
 *
 * <h2>What this proves, and what Slice 6 proves instead</h2>
 *
 * <p>FR-ORC-004's Accept clause has two halves and they belong to different slices. This is the first:
 * <em>after termination at an arbitrary point, state is reloaded and the run's position is
 * unambiguous.</em> Slice 6's exit condition takes the second — <em>the run resumes correctly once the
 * store returns</em> — which needs the resumption service and is not claimed here.
 *
 * <p>The difference is worth stating plainly, because "survives a restart" reads like the stronger
 * claim: <strong>this class proves the state is intact and unambiguous, not that the run continues.</strong>
 *
 * <h2>The two restart classes</h2>
 *
 * <ul>
 *   <li><strong>Orchestrator process.</strong> There is no long-running engine process yet, so the
 *       property that matters is the one that makes a restart survivable at all: <em>nothing about where
 *       a run has got to lives in process memory.</em> Asserted by discarding every object and rebuilding
 *       the store from nothing but a connection — a fresh reader gets the identical position. FR-ORC-004's
 *       negative clause is exactly this: state MUST NOT exist only in process memory.
 *   <li><strong>Persistence layer.</strong> The container is actually restarted and the state read back
 *       over a connection opened afterwards.
 * </ul>
 *
 * <p><strong>No Spring, and its own container.</strong> {@code AnalyticsDurabilityIT} learned both the
 * hard way: restarting a container under a Spring context leaves the Hikari pool holding handles to an
 * address the server no longer answers on — the read afterwards fails, and so does the next test in the
 * class. Durability is a property of the store, so it is proved against the store. {@code pg_ctl restart}
 * is not an option either: PostgreSQL is PID 1 in this image, so stopping the server kills the container.
 *
 * <p><strong>Migration runs once, before any restart.</strong> {@link MigrationSupport} reads
 * Testcontainers' own JDBC URL, whose mapped port is cached from container creation; a restart can
 * republish the port, so anything called after one must re-inspect Docker. {@link #currentJdbcUrl()}
 * does, and the restart test runs last so nothing that does not follows it.
 */
@DisplayName("SLICE 4 EXIT CONDITION — run state survives both restart classes")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunStateDurabilityIT {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** This class's own store, because it restarts it. */
    private static final PostgreSQLContainer<?> OWN_STORE =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("shortener")
                    .withUsername("shortener")
                    .withPassword("shortener");

    @BeforeAll
    static void startAndMigrate() {
        OWN_STORE.start();
        // start() returning is not the store accepting queries. PostgreSQL's entrypoint restarts the
        // postmaster once after initdb, and the log line the default wait strategy watches for can be
        // the pre-restart one — the first migration attempt here failed with a refused connection.
        awaitAcceptingQueries();
        MigrationSupport.migrate(OWN_STORE, "public");
    }

    @AfterAll
    static void stopOwnStore() {
        OWN_STORE.stop();
    }

    /** Read from Docker on every call: a restart can republish the port somewhere else. */
    private static String currentJdbcUrl() {
        InspectContainerResponse info = OWN_STORE.getDockerClient()
                .inspectContainerCmd(OWN_STORE.getContainerId()).exec();
        Ports.Binding[] bindings = info.getNetworkSettings().getPorts().getBindings()
                .get(new ExposedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        assertTrue(bindings != null && bindings.length > 0, "no published port");
        return "jdbc:postgresql://" + OWN_STORE.getHost() + ":"
                + bindings[0].getHostPortSpec() + "/shortener";
    }

    private static ConnectionSource freshConnections() {
        return () -> DriverManager.getConnection(currentJdbcUrl(), "shortener", "shortener");
    }

    /** A store built from nothing but a connection — no state carried over from a previous instance. */
    private static JdbcRunStore freshStore() {
        return new JdbcRunStore(freshConnections(), CLOCK);
    }

    /**
     * Drives a run to a non-trivial, mid-flight position: some nodes finished, one running, one skipped,
     * a fan-out child appended, and the run itself advanced.
     *
     * <p>A run still in its initial state would prove nothing — every field would be a default, and a
     * store that returned defaults for everything would pass.
     */
    private static UUID driveRunToAMidFlightPosition(JdbcRunStore store) {
        UUID runId = store.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());

        store.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "ingestion started");

        store.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "ingesting");
        store.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "run created");
        store.transitionNode(runId, "S2", StageState.BLOCKED, StageState.READY, "S1 done");
        store.transitionNode(runId, "S2", StageState.READY, StageState.RUNNING, "normalizing");
        store.transitionNode(runId, "S2", StageState.RUNNING, StageState.SUCCEEDED, "requirements out");
        store.transitionNode(runId, "S3", StageState.BLOCKED, StageState.READY, "S2 done");
        store.transitionNode(runId, "S3", StageState.READY, StageState.RUNNING, "checking");
        store.transitionNode(runId, "S3", StageState.RUNNING, StageState.SUCCEEDED, "no ambiguity");
        // The conditional gate, not taken. SKIPPED is the state that makes the S3 branch work.
        store.transitionNode(runId, "S4", StageState.BLOCKED, StageState.SKIPPED,
                "no material ambiguity; no_clarification_reason recorded");
        store.transitionNode(runId, "S5", StageState.BLOCKED, StageState.READY, "S3 and S4 settled");
        store.transitionNode(runId, "S5", StageState.READY, StageState.RUNNING, "decomposing");

        // A replan-appended fan-out child, so the reloaded topology differs from the template and a
        // store that re-derived the graph from StageTemplate instead of reading it would be caught.
        store.appendNode(runId, StageNode.fanOutChild("S7.1", 7, "S7", "Implementation slice 1"),
                List.of(new DependencyEdge("S7", "S7.1", JoinSemantics.ALL),
                        new DependencyEdge("S7.1", "S7.join", JoinSemantics.ALL)));
        return runId;
    }

    /** Everything that says where a run has got to, as one comparable value. */
    private record Position(RunState runState, Map<String, StageState> nodeStates,
                            List<String> edges, int transitionCount) {
    }

    private static Position positionOf(JdbcRunStore store, UUID runId) throws Exception {
        Map<String, StageState> nodeStates = store.persistedNodes(runId).stream()
                .collect(Collectors.toMap(PersistedNode::nodeKey, PersistedNode::state));
        List<String> edges = store.edges(runId).stream()
                .map(e -> e.fromNodeKey() + "->" + e.toNodeKey() + ":" + e.joinSemantics())
                .sorted().toList();

        int transitions;
        try (Connection c = freshConnections().get();
             var ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM state_transition WHERE run_id = ?")) {
            ps.setObject(1, runId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                transitions = rs.getInt(1);
            }
        }
        return new Position(store.run(runId).orElseThrow().state(), nodeStates, edges, transitions);
    }

    // ==============================================================================================

    @Test
    @Order(1)
    @DisplayName("RESTART CLASS 1 — orchestrator process: nothing lives in process memory")
    void survivesAnOrchestratorProcessRestart() throws Exception {
        JdbcRunStore before = freshStore();
        UUID runId = driveRunToAMidFlightPosition(before);
        Position positionBefore = positionOf(before, runId);

        // Sanity: the position is actually non-trivial. A run still in its defaults would make every
        // assertion below pass against a store that remembered nothing.
        assertEquals(RunState.RUNNING, positionBefore.runState());
        assertEquals(StageState.SUCCEEDED, positionBefore.nodeStates().get("S3"));
        assertEquals(StageState.SKIPPED, positionBefore.nodeStates().get("S4"));
        assertEquals(StageState.RUNNING, positionBefore.nodeStates().get("S5"));
        assertEquals(14, positionBefore.nodeStates().size(), "thirteen plus the appended child");
        assertEquals(16, positionBefore.edges().size(), "fourteen plus the child's two");
        assertTrue(positionBefore.transitionCount() >= 12,
                "twelve transitions were driven: " + positionBefore.transitionCount());

        // The process restart, modelled as the property that makes one survivable: EVERY object is
        // discarded and the store is rebuilt from nothing but a connection.
        Position positionAfter = positionOf(freshStore(), runId);

        assertEquals(positionBefore, positionAfter,
                "a fresh reader must see the identical position. FR-ORC-004's negative clause: state "
                        + "MUST NOT exist only in process memory");
    }

    @Test
    @Order(2)
    @DisplayName("EC-016: the reloaded position is UNAMBIGUOUS — one state per node, each substantiated")
    void reloadedPositionIsUnambiguous() throws Exception {
        UUID runId = driveRunToAMidFlightPosition(freshStore());

        // FR-ORC-004's other negative clause: recovery MUST NOT report a state it cannot substantiate
        // from persisted records. Two things are asserted — every node has exactly one current state,
        // and every current state is the one the last transition wrote.
        List<PersistedNode> nodes = freshStore().persistedNodes(runId);
        assertEquals(nodes.size(),
                nodes.stream().map(PersistedNode::nodeKey).distinct().count(),
                "a node with two rows would make its position a matter of which one you read");

        try (Connection c = freshConnections().get();
             var ps = c.prepareStatement(
                     "SELECT node_key, to_state FROM state_transition "
                             + "WHERE run_id = ? AND node_key IS NOT NULL "
                             + "AND state_transition_id IN ("
                             + "  SELECT MAX(state_transition_id) FROM state_transition "
                             + "  WHERE run_id = ? AND node_key IS NOT NULL GROUP BY node_key)")) {
            ps.setObject(1, runId);
            ps.setObject(2, runId);
            try (var rs = ps.executeQuery()) {
                int checked = 0;
                while (rs.next()) {
                    String nodeKey = rs.getString("node_key");
                    StageState fromHistory = StageState.valueOf(rs.getString("to_state"));
                    StageState current = freshStore().node(runId, nodeKey).orElseThrow().state();
                    assertEquals(fromHistory, current,
                            nodeKey + ": the current state must be the one the last transition wrote. "
                                    + "A divergence is a state the records cannot substantiate (EC-016)");
                    checked++;
                }
                assertTrue(checked >= 5, "at least the nodes this run moved: " + checked);
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("the run's own state is substantiated by its own transition history too")
    void runStateIsSubstantiated() throws Exception {
        UUID runId = driveRunToAMidFlightPosition(freshStore());

        try (Connection c = freshConnections().get();
             var ps = c.prepareStatement(
                     "SELECT to_state FROM state_transition WHERE run_id = ? AND node_key IS NULL "
                             + "ORDER BY state_transition_id DESC LIMIT 1")) {
            ps.setObject(1, runId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "the run's own transition must be recorded, against no node");
                assertEquals(freshStore().run(runId).orElseThrow().state().name(),
                        rs.getString("to_state"));
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("the store is disk-backed, which is why an in-memory one was disqualified")
    void theStoreIsDiskBacked() {
        // FR-ORC-004 says a disk-backed store is REQUIRED and that an in-memory store does not satisfy
        // the requirement. Asserted rather than assumed: the restart below is only meaningful because the
        // data outlives the process that held it, and this is the image that makes that true.
        assertTrue(OWN_STORE.getDockerImageName().startsWith("postgres:"),
                "the restart proof needs a real server; an embedded store would have nothing to kill");
        assertFalse(OWN_STORE.getJdbcUrl().contains("mem:"),
                "an in-memory URL would make every assertion in this class vacuous");
    }

    @Test
    @Order(5)
    @DisplayName("an execution window is claimed only for a node that actually ran")
    void executionWindowIsOnlyClaimedForNodesThatRan() throws Exception {
        UUID runId = driveRunToAMidFlightPosition(freshStore());

        // S4 is the clarification gate, SKIPPED straight from BLOCKED. It never ran, so it has neither
        // end of an execution window — and the CHECK constraint stage_node_exit_after_entry is what
        // caught the first version of this stamping an exit for it.
        assertWindow(runId, "S4", null, null);

        // S3 ran and finished: both ends, in order.
        Instant s3Entered = timestampOf(runId, "S3", "entered_at");
        Instant s3Exited = timestampOf(runId, "S3", "exited_at");
        assertEquals(NOW, s3Entered);
        assertEquals(NOW, s3Exited);

        // S5 is still running: entered, not exited.
        assertEquals(NOW, timestampOf(runId, "S5", "entered_at"));
        assertEquals(null, timestampOf(runId, "S5", "exited_at"),
                "a running node has not exited");

        // A node INVALIDATED after it succeeded keeps the time it actually finished. Driven on a LATER
        // clock so an overwrite would be visible — with one fixed clock this assertion could not fail.
        JdbcRunStore later = new JdbcRunStore(freshConnections(),
                Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC));
        later.transitionNode(runId, "S2", StageState.SUCCEEDED, StageState.INVALIDATED,
                "superseded by a replan");
        assertEquals(NOW, timestampOf(runId, "S2", "exited_at"),
                "invalidation must not relabel when the node actually finished");
    }

    private void assertWindow(UUID runId, String nodeKey, Instant entered, Instant exited)
            throws Exception {
        assertEquals(entered, timestampOf(runId, nodeKey, "entered_at"), nodeKey + ".entered_at");
        assertEquals(exited, timestampOf(runId, nodeKey, "exited_at"), nodeKey + ".exited_at");
    }

    /** Read straight from the column, because {@link PersistedNode} does not carry the window. */
    private Instant timestampOf(UUID runId, String nodeKey, String column) throws Exception {
        // The column name is a literal from this test, never a caller's string.
        try (Connection c = freshConnections().get();
             var ps = c.prepareStatement("SELECT " + column + " FROM stage_node "
                     + "WHERE run_id = ? AND node_key = ?")) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "no such node: " + nodeKey);
                var value = rs.getTimestamp(1);
                return value == null ? null : value.toInstant();
            }
        }
    }

    /** Last, because a restart can republish the port and invalidate Testcontainers' cached URL. */
    @Test
    @Order(99)
    @DisplayName("RESTART CLASS 2 — persistence layer: the container is actually restarted")
    void survivesAPersistenceLayerRestart() throws Exception {
        UUID runId = driveRunToAMidFlightPosition(freshStore());
        Position positionBefore = positionOf(freshStore(), runId);

        OWN_STORE.getDockerClient().restartContainerCmd(OWN_STORE.getContainerId()).exec();
        awaitAcceptingQueries();

        Position positionAfter = positionOf(freshStore(), runId);

        assertEquals(positionBefore, positionAfter,
                "a store outage MUST NOT lose or corrupt run state (FR-ORC-004)");

        // And the run is still writable, not merely readable — a state nobody can advance is not a run.
        freshStore().transitionNode(runId, "S5", StageState.RUNNING, StageState.SUCCEEDED, "tasks out");
        assertEquals(StageState.SUCCEEDED, freshStore().node(runId, "S5").orElseThrow().state());
    }

    private static void awaitAcceptingQueries() {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                String url = currentJdbcUrl();
                String host = url.substring(url.indexOf("//") + 2, url.lastIndexOf(':'));
                int port = Integer.parseInt(
                        url.substring(url.lastIndexOf(':') + 1, url.lastIndexOf('/')));
                try (Socket probe = new Socket()) {
                    probe.connect(new InetSocketAddress(host, port), 2_000);
                }
                // A reachable socket is not an accepting database: PostgreSQL may still be replaying
                // WAL. The query is the real readiness check, so it is what this waits for.
                try (Connection c = DriverManager.getConnection(url, "shortener", "shortener")) {
                    c.createStatement().execute("SELECT 1");
                    return;
                }
            } catch (IOException | RuntimeException | SQLException e) {
                last = e;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    fail("interrupted while waiting for the store", ie);
                }
            }
        }
        fail("the store never accepted a query", last);
    }
}
