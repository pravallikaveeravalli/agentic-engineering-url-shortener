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
 * T073e's ONE recorded live demo run (Ruling 2, AS-007). See {@link NormalizationAiExecutorLiveDemo}'s
 * javadoc for why this class is not named {@code *Test}/{@code *IT}, how to run it explicitly, and why it
 * uses the Gemini CLI ({@code agy}) rather than Claude (ADR-004-A3, CR-042).
 *
 * <p><strong>{@link BranchApplier} is a STUB here, not a real git/build implementation</strong> — that
 * production implementation is out of this task's scope (see {@code BranchApplier}'s own javadoc), the same
 * disclosed-backlog shape T073's own class javadoc uses for the SDK transport alternative. This demo
 * therefore proves the REAL half — a real CLI call producing a diff-shaped answer this adapter's own guard
 * accepts — and simulates only the applier's report, exactly as {@code ImplementationAiExecutorTest} does.
 */
class ImplementationAiExecutorLiveDemo {

    @Test
    @DisplayName("AS-007 DEMO: one real Gemini CLI (agy) call through the real prompt/parse/guard chain")
    void oneRealCallThroughTheRealChain() throws Exception {
        var realProvider = new GeminiCliStageAiProvider("agy", "gemini-3.8-flash-high");
        var recording = new RecordingStageAiProvider(realProvider);
        BranchApplier stubApplier = (taskId, patch) ->
                new BranchApplier.ApplyResult(true, "demo/" + taskId, "stub applier: accepted for demo purposes");
        var executor = new ImplementationAiExecutor(recording, stubApplier);

        // A self-contained hypothetical file, given inline rather than by name — agy is itself an agentic
        // coding CLI with real filesystem access in this working directory, and a first attempt naming a
        // REAL file by name (ShortLink.java) returned an empty response, most plausibly because the model
        // went looking at the real file rather than answering the single-turn prompt. Giving the "current
        // file" inline removes that ambiguity without changing what is being asked for.
        StageInput input = new StageInput(UUID.randomUUID(), "S7", 7, 1,
                Map.of(ImplementationAiExecutor.INPUT_TASK_KEY,
                        "{\"taskId\":\"T1\",\"requirementIds\":[\"R1\"]}",
                        ImplementationAiExecutor.INPUT_DESIGN_KEY,
                        "{\"design\":\"Add a MAX_DESTINATION_LENGTH constant (value 2048) to the class "
                                + "below, and enforce it in the constructor by throwing "
                                + "IllegalArgumentException with a message citing FR-URL-003 when the "
                                + "destination field exceeds it. The CURRENT file content, exactly as it "
                                + "exists today, is:\\n\\npackage demo;\\n\\npublic final class Widget {\\n"
                                + "    private final String destination;\\n\\n"
                                + "    public Widget(String destination) {\\n"
                                + "        this.destination = destination;\\n"
                                + "    }\\n"
                                + "}\\n\"}"));

        StageOutcome outcome = executor.execute(input);

        assertTrue(outcome.succeeded(),
                outcome.succeeded() ? "" : "live demo call failed: " + outcome.failure());

        String modelId = recording.last().modelId();
        System.out.println("AS-007 DEMO T073e ImplementationAiExecutor — actually-used model id: " + modelId);
        System.out.println("AS-007 DEMO T073e ImplementationAiExecutor — produced artifact (branch ref): "
                + outcome.producedArtifacts().get(0).content());
    }
}
