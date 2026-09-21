package agentic.shortener.delivery.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A per-key fixed-window counter. Shared by both rate-limit tiers. FR-URL-016, EC-013.
 *
 * <p><strong>Fixed window, and the trade-off is stated rather than hidden.</strong> A fixed window
 * permits up to twice the limit across a window boundary — the last instant of one and the first of the
 * next. A sliding window would not, at the cost of keeping every timestamp instead of one count. For
 * PVT-012 and PVT-013, which exist to stop hammering rather than to meter billing, the burst is
 * acceptable and the memory profile is the reason: a popular link would otherwise hold six hundred
 * instants per minute, per code, forever.
 *
 * <p><strong>In-process, and therefore per instance.</strong> Two instances would each allow the limit.
 * That is a property of a demonstration that runs one instance, not a design for a fleet; a shared
 * counter would need the store or a cache on the redirect's critical path, which FR-URL-010 and PVT-001
 * both argue against. Named here so it is a known limit rather than a discovered one.
 *
 * <p><strong>Stale keys are pruned</strong>, because a counter that never forgets is a memory leak with
 * a security label on it: every short code ever followed would be retained for the life of the process.
 */
final class FixedWindowCounter {

    private record Window(Instant startedAt, int count) {
    }

    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** Prune when the map grows past this. Cheap, amortised, and bounded. */
    private static final int PRUNE_THRESHOLD = 10_000;

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    FixedWindowCounter(int limit, Clock clock) {
        if (limit <= 0) {
            // A limit of zero would refuse everything while looking configured, which is the worst
            // failure available: the service appears up and serves nobody.
            throw new IllegalArgumentException(
                    "rate limit must be positive; " + limit + " would refuse every request");
        }
        this.limit = limit;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Counts one attempt and decides.
     *
     * <p>The whole decision happens inside {@code compute}, so the count and the comparison cannot be
     * separated by another thread. A read-then-write would let two callers both see the limit unmet and
     * both proceed, which is exactly the traffic shape EC-013 describes.
     */
    RateLimitDecision check(String key, String tier) {
        Objects.requireNonNull(key, "key");
        Instant now = clock.instant();
        if (windows.size() > PRUNE_THRESHOLD) {
            prune(now);
        }

        Window updated = windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.startedAt().plus(WINDOW))) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.count() + 1);
        });

        if (updated.count() <= limit) {
            return RateLimitDecision.allowed(tier);
        }
        long retryAfter = Duration.between(now, updated.startedAt().plus(WINDOW)).toSeconds();
        return RateLimitDecision.throttled(tier, Math.max(1L, retryAfter));
    }

    private void prune(Instant now) {
        windows.entrySet().removeIf(entry ->
                !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
    }
}
