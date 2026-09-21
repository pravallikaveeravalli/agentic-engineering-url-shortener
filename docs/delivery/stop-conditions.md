# Stop-Conditions Register

**Task**: T006 · **Requirements**: CN-007, CN-005, Constitution X · **Derived from**: `plan.md` §14 Stop conditions, as
classified by the Gate 5 time-attitude amendment (CR-009).

## The governing distinction

**A stop condition halts and reports. It never authorises silent scope reduction.**

The four conditions are **classified by what the trigger does**, because two are correctness controls and two are
time-based — and the owner ruled that **time must not freeze work or narrow scope on its own** (CR-009).

- **HALT** — work on the affected path stops and the position is reported. Two of these; one is non-waivable with no
  exception path at all.
- **ESCALATE** — the position is reported to the owner **with options**, and she decides. The trigger does not itself
  cut, extend, or reduce anything.

---

## The four conditions

### 1. Fabrication pressure — **HALT, non-waivable**

**Trigger**: any temptation to present a **proposed or simulated result as executed**. Not only the act — the
temptation. The condition fires when the thought occurs, because by the time the artifact exists the evidence base is
already compromised.

**Required report**: stop the affected task; state what was about to be claimed, what was actually executed, and what
would be required to execute it honestly.

**No escalation, no discretion, no exception path.** Constitution Principle X and CN-005 are non-waivable, and this is
the one condition with **no owner override**: she cannot authorise it either, which is what non-waivable means. An
exception record cannot be filed against it.

**What it covers, concretely**: test results, metrics, logs, traces, approvals, workflow histories, retry events,
rollback and compensation events, replanning events, performance statistics. And the subtler form — **an induced
condition presented as an encountered one**. This project injects three conditions deliberately (T136's transient
fault, T141's empty change plan, T061a's gate advance), and **each must be labelled as injected in the same place it
presents its result** (AS-007 as extended by CR-033). An unlabelled injection is fabrication by omission.

### 2. Mandatory policy `FAIL` unresolved at Slice 9 — **HALT**

**Trigger**: any of the twelve mandatory checks in `policy-set-1.1.0` reports `FAIL` and is unresolved when release
readiness is evaluated.

**Required report**: release readiness reports **blocked**, naming the failing check and what would resolve it.

**Report blocked rather than weaken the check.** Constitution VI requires the evaluation; Principle XI is non-waivable.
All twelve checks are mandatory — there is no advisory tier to demote a check into. Downstream progression is blocked
and release readiness cannot be declared.

### 3. Engine core late — Slice 4 incomplete at end of Day 2 — **ESCALATE**

**Trigger**: the orchestration state model is not complete when the end-of-Day-2 checkpoint is reached.

**Required report**: the position — what of Slice 4 exists, what does not, why — escalated to the owner for direction.

**This was previously written as an automatic halt to feature work. Under the Gate 5 amendment it reports and asks.**
It is time-based, and the owner ruled that a date may not stop work on its own.

**The options offered exclude abandoning the orchestration model.** It is the graded artifact and cannot be traded
away — so the escalation presents ways to reach it, not ways to do without it.

### 4. Timebox exhausted with slices incomplete — **ESCALATE**

**Trigger**: the 2–3 day box is spent and slices remain incomplete.

**Required report**: exactly what is done, what is not, and why — with no rounding in either direction.

**Scaling scope down is the owner's call, not the agent's, and the agent does not pre-empt it.** Options are drawn from
`backlog.md`. The two non-waivable halts above are untouched by this condition: nothing here permits weakening a check
or presenting unexecuted work as executed in order to fit a box.

---

## Summary table

| # | Condition | Behaviour | Waivable? | Why this classification |
|---|---|---|---|---|
| 1 | **Fabrication pressure** | **HALT** | **No — non-waivable, no exception path, not even by the owner** | Principle X, CN-005. Evidence integrity is the whole basis of the submission's credibility |
| 2 | **Mandatory policy `FAIL` unresolved** | **HALT** — blocks downstream progression and release readiness | **No — Principle XI is non-waivable** | Constitution VI. All twelve checks in `policy-set-1.1.0` are mandatory; there is no advisory tier |
| 3 | **Engine core late** (Slice 4 incomplete, end of Day 2) | **ESCALATE for the owner's direction** | Time-based, so the owner directs | Was an automatic halt; the Gate 5 amendment made it report-and-ask, because time must not freeze work on its own |
| 4 | **Timebox exhausted**, slices incomplete | **ESCALATE for the owner's direction** | Time-based, so the owner directs | Reports the true position; the owner decides whether anything is cut |

**Two non-waivable halts, marked as such**: conditions 1 and 2. Neither has an exception path under
`docs/governance/exceptions/`, because an exception against a non-waivable principle is itself a violation
(Constitution §Exception procedure, Principle VI).

---

## What no stop condition may do

1. **Authorise a silent scope reduction.** A cut happens only on the owner's recorded order at a checkpoint (CR-009).
2. **Cut mandatory validation or reviewer evidence.** Not at any checkpoint, not under any condition, by anyone.
3. **Relabel incomplete work as done.** The plan's minimum defensible outcome says it plainly: anything less is
   **reported as incomplete rather than relabelled as done**.
4. **Justify presenting unexecuted work as executed.** Condition 1 governs, and it outranks every schedule pressure
   that could be argued against it.
