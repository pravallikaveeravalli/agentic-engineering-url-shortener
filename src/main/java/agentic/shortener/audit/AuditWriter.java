package agentic.shortener.audit;

import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Objects;
import java.util.Optional;

/**
 * The append-only writer every orchestrator governance write goes through. Task T111. FR-ORC-023,
 * NFR-AUD-001. ADR-010.
 *
 * <h2>Fail-closed applies to orchestrator governance writes only</h2>
 *
 * <p>ADR-010's own scoping, repeated here because it decides this class's failure contract: no public
 * shortener operation writes an audit row, so create, resolve, and analytics read are structurally
 * incapable of audit-write failure — this class is never on THAT path. A write here failing is a control
 * -plane fact, and {@link #write} throws for it rather than swallowing it, matching every other low-level
 * store writer in this codebase ({@code JdbcRunStore}, {@code GateStore}, {@code ArtifactWriteGuard}).
 * <strong>The caller is expected to compose that exception with
 * {@code ProviderFailureTranslator.forStoreProvider()} and route a classified {@code UNAVAILABLE} through
 * bounded retry to {@code SafeStopHandler.suspend}</strong> — the exact composition
 * {@code StoreRestartResumeIT} (T094) already proves against a real store outage, reused here rather than
 * re-implemented: an audit write failure is a store failure like any other, not a new failure mode this
 * class invents its own handling for.
 *
 * <p>Immutability itself is NOT this class's job — it is enforced by privilege (V3's {@code REVOKE UPDATE,
 * DELETE}), proven in {@code AuditImmutabilityIT} (T028) and {@code GovernanceScanTest} (T113). This class
 * only ever {@code INSERT}s.
 */
public final class AuditWriter {

    private final ConnectionSource connections;

    public AuditWriter(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /** @return the assigned {@code audit_record_id}, ready to be re-read via {@link #byId} */
    public long write(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        String sql = "INSERT INTO audit_record (actor_type, action, occurred_at, affected_artifact, "
                + "result, reason, run_id, correlation_id, stage_number, executor_kind_used, "
                + "corrects_event_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING audit_record_id";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, event.actorType());
            ps.setString(2, event.action());
            ps.setTimestamp(3, Timestamp.from(event.occurredAt()));
            ps.setString(4, event.affectedArtifact());
            ps.setString(5, event.result());
            ps.setString(6, event.reason());
            ps.setObject(7, event.runId());
            // runId doubles as the correlation identifier for every event this writer produces — see
            // AuditEvent's own javadoc for why a separate value is never needed here.
            ps.setObject(8, event.runId());
            if (event.stageNumber() != null) {
                ps.setInt(9, event.stageNumber());
            } else {
                ps.setNull(9, Types.INTEGER);
            }
            ps.setString(10, event.executorKindUsed());
            if (event.correctsEventId() != null) {
                ps.setLong(11, event.correctsEventId());
            } else {
                ps.setNull(11, Types.BIGINT);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to write an audit event: " + event.action(), e);
        }
    }

    public Optional<AuditEvent> byId(long auditRecordId) {
        String sql = "SELECT run_id, actor_type, action, occurred_at, affected_artifact, result, reason, "
                + "stage_number, executor_kind_used, corrects_event_id FROM audit_record "
                + "WHERE audit_record_id = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, auditRecordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Integer stageNumber = rs.getObject("stage_number", Integer.class);
                Long correctsEventId = rs.getObject("corrects_event_id", Long.class);
                return Optional.of(new AuditEvent(auditRecordId, rs.getObject("run_id", java.util.UUID.class),
                        rs.getString("actor_type"), rs.getString("action"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getString("affected_artifact"),
                        rs.getString("result"), rs.getString("reason"), stageNumber,
                        rs.getString("executor_kind_used"), correctsEventId));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read audit event " + auditRecordId, e);
        }
    }
}
