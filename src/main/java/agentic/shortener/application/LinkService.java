package agentic.shortener.application;

import agentic.shortener.domain.analytics.RedirectEventRepository;
import agentic.shortener.domain.creator.Creator;
import agentic.shortener.domain.creator.CreatorRepository;
import agentic.shortener.domain.link.ShortCodeCollisionException;
import agentic.shortener.domain.shortcode.ShortCodeGenerator;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Link creation and lookup. Task T032.
 *
 * <p>The application-plane use case. Holds no HTTP concern and no SQL: the controller translates to and
 * from HTTP, the repositories translate to and from the store, and this decides what happens.
 *
 * <p><strong>Collision handling is ADR-007's bounded retry, and the bound is the point.</strong> A
 * collision is a normal outcome under contention (FR-URL-013), not a fault — but an unbounded retry loop
 * would turn a systematic problem, such as a generator returning a constant, into a hang rather than an
 * error. So the attempts are counted and exhaustion is an explicit failure.
 *
 * <p>The clock is injected because FR-URL-009's expiry rules and EC-036's idle-retention tests both need
 * to control time, and a test that sleeps is a test that is slow and flaky at once.
 */
public final class LinkService {

    /**
     * ADR-007's bound. Five attempts against 2^42 of code space: if five CSPRNG draws all collide, the
     * cause is not bad luck — it is a broken generator or an exhausted keyspace, and either deserves a
     * reported failure rather than a sixth attempt.
     */
    private static final int MAX_CODE_ATTEMPTS = 5;

    /** PVT-011's default TTL, applied when the caller supplies no expiry (FR-URL-009). */
    private static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final ShortLinkRepository links;
    private final CreatorRepository creators;
    private final RedirectEventRepository events;
    private final ShortCodeGenerator codes;
    private final Clock clock;

    public LinkService(ShortLinkRepository links, CreatorRepository creators,
                       RedirectEventRepository events, ShortCodeGenerator codes, Clock clock) {
        this.links = Objects.requireNonNull(links, "links");
        this.creators = Objects.requireNonNull(creators, "creators");
        this.events = Objects.requireNonNull(events, "events");
        this.codes = Objects.requireNonNull(codes, "codes");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates a link, minting a code and retrying a bounded number of times on collision.
     *
     * @throws IllegalArgumentException if the destination or expiry breaks a domain rule — the domain
     *                                  type raises it, and this method does not second-guess the rule
     */
    public ShortLink create(UUID creatorId, String destination, Instant expiresAt) {
        Objects.requireNonNull(creatorId, "creatorId");
        Instant now = clock.instant();
        Instant expiry = expiresAt != null ? expiresAt : now.plus(DEFAULT_TTL);

        ShortCodeCollisionException lastCollision = null;
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            // The domain type validates before any store call, so an invalid destination never reaches
            // PostgreSQL and never consumes an attempt.
            ShortLink candidate = ShortLink.create(codes.next(), destination, creatorId, now, expiry);
            try {
                return links.save(candidate);
            } catch (ShortCodeCollisionException collision) {
                lastCollision = collision;
            }
        }
        throw new IllegalStateException(
                "could not mint an unused short code in " + MAX_CODE_ATTEMPTS + " attempts. Against "
                        + "2^42 of code space this is not bad luck: suspect the generator or an "
                        + "exhausted keyspace (ADR-007)", lastCollision);
    }

    public Optional<ShortLink> find(String shortCode) {
        return links.findByShortCode(shortCode);
    }

    public long redirectCount(String shortCode) {
        return events.countByShortCode(shortCode);
    }

    /**
     * Ensures a creator exists, for the walking skeleton's unauthenticated path.
     *
     * <p><strong>A deliberate scaffold, and named as one.</strong> FR-URL-018 requires link creation to
     * be authenticated; the walking skeleton is T032 and authentication is a later task. Until then a
     * single well-known demonstration creator owns skeleton-created links, because KE-01 has no state
     * for a link without an owner and the foreign key would refuse one. It is not a security decision
     * and must not survive into the authenticated path.
     */
    public UUID demonstrationCreator() {
        // All-hex, because a UUID literal must be. An earlier version used
        // "...00000000dem0", which is not parseable — caught by WalkingSkeletonIT rather
        // than by review.
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000d0");
        if (creators.findById(id).isEmpty()) {
            creators.save(Creator.create(id, "walking-skeleton demonstration creator", clock.instant()));
        }
        return id;
    }
}
