package agentic.shortener.domain.link;

/**
 * The only two states a {@link ShortLink} can hold. Task T021.
 *
 * <p>Two values, not three. There is no {@code DELETED}: data-model.md KE-01 records that a link is
 * <strong>never deleted</strong>, and the only permitted mutation is {@code ACTIVE → EXPIRED}, which
 * is also the compensating action in the Compensation Register. Adding a third value here would
 * quietly create a second mutation path.
 */
public enum LinkState {

    /** Resolvable, subject to its expiry instant. */
    ACTIVE,

    /**
     * No longer resolvable. Reached either by the expiry instant passing or by an explicit
     * {@link ShortLink#expire()} — the register's compensating action for a link that should not
     * have been created.
     */
    EXPIRED
}
