# Reviewer navigation guide

Task T128. US-5, SC-004. Where every artifact lives, and how to answer
[`quickstart.md` §5](../specs/001-agentic-sdlc-url-shortener/quickstart.md#5-reconstruct-a-run-as-an-assessment-reviewer-would)'s
six reconstruction questions **from committed artifacts alone** — no access to the session that produced
them, and, as of this guide, **no live run needed either**: reviewing this submission requires nothing AI
and nothing you don't already have.

## Where things live

| What | Where |
|---|---|
| The requirement, its acceptance criteria, and the ten-link traceability matrix | [`specs/001-agentic-sdlc-url-shortener/spec.md`](../specs/001-agentic-sdlc-url-shortener/spec.md) |
| The task plan, every task's Req/Deps/Artifact/Validate/Guard/Done fields, and checkbox state | [`specs/001-agentic-sdlc-url-shortener/tasks.md`](../specs/001-agentic-sdlc-url-shortener/tasks.md) |
| Design rationale, the observability/MTTR design, stage model | [`specs/001-agentic-sdlc-url-shortener/plan.md`](../specs/001-agentic-sdlc-url-shortener/plan.md) |
| Wire contracts (OpenAPI + JSON Schemas) | [`specs/001-agentic-sdlc-url-shortener/contracts/`](../specs/001-agentic-sdlc-url-shortener/contracts/) |
| How to run and verify everything, corrected against a real execution (T127) | [`specs/001-agentic-sdlc-url-shortener/quickstart.md`](../specs/001-agentic-sdlc-url-shortener/quickstart.md) |
| Architecture decisions, one file per decision, `Status` field states whether accepted | [`docs/governance/adr/`](governance/adr/) |
| Change requests — corrections, scope changes, contract syncs, each with its own reasoning and verification table | [`docs/governance/change-control/`](governance/change-control/) |
| Human gate decisions — one record per mandatory gate, immutable once filed | [`docs/governance/gate-decisions/`](governance/gate-decisions/) |
| Constitution amendments | [`docs/governance/amendments/`](governance/amendments/) |
| What was scoped, deferred, or cut, and why | [`docs/delivery/`](delivery/) — `scope-register.md` (the 9 must-have slices), `backlog.md` (indefinite deferrals), `baseline-omissions.md` (scheduled omissions with a named closing task), `critical-path.md`, `checkpoints.md`, `milestones.md`, `stop-conditions.md` |
| Real, live AI-stage demonstrations (model id, prompt, captured response) | [`docs/evidence/ai-demos/`](evidence/ai-demos/) — `T073a` through `T073f` |
| Deliberate red-phase captures — a real failing test, the exact output, the fix | [`docs/evidence/red-phase/`](evidence/red-phase/) |
| The MTTR measurement method's declared population, exclusions, and limitations | [`docs/evidence/mttr-method.md`](evidence/mttr-method.md) |
| What was built vs. deliberately not, the two named threat-model trade-offs | [`README.md`](../README.md) |
| The constitution itself — every MUST/SHOULD requirement this project is held to | [`.specify/memory/constitution.md`](../.specify/memory/constitution.md) |

## Which gate approved what

Nine numbered gates, `docs/governance/gate-decisions/gate-0N-<stage>.md`: `01` constitution, `02` specify,
`03` clarify, `04` ADR (plus a separate `04-closing-package`), `05` task plan, `06` scope control, `07`
contract baseline. Each record states, at minimum, the outcome, the deciding human, the decision date, the
artifact approved, and its reasoning — in the deciding human's own words where that reasoning matters. A
gate record is filed **immutable**: a later correction is a **new** record referencing the one it
supersedes, never an edit in place (`CLAUDE.md` §Immutability). If a record you are reading looks corrected,
look for the newer one that says so.

## What ambiguity detection actually is

**Semantic, not pattern-matching, and genuinely AI-backed** (`AmbiguityDetectionAiExecutor`, stage 3) — it
can recognise a conflict between two different *concepts* in a requirement, not only contradictory bounds
on one field. It is **non-deterministic by design**, which is safe specifically because its output feeds a
human gate rather than acting on its own authority. **Declared variability**: the same requirement can be
scored differently across runs, and that is disclosed rather than hidden. **When nothing is found, the
reason no clarification was required is recorded** — a non-detection is inspectable, not silent. Real,
live evidence of this stage running against the actual model, not a scripted fake: `docs/evidence/ai-demos/
T073b-ambiguity-detection-gemini-demo.txt`.

## What the baseline deliberately omitted

**One entry, as of this guide**: the per-creator-aggregate redirect rate-limit tier (`PVT-014`, `FR-URL-016`'s
third tier) — `docs/delivery/baseline-omissions.md`. Only the per-creator creation tier (`PVT-012`) and the
per-code redirect tier (`PVT-013`) ship in the baseline; the aggregate tier's absence is itself asserted by
a dedicated test (`RateLimiterTest.theAggregateTierIsNotPartiallyPresent`), not left an unstated gap.

**Which run closes it, and where that run's evidence is — stated honestly, not implied**: the closing task
is `T136a`, in the brownfield demonstration scenario (DS-B). As of this guide, Phase 8's demonstration
scenarios have not yet been executed as live, submitted runs (see the next section) — there is currently no
public HTTP endpoint to submit a fresh orchestration run at all. `baseline-omissions.md`'s own status field
says `open` for exactly this reason. When DS-B runs, its evidence will land under `docs/evidence/`
alongside the six AI-stage demos already there, and this guide's own claim here should be checked against
that evidence rather than trusted on the word of this sentence.

## The capability framing, carried forward — and one thing corrected

**Reviewers may submit any requirement they like, in principle** — the system is required to process
arbitrary requirements with no change to its executors (`FR-ORC-028`, `SC-016`), and every `StageExecutor`
is structurally unable to see which scenario produced its input (`StageInput`'s closed field list,
`StageExecutorContractTest`). **Every run is AI-backed**; a fresh run needs an authenticated CLI on the
machine (there is no keyless mode, ADR-004 Amendment 02). **Reviewing needs none of that** — the committed
evidence is complete and readable with no AI setup.

**Corrected, carried from `quickstart.md`'s own T127 correction**: as of this guide, there is no HTTP
surface that actually accepts a submitted requirement — only `GET /v1/runs/{runId}` (inspect) and `POST
.../gates/{gateId}/decision` (decide a gate) exist. "Reviewers may submit any requirement" is the system's
design intent, proven at the mechanism level (no executor can special-case a known input even if one
existed to submit); it is not yet something a reviewer can literally do over HTTP. Stated here rather than
left for a reviewer to discover by trying it.

**Committed DS evidence, once it exists, carries per-node executor-kind labels and a pinned model id** — an
owner instruction carried from `gate-04-adr.md`. Every stage execution states whether it ran
`DETERMINISTIC`, `AI`, or `HUMAN`, and every `AI` execution states which model answered, read from the
provider's own response where the provider names one and disclosed as a pin where it does not (see
`GeminiCliStageAiProvider`'s own javadoc).

## Answering `quickstart.md` §5's six questions — worked, from committed artifacts alone

This is the adversarial-reader proof itself: each answer below was produced using only the paths named,
without re-deriving anything from outside this repository.

**1. Which requirement produced which tasks?**
`spec.md`'s Traceability matrix — the `Task` column against each `Requirement` row. Mechanically
cross-referenced (not hand-maintained) by `TraceabilityReporter` (`src/main/java/agentic/shortener/audit/
TraceabilityReporter.java`), which `ZeroOrphanTest` runs against the real `spec.md`/`tasks.md` and asserts
zero orphans in both directions — every requirement traces to a task, and vice versa.

**2. Which tests were executed, and with what result?**
`target/surefire-reports/` (fast tier) and `target/failsafe-reports/` (integration tier) after running
`./scripts/build.sh test` and `./scripts/build.sh -DfailIfNoTests=false verify` — every `.txt` report names
its class and its `Tests run / Failures / Errors / Skipped` counts directly; nothing here can report a
generated-but-unexecuted test as passing, because the count comes from Surefire/Failsafe actually running
it. As of this guide: 717 fast-tier tests, 363 integration-tier tests, 0 failures, 1 deliberate skip
(`StoreRestartResumeIT`, `@Disabled` with the reason in its own javadoc).

**3. Where did the run retry, fall back, roll back, or compensate?**
There is no run-level "fallback" to find — `FR-ORC-015` was retired (Decision K, `CR-032`); bounded retry
then safe suspension is the entire degradation story, disclosed in `docs/delivery/backlog.md` and this
project's own `docs/LIMITATIONS.md` once T147 files it. Retry: every attempt is its own record (`Attempt`,
`RetryPolicyTest`); rollback and compensation are labelled distinctly (`CompensationRegister`, T059) — a
corrected short link is **set to expired, never deleted**.

**4. Who decided what, when, and why?**
`docs/governance/gate-decisions/` for the nine mandatory project gates; for an actual orchestration run's
own gates (once one exists), the `gate_decision` database table, append-only and immutable by database
privilege (`AuditImmutabilityIT`, `GovernanceImmutabilityIT`) — every decision materialized as a repository
or database record, never only a conversation.

**5. Which figures were measured, under what conditions, and which are proposed targets?**
Every figure `RunMetrics` or `MttrCalculator` produces is wrapped in `MeasurementLabel`
(`MEASURED`-with-conditions or `PROPOSED`-with-basis) — structurally, not by convention (T121). The MTTR
method's own declared population, exclusions, and limitations: `docs/evidence/mttr-method.md`.

**6. What did the baseline deliberately omit, which run implemented it, and where is that run's evidence?**
Answered above, honestly: `docs/delivery/baseline-omissions.md` names it; the closing run (`T136a`, DS-B)
has not executed as of this guide, and that file's own `open` status says so.
