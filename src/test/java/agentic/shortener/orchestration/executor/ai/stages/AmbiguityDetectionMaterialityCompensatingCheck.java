package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.ClaudeCodeCliStageAiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-049's non-negotiable compensating check, run against the REAL Claude CLI, not a fixture — proving the
 * materiality-classification step added to S3's prompt did not go soft. DS-C's own canonical contradiction
 * (spec.md &sect;"What ambiguity detection is") — expire after a week, retain analytics indefinitely, still
 * redirect for trusted partners — determines which of two behaviours the system MUST exhibit (does an
 * expired link still redirect for a trusted partner?) with no existing approved artifact resolving it, so
 * CR-007's own predicate requires {@code MATERIAL_PENDING} here even after calibration. If this ever stops
 * finding at least one {@code MATERIAL_PENDING} record, the calibration went too far and must be treated as
 * a real regression, not tuned further.
 *
 * <p>NOT named {@code *Test}/{@code *IT} — matching every other live-demo class's own convention; run
 * explicitly:
 *
 * <pre>./scripts/build.sh -q -Dtest=AmbiguityDetectionMaterialityCompensatingCheck -DfailIfNoTests=false test</pre>
 */
class AmbiguityDetectionMaterialityCompensatingCheck {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("CR-049 compensating check: DS-C's canonical contradiction still classifies MATERIAL_PENDING")
    void dsCCanonicalContradictionStillMaterial() throws Exception {
        var provider = new ClaudeCodeCliStageAiProvider("claude", "claude-sonnet-5");
        var executor = new AmbiguityDetectionAiExecutor(provider);

        // spec.md's own canonical demonstration input, verbatim.
        String requirements = "[{\"externalId\":\"R1\",\"type\":\"FUNCTIONAL\",\"statement\":\"Short links "
                + "expire after a week.\"},{\"externalId\":\"R2\",\"type\":\"FUNCTIONAL\",\"statement\":"
                + "\"Redirect analytics are retained indefinitely.\"},{\"externalId\":\"R3\",\"type\":"
                + "\"FUNCTIONAL\",\"statement\":\"Expired links should still redirect for trusted "
                + "partners.\"}]";

        StageInput input = new StageInput(UUID.randomUUID(), "S3", 3, 1,
                Map.of(AmbiguityDetectionAiExecutor.INPUT_KEY, requirements));

        StageOutcome outcome = executor.execute(input);

        assertTrue(outcome.succeeded(), outcome.succeeded() ? "" : "live call failed: " + outcome.failure());

        JsonNode records = JSON.readTree(outcome.producedArtifacts().get(0).content());
        boolean anyMaterial = false;
        for (JsonNode record : records) {
            System.out.println("CR-049 COMPENSATING CHECK: " + record.toString());
            if ("MATERIAL_PENDING".equals(record.path("resolutionState").asText())) {
                anyMaterial = true;
            }
        }

        assertTrue(anyMaterial, "DS-C's canonical contradiction (expire after a week / retain analytics "
                + "indefinitely / still redirect for trusted partners) determines which of two behaviours "
                + "the system MUST exhibit, with no existing approved artifact resolving it -- CR-007's own "
                + "predicate requires MATERIAL_PENDING here. If this fails, the materiality calibration "
                + "went too far and is a real regression, not something to tune further.");
    }
}
