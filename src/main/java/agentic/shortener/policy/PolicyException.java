package agentic.shortener.policy;

import java.time.Instant;
import java.util.Objects;

/**
 * A recorded, approved policy exception — the nine constitutionally required fields plus its
 * materialization path. Task T103. Constitution §Exception procedure, KE-18.
 *
 * <p><strong>This type IS an approval.</strong> There is no "pending" shape: {@code
 * contracts/policy-evaluation.schema.json}'s own {@code exception} object requires {@code
 * approvingAuthority} and {@code approvedAt} unconditionally, so an unapproved exception is represented by
 * the ABSENCE of a {@code PolicyException} on a {@link PolicyCheckResult}, not by one of these with a null
 * approval — matching T104's own framing: {@code EXCEPTION_REQUESTED} with no recorded approval evaluates
 * as {@code FAIL}, which this type structurally cannot help produce, because it cannot exist un-approved.
 *
 * @param clause               the precise clause excepted, not the whole policy
 * @param repositoryRecordPath must be under {@code docs/governance/exceptions/}, matching the schema's own
 *                             pattern
 */
public record PolicyException(String policyId, String clause, String reason, String scope,
                               String approvingAuthority, String compensatingControl, String residualRisk,
                               Instant approvedAt, Instant expiresAt, String repositoryRecordPath) {

    public PolicyException {
        requireNonBlank(policyId, "policyId");
        requireNonBlank(clause, "clause");
        requireNonBlank(reason, "reason");
        requireNonBlank(scope, "scope");
        requireNonBlank(approvingAuthority, "approvingAuthority");
        requireNonBlank(compensatingControl, "compensatingControl");
        requireNonBlank(residualRisk, "residualRisk");
        Objects.requireNonNull(approvedAt, "approvedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(approvedAt)) {
            throw new IllegalArgumentException("expiresAt must be after approvedAt");
        }
        requireNonBlank(repositoryRecordPath, "repositoryRecordPath");
        if (!repositoryRecordPath.matches("^docs/governance/exceptions/.+\\.md$")) {
            throw new IllegalArgumentException(
                    "repositoryRecordPath must match docs/governance/exceptions/*.md, matching the "
                            + "schema's own pattern: " + repositoryRecordPath);
        }
    }

    /** EC-027: expiry is evaluated AT USE (the caller's {@code now}), never only at approval time. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank — Constitution §Exception "
                    + "procedure requires all nine fields");
        }
    }
}
