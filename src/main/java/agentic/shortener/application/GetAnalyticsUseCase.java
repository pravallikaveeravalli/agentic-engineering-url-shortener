package agentic.shortener.application;

import agentic.shortener.domain.analytics.RedirectEvent;
import agentic.shortener.domain.analytics.RedirectEventRepository;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Creator-scoped analytics retrieval. Task T051. FR-URL-011.
 *
 * <p><strong>There is no ownership oracle.</strong> FR-URL-011's negative clause forbids the refusal from
 * disclosing whether the code exists, and that is a stronger requirement than "do not return another
 * creator's data". If a non-owner got a different answer from a caller asking about a code that was never
 * issued, anyone could enumerate the keyspace and learn which codes are live — and, by asking as
 * themselves, which ones are not theirs.
 *
 * <p>So the two refusals are not merely similar: they are the <strong>same value</strong>.
 * {@link View#REFUSED} is a single constant returned by both paths, which makes byte-identity structural
 * rather than something two code paths have to keep agreeing about.
 *
 * <p><strong>Authentication is the caller's problem, not this class's.</strong> The caller identity
 * arrives as a parameter; deciding who the caller <em>is</em> belongs to T052's filter. A {@code null}
 * caller — an anonymous one — gets the same refusal as a non-owner, so this class is already correct
 * before the filter exists, and the HTTP-level 401 is asserted where the filter is.
 *
 * <p>Events come back in time order (FR-URL-011) and carry <strong>timestamps only</strong>: there is no
 * follower-identifying field to expose, because {@link RedirectEvent} has none to hold (CL-002).
 */
public final class GetAnalyticsUseCase {

    /**
     * What a caller is told.
     *
     * <p>{@link #REFUSED} is deliberately a shared constant. Two separately-constructed "empty" results
     * would be equal today and could drift apart tomorrow — a field added to one branch, a message
     * phrased differently — and the drift would be an ownership oracle that no test was watching for.
     */
    public record View(boolean permitted, String shortCode, long totalRedirects,
                       List<Instant> events) {

        /** The single refusal. Identical for a non-owner and for a code that does not exist. */
        public static final View REFUSED = new View(false, "", 0L, List.of());

        static View of(ShortLink link, List<Instant> events) {
            return new View(true, link.shortCode(), events.size(), List.copyOf(events));
        }
    }

    private final ShortLinkRepository links;
    private final RedirectEventRepository events;

    public GetAnalyticsUseCase(ShortLinkRepository links, RedirectEventRepository events) {
        this.links = Objects.requireNonNull(links, "links");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * Retrieves one link's redirect events.
     *
     * @param callerId the authenticated creator, or {@code null} for an anonymous caller
     * @return the events in time order, or {@link View#REFUSED} — the same value for a non-owner and for
     *         a code that was never issued
     */
    public View get(UUID callerId, String shortCode) {
        if (callerId == null || shortCode == null || shortCode.isBlank()) {
            return View.REFUSED;
        }

        Optional<ShortLink> found = links.findByShortCode(shortCode);
        if (found.isEmpty() || !found.get().creatorId().equals(callerId)) {
            // One branch, one answer. Separating these into two returns is how an oracle appears: the
            // second one acquires a detail, and the detail is the disclosure.
            return View.REFUSED;
        }

        // The UNBOUNDED read, deliberately. The first version windowed this at now(), and an event
        // timestamped even slightly ahead of that — clock skew between two instances is enough —
        // disappeared from the owner's analytics without a trace. CR-017 retains events indefinitely, so
        // "all of them" is the honest query and there is no clock in this class at all.
        List<RedirectEvent> recorded = events.findByShortCode(shortCode);
        List<Instant> timestamps = recorded.stream()
                .map(RedirectEvent::occurredAt)
                .sorted()
                .toList();
        return View.of(found.get(), timestamps);
    }
}
