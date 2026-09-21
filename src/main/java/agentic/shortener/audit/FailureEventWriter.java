package agentic.shortener.audit;

import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The append-only writer for {@link FailureEvent}. Task T115. FR-ORC-024. ADR-010.
 *
 * <p>Mirrors {@link AuditWriter}'s contract exactly: this class only ever {@code INSERT}s (V3's {@code
 * REVOKE UPDATE, DELETE} on {@code failure_event} enforces the rest at the privilege level, proven by
 * {@code GovernanceImmutabilityIT}, T113), and a write failure is thrown rather than swallowed — the
 * caller composes it with {@code ProviderFailureTranslator.forStoreProvider()} the same way {@link
 * AuditWriter}'s own javadoc documents.
 */
public final class FailureEventWriter {

    private final ConnectionSource connections;

    public FailureEventWriter(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /** @return the assigned {@code failure_event_id}, ready to be re-read via {@link #byId} */
    public long write(FailureEvent event) {
        Objects.requireNonNull(event, "event");
        String sql = "INSERT INTO failure_event (run_id, node_key, category, detected_at, recovered_at, "
                + "recovery_mechanism, detail, recovery_started_at, human_wait_duration_ms, "
                + "individual_recovery_duration_ms, recovered) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "RETURNING failure_event_id";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, event.runId());
            ps.setString(2, event.stageId());
            ps.setString(3, event.failureCategory());
            ps.setTimestamp(4, Timestamp.from(event.failureDetectedAt()));
            setNullableTimestamp(ps, 5, event.recoveryCompletedAt());
            ps.setString(6, event.recoveryMechanism() != null ? event.recoveryMechanism().dbValue() : null);
            ps.setString(7, event.detail());
            setNullableTimestamp(ps, 8, event.recoveryStartedAt());
            ps.setLong(9, event.humanWaitDuration().toMillis());
            setNullableLong(ps, 10, event.individualRecoveryDuration());
            ps.setBoolean(11, event.recovered());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to write a failure event for run " + event.runId(), e);
        }
    }

    public Optional<FailureEvent> byId(long failureEventId) {
        String sql = "SELECT run_id, node_key, category, detected_at, recovered_at, recovery_mechanism, "
                + "detail, recovery_started_at, human_wait_duration_ms, individual_recovery_duration_ms, "
                + "recovered FROM failure_event WHERE failure_event_id = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, failureEventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String mechanism = rs.getString("recovery_mechanism");
                Long individualMs = rs.getObject("individual_recovery_duration_ms", Long.class);
                return Optional.of(new FailureEvent(failureEventId, rs.getObject("run_id", UUID.class),
                        rs.getString("node_key"), rs.getString("category"),
                        rs.getTimestamp("detected_at").toInstant(),
                        nullableInstant(rs.getTimestamp("recovery_started_at")),
                        nullableInstant(rs.getTimestamp("recovered_at")),
                        mechanism != null ? RecoveryMechanism.fromDbValue(mechanism) : null,
                        Duration.ofMillis(rs.getLong("human_wait_duration_ms")),
                        individualMs != null ? Duration.ofMillis(individualMs) : null,
                        rs.getBoolean("recovered"), rs.getString("detail")));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read failure event " + failureEventId, e);
        }
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index, java.time.Instant value)
            throws java.sql.SQLException {
        if (value != null) {
            ps.setTimestamp(index, Timestamp.from(value));
        } else {
            ps.setNull(index, Types.TIMESTAMP);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Duration value)
            throws java.sql.SQLException {
        if (value != null) {
            ps.setLong(index, value.toMillis());
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }

    private static java.time.Instant nullableInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
