package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageExecutorContract;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.reliability.FailureCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T073d — S6 architecture-and-design AI adapter. FR-ORC-020, FR-ORC-029.
 *
 * <p>Against a fake {@link StageAiProvider} answering a recorded fixture (FR-ORC-030): the seven-dimension
 * impact-analysis completeness guard, reusing {@code ImpactAnalysis}'s own constructor (T107).
 */
@DisplayName("T073d — S6 design adapter: seven-dimension impact-analysis completeness guard")
class DesignAiExecutorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC);

    private static StageInput input() {
        return new StageInput(UUID.randomUUID(), "S6", 6, 1,
                Map.of(DesignAiExecutor.INPUT_KEY, "[{\"taskId\":\"T1\"}]"));
    }

    private static StageAiProvider fixedResponse(String json) {
        return prompt -> new AiResponse("claude-test-fixture", json);
    }

    @Nested
    @DisplayName("the StageExecutorContract, applied to DesignAiExecutor")
    class Contract extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new DesignAiExecutor(
                    fixedResponse("{\"design\":\"a design\",\"contractImpact\":\"none\"}"), CLOCK);
        }

        @Override
        protected StageInput validInput() {
            return input();
        }
    }

    @Test
    @DisplayName("greenfield: a design with NO impactAnalysis at all is accepted")
    void greenfieldDesignWithNoImpactAnalysisIsAccepted() throws Exception {
        StageAiProvider provider = fixedResponse(
                "{\"design\":\"a new redirect controller\",\"contractImpact\":\"none, wholly new\"}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertFalse(out.has("impactAnalysis"));
    }

    @Test
    @DisplayName("brownfield: all SEVEN dimensions present is accepted")
    void brownfieldWithAllSevenDimensionsIsAccepted() throws Exception {
        StageAiProvider provider = fixedResponse("{\"design\":\"add aggregate limiter\","
                + "\"contractImpact\":\"no public contract change\","
                + "\"impactAnalysis\":{"
                + "\"impactedComponents\":\"redirect controller and rate-limit module\","
                + "\"impactedInterfaces\":\"internal ownership lookup added\","
                + "\"impactedDataFlows\":\"ownership lookup enters the hot path\","
                + "\"impactedTests\":\"per-code tier tests are the regression surface\","
                + "\"documentation\":\"threat model trade-off documented\","
                + "\"regressionRisks\":\"per-code tier must remain unregressed\","
                + "\"rolloutRollback\":\"disableable without redeploy\"}}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertTrue(out.has("impactAnalysis"));
        assertEquals(8, out.get("impactAnalysis").size(), "recordedAt plus all seven dimensions");
        assertTrue(out.get("impactAnalysis").has("recordedAt"));
    }

    @Test
    @DisplayName("NEGATIVE: an impact analysis omitting ONE of seven dimensions is rejected, not partial")
    void impactAnalysisOmittingOneDimensionIsRejected() {
        StageAiProvider provider = fixedResponse("{\"design\":\"add aggregate limiter\","
                + "\"contractImpact\":\"no public contract change\","
                + "\"impactAnalysis\":{"
                + "\"impactedComponents\":\"redirect controller and rate-limit module\","
                + "\"impactedInterfaces\":\"internal ownership lookup added\","
                + "\"impactedDataFlows\":\"ownership lookup enters the hot path\","
                + "\"impactedTests\":\"per-code tier tests are the regression surface\","
                + "\"documentation\":\"threat model trade-off documented\","
                + "\"regressionRisks\":\"per-code tier must remain unregressed\""
                // rolloutRollback OMITTED
                + "}}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
        assertFalse(outcome.failure().executorProposesRetryable());
        assertTrue(outcome.failure().detail().contains("rolloutRollback"));
    }

    @Test
    @DisplayName("NEGATIVE: a design missing its own 'design' field is refused")
    void missingDesignFieldIsRefused() {
        StageAiProvider provider = fixedResponse("{\"contractImpact\":\"none\"}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }

    @Test
    @DisplayName("S7 existing-file fix: 'existingFilesToModify' round-trips into the design output verbatim")
    void existingFilesToModifyRoundTripsThroughOutput() throws Exception {
        StageAiProvider provider = fixedResponse("{\"design\":\"add a version endpoint\","
                + "\"contractImpact\":\"adds a new path to the existing OpenAPI contract\","
                + "\"existingFilesToModify\":[\"pom.xml\","
                + "\"specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml\"]}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertTrue(out.has("existingFilesToModify"));
        assertEquals(2, out.get("existingFilesToModify").size());
        assertEquals("pom.xml", out.get("existingFilesToModify").get(0).asText());
        assertEquals("specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml",
                out.get("existingFilesToModify").get(1).asText());
    }

    @Test
    @DisplayName("greenfield: an omitted 'existingFilesToModify' still produces the field, empty -- Conductor "
            + "always has a list to read, never a missing key")
    void existingFilesToModifyDefaultsToEmptyArrayWhenOmitted() throws Exception {
        StageAiProvider provider =
                fixedResponse("{\"design\":\"a wholly new controller\",\"contractImpact\":\"none\"}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertTrue(out.has("existingFilesToModify"));
        assertEquals(0, out.get("existingFilesToModify").size());
    }

    @Test
    @DisplayName("CR-057: material design decisions round-trip into the output verbatim, no justification")
    void materialDesignDecisionsRoundTripThroughOutput() throws Exception {
        StageAiProvider provider = fixedResponse("{\"design\":\"add a trusted-partner read path\","
                + "\"contractImpact\":\"new query parameter on an existing endpoint\","
                + "\"materialDesignDecisions\":["
                + "\"Whether trusted-partner status is a new role, a header, or an allowlist changes the "
                + "security posture and is a genuine fork among viable alternatives\","
                + "\"Whether expired links become visible to ALL callers or only trusted ones changes "
                + "observable behaviour for every existing caller\"]}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertTrue(out.has("materialDesignDecisions"));
        assertEquals(2, out.get("materialDesignDecisions").size());
        assertFalse(out.has("nonMaterialityJustification"),
                "a justification is only for the empty-decisions case, never alongside real decisions");
    }

    @Test
    @DisplayName("CR-057: an empty materialDesignDecisions WITH a substantive justification is accepted, "
            + "no gate")
    void emptyMaterialDesignDecisionsWithSubstantiveJustificationIsAccepted() throws Exception {
        StageAiProvider provider = fixedResponse("{\"design\":\"read the version from a new properties "
                + "file via a new controller\",\"contractImpact\":\"none\","
                + "\"materialDesignDecisions\":[],"
                + "\"nonMaterialityJustification\":\"Every choice here is forced by the requirement's own "
                + "text: a new controller, a new resource file, no auth. No viable alternative would "
                + "change required behaviour.\"}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertTrue(out.has("materialDesignDecisions"));
        assertEquals(0, out.get("materialDesignDecisions").size());
        assertTrue(out.has("nonMaterialityJustification"));
    }

    @Test
    @DisplayName("CR-007 safe default: an omitted materialDesignDecisions (older/non-compliant answer) is "
            + "NEVER a hard failure -- it defaults to material, the gate stays safe, the design still "
            + "succeeds")
    void omittedMaterialityClassificationDefaultsToMaterialNotFailure() throws Exception {
        StageAiProvider provider = fixedResponse(
                "{\"design\":\"a wholly new controller\",\"contractImpact\":\"none\"}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertTrue(outcome.succeeded(), "an omitted materiality signal must default to material, not fail "
                + "the whole design stage (CR-007's own uncertainty default)");
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertTrue(out.has("materialDesignDecisions"));
        assertFalse(out.get("materialDesignDecisions").isEmpty(),
                "an omitted classification must default to a NON-EMPTY materialDesignDecisions, so "
                        + "Conductor's own gate check opens the architecture gate rather than silently "
                        + "skipping it");
    }

    @Test
    @DisplayName("CR-007 safe default: an empty materialDesignDecisions with a PLACEHOLDER justification "
            + "(too short) also defaults to material, not a hard failure")
    void placeholderJustificationDefaultsToMaterialNotFailure() throws Exception {
        StageAiProvider provider = fixedResponse("{\"design\":\"a wholly new controller\","
                + "\"contractImpact\":\"none\",\"materialDesignDecisions\":[],"
                + "\"nonMaterialityJustification\":\"none\"}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertFalse(out.get("materialDesignDecisions").isEmpty(),
                "a placeholder justification (CR-007: non-blank is not the same as substantive) must not "
                        + "be trusted to suppress the gate");
    }

    @Test
    @DisplayName("a materialDesignDecisions entry too short to be substantive is dropped, not trusted "
            + "verbatim -- falls through to the same safe default as an empty list")
    void tooShortMaterialDecisionEntryIsDroppedNotTrusted() throws Exception {
        StageAiProvider provider = fixedResponse("{\"design\":\"a wholly new controller\","
                + "\"contractImpact\":\"none\",\"materialDesignDecisions\":[\"x\"]}");

        StageOutcome outcome = new DesignAiExecutor(provider, CLOCK).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        // The bogus "x" entry is dropped as non-substantive, leaving an effectively-empty list with no
        // justification -- the safe default (material) still applies, it just isn't the literal "x".
        assertFalse(out.get("materialDesignDecisions").isEmpty());
        for (JsonNode entry : out.get("materialDesignDecisions")) {
            assertTrue(entry.asText().length() >= 20, "no non-substantive entry should survive into the "
                    + "output: \"" + entry.asText() + "\"");
        }
    }
}
