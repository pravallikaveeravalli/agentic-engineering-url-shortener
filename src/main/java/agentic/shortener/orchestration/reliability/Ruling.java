package agentic.shortener.orchestration.reliability;

import java.util.Objects;

/**
 * One retry decision, with both votes recorded. Task T084. FR-ORC-014 (CL-006).
 *
 * <p><strong>Both signatures, always.</strong> A record saying only "no retry" cannot be audited, because the
 * question afterwards is always <em>which vote refused</em> — a stage whose declared set is too narrow and an
 * executor misdiagnosing its own failures are different problems with different fixes, and a single boolean
 * cannot tell them apart.
 *
 * @param declaredRetryable          the stage's vote, from its design-time declared retryable set
 * @param executorProposedRetryable  the executor's vote, from the failure envelope
 * @param retry                      the intersection, and nothing else
 */
public record Ruling(int stageNumber, FailureCategory category, boolean declaredRetryable,
                     boolean executorProposedRetryable, boolean retry, String reason) {

    public Ruling {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(reason, "reason");
        if (retry != (declaredRetryable && executorProposedRetryable)) {
            // The invariant restated where it cannot be bypassed. A Ruling constructed by hand with a
            // retry flag that is not the intersection would be a decision nobody made, carrying two
            // signatures that do not support it.
            throw new IllegalArgumentException(
                    "a ruling must be exactly the intersection of the two votes; got retry=" + retry
                            + " from declared=" + declaredRetryable
                            + " and executor=" + executorProposedRetryable);
        }
    }
}
