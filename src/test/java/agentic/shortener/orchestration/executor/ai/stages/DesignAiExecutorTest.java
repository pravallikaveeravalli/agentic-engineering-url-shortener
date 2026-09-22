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
}
