package agentic.shortener.delivery.auth;

import agentic.shortener.domain.creator.Creator;
import agentic.shortener.domain.creator.CreatorCredential;
import agentic.shortener.domain.creator.CreatorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T052 — creator authentication. FR-URL-018, FR-URL-019. EC-041. ADR-013.
 *
 * <p><strong>EC-041 is the requirement with teeth.</strong> An expired credential's refusal must be
 * byte-identical to a revoked one's, so the distinction is not disclosed. A caller who could tell them
 * apart would learn whether a key they hold was <em>turned off</em> or merely <em>timed out</em> — and,
 * more usefully to an attacker, whether a guessed key ever existed at all.
 *
 * <p>So the refusals are not compared for similarity: every denial produces the <strong>same
 * value</strong>, and the test asserts that across five different reasons for denying.
 *
 * <p><strong>Bearer transport, and the reason is operational.</strong> Proxies, log scrubbers and
 * crash reporters redact {@code Authorization} <em>by default</em>. A custom header would forfeit that
 * for nothing — the key would then appear in every intermediary's logs that nobody thought to configure.
 *
 * <p><strong>Constant-time comparison.</strong> Delegated to {@link CreatorCredential#matches}, which
 * uses {@code MessageDigest.isEqual}. This file asserts the filter does not roll its own comparison,
 * because that is the change a later hand would make without noticing what it cost.
 *
 * <p>Fast tier: the filter's decision logic, driven directly. {@code AuthenticationIT} drives it through
 * HTTP.
 */
@DisplayName("T052 creator authentication")
class CreatorAuthFilterTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** Synthetic. Authorises nothing and exists only in this test. */
    private static final String GOOD_KEY = "crk_UNITTESTONLYUNITTESTONLY0000001";
    private static final String OTHER_KEY = "crk_UNITTESTONLYUNITTESTONLY0000002";

    private final Map<UUID, Creator> creators = new LinkedHashMap<>();
    private final List<CreatorCredential> credentials = new ArrayList<>();

    private final CreatorRepository store = new CreatorRepository() {
        @Override
        public Creator save(Creator creator) {
            creators.put(creator.id(), creator);
            return creator;
        }

        @Override
        public Optional<Creator> findById(UUID creatorId) {
            return Optional.ofNullable(creators.get(creatorId));
        }

        @Override
        public CreatorCredential save(CreatorCredential credential) {
            credentials.add(credential);
            return credential;
        }

        @Override
        public Optional<CreatorCredential> findByKeyHash(String keyHash) {
            return credentials.stream().filter(c -> c.keyHash().equals(keyHash)).findFirst();
        }

        @Override
        public List<CreatorCredential> findCredentialsFor(UUID creatorId) {
            return credentials.stream().filter(c -> c.creatorId().equals(creatorId)).toList();
        }
    };

    private final CreatorAuthFilter filter = new CreatorAuthFilter(store, CLOCK);

    private UUID newCreator() {
        UUID id = UUID.randomUUID();
        store.save(Creator.create(id, "auth-test creator", NOW));
        return id;
    }

    @Test
    @DisplayName("a valid bearer key authenticates its creator")
    void validKeyAuthenticates() {
        UUID creator = newCreator();
        store.save(CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), creator, GOOD_KEY, NOW, NOW.plusSeconds(86_400)));

        CreatorAuthFilter.Decision decision = filter.authenticate("Bearer " + GOOD_KEY);

        assertTrue(decision.authenticated());
        assertEquals(creator, decision.creatorId().orElseThrow());
    }

    @Test
    @DisplayName("EC-041: expired and revoked refusals are the SAME value")
    void ec041ExpiredAndRevokedAreIndistinguishable() {
        UUID creator = newCreator();
        // Expired: created in the past, expiry already gone by NOW.
        store.save(CreatorCredential.fromPresentedKey(UUID.randomUUID(), creator, GOOD_KEY,
                NOW.minusSeconds(7200), NOW.minusSeconds(3600)));
        // Revoked: expiry still in the future, revocation already applied.
        store.save(CreatorCredential.fromPresentedKey(UUID.randomUUID(), creator, OTHER_KEY,
                NOW.minusSeconds(7200), NOW.plusSeconds(86_400)).revoke(NOW.minusSeconds(60)));

        CreatorAuthFilter.Decision expired = filter.authenticate("Bearer " + GOOD_KEY);
        CreatorAuthFilter.Decision revoked = filter.authenticate("Bearer " + OTHER_KEY);

        assertFalse(expired.authenticated());
        assertFalse(revoked.authenticated());
        // Identity, not equality. Two separately-built denials are equal today and can drift apart
        // tomorrow, and that drift would disclose exactly the distinction CR-002 forbids disclosing.
        org.junit.jupiter.api.Assertions.assertSame(expired, revoked,
                "an expired credential's refusal must be byte-identical to a revoked one's (EC-041)");
        assertEquals(expired.toString(), revoked.toString(),
                "including the rendered form, which is what reaches a response body");
    }

    @Test
    @DisplayName("every denial is the same value, whatever the reason")
    void everyDenialIsIdentical() {
        // Five reasons, one answer. If a guessed key produced a different refusal from a revoked one,
        // an attacker could confirm which guesses correspond to real credentials.
        UUID creator = newCreator();
        store.save(CreatorCredential.fromPresentedKey(UUID.randomUUID(), creator, GOOD_KEY,
                NOW.minusSeconds(7200), NOW.minusSeconds(3600)));

        List<CreatorAuthFilter.Decision> denials = List.of(
                filter.authenticate(null),                           // no header at all
                filter.authenticate(""),                             // empty header
                filter.authenticate("Basic " + GOOD_KEY),            // wrong scheme
                filter.authenticate("Bearer crk_NEVEREXISTEDTESTONLY0000"),  // unknown key
                filter.authenticate("Bearer " + GOOD_KEY));          // known but expired

        for (CreatorAuthFilter.Decision denial : denials) {
            assertFalse(denial.authenticated());
            org.junit.jupiter.api.Assertions.assertSame(denials.get(0), denial,
                    "all denials must be the identical value; this one differed");
        }
    }

    @Test
    @DisplayName("a denial carries no creator and nothing about why")
    void denialDisclosesNothing() {
        CreatorAuthFilter.Decision denial = filter.authenticate("Bearer crk_WRONGTESTONLY000000000000");

        assertTrue(denial.creatorId().isEmpty(), "no identity may leak from a failed authentication");
        assertFalse(denial.toString().contains("expired"), denial.toString());
        assertFalse(denial.toString().contains("revoked"), denial.toString());
        assertFalse(denial.toString().contains("crk_"), "and the presented key must not be echoed");
    }

    @Test
    @DisplayName("the scheme must be Bearer, and the key is taken verbatim after it")
    void bearerSchemeOnly() {
        UUID creator = newCreator();
        store.save(CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), creator, GOOD_KEY, NOW, NOW.plusSeconds(86_400)));

        assertTrue(filter.authenticate("Bearer " + GOOD_KEY).authenticated());
        // The hash covers the FULL presented string, so a trimmed, padded or re-cased key is a
        // different key. That is the property CreatorCredentialTest already pins down; here it means
        // the filter must not "helpfully" clean the value up.
        assertFalse(filter.authenticate("Bearer " + GOOD_KEY + " ").authenticated());
        assertFalse(filter.authenticate("Bearer " + GOOD_KEY.toLowerCase(java.util.Locale.ROOT))
                .authenticated());
        assertFalse(filter.authenticate("bearer " + GOOD_KEY).authenticated(),
                "the scheme name is matched exactly rather than guessed at");
        assertFalse(filter.authenticate(GOOD_KEY).authenticated(), "a bare key is not a bearer token");
    }

    @Test
    @DisplayName("a credential whose creator no longer exists does not authenticate")
    void orphanedCredentialDenied() {
        // The credential is valid on its own terms and points at nothing. Authenticating it would
        // produce a request with an owner that cannot be looked up, and KE-01 has no state for that.
        UUID absent = UUID.randomUUID();
        store.save(CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), absent, GOOD_KEY, NOW, NOW.plusSeconds(86_400)));

        assertFalse(filter.authenticate("Bearer " + GOOD_KEY).authenticated());
    }

    @Test
    @DisplayName("GUARD: the filter rolls no comparison and no digest of its own")
    void noHandRolledCryptography() throws Exception {
        // Constant-time comparison lives in CreatorCredential.matches, which uses MessageDigest.isEqual.
        // A filter doing its own String.equals would reintroduce a timing side channel, and it is
        // exactly the change a later hand makes without noticing the cost.
        String active = activeSource(
                "src/main/java/agentic/shortener/delivery/auth/CreatorAuthFilter.java");

        for (String forbidden : List.of("MessageDigest", "equals(", "compareTo", "startsWith(keyHash",
                "SHA-256")) {
            assertFalse(active.contains(forbidden),
                    "the filter must delegate comparison and hashing to CreatorCredential; found '"
                            + forbidden + "'");
        }
        assertTrue(active.contains("hashOf"), "it should look a credential up by the shared hash");
        assertTrue(active.contains("isUsableAt"),
                "and ask the credential whether it is usable rather than re-deriving the answer");
    }

    @Test
    @DisplayName("GUARD: the filter never writes the presented key anywhere")
    void keyMaterialIsNeverLogged() throws Exception {
        // FR-URL-019: the application MUST NOT write key material anywhere, at any level.
        String active = activeSource(
                "src/main/java/agentic/shortener/delivery/auth/CreatorAuthFilter.java");
        for (String forbidden : List.of("LOG", "Logger", "System.out", "printStackTrace")) {
            assertFalse(active.contains(forbidden),
                    "the filter holds key material; it must not log at all. Found '" + forbidden + "'");
        }
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
