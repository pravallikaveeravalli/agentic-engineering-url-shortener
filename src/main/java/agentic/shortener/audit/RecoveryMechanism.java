package agentic.shortener.audit;

/**
 * How a failure was recovered from. Task T116. FR-ORC-024.
 *
 * <p><strong>Five values, not six.</strong> {@code fallback} is deliberately absent: FR-ORC-015 was
 * retired by Decision K (CR-032) — every declared fallback in the design was a deterministic counterpart
 * for an AI-capable stage, and removing it left bounded retry then safe suspension as the entire
 * degradation story. V3's own {@code failure_recovery_mechanism_values} CHECK constraint already enforces
 * this at the database boundary; this enum is the Java side of that same closed set, so a seventh value
 * cannot be introduced at one layer without the other refusing it. CR-032's own reasoning applies here
 * exactly as it did to {@code StageState.FALLBACK} and {@code failure_event.recovery_mechanism}: an enum
 * value no code path can emit is a claim, not a classification.
 *
 * <p><strong>Rollback and compensation stay distinguishable here too</strong> — they are not merged into
 * one "reverted" bucket, because CL-009's own distinction (erasable local work vs. an irreversible effect
 * needing a forward-correcting action) is exactly the information an MTTR reader needs to tell a five-second
 * rollback apart from a compensation that took a human decision.
 */
public enum RecoveryMechanism {

    /** Recovered by {@code RetryPolicy} succeeding within the bound. */
    RETRY,

    /** Recovered by reversing an erasable effect (local, un-pushed work). */
    ROLLBACK,

    /** Recovered by a forward-correcting action for an effect that could not be erased. */
    COMPENSATION,

    /** Recovered by {@code ResumeService} reconciling a crashed-process node to persisted evidence. */
    RESUME,

    /** Recovered by a human decision — most often a gate approval that unblocked the node. */
    HUMAN;

    /** The lowercase form {@code failure_event.recovery_mechanism} stores, matching V3's CHECK values. */
    public String dbValue() {
        return name().toLowerCase();
    }

    /**
     * @throws IllegalArgumentException if {@code value} is not one of the five lowercase database values —
     *                                   in particular for {@code "fallback"}, which is a value the schema
     *                                   itself refuses (V3), not merely an unmapped one here
     */
    public static RecoveryMechanism fromDbValue(String value) {
        for (RecoveryMechanism mechanism : values()) {
            if (mechanism.dbValue().equals(value)) {
                return mechanism;
            }
        }
        throw new IllegalArgumentException("unrecognized recovery mechanism: '" + value + "'");
    }
}
