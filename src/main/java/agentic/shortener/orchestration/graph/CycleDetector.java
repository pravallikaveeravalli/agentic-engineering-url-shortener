package agentic.shortener.orchestration.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Cycle rejection before a topology is committed. Task T075. FR-ORC-002, FR-ORC-019. EC-030.
 *
 * <p><strong>Pre-commit is the requirement, not a preference.</strong> A post-commit check leaves a
 * corrupt graph persisted and then complains about it: the run is already unrunnable, and the record of
 * how it got that way is the thing that was just written. {@link #check} therefore takes a
 * <em>proposed</em> set of edges and answers before anything is stored.
 *
 * <p><strong>The combined topology is what is checked.</strong> Validating the replan alone is the
 * defect this rules out — {@code S12 -> S1} is acyclic by itself and closes the entire workflow into a
 * loop the moment it is added to what already exists.
 *
 * <p><strong>Colour-marking, not a visited set.</strong> A plain visited set reports a diamond as a
 * cycle, and the shipped topology contains one: S8 fans to S9 and S10, which both reach S11. What makes
 * a cycle is a node reachable <em>from itself along the current path</em>, which is what the grey
 * marking below tracks. Iterative rather than recursive, so a long replan chain cannot overflow the
 * stack — a detector that crashed on a large graph would fail open at exactly the wrong moment.
 */
public final class CycleDetector {

    /**
     * @param cyclePath the nodes forming the cycle, empty when acceptable. A refusal that does not say
     *                  what it found leaves the caller to guess at their own replan.
     */
    public record Result(boolean acceptable, List<String> cyclePath) {

        public Result {
            cyclePath = List.copyOf(cyclePath);
        }
    }

    private enum Mark { GREY, BLACK }

    public boolean hasCycle(List<DependencyEdge> edges) {
        return !findCycle(edges).isEmpty();
    }

    /** Checks an existing topology plus a proposed addition, without committing either. */
    public Result check(List<DependencyEdge> existing, List<DependencyEdge> proposed) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(proposed, "proposed");

        List<DependencyEdge> combined = new ArrayList<>(existing);
        combined.addAll(proposed);

        List<String> cycle = findCycle(combined);
        return new Result(cycle.isEmpty(), cycle);
    }

    /**
     * The form the commit path uses.
     *
     * <p>A boolean nobody checks is a comment, so the only way to persist a cyclic graph is to delete
     * this call rather than to forget an {@code if}.
     *
     * @throws IllegalArgumentException naming the cycle it found
     */
    public void requireAcyclic(List<DependencyEdge> existing, List<DependencyEdge> proposed) {
        Result result = check(existing, proposed);
        if (!result.acceptable()) {
            throw new IllegalArgumentException(
                    "the proposed topology introduces a cycle and was not committed (EC-030): "
                            + String.join(" -> ", result.cyclePath()));
        }
    }

    /** @return the nodes on a cycle, or an empty list when the graph is acyclic */
    private List<String> findCycle(List<DependencyEdge> edges) {
        Map<String, List<String>> successors = new HashMap<>();
        Set<String> allNodes = new HashSet<>();
        for (DependencyEdge edge : edges) {
            successors.computeIfAbsent(edge.fromNodeKey(), k -> new ArrayList<>())
                    .add(edge.toNodeKey());
            allNodes.add(edge.fromNodeKey());
            allNodes.add(edge.toNodeKey());
        }

        Map<String, Mark> marks = new HashMap<>();
        // Every node is a start, not just the entry. A replan can leave a detached subgraph, and a walk
        // that began only at S1 would never reach a cycle inside one.
        for (String start : allNodes) {
            if (marks.containsKey(start)) {
                continue;
            }
            List<String> cycle = walk(start, successors, marks);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private List<String> walk(String start, Map<String, List<String>> successors,
                              Map<String, Mark> marks) {
        // An explicit stack of (node, next successor index), so depth is bounded by heap rather than by
        // the JVM's call stack.
        Deque<String> path = new ArrayDeque<>();
        Deque<Integer> cursors = new ArrayDeque<>();

        path.push(start);
        cursors.push(0);
        marks.put(start, Mark.GREY);

        while (!path.isEmpty()) {
            String node = path.peek();
            int cursor = cursors.pop();
            List<String> next = successors.getOrDefault(node, List.of());

            if (cursor < next.size()) {
                cursors.push(cursor + 1);
                String child = next.get(cursor);
                Mark mark = marks.get(child);

                if (mark == Mark.GREY) {
                    // On the CURRENT path, so this closes a cycle. A BLACK child is merely a node
                    // already finished — a diamond, not a loop.
                    List<String> cycle = new ArrayList<>(path);
                    java.util.Collections.reverse(cycle);
                    int from = cycle.indexOf(child);
                    List<String> trimmed = new ArrayList<>(cycle.subList(from, cycle.size()));
                    trimmed.add(child);
                    return trimmed;
                }
                if (mark == null) {
                    marks.put(child, Mark.GREY);
                    path.push(child);
                    cursors.push(0);
                }
            } else {
                marks.put(node, Mark.BLACK);
                path.pop();
            }
        }
        return List.of();
    }
}
