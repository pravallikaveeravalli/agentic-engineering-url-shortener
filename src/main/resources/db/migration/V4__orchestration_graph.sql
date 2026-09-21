-- Orchestration graph and run state. Task T074. FR-ORC-002, FR-ORC-003, FR-ORC-004, FR-ORC-017.
--
-- WHY THE GRAPH IS DATA AND NOT CODE
--
-- T074's guard: edges are declared data, never inferred from call order. Per-run materialization is
-- what lets a REPLANNED instance's topology differ from the template and stay queryable. A code-only
-- graph makes replan history unreconstructable, because the only record of what the graph was would be
-- code that no longer runs.
--
-- WHY THIS IS A DISK-BACKED STORE AND NOT A CACHE
--
-- FR-ORC-004 (CL-009): workflow state must survive BOTH an orchestrator-process restart AND a restart
-- of the persistence layer. Its negative clause is explicit — state MUST NOT exist only in process
-- memory, and MUST NOT live only in a store that is itself only in memory. These tables are the whole
-- of a run's position; nothing about where a run has got to lives anywhere else.

-- ---------------------------------------------------------------------------------------------------
-- workflow_run
-- ---------------------------------------------------------------------------------------------------
-- The six run states of FR-ORC-017 as CL-005 fixed them. The terminal set is exactly COMPLETED,
-- REJECTED and ABANDONED; SAFE_STOP is SUSPENDED and NON-terminal. That distinction was a contradiction
-- in the approved specification until CL-005 resolved it, and the two CHECK constraints below are what
-- keep it resolved at the only level that cannot be bypassed by a bug in the application.
CREATE TABLE workflow_run (
    run_id             UUID         PRIMARY KEY,
    state              TEXT         NOT NULL,
    terminal_state     TEXT         NULL,
    policy_set_version TEXT         NOT NULL,
    submitted_at       TIMESTAMPTZ  NOT NULL,
    last_activity_at   TIMESTAMPTZ  NOT NULL,
    auto_abandon_at    TIMESTAMPTZ  NULL,
    suspension_reason  TEXT         NULL,
    correlation_id     UUID         NOT NULL,

    CONSTRAINT run_state_known
        CHECK (state IN ('PENDING', 'RUNNING', 'SAFE_STOP', 'COMPLETED', 'REJECTED', 'ABANDONED')),

    -- The schema's first conditional, enforced in the database: terminal_state is non-null IFF the run
    -- is terminal. "Both directions" matters — a terminal run with no terminal_state is unexplained,
    -- and a live run carrying one is a run that has already been declared finished.
    CONSTRAINT run_terminal_state_iff_terminal
        CHECK ((state IN ('COMPLETED', 'REJECTED', 'ABANDONED')) = (terminal_state IS NOT NULL)),
    CONSTRAINT run_terminal_state_matches
        CHECK (terminal_state IS NULL OR terminal_state = state),

    -- The second conditional: auto_abandon_at and suspension_reason are non-null IFF suspended.
    -- A suspended run with no disclosed abandonment time would be a run nobody can be told about.
    CONSTRAINT run_auto_abandon_iff_safe_stop
        CHECK ((state = 'SAFE_STOP') = (auto_abandon_at IS NOT NULL)),
    CONSTRAINT run_suspension_reason_iff_safe_stop
        CHECK ((state = 'SAFE_STOP') = (suspension_reason IS NOT NULL AND length(suspension_reason) > 0)),

    CONSTRAINT run_activity_after_submission
        CHECK (last_activity_at >= submitted_at)
);

CREATE INDEX workflow_run_state_idx ON workflow_run (state);

-- ---------------------------------------------------------------------------------------------------
-- stage_node
-- ---------------------------------------------------------------------------------------------------
-- NODE-KEYED identity. A stage has many nodes once S7 fans out, so the key is the node and the stage is
-- a separate column.
--
-- stage_number IS A STORED VALUE AND IS NEVER PARSED FROM node_key. T074 asserts this with a node whose
-- key and column deliberately disagree. Parsing would be correct for every node the template makes and
-- would break silently the first time a replan issued a key that did not encode its stage.
CREATE TABLE stage_node (
    run_id          UUID         NOT NULL REFERENCES workflow_run (run_id),
    node_key        TEXT         NOT NULL,
    stage_number    INTEGER      NOT NULL,
    node_role       TEXT         NOT NULL,
    parent_node_key TEXT         NULL,
    name            TEXT         NOT NULL,
    state           TEXT         NOT NULL,
    executor_class  TEXT         NOT NULL,
    attempts_used   INTEGER      NOT NULL DEFAULT 0,
    blocking_reason TEXT         NULL,
    entered_at      TIMESTAMPTZ  NULL,
    exited_at       TIMESTAMPTZ  NULL,

    PRIMARY KEY (run_id, node_key),

    -- The contract's own pattern (workflow-state.schema.json), enforced here as well as in the type.
    CONSTRAINT stage_node_key_shape
        CHECK (node_key ~ '^S([1-9]|1[0-2])(\.([1-9][0-9]*|join))?$'),
    CONSTRAINT stage_node_stage_range
        CHECK (stage_number BETWEEN 1 AND 12),
    CONSTRAINT stage_node_role_known
        CHECK (node_role IN ('SINGLETON', 'FAN_OUT_PARENT', 'FAN_OUT_CHILD', 'JOIN')),

    -- Twelve stage states. FALLBACK is retained because the contract retains it; CR-032 retired the
    -- fallback RECOVERY MECHANISM (failure_event.recovery_mechanism), which is a different enum.
    CONSTRAINT stage_node_state_known
        CHECK (state IN ('BLOCKED', 'READY', 'RUNNING', 'AWAITING_APPROVAL', 'RETRY_WAIT', 'FALLBACK',
                         'ROLLING_BACK', 'COMPENSATING', 'SUCCEEDED', 'FAILED', 'INVALIDATED',
                         'SKIPPED')),
    CONSTRAINT stage_node_executor_class_known
        CHECK (executor_class IN ('DETERMINISTIC', 'AI_CAPABLE', 'HUMAN_GATE')),

    -- Parent linkage is a property of the role, not a convention. A child with no parent is an orphan
    -- the join can never count; a singleton with one is a lie about the topology.
    CONSTRAINT stage_node_parent_iff_child_or_join
        CHECK ((node_role IN ('FAN_OUT_CHILD', 'JOIN')) = (parent_node_key IS NOT NULL)),

    CONSTRAINT stage_node_attempts_not_negative
        CHECK (attempts_used >= 0),
    CONSTRAINT stage_node_exit_after_entry
        CHECK (exited_at IS NULL OR (entered_at IS NOT NULL AND exited_at >= entered_at))
);

CREATE INDEX stage_node_run_idx ON stage_node (run_id);

-- ---------------------------------------------------------------------------------------------------
-- dependency_edge
-- ---------------------------------------------------------------------------------------------------
-- Materialized PER RUN from the static template. Two runs of the same workflow can have different
-- topologies once one of them replans, and both stay queryable because each owns its edges.
CREATE TABLE dependency_edge (
    run_id          UUID  NOT NULL REFERENCES workflow_run (run_id),
    from_node_key   TEXT  NOT NULL,
    to_node_key     TEXT  NOT NULL,
    join_semantics  TEXT  NOT NULL,

    PRIMARY KEY (run_id, from_node_key, to_node_key),

    CONSTRAINT dependency_edge_semantics_known
        CHECK (join_semantics IN ('ALL', 'ANY')),
    -- The shortest possible cycle, refused by the store as well as by the type. T075 catches the long
    -- ones; this one cannot be written at all.
    CONSTRAINT dependency_edge_no_self_loop
        CHECK (from_node_key <> to_node_key),

    FOREIGN KEY (run_id, from_node_key) REFERENCES stage_node (run_id, node_key),
    FOREIGN KEY (run_id, to_node_key)   REFERENCES stage_node (run_id, node_key)
);

CREATE INDEX dependency_edge_to_idx ON dependency_edge (run_id, to_node_key);

-- ---------------------------------------------------------------------------------------------------
-- state_transition: a RUN-level transition has no node
-- ---------------------------------------------------------------------------------------------------
-- V3 made state_transition.node_key NOT NULL, which is right for a stage transition and wrong for a run
-- transition: PENDING -> RUNNING is a fact about the RUN, and it belongs to no node.
--
-- This was found by a test, not by review. The first implementation filed run transitions against "S1"
-- for want of anywhere else to put them, and RunMaterializationIT caught S1's node history carrying a
-- state S1 had never been in. A sentinel node key would have been a lie in exactly the column a reader
-- queries to reconstruct a node's life.
--
-- NULL means "the run itself". V3's node_key CHECK evaluates to NULL for a NULL input and therefore
-- passes, so dropping NOT NULL is the whole change.
ALTER TABLE state_transition ALTER COLUMN node_key DROP NOT NULL;

-- ---------------------------------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------------------------------
-- Run and node state are MUTABLE: a run advances, and a node moves through its states. That is the
-- difference between these and the six governance tables of V3, which are append-only by privilege.
--
-- state_transition stays INSERT+SELECT only (granted in V3). The pairing is the point: the current
-- state may be updated, and the history of how it got there may not. T080 writes both in one
-- transaction, which is what stops the two diverging.
GRANT SELECT, INSERT, UPDATE ON workflow_run, stage_node TO shortener_app;

-- dependency_edge is materialized once per run and amended only by a replan that adds nodes. No UPDATE:
-- an edge is added or it is not, and rewriting one in place would silently change a run's history.
GRANT SELECT, INSERT ON dependency_edge TO shortener_app;
REVOKE UPDATE ON dependency_edge FROM shortener_app;

-- No DELETE anywhere. NFR-AUD-003 (CR-017) retains everything indefinitely, and a run that can be
-- deleted is a run whose reconstruction depends on nobody having done so.
REVOKE DELETE ON workflow_run, stage_node, dependency_edge FROM shortener_app;
