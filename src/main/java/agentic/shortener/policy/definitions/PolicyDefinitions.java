package agentic.shortener.policy.definitions;

import agentic.shortener.policy.PolicyDefinition;
import agentic.shortener.policy.PolicySet;

import java.util.List;

/**
 * The twelve definitions of {@code policy-set-1.1.0}, all mandatory. Task T098. FR-ORC-022. plan §9.
 *
 * <p><strong>Twelve, all mandatory — no advisory instance exists.</strong> plan §9's own table carries no
 * advisory row; {@code PolicyCompletenessTest} asserts the count against that table and asserts the split,
 * so a policy added here without a matching table row (or a quietly-flipped {@code mandatory} flag) is a
 * detection target, not a silent gap in compliance coverage (T098's own Guard clause).
 *
 * <p><strong>{@code POL-CHG-003} added, {@code POL-AUD-002} removed</strong> — the difference between
 * {@code policy-set-1.0.0} and {@code -1.1.0}. CR-013 adopted 1.1.0 on the ground that "version discipline
 * binds from first real use, and no run has ever evaluated 1.0.0"; CR-017 removed the retention-path check
 * because, having settled on indefinite retention with no purge or archival path, it could never
 * meaningfully evaluate anything.
 */
public final class PolicyDefinitions {

    private PolicyDefinitions() {
    }

    /**
     * Domain values are snake_case, matching {@code contracts/policy-evaluation.schema.json}'s own
     * {@code domain} enum exactly (CR-043) — the schema is the contract, so the Java side conforms to it
     * rather than the reverse.
     */
    public static final PolicySet DEFAULT = new PolicySet(PolicySet.CURRENT_VERSION, List.of(
            new PolicyDefinition("POL-SEC-001", "security", true,
                    "The scheme allow-list is present and non-empty"),
            new PolicyDefinition("POL-SEC-002", "security", true,
                    "A secret scan over the repository and captured telemetry finds nothing (FR-URL-017)"),
            new PolicyDefinition("POL-SEC-003", "security", true,
                    "A dependency vulnerability scan finds no unaddressed finding"),
            new PolicyDefinition("POL-PRIV-001", "privacy", true,
                    "The stored schema contains no personal-data field"),
            new PolicyDefinition("POL-AUD-001", "audit_retention", true,
                    "Every audit record has all six mandatory fields"),
            new PolicyDefinition("POL-DEP-001", "approved_dependencies", true,
                    "Every dependency is on the approved list"),
            new PolicyDefinition("POL-LIC-001", "licensing", true,
                    "Every dependency's licence is on the permitted list"),
            new PolicyDefinition("POL-CHG-001", "change_control", true,
                    "A contract or schema change carries a change-control record"),
            new PolicyDefinition("POL-CHG-002", "change_control", true,
                    "Every mandatory gate outcome is materialized as a repository record"),
            new PolicyDefinition("POL-CHG-003", "change_control", true,
                    "A MAJOR-classified contract or schema change carries a new major version (CR-013)"),
            new PolicyDefinition("POL-TST-001", "compliance", true,
                    "The required test categories are non-empty and were actually executed"),
            new PolicyDefinition("POL-TRC-001", "compliance", true,
                    "Zero traceability orphans, in both directions (T124)")));
}
