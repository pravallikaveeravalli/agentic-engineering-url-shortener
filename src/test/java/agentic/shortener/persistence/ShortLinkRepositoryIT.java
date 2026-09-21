package agentic.shortener.persistence;

import agentic.shortener.domain.creator.Creator;
import agentic.shortener.domain.link.LinkState;
import agentic.shortener.domain.link.ShortCodeCollisionException;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T026 — repository implementations against a real PostgreSQL 16.
 *
 * <p>T026's guard is the load-bearing one: <strong>uniqueness is guaranteed by the database
 * constraint, never by application check-then-insert</strong>. The concurrency test below is what
 * makes that a finding rather than a claim — it runs real threads against a real unique index, and a
 * check-then-insert implementation fails it. ADR-011's reason for a real store is exactly this: a mock
 * has no unique index, so it cannot be the thing under test.
 *
 * <p>Integration tier (Failsafe). Extends the shared harness, which also waits for the store to be
 * reachable from this process rather than merely healthy in its container.
 */
@DisplayName("T026 ShortLinkRepository against a real store")
class ShortLinkRepositoryIT extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final Instant LATER = NOW.plus(7, ChronoUnit.DAYS);

    private JdbcShortLinkRepository links;
    private JdbcCreatorRepository creators;
    private UUID creatorId;

    @BeforeEach
    void migrateAndWire() throws Exception {
        MigrationSupport.migrate(POSTGRES, "public");
        links = new JdbcShortLinkRepository(() -> connection());
        creators = new JdbcCreatorRepository(() -> connection());

        // Every link needs an owner (KE-01), so a creator row must exist for the FK to hold.
        creatorId = UUID.randomUUID();
        creators.save(Creator.create(creatorId, "T026 creator", NOW));
    }

    private ShortLink link(String code) {
        return ShortLink.create(code, "https://example.com/" + code, creatorId, NOW, LATER);
    }

    @Test
    @DisplayName("a link round-trips through the real store unchanged")
    void roundTrip() {
        ShortLink saved = links.save(link("rt0001"));
        assertEquals("rt0001", saved.shortCode());

        ShortLink found = links.findByShortCode("rt0001").orElseThrow();
        assertEquals("https://example.com/rt0001", found.destination());
        assertEquals(creatorId, found.creatorId());
        assertEquals(LinkState.ACTIVE, found.state());
        // Instants must survive the round trip. A TIMESTAMPTZ read back through a different default
        // zone is a classic silent corruption, so it is compared rather than assumed.
        assertEquals(NOW, found.createdAt());
        assertEquals(LATER, found.expiresAt());
    }

    @Test
    @DisplayName("an unknown code is an empty Optional, not an exception")
    void unknownCode() {
        assertTrue(links.findByShortCode("nosuch").isEmpty(),
                "FR-URL-007: an unknown code is an ordinary outcome");
    }

    @Test
    @DisplayName("FR-URL-006: the DATABASE rejects a duplicate code, surfaced as a domain exception")
    void duplicateRejectedByTheStore() {
        links.save(link("dup001"));

        ShortCodeCollisionException thrown = assertThrows(ShortCodeCollisionException.class,
                () -> links.save(link("dup001")),
                "the unique constraint must reject the second insert");
        assertEquals("dup001", thrown.shortCode(),
                "the collision must name the code so a retrying caller can record the attempt");
    }

    @Test
    @DisplayName("the unique index actually exists in the migration, not only in intent")
    void uniqueIndexPresent() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            // A PRIMARY KEY on short_code satisfies FR-URL-006; this asserts the constraint is
            // really there, because the concurrency guarantee rests entirely on it.
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(*) FROM pg_constraint c "
                            + "JOIN pg_class t ON t.oid = c.conrelid "
                            + "WHERE t.relname = 'short_link' AND c.contype IN ('p','u')")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 1,
                        "short_link must carry a primary-key or unique constraint on its code");
            }
        }
    }

    @Test
    @DisplayName("FR-URL-013: under real concurrency, exactly one writer wins the same code")
    void concurrentInsertsOfTheSameCode() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger collisions = new AtomicInteger();
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                jobs.add(() -> {
                    try {
                        links.save(link("race01"));
                        wins.incrementAndGet();
                    } catch (ShortCodeCollisionException expected) {
                        collisions.incrementAndGet();
                    }
                    return null;
                });
            }
            for (Future<Void> f : pool.invokeAll(jobs)) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        // This is the assertion a check-then-insert implementation cannot pass: between its SELECT
        // and its INSERT another thread commits, and two writers both believe they won.
        assertEquals(1, wins.get(),
                "exactly one writer may win; got " + wins.get() + " winners and "
                        + collisions.get() + " collisions. More than one winner means uniqueness is "
                        + "being enforced in application code rather than by the constraint");
        assertEquals(threads - 1, collisions.get(),
                "every other writer must see a collision");
    }

    @Test
    @DisplayName("expire() applies the only permitted transition and persists it")
    void expirePersists() {
        links.save(link("exp001"));
        ShortLink active = links.findByShortCode("exp001").orElseThrow();
        assertEquals(LinkState.ACTIVE, active.state());

        links.expire(active);

        ShortLink reloaded = links.findByShortCode("exp001").orElseThrow();
        assertEquals(LinkState.EXPIRED, reloaded.state());
        assertTrue(reloaded.isExpiredAt(NOW), "an explicitly expired link is expired at any instant");
    }

    @Test
    @DisplayName("countByCreator feeds the per-creator creation tier (PVT-012)")
    void countByCreator() {
        long before = links.countByCreator(creatorId);
        links.save(link("cnt001"));
        links.save(link("cnt002"));
        assertEquals(before + 2, links.countByCreator(creatorId));

        assertEquals(0, links.countByCreator(UUID.randomUUID()),
                "a creator with no links counts zero rather than failing");
    }

    @Test
    @DisplayName("FR-URL-014: a link cannot be stored without an existing owner")
    void ownerIsEnforcedByTheStore() {
        ShortLink orphan = ShortLink.create(
                "orph01", "https://example.com/orph", UUID.randomUUID(), NOW, LATER);
        // KE-01 says there is no link without an owner, and the foreign key is what makes that true
        // at the store rather than only in the type.
        assertThrows(RuntimeException.class, () -> links.save(orphan),
                "the creator foreign key must reject a link whose owner does not exist");
    }
}
