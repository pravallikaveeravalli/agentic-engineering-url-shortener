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
 * Task T073c — S5 decomposition AI adapter. FR-ORC-012, FR-ORC-029.
 *
 * <p>Against a fake {@link StageAiProvider} answering a recorded fixture (FR-ORC-030): orphan-task
 * rejection and the no-invented-scope check, Constitution I's own "decomposition MUST NOT invent scope".
 */
@DisplayName("T073c — S5 decomposition adapter: orphan-task and no-invented-scope guards")
class DecompositionAiExecutorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REQUIREMENTS = "[{\"externalId\":\"R1\",\"statement\":\"a\"},"
            + "{\"externalId\":\"R2\",\"statement\":\"b\"}]";

    private static StageInput input() {
        return new StageInput(UUID.randomUUID(), "S5", 5, 1,
                Map.of(DecompositionAiExecutor.INPUT_KEY, REQUIREMENTS));
    }

    private static StageAiProvider fixedResponse(String json) {
        return prompt -> new AiResponse("claude-test-fixture", json);
    }

    @Nested
    @DisplayName("the StageExecutorContract, applied to DecompositionAiExecutor")
    class Contract extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new DecompositionAiExecutor(fixedResponse(
                    "[{\"taskId\":\"T1\",\"requirementIds\":[\"R1\"],\"dependsOn\":[]}]"));
        }

        @Override
        protected StageInput validInput() {
            return input();
        }
    }

    @Test
    @DisplayName("parses dependency-ordered tasks, each tracing to a real requirement")
    void parsesDependencyOrderedTasks() throws Exception {
        StageAiProvider provider = fixedResponse("["
                + "{\"taskId\":\"T1\",\"requirementIds\":[\"R1\"],\"dependsOn\":[]},"
                + "{\"taskId\":\"T2\",\"requirementIds\":[\"R2\"],\"dependsOn\":[\"T1\"]}]");

        StageOutcome outcome = new DecompositionAiExecutor(provider).execute(input());

        assertTrue(outcome.succeeded());
        JsonNode tasks = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertEquals(2, tasks.size());
        assertEquals("T1", tasks.get(1).get("dependsOn").get(0).asText());
    }

    @Test
    @DisplayName("a task tracing to MULTIPLE requirements is fine — the rule is >=1, not ==1")
    void multipleRequirementsPerTaskIsFine() {
        StageAiProvider provider = fixedResponse(
                "[{\"taskId\":\"T1\",\"requirementIds\":[\"R1\",\"R2\"],\"dependsOn\":[]}]");

        StageOutcome outcome = new DecompositionAiExecutor(provider).execute(input());

        assertTrue(outcome.succeeded());
    }

    @Test
    @DisplayName("NEGATIVE: ORPHAN — a task with ZERO requirement references is refused, not stored")
    void orphanTaskIsRefused() {
        StageAiProvider provider = fixedResponse(
                "[{\"taskId\":\"T1\",\"requirementIds\":[],\"dependsOn\":[]}]");

        StageOutcome outcome = new DecompositionAiExecutor(provider).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
        assertFalse(outcome.failure().executorProposesRetryable());
    }

    @Test
    @DisplayName("NEGATIVE: INVENTED SCOPE — a task referencing a requirement id NOT in the input is refused")
    void invalidScopeIsRefused() {
        StageAiProvider provider = fixedResponse(
                "[{\"taskId\":\"T1\",\"requirementIds\":[\"R99-invented\"],\"dependsOn\":[]}]");

        StageOutcome outcome = new DecompositionAiExecutor(provider).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
        assertTrue(outcome.failure().detail().contains("R99-invented"));
    }

    @Test
    @DisplayName("NEGATIVE: a dependency on a task id outside this batch is refused")
    void dependencyOnUnknownTaskIsRefused() {
        StageAiProvider provider = fixedResponse(
                "[{\"taskId\":\"T1\",\"requirementIds\":[\"R1\"],\"dependsOn\":[\"T99-nonexistent\"]}]");

        StageOutcome outcome = new DecompositionAiExecutor(provider).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }

    @Test
    @DisplayName("NEGATIVE: an empty task array is refused")
    void emptyTaskArrayIsRefused() {
        StageAiProvider provider = fixedResponse("[]");

        StageOutcome outcome = new DecompositionAiExecutor(provider).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }
}
