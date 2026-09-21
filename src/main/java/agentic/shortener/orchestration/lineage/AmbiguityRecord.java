package agentic.shortener.orchestration.lineage;

import java.util.Objects;
import java.util.UUID;

/**
 * KE-08: a detected ambiguity, and — when there was none to detect — the proof that the check happened.
 * Task T082. FR-ORC-010.
 *
 * @param qualityChecksPerformed what was actually checked. Required unconditionally: even a
 *                              {@link ResolutionState#MATERIAL_PENDING} ambiguity's discovery implies
 *                              checks were run, and the field is what lets a reviewer tell "nothing was
 *                              checked" from "checked and found nothing" — the two absences DS-A exists to
 *                              tell apart
 * @param noClarificationReason required, and required substantive, exactly when
 *                              {@code resolutionState == NOT_MATERIAL} — enforced structurally by
 *                              {@code ambiguity_record_reason_iff_not_material} rather than left to the
 *                              caller's discipline, because the whole point of this field is that a miss
 *                              is indistinguishable from a correct non-detection unless it is recorded and
 *                              readable
 */
public record AmbiguityRecord(UUID id, AmbiguityClass ambiguityClass, String affectedPath,
                              ResolutionState resolutionState, String qualityChecksPerformed,
                              String noClarificationReason) {

    public AmbiguityRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ambiguityClass, "ambiguityClass");
        Objects.requireNonNull(affectedPath, "affectedPath");
        Objects.requireNonNull(resolutionState, "resolutionState");
        Objects.requireNonNull(qualityChecksPerformed, "qualityChecksPerformed");
        if (qualityChecksPerformed.isBlank()) {
            throw new IllegalArgumentException(
                    "qualityChecksPerformed must not be blank — a placeholder here defeats the reason "
                            + "the field exists");
        }
        boolean hasReason = noClarificationReason != null && !noClarificationReason.isBlank();
        if (resolutionState == ResolutionState.NOT_MATERIAL && !hasReason) {
            throw new IllegalArgumentException(
                    "DS-A: a NOT_MATERIAL ambiguity must carry a substantive no_clarification_reason, or "
                            + "a correct non-detection is indistinguishable from a miss nobody checked for");
        }
        if (resolutionState != ResolutionState.NOT_MATERIAL && hasReason) {
            throw new IllegalArgumentException(
                    "no_clarification_reason is only meaningful for NOT_MATERIAL; carrying one alongside "
                            + resolutionState + " would claim a non-detection inspection that did not "
                            + "happen for something that was, in fact, detected");
        }
    }
}
