package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.executor.ExecutorKind;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T065 — the no-change-plan gate, with three options. FR-ORC-031 (CR-001). EC-040.
 *
 * <p>Deterministic S7 reached with no change plan for the requirement offers exactly three options, never
 * a fourth and never a silent fall-through: governance-only, human-implemented, or abandon.
 *
 * <p><strong>A labelled no-op that flows onward is quiet pretending</strong> (T065's own Guard). Proceeding
 * with nothing implemented requires a recorded human decision — this class IS that decision point, and
 * every option requires a reason naming who decided and why.
 */
@DisplayName("T065 — the no-change-plan gate's three options, and no fourth")
class NoPlanGateIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private NoPlanGate noPlanGate;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        noPlanGate = new NoPlanGate(runStore);
        runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
        driveToS7();
    }

    /**
     * Drives S1..S6 to completion, leaving S7 READY — deterministic S7 reached, no plan to apply.
     * S3→S4 is conditional and S4 is SKIPPED here (no material ambiguity), so S5 becomes ready once
     * S3 and S4 both settle.
     */
    private void driveToS7() {
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
    // option 1 — governance-only
    // ==============================================================================================

    @Test
    @DisplayName("1: governance-only — S7 is SKIPPED, labelled, downstream is free to continue")
    void governanceOnlyLabelsAndSkips() {
        noPlanGate.governanceOnly(runId, "S7", "the owner", "scope covered by existing behaviour");

        var s7 = runStore.node(runId, "S7").orElseThrow();
        assertEquals(StageState.SKIPPED, s7.state());
        assertTrue(StageState.SKIPPED.satisfiesJoin(),
                "SKIPPED must satisfy a downstream join (T078), or a governance-only run would stall "
                        + "S7.join and \"downstream continues\" would be false");

        try (var c = connections.get(); var ps = c.prepareStatement(
                "SELECT reason FROM state_transition WHERE run_id = ? AND node_key = 'S7' "
                        + "ORDER BY state_transition_id DESC LIMIT 1")) {
            ps.setObject(1, runId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getString("reason").contains("intentionally skipped by human decision"),
                        "labelled exactly as T065's own Artifact requires: " + rs.getString("reason"));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("negative: a labelled no-op does NOT itself advance anything downstream")
    void labelledNoOpDoesNotAdvanceDownstream() {
        // "Downstream continues" means the GRAPH remains able to progress — SKIPPED satisfies a join
        // — not that this call itself drives S8 forward. A labelled no-op that flowed onward on its
        // own would be quiet pretending (T065's own Guard).
        noPlanGate.governanceOnly(runId, "S7", "the owner", "scope covered by existing behaviour");

        assertEquals(StageState.BLOCKED, runStore.node(runId, "S8").orElseThrow().state(),
                "S8 must still be BLOCKED — nothing but the engine loop (not this class) may advance "
                        + "it, and that loop is not what was called here");
    }

    // ==============================================================================================
    // option 2 — human-implemented
    // ==============================================================================================

    @Test
    @DisplayName("2: human-implemented — SUCCEEDED, executor kind HUMAN, ready for real testing to judge")
    void humanImplementedRecordsHumanKind() {
        noPlanGate.humanImplemented(runId, "S7", "the owner", "implemented the change by hand");

        var s7 = runStore.node(runId, "S7").orElseThrow();
        assertEquals(StageState.SUCCEEDED, s7.state(),
                "SUCCEEDED, not SKIPPED — real testing (S8) judges what was implemented");
        assertEquals(ExecutorKind.HUMAN, executorKindOf("S7"));
    }

    // ==============================================================================================
    // option 3 — abandon
    // ==============================================================================================

    @Test
    @DisplayName("3: abandon — the run terminates as ABANDONED, by human decision, not retention")
    void abandonTerminatesTheRun() {
        noPlanGate.abandon(runId, "the owner", "not worth implementing manually");

        var run = runStore.run(runId).orElseThrow();
        assertEquals(RunState.ABANDONED, run.state());
        assertEquals(RunState.ABANDONED, run.terminalState());

        try (var c = connections.get(); var ps = c.prepareStatement(
                "SELECT reason FROM state_transition WHERE run_id = ? AND node_key IS NULL "
                        + "ORDER BY state_transition_id DESC LIMIT 1")) {
            ps.setObject(1, runId);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getString("reason").contains("not worth implementing manually"));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ==============================================================================================
    // no fourth option
    // ==============================================================================================

    @Test
    @DisplayName("there is no fourth option, and no configuration selects one automatically")
    void noFourthOption() {
        assertEquals(3, java.util.Arrays.stream(NoPlanGate.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic())
                .count(),
                "exactly three public decision methods — governanceOnly, humanImplemented, abandon");
    }

    private ExecutorKind executorKindOf(String nodeKey) {
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
