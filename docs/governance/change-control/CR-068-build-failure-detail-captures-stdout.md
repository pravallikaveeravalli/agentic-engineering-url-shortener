# CR-068 — `GitWorktreeBranchApplier`'s build-failure detail now captures stdout, not stderr alone: Maven's
own real compile errors under `-q` print to stdout, and the detail message was silently blank on exactly the
failure it exists to explain

| Field | Value |
|---|---|
| **Change request** | CR-068 |
| **Title** | The build-failure branch of `GitWorktreeBranchApplier.apply` now includes `build.stdout()` in its own detail message, not `build.stderr()` alone |
| **Raised by** | This session's own investigation, this turn: attempt 2 of the PART 3 live-run cap reported "change set applied and committed... but does not compile: " with a genuinely EMPTY detail, blocking diagnosis of a real S7 failure |
| **Decided by** | Pravallika Veeravalli (standing delegation: a mechanical, obviously-correct diagnostic fix, no design ambiguity) |
| **Decision** | **APPROVED under the standing delegation** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — a diagnostics-only fix; does not change `ApplyResult`'s own shape, `buildable()`'s own semantics, or which branch is kept/removed |
| **Artifacts changed** | `src/main/java/agentic/shortener/orchestration/conductor/GitWorktreeBranchApplier.java` |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/conductor/GitWorktreeBranchApplierTest.java` |

---

## Confirmed empirically, not assumed

A real, live S7 dispatch this turn applied and committed a change set cleanly, then failed the build check —
the FIRST time in this engagement's history a change has reached that specific path with a genuine compile
defect (every prior S7 failure was earlier in the pipeline: a corrupt patch, or JSON that never parsed at
all). The reported detail was empty. Investigated directly: a probe file with deliberately invalid Java,
compiled via this project's own real `./scripts/build.sh -q compile` (`GitWorktreeBranchApplier`'s own
default build command) with stdout and stderr captured to separate files, confirmed **2416 bytes on stdout,
0 bytes on stderr** — Maven's own real compile diagnostics, under `-q`, print to stdout only. The previous
code read `build.stderr()` alone.

## The fix

`GitWorktreeBranchApplier`'s own build-failure detail now reads `(build.stdout() + "\n" +
build.stderr()).strip()` — both streams included, so a build command that genuinely does report to stderr
(a future injected one, or a different real toolchain) still has its own output shown too, nothing dropped
either way.

## RED-FIRST verification

New test injects a build command that echoes a real-shaped compile-error message to stdout and exits
non-zero — proves the message reaches `ApplyResult.detail()`, where it previously would have been silently
lost. 10 tests in `GitWorktreeBranchApplierTest`, all green. `ci.sh` green.

## Scope

**What changed**: one detail-message construction, plus git-command failure paths (`git add`/`git commit`)
were checked and left as-is — real `git` subprocesses reliably report to stderr, unlike Maven under `-q`, so
they were not touched.

**What did not change**: `ApplyResult`'s own record shape; which branch is kept or removed on which outcome;
`BranchApplier`'s own interface.

## Conditions attached to the approval

1. **Empirically confirmed, not assumed** — honoured; see the probe investigation above.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| The original S7 compile failure this turn (attempt 2, PART 3) remains genuinely undiagnosed — this fix means the NEXT occurrence, if any, will carry real diagnostic text instead of a blank one | Whenever S7 next hits a build-check failure |
