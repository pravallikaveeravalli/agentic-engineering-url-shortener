package agentic.shortener.orchestration.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T075 — cycle detection before commit. FR-ORC-002, FR-ORC-019. EC-030.
 *
 * <p><strong>Pre-commit is the whole requirement.</strong> A post-commit check leaves a corrupt graph
 * persisted and then complains about it — the run is already unrunnable, and the record of how it got
 * that way is the thing that was just written. So the detector takes a <em>proposed</em> topology and
 * answers before anything is stored.
 *
 * <p>The cycle that matters is in the <strong>instance</strong> topology, not the template's. The
 * template is a constant and is trivially acyclic; what can introduce a cycle is a replan adding edges
 * to a live run — which is exactly EC-030.
 *
 * <p>Fast tier: pure graph arithmetic.
 */
@DisplayName("T075 cycle detection")
class CycleDetectorTest {

    private final CycleDetector detector = new CycleDetector();

    private static List<DependencyEdge> edges(String... pairs) {
        List<DependencyEdge> built = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            built.add(new DependencyEdge(pairs[i], pairs[i + 1], JoinSemantics.ALL));
        }
        return built;
    }

    @Test
    @DisplayName("the standard template is acyclic")
    void standardTemplateIsAcyclic() {
        // The baseline. If this failed, every other assertion here would be meaningless, and the
        // workflow itself would be unrunnable.
        assertFalse(detector.hasCycle(StageTemplate.standard().edges()));
    }

    @Test
    @DisplayName("a direct cycle is detected")
    void directCycle() {
        assertTrue(detector.hasCycle(edges("A", "B", "B", "A")));
    }

    @Test
    @DisplayName("a self-loop is INEXPRESSIBLE, which is stronger than detectable")
    void selfLoopCannotBeConstructed() {
        // Written first as "the detector finds a self-loop", and it could not: DependencyEdge refuses
        // to exist with the same node on both ends. That is the better property and it is kept — a
        // shortest-possible cycle that cannot be built is one the detector never has to be right about.
        //
        // The type was not weakened to make the test pass. The test was corrected to assert what the
        // type actually guarantees.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new DependencyEdge("A", "A", JoinSemantics.ALL));
        assertTrue(thrown.getMessage().contains("cannot depend on itself"), thrown.getMessage());

        // And the two-node cycle, which IS expressible, is still found.
        assertTrue(detector.hasCycle(edges("A", "B", "B", "A")));
    }

    @Test
    @DisplayName("a long cycle is detected")
    void longCycle() {
        assertTrue(detector.hasCycle(edges("A", "B", "B", "C", "C", "D", "D", "E", "E", "B")));
    }

    @Test
    @DisplayName("a diamond is NOT a cycle")
    void diamondIsAcyclic() {
        // The false positive that would break the real workflow: S8 fans to S9 and S10, which both
        // reach S11. A detector that flagged a re-visited node rather than a node on the current path
        // would reject the shipped topology.
        assertFalse(detector.hasCycle(edges("A", "B", "A", "C", "B", "D", "C", "D")));
    }

    @Test
    @DisplayName("a disconnected component's cycle is still detected")
    void cycleInASecondComponent() {
        // A walk that started only from the entry node would never reach this one. A replan can easily
        // leave a detached subgraph, so the detector must consider every node.
        assertTrue(detector.hasCycle(edges("A", "B", "X", "Y", "Y", "X")));
    }

    @Test
    @DisplayName("EC-030: a cycle-introducing replan is REJECTED, and nothing is committed")
    void ec030ReplanRejectedPreCommit() {
        // The real case: a live run's topology plus a proposed replan subgraph. Here the replan sends
        // S11 back to S6, which closes a loop through the whole middle of the workflow.
        List<DependencyEdge> existing = new ArrayList<>(StageTemplate.standard().edges());
        List<DependencyEdge> replan = edges("S11", "S6");

        CycleDetector.Result result = detector.check(existing, replan);

        assertFalse(result.acceptable(), "a cycle-introducing replan must be refused");
        assertFalse(result.cyclePath().isEmpty(), "and the refusal must name the cycle it found");
        assertTrue(result.cyclePath().contains("S6"), "the path should include the closing node: "
                + result.cyclePath());
    }

    @Test
    @DisplayName("EC-030: a legitimate replan is ACCEPTED")
    void legitimateReplanAccepted() {
        // Without this, a detector that rejected everything would satisfy the test above. A replan that
        // appends fan-out children is the ordinary case and must go through.
        List<DependencyEdge> existing = new ArrayList<>(StageTemplate.standard().edges());
        List<DependencyEdge> replan = edges(
                "S7", "S7.1", "S7.1", "S7.join",
                "S7", "S7.2", "S7.2", "S7.join");

        CycleDetector.Result result = detector.check(existing, replan);

        assertTrue(result.acceptable(), "appending fan-out children introduces no cycle: "
                + result.cyclePath());
        assertTrue(result.cyclePath().isEmpty());
    }

    @Test
    @DisplayName("the check is on the COMBINED topology, not the replan alone")
    void checkCombinesExistingAndProposed() {
        // The defect this rules out: validating only the new edges. S12→S1 is acyclic by itself and
        // closes the whole workflow into a loop once combined.
        List<DependencyEdge> replanAlone = edges("S12", "S1");
        assertFalse(detector.hasCycle(replanAlone), "the proposed edge is acyclic in isolation");

        CycleDetector.Result result = detector.check(StageTemplate.standard().edges(), replanAlone);
        assertFalse(result.acceptable(),
                "but it closes a cycle once combined with what is already there");
    }

    @Test
    @DisplayName("requireAcyclic throws, so a caller cannot ignore the answer")
    void requireAcyclicThrows() {
        // A boolean nobody checks is a comment. The commit path uses this form, so the only way to
        // persist a cyclic graph is to delete the call.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> detector.requireAcyclic(StageTemplate.standard().edges(), edges("S11", "S6")));
        assertTrue(thrown.getMessage().contains("cycle"), thrown.getMessage());

        detector.requireAcyclic(StageTemplate.standard().edges(), List.of());
    }

    @Test
    @DisplayName("an empty topology is acyclic")
    void emptyIsAcyclic() {
        assertFalse(detector.hasCycle(List.of()));
        assertEquals(List.of(), detector.check(List.of(), List.of()).cyclePath());
    }
}
