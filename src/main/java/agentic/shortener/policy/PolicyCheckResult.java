package agentic.shortener.policy;

import java.util.Objects;

/**
 * One policy's verdict for one run. Task T099, T100. FR-ORC-022.
 *
 * <p>{@code exception} is non-null only when {@code outcome == EXCEPTION_REQUESTED} <em>and</em> approval
 * is recorded — see {@link PolicyException}'s own javadoc for why an unapproved exception cannot be
 * represented here at all, structurally, rather than as a null-approval shape this type would have to
 * reject at construction.
 */
public record PolicyCheckResult(String policyId, String domain, boolean mandatory, PolicyOutcome outcome,
                                 String reason, PolicyException exception) {

    public PolicyCheckResult {
        Objects.requireNonNull(policyId, "policyId");
        if (policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        Objects.requireNonNull(domain, "domain");
        if (domain.isBlank()) {
            throw new IllegalArgumentException("domain must not be blank");
        }
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason must not be blank — a verdict with no reason leaves a blocked run nobody can "
                            + "act on");
        }
        if (exception != null && !exception.policyId().equals(policyId)) {
            throw new IllegalArgumentException(
                    "an exception attached to " + policyId + " must be that policy's own exception, not "
                            + exception.policyId() + "'s");
        }
    }

    public static PolicyCheckResult of(PolicyDefinition definition, PolicyOutcome outcome, String reason) {
        return new PolicyCheckResult(definition.id(), definition.domain(), definition.mandatory(), outcome,
                reason, null);
    }

    public static PolicyCheckResult exceptionRequested(PolicyDefinition definition, String reason,
                                                         PolicyException exception) {
        return new PolicyCheckResult(definition.id(), definition.domain(), definition.mandatory(),
                PolicyOutcome.EXCEPTION_REQUESTED, reason, exception);
    }
}
