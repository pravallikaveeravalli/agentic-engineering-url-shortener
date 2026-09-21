package agentic.shortener.application;

import agentic.shortener.domain.idempotency.IdempotencyRecord;
import agentic.shortener.domain.idempotency.IdempotencyRepository;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Marker-only idempotency. Task T041. FR-URL-012, semantics fixed by CL-008.
 *
 * <p>CL-008 defines <strong>exactly three</strong> behaviours and this class decides which applies. It
 * decides only — it writes nothing, because two of the three outcomes must leave the world untouched and
 * the third has not minted anything yet.
 *
 * <ol>
 *   <li><strong>No marker → MINT.</strong> There is <strong>no destination-based deduplication at any
 *       scope, ever.</strong> Nothing here looks a destination up, and no repository offers a way to.
 *   <li><strong>Same marker, identical request → REPLAY.</strong> The original link returns as
 *       <em>success</em>, labelled, with zero new side effects. This mechanizes EC-003: a client that
 *       never saw the first response can retry safely.
 *   <li><strong>Same marker, different content → CONFLICT.</strong> Nothing minted, nothing changed.
 * </ol>
 *
 * <p><strong>Case 3 is the damaging one to get wrong</strong>, because a conflict returned as a replay
 * <em>looks like success</em> while silently discarding what the caller changed. The separation rests on
 * {@link IdempotencyRecord}'s fingerprint, which covers the destination <em>and</em> the expiry together
 * — the pairing is deliberate, and a resolver computing a digest of the destination alone would have
 * rebuilt destination-based deduplication under another name.
 *
 * <p><strong>A replay must not alter the existing link's expiry.</strong> CL-008 forbids it twice over:
 * a replay must not change state at all, and link editing is out of scope for the same reason deletion
 * is — the link is already circulating with a promised lifetime, and somebody has relied on it.
 *
 * <p><strong>Markers never span creators</strong> (KE-03). Per-creator scoping is not a tidiness
 * measure: global deduplication would break FR-URL-011's ownership model <em>and</em> leak that another
 * creator had shortened the same URL.
 */
public final class IdempotencyResolver {

    public enum Kind {
        /** No usable marker, or a marker never seen for this creator. Mint a new code. */
        MINT,

        /** Same marker, identical request content. Return the original as labelled success. */
        REPLAY,

        /** Same marker, different request content. Mint nothing, change nothing. */
        CONFLICT
    }

    /**
     * The decision.
     *
     * @param replayed the original link, present only for {@link Kind#REPLAY}
     * @param reason   empty unless there is something to tell the caller. Names the rule, never the
     *                 input — this reaches a 409 body, and FR-URL-002's negative clause applies there
     *                 as much as anywhere
     */
    public record Resolution(Kind kind, Optional<ShortLink> replayed, String reason) {

        static Resolution mint() {
            return new Resolution(Kind.MINT, Optional.empty(), "");
        }

        static Resolution replay(ShortLink original) {
            return new Resolution(Kind.REPLAY, Optional.of(original), "");
        }

        static Resolution conflict() {
            return new Resolution(Kind.CONFLICT, Optional.empty(), CONFLICT_REASON);
        }
    }

    private static final String CONFLICT_REASON =
            "This idempotency marker was already used for a different request. Nothing was created or "
                    + "changed. Use a new marker, or resend the original request unchanged "
                    + "(FR-URL-012, CL-008).";

    private final IdempotencyRepository markers;
    private final ShortLinkRepository links;

    public IdempotencyResolver(IdempotencyRepository markers, ShortLinkRepository links) {
        this.markers = Objects.requireNonNull(markers, "markers");
        this.links = Objects.requireNonNull(links, "links");
    }

    /**
     * Decides which of CL-008's three behaviours applies. Writes nothing.
     *
     * @param marker the caller's marker, or {@code null}. Blank is treated as absent: a blank marker
     *               stored as a key would collapse every unmarked request into one bucket
     */
    public Resolution resolve(UUID creatorId, String marker, String destination, Instant expiresAt) {
        Objects.requireNonNull(creatorId, "creatorId");
        if (marker == null || marker.isBlank()) {
            return Resolution.mint();                                       // case 1
        }

        // The ONLY lookup: by marker, within this creator. Never by destination, at any scope.
        Optional<IdempotencyRecord> seen = markers.find(creatorId, marker);
        if (seen.isEmpty()) {
            return Resolution.mint();                                       // case 1, unseen marker
        }

        IdempotencyRecord record = seen.get();
        if (!record.matchesRequest(IdempotencyRecord.fingerprint(destination, expiresAt))) {
            return Resolution.conflict();                                   // case 3
        }

        Optional<ShortLink> original = links.findByShortCode(record.shortCode());
        if (original.isEmpty()) {
            // The marker points at a link that is not there. Minting would be the wrong answer — the
            // marker is genuinely used — so this is reported as the conflict it is rather than guessed
            // past. KE-01 keeps links indefinitely (CR-017), so in practice this means a store problem.
            return Resolution.conflict();
        }
        return Resolution.replay(original.get());                           // case 2
    }
}
