-- V2 domain. Task T026.
--
-- V1 established the migration chain and the three application-plane tables the walking skeleton
-- needs. V2 adds what the domain model turned out to require once the entities existed: the
-- credential table (KE-24), the idempotency table (KE-03), and the creator's `active` flag.
--
-- Forward-only (ADR-002). Nothing here alters or drops a V1 column; a column that needed removing
-- would be deprecated across two versions first (contracts/README.md).

-- ---------------------------------------------------------------------------------------------
-- Creators gain an activity flag.
--
-- Deactivation does not delete: KE-01 has no state for a link without an owner, so a deactivated
-- creator keeps their links and the foreign key keeps holding.
-- ---------------------------------------------------------------------------------------------
ALTER TABLE creator ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- ---------------------------------------------------------------------------------------------
-- Credentials (KE-24). ADR-013, with the owner's expiry refinement via CR-002.
--
-- V1 put `api_key_hash` and `key_expires_at` on `creator` directly. That was wrong once the domain
-- model existed: a creator may hold SEVERAL credentials over time — one revoked, one current — and a
-- single column per creator cannot express rotation. The V1 columns are left in place rather than
-- dropped, per the forward-only rule, and are no longer read; V3 may deprecate them formally.
--
-- The plaintext key is NEVER stored. Only `key_hash`, a SHA-256 hex digest of the FULL presented
-- string including the `crk_` prefix.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE creator_credential (
    credential_id  UUID         PRIMARY KEY,
    creator_id     UUID         NOT NULL REFERENCES creator (creator_id),
    key_hash       CHAR(64)     NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    -- Nullable, and reachable only through the domain's explicit `neverExpires` factory (CR-002):
    -- a permanent key must be a decision, never a forgotten field.
    expires_at     TIMESTAMPTZ  NULL,
    revoked_at     TIMESTAMPTZ  NULL,

    CONSTRAINT credential_expiry_after_creation
        CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT credential_revocation_after_creation
        CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    -- Lower-case hex only. A mixed-case digest would compare unequal to the domain's output and the
    -- lookup would silently miss.
    CONSTRAINT credential_key_hash_is_lower_hex
        CHECK (key_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX creator_credential_creator_idx ON creator_credential (creator_id);

COMMENT ON TABLE creator_credential IS
    'One row per issued credential, so rotation and revocation are expressible. Holds a SHA-256 hex '
    'digest of the full presented key; the key itself is never persisted (ADR-013).';

-- ---------------------------------------------------------------------------------------------
-- Idempotency markers (KE-03). FR-URL-012, semantics fixed by CL-008.
--
-- The primary key is (creator_id, marker): the marker is scoped PER CREATOR, so one creator's choice
-- of string cannot collide with another's. A single-column PK on `marker` would be a correctness bug
-- and a cross-tenant leak at once.
--
-- `request_fingerprint` is what separates CL-008 case 2 (replay) from case 3 (conflict). It covers
-- the destination AND the expiry, because covering the destination alone would report a
-- changed-expiry request as a replay and silently discard the caller's intent.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE idempotency_record (
    creator_id           UUID         NOT NULL REFERENCES creator (creator_id),
    marker               TEXT         NOT NULL,
    request_fingerprint  CHAR(64)     NOT NULL,
    short_code           TEXT         NOT NULL REFERENCES short_link (short_code),
    created_at           TIMESTAMPTZ  NOT NULL,

    PRIMARY KEY (creator_id, marker),
    CONSTRAINT idempotency_marker_not_blank CHECK (length(btrim(marker)) > 0),
    CONSTRAINT idempotency_fingerprint_is_lower_hex
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idempotency_record_code_idx ON idempotency_record (short_code);

COMMENT ON TABLE idempotency_record IS
    'Marker-only idempotency (CL-008). The marker is scoped per creator by the composite primary '
    'key. There is no destination-based deduplication at any scope, ever (CL-008 case 1).';
