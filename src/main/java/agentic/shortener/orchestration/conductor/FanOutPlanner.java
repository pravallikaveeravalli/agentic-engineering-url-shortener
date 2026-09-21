package agentic.shortener.orchestration.conductor;

import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Decides how many children a fan-out parent node (today, only {@code "S7"}) gets, and their node keys.
 * Task T131a.
 *
 * <p>{@link agentic.shortener.orchestration.graph.StageTemplate#standard()}'s own javadoc is explicit that
 * the template cannot guess a child count — "a template that guessed a child count would be asserting a
 * decomposition nobody has made." This is that decision, made once real decomposition content exists,
 * rather than assumed at graph-materialization time.
 *
 * @see Conductor
 */
@FunctionalInterface
public interface FanOutPlanner {

    /**
     * @param artifactsSoFar every artifact produced by the run up to this point, keyed by artifact key —
     *                       in practice this is where a real planner reads S5's {@code "tasks"} output to
     *                       decide how many implementation children the decomposition actually calls for
     * @return the child node keys to create, e.g. {@code ["S7.1", "S7.2"]}. Never empty — a fan-out parent
     *         with zero children would leave nothing for the join to wait on, which {@link Conductor}
     *         does not accept
     */
    List<String> plan(UUID runId, String parentNodeKey, Map<String, String> artifactsSoFar);

    /** The parent itself is the only child — decomposition is not split further for this run. */
    static FanOutPlanner singleChild() {
        return (runId, parentNodeKey, artifacts) -> List.of(parentNodeKey + ".1");
    }
}
