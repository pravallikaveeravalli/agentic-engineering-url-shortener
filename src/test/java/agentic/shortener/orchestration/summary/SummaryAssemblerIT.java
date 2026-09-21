package agentic.shortener.orchestration.summary;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T129's Validate clause, run against the real project rather than a fixture: "every claim in the summary
 * traces to a recorded artifact; a claim without a trace fails the build." Task T129. FR-ORC-026.
 *
 * <p>Deliberately an {@code IT}, not a fast-tier test: it needs {@code target/surefire-reports} AND {@code
 * target/failsafe-reports} to already hold real reports to assert anything meaningful about the executed
 * validation section, and Failsafe only runs after Surefire's own {@code test} phase has completed
 * ({@code scripts/ci.sh}'s own step ordering). Running this earlier would not be wrong, only weaker — the
 * integration-tier claim would simply be absent, exactly as {@link SummaryAssemblerTest}'s fixture proves is
 * the correct behaviour when that directory does not exist yet.
 */
@DisplayName("T129 — SummaryAssembler traces every claim against the real repository")
class SummaryAssemblerIT {

    private static final Path REPO_ROOT = Paths.get("").toAbsolutePath();

    @Test
    @DisplayName("every claim assembled from the real repository resolves to a real file")
    void everyRealClaimTraces() {
        EngineeringSummary summary = new SummaryAssembler(REPO_ROOT).assemble();

        assertFalse(summary.allClaims().isEmpty(), "assembling against the real, delivered project "
                + "produced zero claims — every section method returned nothing, which means either "
                + "the repository state this test expects is missing or the assembler itself regressed");

        for (EvidencedClaim claim : summary.allClaims()) {
            assertTrue(Files.exists(REPO_ROOT.resolve(claim.evidencePath())),
                    "claim \"" + claim.text() + "\" cites " + claim.evidencePath()
                            + ", which does not exist in the real repository — an untraceable claim, "
                            + "exactly what T129's Guard clause forbids");
        }
    }

    @Test
    @DisplayName("what was built cites at least one real, delivered task")
    void whatWasBuiltIsNonEmpty() {
        EngineeringSummary summary = new SummaryAssembler(REPO_ROOT).assemble();

        assertFalse(summary.whatWasBuilt().isEmpty(),
                "expected at least one done task with a real artifact path in the real project");
    }

    @Test
    @DisplayName("decisions cite at least one real accepted ADR")
    void decisionsAreNonEmpty() {
        EngineeringSummary summary = new SummaryAssembler(REPO_ROOT).assemble();

        assertFalse(summary.decisionsAndRejectedAlternatives().isEmpty(),
                "expected at least one ADR Status claim from docs/governance/adr/");
    }
}
