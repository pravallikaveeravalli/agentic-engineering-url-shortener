package agentic.shortener.orchestration.summary;

import java.util.List;
import java.util.stream.Stream;

/**
 * The five sections T129's Artifact clause names, each a list of {@link EvidencedClaim}. Task T129.
 * FR-ORC-026, CN-001, ADR-010.
 *
 * @param whatWasBuilt                    a delivered task, cited by its own artifact path
 * @param decisionsAndRejectedAlternatives an ADR's recorded status and, where it names one, its rejected
 *                                          option(s)
 * @param executedValidationAndResults     a test tier that actually ran, summed from its own reports, plus
 *                                          the measurement-labelling discipline T121 established
 * @param residualRisksAndLimitations      the files this project already uses to record deferrals and
 *                                          scheduled omissions, not re-derived prose
 * @param aiAssistedProcess                a live AI-stage demonstration or a red-phase deviation capture —
 *                                          T129's Artifact clause's own "including deviations from plan"
 */
public record EngineeringSummary(
        List<EvidencedClaim> whatWasBuilt,
        List<EvidencedClaim> decisionsAndRejectedAlternatives,
        List<EvidencedClaim> executedValidationAndResults,
        List<EvidencedClaim> residualRisksAndLimitations,
        List<EvidencedClaim> aiAssistedProcess) {

    public EngineeringSummary {
        whatWasBuilt = List.copyOf(whatWasBuilt);
        decisionsAndRejectedAlternatives = List.copyOf(decisionsAndRejectedAlternatives);
        executedValidationAndResults = List.copyOf(executedValidationAndResults);
        residualRisksAndLimitations = List.copyOf(residualRisksAndLimitations);
        aiAssistedProcess = List.copyOf(aiAssistedProcess);
    }

    public List<EvidencedClaim> allClaims() {
        return Stream.of(whatWasBuilt, decisionsAndRejectedAlternatives, executedValidationAndResults,
                        residualRisksAndLimitations, aiAssistedProcess)
                .flatMap(List::stream)
                .toList();
    }

    /**
     * A plain rendering: one {@code ##} heading per section, one bullet per claim, the claim's own evidence
     * path in backticks immediately after it so a reader can check the citation without leaving the
     * document. No text here originates anywhere but a claim's own {@link EvidencedClaim#text()} — this
     * method formats, it does not compose.
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Engineering Summary\n\n");
        sb.append("Deterministically assembled by `SummaryAssembler` from recorded repository evidence "
                + "only. Every claim below names the artifact that backs it; a claim this class could not "
                + "trace to a real file is a defect, not merely undisclosed (T129).\n\n");
        appendSection(sb, "What was built", whatWasBuilt);
        appendSection(sb, "Decisions and rejected alternatives", decisionsAndRejectedAlternatives);
        appendSection(sb, "Executed validation and results", executedValidationAndResults);
        appendSection(sb, "Residual risks and limitations", residualRisksAndLimitations);
        appendSection(sb, "AI-assisted process, including deviations from plan", aiAssistedProcess);
        return sb.toString();
    }

    private static void appendSection(StringBuilder sb, String title, List<EvidencedClaim> claims) {
        sb.append("## ").append(title).append("\n\n");
        if (claims.isEmpty()) {
            sb.append("_No traceable evidence found for this section at assembly time._\n\n");
            return;
        }
        for (EvidencedClaim claim : claims) {
            sb.append("- ").append(claim.text()).append(" (`").append(claim.evidencePath()).append("`)\n");
        }
        sb.append('\n');
    }
}
