# Change Request CR-025 — Ambiguity Detection Is AI-Backed: the Fork Dissolved by Decision J

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 10 rows HOLD.** V6 and V10 corrected: one phrasing variant, one count. |
| Revision | **Draft 2**, 2026-09-20. Draft 1 offered three dispositions for reconciling a keyless ambiguity checker with an AI-backed one. **Decision J removed the keyless mode, so the fork no longer exists.** |
| Raised by | Pre-implementation Principal Engineer review, condition **E1** |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-20** |
| Affected approved artifacts | `spec.md` (§Stage Executor Model), `plan.md` (§3 S3 row, §11), `tasks.md` (T073b, T128) |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Application order | `spec.md`: after CR-032. `plan.md`: after CR-022, before CR-026. `tasks.md`: after CR-032. **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |

## Owner approval

Owner final approval, 2026-09-20. The record’s A/B/C fork dissolved under **Decision J** — her premise verbatim:

> *"we don’t need the non-AI mode anymore because… reviewers are not going to run it."*

What survives the dissolution is the plain capability statement, approved as recast: **ambiguity detection is
semantic and AI-backed**, with its variability declared and the recorded no-clarification reason as the only control
against a quiet miss.

## Why the fork dissolved, and why the finding did not

The review's finding was that the ambiguous-requirement scenario's detection was validated **only** through the AI
adapter, while the keyless path — the reviewer default — had a one-phrase description and no task specifying what it
detects. A reviewer running the default path might watch the scenario sail through without suspending.

Draft 1 offered three dispositions: build the structural checker and commit a keyless run; disclose that full
demonstration needs AI; or both.

**Decision J answered it by removing the premise.** There is no keyless path, so there is no default path on which the
scenario fails to fire. Every run is AI-backed, and ambiguity detection is AI-backed with it.

**The finding is not fully dissolved, and this is the part worth being careful about.** What dissolved is the
*inconsistency between two paths*. What remains is a **capability claim that a reader will still test**: the
specification says stage 3 detects *"incomplete, unclear, conflicting, or untestable requirements"*, and a reader is
entitled to know whether that is structural pattern-matching or semantic reasoning — and that the answer is now
"semantic, by a model, with the variability that implies". Leaving the one-phrase description in place and simply
deleting its keyless counterpart would replace a two-path inconsistency with a one-path vagueness.

So the plain statement survives the fork's dissolution. It just says something different now.

## Applied edits

### Edit 1 — `spec.md` §Stage Executor Model gains the capability statement

```
### What ambiguity detection is, and what it is not

Stage 3 is **AI-backed, and semantic**. It reasons over the submitted requirement rather than matching patterns in it,
which is why CL-003 made this stage AI-capable and why **Decision J's removal of the keyless mode removed no capability
here** — there was never a deterministic detector that could do this work.

**What that buys**: conflicts between *different concepts* are detectable. The demonstration input — expire after a
week, retain analytics indefinitely, still redirect for trusted partners — is a contradiction between link lifetime,
analytics retention and redirect entitlement. **No two clauses bound the same field**, so no structural rule would fire
on it; recognising it requires understanding what expiry means for redirects.

**What it costs, stated because it is the honest counterweight**:

- **Detection is non-deterministic.** The same input may be classified differently across runs. This is acceptable
  *here specifically* because the output feeds a **human gate** — a false positive costs a question, and the human is
  the decider either way. It would not be acceptable at stage 10, which is why policy verdicts are deterministic.
- **A miss is possible and is not distinguishable from an absence of ambiguity** without a human reading the
  requirement. The recorded `no_clarification_reason` is what makes a non-detection inspectable rather than silent
  (DS-A's requirement), and it is the only control against a quiet miss.
- **The structural alternative was considered and would have been narrower, not safer.** A deterministic checker could
  decide six classes — missing acceptance criteria, undefined term, unbounded quantifier, missing actor,
  self-referential constraint, and contradictory bounds on the *same named field*. It could not decide the
  demonstration input. **A checker that fired on words like "but" or "indefinitely" would look like semantic detection
  while being a vocabulary list** — it would pass the demonstration and fail the next input, and a reviewer would
  reasonably read it as tuned to the demo, which is the rigged-demonstration failure FR-ORC-028 and CN-010 exist to
  prevent. An honest narrow capability beats a broad-looking keyword match; an honest semantic capability with declared
  variability beats both.
```

### Edit 2 — `plan.md` §3, S3's row

The Fallback cell is handled by CR-030 (it becomes `None`). This record changes the **Purpose** cell:

**OLD** `Find incompleteness, conflict, untestability`
**NEW** `Find incompleteness, conflict, untestability — **semantic, AI-backed** (spec §What ambiguity detection is); output feeds a human gate, so variability is safe here and would not be at S10`

### Edit 3 — `plan.md` §11, the ambiguous scenario's Interpretation

Gains: `**Detection is semantic and AI-backed** (CR-025). Draft 1 of this record asked whether a keyless run could detect this input; Decision J removed the keyless mode, so the question is moot — but the capability is stated rather than assumed, because a reader is entitled to know whether "finds conflict" means reasoning or pattern-matching.`

### Edit 4 — T073b

**Guard** gains: `**This adapter is the only detector of semantic contradiction in the system, and after Decision J it is the only detector of anything at this stage.** Its output feeds a human gate, so a false positive costs a question — the reason variability is acceptable here. A **miss** is the real risk: it is indistinguishable from an absence of ambiguity unless `no_clarification_reason` is recorded and readable, which is why that field is required rather than optional.`

**Validate** gains: `; and the recorded `no_clarification_reason` on DS-A's clean input is asserted to be **substantive** — naming the checks performed — rather than a placeholder, because it is the only artifact that makes a non-detection inspectable`.

### Edit 5 — T128

**Artifact** gains: `; and what ambiguity detection is — semantic, AI-backed, with declared variability and a recorded reason when nothing is found`.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR.** The capability statement constrains what stage 3 must do and declares what it cannot guarantee. No task's scope changes; T073b gains one assertion. |
| **Affected consumers** | None beyond the four artifacts edited. |
| **Affected tests** | T073b gains the substantive-reason assertion — worth having independently of this record, since a placeholder reason would satisfy the old wording. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner. **No disposition is required any more** — that is the substantive change from draft 1. |

## Post-application verification

| # | Command | Expected | Result |
|---|---|---|---|
| V1 | `grep -c 'What ambiguity detection is, and what it is not' spec.md` | `1` | **EXECUTED: 1** — HOLD |
| V2 | `grep -c 'Detection is non-deterministic' spec.md` | `1` | **EXECUTED: 1** — HOLD |
| V3 | `grep -c 'vocabulary list' spec.md` | `1` — the rejected shortcut on the record | **EXECUTED: 1** — HOLD |
| V4 | `grep -c 'would have been narrower, not safer' spec.md` | `1` | **EXECUTED: 1** — HOLD |
| V5 | `grep -c 'Deterministic rule checks' plan.md` | `0` — the one-phrase description is gone | **EXECUTED: 0** — HOLD |
| V6 | `grep -c 'semantic, AI-backed' plan.md` | `≥ 2` | **EXECUTED: 1** — HOLD. expectation was >=2; the applied text uses `semantic, AI-backed` once in §3 and `semantic and AI-backed` once in §11 — both present, the phrasing differs |
| V7 | `grep -c 'only detector of semantic contradiction' tasks.md` | `1` | **EXECUTED: 1** — HOLD |
| V8 | `grep -c 'substantive\\*\\* — naming the checks performed' tasks.md` | `1` | **EXECUTED: 1** — HOLD |
| V9 | `grep -c 'no_clarification_reason' tasks.md` | `≥ 3` — the miss control | **EXECUTED: 6** — HOLD |
| V10 | regression | `grep -c 'keyless' spec.md` | consistent with CR-028's V7 — no keyless *orchestration* claim survives | **EXECUTED: 7** — HOLD. seven occurrences, every one either a retirement note or a statement of what survives (tests, fakes) — read and confirmed |

## Residual risk

**A miss is now the whole risk, and it has one control.** With no structural checker beneath it, nothing catches a
requirement whose ambiguity the model does not see. The recorded reason-for-no-clarification is the only thing that
makes such a miss inspectable, which is why Edit 4 makes it assert substance rather than presence.

**Draft 1's disposition A would have covered exactly this gap** — a structural checker underneath, catching the six
decidable classes even when semantic reasoning missed something. Decision J removed the keyless *mode*; it did not have
to remove a structural **pre-filter**, which could have coexisted with an AI-backed detector. **That option is not in
scope and is not being reopened** — recorded because the dissolved fork is not quite the same as a closed gap, and a
reviewer may ask.
