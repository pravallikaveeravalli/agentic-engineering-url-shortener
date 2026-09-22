package agentic.shortener.delivery.ratelimit;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Redirect throttling, per creator, aggregated across every link that creator owns. Task T136a. FR-URL-016.
 * PVT-014.
 *
 * <p>Three thousand a minute, per creator, independent of the existing per-code tier (600/minute/code) —
 * the noisy-neighbour control PVT-013 alone cannot provide: a creator with enough links can put well past
 * PVT-014 through the service while every individual link stays inside its own per-code budget. Deferred at
 * baseline ({@code docs/delivery/baseline-omissions.md} entry 1) and closed by this class, this turn.
 *
 * <p><strong>This tier legitimately knows the creator</strong> — unlike {@link RedirectRateLimiter}, whose
 * whole non-disclosure argument is "there is no creator parameter, so there is no creator to disclose."
 * Counting redirect traffic per creator requires the creator id, by definition. What must not exist is a
 * path for that id to reach a public follower: {@link #check} returns the same {@link RateLimitDecision}
 * shape every tier already returns — {@code allowed}/{@code tier}/{@code retryAfterSeconds}, no creator
 * field anywhere in it — so the id this class necessarily takes as input never appears in what a caller's
 * response is built from. {@code RateLimiterTest} asserts this by reflecting on {@code RateLimitDecision}
 * itself, not on this class's own signature.
 *
 * <p><strong>In-process, same as the other two tiers.</strong> No external counter store, cache, or shared
 * state — {@link FixedWindowCounter}'s own javadoc already discloses the single-instance limitation this
 * inherits; T136a's own security gate confirmed this is the intended posture, not a default nobody chose
 * (docs/governance/gate-decisions/ds-b/t136a-security-gate-approved.md).
 */
public final class AggregateRedirectLimiter {

    /** Named in the throttled response. Names the DIMENSION (aggregated across a creator's links), never a
     * specific creator — the same distinction {@link CreationRateLimiter#TIER} already draws for the
     * authenticated creation path, applied here to an anonymous one. */
    public static final String TIER = "per-creator-aggregate";

    private final FixedWindowCounter counter;

    public AggregateRedirectLimiter(int limitPerMinute, Clock clock) {
        this.counter = new FixedWindowCounter(limitPerMinute, clock);
    }

    public RateLimitDecision check(UUID creatorId) {
        Objects.requireNonNull(creatorId, "creatorId");
        return counter.check(creatorId.toString(), TIER);
    }
}
