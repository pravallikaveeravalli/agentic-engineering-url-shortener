package agentic.shortener.domain.creator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for creators and their credentials. Task T025. NFR-MNT-002. ADR-002, ADR-013.
 *
 * <p>Credential lookup is <strong>by hash, never by presented key</strong>. A method taking the
 * plaintext would put key material into a query, and from there into a slow-query log, a trace span or
 * an exception message. The caller hashes; the store only ever compares digests.
 *
 * <p>No framework type appears in any signature (NFR-MNT-002), which is what keeps ADR-002's store
 * choice reversible.
 */
public interface CreatorRepository {

    Creator save(Creator creator);

    Optional<Creator> findById(UUID creatorId);

    CreatorCredential save(CreatorCredential credential);

    /**
     * Finds a credential by its SHA-256 hex digest.
     *
     * <p>Deliberately not {@code findByPresentedKey}: the presented key never crosses this boundary.
     */
    Optional<CreatorCredential> findByKeyHash(String keyHash);

    List<CreatorCredential> findCredentialsFor(UUID creatorId);
}
