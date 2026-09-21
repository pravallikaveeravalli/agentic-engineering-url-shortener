package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;

import java.util.Objects;

/**
 * Wraps a real {@link StageAiProvider} and remembers its last response, so a live demo test can exercise an
 * adapter's REAL prompt/parse/guard chain end to end against the real CLI while still recovering the
 * actually-used model id afterward — {@link agentic.shortener.orchestration.executor.StageOutcome} carries
 * no model id of its own (FR-ORC-029's evidence is recorded separately, in the demo run's own file, not in
 * workflow state). Used only by the six T073a-f live-demo classes.
 */
final class RecordingStageAiProvider implements StageAiProvider {

    private final StageAiProvider delegate;
    private volatile AiResponse last;

    RecordingStageAiProvider(StageAiProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public AiResponse invoke(String prompt) throws Exception {
        AiResponse response = delegate.invoke(prompt);
        this.last = response;
        return response;
    }

    /** @throws IllegalStateException if no call has completed yet */
    AiResponse last() {
        if (last == null) {
            throw new IllegalStateException("no response recorded yet — invoke() has not completed");
        }
        return last;
    }
}
