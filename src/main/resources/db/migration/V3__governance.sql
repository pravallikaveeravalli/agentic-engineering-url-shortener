-- V3 governance. Task T027.
--
-- NFR-AUD-001, FR-ORC-023. ADR-010.
--
-- Six control-plane tables, and the privilege grants that make append-only a CONTROL rather than a
-- convention. T028 proves the grants work by attempting an UPDATE and requiring the store to refuse
-- it; without that proof this file would be an intention.
--
-- ADR-010's scoping note, applied here: `redirect_event` (V1) keeps append-only privilege too, but it
-- is DOMAIN ANALYTICS, not audit. Its failure semantics come from ADR-014 — a failed append must not
-- fail the redirect — whereas a failed audit append is a governance fault. Same privilege, different
-- meaning, which is why it is not in this file.
--
-- Forward-only (ADR-002).

-- ---------------------------------------------------------------------------------------------
-- 1. audit_record. Every event carries the six mandatory fields.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE audit_record (
    audit_record_id   BIGSERIAL    PRIMARY KEY,
    -- The six mandatory fields (spec §Audit): actor type, action, timestamp, affected
    -- artifact/state, result, reason. Each is NOT NULL because an audit row missing one of them
    -- cannot answer the question the field exists to answer.
    actor_type        TEXT         NOT NULL,
    action            TEXT         NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    affected_artifact TEXT         NOT NULL,
    result            TEXT         NOT NULL,
    reason            TEXT         NOT NULL,
    -- Context: which run, and the correlation id that ties this to logs, metrics and traces.
    run_id            UUID         NULL,
    correlation_id    UUID         NOT NULL,

    CONSTRAINT audit_actor_type_values
        CHECK (actor_type IN ('human', 'agent', 'system')),
    CONSTRAINT audit_reason_not_blank CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX audit_record_run_idx         ON audit_record (run_id, occurred_at);
CREATE INDEX audit_record_correlation_idx ON audit_record (correlation_id);

COMMENT ON TABLE audit_record IS
    'Append-only. Immutability is enforced by privilege (see grants below), not by application '
    'discipline, because an application-enforced audit log is only as immutable as its next bug.';

-- ---------------------------------------------------------------------------------------------
-- 2. state_transition. The append-only log beside current state (ADR-008).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE state_transition (
    state_transition_id BIGSERIAL    PRIMARY KEY,
    run_id              UUID         NOT NULL,
    -- Instance-keyed node identity (CR-011): "S1".."S12", "S7.1".."S7.n", "S7.join".
    node_key            TEXT         NOT NULL,
    from_state          TEXT         NULL,
    to_state            TEXT         NOT NULL,
    occurred_at         TIMESTAMPTZ  NOT NULL,
    reason              TEXT         NOT NULL,

    CONSTRAINT state_transition_node_key_shape
        CHECK (node_key ~ '^S([1-9]|1[0-2])(\.([1-9][0-9]*|join))?$')
);

CREATE INDEX state_transition_run_idx ON state_transition (run_id, occurred_at);

COMMENT ON COLUMN state_transition.node_key IS
    'Instance-keyed: S7 fans out to S7.1..S7.n with an S7.join. The CHECK mirrors the pattern in '
    'contracts/workflow-state.schema.json so the store and the contract cannot drift apart.';

-- ---------------------------------------------------------------------------------------------
-- 3. gate_decision. Human decisions, materialized.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE gate_decision (
    gate_decision_id       BIGSERIAL    PRIMARY KEY,
    run_id                 UUID         NOT NULL,
    gate_id                TEXT         NOT NULL,
    outcome                TEXT         NOT NULL,
    actor_type             TEXT         NOT NULL,
    actor_name             TEXT         NOT NULL,
    reason                 TEXT         NOT NULL,
    decided_at             TIMESTAMPTZ  NOT NULL,
    -- CR-013: a decision is not usable until its repository record exists.
    repository_record_path TEXT         NOT NULL,

    -- FR-ORC-013's five outcomes. TIMED_OUT is deliberately ABSENT: CR-013 made it inexpressible,
    -- because a timeout is not a decision anyone made. Silence produces suspension, not an outcome.
    CONSTRAINT gate_decision_outcome_values
        CHECK (outcome IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED', 'ESCALATED')),
    -- CR-021: only a human decides a gate. The enum has one value, and the declared-not-verified
    -- limitation is disclosed in FR-ORC-021 rather than papered over here.
    CONSTRAINT gate_decision_actor_is_human CHECK (actor_type = 'human'),
    CONSTRAINT gate_decision_record_path_shape
        CHECK (repository_record_path LIKE 'docs/governance/%'),
    CONSTRAINT gate_decision_reason_not_blank CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX gate_decision_run_idx ON gate_decision (run_id, decided_at);

COMMENT ON TABLE gate_decision IS
    'Five outcomes minus TIMED_OUT, which CR-013 made inexpressible: a deadline expiring is not a '
    'decision, and a column able to record it would let silence look like one.';

-- ---------------------------------------------------------------------------------------------
-- 4. failure_event. MUST exist before the scenarios run (critical-path.md) or MTTR has no
--    population, and back-filling it afterwards would be fabrication under CN-005.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE failure_event (
    failure_event_id  BIGSERIAL    PRIMARY KEY,
    run_id            UUID         NOT NULL,
    node_key          TEXT         NOT NULL,
    -- The closed envelope vocabulary (CL-006). UNKNOWN is default-deny, not a catch-all.
    category          TEXT         NOT NULL,
    detected_at       TIMESTAMPTZ  NOT NULL,
    recovered_at      TIMESTAMPTZ  NULL,
    -- Five mechanisms. `fallback` is ABSENT: FR-ORC-015 is retired (Decision K, CR-032), and a value
    -- no code path can emit is a claim rather than a classification.
    recovery_mechanism TEXT        NULL,
    detail            TEXT         NOT NULL,

    CONSTRAINT failure_category_values
        CHECK (category IN ('TIMEOUT', 'UNAVAILABLE', 'RATE_LIMITED', 'INVALID_INPUT',
                            'INTERNAL', 'UNKNOWN')),
    CONSTRAINT failure_recovery_mechanism_values
        CHECK (recovery_mechanism IS NULL OR recovery_mechanism IN
               ('retry', 'rollback', 'compensation', 'resume', 'human')),
    CONSTRAINT failure_recovery_after_detection
        CHECK (recovered_at IS NULL OR recovered_at >= detected_at)
);

CREATE INDEX failure_event_run_idx ON failure_event (run_id, detected_at);

COMMENT ON COLUMN failure_event.recovery_mechanism IS
    'Five values. fallback is deliberately absent — FR-ORC-015 was retired by Decision K (CR-032), '
    'and bounded retry then safe suspension is the entire degradation story.';

-- ---------------------------------------------------------------------------------------------
-- 5. policy_check_result. Four outcomes, twelve mandatory checks (policy-set-1.1.0).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE policy_check_result (
    policy_check_result_id BIGSERIAL    PRIMARY KEY,
    run_id                 UUID         NOT NULL,
    policy_id              TEXT         NOT NULL,
    policy_set_version     TEXT         NOT NULL,
    outcome                TEXT         NOT NULL,
    evaluated_at           TIMESTAMPTZ  NOT NULL,
    detail                 TEXT         NOT NULL,

    CONSTRAINT policy_outcome_values
        CHECK (outcome IN ('PASS', 'FAIL', 'EXCEPTION-REQUESTED', 'NOT-APPLICABLE'))
);

CREATE INDEX policy_check_result_run_idx ON policy_check_result (run_id, evaluated_at);

-- ---------------------------------------------------------------------------------------------
-- 6. compensation_record. Applied at most once per effect, even if a retry later succeeds (EC-022).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE compensation_record (
    compensation_record_id BIGSERIAL    PRIMARY KEY,
    run_id                 UUID         NOT NULL,
    node_key               TEXT         NOT NULL,
    -- The effect being compensated, keyed so resumption cannot re-apply it.
    effect_key             TEXT         NOT NULL,
    compensating_action    TEXT         NOT NULL,
    applied_at             TIMESTAMPTZ  NOT NULL,
    reason                 TEXT         NOT NULL,

    -- EC-022 as a constraint rather than a convention: at most one compensation per effect. A retry
    -- that later succeeds cannot produce a second row, because the store will not accept one.
    CONSTRAINT compensation_once_per_effect UNIQUE (run_id, effect_key)
);

COMMENT ON CONSTRAINT compensation_once_per_effect ON compensation_record IS
    'EC-022: applied at most once per effect. Enforced by the store so a retrying caller cannot '
    'double-compensate, which application-side guarding would eventually allow.';

-- ---------------------------------------------------------------------------------------------
-- Append-only privilege grants. NFR-AUD-001.
--
-- This is the part that makes immutability a control. The application role receives INSERT and
-- SELECT; UPDATE and DELETE are never granted. T028 proves it by attempting an UPDATE against
-- audit_record and requiring the store to refuse.
--
-- The role is created if absent so the migration is self-contained: a reviewer running from a clean
-- checkout gets the grants without a manual step, and a missing role would make the grants silently
-- no-op.
-- ---------------------------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'shortener_app') THEN
        CREATE ROLE shortener_app NOLOGIN;
    END IF;
END
$$;

-- Application-plane tables: full DML, because links expire and creators deactivate.
GRANT SELECT, INSERT, UPDATE ON creator, creator_credential, short_link, idempotency_record
    TO shortener_app;

-- redirect_event: append and read only. Domain analytics, but retained indefinitely (CR-017), so the
-- same privilege shape applies even though ADR-010 scopes it out of "audit".
GRANT SELECT, INSERT ON redirect_event TO shortener_app;
REVOKE UPDATE, DELETE ON redirect_event FROM shortener_app;

-- The six governance tables: INSERT and SELECT ONLY.
GRANT SELECT, INSERT ON
    audit_record, state_transition, gate_decision, failure_event,
    policy_check_result, compensation_record
    TO shortener_app;

-- Explicit revocation as well as absent grant. Belt and braces on purpose: a future migration that
-- grants ALL on a schema would silently re-enable mutation, and an explicit REVOKE makes that
-- regression visible in a diff.
REVOKE UPDATE, DELETE ON
    audit_record, state_transition, gate_decision, failure_event,
    policy_check_result, compensation_record
    FROM shortener_app;

-- Sequences must be usable or every INSERT above fails on the BIGSERIAL default.
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO shortener_app;
