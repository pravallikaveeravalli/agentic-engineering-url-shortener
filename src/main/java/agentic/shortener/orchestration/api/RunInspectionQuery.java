package agentic.shortener.orchestration.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Assembles the {@code RunInspection} view. Task T067. FR-ORC-008, FR-ORC-013. US-2 scenario 1.
 *
 * <p><strong>Framework-free</strong>, matching every other orchestration class built this session:
 * {@code RunInspectionController} (the thin {@code @RestController} wrapper) delegates here, so this class
 * is what {@code RunInspectionQueryIT} proves conforms to {@code contracts/openapi.yaml}'s
 * {@code RunInspection} schema without paying Spring's startup cost for every case.
 *
 * <p><strong>Built as explicit {@link JsonNode} construction</strong>, the same shape
 * {@code GateDecision.toJson()} already uses, rather than relying on automatic record-to-JSON
 * marshalling — it gives precise control over which optional fields are present-but-null (the contract
 * types most of them as {@code [type, 'null']}, meaning present is required even when the value is not)
 * versus genuinely absent.
 *
 * <p><strong>Nodes are keyed by {@code node_key}, and {@code dependsOn} is node keys, never stage
 * numbers.</strong> T067's own Guard is explicit about this — a reviewer following a dependency must be
 * able to go straight from one node's {@code dependsOn} entry to another node's {@code nodeKey} without an
 * intermediate lookup.
 *
 * <p><strong>Carries no creator credential</strong> (CN-012): nothing this class reads comes anywhere near
 * {@code creator} or {@code creator_credential} — it reads {@code workflow_run}, {@code stage_node},
 * {@code dependency_edge} and {@code approval_gate} only, none of which the application plane's
 * credential model touches.
 */
public final class RunInspectionQuery {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConnectionSource connections;

    public RunInspectionQuery(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /** @return empty when no such run exists — never a conformant-looking empty inspection */
    public Optional<JsonNode> inspect(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        try (Connection c = connections.get()) {
            ObjectNode run = readRun(c, runId);
            if (run == null) {
                return Optional.empty();
            }
            run.set("nodes", readNodes(c, runId));
            JsonNode pendingGate = readPendingGate(c, runId);
            run.set("pendingGate", pendingGate);
            // blockingCondition has no source yet — no policy-evaluation task has landed to produce
            // one. Explicitly null rather than omitted: the schema types it [string, null], and a reader
            // should see "nothing is blocking" as a stated fact, not an absent field that could be a bug.
            run.putNull("blockingCondition");
            return Optional.of(run);
        } catch (Exception e) {
            throw new IllegalStateException("failed to assemble inspection for run " + runId, e);
        }
    }

    private ObjectNode readRun(Connection c, UUID runId) throws Exception {
        String sql = "SELECT state, terminal_state, policy_set_version, suspension_reason, "
                + "auto_abandon_at FROM workflow_run WHERE run_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ObjectNode node = JSON.createObjectNode();
                node.put("runId", runId.toString());
                node.put("state", rs.getString("state"));
                putNullableText(node, "terminalState", rs.getString("terminal_state"));
                node.put("policySetVersion", rs.getString("policy_set_version"));
                putNullableText(node, "suspensionReason", rs.getString("suspension_reason"));
                Timestamp autoAbandon = rs.getTimestamp("auto_abandon_at");
                putNullableText(node, "autoAbandonAt",
                        autoAbandon == null ? null : autoAbandon.toInstant().toString());
                return node;
            }
        }
    }

    private ArrayNode readNodes(Connection c, UUID runId) throws Exception {
        // dependsOn is built from the edge table separately (below) and merged by node key, so each
        // node's own row query stays a single simple SELECT.
        Map<String, List<String>> dependsOn = readDependsOn(c, runId);

        ArrayNode nodes = JSON.createArrayNode();
        String sql = "SELECT node_key, stage_number, node_role, parent_node_key, name, state, "
                + "executor_class, executor_kind_used, attempts_used FROM stage_node "
                + "WHERE run_id = ? ORDER BY stage_number, node_key";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ObjectNode node = JSON.createObjectNode();
                    String nodeKey = rs.getString("node_key");
                    node.put("nodeKey", nodeKey);
                    node.put("stageNumber", rs.getInt("stage_number"));
                    node.put("nodeRole", rs.getString("node_role"));
                    putNullableText(node, "parentNodeKey", rs.getString("parent_node_key"));
                    node.put("name", rs.getString("name"));
                    node.put("state", rs.getString("state"));
                    node.put("executorClass", rs.getString("executor_class"));
                    putNullableText(node, "executorKindUsed", rs.getString("executor_kind_used"));
                    node.put("attemptsUsed", rs.getInt("attempts_used"));

                    ArrayNode deps = JSON.createArrayNode();
                    dependsOn.getOrDefault(nodeKey, List.of()).forEach(deps::add);
                    node.set("dependsOn", deps);

                    nodes.add(node);
                }
            }
        }
        return nodes;
    }

    /** Node key -> the node keys it depends on, i.e. every edge's source for a given target. */
    private Map<String, List<String>> readDependsOn(Connection c, UUID runId) throws Exception {
        Map<String, List<String>> dependsOn = new LinkedHashMap<>();
        String sql = "SELECT from_node_key, to_node_key FROM dependency_edge WHERE run_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    dependsOn.computeIfAbsent(rs.getString("to_node_key"), k -> new ArrayList<>())
                            .add(rs.getString("from_node_key"));
                }
            }
        }
        return dependsOn;
    }

    /** Null when no gate is pending — the schema types pendingGate as an object OR null. */
    private JsonNode readPendingGate(Connection c, UUID runId) throws Exception {
        // A gate is "pending" when it has been requested and no decision has yet answered it — the
        // same "still-standing" definition GateOutcomeHandler's voidApprovalIfAny query uses.
        String sql = "SELECT ag.gate_id, ag.node_key, ag.gate_class, ag.wait_deadline, "
                + "ag.disclosed_auto_abandon_at FROM approval_gate ag WHERE ag.run_id = ? "
                + "AND NOT EXISTS (SELECT 1 FROM gate_decision gd "
                + "WHERE gd.run_id = ag.run_id AND gd.gate_id = ag.gate_id) "
                + "ORDER BY ag.requested_at DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return JSON.nullNode();
                }
                ObjectNode gate = JSON.createObjectNode();
                gate.put("gateId", rs.getString("gate_id"));
                gate.put("nodeKey", rs.getString("node_key"));
                gate.put("gateClass", rs.getString("gate_class"));
                gate.put("waitDeadline", rs.getTimestamp("wait_deadline").toInstant().toString());
                gate.put("disclosedAutoAbandonAt",
                        rs.getTimestamp("disclosed_auto_abandon_at").toInstant().toString());
                return gate;
            }
        }
    }

    private static void putNullableText(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
