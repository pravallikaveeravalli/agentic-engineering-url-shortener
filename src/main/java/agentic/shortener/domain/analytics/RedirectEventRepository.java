package agentic.shortener.domain.analytics;

import java.time.Instant;
import java.util.List;

/**
 * Persistence boundary for redirect events. Task T025. NFR-MNT-002. ADR-014.
 *
 * <p>Append and read-back only. <strong>There is no delete and no update, and the omission is
 * deliberate.</strong> NFR-AUD-003 (CR-017) retains every record indefinitely, and the store's own
 * privilege grants (T027) make that structural rather than conventional. A {@code deleteOlderThan}
 * method here would be the first step of a retention job this demonstration does not have — and its
 * absence is easier to defend than a method nobody calls.
 *
 * <p>ADR-014 governs {@link #append}'s failure semantics: synchronous, in a separate transaction, with
 * the failure isolated so it never fails the redirect (EC-012).
 *
 * <p>No framework type appears in any signature (NFR-MNT-002).
 */
public interface RedirectEventRepository {

    /**
     * Appends one event.
     *
     * <p>An implementation MUST NOT propagate a failure in a way that fails the caller's redirect.
     * EC-012 requires the redirect to succeed and the failure to be counted against PVT-009's
     * observable append-failure ceiling.
     */
    RedirectEvent append(RedirectEvent event);

    /** Creator-scoped time-series retrieval (FR-URL-011). */
    List<RedirectEvent> findByShortCodeBetween(String shortCode, Instant from, Instant to);

    long countByShortCode(String shortCode);
}
