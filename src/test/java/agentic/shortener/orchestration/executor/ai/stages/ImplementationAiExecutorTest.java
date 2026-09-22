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
 * executor-kind labels. CR-060: the model's own answer is now a JSON change set (CREATE full-content, or
 * EDIT search/replace pairs) rather than a unified diff — see {@link ImplementationAiExecutor}'s own javadoc
 * for why.
 */
@DisplayName("T073e — S7 implementation adapter: AI authors, the applier judges buildability, never text-executed")
class ImplementationAiExecutorTest {

    private static final String VALID_CHANGE_SET =
            "{\"files\":[{\"path\":\"Foo.java\",\"action\":\"CREATE\",\"content\":\"class Foo {}\"}]}";

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
            return new ImplementationAiExecutor(fixedResponse(VALID_CHANGE_SET),
                    (taskId, changeSet) -> new BranchApplier.ApplyResult(true, "feature/x", "applied"));
        }

        @Override
        protected StageInput validInput() {
            return input();
        }
    }

    @Test
    @DisplayName("a run where the AI-authored change PASSES verification succeeds, labelled AI")
    void passingChangeSucceeds() {
        StageAiProvider provider = fixedResponse(VALID_CHANGE_SET);
        BranchApplier applier = (taskId, changeSet) -> {
            assertEquals("T1", taskId);
            assertEquals(VALID_CHANGE_SET.strip(), changeSet);
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
        StageAiProvider provider = fixedResponse(VALID_CHANGE_SET);
        BranchApplier applier = (taskId, changeSet) ->
                new BranchApplier.ApplyResult(false, null, "compile error: cannot find symbol Foo");

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INVALID_INPUT, outcome.failure().category());
        assertFalse(outcome.failure().executorProposesRetryable(),
                "plan table: conflict = permanent — the identical change set cannot un-fail by retrying");
        assertTrue(outcome.failure().detail().contains("compile error"),
                "the report must route back with the actual detail, or nobody can act on it");
    }

    @Test
    @DisplayName("NEGATIVE: AI output is never executed as text — a non-JSON answer is refused, not applied")
    void nonJsonAnswerIsRefusedNotApplied() {
        StageAiProvider provider = fixedResponse("Sure! I'll implement this by editing Foo.java to fix it.");
        BranchApplier applier = (taskId, changeSet) -> {
            throw new AssertionError("must never be called with a non-JSON answer");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }

    @Test
    @DisplayName("NEGATIVE: a change set naming an unrecognized action is refused")
    void unrecognizedActionIsRefused() {
        StageAiProvider provider = fixedResponse(
                "{\"files\":[{\"path\":\"Foo.java\",\"action\":\"DELETE\"}]}");
        BranchApplier applier = (taskId, changeSet) -> {
            throw new AssertionError("must never be called with an unrecognized action");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }

    @Test
    @DisplayName("NEGATIVE: an EDIT entry missing 'edits' is refused")
    void editMissingEditsArrayIsRefused() {
        StageAiProvider provider = fixedResponse("{\"files\":[{\"path\":\"pom.xml\",\"action\":\"EDIT\"}]}");
        BranchApplier applier = (taskId, changeSet) -> {
            throw new AssertionError("must never be called with a malformed EDIT entry");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }

    @Test
    @DisplayName("a markdown-fenced change set is tolerated")
    void markdownFencedChangeSetIsTolerated() {
        StageAiProvider provider = fixedResponse("```json\n" + VALID_CHANGE_SET + "\n```");
        BranchApplier applier = (taskId, changeSet) ->
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
        BranchApplier applier = (taskId, changeSet) -> {
            throw new AssertionError("must never be called when the provider itself failed");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.UNAVAILABLE, outcome.failure().category());
    }

    @Test
    @DisplayName("S7 existing-file fix: pre-fetched existing-file content, when present, reaches the prompt "
            + "exactly")
    void existingFileContentIsIncludedInPromptWhenPresent() {
        java.util.concurrent.atomic.AtomicReference<String> capturedPrompt = new java.util.concurrent.atomic
                .AtomicReference<>();
        StageAiProvider provider = prompt -> {
            capturedPrompt.set(prompt);
            return new AiResponse("claude-test-fixture", VALID_CHANGE_SET);
        };
        BranchApplier applier = (taskId, changeSet) ->
                new BranchApplier.ApplyResult(true, "feature/T1-impl", "applied and builds");

        StageInput input = new StageInput(UUID.randomUUID(), "S7", 7, 1,
                Map.of(ImplementationAiExecutor.INPUT_TASK_KEY, "{\"taskId\":\"T1\"}",
                        ImplementationAiExecutor.INPUT_DESIGN_KEY, "{\"design\":\"add build-info to pom.xml\"}",
                        ImplementationAiExecutor.INPUT_EXISTING_FILES_KEY,
                        "{\"pom.xml\":\"<project>REAL-CURRENT-CONTENT</project>\"}"));

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input);

        assertTrue(outcome.succeeded());
        assertTrue(capturedPrompt.get().contains("REAL-CURRENT-CONTENT"),
                "the model must be shown the real, current content Conductor pre-fetched, so its EDIT "
                        + "search strings can target real content instead of guessing");
        assertTrue(capturedPrompt.get().contains("already read the following existing files"),
                "the prompt must specifically tell the model which files it was already shown");
    }

    @Test
    @DisplayName("REGRESSION: no 'existingFiles' input key at all -- prompt carries no existing-file block, "
            + "new-file creation is byte-for-byte unaffected by this fix")
    void absentExistingFilesKeyLeavesPromptUnchanged() {
        java.util.concurrent.atomic.AtomicReference<String> capturedPrompt = new java.util.concurrent.atomic
                .AtomicReference<>();
        StageAiProvider provider = prompt -> {
            capturedPrompt.set(prompt);
            return new AiResponse("claude-test-fixture", VALID_CHANGE_SET);
        };
        BranchApplier applier = (taskId, changeSet) ->
                new BranchApplier.ApplyResult(true, "feature/T1-impl", "applied and builds");

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertTrue(outcome.succeeded());
        assertFalse(capturedPrompt.get().contains("already read the following existing files"),
                "input() carries no 'existingFiles' key -- exactly the shape every S7 dispatch had before "
                        + "this fix -- so the prompt must contain no existing-file block at all");
    }

    @Test
    @DisplayName("CR-066: a malformed FIRST answer gets exactly one bounded self-correction retry, and a "
            + "valid SECOND answer succeeds")
    void malformedFirstAnswerSelfCorrectsOnRetry() {
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger();
        StageAiProvider provider = prompt -> {
            int call = callCount.incrementAndGet();
            if (call == 1) {
                return new AiResponse("claude-test-fixture", "{\"files\": [ this is not valid JSON");
            }
            assertTrue(prompt.contains("Your previous answer could not be used"),
                    "the retry prompt must show the model what went wrong");
            assertTrue(prompt.contains("this is not valid JSON"),
                    "the retry prompt must show the model its own prior bad answer");
            return new AiResponse("claude-test-fixture", VALID_CHANGE_SET);
        };
        BranchApplier applier = (taskId, changeSet) -> {
            assertEquals(VALID_CHANGE_SET, changeSet, "the applier must receive the CORRECTED change set, "
                    + "not the original malformed one");
            return new BranchApplier.ApplyResult(true, "feature/T1-impl", "applied and builds");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertTrue(outcome.succeeded(), "a malformed first answer must not fail the whole stage when a "
                + "bounded self-correction retry would have fixed it");
        assertEquals(2, callCount.get(), "exactly one retry -- not zero, not open-ended");
    }

    @Test
    @DisplayName("CR-066 NEGATIVE: TWO malformed answers in a row still fail the stage -- the retry is "
            + "bounded, not open-ended")
    void twoMalformedAnswersInARowStillFails() {
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger();
        StageAiProvider provider = prompt -> {
            callCount.incrementAndGet();
            return new AiResponse("claude-test-fixture", "still not valid JSON either time");
        };
        BranchApplier applier = (taskId, changeSet) -> {
            throw new AssertionError("must never be called -- neither answer was ever usable");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
        assertEquals(2, callCount.get(), "exactly two calls total -- the original plus ONE retry, never a "
                + "third");
    }

    @Test
    @DisplayName("CR-066: a fenced block NOT at the very start of the answer is still found and used")
    void fencedBlockNotAtStartIsStillExtracted() {
        StageAiProvider provider = fixedResponse(
                "Sure, here is the change:\n\n```json\n" + VALID_CHANGE_SET + "\n```\n\nLet me know if you "
                        + "need anything else!");
        BranchApplier applier = (taskId, changeSet) -> {
            assertEquals(VALID_CHANGE_SET, changeSet);
            return new BranchApplier.ApplyResult(true, "feature/T1-impl", "applied and builds");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertTrue(outcome.succeeded());
    }

    @Test
    @DisplayName("CR-066: JSON surrounded by stray prose with NO fence at all is still found and used")
    void jsonSurroundedByProseWithNoFenceIsStillExtracted() {
        StageAiProvider provider = fixedResponse(
                "Here is my answer: " + VALID_CHANGE_SET + " -- that should do it.");
        BranchApplier applier = (taskId, changeSet) -> {
            assertEquals(VALID_CHANGE_SET, changeSet);
            return new BranchApplier.ApplyResult(true, "feature/T1-impl", "applied and builds");
        };

        StageOutcome outcome = new ImplementationAiExecutor(provider, applier).execute(input());

        assertTrue(outcome.succeeded());
    }
}
