package agentic.shortener.orchestration.gates;

/**
 * A decision's repository record does not exist, or is not substantive, in the working tree. Task T062.
 * FR-ORC-013 (CR-001).
 *
 * <p>{@code CLAUDE.md}'s own rule: a decision existing only in workflow state or the database does not
 * satisfy the gate. This is the refusal that rule produces.
 */
public final class UnmaterializedDecisionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnmaterializedDecisionException(String message) {
        super(message);
    }
}
