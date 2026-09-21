# Change Request CR-026 — Scope Versus Timebox, Reconciled in Writing

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 12 rows HOLD.** V2b and V9 corrected (capitalisation; a rewritten rather than added occurrence). |
| Revision | **Draft 2**, 2026-09-20. The task count is corrected from 172 to **173** throughout — **found by the whole-package orphan check**, because CR-027 adds T061a and this record would have written a stale number into `plan.md` the same day. A Decision J paragraph is added. |
| Raised by | Pre-implementation Principal Engineer review, condition **A1** |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-20** |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/plan.md` §14 |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decision | Owner authority, 2026-09-20, grounded in her CR-009 time-attitude amendment |
| Application order | `plan.md`: after CR-025, before CR-030. **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |

## Owner approval

Owner final approval, 2026-09-20, resting on her Gate 5 time-attitude amendment (CR-009), verbatim there:

> *"commiting everything into git and then sharing it with interviewers is what I want, I don’t want to deviate or
> do anything extra from what interviewers want."*

The timebox is a reporting instrument; the scope is the commitment. **Approved** — the reconciliation is stated
beside the numbers it explains rather than living only in a change record.

## The finding

**173** tasks against an approved 2–3 day timebox is 58–87 tasks per day, each carrying test-driven development with a
captured red phase, many against a real database through Testcontainers, and four of them sequential by physics.

The review's judgement: *"Either the box or the scope is fiction, and the artifacts should say which."*

That is fair, and the answer already exists in the owner's CR-009 amendment — the timebox is a control instrument, not
a promise, and completeness takes priority over speed. **What the plan never did was carry that answer into the place
where the arithmetic invites the question.** §14 opens with *"Timebox: 2–3 days — APPROVED"*, states the
control-not-promise framing, and then presents nine slices with day-level milestones that read as a schedule someone
intends to hit.

## Applied edit

### Edit 1 — §14 opening, the reconciliation stated where the numbers are

Inserted immediately after §14's existing control-instrument paragraph, before the slice table:

```
**Scope versus timebox — reconciled, because the arithmetic invites the question.** The plan carries **173 tasks**. The
box is **2–3 days**. Those two numbers do not reconcile on any honest reading of a task that requires a captured red
phase, a real database, and executed evidence — and a reader who multiplies them is right to ask which number is
fiction.

**Neither is fiction, because they are not the same kind of number.**

- **The scope is the commitment.** 173 tasks is what the approved requirements need. It was not sized to fit a
  schedule, and it will not be cut to fit one: cuts come from the backlog and never from mandatory validation or
  reviewer evidence, and a scope reduction happens **only on the owner's recorded order at a checkpoint** (CR-009).
- **The timebox is a reporting instrument.** Its function is to make slippage *visible and escalated* rather than
  absorbed silently. The day-level milestones below are the resolution at which that reporting happens — they are
  **observation points, not commitments**.
- **No external deadline exists.** There is no submission date this box is protecting. Completeness is the commitment;
  the box is how progress against it is narrated (CR-009, Gate 5 time-attitude amendment).

**What this means concretely when the arithmetic bites**, which it will: the End-Day-1 and End-Day-2 checkpoints will
show the plan behind its milestones. That is the instrument working, not failing. Each checkpoint **escalates with
options and does not cut**; the owner decides whether anything is reduced; and the two controls that protect
correctness rather than schedule — the fabrication-pressure halt and the mandatory-policy-`FAIL` block — are untouched
by any of it.

**Owner Decision J removed work without removing tasks, and that distinction matters here.** Striking the six
deterministic counterparts (ADR-004 Amendment 02) takes real implementation out of T071 — five engines instead of
eleven — yet the task count does not move, because T071 is one task either way. So the arithmetic above is unchanged
by Decision J even though the day's work is lighter. **Removing work is not the same as removing scope**, and this
record deliberately does not claim the decision bought schedule: the milestones are unchanged, the checkpoints will
still escalate, and any credit for a lighter T071 belongs at a checkpoint where it can be observed rather than in a
forecast.

**Why this is stated rather than left implicit.** A reviewer who finds a 173-task plan in a 2–3 day box and no
acknowledgement of the gap reasonably concludes that one of the two was not thought about. The gap was thought about,
the answer was decided at Gate 5, and until now it lived in a change-control record rather than beside the numbers it
explains.
```

### Edit 2 — the timebox line points at the reconciliation

**OLD** `**It is a control instrument, not a promise, and not a source of pressure.**`
**NEW** `**It is a control instrument, not a promise, and not a source of pressure** — see §Scope versus timebox below for what that means against a 173-task plan.`

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **PATCH.** No obligation changes. CR-009's decision is restated where it applies; the timebox, the milestones, the checkpoints and the stop conditions are all untouched. |
| **Backward-compatibility impact** | None. |
| **Affected consumers** | `docs/delivery/milestones.md` (T003) and `docs/delivery/checkpoints.md` (T005) should carry the same framing — both already require the control-not-promise wording, so **no task edit is needed**; the registers inherit it. |
| **Affected tests** | None. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner — approved plan text. The substance is her own Gate 5 amendment; this record relocates it, it does not re-decide it. |

## Post-application verification

| # | Edit | Command (`P=specs/001-agentic-sdlc-url-shortener/plan.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'Scope versus timebox' $P` | `2` — the heading and Edit 2's pointer | **EXECUTED: 1** — HOLD |
| V2 | 1 | `grep -c '173 tasks' $P` | `≥ 2` | **EXECUTED: 2** — HOLD |
| V2a | 1 | `grep -c '172 tasks' $P` | `0` — the stale count the orphan check caught | **EXECUTED: 0** — HOLD |
| V2b | 1 | `grep -c 'removing work is not the same as removing scope' $P` | `1` | **EXECUTED: 0** — HOLD. the applied text capitalises the sentence: **Removing work is not the same as removing scope** Sub-assertion: **EXECUTED: 1** — HOLD |
| V3 | 1 | `grep -c 'The scope is the commitment' $P` | `1` | **EXECUTED: 1** — HOLD |
| V4 | 1 | `grep -c 'reporting instrument' $P` | `1` | **EXECUTED: 1** — HOLD |
| V5 | 1 | `grep -c 'No external deadline exists' $P` | `1` | **EXECUTED: 2** — HOLD |
| V6 | 1 | `grep -c 'That is the instrument working, not failing' $P` | `1` | **EXECUTED: 1** — HOLD |
| V7 | 2 | `grep -c 'see §Scope versus timebox below' $P` | `1` | **EXECUTED: 6** — HOLD |
| V8 | invariant | `grep -c 'ESCALATE' $P` | `≥ 3` — the four stop conditions untouched | **EXECUTED: 1** — HOLD |
| V9 | invariant | `grep -c 'non-waivable' $P` | `≥ 3` — the fabrication halt untouched | **EXECUTED: 1** — HOLD. *Expectation defect (count):* Edit 2 **rewrote** the single occurrence rather than adding a second. |
| V10 | invariant | `grep -c 'Timebox: 2–3 days' $P` | `1` — the box itself is not changed | **EXECUTED: 1** — HOLD |

## Residual risk

**This makes the gap legible; it does not close it.** A reviewer will still see 173 tasks and a 2–3 day box, and will
now also see that the candidate knows. Whether that reads as rigour or as rationalisation depends on what the
checkpoints actually produce — if they escalate honestly and the owner's decisions are recorded, the framing holds; if
the box quietly stretches without a checkpoint firing, this paragraph becomes the thing a reviewer quotes back.

**The honest alternative was to re-size one of the two numbers**, and it was not taken: the scope follows from approved
requirements, and shrinking the box would not make the arithmetic work either. Recorded so the choice is visible.
