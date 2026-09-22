# CR-048 — DS-A greenfield requirement tightened in response to two independent, real ambiguity findings

| Field | Value |
|---|---|
| **Change request** | CR-048 |
| **Title** | Revise `docs/evidence/ds-a/design.md` (T131) with a fully-specified DS-A requirement; keep the superseded run's evidence |
| **Raised by** | Two independent, real ambiguity-detection findings against T132's first live attempts (Gemini, then Claude) |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — option (a)**, 2026-09-21. Owner's own word: "go with a" |
| **Decision date** | 2026-09-21 |
| **Classification** | **MINOR** — revises a demonstration-scenario input, not approved specification text (`spec.md`), not production code, not the orchestration mechanism. `POL-CHG-001`'s own trigger (editing **approved specification text**) does not apply here; this CR is filed because the change is substantive and its reasoning is worth a durable record, not because a higher-tier gate requires it |
| **Artifacts changed** | `docs/evidence/ds-a/design.md` (revised in place, with a "Revision history" section rather than a second file — matching how this project treats a living design document, distinct from an immutable gate/CR record) |
| **Non-approved files changed** | None — this CR is itself the record of the revision |
| **Applied** | 2026-09-21 |

---

## Reason / verification performed

T132's first two live attempts at DS-A both found real, material ambiguity in the original requirement —
*"Expose the remaining time-to-expiry for a short link to its owning creator"* — from **two independent
models**:

- **Gemini** (`gemini-3.8-flash-high`), reproduced directly outside the pipeline before the quota
  exhaustion that suspended that attempt:
  `docs/evidence/ds-a/run-snapshot-ATTEMPT-1-BLOCKED-gemini-quota-exhausted.md`.
- **Claude** (`claude-sonnet-5`), live through the real `Conductor`: the run correctly suspended at S4's
  `UNRESOLVED_AMBIGUITY` gate: `docs/evidence/ds-a/pending-gate-s4-context.md`,
  `docs/evidence/ds-a/run-snapshot-ATTEMPT-2-STOPPED-AT-S4-ambiguity-gate.md`.

Both named the same six real gaps (unit/format, non-expiring-link behavior, expired-link behavior, delivery
medium, owning-creator identity, non-owner/anonymous behavior). Two independently-trained models agreeing is
a meaningfully stronger signal than either alone — this was reported to the owner as a genuine finding, not
decided unilaterally, and the owner's decision (option (a) of the two offered) is what this CR applies.

## Scope

**What changed**: the DS-A requirement's own wording, pre-answering every one of the six gaps —
`GET /v1/links/{code}/expiry` returning a fully-specified JSON body, with `null` for non-expiring links,
`0` for already-expired links, and an explicit two-tier refusal (`401` anonymous, `404` non-owner).

**One refinement made and disclosed, not applied silently**: the owner's own proposed wording used a single
`404` for both "anonymous" and "non-owning" callers. The revised design document corrects this against the
real, already-delivered system: `LinkController`'s existing analytics endpoint (T051) uses `401` for
anonymous (routing-level, via `CreatorAuthFilter`) and a separate, non-disclosing `404` only for an
authenticated non-owner — verified directly against `LinkController.java`'s own source before writing this
CR, not assumed. Matching the existing, delivered pattern exactly is what the design document's own
Criterion 2 (Consistent) requires; inventing a simpler single-refusal rule would have been the "trivially
easy" shortcut T131/T132's own Guard clauses warn against, just relocated into the fix rather than the
original input.

**What did not change**: the underlying feature concept (already verified buildable in T131's first pass);
`spec.md` (no approved specification text touched); any production code (this is a scenario-input revision,
not an implementation change — T132 has not yet reached S7).

**Kept, not deleted**: both superseded attempts' evidence. `docs/evidence/ds-a/design.md`'s own "Revision
history" section is the pointer a reader follows from the current requirement back to why it changed.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | Both prior ambiguity findings reviewed together, reported to the owner as a genuine, cross-model-corroborated finding, not decided unilaterally | owner's own decision recorded | **EXECUTED — HOLDS** |
| 2 | Revised wording checked against the real, delivered system's existing refusal pattern before being finalized (not assumed from the owner's own draft) | `LinkController.java` read directly; one refinement found and applied | **EXECUTED — HOLDS** |
| 3 | `docs/evidence/ds-a/design.md` revised, re-verifying all four well-formedness criteria against the new wording, each gap named against its resolution | present | **EXECUTED — HOLDS** |
| 4 | T132 re-run against the revised requirement | see the companion evidence for this run, committed alongside or after this CR | pending / see run evidence |

## Conditions attached to the approval

1. **Keep the superseded run's evidence, labelled, not deleted** — the owner's own explicit instruction.
   Honoured; both prior attempts' files remain committed, named `ATTEMPT-1`/`ATTEMPT-2`, and
   `design.md`'s own "Revision history" section points to both.
2. **The revision must genuinely resolve the found ambiguities, not merely reword around them** — honoured;
   the table in `design.md`'s Criterion 1 maps each of the six found gaps to its specific resolution in the
   new wording, checkable line by line.

## Cross-record orphan check

- `docs/REVIEWER-GUIDE.md` and `quickstart.md` do not name DS-A's specific requirement text anywhere — no
  correction needed there.
- No other applied record cites the original DS-A wording as a premise for something else that would now be
  falsified — checked directly: only `design.md` itself named it, and it is the artifact this CR revises.
- T131's own task-plan entry (`tasks.md`) stays `[x]` — the WORK the task describes (verify an input against
  the four criteria before the run) was done then and is done again now against the revised input; the task
  is not re-opened, its artifact is revised, matching how other design documents in this project are
  corrected forward rather than re-tasked.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If T132's re-run against the revised wording still finds material ambiguity, that would be a genuinely new finding requiring its own report to the owner — not assumed resolved by this CR alone | T132's own re-run, immediately following this CR |
