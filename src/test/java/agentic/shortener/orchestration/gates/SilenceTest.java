package agentic.shortener.orchestration.gates;

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
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task T061 — silence produces suspension. FR-ORC-013, NFR-AUT-002, SC-005.
 *
 * <p><strong>This is the single most important negative test in the suite</strong> (T061's own Guard). If
 * it passes because the run advanced, the governance claim is void — so it does not merely check that
 * nothing currently <em>tries</em> to advance a suspended run; it drives a genuine attempt and proves the
 * store itself refuses it.
 *
 * <h2>A real gap this test found, fixed before it could pass for the wrong reason</h2>
 *
 * <p>{@code JdbcRunStore.transitionNode} built its {@code TransitionRequest} with
 * {@code TransitionRequest.of(from, to)}, which hardcodes {@code runState = RUNNING} unconditionally —
 * regardless of the run's actual state. {@code TransitionRules}' own rule 6/7 was already written to
 * refuse <em>any</em> transition while {@code runState() == SAFE_STOP} (its own comment: <em>"checked
 * FIRST... a transition that is otherwise legitimate slip through while the run was suspended"</em>), but
 * because the real state was never wired through, that rule could not have refused anything — a run
 * genuinely in {@code SAFE_STOP} still looked {@code RUNNING} to every node transition it evaluated. Fixed
 * by reading the run's actual current state before building the request. Without that fix, this test's own
 * {@link #suspendedRunRefusesADownstreamTransition()} would have failed to fail — the state was silently
 * advancing exactly as the Guard warns against, just not yet exercised by any built caller.
 */
@DisplayName("T061 — silence produces suspension, and nothing downstream executes")
class SilenceTest extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private SafeStopHandler safeStop;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        safeStop = new SafeStopHandler(connections, clock, new RetentionPolicy());
        runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");

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
    }

    @Test
    @DisplayName("no decision within the wait → SAFE_STOP, state preserved")
    void silenceProducesSuspension() {
        // Clock-controlled: the wait deadline (PVT-006, T060) has passed with nothing decided —
        // simulated directly, since there is no engine loop yet to notice a deadline on its own. What
        // this proves is the CONSEQUENCE of noticing, not the noticing itself.
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");

        assertEquals(RunState.SAFE_STOP, runStore.run(runId).orElseThrow().state());
        // State preserved: S4 is still exactly where it was, AWAITING_APPROVAL, not reset or advanced.
        assertEquals(StageState.AWAITING_APPROVAL, runStore.node(runId, "S4").orElseThrow().state());
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S3").orElseThrow().state());
    }

    @Test
    @DisplayName("ZERO downstream executions — a suspended run structurally REFUSES a node transition")
    void suspendedRunRefusesADownstreamTransition() {
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");

        // The genuine attempt: try to advance S4 as if the gate had been approved anyway. This must be
        // REFUSED BY THE STORE, not merely something no current caller happens to do — that is the
        // difference between a governance claim and an accident of what has been built so far.
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> runStore.transitionNode(runId, "S4", StageState.AWAITING_APPROVAL,
                        StageState.SUCCEEDED, "attempted advance while suspended"));
        assertEquals("SAFE_STOP", firstCauseSafeStopMessage(refused));

        // And nothing did advance: re-read confirms zero downstream progress.
        assertEquals(StageState.AWAITING_APPROVAL, runStore.node(runId, "S4").orElseThrow().state());
        for (String downstream : List.of("S5", "S6", "S7", "S7.join", "S8", "S9", "S10", "S11", "S12")) {
            StageState state = runStore.node(runId, downstream).orElseThrow().state();
            assertEquals(StageState.BLOCKED, state, downstream + " must still be BLOCKED: " + state);
        }
    }

    @Test
    @DisplayName("even a transition NOT touching the gated node is refused while the run is suspended")
    void evenAnUnrelatedNodeIsRefusedWhileSuspended() {
        // The rule is run-wide, not scoped to the node that triggered suspension — SAFE_STOP freezes
        // the whole run. Proven against a node that never had anything to do with the gate.
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");

        assertThrows(IllegalStateException.class,
                () -> runStore.transitionNode(runId, "S9", StageState.BLOCKED, StageState.READY,
                        "unrelated node, still refused"));
    }

    @Test
    @DisplayName("before suspension, the SAME transition is genuinely permitted — proving the refusal is real")
    void theSameTransitionIsPermittedBeforeSuspension() {
        // Without this, "refused while suspended" could be a coincidence of some OTHER rule always
        // refusing AWAITING_APPROVAL→SUCCEEDED. This shows the transition is legitimate on its own,
        // and it is specifically SAFE_STOP that then blocks it.
        runStore.transitionNode(runId, "S4", StageState.AWAITING_APPROVAL, StageState.SUCCEEDED,
                "approved before any suspension");
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S4").orElseThrow().state());
    }

    @Test
    @DisplayName("resuming on a human decision clears the freeze, and the transition succeeds again")
    void resumingClearsTheFreeze() {
        safeStop.suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");
        safeStop.resumeOnHumanDecision(runId, "the owner answered");

        assertEquals(RunState.RUNNING, runStore.run(runId).orElseThrow().state());
        // The freeze is specific to SAFE_STOP, not a one-way door — once resumed, the same transition
        // that was refused above now succeeds.
        runStore.transitionNode(runId, "S4", StageState.AWAITING_APPROVAL, StageState.SUCCEEDED,
                "approved after resumption");
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S4").orElseThrow().state());
    }

    private static String firstCauseSafeStopMessage(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor.getMessage() != null && cursor.getMessage().contains("SAFE_STOP")) {
                return "SAFE_STOP";
            }
            cursor = cursor.getCause();
        }
        return "not found in: " + t;
    }
}
