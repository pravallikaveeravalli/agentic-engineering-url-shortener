package agentic.shortener.orchestration.reliability;

import java.util.Objects;

/**
 * The only thing the orchestrator reads about a failure. Task T083. FR-ORC-014 (CL-006).
 *
 * @param category                   one of the six standard categories, never a provider's own
 * @param detail                     what happened, for the human reading {@code failure_event} later
 * @param executorProposesRetryable  <strong>one of two votes, not a verdict.</strong> Named for what it is:
 *                                   {@code isRetryable()} would invite a caller to act on the executor's
 *                                   diagnosis alone, and the executor holds a veto, never a grant
 *                                   (T084). The orchestrator rules, because only it knows attempts
 *                                   consumed, compensation already issued and replan invalidation
 */
public record FailureEnvelope(FailureCategory category, String detail,
                              boolean executorProposesRetryable) {

    public FailureEnvelope {
        Objects.requireNonNull(category, "category");
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException(
                    "a failure detail is required: a blank one leaves a failure_event row nobody can act "
                            + "on, and MTTR over unactionable rows is a number rather than a measurement");
        }
        if (category == FailureCategory.UNKNOWN && executorProposesRetryable) {
            // EC-031/EC-032 enforced where the envelope is built rather than where it is read. "Retry
            // this, I don't know what it is" is the unbounded-retry path FR-ORC-014 forbids, and refusing
            // it here means no ruling has to remember the rule.
            throw new IllegalArgumentException(
                    "an UNKNOWN failure must not be proposed retryable: default-deny is what stops an "
                            + "unrecognized failure becoming an unbounded retry (EC-031, EC-032)");
        }
    }
}
