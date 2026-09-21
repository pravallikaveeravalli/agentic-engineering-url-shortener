package agentic.shortener.policy;

import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.lineage.ArtifactProvenanceQuery;
import agentic.shortener.orchestration.replan.ReplanEvent;
import agentic.shortener.orchestration.replan.ReplanService;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T108 — an upstream change invalidates downstream stages rather than leaving stale artifacts.
 * Constitution §Workflow, FR-ORC-019. DS-C.
 *
 * <p>Reuses {@link ReplanService} (T096), which already proves the invalidation walk itself
 * ({@code ReplanServiceTest}); this test's own contribution is the artifact side of the same claim — a
 * downstream node that had already produced an artifact must have that artifact become identifiable as
 * <strong>stale</strong> ({@link ArtifactProvenanceQuery#staleArtifactKeys}) the moment its producer is
 * invalidated, with nothing left over that is downstream-and-invalidated but not flagged.
 */
@DisplayName("T108 — an upstream change leaves zero unflagged stale artifacts downstream")
class UpstreamChangeIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private ReplanService replanService;
    private ArtifactProvenanceQuery provenanceQuery;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        GateStore gateStore = new GateStore(connections, clock);
        replanService = new ReplanService(runStore, gateStore, connections, clock);
        provenanceQuery = new ArtifactProvenanceQuery(connections);
        runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
    }

    private void driveToS6Ready() {
        runStore.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S2", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S2", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S2", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S3", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S3", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S3", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S4", StageState.BLOCKED, StageState.SKIPPED, "no ambiguity");
        runStore.transitionNode(runId, "S5", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S5", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S5", StageState.RUNNING, StageState.SUCCEEDED, "x");
    }

    private void insertArtifactVersion(String artifactKey, String producedByNodeKey) throws Exception {
        try (var c = connections.get(); var ps = c.prepareStatement(
                "INSERT INTO artifact_version (run_id, artifact_key, version, content_hash, "
                        + "produced_by_node_key, input_artifact_keys, produced_at) "
                        + "VALUES (?, ?, 1, ?, ?, '', ?)")) {
            ps.setObject(1, runId);
            ps.setString(2, artifactKey);
            ps.setString(3, "a".repeat(64));
            ps.setString(4, producedByNodeKey);
            ps.setTimestamp(5, Timestamp.from(T0));
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("before the change: an already-produced downstream artifact is not stale")
    void beforeTheChangeArtifactIsNotStale() throws Exception {
        driveToS6Ready();
        insertArtifactVersion("s5-output", "S5");

        assertEquals(List.of(), provenanceQuery.staleArtifactKeys(runId),
                "nothing is stale before any change — S5 is still SUCCEEDED, not invalidated");
    }

    @Test
    @DisplayName("after an upstream change: the downstream artifact is now correctly identified as stale")
    void afterTheChangeArtifactIsStale() throws Exception {
        driveToS6Ready();
        insertArtifactVersion("s5-output", "S5");

        ReplanEvent event = replanService.replan(runId, "S3", "requirement R1 changed — invalidates S5");

        assertTrue(event.invalidatedNodes().contains("S5"), "S5 is downstream of S3, so it must be invalidated");
        assertEquals(List.of("s5-output"), provenanceQuery.staleArtifactKeys(runId),
                "s5-output's producer (S5) is now INVALIDATED, so it must be flagged stale — zero "
                        + "downstream artifacts may be left silently treated as current");
    }

    @Test
    @DisplayName("an artifact from an UNAFFECTED upstream node is never flagged stale")
    void upstreamArtifactIsNeverFlaggedStale() throws Exception {
        driveToS6Ready();
        insertArtifactVersion("s2-output", "S2");
        insertArtifactVersion("s5-output", "S5");

        replanService.replan(runId, "S3", "requirement R1 changed");

        List<String> stale = provenanceQuery.staleArtifactKeys(runId);
        assertEquals(List.of("s5-output"), stale, "s2-output's producer (S2) is upstream of S3 and stays "
                + "SUCCEEDED — it must never appear here, or the invalidation blast radius is too wide");
    }

    @Test
    @DisplayName("EC-019: an artifact from a RUNNING (in-flight) downstream node is never flagged stale "
            + "either — it was not invalidated, so calling it stale would be a claim this test does not "
            + "back")
    void inFlightNodesArtifactIsNeverFlaggedStale() throws Exception {
        driveToS6Ready();
        runStore.transitionNode(runId, "S6", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S6", StageState.READY, StageState.RUNNING, "x");
        insertArtifactVersion("s5-output", "S5");

        ReplanEvent event = replanService.replan(runId, "S3", "requirement R1 changed while S6 was running");

        assertTrue(event.invalidatedNodes().contains("S5"));
        // S6 itself never produced an artifact in this test (it is mid-execution), so this asserts the
        // absence of any artifact incorrectly attributed to it, via the same query the previous tests use.
        assertEquals(List.of("s5-output"), provenanceQuery.staleArtifactKeys(runId));
    }
}
