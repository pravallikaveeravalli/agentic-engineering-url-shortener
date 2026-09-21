package agentic.shortener.orchestration.state;

/**
 * The six run states. Task T077. FR-ORC-017 (CL-005), NFR-REL-001.
 *
 * <p><strong>A state cannot be both terminal and resumable.</strong> That was a contradiction in the
 * approved specification until CL-005 resolved it. The terminal set is exactly {@link #COMPLETED},
 * {@link #REJECTED} and {@link #ABANDONED}; {@link #SAFE_STOP} is <em>suspended</em> and non-terminal,
 * because a suspended run is one a human can still decide about.
 *
 * <p>The contract's two conditionals are properties of the state itself here, CHECK constraints in V4,
 * and schema conditionals in {@code workflow-state.schema.json}. Three levels for one property, because
 * what is being prevented is a run that claims to be finished and is not, or the reverse — and the type
 * can be bypassed by a direct write while the constraint cannot.
 */
public enum RunState {

    /** Created, not yet started. */
    PENDING(false, false),

    /** In flight. */
    RUNNING(false, false),

    /**
     * Suspended. <strong>Not terminal.</strong> Left only by a human decision or by retention expiry
     * (T079's sixth prohibited transition), and carries a disclosed auto-abandonment time so that a
     * suspension is never open-ended and unannounced.
     */
    SAFE_STOP(false, true),

    COMPLETED(true, false),
    REJECTED(true, false),
    ABANDONED(true, false);

    private final boolean terminal;
    private final boolean suspended;

    RunState(boolean terminal, boolean suspended) {
        this.terminal = terminal;
        this.suspended = suspended;
    }

    public boolean terminal() {
        return terminal;
    }

    public boolean suspended() {
        return suspended;
    }

    /** Contract conditional 1: {@code terminalState} is non-null iff the run is terminal. */
    public boolean requiresTerminalState() {
        return terminal;
    }

    /** Contract conditional 2, first half: {@code autoAbandonAt} is non-null iff suspended. */
    public boolean requiresAutoAbandonAt() {
        return suspended;
    }

    /** Contract conditional 2, second half. A suspension with no reason is one nobody can act on. */
    public boolean requiresSuspensionReason() {
        return suspended;
    }
}
