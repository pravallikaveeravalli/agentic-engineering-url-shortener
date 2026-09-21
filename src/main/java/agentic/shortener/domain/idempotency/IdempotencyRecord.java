package agentic.shortener.domain.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Marker-only idempotency. Task T023. FR-URL-012, semantics fixed by CL-008.
 *
 * <p>CL-008 defines exactly three behaviours, and this type exists to make the third one impossible
 * to get wrong by accident:
 * <ol>
 *   <li><strong>No marker</strong> means always mint. There is <strong>no destination-based
 *       deduplication at any scope, ever</strong> — which is why nothing here hashes a destination on
 *       its own, and why a lookup is only ever by marker.
 *   <li><strong>Same marker, identical request</strong> is a replay: the original link comes back as
 *       success, labelled, with zero new side effects. {@link #matchesRequest} is that decision.
 *   <li><strong>Same marker, different content</strong> is an explicit conflict. The fingerprint is
 *       what separates this from case 2, and getting it wrong is the damaging failure because it
 *       <em>looks like success</em> while silently discarding the caller's changed intent.
 * </ol>
 *
 * <p>The marker is <strong>scoped per creator</strong> (KE-03): one creator's choice of string must
 * not collide with another's, which would be both a correctness bug and a cross-tenant leak.
 */
public final class IdempotencyRecord {

    /**
     * A separator that cannot appear in a URL or an ISO-8601 instant. Without it, naive concatenation
     * lets one field bleed into the next — ("ab","c") and ("a","bc") would fingerprint identically.
     */
    private static final String FIELD_SEPARATOR = "";

    private final UUID creatorId;
    private final String marker;
    private final String requestFingerprint;
    private final String shortCode;
    private final Instant createdAt;

    private IdempotencyRecord(UUID creatorId, String marker, String requestFingerprint,
                              String shortCode, Instant createdAt) {
        this.creatorId = creatorId;
        this.marker = marker;
        this.requestFingerprint = requestFingerprint;
        this.shortCode = shortCode;
        this.createdAt = createdAt;
    }

    public static IdempotencyRecord of(UUID creatorId, String marker, String destination,
                                       Instant expiresAt, String shortCode, Instant createdAt) {
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(shortCode, "shortCode");
        Objects.requireNonNull(createdAt, "createdAt");
        if (marker.isBlank()) {
            throw new IllegalArgumentException(
                    "a blank marker must not be stored as a usable key: it would collapse every "
                            + "unmarked request into one bucket");
        }
        return new IdempotencyRecord(creatorId, marker, fingerprint(destination, expiresAt),
                shortCode, createdAt);
    }

    /** Rehydrates from the store, which holds the fingerprint rather than the request. */
    public static IdempotencyRecord rehydrate(UUID creatorId, String marker, String fingerprint,
                                              String shortCode, Instant createdAt) {
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint must be a SHA-256 hex digest");
        }
        return new IdempotencyRecord(Objects.requireNonNull(creatorId),
                Objects.requireNonNull(marker), fingerprint,
                Objects.requireNonNull(shortCode), Objects.requireNonNull(createdAt));
    }

    /**
     * Fingerprints the request content that must match for a replay.
     *
     * <p><strong>Both the destination and the expiry are covered.</strong> Covering the destination
     * alone is the defect T023's guard names: a caller reusing a marker with a changed expiry would
     * be told "replay" and their new intent would vanish.
     */
    public static String fingerprint(String destination, Instant expiresAt) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(expiresAt, "expiresAt");
        String canonical = destination + FIELD_SEPARATOR + expiresAt;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }

    /**
     * CL-008 case 2 versus case 3. True means replay; false means conflict.
     *
     * <p>Constant-time, because the fingerprint is derived from caller-supplied content and a timing
     * side channel would let a caller probe another request's content one byte at a time.
     */
    public boolean matchesRequest(String candidateFingerprint) {
        if (candidateFingerprint == null) {
            return false;
        }
        return MessageDigest.isEqual(
                requestFingerprint.getBytes(StandardCharsets.UTF_8),
                candidateFingerprint.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The per-creator scoped key. KE-03 makes the marker unique <em>within</em> a creator, so the
     * creator must be part of the key the store enforces uniqueness on.
     */
    public String scopedKey() {
        return creatorId + FIELD_SEPARATOR + marker;
    }

    public UUID creatorId() {
        return creatorId;
    }

    public String marker() {
        return marker;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public String shortCode() {
        return shortCode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "IdempotencyRecord[creator=" + creatorId + ", marker=" + marker
                + ", shortCode=" + shortCode + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof IdempotencyRecord that
                && creatorId.equals(that.creatorId)
                && marker.equals(that.marker)
                && requestFingerprint.equals(that.requestFingerprint)
                && shortCode.equals(that.shortCode)
                && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(creatorId, marker, requestFingerprint, shortCode, createdAt);
    }
}
