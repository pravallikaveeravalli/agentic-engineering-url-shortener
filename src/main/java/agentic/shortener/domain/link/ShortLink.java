package agentic.shortener.domain.link;

import agentic.shortener.domain.validation.SchemeAllowList;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A short link. Task T021. FR-URL-001, FR-URL-004, FR-URL-008, FR-URL-009. ADR-007.
 *
 * <p><strong>Invariants live here, not in callers.</strong> That is T021's Done condition, and the
 * reason every rule is checked in {@link #create} rather than in a service: a type that can be
 * constructed into an invalid state makes every future caller responsible for remembering the rule,
 * and one of them will not.
 *
 * <p>Immutable. {@link #expire()} returns a new value rather than mutating, so a caller holding an
 * older reference cannot be surprised, and the single permitted transition cannot be applied halfway.
 *
 * <p>Pure domain: no framework, no persistence, no control-plane import. T014 asserts that, and T015
 * proves T014 can fail.
 */
public final class ShortLink {

    /** FR-URL-001. */
    private static final int MAX_DESTINATION_LENGTH = 2048;

    /**
     * ADR-007's fixed alphabet. Unreserved URL characters only, so a code never needs escaping and
     * a copied link cannot be mangled in transit.
     */
    private static final String CODE_PATTERN = "[A-Za-z0-9_-]+";

    private final String shortCode;
    private final String destination;
    private final UUID creatorId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final LinkState state;

    private ShortLink(String shortCode, String destination, UUID creatorId,
                      Instant createdAt, Instant expiresAt, LinkState state) {
        this.shortCode = shortCode;
        this.destination = destination;
        this.creatorId = creatorId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.state = state;
    }

    /**
     * The only way to mint a link. Every invariant is enforced before an instance exists.
     *
     * @throws NullPointerException     if any required value is absent — including the creator,
     *                                  because there is no link without an owner (KE-01)
     * @throws IllegalArgumentException if any stated bound or rule is broken
     */
    public static ShortLink create(String shortCode, String destination, UUID creatorId,
                                   Instant createdAt, Instant expiresAt) {
        Objects.requireNonNull(shortCode, "shortCode");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(creatorId, "creatorId: there is no link without an owner (KE-01)");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");

        if (!shortCode.matches(CODE_PATTERN)) {
            throw new IllegalArgumentException(
                    "short code must be non-empty and drawn from ADR-007's alphabet [A-Za-z0-9_-]: '"
                            + shortCode + "'");
        }
        if (destination.isBlank()) {
            throw new IllegalArgumentException("destination must be non-empty (FR-URL-001)");
        }
        if (destination.length() > MAX_DESTINATION_LENGTH) {
            throw new IllegalArgumentException("destination exceeds " + MAX_DESTINATION_LENGTH
                    + " characters (FR-URL-001): " + destination.length());
        }
        // FR-URL-004, non-waivable. Delegated to the one allow-list (T035) rather than kept as a
        // second copy here: two definitions of a security control drift, and the copy nobody tests is
        // the one that stays wrong.
        SchemeAllowList.requirePermitted(destination);

        // FR-URL-009: strictly after. Equal instants would mean a link expiring the moment it
        // exists, which is the already-expired state the requirement forbids.
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "expiry must be strictly after creation (FR-URL-009, EC-014): createdAt="
                            + createdAt + " expiresAt=" + expiresAt);
        }

        return new ShortLink(shortCode, destination, creatorId, createdAt, expiresAt,
                LinkState.ACTIVE);
    }

    /**
     * Rehydrates a persisted link. Used by the persistence layer only; it trusts the stored state
     * because the store's own constraints enforce the same rules (V1 baseline), and re-deriving
     * ACTIVE/EXPIRED here would silently overwrite a deliberate compensation.
     */
    public static ShortLink rehydrate(String shortCode, String destination, UUID creatorId,
                                      Instant createdAt, Instant expiresAt, LinkState state) {
        ShortLink validated = create(shortCode, destination, creatorId, createdAt, expiresAt);
        return new ShortLink(validated.shortCode, validated.destination, validated.creatorId,
                validated.createdAt, validated.expiresAt, Objects.requireNonNull(state, "state"));
    }

    /**
     * The single permitted mutation. Returns a new value; the receiver is unchanged.
     *
     * <p>Idempotent by design: this is the Compensation Register's action for a link that should not
     * have been created, and a compensating action must be safe to call twice even though EC-022
     * applies it at most once.
     */
    public ShortLink expire() {
        if (state == LinkState.EXPIRED) {
            return this;
        }
        return new ShortLink(shortCode, destination, creatorId, createdAt, expiresAt,
                LinkState.EXPIRED);
    }

    /**
     * FR-URL-008. <strong>The expiry instant itself counts as expired</strong> — FR-URL-009 requires
     * the boundary to be defined, and this is the definition.
     */
    public boolean isExpiredAt(Instant at) {
        Objects.requireNonNull(at, "at");
        return state == LinkState.EXPIRED || !at.isBefore(expiresAt);
    }

    public String shortCode() {
        return shortCode;
    }

    public String destination() {
        return destination;
    }

    public UUID creatorId() {
        return creatorId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public LinkState state() {
        return state;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ShortLink that)) {
            return false;
        }
        return shortCode.equals(that.shortCode)
                && destination.equals(that.destination)
                && creatorId.equals(that.creatorId)
                && createdAt.equals(that.createdAt)
                && expiresAt.equals(that.expiresAt)
                && state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(shortCode, destination, creatorId, createdAt, expiresAt, state);
    }

    @Override
    public String toString() {
        return "ShortLink[" + shortCode + " -> " + destination + ", creator=" + creatorId
                + ", state=" + state + ", expiresAt=" + expiresAt + "]";
    }
}
