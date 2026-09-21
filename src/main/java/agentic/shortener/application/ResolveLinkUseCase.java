package agentic.shortener.application;

import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolution. Task T043. FR-URL-007, FR-URL-008. CR-003.
 *
 * <p><strong>The stored destination, untouched.</strong> FR-URL-007 requires the target to equal what was
 * stored <em>byte for byte</em> and forbids rewriting it at resolution time. Normalization happened once,
 * at creation; doing it again here would mean the link a creator was shown is not the link a follower
 * gets. There is no call to a normalizer below, and a test asserts the source contains none.
 *
 * <p><strong>The target comes from storage and never from request input.</strong> This is the difference
 * between a redirector and an open redirect: if any part of the request could steer the destination,
 * anyone could use this service as a trusted-looking hop to anywhere. The only thing the code is used for
 * is a primary-key lookup.
 *
 * <p><strong>Three outcomes, not two.</strong> Never-issued and expired must be distinguishable
 * (FR-URL-008). A store failure is <em>neither</em> — it is not an answer, so it is not turned into one.
 * It propagates, and the delivery layer maps it to 503. Catching it and returning
 * {@link Outcome#NOT_FOUND} would be the damaging bug: a 404 is a definite statement that the code does
 * not exist, a broken store cannot make that statement, and a creator told 404 would reasonably conclude
 * their link was gone.
 *
 * <p><strong>An expired resolution carries no destination.</strong> An expired link still holds one, and a
 * 410 body that leaked it would let anyone read the target of every dead link.
 *
 * <p><strong>The clock is read after the lookup, not before</strong> — EC-009. A link that was alive when
 * the lookup began and is dead when the decision is taken must not redirect.
 *
 * <p>This is a read. Counting a redirect is FR-URL-010's concern and a separate, non-blocking path
 * (T048–T050); a write here would put the store's availability on the follower's critical path.
 */
public final class ResolveLinkUseCase {

    public enum Outcome {
        /** A known, unexpired code. Redirect, temporary class. */
        REDIRECT,

        /** The code was never issued. */
        NOT_FOUND,

        /** The code exists and the link has expired. Never redirects (FR-URL-008). */
        EXPIRED
    }

    /**
     * @param destination the stored destination, byte for byte — and the empty string for anything other
     *                    than {@link Outcome#REDIRECT}, because there is nothing a caller may be told
     */
    public record Resolution(Outcome outcome, Optional<ShortLink> link, String destination) {

        static Resolution redirect(ShortLink link) {
            return new Resolution(Outcome.REDIRECT, Optional.of(link), link.destination());
        }

        static Resolution notFound() {
            return new Resolution(Outcome.NOT_FOUND, Optional.empty(), "");
        }

        static Resolution expired(ShortLink link) {
            return new Resolution(Outcome.EXPIRED, Optional.of(link), "");
        }
    }

    private final ShortLinkRepository links;
    private final Clock clock;

    public ResolveLinkUseCase(ShortLinkRepository links, Clock clock) {
        this.links = Objects.requireNonNull(links, "links");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Resolves one code.
     *
     * @throws IllegalStateException if the store could not answer. Deliberately not caught: see the class
     *                               note on why a store failure must not become a 404
     */
    public Resolution resolve(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            return Resolution.notFound();
        }

        Optional<ShortLink> found = links.findByShortCode(shortCode);
        if (found.isEmpty()) {
            return Resolution.notFound();
        }

        // Read AFTER the lookup. EC-009: a link that expired while the lookup was in flight is expired.
        ShortLink link = found.get();
        if (link.isExpiredAt(clock.instant())) {
            return Resolution.expired(link);
        }
        return Resolution.redirect(link);
    }
}
