package agentic.shortener.orchestration.api;

import agentic.shortener.contract.OpenApiConformanceTest;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T067 — the ONE end-to-end proof that the HTTP wiring is real. FR-ORC-008.
 *
 * <p>{@code RunInspectionQueryIT} already proves the JSON shape exhaustively, without Spring's startup
 * cost. What it cannot prove is that {@code GET /v1/runs/{runId}} actually reaches
 * {@link RunInspectionController}, which reaches {@link RunInspectionQuery} via real Spring wiring
 * ({@code OrchestrationConfiguration}) rather than a bean that was never registered. This is that one
 * proof, kept deliberately minimal — the JSON content is {@code RunInspectionQueryIT}'s job, not this
 * class's.
 *
 * <p>Extends {@link PostgresIntegrationTest} and reuses its shared container, matching
 * {@code WalkingSkeletonIT}'s own convention — a second, independently-started container here would pay
 * the startup cost twice for no gain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T067 — the run inspection endpoint is real, wired, and conforms over real HTTP")
class RunInspectionControllerIT extends PostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ConnectionSource connections;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("GET /v1/runs/{runId} reaches the real controller, the real query, the real store")
    void endpointReachesTheRealQuery() throws Exception {
        UUID runId = new JdbcRunStore(connections, Clock.systemUTC())
                .createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        new JdbcRunStore(connections, Clock.systemUTC())
                .transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");

        var response = rest.getForEntity(url("/v1/runs/" + runId), String.class);

        assertEquals(200, response.getStatusCode().value());
        var body = JSON.readTree(response.getBody());
        assertEquals(runId.toString(), body.path("runId").asText());
        assertEquals("RUNNING", body.path("state").asText());
        assertEquals(13, body.path("nodes").size());

        new OpenApiConformanceTest().assertConforms("/v1/runs/{runId}", "get", 200, response.getBody());
    }

    @Test
    @DisplayName("an unknown run returns 404 with the contract's Error shape")
    void unknownRunReturns404() throws Exception {
        var response = rest.getForEntity(url("/v1/runs/" + UUID.randomUUID()), String.class);

        assertEquals(404, response.getStatusCode().value());
        var body = JSON.readTree(response.getBody());
        assertEquals("NOT_FOUND", body.path("code").asText());
        assertTrue(body.has("message"));

        new OpenApiConformanceTest().assertConforms("/v1/runs/{runId}", "get", 404, response.getBody());
    }

    @Test
    @DisplayName("the response carries no creator credential and requires none — CN-012")
    void noCredentialRequiredOrReturned() {
        UUID runId = new JdbcRunStore(connections, Clock.systemUTC())
                .createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());

        // No Authorization header sent at all — the request still succeeds, proving the surface is
        // genuinely unauthenticated rather than merely undocumented.
        var response = rest.getForEntity(url("/v1/runs/" + runId), String.class);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(!response.getBody().toLowerCase().contains("credential"));
    }
}
