package agentic.shortener.application;

import agentic.shortener.domain.analytics.RedirectEvent;
import agentic.shortener.domain.analytics.RedirectEventRepository;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T051 — creator-scoped analytics retrieval. FR-URL-011.
 *
 * <p><strong>The guard is the interesting requirement: no ownership oracle.</strong> The refusal must not
 * disclose whether the code exists. That is stronger than "do not return another creator's data" — if a
 * non-owner's answer differed in any respect from the answer for a code that was never issued, anyone
 * could walk the keyspace and learn which codes are live, and by asking as themselves, which are not
 * theirs.
 *
 * <p>So byte-identity is asserted, and asserted the strict way: the two refusals must be the
 * <strong>same value</strong>, not two values that happen to be equal today. Equality can survive a field
 * being added to one branch; identity cannot.
 *
 * <p><strong>Authentication is not this class's concern.</strong> The caller identity arrives as a
 * parameter, so this is testable and correct before T052's filter exists. The HTTP-level 401 for an
 * anonymous caller is asserted where the filter is; here an anonymous caller is a {@code null} identity
 * and receives the same refusal as a non-owner.
 *
 * <p>Fast tier: pure application logic against fakes.
 */
@DisplayName("T051 creator-scoped analytics retrieval")
class GetAnalyticsUseCaseTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private final Map<String, ShortLink> links = new LinkedHashMap<>();
    private final List<RedirectEvent> events = new ArrayList<>();

    private final ShortLinkRepository linkStore = new ShortLinkRepository() {
        @Override
        public ShortLink save(ShortLink link) {
            links.put(link.shortCode(), link);
            return link;
        }

        @Override
        public Optional<ShortLink> findByShortCode(String shortCode) {
            return Optional.ofNullable(links.get(shortCode));
        }

        @Override
        public ShortLink expire(ShortLink link) {
            links.put(link.shortCode(), link.expire());
            return links.get(link.shortCode());
        }

        @Override
        public long countByCreator(UUID creatorId) {
            return links.values().stream().filter(l -> l.creatorId().equals(creatorId)).count();
        }
    };

    private final RedirectEventRepository eventStore = new RedirectEventRepository() {
        @Override
        public RedirectEvent append(RedirectEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public List<RedirectEvent> findByShortCode(String shortCode) {
            return events.stream().filter(e -> e.shortCode().equals(shortCode)).toList();
        }

        @Override
        public List<RedirectEvent> findByShortCodeBetween(String shortCode, Instant from, Instant to) {
            return events.stream()
                    .filter(e -> e.shortCode().equals(shortCode))
                    .filter(e -> !e.occurredAt().isBefore(from) && !e.occurredAt().isAfter(to))
                    .toList();
        }

        @Override
        public long countByShortCode(String shortCode) {
            return events.stream().filter(e -> e.shortCode().equals(shortCode)).count();
        }
    };

    private final GetAnalyticsUseCase analytics = new GetAnalyticsUseCase(linkStore, eventStore);

    private void seed(String code, UUID owner) {
        links.put(code, ShortLink.create(code, "https://example.com/" + code, owner,
                NOW.minusSeconds(3600), NOW.plusSeconds(3600)));
    }

    @Test
    @DisplayName("the OWNER receives the link's events, in time order")
    void ownerSeesEvents() {
        seed("Aaaaaaa", ALICE);
        // Appended out of order on purpose: "time-ordered" is a promise about the response, not an
        // accident of insertion order.
        eventStore.append(RedirectEvent.unsaved("Aaaaaaa", NOW.plusSeconds(30)));
        eventStore.append(RedirectEvent.unsaved("Aaaaaaa", NOW.plusSeconds(10)));
        eventStore.append(RedirectEvent.unsaved("Aaaaaaa", NOW.plusSeconds(20)));

        GetAnalyticsUseCase.View view = analytics.get(ALICE, "Aaaaaaa");

        assertTrue(view.permitted());
        assertEquals("Aaaaaaa", view.shortCode());
        assertEquals(3, view.totalRedirects());
        assertEquals(List.of(NOW.plusSeconds(10), NOW.plusSeconds(20), NOW.plusSeconds(30)),
                view.events(), "events must come back in time order");
    }

    @Test
    @DisplayName("another creator's link is refused")
    void nonOwnerRefused() {
        seed("Bbbbbbb", ALICE);
        eventStore.append(RedirectEvent.unsaved("Bbbbbbb", NOW));

        GetAnalyticsUseCase.View view = analytics.get(BOB, "Bbbbbbb");

        assertFalse(view.permitted());
        assertTrue(view.events().isEmpty(), "no event may leak to a non-owner");
        assertEquals(0L, view.totalRedirects(), "not even the COUNT may leak — it is information");
        assertTrue(view.shortCode().isEmpty(), "and the code must not be echoed back as confirmation");
    }

    @Test
    @DisplayName("an anonymous caller is refused")
    void anonymousRefused() {
        seed("Ccccccc", ALICE);
        assertFalse(analytics.get(null, "Ccccccc").permitted(),
                "FR-URL-011: retrieval MUST NOT be reachable without creator authentication");
    }

    @Test
    @DisplayName("NO OWNERSHIP ORACLE: non-owner and never-issued are the SAME value")
    void noOwnershipOracle() {
        seed("Ddddddd", ALICE);
        eventStore.append(RedirectEvent.unsaved("Ddddddd", NOW));

        GetAnalyticsUseCase.View nonOwner = analytics.get(BOB, "Ddddddd");
        GetAnalyticsUseCase.View neverIssued = analytics.get(BOB, "Zzzzzzz");
        GetAnalyticsUseCase.View anonymous = analytics.get(null, "Ddddddd");

        // Identity, not equality. Two separately-built "empty" results are equal today and can drift
        // apart tomorrow — one gains a field, the other phrases a message differently — and the drift
        // would be an oracle no test was watching for.
        assertSame(nonOwner, neverIssued,
                "a non-owner and a caller asking about a code that does not exist must receive the "
                        + "identical value, or the difference is an enumeration oracle");
        assertSame(nonOwner, anonymous);
        assertSame(GetAnalyticsUseCase.View.REFUSED, nonOwner);
        assertEquals(nonOwner, neverIssued);
        assertEquals(nonOwner.toString(), neverIssued.toString(),
                "even the rendered form must not differ — this is what reaches a response body");
    }

    @Test
    @DisplayName("NO OWNERSHIP ORACLE: an expired link a caller does not own is no different either")
    void expiredNonOwnedIsAlsoIndistinguishable() {
        // A third way the oracle could appear. An expired link still exists, and a refusal that said so
        // would tell a stranger the code had once been issued.
        seed("Eeeeeee", ALICE);
        linkStore.expire(links.get("Eeeeeee"));

        assertSame(GetAnalyticsUseCase.View.REFUSED, analytics.get(BOB, "Eeeeeee"));
        assertSame(GetAnalyticsUseCase.View.REFUSED, analytics.get(BOB, "Yyyyyyy"));
    }

    @Test
    @DisplayName("the OWNER of an expired link still sees its analytics")
    void ownerOfExpiredLinkStillSeesAnalytics() {
        // Expiry stops the redirect (FR-URL-008); it does not confiscate the creator's own history.
        // CR-017 keeps events indefinitely, so there is history to see.
        seed("Fffffff", ALICE);
        eventStore.append(RedirectEvent.unsaved("Fffffff", NOW.minusSeconds(10)));
        linkStore.expire(links.get("Fffffff"));

        GetAnalyticsUseCase.View view = analytics.get(ALICE, "Fffffff");
        assertTrue(view.permitted());
        assertEquals(1, view.totalRedirects());
    }

    @Test
    @DisplayName("a link with no follows yet reports zero, not a refusal")
    void zeroEventsIsNotARefusal() {
        // The difference matters to the owner: "nobody has clicked" and "you may not look" are
        // different answers, and collapsing them would make the feature useless on day one.
        seed("Ggggggg", ALICE);

        GetAnalyticsUseCase.View view = analytics.get(ALICE, "Ggggggg");
        assertTrue(view.permitted());
        assertEquals(0L, view.totalRedirects());
        assertTrue(view.events().isEmpty());
        assertFalse(view.equals(GetAnalyticsUseCase.View.REFUSED),
                "an owner with no follows must NOT receive the refusal value");
    }

    @Test
    @DisplayName("a blank or null code is refused without touching the store")
    void blankCodeRefused() {
        assertSame(GetAnalyticsUseCase.View.REFUSED, analytics.get(ALICE, null));
        assertSame(GetAnalyticsUseCase.View.REFUSED, analytics.get(ALICE, ""));
        assertSame(GetAnalyticsUseCase.View.REFUSED, analytics.get(ALICE, "   "));
    }

    @Test
    @DisplayName("CL-002: only timestamps are returned, because that is all an event holds")
    void timestampsOnly() {
        // FR-URL-010 stores nothing that identifies a follower, so there is nothing here to filter out.
        // Asserted at the shape of the response: the events are Instants, not records with fields that
        // a future change could quietly widen.
        seed("Hhhhhhh", ALICE);
        eventStore.append(RedirectEvent.unsaved("Hhhhhhh", NOW));

        List<Instant> returned = analytics.get(ALICE, "Hhhhhhh").events();
        assertEquals(1, returned.size());
        assertEquals(NOW, returned.get(0));
        assertTrue(returned.get(0) instanceof Instant,
                "the response carries instants, so there is no field for a follower identifier");
    }

    @Test
    @DisplayName("the returned event list cannot be mutated by its caller")
    void returnedEventsAreImmutable() {
        seed("Jjjjjjj", ALICE);
        eventStore.append(RedirectEvent.unsaved("Jjjjjjj", NOW));

        List<Instant> returned = analytics.get(ALICE, "Jjjjjjj").events();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> returned.add(NOW.plusSeconds(1)),
                "a caller holding the list must not be able to alter what the use case reported");
    }
}
