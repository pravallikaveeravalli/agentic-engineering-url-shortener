# Day-Level Milestone Schedule

**Task**: T003 · **Requirement**: CN-007 · **Derived from**: `plan.md` §14 Delivery Sequence, exactly. No milestone
here is invented.

## The schedule is a control instrument, not a promise, and not a source of pressure

This is the framing the owner set at Gate 5 (CR-009) and it governs how every row below is read:

- **Completeness takes priority over speed.** A slice is finished when its exit condition in `scope-register.md` holds,
  not when its milestone passes.
- **No external submission deadline exists.** There is no date this schedule protects.
- **Slippage is observed at a checkpoint and escalated to the owner for direction** — never a silent extension, and
  **never a self-executing cut**. A checkpoint reports with options; the owner decides whether anything is reduced.
- **The milestones are observation points, not commitments.** Their function is to make a position visible early enough
  to ask about it.

**The arithmetic will bite, and that is the instrument working.** 173 tasks against a 2–3 day box do not reconcile on
any honest reading of a task requiring a captured red phase, a real database and executed evidence (plan §Scope versus
timebox, CR-026). The End Day 1 and End Day 2 checkpoints are **expected** to show the plan behind these milestones.
Being behind is the signal this schedule exists to produce — not a failure of it, and not a licence to cut.

---

## The nine milestones

| Milestone | Slice due | What "due" means here |
|---|---|---|
| **Day 1 AM** | **1 Engineering baseline** | Build, layout, Flyway, OpenAPI skeleton, contract-test harness, local CI-equivalent script |
| **Day 1 AM** | **2 Walking skeleton** | One endpoint + health + a real store round-trip, end to end |
| **Day 1 PM** | **3 Core URL behaviour** | TDD across validation, schemes, code generation, redirect, expiry, three idempotency semantics, analytics, provisioning, and **two of three rate-limit tiers** |
| **Day 2 AM** | **4 Orchestration state model** | Persisted DAG, transitions, prohibited-transition enforcement, inspection |
| **Day 2 midday** | **5 Approval governance** | Gates, five outcomes, deadline-in-ask, silence → suspension, retention and auto-abandon |
| **Day 2 PM** | **6 Reliability controls** | Envelope, two-vote retry, timeout gating, Compensation Register, resume across both restart classes |
| **Day 3 AM** | **7 Observability** | Audit events, correlation IDs, metrics, `failure_event` capture, MTTR calculation |
| **Day 3 midday** | **8 Three scenarios** | DS-A, DS-B, DS-C executed with evidence |
| **Day 3 PM** | **9 Release readiness** | Policy evaluation, nine blocking conditions with negative tests, final engineering summary |

**Two milestones fall on Day 1 AM.** That is the plan's own allocation — Slices 1 and 2 are both due there, because the
walking skeleton is what proves the baseline is real rather than a scaffold. It is recorded as-is rather than smoothed,
since smoothing it would be inventing a milestone.

## Milestone → slice → tasks

The slice → task mapping is in `tasks.md`'s phase structure; this table names the link so the three files agree.

| Milestone | Slice | Task phase in `tasks.md` |
|---|---|---|
| Day 1 AM | 1 | Phase 1 — Engineering baseline (T008–T020) |
| Day 1 AM | 2 | Phase 2 — Walking skeleton |
| Day 1 PM | 3 | Phase 3 — User Story 1, core URL behaviour (through T057) |
| Day 2 AM | 4 | Phase 4 onward — orchestration state model |
| Day 2 midday | 5 | Approval governance (T058–T068, incl. T061a) |
| Day 2 PM | 6 | Reliability controls (T069–T096) |
| Day 3 AM | 7 | Observability (T097–T122) |
| Day 3 midday | 8 | Three scenarios (T131–T142) |
| Day 3 PM | 9 | Release readiness (T143–T152) |

**Phase 0 — this register and its five siblings — is not on the schedule.** It precedes Day 1 and gates it: `T007` is
the scope-control gate, and Phase 1 does not begin until the owner has acknowledged these controls.

## What happens when a milestone is missed

Per `checkpoints.md`, and stated here so the two files cannot drift:

1. The checkpoint **compares position to the milestone** and records what is done and what is not.
2. It **escalates to the owner with options**, drawn from `backlog.md`.
3. The owner decides. **A scope reduction happens only on her recorded order at that checkpoint.**
4. Mandatory validation and reviewer evidence are **not** among the options, at any checkpoint, for anyone.
