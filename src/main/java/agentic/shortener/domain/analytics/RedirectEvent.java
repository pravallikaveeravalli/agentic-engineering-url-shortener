package agentic.shortener.domain.analytics;

import java.time.Instant;
import java.util.Objects;

/**
 * One redirect, recorded as a timestamp and nothing more. Task T024.
 * FR-URL-010, NFR-SEC-005, POL-PRIV-001, CL-002. ADR-014.
 *
 * <p><strong>The absence is the requirement.</strong> Three fields: {@code id}, {@code shortCode},
 * {@code occurredAt}. No IP address, no user agent, no referrer, no device identifier, no
 * geolocation, no session or visitor identifier of any kind.
 *
 * <p>CL-002 decided this on data-minimisation grounds, and the consequence reaches further than
 * privacy: <strong>it is also what makes indefinite retention safe</strong> (NFR-AUD-003, CR-017).
 * This demonstration keeps every click record for ever, and that is defensible only because there is
 * no personal data here to keep. Adding a field to this type would silently invalidate the retention
 * posture as well as the privacy claim, which is why {@code RedirectEventTest} enforces an
 * <em>allow-list</em> of permitted fields rather than a deny-list of forbidden ones — a deny-list
 * would wave through a plausible-sounding {@code clientFingerprint}.
 *
 * <p>ADR-014 governs how the append fails: synchronously, in a separate transaction, with the failure
 * isolated so a failed analytics write never fails the redirect (EC-012).
 */
public final class RedirectEvent {

    private final long id;
    private final String shortCode;
    private final Instant occurredAt;

    private RedirectEvent(long id, String shortCode, Instant occurredAt) {
        this.id = id;
        this.shortCode = shortCode;
        this.occurredAt = occurredAt;
    }

    public static RedirectEvent of(long id, String shortCode, Instant occurredAt) {
        Objects.requireNonNull(shortCode, "shortCode");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (shortCode.isBlank()) {
            throw new IllegalArgumentException("a blank code cannot identify a link");
        }
        return new RedirectEvent(id, shortCode, occurredAt);
    }

    /** For an event not yet assigned a store-generated identifier. */
    public static RedirectEvent unsaved(String shortCode, Instant occurredAt) {
        return of(0L, shortCode, occurredAt);
    }

    public long id() {
        return id;
    }

    public String shortCode() {
        return shortCode;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public String toString() {
        return "RedirectEvent[" + shortCode + " at " + occurredAt + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RedirectEvent that
                && id == that.id
                && shortCode.equals(that.shortCode)
                && occurredAt.equals(that.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, shortCode, occurredAt);
    }
}
