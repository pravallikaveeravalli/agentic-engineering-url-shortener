package agentic.shortener.orchestration.gates;

import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists {@link ApprovalGate} and {@link GateDecision}. Task T058. FR-ORC-013.
 *
 * <p>{@code approval_gate} and {@code gate_decision} are correlated by {@code (run_id, gate_id)}, not by
 * a foreign key — see V8's own comment on why: {@code gate_decision} has writers (T090's superseding-record
 * path) that predate this table and do not create an {@code approval_gate} row first, and retrofitting a
 * hard reference onto them was judged riskier than the correlation it would buy.
 */
public final class GateStore {

    private final ConnectionSource connections;
    private final Clock clock;

    public GateStore(ConnectionSource connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ==============================================================================================
    // ApprovalGate
    // ==============================================================================================

    public void requestGate(ApprovalGate gate) {
        Objects.requireNonNull(gate, "gate");
        String sql = "INSERT INTO approval_gate (run_id, gate_id, node_key, gate_class, wait_deadline, "
                + "disclosed_auto_abandon_at, requested_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, gate.runId());
            ps.setString(2, gate.gateId());
            ps.setString(3, gate.nodeKey());
            ps.setString(4, gate.gateClass().name());
            ps.setTimestamp(5, Timestamp.from(gate.waitDeadline()));
            ps.setTimestamp(6, Timestamp.from(gate.disclosedAutoAbandonAt()));
            ps.setTimestamp(7, Timestamp.from(gate.requestedAt()));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("failed to request gate '" + gate.gateId() + "'", e);
        }
    }

    public Optional<ApprovalGate> pendingGate(UUID runId, String gateId) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(gateId, "gateId");
        String sql = "SELECT node_key, gate_class, wait_deadline, disclosed_auto_abandon_at, "
                + "requested_at FROM approval_gate WHERE run_id = ? AND gate_id = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, gateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ApprovalGate(gateId, runId, rs.getString("node_key"),
                        GateClass.valueOf(rs.getString("gate_class")),
                        rs.getTimestamp("wait_deadline").toInstant(),
                        rs.getTimestamp("disclosed_auto_abandon_at").toInstant(),
                        rs.getTimestamp("requested_at").toInstant()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read gate '" + gateId + "'", e);
        }
    }

    /**
     * Whether ANY decision already exists for this (run, gate) pair — the HTTP surface's own 409 check
     * (T067a). Deliberately not "any still-standing decision": a human resubmitting to this endpoint is a
     * duplicate, not the internal superseding path {@code GateOutcomeHandler.voidApprovalIfAny} uses when a
     * downstream replan invalidates an earlier approval.
     */
    public boolean decisionRecorded(UUID runId, String gateId) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(gateId, "gateId");
        String sql = "SELECT 1 FROM gate_decision WHERE run_id = ? AND gate_id = ? LIMIT 1";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, gateId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to check for an existing decision on gate '" + gateId + "'", e);
        }
    }

    // ==============================================================================================
    // GateDecision
    // ==============================================================================================

    /**
     * @return the assigned {@code gate_decision_id}, ready to be re-read via {@link #decisionById}
     */
    public long recordDecision(GateDecision decision) {
        Objects.requireNonNull(decision, "decision");
        // One INSERT, every column named up front — gate_decision is append-only by privilege (no UPDATE
        // grant), so escalation_target has to arrive in the same statement as everything else, not in a
        // follow-up write.
        String sql = "INSERT INTO gate_decision (run_id, gate_id, gate_class, stage_number, outcome, "
                + "actor_type, actor_name, reason, decided_at, repository_record_path, "
                + "supersedes_gate_decision_id, escalation_target) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING gate_decision_id";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, decision.runId());
            ps.setString(2, decision.gateId());
            ps.setString(3, decision.gateClass().name());
            if (decision.stageNumber() != null) {
                ps.setInt(4, decision.stageNumber());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, decision.outcome().name());
            ps.setString(6, decision.actor().actorType());
            ps.setString(7, decision.actor().identity());
            ps.setString(8, decision.reason());
            ps.setTimestamp(9, Timestamp.from(decision.decidedAt()));
            ps.setString(10, decision.repositoryRecordPath());
            if (decision.supersedesDecisionId() != null) {
                ps.setLong(11, decision.supersedesDecisionId());
            } else {
                ps.setNull(11, java.sql.Types.BIGINT);
            }
            ps.setString(12, decision.escalationTarget());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                long id = rs.getLong(1);
                if (!decision.requestedChanges().isEmpty()) {
                    insertRequestedChanges(c, id, decision.requestedChanges());
                }
                return id;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to record decision on gate '" + decision.gateId()
                    + "'", e);
        }
    }

    private void insertRequestedChanges(Connection c, long decisionId, List<String> changes)
            throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO gate_decision_requested_change (gate_decision_id, ordinal, change_text) "
                        + "VALUES (?, ?, ?)")) {
            for (int i = 0; i < changes.size(); i++) {
                ps.setLong(1, decisionId);
                ps.setInt(2, i);
                ps.setString(3, changes.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public Optional<GateDecision> decisionById(long decisionId) {
        String sql = "SELECT run_id, gate_id, gate_class, stage_number, outcome, actor_type, "
                + "actor_name, reason, decided_at, repository_record_path, escalation_target, "
                + "supersedes_gate_decision_id FROM gate_decision WHERE gate_decision_id = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, decisionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Integer stageNumber = rs.getObject("stage_number", Integer.class);
                Long supersedes = rs.getObject("supersedes_gate_decision_id", Long.class);
                UUID runId = rs.getObject("run_id", UUID.class);

                return Optional.of(new GateDecision(String.valueOf(decisionId), runId,
                        rs.getString("gate_id"), stageNumber,
                        GateClass.valueOf(rs.getString("gate_class")),
                        GateOutcome.valueOf(rs.getString("outcome")),
                        new Actor(rs.getString("actor_type"), rs.getString("actor_name")),
                        rs.getTimestamp("decided_at").toInstant(),
                        rs.getString("reason"), rs.getString("repository_record_path"),
                        requestedChangesFor(c, decisionId), rs.getString("escalation_target"),
                        supersedes));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read decision " + decisionId, e);
        }
    }

    private List<String> requestedChangesFor(Connection c, long decisionId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT change_text FROM gate_decision_requested_change "
                        + "WHERE gate_decision_id = ? ORDER BY ordinal")) {
            ps.setLong(1, decisionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> changes = new ArrayList<>();
                while (rs.next()) {
                    changes.add(rs.getString(1));
                }
                return List.copyOf(changes);
            }
        }
    }
}
