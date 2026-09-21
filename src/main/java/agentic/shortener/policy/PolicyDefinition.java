package agentic.shortener.policy;

import java.util.Objects;

/**
 * One policy check's identity, independent of any particular run's verdict on it. Task T097. FR-ORC-022,
 * Constitution VI. plan §9.
 *
 * @param id          e.g. {@code POL-SEC-002}
 * @param domain      one of the seven domains plan §9 names: Security, Privacy, Audit retention, Approved
 *                    dependencies, Licensing, Change control, Compliance
 * @param mandatory   {@code policy-set-1.1.0} carries twelve definitions, **all** mandatory — see {@code
 *                    PolicyDefinitions}'s own javadoc for why an advisory instance is a completeness-test
 *                    finding rather than a legitimate shape this type merely happens to allow
 * @param description what the check evaluates and what PASS/FAIL mean for it
 */
public record PolicyDefinition(String id, String domain, boolean mandatory, String description) {

    public PolicyDefinition {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(domain, "domain");
        if (domain.isBlank()) {
            throw new IllegalArgumentException("domain must not be blank");
        }
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }
}
