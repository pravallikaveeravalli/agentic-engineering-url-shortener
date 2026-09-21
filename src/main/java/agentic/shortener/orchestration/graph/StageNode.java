package agentic.shortener.orchestration.graph;

import java.util.Objects;

/**
 * One node in a run's materialized graph. Task T074. FR-ORC-002.
 *
 * <p><strong>Node-keyed identity, not stage-keyed.</strong> A stage can have many nodes once S7 fans
 * out, so the identity is {@code nodeKey} — {@code "S7"}, {@code "S7.1"}, {@code "S7.join"} — and the
 * stage it belongs to is a separate field.
 *
 * <p><strong>{@code stageNumber} is STORED and never parsed from the key.</strong> Parsing would look
 * correct for every node the template makes and break silently the first time a replan issued a key that
 * did not encode its stage. {@code StageTemplateTest} asserts this with a node whose key and column
 * deliberately disagree — the column wins.
 *
 * <p>Pure data. No framework, no persistence, no application-plane import.
 */
public record StageNode(String nodeKey, int stageNumber, NodeRole role, String parentNodeKey,
                        String name) {

    /** The contract's own pattern (`workflow-state.schema.json`). */
    private static final String KEY_PATTERN = "^S([1-9]|1[0-2])(\\.([1-9][0-9]*|join))?$";

    public StageNode {
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(name, "name");
        if (!nodeKey.matches(KEY_PATTERN)) {
            throw new IllegalArgumentException(
                    "node key must match the contract pattern S1..S12, S7.n or S7.join: " + nodeKey);
        }
        if (stageNumber < 1 || stageNumber > 12) {
            throw new IllegalArgumentException(
                    "stage number must be 1..12 (FR-ORC-001 fixes twelve stages): " + stageNumber);
        }
        boolean needsParent = role == NodeRole.FAN_OUT_CHILD || role == NodeRole.JOIN;
        if (needsParent && parentNodeKey == null) {
            throw new IllegalArgumentException(role + " must name its parent: " + nodeKey);
        }
        if (!needsParent && parentNodeKey != null) {
            throw new IllegalArgumentException(role + " must have no parent: " + nodeKey);
        }
    }

    public static StageNode singleton(String nodeKey, int stageNumber, String name) {
        return new StageNode(nodeKey, stageNumber, NodeRole.SINGLETON, null, name);
    }

    public static StageNode fanOutParent(String nodeKey, int stageNumber, String name) {
        return new StageNode(nodeKey, stageNumber, NodeRole.FAN_OUT_PARENT, null, name);
    }

    public static StageNode join(String nodeKey, int stageNumber, String parentNodeKey, String name) {
        return new StageNode(nodeKey, stageNumber, NodeRole.JOIN, parentNodeKey, name);
    }

    public static StageNode fanOutChild(String nodeKey, int stageNumber, String parentNodeKey,
                                        String name) {
        return new StageNode(nodeKey, stageNumber, NodeRole.FAN_OUT_CHILD, parentNodeKey, name);
    }
}
