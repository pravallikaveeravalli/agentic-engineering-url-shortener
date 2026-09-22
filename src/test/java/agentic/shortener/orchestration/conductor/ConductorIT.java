package agentic.shortener.orchestration.conductor;

import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.gates.Actor;
import agentic.shortener.orchestration.gates.ApprovalGate;
import agentic.shortener.orchestration.gates.GateClass;
import agentic.shortener.orchestration.gates.GateDecision;
import agentic.shortener.orchestration.gates.GateOutcome;
import agentic.shortener.orchestration.gates.GateOutcomeHandler;
import agentic.shortener.orchestration.gates.GateRequestPresenter;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.ArtifactWriteGuard;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.reliability.Backoff;
import agentic.shortener.orchestration.reliability.RetryPolicy;
import agentic.shortener.orchestration.state.RetentionPolicy;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.SafeStopHandler;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.orchestration.store.PersistedNode;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Task T131a, RED-FIRST. FR-ORC-001, FR-ORC-004, FR-ORC-006, FR-ORC-013, FR-ORC-017.
 *
 * <p>The three properties the owner's own instruction named directly: non-linear execution (a genuine
 * parallel fan-out with a join, not merely a graph that permits one), gate-pausing (a run stops and waits
 * rather than polling, and resumes only once a decision exists), and a full run reaching a terminal outcome
 * with persisted state and audit. All three are proven against {@link StageTemplate#standard()} — the real
 * 13-node graph — with deliberately trivial stub {@link StageExecutor}s standing in for the six AI-capable
 * and five deterministic engines, so this class tests {@link Conductor}'s own sequencing logic in isolation
 * from any live AI call or real test-suite execution.
 */
@DisplayName("T131a — Conductor drives a run through parallel fan-out, gates, to a terminal outcome")
class ConductorIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private Clock clock;
    private JdbcRunStore runStore;
    private GateStore gateStore;
    private GateOutcomeHandler gateOutcomeHandler;
    private ArtifactWriteGuard artifactWriteGuard;
    private AuditWriter auditWriter;
    private ExecutorService dispatchPool;

    @BeforeEach
    void setUp() throws Exception {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        gateStore = new GateStore(connections, clock);
        gateOutcomeHandler = new GateOutcomeHandler(runStore, gateStore, connections);
        artifactWriteGuard = new ArtifactWriteGuard(connections, clock);
        auditWriter = new AuditWriter(connections);
        dispatchPool = Executors.newFixedThreadPool(4);
    }

    // ==============================================================================================
    // Non-linear execution: S9/S10 genuinely run at the same time, and a real 2-child S7 fan-out
    // genuinely runs its children at the same time too — plus the run reaches COMPLETED.
    // ==============================================================================================

    @Test
    @DisplayName("T082a: submit() alone materializes 13 nodes with S4 BLOCKED and does not itself advance")
    void submitAloneDoesNotAdvanceTheRun() throws Exception {
        CountDownLatch unusedBarrier1 = new CountDownLatch(2);
        CountDownLatch unusedBarrier2 = new CountDownLatch(2);

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter(), artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler(), retryPolicy(),
                stubExecutors(notMaterialAmbiguity(), unusedBarrier1, unusedBarrier2),
                FanOutPlanner.singleChild(), clock, dispatchPool, paths -> Map.of());

        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0",
                "a requirement whose submission alone must not drive anything");

        List<PersistedNode> nodes = runStore.persistedNodes(runId);
        assertEquals(13, nodes.size(), "StageTemplate.standard() materializes 13 nodes (S1-S12 plus S7.join)");
        assertEquals(StageState.BLOCKED, runStore.node(runId, "S4").orElseThrow().state(),
                "submit() must not itself have advanced the run far enough for S4 to be anything but "
                        + "BLOCKED -- a caller (RunSubmissionController) needs a fast, bounded response, "
                        + "not one that blocks for however long a real AI-capable stage takes");
        assertEquals(RunState.RUNNING, runStore.run(runId).orElseThrow().state());
    }

    @Test
    @DisplayName("S9/S10 dispatch concurrently, a 2-child S7 fan-out dispatches concurrently, "
            + "run reaches COMPLETED with every node settled")
    void nonLinearFanOutAndJoinReachTerminalOutcome() throws Exception {
        CountDownLatch s9s10Barrier = new CountDownLatch(2);
        CountDownLatch s7ChildrenBarrier = new CountDownLatch(2);

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter(), artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler(), retryPolicy(),
                stubExecutors(notMaterialAmbiguity(), s9s10Barrier, s7ChildrenBarrier),
                (runId, parentKey, artifacts) -> List.of(parentKey + ".1", parentKey + ".2"),
                clock, dispatchPool, paths -> Map.of());

        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0",
                "a genuinely well-formed test requirement");
        conductor.advance(runId);

        // S4 was skipped (no material ambiguity), S6 and S11 are gate-held — answer both to let the run
        // finish, calling advance() again after each exactly as a real gate-decision handler would.
        approveGate(conductor, runId, "S6");
        approveGate(conductor, runId, "S11");

        PersistedNode s4 = runStore.node(runId, "S4").orElseThrow();
        assertEquals(StageState.SKIPPED, s4.state(), "no material ambiguity: S4 must be SKIPPED, not gated");

        assertEquals(RunState.COMPLETED, runStore.run(runId).orElseThrow().state(),
                "run did not reach a terminal COMPLETED state");

        List<PersistedNode> nodes = runStore.persistedNodes(runId);
        for (PersistedNode node : nodes) {
            assertTrue(node.state().satisfiesJoin(),
                    node.nodeKey() + " is " + node.state() + ", which does not satisfy a join — the run "
                            + "should not have reached COMPLETED with this node unsettled");
        }
        assertTrue(nodes.stream().anyMatch(n -> n.nodeKey().equals("S7.1")),
                "expected the fan-out planner's two children to have actually been appended and dispatched");
        assertTrue(nodes.stream().anyMatch(n -> n.nodeKey().equals("S7.2")));
    }

    // ==============================================================================================
    // Gate-pausing: a run stops and waits, it does not poll; it resumes only once a decision exists.
    // ==============================================================================================

    @Test
    @DisplayName("material ambiguity opens a gate and the run stops; a decision resumes it")
    void gateGenuinelyPausesAndOnlyADecisionResumesIt() throws Exception {
        CountDownLatch unusedBarrier1 = new CountDownLatch(2);
        CountDownLatch unusedBarrier2 = new CountDownLatch(2);

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter(), artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler(), retryPolicy(),
                stubExecutors(materialAmbiguity(), unusedBarrier1, unusedBarrier2),
                FanOutPlanner.singleChild(), clock, dispatchPool, paths -> Map.of());

        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0",
                "a requirement whose ambiguity check reports MATERIAL_PENDING");
        conductor.advance(runId);

        // The run must be paused, not failed and not progressed: S4 awaiting a decision, S5 (and
        // everything after it) never dispatched, and the run itself still RUNNING — a suspended run with
        // no terminal state (KE-04), not stuck by accident.
        PersistedNode s4 = runStore.node(runId, "S4").orElseThrow();
        assertEquals(StageState.AWAITING_APPROVAL, s4.state());
        PersistedNode s5 = runStore.node(runId, "S5").orElseThrow();
        assertEquals(StageState.BLOCKED, s5.state(),
                "S5 must not have been dispatched while S4's gate is still open — a run that advances "
                        + "past an unanswered gate is exactly the defect FR-ORC-013 exists to prevent");
        assertEquals(RunState.RUNNING, runStore.run(runId).orElseThrow().state());

        // Calling advance() again with nothing decided must change nothing -- this is the "does not poll,
        // does not guess" half of the proof.
        conductor.advance(runId);
        assertEquals(StageState.AWAITING_APPROVAL, runStore.node(runId, "S4").orElseThrow().state());
        assertEquals(StageState.BLOCKED, runStore.node(runId, "S5").orElseThrow().state());

        // Now a decision arrives, exactly the way GateDecisionController would produce one, and resuming
        // is a second, separate call -- proving resumption, not merely non-progression.
        approveGate(conductor, runId, "S4");

        PersistedNode s5After = runStore.node(runId, "S5").orElseThrow();
        assertTrue(s5After.state() == StageState.SUCCEEDED || s5After.state() == StageState.RUNNING
                        || s5After.state() == StageState.READY,
                "S5 must have been unblocked once S4's gate was decided, found: " + s5After.state());
    }

    // ==============================================================================================
    // Stub executors and test collaborators
    // ==============================================================================================

    private java.util.function.Function<Integer, StageExecutor> stubExecutors(String ambiguitiesJson,
            CountDownLatch s9s10Barrier, CountDownLatch s7ChildrenBarrier) {
        return stageNumber -> switch (stageNumber) {
            case 3 -> input -> succeeded("ambiguities", ambiguitiesJson);
            case 7 -> input -> {
                await(s7ChildrenBarrier);
                return succeeded("branchCommit", "stub/" + input.nodeKey());
            };
            case 9 -> input -> {
                await(s9s10Barrier);
                return succeeded("documentation", "stub docs");
            };
            case 10 -> input -> {
                await(s9s10Barrier);
                return succeeded("policy-results", "stub policy verdict");
            };
            default -> input -> succeeded("stub-output-S" + stageNumber, "stub content for S" + stageNumber);
        };
    }

    /** Both parties in a 2-party barrier must arrive before either proceeds — the only way this returns
     * without timing out is if the Conductor genuinely dispatched both nodes before either finished. */
    private static void await(CountDownLatch barrier) {
        barrier.countDown();
        try {
            if (!barrier.await(10, TimeUnit.SECONDS)) {
                fail("barrier never reached — the two nodes did not run concurrently");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted waiting for the concurrency barrier");
        }
    }

    private static StageOutcome succeeded(String artifactKey, String content) {
        return StageOutcome.succeeded(List.of(new ProducedArtifact(artifactKey, content, List.of())),
                ExecutorKind.DETERMINISTIC);
    }

    private static String notMaterialAmbiguity() {
        return "[{\"id\":\"" + UUID.randomUUID() + "\",\"ambiguityClass\":\"MISSING_ACCEPTANCE_CRITERIA\","
                + "\"affectedPath\":\"none\",\"resolutionState\":\"NOT_MATERIAL\","
                + "\"qualityChecksPerformed\":\"checked structural criteria\","
                + "\"noClarificationReason\":\"the requirement is complete and unambiguous\"}]";
    }

    private static String materialAmbiguity() {
        return "[{\"id\":\"" + UUID.randomUUID() + "\",\"ambiguityClass\":\"UNDEFINED_TERM\","
                + "\"affectedPath\":\"the term 'promptly' is undefined\",\"resolutionState\":"
                + "\"MATERIAL_PENDING\",\"qualityChecksPerformed\":\"checked structural criteria\"}]";
    }

    private GateRequestPresenter gateRequestPresenter() {
        return new GateRequestPresenter(clock, new RetentionPolicy(), Duration.ofMinutes(15));
    }

    private SafeStopHandler safeStopHandler() {
        return new SafeStopHandler(connections, clock, new RetentionPolicy());
    }

    private RetryPolicy retryPolicy() {
        return new RetryPolicy(Backoff.sleeping());
    }

    /** Mirrors exactly what {@code GateDecisionController} does: build the decision, record it, apply it,
     * then resume the run — proving {@link Conductor#advance} is the correct thing for that caller to do
     * next, not merely a method that exists. */
    private void approveGate(Conductor conductor, UUID runId, String nodeKey) throws Exception {
        List<ApprovalGate> pending = pendingGateFor(runId, nodeKey);
        assertFalse(pending.isEmpty(), "expected a pending gate for " + nodeKey);
        ApprovalGate gate = pending.get(0);

        GateDecision decision = new GateDecision(null, runId, gate.gateId(), stageNumberOf(nodeKey),
                gate.gateClass(), GateOutcome.APPROVED, new Actor("human", "test-owner"), clock.instant(),
                "approved for ConductorIT", "docs/governance/gate-decisions/test-fixture.md", List.of(), null,
                null);
        gateStore.recordDecision(decision);
        gateOutcomeHandler.apply(runId, nodeKey, decision);
        conductor.advance(runId);
    }

    /** {@link GateStore}'s own read API is keyed by a gate id the caller must already know; a real caller
     * (a human reviewer, or {@code GateDecisionController}'s caller) discovers it the same way this test
     * does — by inspecting the run, which is exactly {@code RunInspectionQuery}'s own {@code approval_gate}
     * query (T067), read here directly rather than through its JSON-serializing wrapper. */
    private List<ApprovalGate> pendingGateFor(UUID runId, String nodeKey) throws Exception {
        String sql = "SELECT ag.gate_id, ag.gate_class, ag.wait_deadline, ag.disclosed_auto_abandon_at, "
                + "ag.requested_at FROM approval_gate ag WHERE ag.run_id = ? AND ag.node_key = ? "
                + "AND NOT EXISTS (SELECT 1 FROM gate_decision gd WHERE gd.run_id = ag.run_id "
                + "AND gd.gate_id = ag.gate_id)";
        try (var c = connection(); var ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (var rs = ps.executeQuery()) {
                List<ApprovalGate> result = new java.util.ArrayList<>();
                while (rs.next()) {
                    result.add(new ApprovalGate(rs.getString("gate_id"), runId, nodeKey,
                            GateClass.valueOf(rs.getString("gate_class")),
                            rs.getTimestamp("wait_deadline").toInstant(),
                            rs.getTimestamp("disclosed_auto_abandon_at").toInstant(),
                            rs.getTimestamp("requested_at").toInstant()));
                }
                return result;
            }
        }
    }

    private static Integer stageNumberOf(String nodeKey) {
        return switch (nodeKey) {
            case "S4" -> 4;
            case "S6" -> 6;
            case "S11" -> 11;
            default -> throw new IllegalArgumentException("unexpected gated node in this test: " + nodeKey);
        };
    }
}
