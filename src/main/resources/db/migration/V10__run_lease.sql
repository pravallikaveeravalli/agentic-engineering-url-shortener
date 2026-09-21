-- Run-level lease preventing concurrent resumption. Task T095. FR-ORC-018. EC-026.
--
-- THE PRIMARY KEY IS THE SYNCHRONIZATION PRIMITIVE, the same shape V5's artifact_version unique
-- constraint uses for concurrent artifact writes (T076a): two resumption attempts race an INSERT, the
-- database allows exactly one, and the loser is told so by the constraint violation rather than by a
-- check-then-write race in application code.
--
-- One row per run, never a queue: a run being resumed at all is the fact this table records, and a
-- second, later resumption attempt while the first still holds it is EC-026's scenario exactly.
CREATE TABLE run_lease (
    run_id      UUID        PRIMARY KEY REFERENCES workflow_run (run_id),
    holder_id   UUID        NOT NULL,
    acquired_at TIMESTAMPTZ NOT NULL
);

GRANT SELECT, INSERT, DELETE ON run_lease TO shortener_app;
