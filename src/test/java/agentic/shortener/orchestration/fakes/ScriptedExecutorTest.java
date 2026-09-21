package agentic.shortener.orchestration.fakes;

import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.reliability.FailureCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T072 — each script produces its scripted outcome deterministically. FR-ORC-030.
 *
 * <p>The fakes are the instrument every reliability proof in T085–T096 is measured with, so the instrument is
 * calibrated first. A "fail twice then succeed" script that actually failed three times would make a retry
 * bound look broken; one that failed once would make an unbounded retry look bounded. Either way the
 * reliability suite would be reporting on the fake rather than on the orchestrator.
 */
@DisplayName("T072 — scripted executors do exactly what they were told")
class ScriptedExecutorTest {

    private static StageInput input(int attempt) {
        return new StageInput(UUID.fromString("00000000-0000-0000-0000-000000000001"), "S7.1", 7, attempt,
                Map.of("design", "the plan"));
    }

    @Test
    @DisplayName("fail twice then succeed: twice, then succeed — not once, not three times")
    void failsTwiceThenSucceeds() {
        ScriptedExecutor executor = ScriptedExecutor.failsThenSucceeds(2);

        StageOutcome first = executor.execute(input(1));
        StageOutcome second = executor.execute(input(2));
        StageOutcome third = executor.execute(input(3));

        assertFalse(first.succeeded());
        assertFalse(second.succeeded());
        assertTrue(third.succeeded(), "the third attempt is the one that works");
        assertEquals(FailureCategory.UNAVAILABLE, first.failure().category(),
                "a category every stage's declared set contains, so a retry test exercises the retry "
                        + "machinery rather than the declared-set intersection");
        assertTrue(first.failure().executorProposesRetryable());
        assertEquals(3, executor.invocations());

        // And it keeps succeeding. A script that succeeded once and then failed again would turn a passing
        // resume test into a flaky one, discovered much later and blamed on the orchestrator.
        assertTrue(executor.execute(input(4)).succeeded());
    }

    @Test
    @DisplayName("fail zero times is a real script, not a misuse")
    void failsZeroTimes() {
        assertTrue(ScriptedExecutor.failsThenSucceeds(0).execute(input(1)).succeeded());
        assertThrows(IllegalArgumentException.class, () -> ScriptedExecutor.failsThenSucceeds(-1));
    }

    @Test
    @DisplayName("two instances of the same script do not share a counter")
    void instancesAreIndependent() {
        ScriptedExecutor one = ScriptedExecutor.failsThenSucceeds(1);
        ScriptedExecutor two = ScriptedExecutor.failsThenSucceeds(1);

        one.execute(input(1));
        // If the counter were shared or static, two's first call would be invocation 2 and would succeed —
        // and a retry test using a fresh fake would silently never retry.
        assertFalse(two.execute(input(1)).succeeded(), "each script instance counts its own attempts");
        assertEquals(1, one.invocations());
        assertEquals(1, two.invocations());
    }

    @Test
    @DisplayName("timeout: TIMEOUT, proposed retryable, every time")
    void timesOut() {
        ScriptedExecutor executor = ScriptedExecutor.timesOut();
        for (int attempt = 1; attempt <= 3; attempt++) {
            StageOutcome outcome = executor.execute(input(attempt));
            assertFalse(outcome.succeeded());
            assertEquals(FailureCategory.TIMEOUT, outcome.failure().category());
            assertTrue(outcome.failure().executorProposesRetryable(),
                    "the executor proposes; whether TIMEOUT is ALLOWED is the stage's declared set and "
                            + "T084's ruling, which is the decision under test");
        }
    }

    @Test
    @DisplayName("malformed: throws, which is the only malformation an envelope permits")
    void malformedThrows() {
        // FailureEnvelope refuses to be constructed in a contradictory state, so there is no such thing as
        // handing back a malformed one. An executor that escapes with an exception gives the orchestrator a
        // failure with no category at all — the case EC-032 says is permanent.
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ScriptedExecutor.malformed().execute(input(1)));
        assertTrue(thrown.getMessage().contains("must RETURN"), thrown.getMessage());
    }

    @Test
    @DisplayName("returns null: the other way to give the orchestrator nothing to rule on")
    void returnsNull() {
        assertNull(ScriptedExecutor.returnsNull().execute(input(1)));
    }

    @Test
    @DisplayName("propose transient for an undeclared category: the proposal arrives in good faith")
    void proposesRetryForUndeclaredCategory() {
        StageOutcome outcome = ScriptedExecutor
                .proposesRetryForUndeclaredCategory(FailureCategory.RATE_LIMITED)
                .execute(input(1));

        assertEquals(FailureCategory.RATE_LIMITED, outcome.failure().category());
        assertTrue(outcome.failure().executorProposesRetryable(),
                "this is the script that distinguishes a real intersection from a rubber stamp: if the "
                        + "ruling took the executor's word, a declared set would be documentation");

        assertThrows(IllegalArgumentException.class,
                () -> ScriptedExecutor.proposesRetryForUndeclaredCategory(FailureCategory.UNKNOWN),
                "UNKNOWN cannot be proposed retryable at all, so a script asking for it would fail at "
                        + "construction and the test would prove the wrong control");
    }

    @Test
    @DisplayName("every script records what it was handed, so attempt propagation is assertable")
    void scriptsRecordTheirInputs() {
        ScriptedExecutor executor = ScriptedExecutor.failsThenSucceeds(1);
        executor.execute(input(1));
        executor.execute(input(2));

        assertEquals(List.of(1, 2), executor.seen().stream().map(StageInput::attempt).toList(),
                "a retry that did not increment the attempt would be invisible in the outcome alone");
    }

    @Test
    @DisplayName("a succeeding script's artifact derives from the inputs it was given")
    void producedArtifactCarriesProvenance() {
        StageOutcome outcome = ScriptedExecutor.alwaysSucceeds().execute(input(1));
        assertEquals(List.of("design"), outcome.producedArtifacts().get(0).derivedFrom(),
                "a fake that produced provenance-free artifacts would make T081's chain assertions pass "
                        + "against runs where nothing was derived from anything");
    }
}
