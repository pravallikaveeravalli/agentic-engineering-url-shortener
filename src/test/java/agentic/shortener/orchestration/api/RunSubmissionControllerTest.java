package agentic.shortener.orchestration.api;

import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.conductor.Conductor;
import agentic.shortener.orchestration.conductor.FanOutPlanner;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.gates.GateRequestPresenter;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.ArtifactWriteGuard;
import agentic.shortener.orchestration.reliability.Backoff;
import agentic.shortener.orchestration.reliability.RetryPolicy;
import agentic.shortener.orchestration.state.RetentionPolicy;
import agentic.shortener.orchestration.state.SafeStopHandler;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task T082a, fast tier. The {@code 503} store-unavailable path from {@code contracts/openapi.yaml}'s own
 * {@code POST /v1/runs} definition, proven with a real {@link JdbcRunStore} wired to a {@link
 * ConnectionSource} that genuinely always fails — an injected fake for the ONE thing under test (a broken
 * store), matching ADR-011's discipline exactly, not a mock of {@link Conductor} itself. {@link
 * RunSubmissionControllerIT} (the integration tier) proves the successful and malformed-input paths against
 * a real database; this class exists because reliably forcing a live PostgreSQL outage mid-test would be
 * disruptive to share with other integration tests running against the same container.
 */
@DisplayName("T082a — 503 when the store cannot be reached at all")
class RunSubmissionControllerTest {

    @Test
    @DisplayName("a store that cannot be reached returns 503 STORE_UNAVAILABLE, no run id issued")
    void storeUnavailableReturns503() {
        ConnectionSource brokenStore = () -> {
            throw new SQLException("simulated: the store cannot be reached");
        };
        Clock clock = Clock.systemUTC();
        JdbcRunStore runStore = new JdbcRunStore(brokenStore, clock);
        GateStore gateStore = new GateStore(brokenStore, clock);
        RunInspectionQuery inspectionQuery = new RunInspectionQuery(brokenStore);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Conductor conductor = new Conductor(runStore, gateStore,
                new GateRequestPresenter(clock, new RetentionPolicy()),
                new ArtifactWriteGuard(brokenStore, clock), new AuditWriter(brokenStore),
                StageTelemetry.disabled(), new SafeStopHandler(brokenStore, clock, new RetentionPolicy()),
                new RetryPolicy(Backoff.sleeping()),
                (java.util.function.Function<Integer, StageExecutor>) stageNumber -> {
                    throw new IllegalStateException("no executor should ever be reached in this test");
                },
                FanOutPlanner.singleChild(), clock, pool, paths -> java.util.Map.of());

        RunSubmissionController controller = new RunSubmissionController(conductor, inspectionQuery, pool);

        ResponseEntity<JsonNode> response =
                controller.create(new RunSubmissionController.CreateRunRequest("a requirement"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("STORE_UNAVAILABLE", response.getBody().get("code").asText());
    }
}
