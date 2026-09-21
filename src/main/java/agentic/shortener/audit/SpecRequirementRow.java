package agentic.shortener.audit;

import java.util.Objects;

/**
 * One row of {@code spec.md}'s Traceability matrix, parsed as-is. Task T123. FR-ORC-027.
 *
 * @param requirementId e.g. {@code FR-URL-001}
 * @param design        never blank for a row this parser accepts — CR-006 added Design and ADR to every
 *                       row, and a row without either is itself a finding, not a parse the reporter drops
 * @param taskCell      the raw Task-column text. For most {@code FR-ORC-*} rows this is still the literal
 *                       placeholder {@code *Tasks stage*} — see {@link TraceabilityReporter}'s own javadoc
 *                       for why this parser does not trust this column and cross-references
 *                       {@code tasks.md} instead
 */
public record SpecRequirementRow(String requirementId, String journey, String scenario, String edgeCases,
                                  String design, String adr, String taskCell, String testCell,
                                  String evidenceCell) {

    public SpecRequirementRow {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(design, "design");
        Objects.requireNonNull(adr, "adr");
    }
}
