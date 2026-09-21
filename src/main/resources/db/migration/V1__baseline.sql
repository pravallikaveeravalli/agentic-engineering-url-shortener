-- V1 baseline. Task T010.
--
-- ADR-002 (Accepted): PostgreSQL 16 with Flyway. Migrations are FORWARD-ONLY — there are no `down`
-- scripts, and a column is deprecated across two versions before removal (contracts/README.md).
-- This file must apply cleanly to a completely empty database; MigrationFromEmptyTest asserts it.
--
-- Scope of V1: the migration chain's root and the two application-plane tables the walking skeleton
-- needs. Orchestration, policy and audit tables arrive in later slices with their own versioned
-- migrations, because a baseline that guessed at their shape would be inventing schema ahead of the
-- tasks that specify it.

-- ---------------------------------------------------------------------------------------------
-- Creators. FR-URL-018: link creation is authenticated; redirect resolution is public.
-- ADR-013: an opaque 256-bit key, stored only as a SHA-256 hash, compared in constant time.
-- The plaintext key is NEVER stored — only its hash, which is why there is no `api_key` column.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE creator (
    creator_id      UUID         PRIMARY KEY,
    name            TEXT         NOT NULL,
    -- Hex-encoded SHA-256 of the opaque key. 64 hex characters, fixed width.
    api_key_hash    CHAR(64)     NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL,
    -- ADR-013 owner refinement: credential expiry is required, not optional (CR-002).
    key_expires_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT creator_key_expiry_after_creation CHECK (key_expires_at > created_at)
);

COMMENT ON TABLE creator IS
    'Application-plane identity. Holds no orchestrator authority: CN-012 forbids any credential '
    'class from crossing between the shortener and the orchestrator.';
COMMENT ON COLUMN creator.api_key_hash IS
    'SHA-256 hex of the opaque key. The key itself is never persisted (ADR-013).';

-- ---------------------------------------------------------------------------------------------
-- Short links. FR-URL-001, FR-URL-006, FR-URL-008, FR-URL-009.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE short_link (
    -- FR-URL-006: uniqueness is enforced by the store, not by an application-side check. A unique
    -- constraint plus bounded retry (ADR-007) is what makes collision handling correct under
    -- contention; a SELECT-then-INSERT would not be.
    short_code   TEXT         PRIMARY KEY,
    destination  TEXT         NOT NULL,
    creator_id   UUID         NOT NULL REFERENCES creator (creator_id),
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    state        TEXT         NOT NULL,

    -- FR-URL-001: destination is non-empty and at most 2048 characters.
    CONSTRAINT short_link_destination_length CHECK (
        length(destination) > 0 AND length(destination) <= 2048
    ),
    -- FR-URL-009: expiry must be in the future at creation. Enforced structurally so a clock bug
    -- cannot mint an already-expired link.
    CONSTRAINT short_link_expiry_after_creation CHECK (expires_at > created_at),
    CONSTRAINT short_link_state_values CHECK (state IN ('ACTIVE', 'EXPIRED'))
);

CREATE INDEX short_link_creator_idx  ON short_link (creator_id);
CREATE INDEX short_link_expires_idx  ON short_link (expires_at);

COMMENT ON COLUMN short_link.creator_id IS
    'Required. There is no link without an owner (data-model.md KE-01).';

-- ---------------------------------------------------------------------------------------------
-- Redirect events. FR-URL-010: per-event capture, TIMESTAMP ONLY.
--
-- CL-002's deciding ground was data minimisation: no IP address, no user agent, no referrer, no
-- follower-identifying field of any kind. That is also what makes indefinite retention safe
-- (NFR-AUD-003, CR-017) — there is no personal data here to retain.
--
-- Unbounded growth is accepted BY DESIGN in this demonstration. Time-partitioning is backlog item
-- B4, not deferred work that someone intends to do here.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE redirect_event (
    redirect_event_id  BIGSERIAL   PRIMARY KEY,
    short_code         TEXT         NOT NULL REFERENCES short_link (short_code),
    occurred_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX redirect_event_code_time_idx ON redirect_event (short_code, occurred_at);

COMMENT ON TABLE redirect_event IS
    'Timestamp-only click records. No follower-identifying column exists, by design (CL-002). '
    'A column added here later would need change control precisely because the absence is the control.';
