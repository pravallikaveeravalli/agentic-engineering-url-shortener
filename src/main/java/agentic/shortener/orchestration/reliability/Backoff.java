package agentic.shortener.orchestration.reliability;

import java.time.Duration;

/**
 * Waits between attempts. Task T085's seam.
 *
 * <p>Injected rather than a {@code Thread.sleep} inside {@link RetryPolicy}, because otherwise every retry
 * test pays PVT-007's real backoff — one second, then two — and a suite that takes three seconds per retry
 * case is a suite people stop running. A test passes a recorder, asserts the schedule and waits for nothing.
 *
 * <p>That is not a test-only convenience: making the wait a collaborator is what lets the schedule be
 * <em>asserted</em> rather than observed, and "the second gap was 2 s" is not something a stopwatch in a test
 * can claim reliably on a loaded machine.
 */
@FunctionalInterface
public interface Backoff {

    void await(Duration delay);

    /** The production wait. Interruption is honoured rather than swallowed. */
    static Backoff sleeping() {
        return delay -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                // Restore the flag and stop waiting. Swallowing this would make a shutdown request take
                // however long the remaining backoff had to run.
                Thread.currentThread().interrupt();
            }
        };
    }
}
