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
 * Task T073f — S9 documentation AI adapter. FR-ORC-029, Constitution X.
 *
 * <p>Against a fake {@link StageAiProvider} answering a recorded fixture (FR-ORC-030): the drift guard —
 * a documented behaviour with no corresponding executed test is rejected.
 */
@DisplayName("T073f — S9 documentation adapter: the drift guard, applied at authorship")
class DocumentationAiExecutorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RESULTS =
            "{\"executedBehaviors\":[\"redirect within budget\",\"analytics recorded\"],\"total\":2,\"failed\":0}";

    private static StageInput input() {
        return new StageInput(UUID.randomUUID(), "S9", 9, 1,
                Map.of(DocumentationAiExecutor.INPUT_CHANGE_KEY, "{\"branch\":\"feature/T1-impl\"}",
                        DocumentationAiExecutor.INPUT_RESULTS_KEY, RESULTS));
    }

    private static StageAiProvider fixedResponse(String json) {
        return prompt -> new AiResponse("claude-test-fixture", json);
    }

    @Nested
    @DisplayName("the StageExecutorContract, applied to DocumentationAiExecutor")
    class Contract extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new DocumentationAiExecutor(fixedResponse(
                    "{\"documentation\":\"redirects complete within budget\","
                            + "\"behaviorsDescribed\":[\"redirect within budget\"]}"));
        }

        @Override
        protected StageInput validInput() {
            return input();
        }
    }

    @Test
    @DisplayName("documentation describing only EXECUTED behaviours is accepted")
    void describingOnlyExecutedBehavioursIsAccepted() throws Exception {
        StageAiProvider provider = fixedResponse("{\"documentation\":\"redirects now complete within the "
                + "latency budget, and every follow is recorded to analytics\","
                + "\"behaviorsDescribed\":[\"redirect within budget\",\"analytics recorded\"]}");

        StageOutcome outcome = new DocumentationAiExecutor(provider).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode out = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertEquals(2, out.get("behaviorsDescribed").size());
    }

    @Test
    @DisplayName("documenting a SUBSET of executed behaviours is fine — not every test needs a doc line")
    void describingASubsetIsFine() {
        StageAiProvider provider = fixedResponse(
                "{\"documentation\":\"redirects now complete within budget\","
                        + "\"behaviorsDescribed\":[\"redirect within budget\"]}");

        StageOutcome outcome = new DocumentationAiExecutor(provider).execute(input());

        assertTrue(outcome.succeeded());
    }

    @Test
    @DisplayName("NEGATIVE: DRIFT — a documented behaviour with NO corresponding executed test is rejected")
    void undeliveredBehaviourIsRejected() {
        StageAiProvider provider = fixedResponse("{\"documentation\":\"the link now supports custom vanity "
                + "codes chosen by the creator\","
                + "\"behaviorsDescribed\":[\"redirect within budget\",\"custom vanity codes\"]}");

        StageOutcome outcome = new DocumentationAiExecutor(provider).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
        assertFalse(outcome.failure().executorProposesRetryable());
        assertTrue(outcome.failure().detail().contains("custom vanity codes"));
    }

    @Test
    @DisplayName("NEGATIVE: malformed output is INTERNAL and permanent")
    void malformedOutputIsInternalAndPermanent() {
        StageAiProvider provider = fixedResponse("not json");

        StageOutcome outcome = new DocumentationAiExecutor(provider).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }

    @Test
    @DisplayName("CR-066: the prompt shows the real executed behaviours as their own explicit, prominent, "
            + "verbatim-only allow-list -- not buried inside the raw results JSON")
    void promptShowsExplicitVerbatimAllowList() {
        java.util.concurrent.atomic.AtomicReference<String> capturedPrompt = new java.util.concurrent.atomic
                .AtomicReference<>();
        StageAiProvider provider = prompt -> {
            capturedPrompt.set(prompt);
            return new AiResponse("claude-test-fixture",
                    "{\"documentation\":\"x\",\"behaviorsDescribed\":[\"redirect within budget\"]}");
        };

        StageOutcome outcome = new DocumentationAiExecutor(provider).execute(input());

        assertTrue(outcome.succeeded());
        String prompt = capturedPrompt.get();
        assertTrue(prompt.contains("- redirect within budget"),
                "each real behaviour must appear as its own explicit, bulleted allow-list entry");
        assertTrue(prompt.contains("- analytics recorded"));
        assertTrue(prompt.toLowerCase().contains("verbatim") || prompt.toLowerCase().contains("copied exactly")
                        || prompt.toLowerCase().contains("copy exactly"),
                "the prompt must instruct verbatim, copy-paste selection, not paraphrase-from-context");
    }
}
