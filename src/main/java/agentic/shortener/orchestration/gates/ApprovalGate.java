package agentic.shortener.orchestration.gates;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * KE-10: a pending checkpoint on a stage node. Task T058. FR-ORC-013.
 *
 * @param waitDeadline            when the run suspends absent a decision (PVT-006, uniform across all ten
 *                                gate classes — CR-023's proposed special deadline for NODE_OVERRUN was
 *                                withdrawn before application)
 * @param disclosedAutoAbandonAt  the computed retention deadline, shown in the SAME ask as the wait
 *                                deadline (CL-005 addendum, T060) rather than left for a reviewer to
 *                                discover by inspecting the run
 */
public record ApprovalGate(String gateId, UUID runId, String nodeKey, GateClass gateClass,
                           Instant waitDeadline, Instant disclosedAutoAbandonAt, Instant requestedAt) {

    public ApprovalGate {
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(gateClass, "gateClass");
        Objects.requireNonNull(waitDeadline, "waitDeadline");
        Objects.requireNonNull(disclosedAutoAbandonAt, "disclosedAutoAbandonAt");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (gateId.isBlank()) {
            throw new IllegalArgumentException("gateId must not be blank");
        }
        if (waitDeadline.isBefore(requestedAt)) {
            throw new IllegalArgumentException("waitDeadline must not precede requestedAt");
        }
        if (disclosedAutoAbandonAt.isBefore(waitDeadline)) {
            throw new IllegalArgumentException(
                    "disclosedAutoAbandonAt must not precede waitDeadline — a run cannot be "
                            + "disclosed as abandoned before its own gate wait has even elapsed");
        }
    }
}
