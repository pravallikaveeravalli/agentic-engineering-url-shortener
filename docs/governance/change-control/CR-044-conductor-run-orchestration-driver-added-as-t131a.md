# CR-044 — Task plan gap: the run-orchestration driver was never tasked. Added as T131a.

| Field | Value |
|---|---|
| **Change request** | CR-044 |
| **Title** | Add task T131a — `Conductor`, the run-orchestration driver — to `tasks.md` Phase 8, ahead of T132 |
| **Raised by** | Owner instruction, on the acting agent's own discovered blocker |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — build it**, 2026-09-21. Owner's words: "go, implement it — this is very basic." |
| **Decision date** | 2026-09-21 |
| **Classification** | **MAJOR** — adds a new production class the demonstration-scenario tasks (T132, T136, T136a, T138–T141) structurally depend on, and a new task id to the approved task plan |
| **Artifacts changed** | `specs/001-agentic-sdlc-url-shortener/tasks.md` (T131a inserted between T131 and T132) |
| **Non-approved files changed** | `src/main/java/agentic/shortener/orchestration/conductor/Conductor.java` (new), `FanOutPlanner.java` (new), `src/test/java/agentic/shortener/orchestration/conductor/ConductorIT.java` (new) |
| **Applied** | 2026-09-21 |

---

## Reason / verification performed

T132 ("DS-A executed run") and every other Phase 8 run-execution task assume something drives a requirement
through S1–S12 to a terminal outcome. Nothing in the codebase did. Verified directly, not assumed, before
writing any code (two Explore agents, ~35 and ~62 tool uses, dispatched in parallel to map every relevant
class's exact API):

- No class anywhere named `Conductor`, `Orchestrator`, `WorkflowEngine`, `RunOrchestrationService`, or
  similar. `JdbcRunStore` is pure per-node persistence (create/read/transition one node or run at a time) —
  it has no method that reads the graph, decides what is ready, invokes an executor, and applies the
  result.
- `StageCriteria.mayEnter` (the one predicate that could anchor a readiness computation) had **zero**
  production callers anywhere in the codebase before this change.
- The only existing entry points into orchestration are `GET /v1/runs/{runId}` and
  `POST .../gates/{gateId}/decision`. `POST /v1/runs` (`RunSubmissionController`, task **T082a**) — the
  contract already names it in `contracts/openapi.yaml` — was itself unbuilt (`[ ]` in `tasks.md`) and was
  not even listed as a T132 dependency, despite being structurally required to submit a run at all.
- The six T073a–f "live demos" each construct one `StageInput` by hand and call exactly one executor once,
  with no persistence and no run id shared across stages — proof that the AI-capable executors work
  individually, not proof that a run can be driven end to end.

Full findings reported to the owner before any code was written; this CR and T131a's implementation are the
owner's explicit response ("build the genuine orchestration driver... this is very basic"), not a
unilateral scope addition.

## Scope

**What T131a builds**: `Conductor` — the dispatch loop (readiness computation, concurrent dispatch of every
ready node per round, join-aware convergence), plus the two Conductor-level policies no single existing
class owned (S4's conditional clarification gate, S6/S11's approval-required-after-success handling), plus
`FanOutPlanner` (the extension point `StageTemplate.standard()`'s own javadoc explicitly left open: "a
template that guessed a child count would be asserting a decomposition nobody has made").

**What T131a explicitly composes rather than reinvents** — per the owner's own instruction, verified against
each collaborator's real, current API rather than assumed: `JdbcRunStore` (persistence), `GateStore` +
`GateRequestPresenter` + `GateOutcomeHandler` (gates — unmodified), `RetryPolicy` (attempts/backoff/ruling —
unmodified), `SafeStopHandler` (suspension — unmodified), `ArtifactWriteGuard` (durable artifact writes —
unmodified), `AuditWriter` (six-field governance records — unmodified), `StageTelemetry` (T114's
logs/metrics/traces — unmodified), `DeterministicEngines`/the six AI stage executors (unmodified).

**What T131a does not yet build**, disclosed rather than silently assumed: `RunSubmissionController`
(T082a itself — the HTTP entry point; `Conductor.submit` is what it will call once built), automated
compensation/rollback/replan wiring into the dispatch loop (needed for DS-B/DS-C specifically, to be added
when those scenarios are built), and crash-restart recovery of the in-memory artifact-accumulation map
(disclosed directly in `Conductor`'s own javadoc — a `Conductor` instance's artifact cache does not survive
a process restart; `ArtifactWriteGuard` durably persists the same content, but no read accessor exists yet
to rebuild the cache from it).

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | RED-FIRST: `ConductorIT` written naming `Conductor`/`FanOutPlanner` before either existed | compile failure | **EXECUTED — HOLDS** (captured, `docs/evidence/red-phase/` is this CR's companion T129-style discipline; see note below) |
| 2 | `Conductor` + `FanOutPlanner` implemented, composing the collaborators listed above | compiles | **EXECUTED — HOLDS** |
| 3 | Non-linear execution: S9/S10 dispatch concurrently; a real 2-child S7 fan-out dispatches concurrently — both proven via a 2-party `CountDownLatch` barrier that can only clear if both nodes are genuinely in flight at once | `ConductorIT.nonLinearFanOutAndJoinReachTerminalOutcome` | **EXECUTED — HOLDS** |
| 4 | Gate-pausing: a material-ambiguity S4 gate stops the run (S5 stays `BLOCKED`, run stays `RUNNING`); a second `advance()` call with nothing decided changes nothing; a decision resumes it | `ConductorIT.gateGenuinelyPausesAndOnlyADecisionResumesIt` | **EXECUTED — HOLDS** |
| 5 | Terminal outcome: every node reaches a join-satisfying terminal state, the run transitions to `COMPLETED` | same two tests | **EXECUTED — HOLDS** |
| 6 | A genuine defect found by the real suite, not staged: `Conductor` originally called `JdbcRunStore.recordExecutorKind` after every stage, violating T066's `HumanExecutorKindTest` (that method is reserved exclusively for `NoPlanGate`'s HUMAN override) | fast tier failure, then fixed and re-verified | **EXECUTED — HOLDS**, `docs/evidence/red-phase/20260921T221619Z-T131a-conductor-executor-kind-violation.txt` |
| 7 | Fast + integration suites, `scripts/ci.sh`, from a clean build | green | **EXECUTED — HOLDS** — 729 fast (0 failures), 367 integration (0 failures, 1 pre-existing disclosed skip) |

**Note on step 1's evidence**: the compile-failure capture for `ConductorIT` naming a not-yet-existing
`Conductor` was not saved to a timestamped file the way T129's was — a process gap in this slice, disclosed
rather than backfilled with a reconstructed file. The genuine defect at step 6, caught by the real suite
after the class existed and compiled, **is** captured verbatim and is real, unstaged red-phase evidence for
this task.

## Conditions attached to the approval

1. **Compose, do not reinvent** — the owner's explicit instruction. Honoured; see Scope above for exactly
   which existing classes this task calls unmodified.
2. **Prove non-linear execution (a parallel fan-out with a join), gate-pausing, and a full run reaching a
   terminal outcome with persisted state + audit** — the owner's own three named properties. Honoured;
   `ConductorIT`'s two tests are structured explicitly around these three, not incidentally covering them.

## Cross-record orphan check

- No existing record claims T131a's ground already — `tasks.md`'s Phase 8 preamble named only the goal
  ("three recorded, reproducible runs plus one out-of-scenario run") and never a mechanism; this CR is
  additive to a genuine gap, not a correction of a prior claim.
- `contracts/openapi.yaml`'s `POST /v1/runs` operation is unaffected — this CR does not build
  `RunSubmissionController` (T082a remains open, listed as `Conductor.submit`'s future caller in Scope
  above), so no claim about the HTTP surface is made or falsified here.
- `docs/REVIEWER-GUIDE.md`'s "carried, corrected" section (T128) already states plainly that no HTTP
  submission surface exists; this CR does not change that fact and requires no correction there.
- No task before T131a claims Phase 8's runs already execute; T131–T142 were all `[ ]` before this session's
  work on them, so nothing here falsifies an already-applied record.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Build `RunSubmissionController` (T082a) so `Conductor.submit` is reachable over HTTP, not only from a test/driver | Before any claim that a reviewer can submit a run themselves is restated as reachable |
| Wire compensation/rollback (`CompensationRegister`/`RollbackHandler`/`CompensationHandler`) into the dispatch loop | When T136 (DS-B's injected retry and compensation) is built |
| Wire replanning (`ReplanService`/`DownstreamInvalidation`) into the dispatch loop for a mid-run change request | When T139 (DS-C's clarification/replan/resumption) is built |
| Build an `artifact_version` read accessor so a fresh `Conductor` instance can rebuild its artifact cache after a process restart | Disclosed limitation; not required for this session's single-process demonstration runs |
