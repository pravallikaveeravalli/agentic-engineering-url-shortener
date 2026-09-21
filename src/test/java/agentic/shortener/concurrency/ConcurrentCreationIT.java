package agentic.shortener.concurrency;

import agentic.shortener.application.CreateLinkUseCase;
import agentic.shortener.domain.creator.Creator;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.shortcode.ShortCodeGenerator;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.JdbcCreatorRepository;
import agentic.shortener.persistence.JdbcShortLinkRepository;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T040 — concurrent creation against a real PostgreSQL. FR-URL-013, FR-URL-006. ADR-011.
 *
 * <p><strong>A mocked store cannot exhibit what this proves.</strong> The invariant is enforced by a
 * unique constraint inside the database, and the failure mode is two transactions racing between a read
 * and a write. An in-memory fake decides that race in Java, where it does not exist. That is ADR-011's
 * whole argument for Testcontainers, and it is why this test is in the integration tier.
 *
 * <p><strong>Barriers, not sleeps</strong> — T040's guard. Every thread parks on one latch and is
 * released together, so the contention is real and the test is deterministic. A sleep would make the
 * overlap a matter of scheduling luck: it would pass on a fast machine, fail on a loaded one, and prove
 * nothing either way.
 *
 * <p><strong>Two shapes of contention.</strong> The first is natural: {@value #CONCURRENCY} clients
 * creating at once, which is PVT-003. The second is forced: every client draws the <em>same</em> code
 * first, so exactly one may have it and the other ninety-nine must retry. The second is the one that
 * would catch a check-then-insert, because natural collisions at 57⁷ essentially never happen and a
 * broken implementation would sail through the first test alone.
 *
 * <p>T040's Done says "no flakiness across ten runs", so each is a {@code @RepeatedTest(10)}.
 */
@DisplayName("T040 concurrent creation at PVT-003")
class ConcurrentCreationIT extends PostgresIntegrationTest {

    /** PVT-003: 100 sustained concurrent clients. */
    private static final int CONCURRENCY = 100;

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ConnectionSource connections;
    private JdbcShortLinkRepository links;
    private UUID creator;

    @BeforeEach
    void setUp() throws Exception {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();

        links = new JdbcShortLinkRepository(connections);
        creator = UUID.randomUUID();
        new JdbcCreatorRepository(connections).save(
                Creator.create(creator, "concurrency-test creator", NOW));

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM short_link")) {
            ps.executeUpdate();
        }
    }

    /**
     * A generator returning exactly these codes, in order, by driving the CSPRNG seam. Typing that seam
     * as {@link SecureRandom} is what keeps this stub possible while keeping a weak PRNG out (T038).
     */
    private static ShortCodeGenerator returning(String... codes) {
        String alphabet = new ShortCodeGenerator().alphabet();
        Deque<Integer> draws = new ArrayDeque<>();
        for (String code : codes) {
            for (char c : code.toCharArray()) {
                draws.add(alphabet.indexOf(c));
            }
        }
        return new ShortCodeGenerator(new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                synchronized (draws) {
                    if (draws.isEmpty()) {
                        throw new IllegalStateException("scripted generator exhausted");
                    }
                    return draws.poll();
                }
            }
        });
    }

    private <T> List<Future<T>> runTogether(List<Callable<T>> work) throws Exception {
        // One latch as the starting gate. Every thread is already running and blocked on it, so the
        // release is the closest thing to simultaneous this machine can offer.
        CountDownLatch ready = new CountDownLatch(work.size());
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        try {
            List<Future<T>> futures = new java.util.ArrayList<>();
            for (Callable<T> task : work) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "threads did not reach the barrier");
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "creation did not finish");
            return futures;
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(10)
    @DisplayName("PVT-003: 100 concurrent creations converge on 100 distinct codes")
    void concurrentCreationsAreDistinct() throws Exception {
        CreateLinkUseCase useCase =
                new CreateLinkUseCase(links, new ShortCodeGenerator(), CLOCK);

        List<Callable<ShortLink>> work = new java.util.ArrayList<>();
        for (int i = 0; i < CONCURRENCY; i++) {
            String destination = "https://example.com/concurrent/" + i;
            work.add(() -> useCase.create(creator, destination, NOW.plusSeconds(3600)));
        }

        Set<String> codes = ConcurrentHashMap.newKeySet();
        for (Future<ShortLink> future : runTogether(work)) {
            assertTrue(codes.add(future.get().shortCode()), "a code was issued twice");
        }

        assertEquals(CONCURRENCY, codes.size());
        assertEquals(CONCURRENCY, countLinks(), "every creation must have produced exactly one row");
        assertEquals(CONCURRENCY, countDistinctDestinations(),
                "no destination may have been lost to an overwrite");
    }

    @RepeatedTest(10)
    @DisplayName("forced contention: 100 clients draw the SAME code; exactly one keeps it")
    void forcedContentionOnOneCode() throws Exception {
        String hot = "Hotcode";
        AtomicInteger next = new AtomicInteger();

        List<Callable<ShortLink>> work = new java.util.ArrayList<>();
        for (int i = 0; i < CONCURRENCY; i++) {
            String destination = "https://example.com/contended/" + i;
            // Each client draws the hot code FIRST, then a code of its own. Exactly one insert of the
            // hot code can succeed; every other client must take the collision and retry.
            String fallback = uniqueCode(next.getAndIncrement());
            CreateLinkUseCase useCase =
                    new CreateLinkUseCase(links, returning(hot, fallback), CLOCK);
            work.add(() -> useCase.create(creator, destination, NOW.plusSeconds(3600)));
        }

        Set<String> codes = new HashSet<>();
        int hotWinners = 0;
        String hotDestination = null;
        for (Future<ShortLink> future : runTogether(work)) {
            ShortLink link = future.get();
            assertTrue(codes.add(link.shortCode()), "code " + link.shortCode() + " was issued twice");
            if (link.shortCode().equals(hot)) {
                hotWinners++;
                hotDestination = link.destination();
            }
        }

        assertEquals(1, hotWinners, "exactly one client may hold the contended code");
        assertEquals(CONCURRENCY, codes.size(), "every client must still have got a link");
        assertEquals(CONCURRENCY, countLinks());

        // Zero overwrites: the row holding the contended code still belongs to the client that won it.
        assertEquals(hotDestination, destinationOf(hot),
                "the stored row for the contended code does not match the client that won it — "
                        + "something overwrote it");
    }

    private long countLinks() throws Exception {
        return scalar("SELECT COUNT(*) FROM short_link");
    }

    private long countDistinctDestinations() throws Exception {
        return scalar("SELECT COUNT(DISTINCT destination) FROM short_link");
    }

    private long scalar(String sql) throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String destinationOf(String code) throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps =
                     c.prepareStatement("SELECT destination FROM short_link WHERE short_code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** A distinct seven-character code per client, drawn from the real alphabet. */
    private static String uniqueCode(int index) {
        String alphabet = new ShortCodeGenerator().alphabet();
        StringBuilder code = new StringBuilder("Zz");
        int value = index;
        for (int i = 0; i < 5; i++) {
            code.append(alphabet.charAt(value % alphabet.length()));
            value /= alphabet.length();
        }
        return code.toString();
    }
}
