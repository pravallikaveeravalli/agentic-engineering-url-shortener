package agentic.shortener.orchestration.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T079 — the seven prohibited transitions, each asserted <strong>rejected</strong>. FR-ORC-006,
 * FR-ORC-013.
 *
 * <p><strong>Rejected, not merely absent.</strong> The difference is the whole task. A transition that is
 * absent from a table is one nobody wrote down; a transition that is <em>rejected</em> is one the engine
 * refuses when asked. Only the second survives a caller who asks anyway — and a caller who asks anyway is
 * what a bug is.
 *
 * <p><strong>Why this suite is the control that makes ADR-003 defensible.</strong> That ADR chose to
 * build the engine rather than adopt one, and building means owning transition correctness. An adopted
 * engine would come with its own guarantees; this file is what replaces them.
 *
 * <p>Each of the seven is a different way a run could claim progress it has not made. They are separate
 * tests because a suite that collapsed them into one loop would report "a prohibited transition was
 * allowed" without saying which, and they do not fail for the same reasons.
 *
 * <p>Fast tier.
 */
@DisplayName("T079 prohibited transitions are rejected")
class ProhibitedTransitionTest {

    private final TransitionRules rules = new TransitionRules();

    private static TransitionRequest request(StageState from, StageState to) {
        return TransitionRequest.of(from, to);
    }

    @Test
    @DisplayName("1/7 — RUNNING → SUCCEEDED without exit criteria")
    void runningToSucceededWithoutExitCriteria() {
        // EC-029's transition form: the stage ran, produced nothing, and would be recorded as complete.
        TransitionRequest withoutArtifact = request(StageState.RUNNING, StageState.SUCCEEDED)
                .withArtifactCount(0);

        assertFalse(rules.permits(withoutArtifact).allowed());
        assertTrue(rules.permits(withoutArtifact).reason().contains("exit criteria"),
                rules.permits(withoutArtifact).reason());

        // The same transition WITH an artifact is the ordinary success path, and must work.
        assertTrue(rules.permits(withoutArtifact.withArtifactCount(1)).allowed(),
                "a rule that rejected the legitimate case too would just be a broken engine");
    }

    @Test
    @DisplayName("2/7 — AWAITING_APPROVAL → SUCCEEDED without a recorded decision")
    void awaitingApprovalToSucceededWithoutDecision() {
        // Constitution III: silence is never approval. A gate that advanced on anything other than a
        // recorded human decision would make every gate in the system decorative.
        TransitionRequest withoutDecision = request(StageState.AWAITING_APPROVAL, StageState.SUCCEEDED)
                .withArtifactCount(1);

        assertFalse(rules.permits(withoutDecision).allowed());
        assertTrue(rules.permits(withoutDecision).reason().contains("decision"),
                rules.permits(withoutDecision).reason());

        assertTrue(rules.permits(withoutDecision.withRecordedGateDecision(true)).allowed());
    }

    @Test
    @DisplayName("3/7 — RETRY_WAIT → RUNNING with an exhausted bound")
    void retryWaitToRunningWithExhaustedBound() {
        TransitionRequest exhausted = request(StageState.RETRY_WAIT, StageState.RUNNING)
                .withAttemptsUsed(3).withAttemptBound(3).withRetryVotes(true);

        assertFalse(rules.permits(exhausted).allowed());
        assertTrue(rules.permits(exhausted).reason().contains("bound"), rules.permits(exhausted).reason());

        assertTrue(rules.permits(exhausted.withAttemptsUsed(2)).allowed(),
                "inside the bound, with both votes, a retry is exactly what should happen");
    }

    @Test
    @DisplayName("4/7 — RETRY_WAIT → RUNNING with a missing vote")
    void retryWaitToRunningWithMissingVote() {
        // The two-vote rule: a retry needs the declared retryable set AND the executor's proposal to
        // agree. Either alone is one party deciding it may try again, which is how a non-idempotent
        // effect gets applied twice.
        TransitionRequest oneVote = request(StageState.RETRY_WAIT, StageState.RUNNING)
                .withAttemptsUsed(0).withAttemptBound(3).withRetryVotes(false);

        assertFalse(rules.permits(oneVote).allowed());
        assertTrue(rules.permits(oneVote).reason().contains("vote"), rules.permits(oneVote).reason());
    }

    @Test
    @DisplayName("5/7 — FAILED → SUCCEEDED")
    void failedToSucceeded() {
        // No qualifier and no escape hatch: a failed node is re-entered by a new attempt, never by
        // relabelling the old one. This is the transition that would let a run launder its own history.
        TransitionRequest laundering = request(StageState.FAILED, StageState.SUCCEEDED)
                .withArtifactCount(1).withRecordedGateDecision(true);

        assertFalse(rules.permits(laundering).allowed());
        assertTrue(rules.permits(laundering).reason().contains("FAILED"),
                rules.permits(laundering).reason());
    }

    @Test
    @DisplayName("6/7 — leaving SAFE_STOP other than by human decision or retention")
    void leavingSafeStopWithoutAuthority() {
        TransitionRequest unauthorized = request(StageState.BLOCKED, StageState.RUNNING)
                .withRunState(RunState.SAFE_STOP);

        assertFalse(rules.permits(unauthorized).allowed(),
                "a suspended run must not resume because a component decided to carry on");
        assertTrue(rules.permits(unauthorized).reason().contains("SAFE_STOP"),
                rules.permits(unauthorized).reason());

        // The two legitimate exits.
        assertTrue(rules.permits(unauthorized.withHumanDecision(true)).allowed(),
                "a human decision resumes it");
        assertTrue(rules.permits(unauthorized.withRetentionExpiry(true)).allowed(),
                "and retention abandons it — the only other way out");
    }

    @Test
    @DisplayName("7/7 — COMPENSATING on an erasable effect, ROLLING_BACK on an immutable store")
    void wrongRecoveryForTheEffectKind() {
        // Two halves of one rule, and they are mirror images. Rolling back an append-only store is
        // impossible; compensating an erasable one is a forward-correcting action where a simple
        // reversal was available and honest.
        TransitionRequest compensatingErasable = request(StageState.FAILED, StageState.COMPENSATING)
                .withEffectErasable(true);
        assertFalse(rules.permits(compensatingErasable).allowed());
        assertTrue(rules.permits(compensatingErasable).reason().contains("erasable"),
                rules.permits(compensatingErasable).reason());

        TransitionRequest rollingBackImmutable = request(StageState.FAILED, StageState.ROLLING_BACK)
                .withEffectErasable(false);
        assertFalse(rollingBackImmutable.effectErasable());
        assertFalse(rules.permits(rollingBackImmutable).allowed());
        assertTrue(rules.permits(rollingBackImmutable).reason().contains("immutable"),
                rules.permits(rollingBackImmutable).reason());

        // And each correct pairing is permitted.
        assertTrue(rules.permits(request(StageState.FAILED, StageState.ROLLING_BACK)
                .withEffectErasable(true)).allowed());
        assertTrue(rules.permits(request(StageState.FAILED, StageState.COMPENSATING)
                .withEffectErasable(false)).allowed());
    }

    @Test
    @DisplayName("all seven are rejected, asserted together")
    void allSevenTogether() {
        // T079's Done condition is "seven prohibited transitions each rejected", and it is asserted as
        // one statement as well as seven. A future change that fixed six and broke the seventh would
        // leave six green tests and this one red.
        List<TransitionRequest> prohibited = List.of(
                request(StageState.RUNNING, StageState.SUCCEEDED).withArtifactCount(0),
                request(StageState.AWAITING_APPROVAL, StageState.SUCCEEDED).withArtifactCount(1),
                request(StageState.RETRY_WAIT, StageState.RUNNING)
                        .withAttemptsUsed(3).withAttemptBound(3).withRetryVotes(true),
                request(StageState.RETRY_WAIT, StageState.RUNNING)
                        .withAttemptsUsed(0).withAttemptBound(3).withRetryVotes(false),
                request(StageState.FAILED, StageState.SUCCEEDED).withArtifactCount(1),
                request(StageState.BLOCKED, StageState.RUNNING).withRunState(RunState.SAFE_STOP),
                request(StageState.FAILED, StageState.COMPENSATING).withEffectErasable(true));

        assertEquals(7, prohibited.size(), "seven, as T079 enumerates them");
        for (TransitionRequest attempt : prohibited) {
            assertFalse(rules.permits(attempt).allowed(),
                    "permitted a prohibited transition: " + attempt);
        }
    }

    @Test
    @DisplayName("the throwing form refuses, so a caller cannot ignore the answer")
    void requirePermittedThrows() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> rules.requirePermitted(request(StageState.FAILED, StageState.SUCCEEDED)));
        assertTrue(thrown.getMessage().contains("FAILED"), thrown.getMessage());

        rules.requirePermitted(request(StageState.RUNNING, StageState.SUCCEEDED).withArtifactCount(1));
    }

    @Test
    @DisplayName("a refusal names the rule, so it is actionable")
    void refusalsAreActionable() {
        for (TransitionRequest attempt : List.of(
                request(StageState.RUNNING, StageState.SUCCEEDED).withArtifactCount(0),
                request(StageState.FAILED, StageState.SUCCEEDED))) {
            String reason = rules.permits(attempt).reason();
            assertFalse(reason.isBlank(), "a refusal with no reason is a dead end: " + attempt);
            assertTrue(reason.length() > 20, "and a terse one is barely better: " + reason);
        }
    }

    @Test
    @DisplayName("the ordinary transitions still work — this is a rule set, not a wall")
    void legitimateTransitionsArePermitted() {
        // Without this, a rules object that refused everything would pass every assertion above.
        for (TransitionRequest allowed : List.of(
                request(StageState.BLOCKED, StageState.READY),
                request(StageState.READY, StageState.RUNNING),
                request(StageState.RUNNING, StageState.AWAITING_APPROVAL),
                request(StageState.RUNNING, StageState.FAILED),
                request(StageState.RUNNING, StageState.RETRY_WAIT),
                request(StageState.BLOCKED, StageState.SKIPPED),
                request(StageState.RUNNING, StageState.SUCCEEDED).withArtifactCount(1))) {
            assertTrue(rules.permits(allowed).allowed(),
                    "refused a legitimate transition: " + allowed + " — " + rules.permits(allowed).reason());
        }
    }
}
