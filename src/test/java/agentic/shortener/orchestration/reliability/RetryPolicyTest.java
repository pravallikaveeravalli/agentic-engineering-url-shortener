package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.fakes.ScriptedExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T085 — bounded retry with backoff. FR-ORC-014. PVT-007.
 *
 * <p>Driven entirely by {@link ScriptedExecutor}, which is what makes these assertions mean anything: a
 * "fail twice then succeed" test against a real provider would prove the provider failed twice that
 * afternoon, not that the bound holds.
 */
@DisplayName("T085 — three attempts, exponential from one second, and never more")
class RetryPolicyTest {

    /** S2, whose declared set contains UNAVAILABLE — so the scripted failures are genuinely retryable. */
    private static final int AI_STAGE = 2;

    private static StageInput input() {
        return new StageInput(UUID.fromString("00000000-0000-0000-0000-0000000000bb"), "S2", 2, 1,
                Map.of("requirement.intake", "shorten urls"));
    }

    /** Records what the policy would have waited, instead of waiting. */
    private static final class RecordingBackoff implements Backoff {
        private final List<Duration> waited = new ArrayList<>();

        @Override
        public void await(Duration delay) {
            waited.add(delay);
        }

        List<Duration> waited() {
            return List.copyOf(waited);
        }
    }

    @Test
    @DisplayName("fail twice then succeed: two recorded failures, then success")
    void failTwiceThenSucceed() {
        RecordingBackoff backoff = new RecordingBackoff();
        ScriptedExecutor executor = ScriptedExecutor.failsThenSucceeds(2);

        RetryOutcome outcome = new RetryPolicy(backoff).execute(AI_STAGE, executor, input());

        assertTrue(outcome.succeeded());
        assertEquals(3, outcome.attempts().size(), "each attempt is recorded individually");
        assertFalse(outcome.attempts().get(0).outcome().succeeded());
        assertFalse(outcome.attempts().get(1).outcome().succeeded());
        assertTrue(outcome.attempts().get(2).outcome().succeeded());

        assertEquals(List.of(1, 2, 3),
                outcome.attempts().stream().map(Attempt::number).toList(),
                "numbered 1-based and in order, or an evidence reader cannot tell a first failure from a "
                        + "third");

        // Two waits, not three: the wait belongs BEFORE a retry, and there is no retry after the last
        // attempt. A third wait would be a second of delay charged to a run that had already finished.
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), backoff.waited());
    }

    @Test
    @DisplayName("PVT-007: exponential from one second — 1 s, 2 s, and the bound stops it there")
    void backoffIsExponentialFromOneSecond() {
        RecordingBackoff backoff = new RecordingBackoff();
        new RetryPolicy(backoff).execute(AI_STAGE, ScriptedExecutor.failsThenSucceeds(99), input());

        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), backoff.waited(),
                "three attempts means two gaps. A 4 s wait here would mean a fourth attempt was coming");
        assertEquals(3, RetryPolicy.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("bound exhaustion is a FAILURE, never a success")
    void exhaustionFails() {
        ScriptedExecutor executor = ScriptedExecutor.failsThenSucceeds(99);
        RetryOutcome outcome = new RetryPolicy(new RecordingBackoff()).execute(AI_STAGE, executor, input());

        assertFalse(outcome.succeeded(),
                "a bound that yields a success on exhaustion is not a bound; the run must stop and be "
                        + "seen to stop");
        assertEquals(3, executor.invocations(), "exactly the bound — not two, not four");
        assertEquals(3, outcome.attempts().size());
        assertTrue(outcome.exhausted());
        assertEquals(FailureCategory.UNAVAILABLE, outcome.finalFailure().category(),
                "the last failure's classification survives, so suspension records why rather than "
                        + "recording that the bound ran out");
    }

    @Test
    @DisplayName("a PERMANENT failure stops immediately — the bound is a ceiling, not a quota")
    void permanentFailureStopsAtOnce() {
        // The ruling, not the counter, decides whether there is a next attempt. Spending three attempts on
        // a failure the two-vote rule already called permanent would be three times the damage for a
        // guaranteed identical answer.
        RecordingBackoff backoff = new RecordingBackoff();
        ScriptedExecutor executor =
                ScriptedExecutor.proposesRetryForUndeclaredCategory(FailureCategory.INVALID_INPUT);

        RetryOutcome outcome = new RetryPolicy(backoff).execute(AI_STAGE, executor, input());

        assertFalse(outcome.succeeded());
        assertEquals(1, executor.invocations(), "INVALID_INPUT is in no declared set: one attempt only");
        assertFalse(outcome.exhausted(), "it did not exhaust the bound; it was ruled permanent");
        assertEquals(List.of(), backoff.waited(), "and nothing waited for a retry that was never coming");
    }

    @Test
    @DisplayName("an executor that THROWS is ruled permanent, not retried and not propagated")
    void thrownFailureIsPermanent() {
        // EC-032. The policy is the last thing between a misbehaving executor and the run, so it converts
        // the violation into a classified permanent failure rather than letting it escape — a run that
        // cannot record why it stopped is worse than one that stopped.
        ScriptedExecutor executor = ScriptedExecutor.malformed();
        RetryOutcome outcome = new RetryPolicy(new RecordingBackoff()).execute(AI_STAGE, executor, input());

        assertFalse(outcome.succeeded());
        assertEquals(1, executor.invocations(), "an unclassifiable failure is never retried");
        assertEquals(FailureCategory.UNKNOWN, outcome.finalFailure().category());
        assertTrue(outcome.finalFailure().detail().contains("IllegalStateException"),
                "what actually escaped has to reach the record: " + outcome.finalFailure().detail());
    }

    @Test
    @DisplayName("an executor that returns NULL is ruled permanent too")
    void nullOutcomeIsPermanent() {
        ScriptedExecutor executor = ScriptedExecutor.returnsNull();
        RetryOutcome outcome = new RetryPolicy(new RecordingBackoff()).execute(AI_STAGE, executor, input());

        assertFalse(outcome.succeeded());
        assertEquals(1, executor.invocations());
        assertEquals(FailureCategory.UNKNOWN, outcome.finalFailure().category());
    }

    @Test
    @DisplayName("each attempt carries its own ruling, so evidence shows WHY there was a next one")
    void everyAttemptCarriesItsRuling() {
        RetryOutcome outcome = new RetryPolicy(new RecordingBackoff())
                .execute(AI_STAGE, ScriptedExecutor.failsThenSucceeds(2), input());

        assertTrue(outcome.attempts().get(0).ruling().retry());
        assertTrue(outcome.attempts().get(1).ruling().retry());
        assertEquals(null, outcome.attempts().get(2).ruling(),
                "a successful attempt was never ruled on; recording a ruling for it would put a retry "
                        + "decision in evidence that nobody made");
    }

    @Test
    @DisplayName("the attempt number reaches the executor, so a stage can see which try this is")
    void attemptNumberIsPropagated() {
        ScriptedExecutor executor = ScriptedExecutor.failsThenSucceeds(2);
        new RetryPolicy(new RecordingBackoff()).execute(AI_STAGE, executor, input());

        assertEquals(List.of(1, 2, 3), executor.seen().stream().map(StageInput::attempt).toList(),
                "an executor handed attempt=1 three times cannot record which attempt produced what");
    }

    @Test
    @DisplayName("a stage whose declared set is empty never retries at all")
    void emptyDeclaredSetNeverRetries() {
        ScriptedExecutor executor = ScriptedExecutor.timesOut();
        StageInput s4 = new StageInput(UUID.randomUUID(), "S4", 4, 1, Map.of("x", "y"));

        RetryOutcome outcome = new RetryPolicy(new RecordingBackoff()).execute(4, executor, s4);

        assertFalse(outcome.succeeded());
        assertEquals(1, executor.invocations(), "S4 declares {} — default-deny, one attempt");
    }
}
