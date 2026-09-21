-- ApprovalGate, and the columns GateDecision needed but did not yet have. Task T058.
-- FR-ORC-013. KE-10, KE-11. CR-040.

-- ---------------------------------------------------------------------------------------------------
-- approval_gate
-- ---------------------------------------------------------------------------------------------------
-- A pending checkpoint on a stage node. Keyed by (run_id, gate_id) rather than a synthetic id: gate_id
-- is the free-text identifier gate_decision has already used since V3 (e.g. "S4", or a fan-out child's
-- own instance), so a decision can be correlated with the gate it answers without a new indirection.
--
-- DELIBERATELY NOT foreign-keyed FROM gate_decision. A gate_decision row can be written whether or not
-- an approval_gate row for the same (run_id, gate_id) exists yet — T090's superseding-record path
-- writes gate_decision rows directly, and requiring an approval_gate first would retrofit a constraint
-- onto a writer that predates this table. The correlation is logical, read by whoever presents a gate
-- request or inspects a run, not a hard reference.
CREATE TABLE approval_gate (
    run_id                      UUID         NOT NULL REFERENCES workflow_run (run_id),
    gate_id                      TEXT         NOT NULL,
    node_key                      TEXT         NOT NULL,
    gate_class                     TEXT         NOT NULL,
    wait_deadline                   TIMESTAMPTZ  NOT NULL,
    disclosed_auto_abandon_at       TIMESTAMPTZ  NOT NULL,
    requested_at                    TIMESTAMPTZ  NOT NULL,

    PRIMARY KEY (run_id, gate_id),

    -- CR-040: the ten plan-§5 classes, kept in exact agreement with GateClass.java and
    -- approval.schema.json's gateClass enum by three independently-run checks, never by one being
    -- trusted to imply the others.
    CONSTRAINT approval_gate_class_values
        CHECK (gate_class IN (
            'UNRESOLVED_AMBIGUITY', 'ARCHITECTURE_APPROVAL', 'SECURITY_SENSITIVE_ACTION',
            'DESTRUCTIVE_OR_IRREVERSIBLE_ACTION', 'CONSTITUTIONAL_EXCEPTION', 'MATERIAL_RISK_ACCEPTANCE',
            'NO_CHANGE_PLAN', 'NODE_OVERRUN', 'RELEASE_READINESS', 'FINAL_SUBMISSION')),
    -- CL-005 addendum: both deadlines are disclosed in the same ask, so the person answering sees the
    -- expiry consequence rather than discovering it by inspecting the run (T060's whole point).
    CONSTRAINT approval_gate_deadline_after_request
        CHECK (wait_deadline >= requested_at),
    CONSTRAINT approval_gate_abandon_after_wait
        CHECK (disclosed_auto_abandon_at >= wait_deadline),

    FOREIGN KEY (run_id, node_key) REFERENCES stage_node (run_id, node_key)
);

CREATE INDEX approval_gate_run_idx ON approval_gate (run_id);

-- ---------------------------------------------------------------------------------------------------
-- gate_decision gains what KE-11 and approval.schema.json need that V3 did not yet have
-- ---------------------------------------------------------------------------------------------------
-- gate_class: denormalized rather than joined through approval_gate, so a GateDecision's JSON
-- representation is self-sufficient for schema validation without a lookup — and so this ADD COLUMN
-- does not need every existing writer to have created an approval_gate row first. NOT NULL is safe
-- here: this table is always empty at the moment a migration applies (a fresh container per test
-- class, and a fresh database on first deploy), so the constraint binds only writers from this
-- version onward.
ALTER TABLE gate_decision
    ADD COLUMN gate_class TEXT NOT NULL;

ALTER TABLE gate_decision
    ADD CONSTRAINT gate_decision_class_values
        CHECK (gate_class IN (
            'UNRESOLVED_AMBIGUITY', 'ARCHITECTURE_APPROVAL', 'SECURITY_SENSITIVE_ACTION',
            'DESTRUCTIVE_OR_IRREVERSIBLE_ACTION', 'CONSTITUTIONAL_EXCEPTION', 'MATERIAL_RISK_ACCEPTANCE',
            'NO_CHANGE_PLAN', 'NODE_OVERRUN', 'RELEASE_READINESS', 'FINAL_SUBMISSION'));

-- stage_number: optional per the contract (a gate need not sit on a numbered stage node in every
-- case the schema anticipates), so nullable rather than widening every existing call site.
ALTER TABLE gate_decision
    ADD COLUMN stage_number INTEGER NULL;

ALTER TABLE gate_decision
    ADD CONSTRAINT gate_decision_stage_number_range
        CHECK (stage_number IS NULL OR stage_number BETWEEN 1 AND 12);

-- supersedes: EC-020, made structural. A self-referential FK rather than the free-text mention T090's
-- superseding-record path put only in `reason` — a reader (or a query) can now follow the chain
-- without parsing prose. NULL means "supersedes nothing", the ordinary case.
ALTER TABLE gate_decision
    ADD COLUMN supersedes_gate_decision_id BIGINT NULL REFERENCES gate_decision (gate_decision_id);

ALTER TABLE gate_decision
    ADD CONSTRAINT gate_decision_no_self_supersede
        CHECK (supersedes_gate_decision_id IS NULL
            OR supersedes_gate_decision_id <> gate_decision_id);

COMMENT ON COLUMN gate_decision.supersedes_gate_decision_id IS
    'EC-020: a superseding decision references the one it voids, never deleting or editing it. Set by '
    'CompensationHandler.supersedeGateDecision (T090) from V8 onward; earlier rows carry the same '
    'reference only in their reason text.';

-- escalation_target: required by the contract only when outcome = ESCALATED (T058's own guard, enforced
-- earlier still by GateDecision's constructor). Nullable here because it is meaningless for every other
-- outcome, and a NOT NULL column would force a placeholder onto rows that have nothing to say.
ALTER TABLE gate_decision
    ADD COLUMN escalation_target TEXT NULL;

ALTER TABLE gate_decision
    ADD CONSTRAINT gate_decision_escalation_target_iff_escalated
        CHECK ((outcome = 'ESCALATED') = (escalation_target IS NOT NULL
            AND length(btrim(escalation_target)) > 0));

-- ---------------------------------------------------------------------------------------------------
-- gate_decision_requested_change
-- ---------------------------------------------------------------------------------------------------
-- CHANGES_REQUESTED's enumerated changes. A child table rather than a TEXT[] column, for the same
-- reason task_requirement (T082) is a join table rather than an array: the ordinal makes the list's
-- order reconstructable, and "at least one row" is enforced by GateDecision's constructor refusing an
-- empty list before any write — the same EC-029/orphan-task shape used throughout this project.
CREATE TABLE gate_decision_requested_change (
    gate_decision_id  BIGINT  NOT NULL REFERENCES gate_decision (gate_decision_id),
    ordinal            INTEGER NOT NULL,
    change_text         TEXT    NOT NULL,

    PRIMARY KEY (gate_decision_id, ordinal),
    CONSTRAINT gate_decision_requested_change_not_blank CHECK (length(btrim(change_text)) > 0)
);

GRANT SELECT, INSERT ON approval_gate, gate_decision_requested_change TO shortener_app;
