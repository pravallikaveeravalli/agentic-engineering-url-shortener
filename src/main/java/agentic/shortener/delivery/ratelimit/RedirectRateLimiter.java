package agentic.shortener.delivery.ratelimit;

import java.time.Clock;
import java.util.Objects;

/**
 * Redirect throttling, per short code. Task T055. FR-URL-016. PVT-013.
 *
 * <p>Six hundred a minute per code — hotspot protection, so no single link can be hammered. Per code and
 * not global, so one popular link cannot take every other link down with it.
 *
 * <p><strong>This tier never learns who owns the link.</strong> FR-URL-016 forbids a throttled response
 * disclosing the owning creator's identity to a public follower, and the enforcement is the signature:
 * there is no creator parameter, so there is no creator to disclose. Not even the tier name points at
 * one, because the tier name reaches the follower. {@code RateLimiterTest} asserts both by reflection.
 *
 * <p><strong>The third tier is not here, and that is a recorded decision.</strong> FR-URL-016's
 * per-creator <em>aggregate</em> redirect limit (PVT-014) is deferred to the brownfield scenario and
 * disclosed in {@code docs/delivery/baseline-omissions.md} with T136a named as the run that closes it.
 * FR-URL-016 remains binding in full; this is a scheduled omission, not an accepted gap, and the
 * difference between those two is entirely whether that record exists. A test asserts that it does.
 *
 * <p><strong>The accepted trade-off</strong>, recorded because FR-URL-016 requires it to be: followers of
 * a popular creator's links may be throttled through no fault of their own. That deliberately prioritises
 * service protection over unlimited availability of any one tenant's links.
 */
public final class RedirectRateLimiter {

    /** Named in the throttled response. Deliberately says nothing about who owns the link. */
    public static final String TIER = "per-code";

    private final FixedWindowCounter counter;

    public RedirectRateLimiter(int limitPerMinute, Clock clock) {
        this.counter = new FixedWindowCounter(limitPerMinute, clock);
    }

    public RateLimitDecision check(String shortCode) {
        Objects.requireNonNull(shortCode, "shortCode");
        return counter.check(shortCode, TIER);
    }
}
