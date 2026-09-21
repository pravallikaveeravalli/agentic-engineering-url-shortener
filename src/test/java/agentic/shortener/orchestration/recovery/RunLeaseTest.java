package agentic.shortener.orchestration.recovery;

import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T095 — the run-level lease itself, at the unit level. FR-ORC-018. EC-026. See
 * {@code ResumeServiceTest.concurrentResumeAttemptsExactlyOneAdvances} for the same guarantee proven with
 * real concurrent threads, which is the shape EC-026 actually describes.
 */
@DisplayName("T095 — RunLease: exactly one holder at a time, per run")
class RunLeaseTest extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private RunLease lease;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        lease = new RunLease(connections, clock);
        runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");
    }

    @Test
    @DisplayName("a free run's lease is acquired")
    void aFreeLeaseIsAcquired() {
        assertTrue(lease.tryAcquire(runId, UUID.randomUUID()));
    }

    @Test
    @DisplayName("EC-026: a SECOND holder cannot acquire a lease the first still holds")
    void aSecondHolderCannotAcquireAHeldLease() {
        assertTrue(lease.tryAcquire(runId, UUID.randomUUID()));

        assertFalse(lease.tryAcquire(runId, UUID.randomUUID()),
                "the first holder still has it; a second acquire attempt must be refused, not queued or "
                        + "silently granted");
    }

    @Test
    @DisplayName("releasing frees the lease for the next holder")
    void releasingFreesTheLeaseForTheNextHolder() {
        UUID first = UUID.randomUUID();
        assertTrue(lease.tryAcquire(runId, first));
        lease.release(runId, first);

        assertTrue(lease.tryAcquire(runId, UUID.randomUUID()));
    }

    @Test
    @DisplayName("releasing a lease you do not hold is a no-op — it cannot release someone else's")
    void releasingALeaseYouDoNotHoldIsANoOp() {
        UUID holder = UUID.randomUUID();
        assertTrue(lease.tryAcquire(runId, holder));

        lease.release(runId, UUID.randomUUID()); // a different, uninvolved id

        assertFalse(lease.tryAcquire(runId, UUID.randomUUID()),
                "the real holder's lease must still stand — an unrelated release must not have freed it");
    }

    @Test
    @DisplayName("two DIFFERENT runs never contend — a lease is scoped per run")
    void twoDifferentRunsNeverContend() {
        UUID otherRun = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(otherRun, RunState.PENDING, RunState.RUNNING, "started");

        assertTrue(lease.tryAcquire(runId, UUID.randomUUID()));
        assertTrue(lease.tryAcquire(otherRun, UUID.randomUUID()),
                "a lease held on one run must never block a lease on a different run");
    }
}
