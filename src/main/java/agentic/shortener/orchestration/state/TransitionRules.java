package agentic.shortener.orchestration.state;

import java.util.Objects;

/**
 * The seven prohibited transitions, refused rather than omitted. Task T079. FR-ORC-006, FR-ORC-013.
 *
 * <p><strong>Rejected, not merely absent.</strong> A transition absent from a table is one nobody wrote
 * down; a transition <em>rejected</em> is one the engine refuses when asked. Only the second survives a
 * caller who asks anyway — and a caller who asks anyway is what a bug is.
 *
 * <p><strong>This is the control that makes ADR-003's build decision defensible.</strong> That ADR chose
 * to build the engine rather than adopt one, and building means owning transition correctness. An
 * adopted engine would arrive with its own guarantees; these rules replace them.
 *
 * <p>Each refusal names the rule it enforces. A refusal without a reason leaves the caller to guess at
 * their own workflow, and the caller here is usually a stage executor with no view of the run.
 */
public final class TransitionRules {

    /** @param reason empty when allowed; always populated on a refusal */
    public record Verdict(boolean allowed, String reason) {

        static Verdict allow() {
            return new Verdict(true, "");
        }

        static Verdict refuse(String reason) {
            return new Verdict(false, reason);
        }
    }

    public Verdict permits(TransitionRequest request) {
        Objects.requireNonNull(request, "request");

        // 6/7 — leaving SAFE_STOP. Checked FIRST, because a suspended run must not resume for any
        // reason that is not one of the two authorities, and checking it last would let a transition
        // that is otherwise legitimate slip through while the run was suspended.
        if (request.runState() == RunState.SAFE_STOP
                && !request.humanDecision() && !request.retentionExpiry()) {
            return Verdict.refuse(
                    "a run in SAFE_STOP is left only by a human decision or by retention expiry; "
                            + "no component may resume it on its own authority (FR-ORC-017)");
        }

        // 5/7 — FAILED is never relabelled. A failed node is re-entered by a NEW attempt, never by
        // rewriting the old one, or a run could launder its own history.
        if (request.from() == StageState.FAILED && request.to() == StageState.SUCCEEDED) {
            return Verdict.refuse(
                    "FAILED does not become SUCCEEDED: a failed node is re-entered by a new attempt, "
                            + "never by relabelling the one that failed (FR-ORC-006)");
        }

        // 2/7 — a gate advances only on a recorded decision. Constitution III: silence is never
        // approval, and a gate that advanced otherwise would make every gate decorative.
        if (request.from() == StageState.AWAITING_APPROVAL && request.to() == StageState.SUCCEEDED
                && !request.recordedGateDecision()) {
            return Verdict.refuse(
                    "AWAITING_APPROVAL advances only on a recorded human decision; silence is never "
                            + "approval (FR-ORC-013, Constitution III)");
        }

        // 1/7 — EC-029 in its transition form.
        if (request.to() == StageState.SUCCEEDED && request.from() == StageState.RUNNING
                && request.artifactCount() <= 0) {
            return Verdict.refuse(
                    "a stage reporting success with no output artifact fails its exit criteria "
                            + "(EC-029, FR-ORC-006)");
        }

        if (request.from() == StageState.RETRY_WAIT && request.to() == StageState.RUNNING) {
            // 3/7 — the bound. Exhausted means exhausted; one more attempt is how a bounded retry
            // becomes an unbounded one.
            if (request.attemptsUsed() >= request.attemptBound()) {
                return Verdict.refuse(
                        "the retry bound is exhausted (" + request.attemptsUsed() + " of "
                                + request.attemptBound() + "); a bound that yields once is not a bound");
            }
            // 4/7 — the two-vote rule. The declared retryable set AND the executor's proposal must
            // agree; either alone is one party deciding it may try again, which is how a
            // non-idempotent effect gets applied twice.
            if (!request.retryVotes()) {
                return Verdict.refuse(
                        "a retry needs both votes — the stage's declared retryable set and the "
                                + "executor's proposed classification (CL-006, FR-ORC-016)");
            }
        }

        // 7/7 — the recovery kind must match the effect kind. Two halves, mirror images.
        if (request.to() == StageState.COMPENSATING && request.effectErasable()) {
            return Verdict.refuse(
                    "COMPENSATING is for an effect that cannot be erased; this one is erasable, so "
                            + "ROLLING_BACK is the honest action (FR-ORC-018)");
        }
        if (request.to() == StageState.ROLLING_BACK && !request.effectErasable()) {
            return Verdict.refuse(
                    "ROLLING_BACK is impossible against an immutable store; an append-only effect is "
                            + "corrected forward by COMPENSATING (FR-ORC-018)");
        }

        return Verdict.allow();
    }

    /**
     * The form the writer uses.
     *
     * <p>A boolean nobody checks is a comment, so the only way to make a prohibited transition is to
     * delete this call rather than to forget an {@code if}.
     */
    public void requirePermitted(TransitionRequest request) {
        Verdict verdict = permits(request);
        if (!verdict.allowed()) {
            throw new IllegalStateException("transition refused: " + request.from() + " -> "
                    + request.to() + ": " + verdict.reason());
        }
    }
}
