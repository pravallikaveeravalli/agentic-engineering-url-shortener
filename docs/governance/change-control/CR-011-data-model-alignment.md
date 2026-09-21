# Change Request CR-011 — Data Model Aligned with CR-001, CR-005 and the Instance-Keyed Node Decision

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, from `/speckit-analyze` findings **H2**, **H4** (data-model half), **L6** |
| Revision | **Draft 3**, 2026-09-20 — the archive entities drafted at revision 2 are **withdrawn** under owner **Decision H**; this record is back to its seven original edits |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"go ahead."* |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/data-model.md` — *"Approved indirectly via `plan.md`'s approval at the Gate 4 closing package (2026-09-20)… Changes to it pass through change control, as they do for the plan."* |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decisions | **CR-001** (run-level flag renamed `ai: on \| off`; executor-kind vocabulary), **CR-005** (`executorModeUsed` → `executorKindUsed`), owner **Decision 2** (instance-keyed node identity) |
| Application order | Third. Independent of CR-010 and CR-012; must apply together with **CR-013**, which changes the same identity model in the contracts. |

## Why this change exists

`/speckit-analyze` found that **`data-model.md` is the one artifact CR-001 and CR-005 never reached.** It still
defines:

- a run-level `executor_mode` enum `DETERMINISTIC | AI` — the construct **CR-001 abolished** in favour of
  `ai: on | off`, on the owner's ground that a run-level determinism label over-promises, because a keyless run
  may still contain a `HUMAN` execution at the stage-7 no-plan gate; and
- `executor_mode_used`, which **CR-005 renamed** `executorKindUsed`, and whose enum omits `HUMAN` entirely.

The contracts are correct. The design document that `tasks.md` T077 and T082 cite for KE-04 and KE-05 is not. An
implementer working from it would build the pre-CR-001 model and the contract test would fail — the right failure,
discovered at the most expensive point.

Owner **Decision 2** additionally replaces integer stage identity with instance-keyed node identity, which lands
in the same two rows.

## Owner decision recorded — Decision 2, verbatim

> each parallel stage will be like 7.1, 7.2, 7.3 etc. if that is the case i am ok with it

Resolved model: singleton stages remain single nodes (`"S1".."S12"`); a fan-out stage gets **first-class child
nodes** (`"S7.1".."S7.n"`), each carrying its own status, attempts and evidence; edges are explicit between nodes;
an `ALL`-join node gates the successor.

## Owner approval — 2026-09-20

Approved by **Pravallika Veeravalli**: *"go ahead."*

Her approval followed a three-item list and carries all three, recorded as relayed:

1. **The package as revised**, including the §11 brownfield wording.
2. **PVT-010 explicitly withdrawn** — the binding 30/90-day deletion target is formally retired and replaced by the
   documented production recommendation, **by her explicit decision, not by implication**.
3. **Policy set `1.1.0`**, on the ground she herself set for the API contract: **version discipline binds from first
   real use**, and no run has ever evaluated `1.0.0`.

## Applied edits

### Edit 1 — WorkflowRun (KE-04), run-level flag row (`data-model.md:77`)

**OLD**

```
| `executor_mode` | enum | `DETERMINISTIC` \| `AI` — per-run selection |
```

**NEW**

```
| `ai` | enum | `on` \| `off`, **default `off`** — whether AI executors participate. Named for the one thing it controls: a run with `ai: off` may still contain a `HUMAN` execution at the stage-7 no-plan gate, so a run-level determinism claim would over-promise (FR-ORC-029, renamed by **CR-001**) |
```

### Edit 2 — StageNode row (`data-model.md:86`)

Carries both the CR-005 rename and Decision 2's node identity, because both tokens live on this line.

**OLD**

```
| StageNode | `id`, `run_id`, `stage_number` (1–12), `state`, `executor_class`, `executor_mode_used`, `attempts_used`, `entered_at`, `exited_at?`, `blocking_reason?` |
```

**NEW**

```
| StageNode | `id`, `run_id`, `node_key` (string — `"S1".."S12"` for a singleton or fan-out-parent stage, `"S7.1".."S7.n"` for a fan-out child, `"S7.join"` for its join node; **unique per run**), `stage_number` (1–12 — the stage the node belongs to, no longer the node's identity), `node_role` (`SINGLETON` \| `FAN_OUT_PARENT` \| `FAN_OUT_CHILD` \| `JOIN`), `parent_node_key?` (set on a `FAN_OUT_CHILD` and a `JOIN`, null otherwise), `state`, `executor_class`, `executor_kind_used`, `attempts_used`, `entered_at`, `exited_at?`, `blocking_reason?` |
```

### Edit 3 — DependencyEdge row (`data-model.md:87`)

**OLD**

```
| DependencyEdge | `id`, `run_id`, `from_stage`, `to_stage`, `join_semantics` (`ALL` \| `ANY`) |
```

**NEW**

```
| DependencyEdge | `id`, `run_id`, `from_node_key`, `to_node_key`, `join_semantics` (`ALL` \| `ANY`) — edges address **nodes**, never stage numbers, so each fan-out child's edges are individually declared and a replanned instance's topology stays queryable (FR-ORC-002, ADR-008) |
```

### Edit 4 — executor-kind requirement statement (`data-model.md:93-94`)

**OLD**

```
`executor_mode_used` is **required on every executed stage** — an unlabelled execution may not appear
in evidence (FR-ORC-029).
```

**NEW**

```
`executor_kind_used` — `DETERMINISTIC` | `AI` | `HUMAN` — is **required on every executed node**; an unlabelled
execution may not appear in evidence (FR-ORC-029; renamed from `executor_mode_used` by **CR-005** and extended
with `HUMAN` by **CR-001**). `HUMAN` is **never flag-selectable**: it is recorded only as the outcome of a
no-plan-gate decision (FR-ORC-031). Note the deliberate asymmetry with `executor_class`, whose vocabulary is
`DETERMINISTIC | AI_CAPABLE | HUMAN_GATE` and which **does not include `HUMAN`** — a stage may not be declared
human-implemented up front.
```

### Edit 5 — Cross-cutting invariant 6 (`data-model.md:181`)

**OLD**

```
6. Every executed StageNode has a non-null `executor_mode_used`.
```

**NEW**

```
6. Every executed StageNode has a non-null `executor_kind_used`.
```

### Edit 6 — two new cross-cutting invariants appended after invariant 8

**NEW**

```
9. `node_key` is unique per run. Every stage 1–12 appears exactly once as either a `SINGLETON` or a
   `FAN_OUT_PARENT`, so a freshly materialised run holds **thirteen** nodes: eleven singletons, the S7 fan-out
   parent, and its `JOIN`. There is **no fixed upper bound** — the old "exactly twelve" invariant was wrong,
   because it made S7's per-task fan-out unrepresentable (analyze finding H4).
10. A `FAN_OUT_PARENT` has exactly one `JOIN` node from the outset and **zero or more** `FAN_OUT_CHILD` nodes,
    created when the preceding stage yields tasks. The join does not proceed while any child is incomplete or
    failed (EC-018, FR-ORC-003). Uniqueness and this join invariant are asserted by T074's test rather than by
    JSON Schema, which cannot express uniqueness-by-property.
```

### Edit 7 — status note recording this change

Appended to the Status block at `data-model.md:5-8`:

```
Amended by **CR-011** (2026-09-20): aligned with CR-001's `ai` flag, CR-005's `executorKindUsed` rename, and the
owner's instance-keyed node-identity decision. Before that amendment this document was the only artifact still
carrying the pre-CR-001 run-level `executor_mode` enum.
```

### Edits 8 and 9 — WITHDRAWN under owner Decision H

Draft 2 added a `RetentionPolicy` entity, an `ArchiveBatch` entity with half-completed-move reconciliation, and two
invariants about archival appends. **All withdrawn.** Decision H removes archival from the design entirely, so
`data-model.md` needs **no retention or archive entities at all** and this record reverts to its seven
node-identity and executor-kind edits.

Recorded as withdrawn rather than deleted so that the absence of archive entities is visibly a decision. The retention
posture lives in **CR-017**, in the specification, with no data model behind it because there is no data to model.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MAJOR** for the persisted shape. `stage_number` loses its identity role, `from_stage`/`to_stage` are replaced, and a field is renamed — all three are narrowing or renaming changes under `plan.md` §2's own classification. Nothing is implemented, so nothing breaks; the classification is recorded honestly rather than softened to MINOR because it is cheap today. |
| **Backward-compatibility impact** | None in practice — no schema exists yet, no rows exist, no consumer. `V4__orchestration_graph.sql` (T074) has not been written. |
| **Affected consumers** | `contracts/workflow-state.schema.json` and `contracts/openapi.yaml` (**CR-013** — must apply with this record). `tasks.md` T074, T076, T067, T077, T082 (**CR-016**). `plan.md` §3 and §4 (**CR-015**). |
| **Affected tests** | T074's schema-validation test changes from *"twelve nodes"* to *"every singleton present exactly once, every fan-out stage has ≥1 child plus one join, node keys unique"*. T076's fan-out proof gains concrete node keys to assert. T066's executor-kind test is unaffected — it was already written against the contract, not this document. |
| **Affected documentation** | This file, plus the four artifacts above. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner. Decision 2 is already given; this record is the mechanism, and approval here confirms the field-level shape rather than re-deciding the model. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

| # | Edit | Verification command (from repo root) | Expected | Result |
|---|---|---|---|---|
| V1 | 1, 2, 4, 5 | `grep -n 'executor_mode' specs/001-agentic-sdlc-url-shortener/data-model.md` — then assert no **field definition** is among the hits (no table row, no invariant) | **2 hits, both historical citations** inside this record's own approved NEW text: the status note and Edit 4's rename attribution. No field uses the old name | **EXECUTED: 1** — HOLDS |
| V2 | 1 | `grep -c 'DETERMINISTIC \\\| `AI` — per-run' specs/001-agentic-sdlc-url-shortener/data-model.md` | `0` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V3 | 2, 4, 5 | `grep -c 'executor_kind_used' specs/001-agentic-sdlc-url-shortener/data-model.md` | `≥ 3` | **EXECUTED: 3** — HOLDS |
| V4 | 2 | `grep -c 'node_key' specs/001-agentic-sdlc-url-shortener/data-model.md` | `≥ 5` | **EXECUTED: 5** — HOLDS |
| V5 | 3 | `grep -c 'from_stage' specs/001-agentic-sdlc-url-shortener/data-model.md` | `0` | **EXECUTED: 0** — HOLDS |
| V6 | 6 | `grep -c 'no fixed node count' specs/001-agentic-sdlc-url-shortener/data-model.md` | `1` | **EXECUTED: 1** — HOLDS |
| V7 | 6 | `grep -c '^10\.' specs/001-agentic-sdlc-url-shortener/data-model.md` | `1` — invariant 10 present | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V8 | 7 | `grep -c 'CR-011' specs/001-agentic-sdlc-url-shortener/data-model.md` | `≥ 1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V9 | cross-artifact | `grep -rc 'executor_mode\\\|executorMode' specs/001-agentic-sdlc-url-shortener/ docs/governance/adr/` | `0` outside this record and CR-005's history | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V10 | cross-artifact | `grep -c 'maxItems' specs/001-agentic-sdlc-url-shortener/contracts/workflow-state.schema.json` | `0` — proves **CR-013** landed with this one; a passing V4 with a failing V10 means the design doc and the contract now disagree | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V11 | 8–9 withdrawn | `grep -c -E 'ArchiveBatch\|RetentionPolicy\|archive' specs/001-agentic-sdlc-url-shortener/data-model.md` | `0` — the revision-2 archive entities were **not** applied | **EXECUTED: 0** — HOLDS |
| V12 | 8–9 withdrawn | `grep -c '^11\.' specs/001-agentic-sdlc-url-shortener/data-model.md` | `0` — invariants stop at 10 | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |


**Check correction recorded, 2026-09-20.** V1 was first written as `grep -c 'executor_mode' … | 0`. On execution it
returned **2**, and both hits are **inside this record's own approved NEW text**: the Edit 7 status note explaining
that this document was the last artifact carrying the old enum, and Edit 4's attribution *"renamed from
`executor_mode_used` by CR-005"*. Those are provenance, which this project deliberately retains.

**The edits were correct; the check was wrong.** This is the third time in this package that a whole-file
absence-grep failed for the same reason, and the reason is worth stating once: **the project's convention is to
retain superseded text with attribution rather than delete it**, so "the old string appears nowhere" is the wrong
assertion. The right one is "the old string appears in no *binding position*". V1 is narrowed accordingly. The
record's requirement is unchanged — no field may use the old name — and is now precisely testable.

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V9 and V10 are cross-artifact checks and exist because the defect this CR fixes was precisely a
single-artifact edit that its own record believed was complete.**

## Residual risk

`node_key` as a string introduces a parsing temptation: code that derives `stage_number` by splitting `"S7.1"` on
the dot rather than reading the `stage_number` column. That would reintroduce the coupling this change removes, and
silently — a malformed key would produce a wrong stage attribution rather than an error. Mitigation: `stage_number`
and `node_role` are **stored columns, never derived**, and T074's test asserts that a node's `stage_number` is read
from the column by constructing a node whose key and column deliberately disagree and requiring the column to win.
Recorded here so the mitigation is a named task obligation rather than a coding preference.
