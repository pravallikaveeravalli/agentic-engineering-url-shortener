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
 * T073f's ONE recorded live demo run (Ruling 2, AS-007). See {@link NormalizationAiExecutorLiveDemo}'s
 * javadoc for why this class is not named {@code *Test}/{@code *IT}, and how to run it explicitly.
 */
class DocumentationAiExecutorLiveDemo {

    @Test
    @DisplayName("AS-007 DEMO: one real Claude Code CLI call through the real prompt/parse/guard chain")
    void oneRealCallThroughTheRealChain() throws Exception {
        var realProvider = new ClaudeCodeCliStageAiProvider("claude", "claude-sonnet-5");
        var recording = new RecordingStageAiProvider(realProvider);
        var executor = new DocumentationAiExecutor(recording);

        StageInput input = new StageInput(UUID.randomUUID(), "S9", 9, 1,
                Map.of(DocumentationAiExecutor.INPUT_CHANGE_KEY,
                        "{\"design\":\"Added MAX_DESTINATION_LENGTH (2048) enforcement to ShortLink's "
                                + "constructor.\"}",
                        DocumentationAiExecutor.INPUT_RESULTS_KEY,
                        "{\"executedBehaviors\":[\"destination exceeding 2048 characters is refused at "
                                + "creation\",\"destination at exactly 2048 characters is accepted\"],"
                                + "\"total\":2,\"failed\":0}"));

        StageOutcome outcome = executor.execute(input);

        assertTrue(outcome.succeeded(),
                outcome.succeeded() ? "" : "live demo call failed: " + outcome.failure());

        String modelId = recording.last().modelId();
        System.out.println("AS-007 DEMO T073f DocumentationAiExecutor — actually-used model id: " + modelId);
        System.out.println("AS-007 DEMO T073f DocumentationAiExecutor — produced artifact: "
                + outcome.producedArtifacts().get(0).content());
    }
}
