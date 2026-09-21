package agentic.shortener.orchestration.recovery;

import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A run-level lease making concurrent resumption of one persisted run impossible. Task T095. FR-ORC-018.
 * EC-026.
 *
 * <p><strong>The primary key on {@code run_lease.run_id} is the synchronization primitive</strong> — the
 * same shape {@link agentic.shortener.orchestration.graph.ArtifactWriteGuard} uses for concurrent artifact
 * writes (T076a). Two callers racing {@link #tryAcquire} both attempt the same {@code INSERT}; the
 * database allows exactly one, and the loser is told so by a constraint violation rather than by a
 * check-then-write race in application code — the loser never even reads a stale "is it free" answer,
 * because there is no read step to race.
 *
 * <p>Without this, {@link ResumeService#resume} would let two concurrent restarts of the orchestrator
 * process both reconcile the same run at once — each reading {@code RUNNING} nodes, each racing the other
 * to transition them. The per-node compare-and-set in {@code JdbcRunStore.transitionNode} (its own
 * {@code WHERE state = ?}) would stop either from corrupting a node's final state, but one of the two
 * would still fail loudly mid-reconciliation rather than the second resumer never starting at all — which
 * is the difference this class exists to make.
 */
public final class RunLease {

    private final ConnectionSource connections;
    private final Clock clock;

    public RunLease(ConnectionSource connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @return {@code true} if {@code holderId} now holds the lease on {@code runId}; {@code false} if
     *         another holder already does. Never throws for a losing attempt — losing the race is an
     *         ordinary outcome, not a failure
     */
    public boolean tryAcquire(UUID runId, UUID holderId) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(holderId, "holderId");
        Instant now = clock.instant();
        String sql = "INSERT INTO run_lease (run_id, holder_id, acquired_at) VALUES (?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setObject(2, holderId);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            if (e instanceof SQLException sql2 && isPrimaryKeyViolation(sql2)) {
                return false;
            }
            throw new IllegalStateException("failed to acquire the lease on run " + runId, e);
        }
    }

    /** PostgreSQL SQLSTATE for unique_violation — the same value {@code ArtifactWriteGuard} rests on. */
    private static boolean isPrimaryKeyViolation(SQLException e) {
        return "23505".equals(e.getSQLState());
    }

    /**
     * Releases the lease, but only for the holder that actually holds it — a caller releasing a lease it
     * never held (or held and already lost, somehow) must not be able to release someone else's.
     */
    public void release(UUID runId, UUID holderId) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(holderId, "holderId");
        String sql = "DELETE FROM run_lease WHERE run_id = ? AND holder_id = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setObject(2, holderId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("failed to release the lease on run " + runId, e);
        }
    }
}
