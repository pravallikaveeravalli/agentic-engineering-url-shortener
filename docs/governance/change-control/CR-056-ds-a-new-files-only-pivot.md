# CR-056 — Greenfield pivots to a new-files-only implementation after CR-055's fix improved but did not
reliably fix S7; correction-forward note on S6's own approval record

| Field | Value |
|---|---|
| **Change request** | CR-056 |
| **Title** | `GET /v1/version`'s implementation sourced from a NEW resource file, never by editing `pom.xml` — under the owner's own pre-authorized timebox fallback; correction-forward note on `s6-architecture-approved.md`'s own "build-info" wording |
| **Raised by** | Owner's own direction, this session (2026-09-22): "If S7 STILL corrupt-patches — pivot immediately to NEW-FILES-ONLY, same turn... This is a routine implementation detail — resolve it under the standing delegation... it does not invalidate the owner's S6 architecture approval" |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — pivot to new-files-only, one sentence added to `DsALiveRun`'s own `REQUIREMENT` constant, under the standing routine-clarification delegation.** Conversational instruction, not a formal gate decision; no `docs/governance/gate-decisions/` record produced for the pivot itself (CLAUDE.md's own trigger rule) |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — no change to the endpoint's own approved contract (path, method, no-auth posture, response shape, all CR-054's own wording, unchanged); changes only the version value's sourcing mechanism and constrains S7's file scope |
| **Artifacts changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`REQUIREMENT` constant), `docs/evidence/ds-a/s7-existing-file-diff-finding.md` (addendum) |
| **Non-approved files changed** | none |

---

## Why this pivot, right now

CR-055 built the "clean" fix the owner directed (`Conductor` pre-fetches real file content, `ImplementationAiExecutor` stays pure) — real, unit- and integration-tested, `ci.sh` green, committed. **One live verification attempt was authorized and made** (attempt 17,
`docs/evidence/ds-a/run-snapshot-ATTEMPT-17-BLOCKED-S7-corrupt-patch-despite-fix.md`): the fix demonstrably
improved the model's output (real, accurate hunk headers against real content it had genuinely been shown,
unlike attempts 14/15's fabricated ones) but `git apply` still refused it — `corrupt patch at line 78`, a
different, narrower failure than before (most likely a hunk-formatting slip, not a content-accuracy one), but
still a failure. Per the owner's own explicit, pre-authorized timebox ("do NOT retry, pivot immediately"), no
second live attempt was made against the same design.

## The pivot: zero existing-file dependency, this run

One sentence appended to `DsALiveRun`'s own `REQUIREMENT` constant (the SAME mechanism CR-054 already
established for evolving this driver's subject text turn to turn): the version value must come from a NEW,
dedicated resource file, and the change must add only new files. Concretely, this steers the run's own real,
live S5/S6 output toward: a new `VersionController`, a new resource/properties file the executor can create
cleanly (S7's own new-file hunks have never failed across all three real attempts — 14, 15, 17), and a new,
dedicated test class rather than an edit to the existing `ContractConformanceIT.java`. **The OpenAPI contract
entry documenting `/v1/version` is deliberately NOT added this run** — the requirement text does not ask for
it, so S6/S7 have no reason to touch `openapi.yaml` at all. Verified before relying on this: no test in the
fast tier (`ContractFilesLintTest`, `OpenApiConformanceTest`, `ResponseExamplesTest`, or any other) asserts
that every live controller endpoint has a matching contract entry — all three iterate the *document*, not the
*implementation*, so an undocumented-but-implemented endpoint does not fail S8's own real test run. This is a
disclosed, deliberate follow-up gap, not a silent omission.

## Correction-forward note (orphan-check finding)

`docs/governance/gate-decisions/ds-a/s6-architecture-approved.md` — already filed, already applied — approves
"the `VersionController` + Spring `build-info`/`BuildProperties` + OpenAPI-mirroring design" by name. This
run's real design will not match that wording: no `build-info`, no `pom.xml` edit, no OpenAPI mirroring this
turn. Per CLAUDE.md's own cross-record orphan-check discipline, a falsified claim in an already-applied record
is never edited in place; it is corrected forward by a record in the current package, labelled as such — **this
is that record.** The correction: `s6-architecture-approved.md`'s own approval is read, per the owner's own
explicit statement this session, as covering the endpoint's architecturally significant surface — the new
public, unauthenticated `GET /v1/version` resource, its controller shape, and its response contract — and NOT
as freezing the specific value-sourcing mechanism, which the owner herself has now changed under the standing
delegation of routine implementation detail. `s6-architecture-approved.md` itself is not edited (immutability);
this CR is the durable record of why its literal wording and the real implementation now diverge, and that the
divergence is authorized, not accidental.

## Scope

**What changed**: the live driver's own requirement text (one added sentence); the finding document gained an
addendum recording attempt 17's honest result.

**What did not change**: the endpoint's own contract (CR-054's wording, verbatim), S6's own architecture
approval (still applied, not reopened), CR-055's own fix (not reverted — real, tested, committed, and remains
available for a future attempt with e.g. hunk-formatting validation added before `git apply`).

## Conditions attached to the approval

1. **No second live attempt against the build-info design** — honoured; the pivot is immediate, per the
   owner's own explicit instruction.
2. **The OpenAPI gap is disclosed, not hidden** — honoured, both here and in the finding document's addendum.
3. **S6's own approval record is not edited in place** — honoured; this CR is the correction-forward record,
   not a rewrite of the applied one.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Add the `/v1/version` OpenAPI contract entry as a genuine follow-up task, through the normal governed pipeline (a future S7 existing-file edit, ideally once CR-055's remaining hunk-formatting gap is closed) | Before this feature is considered fully documented, a future turn |
| If a future attempt revisits CR-055's own remaining gap (hunk formatting on existing-file edits), it should build on CR-055's real infrastructure rather than re-deriving it | Whenever existing-file editing through S7 is revisited |
