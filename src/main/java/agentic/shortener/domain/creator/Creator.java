package agentic.shortener.domain.creator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An application-plane identity. Task T022. FR-URL-018. ADR-013.
 *
 * <p><strong>Holds identity only.</strong> A Creator carries no credential and no key material:
 * CN-012 keeps the shortener's actor model and the orchestrator's actor model as separate domains
 * with no credential class crossing between them, and a Creator that held its own key would make the
 * two one thing. {@link CreatorCredential} is a separate type for that reason, and
 * {@code CreatorCredentialTest} asserts the separation reflectively.
 *
 * <p>Immutable. Deactivation returns a new value.
 */
public final class Creator {

    private final UUID id;
    private final String name;
    private final Instant createdAt;
    private final boolean active;

    private Creator(UUID id, String name, Instant createdAt, boolean active) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.active = active;
    }

    public static Creator create(UUID id, String name, Instant createdAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a creator's name must not be blank");
        }
        return new Creator(id, name, createdAt, true);
    }

    public static Creator rehydrate(UUID id, String name, Instant createdAt, boolean active) {
        Creator validated = create(id, name, createdAt);
        return new Creator(validated.id, validated.name, validated.createdAt, active);
    }

    /**
     * Deactivates the creator. Returns a new value.
     *
     * <p>Deactivation does not delete: existing links keep their owner, because a link without an
     * owner is not a state KE-01 permits.
     */
    public Creator deactivate() {
        return active ? new Creator(id, name, createdAt, false) : this;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean active() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Creator that
                && id.equals(that.id)
                && name.equals(that.name)
                && createdAt.equals(that.createdAt)
                && active == that.active;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, createdAt, active);
    }

    @Override
    public String toString() {
        return "Creator[" + id + ", name=" + name + ", active=" + active + "]";
    }
}
