-- Task T115. FR-ORC-024, plan §7.
--
-- V3 created failure_event with the closed envelope shape (run_id, node_key, category, detected_at,
-- recovered_at, recovery_mechanism, detail) before MTTR measurement existed as a requirement. This widens
-- it with the four columns MTTR needs, the same way V12 widened audit_record rather than creating a
-- parallel table — one governance table for one concept, not two that have to be joined and kept in sync.
--
-- REVOKE UPDATE, DELETE on failure_event (V3) already covers these new columns: grants are per-table, not
-- per-column, so the append-only guarantee GovernanceImmutabilityIT (T113) proves needs no re-statement
-- here. A failure_event row is written exactly once, fully formed, at the point its fate is already known
-- (recovered, or given up on) — there is no later UPDATE to fill these in, because the privilege grant
-- forbids one.

ALTER TABLE failure_event
    ADD COLUMN recovery_started_at TIMESTAMPTZ NULL,
    ADD COLUMN human_wait_duration_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN individual_recovery_duration_ms BIGINT NULL,
    -- Denormalized from recovered_at IS NOT NULL rather than computed on every read. The consistency
    -- constraint below is what keeps it from drifting into a second source of truth.
    ADD COLUMN recovered BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE failure_event
    ADD CONSTRAINT failure_event_recovery_started_after_detection
        CHECK (recovery_started_at IS NULL OR recovery_started_at >= detected_at),
    ADD CONSTRAINT failure_event_recovered_matches_recovered_at
        CHECK (recovered = (recovered_at IS NOT NULL)),
    -- individual_recovery_duration_ms exists if and only if the event recovered — an unrecovered failure
    -- has no completion time to measure a duration from, and T119 excludes it from the MTTR denominator
    -- for exactly that reason.
    ADD CONSTRAINT failure_event_duration_present_iff_recovered
        CHECK ((recovered_at IS NULL AND individual_recovery_duration_ms IS NULL)
            OR (recovered_at IS NOT NULL AND individual_recovery_duration_ms IS NOT NULL)),
    ADD CONSTRAINT failure_event_human_wait_nonnegative
        CHECK (human_wait_duration_ms >= 0),
    ADD CONSTRAINT failure_event_duration_nonnegative
        CHECK (individual_recovery_duration_ms IS NULL OR individual_recovery_duration_ms >= 0);

COMMENT ON COLUMN failure_event.human_wait_duration_ms IS
    'Time in AWAITING_APPROVAL or SAFE_STOP during the recovery window, measured separately (T117) so '
    'MTTR is not a function of when a person was at a keyboard (plan §7 declared exclusion).';

COMMENT ON COLUMN failure_event.individual_recovery_duration_ms IS
    '(recovered_at - detected_at) - human_wait_duration_ms. Stored rather than recomputed per read so the '
    'MTTR formula (T118) averages a value every reader sees identically.';
