package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded retry with exponential backoff. Task T085. FR-ORC-014, PVT-007.
 *
 * <p><strong>Three attempts, gaps of 1 s and 2 s.</strong> Three attempts means two gaps; a third wait would
 * be delay charged to a run that had already finished.
 *
 * <h2>The bound is a ceiling, not a quota</h2>
 *
 * <p>{@link RetryRuling} decides whether there is a next attempt; the counter only decides when to stop
 * trying. A failure ruled permanent stops at once, because spending three attempts on an answer the two-vote
 * rule has already settled is three times the damage for a guaranteed identical result. That distinction is
 * visible in {@link RetryOutcome#exhausted()}, which is true only when the bound actually ran out — a run that
 * stopped after one permanent failure did not fight hard, and evidence should not suggest it did.
 *
 * <h2>This is the last thing between a misbehaving executor and the run</h2>
 *
 * <p>An executor that throws, or returns null, has broken {@code StageExecutorContract}. Letting that escape
 * would leave a run that cannot record why it stopped, which is worse than a run that stopped — so it becomes
 * a classified {@code UNKNOWN} failure, permanent under EC-032, with what actually escaped in the detail.
 *
 * <p>There is deliberately <strong>no run-level circuit breaker</strong>: it is a recorded backlog item, and
 * the in-scope controls are these per-stage bounds plus run-level blocking. A breaker added quietly here would
 * be an unrecorded change to the reliability design.
 */
public final class RetryPolicy {

    /** PVT-007. */
    public static final int MAX_ATTEMPTS = 3;

    /** PVT-007: exponential from one second. */
    private static final Duration FIRST_GAP = Duration.ofSeconds(1);

    private final Backoff backoff;
    private final RetryRuling ruling = new RetryRuling();

    public RetryPolicy(Backoff backoff) {
        this.backoff = Objects.requireNonNull(backoff, "backoff");
    }

    /**
     * Runs the stage until it succeeds, is ruled permanent, or exhausts the bound.
     *
     * @param input the first attempt's input; each retry gets a copy with the attempt number advanced, so an
     *              executor can see which try this is and record it
     */
    public RetryOutcome execute(int stageNumber, StageExecutor executor, StageInput input) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(input, "input");

        List<Attempt> attempts = new ArrayList<>();
        FailureEnvelope lastFailure = null;

        for (int number = 1; number <= MAX_ATTEMPTS; number++) {
            StageOutcome outcome = attempt(executor, withAttemptNumber(input, number));

            if (outcome != null && outcome.succeeded()) {
                attempts.add(new Attempt(number, outcome, null));
                return new RetryOutcome(true, false, null, attempts);
            }

            FailureEnvelope envelope = outcome == null ? null : outcome.failure();
            Ruling verdict = ruling.rule(stageNumber, envelope);
            attempts.add(new Attempt(number, outcome, verdict));
            lastFailure = envelope != null ? envelope : unclassified(verdict);

            if (!verdict.retry()) {
                // Ruled permanent. Not exhausted — nothing ran out, the answer was settled.
                return new RetryOutcome(false, false, lastFailure, attempts);
            }
            if (number < MAX_ATTEMPTS) {
                backoff.await(gapBefore(number + 1));
            }
        }
        return new RetryOutcome(false, true, lastFailure, attempts);
    }

    /**
     * Invokes the executor, converting a contract violation into a classified failure.
     *
     * @return the outcome, or {@code null} when the executor gave none. {@link RetryRuling} rules on a null
     *         envelope as permanent, so the two cases converge without either being special-cased twice
     */
    private static StageOutcome attempt(StageExecutor executor, StageInput input) {
        try {
            return executor.execute(input);
        } catch (RuntimeException e) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.UNKNOWN,
                    "the executor threw instead of returning a classified failure: "
                            + e.getClass().getSimpleName() + ": " + messageOf(e),
                    false));
        }
    }

    /** What to record when the executor returned nothing at all. */
    private static FailureEnvelope unclassified(Ruling verdict) {
        return new FailureEnvelope(FailureCategory.UNKNOWN, verdict.reason(), false);
    }

    /** 1 s before attempt 2, 2 s before attempt 3. Doubling, from one second. */
    private static Duration gapBefore(int attemptNumber) {
        return FIRST_GAP.multipliedBy(1L << (attemptNumber - 2));
    }

    private static StageInput withAttemptNumber(StageInput input, int number) {
        return number == input.attempt()
                ? input
                : new StageInput(input.runId(), input.nodeKey(), input.stageNumber(), number,
                        input.inputArtifacts());
    }

    private static String messageOf(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "no message" : message;
    }
}
