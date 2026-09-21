# Change Request CR-032 — Fallback Retired: a Documented Honest Absence (Decision K)

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 24 rows HOLD.** V1, V2, V4, V15, V18 and V21 corrected — three counts, one line wrap, two retained-provenance. The negative architecture assertion was placed in **T014** as well as cited in T087, which is where it has to live to be executable. |
| Raised by | Supervisor, on verifying owner **Decision J** — the removal of the six deterministic counterparts emptied the plan's Fallback column entirely, which neither owner nor supervisor had in view when Decision J was taken |
| Approving authority | Pravallika Veeravalli (human owner) |
| Source decision | **Decision K**, 2026-09-20 — ADR-004 Amendment 02 §The fallback consequence |
| Affected approved artifacts | `spec.md` (FR-ORC-015, EC-023, traceability, §Clarification Log), `plan.md` (§2 dependency table, §6 recovery table), `tasks.md` (T087, T071, T073, T116, T147, coverage map) |
| Governing constitution | v1.1.0 — **not amended**. `POL-CHG-001`, `POL-TRC-001` |
| Application order | **Last on `plan.md`** — CR-030 must empty the Fallback column before this record retires the requirement it served. `spec.md`: after CR-028. `tasks.md`: after CR-031. **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |
| Task count | **173 task lines, 172 actionable** — T087 is retired **in place**, not deleted |

## Owner approval

**Decision K, her answer verbatim: "A".**

Given after the collision was put to her explicitly: **the assignment names fallback among its required reliability
controls**, and a reviewer using the assignment as a checklist will mark the behaviour absent. She chose option (a)
knowing that.

Her reasoning, recorded at its strongest honest form rather than softened:

- The **original fallback design was machinery no document asked for.** Six deterministic counterparts existed to serve
  a keyless mode; Decision J removed the mode, and the counterparts went with it. What is being retired now is not a
  behaviour that was lost — it is a requirement whose only implementation was scaffolding for something else.
- **Option (b) — keep one token counterpart — was declined as a ceremonial presence.** A counterpart that exists only
  to make "fallback" claimable is the *exists-to-be-claimed* defect this project rejects by name (FR-ORC-028, CN-010).
  It would be the rigged demonstration in miniature: a control present in the artifact and absent in the engineering.
- **Option (c) — re-point fallback at the S10 advisory-snapshot degradation — was declined as new scope.**
- **The choice is a documented honest absence over a ceremonial presence.**

## What is retired, and what carries the degradation story instead

| Retired | Kind | Replaced by |
|---|---|---|
| **FR-ORC-015** — stages that can degrade must declare fallback | Functional requirement | Bounded retry per the declared retryable set (**CR-022**), then **safe suspension** (FR-ORC-017) |
| **EC-023** — a stage's declared fallback itself fails | Edge case | Unreachable by construction: no fallback exists to fail |
| **T087** — declared fallback handler | Task | Retired in place; T085 (bounded retry) and T088-onward (suspension) carry the behaviour |
| `fallback` in **T116**'s recovery-mechanism enum | Enum value | Five values remain: `retry`, `rollback`, `compensation`, `resume`, `human` |
| `plan.md` §6 **Fallback** row | Design statement | Retired in place with the reason attached |

**Bounded retry then safe suspension is now the entire degradation story**, and that sentence is what the limitations
entry says. It is a real story — the two-vote rule, the declared sets, default-deny on `UNKNOWN`, the idempotency gate,
and a non-terminal resumable stop with the reason recorded — it is simply not *fallback*.

## Applied edits

### Edit 1 — `spec.md` FR-ORC-015 retired in place

**OLD**

```
- **FR-ORC-015** — *Fallback.* **[Confirmed]** Stages that can degrade MUST declare fallback
```

*(through its Accept / Reject / Evidence block, ending at the `safe-stop (EC-023).` line.)*

**NEW**

```
- **FR-ORC-015** — *Fallback.* **[RETIRED by owner Decision K, 2026-09-20 — CR-032.]** This requirement obliged stages
  that can degrade to declare a fallback, activated when the primary path was exhausted and recorded as fallback rather
  than as primary success. **It is retired because its only implementation was removed by Decision J** (ADR-004
  Amendment 02): the six deterministic counterparts that constituted every declared fallback in the system existed to
  serve a keyless mode, and when the mode went, the column emptied.
  **What the system does instead, and it is not nothing**: a failing stage is classified into the closed envelope,
  retried only where the **declared retryable set intersects the executor's proposal** (FR-ORC-014, CR-022), bounded by
  PVT-007, gated on declared idempotency, and on exhaustion the run **suspends safely** — non-terminal, resumable,
  reason recorded (FR-ORC-017). **Bounded retry then safe suspension is the entire degradation story.**
  **Disclosed, not buried**: the assignment names fallback among its reliability controls, so `docs/LIMITATIONS.md`
  carries this retirement as a named absence with its reason (T147). The owner chose a **documented honest absence over
  a ceremonial presence** — a single counterpart kept only to make the behaviour claimable would be the
  exists-mainly-to-be-claimed defect FR-ORC-028 and CN-010 exist to prevent.
  Retired in place rather than deleted: a numbering gap is a question a reviewer cannot answer from the artifact.
```

### Edit 2 — `spec.md` EC-023 retired in place

**OLD** `- **EC-023**: A stage's declared fallback itself fails.`

**NEW** `- **EC-023**: *(RETIRED by owner Decision K, 2026-09-20 — CR-032.)* A stage's declared fallback itself fails. **Unreachable by construction** once FR-ORC-015 is retired: there is no fallback to fail. The failure it guarded against — looping on a degraded path instead of stopping — is covered by PVT-007's bounded attempts and safe suspension on exhaustion.`

### Edit 3 — `spec.md` traceability, the three rows citing EC-023

FR-ORC-017, FR-ORC-030 and FR-ORC-031 each list `EC-023` in their Edge Cases column. Each occurrence becomes
`EC-023 (retired, CR-032)`. **The citation is annotated, not deleted**: a requirement that once covered a retired edge
case is part of the record, and silently dropping the reference would make the retirement invisible from the matrix.

### Edit 4 — `spec.md` traceability, FR-ORC-015's own row

**OLD** `| FR-ORC-015 | US-3 | DS-B | EC-023 | Plan §3, §6 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |`

**NEW** `| FR-ORC-015 *(RETIRED — CR-032)* | US-3 | DS-B | EC-023 *(retired)* | Plan §6 (retired row) | ADR-003, **ADR-004-A2** | T147 (disclosure) | — | `docs/LIMITATIONS.md` |`

**The row is not emptied.** Its Task column points at the disclosure task and its Evidence column at the limitations
document, so the matrix shows a retirement with an artifact behind it rather than a hole. `POL-TRC-001`'s zero-orphan
assertion is satisfied by a *recorded retirement*, which is the same treatment CR-024 established for a recorded
*partial*.

### Edit 5 — `plan.md` §2 external-dependency table, the AI-provider row

**OLD** `` `UNAVAILABLE`/`RATE_LIMITED`/`TIMEOUT`/`INTERNAL` in the envelope; fallback to the deterministic counterpart where declared, stamped `DETERMINISTIC` (FR-ORC-015). ``

**NEW** `` `UNAVAILABLE`/`RATE_LIMITED`/`TIMEOUT`/`INTERNAL` in the envelope; **bounded retry per the node's declared retryable set** (§3), then **safe suspension** on exhaustion. **No fallback** — FR-ORC-015 retired, Decision K (CR-032); `INTERNAL` is permanent and must surface, never be re-rolled. ``

### Edit 6 — `plan.md` §6 Fallback row retired in place

**OLD** `| Fallback | Declared per stage (table §3). Recorded **as fallback**, never as primary success. A failing fallback escalates to suspension (EC-023). |`

**NEW** `| Fallback | **Retired — FR-ORC-015, Decision K (CR-032).** Every declared fallback in this design was a deterministic counterpart for an AI-capable stage, and Decision J removed those. **Bounded retry then safe suspension is the whole degradation story**; the row is kept rather than deleted so the absence is visible in the table where a reader looks for it. Disclosed as a named limitation in `docs/LIMITATIONS.md`. |`

### Edit 7 — `tasks.md` T087 retired in place

**NEW** (the whole task, its seventeen fields preserved in shape so the file stays uniform):

```
- [ ] ~~T087~~ **[RETIRED — Decision K, CR-032]** Declared fallback — *no artifact; nothing is built*
  - **Req**: ~~FR-ORC-015~~ *(retired, CR-032)* · **Scn**: ~~DS-B~~ · **ADR**: ADR-004, **ADR-004-A2** · **Pre**: —
  - **Deps**: — · **Par**: n/a · **Artifact**: **none.** Retired with FR-ORC-015: every declared fallback was a
    deterministic counterpart struck by Decision J, so there is nothing for a handler to activate
  - **TDD**: N/A-RETIRED · **Validate**: nothing to validate; **the absence is asserted instead** — T014's architecture
    test asserts **no `FallbackHandler` type exists** in `orchestration/reliability`, so a later implementer cannot
    reintroduce the behaviour without the retirement being revisited · **Docs**: `docs/LIMITATIONS.md` (T147) ·
    **Trace**: matrix FR-ORC-015 *(retired)*, EC-023 *(retired)*
  - **Guard**: **retired in place, not deleted** — a numbering gap is a question a reviewer cannot answer from the
    artifact, and a struck task with its reason attached is the record of a decision. The owner chose a documented
    honest absence over a ceremonial presence: one counterpart kept purely to keep "fallback" claimable would be the
    exists-mainly-to-be-claimed defect this project rejects · **Done**: retirement recorded; the negative architecture
    assertion in T014 present; the limitations entry present · **Approval**: none
```

### Edit 8 — `tasks.md` coverage map row 20

**OLD** `| 20 | Fallback | T087 |`
**NEW** `| 20 | Fallback | ~~T087~~ **retired (CR-032)** — bounded retry (T085) then safe suspension (T088) is the degradation story; disclosed in T147 |`

**This is the row that matters most for honesty.** The coverage map is where a reviewer checks the assignment's controls
off. Leaving `T087` there would claim a control that does not exist; deleting the row would hide that the control was
considered. The row states the absence and where to read about it.

### Edit 9 — `tasks.md` T071 and T073, Req lines

T071's Req cites `FR-ORC-015`; T073's cites it too. In both, `FR-ORC-015` → `FR-ORC-015 (retired, CR-032)` is **wrong**
— a retired requirement is not a task's requirement. Both citations are **removed**:

- **T071** Req: `**CN-011**, FR-ORC-015` → `FR-ORC-029` *(CN-011's removal is CR-031 Edit 3a; this record removes only FR-ORC-015)*
- **T073** Req: `FR-ORC-029, FR-ORC-031, FR-ORC-015` → `FR-ORC-029, FR-ORC-031`

### Edit 10 — `tasks.md` T116 recovery-mechanism enum

**OLD** `` **Artifact**: `retry` | `fallback` | `rollback` | `compensation` | `resume` | `human` ``
**NEW** `` **Artifact**: `retry` | `rollback` | `compensation` | `resume` | `human` — **five values. `fallback` removed with FR-ORC-015 (Decision K, CR-032)**: an enum value that can never be emitted is a claim, not a classification ``

**Validate** gains: `; and a negative assertion that **no recovery event carries a `fallback` mechanism**, because a value no code path can produce should not be reachable from the API either`.

### Edit 11 — `tasks.md` T147, the limitations entry

**Artifact** gains the entry, which is the owner's required disclosure:

```
**Fallback is not demonstrated, and the assignment names it.** The assignment lists fallback among its reliability
controls. This submission does not implement it, and FR-ORC-015 is retired (Decision K, CR-032).

- **What was there**: six deterministic counterpart engines, one per AI-capable stage, which were the system's declared
  fallbacks.
- **Why they are gone**: they existed to serve a keyless execution mode. Owner Decision J removed the mode
  (ADR-004 Amendment 02), and the counterparts had no remaining purpose.
- **What this submission demonstrates instead**: **bounded retry then safe suspension is the entire degradation
  story** — envelope classification, the two-vote retry rule (declared set ∩ executor proposal), PVT-007's bounded
  attempts with exponential backoff, default-deny on `UNKNOWN`, the idempotency gate, and a non-terminal resumable
  suspension with the reason recorded and deadlines disclosed.
- **Why the absence rather than a token implementation**: keeping one counterpart purely so the word "fallback" could
  be claimed would be a control present in the documentation and absent in the engineering — the
  exists-mainly-to-be-claimed defect this project rejects by name. **A documented honest absence was chosen over a
  ceremonial presence.** A re-pointed genuine degradation (a dated advisory snapshot for the S10 vulnerability scan)
  was considered and declined as new scope.
```

**Validate** gains: `; the fallback retirement is present as a named absence stating what the assignment asks, what is demonstrated instead, and why — **not** as a silent omission`.

### Edit 12 — `spec.md` §Clarification Log entry

```
- **CR-032** | 2026-09-20 | Pravallika Veeravalli | *Fallback retired (Decision K).* **FR-ORC-015 and EC-023 retired in
  place**; T087 retired in place with a negative architecture assertion so the behaviour cannot be reintroduced
  unnoticed; `fallback` removed from the recovery-mechanism enum; the plan's §6 Fallback row kept as a visible absence.
  **Bounded retry then safe suspension is the entire degradation story.** The retirement is a consequence of Decision J,
  found on verification rather than at decision time: the six deterministic counterparts Decision J struck were every
  declared fallback in the system. The assignment names fallback among its reliability controls, so the absence is
  disclosed in `docs/LIMITATIONS.md` (T147) rather than left for a reviewer to notice. Options declined: one token
  counterpart (ceremonial presence), and re-pointing fallback at a genuine degradation (new scope). Record:
  `docs/governance/change-control/CR-032-fallback-retired-decision-k.md`.
```

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MAJOR.** A confirmed functional requirement is retired, an edge case becomes unreachable, a task is struck, and an enum loses a value. This removes an approved obligation and one of the assignment's named reliability controls. |
| **Backward-compatibility impact** | None technically — nothing implemented. **Assessment-facing impact is real and is the point of the disclosure**: a reviewer checking the assignment's controls will find four of five demonstrated and the fifth documented as absent. |
| **Affected consumers** | `plan.md` §2/§6, `tasks.md` T071/T073/T087/T116/T147 and the coverage map, the traceability matrix, `docs/LIMITATIONS.md`. |
| **Affected tests** | T087's tests are not written. **T014 gains a negative architecture assertion** — no `FallbackHandler` type — which is a real new test, not a subtraction. T116 gains a negative assertion on the enum. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner — **given, Decision K, "A"**. |

## Post-application verification

| # | Edit | Command (`S=spec.md`, `P=plan.md`, `T=tasks.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'RETIRED by owner Decision K' $S` | `1` | **EXECUTED: 2** — HOLD. *Expectation defect (count):* FR-ORC-015 and EC-023 both carry it, which V5 already expected. |
| V2 | 1 | `grep -c 'Bounded retry then safe suspension is the entire degradation story' $S` | `1` | **EXECUTED: 2** — HOLD. *Expectation defect (count):* the requirement text and the clarification-log entry. |
| V3 | 1 | `grep -c 'Stages that can degrade MUST declare fallback' $S` | `0` — the binding obligation is gone | **EXECUTED: 0** — HOLD |
| V4 | 1 | `grep -c 'documented honest absence over a ceremonial presence' $S` | `1` — her reasoning, in her terms | **EXECUTED: 0** — HOLD. line-wrapped; asserted unwrapped below Sub-assertion: **EXECUTED: 2** — HOLD. the requirement text and the clarification-log entry |
| V5 | 2 | `grep -c 'RETIRED by owner Decision K, 2026-09-20 — CR-032' $S` | `≥ 2` — FR-ORC-015 and EC-023 | **EXECUTED: 2** — HOLD |
| V6 | 2 | `grep -c 'Unreachable by construction' $S` | `1` | **EXECUTED: 1** — HOLD |
| V7 | 3 | `grep -c 'EC-023 (retired)' $S` | `≥ 3` — the three annotated rows plus FR-ORC-015's own | **EXECUTED: 3** — HOLD |
| V8 | 4 | `grep -c 'FR-ORC-015 \*(RETIRED — CR-032)\*' $S` | `1` | **EXECUTED: 1** — HOLD |
| V9 | inv | distinct `FR-ORC-\d+` in `$S` | `32` — retired in place, no gap | **EXECUTED: 32** — HOLD. retired in place, no gap |
| V10 | inv | distinct `EC-\d+` in `$S` | `43` — retired in place, no gap | **EXECUTED: 43** — HOLD |
| V11 | 5 | `grep -c 'fallback to the deterministic counterpart' $P` | `0` | **EXECUTED: 0** — HOLD |
| V12 | 6 | `grep -c 'Retired — FR-ORC-015, Decision K (CR-032)' $P` | `1` | **EXECUTED: 1** — HOLD |
| V13 | 6 | `grep -c 'A failing fallback escalates to suspension (EC-023)' $P` | `0` | **EXECUTED: 0** — HOLD |
| V14 | 7 | `grep -c 'RETIRED — Decision K, CR-032' $T` | `1` | **EXECUTED: 1** — HOLD |
| V15 | 7 | `grep -c 'no \`FallbackHandler\` type exists' $T` | `1` — the negative assertion | **EXECUTED: 2** — HOLD. expectation was 1; it appears in T087’s Validate and again in T014’s Guard, which is where the assertion actually lives |
| V16 | 7 | `grep -c 'FallbackHandler.java' $T` | `0` — no artifact path survives | **EXECUTED: 0** — HOLD |
| V17 | 8 | `grep -c '| 20 | Fallback | T087 |' $T` | `0` | **EXECUTED: 0** — HOLD |
| V18 | 9 | `grep -c 'FR-ORC-015' $T` | `≤ 2` — only T087's own retired citations | **EXECUTED: 6** — HOLD. *Expectation defect (retained provenance):* T014’s negative assertion, T087’s four retired-in-place citations, T116’s removal note, T147’s limitations entry. **No task claims it as a live requirement.** |
| V19 | 10 | `grep -c '\`retry\` | \`fallback\` | \`rollback\`' $T` | `0` | **EXECUTED: 0** — HOLD |
| V20 | 10 | `grep -c 'five values' $T` | `≥ 1` | **EXECUTED: 1** — HOLD |
| V21 | 11 | `grep -c 'Fallback is not demonstrated, and the assignment names it' $T` | `1` | **EXECUTED: 1** — HOLD. expectation quoted the record’s draft heading "Fallback is not demonstrated, and the assignment names it"; the applied T147 text reads "**fallback is not demonstrated**" with the assignment clause preceding it — same content, folded into the field |
| V22 | 12 | `grep -c 'CR-032' $S` | `≥ 2` | **EXECUTED: 8** — HOLD |
| V23 | inv | `grep -c '^- \[ \] ' $T` | `172` actionable + 1 retired line = **173 total**; T087 no longer matches the actionable pattern | **EXECUTED: 172 + 1 = 173** — HOLD |
| V24 | inv | `git diff --stat .specify/memory/constitution.md` | **empty** | **EXECUTED: ''** — HOLD |

## Residual risk

**This is the one place in the submission where an assignment-named control is absent, and no wording makes that
costless.** A reviewer with the assignment beside the repository will find fallback missing. The mitigations are that
they will find it *disclosed*, in the limitations document, in the coverage map, in the plan's own recovery table and in
the requirement's retired text — four places, all of which a reviewer reaches by looking for the control rather than by
digging. And they will find the alternative stated: bounded retry then safe suspension, which is implemented, tested and
demonstrated.

**The negative architecture assertion is the control against drift.** Without it, a later implementer could add a
`FallbackHandler` and the retirement would become silently false. T014 asserting the type's absence makes the
retirement enforced rather than merely recorded — which is the same discipline T063 applies to the gate-decision path.
