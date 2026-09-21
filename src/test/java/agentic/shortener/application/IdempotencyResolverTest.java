package agentic.shortener.application;

import agentic.shortener.domain.idempotency.IdempotencyRecord;
import agentic.shortener.domain.idempotency.IdempotencyRepository;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T041 — marker-only idempotency. FR-URL-012, semantics fixed by CL-008.
 *
 * <p>CL-008 defines <strong>exactly three</strong> behaviours, and the damaging one to get wrong is the
 * third: a conflict returned as a replay <em>looks like success</em> while silently discarding what the
 * caller changed. So each case gets its own test, plus the cross-creator case FR-URL-012's evidence line
 * names separately.
 *
 * <p><strong>The guard is an absence.</strong> <em>"No destination-based deduplication at any scope,
 * ever."</em> An absence cannot be demonstrated by trying inputs, so it is asserted structurally too:
 * the resolver's only lookup is by {@code (creator, marker)}, and neither repository offers a lookup by
 * destination for it to use.
 *
 * <p>Fast tier: pure application logic against fakes that record every call.
 */
@DisplayName("T041 marker-only idempotency (CL-008)")
class IdempotencyResolverTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final Instant EXPIRY = Instant.parse("2026-10-21T10:00:00Z");
    private static final String DESTINATION = "https://example.com/promo";

    private final Map<String, IdempotencyRecord> markers = new LinkedHashMap<>();
    private final Map<String, ShortLink> links = new LinkedHashMap<>();
    private final List<String> calls = new ArrayList<>();

    private final IdempotencyRepository markerStore = new IdempotencyRepository() {
        @Override
        public IdempotencyRecord save(IdempotencyRecord record) {
            calls.add("saveMarker:" + record.scopedKey());
            markers.put(record.scopedKey(), record);
            return record;
        }

        @Override
        public Optional<IdempotencyRecord> find(UUID creatorId, String marker) {
            calls.add("findMarker:" + creatorId + ":" + marker);
            return Optional.ofNullable(markers.get(creatorId + "" + marker));
        }
    };

    private final ShortLinkRepository linkStore = new ShortLinkRepository() {
        @Override
        public ShortLink save(ShortLink link) {
            calls.add("saveLink:" + link.shortCode());
            links.put(link.shortCode(), link);
            return link;
        }

        @Override
        public Optional<ShortLink> findByShortCode(String shortCode) {
            calls.add("findLink:" + shortCode);
            return Optional.ofNullable(links.get(shortCode));
        }

        @Override
        public ShortLink expire(ShortLink link) {
            calls.add("expire:" + link.shortCode());
            return link.expire();
        }

        @Override
        public long countByCreator(UUID creatorId) {
            return links.values().stream().filter(l -> l.creatorId().equals(creatorId)).count();
        }
    };

    private final IdempotencyResolver resolver = new IdempotencyResolver(markerStore, linkStore);

    private ShortLink seed(String code, UUID creator, String destination, Instant expiry) {
        ShortLink link = ShortLink.create(code, destination, creator, NOW, expiry);
        links.put(code, link);
        return link;
    }

    private void seedMarker(UUID creator, String marker, String code, String destination,
                            Instant expiry) {
        IdempotencyRecord record =
                IdempotencyRecord.of(creator, marker, destination, expiry, code, NOW);
        markers.put(record.scopedKey(), record);
    }

    @Test
    @DisplayName("CASE 1: no marker means MINT — and there is no destination-based dedup, ever")
    void case1NoMarkerAlwaysMints() {
        seed("aaaaaaa", ALICE, DESTINATION, EXPIRY);
        seedMarker(ALICE, "m1", "aaaaaaa", DESTINATION, EXPIRY);

        // The same creator, the same destination, the same expiry — and no marker. CL-008 says mint.
        for (String marker : new String[]{null, "", "   "}) {
            IdempotencyResolver.Resolution resolution =
                    resolver.resolve(ALICE, marker, DESTINATION, EXPIRY);
            assertEquals(IdempotencyResolver.Kind.MINT, resolution.kind(),
                    "marker=" + marker + " must mint: a blank marker is not a marker");
            assertTrue(resolution.replayed().isEmpty());
        }
    }

    @Test
    @DisplayName("CASE 2: same marker, identical request — REPLAY the ORIGINAL, zero side effects")
    void case2ReplayReturnsTheOriginal() {
        ShortLink original = seed("bbbbbbb", ALICE, DESTINATION, EXPIRY);
        seedMarker(ALICE, "m1", "bbbbbbb", DESTINATION, EXPIRY);
        calls.clear();

        IdempotencyResolver.Resolution resolution =
                resolver.resolve(ALICE, "m1", DESTINATION, EXPIRY);

        assertEquals(IdempotencyResolver.Kind.REPLAY, resolution.kind());
        assertSame(original, resolution.replayed().orElseThrow(),
                "the ORIGINAL link comes back — not a fresh one that happens to look the same");

        // Zero new side effects: nothing was written.
        assertTrue(calls.stream().noneMatch(c -> c.startsWith("save")),
                "a replay wrote something: " + calls);
        assertEquals(1, links.size(), "no new link may exist");
        assertEquals(1, markers.size(), "no new marker may exist");
    }

    @Test
    @DisplayName("GUARD: a replay does not alter the existing link's expiry")
    void replayDoesNotTouchExpiry() {
        // FR-URL-012's negative clause, named twice in CL-008's reasoning: the link is already
        // circulating with a promised lifetime, so a replay that extended or shortened it would change
        // a promise somebody has already relied on.
        ShortLink original = seed("ccccccc", ALICE, DESTINATION, EXPIRY);
        seedMarker(ALICE, "m1", "ccccccc", DESTINATION, EXPIRY);

        resolver.resolve(ALICE, "m1", DESTINATION, EXPIRY);

        ShortLink after = links.get("ccccccc");
        assertEquals(EXPIRY, after.expiresAt(), "the expiry moved");
        assertEquals(original, after, "the link changed in some other respect");
    }

    @Test
    @DisplayName("CASE 3: same marker, different content — CONFLICT, nothing minted, nothing changed")
    void case3DifferentContentIsAConflict() {
        ShortLink original = seed("ddddddd", ALICE, DESTINATION, EXPIRY);
        seedMarker(ALICE, "m1", "ddddddd", DESTINATION, EXPIRY);
        calls.clear();

        // A different expiry is the case CL-008 calls out by name, because it is the one that would
        // force a silent lie under destination-based deduplication.
        IdempotencyResolver.Resolution differentExpiry =
                resolver.resolve(ALICE, "m1", DESTINATION, EXPIRY.plusSeconds(86_400));
        assertEquals(IdempotencyResolver.Kind.CONFLICT, differentExpiry.kind());

        IdempotencyResolver.Resolution differentDestination =
                resolver.resolve(ALICE, "m1", "https://example.com/other", EXPIRY);
        assertEquals(IdempotencyResolver.Kind.CONFLICT, differentDestination.kind());

        assertTrue(differentExpiry.replayed().isEmpty(), "a conflict returns no link to serve");
        assertTrue(calls.stream().noneMatch(c -> c.startsWith("save")),
                "a conflict wrote something: " + calls);
        assertEquals(1, links.size(), "a conflict must not mint past a detected caller error");
        assertEquals(original, links.get("ddddddd"), "the existing link must be untouched");
    }

    @Test
    @DisplayName("a conflict names the mismatch without echoing the submitted content")
    void conflictIsActionableWithoutEchoing() {
        seed("eeeeeee", ALICE, DESTINATION, EXPIRY);
        seedMarker(ALICE, "m1", "eeeeeee", DESTINATION, EXPIRY);

        IdempotencyResolver.Resolution conflict =
                resolver.resolve(ALICE, "m1", "https://evil.example/marker-idem", EXPIRY);

        assertEquals(IdempotencyResolver.Kind.CONFLICT, conflict.kind());
        assertTrue(conflict.reason().contains("marker"),
                "the reason must say what went wrong: " + conflict.reason());
        assertFalse(conflict.reason().contains("marker-idem"),
                "and must not echo the submitted destination: " + conflict.reason());
        assertFalse(conflict.reason().contains("evil.example"), conflict.reason());
    }

    @Test
    @DisplayName("CROSS-CREATOR: one creator's marker never resolves another's request")
    void markersDoNotSpanCreators() {
        // FR-URL-012 names this separately, and for two reasons at once: deduplication across creators
        // would break FR-URL-011's ownership model AND leak that somebody else shortened the same URL.
        seed("fffffff", ALICE, DESTINATION, EXPIRY);
        seedMarker(ALICE, "shared-marker", "fffffff", DESTINATION, EXPIRY);

        IdempotencyResolver.Resolution forBob =
                resolver.resolve(BOB, "shared-marker", DESTINATION, EXPIRY);

        assertEquals(IdempotencyResolver.Kind.MINT, forBob.kind(),
                "Bob's identical request under the same marker string must MINT, not replay Alice's");
        assertTrue(forBob.replayed().isEmpty(), "Alice's link must not be visible to Bob at all");
    }

    @Test
    @DisplayName("GUARD: the only lookup is by (creator, marker)")
    void theOnlyLookupIsByMarker() {
        seed("ggggggg", ALICE, DESTINATION, EXPIRY);
        seedMarker(ALICE, "m1", "ggggggg", DESTINATION, EXPIRY);
        calls.clear();

        resolver.resolve(ALICE, "m1", DESTINATION, EXPIRY);

        assertEquals(List.of("findMarker:" + ALICE + ":m1", "findLink:ggggggg"), calls,
                "the marker is looked up, then the link it points at — and nothing is searched by "
                        + "destination: " + calls);
    }

    @Test
    @DisplayName("GUARD: no repository even offers a lookup by destination")
    void noRepositoryCanSearchByDestination() {
        // Structural. "No destination-based deduplication at any scope, ever" is an absence, and an
        // absence is not demonstrable by trying inputs — so the capability is removed rather than
        // merely unused.
        for (Class<?> repository : List.of(ShortLinkRepository.class, IdempotencyRepository.class)) {
            for (Method method : repository.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                assertFalse(name.contains("destination") || name.contains("byurl")
                                || name.contains("bytarget"),
                        repository.getSimpleName() + " declares '" + method.getName()
                                + "', which is a destination lookup. CL-008 forbids one at any scope");
            }
        }
    }

    @Test
    @DisplayName("GUARD: the resolver's source never fingerprints a destination on its own")
    void theResolverNeverHashesADestinationAlone() throws Exception {
        // IdempotencyRecord.fingerprint covers destination AND expiry together, deliberately. A
        // resolver that hashed the destination by itself would have rebuilt destination-based
        // deduplication under another name.
        Path source = Path.of("src/main/java/agentic/shortener/application/IdempotencyResolver.java");
        assertTrue(Files.exists(source), "missing " + source.toAbsolutePath());
        String active = Files.readString(source).lines()
                .map(String::stripLeading)
                .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                .reduce("", (a, b) -> a + "\n" + b);

        for (String forbidden : List.of("MessageDigest", "sha", "SHA-256", "hashCode()")) {
            assertFalse(active.contains(forbidden),
                    "the resolver computes a digest of its own ('" + forbidden + "'); fingerprinting "
                            + "belongs to IdempotencyRecord, where destination and expiry are covered "
                            + "together");
        }
    }

    @Test
    @DisplayName("an unmarked request is not recorded as a marker")
    void unmarkedRequestsLeaveNoMarker() {
        resolver.resolve(ALICE, null, DESTINATION, EXPIRY);
        assertTrue(markers.isEmpty(), "a blank marker must never become a stored key");
        assertNotEquals(IdempotencyResolver.Kind.REPLAY,
                resolver.resolve(ALICE, null, DESTINATION, EXPIRY).kind());
    }
}
