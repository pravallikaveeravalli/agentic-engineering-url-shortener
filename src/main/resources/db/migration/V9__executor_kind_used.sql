-- executor_kind_used. Task T065, T066. FR-ORC-029.
--
-- V4's stage_node already carries executor_class — the DESIGN-TIME declaration
-- (DETERMINISTIC/AI_CAPABLE/HUMAN_GATE). Nothing yet records the RUNTIME fact of what actually ran: no
-- orchestration engine loop exists to call an executor, read back its StageOutcome (T069), and persist
-- the kind it carried. T065's no-change-plan gate is the first caller that needs to write this fact —
-- its "human-implemented" option records executorKindUsed = HUMAN, the only path that may ever do so
-- (FR-ORC-029: HUMAN is never selectable by any caller or configuration, only a no-plan-gate outcome).
--
-- Nullable: most nodes are not yet executed, and a node whose executor kind is BLOCKED/READY/RUNNING has
-- none to report. Matches contracts/workflow-state.schema.json's own executorKindUsed field exactly
-- (type: [string, null], same three values).
ALTER TABLE stage_node
    ADD COLUMN executor_kind_used TEXT NULL;

ALTER TABLE stage_node
    ADD CONSTRAINT stage_node_executor_kind_used_values
        CHECK (executor_kind_used IS NULL
            OR executor_kind_used IN ('DETERMINISTIC', 'AI', 'HUMAN'));

COMMENT ON COLUMN stage_node.executor_kind_used IS
    'FR-ORC-029: DETERMINISTIC, AI or HUMAN — what actually ran, distinct from executor_class (the '
    'design-time declaration). HUMAN is written only by NoPlanGate.humanImplemented (T065); it is never '
    'selectable by any caller or configuration.';
