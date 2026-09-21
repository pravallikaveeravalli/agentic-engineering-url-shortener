package agentic.shortener.domain.link;

/**
 * A short code was already taken. Task T025. FR-URL-006. ADR-007.
 *
 * <p>Raised by a {@link ShortLinkRepository} implementation when the store's unique constraint
 * rejects an insert. This is a <strong>domain</strong> exception rather than a leaked
 * {@code DataIntegrityViolationException}, so that ADR-007's bounded-retry caller can act on a
 * collision without importing the persistence framework — which NFR-MNT-002 forbids it from doing.
 *
 * <p>It is a normal, expected outcome under contention, not a fault: a CSPRNG will occasionally
 * produce a code that already exists, and FR-URL-013 requires the system to stay correct when it
 * does.
 */
public class ShortCodeCollisionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String shortCode;

    public ShortCodeCollisionException(String shortCode, Throwable cause) {
        super("short code already taken: " + shortCode, cause);
        this.shortCode = shortCode;
    }

    /** The code that collided, so a retrying caller can record which attempt failed. */
    public String shortCode() {
        return shortCode;
    }
}
