# Checklist Evaluation Results — Register

**Feature**: 001-agentic-sdlc-url-shortener · **Constitution**: v1.1.0 · **Created**: 2026-09-20
**Raised by**: `/speckit-analyze` finding **H10** · **Status of this register**: **PROPOSED** — not approved

## Why this file exists

The six CHK-numbered checklists in this directory carry **267 items, every box unchecked**. Their own stated
convention is:

> This command generated every item unchecked; the agent's evaluation of `[Now]` items is reported separately in
> conversation and does **not** tick boxes.

`/speckit-analyze` classified that as finding **H10**: it routes verification results **outside the repository**,
against Constitution §Assessment Scope — *"Approvals, decisions, and evidence count only when recorded in the
repository. Statements made only in conversation are not artifacts"* — and against Principle XI.10, *"Evidence is
available for independent reviewer verification."* A reviewer opening these files today reads them as *nothing
verified*.

This register is where checklist evaluation results live from now on. **It does not tick the checklist boxes**: box
state stays reviewer-owned, exactly as the checklists say. It records, per item, what was evaluated, by whom, when,
and with what outcome — so the box and the evidence for the box are separate artifacts, which is the right shape.

## Where it lives and how it stays current

| Question | Answer |
|---|---|
| **Location** | `specs/001-agentic-sdlc-url-shortener/checklists/EVALUATION-RESULTS.md` — beside the instruments it records, so it cannot drift out of sight of the person reading them |
| **Who writes it** | The agent performing an evaluation, in the same act as the evaluation. An evaluation that does not write here **has not happened**, by the same logic that makes a conversational gate decision non-citable |
| **When** | On every evaluation pass: at `/speckit-checklist`, at `/speckit-analyze`, and at release readiness for `[Pre-Release]` items |
| **Append-only** | A superseding pass appends a new dated section and references the prior one. Outcomes are never edited in place — the same immutability rule `CLAUDE.md` sets for gate records |
| **Enforcement** | `POL-TRC-001` and release-blocking condition 9 both bite on unverifiable evidence. T148's governance-evidence index must list this register, so an absent or stale register is detected rather than assumed |

## Scope of the instrument set

| File | Items | ID range | `[Now]` | `[Post-Tasks]` | `[Pre-Release]` |
|---|---|---|---|---|---|
| `requirements-review.md` | 43 | CHK001–CHK043 | 38 | 1 | 4 |
| `architecture.md` | 33 | CHK044–CHK076 | 29 | 0 | 4 |
| `orchestration.md` | 62 | CHK077–CHK138 | 56 | 0 | 6 |
| `governance.md` | 44 | CHK139–CHK182 | 42 | 0 | 2 |
| `testing.md` | 42 | CHK183–CHK224 | 36 | 0 | 6 |
| `submission.md` | 43 | CHK225–CHK267 | 30 | 0 | 13 |
| **Total** | **267** | CHK001–CHK267 | **231** | **1** | **35** |

`requirements.md` is the built-in specification-quality checklist. It uses its own numbering and **already records
its result in-file** — *"16 of 16 pass"*, validation iteration 3, 2026-09-19 — so it is outside this register's
scope and is the pattern this register generalises.

---

## Pass 1 — 2026-09-20, `[Now]` items, at `/speckit-checklist`

**Evaluator**: executing agent. **Reviewed by**: Pravallika Veeravalli (findings and remediations approved).
**Artifacts as at**: commit `62cf924` (checklists filed), remediated at `e301efe`.

### 1.1 — Findings raised, with dispositions *(materialized)*

Seven items failed and were remediated. Each disposition is substantiable from a repository artifact, which is why
these results can be recorded as evidence rather than recollection.

| Item | Criterion | Outcome | Disposition | Record |
|---|---|---|---|---|
| **CHK011** | Is "material" defined or exemplified wherever it gates behavior? | **FAIL → remediated** | §Defined terms added; the closing default makes uncertainty route to the human | `CR-007-define-material.md` |
| **CHK037** | Is the Design and ADR linkage recorded for requirements whose design decisions have been made? | **FAIL → remediated** | Two matrix columns added and populated for all 51 requirements from the ADRs' own Traceability sections | `CR-006-traceability-matrix-design-and-adr-columns.md` |
| **CHK070** | Does any ADR claim "applied as proposed" for a change that was only partly applied? | **FAIL → remediated** | CR-001's application claim corrected; the `HUMAN` enum extension had not in fact been applied | `CR-001` (amended), commit `e301efe` |
| **CHK181** | Does each applied change-control record's claim of application match the artifact? | **FAIL → remediated (incompletely — see 1.4)** | CR-001's claim corrected | commit `e301efe` |
| **CHK225** | Does every artifact state its own status, date, and authority? | **FAIL → remediated** | Status blocks added to `data-model.md`, `quickstart.md`, `contracts/README.md` | commit `e301efe` |
| **CHK239** | Is every governance record class filed in the location the operational guidance defines? | **FAIL → remediated** | `CLAUDE.md` gained the ADR location it had been missing | commit `e301efe` |
| **CHK244** | Does every evidence artifact carry a measured-versus-proposed label? | **FAIL → remediated** | ADR-014 and dependent text labelled | commit `e301efe` |

CHK244 is tagged `[Pre-Release]`; it was evaluated early because ADR-014's scale analysis introduced figures, and
the finding is recorded here rather than deferred.

### 1.2 — Items declared not evaluable at generation *(materialized; stated in the instruments themselves)*

| File | Items | Reason |
|---|---|---|
| `architecture.md` | CHK055, CHK074–CHK076 | Require implementation or measurement |
| `orchestration.md` | CHK085, CHK093, CHK103, CHK124, CHK137, CHK138 | Require executed runs |
| `governance.md` | CHK164, CHK182 | Require executed tests |
| `testing.md` | CHK199–CHK201, CHK208, CHK215, CHK224 | Require executed tests or runs |
| `requirements-review.md` | all `[Post-Tasks]` and `[Pre-Release]` items | No `tasks.md` and no implementation at generation time |

**CHK055** — *"Is the supporting measurement for the scalability analysis present, not only the analysis?"* — is
carried forward explicitly. It was still open at `/speckit-analyze` as finding **M8**, and **T145d** is the task
that closes it.

### 1.3 — The remaining `[Now]` items: **NOT MATERIALIZED**

**Approximately 224 `[Now]` items were evaluated in conversation and their outcomes are not recoverable as
evidence.** They are recorded here as **NOT MATERIALIZED**, not as passes.

This is deliberate and it is the only honest available entry. Constitution X forbids back-filling evidence, and
asserting 224 verdicts from recollection — after the conversation carrying them has been compacted — would be
precisely that. A pass that cannot be substantiated is not a pass; it is an unrecorded opinion.

**Consequence, stated plainly:**

- These items are **not citable** as verification evidence (Constitution §Assessment Scope).
- Release-blocking **condition 9** — *"any evidence presented is unverifiable"* — bites if any completion claim
  rests on them.
- **The seven findings in 1.1 are the evidence that the pass happened and was substantive.** What is missing is the
  per-item record of the passes, not the pass itself.

**Closure**: a re-evaluation pass over the `[Now]` items, writing per-item outcomes into §Pass 2 below. It is cheap
— the artifacts are the same ones `/speckit-analyze` just traversed — and it is the only thing that converts this
section from a disclosure into a result.

### 1.4 — Honest note: CHK181 was remediated while still failing

**CHK181 asks whether any change-control record claims an application that did not occur.** It was marked
remediated on 2026-09-20 after CR-001's false claim was corrected.

`/speckit-analyze`, later the same day, found **two more**:

- **CR-003** recorded *"DF-002 and AQ-004 marked resolved"*. AQ-004 was **not** marked resolved, in either of the
  two places stating it (analyze finding **H1**; fixed by **CR-010** Edits 5 and 7).
- **CR-008** recorded five edits; the fifth had not been applied (found earlier by verification, not assumption).

So the item guarding against false application claims was recorded as satisfied while a false application claim was
live in the approved specification. **CHK181's correct Pass-1 outcome is `FAIL`, remediated only in part.**

This is recorded rather than quietly corrected because it is the most useful single fact in this register: the
checklist found the right class of defect and the remediation fixed the instance in front of it without re-running
the check across the class. The control that follows is the **per-edit post-application verification table now
mandatory on every change request** in this remediation package (CR-010 through CR-016), which makes the claim
carry its own executed evidence.

---

## Pass 2 — re-evaluation of `[Now]` items — **NOT YET RUN**

Reserved. When run, this section records, per item: outcome (`PASS` / `FAIL` / `NOT-EVALUABLE`), the artifact and
location inspected, and — for a `FAIL` — the finding and its disposition record. Outcomes are appended, never
overwritten.

**Required before**: release readiness. **Blocks**: nothing directly, but every completion claim that would
otherwise rest on §1.3 depends on it, and T148's governance-evidence index must show it complete.

## Pass 3 — `[Pre-Release]` items — **NOT YET RUN**

Reserved for the 35 `[Pre-Release]` items plus the one `[Post-Tasks]` item, evaluable only against executed tests,
measurements and runs. Due at release readiness, alongside T146.

---

## What this register does not claim

- It does **not** tick any checklist box. Box state remains reviewer-owned.
- It does **not** assert outcomes for the items in §1.3. They are disclosed as unrecorded.
- It does **not** convert a conversational determination into evidence retroactively. Nothing here is back-filled;
  §1.1 and §1.2 are materialized because each maps to an artifact a reviewer can open, and §1.3 is materialized as
  **a disclosure of absence**, which is the only form of record Constitution X permits for it.
