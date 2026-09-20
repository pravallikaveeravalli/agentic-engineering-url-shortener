# Gate Decision Record — Gate 5: Task Plan Approval

| Field | Value |
|---|---|
| Gate | Task plan approval (blocks Implement) |
| Outcome | **APPROVED** — 158 tasks, with the owner's time-attitude amendment applied |
| Deciding human | Pravallika Veeravalli |
| Decision date | 2026-09-20 |
| Artifact approved | `specs/001-agentic-sdlc-url-shortener/tasks.md` (158 tasks, T001–T152 plus T073a–T073f) |
| Governing constitution | v1.1.0 · **Policy set**: `policy-set-1.0.0` |
| Recorded in | This record; `tasks.md` T003/T005/T006 semantics; `plan.md` §14 via **CR-009**; the commit landing the task plan |

## Reason / verification performed

The owner reviewed the 158-task plan and approved it, recording her answers to the open items and adding one
amendment. Her decisions, in her terms:

> Build order approved (shortener MVP first — a governed lifecycle over non-functional software would demonstrate
> nothing); AI wiring for all six stages stands as I overruled earlier (estimates must not price AI-authored work
> at human speed); no external submission deadline exists.

### Time-attitude amendment — recorded verbatim

> **Completeness over speed.** The worker must not treat the 2–3-day framing as pressure. The schedule registers
> are still written as required deliverables — a control instrument, not a promise. Time-based triggers
> (engine-core-late, timebox-exhausted) ESCALATE TO ME for direction; they never automatically freeze work. Scope
> cuts happen only if I order one at a checkpoint. Unchanged and fully armed: the fabrication-pressure halt
> (non-waivable), the mandatory-policy-FAIL block, and the rule that tests, validation, and reviewer evidence are
> never cut. Apply this to T003/T005/T006 semantics and any plan §14 wording that needs it — route through change
> control where approved text changes, on my authority today.

**What this changes.** The four §14 stop conditions were written as a mix of halts and automatic reductions. Two
of them are time-based, and under this amendment those two become **escalations to the owner for direction**
rather than self-executing freezes. The registers themselves remain required deliverables — the amendment changes
what a trigger *does*, not whether the control exists.

**What this does not change**, and is explicitly re-armed:

| Control | Status |
|---|---|
| Fabrication-pressure halt | **Unchanged, non-waivable.** Presenting proposed or simulated results as executed remains a hard stop |
| Mandatory-policy-`FAIL` block | **Unchanged.** A failing mandatory policy still blocks downstream progression and release readiness |
| Tests, validation, reviewer evidence are never cut | **Unchanged.** No checkpoint and no escalation may authorise cutting them |
| Checkpoint valve on AI wiring | **Unchanged and available** — but only by the owner's order at a checkpoint, on the record |

## Conditions attached to the approval

1. Time-based triggers escalate to the owner for direction; they never automatically freeze work or reduce scope.
2. Scope cuts occur only on the owner's order at a checkpoint, recorded.
3. The schedule registers (T001–T006) remain required deliverables, written as a control instrument rather than a
   commitment to a date. No external submission deadline exists.
4. Gate identifiers are not pre-allocated: this record takes the next free number, and `tasks.md` references
   `gate-<next>` (see below).
5. `plan.md` §14 changes route through change control — **CR-009**, approved on the owner's authority, 2026-09-20.
6. Implementation does not begin, and `/speckit-analyze` is not run, at this gate.

## Numbering resolution

The owner extended the no-preallocation principle — established for change requests in `CR-008`'s numbering note —
to gate identifiers.

| Item | Was | Now |
|---|---|---|
| This record | — | **`gate-05-task-plan.md`** (next free; committed records ran gate-01..gate-04 plus the gate-04 closing package) |
| `tasks.md` T007 | `gate-05-scope-control.md` — **would have collided with this record** | `gate-<next>-scope-control.md` |
| `tasks.md` T152 | `gate-06-release-readiness.md` | `gate-<next>-release-readiness.md` |

The collision was real, not hypothetical: T007 had reserved `gate-05` while `tasks.md` was unapproved, and this
approval is itself the fifth gate. Pre-allocating an identifier in a registry that other work appends to produces
exactly this, which is why the convention now forbids it for both gates and change requests.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Time-based triggers escalate to the owner rather than freezing work; the worker does not treat the timebox as pressure | Every checkpoint |
| Scope cuts only on the owner's recorded order at a checkpoint | Every checkpoint |
| Fabrication-pressure halt armed and non-waivable | Continuous |
| Mandatory-policy-`FAIL` block armed | Continuous |
| Tests, validation, and reviewer evidence are never cut by any trigger or checkpoint | Continuous |
| AI wiring for all six stages (T073a–T073f) is in scope from the outset; reduction only by recorded checkpoint order | Slice 4–6 |
| Gate and CR identifiers taken at filing time, never pre-allocated | Every record |
| Implementation blocked until Analyze completes and its findings are dispositioned | Implement gate |
| Enforcement points from `gate-04-adr.md` and `gate-04-closing-package.md` remain in force and are not superseded | As stated there |
