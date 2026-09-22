package agentic.shortener.orchestration.conductor;

import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageOutcome;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * CR-057: plan §5's own trigger for the architecture gate is "S6 produces MATERIAL design decisions" — not
 * unconditional. This proves the real end-to-end {@link Conductor} wiring both ways, the same shape {@link
 * ArtifactAliasingIT} already established for the artifact-aliasing fix: a stub S6 that names real material
 * decisions opens the gate exactly as before; a stub S6 that affirmatively shows non-materiality (a
 * substantive {@code nonMaterialityJustification}, empty {@code materialDesignDecisions}) proceeds straight
 * through to S7 with no gate at all — and S11's own gate stays unconditional regardless, proving this change
 * did not accidentally weaken release readiness too.
 */
@DisplayName("CR-057 — the architecture gate is conditional on S6's own materiality signal, S11 stays "
        + "unconditional")
class ArchitectureGateConditionalityIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-22T12:00:00Z");

    @Test
    @DisplayName("a design with real material decisions opens the architecture gate, exactly as before")
    void materialDesignOpensArchitectureGate() throws Exception {
        Conductor conductor = newConductor(stageNumber -> switch (stageNumber) {
            case 6 -> input -> succeeded("design", "{\"design\":\"stub\",\"materialDesignDecisions\":"
                    + "[\"a genuine, real architectural fork with observable consequences\"]}");
            case 7 -> input -> succeeded("branchCommit", "stub/" + input.nodeKey());
            default -> null;
        });

        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", "a test requirement");
        conductor.advance(runId);

        assertEquals(StageState.AWAITING_APPROVAL, runStoreOf(conductor).node(runId, "S6").orElseThrow().state(),
                "a design naming real material decisions must still open the architecture gate");
        assertEquals(StageState.BLOCKED, runStoreOf(conductor).node(runId, "S7").orElseThrow().state(),
                "S7 must stay blocked behind the still-open architecture gate");
    }

    @Test
    @DisplayName("a design that affirmatively shows NO material decisions proceeds straight through S6 to "
            + "S7, with no architecture gate at all")
    void nonMaterialDesignSkipsArchitectureGateEntirely() throws Exception {
        Conductor conductor = newConductor(stageNumber -> switch (stageNumber) {
            case 6 -> input -> succeeded("design", "{\"design\":\"stub\",\"materialDesignDecisions\":[],"
                    + "\"nonMaterialityJustification\":\"every choice here is forced, no viable "
                    + "alternative changes required behaviour\"}");
            case 7 -> input -> succeeded("branchCommit", "stub/" + input.nodeKey());
            default -> null;
        });

        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", "a test requirement");
        conductor.advance(runId);

        JdbcRunStore runStore = runStoreOf(conductor);
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S6").orElseThrow().state(),
                "a design affirmatively showing no material decisions must succeed straight through -- no "
                        + "gate to be stuck AWAITING_APPROVAL on");
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S7.1").orElseThrow().state(),
                "with no architecture gate opened, the SAME advance() call must already have carried the "
                        + "run through S7 too -- proving this is a real skip, not merely a state label");
        assertNoApprovalGateRow(runId, "S6");
    }

    @Test
    @DisplayName("REGRESSION: S11's own release-readiness gate stays unconditional regardless of S6's own "
            + "materiality outcome")
    void releaseReadinessGateStaysUnconditional() throws Exception {
        Conductor conductor = newConductor(stageNumber -> switch (stageNumber) {
            case 6 -> input -> succeeded("design", "{\"design\":\"stub\",\"materialDesignDecisions\":[],"
                    + "\"nonMaterialityJustification\":\"every choice here is forced, no viable "
                    + "alternative changes required behaviour\"}");
            default -> input -> switch (stageNumber) {
                case 7 -> succeeded("branchCommit", "stub/" + input.nodeKey());
                default -> succeeded("stub-output-S" + stageNumber, "stub content for S" + stageNumber);
            };
        });

        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", "a test requirement");
        conductor.advance(runId);

        JdbcRunStore runStore = runStoreOf(conductor);
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S6").orElseThrow().state(),
                "S6 itself must still skip its own gate under this non-material stub design");
        assertEquals(StageState.AWAITING_APPROVAL, runStore.node(runId, "S11").orElseThrow().state(),
                "S11's own release-readiness gate must still open unconditionally -- CR-057 only changed "
                        + "S6, never S11");
    }

    private static StageOutcome succeeded(String key, String content) {
        return StageOutcome.succeeded(List.of(new ProducedArtifact(key, content, List.of())), ExecutorKind.AI);
    }

    private JdbcRunStore sharedRunStore;

    private Conductor newConductor(java.util.function.Function<Integer, StageExecutor> stageExecutors)
            throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS harness_round_trip");
        }
        MigrationSupport.migrate(POSTGRES, "public");
        ConnectionSource connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

        JdbcRunStore runStore = new JdbcRunStore(connections, clock);
        sharedRunStore = runStore;
        GateStore gateStore = new GateStore(connections, clock);
        RetentionPolicy retentionPolicy = new RetentionPolicy();
        GateRequestPresenter gateRequestPresenter =
                new GateRequestPresenter(clock, retentionPolicy, Duration.ofHours(24));
        ArtifactWriteGuard artifactWriteGuard = new ArtifactWriteGuard(connections, clock);
        AuditWriter auditWriter = new AuditWriter(connections);
        agentic.shortener.orchestration.state.SafeStopHandler safeStopHandler =
                new agentic.shortener.orchestration.state.SafeStopHandler(connections, clock, retentionPolicy);
        RetryPolicy retryPolicy = new RetryPolicy(Backoff.sleeping(), StageTelemetry.disabled());

        java.util.function.Function<Integer, StageExecutor> executors = stageNumber -> switch (stageNumber) {
            case 1 -> input -> succeeded("normalized", "req");
            case 2 -> input -> succeeded("requirements", "[{\"externalId\":\"1\"}]");
            case 3 -> input -> succeeded("ambiguities", "[{\"ambiguityClass\":\"UNDEFINED_TERM\","
                    + "\"affectedPath\":\"n/a\",\"resolutionState\":\"NOT_MATERIAL\","
                    + "\"qualityChecksPerformed\":\"n/a\",\"noClarificationReason\":\"nothing to check "
                    + "here\"}]");
            case 5 -> input -> succeeded("tasks",
                    "[{\"taskId\":\"T1\",\"requirementIds\":[\"1\"],\"dependsOn\":[]}]");
            default -> {
                StageExecutor override = stageExecutors.apply(stageNumber);
                yield override != null ? override
                        : input -> succeeded("stub-output-S" + stageNumber, "stub content for S" + stageNumber);
            }
        };

        return new Conductor(runStore, gateStore, gateRequestPresenter, artifactWriteGuard, auditWriter,
                StageTelemetry.disabled(), safeStopHandler, retryPolicy, executors,
                FanOutPlanner.singleChild(), clock, Executors.newFixedThreadPool(2),
                paths -> java.util.Map.of());
    }

    private JdbcRunStore runStoreOf(Conductor conductor) {
        return sharedRunStore;
    }

    private void assertNoApprovalGateRow(UUID runId, String nodeKey) throws Exception {
        try (var c = connection();
             var ps = c.prepareStatement(
                     "SELECT gate_id FROM approval_gate WHERE run_id = ? AND node_key = ?")) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (var rs = ps.executeQuery()) {
                assertFalse(rs.next(), "no architecture-approval gate row should ever have been requested "
                        + "for " + nodeKey + " when the design affirmatively showed no material decisions");
            }
        }
    }
}
