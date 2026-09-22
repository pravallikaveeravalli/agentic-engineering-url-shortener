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
| Real, live, per-unit-stage AI demonstrations (model id, prompt, captured response) — six stages, one call each, via the Gemini CLI adapter, proving the transport seam is pluggable | [`docs/evidence/ai-demos/`](evidence/ai-demos/) — `T073a` through `T073f` |
| **The three demonstration scenarios, executed live on the Claude transport** — greenfield (DS-A), brownfield (DS-B), ambiguous (DS-C) | [`docs/evidence/ds-a/`](evidence/ds-a/), [`docs/evidence/ds-b/`](evidence/ds-b/), [`docs/evidence/ds-c/`](evidence/ds-c/) — see "The three scenarios" below |
| Deliberate red-phase captures — a real failing test, the exact output, the fix | [`docs/evidence/red-phase/`](evidence/red-phase/) |
| The MTTR measurement method's declared population, exclusions, and limitations | [`docs/evidence/mttr-method.md`](evidence/mttr-method.md) |
| Every known limitation and residual risk, disclosed by name | [`docs/LIMITATIONS.md`](LIMITATIONS.md) |
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
live evidence of this stage running against an actual model, not a scripted fake: one unit-level call via
the Gemini transport (`docs/evidence/ai-demos/T073b-ambiguity-detection-gemini-demo.txt`), and — the fuller
proof — dozens of independent live dispatches via the Claude transport across the DS-A/DS-B/DS-C scenario
runs (`docs/evidence/ds-a/`, `docs/evidence/ds-b/`, `docs/evidence/ds-c/`), including the genuinely
non-deterministic real findings documented in `docs/LIMITATIONS.md` §2-3.

## What the baseline deliberately omitted

**One entry, ever**: the per-creator-aggregate redirect rate-limit tier (`PVT-014`, `FR-URL-016`'s third
tier) — `docs/delivery/baseline-omissions.md`. Only the per-creator creation tier (`PVT-012`) and the
per-code redirect tier (`PVT-013`) shipped in the baseline; the tier's own absence, at that point, was
itself asserted by a dedicated test rather than left an unstated gap.

**Closed.** The closing task, `T136a`, in the brownfield demonstration scenario (DS-B), has run:
`AggregateRedirectLimiter` exists, is wired into `RedirectController`, and is enforced. Built directly
rather than by a live AI dispatch through the orchestrator — three genuine, capped, live orchestrator
attempts were made first (`docs/evidence/ds-b/attempts-1-3-finding.md`); the owner then directed the tier
be built directly rather than risk a further live-AI variance round, and that choice is disclosed by name
in `docs/evidence/ds-b/t136a-built-directly.md`, not presented as orchestrator output.
`baseline-omissions.md`'s own status field now says `Closed`, and FR-URL-016's traceability-matrix row
reads complete, not `PARTIAL`.

## The three demonstration scenarios

All three ran live, on the Claude transport (ADR-004 Amendment 04), each with its own evidence directory —
none of this is aspirational or "once run" language; it already happened and the evidence is on disk.

| Scenario | Subject | Evidence | Honest caveat |
|---|---|---|---|
| **DS-A — greenfield** | `GET /v1/version`, a genuinely new endpoint | [`docs/evidence/ds-a/`](evidence/ds-a/) — `run.json`, `bundle/` (T134's seven-item evidence bundle) | The literal S4-`SKIPPED` path this task's own artifact names has never once occurred across ~30 real attempts — every real, well-formed requirement this engagement tried still surfaced genuine ambiguity (`docs/evidence/ds-a/requirement-completeness-ceiling-finding.md`). What *is* real: attempt 26 ran S1→S10 clean in one continuous pass (one genuine S4 clarification resolved under standing delegation, a real S6 design gate, real S7-authored code, a real S8 pass, S9's drift guard confirming only executed behaviour), and that run's own feature is landed in this codebase. S11's own open gate has not been reached in a single continuous run; disclosed, not hidden. |
| **DS-B — brownfield** | The per-creator aggregate redirect tier (PVT-014) | [`docs/evidence/ds-b/`](evidence/ds-b/) — `run.json`, `test-results/`, `attempts-1-3-finding.md`, `t136a-built-directly.md` | Retry (a real two-attempt S3 recovery) and compensation (T090's real machinery against a real effect) were both genuinely demonstrated live, through the orchestrator. The tier's own code was **not** — built directly, after three capped live attempts, disclosed by name rather than claimed as AI output. Before/after tests real and passing against the landed tier. |
| **DS-C — ambiguous** | A requirement with an internal contradiction (expire vs. retain vs. trusted-partner access) | [`docs/evidence/ds-c/`](evidence/ds-c/) — `silence.json`, `replan.json`, `rejection.json` | The conflict is detected and named before implementation; the silence path is demonstrated first (`SAFE_STOP`, deadlines disclosed) before any resolution; a replan event lists invalidated stages and voided approvals. The trusted-partner feature itself was not built — the owner accepted the detect→clarify→replan→resume mechanism as proven and declined to build the feature it was demonstrating around (`docs/governance/gate-decisions/ds-c/scenario-accepted-as-demonstrated.md`). |

## The capability framing, carried forward — and one thing corrected

**Reviewers may submit any requirement they like, in principle** — the system is required to process
arbitrary requirements with no change to its executors (`FR-ORC-028`, `SC-016`), and every `StageExecutor`
is structurally unable to see which scenario produced its input (`StageInput`'s closed field list,
`StageExecutorContractTest`). **Every run is AI-backed**; a fresh run needs an authenticated CLI on the
machine (there is no keyless mode, ADR-004 Amendment 02). **Reviewing needs none of that** — the committed
evidence is complete and readable with no AI setup.

**Corrected again, forward from `quickstart.md`'s own T127 correction**: T127 found no HTTP surface accepted
a submitted requirement at all — only `GET /v1/runs/{runId}` (inspect) and `POST .../gates/{gateId}/decision`
(decide a gate) existed. That gap is now closed: `POST /v1/runs` (`RunSubmissionController`, T082a, CR-045)
admits a requirement, materializes the run, and returns its durable identifier — the response returns
before the run is fully driven (a bounded, fast reply; the rest advances on a background thread, matching
`contracts/openapi.yaml`'s own example), so `GET /v1/runs/{runId}` immediately afterward is still how a
caller watches it progress. "Reviewers may submit any requirement" is now something a reviewer can literally
do over HTTP, not only the system's proven design intent — carries the same structural guarantee this guide
already states (no executor can special-case a known input) now that there is a real endpoint for it to
apply to.

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
it. As of this guide (verified with a clean build, 2026-09-22): 792 fast-tier tests, 381 integration-tier
tests, 0 failures, 1 deliberate skip
(`StoreRestartResumeIT`, `@Disabled` with the reason in its own javadoc). **A raw, non-clean `target/`
directory can also carry `Ds*LiveRun` reports** (e.g. `DsALiveRun`, `DsBLiveRun`) — these drive the real
scenario evidence under `docs/evidence/ds-a|ds-b|ds-c/`, are excluded from the graded suite by name (same
convention as `T073a-f*LiveDemo`), require an authenticated Claude CLI, and are run manually and
separately; a clean build (`clean test`/`clean verify`) never includes their residue.

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
method's own declared population, exclusions, and limitations: `docs/evidence/mttr-method.md`. Real retry,
rollback, and compensation counts drawn from the three scenarios' own evidence (not a combined MTTR, which
was never computed over a persisted cross-scenario population — stated honestly rather than fabricated):
`quickstart.md` §5, "Reliability, as figures."

**6. What did the baseline deliberately omit, which run implemented it, and where is that run's evidence?**
Answered above, honestly: `docs/delivery/baseline-omissions.md` names it; the closing run (`T136a`, DS-B)
has executed and closed it — `docs/evidence/ds-b/` carries the evidence, and that file's own status field
now reads `Closed`.
