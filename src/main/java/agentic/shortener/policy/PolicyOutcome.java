package agentic.shortener.policy;

/**
 * Exactly four outcome values a policy check can produce. Task T099. plan §9.
 *
 * <p>No {@code UNKNOWN}, no {@code SKIPPED} — EC-025's default-deny rule means a check that could not be
 * evaluated at all is recorded {@link #FAIL}, never {@link #PASS}. Matches {@code
 * contracts/policy-evaluation.schema.json}'s {@code outcome} enum exactly (underscore form on the wire;
 * Java identifiers cannot carry the hyphen plan §9's own prose uses).
 */
public enum PolicyOutcome {
    PASS,
    FAIL,
    EXCEPTION_REQUESTED,
    NOT_APPLICABLE
}
