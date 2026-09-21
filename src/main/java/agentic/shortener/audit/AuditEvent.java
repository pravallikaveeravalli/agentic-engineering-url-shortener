package agentic.shortener.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * KE-19: one immutable audit record. Task T111. FR-ORC-023, NFR-AUD-001. Constitution IX.
 *
 * <p>The six mandatory fields, structurally enforced — {@code actorType}, {@code action},
 * {@code occurredAt}, {@code affectedArtifact}, {@code result}, {@code reason} — plus the three optional
 * ones {@code contracts/audit-event.schema.json} also names: {@code stageNumber}, {@code executorKindUsed},
 * {@code correctsEventId}.
 *
 * <p><strong>{@code runId} doubles as the correlation identifier</strong> for every event this class can
 * represent — the schema's own field description for {@code runId} calls it exactly that ("Correlation
 * identifier propagated across logs, metrics, traces and state"). Every governance write this codebase
 * currently produces is tied to a specific run (ADR-010's own scoping: "fail-closed applies to orchestrator
 * governance writes only"), so this class requires a real {@code runId} unconditionally — stricter than
 * {@code audit_record.run_id}'s own nullable column, which predates this task and stays nullable for a
 * hypothetical run-less system event nothing here yet produces.
 *
 * @param eventId  {@code null} for a not-yet-recorded event, which the store assigns
 * @param reason   Mandatory field 6. "Never empty — an unexplained audit record is not auditable" (the
 *                 schema's own words)
 */
public record AuditEvent(Long eventId, UUID runId, String actorType, String action, Instant occurredAt,
                         String affectedArtifact, String result, String reason, Integer stageNumber,
                         String executorKindUsed, Long correctsEventId) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** {@code 'agent'} is legitimate here, unlike {@code gates.Actor} — a gate DECISION excludes it
     * (FR-ORC-021), but an audit event may report what an agent DID, distinct from deciding a gate. */
    private static final Set<String> ACTOR_TYPES = Set.of("human", "agent", "system");

    private static final Set<String> RESULTS =
            Set.of("SUCCESS", "FAILURE", "REFUSED", "BLOCKED", "SUSPENDED", "NOT_APPLICABLE");

    private static final Set<String> EXECUTOR_KINDS = Set.of("DETERMINISTIC", "AI", "HUMAN");

    /** {@code contracts/audit-event.schema.json}'s own closed action vocabulary — the twenty-seven values. */
    private static final Set<String> ACTIONS = Set.of(
            "RUN_CREATED", "RUN_SUSPENDED", "RUN_RESUMED", "RUN_COMPLETED", "RUN_REJECTED", "RUN_ABANDONED",
            "STAGE_ENTERED", "STAGE_EXITED", "CRITERIA_EVALUATED", "EXECUTOR_DISPATCHED",
            "FAILURE_CLASSIFIED", "RETRY_RULED", "FALLBACK_ACTIVATED",
            "ROLLBACK_APPLIED", "COMPENSATION_APPLIED",
            "GATE_REQUESTED", "GATE_DECIDED", "GATE_TIMED_OUT",
            "CLARIFICATION_REQUESTED", "CLARIFICATION_RECORDED",
            "REPLAN_EXECUTED", "APPROVAL_VOIDED",
            "POLICY_EVALUATED", "EXCEPTION_REQUESTED", "EXCEPTION_APPROVED", "EXCEPTION_EXPIRED",
            "AUTONOMY_VIOLATION_REFUSED",
            "RELEASE_READINESS_EVALUATED", "SUMMARY_ASSEMBLED");

    public AuditEvent {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(affectedArtifact, "affectedArtifact");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(reason, "reason");

        if (!ACTOR_TYPES.contains(actorType)) {
            throw new IllegalArgumentException(
                    "actorType must be one of " + ACTOR_TYPES + ", got '" + actorType + "'");
        }
        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("unrecognized action: '" + action + "'");
        }
        if (affectedArtifact.isBlank()) {
            throw new IllegalArgumentException(
                    "affectedArtifact must not be blank — mandatory field 4 names WHAT was affected");
        }
        if (!RESULTS.contains(result)) {
            throw new IllegalArgumentException("result must be one of " + RESULTS + ", got '" + result + "'");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason must not be blank — an unexplained audit record is not auditable "
                            + "(the schema's own words)");
        }
        if (stageNumber != null && (stageNumber < 1 || stageNumber > 12)) {
            throw new IllegalArgumentException("stageNumber must be 1..12, got " + stageNumber);
        }
        if (executorKindUsed != null && !EXECUTOR_KINDS.contains(executorKindUsed)) {
            throw new IllegalArgumentException("unrecognized executorKindUsed: '" + executorKindUsed + "'");
        }
        if (correctsEventId != null && correctsEventId.equals(eventId)) {
            throw new IllegalArgumentException(
                    "an audit event cannot correct itself: correctsEventId must name a DIFFERENT, "
                            + "prior record");
        }
    }

    /**
     * The shape {@code contracts/audit-event.schema.json} validates. Optional fields absent here are
     * OMITTED, never emitted as {@code null} — the same convention {@code GateDecision.toJson()} uses.
     *
     * @throws IllegalStateException if {@code eventId} is not yet assigned — the store must record this
     *                               event first
     */
    public JsonNode toJson() {
        if (eventId == null) {
            throw new IllegalStateException(
                    "cannot serialize an audit event with no assigned eventId; record it first");
        }
        ObjectNode node = JSON.createObjectNode();
        node.put("eventId", String.valueOf(eventId));
        node.put("runId", runId.toString());
        if (stageNumber != null) {
            node.put("stageNumber", stageNumber);
        }
        node.put("actorType", actorType);
        node.put("action", action);
        node.put("occurredAt", occurredAt.toString());
        node.put("affectedArtifact", affectedArtifact);
        node.put("result", result);
        node.put("reason", reason);
        if (executorKindUsed != null) {
            node.put("executorKindUsed", executorKindUsed);
        }
        if (correctsEventId != null) {
            node.put("correctsEventId", String.valueOf(correctsEventId));
        }
        return node;
    }
}
