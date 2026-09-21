package agentic.shortener.policy;

import java.util.List;
import java.util.Objects;

/**
 * A versioned, loadable set of policy definitions. Task T097. FR-ORC-022, Constitution VI. plan §9.
 *
 * <p><strong>Every run records the policy version evaluated; a run without one is invalid</strong> — T097's
 * own Validate clause. {@link agentic.shortener.audit.AuditWriter}-style writers elsewhere in this codebase
 * enforce their own mandatory fields structurally; this type does the same for the version: {@link
 * #definitions()} cannot be evaluated without a {@link #version()} to attach to the result, because both
 * live on the same immutable record.
 *
 * <p><strong>Never applied retroactively</strong> (plan §9): a {@code PolicySet} instance is a frozen
 * snapshot of one version's definitions. Evaluating a past run's evidence against a newer version, or a
 * current run's evidence against an older one, are both callers' mistakes this type cannot prevent by
 * itself — the version travelling with every evaluation result (T098's {@code PolicyCheckResult}) is what
 * makes such a mismatch detectable after the fact.
 */
public record PolicySet(String version, List<PolicyDefinition> definitions) {

    /** {@code policy-set-1.1.0} — CR-013 adopted it (POL-CHG-003 added), CR-017 removed POL-AUD-002. */
    public static final String CURRENT_VERSION = "policy-set-1.1.0";

    public PolicySet {
        Objects.requireNonNull(version, "version");
        if (version.isBlank()) {
            throw new IllegalArgumentException(
                    "a policy set with no version is exactly the defect T097 exists to make impossible");
        }
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("a policy set with no definitions evaluates nothing");
        }
        definitions = List.copyOf(definitions);
    }

    public PolicyDefinition require(String id) {
        return definitions.stream().filter(d -> d.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no policy '" + id + "' in " + version));
    }
}
