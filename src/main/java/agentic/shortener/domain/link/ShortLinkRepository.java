package agentic.shortener.domain.link;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for {@link ShortLink}. Task T025. NFR-MNT-002. ADR-002.
 *
 * <p><strong>Owned by the domain, with no framework or JPA type in any signature.</strong> That is
 * what makes ADR-002's reversibility real rather than claimed: swapping the store means writing
 * another implementation of this interface. A leaked {@code Page}, {@code Pageable},
 * {@code EntityManager} or {@code CrudRepository} would put the framework on the domain's side of the
 * boundary and destroy that property. T014 asserts no such import exists; T015 proves T014 can fail.
 *
 * <p>{@link Optional} rather than null for a missing link: a redirect for an unknown code is an
 * ordinary outcome (FR-URL-007), not an exception.
 */
public interface ShortLinkRepository {

    /**
     * Persists a new link.
     *
     * <p><strong>Uniqueness is the store's job, not this method's.</strong> FR-URL-006 is enforced by
     * a unique constraint, and an implementation MUST surface a collision as
     * {@link ShortCodeCollisionException} rather than checking first and then inserting. A
     * check-then-insert is a race under contention, and ADR-007 pairs the constraint with bounded
     * retry precisely because the constraint — not the application — is the authority.
     *
     * @throws ShortCodeCollisionException if the code is already taken
     */
    ShortLink save(ShortLink link);

    Optional<ShortLink> findByShortCode(String shortCode);

    /** Applies the single permitted transition, {@code ACTIVE -> EXPIRED}. Returns the stored value. */
    ShortLink expire(ShortLink link);

    /** Count for a creator, used by the per-creator creation tier (PVT-012). */
    long countByCreator(UUID creatorId);
}
