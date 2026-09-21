package agentic.shortener.orchestration.store;

/**
 * What kind of executor a node's stage uses. Contract-fixed (`workflow-state.schema.json`).
 *
 * <p>{@code AI_CAPABLE} rather than {@code AI}: Decision J removed the keyless mode, so an AI-capable
 * stage always uses AI, and the name records the capability rather than a toggle nobody can set.
 */
public enum ExecutorClass {
    DETERMINISTIC, AI_CAPABLE, HUMAN_GATE
}
