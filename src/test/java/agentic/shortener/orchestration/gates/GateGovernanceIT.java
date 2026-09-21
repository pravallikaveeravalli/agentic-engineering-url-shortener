package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RetentionPolicy;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.SafeStopHandler;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.state.SuspensionTrigger;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
 * Task T068 — the US2 gate-governance sweep. FR-ORC-013, SC-005. Plan §5. <strong>Synchronization
 * point</strong> for T058–T067.
 *
 * <p>Every other class in this package proves ONE fact in isolation: {@code GateOutcomeHandlerIT} proves
 * the four outcomes' effects, {@code SilenceTest} proves the timeout path, {@code MaterializationCheckTest}
 * and {@code ActorAuthorityTest} prove their own refusals, {@code NoPlanGateIT} proves the three no-plan
 * options. This class proves they compose — the same story {@code UrlShortenerAcceptanceIT} tells for US1,
 * told here for US2: every outcome, the silence path, materialization refusal, authority refusal, and all
 * three no-plan options, driven end to end against real runs in one suite.
 *
 * <p><strong>Guard (T068's own):</strong> US2 must be provable without domain code changing — every fixture
 * here is {@code StageTemplate.standard()} and fake work labels ({@code "x"}), never the shortener's own
 * domain classes.
 */
@Tag("us2")
@DisplayName("T068 US2 gate-governance sweep — synchronization point for T058-T067")
class GateGovernanceIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private GateStore gateStore;
    private GateOutcomeHandler outcomeHandler;
    private SafeStopHandler safeStop;
    private NoPlanGate noPlanGate;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        gateStore = new GateStore(connections, clock);
        outcomeHandler = new GateOutcomeHandler(runStore, gateStore, connections);
        safeStop = new SafeStopHandler(connections, clock, new RetentionPolicy());
        noPlanGate = new NoPlanGate(runStore);
    }

    private UUID freshRun() {
        UUID runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
        return runId;
    }

    /** Drives S1-S3 to completion and raises a real {@link ApprovalGate} on S4. The ordinary gate shape. */
    private void driveToS4Gate(UUID runId) {
        runStore.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S2", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S2", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S2", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S3", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S3", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S3", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S4", StageState.BLOCKED, StageState.AWAITING_APPROVAL,
                "material ambiguity found");
        gateStore.requestGate(new ApprovalGate("S4", runId, "S4", GateClass.UNRESOLVED_AMBIGUITY,
                T0.plusSeconds(3600), T0.plusSeconds(90 * 86_400), T0));
    }

    /** Drives S1-S6 to completion, leaving S7 READY — deterministic S7, no change plan. */
    private void driveToS7(UUID runId) {
        runStore.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S2", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S2", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S2", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S3", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S3", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S3", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S4", StageState.BLOCKED, StageState.SKIPPED,
                "no material ambiguity; no_clarification_reason recorded");
        runStore.transitionNode(runId, "S5", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S5", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S5", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S6", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S6", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S6", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S7", StageState.BLOCKED, StageState.READY,
                "reached S7; no change plan for the requirement");
    }

    // ==============================================================================================
    // Every outcome — four submittable, driven end to end (T059)
    // ==============================================================================================

    @Test
    @DisplayName("OUTCOME 1/4: APPROVED — the gated stage succeeds")
    void approvedSucceedsTheGatedStage() {
        UUID runId = freshRun();
        driveToS4Gate(runId);

        GateDecision decision = new GateDecision(runId, "S4", 4, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.APPROVED, new Actor("human", "the owner"), T0, "resolved",
                "docs/governance/gate-decisions/gate-01.md", List.of(), null, null);
        gateStore.recordDecision(decision);
        outcomeHandler.apply(runId, "S4", decision);

        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S4").orElseThrow().state());
        assertEquals(RunState.RUNNING, runStore.run(runId).orElseThrow().state());
    }

    @Test
    @DisplayName("OUTCOME 2/4: REJECTED — the run terminates")
    void rejectedTerminatesTheRun() {
        UUID runId = freshRun();
        driveToS4Gate(runId);

        GateDecision decision = new GateDecision(runId, "S4", 4, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.REJECTED, new Actor("human", "the owner"), T0, "not viable",
                "docs/governance/gate-decisions/gate-02.md", List.of(), null, null);
        gateStore.recordDecision(decision);
        outcomeHandler.apply(runId, "S4", decision);

        var run = runStore.run(runId).orElseThrow();
        assertEquals(RunState.REJECTED, run.state());
        assertEquals(RunState.REJECTED, run.terminalState());
    }

    @Test
    @DisplayName("OUTCOME 3/4: CHANGES_REQUESTED — returns to the owning stage, invalidates downstream")
    void changesRequestedInvalidatesDownstream() {
        UUID runId = freshRun();
        driveToS4Gate(runId);
        runStore.transitionNode(runId, "S4", StageState.AWAITING_APPROVAL, StageState.SUCCEEDED,
                "answered");
        runStore.transitionNode(runId, "S5", StageState.BLOCKED, StageState.READY, "x");

        GateDecision decision = new GateDecision(runId, "S3", 3, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.CHANGES_REQUESTED, new Actor("human", "the owner"), T0,
                "the ambiguity check missed a case", "docs/governance/gate-decisions/gate-03.md",
                List.of("re-check for contradictory bounds"), null, null);
        gateStore.recordDecision(decision);
        outcomeHandler.apply(runId, "S3", decision);

        assertEquals(StageState.READY, runStore.node(runId, "S3").orElseThrow().state());
        assertEquals(StageState.INVALIDATED, runStore.node(runId, "S4").orElseThrow().state());
        assertEquals(StageState.INVALIDATED, runStore.node(runId, "S5").orElseThrow().state());
    }

    @Test
    @DisplayName("OUTCOME 4/4: ESCALATED — records what exceeds authority, no automatic state effect")
    void escalatedRecordsWithNoStateEffect() {
        UUID runId = freshRun();
        driveToS4Gate(runId);

        GateDecision decision = new GateDecision(runId, "S4", 4, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.ESCALATED, new Actor("human", "the reviewer"), T0,
                "this exceeds my authority to decide alone", "docs/governance/gate-decisions/gate-04.md",
                List.of(), "the release owner", null);
        gateStore.recordDecision(decision);
        outcomeHandler.apply(runId, "S4", decision);

        assertEquals(StageState.AWAITING_APPROVAL, runStore.node(runId, "S4").orElseThrow().state(),
                "an escalation is a governance record for someone with more authority, never itself a "
                        + "transition");
    }

    // ==============================================================================================
    // SILENCE — the timeout path, explicitly its own test (T068's Done bar names this specifically)
    // ==============================================================================================

    @Test
    @DisplayName("SILENCE: no decision within the wait produces SAFE_STOP — NEVER an implicit approval")
    void silenceProducesSuspensionNeverApproval() {
        UUID runId = freshRun();
        driveToS4Gate(runId);

        // The wait deadline (PVT-006) has passed with nothing decided. Constitution III: silence is
        // never approval — there is no GateOutcome value silence could even construct (T058), so the
        // only path from "nothing happened" is suspension, proven here against the real handler.
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");

        assertEquals(RunState.SAFE_STOP, runStore.run(runId).orElseThrow().state(),
                "SILENCE REPORT: run " + runId + " suspended on gate S4 (UNRESOLVED_AMBIGUITY) after "
                        + "the wait deadline elapsed with no decision — this is SAFE_STOP, not APPROVED");
        assertEquals(StageState.AWAITING_APPROVAL, runStore.node(runId, "S4").orElseThrow().state(),
                "the gated stage's own state is preserved exactly, not advanced by the silence");

        // And the freeze is real, not merely undisturbed so far: the same genuine-attempt proof
        // SilenceTest establishes in full — repeated once here so this sweep does not just assert the
        // run LOOKS suspended, but that a downstream transition is structurally refused because of it.
        assertThrows(IllegalStateException.class,
                () -> runStore.transitionNode(runId, "S4", StageState.AWAITING_APPROVAL,
                        StageState.SUCCEEDED, "attempted advance while suspended"));
    }

    // ==============================================================================================
    // MATERIALIZATION REFUSAL (T062)
    // ==============================================================================================

    @Test
    @DisplayName("MATERIALIZATION REFUSAL: a decision naming a record that does not exist cannot take effect")
    void materializationRefusesAnUnbackedDecision() {
        UUID runId = freshRun();
        driveToS4Gate(runId);

        GateDecision decision = new GateDecision(runId, "S4", 4, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.APPROVED, new Actor("human", "the owner"), T0, "resolved",
                "docs/governance/gate-decisions/does-not-exist-" + UUID.randomUUID() + ".md",
                List.of(), null, null);

        // The domain object builds fine — repositoryRecordPath is shape-checked, not filesystem-checked,
        // by GateDecision's own constructor. MaterializationCheck is the layer that looks at the
        // filesystem, and it is what a caller MUST run before treating the decision as authorizing
        // anything (CR-001, FR-ORC-013).
        assertThrows(UnmaterializedDecisionException.class,
                () -> MaterializationCheck.requireMaterialized(decision));

        // Nothing advanced: the refusal happened before anyone applied the decision's effect.
        assertEquals(StageState.AWAITING_APPROVAL, runStore.node(runId, "S4").orElseThrow().state());
    }

    // ==============================================================================================
    // AUTHORITY REFUSAL (T063, EC-024)
    // ==============================================================================================

    @Test
    @DisplayName("AUTHORITY REFUSAL: a non-human actor cannot ever record a gate decision, any outcome")
    void nonHumanActorIsRefusedStructurally() {
        UUID runId = freshRun();
        driveToS4Gate(runId);

        Actor system = new Actor("system", "the-engine");
        assertFalse(ActorAuthority.isAuthorized(system, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.APPROVED), "ActorAuthority's own predicate must already say no");

        // And the real control: GateDecision's own constructor refuses it structurally, before a caller
        // that skipped consulting ActorAuthority could ever reach the store.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new GateDecision(runId, "S4", 4, GateClass.UNRESOLVED_AMBIGUITY,
                        GateOutcome.APPROVED, system, T0, "attempted by the engine itself",
                        "docs/governance/gate-decisions/gate-05.md", List.of(), null, null));
        assertTrue(refused.getMessage().contains("EC-024"), refused.getMessage());

        assertEquals(StageState.AWAITING_APPROVAL, runStore.node(runId, "S4").orElseThrow().state(),
                "the refused decision never existed to apply, so nothing advanced");
    }

    // ==============================================================================================
    // ALL THREE NO-PLAN OPTIONS (T065), composed within this same US2 sweep
    // ==============================================================================================

    @Test
    @DisplayName("NO-PLAN 1/3: governance-only — S7 SKIPPED, downstream free to continue")
    void noPlanGovernanceOnly() {
        UUID runId = freshRun();
        driveToS7(runId);

        noPlanGate.governanceOnly(runId, "S7", "the owner", "scope covered by existing behaviour");

        assertEquals(StageState.SKIPPED, runStore.node(runId, "S7").orElseThrow().state());
    }

    @Test
    @DisplayName("NO-PLAN 2/3: human-implemented — SUCCEEDED, executorKindUsed HUMAN")
    void noPlanHumanImplemented() {
        UUID runId = freshRun();
        driveToS7(runId);

        noPlanGate.humanImplemented(runId, "S7", "the owner", "implemented the change by hand");

        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S7").orElseThrow().state());
        assertEquals(ExecutorKind.HUMAN, executorKindOf(runId, "S7"));
    }

    @Test
    @DisplayName("NO-PLAN 3/3: abandon — the run terminates as ABANDONED, by human decision")
    void noPlanAbandon() {
        UUID runId = freshRun();
        driveToS7(runId);

        noPlanGate.abandon(runId, "the owner", "not worth implementing manually");

        var run = runStore.run(runId).orElseThrow();
        assertEquals(RunState.ABANDONED, run.state());
        assertEquals(RunState.ABANDONED, run.terminalState());
    }

    private ExecutorKind executorKindOf(UUID runId, String nodeKey) {
        try (var c = connections.get(); var ps = c.prepareStatement(
                "SELECT executor_kind_used FROM stage_node WHERE run_id = ? AND node_key = ?")) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String kind = rs.getString(1);
                return kind == null ? null : ExecutorKind.valueOf(kind);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
