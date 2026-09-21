-- Widens audit_record to the full AuditEvent contract. Task T111. FR-ORC-023, NFR-AUD-001.
--
-- V3 (T027) built the six mandatory fields plus run/correlation context. contracts/audit-event.schema.json
-- names three more: stageNumber, executorKindUsed, correctsEventId — none of them mandatory, all of them
-- required to be PRESENT on the events they apply to (a stage-execution event without executorKindUsed, a
-- correction without correctsEventId pointing at what it corrects). Nullable columns, added here rather
-- than assumed, because T111 is the first task that actually builds a general-purpose writer against the
-- full contract shape.
ALTER TABLE audit_record
    ADD COLUMN stage_number       INTEGER  NULL,
    ADD COLUMN executor_kind_used TEXT     NULL,
    ADD COLUMN corrects_event_id  BIGINT   NULL REFERENCES audit_record (audit_record_id);

ALTER TABLE audit_record
    ADD CONSTRAINT audit_record_stage_number_range
        CHECK (stage_number IS NULL OR stage_number BETWEEN 1 AND 12);

ALTER TABLE audit_record
    ADD CONSTRAINT audit_record_executor_kind_values
        CHECK (executor_kind_used IS NULL OR executor_kind_used IN ('DETERMINISTIC', 'AI', 'HUMAN'));

-- A correction must not point at itself — the same no-self-supersede shape V8 gives gate_decision.
ALTER TABLE audit_record
    ADD CONSTRAINT audit_record_no_self_correction
        CHECK (corrects_event_id IS NULL OR corrects_event_id <> audit_record_id);
