package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.ClaudeCodeCliStageAiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T073e's ONE recorded live demo run (Ruling 2, AS-007). See {@link NormalizationAiExecutorLiveDemo}'s
 * javadoc for why this class is not named {@code *Test}/{@code *IT}, and how to run it explicitly.
 *
 * <p><strong>{@link BranchApplier} is a STUB here, not a real git/build implementation</strong> — that
 * production implementation is out of this task's scope (see {@code BranchApplier}'s own javadoc), the same
 * disclosed-backlog shape T073's own class javadoc uses for the SDK transport alternative. This demo
 * therefore proves the REAL half — a real CLI call producing a diff-shaped answer this adapter's own guard
 * accepts — and simulates only the applier's report, exactly as {@code ImplementationAiExecutorTest} does.
 */
class ImplementationAiExecutorLiveDemo {

    @Test
    @DisplayName("AS-007 DEMO: one real Claude Code CLI call through the real prompt/parse/guard chain")
    void oneRealCallThroughTheRealChain() throws Exception {
        var realProvider = new ClaudeCodeCliStageAiProvider("claude", "claude-sonnet-5");
        var recording = new RecordingStageAiProvider(realProvider);
        BranchApplier stubApplier = (taskId, patch) ->
                new BranchApplier.ApplyResult(true, "demo/" + taskId, "stub applier: accepted for demo purposes");
        var executor = new ImplementationAiExecutor(recording, stubApplier);

        StageInput input = new StageInput(UUID.randomUUID(), "S7", 7, 1,
                Map.of(ImplementationAiExecutor.INPUT_TASK_KEY,
                        "{\"taskId\":\"T1\",\"requirementIds\":[\"R1\"]}",
                        ImplementationAiExecutor.INPUT_DESIGN_KEY,
                        "{\"design\":\"Add a MAX_DESTINATION_LENGTH constant of 2048 to ShortLink and "
                                + "enforce it in the constructor, throwing IllegalArgumentException with a "
                                + "message citing FR-URL-003 when exceeded.\"}"));

        StageOutcome outcome = executor.execute(input);

        assertTrue(outcome.succeeded(),
                outcome.succeeded() ? "" : "live demo call failed: " + outcome.failure());

        String modelId = recording.last().modelId();
        System.out.println("AS-007 DEMO T073e ImplementationAiExecutor — actually-used model id: " + modelId);
        System.out.println("AS-007 DEMO T073e ImplementationAiExecutor — produced artifact (branch ref): "
                + outcome.producedArtifacts().get(0).content());
    }
}
