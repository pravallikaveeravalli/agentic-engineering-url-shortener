package agentic.shortener.orchestration.executor;

import agentic.shortener.orchestration.reliability.FailureEnvelope;

import java.util.List;
import java.util.Objects;

/**
 * What a stage executor reports. Task T069. FR-ORC-028, FR-ORC-029.
 *
 * <p>Two dispositions and no third. A failure is a value here, never an exception: the orchestrator rules on
 * retry from a classified envelope, and an exception escaping an executor arrives with no category — which
 * T084 rules permanent. A thrown failure is therefore a retryable failure silently reclassified, which is why
 * {@link StageExecutor}'s contract makes returning one an obligation.
 *
 * <p>The factory methods are the only way in, so the states the orchestrator would have to reject are not
 * constructible: success with no artifact (EC-029), success claiming {@link ExecutorKind#HUMAN}
 * (FR-ORC-031), or failure with nothing to classify.
 */
public record StageOutcome(boolean succeeded, List<ProducedArtifact> producedArtifacts,
                           ExecutorKind executorKind, FailureEnvelope failure) {

    public StageOutcome {
        producedArtifacts = List.copyOf(producedArtifacts);
    }

    public static StageOutcome succeeded(List<ProducedArtifact> artifacts, ExecutorKind kind) {
        Objects.requireNonNull(artifacts, "artifacts");
        Objects.requireNonNull(kind, "kind");
        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException(
                    "a stage reporting success must produce at least one artifact (EC-029). "
                            + "TransitionRules refuses the RUNNING -> SUCCEEDED transition without one, so "
                            + "an empty success could only ever fail later and further away");
        }
        if (kind == ExecutorKind.HUMAN) {
            throw new IllegalArgumentException(
                    "HUMAN is never selectable by an executor: it is recorded as the outcome of a "
                            + "no-plan-gate decision (FR-ORC-031, T065)");
        }
        return new StageOutcome(true, artifacts, kind, null);
    }

    public static StageOutcome failed(FailureEnvelope failure) {
        // No artifacts and no kind. A failed stage's partial output is work the run must not build on, and
        // labelling the kind of an execution that did not complete would put it in evidence as one that did.
        Objects.requireNonNull(failure,
                "a failure with no envelope is one the orchestrator cannot rule on");
        return new StageOutcome(false, List.of(), null, failure);
    }
}
