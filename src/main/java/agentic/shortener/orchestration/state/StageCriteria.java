package agentic.shortener.orchestration.state;

import java.util.List;
import java.util.Objects;

/**
 * Entry and exit criteria. Task T078. FR-ORC-006. EC-029.
 *
 * <p>FR-ORC-006 has two halves and they fail differently. Unmet <strong>entry</strong> criteria waste
 * work on a stage that cannot succeed; unmet <strong>exit</strong> criteria report work that was never
 * done. The second is worse, because the run carries on believing it.
 *
 * <p><strong>EC-029 is the exit criterion that matters most</strong>: a stage reporting success with no
 * output artifact must fail. That is the shape of every silently-skipped step — the stage ran, nothing
 * came out, and the workflow moved on.
 */
public final class StageCriteria {

    /**
     * Whether a node whose dependencies are in these states may execute.
     *
     * <p>A node with no dependencies may always start: that is the entry point, not an oversight.
     */
    public boolean mayEnter(List<StageState> dependencyStates) {
        Objects.requireNonNull(dependencyStates, "dependencyStates");
        return dependencyStates.stream().allMatch(StageState::satisfiesJoin);
    }

    /**
     * Whether a node may be marked complete.
     *
     * <p>Two conditions, both necessary: it must actually be running — a blocked stage completing would
     * be one reporting work it never started — and it must have produced something (EC-029).
     */
    public boolean mayComplete(StageState current, int artifactCount) {
        Objects.requireNonNull(current, "current");
        return current == StageState.RUNNING && artifactCount > 0;
    }

    /**
     * Whether a node may be skipped.
     *
     * <p>The one legitimate way to finish with no output, and deliberately separate from
     * {@link #mayComplete}: conflating them is how a silently-skipped stage gets recorded as a
     * successful one. A stage that has already started has done something, and discarding that is not
     * skipping.
     */
    public boolean maySkip(StageState current) {
        Objects.requireNonNull(current, "current");
        return current == StageState.BLOCKED || current == StageState.READY;
    }
}
