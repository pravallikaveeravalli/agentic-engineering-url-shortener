# Executed test results — attempt 26

T134 artifact component.

S8 (`ScriptTestSuiteRunner`, the real fast-tier suite run against the applied branch in an isolated worktree)
terminated `SUCCEEDED` for this run — source: `docs/evidence/ds-a/run-snapshot-ATTEMPT-26-S7-S8-S9-S10-SUCCEEDED-S11-real-not-ready.md`,
node table, `S8 ... -> SUCCEEDED`.

**Disclosed gap, stated honestly rather than filled in with an invented number**: this run's own snapshot
writer (`DsALiveRun.writeRunSnapshot`) persists each node's terminal state, not the suite's own pass/fail
counts — those are visible only in the live console output at the time a run executes, which was not
separately captured to a file for attempt 26. The terminal state genuinely proves the suite passed (S8 only
reaches `SUCCEEDED` when the real suite it ran reports zero failures — see `ScriptTestSuiteRunner`'s own
contract); the exact test count for this specific run is not independently on record.

What **is** independently verifiable for the feature this run produced: with the same 3-file change set
(`VersionController.java`, `pom.xml`, `openapi.yaml` — now landed in this repository, commit `d4e3ccf`) plus
the CR-064 bookkeeping bump, `./scripts/ci.sh` (this repository's own full CI-equivalent, run 2026-09-22) is
green — fast tier, secret scan, integration tier (real PostgreSQL via Testcontainers), and telemetry scan all
pass, confirmed directly against the landed code, not reconstructed from the run's own console log.
