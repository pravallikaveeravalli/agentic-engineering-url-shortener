package agentic.shortener.orchestration.gates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * KE-11: the recorded outcome of an {@link ApprovalGate}. Task T058. FR-ORC-013.
 *
 * <p>Two guards enforced in the constructor, both structural rather than conventional:
 *
 * <ul>
 *   <li><strong>{@code CHANGES_REQUESTED} requires a non-empty change list.</strong> There is no sixth
 *       value and none meaning "no response, proceed" (T059's own guard); an empty list on this outcome
 *       would be the same defect wearing a different shape — changes must be enumerated, never implied.
 *   <li><strong>{@code ESCALATED} requires an escalation target.</strong> Recording only that something
 *       exceeded authority, without saying what decision it exceeds authority FOR, leaves the escalation
 *       unactionable.
 *   <li><strong>{@code APPROVED} structurally requires a human actor</strong> (CR-021). The schema's own
 *       conditional says the same thing; this constructor is the same rule enforced one layer earlier,
 *       before a value that would fail schema validation is even built.
 * </ul>
 *
 * @param decisionId   {@code null} for a decision not yet submitted to the store, which assigns it. A
 *                     read-back decision always carries one
 * @param supersedesDecisionId EC-020: the decision this one voids, referenced by id, never a deletion or
 *                             an edit of the original
 */
public record GateDecision(String decisionId, UUID runId, String gateId, Integer stageNumber,
                           GateClass gateClass, GateOutcome outcome, Actor actor, Instant decidedAt,
                           String reason, String repositoryRecordPath, List<String> requestedChanges,
                           String escalationTarget, Long supersedesDecisionId) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public GateDecision {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(gateClass, "gateClass");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(decidedAt, "decidedAt");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(repositoryRecordPath, "repositoryRecordPath");
        Objects.requireNonNull(requestedChanges, "requestedChanges");
        requestedChanges = List.copyOf(requestedChanges);

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "a decision without a reason is not a gate record (T058's own guard)");
        }
        if (outcome == GateOutcome.CHANGES_REQUESTED && requestedChanges.isEmpty()) {
            throw new IllegalArgumentException(
                    "CHANGES_REQUESTED with an empty change list is invalid: there is no sixth value "
                            + "and none meaning \"no response, proceed\" (T059's guard) — changes "
                            + "must be enumerated, never implied");
        }
        if (outcome == GateOutcome.ESCALATED
                && (escalationTarget == null || escalationTarget.isBlank())) {
            throw new IllegalArgumentException(
                    "ESCALATED requires an escalation target: what decision exceeds the current "
                            + "owner's authority. Recording only that something exceeded it leaves the "
                            + "escalation unactionable");
        }
        if (!"human".equals(actor.actorType())) {
            // Unconditional, not narrowed to APPROVED: gate_decision_actor_is_human requires a human on
            // EVERY row regardless of outcome, and the first version of this guard only matched APPROVED
            // — a REJECTED, CHANGES_REQUESTED or ESCALATED decision from 'system' would have built
            // successfully here and failed only later, at GateStore.recordDecision's INSERT, against the
            // DB CHECK. T063 (EC-024) is what a 'system' actor has recorded authority for NOTHING that
            // goes through a GateDecision at all — its only authority anywhere is deadline expiry and
            // retention-driven abandonment, neither of which is a GateDecision.
            throw new IllegalArgumentException(
                    "CR-021, EC-024: only a human decides a gate, for any outcome. actorType='"
                            + actor.actorType() + "' is refused structurally, before it ever reaches the "
                            + "store");
        }
    }

    /** For a not-yet-submitted decision: the store assigns {@code decisionId} once it is recorded. */
    public GateDecision(UUID runId, String gateId, Integer stageNumber, GateClass gateClass,
                        GateOutcome outcome, Actor actor, Instant decidedAt, String reason,
                        String repositoryRecordPath, List<String> requestedChanges,
                        String escalationTarget, Long supersedesDecisionId) {
        this(null, runId, gateId, stageNumber, gateClass, outcome, actor, decidedAt, reason,
                repositoryRecordPath, requestedChanges, escalationTarget, supersedesDecisionId);
    }

    /**
     * The persisted-record shape {@code approval.schema.json} validates.
     *
     * <p>Optional fields absent from the record are OMITTED, never emitted as {@code null}: the contract
     * types {@code requestedChanges} as an array with no {@code null} option, so a present-but-null value
     * would fail validation where an absent one passes.
     *
     * @throws IllegalStateException if {@code decisionId} is not yet assigned — the store must have
     *                               recorded this decision first
     */
    public JsonNode toJson() {
        if (decisionId == null) {
            throw new IllegalStateException(
                    "cannot serialize a decision with no assigned decisionId; record it first");
        }
        ObjectNode node = JSON.createObjectNode();
        node.put("decisionId", decisionId);
        node.put("runId", runId.toString());
        if (stageNumber != null) {
            node.put("stageNumber", stageNumber);
        }
        node.put("gateClass", gateClass.name());
        node.put("outcome", outcome.name());

        ObjectNode actorNode = JSON.createObjectNode();
        actorNode.put("actorType", actor.actorType());
        actorNode.put("identity", actor.identity());
        node.set("actor", actorNode);

        node.put("decidedAt", decidedAt.toString());
        node.put("reason", reason);
        node.put("repositoryRecordPath", repositoryRecordPath);

        if (!requestedChanges.isEmpty()) {
            ArrayNode changes = JSON.createArrayNode();
            requestedChanges.forEach(changes::add);
            node.set("requestedChanges", changes);
        }
        if (escalationTarget != null) {
            node.put("escalationTarget", escalationTarget);
        }
        if (supersedesDecisionId != null) {
            node.put("supersedes", String.valueOf(supersedesDecisionId));
        }
        return node;
    }
}
