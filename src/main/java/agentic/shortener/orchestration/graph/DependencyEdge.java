package agentic.shortener.orchestration.graph;

import java.util.Objects;

/**
 * One declared dependency between two nodes. Task T074. FR-ORC-002, FR-ORC-003.
 *
 * <p><strong>Edges are declared data, never inferred from call order</strong> — T074's guard. Per-run
 * materialization is what lets a replanned instance's topology differ from the template and stay
 * queryable; a code-only graph makes replan history unreconstructable, because the only record of what
 * the graph was would be code that no longer runs.
 *
 * @param joinSemantics {@code ALL} means the target does not proceed while any required source is
 *                      incomplete or failed (EC-018). {@code ANY} means one suffices.
 */
public record DependencyEdge(String fromNodeKey, String toNodeKey, JoinSemantics joinSemantics) {

    public DependencyEdge {
        Objects.requireNonNull(fromNodeKey, "fromNodeKey");
        Objects.requireNonNull(toNodeKey, "toNodeKey");
        Objects.requireNonNull(joinSemantics, "joinSemantics");
        if (fromNodeKey.equals(toNodeKey)) {
            throw new IllegalArgumentException("a node cannot depend on itself: " + fromNodeKey);
        }
    }
}
