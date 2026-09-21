package agentic.shortener.domain.link;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T046 — expiry policy. FR-URL-009. EC-014. PVT-011.
 *
 * <p><strong>The boundary instant is the whole test.</strong> FR-URL-009 requires the expiry instant to
 * be treated <em>consistently</em>, which means the answer to "is a link expired exactly at its expiry
 * instant?" has to be decided once and written down. It is decided as <strong>yes</strong>: at
 * {@code expiresAt} the link is expired. A rule that said "no" would mean the link is alive for one
 * indivisible instant that no clock can reliably observe, which is a definition nobody can test against.
 *
 * <p><strong>No sleeping.</strong> The clock is injected, so the boundary is reached by choosing an
 * instant rather than by waiting for one. A test that sleeps to cross an expiry is slow and flaky at
 * once, and at a 30-day default TTL it is not even possible.
 *
 * <p>Fast tier: pure domain.
 */
@DisplayName("T046 expiry policy")
class ExpiryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID CREATOR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ExpiryPolicy policy = new ExpiryPolicy(CLOCK);

    @Test
    @DisplayName("PVT-011: an unspecified expiry defaults to 30 days")
    void defaultsToThirtyDays() {
        assertEquals(NOW.plus(Duration.ofDays(30)), policy.resolve(null),
                "PVT-011 fixes the default at 30 days — a bounded default rather than unbounded growth");
        assertEquals(Duration.ofDays(30), ExpiryPolicy.DEFAULT_TTL);
    }

    @Test
    @DisplayName("a caller-supplied expiry is honoured exactly")
    void callerSuppliedIsHonoured() {
        Instant asked = NOW.plusSeconds(3600);
        assertEquals(asked, policy.resolve(asked),
                "the caller's instant must come back unrounded and unadjusted");
    }

    @Test
    @DisplayName("EC-014: an expiry already in the past is REFUSED")
    void pastExpiryRefused() {
        // The guard: a link MUST NOT be created already expired. Such a link would be minted, stored,
        // consume a code, and answer 410 to its very first follower.
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(NOW.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(NOW.minusSeconds(86_400)));
    }

    @Test
    @DisplayName("BOUNDARY: an expiry equal to now is refused, because that link is already expired")
    void expiryEqualToNowRefused() {
        // Follows directly from the boundary rule below. If the expiry instant counts as expired, then
        // an expiry of exactly `now` produces a link that is expired at the moment it exists — which is
        // what EC-014 forbids. Deciding the boundary one way and the refusal the other would be the
        // inconsistency FR-URL-009 is about.
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(NOW),
                "an expiry equal to the creation instant means a link that expires as it is born");

        // One nanosecond later is legal. Absurd, but legal — and the bound has to be somewhere exact.
        assertEquals(NOW.plusNanos(1), policy.resolve(NOW.plusNanos(1)));
    }

    @Test
    @DisplayName("BOUNDARY: the expiry instant itself counts as EXPIRED")
    void theExpiryInstantIsExpired() {
        ShortLink link = ShortLink.create("Aaaaaaa", "https://example.com/a", CREATOR,
                NOW, NOW.plusSeconds(60));

        assertFalse(link.isExpiredAt(NOW), "alive before expiry");
        assertFalse(link.isExpiredAt(NOW.plusSeconds(59)), "alive one second before expiry");
        assertFalse(link.isExpiredAt(NOW.plusSeconds(60).minusNanos(1)),
                "alive one nanosecond before expiry");
        assertTrue(link.isExpiredAt(NOW.plusSeconds(60)),
                "AT the expiry instant the link is expired — this is the definition FR-URL-009 asks for");
        assertTrue(link.isExpiredAt(NOW.plusSeconds(61)), "expired after");
    }

    @Test
    @DisplayName("BOUNDARY: the policy and the link agree on the same instant")
    void policyAndLinkAgree() {
        // Two places decide expiry: this policy at creation and ShortLink.isExpiredAt at resolution.
        // If they disagreed by one instant, a link could be accepted at creation and dead on arrival.
        // Asserted directly rather than left to inspection of two files.
        Instant expiry = policy.resolve(NOW.plusSeconds(1));
        ShortLink link = ShortLink.create("Bbbbbbb", "https://example.com/b", CREATOR, NOW, expiry);

        assertFalse(link.isExpiredAt(NOW),
                "a link the policy just accepted must be alive at the instant it was created");
        assertTrue(link.isExpiredAt(expiry), "and expired at the instant the policy returned");
    }

    @Test
    @DisplayName("an EXPIRED link stays expired even before its expiry instant")
    void explicitlyExpiredStaysExpired() {
        // The state and the clock are both authorities, and the state wins. expire() is the
        // Compensation Register's action for a link that should not have been created; if the clock
        // could override it, the compensation would silently undo itself.
        ShortLink link = ShortLink.create("Ccccccc", "https://example.com/c", CREATOR,
                NOW, NOW.plusSeconds(3600)).expire();

        assertEquals(LinkState.EXPIRED, link.state());
        assertTrue(link.isExpiredAt(NOW), "a compensated link must not come back to life");
    }

    @Test
    @DisplayName("the clock is injected, so no test has to sleep")
    void clockIsInjected() {
        // At a 30-day default TTL, waiting for an expiry is not a slow test — it is an impossible one.
        Clock later = Clock.fixed(NOW.plus(Duration.ofDays(365)), ZoneOffset.UTC);
        ExpiryPolicy future = new ExpiryPolicy(later);

        assertEquals(NOW.plus(Duration.ofDays(395)), future.resolve(null));
        // And an instant that is future to the real clock but past to this one is refused.
        assertThrows(IllegalArgumentException.class, () -> future.resolve(NOW.plusSeconds(60)));
    }

    @Test
    @DisplayName("a refusal names the rule and never echoes the submitted instant")
    void refusalNamesTheRule() {
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> policy.resolve(NOW.minusSeconds(1)));
        assertTrue(thrown.getMessage().contains("expiry"),
                "the reason must be actionable: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("FR-URL-009") || thrown.getMessage().contains("EC-014"),
                "and cite its rule: " + thrown.getMessage());
    }
}
