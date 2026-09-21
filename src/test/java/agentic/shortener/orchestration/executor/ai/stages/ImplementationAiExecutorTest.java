package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageExecutorContract;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.reliability.FailureCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T073e — S7 implementation AI adapter. FR-ORC-031, FR-ORC-029.
 *
 * <p>Against a fake {@link StageAiProvider} AND a fake {@link BranchApplier} (FR-ORC-030: no live CLI, no
 * real git, no real build inside a unit test) — this task's own Validate clause verbatim: a run where an
 * AI-authored change fails verification and routes back, and a run where one passes, both with
 * executor-kind labels.
 */
@DisplayName("T073e — S7 implementation adapter: AI authors, the applier judges buildability, never text-executed")
class ImplementationAiExecutorTest {

    private static final String VALID_DIFF = "--- a/Foo.java\n+++ b/Foo.java\n@@ -1 +1 @@\n-old\n+new\n";

    private static StageInput input() {
        return new StageInput(UUID.randomUUID(), "S7", 7, 1,
                Map.of(ImplementationAiExecutor.INPUT_TASK_KEY, "{\"taskId\":\"T1\"}",
                        ImplementationAiExecutor.INPUT_DESIGN_KEY, "{\"design\":\"add a limiter\"}"));
    }

    private static StageAiProvider fixedResponse(String content) {
        return prompt -> new AiResponse("claude-test-fixture", content);
    }

    @Nested
    @DisplayName("the StageExecutorContract, applied to ImplementationAiExecutor")
    class Contract extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new ImplementationAiExecutor(fixedResponse(VALID_DIFF),
                    (taskId, patch) -> new BranchApplier.ApplyResult(true, "feature/x", "applied"));
        }

        @Override
        protected StageInput validInput() {
            return input();
        }
    }

    @Test
    @DisplayName("a run where the AI-authored change PASSES verification succeeds, labelled AI")
    void passingChangeSucceeds() {
        StageAiProvider provider = fixedResponse(VALID_DIFF);
        BranchApplier applier = (taskId, patch) -> {
            assertEquals("T1", taskId);
            assertEquals(VALID_DIFF.strip(), patch);
            return new BranchApplier.ApplyResult(true, "feature/T1-impl", "applied and builds");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertTrue(outcome.succeeded());
        assertEquals(ExecutorKind.AI, outcome.executorKind());
        assertEquals("feature/T1-impl", outcome.producedArtifacts().get(0).content());
    }

    @Test
    @DisplayName("a run where the AI-authored change FAILS verification routes back, INVALID_INPUT, permanent")
    void failingChangeRoutesBack() {
        StageAiProvider provider = fixedResponse(VALID_DIFF);
        BranchApplier applier = (taskId, patch) ->
                new BranchApplier.ApplyResult(false, null, "compile error: cannot find symbol Foo");

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INVALID_INPUT, outcome.failure().category());
        assertFalse(outcome.failure().executorProposesRetryable(),
                "plan table: conflict = permanent — the identical patch cannot un-fail by retrying");
        assertTrue(outcome.failure().detail().contains("compile error"),
                "the report must route back with the actual detail, or nobody can act on it");
    }

    @Test
    @DisplayName("NEGATIVE: AI output is never executed as text — an answer that is not a diff is refused")
    void nonDiffAnswerIsRefusedNotApplied() {
        StageAiProvider provider = fixedResponse("Sure! I'll implement this by editing Foo.java to fix it.");
        BranchApplier applier = (taskId, patch) -> {
            throw new AssertionError("must never be called with a non-diff answer");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }

    @Test
    @DisplayName("a markdown-fenced diff is tolerated")
    void markdownFencedDiffIsTolerated() {
        StageAiProvider provider = fixedResponse("```diff\n" + VALID_DIFF + "```");
        BranchApplier applier = (taskId, patch) ->
                new BranchApplier.ApplyResult(true, "feature/T1-impl", "applied and builds");

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertTrue(outcome.succeeded());
    }

    @Test
    @DisplayName("a provider failure translates through the standard translator")
    void providerFailurePropagatesThroughTranslator() {
        StageAiProvider provider = prompt -> {
            throw new java.io.IOException("no such file or directory: claude");
        };
        BranchApplier applier = (taskId, patch) -> {
            throw new AssertionError("must never be called when the provider itself failed");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.UNAVAILABLE, outcome.failure().category());
    }
}
