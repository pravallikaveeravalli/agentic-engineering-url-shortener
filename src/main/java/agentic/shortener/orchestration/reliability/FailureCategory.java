package agentic.shortener.orchestration.reliability;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The closed standard failure vocabulary. Task T083. FR-ORC-014 (CL-006).
 *
 * <p><strong>Closed on purpose.</strong> A stage's declared retryable set is expressed over these six and
 * nothing else, and {@code RetryRuling} (T084) rules permanent on anything a declared set does not name.
 * So a seventh category is not an addition — it is a silent narrowing of what retries, applied to every
 * stage at once, because no existing declaration mentions it.
 *
 * <p><strong>No vendor taxonomy reaches here.</strong> Provider knowledge lives in
 * {@link ProviderFailureTranslator}: the core never learns that a store speaks SQLSTATE or that a
 * subprocess throws {@code IOException} when a binary is missing. That was the reason the repaired
 * static-list option was rejected — a taxonomy in the core leaks upward one exception name at a time, and
 * each one looks harmless.
 */
public enum FailureCategory {

    /** The work did not finish in the allowed time. Retryable only where the effect is repeat-safe. */
    TIMEOUT,

    /** The dependency could not be reached or reported itself unable to serve. */
    UNAVAILABLE,

    /** The dependency refused because of a quota. Distinct from UNAVAILABLE: waiting is the remedy. */
    RATE_LIMITED,

    /** The input was rejected. Retrying identical input cannot change the answer. */
    INVALID_INPUT,

    /** A defect on our side of the boundary — malformed output, a broken invariant. Never retried. */
    INTERNAL,

    /**
     * The failure could not be classified.
     *
     * <p>The default-deny value. EC-031/EC-032: an unrecognized failure never defaults to retryable,
     * mirroring EC-025's rule for policy checks. {@link FailureEnvelope} refuses to carry a retryable
     * proposal alongside it, so the rule holds at the point the envelope is built rather than depending on
     * every ruling remembering it.
     */
    UNKNOWN;

    private static final Set<FailureCategory> DECLARED =
            Collections.unmodifiableSet(EnumSet.allOf(FailureCategory.class));

    /**
     * The set other code checks membership against.
     *
     * <p>Derived from the enum rather than listed, so the two cannot drift: a hand-written list is a second
     * place to add a category and the place someone forgets.
     */
    public static Set<FailureCategory> declared() {
        return DECLARED;
    }
}
