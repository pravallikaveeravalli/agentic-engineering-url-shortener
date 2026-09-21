package agentic.shortener.orchestration.api;

import agentic.shortener.contract.OpenApiConformanceTest;
import agentic.shortener.orchestration.gates.ApprovalGate;
import agentic.shortener.orchestration.gates.GateClass;
import agentic.shortener.orchestration.gates.GateRequestPresenter;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RetentionPolicy;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.SafeStopHandler;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.state.SuspensionTrigger;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T067 — the gate inspection view's assembly logic. FR-ORC-008, FR-ORC-013. US-2 scenario 1.
 *
 * <p>Framework-free by design, matching every other orchestration class built this session: this is what
 * {@code RunInspectionController} (the thin {@code @RestController} wrapper) delegates to, and testing the
 * assembly here means the JSON shape is proven without paying Spring's startup cost for every case.
 * {@code RunInspectionControllerIT} covers the one thing this class cannot: that the HTTP wiring is real.
 */
@DisplayName("T067 — run inspection assembles a conformant RunInspection, reviewer-facing")
class RunInspectionQueryIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private GateStore gateStore;
    private RunInspectionQuery query;
    private OpenApiConformanceTest conformance;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        gateStore = new GateStore(connections, clock);
        query = new RunInspectionQuery(connections);
        conformance = new OpenApiConformanceTest();
        runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
    }

    private void assertConforms(String json) {
        conformance.assertConforms("/v1/runs/{runId}", "get", 200, json);
    }

    @Test
    @DisplayName("a fresh run conforms, with all thirteen nodes and no pending gate")
    void freshRunConforms() throws Exception {
        var inspection = query.inspect(runId).orElseThrow();
        String json = JSON.writeValueAsString(inspection);
        assertConforms(json);

        var node = JSON.readTree(json);
        assertEquals(13, node.path("nodes").size(), "eleven singletons, S7's fan-out parent, its join");
        assertTrue(node.path("pendingGate").isNull());
        assertEquals("RUNNING", node.path("state").asText());
        assertTrue(node.path("terminalState").isNull());
    }

    @Test
    @DisplayName("nodes are keyed by node_key, and dependsOn is node keys, not stage numbers")
    void nodesKeyedByNodeKeyWithNodeKeyDependencies() throws Exception {
        var inspection = query.inspect(runId).orElseThrow();
        var node = JSON.readTree(JSON.writeValueAsString(inspection));

        Map<String, com.fasterxml.jackson.databind.JsonNode> byKey = new java.util.LinkedHashMap<>();
        node.path("nodes").forEach(n -> byKey.put(n.path("nodeKey").asText(), n));

        assertTrue(byKey.containsKey("S3"));
        List<String> s3DependsOn = new java.util.ArrayList<>();
        byKey.get("S3").path("dependsOn").forEach(d -> s3DependsOn.add(d.asText()));
        assertEquals(List.of("S2"), s3DependsOn, "S3 depends on S2's node key, not on the integer 2");

        // S7.join depends on every fan-out child — node keys, never stage numbers, which is the
        // whole reason this field exists rather than an integer.
        assertTrue(byKey.containsKey("S7.join"));
        List<String> joinDependsOn = new java.util.ArrayList<>();
        byKey.get("S7.join").path("dependsOn").forEach(d -> joinDependsOn.add(d.asText()));
        assertEquals(List.of("S7"), joinDependsOn);
    }

    @Test
    @DisplayName("a run with a pending gate conforms, and pendingGate carries BOTH deadlines (T060)")
    void pendingGateCarriesBothDeadlines() throws Exception {
        driveToS4();
        var presenter = new GateRequestPresenter(Clock.fixed(T0, ZoneOffset.UTC), new RetentionPolicy());
        ApprovalGate gate = presenter.request("S4", runId, "S4", GateClass.UNRESOLVED_AMBIGUITY);
        gateStore.requestGate(gate);

        var inspection = query.inspect(runId).orElseThrow();
        String json = JSON.writeValueAsString(inspection);
        assertConforms(json);

        var node = JSON.readTree(json);
        assertFalse(node.path("pendingGate").isNull());
        assertEquals("S4", node.path("pendingGate").path("gateId").asText());
        assertEquals("UNRESOLVED_AMBIGUITY", node.path("pendingGate").path("gateClass").asText());
        assertTrue(node.path("pendingGate").hasNonNull("waitDeadline"));
        assertTrue(node.path("pendingGate").hasNonNull("disclosedAutoAbandonAt"),
                "T060: the person being asked must see BOTH deadlines in the ask, and inspection must "
                        + "surface the same two");
    }

    @Test
    @DisplayName("a suspended run conforms, and discloses autoAbandonAt — non-null iff SAFE_STOP")
    void suspendedRunDisclosesAutoAbandon() throws Exception {
        driveToS4();
        new SafeStopHandler(connections, Clock.fixed(T0, ZoneOffset.UTC), new RetentionPolicy())
                .suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");

        var inspection = query.inspect(runId).orElseThrow();
        String json = JSON.writeValueAsString(inspection);
        assertConforms(json);

        var node = JSON.readTree(json);
        assertEquals("SAFE_STOP", node.path("state").asText());
        assertTrue(node.path("terminalState").isNull(), "CL-005: a suspended run has no terminal outcome");
        assertTrue(node.path("autoAbandonAt").isTextual());
        assertTrue(node.path("suspensionReason").isTextual());
    }

    @Test
    @DisplayName("a RUNNING run's autoAbandonAt is null — disclosed only while suspended")
    void runningRunHasNoDisclosedAutoAbandon() throws Exception {
        var inspection = query.inspect(runId).orElseThrow();
        var node = JSON.readTree(JSON.writeValueAsString(inspection));
        assertTrue(node.path("autoAbandonAt").isNull());
        assertTrue(node.path("suspensionReason").isNull());
    }

    @Test
    @DisplayName("executorKindUsed is present once recorded, and null before")
    void executorKindUsedReflectsWhatWasRecorded() throws Exception {
        runStore.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "x");

        var beforeInspection = JSON.readTree(JSON.writeValueAsString(query.inspect(runId).orElseThrow()));
        Map<String, com.fasterxml.jackson.databind.JsonNode> before = new java.util.LinkedHashMap<>();
        beforeInspection.path("nodes").forEach(n -> before.put(n.path("nodeKey").asText(), n));
        assertTrue(before.get("S1").path("executorKindUsed").isNull());

        runStore.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.recordExecutorKind(runId, "S1",
                agentic.shortener.orchestration.executor.ExecutorKind.DETERMINISTIC);

        var afterInspection = JSON.readTree(JSON.writeValueAsString(query.inspect(runId).orElseThrow()));
        Map<String, com.fasterxml.jackson.databind.JsonNode> after = new java.util.LinkedHashMap<>();
        afterInspection.path("nodes").forEach(n -> after.put(n.path("nodeKey").asText(), n));
        assertEquals("DETERMINISTIC", after.get("S1").path("executorKindUsed").asText());
    }

    @Test
    @DisplayName("inspection carries NO creator credential — CN-012")
    void noCreatorCredential() throws Exception {
        String json = JSON.writeValueAsString(query.inspect(runId).orElseThrow());
        assertFalse(json.toLowerCase().contains("credential"),
                "run inspection is an orchestrator surface; creators have no standing in it: " + json);
        assertFalse(json.toLowerCase().contains("apikey") && json.toLowerCase().contains("crk_"),
                "no api key material either");
    }

    @Test
    @DisplayName("an unknown run is reported absent, not a conformant empty inspection")
    void unknownRunIsAbsent() {
        assertTrue(query.inspect(UUID.randomUUID()).isEmpty());
    }

    private void driveToS4() {
        runStore.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S2", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S2", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S2", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S3", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S3", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S3", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S4", StageState.BLOCKED, StageState.AWAITING_APPROVAL,
                "material ambiguity found");
    }
}
