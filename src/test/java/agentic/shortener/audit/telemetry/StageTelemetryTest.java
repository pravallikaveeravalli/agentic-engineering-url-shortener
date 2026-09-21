package agentic.shortener.audit.telemetry;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.reliability.Attempt;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;
import agentic.shortener.orchestration.reliability.RetryOutcome;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T114 — StageTelemetry: a span per stage execution and per retry attempt, with counters/timers,
 * carrying {@code runId} throughout. NFR-OBS-001, NFR-OBS-002.
 *
 * <p>The carried T112 obligation this class closes: correlation id presence was proven for audit/persisted
 * rows only (T112); this proves it for the other three signal kinds the Artifact line names — log line,
 * metric label, and trace span — not merely that the emitters exist.
 */
@DisplayName("T114 — StageTelemetry: spans, counters, and timers all carry runId")
class StageTelemetryTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Logger telemetryLogger;
    private ListAppender<ILoggingEvent> captured;
    private SimpleMeterRegistry meterRegistry;
    private StageTelemetry telemetry;

    @BeforeEach
    void setUp() {
        telemetryLogger = (Logger) LoggerFactory.getLogger("agentic.shortener.telemetry.stage");
        captured = new ListAppender<>();
        captured.start();
        telemetryLogger.addAppender(captured);

        meterRegistry = new SimpleMeterRegistry();
        telemetry = new StageTelemetry(meterRegistry);
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

    private static StageOutcome succeeded() {
        return StageOutcome.succeeded(List.of(new ProducedArtifact("a.key", "a-value", List.of())),
                ExecutorKind.DETERMINISTIC);
    }

    private static StageOutcome failed() {
        return StageOutcome.failed(new FailureEnvelope(FailureCategory.UNAVAILABLE, "unavailable", true));
    }

    @Test
    @DisplayName("LOG LINE: every event for a stage execution carries the same runId")
    void everyLogLineCarriesRunId() {
        UUID runId = UUID.randomUUID();
        telemetry.stageExecution(runId, 2, spanId -> {
            telemetry.retryAttempt(runId, spanId, 2, 1, StageTelemetryTest::succeeded,
                    StageOutcome::succeeded);
            return new RetryOutcome(true, false, null,
                    List.of(new Attempt(1, succeeded(), null)));
        }, RetryOutcome::succeeded);

        List<JsonNode> events = capturedEvents();
        assertEquals(4, events.size(), "STAGE started/completed + RETRY_ATTEMPT started/completed");
        for (JsonNode event : events) {
            assertEquals(runId.toString(), event.get("runId").asText());
        }
    }

    @Test
    @DisplayName("TRACE SPAN: a retry attempt's parentSpanId equals the enclosing stage-execution's spanId")
    void retryAttemptSpanIsParentedToStageExecutionSpan() {
        UUID runId = UUID.randomUUID();
        telemetry.stageExecution(runId, 2, spanId -> {
            telemetry.retryAttempt(runId, spanId, 2, 1, StageTelemetryTest::succeeded,
                    StageOutcome::succeeded);
            return new RetryOutcome(true, false, null,
                    List.of(new Attempt(1, succeeded(), null)));
        }, RetryOutcome::succeeded);

        List<JsonNode> events = capturedEvents();
        String stageSpanId = events.get(0).get("spanId").asText();
        assertEquals("STAGE_EXECUTION_STARTED", events.get(0).get("event").asText());

        JsonNode attemptStarted = events.stream()
                .filter(e -> e.get("event").asText().equals("RETRY_ATTEMPT_STARTED")).findFirst()
                .orElseThrow();
        assertEquals(stageSpanId, attemptStarted.get("parentSpanId").asText());
        assertNotEquals(stageSpanId, attemptStarted.get("spanId").asText(),
                "a retry attempt has its own span, distinct from the stage-execution span it is parented to");
    }

    @Test
    @DisplayName("each attempt gets a distinct spanId, even across the same stage execution")
    void eachAttemptHasADistinctSpan() {
        UUID runId = UUID.randomUUID();
        telemetry.stageExecution(runId, 2, spanId -> {
            telemetry.retryAttempt(runId, spanId, 2, 1, StageTelemetryTest::failed, StageOutcome::succeeded);
            telemetry.retryAttempt(runId, spanId, 2, 2, StageTelemetryTest::succeeded,
                    StageOutcome::succeeded);
            return new RetryOutcome(true, false, null,
                    List.of(new Attempt(1, failed(), null), new Attempt(2, succeeded(), null)));
        }, RetryOutcome::succeeded);

        List<String> attemptSpanIds = capturedEvents().stream()
                .filter(e -> e.get("event").asText().equals("RETRY_ATTEMPT_STARTED"))
                .map(e -> e.get("spanId").asText())
                .toList();
        assertEquals(2, attemptSpanIds.size());
        assertNotEquals(attemptSpanIds.get(0), attemptSpanIds.get(1));
    }

    @Test
    @DisplayName("METRIC LABEL: the stage-execution timer and counter carry run_id as a tag")
    void stageExecutionMetricsCarryRunIdTag() {
        UUID runId = UUID.randomUUID();
        telemetry.stageExecution(runId, 5, spanId -> new RetryOutcome(true, false, null,
                List.of(new Attempt(1, succeeded(), null))), RetryOutcome::succeeded);

        var timer = meterRegistry.find("stage.execution.duration").tag("run_id", runId.toString()).timer();
        assertNotNull(timer, "no timer sample carried this run's run_id tag");
        assertEquals(1, timer.count());

        var counter = meterRegistry.find("stage.execution.count").tag("run_id", runId.toString())
                .tag("outcome", "SUCCEEDED").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    @DisplayName("METRIC LABEL: the retry-attempt timer and counter also carry run_id, plus attempt_number")
    void retryAttemptMetricsCarryRunIdAndAttemptNumberTags() {
        UUID runId = UUID.randomUUID();
        telemetry.stageExecution(runId, 5, spanId -> {
            telemetry.retryAttempt(runId, spanId, 5, 1, StageTelemetryTest::failed, StageOutcome::succeeded);
            telemetry.retryAttempt(runId, spanId, 5, 2, StageTelemetryTest::succeeded,
                    StageOutcome::succeeded);
            return new RetryOutcome(true, false, null,
                    List.of(new Attempt(1, failed(), null), new Attempt(2, succeeded(), null)));
        }, RetryOutcome::succeeded);

        var attempt1Timer = meterRegistry.find("stage.retry.attempt.duration")
                .tag("run_id", runId.toString()).tag("attempt_number", "1").tag("outcome", "FAILED").timer();
        var attempt2Timer = meterRegistry.find("stage.retry.attempt.duration")
                .tag("run_id", runId.toString()).tag("attempt_number", "2").tag("outcome", "SUCCEEDED")
                .timer();
        assertNotNull(attempt1Timer, "attempt 1's failure was not recorded with its own tags");
        assertNotNull(attempt2Timer, "attempt 2's success was not recorded with its own tags");
    }

    @Test
    @DisplayName("a NOOP-disabled instance (existing RetryPolicy callers) emits nothing and records nothing")
    void disabledInstanceIsANoOp() {
        StageTelemetry disabled = StageTelemetry.disabled();
        UUID runId = UUID.randomUUID();

        RetryOutcome outcome = disabled.stageExecution(runId, 2, spanId ->
                new RetryOutcome(true, false, null, List.of(new Attempt(1, succeeded(), null))),
                RetryOutcome::succeeded);

        assertTrue(outcome.succeeded(), "the body still runs; only the telemetry emission is skipped");
        assertEquals(0, capturedEvents().size(),
                "a disabled instance must emit no telemetry at all, or callers that opted out would not "
                        + "have opted out");
    }
}
