# Change Request CR-034 — The AI-Reduction Checkpoint Option Is Struck

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-21. Approved by Pravallika Veeravalli; applied and verified the same day. **All 18 rows HOLD.** V1, V5 and V17 corrected — two **retained-provenance** defects of the same kind (the strike quotes what it struck, so "appears nowhere" was the wrong assertion; "offered nowhere" is the right one) and one phrasing defect. **This is the third record in which my own replacement text re-introduced the string a row asserted was absent** — the rule is now twice-stated and was still not applied: when a record removes a phrase, its own prose is inside the search space. |
| Raised by | Supervisor, at the Gate 6 presentation, as a recommended condition on the scope-control acknowledgement |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-21** |
| Source decision | **Gate 6 condition** — `docs/governance/gate-decisions/gate-06-scope-control.md` |
| Affected approved artifacts | `plan.md` §14 (the checkpoint-option table, the AI-wiring paragraph); `docs/delivery/checkpoints.md` (T005's artifact, not itself approved text, updated for consistency) |
| Governing constitution | v1.1.0 — **not amended**. `POL-CHG-001` |
| Application order | Sole record. `plan.md` → `checkpoints.md`. Applies to the state left by CR-021..CR-033. |
| Task count | **173 task lines, 172 actionable** — unchanged |

## Owner approval

**Gate 6, approved with this as its single attached condition.** Her decision on the gate, verbatim: **"Go ahead"** —
given in response to a presentation that stated the four acknowledgements and recommended this condition.

**The proposal text was the supervisor's; the decision and its adoption are hers.** Recorded explicitly, as this
project has recorded provenance throughout.

## Why the option is struck rather than annotated

The supervisor raised this at the gate because verification of the Phase 0 registers had surfaced it: the plan's
End-Day-2-AM option **no longer means what it says**, and `checkpoints.md` had been written to carry it forward with a
warning attached. The owner chose removal instead.

**Her grounds, as recorded at the gate:**

1. **Her Gate 5 overrule already rejected trading away AI wiring.** Her words then: *"AI is the one writing the
   code."* She overruled a pre-emptive reduction on the ground that its estimate priced AI-authored work at human
   authoring speed. **An option that re-offers the same trade at a checkpoint lets a rejected decision return through a
   side door** — which is precisely what `backlog.md` already forbids for AI wiring, and the checkpoint table
   contradicted it.
2. **Decision J changed what the option means.** The plan's wording is *"the rest running deterministic"*. ADR-004
   Amendment 02 removed every deterministic counterpart for an AI-capable stage, so the option's true meaning became
   **"leave stages with no executor at all — parts of the orchestrator unbuilt."** That is not a scope reduction; it is
   shipping an incomplete orchestration engine, and the orchestration engine is the graded artifact.
3. **CR-009 removed the pressure the option existed to serve.** There is no external submission deadline. The option
   was a schedule-relief valve for a schedule that does not need relieving.

**A misleading menu item is struck rather than annotated**, because an option that reads as cheaper than it is will be
chosen under pressure *precisely because it reads cheap*, and the annotation would be read last, if at all.

## Applied edits

### Edit 1 — `plan.md` §14, the End-Day-2-AM checkpoint row

**OLD**

```
| End Day 2 AM | **First option offered**: reduce AI-capable stages from six toward two (S2 normalization, S7 implementation), the rest running deterministic. Mode labelling makes any such reduction honest and visible, not hidden. Offered, never applied by default |
```

**NEW**

```
| End Day 2 AM | **No reduction option is offered. The former AI-reduction option is STRUCK** — Gate 6 condition, 2026-09-21 (CR-034). It read *"reduce AI-capable stages from six toward two, the rest running deterministic"*, and **Decision J removed every deterministic counterpart** (ADR-004-A2), so its true meaning became *leave stages with no executor at all — parts of the orchestrator unbuilt*. That is not a scope reduction; it is shipping an incomplete graded artifact. The owner's Gate 5 overrule had already rejected the same trade. This checkpoint **observes Slice 4 against its milestone, records the position, and escalates without a pre-drawn option**; stop condition 3's rule stands — the options offered exclude abandoning the orchestration model |
```

### Edit 2 — `plan.md` §14, the AI-wiring paragraph

The paragraph's last clause is now false: reduction does **not** re-enter by a recorded checkpoint order, because the
checkpoint no longer carries the option.

**OLD** `The owner overruled an earlier pre-emptive reduction on the ground that its estimate priced\nAI-authored work at human authoring speed; reduction re-enters only by a recorded checkpoint order (CR-009).`

**NEW**

```
The owner overruled an earlier pre-emptive reduction on the ground that its estimate priced
AI-authored work at human authoring speed. **As of the Gate 6 condition (CR-034) there is no checkpoint option to
reduce it either**: the End-Day-2-AM option that once carried that route is struck, because Decision J made its true
meaning *leave stages with no executor*. Any reduction would now require a fresh owner decision on the record, with
what it costs stated — not the selection of a pre-drawn menu item.
```

### Edit 3 — `docs/delivery/checkpoints.md`, the End Day 2 AM section

The register was written to carry the option forward with a warning. The whole block is replaced by the strike, and the
reason is kept rather than deleted, because a reader who finds no option at this checkpoint is entitled to know one used
to be there and why it went.

*(Full replacement text in the applied diff; the substance is Edit 1's, expanded, plus the "may never cut" row which
is unchanged.)*

### Edit 4 — `docs/delivery/backlog.md`, the AI-wiring absence

Its closing paragraph says a reduction *"re-enters only by a recorded checkpoint order"* and that the option is *"the
first option offered at the End Day 2 AM checkpoint"*. Both are now false.

**NEW** — the paragraph states that **no checkpoint offers it**, and that a reduction would require a fresh owner
decision with its cost stated.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR.** No requirement, architecture, schema, state semantic or security control changes. A **scope-reduction option is removed**, which narrows what may happen at a checkpoint rather than what the system must do. It is the direction that needs saying out loud: the plan now offers the owner *fewer* ways to reduce scope than it did. |
| **Backward-compatibility impact** | None. Nothing implemented. |
| **Affected consumers** | `docs/delivery/checkpoints.md` and `backlog.md` (Phase 0 registers, T005 and T002 artifacts); the Gate 6 record, which carries the condition. |
| **Affected tests** | None. No task's validation referenced this option; T005's Guard required the option to be *offered, never applied by default*, and that requirement is discharged by its removal rather than contradicted — **stated plainly because a reader could reasonably ask whether striking it violates the guard it satisfied.** The guard forbade applying a reduction by default; it did not require the option to exist. |
| **Affected documentation** | `plan.md` §14; the two registers. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner — **given as the Gate 6 condition, 2026-09-21**. |

## Post-application verification

| # | Edit | Command (`P=plan.md`, `CK=docs/delivery/checkpoints.md`, `BL=docs/delivery/backlog.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'the rest running deterministic' $P` | `0` — the impossible option text is gone | **EXECUTED: 1 total, 0 binding** — HOLD. EXPECTATION DEFECT (retained provenance): the one occurrence is inside the strike’s own quote of what was struck, which is deliberate — a reader must be able to see what was removed. The right assertion is "it is offered nowhere", not "it appears nowhere" |
| V2 | 1 | `grep -c 'former AI-reduction option is STRUCK' $P` | `1` | **EXECUTED: 1** — HOLD |
| V3 | 1 | `grep -c 'shipping an incomplete graded artifact' $P` | `1` — the true meaning on the record | **EXECUTED: 1** — HOLD |
| V4 | 1 | `grep -c 'escalates without a pre-drawn option' $P` | `1` | **EXECUTED: 1** — HOLD |
| V5 | 1 | `grep -c 'options offered exclude abandoning the orchestration model' $P` | `≥ 2` — the checkpoint row and stop condition 1, which is unchanged | **EXECUTED: 1 + 1** — HOLD. EXPECTATION DEFECT (phrasing): stop condition 1 reads "exclude abandoning **it**", not "...the orchestration model". Both places protect it; only one uses my row’s words |
| V6 | 2 | `grep -c 'reduction re-enters only by a recorded checkpoint order' $P` | `0` — the false clause is gone | **EXECUTED: 0** — HOLD |
| V7 | 2 | `grep -c 'no checkpoint option to' $P` | `1` | **EXECUTED: 1** — HOLD. my row quoted the fragment "no checkpoint option to"; asserted on the whole clause after a malformed nested-bold in my own edit was corrected |
| V8 | 3 | `grep -c 'First option offered' $CK` | `0` | **EXECUTED: 0** — HOLD |
| V9 | 3 | `grep -c 'STRUCK' $CK` | `≥ 1` | **EXECUTED: 1** — HOLD |
| V10 | 3 | `grep -c 'May never cut' $CK` | `4` — all four checkpoints keep theirs | **EXECUTED: 4** — HOLD |
| V11 | 3 | `grep -c '^### End Day' $CK` | `4` — the checkpoint is not deleted, only its option | **EXECUTED: 4** — HOLD |
| V12 | 4 | `grep -c 'first option offered' $BL` | `0` | **EXECUTED: 0** — HOLD |
| V13 | 4 | `grep -c 'no checkpoint offers it' $BL` | `1` | **EXECUTED: 1** — HOLD. my row quoted "no checkpoint offers it"; the applied text reads "no checkpoint offers it either" |
| V14 | inv | `grep -c '^\| \*\*B[0-9]\*\*' $BL` | `8` — no backlog item added or removed | **EXECUTED: 8** — HOLD |
| V15 | inv | `grep -c '173 tasks' $P` | `≥ 2` — task count untouched | **EXECUTED: 2** — HOLD |
| V16 | inv | `git diff --stat .specify/memory/constitution.md` | **empty** | **EXECUTED: ''** — HOLD |
| V17 | orphan | `grep -rc 'reduce AI-capable stages' plan.md tasks.md spec.md quickstart.md docs/delivery/` | `0` everywhere | **EXECUTED: 2 total, 0 binding** — HOLD. EXPECTATION DEFECT (retained provenance), same cause as V1: both survivals are the strike quoting what it struck — plan §14’s checkpoint row and checkpoints.md’s "What was struck, and why". Neither offers it |
| V18 | 51-criteria regression | re-run the Phase 0 validation suite | all pass, **with T005's AI-option assertion inverted** to require absence | **EXECUTED: 51 criteria, 0 FAIL** — HOLD. T005’s AI-option assertion **inverted, not deleted**: it now requires the option to be absent and the strike to be recorded. A check that stops testing a rule is not the same as a rule that changed. |

## Cross-record orphan check

| Question | Result |
|---|---|
| Does every `OLD` string this record quotes still exist in its target? | **Asserted at application** — all three located, or the edit stops. |
| Does this record quote text another record removes first? | **No.** CR-021..CR-033 are all applied; this record is the sole editor of every string it touches, each read from the post-package state. |
| Does any **applied** record now assert something this record falsifies? | **Yes, two, both corrected forward.** **CR-009** (Gate 5) established that AI-wiring reduction *"re-enters only by a recorded checkpoint order"* — true when written, false now; CR-009 is applied and immutable, so Edit 2 corrects it forward in `plan.md` and this record states the supersession. **CR-030** did not touch the checkpoint table, so it is unaffected. |
| Do the stated application orders agree? | **Yes.** Sole record. |
| Does any survival remain unclaimed? | **V17 is that assertion**: zero occurrences of *"reduce AI-capable stages"* across the specification, plan, tasks, quickstart and every delivery register. |
| Does the Phase 0 validation suite still hold? | **V18.** The suite contains an assertion that the AI-reduction option **is** offered — written yesterday against the register as it then stood. It must be **inverted, not deleted**, so the suite asserts the strike rather than falling silent about it. A check that stops testing a rule is not the same as a rule that changed. |

**V18 is this check's finding.** The orphan discipline is written for change records, and the Phase 0 validation suite
is not one — but it encodes the same claims, so a record that falsifies a register also falsifies the suite that
validates it. **The suite is inside the search space**, and nothing in the five questions would have said so.

## Residual risk

**The owner now has one fewer lever at the End-Day-2-AM checkpoint, and if Slice 4 is late she will be told so with no
pre-drawn option to choose.** That is the intended effect and it is still worth naming: an escalation without options is
harder to answer than one with them.

**Why it is nonetheless the right trade.** The struck option was not a real lever — it was a lever labelled with the
wrong price. Choosing it would have left parts of the orchestration engine unbuilt while reading as a scope trim, and
the engine is the graded artifact. An honest escalation with no options beats a menu whose cheapest-looking item is the
most expensive one on it.

**What is available instead**: the other three checkpoints keep their options; `backlog.md`'s eight items remain
available on her order at any checkpoint; and stop condition 3 still escalates rather than halting, so a late Slice 4
produces a conversation rather than a freeze.
