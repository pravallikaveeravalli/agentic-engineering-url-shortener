package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.GeminiCliStageAiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T073a's ONE recorded live demo run (Ruling 2, AS-007). NOT named {@code *Test} or {@code *IT} on purpose
 * — surefire and failsafe's default include patterns both skip this class, so {@code scripts/ci.sh} and
 * routine {@code mvn test}/{@code verify} never spend quota on it. Run explicitly and only when a fresh
 * demonstration is wanted:
 *
 * <pre>./mvnw -q -Dtest=NormalizationAiExecutorLiveDemo -DfailIfNoTests=false test</pre>
 *
 * <p>This is a DEMONSTRATION execution, not a reliability proof — FR-ORC-030 still holds: every other test
 * of this adapter's parsing and guards runs against a fake provider ({@code NormalizationAiExecutorTest}).
 * This is the one place a real subprocess call to a real, installed CLI happens for this adapter, and its
 * only job is to prove the wiring — prompt to real model to real parse — actually works. Uses the Gemini
 * CLI ({@code agy}), not the Claude CLI: ADR-004-A3 (CR-042) — a Claude Code build agent cannot spawn
 * {@code claude} as a nested subprocess, but can spawn {@code agy}.
 */
class NormalizationAiExecutorLiveDemo {

    @Test
    @DisplayName("AS-007 DEMO: one real Gemini CLI (agy) call through the real prompt/parse/guard chain")
    void oneRealCallThroughTheRealChain() throws Exception {
        var realProvider = new GeminiCliStageAiProvider("agy", "gemini-3.8-flash-high");
        var recording = new RecordingStageAiProvider(realProvider);
        var executor = new NormalizationAiExecutor(recording);

        StageInput input = new StageInput(UUID.randomUUID(), "S2", 2, 1,
                Map.of(NormalizationAiExecutor.INPUT_KEY,
                        "A short link must redirect to its destination within 200 milliseconds."));

        StageOutcome outcome = executor.execute(input);

        assertTrue(outcome.succeeded(),
                outcome.succeeded() ? "" : "live demo call failed: " + outcome.failure());

        String modelId = recording.last().modelId();
        System.out.println("AS-007 DEMO T073a NormalizationAiExecutor — actually-used model id: " + modelId);
        System.out.println("AS-007 DEMO T073a NormalizationAiExecutor — produced artifact: "
                + outcome.producedArtifacts().get(0).content());
    }
}
