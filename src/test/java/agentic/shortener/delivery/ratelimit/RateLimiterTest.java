package agentic.shortener.delivery.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T054, T055, and T136a — all three rate-limit tiers FR-URL-016 defines. EC-013. PVT-012, PVT-013, PVT-014.
 *
 * <p><strong>The third tier was deferred, and is deferred no longer.</strong> The per-creator
 * <em>aggregate</em> redirect tier (PVT-014) was deliberately deferred to the brownfield scenario
 * (`docs/delivery/baseline-omissions.md` entry 1) and is closed by T136a, this turn.
 * {@link #theDeferredTierIsDisclosed} still asserts the historical disclosure record itself remains
 * intact (a closed entry is not a deleted one); {@link #theAggregateTierIsNowBuiltAndDisclosedAsClosed}
 * asserts the register now says so.
 *
 * <p><strong>A mutable clock, not sleeping.</strong> A window is a minute long. A test that waited one
 * out would add a minute to every build and still be racy at the boundary.
 *
 * <p>Fast tier.
 */
@DisplayName("T054/T055 rate limiting")
class RateLimiterTest {

    private static final Instant START = Instant.parse("2026-09-21T10:00:00Z");

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {
        private Instant now = START;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }
    }

    // ------------------------------------------------------------------ T054, per-creator creation

    @Test
    @DisplayName("PVT-012: exactly 60 creations a minute per creator, and the 61st is refused")
    void creationLimitIsSixtyPerMinute() {
        MovableClock clock = new MovableClock();
        CreationRateLimiter limiter = new CreationRateLimiter(60, clock);
        UUID creator = UUID.randomUUID();

        for (int i = 1; i <= 60; i++) {
            assertTrue(limiter.check(creator).allowed(), "attempt " + i + " should be allowed");
        }
        RateLimitDecision refused = limiter.check(creator);
        assertFalse(refused.allowed(), "the 61st must be refused: PVT-012 is 60 a minute");
    }

    @Test
    @DisplayName("the refusal NAMES ITS TIER, so a caller knows which limit they hit")
    void creationRefusalNamesItsTier() {
        // FR-URL-016's Accept clause: exceeding any of the three limits produces a distinguishable
        // throttled outcome NAMING WHICH TIER was exceeded. With three tiers, "too many requests" alone
        // leaves a caller unable to tell whether to slow their own creation or their links' traffic.
        MovableClock clock = new MovableClock();
        CreationRateLimiter limiter = new CreationRateLimiter(1, clock);
        UUID creator = UUID.randomUUID();

        limiter.check(creator);
        RateLimitDecision refused = limiter.check(creator);

        assertFalse(refused.allowed());
        assertEquals(CreationRateLimiter.TIER, refused.tier());
        assertTrue(refused.tier().contains("creation"), refused.tier());
        assertNotEquals(RedirectRateLimiter.TIER, refused.tier(), "the two tiers must be tellable apart");
    }

    @Test
    @DisplayName("creators have independent budgets")
    void creatorsAreIndependent() {
        MovableClock clock = new MovableClock();
        CreationRateLimiter limiter = new CreationRateLimiter(2, clock);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        limiter.check(alice);
        limiter.check(alice);
        assertFalse(limiter.check(alice).allowed(), "Alice is out");
        assertTrue(limiter.check(bob).allowed(),
                "Bob must not pay for Alice's traffic — the tier is PER CREATOR");
    }

    @Test
    @DisplayName("the budget returns when the window rolls")
    void windowRolls() {
        MovableClock clock = new MovableClock();
        CreationRateLimiter limiter = new CreationRateLimiter(2, clock);
        UUID creator = UUID.randomUUID();

        limiter.check(creator);
        limiter.check(creator);
        assertFalse(limiter.check(creator).allowed());

        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.check(creator).allowed(), "a rate limit that never released would be a ban");
    }

    @Test
    @DisplayName("a refusal says how long to wait, and the answer is within the window")
    void refusalSaysWhenToRetry() {
        MovableClock clock = new MovableClock();
        CreationRateLimiter limiter = new CreationRateLimiter(1, clock);
        UUID creator = UUID.randomUUID();

        limiter.check(creator);
        clock.advance(Duration.ofSeconds(20));
        RateLimitDecision refused = limiter.check(creator);

        assertFalse(refused.allowed());
        assertTrue(refused.retryAfterSeconds() > 0 && refused.retryAfterSeconds() <= 60,
                "retry-after must be inside the window; got " + refused.retryAfterSeconds());
        assertEquals(40, refused.retryAfterSeconds(),
                "twenty seconds into a sixty-second window leaves forty");
    }

    @Test
    @DisplayName("the counter is correct under concurrency")
    void concurrentCallersCannotExceedTheLimit() throws Exception {
        // EC-013 is a single caller at very high rate, which in practice means many threads. A limiter
        // that lost increments under load would let exactly the traffic it exists to stop straight
        // through.
        MovableClock clock = new MovableClock();
        CreationRateLimiter limiter = new CreationRateLimiter(60, clock);
        UUID creator = UUID.randomUUID();

        int callers = 200;
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            for (int i = 0; i < callers; i++) {
                pool.submit(() -> {
                    go.await();
                    if (limiter.check(creator).allowed()) {
                        allowed.incrementAndGet();
                    }
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(60, allowed.get(),
                "exactly the limit, not one more: increments were lost or double-counted");
    }

    @Test
    @DisplayName("a nonsensical limit is refused at construction")
    void limitMustBePositive() {
        // A limit of zero would refuse everything while looking configured, which is the worst failure
        // mode available: the service appears up and serves nobody.
        MovableClock clock = new MovableClock();
        assertThrows(IllegalArgumentException.class, () -> new CreationRateLimiter(0, clock));
        assertThrows(IllegalArgumentException.class, () -> new CreationRateLimiter(-1, clock));
        assertThrows(IllegalArgumentException.class, () -> new RedirectRateLimiter(0, clock));
    }

    // ------------------------------------------------------------------ T055, per-code redirects

    @Test
    @DisplayName("PVT-013: exactly 600 follows a minute per short code")
    void redirectLimitIsSixHundredPerMinutePerCode() {
        MovableClock clock = new MovableClock();
        RedirectRateLimiter limiter = new RedirectRateLimiter(600, clock);

        for (int i = 1; i <= 600; i++) {
            assertTrue(limiter.check("Hotcode").allowed(), "follow " + i + " should be allowed");
        }
        assertFalse(limiter.check("Hotcode").allowed(), "the 601st must be refused: PVT-013 is 600");
    }

    @Test
    @DisplayName("hotspot protection is PER CODE: one hammered link does not take the others down")
    void codesAreIndependent() {
        MovableClock clock = new MovableClock();
        RedirectRateLimiter limiter = new RedirectRateLimiter(2, clock);

        limiter.check("Aaaaaaa");
        limiter.check("Aaaaaaa");
        assertFalse(limiter.check("Aaaaaaa").allowed());
        assertTrue(limiter.check("Bbbbbbb").allowed(),
                "that is what makes this the HOTSPOT tier rather than a global one");
    }

    @Test
    @DisplayName("GUARD: the redirect tier never learns who owns the link")
    void redirectTierCannotIdentifyTheCreator() throws Exception {
        // FR-URL-016's negative clause: a throttled response MUST NOT disclose the owning creator's
        // identity to the public follower. Enforced by the signature — there is no creator to disclose,
        // because the limiter is never told one.
        for (java.lang.reflect.Method method : RedirectRateLimiter.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertNotEquals(UUID.class, parameter,
                        "method '" + method.getName() + "' takes a UUID; the per-code tier must not "
                                + "know the creator (FR-URL-016)");
            }
        }
        assertEquals(RedirectRateLimiter.TIER, "per-code");
        assertFalse(RedirectRateLimiter.TIER.contains("creator"),
                "not even the tier NAME may point at a creator: it reaches a public follower");
    }

    // ------------------------------------------------------------------ T136a, per-creator aggregate redirects

    @Test
    @DisplayName("PVT-014: exactly 3000 follows a minute per creator, aggregated across every link they own")
    void aggregateLimitIsThreeThousandPerMinutePerCreator() {
        MovableClock clock = new MovableClock();
        AggregateRedirectLimiter limiter = new AggregateRedirectLimiter(3000, clock);
        UUID creator = UUID.randomUUID();

        for (int i = 1; i <= 3000; i++) {
            assertTrue(limiter.check(creator).allowed(), "follow " + i + " should be allowed");
        }
        assertFalse(limiter.check(creator).allowed(), "the 3001st must be refused: PVT-014 is 3000");
    }

    @Test
    @DisplayName("the aggregate tier counts ACROSS every link a creator owns, not per code")
    void aggregateCountsAcrossLinks() {
        // The whole reason this tier exists (T-08, noisy neighbour): a per-code count alone cannot see
        // that the SAME creator's several links, each individually fine, sum to a problem.
        MovableClock clock = new MovableClock();
        AggregateRedirectLimiter limiter = new AggregateRedirectLimiter(3, clock);
        UUID creator = UUID.randomUUID();

        assertTrue(limiter.check(creator).allowed());
        assertTrue(limiter.check(creator).allowed());
        assertTrue(limiter.check(creator).allowed());
        assertFalse(limiter.check(creator).allowed(),
                "three follows already spent the budget, regardless of which of the creator's own codes "
                        + "each one was against — this limiter is never told a code at all");
    }

    @Test
    @DisplayName("creators have independent aggregate budgets")
    void aggregateCreatorsAreIndependent() {
        MovableClock clock = new MovableClock();
        AggregateRedirectLimiter limiter = new AggregateRedirectLimiter(2, clock);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        limiter.check(alice);
        limiter.check(alice);
        assertFalse(limiter.check(alice).allowed(), "Alice is out");
        assertTrue(limiter.check(bob).allowed(),
                "Bob must not pay for Alice's traffic — the tier is PER CREATOR");
    }

    @Test
    @DisplayName("the aggregate refusal NAMES ITS TIER, distinguishable from the other two")
    void aggregateRefusalNamesItsTier() {
        MovableClock clock = new MovableClock();
        AggregateRedirectLimiter limiter = new AggregateRedirectLimiter(1, clock);
        UUID creator = UUID.randomUUID();

        limiter.check(creator);
        RateLimitDecision refused = limiter.check(creator);

        assertFalse(refused.allowed());
        assertEquals(AggregateRedirectLimiter.TIER, refused.tier());
        assertEquals("per-creator-aggregate", refused.tier());
        assertNotEquals(CreationRateLimiter.TIER, refused.tier());
        assertNotEquals(RedirectRateLimiter.TIER, refused.tier());
    }

    @Test
    @DisplayName("the aggregate budget returns when the window rolls")
    void aggregateWindowRolls() {
        MovableClock clock = new MovableClock();
        AggregateRedirectLimiter limiter = new AggregateRedirectLimiter(2, clock);
        UUID creator = UUID.randomUUID();

        limiter.check(creator);
        limiter.check(creator);
        assertFalse(limiter.check(creator).allowed());

        clock.advance(Duration.ofSeconds(61));
        assertTrue(limiter.check(creator).allowed(), "a rate limit that never released would be a ban");
    }

    @Test
    @DisplayName("a nonsensical aggregate limit is refused at construction")
    void aggregateLimitMustBePositive() {
        MovableClock clock = new MovableClock();
        assertThrows(IllegalArgumentException.class, () -> new AggregateRedirectLimiter(0, clock));
        assertThrows(IllegalArgumentException.class, () -> new AggregateRedirectLimiter(-1, clock));
    }

    @Test
    @DisplayName("GUARD: the aggregate tier's own DECISION never carries the creator, even though its "
            + "CHECK does")
    void aggregateTierDecisionCannotIdentifyTheCreator() {
        // AggregateRedirectLimiter.check() necessarily takes a creator id -- unlike the per-code tier,
        // there IS a creator here, by definition (FR-URL-016's own per-creator aggregation requires it).
        // What must not exist is a path for that id to reach a public follower: RateLimitDecision -- what
        // RedirectController actually receives and turns into an HTTP response -- has no field of type
        // UUID anywhere, so the id this class's own input necessarily carries never appears in what a
        // caller's response is built from, regardless of which tier produced the decision.
        for (java.lang.reflect.RecordComponent component : RateLimitDecision.class.getRecordComponents()) {
            assertNotEquals(UUID.class, component.getType(),
                    "RateLimitDecision carries a UUID component '" + component.getName() + "' -- the "
                            + "aggregate tier's own creator id must never reach the decision a public "
                            + "follower's response is built from (FR-URL-016)");
        }
        assertTrue(AggregateRedirectLimiter.TIER.contains("creator"),
                "the tier NAME may name the DIMENSION (per-creator-aggregate) -- CreationRateLimiter.TIER "
                        + "already does the same for the authenticated creation path; naming which rule "
                        + "fired is required by FR-URL-016 and is not the same fact as disclosing a "
                        + "specific creator's identity");
    }

    // ------------------------------------------------------------------ the deferred third tier

    @Test
    @DisplayName("T055's Artifact: the deferred aggregate tier is DISCLOSED, with a named closure")
    void theDeferredTierIsDisclosed() throws Exception {
        // This is the assertion that makes the deferral defensible. FR-URL-016 remains binding in full;
        // deferring the third tier is a scheduled omission, never an accepted gap, and the difference
        // between the two is entirely whether this file exists and says so.
        Path register = Path.of("docs/delivery/baseline-omissions.md");
        assertTrue(Files.exists(register),
                "without " + register + " the deferral is indistinguishable from an oversight (T055a)");

        String text = Files.readString(register);
        assertTrue(text.contains("PVT-014"), "the register must name the omitted tier's target");
        assertTrue(text.contains("FR-URL-016"), "and the requirement it belongs to");
        assertTrue(text.contains("binding"), "and state that the requirement remains binding in full");
        assertTrue(text.contains("T136a"), "and name the run that closes it");
        assertTrue(text.toLowerCase(java.util.Locale.ROOT).contains("release readiness"),
                "and say what release readiness must report if that run does not happen");
    }

    @Test
    @DisplayName("T136a: the aggregate tier's own baseline-omissions entry is now CLOSED")
    void theAggregateTierIsNowBuiltAndDisclosedAsClosed() throws Exception {
        // Was theAggregateTierIsNotPartiallyPresent, whose own premise (the tier is absent) T136a makes
        // false on purpose -- AggregateRedirectLimiter now exists and is wired (PersistenceConfiguration,
        // RedirectController). The register entry itself is the durable fact worth asserting here.
        String text = Files.readString(Path.of("docs/delivery/baseline-omissions.md"));
        assertTrue(text.toLowerCase(java.util.Locale.ROOT).contains("**status** | **closed**"),
                "T136a landed; entry 1 of the baseline-omissions register's own Status field must say so");
    }

    // ------------------------------------------------------------------ the on-by-default guard

    @Test
    @DisplayName("GUARD: throttling is not silently disabled by default configuration")
    void throttlingIsOnByDefault() throws Exception {
        // FR-URL-016's negative clause, and T054's guard. T018 asserts the flag; this asserts the flag
        // and the NUMBERS agree with the approved targets, because a limit configured to a million
        // would satisfy "enabled: true" and throttle nothing.
        JsonNode config = new ObjectMapper(new YAMLFactory())
                .readTree(Files.readString(Path.of("src/main/resources/application.yml")));
        JsonNode rateLimit = config.path("shortener").path("ratelimit");

        assertTrue(rateLimit.path("enabled").asBoolean(false), "throttling must be ON by default");
        assertEquals(60, rateLimit.path("creation-per-creator-per-minute").asInt(), "PVT-012");
        assertEquals(600, rateLimit.path("redirect-per-code-per-minute").asInt(), "PVT-013");
        assertEquals(3000, rateLimit.path("aggregate-per-creator-per-minute").asInt(), "PVT-014");
    }
}
