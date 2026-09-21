package agentic.shortener.orchestration.state;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Idle retention, and when a suspended run is abandoned. Task T092. FR-ORC-032 (CL-005). PVT-015.
 *
 * <h2>The clock runs from last activity, never from creation</h2>
 *
 * <p>This is the whole design and the reason EC-036 needs no separate reset rule. {@code auto_abandon_at} is
 * <em>derived</em> from {@code last_activity_at}, so recording activity moves the deadline as a consequence
 * rather than as a second step somebody has to remember. Measured from creation, a run that had been worked on
 * for months would be reaped while somebody was still using it — and the reset would have to be applied by
 * every code path that touches a run, which is the kind of rule that holds until the tenth path.
 *
 * <h2>Abandonment is a lifecycle transition, not a purge</h2>
 *
 * <p>CR-017: there is no purge anywhere. An abandoned run's records stay in the tables like every other
 * record, its history keeps growing by one transition, and nothing is removed. The retention period bounds how
 * long a run stays <em>open</em>, not how long its evidence is kept.
 *
 * <p><strong>And it is never an approval.</strong> EC-037: a deadline expiring while a gate is pending
 * abandons the run terminally and leaves the gate undecided. Constitution III — silence is never approval, and
 * neither is a deadline.
 */
public final class RetentionPolicy {

    /** PVT-015. */
    public static final Duration IDLE_PERIOD = Duration.ofDays(90);

    /**
     * Cited in the audit row for every automatic abandonment.
     *
     * <p>Versioned because the authority for a system actor acting alone is a <em>pre-approved policy</em>, and
     * an audit row citing "the retention policy" without saying which one cannot establish that the policy in
     * force at the time permitted what happened.
     */
    public static final String VERSION = "retention-policy-1.0.0";

    /** The disclosed deadline. Derived, so activity resets it without anything else being called. */
    public Instant autoAbandonAt(Instant lastActivity) {
        Objects.requireNonNull(lastActivity, "lastActivity");
        return lastActivity.plus(IDLE_PERIOD);
    }

    /**
     * Whether the disclosed deadline has passed.
     *
     * <p>Strictly after: a run at exactly its deadline has not yet been idle for longer than the period, and
     * reaping on the boundary would make the disclosed time a moment early — small, and exactly the kind of
     * off-by-one that turns a promise into an approximation.
     */
    public boolean expired(Instant autoAbandonAt, Instant now) {
        Objects.requireNonNull(autoAbandonAt, "autoAbandonAt");
        Objects.requireNonNull(now, "now");
        return now.isAfter(autoAbandonAt);
    }
}
