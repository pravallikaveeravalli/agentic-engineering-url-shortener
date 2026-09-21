package agentic.shortener.audit.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T114 — the JSON shape {@link StageTelemetry} emits one line of. NFR-OBS-001.
 */
@DisplayName("T114 — StructuredLogEvent: one JSON object per line, runId always present")
class StructuredLogEventTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static StructuredLogEvent started(UUID runId, UUID spanId, UUID parentSpanId) {
        return new StructuredLogEvent(Instant.parse("2026-09-21T12:00:00Z"), runId, spanId, parentSpanId,
                "STAGE_EXECUTION_STARTED", 2, null, null, null);
    }

    @Test
    @DisplayName("runId, spanId, event, and stageNumber are always present")
    void mandatoryFieldsAlwaysPresent() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID spanId = UUID.randomUUID();
        JsonNode node = JSON.readTree(started(runId, spanId, null).toJson());

        assertEquals(runId.toString(), node.get("runId").asText());
        assertEquals(spanId.toString(), node.get("spanId").asText());
        assertEquals("STAGE_EXECUTION_STARTED", node.get("event").asText());
        assertEquals(2, node.get("stageNumber").asInt());
        assertTrue(node.has("timestamp"));
    }

    @Test
    @DisplayName("a stage-execution span has no parentSpanId; a retry-attempt span does")
    void parentSpanIdOnlyWhenNested() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID stageSpanId = UUID.randomUUID();

        JsonNode stageEvent = JSON.readTree(started(runId, stageSpanId, null).toJson());
        assertFalse(stageEvent.has("parentSpanId"), "a top-level span names no parent");

        UUID attemptSpanId = UUID.randomUUID();
        StructuredLogEvent attemptEvent = new StructuredLogEvent(Instant.now(), runId, attemptSpanId,
                stageSpanId, "RETRY_ATTEMPT_STARTED", 2, 1, null, null);
        JsonNode node = JSON.readTree(attemptEvent.toJson());
        assertEquals(stageSpanId.toString(), node.get("parentSpanId").asText(),
                "a retry attempt is parented to its enclosing stage-execution span");
    }

    @Test
    @DisplayName("outcome and durationMillis appear only once the span has closed")
    void outcomeAndDurationOnlyOnCompletion() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID spanId = UUID.randomUUID();

        JsonNode open = JSON.readTree(started(runId, spanId, null).toJson());
        assertFalse(open.has("outcome"));
        assertFalse(open.has("durationMillis"));

        StructuredLogEvent completed = new StructuredLogEvent(Instant.now(), runId, spanId, null,
                "STAGE_EXECUTION_COMPLETED", 2, null, "SUCCEEDED", 42L);
        JsonNode closed = JSON.readTree(completed.toJson());
        assertEquals("SUCCEEDED", closed.get("outcome").asText());
        assertEquals(42, closed.get("durationMillis").asLong());
    }

    @Test
    @DisplayName("NEGATIVE: a missing runId is rejected — every emitted event must be correlatable")
    void missingRunIdIsRejected() {
        assertThrows(NullPointerException.class, () -> new StructuredLogEvent(Instant.now(), null,
                UUID.randomUUID(), null, "STAGE_EXECUTION_STARTED", 2, null, null, null));
    }

    @Test
    @DisplayName("this type carries no field through which a stage's data could reach captured telemetry")
    void noDataBearingFieldExists() {
        // Structural, matching StageInput's own closed-component-list discipline: the record's components
        // are exactly the correlation/span/outcome shape, never an artifact, a destination, or free text.
        var componentNames = java.util.Arrays.stream(StructuredLogEvent.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertEquals(java.util.List.of("timestamp", "runId", "spanId", "parentSpanId", "event",
                "stageNumber", "attemptNumber", "outcome", "durationMillis"), componentNames);
    }
}
