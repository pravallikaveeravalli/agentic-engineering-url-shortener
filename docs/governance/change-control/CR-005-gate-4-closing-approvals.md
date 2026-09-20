# Change Request CR-005 — Gate 4 Closing Approvals Reflected in the Specification

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20 |
| Approving authority | Pravallika Veeravalli (human owner), Gate 4 closing package |
| Raised by | Executing agent, implementing the owner's closing-package approvals |
| Affected approved artifacts | `specs/001-agentic-sdlc-url-shortener/spec.md`; `contracts/workflow-state.schema.json`; `contracts/openapi.yaml` |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Prior record | `docs/governance/gate-decisions/gate-04-closing-package.md` |

## Scope

Three things, all flowing from the closing package rather than introducing new design.

### 1. The PVT set becomes approved acceptance thresholds

The specification currently states: *"Every numeric figure below is a **proposed** validation target (PVT-nnn),
not a client requirement. None constrains implementation until approved."* All 15 are now approved, so that
preamble is false as written and must be corrected — leaving it would misrepresent approved thresholds as
proposals, which cuts against Principle X in the direction of *understating* commitment.

Approved as tabled, including the four the owner named explicitly: **PVT-009** as redefined (append-failure-rate
ceiling, not loss budget), **PVT-013/014** (the two-tier redirect limits), **PVT-015** (90-day idle retention),
and **PVT-001** approved knowing the analytics append now sits inside its budget and is measured separately.

### 2. DF-005 is closed

DF-005 tracked the Gate-1 and Gate-3 items still outstanding at the Plan gate: MTTR method, timebox and scope
controls, versioned API/schema deliverables with contract validation, PVT approvals, and the v1.1.0 constitution
check. All are now approved or discharged, so the finding closes.

### 3. `executorModeUsed` → `executorKindUsed`

CR-001 recorded this as a recommendation and did not apply it. The owner authorised it in the closing package's
housekeeping. Rationale unchanged from CR-001: "mode" now unambiguously means the run-level `ai` flag, so a
per-stage field named `...ModeUsed` collides with it, and "kind" is the owner's own vocabulary.

## Applied edits

| # | Location | Change |
|---|---|---|
| 1 | **§Proposed Validation Targets** heading and preamble | Retitled **§Validation Targets (approved)**; preamble now records approval at the Gate 4 closing package, 2026-09-20, and states that the values **constrain implementation** |
| 2 | **§Deferred Findings, DF-005** | Marked **CLOSED**, each constituent item's disposition named |
| 3 | **§Deferred Findings** preamble | Updated — DF-001, DF-002, DF-003, DF-005 resolved or closed; DF-004 remains open by design as deferred production enhancements |
| 4 | **FR-ORC-029** | Field name in the executor-kind requirement aligned to `executorKindUsed` |
| 5 | `contracts/workflow-state.schema.json` | `executorModeUsed` → `executorKindUsed` |
| 6 | `contracts/openapi.yaml` | `executorModeUsed` → `executorKindUsed` in `RunInspection.stages` |

## Impact analysis

**Version impact**: on the specification, MINOR — a status change from proposed to approved is a change in
obligation, since the values now constrain implementation. On the contracts, **breaking** in the abstract (a
property is renamed), but both files are unpublished and uncommitted-as-a-released-version, so it is
pre-first-version. Recorded rather than waved past, because `POL-CHG-001` governs contract changes regardless of
release state.

**Backward-compatibility impact**: none in practice — no consumer exists. The rename would be MAJOR against a
published contract and is classified as such for the record.

**Affected consumers**: internal only — run inspection response construction and the evidence labeller.

**Affected tests**: all PVT-referencing acceptance tests now assert against approved thresholds rather than
proposals; the schema-validation test picks up the renamed property; `SC-015`'s unlabelled-execution assertion is
unaffected in substance.

**Affected documentation**: `plan.md` Technical Context (performance goals no longer "awaiting approval"),
§Decisions Required (all items resolved), and `quickstart.md` where it referenced provisional values.

**Rollout / migration**: none. No data exists under the old property name.

## Defect found while applying: CR-001's enum extension was never applied

Recorded because it is exactly the kind of gap a change-control trail exists to catch. **CR-001 was marked
APPROVED and APPLIED on 2026-09-20 stating that two contract enums would gain `HUMAN`. The rename half was
applied; the `HUMAN` extension was not.** All three occurrences still read `["DETERMINISTIC", "AI", null]` when
CR-005 was applied.

Fixed here: `HUMAN` added to `executorKindUsed` in `workflow-state.schema.json`, `audit-event.schema.json`, and
`openapi.yaml`, each with a description noting it is recorded for a human-implemented stage under the no-plan
gate and is never flag-selectable.

**`executorClass` deliberately left unchanged**, contrary to CR-001's literal wording. CR-001 said both enums
would gain `HUMAN`, but `executorClass` is a stage's **design-time declaration** (`DETERMINISTIC`, `AI_CAPABLE`,
`HUMAN_GATE`), whereas `HUMAN` is a **runtime outcome** of the no-plan gate. Adding it to the declaration would
imply a stage can be declared human-implemented up front, which FR-ORC-029 explicitly denies ("`HUMAN` is never
flag-selectable"). Flagged for the owner rather than applied silently: if she intends `executorClass` to gain
`HUMAN` too, that needs its own decision, because it would change what a stage can declare.

**Process note**: CR-001's status said "applied exactly as proposed" when it was not. That claim was inaccurate,
and the inaccuracy survived a commit. The lesson is that an applied-status claim should be verified against the
artifact rather than asserted from intent.

## Residual risk

Low. The PVT status change is the one with teeth: the 15 values now bind, so a measurement that misses a
threshold is a failure rather than an observation against a proposal. That is the intended effect of approval,
and the owner approved PVT-001 with explicit knowledge that the analytics append consumes part of its budget.
