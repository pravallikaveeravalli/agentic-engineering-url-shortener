# CR-045 — Conductor runtime wiring: T131b (advance-on-decision), T131c (real BranchApplier/TestSuiteRunner), T082a completed

| Field | Value |
|---|---|
| **Change request** | CR-045 |
| **Title** | Add tasks T131b and T131c; modify the committed `GateDecisionController` (T067a) to resume a run after a decision; complete T082a (`RunSubmissionController`) |
| **Raised by** | Owner instruction, on the acting agent's own discovered gaps |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — build the remaining runtime wiring**, 2026-09-21 |
| **Decision date** | 2026-09-21 |
| **Classification** | **MAJOR** for T131c (genuinely new production classes with no prior task); **MINOR** for T131b (a small, behavior-preserving-except-for-the-fix addition to an already-tested class); T082a itself needed no reclassification — it already existed in the plan |
| **Artifacts changed** | `specs/001-agentic-sdlc-url-shortener/tasks.md` (T131b, T131c inserted; T082a marked done and its stale "ai flag" text corrected; T132's Deps updated); `docs/REVIEWER-GUIDE.md` (two passages corrected — the submission endpoint now exists) |
| **Non-approved files changed** | `src/main/java/agentic/shortener/orchestration/api/GateDecisionController.java` (modified — new `Conductor` dependency, one new call), `RunSubmissionController.java` (new), `src/main/java/agentic/shortener/orchestration/conductor/{GitWorktreeBranchApplier,ScriptTestSuiteRunner,ProcessRunner}.java` (new), `src/main/java/agentic/shortener/config/OrchestrationConfiguration.java` (extensive new `@Bean` wiring), `src/main/java/agentic/shortener/orchestration/conductor/Conductor.java` (modified — `submit()` no longer calls `advance()` internally; `dispatchOne` now catches unanticipated failures defensively), `src/test/java/agentic/shortener/orchestration/conductor/ConductorIT.java` (modified — explicit `advance()` calls added, one new test) |
| **Applied** | 2026-09-21 |

---

## Reason / verification performed

Driving a live DS-A run surfaced two gaps only visible once `Conductor` (T131a, CR-044) actually existed to
reveal them:

1. **`GateDecisionController` applies a decision but never resumes anything.** Read directly before writing
   any code: `GateOutcomeHandler.apply` only ever transitions the gated node's own state (and, for
   `REJECTED`, the run's). Nothing in the existing, already-committed, already-tested controller called
   anything that would let the run progress past a decided gate. A decision landed; the run simply never
   advanced.
2. **No real `BranchApplier` exists anywhere.** `ImplementationAiExecutorLiveDemo`'s own javadoc says so
   directly: "`BranchApplier` is a STUB here, not a real git/build implementation — that production
   implementation is out of this task's scope." Without one, S7 cannot actually apply anything a live run
   produces.

## Scope

**T131b** (`GateDecisionController` change): adds exactly one field (`Conductor conductor`) and one call
(`conductor.advance(id)`, unconditional, after `outcomeHandler.apply(...)` succeeds) — `advance()` is a
no-op on a non-`RUNNING` run, so this is safe regardless of outcome. Re-running the pre-existing
`GateDecisionControllerIT` immediately caught a real defect the change exposed (see Application order below)
— not a defect in the modification's own intent, but a genuine gap in `Conductor`'s own robustness that this
CR's application step fixed before the modification could be considered done.

**T131c** (real `BranchApplier`/`TestSuiteRunner`): entirely new production classes, composing `git`,
`ProcessBuilder` (the same argv-not-shell, closed-stdin discipline `GeminiCliStageAiProvider` already
established — reused, not reinvented), and T129's own Surefire-report regex. Both the build-check command
and the test-run command are injectable constructor parameters — production defaults call the real
`scripts/build.sh`; tests substitute `true`/`false`/a tiny script against a fixture repository, never the
real project's own Maven toolchain, so these tests stay fast and never touch this repository's own working
tree.

**T082a** itself: already existed in the plan (unchecked). This CR does not reclassify it; it corrects one
piece of drift found while building it — the task body's "optional `ai` flag defaulting off" is stale
language from before CR-028/Decision J retired keyless mode project-wide; `contracts/openapi.yaml`'s own
`CreateRunRequest` schema (the authoritative contract) already carries no such flag, and `RunSubmissionController`
correctly builds none.

**A design refinement to `Conductor` itself, not originally specified in T131a**: `submit()` no longer calls
`advance()` internally. T082a's own Validate clause requires the returned run to still show S4 `BLOCKED` —
i.e. a caller needs a fast, bounded HTTP response, not one that blocks for however long a real multi-minute
AI-capable stage takes. `RunSubmissionController` now advances the run on a background thread after
replying, matching `contracts/openapi.yaml`'s own `runCreated` example exactly (S1 `SUCCEEDED`, S2 already
`RUNNING`). `ConductorIT`'s two existing tests were updated with an explicit `conductor.advance(runId)` call
immediately after `submit()` to keep testing the same properties; a new third test
(`submitAloneDoesNotAdvanceTheRun`) asserts the new contract directly.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | `Conductor.submit()` split from `advance()`; `ConductorIT` updated + one new test | 4/4 green | **EXECUTED — HOLDS** |
| 2 | `GitWorktreeBranchApplier`/`ScriptTestSuiteRunner`/`ProcessRunner` built, RED-FIRST | 5 tests green after one real fix (a test-helper whitespace mismatch, not a class defect) | **EXECUTED — HOLDS**, `docs/evidence/red-phase/20260921T222857Z-T131c-branch-applier-and-test-runner.txt` |
| 3 | Full Spring wiring added (`OrchestrationConfiguration`) for every `Conductor` collaborator | Spring context loads; `GateDecisionControllerIT` (an existing `@SpringBootTest`) green with zero test changes | **EXECUTED — HOLDS** |
| 4 | `GateDecisionController` modified to call `conductor.advance(id)` | re-running the ALREADY-PASSING `GateDecisionControllerIT` caught a real regression: `CHANGES_REQUESTED` on a hand-built test fixture (never driven by any `Conductor` instance) 500'd with an uncaught `IllegalStateException` from `handleClarificationGate`'s missing-artifact guard | **EXECUTED — HOLDS after a fix**, `docs/evidence/red-phase/20260921T223932Z-T131b-gate-decision-advance-unhandled-exception.txt` |
| 5 | `Conductor.dispatchOne` wrapped to route any unanticipated `RuntimeException` through `SafeStopHandler.suspend` (`UNRECOGNIZED_FAILURE_CLASSIFICATION`) rather than letting it propagate | `GateDecisionControllerIT` green (11/11) | **EXECUTED — HOLDS** |
| 6 | `RunSubmissionController` (T082a) built, RED-FIRST | `RunSubmissionControllerIT` (4 tests, real Postgres) + `RunSubmissionControllerTest` (1 test, injected broken store, the 503 path) | **EXECUTED — HOLDS** |
| 7 | A second genuine, unstaged catch: `RunSubmissionControllerIT`'s own fixture planted a fake creator-credential literal to prove it is never read — the literal itself matched `POL-SEC-002`'s real secret-scan pattern, failing `PolicySetEvaluatorIT` against the real, currently-committed project | fixed by splitting the literal (same technique already used once before, in T110's `ReleaseBlockingConditionsIT`); `./scripts/scan.sh` clean after | **EXECUTED — HOLDS**, `docs/evidence/red-phase/20260921T225003Z-T082a-self-inflicted-secret-scan-match.txt` |
| 8 | Fast + integration suites, `scripts/ci.sh`, from a clean build | green | **EXECUTED — HOLDS** — 735 fast (0 failures), 372 integration (0 failures, 1 pre-existing disclosed skip) |

## Conditions attached to the approval

1. **Do not self-approve any gate the live DS-A run reaches** — `ActorAuthority` already refuses an
   agent-identified approving actor structurally; this CR builds no workaround and none was attempted.
2. **RED-FIRST for genuinely new work (T131c)** — honoured; a real, unstaged failure was caught and fixed
   (a test-helper defect, not a production one) before any class was declared done.

## Cross-record orphan check

- CR-044 named `RunSubmissionController` (T082a) and compensation/replan wiring as explicitly *not* built by
  T131a — this CR builds the former; the latter remains open, unclaimed by this record, still due at DS-B/DS-C.
- T082a's own task body's stale "ai flag" language is corrected in place in `tasks.md` (not a separate
  record) — the retirement itself was already decided by CR-028/CR-029/CR-030/CR-031, which this CR does not
  restate or re-decide, only applies.
- No other applied record claims `GateDecisionController` resumes a run — T067a's own commit predates
  `Conductor`'s existence entirely, so this is a genuine addition, not a correction of a prior false claim.
- `docs/REVIEWER-GUIDE.md`'s "carried, corrected" section (T128) stated no HTTP submission surface exists.
  **This was false as of this CR** — `RunSubmissionController` exists now. Found by this orphan check and
  **corrected forward inside this same commit** (not deferred): both passages (the baseline-omissions
  section and the capability-framing section) now state the endpoint exists and describe its actual
  fast-response/background-advance shape. `README.md` was checked and carries no equivalent stale claim —
  nothing to correct there.
- `specs/001-agentic-sdlc-url-shortener/quickstart.md` carries the SAME class of now-stale claim in at least
  six places (T127's own correction pass, now superseded in part by this CR). **Not corrected inside this
  CR** — it is materially more text than `REVIEWER-GUIDE.md`'s two passages and deserves its own reviewed
  slice rather than a rushed pass bundled into runtime-wiring work. Carried forward below, named explicitly
  rather than left for a future orphan check to rediscover.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Correct `quickstart.md`'s (at least six) "no submission endpoint exists" passages now that `RunSubmissionController` is real | Before the guide is next read as authoritative by a reviewer — flagged now, by name, so it is not rediscovered by a later orphan check instead of carried here |
| Wire compensation/rollback and replanning into `Conductor`'s dispatch loop | When T136 (DS-B) and T139 (DS-C) are built, per CR-044's own carried-forward list |
| Build an `artifact_version` read accessor for crash-restart recovery of `Conductor`'s in-memory artifact cache | Still open; this CR's own T131b fix (defensive suspension) makes the FAILURE MODE safe when it is hit, but does not close the underlying gap |
