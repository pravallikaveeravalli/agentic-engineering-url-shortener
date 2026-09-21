package agentic.shortener.orchestration.impact;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * KE-13: the brownfield pre-change assessment across the seven dimensions of FR-ORC-020. Task T107.
 * ADR-006. DS-B.
 *
 * <p><strong>Seven, none omitted silently.</strong> FR-ORC-020 names exactly these dimensions — impacted
 * components, interfaces, data flows, tests, documentation, regression risks, rollout/rollback — and its
 * own negative clause is explicit: "an analysis MUST NOT omit a dimension silently." Seven constructor
 * parameters rather than a map or a list is what makes that structural: a caller cannot build this record
 * while leaving one out, the way a map could be built with a key simply missing.
 *
 * <p><strong>{@code recordedAt} is what makes FR-ORC-020's ordering claim checkable.</strong> The
 * requirement is "before any change to existing code," and a requirement about ordering is not provable
 * from the analysis's content alone — it needs a timestamp to compare against the first code modification
 * in the run history. This class carries that timestamp; DS-B's own evidence task (T135) is what performs
 * the comparison against a real run.
 */
public record ImpactAnalysis(UUID id, UUID runId, Instant recordedAt, String impactedComponents,
                             String impactedInterfaces, String impactedDataFlows, String impactedTests,
                             String documentation, String regressionRisks, String rolloutRollback) {

    public ImpactAnalysis {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(recordedAt, "recordedAt");
        requireSubstantive("impactedComponents", impactedComponents);
        requireSubstantive("impactedInterfaces", impactedInterfaces);
        requireSubstantive("impactedDataFlows", impactedDataFlows);
        requireSubstantive("impactedTests", impactedTests);
        requireSubstantive("documentation", documentation);
        requireSubstantive("regressionRisks", regressionRisks);
        requireSubstantive("rolloutRollback", rolloutRollback);
    }

    private static void requireSubstantive(String dimension, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "FR-ORC-020: an impact analysis must not omit a dimension silently — '" + dimension
                            + "' is missing or blank. All seven dimensions are required on every analysis, "
                            + "brownfield or not");
        }
    }
}
