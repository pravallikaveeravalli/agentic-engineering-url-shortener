package agentic.shortener.orchestration.state;

/**
 * Why a run suspended. Task T091. FR-ORC-017.
 *
 * <p>Five classes, and the reason each is named rather than collapsed into one "suspended" state is triage: a
 * run that stopped because nobody answered a gate needs a person, and a run that stopped because a policy check
 * is a mandatory FAIL needs the change fixed. A suspension reason that read only "suspended" would make every
 * one of those the same ticket.
 */
public enum SuspensionTrigger {

    /** Nobody decided within the gate wait. The one trigger that is somebody else's turn, not a problem. */
    GATE_TIMEOUT,

    /** A failure the two-vote rule ruled permanent, with nothing left to try. */
    UNRECOVERABLE_FAILURE,

    /** A mandatory policy check returned FAIL, which blocks downstream progression. */
    BLOCKING_POLICY_FAIL,

    /**
     * The failure could not be classified.
     *
     * <p>Distinct from {@link #UNRECOVERABLE_FAILURE} on purpose: a known-permanent failure and a failure
     * nobody could categorise look identical in the run's state and mean different things. The second says the
     * system met something it does not understand, which is worth knowing.
     */
    UNRECOGNIZED_FAILURE_CLASSIFICATION,

    /**
     * An effect was applied and no compensating action is known for it.
     *
     * <p>The trigger that must never be silent: something has happened that cannot be corrected, so the run
     * stops with the effect recorded rather than attempting a plausible recovery (FR-ORC-016 rule 4).
     */
    EFFECT_WITH_NO_COMPENSATING_ACTION
}
