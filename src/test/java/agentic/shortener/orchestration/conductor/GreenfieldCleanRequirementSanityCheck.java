package agentic.shortener.orchestration.conductor;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.ClaudeCodeCliStageAiProvider;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.executor.ai.stages.AmbiguityDetectionAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.NormalizationAiExecutor;
import agentic.shortener.orchestration.executor.deterministic.IngestionEngine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-062's own owner-mandated sanity check: before any full live run against the clean greenfield
 * requirement (CR-062, {@link DsALiveRun#REQUIREMENT}), run ONLY S1 (real, deterministic) → S2 (real, live
 * AI) → S3 (real, live AI) in isolation — no S5+, no {@link Conductor}, no database — and inspect S3's own
 * real findings before ever risking a full pipeline run against a gate this session does not yet understand
 * for this exact wording. NOT named {@code *Test}/{@code *IT}; run explicitly:
 *
 * <pre>./scripts/build.sh -q -Dtest=GreenfieldCleanRequirementSanityCheck -DfailIfNoTests=false test</pre>
 */
class GreenfieldCleanRequirementSanityCheck {

    private static final String CLI = "claude";
    private static final String MODEL = "claude-sonnet-5";

    // Verbatim copy of DsALiveRun.REQUIREMENT (CR-062) -- duplicated deliberately rather than widening that
    // field's visibility just for this isolated, throwaway sanity check.
    private static final String REQUIREMENT =
            "Add a public endpoint GET /v1/version that requires no authentication and takes no path or "
                    + "query parameters, returning HTTP 200 with Content-Type: application/json, body "
                    + "{\"version\": \"<the application's version>\"}, and header Cache-Control: no-store.";

    @Test
    void s1ThroughS3InIsolation() {
        System.out.println("SANITY CHECK: submitting requirement: " + REQUIREMENT);

        // S1 -- real, deterministic, no AI call.
        StageOutcome s1Outcome = new IngestionEngine().execute(
                new StageInput(UUID.randomUUID(), "S1", 1, 1, Map.of("submission", REQUIREMENT)));
        assertTrue(s1Outcome.succeeded(), "S1 must succeed on a non-blank requirement: "
                + (s1Outcome.succeeded() ? "" : s1Outcome.failure().detail()));
        String intake = artifact(s1Outcome, "requirement.intake");
        System.out.println("SANITY CHECK: S1 output (requirement.intake): " + intake);

        // S2 -- real, live AI call.
        StageAiProvider s2Provider = new ClaudeCodeCliStageAiProvider(CLI, MODEL);
        NormalizationAiExecutor s2 = new NormalizationAiExecutor(s2Provider);
        StageOutcome s2Outcome = s2.execute(
                new StageInput(UUID.randomUUID(), "S2", 2, 1, Map.of("requirement.intake", intake)));
        assertTrue(s2Outcome.succeeded(), "S2 must succeed: "
                + (s2Outcome.succeeded() ? "" : s2Outcome.failure().detail()));
        String requirements = artifact(s2Outcome, "requirements");
        System.out.println("SANITY CHECK: S2 real output (requirements):\n" + requirements);

        // S3 -- real, live AI call. This is the actual sanity check.
        StageAiProvider s3Provider = new ClaudeCodeCliStageAiProvider(CLI, MODEL);
        AmbiguityDetectionAiExecutor s3 = new AmbiguityDetectionAiExecutor(s3Provider);
        StageOutcome s3Outcome = s3.execute(
                new StageInput(UUID.randomUUID(), "S3", 3, 1, Map.of("requirements", requirements)));
        assertTrue(s3Outcome.succeeded(), "S3 must succeed (a structurally malformed answer is a separate "
                + "problem from a material finding): "
                + (s3Outcome.succeeded() ? "" : s3Outcome.failure().detail()));
        String ambiguities = artifact(s3Outcome, "ambiguities");
        System.out.println("SANITY CHECK: S3 real output (ambiguities), IN FULL:\n" + ambiguities);
        System.out.println("SANITY CHECK: complete. Inspect the ambiguities above for any MATERIAL_PENDING "
                + "before running the full pipeline.");
    }

    private static String artifact(StageOutcome outcome, String key) {
        for (ProducedArtifact artifact : outcome.producedArtifacts()) {
            if (artifact.artifactKey().equals(key)) {
                return artifact.content();
            }
        }
        throw new IllegalStateException("no '" + key + "' artifact in outcome: " + outcome);
    }
}
