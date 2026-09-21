package agentic.shortener.orchestration.reliability;

import java.util.List;

/**
 * The result of running a stage under {@link RetryPolicy}. Task T085.
 *
 * @param exhausted     true only when the bound ran out. A failure that was ruled <em>permanent</em> did not
 *                      exhaust anything, and conflating the two would make a run look like it fought hard
 *                      when it correctly stopped after one attempt — and would hide a stage whose declared
 *                      set is too narrow behind a number that looks like effort
 * @param finalFailure  the last failure's classification, or {@code null} on success. Carried so that
 *                      suspension records <em>why</em> rather than recording that the bound ran out
 */
public record RetryOutcome(boolean succeeded, boolean exhausted, FailureEnvelope finalFailure,
                           List<Attempt> attempts) {

    public RetryOutcome {
        attempts = List.copyOf(attempts);
        if (attempts.isEmpty()) {
            throw new IllegalArgumentException("a run of a stage produced no attempts at all");
        }
        if (succeeded && finalFailure != null) {
            throw new IllegalArgumentException("a successful run has no final failure");
        }
        if (!succeeded && finalFailure == null) {
            throw new IllegalArgumentException(
                    "a failed run must carry its last classification, or suspension has nothing to record");
        }
        if (succeeded && exhausted) {
            throw new IllegalArgumentException("a run that succeeded did not exhaust its bound");
        }
    }
}
