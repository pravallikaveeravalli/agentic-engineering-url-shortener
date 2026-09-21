package agentic.shortener.domain.link;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T021 — ShortLink invariants live in the type, not in callers.
 *
 * <p>FR-URL-001, FR-URL-008, FR-URL-009. ADR-007.
 *
 * <p>T021's Done condition is <em>"invariants enforced in the type, not by callers"</em>. That is what
 * every test here checks: each construction that should be impossible is attempted, and the type must
 * refuse it. A validator sitting in a service layer would leave the type constructible into an
 * invalid state, and every later caller would have to remember the rule.
 *
 * <p>Fast tier: pure domain, no framework, no store. T014 asserts this package imports neither.
 */
@DisplayName("T021 ShortLink")
class ShortLinkTest {

    private static final UUID CREATOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final Instant LATER = NOW.plus(7, ChronoUnit.DAYS);

    private static ShortLink valid() {
        return ShortLink.create("abc123", "https://example.com/a", CREATOR, NOW, LATER);
    }

    @Test
    @DisplayName("a valid link is created ACTIVE and keeps what it was given")
    void validLinkIsActive() {
        ShortLink link = valid();
        assertEquals("abc123", link.shortCode());
        assertEquals("https://example.com/a", link.destination());
        assertEquals(CREATOR, link.creatorId());
        assertEquals(NOW, link.createdAt());
        assertEquals(LATER, link.expiresAt());
        assertEquals(LinkState.ACTIVE, link.state(),
                "a new link is ACTIVE; FR-URL-009 forbids creating one already expired");
    }

    @Test
    @DisplayName("FR-URL-001: destination must be non-empty and at most 2048 characters")
    void destinationBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> ShortLink.create("abc123", "", CREATOR, NOW, LATER),
                "an empty destination must be refused by the type");
        assertThrows(NullPointerException.class,
                () -> ShortLink.create("abc123", null, CREATOR, NOW, LATER));

        String tooLong = "https://example.com/" + "x".repeat(2049);
        assertTrue(tooLong.length() > 2048);
        assertThrows(IllegalArgumentException.class,
                () -> ShortLink.create("abc123", tooLong, CREATOR, NOW, LATER),
                "2048 is the bound FR-URL-001 states, and it is the type's job to hold it");

        // The boundary itself is legal, which is the half of a bound that gets forgotten.
        String exactly2048 = "https://e.com/" + "x".repeat(2048 - "https://e.com/".length());
        assertEquals(2048, exactly2048.length());
        assertEquals(exactly2048,
                ShortLink.create("abc123", exactly2048, CREATOR, NOW, LATER).destination());
    }

    @Test
    @DisplayName("FR-URL-004: only allow-listed schemes, and the list is not configurable")
    void schemeAllowList() {
        for (String ok : new String[]{"https://example.com", "http://example.com"}) {
            assertEquals(ok, ShortLink.create("abc123", ok, CREATOR, NOW, LATER).destination());
        }
        // The list in FR-URL-004's evidence line: script, data and file must all be refused. These
        // are the schemes that turn a redirect into an execution or a local-file read.
        for (String bad : new String[]{"javascript:alert(1)", "data:text/html,<script>x</script>",
                "file:///etc/passwd", "ftp://example.com", "mailto:a@b.c", "//example.com",
                "HTTPS\t://example.com"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ShortLink.create("abc123", bad, CREATOR, NOW, LATER),
                    "must refuse non-allow-listed scheme: " + bad);
        }
    }

    @Test
    @DisplayName("FR-URL-009: expiry must be strictly after creation")
    void expiryMustBeFuture() {
        assertThrows(IllegalArgumentException.class,
                () -> ShortLink.create("abc123", "https://example.com", CREATOR, NOW, NOW.minusSeconds(1)),
                "EC-014: an already-past expiry must be refused");
        assertThrows(IllegalArgumentException.class,
                () -> ShortLink.create("abc123", "https://example.com", CREATOR, NOW, NOW),
                "equal instants mean a link that expires the moment it exists");
    }

    @Test
    @DisplayName("creator is required — there is no link without an owner")
    void creatorRequired() {
        assertThrows(NullPointerException.class,
                () -> ShortLink.create("abc123", "https://example.com", null, NOW, LATER),
                "data-model.md KE-01: creator_id is required");
    }

    @Test
    @DisplayName("short code must be non-blank and drawn from the fixed alphabet (ADR-007)")
    void shortCodeAlphabet() {
        assertThrows(IllegalArgumentException.class,
                () -> ShortLink.create("", "https://example.com", CREATOR, NOW, LATER));
        for (String bad : new String[]{"abc 12", "abc/12", "abc+12", "abc=12"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> ShortLink.create(bad, "https://example.com", CREATOR, NOW, LATER),
                    "code outside the alphabet must be refused: " + bad);
        }
    }

    @Test
    @DisplayName("the ONLY permitted mutation is ACTIVE -> EXPIRED, and it is not in-place")
    void theOnlyPermittedMutation() {
        ShortLink active = valid();
        ShortLink expired = active.expire();

        assertEquals(LinkState.EXPIRED, expired.state());
        assertEquals(LinkState.ACTIVE, active.state(),
                "expire() must not mutate the receiver: an immutable value cannot be corrupted by a "
                        + "caller holding an older reference");

        // T021's guard: never deleted, and ACTIVE -> EXPIRED is also the compensating action for a
        // link that should not have been created. Expiring twice must therefore be harmless rather
        // than an error, because a compensation may be retried (EC-022 applies it at most once, but
        // the type must not punish a second call).
        assertSame(expired, expired.expire(),
                "expiring an already-expired link returns the same value \u2014 idempotent, because a "
                        + "compensating action must be safe to repeat");
    }

    @Test
    @DisplayName("FR-URL-008: expiry is evaluated against an instant, boundary included")
    void expiryEvaluation() {
        ShortLink link = valid();
        assertTrue(link.isExpiredAt(LATER),
                "at the expiry instant the link IS expired \u2014 FR-URL-009 requires the boundary to be "
                        + "defined, and this is the definition");
        assertTrue(link.isExpiredAt(LATER.plusSeconds(1)));
        assertTrue(!link.isExpiredAt(LATER.minusSeconds(1)));

        // State and clock must agree: an EXPIRED link is expired at any instant, including before
        // its own expiry timestamp, because the state was set deliberately (compensation).
        assertTrue(link.expire().isExpiredAt(NOW),
                "an explicitly expired link is expired regardless of the clock");
    }
}
