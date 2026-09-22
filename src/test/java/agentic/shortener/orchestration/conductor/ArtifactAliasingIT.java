package agentic.shortener.orchestration.conductor;

import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.gates.Actor;
import agentic.shortener.orchestration.gates.ApprovalGate;
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
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A real, previously-unexercised gap: {@link agentic.shortener.orchestration.executor.ai.stages.
 * DecompositionAiExecutor}'s own output key is {@code "tasks"} (plural — S5's own contract), but {@link
 * agentic.shortener.orchestration.executor.ai.stages.ImplementationAiExecutor}'s own input key is
 * {@code "task"} (singular — S7's own contract). With {@link FanOutPlanner#singleChild()} (the whole
 * decomposition implemented as one child, no further split), S7's single child never receives anything under
 * "task" unless {@code Conductor}'s own {@code ARTIFACT_ALIASES} carries the same alias the two OTHER known
 * producer/consumer key mismatches already use ({@code "branchCommit"}→{@code "branch"}/{@code "change"},
 * {@code "test-results"}→{@code "results"}).
 *
 * <p>Found live, for real, while preparing DS-A's first-ever real S7 dispatch (T132) — no prior test in this
 * codebase exercises the real {@code ImplementationAiExecutor} downstream of a real {@code
 * DecompositionAiExecutor} output; {@code ConductorIT}'s own S7 coverage uses a stub that never reads either
 * key by name.
 */
@DisplayName("Artifact aliasing — S5's 'tasks' output must reach S7 under 'task' too, via FanOutPlanner.singleChild()")
class ArtifactAliasingIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    @DisplayName("S7's single child (FanOutPlanner.singleChild()) receives S5's 'tasks' output under 'task' too")
    void s7SingleChildReceivesTasksOutputUnderSingularTaskKey() throws Exception {
        // Defensive against JUnit's own undefined method order running this class in isolation --
        // matches the same fix DsALiveRun/DsCLiveRun already carry for the identical hazard.
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS harness_round_trip");
        }
        MigrationSupport.migrate(POSTGRES, "public");
        ConnectionSource connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

        JdbcRunStore runStore = new JdbcRunStore(connections, clock);
        GateStore gateStore = new GateStore(connections, clock);
        RetentionPolicy retentionPolicy = new RetentionPolicy();
        GateRequestPresenter gateRequestPresenter =
                new GateRequestPresenter(clock, retentionPolicy, Duration.ofHours(24));
        ArtifactWriteGuard artifactWriteGuard = new ArtifactWriteGuard(connections, clock);
        AuditWriter auditWriter = new AuditWriter(connections);
        agentic.shortener.orchestration.state.SafeStopHandler safeStopHandler =
                new agentic.shortener.orchestration.state.SafeStopHandler(connections, clock, retentionPolicy);
        RetryPolicy retryPolicy = new RetryPolicy(Backoff.sleeping(), StageTelemetry.disabled());

        AtomicReference<String> capturedTaskInput = new AtomicReference<>();

        java.util.function.Function<Integer, StageExecutor> executors = stageNumber -> switch (stageNumber) {
            case 1 -> input -> succeeded("normalized", "req");
            case 2 -> input -> succeeded("requirements", "[{\"externalId\":\"1\"}]");
            case 3 -> input -> succeeded("ambiguities", "[{\"ambiguityClass\":\"UNDEFINED_TERM\","
                    + "\"affectedPath\":\"n/a\",\"resolutionState\":\"NOT_MATERIAL\","
                    + "\"qualityChecksPerformed\":\"n/a\",\"noClarificationReason\":\"nothing to check here\"}]");
            case 5 -> input -> succeeded("tasks",
                    "[{\"taskId\":\"T1\",\"requirementIds\":[\"1\"],\"dependsOn\":[]}]");
            case 6 -> input -> succeeded("design", "{\"design\":\"stub design\"}");
            case 7 -> input -> {
                // The real bug this test proves and guards: reading "task" (singular), exactly as
                // ImplementationAiExecutor.INPUT_TASK_KEY does.
                capturedTaskInput.set(input.inputArtifacts().get("task"));
                return succeeded("branchCommit", "stub/" + input.nodeKey());
            };
            default -> input -> succeeded("stub-output-S" + stageNumber, "stub content for S" + stageNumber);
        };

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter, artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler, retryPolicy, executors,
                FanOutPlanner.singleChild(), clock, Executors.newFixedThreadPool(2),
                paths -> java.util.Map.of());

        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", "a test requirement");
        conductor.advance(runId);

        // S6 is a real human gate (ARCHITECTURE_APPROVAL) between S5/S6 succeeding and S7 ever becoming
        // ready -- approve it the same way a real GateDecisionController call would, so the run actually
        // reaches S7's fan-out.
        approveGate(conductor, runId, runStore, gateStore, connections, clock, "S6", 6);

        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S7.1").orElseThrow().state(),
                "S7.1 should have succeeded with a stub branch commit");
        assertNotNull(capturedTaskInput.get(),
                "S7's single child must receive S5's 'tasks' output aliased under 'task' too -- "
                        + "ImplementationAiExecutor.INPUT_TASK_KEY is 'task' (singular), but "
                        + "DecompositionAiExecutor.OUTPUT_KEY is 'tasks' (plural); without the alias, every "
                        + "real S7 dispatch behind FanOutPlanner.singleChild() fails INVALID_INPUT even "
                        + "though S5 genuinely ran");
    }

    private static StageOutcome succeeded(String key, String content) {
        return StageOutcome.succeeded(List.of(new ProducedArtifact(key, content, List.of())), ExecutorKind.AI);
    }

    /** Same shape as {@code ConductorIT}'s own {@code approveGate} helper. */
    private void approveGate(Conductor conductor, UUID runId, JdbcRunStore runStore, GateStore gateStore,
                             ConnectionSource connections, Clock clock, String nodeKey, int stageNumber)
            throws Exception {
        String gateId;
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT gate_id FROM approval_gate WHERE run_id = ? AND node_key = ?")) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("no pending gate for " + nodeKey);
                }
                gateId = rs.getString("gate_id");
            }
        }
        GateDecision decision = new GateDecision(runId, gateId, stageNumber,
                agentic.shortener.orchestration.gates.GateClass.ARCHITECTURE_APPROVAL, GateOutcome.APPROVED,
                new Actor("human", "test-owner"), clock.instant(), "approved for ArtifactAliasingIT",
                "docs/governance/gate-decisions/test-fixture.md", List.of(), null, null);
        long decisionId = gateStore.recordDecision(decision);
        GateDecision recorded = gateStore.decisionById(decisionId).orElseThrow();
        GateOutcomeHandler outcomeHandler = new GateOutcomeHandler(runStore, gateStore, connections);
        outcomeHandler.apply(runId, nodeKey, recorded);
        conductor.advance(runId);
    }
}
