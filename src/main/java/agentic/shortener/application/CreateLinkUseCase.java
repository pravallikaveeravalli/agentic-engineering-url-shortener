package agentic.shortener.application;

import agentic.shortener.domain.link.ShortCodeCollisionException;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import agentic.shortener.domain.shortcode.ShortCodeGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Link creation with bounded collision retry. Task T039. FR-URL-006, FR-URL-001. ADR-007.
 *
 * <p><strong>Insert, catch, regenerate, retry — and nothing else.</strong> The unique constraint is the
 * authority on whether a code is free; this method's job is to react to its answer. There is no lookup
 * before the insert, and there is no branch anywhere below in which an existing link is written to.
 *
 * <p><strong>Why check-then-insert is a defect rather than a style.</strong> Asking whether the code is
 * taken and then inserting passes every single-threaded test and fails under contention, because another
 * writer commits between the two statements. T040 runs a hundred real clients against a real PostgreSQL
 * to make that difference a result rather than an argument.
 *
 * <p><strong>The prohibited overwrite is structurally inexpressible</strong>, which is T039's guard, and
 * three separate things make it so rather than one comment:
 * <ul>
 *   <li>{@link ShortLinkRepository} declares no update, upsert or replace — there is no method to call.
 *   <li>The insert SQL carries no {@code ON CONFLICT DO UPDATE} — the constraint is an authority, not a
 *       suggestion.
 *   <li>This class never reads a link at all, so it holds nothing it could write back.
 * </ul>
 * {@code CreateLinkUseCaseTest} asserts all three, because "nobody would do that" is not a control.
 *
 * <p><strong>The bound is the point.</strong> A collision is a normal outcome under contention
 * (FR-URL-013), not a fault. But an unbounded retry would turn a <em>systematic</em> problem — a
 * generator stuck on one value, an exhausted keyspace — into a hang rather than an error. Five attempts
 * against roughly 2×10¹² codes is not bad luck; it is a diagnosis.
 */
public final class CreateLinkUseCase {

    /** ADR-007's bound. Public so the test asserts against the same number the code uses. */
    public static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortLinkRepository links;
    private final ShortCodeGenerator codes;
    private final Clock clock;

    public CreateLinkUseCase(ShortLinkRepository links, ShortCodeGenerator codes, Clock clock) {
        this.links = Objects.requireNonNull(links, "links");
        this.codes = Objects.requireNonNull(codes, "codes");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates a link, minting a fresh code on each attempt.
     *
     * @throws IllegalArgumentException if the destination or expiry breaks a domain rule. The domain
     *                                  type raises it before any store call, so a refused request
     *                                  persists nothing and consumes no code from the keyspace
     *                                  (FR-URL-002's negative clause)
     * @throws IllegalStateException    if the bound is exhausted. Failure, never reuse
     */
    public ShortLink create(UUID creatorId, String destination, Instant expiresAt) {
        Objects.requireNonNull(creatorId, "creatorId");
        // The expiry arrives already resolved. PVT-011's default and EC-014's refusal live in
        // ExpiryPolicy (T046), once — a second copy of the default here is how two answers to one
        // question start to diverge.
        Objects.requireNonNull(expiresAt, "expiresAt: resolve it through ExpiryPolicy first");
        Instant now = clock.instant();
        Instant expiry = expiresAt;

        ShortCodeCollisionException lastCollision = null;
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            // A FRESH code every attempt. Retrying the same insert would just re-lose the same race.
            ShortLink candidate = ShortLink.create(codes.next(), destination, creatorId, now, expiry);
            try {
                return links.save(candidate);
            } catch (ShortCodeCollisionException collision) {
                lastCollision = collision;
            }
        }
        throw new IllegalStateException(
                "could not mint an unused short code in " + MAX_CODE_ATTEMPTS + " attempts. Against "
                        + "roughly 2e12 codes this is not bad luck: suspect the generator or an "
                        + "exhausted keyspace (ADR-007)", lastCollision);
    }
}
