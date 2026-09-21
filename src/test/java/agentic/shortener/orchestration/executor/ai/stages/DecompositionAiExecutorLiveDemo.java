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
 * T073c's ONE recorded live demo run (Ruling 2, AS-007). See {@link NormalizationAiExecutorLiveDemo}'s
 * javadoc for why this class is not named {@code *Test}/{@code *IT}, and how to run it explicitly.
 */
class DecompositionAiExecutorLiveDemo {

    @Test
    @DisplayName("AS-007 DEMO: one real Claude Code CLI call through the real prompt/parse/guard chain")
    void oneRealCallThroughTheRealChain() throws Exception {
        var realProvider = new ClaudeCodeCliStageAiProvider("claude", "claude-sonnet-5");
        var recording = new RecordingStageAiProvider(realProvider);
        var executor = new DecompositionAiExecutor(recording);

        StageInput input = new StageInput(UUID.randomUUID(), "S5", 5, 1,
                Map.of(DecompositionAiExecutor.INPUT_KEY,
                        "[{\"externalId\":\"R1\",\"statement\":\"A short link must redirect to its "
                                + "destination within 200 milliseconds.\"},"
                                + "{\"externalId\":\"R2\",\"statement\":\"Every redirect must be recorded "
                                + "with a timestamp for analytics.\"}]"));

        StageOutcome outcome = executor.execute(input);

        assertTrue(outcome.succeeded(),
                outcome.succeeded() ? "" : "live demo call failed: " + outcome.failure());

        String modelId = recording.last().modelId();
        System.out.println("AS-007 DEMO T073c DecompositionAiExecutor — actually-used model id: " + modelId);
        System.out.println("AS-007 DEMO T073c DecompositionAiExecutor — produced artifact: "
                + outcome.producedArtifacts().get(0).content());
    }
}
