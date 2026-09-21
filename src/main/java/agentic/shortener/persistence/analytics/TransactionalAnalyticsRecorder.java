package agentic.shortener.persistence.analytics;

import agentic.shortener.domain.analytics.AnalyticsRecordingPort;
import agentic.shortener.domain.analytics.RedirectEvent;
import agentic.shortener.domain.analytics.RedirectEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The only implementation of the analytics recording port. Task T050. FR-URL-010. ADR-014. EC-012.
 *
 * <p><strong>Its own transaction, and that is a correction rather than a preference.</strong> The plan's
 * original provisional position was a same-transaction append; ADR-014 prohibits it. Sharing the
 * redirect's transaction means an analytics failure rolls the redirect back, which is precisely the
 * failure EC-012 forbids: a resolvable link would stop resolving because a counter could not be written.
 * Each append here is its own unit of work, committed or lost on its own.
 *
 * <p><strong>Failure is isolated, counted and logged — never silent, and never propagated.</strong>
 * {@link #record} catches everything, including {@link Error}s it cannot sensibly handle, because the one
 * thing it must never do is throw into the redirect path. PVT-009 as ADR-014 redefined it is a ceiling on
 * <em>counted, visible</em> failures with <strong>zero silent ones</strong>: 0% was rejected precisely
 * because it cannot survive its own fault-injection test — when the store is deliberately killed, appends
 * must fail and be counted, and that is the design working.
 *
 * <p><strong>Durability on acknowledgement.</strong> {@link #accepted()} is incremented only after the
 * append returns, so the counter never claims an event the store did not take. A counter incremented
 * first would over-report exactly when the store was in trouble — the moment the number matters most.
 *
 * <p><strong>Timestamp only</strong> (FR-URL-010, CL-002). Nothing here has access to an IP address, a
 * user agent or a referrer, because the port's signature offers no way to pass one. The data
 * minimization is structural, not a matter of remembering.
 */
public final class TransactionalAnalyticsRecorder implements AnalyticsRecordingPort {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionalAnalyticsRecorder.class);

    private final RedirectEventRepository events;
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public TransactionalAnalyticsRecorder(RedirectEventRepository events) {
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public void record(String shortCode, Instant occurredAt) {
        try {
            // One append, one connection, one commit. The repository takes a connection per call, so
            // this is a transaction of its own by construction rather than by a framework annotation
            // that a later refactor could widen.
            events.append(RedirectEvent.unsaved(shortCode, occurredAt));
            accepted.incrementAndGet();
        } catch (RuntimeException e) {
            countAndLog(shortCode, e);
        } catch (Throwable fatal) {
            // Deliberately broad. A redirect that a resolvable link earned must not be taken down by
            // anything on this path, and "anything" includes the failures a catch of RuntimeException
            // would let through.
            countAndLog(shortCode, fatal);
        }
    }

    private void countAndLog(String shortCode, Throwable cause) {
        long total = failed.incrementAndGet();
        // Counted AND logged. A counted-but-unlogged failure gives an operator a number with no way to
        // find out what happened; a logged-but-uncounted one cannot be measured against PVT-009.
        LOG.warn("analytics append failed for short code {} (failure {} on this instance); the "
                        + "redirect itself was unaffected (EC-012, PVT-009)",
                shortCode, total, cause);
    }

    @Override
    public long accepted() {
        return accepted.get();
    }

    @Override
    public long failed() {
        return failed.get();
    }
}
