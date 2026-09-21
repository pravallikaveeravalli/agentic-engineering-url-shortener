package agentic.shortener.domain.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T023 — IdempotencyRecord. FR-URL-012, semantics fixed by CL-008.
 *
 * <p>T023's guard names the case that matters: <strong>the fingerprint must distinguish
 * same-marker-different-content</strong>, which is CL-008 case 3. If it did not, a client reusing a
 * marker with a changed expiry would silently receive the old link as a "replay" — the most damaging
 * of the three behaviours to get wrong, because it looks like success.
 *
 * <p>CL-008's three behaviours, and how they appear here:
 * <ol>
 *   <li>No marker means always mint. <strong>No destination-based dedup at any scope, ever</strong> —
 *       so there is deliberately no test for "same destination collapses", because it must not.
 *   <li>Same marker, identical request means replay as success with zero new side effects.
 *   <li>Same marker, different content means an explicit conflict.
 * </ol>
 *
 * <p>Fast tier.
 */
@DisplayName("T023 IdempotencyRecord")
class IdempotencyRecordTest {

    private static final UUID CREATOR = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_CREATOR = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final Instant EXPIRY = NOW.plus(7, ChronoUnit.DAYS);

    @Test
    @DisplayName("a record carries its marker, fingerprint, minted code and creation instant")
    void shape() {
        IdempotencyRecord r = IdempotencyRecord.of(
                CREATOR, "marker-1", "https://example.com/a", EXPIRY, "abc123", NOW);

        assertEquals(CREATOR, r.creatorId());
        assertEquals("marker-1", r.marker());
        assertEquals("abc123", r.shortCode());
        assertEquals(NOW, r.createdAt());
        assertTrue(r.requestFingerprint().matches("[0-9a-f]{64}"),
                "the fingerprint is a SHA-256 hex digest, got: " + r.requestFingerprint());
    }

    @Test
    @DisplayName("CL-008 case 2: same marker, IDENTICAL request -> replay, not conflict")
    void identicalRequestIsAReplay() {
        IdempotencyRecord first = IdempotencyRecord.of(
                CREATOR, "marker-1", "https://example.com/a", EXPIRY, "abc123", NOW);

        // A retry from a client that never saw the first response. EC-003.
        String retryFingerprint = IdempotencyRecord.fingerprint("https://example.com/a", EXPIRY);

        assertTrue(first.matchesRequest(retryFingerprint),
                "an identical retry must be recognised as a replay and returned as SUCCESS");
        assertEquals(first.requestFingerprint(), retryFingerprint);
    }

    @Test
    @DisplayName("CL-008 case 3: same marker, DIFFERENT content -> must NOT match")
    void differentContentMustNotMatch() {
        IdempotencyRecord first = IdempotencyRecord.of(
                CREATOR, "marker-1", "https://example.com/a", EXPIRY, "abc123", NOW);

        // A different expiry with the same destination. This is the exact case T023's guard names:
        // if the fingerprint ignored expiry, this would be reported as a replay and the caller's
        // changed intent would be silently discarded.
        String differentExpiry = IdempotencyRecord.fingerprint(
                "https://example.com/a", EXPIRY.plus(1, ChronoUnit.DAYS));
        assertFalse(first.matchesRequest(differentExpiry),
                "a changed expiry must NOT be treated as a replay - CL-008 case 3 is a conflict");

        // And a different destination with the same expiry.
        String differentDestination = IdempotencyRecord.fingerprint("https://example.com/b", EXPIRY);
        assertFalse(first.matchesRequest(differentDestination),
                "a changed destination must NOT be treated as a replay");

        assertNotEquals(first.requestFingerprint(), differentExpiry);
        assertNotEquals(first.requestFingerprint(), differentDestination);
    }

    @Test
    @DisplayName("the fingerprint is deterministic, and fields cannot bleed into each other")
    void fingerprintIsDeterministic() {
        String a = IdempotencyRecord.fingerprint("https://example.com/a", EXPIRY);
        String b = IdempotencyRecord.fingerprint("https://example.com/a", EXPIRY);
        assertEquals(a, b, "the same inputs must always fingerprint identically");

        // A field-separator collision is the classic fingerprint bug: naive concatenation makes
        // ("ab","c") and ("a","bc") hash identically. Asserted rather than assumed.
        String x = IdempotencyRecord.fingerprint("https://e.com/a", EXPIRY);
        String y = IdempotencyRecord.fingerprint("https://e.com/a" + EXPIRY, EXPIRY);
        assertNotEquals(x, y, "concatenation must not let one field bleed into the next");
    }

    @Test
    @DisplayName("the marker is scoped PER CREATOR - two creators may use the same marker")
    void markerIsScopedPerCreator() {
        IdempotencyRecord mine = IdempotencyRecord.of(
                CREATOR, "marker-1", "https://example.com/a", EXPIRY, "abc123", NOW);
        IdempotencyRecord theirs = IdempotencyRecord.of(
                OTHER_CREATOR, "marker-1", "https://example.com/z", EXPIRY, "zzz999", NOW);

        // data-model.md KE-03: the marker is the primary key SCOPED PER CREATOR. An unscoped marker
        // would let one creator's choice of string collide with another's - both a correctness bug
        // and a cross-tenant information leak.
        assertNotEquals(mine.scopedKey(), theirs.scopedKey(),
                "the same marker from different creators must not collide");
        assertTrue(mine.scopedKey().contains(CREATOR.toString()),
                "the scoped key must carry the creator, or the scoping is not real");
    }

    @Test
    @DisplayName("marker and creator are required; a blank marker is not a marker")
    void requiredFields() {
        assertThrows(NullPointerException.class, () -> IdempotencyRecord.of(
                null, "marker-1", "https://example.com/a", EXPIRY, "abc123", NOW));
        assertThrows(NullPointerException.class, () -> IdempotencyRecord.of(
                CREATOR, null, "https://example.com/a", EXPIRY, "abc123", NOW));
        assertThrows(IllegalArgumentException.class, () -> IdempotencyRecord.of(
                        CREATOR, "   ", "https://example.com/a", EXPIRY, "abc123", NOW),
                "a blank marker must be refused rather than stored as a usable key");
    }
}
