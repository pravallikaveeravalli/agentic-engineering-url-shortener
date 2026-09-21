package agentic.shortener.application;

import agentic.shortener.domain.analytics.RedirectEventRepository;
import agentic.shortener.domain.idempotency.IdempotencyRecord;
import agentic.shortener.domain.idempotency.IdempotencyRepository;
import agentic.shortener.domain.idempotency.MarkerAlreadyUsedException;
import agentic.shortener.domain.link.ExpiryPolicy;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import agentic.shortener.domain.validation.AbuseGuard;
import agentic.shortener.domain.validation.CredentialRedactor;
import agentic.shortener.domain.validation.DestinationNormalizer;
import agentic.shortener.domain.validation.SchemeAllowList;
import agentic.shortener.domain.validation.UrlSyntaxValidator;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates creation. Tasks T032, T041, T042.
 *
 * <p>The application-plane entry point. Holds no HTTP concern and no SQL: the controller translates to
 * and from HTTP, the repositories translate to and from the store, and this decides the order of
 * operations between {@link IdempotencyResolver} and {@link CreateLinkUseCase}.
 *
 * <p><strong>The retry logic is not duplicated here.</strong> It lives in {@link CreateLinkUseCase},
 * once. An earlier version of this class had its own copy, which is exactly how two implementations of
 * one rule start to drift.
 *
 * <p>The clock is injected because FR-URL-009's expiry rules and EC-036's idle-retention tests both need
 * to control time, and a test that sleeps is slow and flaky at once.
 */
public final class LinkService {

    /**
     * What happened, in CL-008's terms. The controller maps this to 201, 200 or 409 and nothing else.
     *
     * @param link   the link to serve — the new one for a mint, the original for a replay, absent for a
     *               conflict
     * @param reason names the rule for a conflict; never the input
     */
    public record CreationOutcome(IdempotencyResolver.Kind kind, Optional<ShortLink> link,
                                  String reason) {
    }

    private final ShortLinkRepository links;
    private final RedirectEventRepository events;
    private final IdempotencyRepository markers;
    private final CreateLinkUseCase createLink;
    private final IdempotencyResolver idempotency;
    private final DestinationNormalizer normalizer;
    private final UrlSyntaxValidator syntax;
    private final AbuseGuard abuse;
    private final ExpiryPolicy expiry;
    private final Clock clock;

    public LinkService(ShortLinkRepository links,
                       RedirectEventRepository events, IdempotencyRepository markers,
                       CreateLinkUseCase createLink, IdempotencyResolver idempotency,
                       DestinationNormalizer normalizer, UrlSyntaxValidator syntax,
                       AbuseGuard abuse, ExpiryPolicy expiry, Clock clock) {
        this.links = Objects.requireNonNull(links, "links");
        this.events = Objects.requireNonNull(events, "events");
        this.markers = Objects.requireNonNull(markers, "markers");
        this.createLink = Objects.requireNonNull(createLink, "createLink");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.syntax = Objects.requireNonNull(syntax, "syntax");
        this.abuse = Objects.requireNonNull(abuse, "abuse");
        this.expiry = Objects.requireNonNull(expiry, "expiry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The destination pipeline, in the order the requirements fix. Task T034's Artifact says
     * normalization is applied <strong>before validation and storage</strong>, and this is the
     * composition point where that ordering exists to be asserted — the ordering half T034 could only
     * state, {@code CreateLinkConformanceIT} now proves by submitting a destination with surrounding
     * whitespace, which succeeds only if normalization runs first.
     *
     * <p>Order, and why this order:
     * <ol>
     *   <li><strong>Normalize.</strong> Everything after it judges one canonical form, so two spellings
     *       of the same URL cannot be refused differently, and an idempotency fingerprint over the
     *       normalized destination makes a replay a replay.
     *   <li><strong>Scheme allow-list</strong> (FR-URL-004, non-waivable). First among the checks so the
     *       most specific and most important refusal is the one the caller is told about.
     *   <li><strong>Syntax and length</strong> (FR-URL-002, EC-006).
     *   <li><strong>Credentials</strong> (EC-007, FR-URL-017, non-waivable).
     *   <li><strong>Abuse controls</strong> (FR-URL-005) — last, because they need a host and the steps
     *       above are what establish there is one.
     * </ol>
     *
     * <p>Steps 2 and 4 are <em>also</em> enforced inside {@link ShortLink#create}, and that repetition is
     * deliberate rather than an oversight. The type holds them as invariants so no caller can forget
     * (T021's Done condition); the pipeline runs them here so the caller gets the most useful message.
     * Both call the same single definition, so there is nothing to drift.
     *
     * @return the normalized destination
     * @throws IllegalArgumentException with a message that names the rule and never the input
     */
    private String vet(String destination) {
        String normalized = normalizer.normalize(destination);

        SchemeAllowList.requirePermitted(normalized);

        UrlSyntaxValidator.Result result = syntax.validate(normalized);
        if (!result.valid()) {
            throw new IllegalArgumentException(result.reason());
        }

        CredentialRedactor.requireNoCredentials(normalized);
        abuse.requireNotAbusive(normalized);
        return normalized;
    }

    /**
     * Creates a link, honouring CL-008's three marker behaviours.
     *
     * <p><strong>Resolution comes first and writes nothing</strong>, so a replay and a conflict both cost
     * a read and no more. Only a mint proceeds to insert.
     *
     * <p><strong>The marker is recorded after the link exists</strong>, because the record must carry the
     * code and the code is not known until the insert succeeds. That leaves a window in which a
     * concurrent request with the same marker can claim it first, and the store — not this code — is the
     * authority on who won. The loser compensates: the link it just minted is expired, using the action
     * the Compensation Register defines for a link that should not have been created, and the request is
     * re-resolved so the caller gets the replay or the conflict that is actually true.
     *
     * <p><strong>The residual, stated rather than implied</strong>: the compensated link still exists, as
     * {@code EXPIRED} rather than absent, and its code is spent. Removing the window entirely needs both
     * inserts in one transaction, which the per-call-connection repository design does not currently
     * offer. It is bounded, it is never a false replay, and it is recorded rather than quietly accepted.
     *
     * @throws IllegalArgumentException if the destination or expiry breaks a domain rule
     */
    public CreationOutcome create(UUID creatorId, String marker, String destination,
                                  Instant expiresAt) {
        Objects.requireNonNull(creatorId, "creatorId");
        // PVT-011's default and EC-014's refusal, both owned by ExpiryPolicy (T046).
        Instant resolvedExpiry = this.expiry.resolve(expiresAt);

        // Normalization and every refusal happen BEFORE the marker is consulted. A request that is
        // going to be refused must be refused whether or not it carries a marker, and the fingerprint
        // must be taken over the canonical destination or two spellings of one URL would conflict.
        String vetted = vet(destination);

        IdempotencyResolver.Resolution resolution =
                idempotency.resolve(creatorId, marker, vetted, resolvedExpiry);
        if (resolution.kind() != IdempotencyResolver.Kind.MINT) {
            return new CreationOutcome(resolution.kind(), resolution.replayed(), resolution.reason());
        }

        ShortLink minted = createLink.create(creatorId, vetted, resolvedExpiry);
        if (marker == null || marker.isBlank()) {
            return new CreationOutcome(IdempotencyResolver.Kind.MINT, Optional.of(minted), "");
        }

        try {
            markers.save(IdempotencyRecord.of(creatorId, marker, vetted, resolvedExpiry,
                    minted.shortCode(), clock.instant()));
            return new CreationOutcome(IdempotencyResolver.Kind.MINT, Optional.of(minted), "");
        } catch (MarkerAlreadyUsedException raced) {
            links.expire(minted);
            IdempotencyResolver.Resolution settled =
                    idempotency.resolve(creatorId, marker, vetted, resolvedExpiry);
            return new CreationOutcome(settled.kind(), settled.replayed(), settled.reason());
        }
    }

    /** The unmarked path. CL-008 case 1: always mint. */
    public ShortLink create(UUID creatorId, String destination, Instant expiresAt) {
        return create(creatorId, null, destination, expiresAt).link().orElseThrow();
    }

    public Optional<ShortLink> find(String shortCode) {
        return links.findByShortCode(shortCode);
    }

    public long redirectCount(String shortCode) {
        return events.countByShortCode(shortCode);
    }

}
