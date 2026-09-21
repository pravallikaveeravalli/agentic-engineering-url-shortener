package agentic.shortener.application;

import agentic.shortener.domain.link.ShortCodeCollisionException;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import agentic.shortener.domain.shortcode.ShortCodeGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T039 — link creation with bounded collision retry. FR-URL-006, FR-URL-001. ADR-007.
 *
 * <p>T039's guard is the unusual one: <em>"the prohibited overwrite must be structurally
 * inexpressible — there is no branch in which an existing link is replaced."</em> That is stronger than
 * "does not overwrite". A behavioural test shows that today's code does not take a branch; it cannot
 * show that no such branch can be written. So this file asserts the shape as well as the behaviour: the
 * repository offers no update, the SQL carries no upsert, and the use case never reads before it writes.
 *
 * <p><strong>Why check-then-insert is the defect and not merely a style.</strong> Looking up the code
 * and then inserting passes every single-threaded test and fails under contention, because another
 * writer commits between the two statements. The unique constraint is the authority; this code's job is
 * to react to it. {@code ConcurrentCreationIT} (T040) makes that difference visible against a real
 * PostgreSQL rather than arguable.
 *
 * <p>Fast tier: the retry logic against a fake store that enforces uniqueness the way the real one does.
 * The real constraint is exercised by {@code ShortLinkRepositoryIT} and T040.
 */
@DisplayName("T039 create with bounded collision retry")
class CreateLinkUseCaseTest {

    private static final UUID CREATOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /**
     * A store that enforces uniqueness the way PostgreSQL does — by refusing the insert, not by being
     * asked first. Records every call so the test can assert what was NOT called.
     */
    private static final class FakeLinkRepository implements ShortLinkRepository {
        private final Map<String, ShortLink> rows = new LinkedHashMap<>();
        private final List<String> calls = new ArrayList<>();

        @Override
        public ShortLink save(ShortLink link) {
            calls.add("save:" + link.shortCode());
            if (rows.containsKey(link.shortCode())) {
                throw new ShortCodeCollisionException(link.shortCode(), null);
            }
            rows.put(link.shortCode(), link);
            return link;
        }

        @Override
        public Optional<ShortLink> findByShortCode(String shortCode) {
            calls.add("findByShortCode:" + shortCode);
            return Optional.ofNullable(rows.get(shortCode));
        }

        @Override
        public ShortLink expire(ShortLink link) {
            calls.add("expire:" + link.shortCode());
            rows.put(link.shortCode(), link.expire());
            return rows.get(link.shortCode());
        }

        @Override
        public long countByCreator(UUID creatorId) {
            return rows.values().stream().filter(l -> l.creatorId().equals(creatorId)).count();
        }
    }

    /**
     * Builds a generator that returns exactly the given codes, in order, by driving the CSPRNG seam.
     *
     * <p>ADR-007 wanted a stub for precisely this test, and typing the seam as {@link SecureRandom}
     * rather than as a settable code list is what keeps the stub possible while keeping a weak PRNG
     * out. Each code is expressed as the sequence of alphabet indices that produces it.
     */
    private static ShortCodeGenerator returning(String... codes) {
        String alphabet = new ShortCodeGenerator().alphabet();
        Deque<Integer> draws = new ArrayDeque<>();
        for (String code : codes) {
            for (char c : code.toCharArray()) {
                int index = alphabet.indexOf(c);
                if (index < 0) {
                    throw new IllegalArgumentException(
                            "'" + c + "' is not in the alphabet, so no draw can produce it");
                }
                draws.add(index);
            }
        }
        return new ShortCodeGenerator(new SecureRandom() {
            @Override
            public int nextInt(int bound) {
                if (draws.isEmpty()) {
                    throw new IllegalStateException("the scripted generator ran out of codes");
                }
                return draws.poll();
            }
        });
    }

    private CreateLinkUseCase useCase(ShortLinkRepository links, ShortCodeGenerator codes) {
        return new CreateLinkUseCase(links, codes, CLOCK);
    }

    @Test
    @DisplayName("the scripted generator itself works — otherwise every test below is meaningless")
    void scriptedGeneratorIsUsable() {
        ShortCodeGenerator codes = returning("aaaaaaa", "bbbbbbb");
        assertEquals("aaaaaaa", codes.next());
        assertEquals("bbbbbbb", codes.next());
    }

    @Test
    @DisplayName("a first creation succeeds on the first attempt")
    void firstCreationSucceeds() {
        FakeLinkRepository store = new FakeLinkRepository();
        ShortLink link = useCase(store, returning("aaaaaaa"))
                .create(CREATOR, "https://example.com/a", NOW.plusSeconds(3600));

        assertEquals("aaaaaaa", link.shortCode());
        assertEquals(List.of("save:aaaaaaa"), store.calls,
                "one insert and nothing else — no lookup, no second write");
    }

    @Test
    @DisplayName("forced collision: the duplicate is retried and the EXISTING link is untouched")
    void forcedCollisionRetriesAndLeavesTheOriginalAlone() {
        FakeLinkRepository store = new FakeLinkRepository();
        ShortLink original = useCase(store, returning("ccccccc"))
                .create(CREATOR, "https://example.com/first", NOW.plusSeconds(3600));

        // The next creation draws the SAME code first, then a fresh one.
        UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
        store.calls.clear();
        ShortLink second = useCase(store, returning("ccccccc", "ddddddd"))
                .create(other, "https://example.com/second", NOW.plusSeconds(7200));

        assertEquals("ddddddd", second.shortCode(), "the collision must produce a NEW code");
        assertEquals(List.of("save:ccccccc", "save:ddddddd"), store.calls,
                "insert, catch, regenerate, insert — and never a read in between");

        // The existing link is untouched: same destination, same creator, same expiry, same state.
        ShortLink reread = store.rows.get("ccccccc");
        assertEquals(original, reread,
                "the colliding attempt must not have altered the link that already held the code");
        assertEquals("https://example.com/first", reread.destination());
        assertEquals(CREATOR, reread.creatorId());
    }

    @Test
    @DisplayName("bound exhaustion FAILS rather than reusing a code")
    void exhaustionFailsRatherThanReusing() {
        FakeLinkRepository store = new FakeLinkRepository();
        ShortLink original = useCase(store, returning("eeeeeee"))
                .create(CREATOR, "https://example.com/first", NOW.plusSeconds(3600));

        // A generator stuck on one code — the systematic failure the bound exists to surface.
        String[] stuck = new String[CreateLinkUseCase.MAX_CODE_ATTEMPTS + 1];
        java.util.Arrays.fill(stuck, "eeeeeee");
        store.calls.clear();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> useCase(store, returning(stuck))
                        .create(CREATOR, "https://example.com/second", NOW.plusSeconds(7200)));

        assertTrue(thrown.getMessage().contains(String.valueOf(CreateLinkUseCase.MAX_CODE_ATTEMPTS)),
                "the failure should say how many attempts were made: " + thrown.getMessage());
        assertEquals(CreateLinkUseCase.MAX_CODE_ATTEMPTS, store.calls.size(),
                "exactly the bound, not one more: " + store.calls);
        assertEquals(original, store.rows.get("eeeeeee"),
                "exhaustion must leave the existing link exactly as it was");
        assertEquals(1, store.rows.size(), "nothing new may have been written");
    }

    @Test
    @DisplayName("GUARD: the repository offers no way to replace a link")
    void theRepositoryCannotOverwrite() {
        // Structural, not behavioural. An update or upsert method would make the prohibited branch
        // writable even if nothing wrote it today, and "nobody would do that" is not a control.
        for (Method method : ShortLinkRepository.class.getDeclaredMethods()) {
            String name = method.getName();
            assertFalse(name.matches("^(update|replace|upsert|merge|put|overwrite|saveOrUpdate).*"),
                    "ShortLinkRepository declares '" + name + "', which would let a caller replace an "
                            + "existing link. FR-URL-006 says a code must not be reissued while its "
                            + "link exists");
        }
    }

    @Test
    @DisplayName("GUARD: the insert SQL carries no upsert")
    void theSqlCannotOverwrite() throws Exception {
        // ON CONFLICT DO UPDATE would turn the constraint from an authority into a suggestion, and
        // the retry this whole task is about would silently never happen.
        String active = activeSource(
                "src/main/java/agentic/shortener/persistence/JdbcShortLinkRepository.java");
        for (String forbidden : List.of("ON CONFLICT", "DO UPDATE", "UPSERT", "MERGE INTO",
                "INSERT OR REPLACE")) {
            assertFalse(active.toUpperCase().contains(forbidden),
                    "the link insert must not be an upsert; found '" + forbidden + "'");
        }
        assertTrue(active.contains("INSERT INTO short_link"), "the insert should still be there");
    }

    @Test
    @DisplayName("GUARD: the use case never reads before it writes")
    void theUseCaseIsNotCheckThenInsert() throws Exception {
        // The defect that passes single-threaded tests and fails under contention. Asserted on the
        // source because the behavioural version of this check can only ever observe one interleaving.
        String active = activeSource(
                "src/main/java/agentic/shortener/application/CreateLinkUseCase.java");
        for (String forbidden : List.of("findByShortCode", "exists", "isPresent", "expire(")) {
            assertFalse(active.contains(forbidden),
                    "CreateLinkUseCase names '" + forbidden + "'. Creation inserts and reacts to the "
                            + "constraint; it does not look first, and it does not touch an existing "
                            + "link at all");
        }
    }

    @Test
    @DisplayName("every attempt draws a FRESH code, so a retry is not the same insert twice")
    void eachAttemptRegenerates() {
        FakeLinkRepository store = new FakeLinkRepository();
        useCase(store, returning("fffffff")).create(CREATOR, "https://e.com/1", NOW.plusSeconds(60));
        store.calls.clear();

        ShortLink result = useCase(store, returning("fffffff", "ggggggg", "hhhhhhh"))
                .create(CREATOR, "https://e.com/2", NOW.plusSeconds(60));

        assertEquals("ggggggg", result.shortCode());
        assertNotEquals("fffffff", result.shortCode());
        assertEquals(2, store.calls.size(), "one collision, one success: " + store.calls);
    }

    @Test
    @DisplayName("a domain refusal costs no attempt and touches no store")
    void invalidDestinationNeverReachesTheStore() {
        // FR-URL-002's negative clause: a rejected request must not persist anything and must not
        // consume a code from the keyspace.
        FakeLinkRepository store = new FakeLinkRepository();
        assertThrows(IllegalArgumentException.class,
                () -> useCase(store, returning("iiiiiii"))
                        .create(CREATOR, "javascript:alert(1)", NOW.plusSeconds(60)));
        assertTrue(store.calls.isEmpty(), "the store was touched: " + store.calls);
        assertTrue(store.rows.isEmpty());
    }

    private static String activeSource(String path) throws Exception {
        Path source = Path.of(path);
        assertTrue(Files.exists(source), "missing " + source.toAbsolutePath());
        return Files.readString(source).lines()
                .map(String::stripLeading)
                .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                .reduce("", (a, b) -> a + "\n" + b);
    }
}
