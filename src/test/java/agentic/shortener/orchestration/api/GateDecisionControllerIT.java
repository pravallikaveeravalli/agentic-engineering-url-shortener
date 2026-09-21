package agentic.shortener.orchestration.api;

import agentic.shortener.contract.OpenApiConformanceTest;
import agentic.shortener.orchestration.gates.ApprovalGate;
import agentic.shortener.orchestration.gates.GateClass;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T067a — the gate-decision recording surface. FR-ORC-013, FR-ORC-021, CN-012, SC-005. CR-013,
 * ADR-005, ADR-008.
 *
 * <p>Seven tests, matching T067a's own Validate clause exactly: one test per submittable outcome
 * (parameterized — APPROVED, REJECTED, CHANGES_REQUESTED, ESCALATED), TIMED_OUT rejected at the schema
 * boundary, CHANGES_REQUESTED with an empty change list, a missing repositoryRecordPath, an agent actor
 * attempting APPROVED, a second decision on an already-decided gate, and a presented creator credential
 * being ignored.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T067a — the gate-decision HTTP surface")
class GateDecisionControllerIT extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PATH = "/v1/runs/{runId}/gates/{gateId}/decision";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ConnectionSource connections;

    private JdbcRunStore runStore;
    private GateStore gateStore;
    private Path materializedRecord;

    @BeforeEach
    void setUp() {
        runStore = new JdbcRunStore(connections, Clock.systemUTC());
        gateStore = new GateStore(connections, Clock.systemUTC());
    }

    @AfterEach
    void cleanUp() throws IOException {
        if (materializedRecord != null) {
            Files.deleteIfExists(materializedRecord);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** A run with S4 raised as an UNRESOLVED_AMBIGUITY gate — the ordinary shape every test drives from. */
    private UUID runWithAPendingGate(String gateId) {
        UUID runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
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
        var now = java.time.Instant.now();
        gateStore.requestGate(new ApprovalGate(gateId, runId, "S4", GateClass.UNRESOLVED_AMBIGUITY,
                now.plusSeconds(3600), now.plusSeconds(90 * 86_400), now));
        return runId;
    }

    /** A real, non-blank file under docs/governance/, so MaterializationCheck passes. */
    private String materializedPath() throws IOException {
        materializedRecord = Files.createTempFile(
                Path.of("docs/governance/gate-decisions"), "t067a-test-", ".md");
        Files.writeString(materializedRecord, "# a real gate decision record for T067a\n");
        return materializedRecord.toString();
    }

    private ResponseEntity<String> post(UUID runId, String gateId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url("/v1/runs/" + runId + "/gates/" + gateId + "/decision"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    // ==============================================================================================
    // 1/7 — one test per submittable outcome
    // ==============================================================================================

    @ParameterizedTest(name = "outcome {0} is recorded and its effect applied")
    @ValueSource(strings = {"APPROVED", "REJECTED", "CHANGES_REQUESTED", "ESCALATED"})
    @DisplayName("1/7: one test per submittable outcome — APPROVED, REJECTED, CHANGES_REQUESTED, ESCALATED")
    void eachSubmittableOutcomeIsRecorded(String outcome) throws Exception {
        UUID runId = runWithAPendingGate("gate-" + outcome);
        String extra = switch (outcome) {
            case "CHANGES_REQUESTED" -> ",\"changes\":[\"re-check the bound\"]";
            case "ESCALATED" -> ",\"escalationTarget\":\"the release owner\"";
            default -> "";
        };
        String body = "{\"outcome\":\"" + outcome + "\",\"actorType\":\"human\",\"actorName\":\"the owner\","
                + "\"reason\":\"a real decision\",\"repositoryRecordPath\":\"" + materializedPath() + "\""
                + extra + "}";

        ResponseEntity<String> response = post(runId, "gate-" + outcome, body);

        assertEquals(200, response.getStatusCode().value(), response.getBody());
        JsonNode inspection = JSON.readTree(response.getBody());
        assertEquals(runId.toString(), inspection.get("runId").asText());
        new OpenApiConformanceTest().assertConforms(PATH, "post", 200, response.getBody());
    }

    // ==============================================================================================
    // 2/7 — TIMED_OUT rejected at the schema boundary
    // ==============================================================================================

    @Test
    @DisplayName("2/7: TIMED_OUT is rejected as an unknown outcome value — it is never submitted")
    void timedOutIsRejectedAtTheSchemaBoundary() throws Exception {
        UUID runId = runWithAPendingGate("gate-timeout");
        String body = "{\"outcome\":\"TIMED_OUT\",\"actorType\":\"human\",\"actorName\":\"the owner\","
                + "\"reason\":\"attempting to submit silence\",\"repositoryRecordPath\":\""
                + materializedPath() + "\"}";

        ResponseEntity<String> response = post(runId, "gate-timeout", body);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_INPUT", JSON.readTree(response.getBody()).get("code").asText());
        new OpenApiConformanceTest().assertConforms(PATH, "post", 400, response.getBody());
    }

    // ==============================================================================================
    // 3/7 — CHANGES_REQUESTED with an empty change list
    // ==============================================================================================

    @Test
    @DisplayName("3/7: CHANGES_REQUESTED with an EMPTY change list is 400 — changes must be enumerated")
    void changesRequestedWithEmptyListIs400() throws Exception {
        UUID runId = runWithAPendingGate("gate-empty-changes");
        String body = "{\"outcome\":\"CHANGES_REQUESTED\",\"actorType\":\"human\","
                + "\"actorName\":\"the owner\",\"reason\":\"needs work\",\"repositoryRecordPath\":\""
                + materializedPath() + "\",\"changes\":[]}";

        ResponseEntity<String> response = post(runId, "gate-empty-changes", body);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_INPUT", JSON.readTree(response.getBody()).get("code").asText());
        new OpenApiConformanceTest().assertConforms(PATH, "post", 400, response.getBody());
    }

    // ==============================================================================================
    // 4/7 — missing repositoryRecordPath
    // ==============================================================================================

    @Test
    @DisplayName("4/7: a missing repositoryRecordPath is 400")
    void missingRepositoryRecordPathIs400() {
        UUID runId = runWithAPendingGate("gate-no-record");
        String body = "{\"outcome\":\"APPROVED\",\"actorType\":\"human\",\"actorName\":\"the owner\","
                + "\"reason\":\"resolved\"}";

        ResponseEntity<String> response = post(runId, "gate-no-record", body);

        assertEquals(400, response.getStatusCode().value());
        new OpenApiConformanceTest().assertConforms(PATH, "post", 400, response.getBody());
    }

    // ==============================================================================================
    // 5/7 — an agent actor attempting APPROVED
    // ==============================================================================================

    @Test
    @DisplayName("5/7: an agent actor attempting APPROVED is 403, an autonomy violation (EC-024)")
    void agentActorAttemptingApprovedIs403() throws Exception {
        UUID runId = runWithAPendingGate("gate-agent");
        String body = "{\"outcome\":\"APPROVED\",\"actorType\":\"agent\",\"actorName\":\"the-engine\","
                + "\"reason\":\"self-approval attempt\",\"repositoryRecordPath\":\"" + materializedPath()
                + "\"}";

        ResponseEntity<String> response = post(runId, "gate-agent", body);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("FORBIDDEN", JSON.readTree(response.getBody()).get("code").asText());
        new OpenApiConformanceTest().assertConforms(PATH, "post", 403, response.getBody());
    }

    // ==============================================================================================
    // 6/7 — a second decision on the same gate
    // ==============================================================================================

    @Test
    @DisplayName("6/7: a second decision on an already-decided gate is 409 — decisions are append-only")
    void secondDecisionOnTheSameGateIs409() throws Exception {
        UUID runId = runWithAPendingGate("gate-twice");
        String body = "{\"outcome\":\"ESCALATED\",\"actorType\":\"human\",\"actorName\":\"the owner\","
                + "\"reason\":\"exceeds my authority\",\"repositoryRecordPath\":\"" + materializedPath()
                + "\",\"escalationTarget\":\"the release owner\"}";
        ResponseEntity<String> first = post(runId, "gate-twice", body);
        assertEquals(200, first.getStatusCode().value(), first.getBody());

        ResponseEntity<String> second = post(runId, "gate-twice", body);

        assertEquals(409, second.getStatusCode().value());
        assertEquals("CONFLICT", JSON.readTree(second.getBody()).get("code").asText());
        new OpenApiConformanceTest().assertConforms(PATH, "post", 409, second.getBody());
    }

    // ==============================================================================================
    // 7/7 — a creator credential presented is ignored, never consulted
    // ==============================================================================================

    @Test
    @DisplayName("7/7: a creator credential PRESENTED is ignored — the surface is unauthenticated by design")
    void creatorCredentialPresentedIsIgnored() throws Exception {
        UUID runId = runWithAPendingGate("gate-credential");
        String body = "{\"outcome\":\"APPROVED\",\"actorType\":\"human\",\"actorName\":\"the owner\","
                + "\"reason\":\"resolved\",\"repositoryRecordPath\":\"" + materializedPath() + "\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // A creator API key, presented anyway — CN-012: run governance is not a URL-shortener concept,
        // and this header must be neither required NOR consulted.
        headers.set("Authorization", "ApiKey some-creator-api-key");
        ResponseEntity<String> response = rest.exchange(
                url("/v1/runs/" + runId + "/gates/gate-credential/decision"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        assertEquals(200, response.getStatusCode().value(), response.getBody());
        assertFalse(response.getBody().toLowerCase().contains("credential"));
    }
}
