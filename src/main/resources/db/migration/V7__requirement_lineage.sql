-- Requirement, ambiguity, clarification, and task lineage. Task T082.
-- FR-ORC-009, FR-ORC-010, FR-ORC-011, FR-ORC-012. KE-07..KE-12.

-- ---------------------------------------------------------------------------------------------------
-- requirement_record
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE requirement_record (
    requirement_record_id  UUID         PRIMARY KEY,
    run_id                  UUID         NOT NULL REFERENCES workflow_run (run_id),
    external_id             TEXT         NOT NULL,
    type                     TEXT         NOT NULL,
    statement                TEXT         NOT NULL,
    status                   TEXT         NOT NULL,

    CONSTRAINT requirement_record_type_values
        CHECK (type IN ('FUNCTIONAL', 'NON_FUNCTIONAL')),
    CONSTRAINT requirement_record_status_values
        CHECK (status IN ('NORMALIZED')),
    CONSTRAINT requirement_record_statement_not_blank
        CHECK (length(btrim(statement)) > 0),
    -- FR-ORC-012: task decomposition addresses a requirement by this id, so it must be unique within
    -- the run or that addressing would be ambiguous. Scoped to run_id, not global, because the same
    -- submitter-chosen id in two different runs names two different requirements.
    CONSTRAINT requirement_record_external_id_unique_per_run
        UNIQUE (run_id, external_id)
);

CREATE INDEX requirement_record_run_idx ON requirement_record (run_id);

-- ---------------------------------------------------------------------------------------------------
-- ambiguity_record
-- ---------------------------------------------------------------------------------------------------
-- The ambiguity classes are the six structural ones spec.md names as the alternative that was
-- CONSIDERED and found narrower than S3's semantic detection, plus SEMANTIC_CONTRADICTION — the class
-- none of the six could catch, which is DS-A's own demonstration input (link lifetime vs analytics
-- retention vs redirect entitlement: no two clauses bound the same field, so no structural rule fires).
CREATE TABLE ambiguity_record (
    ambiguity_record_id       UUID         PRIMARY KEY,
    requirement_record_id     UUID         NOT NULL REFERENCES requirement_record (requirement_record_id),
    ambiguity_class            TEXT         NOT NULL,
    affected_path               TEXT         NOT NULL,
    resolution_state             TEXT         NOT NULL,
    quality_checks_performed   TEXT         NOT NULL,
    no_clarification_reason     TEXT         NULL,

    CONSTRAINT ambiguity_record_class_values
        CHECK (ambiguity_class IN (
            'MISSING_ACCEPTANCE_CRITERIA', 'UNDEFINED_TERM', 'UNBOUNDED_QUANTIFIER', 'MISSING_ACTOR',
            'SELF_REFERENTIAL_CONSTRAINT', 'CONTRADICTORY_BOUNDS', 'SEMANTIC_CONTRADICTION')),
    CONSTRAINT ambiguity_record_resolution_values
        CHECK (resolution_state IN ('MATERIAL_PENDING', 'RESOLVED', 'NOT_MATERIAL')),
    CONSTRAINT ambiguity_record_checks_not_blank
        CHECK (length(btrim(quality_checks_performed)) > 0),
    -- DS-A's own requirement, made structural rather than conventional: no_clarification_reason is
    -- recorded IFF the ambiguity was found not material. This is what makes a non-detection
    -- inspectable rather than silent — the field is the only control against a quiet miss, so it
    -- cannot be an optional caller choice.
    CONSTRAINT ambiguity_record_reason_iff_not_material
        CHECK ((resolution_state = 'NOT_MATERIAL')
            = (no_clarification_reason IS NOT NULL AND length(btrim(no_clarification_reason)) > 0))
);

CREATE INDEX ambiguity_record_requirement_idx ON ambiguity_record (requirement_record_id);

-- ---------------------------------------------------------------------------------------------------
-- clarification_decision
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE clarification_decision (
    clarification_decision_id  UUID         PRIMARY KEY,
    ambiguity_record_id         UUID         NOT NULL REFERENCES ambiguity_record (ambiguity_record_id),
    actor                         TEXT         NOT NULL,
    question                      TEXT         NOT NULL,
    answer                         TEXT         NOT NULL,
    decided_at                     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT clarification_decision_question_not_blank CHECK (length(btrim(question)) > 0),
    CONSTRAINT clarification_decision_answer_not_blank CHECK (length(btrim(answer)) > 0)
);

CREATE INDEX clarification_decision_ambiguity_idx ON clarification_decision (ambiguity_record_id);

-- ---------------------------------------------------------------------------------------------------
-- task_record, and its requirement/dependency links
-- ---------------------------------------------------------------------------------------------------
-- FR-ORC-012: every TaskRecord traces to >=1 requirement. Normalized into its own join table rather
-- than an array column on task_record, so each reference is a REAL foreign key — an array of ids
-- could name a requirement that was never recorded, which is exactly the kind of thing this project's
-- standing rule (structural enforcement over detection) exists to rule out. The store enforces the
-- ">=1" half by refusing to open the transaction for an empty list before any row is written, the
-- same shape StageOutcome.succeeded() uses for EC-029.
CREATE TABLE task_record (
    task_record_id       UUID         PRIMARY KEY,
    run_id                 UUID         NOT NULL REFERENCES workflow_run (run_id),
    validation_status     TEXT         NOT NULL,

    CONSTRAINT task_record_validation_status_values
        CHECK (validation_status IN ('PENDING', 'VALIDATED', 'REJECTED'))
);

CREATE INDEX task_record_run_idx ON task_record (run_id);

CREATE TABLE task_requirement (
    task_record_id          UUID  NOT NULL REFERENCES task_record (task_record_id),
    requirement_record_id   UUID  NOT NULL REFERENCES requirement_record (requirement_record_id),

    PRIMARY KEY (task_record_id, requirement_record_id)
);

CREATE INDEX task_requirement_requirement_idx ON task_requirement (requirement_record_id);

-- Dependency-ordered tasks (T082's Artifact wording). Self-loop refused structurally, matching
-- dependency_edge's own no-self-loop constraint in V4 — the same shape for the same reason.
CREATE TABLE task_dependency (
    task_record_id             UUID  NOT NULL REFERENCES task_record (task_record_id),
    depends_on_task_record_id  UUID  NOT NULL REFERENCES task_record (task_record_id),

    PRIMARY KEY (task_record_id, depends_on_task_record_id),
    CONSTRAINT task_dependency_no_self_loop CHECK (task_record_id <> depends_on_task_record_id)
);

-- ---------------------------------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------------------------------
-- ambiguity_record and task_record both have a state that moves (resolution_state,
-- validation_status), so they get UPDATE. Everything else here is an append-only fact once written.
GRANT SELECT, INSERT, UPDATE ON ambiguity_record, task_record TO shortener_app;
GRANT SELECT, INSERT ON requirement_record, clarification_decision, task_requirement, task_dependency
    TO shortener_app;
