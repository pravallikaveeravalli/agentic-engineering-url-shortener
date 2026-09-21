package agentic.shortener.domain.idempotency;

/**
 * The marker was claimed by another request between resolution and this insert. Task T041/T042 support.
 *
 * <p>A domain exception rather than a leaked {@code SQLException}, for the same reason
 * {@code ShortCodeCollisionException} is one: the caller has to act on it, and NFR-MNT-002 forbids the
 * application plane importing the persistence framework to do so.
 *
 * <p>This exists because {@code (creator_id, marker)} is a composite primary key, so the store — not the
 * application — is the authority on whether a marker is taken. Resolving first and inserting second
 * leaves a window, and the store closing it is the design working rather than failing.
 */
public final class MarkerAlreadyUsedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String scopedKey;

    public MarkerAlreadyUsedException(String scopedKey, Throwable cause) {
        super("idempotency marker already used", cause);
        this.scopedKey = scopedKey;
    }

    /** For logging only. Never for a response body: the marker is caller-supplied text. */
    public String scopedKey() {
        return scopedKey;
    }
}
