-- Artifact provenance and versioning. Tasks T081, T076a. FR-ORC-005, FR-ORC-003.
--
-- A separate migration rather than an edit to V4, because migrations here are forward-only. V4 was
-- committed minutes before this one and no environment outside a throwaway test container has it, but
-- the discipline is the point: V2 did not drop V1's superseded columns for the same reason, and a
-- project that edits its most recent migration when it is convenient has no forward-only rule.

-- ---------------------------------------------------------------------------------------------------
-- artifact_version
-- ---------------------------------------------------------------------------------------------------
-- T081: for any artifact — which stage produced it, from which inputs, under which decisions.
-- T076a: one content hash per logical artifact PER WRITE, never a lost update.
--
-- THE UNIQUE CONSTRAINT IS THE SYNCHRONIZATION PRIMITIVE.
--
-- FR-ORC-003's negative criterion is that parallel branches MUST NOT corrupt a shared downstream
-- artifact. Two fan-out children writing the same artifact concurrently would, under last-write-wins,
-- leave one write silently discarded — and the run would carry on with an artifact neither author
-- recognises.
--
-- UNIQUE (run_id, artifact_key, version) makes that outcome IMPOSSIBLE rather than unlikely: both
-- writers compute the next version from what they read, both attempt to insert it, and exactly one
-- succeeds. The loser is told so and the declared policy for the artifact class decides what happens
-- next — serialize behind the winner, or refuse. What cannot happen is both appearing to succeed.
--
-- This is the same shape as the short-code unique constraint in V1: the database is the authority, the
-- application reacts to its answer, and a check-then-write would be a race.
CREATE TABLE artifact_version (
    artifact_version_id  BIGSERIAL    PRIMARY KEY,
    run_id               UUID         NOT NULL REFERENCES workflow_run (run_id),
    -- The LOGICAL artifact. Many versions share one key; that is what makes it logical.
    artifact_key         TEXT         NOT NULL,
    version              INTEGER      NOT NULL,
    -- SHA-256 hex of the content. The identity of what was actually written.
    content_hash         CHAR(64)     NOT NULL,
    -- Provenance: which node produced it. NOT NULL because T081's guard is that an artifact MUST NOT
    -- exist in a run without recorded provenance, and a nullable column is an invitation to one that
    -- does.
    produced_by_node_key TEXT         NOT NULL,
    -- The inputs it was derived from, as a comma-separated list of artifact keys. Empty for a root
    -- artifact, which is a fact rather than an absence.
    input_artifact_keys  TEXT         NOT NULL,
    produced_at          TIMESTAMPTZ  NOT NULL,

    CONSTRAINT artifact_version_one_per_write
        UNIQUE (run_id, artifact_key, version),
    CONSTRAINT artifact_version_positive
        CHECK (version >= 1),
    CONSTRAINT artifact_version_hash_is_lower_hex
        CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT artifact_version_key_not_blank
        CHECK (length(btrim(artifact_key)) > 0),

    FOREIGN KEY (run_id, produced_by_node_key) REFERENCES stage_node (run_id, node_key)
);

CREATE INDEX artifact_version_lookup_idx ON artifact_version (run_id, artifact_key, version DESC);

-- ---------------------------------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------------------------------
-- APPEND-ONLY. A new version is a new row; rewriting one in place is precisely the lost update this
-- table exists to prevent, so the privilege removes the capability rather than trusting the code.
GRANT SELECT, INSERT ON artifact_version TO shortener_app;
REVOKE UPDATE, DELETE ON artifact_version FROM shortener_app;
