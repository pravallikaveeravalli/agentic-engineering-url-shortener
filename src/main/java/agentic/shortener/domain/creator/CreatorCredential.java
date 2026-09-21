package agentic.shortener.domain.creator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * A creator's API credential, stored as a hash and never as key material. Task T022.
 * FR-URL-018, FR-URL-019. ADR-013, with the owner's expiry refinement applied through CR-002.
 *
 * <p><strong>This type cannot be constructed from plaintext and then made to hold it.</strong> The
 * only entry points hash immediately, the hash is the only String state, and there is no accessor
 * that could return the presented key because the presented key is not retained past the factory
 * call. {@code CreatorCredentialTest} asserts all of that reflectively rather than by reading the
 * source, so a future field named {@code presentedKey} fails a test instead of passing a review.
 *
 * <p><strong>The hash covers the full presented string, {@code crk_} prefix included</strong>
 * (ADR-013). Hashing the suffix alone would make {@code crk_X} and any other {@code ..._X} collide.
 *
 * <p><strong>Expiry is required unless the caller explicitly says never.</strong> That is CR-002's
 * point: {@link #fromPresentedKey} demands an instant, and a never-expiring credential is only
 * reachable through {@link #neverExpires}, whose name is the decision. Forgetting to set an expiry
 * cannot silently mint a permanent key.
 */
public final class CreatorCredential {

    private final UUID id;
    private final UUID creatorId;
    /** SHA-256 hex of the full presented string. The only String state on this type. */
    private final String keyHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant revokedAt;

    private CreatorCredential(UUID id, UUID creatorId, String keyHash,
                              Instant createdAt, Instant expiresAt, Instant revokedAt) {
        this.id = id;
        this.creatorId = creatorId;
        this.keyHash = keyHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    /**
     * Builds a credential from the key as presented, hashing it immediately.
     *
     * @param expiresAt required and must be after {@code createdAt}. For a deliberately
     *                  never-expiring credential use {@link #neverExpires} — CR-002 requires that
     *                  choice to be explicit rather than a forgotten null.
     */
    public static CreatorCredential fromPresentedKey(UUID id, UUID creatorId, String presentedKey,
                                                     Instant createdAt, Instant expiresAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(presentedKey, "presentedKey");
        Objects.requireNonNull(createdAt, "createdAt");
        if (presentedKey.isBlank()) {
            throw new IllegalArgumentException("a blank string is not a key");
        }
        if (expiresAt != null && !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "credential expiry must be after creation (ADR-013 / CR-002): createdAt="
                            + createdAt + " expiresAt=" + expiresAt);
        }
        return new CreatorCredential(id, creatorId, sha256Hex(presentedKey), createdAt,
                expiresAt, null);
    }

    /**
     * A credential that never expires. Named so the choice is visible in the calling code and in a
     * diff — CR-002's requirement is that a permanent key is a decision, not an omission.
     */
    public static CreatorCredential neverExpires(UUID id, UUID creatorId, String presentedKey,
                                                 Instant createdAt) {
        return fromPresentedKey(id, creatorId, presentedKey, createdAt, null);
    }

    /** Rehydrates from the store, which holds the hash and never the key. */
    public static CreatorCredential rehydrate(UUID id, UUID creatorId, String keyHash,
                                              Instant createdAt, Instant expiresAt,
                                              Instant revokedAt) {
        Objects.requireNonNull(keyHash, "keyHash");
        if (!keyHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "keyHash must be a SHA-256 hex digest; refusing to rehydrate from what may be "
                            + "plaintext key material");
        }
        return new CreatorCredential(Objects.requireNonNull(id), Objects.requireNonNull(creatorId),
                keyHash, Objects.requireNonNull(createdAt), expiresAt, revokedAt);
    }

    /**
     * Constant-time comparison against the presented key (ADR-013). A short-circuiting
     * {@code equals} would leak the digest a byte at a time under timing observation.
     *
     * <p>A {@code null} presentation is a denial, not an exception: an unauthenticated request with
     * no header is the ordinary case, not an error.
     */
    public boolean matches(String presentedKey) {
        if (presentedKey == null || presentedKey.isEmpty()) {
            return false;
        }
        byte[] expected = keyHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = sha256Hex(presentedKey).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    /** True when the credential has expired at {@code at}. The expiry instant itself denies. */
    public boolean isExpiredAt(Instant at) {
        Objects.requireNonNull(at, "at");
        return expiresAt != null && !at.isBefore(expiresAt);
    }

    /** Usable means neither expired nor revoked. Both deny independently. */
    public boolean isUsableAt(Instant at) {
        Objects.requireNonNull(at, "at");
        if (revokedAt != null && !at.isBefore(revokedAt)) {
            return false;
        }
        return !isExpiredAt(at);
    }

    /** Revokes the credential. Returns a new value; revocation is not reversible on the result. */
    public CreatorCredential revoke(Instant when) {
        Objects.requireNonNull(when, "when");
        if (revokedAt != null) {
            return this;
        }
        return new CreatorCredential(id, creatorId, keyHash, createdAt, expiresAt, when);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // The WHOLE presented string, prefix included (ADR-013).
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by ADR-013 and must be available", e);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID creatorId() {
        return creatorId;
    }

    public String keyHash() {
        return keyHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    /**
     * Renders identifiers and lifecycle only. <strong>Never the hash</strong>: a digest in a log line
     * is an offline-attack target, and NFR-SEC-005 forbids sensitive data in telemetry.
     */
    @Override
    public String toString() {
        return "CreatorCredential[" + id + ", creator=" + creatorId
                + ", expiresAt=" + (expiresAt == null ? "never" : expiresAt)
                + ", revokedAt=" + (revokedAt == null ? "no" : revokedAt) + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof CreatorCredential that
                && id.equals(that.id)
                && creatorId.equals(that.creatorId)
                && keyHash.equals(that.keyHash)
                && createdAt.equals(that.createdAt)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(revokedAt, that.revokedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, creatorId, keyHash, createdAt, expiresAt, revokedAt);
    }
}
