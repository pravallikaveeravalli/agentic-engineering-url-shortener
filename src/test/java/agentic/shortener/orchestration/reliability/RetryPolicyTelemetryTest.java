package agentic.shortener.orchestration.reliability;

import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.fakes.ScriptedExecutor;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T114 — proves the {@link StageTelemetry} wiring inside the REAL {@link RetryPolicy#execute} path,
 * not just direct {@code StageTelemetry} calls ({@code StageTelemetryTest} covers those). FR-ORC-014,
 * NFR-OBS-001, NFR-OBS-002.
 */
@DisplayName("T114 — RetryPolicy emits a real stage-execution span and one retry-attempt span per attempt")
class RetryPolicyTelemetryTest {

    private static final int AI_STAGE = 2;

    private static final ObjectMapper JSON = new ObjectMapper();

    private Logger telemetryLogger;
    private ListAppender<ILoggingEvent> captured;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        telemetryLogger = (Logger) LoggerFactory.getLogger("agentic.shortener.telemetry.stage");
        captured = new ListAppender<>();
        captured.start();
        telemetryLogger.addAppender(captured);
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        telemetryLogger.detachAppender(captured);
        captured.stop();
    }

    private List<JsonNode> capturedEvents() {
        return captured.list.stream().map(e -> {
            try {
                return JSON.readTree(e.getFormattedMessage());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }).toList();
    }

    private static StageInput input(UUID runId) {
        return new StageInput(runId, "S2", 2, 1, Map.of("requirement.intake", "shorten urls"));
    }

    @Test
    @DisplayName("fail-twice-then-succeed produces one stage span and three attempt spans, all one runId")
    void wiredThroughRealRetryLoop() {
        UUID runId = UUID.randomUUID();
        RetryPolicy retry = new RetryPolicy(new NoWaitBackoff(), new StageTelemetry(meterRegistry));

        RetryOutcome outcome = retry.execute(AI_STAGE, ScriptedExecutor.failsThenSucceeds(2), input(runId));

        assertTrue(outcome.succeeded());
        List<JsonNode> events = capturedEvents();
        assertEquals(8, events.size(), "1 stage span (2 events) + 3 attempt spans (2 events each)");
        for (JsonNode event : events) {
            assertEquals(runId.toString(), event.get("runId").asText());
        }

        String stageSpanId = events.get(0).get("spanId").asText();
        long attemptStartedCount = events.stream()
                .filter(e -> e.get("event").asText().equals("RETRY_ATTEMPT_STARTED"))
                .peek(e -> assertEquals(stageSpanId, e.get("parentSpanId").asText(),
                        "every attempt in this run is parented to the same stage-execution span"))
                .count();
        assertEquals(3, attemptStartedCount);

        var stageTimer = meterRegistry.find("stage.execution.duration").tag("run_id", runId.toString())
                .timer();
        assertNotNull(stageTimer, "the real execute() path must record a stage-execution timer sample");

        var attemptCounter = meterRegistry.find("stage.retry.attempt.count").tag("run_id", runId.toString())
                .counters();
        assertEquals(3, attemptCounter.stream().mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum(), 0.001, "one counter increment per attempt, three attempts made");
    }

    /** Instant, so this test does not spend the real 1s+2s backoff. */
    private static final class NoWaitBackoff implements Backoff {
        @Override
        public void await(java.time.Duration delay) {
            // no-op
        }
    }
}
