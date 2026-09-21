package agentic.shortener.orchestration.gates;

import java.util.List;

/**
 * The four SUBMITTABLE outcomes. Task T058. FR-ORC-013.
 *
 * <p><strong>Four, not five.</strong> {@code TIMED_OUT} is Constitution III's "silence is never approval"
 * made structural rather than remembered: it exists in the persisted record's vocabulary (a decision that
 * timed out still needs an outcome to report), but it is not a value a human ever submits — nothing
 * produces it except the gate-wait deadline expiring. Keeping it out of this enum entirely, and giving it
 * to {@link GateOutcomeHandler} as the one path a caller cannot reach, is what makes a caller unable to
 * even attempt to submit it — matching {@code openapi.yaml}'s own {@code GateDecisionRequest.outcome}
 * enum, which also omits it.
 */
public enum GateOutcome {

    /** The stage succeeds. */
    APPROVED,

    /** The run terminates. */
    REJECTED,

    /** Returns to the owning stage with changes enumerated; downstream is invalidated. */
    CHANGES_REQUESTED,

    /** Records what exceeds the deciding actor's authority. */
    ESCALATED;

    /** Every value of this enum, IS every submittable outcome — there is no sixth, hidden or otherwise. */
    public static List<GateOutcome> submittable() {
        return List.of(values());
    }
}
