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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T073a — S2 normalization AI adapter. FR-ORC-009, FR-ORC-029.
 *
 * <p>Fast tier: a fake {@link StageAiProvider} answers with a recorded response fixture, never a live
 * process — the same discipline {@code ClaudeCodeCliStageAiProviderTest} established for T073 itself,
 * applied here to the parsing and guard logic that is THIS adapter's own contribution (FR-ORC-030: no
 * reliability-relevant proof depends on AI availability).
 */
@DisplayName("T073a — S2 normalization adapter: parse tests and the input-count-preservation guard")
class NormalizationAiExecutorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static StageInput inputWith(String requirementIntake) {
        return new StageInput(UUID.randomUUID(), "S2", 2, 1,
                Map.of(NormalizationAiExecutor.INPUT_KEY, requirementIntake));
    }

    private static StageAiProvider fixedResponse(String json) {
        return prompt -> new AiResponse("claude-test-fixture", json);
    }

    @Nested
    @DisplayName("the StageExecutorContract, applied to NormalizationAiExecutor")
    class Contract extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new NormalizationAiExecutor(fixedResponse(
                    "[{\"externalId\":\"R1\",\"type\":\"FUNCTIONAL\",\"statement\":\"a requirement\"}]"));
        }

        @Override
        protected StageInput validInput() {
            return inputWith("a raw requirement");
        }
    }

    @Test
    @DisplayName("parses a real fixture into RequirementRecords, one input item, one output record")
    void parsesOneItemFixture() throws Exception {
        StageAiProvider provider = fixedResponse(
                "[{\"externalId\":\"R1\",\"type\":\"FUNCTIONAL\",\"statement\":\"Links expire after 7 days\"}]");

        StageOutcome outcome = new NormalizationAiExecutor(provider)
                .execute(inputWith("Links must expire after 7 days"));

        assertTrue(outcome.succeeded());
        assertEquals(1, outcome.producedArtifacts().size());
        JsonNode records = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertEquals(1, records.size());
        assertEquals("R1", records.get(0).get("externalId").asText());
        assertEquals("NORMALIZED", records.get(0).get("status").asText());
    }

    @Test
    @DisplayName("one raw submission normalizing into SEVERAL records is not a violation — MORE is allowed")
    void oneSubmissionMayNormalizeIntoSeveralRecords() {
        // DS-A's own design: one submission normalizes into a functional requirement plus its
        // non-functional latency constraint. The guard is never "one in, one out".
        StageAiProvider provider = fixedResponse("["
                + "{\"externalId\":\"R1\",\"type\":\"FUNCTIONAL\",\"statement\":\"redirect to destination\"},"
                + "{\"externalId\":\"R2\",\"type\":\"NON_FUNCTIONAL\",\"statement\":\"redirect within 50ms\"}]");

        StageOutcome outcome = new NormalizationAiExecutor(provider)
                .execute(inputWith("redirect quickly to the destination"));

        assertTrue(outcome.succeeded());
    }

    @Test
    @DisplayName("NEGATIVE: fewer output records than raw input items is refused, never coerced to empty")
    void fewerRecordsThanInputItemsIsRefused() {
        // Two raw items, blank-line separated; the model answers with only one record — an item was
        // silently discarded, which the guard must refuse rather than accept as "nothing further found".
        StageAiProvider provider = fixedResponse(
                "[{\"externalId\":\"R1\",\"type\":\"FUNCTIONAL\",\"statement\":\"first requirement\"}]");

        StageOutcome outcome = new NormalizationAiExecutor(provider)
                .execute(inputWith("first raw requirement\n\nsecond raw requirement"));

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
        assertFalse(outcome.failure().executorProposesRetryable(),
                "a discarded requirement is a defect in the answer, not a transient condition — retrying "
                        + "the identical prompt cannot un-discard it");
    }

    @Test
    @DisplayName("NEGATIVE: malformed (non-JSON) output is INTERNAL and permanent, never coerced")
    void malformedOutputIsInternalAndPermanent() {
        StageAiProvider provider = fixedResponse("this is not json at all");

        StageOutcome outcome = new NormalizationAiExecutor(provider).execute(inputWith("a requirement"));

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
        assertFalse(outcome.failure().executorProposesRetryable());
    }

    @Test
    @DisplayName("NEGATIVE: an output record with a blank statement is refused, not silently kept")
    void blankStatementIsRefused() {
        StageAiProvider provider = fixedResponse(
                "[{\"externalId\":\"R1\",\"type\":\"FUNCTIONAL\",\"statement\":\"\"}]");

        StageOutcome outcome = new NormalizationAiExecutor(provider).execute(inputWith("a requirement"));

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }

    @Test
    @DisplayName("a markdown-fenced JSON answer is tolerated (models commonly wrap JSON in code fences)")
    void markdownFencedAnswerIsTolerated() {
        StageAiProvider provider = fixedResponse("```json\n"
                + "[{\"externalId\":\"R1\",\"type\":\"FUNCTIONAL\",\"statement\":\"a statement\"}]\n```");

        StageOutcome outcome = new NormalizationAiExecutor(provider).execute(inputWith("a requirement"));

        assertTrue(outcome.succeeded());
    }

    @Test
    @DisplayName("a provider failure (CLI unavailable, say) translates through the standard translator")
    void providerFailurePropagatesThroughTranslator() {
        StageAiProvider provider = prompt -> {
            throw new java.io.IOException("no such file or directory: claude");
        };

        StageOutcome outcome = new NormalizationAiExecutor(provider).execute(inputWith("a requirement"));

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.UNAVAILABLE, outcome.failure().category());
        assertTrue(outcome.failure().executorProposesRetryable());
    }
}
