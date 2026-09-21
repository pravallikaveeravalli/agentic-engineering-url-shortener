package agentic.shortener.persistence;

import agentic.shortener.domain.analytics.RedirectEvent;
import agentic.shortener.domain.creator.Creator;
import agentic.shortener.domain.creator.CreatorCredential;
import agentic.shortener.domain.idempotency.IdempotencyRecord;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T026 — the remaining three repository implementations against the real store.
 *
 * <p>Credential, idempotency and analytics round-trips, plus the two store-level guarantees that only
 * a real database can demonstrate: the per-creator composite key on markers, and the fact that the
 * analytics table holds no column able to identify a follower.
 */
@DisplayName("T026 Creator, Idempotency and RedirectEvent repositories")
class CreatorAndIdempotencyRepositoryIT extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final Instant LATER = NOW.plus(7, ChronoUnit.DAYS);
    // Synthetic. Authorises nothing and exists only in this test.
    private static final String PRESENTED = "crk_ITTESTONLYITTESTONLY000000";

    private JdbcCreatorRepository creators;
    private JdbcShortLinkRepository links;
    private JdbcIdempotencyRepository markers;
    private JdbcRedirectEventRepository events;

    @BeforeEach
    void migrateAndWire() {
        MigrationSupport.migrate(POSTGRES, "public");
        creators = new JdbcCreatorRepository(() -> connection());
        links = new JdbcShortLinkRepository(() -> connection());
        markers = new JdbcIdempotencyRepository(() -> connection());
        events = new JdbcRedirectEventRepository(() -> connection());
    }

    private UUID newCreator() {
        UUID id = UUID.randomUUID();
        creators.save(Creator.create(id, "IT creator " + id, NOW));
        return id;
    }

    @Test
    @DisplayName("a creator round-trips, and its credential stores only a digest")
    void creatorAndCredentialRoundTrip() {
        UUID id = newCreator();
        Creator found = creators.findById(id).orElseThrow();
        assertEquals("IT creator " + id, found.name());
        assertTrue(found.active());

        CreatorCredential cred = CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), id, PRESENTED, NOW, NOW.plus(90, ChronoUnit.DAYS));
        creators.save(cred);

        CreatorCredential reloaded = creators.findByKeyHash(cred.keyHash()).orElseThrow();
        assertEquals(cred.keyHash(), reloaded.keyHash());
        assertTrue(reloaded.matches(PRESENTED), "the reloaded credential must verify the same key");
        assertEquals(1, creators.findCredentialsFor(id).size());
    }

    @Test
    @DisplayName("a creator may hold several credentials — rotation is expressible (V2)")
    void rotationIsExpressible() {
        UUID id = newCreator();
        CreatorCredential old = CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), id, PRESENTED + "A", NOW, NOW.plus(30, ChronoUnit.DAYS));
        CreatorCredential current = CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), id, PRESENTED + "B", NOW, NOW.plus(90, ChronoUnit.DAYS));
        creators.save(old.revoke(NOW.plusSeconds(60)));
        creators.save(current);

        List<CreatorCredential> all = creators.findCredentialsFor(id);
        assertEquals(2, all.size(),
                "V1's single api_key_hash column per creator could not express this, which is why V2 "
                        + "added a credential table");
        assertTrue(all.stream().anyMatch(c -> c.revokedAt() != null), "the revoked one survives");
        assertTrue(all.stream().anyMatch(c -> c.revokedAt() == null), "the current one is unrevoked");
    }

    @Test
    @DisplayName("a never-expiring credential stores a NULL expiry, not a sentinel date")
    void neverExpiringStoresNull() {
        UUID id = newCreator();
        CreatorCredential never = CreatorCredential.neverExpires(
                UUID.randomUUID(), id, PRESENTED + "N", NOW);
        creators.save(never);

        CreatorCredential reloaded = creators.findByKeyHash(never.keyHash()).orElseThrow();
        assertNull(reloaded.expiresAt(),
                "a far-future sentinel would make 'never' indistinguishable from 'expires in 2999'");
        assertFalse(reloaded.isExpiredAt(NOW.plus(3650, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("CL-008: the same marker from two creators does NOT collide in the store")
    void markerIsScopedPerCreatorInTheStore() {
        UUID a = newCreator();
        UUID b = newCreator();
        links.save(ShortLink.create("mk0001", "https://example.com/a", a, NOW, LATER));
        links.save(ShortLink.create("mk0002", "https://example.com/b", b, NOW, LATER));

        markers.save(IdempotencyRecord.of(a, "shared-marker", "https://example.com/a", LATER,
                "mk0001", NOW));
        // The composite primary key is what permits this. A single-column key on `marker` would
        // reject it, and one creator would be able to block another's marker.
        markers.save(IdempotencyRecord.of(b, "shared-marker", "https://example.com/b", LATER,
                "mk0002", NOW));

        assertEquals("mk0001", markers.find(a, "shared-marker").orElseThrow().shortCode());
        assertEquals("mk0002", markers.find(b, "shared-marker").orElseThrow().shortCode());
    }

    @Test
    @DisplayName("CL-008 case 3: a stored marker's fingerprint rejects changed content")
    void storedFingerprintDistinguishesContent() {
        UUID id = newCreator();
        links.save(ShortLink.create("fp0001", "https://example.com/a", id, NOW, LATER));
        markers.save(IdempotencyRecord.of(id, "m1", "https://example.com/a", LATER, "fp0001", NOW));

        IdempotencyRecord stored = markers.find(id, "m1").orElseThrow();
        assertTrue(stored.matchesRequest(
                        IdempotencyRecord.fingerprint("https://example.com/a", LATER)),
                "an identical retry is a replay");
        assertFalse(stored.matchesRequest(IdempotencyRecord.fingerprint(
                        "https://example.com/a", LATER.plus(1, ChronoUnit.DAYS))),
                "a changed expiry must survive the round trip as a CONFLICT, not a replay");
    }

    @Test
    @DisplayName("the same marker twice for one creator is rejected by the store")
    void duplicateMarkerRejected() {
        UUID id = newCreator();
        links.save(ShortLink.create("dm0001", "https://example.com/a", id, NOW, LATER));
        markers.save(IdempotencyRecord.of(id, "dup", "https://example.com/a", LATER, "dm0001", NOW));
        assertThrows(IllegalStateException.class,
                () -> markers.save(IdempotencyRecord.of(
                        id, "dup", "https://example.com/a", LATER, "dm0001", NOW)),
                "the composite primary key must reject a second row for the same scoped marker");
    }

    @Test
    @DisplayName("redirect events append and read back in time order")
    void redirectEventsAppendAndRead() {
        UUID id = newCreator();
        links.save(ShortLink.create("ev0001", "https://example.com/a", id, NOW, LATER));

        events.append(RedirectEvent.unsaved("ev0001", NOW));
        events.append(RedirectEvent.unsaved("ev0001", NOW.plusSeconds(10)));
        events.append(RedirectEvent.unsaved("ev0001", NOW.plusSeconds(20)));

        assertEquals(3, events.countByShortCode("ev0001"));
        List<RedirectEvent> window = events.findByShortCodeBetween(
                "ev0001", NOW, NOW.plusSeconds(15));
        assertEquals(2, window.size(), "the upper bound is exclusive, so the 20s event is outside it");
        assertEquals(NOW, window.get(0).occurredAt());
        assertTrue(window.get(0).id() > 0, "the store assigns the identifier");
    }

    @Test
    @DisplayName("CL-002: the analytics table holds no column able to identify a follower")
    void analyticsTableHoldsNothingIdentifying() throws Exception {
        List<String> columns = new java.util.ArrayList<>();
        try (var c = connection(); var s = c.createStatement();
             var rs = s.executeQuery("SELECT column_name FROM information_schema.columns "
                     + "WHERE table_name = 'redirect_event'")) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }
        // Asserted at the STORE, not only on the type: a column could be added by a migration without
        // touching the entity, and the privacy claim plus CR-017's retention posture both rest on the
        // table's shape rather than the class's.
        assertEquals(3, columns.size(),
                "redirect_event must hold exactly three columns; got " + columns);
        for (String forbidden : List.of("ip", "agent", "referr", "device", "geo", "session",
                "visitor", "cookie", "fingerprint")) {
            assertTrue(columns.stream().noneMatch(col -> col.contains(forbidden)),
                    "no column may carry " + forbidden + " (CL-002, POL-PRIV-001); got " + columns);
        }
    }
}
