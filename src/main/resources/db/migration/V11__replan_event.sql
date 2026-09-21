-- Replanning history. Task T096. FR-ORC-019. EC-019, EC-020, EC-030.
--
-- Plan §4's own table: "Replanning history | replan_event | Cause, invalidated stages, voided approvals."
-- Three join surfaces for those three things: the event row itself carries the cause and the node that
-- triggered it; the two child tables enumerate which nodes were actually invalidated and which prior
-- gate decisions were voided by that invalidation (EC-020) — never a delete, a reference.
CREATE TABLE replan_event (
    replan_event_id     BIGSERIAL    PRIMARY KEY,
    run_id               UUID         NOT NULL REFERENCES workflow_run (run_id),
    cause                TEXT         NOT NULL,
    triggering_node_key  TEXT         NOT NULL,
    occurred_at          TIMESTAMPTZ  NOT NULL,

    CONSTRAINT replan_event_cause_not_blank
        CHECK (length(btrim(cause)) > 0)
);

CREATE INDEX replan_event_run_idx ON replan_event (run_id, occurred_at);

-- The invalidated set, node-level (CR-011/CR-013's own convention — node keys, never stage numbers), so a
-- replan that invalidates one fan-out child and leaves its siblings intact is exactly what this records.
CREATE TABLE replan_event_invalidated_node (
    replan_event_id  BIGINT  NOT NULL REFERENCES replan_event (replan_event_id),
    node_key         TEXT    NOT NULL,

    PRIMARY KEY (replan_event_id, node_key)
);

-- EC-020, made queryable from the replan's own side too — gate_decision.supersedes_gate_decision_id (V8)
-- already carries which decision superseded which; this table is what lets a reviewer ask "which
-- approvals did THIS replan void" without walking every gate_decision row in the run.
CREATE TABLE replan_event_voided_decision (
    replan_event_id   BIGINT  NOT NULL REFERENCES replan_event (replan_event_id),
    gate_decision_id  BIGINT  NOT NULL REFERENCES gate_decision (gate_decision_id),

    PRIMARY KEY (replan_event_id, gate_decision_id)
);

GRANT SELECT, INSERT ON replan_event, replan_event_invalidated_node, replan_event_voided_decision
    TO shortener_app;
GRANT USAGE, SELECT ON SEQUENCE replan_event_replan_event_id_seq TO shortener_app;
