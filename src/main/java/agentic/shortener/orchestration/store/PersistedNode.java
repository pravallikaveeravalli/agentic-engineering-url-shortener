package agentic.shortener.orchestration.store;

import agentic.shortener.orchestration.graph.NodeRole;
import agentic.shortener.orchestration.graph.StageNode;
import agentic.shortener.orchestration.state.StageState;

import java.util.Objects;

/**
 * A node as the store holds it: its topology plus its current state. Task T074.
 *
 * <p>Two things deliberately kept apart and joined only here. {@link StageNode} is topology and is
 * immutable for the life of the run; the state moves. Merging them into one mutable type is how a
 * topology change and a state change end up looking like the same kind of event, which is exactly what
 * replan history needs to be able to tell apart.
 */
public record PersistedNode(StageNode node, StageState state, ExecutorClass executorClass,
                            int attemptsUsed) {

    public PersistedNode {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(executorClass, "executorClass");
    }

    public String nodeKey() {
        return node.nodeKey();
    }

    /** From the stored column. Never parsed from the key — see {@link StageNode}. */
    public int stageNumber() {
        return node.stageNumber();
    }

    public NodeRole role() {
        return node.role();
    }

    public String parentNodeKey() {
        return node.parentNodeKey();
    }

    public String name() {
        return node.name();
    }
}
