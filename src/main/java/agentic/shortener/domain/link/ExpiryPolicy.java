package agentic.shortener.domain.link;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Expiry policy. Task T046. FR-URL-009. EC-014. PVT-011.
 *
 * <p><strong>The boundary is decided here, once.</strong> FR-URL-009 requires the expiry instant to be
 * treated <em>consistently</em>, which means somebody has to answer "is a link expired exactly at its
 * expiry instant?" and write the answer down. It is <strong>yes</strong>: at {@code expiresAt} the link
 * is expired. The alternative would make a link alive for one indivisible instant no clock can reliably
 * observe, which is a definition nothing can be tested against.
 *
 * <p>{@link ShortLink#isExpiredAt} implements the same rule at resolution time. {@code ExpiryPolicyTest}
 * asserts that the two agree, because a one-instant disagreement between creation and resolution would
 * let a link be accepted and then be dead on arrival.
 *
 * <p><strong>EC-014: an expiry at or before now is refused.</strong> Such a link would be minted, stored,
 * consume a code from the keyspace, and answer 410 to its very first follower. "At or before" rather than
 * "before" follows from the boundary rule above — deciding the boundary one way and this refusal the
 * other is exactly the inconsistency the requirement is about.
 *
 * <p>The clock is injected. At a 30-day default TTL, waiting for an expiry is not a slow test but an
 * impossible one.
 */
public final class ExpiryPolicy {

    /** PVT-011: a bounded default rather than unbounded growth. */
    public static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final Clock clock;

    public ExpiryPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Resolves the expiry to store.
     *
     * @param requested the caller's expiry, or {@code null} for PVT-011's default
     * @return the caller's instant, honoured exactly, or now plus the default TTL
     * @throws IllegalArgumentException if the requested expiry is at or before now (EC-014). The message
     *                                  names the rule and never the submitted value
     */
    public Instant resolve(Instant requested) {
        Instant now = clock.instant();
        if (requested == null) {
            return now.plus(DEFAULT_TTL);
        }
        if (!requested.isAfter(now)) {
            throw new IllegalArgumentException(
                    "expiry must be strictly in the future: a link must not be created already expired "
                            + "(FR-URL-009, EC-014)");
        }
        return requested;
    }
}
