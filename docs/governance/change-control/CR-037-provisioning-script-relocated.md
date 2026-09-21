# CR-037 — Operator provisioning script relocated out of `ops/`

| Field | Value |
|---|---|
| **Change request** | CR-037 |
| **Title** | Relocate `ops/scripts/provision-creator.sh` → `scripts/provision-creator.sh` |
| **Raised by** | Acting agent (supervisor-reported mislocation) |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED** |
| **Decision date** | 2026-09-21 |
| **Classification** | **PATCH** — path only; no behaviour, no interface, no requirement changes |
| **Artifacts changed** | `specs/001-agentic-sdlc-url-shortener/tasks.md` (T053 Artifact path); `specs/001-agentic-sdlc-url-shortener/quickstart.md` (two usage examples) |
| **Non-approved files changed** | `scripts/provision-creator.sh` (moved, header comment); `ProvisionCreatorTest.java`, `ProvisionCreatorIT.java` (path constants) |
| **Applied** | 2026-09-21 |

---

## Reason

`ops/` is the candidate's **private supervisor scaffolding** — relay logs, the decision log, the worker
transcript — and is being excluded from the repository entirely. It is not a deliverable location.

T053's artifact is a **real deliverable**: FR-URL-019 requires creator provisioning to be performed by a
local operator script rather than an HTTP endpoint, and that script is part of what a reviewer runs. It was
mislocated inside `ops/`, so excluding `ops/` would have removed a deliverable from the repository along
with the scaffolding.

The owner's instruction was to move it **so the whole `ops/` folder can be cleanly ignored**. `scripts/`
is its proper home: it already holds the other operator and build scripts (`build.sh`, `ci.sh`, `scan.sh`,
`record-red.sh`).

### How the mislocation arose

The path came from T053's own `Artifact` field in the approved task plan, which named
`ops/scripts/provision-creator.sh`. The acting agent implemented the path as written rather than
questioning whether `ops/` was a deliverable location. Two things followed from that:

1. The script was created inside private scaffolding.
2. Committing T053 with `git add -A src ops docs …` swept **214 unrelated `ops/` files** into
   `b28e61a` — the supervisor's transcripts and decision log. That error was reported at the Slice 3
   boundary and is being corrected separately by the supervisor; **this record does not cover it.**

This change corrects the task plan's path, which is why it is change-controlled rather than a silent edit.

## Scope

**The path is the only thing that moves.** Explicitly unchanged:

- every behaviour of the script — argument parsing, the required `--expires` with no default, key
  generation, hashing, storage, the once-only terminal output;
- every assertion in `ProvisionCreatorTest` and `ProvisionCreatorIT` other than the path constant;
- FR-URL-019, and every clause of it;
- the script's `usage:` line, which names the bare filename and is path-agnostic by design.

`git` records this as a **rename**, so the file's history is preserved and `git log --follow` continues to
work across the move.

## Application order and verification

Applied in this order, so that no step depends on a reference that has not yet moved.

| # | Step | Expectation | Result |
|---|---|---|---|
| 1 | Move the file to `scripts/provision-creator.sh`, preserving the executable bit | file present and executable at the new path; old path absent | **EXECUTED — HOLDS** |
| 2 | `scripts/provision-creator.sh` header comment line 25 | usage line reads `scripts/provision-creator.sh` | **EXECUTED — HOLDS** |
| 3 | `ProvisionCreatorTest.java` `SCRIPT` constant | `Path.of("scripts/provision-creator.sh")` | **EXECUTED — HOLDS** |
| 4 | `ProvisionCreatorIT.java` `SCRIPT` constant | `Path.of("scripts/provision-creator.sh")` | **EXECUTED — HOLDS** |
| 5 | `tasks.md` T053 `Artifact` path | backtick path reads `scripts/provision-creator.sh` | **EXECUTED — HOLDS** |
| 6 | `quickstart.md` usage examples | both occurrences updated (2 of 2) | **EXECUTED — HOLDS** |
| 7 | `grep -rn "ops/scripts/provision-creator" specs docs src scripts` | **no matches** | **EXECUTED — HOLDS: none** |
| 8 | `-Dtest=ProvisionCreatorTest` | 10 tests pass at the new path | **EXECUTED — HOLDS: 10/10** |
| 9 | `-Dit.test=ProvisionCreatorIT` | 8 tests pass at the new path | **EXECUTED — HOLDS: 8/8** |
| 10 | `git diff --cached -M --stat` | the move is recorded as a **rename**, not add+delete | **EXECUTED — HOLDS** |
| 11 | `scripts/ci.sh` | GREEN, all six steps | **EXECUTED — HOLDS: GREEN** |

Steps 8 and 9 are a **real regression check, not a formality**: both classes locate the script through a
path constant, and `ProvisionCreatorIT` additionally *executes* it as a subprocess. A wrong constant fails
loudly at `scriptExists`, and a right constant with a broken move fails at the first `run(...)`.

## Reference completeness

The supervisor identified five reference locations. A repository-wide search found those five and one
more, which needed no change:

| Location | Action |
|---|---|
| `tasks.md:432` | updated |
| `quickstart.md:151` | updated |
| `quickstart.md:153` | updated |
| `provision-creator.sh:25` (header) | updated, moved with the file |
| `ProvisionCreatorTest.java:38` | updated |
| `ProvisionCreatorIT.java:49` | updated |
| `provision-creator.sh:47` (`usage:` line) | **no change needed** — it prints the bare filename, deliberately, so the message is correct from any directory |
| `ops/exchanges/*`, `ops/WORKER-TRANSCRIPT.md` | **out of scope** — supervisor relay logs; `ops/` is left exactly as it is |

## Conditions attached to the approval

1. **Commit explicit paths only.** No `git add -A`. Nothing under `ops/` may be staged beyond the moved
   file's own deletion, which is inseparable from the rename. The supervisor handles `ops/` exclusion and
   its git history separately.
2. **`git mv` was requested** so history is preserved. `git mv` is not on this session's command allow
   list, so the move was performed as a filesystem move plus `git add` of both paths — which is precisely
   what `git mv` does internally. Verification row 10 confirms the commit records a rename. Recorded here
   because the owner named the mechanism, and the mechanism differed.
3. **Re-run both provisioning test classes** and confirm green before committing. Rows 8–9.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| `ops/` excluded from the repository, and the 214 files committed in `b28e61a` untracked | Supervisor, next — immediately after this change |
| No future deliverable is placed under `ops/`; a task Artifact naming that path is a defect to raise, not a path to implement | Every remaining task with a non-`src/` artifact |
