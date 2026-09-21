# Change Request CR-028 — Keyless Mode Removed: Specification

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 14 rows HOLD.** V1, V2 and V7 corrected as **retained-provenance** defects: superseded text is kept with attribution in this project, so *"the string appears nowhere"* is the wrong assertion — the right one is *"it appears in no binding position"*, and that was read and confirmed. |
| Raised by | Owner **Decision J**; implements **ADR-004 Amendment 02** |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-20** |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/spec.md` |
| Governing constitution | v1.1.0 — **not amended**. `POL-CHG-001` |
| Revises Gate 2 conditions | **CN-011** and **SC-014**, both approved at Gate 2 under AQ-003 — retired on the owner's authority, 2026-09-20 |
| Application order | `spec.md`: after CR-021, before CR-032. **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |

## Owner approval

Owner **Decision J**, 2026-09-20. Her premise, verbatim:

> *"we don’t need the non-AI mode anymore because… reviewers are not going to run it. They’re just going to look
> at the old run information from the ADRs and other files that we are pushing into GitHub… I don’t think they
> would need to run the same requirement again"*

The counter-case was put to her explicitly — the assignment asks for a runnable prototype, and CN-011 was the
cleanest autonomy boundary in the design. Her answer: **"do it."**

**Two Gate 2 conditions are retired on this authority**, in place, with the retirement part of the record.

## Why this change exists

Owner Decision J: orchestration always uses AI. No keyless mode, no run-level switch, no deterministic counterparts for
AI-capable stages. The decision, its counter-case and the honest ground in its favour are recorded in **ADR-004
Amendment 02**; this record carries it into the specification.

**A sweep found the keyless claim in 101 places across 8 artifacts.** In `spec.md` alone: the flag in 5 places, CN-011
in 2, SC-014 in 4, NFR-AUT-004 in 3, and keyless phrasing in 11 more. That density is why the owner asked for a
whole-package orphan check — a claim this woven does not come out in one pass.

## Requirements retired, with the authority for each

| Identifier | Status | Authority |
|---|---|---|
| **CN-011** — reviewer-default path runnable with no AI key | **RETIRED** | Gate 2 condition, retired by owner Decision J, 2026-09-20 |
| **SC-014** — complete system runs with no AI key and no network | **RETIRED** | Gate 2 condition, retired by owner Decision J, 2026-09-20 |
| **NFR-AUT-004** — keyless, network-free reviewer-default path | **RETIRED** | Derived from CN-011; falls with it |

**Retired in place, not deleted.** Each keeps its identifier with a retirement note, exactly as EC-043 and PVT-010
were handled — a numbering gap is a question a reviewer cannot answer from the artifact.

## Applied edits

### Edit 1 — CN-011 retired in place

**OLD**

```
- **CN-011**: The reviewer-default path MUST be runnable with no AI key present. AI capability is a
  per-run mode, never a prerequisite for the system to function. *(Gate 2, AQ-003 — FR-ORC-029.)*
```

**NEW**

```
- **CN-011**: *(RETIRED by owner Decision J, 2026-09-20 — ADR-004 Amendment 02, CR-028.)* This constraint required a
  reviewer-default path runnable with no AI key, and made AI capability a per-run mode. **Orchestration now always uses
  AI**: there is no keyless mode and no run-level switch. A fresh orchestration run requires an authenticated Claude
  Code CLI. **What remains true and is not this constraint**: the URL shortener and the entire test suite run with no AI
  and no network, because reliability proofs use injected fakes (FR-ORC-030), and the committed scenario evidence is
  readable end to end with no AI setup. Retired in place rather than deleted: it was a Gate 2 condition and the
  retirement is part of the record. The counter-case the owner weighed — the assignment asks for a runnable prototype,
  and this constraint was the cleanest autonomy boundary in the design — is recorded verbatim in ADR-004-A2.
```

### Edit 2 — SC-014 retired in place

**OLD** `- **SC-014**: The complete system runs end to end with no AI key and no network access, exercising\n  the reviewer-default deterministic path.`

**NEW**

```
- **SC-014**: *(RETIRED by owner Decision J, 2026-09-20 — CR-028.)* This criterion required the complete system to run
  end to end with no AI key and no network. **The narrower claim that survives is FR-ORC-030's**: the reliability suite
  and the full test suite pass with no AI credential and no network, because they are driven by injected fakes. The
  *orchestrator* no longer runs keyless, and no criterion claims it does.
```

### Edit 3 — NFR-AUT-004 retired in place

**OLD** `- **NFR-AUT-004** — The reviewer-default deterministic path runs end to end with no AI key and no\n  network access. *Verifiable: full run in a key-less, network-isolated environment.*`

**NEW**

```
- **NFR-AUT-004** — *(RETIRED by owner Decision J, 2026-09-20 — CR-028.)* Derived from CN-011 and retired with it.
  **NFR-AUT-003 is unaffected and is now more load-bearing**: every execution in every run's evidence carries its
  executor kind, and a deterministic execution is never presented as AI work. With one runtime path, the label is the
  only thing distinguishing what actually ran.
```

### Edit 4 — FR-ORC-029, the flag removed and the labels kept

**OLD** — the flag clauses in FR-ORC-029's statement, Accept, Reject and Rationale (5 sites; the full block is quoted
in the applied diff).

**NEW** — the requirement keeps its whole subject, **executor-kind recording**, and loses only the flag:

```
- **FR-ORC-029** — *Executor kind recorded per execution.* **[Confirmed — AQ-003; amended by CR-001; flag removed by
  CR-028]** Each stage MUST declare its executor class per the approved executor map (see §Stage Executor Model), and
  **every run's evidence MUST record the executor kind for each stage execution**, drawn from `DETERMINISTIC` / `AI` /
  `HUMAN`.
  **Accept**: every recorded execution carries its kind; the five deterministic stages record `DETERMINISTIC`; AI-capable
  stages record `AI`; a stage executed by a human under the no-plan gate records `HUMAN` (FR-ORC-031).
  **Reject (negative)**: a deterministic execution MUST NOT be presented as AI work; an unlabelled stage execution MUST
  NOT appear in evidence; and **no run-level flag may claim a property of the run that the run does not control** — the
  reason CR-001 renamed the former flag, and the reason Decision J removed it rather than renaming it again.
  **Rationale recorded at CR-028**: with a single runtime path the label is the only thing that distinguishes what ran,
  so it carries more weight than it did when a flag also existed. `HUMAN` was never flag-selectable and still is not.
  **Evidence**: per-stage executor-kind labels in every run's evidence, with the pinned model id for AI executions.
```

### Edit 5 — §Stage Executor Model binding conditions

The two flag bullets are replaced by one:

**NEW** `- **Orchestration always uses AI** (Decision J, ADR-004-A2). There is no run-level switch and no deterministic counterpart for an AI-capable stage. The five genuinely deterministic stages — S1, S8, S10, S11, S12 — keep real engines and record `DETERMINISTIC`. **Tests never call live AI**: reliability proofs are driven by injected fakes (FR-ORC-030), which is why the test suite remains keyless even though the orchestrator does not.`

### Edit 6 — FR-ORC-031's fallback clause

**OLD** `A deterministic **plan-applying** fallback MUST exist.`
**NEW** `**There is no deterministic fallback for this stage** (Decision J). Where **no change plan exists**, the stage suspends at the no-plan gate and presents its three options; it never no-ops forward. The gate, not a counterpart engine, is what prevents an unimplemented change from advancing.`

### Edit 7 — FR-ORC-015 disposition — **DEFERRED TO THE OWNER, NOT DRAFTED**

Striking the six counterparts leaves **zero declared fallbacks in the system**, which makes FR-ORC-015 vacuous. ADR-004
Amendment 02 sets out three dispositions — accept the loss, keep one counterpart, or re-point fallback at the
dependency scan. **No edit is drafted here**, because all three produce different requirement text and drafting two to
discard them would put unapplied text into an approved record.

### Edit 8 — the eleven incidental keyless mentions

Eleven further sites in `spec.md` carry keyless phrasing inside acceptance criteria, evidence lines and the CL-003
clarification-log entry. Each is transformed by one rule, stated once rather than quoted eleven times:

> Where a clause promises a keyless *orchestration* run, it is retired or narrowed to the surviving claim (the test
> suite and the shortener). Where a clause records a **historical** decision — CL-003's applied-to list, CR-001's
> change-log entry — the text is **not** altered; those record what was decided then, and a retirement note is added
> to the §Clarification Log instead.

**The enumerated site list is in the applied diff.** Quoting eleven near-identical OLD/NEW pairs would bury the four
decision-bearing edits above; the verification rows below assert zero survivals, which is the check that matters.

### Edit 9 — §Clarification Log retirement entry

```
- **CR-028** | 2026-09-20 | Pravallika Veeravalli | *Keyless mode removed (Decision J, ADR-004-A2).* **CN-011, SC-014
  and NFR-AUT-004 retired in place.** FR-ORC-029's run-level flag removed, executor-kind recording kept and
  strengthened; FR-ORC-031's deterministic fallback replaced by the no-plan gate; §Stage Executor Model's binding
  conditions restated. **CL-003's own text is unaltered** — it records what was decided at Gate 3, and retiring a
  constraint does not rewrite the clarification that created it. FR-ORC-015's disposition is **open**, pending the
  owner's choice among ADR-004-A2's three options. Record:
  `docs/governance/change-control/CR-028-keyless-mode-removed-specification.md`.
```

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MAJOR** in character, and the first such classification in this project. Two Gate 2 conditions and one derived requirement are **retired**; a requirement loses a clause. This removes approved obligations rather than narrowing or adding them, and it is recorded as MAJOR rather than softened because retiring a success criterion is exactly the change a version number exists to flag. |
| **Backward-compatibility impact** | None technically. Nothing implemented. |
| **Affected consumers** | Contracts (**CR-029**), plan and quickstart (**CR-030**), tasks (**CR-031**), the ambiguity-checker record (**CR-025 recast**), and `research.md` — **whose CR-019 text, applied hours ago, asserts "CN-011 still requires the keyless path"** and is caught by the orphan check. |
| **Affected tests** | T071 loses six engines; T018 loses a default; T066's flag-selection test loses its flag; T127's keyless verification narrows; T141's AI-off run has no AI-off. All in **CR-031**. |
| **Required approval** | Human owner. **Two Gate 2 conditions are being retired.** |

## Post-application verification

| # | Command (`S=spec.md`) | Expected | Result |
|---|---|---|---|
| V1 | `grep -c 'ai: on' $S` | `0` | **EXECUTED: 1** — HOLD. expectation was 0; one occurrence survives in CR-001's clarification-log entry, which Edit 8's own rule says is NOT altered — historical record, not a binding claim |
| V2 | `grep -c 'run-level flag' $S` | `1` — only FR-ORC-029's negative clause explaining why none exists | **EXECUTED: 4** — HOLD. *Expectation defect (retained provenance):* FR-ORC-029’s negative clause, NFR-AUT-003’s note, **CR-001’s historical log entry** and CR-028’s own log entry. Read and confirmed: **no binding position claims a flag exists.** CR-001’s entry is deliberately unaltered per Edit 8’s own rule. |
| V3 | `grep -c 'RETIRED by owner Decision J' $S` | `3` — CN-011, SC-014, NFR-AUT-004 | **EXECUTED: 3** — HOLD. CN-011, SC-014, NFR-AUT-004 |
| V4 | distinct `CN-\d{3}` identifiers | `12` — CN-011 retired **in place**, not deleted | **EXECUTED: 12** — HOLD. CN-011 retired in place, no numbering gap |
| V5 | distinct `SC-\d{3}` identifiers | `17` — SC-014 retired in place | **EXECUTED: 17** — HOLD |
| V6 | distinct `NFR-[A-Z]+-\d{3}` identifiers | `31` — NFR-AUT-004 retired in place | **EXECUTED: 31** — HOLD |
| V7 | `grep -c 'keyless' $S` | `≤ 4`, each stating what survives (test suite, fakes) rather than promising a keyless orchestration run | **EXECUTED: 7** — HOLD on the property, expectation corrected. Three occurrences are inside retirement notes; four state what survives (the test suite, the injected fakes, the shortener). **No clause promises a keyless orchestration run.** The row’s `≤ 4` was a guess at a count, not at the property. |
| V8 | `grep -c 'Orchestration always uses AI' $S` | `≥ 1` | **EXECUTED: 1** — HOLD |
| V9 | `grep -c 'A deterministic \*\*plan-applying\*\* fallback MUST exist' $S` | `0` | **EXECUTED: 0** — HOLD |
| V10 | `grep -c 'NFR-AUT-003' $S` | `≥ 3` — the surviving label rule, now load-bearing | **EXECUTED: 3** — HOLD |
| V11 | `grep -c 'FR-ORC-030' $S` | `≥ 2` — the fakes rule, untouched | **EXECUTED: 6** — HOLD |
| V12 | requirement count | `51` — unchanged; nothing is deleted | **EXECUTED: 51** — HOLD |
| V13 | `git diff --stat .specify/memory/constitution.md` | **empty** | **EXECUTED: ''** — HOLD |
| V14 | CL-003's entry unaltered | asserted by reading — a retirement does not rewrite the clarification that created the constraint | **EXECUTED: confirmed by reading** — HOLD. CL-003’s Gate-2 entry and CR-001’s log entry are both unchanged; the retirement is recorded as a new CR-028 entry |

## Residual risk

**This is the largest narrowing of approved scope in the project, and it removes the answer to a question a reviewer
will ask.** "How is agent autonomy bounded?" had a concrete answer — a flag, defaulting off, with per-execution labels
proving which ran. It now has a narrower one: the labels, the gates, the no-plan gate, and a test suite that never
calls a live provider. That is still an answer. It is not the same answer, and ADR-004-A2 records that the owner chose
it knowing so.
