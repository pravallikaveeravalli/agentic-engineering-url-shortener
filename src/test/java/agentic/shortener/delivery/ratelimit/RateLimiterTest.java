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
 * T054 and T055 — the two rate-limit tiers that are built. FR-URL-016. EC-013. PVT-012, PVT-013.
 *
 * <p><strong>Two of three, and the third is deferred rather than forgotten.</strong> FR-URL-016 defines
 * three tiers. The per-creator <em>aggregate</em> redirect tier (PVT-014) is deliberately deferred to the
 * brownfield scenario, and the whole defence of deferring a binding requirement is that the omission is
 * written down with a named closure. {@link #theDeferredTierIsDisclosed} asserts that record exists and
 * says what it must — because without it the deferral is indistinguishable from an oversight.
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
    @DisplayName("the aggregate tier is genuinely ABSENT, not half-built")
    void theAggregateTierIsNotPartiallyPresent() throws Exception {
        // A half-built third tier would be worse than none: it would look present in a review and
        // enforce nothing. The absence is asserted so that building it is a visible change that has to
        // update this test and the register together.
        try (java.util.stream.Stream<Path> sources =
                     Files.walk(Path.of("src/main/java/agentic/shortener/delivery/ratelimit"))) {
            for (Path file : sources.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file).lines()
                        .map(String::stripLeading)
                        .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                        .reduce("", (a, b) -> a + "\n" + b);
                assertFalse(text.contains("PVT_014") || text.contains("aggregatePerCreator"),
                        file + " implements part of the deferred tier; if it is being built, T136a and "
                                + "the baseline-omissions register must change with it");
            }
        }
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
        assertTrue(rateLimit.path("aggregate-per-creator-per-minute").isMissingNode(),
                "the deferred tier must have no configuration key: a key with no enforcement behind it "
                        + "is worse than an absence, because it reads as a working control");
    }
}
