# Checkpoint Decision Procedure

**Task**: T005 · **Requirement**: CN-007 · **ADR**: ADR-004 · **Consumes**: `scope-register.md` (T001),
`backlog.md` (T002), `milestones.md` (T003) · **Derived from**: `plan.md` §14, as amended by CR-009.

## The distinction this file exists to state

**A checkpoint observes and reports. It does not itself cut.**

Under the Gate 5 time-attitude amendment (CR-009), **a scope cut happens only if the owner orders one at a checkpoint,
on the record.** A checkpoint's output is an **escalation with options**, not an executed reduction.

That distinction is the difference between a controlled cut and a quietly narrowed scope, and it is why this file is
written before any code exists — so that the procedure is fixed while nobody is under pressure.

### What a checkpoint does, in order

1. **Compare** the actual position to the milestone in `milestones.md`.
2. **Record** what is done and what is not, with no rounding in either direction.
3. If behind: **escalate to the owner**, with the options named for that checkpoint below, drawn from
   `backlog.md`. **One checkpoint — End Day 2 AM — has no option** and escalates without one (CR-034).
4. **Wait.** The owner decides. **Silence is not approval** and does not authorise a reduction (Constitution III).
5. If she orders a reduction, **record it** — what was cut, on whose order, at which checkpoint.
6. If she does not, **continue**. Being behind is not itself a reason to change anything; completeness takes priority
   over speed and no external deadline exists.

**Being behind is expected at the first two checkpoints.** 173 tasks against a 2–3 day box do not reconcile (plan
§Scope versus timebox, CR-026). The checkpoint showing the plan behind its milestone is **the instrument working, not
failing**, and it is not a licence to cut.

---

## The four checkpoints

### End Day 1

| | |
|---|---|
| **Observes** | Slices 1, 2 and 3 against the Day 1 AM and Day 1 PM milestones |
| **Option escalated if behind** | Reduce Slice 3 to the FR-URL requirements **DS-B needs**; defer rate-limit tier sophistication |
| **May never cut** | Slice 3's acceptance sweep; the baseline-omissions entry; any RED-FIRST red phase |
| **Note** | The aggregate redirect tier is **already** deferred by design and is not available as a cut — it is the brownfield subject, and cutting it would remove DS-B's premise |

### End Day 2 AM

| | |
|---|---|
| **Observes** | Slice 4, the orchestration state model, against the Day 2 AM milestone. **Stop condition 3 also fires here if Slice 4 is incomplete at end of Day 2** |
| **Option escalated if behind** | **None. The former AI-reduction option is STRUCK** — Gate 6 condition, 2026-09-21 (CR-034) |
| **May never cut** | The orchestration model itself. It is the graded artifact; the options offered exclude abandoning it |

**This checkpoint escalates without a pre-drawn option.** It observes Slice 4 against its milestone, records the
position honestly, and asks. That is deliberately harder to answer than an escalation with a menu, and it is the right
trade for the reason below.

**What was struck, and why.** The option read *"reduce AI-capable stages from six toward two (S2 normalization, S7
implementation), the rest running deterministic."* Three things were wrong with it by the time this register was
written:

1. **Its stated mechanism no longer exists.** Decision J (ADR-004 Amendment 02) removed every deterministic counterpart
   for an AI-capable stage. *"The rest running deterministic"* is not available. The option's true meaning became
   **leave stages with no executor at all — parts of the orchestrator unbuilt**, which is not a scope reduction but
   **shipping an incomplete graded artifact**.
2. **The owner had already rejected the same trade.** At Gate 5 she overruled a pre-emptive AI-wiring reduction — *"AI
   is the one writing the code"* — on the ground that its estimate priced AI-authored work at human authoring speed.
   Leaving the option here let a rejected decision return through a side door, and it contradicted `backlog.md`, which
   already forbids AI wiring from being treated as deferrable.
3. **The pressure it existed to serve is gone.** CR-009 removed the external deadline. It was a schedule-relief valve
   for a schedule that does not need relieving.

**It is struck rather than annotated** because an option that reads as cheaper than it is will be chosen under pressure
*precisely because it reads cheap*, and a warning attached to it would be read last, if at all. **A misleading menu item
is removed, not footnoted.**

**If Slice 4 is late**, stop condition 3 applies unchanged: the position is reported and the owner directs, and **the
options offered exclude abandoning the orchestration model**. Any AI-wiring reduction would now need a fresh decision
from her, on the record, with its true cost stated — not the selection of a pre-drawn menu item.

### End Day 2 PM

| | |
|---|---|
| **Observes** | Slices 5 and 6 against the Day 2 midday and Day 2 PM milestones |
| **Option escalated if behind** | Cut **DS-B's injected compensation case to a unit-level proof** — **keep the scenario** |
| **May never cut** | DS-B itself; the two-vote retry proof; the compensation/rollback distinction |

The scenario survives because it is reviewer evidence. What may reduce is the *fidelity of one injected case* within it,
from an in-scenario injection to a unit-level proof — and if that reduction is ordered, **the evidence says which it
was** (AS-007 as extended by CR-033: an induced condition is labelled where its result appears).

### End Day 3 AM

| | |
|---|---|
| **Observes** | Slices 7 and 8 against the Day 3 AM and Day 3 midday milestones |
| **Option escalated if behind** | Reduce the **MTTR population to the reliability suite only** — and **say so in the declared population** |
| **May never cut** | `failure_event` capture before Slice 8 runs (see `critical-path.md`); the three scenarios; the audit reconstruction |

A narrowed population is legitimate **only** when declared. An MTTR figure over a reduced population presented as a
general one is fabrication under CN-005, not a reduction.

---

## What no checkpoint may authorise cutting

Stated absolutely, because this is the clause that makes the rest safe:

1. **Mandatory validation.** Every captured red phase; every negative test; every contract, architecture, integration
   and orchestration assertion. A checkpoint may reduce *scope*; it may never reduce *proof of what was built*.
2. **Reviewer evidence.** The three scenarios, the out-of-scenario generic-executor run, the audit reconstruction, and
   the committed run exports a reviewer reads without running anything.
3. **Disclosure.** A limitation may be disclosed; it may not be omitted, and an omission may not be relabelled as a
   design choice after the fact.
4. **The two non-waivable halts.** Fabrication pressure and an unresolved mandatory policy `FAIL`
   (`stop-conditions.md` conditions 1 and 2). No checkpoint touches these, and the owner cannot waive condition 1
   either.

## Where options come from

**`backlog.md`, and nowhere else.** An option not already recorded as deferrable is not an option a checkpoint may
offer; inventing one under time pressure is the improvisation this whole register set exists to prevent.

The nine slices in `scope-register.md` are **not** a source of options, with the **three** exceptions named
per-checkpoint above — each a **reduction in fidelity within a retained slice**, never the removal of a slice. **Three,
not four, since the Gate 6 condition struck End Day 2 AM’s** (CR-034): that checkpoint now escalates with no pre-drawn
option at all.
