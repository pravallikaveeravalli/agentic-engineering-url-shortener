package agentic.shortener.delivery.ratelimit;

/**
 * One rate-limit decision. Tasks T054, T055. FR-URL-016.
 *
 * <p><strong>The tier is part of the answer, not a detail.</strong> FR-URL-016 defines three limits and
 * requires a throttled outcome to name <em>which one</em> was exceeded. With three tiers, "too many
 * requests" alone leaves a caller unable to tell whether to slow their own creation, their link's
 * traffic, or their account's traffic in aggregate — three different remedies.
 *
 * @param retryAfterSeconds how long until the window rolls. A refusal without one is a dead end
 */
public record RateLimitDecision(boolean allowed, String tier, long retryAfterSeconds) {

    static RateLimitDecision allowed(String tier) {
        return new RateLimitDecision(true, tier, 0L);
    }

    static RateLimitDecision throttled(String tier, long retryAfterSeconds) {
        return new RateLimitDecision(false, tier, retryAfterSeconds);
    }
}
