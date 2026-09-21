package agentic.shortener.orchestration.replan;

import agentic.shortener.orchestration.gates.Actor;
import agentic.shortener.orchestration.gates.GateClass;
import agentic.shortener.orchestration.gates.GateDecision;
import agentic.shortener.orchestration.gates.GateOutcome;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.DependencyEdge;
import agentic.shortener.orchestration.graph.JoinSemantics;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T096 — dynamic replanning with approval voiding. FR-ORC-019, NFR-CHG-002, SC-007. EC-019, EC-020,
 * EC-030.
 *
 * <p>Five assertions, matching T096's own Validate clause exactly: the affected set equals the computed
 * closure; unaffected parallel branches are untouched; EC-020 (a voided approval cannot satisfy the
 * re-planned stage); EC-019 (an in-flight stage is not corrupted); EC-030 (a cycle-introducing replan is
 * rejected). Plus "Done: ... replan event queryable" — proven directly.
 */
@DisplayName("T096 — ReplanService: the affected-set closure, EC-019, EC-020, EC-030, and a queryable event")
class ReplanServiceTest extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private GateStore gateStore;
    private ReplanService replanService;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        gateStore = new GateStore(connections, clock);
        replanService = new ReplanService(runStore, gateStore, connections, clock);
        runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
    }

    /** Drives S1-S3 to SUCCEEDED, S4 SKIPPED, S5 to SUCCEEDED — S6 left READY, everything past it BLOCKED. */
    private void driveToS6Ready() {
        runStore.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S2", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S2", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S2", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S3", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S3", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S3", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S4", StageState.BLOCKED, StageState.SKIPPED, "no ambiguity");
        runStore.transitionNode(runId, "S5", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S5", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S5", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S6", StageState.BLOCKED, StageState.READY, "x");
    }

    @Test
    @DisplayName("1/5: the affected set equals the computed downstream closure of S3")
    void affectedSetEqualsComputedClosure() {
        driveToS6Ready();

        ReplanEvent event = replanService.replan(runId, "S3", "requirement R1 changed after clarification");

        // Downstream of S3 in the standard template: S4, S5, S6, S7, S7.join, S8, S9, S10, S11, S12.
        assertEquals(
                List.of("S10", "S11", "S12", "S4", "S5", "S6", "S7", "S7.join", "S8", "S9"),
                event.invalidatedNodes().stream().sorted().toList(),
                "the invalidated set must be EXACTLY the downstream closure, no more and no less");
        for (String nodeKey : event.invalidatedNodes()) {
            assertEquals(StageState.INVALIDATED, runStore.node(runId, nodeKey).orElseThrow().state());
        }
    }

    @Test
    @DisplayName("2/5: unaffected UPSTREAM and sibling nodes are untouched")
    void unaffectedBranchesAreUntouched() {
        driveToS6Ready();

        replanService.replan(runId, "S3", "requirement R1 changed");

        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S1").orElseThrow().state());
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S2").orElseThrow().state());
        // S3 ITSELF is the triggering node, not downstream of itself — untouched by this replan (a
        // caller wanting S3 re-run would transition it separately; the closure starts strictly after it).
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S3").orElseThrow().state());
    }

    @Test
    @DisplayName("3/5: EC-020 — a voided approval cannot satisfy the re-planned stage")
    void voidedApprovalCannotSatisfyTheReplannedStage() {
        driveToS6Ready();
        runStore.transitionNode(runId, "S6", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S6", StageState.RUNNING, StageState.AWAITING_APPROVAL,
                "architecture review requested");
        long approvedDecisionId = gateStore.recordDecision(new GateDecision(runId, "S6", 6,
                GateClass.ARCHITECTURE_APPROVAL, GateOutcome.APPROVED, new Actor("human", "the owner"), T0,
                "design approved", "docs/governance/gate-decisions/gate-t096-1.md", List.of(), null, null));

        ReplanEvent event = replanService.replan(runId, "S3", "requirement R1 changed after S6 was approved");

        assertTrue(event.voidedDecisionIds().contains(approvedDecisionId),
                "the replan event must record that S6's own approval was voided");
        // The ORIGINAL decision is untouched (EC-020: superseded, never edited or deleted)...
        assertEquals("APPROVED", gateStore.decisionById(approvedDecisionId).orElseThrow().outcome().name());
        // ...but it no longer satisfies anything: querying for the STILL-STANDING decision on this gate
        // (the same "not superseded by anything" definition GateOutcomeHandler's own voiding uses) finds
        // only the new REJECTED superseding record, never the original APPROVED one.
        assertEquals("REJECTED", stillStandingOutcome(runId, "S6"),
                "a voided approval must not be the still-standing answer for its gate any more");
    }

    private String stillStandingOutcome(UUID runId, String gateId) {
        try (var c = connections.get(); var ps = c.prepareStatement(
                "SELECT outcome FROM gate_decision WHERE run_id = ? AND gate_id = ? "
                        + "AND gate_decision_id NOT IN (SELECT supersedes_gate_decision_id FROM gate_decision "
                        + "WHERE supersedes_gate_decision_id IS NOT NULL)")) {
            ps.setObject(1, runId);
            ps.setString(2, gateId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "expected exactly one still-standing decision");
                return rs.getString(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("4/5: EC-019 — an in-flight (RUNNING) downstream stage is not corrupted by the replan")
    void inFlightStageIsNotCorrupted() {
        driveToS6Ready();
        runStore.transitionNode(runId, "S6", StageState.READY, StageState.RUNNING, "x");

        ReplanEvent event = replanService.replan(runId, "S3", "requirement R1 changed while S6 was running");

        assertEquals(StageState.RUNNING, runStore.node(runId, "S6").orElseThrow().state(),
                "an in-flight stage must not be mutated mid-execution (EC-019)");
        assertFalse(event.invalidatedNodes().contains("S6"),
                "S6 must not appear in the invalidated set while it is genuinely still running");
        // S7 onward — downstream of S6 — is still correctly invalidated; EC-019 scopes the exception to
        // the in-flight node itself, it does not stop the closure from reaching past it.
        assertTrue(event.invalidatedNodes().contains("S7"));
    }

    @Test
    @DisplayName("5/5: EC-030 — a cycle-introducing replan is rejected, never committed")
    void cycleIntroducingReplanIsRejected() {
        // S12 -> S1 closes the entire standard template into a loop — the exact example this codebase's
        // own CycleDetector javadoc uses (T075).
        List<DependencyEdge> cycleIntroducing = List.of(new DependencyEdge("S12", "S1", JoinSemantics.ALL));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> replanService.appendReplannedEdges(runId, cycleIntroducing));
        assertTrue(refused.getMessage().contains("EC-030"));

        // Never committed: the run's edge set is unchanged.
        assertFalse(runStore.edges(runId).contains(new DependencyEdge("S12", "S1", JoinSemantics.ALL)));
    }

    @Test
    @DisplayName("Done: the replan event is queryable after the fact")
    void replanEventIsQueryable() {
        driveToS6Ready();

        ReplanEvent recorded = replanService.replan(runId, "S3", "a queryable replan");

        ReplanEvent reread = replanService.replanEvent(recorded.replanEventId()).orElseThrow();
        assertEquals(recorded.runId(), reread.runId());
        assertEquals("a queryable replan", reread.cause());
        assertEquals("S3", reread.triggeringNodeKey());
        assertEquals(recorded.invalidatedNodes(), reread.invalidatedNodes());
    }
}
