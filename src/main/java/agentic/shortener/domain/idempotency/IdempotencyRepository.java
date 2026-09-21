package agentic.shortener.domain.idempotency;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for idempotency markers. Task T025. NFR-MNT-002. CL-008.
 *
 * <p>Lookup takes the creator <em>and</em> the marker, because KE-03 scopes the marker per creator. An
 * interface taking the marker alone would make the scoping impossible to enforce at the store, and one
 * creator's marker would collide with another's — a correctness bug and a cross-tenant leak in one.
 *
 * <p>No framework type appears in any signature (NFR-MNT-002).
 */
public interface IdempotencyRepository {

    IdempotencyRecord save(IdempotencyRecord record);

    /** CL-008's lookup: by marker WITHIN a creator. Never by marker alone. */
    Optional<IdempotencyRecord> find(UUID creatorId, String marker);
}
