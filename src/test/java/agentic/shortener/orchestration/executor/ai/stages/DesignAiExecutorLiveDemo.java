package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.GeminiCliStageAiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T073d's ONE recorded live demo run (Ruling 2, AS-007). See {@link NormalizationAiExecutorLiveDemo}'s
 * javadoc for why this class is not named {@code *Test}/{@code *IT}, how to run it explicitly, and why it
 * uses the Gemini CLI ({@code agy}) rather than Claude (ADR-004-A3, CR-042).
 */
class DesignAiExecutorLiveDemo {

    @Test
    @DisplayName("AS-007 DEMO: one real Gemini CLI (agy) call through the real prompt/parse/guard chain")
    void oneRealCallThroughTheRealChain() throws Exception {
        var realProvider = new GeminiCliStageAiProvider("agy", "gemini-3.8-flash-high");
        var recording = new RecordingStageAiProvider(realProvider);
        var executor = new DesignAiExecutor(recording, Clock.systemUTC());

        StageInput input = new StageInput(UUID.randomUUID(), "S6", 6, 1,
                Map.of(DesignAiExecutor.INPUT_KEY,
                        "[{\"taskId\":\"T1\",\"requirementIds\":[\"R1\"]}] — implement a redirect latency "
                                + "budget check in the existing RedirectController class."));

        StageOutcome outcome = executor.execute(input);

        assertTrue(outcome.succeeded(),
                outcome.succeeded() ? "" : "live demo call failed: " + outcome.failure());

        String modelId = recording.last().modelId();
        System.out.println("AS-007 DEMO T073d DesignAiExecutor — actually-used model id: " + modelId);
        System.out.println("AS-007 DEMO T073d DesignAiExecutor — produced artifact: "
                + outcome.producedArtifacts().get(0).content());
    }
}
