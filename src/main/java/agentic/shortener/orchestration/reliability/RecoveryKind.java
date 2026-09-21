package agentic.shortener.orchestration.reliability;

/**
 * What kind of recovery an effect gets. Task T088. FR-ORC-016 (CL-007).
 *
 * <p>Four values, and the last two are the ones a careless design would collapse into one. Both mean "no
 * action will be applied", and they must never be recorded as the same thing: the run history has to let a
 * reviewer tell an effect that needed no correction from an effect nobody knows how to correct. The first is
 * fine; the second must suspend.
 *
 * <p>The first two must stay distinct for the mirror-image reason. T089 and T090 share a guard —
 * <em>compensation MUST NOT be described as rollback or vice versa</em> — because "we undid it" and "we
 * corrected forward and the original is still there" are different claims about the state of the world.
 */
public enum RecoveryKind {

    /** The effect was discarded. Only legitimate for local un-pushed work (CL-004). */
    ROLLBACK,

    /** The effect stands and a correction was appended. The default for anything unclassified. */
    COMPENSATION,

    /** The effect changed nothing that needs correcting. Still recorded — see the class note. */
    NOTHING_TO_COMPENSATE,

    /**
     * No compensating action is known.
     *
     * <p>Not a recovery at all, and deliberately not recordable as one: {@code CompensationRegister} refuses
     * to write a row for it, because a row would read as a correction that happened. The caller's response is
     * suspension (FR-ORC-017) — touch nothing and ask a human.
     */
    NONE_KNOWN
}
