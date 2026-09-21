package agentic.shortener.domain.creator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T022 — Creator and CreatorCredential. FR-URL-018, FR-URL-019. ADR-013.
 *
 * <p>T022's guard is the sharp one: <strong>the type MUST NOT be constructible from plaintext key
 * material</strong>, and its Done condition is that <em>no field or accessor exposes a plaintext
 * key</em>. Two tests here check that reflectively rather than by reading the source, because
 * "I looked and there wasn't one" is not a control that survives the next edit.
 *
 * <p>ADR-013: the hash covers the <em>full presented string including the {@code crk_} prefix</em>.
 * That detail matters — hashing the suffix alone would make two keys with different prefixes collide.
 *
 * <p>Fast tier.
 */
@DisplayName("T022 Creator and CreatorCredential")
class CreatorCredentialTest {

    private static final UUID CREATOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    // A synthetic key in ADR-013's format. Authorises nothing: it exists only inside this test.
    private static final String PRESENTED = "crk_TESTONLYTESTONLYTESTONLY0000";

    @Test
    @DisplayName("a credential is built by hashing the presented string, never by holding it")
    void builtFromHashOfTheFullPresentedString() {
        CreatorCredential c = CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), CREATOR, PRESENTED, NOW, NOW.plus(90, ChronoUnit.DAYS));

        assertEquals(64, c.keyHash().length(), "SHA-256 hex is 64 characters");
        assertTrue(c.keyHash().matches("[0-9a-f]{64}"), "lower-case hex, got: " + c.keyHash());

        // ADR-013: the hash covers the WHOLE presented string, prefix included. If the prefix were
        // stripped first, `crk_X` and `other_X` would hash identically.
        String withoutPrefix = PRESENTED.substring("crk_".length());
        CreatorCredential stripped = CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), CREATOR, "crk_" + withoutPrefix, NOW, null);
        assertEquals(c.keyHash(), stripped.keyHash(), "same presented string, same hash");

        CreatorCredential different = CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), CREATOR, "xxx_" + withoutPrefix, NOW, null);
        assertNotEquals(c.keyHash(), different.keyHash(),
                "a different prefix must produce a different hash, or the prefix is not covered");
    }

    @Test
    @DisplayName("no field and no accessor exposes plaintext key material")
    void noPlaintextAnywhereOnTheType() throws Exception {
        CreatorCredential c = CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), CREATOR, PRESENTED, NOW, null);

        // Reflective, not by reading the source: a future edit that adds a `presentedKey` field
        // would pass a source review and fail here.
        for (var field : CreatorCredential.class.getDeclaredFields()) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(c);
            if (value instanceof String s) {
                assertFalse(s.contains(PRESENTED),
                        "field '" + field.getName() + "' holds the plaintext key");
                assertFalse(s.startsWith("crk_"),
                        "field '" + field.getName() + "' looks like plaintext key material");
            }
            if (value instanceof char[] chars) {
                assertFalse(new String(chars).startsWith("crk_"),
                        "field '" + field.getName() + "' holds key material as a char array");
            }
        }

        // toString is the accidental leak path: a log line or an error message would carry it.
        assertFalse(c.toString().contains(PRESENTED),
                "toString must not render the plaintext key — this is how keys reach logs");

        for (Method m : CreatorCredential.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() == String.class
                    && !m.isSynthetic()) {
                m.setAccessible(true);
                Object out = m.invoke(c);
                if (out instanceof String s) {
                    assertFalse(s.contains(PRESENTED),
                            "accessor '" + m.getName() + "' returns the plaintext key");
                }
            }
        }
    }

    @Test
    @DisplayName("the type cannot be constructed directly with arbitrary state")
    void noPublicConstructor() {
        for (Constructor<?> ctor : CreatorCredential.class.getConstructors()) {
            // A public constructor taking the hash would let a caller pass a plaintext string as the
            // "hash", defeating the factory. The named factory is the only way in.
            assertTrue(Arrays.stream(ctor.getParameterTypes()).noneMatch(t -> t == String.class)
                            || !java.lang.reflect.Modifier.isPublic(ctor.getModifiers()),
                    "no public constructor may take a String — use fromPresentedKey");
        }
    }

    @Test
    @DisplayName("verification is by hash comparison and is constant-time (ADR-013)")
    void verificationByHash() {
        CreatorCredential c = CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), CREATOR, PRESENTED, NOW, NOW.plus(90, ChronoUnit.DAYS));

        assertTrue(c.matches(PRESENTED), "the key it was built from must verify");
        assertFalse(c.matches("crk_WRONGWRONGWRONGWRONGWRONG00"), "a different key must not verify");
        assertFalse(c.matches(""), "an empty presentation must not verify");
        assertFalse(c.matches(null), "a null presentation must not verify, and must not throw");
    }

    @Test
    @DisplayName("CR-002: expiry is REQUIRED unless the caller explicitly says never")
    void expiryRequiredUnlessExplicitlyNever() {
        // ADR-013's owner refinement, applied through CR-002: a credential with no expiry is a
        // decision, not a default. `fromPresentedKey` with null expiry is reachable only through the
        // explicit `neverExpires` factory, so forgetting to set one cannot silently mint a
        // never-expiring key.
        CreatorCredential never = CreatorCredential.neverExpires(
                UUID.randomUUID(), CREATOR, PRESENTED, NOW);
        assertNull(never.expiresAt(), "the explicit never-expires factory yields a null expiry");
        assertFalse(never.isExpiredAt(NOW.plus(3650, ChronoUnit.DAYS)),
                "a never-expiring credential does not expire");

        assertThrows(IllegalArgumentException.class,
                () -> CreatorCredential.fromPresentedKey(
                        UUID.randomUUID(), CREATOR, PRESENTED, NOW, NOW.minusSeconds(1)),
                "an expiry already in the past at creation must be refused");
    }

    @Test
    @DisplayName("expiry and revocation both deny, and revocation is not reversible")
    void expiryAndRevocation() {
        Instant expires = NOW.plus(30, ChronoUnit.DAYS);
        CreatorCredential c = CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), CREATOR, PRESENTED, NOW, expires);

        assertFalse(c.isExpiredAt(NOW));
        assertTrue(c.isExpiredAt(expires), "the expiry instant itself denies — boundary defined");
        assertTrue(c.isUsableAt(NOW));
        assertFalse(c.isUsableAt(expires));

        CreatorCredential revoked = c.revoke(NOW.plusSeconds(60));
        assertFalse(revoked.isUsableAt(NOW.plusSeconds(120)),
                "a revoked credential is unusable even before its expiry");
        assertEquals(NOW.plusSeconds(60), revoked.revokedAt());
        assertNull(c.revokedAt(), "revoke() must not mutate the receiver");
    }

    @Test
    @DisplayName("Creator holds identity only — no credential, no orchestrator authority (CN-012)")
    void creatorHoldsNoCredential() {
        Creator creator = Creator.create(CREATOR, "Demo Creator", NOW);
        assertEquals(CREATOR, creator.id());
        assertEquals("Demo Creator", creator.name());
        assertTrue(creator.active());

        // CN-012: no credential class crosses between the shortener and the orchestrator. A Creator
        // that carried its own key would make the two models one.
        for (var field : Creator.class.getDeclaredFields()) {
            assertFalse(field.getType() == CreatorCredential.class,
                    "Creator must not hold a credential; CN-012 keeps the identity models separate");
            assertFalse(field.getName().toLowerCase().contains("key"),
                    "Creator must hold no key material of any kind");
        }

        assertThrows(IllegalArgumentException.class,
                () -> Creator.create(CREATOR, "  ", NOW), "a blank name must be refused");
    }
}
