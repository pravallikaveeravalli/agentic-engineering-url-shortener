package agentic.shortener.arch.fixture;

import agentic.shortener.domain.analytics.RedirectEvent;
import agentic.shortener.domain.analytics.RedirectEventRepository;

import java.time.Instant;

/**
 * A deliberate bypass of the analytics recording port. Fixture for T049.
 *
 * <p><strong>This class violates FR-URL-010 on purpose.</strong> It holds the event store directly and
 * appends to it without going through {@code AnalyticsRecordingPort}, which is exactly the shape ADR-014
 * Condition 2 forbids — and exactly what a redirect handler would be tempted to write, because the
 * repository is right there and one call looks cheaper than an interface.
 *
 * <p>It lives in test sources and nothing wires it, so it can never run. Its only job is to be caught:
 * {@code AnalyticsPortBypassTest} aims T049's rule at this package and asserts the rule fails. A rule
 * that has never failed is an assertion that happens to be green.
 *
 * <p>The same reasoning as {@link FrameworkDependentDomainFixture}, which stands in for T014's rules.
 */
public final class AnalyticsPortBypassFixture {

    private final RedirectEventRepository events;

    public AnalyticsPortBypassFixture(RedirectEventRepository events) {
        this.events = events;
    }

    /** The bypass. Appends straight to the store, with no port anywhere in sight. */
    public void recordWithoutThePort(String shortCode, Instant occurredAt) {
        events.append(RedirectEvent.unsaved(shortCode, occurredAt));
    }
}
