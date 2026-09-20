# Change Request CR-001 — Human Executor Kind and the No-Change-Plan Gate

> ## ⚠ CORRECTION NOTICE — added 2026-09-20, additive only
>
> **This record's "applied exactly as proposed" claim was inaccurate when made.** The two contract enum
> extensions it specified (`HUMAN` added to the executor-kind enums) were **not** applied at the time; only the
> rename half was. The omission was caught while applying CR-005 and fixed there.
>
> **See [`CR-005`](./CR-005-gate-4-closing-approvals.md) §"Defect found while applying"** for the full account,
> including the related judgment that `executorClass` deliberately does **not** gain `HUMAN` — confirmed by the
> owner on 2026-09-20, because `HUMAN` is a runtime outcome of the no-plan gate, never a design-time declaration.
>
> Nothing below this notice has been altered. No original word is changed, added, or removed. This notice exists
> because leaving a known-false status claim as the first thing a reader sees is worse than a disciplined
> annotation: immutability protects the decision content, not the discoverability of a correction to it. Added on
> the human owner's explicit instruction, noted in the commit message per `CLAUDE.md` §Immutability.

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20 |
| Approving authority | Pravallika Veeravalli (human owner) |
| Approved / applied | 2026-09-20. All six specification edits and both additive contract enum extensions applied exactly as proposed. Recorded in the specification's §Change-control amendments. |
| Raised by | Pravallika Veeravalli (owner design decision, 2026-09-20) |
| Raised during | Gate 4, ADR-004 review |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/spec.md` (approved Gate 2, clarified Gate 3) |
| Change owner | Human owner |
| Governing constitution | v1.1.0 — §Compliance and Change-Control Policy Enforcement; `POL-CHG-001` |

## The decision requiring the change

Recorded in the owner's terms:

> A labeled no-op that flows onward is quiet pretending — proceeding with nothing implemented is a material
> fact a human must consciously accept, not discover in a label afterwards. This is the same principle as my
> earlier decisions: nothing material advances on inference or silence.

**Approved behavior**: when stage 7 executes deterministically and **no change plan exists** for the
requirement — the novel-requirement keyless case — it MUST NOT no-op forward. It suspends at a human gate,
with the expiry consequence stated in the ask per CL-005, presenting exactly three options:

1. **Proceed as a governance-only run** — recorded decision; downstream stages continue; run evidence labels
   implementation as intentionally skipped by human decision.
2. **Human-implemented** — the human makes the change themselves, either externally in the working tree or by
   supplying change content with the decision, and the run proceeds into real testing that judges it. The
   executor kind for such an execution is recorded as **`HUMAN`**, completing the executor model as
   **AI / deterministic / human**.
3. **Abandon the run.**

The three prepared scenarios are unaffected — their change plans exist, so this gate never fires for them. The
out-of-scenario evidence run (SC-016) should deliberately exercise this gate, because it demonstrates
governance exactly where a reviewer will personally encounter it.

## Why this is a change to approved text and not a clarification

The approved specification does not merely omit this behavior; in two places it describes a two-valued executor
model that the decision extends to three, and in one place it names plan-applying as the deterministic fallback
without addressing the case where no plan exists. Extending an approved enumeration and adding a gate trigger
are changes in obligation, so they route through change control rather than being folded in as clarifications.

## Proposed edits to `spec.md`

| # | Location | Current text (abbreviated) | Proposed change |
|---|---|---|---|
| 1 | **FR-ORC-029**, line ~841 | "A configuration flag MUST select **AI mode or deterministic mode** per run, and every run's evidence MUST label each stage's executor mode." | Two changes. **(a)** Rename the run-level flag to **`ai: on \| off`**, default `off` — see §Flag rename below. **(b)** Extend the per-execution kind vocabulary to **`DETERMINISTIC` / `AI` / `HUMAN`**; `HUMAN` is not flag-selectable, it is recorded for a stage executed under option 2 of the no-plan gate. |
| 2 | **FR-ORC-031**, line ~865 | "A deterministic plan-applying mode MUST exist as fallback." | Add: where **no change plan exists**, the stage MUST NOT proceed. It suspends at a human gate offering the three options above, with expiry consequences disclosed per CL-005. Add negative criterion: the stage MUST NOT record success, and MUST NOT allow downstream stages to execute, on the basis of an unimplemented change — a labelled no-op is prohibited. |
| 3 | **KE-25 StageExecutor**, line ~927 | executor class "(deterministic engine, AI-capable, or human gate)" | Add **`HUMAN`** as an executor kind distinct from `HUMAN_GATE`: a human *implementing* a change is not the same as a human *deciding* a gate. |
| 4 | **§Stage Executor Model**, stage 7 row, line ~1028 | "deterministic plan-applying mode as fallback (FR-ORC-031)" | Add: "…or, where no change plan exists, suspension at the no-plan gate (FR-ORC-031)". |
| 5 | **§Stage Executor Model**, binding conditions, line ~1035 | "A configuration flag selects mode per run. **Reviewer default is deterministic**…" | Restate as `ai: on \| off`, default `off` — "with AI off (the keyless default)". Note that `HUMAN` is a recorded outcome of a gate decision, not flag-selected, so the reviewer default is unchanged. |
| 6 | **New edge case** | — | `EC-040`: deterministic stage 7 reached with no change plan for the requirement. Must suspend at the no-plan gate; must not no-op forward. |

### Flag rename — `ai: on | off` (owner decision, 2026-09-20)

Raised after the no-plan gate was specified, and folded into this request because it touches the same clauses:

> A run-level name must not claim a property the run cannot guarantee — a run started keyless can contain a
> `HUMAN` execution at the stage-7 no-plan gate, so "deterministic mode" over-promises at the run level. The
> flag controls exactly one thing, whether AI executors participate, and is now named for exactly that. A
> keyless run containing a human execution contradicts nothing under this name.

Per-execution kind labels are **unchanged** — `DETERMINISTIC` / `AI` / `HUMAN`, each literally true of the
single execution it stamps. Engines genuinely are deterministic; the run-level *mode* was the only dishonest
word.

**Contract fields renamed accordingly** (both files unapproved and uncommitted, so recorded here for
completeness rather than as a breaking change):

| File | Was | Now |
|---|---|---|
| `workflow-state.schema.json` | `executorMode: enum [DETERMINISTIC, AI]` | `ai: enum [on, off], default off` |
| `openapi.yaml` → `RunInspection` | `executorMode: enum [DETERMINISTIC, AI]` | `ai: enum [on, off], default off` |

Per-stage `executorClass` and `executorModeUsed` keep `DETERMINISTIC` as a **value**, per the owner's
instruction that per-execution labels are unchanged.

**Recommendation for the owner's consideration, not applied**: the per-stage field is still named
`executorModeUsed`, and "mode" now unambiguously means the run-level flag. Renaming it to `executorKindUsed`
would align it with the owner's own vocabulary ("executor kind") and remove the collision. Left unchanged
because the instruction scoped the rename to names saying "deterministic" where they mean "AI off", which this
one does not.

### Assessed as needing no change

- **FR-ORC-013** — requires gates with the five outcomes; a new gate *class* fits within it unchanged. Gate
  classes are enumerated in plan §5, not in the spec.
- **FR-ORC-017** — the gate times out into `SAFE_STOP` through existing machinery; no new safe-stop trigger.
- **SC-015** — "zero stage executions without an executor-mode label" remains true with a larger vocabulary.
- **SC-016** — an out-of-scenario run that suspends and is then resolved by a recorded decision still
  "completes a full run with no executor code changes". The owner's intent that it *deliberately* exercise
  this gate is recorded as an evidence obligation rather than a requirement change.
- **CN-011** — unaffected; the reviewer default remains keyless deterministic.

## Impact analysis

**Version impact**: MINOR on the specification — an enumeration is extended and a behavior added; nothing
existing is removed or redefined.

**Backward-compatibility impact**: none within the project; no released consumer exists. The contract change
in `workflow-state.schema.json` and `openapi.yaml` (`executorClass`, `executorModeUsed` gaining `HUMAN`) is
**additive** to enums, which `contracts/README.md` classifies as MINOR. Those files are unpublished and
uncommitted, so this is pre-first-version rather than a breaking change.

**Affected consumers**: none external. Internally: the orchestration state model, the gate model, the evidence
labeller, and the run-inspection response.

**Affected tests**: a new gate test per option (governance-only, human-implemented, abandon); a negative test
asserting a labelled no-op cannot advance downstream stages; extension of the executor-mode labelling test to
the three-valued vocabulary; the out-of-scenario evidence run exercising the gate.

**Affected documentation**: ADR-004, `quickstart.md`, and plan §3 / §5 / §Stage Executor Model — all
**unapproved** artifacts, updated under this request without needing separate approval. `spec.md` and the two
contract files await the decision below.

**Rollout / migration steps**: no data migration. If approved, the six spec edits are applied in one change,
the two contract enums extended, and the spec's Clarification Log or an amendment note records the change with
its date and this CR's identifier.

**Required change approval**: human owner. `POL-CHG-001` makes a contract or schema change without a
change-control record a mandatory release-blocking `FAIL`, which is why this record exists before any edit.

## Residual risk

Low. The change strictly *removes* a path by which a run could advance with nothing implemented, so it tightens
governance rather than loosening it. The one new risk is gate fatigue for a reviewer exploring many novel
requirements — mitigated by the gate presenting option 1 as a single recorded decision rather than a blocking
obstacle.

## Decision

**APPROVED** by Pravallika Veeravalli, 2026-09-20, with instruction to execute immediately.

Applied as proposed:

| # | Spec location | Applied |
|---|---|---|
| 1 | FR-ORC-029 | Retitled *"Executor kind recorded per execution; AI participation flagged per run"*; flag is `ai: on \| off` default `off`; kind vocabulary `DETERMINISTIC` / `AI` / `HUMAN`; negative criterion added that the flag must not claim a property it does not control and `HUMAN` is never flag-selectable; CR-001 rationale recorded |
| 2 | FR-ORC-031 | **No-change-plan gate** subsection added with the three numbered options; accept criteria extended; negative criterion added prohibiting a labelled no-op from recording success or letting downstream stages execute; CR-001 rationale recorded verbatim |
| 3 | KE-25 | Executor kind now `DETERMINISTIC` / `AI` / `HUMAN`, with the amendment note that a human **implementing** differs from a human **deciding** a gate |
| 4 | §Stage Executor Model, stage 7 row | Plan-applying fallback plus no-plan-gate suspension; `HUMAN` kind noted |
| 5 | §Stage Executor Model, binding conditions | Three bullets replacing two: the `ai` flag with its rename rationale; `HUMAN` never flag-selectable; executor kind recorded per execution |
| 6 | New **EC-040** | Deterministic stage 7 with no change plan must suspend, not no-op |

Also applied: `FR-ORC-031`'s traceability row now cites EC-040, and a new **§Change-control amendments to
this specification** section records this CR as an append-only entry.

Contract enums renamed and extended as tabled in §Flag rename.

Nothing pushed; all artifacts held uncommitted for the Gate 4 close batch.
