# Change Request CR-009 — Time-Attitude Semantics and AI Scope in Plan §14

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20 |
| Approving authority | Pravallika Veeravalli (human owner), Gate 5 task-plan approval |
| Raised by | Executing agent, implementing the owner's time-attitude amendment |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/plan.md` §14 (APPROVED 2026-09-20) |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decision | `docs/governance/gate-decisions/gate-05-task-plan.md` |

## The decision requiring the change

Gate 5's time-attitude amendment, recorded verbatim in that gate record:

> Completeness over speed. The worker must not treat the 2–3-day framing as pressure. The schedule registers are
> still written as required deliverables — a control instrument, not a promise. Time-based triggers
> (engine-core-late, timebox-exhausted) ESCALATE TO ME for direction; they never automatically freeze work. Scope
> cuts happen only if I order one at a checkpoint. […] Apply this to T003/T005/T006 semantics and any plan §14
> wording that needs it — route through change control where approved text changes, on my authority today.

Plan §14 is approved text and carries wording the amendment changes, so it routes here.

## Applied edits

| # | Location | Was | Now |
|---|---|---|---|
| 1 | §14 stop conditions 1 and 4 (the two **time-based** ones) | Written as self-executing: "stop feature work"; "report exactly what is done" | Reclassified **ESCALATE** — the condition reports and asks the owner for direction; it does not freeze work or reduce scope on its own |
| 2 | §14 stop conditions 2 and 3 | Already halts | Marked explicitly **HALT**, with condition 3 (fabrication pressure) marked **non-waivable** — re-armed rather than changed |
| 3 | §14 scope-control checkpoints preamble | "cut from the backlog only" | Clarified: a checkpoint **observes and escalates with options**; a cut happens only on the owner's recorded order at that checkpoint |
| 4 | §14 timebox line | "2–3 days — APPROVED" | Adds that it is a **control instrument, not a promise**, that **no external submission deadline exists**, and that completeness takes priority over speed |
| 5 | §14 backlog list | Listed "AI participation for S3, S5, S6, S9" as deferred | **Removed.** All six AI adapters are in scope from the outset (owner's overruling, carried into `tasks.md` T073a–T073f). Reduction re-enters only by a recorded checkpoint order |

Edit 5 resolves a contradiction the executing agent had flagged and **not** acted on unprompted: `tasks.md` had
been corrected to all six adapters while `plan.md` §14 still listed four as deferred. The owner's authorisation of
"any plan §14 wording that needs it" covers it, and leaving it would have been the artifacts-disagreeing defect
class that checklist item CHK228 exists to catch.

## Assessed as needing no change

- **`spec.md`** — carries no timebox, schedule, or stop-condition wording. CN-007 defers timebox and scope
  controls to the Plan stage and says nothing about trigger semantics. **No spec edit made.**
- **The four §14 checkpoints themselves** — their content is unchanged; only what a checkpoint *does* with its
  observation is clarified.
- **Constitution** — unaffected. The amendment tightens nothing the constitution mandates and weakens nothing:
  Principle XI's completion standard, Principle X's non-fabrication clause, and Constitution VI's blocking rule
  all remain exactly as written, and the amendment explicitly re-arms the first two.

## Impact analysis

**Version impact**: **MINOR**. This changes obligations. Two triggers that previously authorised the worker to
halt or reduce scope on its own now require the owner's direction — a reallocation of authority from the agent to
the human, which is a change in what the agent may do, not a clarification of it.

**Backward-compatibility impact**: none. No interface, no consumer.

**Affected consumers**: the worker's own behaviour at checkpoints, and `tasks.md` T003/T005/T006, updated in the
same change.

**Affected tests**: none directly. The stop conditions are process controls, not code paths. `tasks.md` T006 now
requires the register to classify each condition HALT or ESCALATE, which is a documentation deliverable checked by
review.

**Affected documentation**: `plan.md` §14; `tasks.md` T003, T005, T006 (unapproved, edited directly);
`docs/delivery/*` registers when T001–T006 are executed.

**Rollout / migration**: none.

## Residual risk

**One risk worth naming, in the direction opposite to the usual.** Removing automatic time-based halts means work
can continue past a milestone without anything stopping it. The mitigation is that the checkpoint still *fires* and
still *escalates* — what changes is who decides the consequence. The owner accepted this explicitly, and the two
controls that protect correctness rather than schedule (fabrication halt, mandatory-policy block) are untouched and
re-armed.

The opposite risk — that a time trigger silently narrows scope and the narrowing reaches a reviewer as though it
were the plan — is what this amendment removes, and it is the more serious of the two for an assessment whose
subject is governed autonomy.
