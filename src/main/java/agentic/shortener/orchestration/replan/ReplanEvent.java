package agentic.shortener.orchestration.replan;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * KE-14: a recorded replanning action. Task T096. FR-ORC-019.
 *
 * <p>Plan §4's own words for what this preserves: "Cause, invalidated stages, voided approvals." Three
 * fields carry exactly that — {@code invalidatedNodes} as node keys, never stage numbers (CR-011/CR-013),
 * so a replan that invalidated one fan-out child and left its siblings intact is recorded precisely, not
 * approximated to "stage 7."
 *
 * @param replanEventId      {@code null} for a not-yet-recorded event, which the store assigns
 * @param triggeringNodeKey  the node whose changed output is the replan's cause — the closure is computed
 *                           downstream FROM this node
 * @param invalidatedNodes   every node key actually transitioned to {@code INVALIDATED} by this event —
 *                           excludes any in-flight node EC-019 left untouched
 * @param voidedDecisionIds  the {@code gate_decision_id}s superseded because their node was invalidated
 *                           (EC-020)
 */
public record ReplanEvent(Long replanEventId, UUID runId, String cause, String triggeringNodeKey,
                          Instant occurredAt, List<String> invalidatedNodes, List<Long> voidedDecisionIds) {

    public ReplanEvent {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(triggeringNodeKey, "triggeringNodeKey");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (cause.isBlank()) {
            throw new IllegalArgumentException(
                    "a replan without a cause is not a governance record a reviewer can act on");
        }
        invalidatedNodes = List.copyOf(invalidatedNodes);
        voidedDecisionIds = List.copyOf(voidedDecisionIds);
    }
}
