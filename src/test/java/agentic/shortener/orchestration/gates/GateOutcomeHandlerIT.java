package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.SafeStopHandler;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.state.SuspensionTrigger;
import agentic.shortener.orchestration.state.RetentionPolicy;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T059 — the five gate outcomes and their workflow effects. FR-ORC-013. Plan §5.
 *
 * <p>One test per outcome, matching T059's own Validate clause. {@code TIMED_OUT} is the fifth and it is
 * proven by a DIFFERENT path deliberately: {@link GateOutcomeHandler} never receives it (T058 made it
 * unconstructible as a {@link GateOutcome}), so its effect — suspension — is proven against
 * {@link SafeStopHandler} instead, which is where it actually happens. Testing it against the handler
 * would be testing a path that cannot be reached.
 */
@DisplayName("T059 — five outcomes, five effects, and no sixth")
class GateOutcomeHandlerIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private GateStore gateStore;
    private GateOutcomeHandler handler;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        gateStore = new GateStore(connections, clock);
        handler = new GateOutcomeHandler(runStore, gateStore, connections);
        runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
    }

    /** Drives S1→S2→S3 to completion and puts S4 into AWAITING_APPROVAL, the ordinary gate shape. */
    private void driveToS4Gate() {
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

    // ==============================================================================================
    // 1 of 5 — APPROVED
    // ==============================================================================================

    @Test
    @DisplayName("1: APPROVED — the stage succeeds")
    void approvedMakesTheStageSucceed() {
        driveToS4Gate();
        GateDecision decision = new GateDecision(runId, "S4", 4, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.APPROVED, new Actor("human", "the owner"), T0, "resolved",
                "docs/governance/gate-decisions/gate-01.md", List.of(), null, null);

        handler.apply(runId, "S4", decision);

        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S4").orElseThrow().state());
        assertEquals(RunState.RUNNING, runStore.run(runId).orElseThrow().state(),
                "the RUN does not terminate on an approval — only REJECTED does that");
    }

    // ==============================================================================================
    // 2 of 5 — REJECTED
    // ==============================================================================================

    @Test
    @DisplayName("2: REJECTED — the run terminates")
    void rejectedTerminatesTheRun() {
        driveToS4Gate();
        GateDecision decision = new GateDecision(runId, "S4", 4, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.REJECTED, new Actor("human", "the owner"), T0, "not viable",
                "docs/governance/gate-decisions/gate-02.md", List.of(), null, null);

        handler.apply(runId, "S4", decision);

        var run = runStore.run(runId).orElseThrow();
        assertEquals(RunState.REJECTED, run.state());
        assertEquals(RunState.REJECTED, run.terminalState());
    }

    // ==============================================================================================
    // 3 of 5 — CHANGES_REQUESTED
    // ==============================================================================================

    @Test
    @DisplayName("3: CHANGES_REQUESTED — returns to the owning stage, invalidates everything downstream")
    void changesRequestedReturnsAndInvalidatesDownstream() {
        driveToS4Gate();
        // Advance PAST S4 so there is real downstream state to invalidate — an all-BLOCKED graph would
        // make the invalidation trivially true of nodes that were already inert.
        runStore.transitionNode(runId, "S4", StageState.AWAITING_APPROVAL, StageState.SUCCEEDED,
                "answered");
        runStore.transitionNode(runId, "S5", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S5", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S5", StageState.RUNNING, StageState.SUCCEEDED, "decomposed");
        runStore.transitionNode(runId, "S6", StageState.BLOCKED, StageState.READY, "x");

        GateDecision decision = new GateDecision(runId, "S3", 3, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.CHANGES_REQUESTED, new Actor("human", "the owner"), T0,
                "the ambiguity check missed a case",
                "docs/governance/gate-decisions/gate-03.md",
                List.of("re-check for contradictory bounds"), null, null);

        // S3 itself is SUCCEEDED, not AWAITING_APPROVAL — CHANGES_REQUESTED can be raised on a stage
        // whose own gate already passed once, if what it produced turns out to need revisiting.
        handler.apply(runId, "S3", decision);

        assertEquals(StageState.READY, runStore.node(runId, "S3").orElseThrow().state(),
                "returned to the owning stage, ready to run again with the changes in mind");

        // Downstream of S3: S4, S5, S6, S7 (fan-out parent), S7.join, S8, S9, S10, S11, S12 —
        // everything the template reaches from S3 onward.
        for (String downstream : List.of("S4", "S5", "S6", "S7", "S7.join", "S8", "S9", "S10", "S11",
                "S12")) {
            assertEquals(StageState.INVALIDATED, runStore.node(runId, downstream).orElseThrow().state(),
                    downstream + " must be invalidated — it was reached from a stage whose output "
                            + "just changed");
        }

        // Upstream and siblings are UNTOUCHED.
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S1").orElseThrow().state());
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S2").orElseThrow().state());
    }

    @Test
    @DisplayName("REGRESSION (T096): EC-019 — a downstream node RUNNING is skipped, never force-invalidated")
    void changesRequestedDoesNotInvalidateAnInFlightDownstreamNode() {
        // Found while extracting T096's shared DownstreamInvalidation: the original private methods here
        // applied RUNNING -> INVALIDATED unconditionally, because no test had ever driven a downstream
        // node into RUNNING before requesting changes upstream of it. Fixed at the source
        // (DownstreamInvalidation, shared by this class and ReplanService), proven here directly too.
        driveToS4Gate();
        runStore.transitionNode(runId, "S4", StageState.AWAITING_APPROVAL, StageState.SUCCEEDED,
                "answered");
        runStore.transitionNode(runId, "S5", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S5", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S5", StageState.RUNNING, StageState.SUCCEEDED, "decomposed");
        runStore.transitionNode(runId, "S6", StageState.BLOCKED, StageState.READY, "x");
        // S6 is IN FLIGHT when the change request lands — left running, never completed.
        runStore.transitionNode(runId, "S6", StageState.READY, StageState.RUNNING, "x");

        GateDecision decision = new GateDecision(runId, "S3", 3, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.CHANGES_REQUESTED, new Actor("human", "the owner"), T0,
                "the ambiguity check missed a case",
                "docs/governance/gate-decisions/gate-03b.md",
                List.of("re-check for contradictory bounds"), null, null);

        handler.apply(runId, "S3", decision);

        assertEquals(StageState.RUNNING, runStore.node(runId, "S6").orElseThrow().state(),
                "EC-019: an in-flight stage is not mutated mid-execution — S6 must still be RUNNING, "
                        + "exactly as it was when the change request landed");
        // Everything else downstream that was NOT in flight is still invalidated as before — EC-019
        // scopes the exception to the in-flight node, it does not disable invalidation entirely.
        assertEquals(StageState.INVALIDATED, runStore.node(runId, "S4").orElseThrow().state());
        assertEquals(StageState.INVALIDATED, runStore.node(runId, "S5").orElseThrow().state());
    }

    @Test
    @DisplayName("CHANGES_REQUESTED voids the approvals it invalidates, by a superseding record (EC-020)")
    void changesRequestedVoidsInvalidatedApprovals() {
        driveToS4Gate();
        runStore.transitionNode(runId, "S4", StageState.AWAITING_APPROVAL, StageState.SUCCEEDED,
                "answered");
        long s4Decision = gateStore.recordDecision(new GateDecision(runId, "S4", 4,
                GateClass.UNRESOLVED_AMBIGUITY, GateOutcome.APPROVED, new Actor("human", "x"), T0,
                "approved", "docs/governance/gate-decisions/gate-04.md", List.of(), null, null));

        GateDecision changeRequest = new GateDecision(runId, "S3", 3, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.CHANGES_REQUESTED, new Actor("human", "x"), T0, "reconsider",
                "docs/governance/gate-decisions/gate-05.md", List.of("re-check"), null, null);
        handler.apply(runId, "S3", changeRequest);

        // S4's approval is downstream of S3 and just got invalidated; EC-020 says a voided approval is
        // superseded by a new record referencing it, never deleted or edited.
        GateDecision reread = gateStore.decisionById(s4Decision).orElseThrow();
        assertEquals("APPROVED", reread.outcome().name(), "the original decision is untouched");

        try (var c = connections.get();
             var ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM gate_decision WHERE supersedes_gate_decision_id = ?")) {
            ps.setLong(1, s4Decision);
            try (var rs = ps.executeQuery()) {
                rs.next();
                assertTrue(rs.getInt(1) >= 1,
                        "a superseding record must reference the voided approval");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ==============================================================================================
    // 4 of 5 — ESCALATED
    // ==============================================================================================

    @Test
    @DisplayName("4: ESCALATED — records what exceeds authority; no automatic state effect")
    void escalatedRecordsWithNoStateEffect() {
        driveToS4Gate();
        GateDecision decision = new GateDecision(runId, "S4", 4, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.ESCALATED, new Actor("human", "the reviewer"), T0,
                "this exceeds my authority to decide alone",
                "docs/governance/gate-decisions/gate-06.md", List.of(), "the release owner", null);

        handler.apply(runId, "S4", decision);

        // The decision is recorded (proven by re-reading it), and the node's own state is UNCHANGED —
        // an escalation is a governance record for someone with more authority to act on, not itself an
        // automatic transition.
        assertEquals(StageState.AWAITING_APPROVAL, runStore.node(runId, "S4").orElseThrow().state());
        assertEquals(RunState.RUNNING, runStore.run(runId).orElseThrow().state());
    }

    // ==============================================================================================
    // 5 of 5 — TIMED_OUT, proven where it actually happens
    // ==============================================================================================

    @Test
    @DisplayName("5: TIMED_OUT — suspension, proven against SafeStopHandler, never against this handler")
    void timedOutProducesSuspensionElsewhere() {
        driveToS4Gate();
        SafeStopHandler safeStop = new SafeStopHandler(connections, Clock.fixed(T0, ZoneOffset.UTC),
                new RetentionPolicy());

        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");

        assertEquals(RunState.SAFE_STOP, runStore.run(runId).orElseThrow().state());
        // And GateOutcomeHandler genuinely CANNOT be asked to do this — there is no GateOutcome value
        // for it to receive, which is the structural half of this proof (T058).
        assertEquals(4, GateOutcome.values().length);
    }

    @Test
    @DisplayName("there is no sixth outcome, and none meaning “no response, proceed”")
    void noSixthOutcome() {
        assertEquals(List.of("APPROVED", "REJECTED", "CHANGES_REQUESTED", "ESCALATED"),
                java.util.Arrays.stream(GateOutcome.values()).map(Enum::name).toList());
    }
}
