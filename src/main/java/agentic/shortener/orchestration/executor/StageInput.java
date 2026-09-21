package agentic.shortener.orchestration.executor;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything a stage executor is given. Task T069. FR-ORC-028.
 *
 * <p><strong>Workflow data and nothing else.</strong> FR-ORC-028 requires an executor's behaviour to be a
 * function of the data flowing through the workflow rather than of which requirement is being processed, and
 * the way to get that is to hand it nothing it could branch on: no scenario label, no run mode, no flag, no
 * caller identity. {@code StageExecutorContractTest} asserts this component list as a <em>closed</em> set, so
 * a future field naming a scenario fails a test rather than passing review.
 *
 * @param attempt        1-based. Present because a retrying executor may legitimately behave differently — a
 *                       subprocess provider re-reading a cached response, say — and because a stage that
 *                       cannot see its own attempt number cannot record one
 * @param inputArtifacts the upstream artifacts, keyed by artifact key. Immutable: a stage that could mutate
 *                       its input would let one fan-out child change what a sibling reads, which is the
 *                       shared-state corruption FR-ORC-003 forbids
 */
public record StageInput(UUID runId, String nodeKey, int stageNumber, int attempt,
                         Map<String, String> inputArtifacts) {

    public StageInput {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(nodeKey, "nodeKey");
        if (stageNumber < 1 || stageNumber > 12) {
            throw new IllegalArgumentException("stage number must be 1..12, got " + stageNumber);
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("attempts are 1-based, got " + attempt);
        }
        inputArtifacts = Map.copyOf(inputArtifacts);
    }
}
