package agentic.shortener.orchestration.lineage;

/**
 * Where an ambiguity stands. Task T082. FR-ORC-010, FR-ORC-011.
 *
 * <p>{@link #NOT_MATERIAL} is the state DS-A requires be inspectable rather than silent: a well-formed
 * requirement that correctly needed no clarification is not the absence of a record, it is a record whose
 * {@code no_clarification_reason} says what was checked. See {@link AmbiguityRecord}.
 */
public enum ResolutionState {

    /** Found, and material enough to need a human answer. */
    MATERIAL_PENDING,

    /** A {@link ClarificationDecision} has answered it. */
    RESOLVED,

    /** Checked for and not found. Requires a substantive {@code no_clarification_reason}. */
    NOT_MATERIAL
}
