package agentic.shortener.domain.analytics;

import java.time.Instant;

/**
 * The analytics recording port. Task T048. FR-URL-010. ADR-014 <strong>Condition 2</strong>.
 *
 * <p><strong>Every append passes through here.</strong> Condition 2 is explicit that the port must not
 * be bypassed <em>anywhere</em> — not for convenience, not for a hot path, and not in a test that then
 * fails to exercise it. {@code AnalyticsPortBypassTest} (T049) is the mechanism that keeps that true;
 * without it the port is advisory, and an advisory port is a comment.
 *
 * <p><strong>Why it earns its existence.</strong> ADR-014 records an evolution ladder — the Postgres
 * table today, a queue or a stream later — and every rung of it is reachable only behind this seam. A
 * single direct store call from the resolution path would pin the design to the first rung, because
 * moving would then mean finding every caller rather than writing one more implementation.
 *
 * <p><strong>Recording never throws.</strong> EC-012 requires a resolvable redirect to succeed while
 * analytics recording fails, so {@link #record} swallowing its own failures is the contract rather than
 * sloppiness. What it must not do is swallow them <em>silently</em>: PVT-009 as ADR-014 redefined it is a
 * ceiling on <em>counted, visible</em> failures with <strong>zero silent ones</strong>, which is why
 * {@link #failed()} exists and is part of the interface rather than an implementation detail.
 *
 * <p><strong>Timestamp only</strong> (FR-URL-010, CL-002). No IP address, no user agent, no referrer, no
 * device identifier, no geolocation. That is a data-minimization decision, not an omission — and the
 * signature is where it is enforced: there is no parameter through which follower-identifying data
 * could arrive.
 *
 * <p>Owned by the domain. No framework type appears here (NFR-MNT-002).
 */
public interface AnalyticsRecordingPort {

    /**
     * Records one redirect. <strong>Never throws</strong>, for any reason, including an unreachable
     * store — see the class note on EC-012.
     *
     * @param shortCode  the code that was followed
     * @param occurredAt when it was followed
     */
    void record(String shortCode, Instant occurredAt);

    /** Appends this instance has durably accepted. */
    long accepted();

    /**
     * Appends this instance has failed. PVT-009's measurement point, per ADR-014 Condition 2: a
     * failure that is counted and visible is the design working; a failure that is silent is not.
     */
    long failed();
}
