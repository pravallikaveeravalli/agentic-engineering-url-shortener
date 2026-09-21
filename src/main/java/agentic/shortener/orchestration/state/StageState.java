package agentic.shortener.orchestration.state;

/**
 * The twelve stage states. Task T078. FR-ORC-006.
 *
 * <p><strong>{@link #SKIPPED} satisfies a join, and that is load-bearing.</strong> S4 is skipped when
 * there is no material ambiguity, and S5 must still become ready — so an {@code ALL} join treats a
 * skipped branch as complete. Without that, the S3 conditional would need different edges depending on
 * which way it went, and the topology would stop being declared data.
 *
 * <p><strong>{@link #FALLBACK} is retained and currently unreachable.</strong> The frozen contract
 * declares it, and CR-032 did not remove it — that record retired {@code fallback} from
 * {@code failure_event.recovery_mechanism}, a different enum. But with FR-ORC-015 retired (Decision K)
 * nothing drives a node into this state any more. It is kept because the contract keeps it, and the
 * situation is recorded in {@code StageStateTest} rather than asserted away, because CR-032's own
 * reasoning — "an enum value that can never be emitted is a claim, not a classification" — applies here
 * too.
 */
public enum StageState {

    /** Dependencies unmet. */
    BLOCKED(false, false),

    /** Dependencies met, not yet started. */
    READY(false, false),

    RUNNING(false, false),

    /** Waiting on a human gate. Left only by a recorded decision (T079, prohibition 2). */
    AWAITING_APPROVAL(false, false),

    /** Waiting out a retry backoff. */
    RETRY_WAIT(false, false),

    /** Retained because the contract retains it; see the class note. */
    FALLBACK(false, false),

    /** Reversing an erasable effect. */
    ROLLING_BACK(false, false),

    /** Forward-correcting an effect that cannot be erased. */
    COMPENSATING(false, false),

    SUCCEEDED(true, true),
    FAILED(true, false),

    /** Superseded by a replan. Produced nothing downstream may rely on. */
    INVALIDATED(true, false),

    /** Not taken. Counts as complete for a join — see the class note. */
    SKIPPED(true, true);

    private final boolean terminal;
    private final boolean satisfiesJoin;

    StageState(boolean terminal, boolean satisfiesJoin) {
        this.terminal = terminal;
        this.satisfiesJoin = satisfiesJoin;
    }

    /** Whether a node in this state has finished, however it finished. */
    public boolean terminal() {
        return terminal;
    }

    /**
     * Whether a dependency in this state lets an {@code ALL} join proceed.
     *
     * <p>EC-018: a join does not proceed while any required branch is incomplete <em>or failed</em>. So
     * this is narrower than {@link #terminal()} — {@code FAILED} and {@code INVALIDATED} are finished
     * and do not satisfy anything.
     */
    public boolean satisfiesJoin() {
        return satisfiesJoin;
    }
}
