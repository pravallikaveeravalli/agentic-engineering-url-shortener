package agentic.shortener.orchestration.replan;

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
 * Persists {@link ReplanEvent}. Task T096. FR-ORC-019. "Done: ... replan event queryable."
 *
 * <p>One transaction for the event row and its two join tables (V11) — a replan event with no recorded
 * invalidated-node set, or none of its voided decisions, would be a governance record a reviewer cannot
 * fully act on, so it is written whole or not at all.
 */
public final class ReplanStore {

    private final ConnectionSource connections;
    private final Clock clock;

    public ReplanStore(ConnectionSource connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** @return the assigned {@code replan_event_id}, ready to be re-read via {@link #byId} */
    public long record(UUID runId, String cause, String triggeringNodeKey, List<String> invalidatedNodes,
                       List<Long> voidedDecisionIds) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(triggeringNodeKey, "triggeringNodeKey");
        Instant now = clock.instant();

        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO replan_event (run_id, cause, triggering_node_key, occurred_at) "
                                + "VALUES (?, ?, ?, ?) RETURNING replan_event_id")) {
                    ps.setObject(1, runId);
                    ps.setString(2, cause);
                    ps.setString(3, triggeringNodeKey);
                    ps.setTimestamp(4, Timestamp.from(now));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = rs.getLong(1);
                    }
                }
                if (!invalidatedNodes.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO replan_event_invalidated_node (replan_event_id, node_key) "
                                    + "VALUES (?, ?)")) {
                        for (String nodeKey : invalidatedNodes) {
                            ps.setLong(1, id);
                            ps.setString(2, nodeKey);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                if (!voidedDecisionIds.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO replan_event_voided_decision (replan_event_id, gate_decision_id) "
                                    + "VALUES (?, ?)")) {
                        for (Long decisionId : voidedDecisionIds) {
                            ps.setLong(1, id);
                            ps.setLong(2, decisionId);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                c.commit();
                return id;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to record a replan event for run " + runId, e);
        }
    }

    public Optional<ReplanEvent> byId(long replanEventId) {
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(
                "SELECT run_id, cause, triggering_node_key, occurred_at FROM replan_event "
                        + "WHERE replan_event_id = ?")) {
            ps.setLong(1, replanEventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UUID runId = rs.getObject("run_id", UUID.class);
                String cause = rs.getString("cause");
                String triggeringNodeKey = rs.getString("triggering_node_key");
                Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();

                return Optional.of(new ReplanEvent(replanEventId, runId, cause, triggeringNodeKey,
                        occurredAt, invalidatedNodesOf(c, replanEventId), voidedDecisionsOf(c, replanEventId)));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read replan event " + replanEventId, e);
        }
    }

    private List<String> invalidatedNodesOf(Connection c, long replanEventId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT node_key FROM replan_event_invalidated_node WHERE replan_event_id = ? "
                        + "ORDER BY node_key")) {
            ps.setLong(1, replanEventId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> nodes = new ArrayList<>();
                while (rs.next()) {
                    nodes.add(rs.getString(1));
                }
                return nodes;
            }
        }
    }

    private List<Long> voidedDecisionsOf(Connection c, long replanEventId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT gate_decision_id FROM replan_event_voided_decision WHERE replan_event_id = ? "
                        + "ORDER BY gate_decision_id")) {
            ps.setLong(1, replanEventId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
                return ids;
            }
        }
    }
}
