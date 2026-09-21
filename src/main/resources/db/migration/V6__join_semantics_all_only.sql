-- Join semantics narrowed to ALL. CR-039, owner ruling 2026-09-21. FR-ORC-002, EC-018.
--
-- The Slice 4 artifact-coverage sweep found that `join_semantics` was stored and never read: nothing in
-- src/main consulted it, and StageCriteria.mayEnter takes dependency states with no edge information and
-- requires all of them. So an 'ANY' edge was writable and would have been evaluated as 'ALL' — a value a
-- caller could set and nothing honoured.
--
-- CR-032's reasoning, applied outward: an enum value that can never be emitted is a claim rather than a
-- classification. This was the worse version, because the value COULD be emitted.
--
-- NARROWING, NOT DROPPING. contracts/README.md classifies dropping a column as MAJOR and requires
-- deprecation across two versions — one that stops reading and writing it, one that removes it — because a
-- single migration doing both would leave a rollback to the earlier version with the data already gone. V1's
-- `api_key_hash` is the live example. Pinning the value reaches the ruling in one step without touching that
-- rule, and makes 'ANY' inexpressible at all three layers: the Java enum has one constant, the contract's
-- enum is ["ALL"], and the CHECK below admits nothing else.
--
-- Safe against existing data: the standard template writes 'ALL' on all fourteen edges and nothing else
-- inserts edges, so no row can violate the narrowed constraint. Verified before applying (CR-039 step 2).
--
-- V4 is not edited. Forward-only (ADR-002).

ALTER TABLE dependency_edge
    DROP CONSTRAINT dependency_edge_semantics_known;

ALTER TABLE dependency_edge
    ADD CONSTRAINT dependency_edge_semantics_known
        CHECK (join_semantics = 'ALL');

-- Written as a literal equality rather than IN ('ALL') so the constraint reads as the statement it is: there
-- is one legal value. An IN list of length one invites a second entry, which is the same trap the single-value
-- Java enum has and is guarded against the same way.

COMMENT ON COLUMN dependency_edge.join_semantics IS
    'Always ALL (CR-039). ANY was removed by owner ruling 2026-09-21: it was storable and never honoured, '
    'because StageCriteria.mayEnter requires every dependency to satisfy the join and has no per-edge '
    'behaviour. The column is retained rather than dropped so that the join concept stays named and so that '
    'removal, if ever wanted, goes through the two-version deprecation rule in contracts/README.md.';
