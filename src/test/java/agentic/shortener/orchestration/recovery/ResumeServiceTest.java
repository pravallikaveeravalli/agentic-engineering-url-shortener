package agentic.shortener.orchestration.recovery;

import agentic.shortener.orchestration.graph.ArtifactWriteGuard;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.ArtifactWritePolicy;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T093 — resumption across an orchestrator-process restart. FR-ORC-018, NFR-REL-003, SC-006.
 * EC-015, EC-016.
 *
 * <p>"Process killed at every stage boundary; each resumes" (T093's own Validate clause), driven
 * literally: every node that legitimately passes through {@code RUNNING} in the current build — S1, S2,
 * S3, S5, S6, S7, S8, S9, S10, S11, S12 — is parameterized, each on a FRESH run driven up to exactly that
 * node being {@code RUNNING} and left there, simulating the process dying mid-stage. S4 is excluded: a
 * human gate goes {@code BLOCKED -> AWAITING_APPROVAL} or {@code -> SKIPPED} directly and never passes
 * through {@code RUNNING} in this build, so there is no crash-mid-execution scenario for it to resume from.
 * {@code S7.join} is excluded for the same reason: a join is satisfied by its parents' states, not executed.
 *
 * <p>Each node is exercised BOTH ways EC-016 requires being told apart: with a committed artifact already
 * persisted (the crash landed between the effect and its recording) and with none (nothing was committed).
 */
@DisplayName("T093 — resumption reconciles every RUNNING node to what its persisted records substantiate")
class ResumeServiceTest extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private ArtifactWriteGuard artifacts;
    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        artifacts = new ArtifactWriteGuard(connections, clock);
        resumeService = new ResumeService(runStore, connections, new RunLease(connections, clock));
    }

    private ResumeService.ResumeOutcome resumeNow(UUID runId) {
        return resumeService.resume(runId)
                .orElseThrow(() -> new AssertionError("expected the lease to be free"));
    }

    private UUID freshRun() {
        UUID runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
        return runId;
    }

    /** The full, fixed sequence of nodes this build's StageTemplate.standard() runs through in order. */
    private static final List<String> SEQUENCE =
            List.of("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10", "S11", "S12");

    /**
     * Drives every node BEFORE {@code target} to SUCCEEDED (S4 to SKIPPED — no material ambiguity, the
     * ordinary path), then moves {@code target} itself to RUNNING and stops — the process "dies" there.
     */
    private void driveToRunning(UUID runId, String target) {
        for (String key : SEQUENCE) {
            if (key.equals(target)) {
                if (!key.equals("S1")) {
                    runStore.transitionNode(runId, key, StageState.BLOCKED, StageState.READY, "x");
                }
                runStore.transitionNode(runId, key, StageState.READY, StageState.RUNNING, "x");
                return;
            }
            if (key.equals("S4")) {
                runStore.transitionNode(runId, key, StageState.BLOCKED, StageState.SKIPPED,
                        "no material ambiguity; no_clarification_reason recorded");
            } else {
                if (!key.equals("S1")) {
                    runStore.transitionNode(runId, key, StageState.BLOCKED, StageState.READY, "x");
                }
                runStore.transitionNode(runId, key, StageState.READY, StageState.RUNNING, "x");
                runStore.transitionNode(runId, key, StageState.RUNNING, StageState.SUCCEEDED, "x");
            }
        }
        throw new IllegalArgumentException("not in SEQUENCE: " + target);
    }

    @ParameterizedTest(name = "{0}: RUNNING with a COMMITTED artifact resumes to SUCCEEDED, never re-executed")
    @ValueSource(strings = {"S1", "S2", "S3", "S5", "S6", "S7", "S8", "S9", "S10", "S11", "S12"})
    @DisplayName("EC-016: a committed effect is substantiated, not redone")
    void runningWithCommittedArtifactResumesToSucceeded(String nodeKey) {
        UUID runId = freshRun();
        driveToRunning(runId, nodeKey);
        // The crash landed BETWEEN the effect and its recording (EC-016) — the artifact IS there.
        ArtifactWriteGuard.Result written = artifacts.write(runId, nodeKey + ".output", "committed content",
                nodeKey, List.of(), ArtifactWritePolicy.SERIALIZE);
        assertTrue(written.accepted());

        ResumeService.ResumeOutcome outcome = resumeNow(runId);

        assertEquals(List.of(nodeKey), outcome.confirmedSucceeded());
        assertEquals(List.of(), outcome.resetToReady());
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, nodeKey).orElseThrow().state());
    }

    @ParameterizedTest(name = "{0}: RUNNING with NO artifact resumes to READY, safe to re-attempt")
    @ValueSource(strings = {"S1", "S2", "S3", "S5", "S6", "S7", "S8", "S9", "S10", "S11", "S12"})
    @DisplayName("EC-015: nothing committed means nothing to duplicate — reset, not relabelled")
    void runningWithNoArtifactResumesToReady(String nodeKey) {
        UUID runId = freshRun();
        driveToRunning(runId, nodeKey);
        // No artifact written — the crash landed before anything was committed.

        ResumeService.ResumeOutcome outcome = resumeNow(runId);

        assertEquals(List.of(), outcome.confirmedSucceeded());
        assertEquals(List.of(nodeKey), outcome.resetToReady());
        assertEquals(StageState.READY, runStore.node(runId, nodeKey).orElseThrow().state());
    }

    // ==============================================================================================
    // Idempotence and the substantiation guard itself
    // ==============================================================================================

    @Test
    @DisplayName("resuming a run with nothing RUNNING is a no-op — safe to call after an already-clean run")
    void resumingAnAlreadySettledRunIsANoOp() {
        UUID runId = freshRun();
        // S1 fully settled; nothing left RUNNING.
        runStore.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "x");

        ResumeService.ResumeOutcome outcome = resumeNow(runId);

        assertEquals(List.of(), outcome.confirmedSucceeded());
        assertEquals(List.of(), outcome.resetToReady());
    }

    @Test
    @DisplayName("calling resume() twice is safe — the second call finds nothing left to reconcile")
    void resumingTwiceIsSafe() {
        UUID runId = freshRun();
        driveToRunning(runId, "S2");

        ResumeService.ResumeOutcome first = resumeNow(runId);
        assertEquals(List.of("S2"), first.resetToReady());

        ResumeService.ResumeOutcome second = resumeNow(runId);
        assertEquals(List.of(), second.confirmedSucceeded());
        assertEquals(List.of(), second.resetToReady());
    }

    @Test
    @DisplayName("MULTIPLE nodes RUNNING at once (parallel branches) each resolve independently")
    void multipleRunningNodesResolveIndependently() {
        // S8 and S10 are siblings after the S7 join in the real topology, but this test only needs two
        // nodes simultaneously RUNNING to prove independence — driving both directly is sufficient and
        // does not depend on the join actually having fired.
        UUID runId = freshRun();
        runStore.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S2", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S2", StageState.READY, StageState.RUNNING, "x");
        // S1 committed, S2 not.
        artifacts.write(runId, "S1.output", "content", "S1", List.of(), ArtifactWritePolicy.SERIALIZE);

        ResumeService.ResumeOutcome outcome = resumeNow(runId);

        assertEquals(List.of("S1"), outcome.confirmedSucceeded());
        assertEquals(List.of("S2"), outcome.resetToReady());
    }

    // ==============================================================================================
    // T095, EC-026 — two concurrent resume attempts on the SAME run; exactly one advances
    // ==============================================================================================

    @Test
    @DisplayName("T095 EC-026: two REAL concurrent resume() calls on one run — exactly one performs the work")
    void concurrentResumeAttemptsExactlyOneAdvances() throws Exception {
        UUID runId = freshRun();
        driveToRunning(runId, "S3");

        // Two independent ResumeService instances (as two orchestrator processes racing to resume the
        // same persisted run would be), each with its own connection, released to fire as close to
        // simultaneously as the JVM allows via a shared start latch.
        RunLease sharedLease = new RunLease(connections, Clock.fixed(T0, ZoneOffset.UTC));
        ResumeService first = new ResumeService(runStore, connections, sharedLease);
        ResumeService second = new ResumeService(runStore, connections, sharedLease);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<ResumeService.ResumeOutcome>> firstResult = pool.submit(() -> {
                start.await();
                return first.resume(runId);
            });
            Future<Optional<ResumeService.ResumeOutcome>> secondResult = pool.submit(() -> {
                start.await();
                return second.resume(runId);
            });
            start.countDown();

            Optional<ResumeService.ResumeOutcome> a = firstResult.get(10, TimeUnit.SECONDS);
            Optional<ResumeService.ResumeOutcome> b = secondResult.get(10, TimeUnit.SECONDS);

            AtomicInteger presentCount = new AtomicInteger(0);
            a.ifPresent(o -> presentCount.incrementAndGet());
            b.ifPresent(o -> presentCount.incrementAndGet());

            assertEquals(1, presentCount.get(),
                    "EC-026: exactly one of two concurrent resume attempts must advance; the other must "
                            + "see the lease already held and do no reconciliation work at all. Got a="
                            + a + " b=" + b);
            // And the run itself ends up correctly resumed regardless of which of the two won the race.
            assertEquals(StageState.READY, runStore.node(runId, "S3").orElseThrow().state());
        } finally {
            pool.shutdownNow();
        }
    }
}
