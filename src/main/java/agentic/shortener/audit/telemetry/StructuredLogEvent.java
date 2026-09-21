package agentic.shortener.audit.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One structured JSON log line: a span boundary for a stage execution or a retry attempt. Task T114.
 * NFR-OBS-001, NFR-OBS-002. plan §7.
 *
 * <p>Deliberately narrow: {@code runId}, a {@code spanId}, an optional {@code parentSpanId}, the event
 * kind, the stage/attempt numbers, and — once the span closes — an outcome label and a duration. No field
 * here can carry a stage's data. {@link StageTelemetry} never receives {@code inputArtifacts} at all, so
 * there is no shape this type could serialize that would put a secret in captured telemetry (FR-URL-017).
 */
public record StructuredLogEvent(Instant timestamp, UUID runId, UUID spanId, UUID parentSpanId,
                                  String event, int stageNumber, Integer attemptNumber,
                                  String outcome, Long durationMillis) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public StructuredLogEvent {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(spanId, "spanId");
        Objects.requireNonNull(event, "event");
    }

    /** One line, no pretty-printing — one JSON object per line, which is what a line-oriented scan needs. */
    public String toJson() {
        ObjectNode node = JSON.createObjectNode();
        node.put("timestamp", timestamp.toString());
        node.put("runId", runId.toString());
        node.put("spanId", spanId.toString());
        if (parentSpanId != null) {
            node.put("parentSpanId", parentSpanId.toString());
        }
        node.put("event", event);
        node.put("stageNumber", stageNumber);
        if (attemptNumber != null) {
            node.put("attemptNumber", attemptNumber);
        }
        if (outcome != null) {
            node.put("outcome", outcome);
        }
        if (durationMillis != null) {
            node.put("durationMillis", durationMillis);
        }
        return node.toString();
    }
}
