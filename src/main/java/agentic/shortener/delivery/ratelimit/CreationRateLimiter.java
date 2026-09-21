package agentic.shortener.delivery.ratelimit;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Creation throttling, per creator. Task T054. FR-URL-016, EC-013. PVT-012.
 *
 * <p>Sixty a minute per authenticated creator. The tier is per <em>creator</em> and not per connection
 * or per address, because creation is authenticated (FR-URL-018) and the creator is the thing whose
 * behaviour the limit is about — an address limit would throttle an office and miss a script.
 *
 * <p><strong>On by default.</strong> FR-URL-016's negative clause forbids throttling being silently
 * disabled by default configuration, and {@code RateLimiterTest} asserts both the flag and the numbers,
 * because a limit configured to a million would satisfy "enabled: true" and throttle nothing.
 */
public final class CreationRateLimiter {

    /** Named in the throttled response, so a caller knows which of the three limits they hit. */
    public static final String TIER = "per-creator-creation";

    private final FixedWindowCounter counter;

    public CreationRateLimiter(int limitPerMinute, Clock clock) {
        this.counter = new FixedWindowCounter(limitPerMinute, clock);
    }

    public RateLimitDecision check(UUID creatorId) {
        Objects.requireNonNull(creatorId, "creatorId");
        return counter.check(creatorId.toString(), TIER);
    }
}
