package agentic.shortener.application;

import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T043 — resolution. FR-URL-007. CR-003.
 *
 * <p><strong>Byte-equality is the requirement, not "equivalent".</strong> FR-URL-007's Accept clause says
 * the redirect target equals the stored destination <em>byte for byte</em>, and the negative clause adds
 * that the service must not rewrite the destination at resolution time. Normalization happens once, at
 * creation; doing it again here would mean the link a creator was shown is not the link a follower gets.
 *
 * <p><strong>The target comes from storage and never from request input.</strong> That is the other half
 * of the guard, and it is the difference between a redirector and an open redirect: if any part of the
 * request could steer the Location header, anyone could turn this service into a trusted-looking hop to
 * anywhere.
 *
 * <p>Three outcomes, not two. Never-issued and expired must be <em>distinguishable</em> (FR-URL-008), and
 * a store failure must be neither — it is not an answer, so it must not be reported as one.
 *
 * <p>Fast tier: the decision logic against fakes. {@code ExpiredLinkIT} and the redirect conformance test
 * drive it through HTTP against a real store.
 */
@DisplayName("T043 resolution")
class ResolveLinkUseCaseTest {

    private static final UUID CREATOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final Map<String, ShortLink> rows = new LinkedHashMap<>();
    private boolean storeBroken;

    private final ShortLinkRepository links = new ShortLinkRepository() {
        @Override
        public ShortLink save(ShortLink link) {
            rows.put(link.shortCode(), link);
            return link;
        }

        @Override
        public Optional<ShortLink> findByShortCode(String shortCode) {
            if (storeBroken) {
                // What JdbcShortLinkRepository actually raises when the store is unreachable.
                throw new IllegalStateException("failed to read link " + shortCode);
            }
            return Optional.ofNullable(rows.get(shortCode));
        }

        @Override
        public ShortLink expire(ShortLink link) {
            rows.put(link.shortCode(), link.expire());
            return rows.get(link.shortCode());
        }

        @Override
        public long countByCreator(UUID creatorId) {
            return rows.size();
        }
    };

    private final ResolveLinkUseCase resolve = new ResolveLinkUseCase(links, CLOCK);

    private ShortLink seed(String code, String destination, Instant expiresAt) {
        ShortLink link = ShortLink.create(code, destination, CREATOR, NOW.minusSeconds(60), expiresAt);
        rows.put(code, link);
        return link;
    }

    @Test
    @DisplayName("a live code resolves to the EXACT stored destination, byte for byte")
    void byteEquality() {
        // Chosen to be full of things a careless resolver would 'tidy': mixed case in the path, an
        // encoded slash, query order, a fragment, a trailing element.
        String destination =
                "https://example.com/A/b%2Fc/D?z=1&a=2&Mixed=Case#Frag-ment";
        ShortLink stored = seed("Aaaaaaa", destination, NOW.plusSeconds(3600));

        ResolveLinkUseCase.Resolution outcome = resolve.resolve("Aaaaaaa");

        assertEquals(ResolveLinkUseCase.Outcome.REDIRECT, outcome.outcome());
        assertEquals(destination, outcome.destination(),
                "the target must equal the stored destination byte for byte (FR-URL-007)");
        assertSame(stored, outcome.link().orElseThrow());
        // Asserted character by character, because assertEquals on two equal-looking strings is exactly
        // where an invisible difference hides.
        assertEquals(destination.length(), outcome.destination().length());
        for (int i = 0; i < destination.length(); i++) {
            assertEquals(destination.charAt(i), outcome.destination().charAt(i), "differs at index " + i);
        }
    }

    @Test
    @DisplayName("a never-issued code is NOT_FOUND")
    void neverIssued() {
        assertEquals(ResolveLinkUseCase.Outcome.NOT_FOUND, resolve.resolve("Zzzzzzz").outcome());
        assertTrue(resolve.resolve("Zzzzzzz").destination().isEmpty(),
                "there is no destination to disclose for a code that was never issued");
    }

    @Test
    @DisplayName("an expired link is EXPIRED, and that is distinguishable from NOT_FOUND")
    void expiredIsDistinguishable() {
        // FR-URL-008: the two outcomes must differ. Collapsing them would be defensible on privacy
        // grounds and is still forbidden, because a creator cannot otherwise tell a typo from a dead
        // link.
        seed("Bbbbbbb", "https://example.com/b", NOW.minusSeconds(1));
        assertEquals(ResolveLinkUseCase.Outcome.EXPIRED, resolve.resolve("Bbbbbbb").outcome());
        assertEquals(ResolveLinkUseCase.Outcome.NOT_FOUND, resolve.resolve("Ccccccc").outcome());
        assertFalse(ResolveLinkUseCase.Outcome.EXPIRED == ResolveLinkUseCase.Outcome.NOT_FOUND);
    }

    @Test
    @DisplayName("an expired link exposes NO destination")
    void expiredDisclosesNothing() {
        // An expired link still holds a destination; a 410 body that leaked it would let anyone read
        // every dead link's target. The outcome carries the fact, not the target.
        seed("Ddddddd", "https://secret.example/private-campaign", NOW.minusSeconds(1));
        ResolveLinkUseCase.Resolution outcome = resolve.resolve("Ddddddd");

        assertEquals(ResolveLinkUseCase.Outcome.EXPIRED, outcome.outcome());
        assertTrue(outcome.destination().isEmpty(),
                "an expired resolution must not carry the destination: " + outcome.destination());
    }

    @Test
    @DisplayName("BOUNDARY: exactly at the expiry instant, the link does not redirect")
    void boundaryDoesNotRedirect() {
        seed("Eeeeeee", "https://example.com/e", NOW);
        assertEquals(ResolveLinkUseCase.Outcome.EXPIRED, resolve.resolve("Eeeeeee").outcome(),
                "the expiry instant counts as expired (FR-URL-009), so this must not redirect");

        seed("Fffffff", "https://example.com/f", NOW.plusNanos(1));
        assertEquals(ResolveLinkUseCase.Outcome.REDIRECT, resolve.resolve("Fffffff").outcome(),
                "one nanosecond of life is still life");
    }

    @Test
    @DisplayName("EC-009: a link that expires mid-resolution does not redirect")
    void midResolutionExpiry() {
        // The link is alive when the lookup starts and dead when the decision is taken. A resolver that
        // read the clock before the lookup would redirect here. Reading it after is the fix, and this is
        // the test that distinguishes the two.
        seed("Ggggggg", "https://example.com/g", NOW.plusSeconds(1));
        ResolveLinkUseCase justInTime = new ResolveLinkUseCase(links,
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));

        assertEquals(ResolveLinkUseCase.Outcome.EXPIRED, justInTime.resolve("Ggggggg").outcome());
    }

    @Test
    @DisplayName("EC-011: a store failure is NEITHER not-found nor expired — it propagates")
    void storeFailureIsNotAnAnswer() {
        // The damaging failure would be catching this and returning NOT_FOUND. A 404 is a definite
        // statement that the code does not exist; a broken store cannot make that statement, and a
        // creator told 404 would reasonably conclude their link was gone.
        seed("Hhhhhhh", "https://example.com/h", NOW.plusSeconds(3600));
        storeBroken = true;

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> resolve.resolve("Hhhhhhh"));
        assertFalse(thrown.getMessage().contains("https://example.com/h"),
                "and it must not carry the destination: " + thrown.getMessage());
    }

    @Test
    @DisplayName("GUARD: the target can only come from storage — the input is never a destination")
    void targetNeverComesFromInput() {
        // An open redirect is what this prevents. Nothing in the request may steer the Location header,
        // so a code that happens to look like a URL resolves to nothing at all.
        for (String hostile : List.of("https://evil.example/x", "//evil.example", "..%2Fevil")) {
            ResolveLinkUseCase.Resolution outcome = resolve.resolve(hostile);
            assertEquals(ResolveLinkUseCase.Outcome.NOT_FOUND, outcome.outcome(),
                    hostile + " is not a code and must resolve to nothing");
            assertTrue(outcome.destination().isEmpty(),
                    "the input must never appear as a destination: " + outcome.destination());
        }
    }

    @Test
    @DisplayName("GUARD: the use case's source contains no rewriting and no permanent class")
    void noRewritingAndNoPermanentClass() throws Exception {
        // FR-URL-007's negative clause forbids rewriting the destination at resolution time, and CR-003
        // forbids the permanent class. Asserted on the source because both are absences, and an absence
        // asserted behaviourally only ever covers the inputs somebody thought of.
        Path source = Path.of("src/main/java/agentic/shortener/application/ResolveLinkUseCase.java");
        assertTrue(Files.exists(source), "missing " + source.toAbsolutePath());
        String active = Files.readString(source).lines()
                .map(String::stripLeading)
                .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                .reduce("", (a, b) -> a + "\n" + b);

        for (String forbidden : List.of("normalize", "replace", "MOVED_PERMANENTLY", "301", "308",
                "PERMANENT")) {
            assertFalse(active.contains(forbidden),
                    "resolution must return the stored destination untouched; found '" + forbidden + "'");
        }
    }

    @Test
    @DisplayName("resolution never writes")
    void resolutionNeverWrites() {
        // FR-URL-010's counting is a separate, asynchronous concern (T048-T050). Resolution itself is a
        // read: a write on the redirect path would put the store's availability on the follower's
        // critical path for no reason.
        seed("Jjjjjjj", "https://example.com/j", NOW.plusSeconds(3600));
        ShortLink before = rows.get("Jjjjjjj");

        resolve.resolve("Jjjjjjj");
        resolve.resolve("Jjjjjjj");

        assertSame(before, rows.get("Jjjjjjj"), "resolution modified the stored link");
        assertEquals(1, rows.size());
    }
}
