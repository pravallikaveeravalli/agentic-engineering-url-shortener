package agentic.shortener.orchestration.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The static topology a run is materialized from. Tasks T074, T076. FR-ORC-001, FR-ORC-002, FR-ORC-003.
 *
 * <p><strong>Thirteen nodes on a fresh run.</strong> Eleven singletons (S1–S6, S8–S12), the S7 fan-out
 * parent, and its join. Children {@code S7.1..S7.n} are <em>appended</em> once decomposition says how
 * many tasks there are — a template that guessed a child count would be asserting a decomposition nobody
 * has made.
 *
 * <p><strong>Two fan-out/join points, expressed differently on purpose.</strong>
 *
 * <ul>
 *   <li><strong>S7</strong> has an explicit {@code S7.join}, because its children are dynamic. Something
 *       has to hold the "all of them" condition when nobody knows yet how many "all" is.
 *   <li><strong>S9 ∥ S10</strong> meet at S11 as <em>two incoming {@code ALL} edges with no join
 *       node</em>. Both children are known, so a join node would be a third thing to keep consistent
 *       that says exactly what the two edges already say.
 * </ul>
 *
 * <p><strong>The S3 conditional.</strong> S4 fires only on material ambiguity, and the not-taken state is
 * {@code SKIPPED}. Both S3→S5 and S4→S5 are {@code ALL}, which works in both directions because
 * {@code SKIPPED} counts as complete: S5 waits for S4 when it runs and proceeds when it is skipped,
 * without either path needing different edges. {@code ANY} would be wrong — it would let S5 start while
 * a <em>taken</em> S4 was still running.
 *
 * <p>Pure data. A linear chain would satisfy none of FR-ORC-003, so the shape is asserted positively.
 */
public final class StageTemplate {

    private final List<StageNode> nodes;
    private final List<DependencyEdge> edges;
    private final Map<String, StageNode> byKey;

    private StageTemplate(List<StageNode> nodes, List<DependencyEdge> edges) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        Map<String, StageNode> index = new LinkedHashMap<>();
        for (StageNode node : this.nodes) {
            if (index.put(node.nodeKey(), node) != null) {
                throw new IllegalArgumentException("duplicate node key: " + node.nodeKey());
            }
        }
        this.byKey = Map.copyOf(index);
    }

    /** The twelve-stage lifecycle of FR-ORC-001, as plan §3 draws it. */
    public static StageTemplate standard() {
        List<StageNode> nodes = List.of(
                StageNode.singleton("S1", 1, "Ingestion"),
                StageNode.singleton("S2", 2, "Normalization"),
                StageNode.singleton("S3", 3, "Ambiguity detection"),
                StageNode.singleton("S4", 4, "Human clarification"),
                StageNode.singleton("S5", 5, "Decomposition"),
                StageNode.singleton("S6", 6, "Architecture and design"),
                StageNode.fanOutParent("S7", 7, "Implementation"),
                StageNode.join("S7.join", 7, "S7", "Implementation join"),
                StageNode.singleton("S8", 8, "Testing"),
                StageNode.singleton("S9", 9, "Documentation"),
                StageNode.singleton("S10", 10, "Security and policy"),
                StageNode.singleton("S11", 11, "Release readiness"),
                StageNode.singleton("S12", 12, "Final engineering summary"));

        List<DependencyEdge> edges = List.of(
                all("S1", "S2"),
                all("S2", "S3"),
                // The conditional. S4 runs only on material ambiguity; SKIPPED counts as complete.
                all("S3", "S4"),
                all("S3", "S5"),
                all("S4", "S5"),
                all("S5", "S6"),
                all("S6", "S7"),
                // Fan-out point 1: children are appended between S7 and S7.join at replan time.
                all("S7", "S7.join"),
                all("S7.join", "S8"),
                // Fan-out point 2: a known pair, so two ALL edges rather than a join node.
                all("S8", "S9"),
                all("S8", "S10"),
                all("S9", "S11"),
                all("S10", "S11"),
                all("S11", "S12"));

        return new StageTemplate(nodes, edges);
    }

    private static DependencyEdge all(String from, String to) {
        return new DependencyEdge(from, to, JoinSemantics.ALL);
    }

    public List<StageNode> nodes() {
        return nodes;
    }

    public List<DependencyEdge> edges() {
        return edges;
    }

    public Optional<StageNode> node(String nodeKey) {
        return Optional.ofNullable(byKey.get(Objects.requireNonNull(nodeKey, "nodeKey")));
    }

    public boolean hasEdge(String from, String to) {
        return edge(from, to).isPresent();
    }

    public Optional<DependencyEdge> edge(String from, String to) {
        return edges.stream()
                .filter(e -> e.fromNodeKey().equals(from) && e.toNodeKey().equals(to))
                .findFirst();
    }

    public List<DependencyEdge> incomingEdges(String nodeKey) {
        return edges.stream().filter(e -> e.toNodeKey().equals(nodeKey)).toList();
    }

    public List<DependencyEdge> outgoingEdges(String nodeKey) {
        return edges.stream().filter(e -> e.fromNodeKey().equals(nodeKey)).toList();
    }
}
