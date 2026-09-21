# Change Request CR-023 — A Distinct, Short Escalation Deadline for Node Overrun

| Field | Value |
|---|---|
| Status | **WITHDRAWN** — 2026-09-20, by owner ruling. **Never applied.** No artifact carries any edit from this record. |
| Raised by | Pre-implementation Principal Engineer review, condition **A2** |
| Approving authority | Pravallika Veeravalli (human owner) — **ruled against the record itself** |
| Disposition of finding A2 | **Declined by owner, cost accepted.** |
| Affected approved artifacts | `spec.md` (new PVT-017, §Validation Targets preamble, FR-ORC-014 rule 6), `plan.md` (§3 preamble, §5 node-overrun row, §6), `tasks.md` (T086a) |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Application order | **None — withdrawn before application.** This record appears in no artifact’s order. The order row of the withdrawn draft below is void. |

## Withdrawal — owner ruling, 2026-09-20

**Her ruling, verbatim:**

> *"I don't think we should have any timeout as I said. Just wait till the user responds. Yeah, that's all. Keep it
> simple."*

**PVT-017 is not created.** No new validation target, no new deadline, no new gate class behaviour. The stage-overrun
question is an **ordinary ask under the existing uniform rule she approved at the clarify gate**: PVT-006's gate-wait
deadline like every other ask, expiry to safe suspension, resume re-asks, the 90-day staleness clock unchanged.

**This ruling requires no edit anywhere, and that is worth stating plainly.** The approved text already says exactly
what she ruled. PVT-016's conditions cell reads *"**No answer** follows the gate-wait deadline (PVT-006) to suspension:
silence is never approval (Constitution III)"*, and `plan.md` §6 reads *"An unanswered escalation suspends."* Both are
correct under the uniform rule. **The only thing that would have made them wrong was this record.** Withdrawing it
leaves the artifacts correct — there is nothing to revert, because nothing was applied.

**Reviewer finding A2 is dispositioned: declined by owner, cost accepted.** The accepted cost, stated so it is not
discovered later: **a stalled node may wait the uniform deadline — PVT-006, 24 hours — before the run parks itself.**
In a 2–3 day box that is a third of the budget consumed by one slow provider call, and an unattended demonstration run
would appear to hang for a day before suspending. The owner weighed that against adding a second deadline class and
chose simplicity. The compensating facts are that suspension is non-terminal and resumable, that the deadline is
configurable and demonstration runs may compress it under AS-007, and that **a human watching a run can answer the
escalation at any point** — the 24 hours is the ceiling on silence, not an expected wait.

**Nothing needed re-homing.** Edit 2 below existed only because adding PVT-017 would have made CR-018's applied
preamble — *"Sixteen targets, of which fifteen constrain implementation and one is retired"* — stale. With PVT-017 not
created, **that preamble is correct as applied**: `spec.md` carries 16 distinct PVT identifiers, 15 binding and PVT-010
retired. Verified rather than assumed; the row is in §Withdrawal verification below. The cross-record orphan check that
found the original staleness is what confirms it is now a non-issue.

**This record is retained, not deleted.** Records are never deleted in this project. What follows below the line is the
**withdrawn draft as it stood**, preserved unaltered so the reasoning the owner ruled against is readable. **None of it
is in force.** Every "Applied edits" heading, proposed value and verification row below describes work that **did not
happen**.

## Withdrawal verification

Executed against the working tree at withdrawal, to prove the withdrawal leaves nothing behind and nothing stale.
**All six hold.** W6's expectation was written wrongly and is corrected in place with the executed result, rather than
restated to match what ran:

| # | Command | Expected | Result |
|---|---|---|---|
| W1 | `grep -c 'PVT-017' spec.md plan.md tasks.md` | `0` each — the target was never created | **EXECUTED: 0, 0, 0** — HOLD |
| W2 | distinct `PVT-\d+` in `spec.md` | `16` — unchanged | **EXECUTED: 16** — HOLD |
| W3 | `grep -c 'Sixteen targets, of which fifteen constrain' spec.md` | `1` — CR-018's preamble is **correct**, not stale | **EXECUTED: 1** — HOLD. Nothing needed re-homing |
| W4 | `grep -c 'follows the gate-wait deadline (PVT-006) to suspension' spec.md` | `≥ 1` — the uniform rule she affirmed, already present | **EXECUTED: present** — HOLD |
| W5 | `grep -c 'An unanswered escalation suspends.' plan.md` | `1` — unchanged and correct | **EXECUTED: 1** — HOLD |
| W6 | `grep -rc 'CR-023' docs/governance/change-control/CR-0{21,22,24,25,26,27,28,29,30,31,32}*.md` | `0` in every other record's application order | **EXECUTED: 11 hits, one per record** — each is the *"CR-023 is WITHDRAWN and appears in no order"* notice, not an order entry. **HOLD, with the expectation corrected**: the honest assertion is not *"CR-023 is unmentioned"* but *"CR-023 appears in no order"*, and the withdrawal is stated in all eleven rather than left silent |

---

# ⌦ WITHDRAWN DRAFT BELOW — NOT IN FORCE, RETAINED FOR THE RECORD

## The finding

CR-010 made a breached execution threshold raise a **gate**, so that only a human decides whether a slow node waits or
dies. Correct. But gates wait **PVT-006 = 24 hours** before suspending, and nothing gave the overrun gate its own
deadline.

**Consequence**: a node breaching at 120 seconds stalls the run for a day. In a 2–3 day box that is a third of the
budget for one slow provider call, and an unattended demonstration run would simply appear to hang. The review's
judgement was that the accepted counter-case covers *stall-then-suspend versus auto-kill* but says nothing about
*duration* — and it was right; the 24-hour figure was inherited by default rather than chosen.

## Proposed value and grounds — the owner picks

**Proposed: PVT-017 = 30 minutes.** The supervisor's recommendation to the owner was 30–60; this draft takes the lower
end. Grounds, and the trade at each end:

| Value | Case for | Case against |
|---|---|---|
| **15 min** | An unattended run reaches a decidable state fast; the demonstration never looks hung | A human momentarily away — lunch, a meeting — returns to a suspended run and must resume it. Suspension is cheap but it is one more step in a reviewer's path |
| **30 min** *(proposed)* | Long enough that ordinary absence does not suspend a run; short enough that a stalled node costs ~1% of a 3-day budget rather than 33%. **A breach is re-armable**, so the cost of choosing too short is one extra question, not lost work | A reviewer who steps away for an hour still returns to a suspended run |
| **60 min** | Comfortably covers an hour's absence | An unattended overnight run suspends after an hour regardless, so the extra 30 minutes buys attendance tolerance and nothing else |

**The asymmetry is what makes the low end safe**: choosing too short costs a repeated question; choosing too long costs
budget and makes the system look broken. That argument points at 15; **30 is proposed because it is the smallest value
that survives an ordinary interruption**, and a demonstration a reviewer watches should not punish them for stepping
away.

**Why a separate target rather than reusing PVT-006**: the two deadlines answer different questions. PVT-006 asks *how
long should a human have to decide something substantive* — 24 hours is right for an architecture approval. The overrun
ask is *is this still running or is it stuck*, which is operational, answerable in seconds, and worthless once stale.

## Applied edits

### Edit 1 — new **PVT-017** row in §Validation Targets

```
| PVT-017 | Node-overrun escalation deadline | 30 minutes | A breached execution threshold (PVT-016) raises a gate, and gates otherwise wait PVT-006 = 24 h — which would stall a run for a day over one slow provider call, a third of the approved timebox. This deadline is **operational, not deliberative**: the question is "is this stuck?", answerable in seconds and worthless once stale, unlike PVT-006's substantive decisions. The asymmetry favours the low end — too short costs one repeated question because a breach is **re-armable**; too long costs budget and makes the system look hung | Configurable; demonstration runs may compress it, labelled per AS-007. On expiry the run suspends exactly as any gate does (FR-ORC-017) — **the deadline changes how long the ask waits, never what silence means** |
```

### Edit 2 — §Validation Targets preamble, seventeen targets

**This edit exists because of the cross-record orphan check, and it is the check's first catch.** CR-018 was applied
hours ago and restated the preamble as *"Sixteen targets, of which fifteen constrain implementation and one is
retired."* Adding PVT-017 makes that stale the moment this record applies.

**OLD** `**Sixteen targets, of which fifteen constrain implementation and one is retired.**`
**NEW** `**Seventeen targets, of which sixteen constrain implementation and one is retired.**`

and in the same paragraph:

**OLD** `Fifteen binding, one retired, sixteen rows.`
**NEW** `Sixteen binding, one retired, seventeen rows.`

and appended to the PVT-016 bullet:

```
- **PVT-017** — added by **CR-023** (node-overrun escalation deadline). Binding: the overrun ask must carry its own
  short deadline rather than inheriting PVT-006's 24 hours.
```

### Edit 3 — FR-ORC-014 rule 6 names the deadline

**OLD** `**Absence of an answer follows the gate-wait deadline to suspension**`
**NEW** `**Absence of an answer follows the overrun escalation deadline (PVT-017) to suspension** — a short, operational deadline distinct from PVT-006's deliberative gate wait, because "is this node stuck?" is not a question worth holding a run open for a day`

### Edit 4 — `plan.md` §3 preamble

**OLD** `and an unanswered escalation follows the gate-wait deadline to suspension.`
**NEW** `and an unanswered escalation follows **PVT-017** (30 min) to suspension — not PVT-006's 24 h, which is for deliberative decisions.`

### Edit 5 — `plan.md` §5 node-overrun row gains its deadline

Appended to the Blocks cell: `. Waits **PVT-017**, not PVT-006`

### Edit 6 — `plan.md` §6 timeout/overrun row

**OLD** `An unanswered escalation suspends.`
**NEW** `An unanswered escalation suspends after **PVT-017** (30 min), a deadline distinct from PVT-006 because the question is operational rather than deliberative.`

### Edit 7 — T086a

**Artifact** gains: `; the ask carries **PVT-017** as its deadline, not PVT-006`.
**Validate**'s silence case gains: `asserting the run suspends after **PVT-017**, and that PVT-006 is **not** used for this gate class`.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR.** A new binding target and a new obligation on one gate class. Nothing is relaxed: silence still suspends, and what silence means is untouched. |
| **Affected consumers** | `plan.md` §3/§5/§6, `tasks.md` T086a, `spec.md` preamble (the orphan catch). |
| **Affected tests** | T086a's silence case gains a deadline assertion and a negative assertion that PVT-006 is not used. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner — **the value is hers**. The draft proposes 30 minutes with the 15/60 trade laid out. |

## Post-application verification

| # | Edit | Command | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'PVT-017' spec.md` | `≥ 3` | **VOID — never executed; this record was withdrawn before application** |
| V2 | 1 | count of `^| PVT-` rows in `spec.md` | `17` | **VOID — never executed; this record was withdrawn before application** |
| V3 | 1 | distinct `PVT-\d{3}` identifiers in `spec.md` | `17` | **VOID — never executed; this record was withdrawn before application** |
| V4 | 2 | `grep -c 'Seventeen targets' spec.md` | `1` | **VOID — never executed; this record was withdrawn before application** |
| V5 | 2 | `grep -c 'Sixteen targets, of which fifteen' spec.md` | `0` — CR-018's now-stale count is gone | **VOID — never executed; this record was withdrawn before application** |
| V6 | 2 | `grep -c 'Sixteen binding, one retired, seventeen rows' spec.md` | `1` | **VOID — never executed; this record was withdrawn before application** |
| V7 | 3 | `grep -c 'overrun escalation deadline (PVT-017)' spec.md` | `1` | **VOID — never executed; this record was withdrawn before application** |
| V8 | 4, 6 | `grep -c 'PVT-017' plan.md` | `≥ 3` | **VOID — never executed; this record was withdrawn before application** |
| V9 | 5 | `grep -c 'Waits \*\*PVT-017\*\*, not PVT-006' plan.md` | `1` | **VOID — never executed; this record was withdrawn before application** |
| V10 | 7 | `grep -c 'PVT-017' tasks.md` | `≥ 2` | **VOID — never executed; this record was withdrawn before application** |
| V11 | invariant | `grep -c '| PVT-006 |' spec.md` | `1` — PVT-006 itself untouched | **VOID — never executed; this record was withdrawn before application** |
| V12 | invariant | `grep -c 'silence is never approval' spec.md` | `≥ 1` — what silence means is unchanged | **VOID — never executed; this record was withdrawn before application** |

**V5 is the orphan check made executable.** It asserts that a count CR-018 wrote is gone, which is the class of defect
the new cross-record discipline exists to catch.

## Residual risk

**A 30-minute deadline means a run left alone overnight will be suspended by morning.** That is the intended behaviour
and it is still a behaviour change worth naming: resumption is a human action, so an unattended overnight run now
requires a morning resume rather than being found mid-flight. The compensating fact is that the alternative — finding
it 23 hours into a 24-hour wait with the node still stuck — is worse in every respect except appearing to need no
attention.
