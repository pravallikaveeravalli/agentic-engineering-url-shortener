package agentic.shortener.orchestration.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T074 and T076 — the static template a run is materialized from. FR-ORC-001, FR-ORC-002, FR-ORC-003.
 *
 * <p><strong>Thirteen nodes on a fresh run</strong>: eleven singletons (S1–S6, S8–S12), the S7 fan-out
 * parent, and its join. Children {@code S7.1..S7.n} are appended when decomposition says how many tasks
 * there are, so they are not in the template — a template that guessed a child count would be asserting
 * a decomposition nobody has made.
 *
 * <p><strong>The topology is declared data, not inferred from call order</strong> (T074's guard). That is
 * what lets a replanned instance's topology differ from the template and still be queryable: the run owns
 * its own edges. A code-only graph makes replan history unreconstructable, because the only record of what
 * the graph was would be the code that no longer runs.
 *
 * <p><strong>A linear chain would satisfy none of this</strong> (T076's guard), so the shape is asserted
 * positively: two fan-out/join points and one conditional. The S9∥S10 pair is expressed as <em>two
 * incoming ALL edges on S11 with no join node</em> — deliberately different from S7's explicit join,
 * because a join node earns its place only when children are dynamic.
 *
 * <p>Fast tier: the template is pure data.
 */
@DisplayName("T074/T076 stage template")
class StageTemplateTest {

    private final StageTemplate template = StageTemplate.standard();

    @Test
    @DisplayName("a fresh run materializes exactly THIRTEEN nodes")
    void thirteenNodes() {
        List<StageNode> nodes = template.nodes();
        assertEquals(13, nodes.size(),
                "eleven singletons + the S7 fan-out parent + its join: " + keysOf(nodes));
    }

    @Test
    @DisplayName("every stage 1–12 appears exactly once as SINGLETON or FAN_OUT_PARENT")
    void everyStageOnce() {
        Map<Integer, List<StageNode>> byStage = template.nodes().stream()
                .filter(n -> n.role() == NodeRole.SINGLETON || n.role() == NodeRole.FAN_OUT_PARENT)
                .collect(Collectors.groupingBy(StageNode::stageNumber));

        for (int stage = 1; stage <= 12; stage++) {
            List<StageNode> found = byStage.get(stage);
            assertEquals(1, found == null ? 0 : found.size(),
                    "stage " + stage + " must appear exactly once as a singleton or fan-out parent");
        }
        assertEquals(12, byStage.size(), "twelve stages, no more and no fewer (FR-ORC-001)");
    }

    @Test
    @DisplayName("node keys are unique and match the contract's pattern")
    void nodeKeysAreUniqueAndWellFormed() {
        Set<String> seen = new HashSet<>();
        for (StageNode node : template.nodes()) {
            assertTrue(seen.add(node.nodeKey()), "duplicate node key: " + node.nodeKey());
            assertTrue(node.nodeKey().matches("^S([1-9]|1[0-2])(\\.([1-9][0-9]*|join))?$"),
                    "node key does not match the contract pattern: " + node.nodeKey());
        }
    }

    @Test
    @DisplayName("the roles are exactly eleven singletons, one fan-out parent, one join")
    void roleCounts() {
        Map<NodeRole, Long> counts = template.nodes().stream()
                .collect(Collectors.groupingBy(StageNode::role, Collectors.counting()));

        assertEquals(11L, counts.getOrDefault(NodeRole.SINGLETON, 0L));
        assertEquals(1L, counts.getOrDefault(NodeRole.FAN_OUT_PARENT, 0L));
        assertEquals(1L, counts.getOrDefault(NodeRole.JOIN, 0L));
        assertEquals(0L, counts.getOrDefault(NodeRole.FAN_OUT_CHILD, 0L),
                "children are appended when decomposition says how many tasks there are; a template "
                        + "that guessed would be asserting a decomposition nobody has made");
    }

    @Test
    @DisplayName("S7 is the fan-out parent and S7.join is its join")
    void s7FansOut() {
        StageNode parent = template.node("S7").orElseThrow();
        StageNode join = template.node("S7.join").orElseThrow();

        assertEquals(NodeRole.FAN_OUT_PARENT, parent.role());
        assertEquals(NodeRole.JOIN, join.role());
        assertEquals(7, join.stageNumber(), "the join belongs to stage 7");
        assertEquals("S7", join.parentNodeKey(), "and names its parent");
        assertTrue(parent.parentNodeKey() == null, "a parent has no parent");
    }

    @Test
    @DisplayName("parentNodeKey is set on JOIN and null on SINGLETON and FAN_OUT_PARENT")
    void parentKeyIsSetOnlyWhereItBelongs() {
        for (StageNode node : template.nodes()) {
            boolean shouldHaveParent = node.role() == NodeRole.JOIN
                    || node.role() == NodeRole.FAN_OUT_CHILD;
            assertEquals(shouldHaveParent, node.parentNodeKey() != null,
                    node.nodeKey() + " (" + node.role() + ") has the wrong parent linkage");
        }
    }

    @Test
    @DisplayName("FAN-OUT/JOIN POINT 1: S7's children gate S8 through an explicit ALL join")
    void s7JoinGatesS8() {
        assertTrue(template.hasEdge("S7", "S7.join"), "the parent feeds its join");
        assertEquals(JoinSemantics.ALL, template.edge("S7", "S7.join").orElseThrow().joinSemantics(),
                "EC-018: a join does not proceed while any required branch is incomplete or failed");
        assertTrue(template.hasEdge("S7.join", "S8"), "and the join gates S8");
        assertFalse(template.hasEdge("S7", "S8"),
                "S7 must NOT reach S8 directly, or the join could be bypassed entirely");
    }

    @Test
    @DisplayName("FAN-OUT/JOIN POINT 2: S9 and S10 run in parallel and meet at S11 with NO join node")
    void s9AndS10MeetAtS11() {
        // Deliberately different from S7's shape. A join node earns its place when the children are
        // DYNAMIC and have to be counted; S9 and S10 are two known nodes, so two incoming ALL edges say
        // the same thing with one less node to keep consistent.
        assertTrue(template.hasEdge("S8", "S9"));
        assertTrue(template.hasEdge("S8", "S10"));
        assertTrue(template.hasEdge("S9", "S11"));
        assertTrue(template.hasEdge("S10", "S11"));

        assertEquals(JoinSemantics.ALL, template.edge("S9", "S11").orElseThrow().joinSemantics());
        assertEquals(JoinSemantics.ALL, template.edge("S10", "S11").orElseThrow().joinSemantics());

        assertTrue(template.node("S9.join").isEmpty(), "no join node for this pair");
        assertTrue(template.node("S10.join").isEmpty());
        assertEquals(2, template.incomingEdges("S11").size(),
                "exactly two incoming edges, which is what makes this a synchronization point");
    }

    @Test
    @DisplayName("CONDITIONAL: S3 reaches S5 both directly and through S4")
    void s3ConditionalBranch() {
        // S4 fires only on material ambiguity. The not-taken state is SKIPPED, which counts as complete
        // for an ALL join — so S5 waits for S4 when it runs and proceeds when it is skipped, without
        // either path needing different edges.
        assertTrue(template.hasEdge("S3", "S4"), "the taken branch");
        assertTrue(template.hasEdge("S3", "S5"), "and the not-taken branch");
        assertTrue(template.hasEdge("S4", "S5"), "with S4 rejoining");

        assertEquals(2, template.incomingEdges("S5").size(),
                "S5 is reached from S3 and from S4");
        assertEquals(JoinSemantics.ALL, template.edge("S3", "S5").orElseThrow().joinSemantics());
        assertEquals(JoinSemantics.ALL, template.edge("S4", "S5").orElseThrow().joinSemantics(),
                "ALL, not ANY: with ANY, S5 could start while a TAKEN S4 was still running");
    }

    @Test
    @DisplayName("the linear spine is present where the workflow really is linear")
    void linearSpine() {
        for (String[] edge : new String[][]{{"S1", "S2"}, {"S2", "S3"}, {"S5", "S6"}, {"S6", "S7"},
                {"S11", "S12"}}) {
            assertTrue(template.hasEdge(edge[0], edge[1]),
                    "missing edge " + edge[0] + " -> " + edge[1]);
        }
    }

    @Test
    @DisplayName("the template has exactly fourteen edges, and they are all accounted for")
    void edgeCount() {
        // Counted explicitly rather than left implicit: an extra edge is a topology change, and a
        // missing one is a stage that never becomes ready.
        assertEquals(14, template.edges().size(), "edges: " + template.edges());
    }

    @Test
    @DisplayName("S1 is the only entry and S12 the only exit")
    void singleEntryAndExit() {
        List<String> noIncoming = template.nodes().stream()
                .map(StageNode::nodeKey)
                .filter(key -> template.incomingEdges(key).isEmpty())
                .toList();
        List<String> noOutgoing = template.nodes().stream()
                .map(StageNode::nodeKey)
                .filter(key -> template.outgoingEdges(key).isEmpty())
                .toList();

        assertEquals(List.of("S1"), noIncoming, "a second entry point would be an unreachable start");
        assertEquals(List.of("S12"), noOutgoing, "a second exit would be a branch that runs into sand");
    }

    @Test
    @DisplayName("every edge names nodes that exist")
    void edgesReferenceRealNodes() {
        Set<String> keys = template.nodes().stream()
                .map(StageNode::nodeKey).collect(Collectors.toSet());
        for (DependencyEdge edge : template.edges()) {
            assertTrue(keys.contains(edge.fromNodeKey()), "dangling from: " + edge);
            assertTrue(keys.contains(edge.toNodeKey()), "dangling to: " + edge);
        }
    }

    @Test
    @DisplayName("the template is immutable — a caller cannot reshape the workflow")
    void templateIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> template.nodes().add(StageNode.singleton("S1", 1, "spurious")));
        assertThrows(UnsupportedOperationException.class,
                () -> template.edges().add(new DependencyEdge("S1", "S12", JoinSemantics.ANY)));
    }

    @Test
    @DisplayName("stageNumber is STORED, not parsed from the key")
    void stageNumberIsStoredNotParsed() {
        // T074's Validate names this explicitly, and it is asserted with a node whose key and column
        // deliberately disagree. Parsing the key would look right for every real node and silently
        // break the first time a replan issued a key that did not encode its stage.
        StageNode disagreeing = StageNode.singleton("S3", 9, "deliberately inconsistent");
        assertEquals(9, disagreeing.stageNumber(),
                "the stored stage number must win; parsing \"S3\" would give 3");
        assertEquals("S3", disagreeing.nodeKey());
    }

    private static List<String> keysOf(List<StageNode> nodes) {
        return nodes.stream().map(StageNode::nodeKey).toList();
    }
}
