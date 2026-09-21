# Change Request CR-033 — EC-040's Trigger Restated for an Always-AI System; the No-Plan Gate Demonstrated by Injection

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-21. Approved by Pravallika Veeravalli; applied and verified the same day. **All 20 rows HOLD on first execution** — no expectation corrected, no edit wrong. The first record in this project to do so, which is attributable to its size rather than to anything new: six edits against text read immediately beforehand. |
| Raised by | `/speckit-analyze` re-run, 2026-09-21, finding **M1** — the only finding the CR-021..CR-032 package introduced |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-21** |
| Source decision | Owner decision, 2026-09-21, **option (a)** of three presented with costs |
| Affected approved artifacts | `spec.md` (EC-040, §Assumptions, a gate-discharge row), `tasks.md` (T141, header), `quickstart.md` (stage-7 passage) |
| Governing constitution | v1.1.0 — **not amended**. `POL-CHG-001` |
| Application order | Sole record. `spec.md` → `tasks.md` → `quickstart.md`. Applies to the state left by CR-021..CR-032. |
| Task count | **173 task lines, 172 actionable** — unchanged; no task added or struck |

## Owner approval

Her answer, verbatim: **"Let's go with ur recommendation"** — **option (a)**, given after all three dispositions were
put to her with their costs:

- **(a)** *chosen* — restate EC-040's trigger honestly and demonstrate the gate by **fault injection**. Cheapest; the
  gate is still proven; a reviewer no longer meets it by accident.
- **(b)** declined — same restatement but drop T141's gate assertion entirely, losing the passage the quickstart calls
  the place *"where a reviewer will personally meet governance"*.
- **(c)** declined — keep a reviewer-reachable route by adding a submission mode that skips authoring. New scope, and
  it would re-introduce a switch Decision J had just removed.

## The finding

Owner **Decision J** removed the keyless mode. EC-040 names its own trigger as *"the case a reviewer creates by
submitting their own requirement **with AI off**"*. There is no AI off.

**The obligation was never in doubt and is not changed here.** FR-ORC-031 still forbids a no-op advancing, T065 still
builds the gate with its three options and its negative test, and CR-028 made the gate the *sole* guard at stage 7 once
the deterministic counterpart went. What broke is narrower and specific: **the stated route to reaching the gate no
longer exists**, because the AI now authors the change from stage 6's design output. And **T141 asserted a
demonstration that route produced** — *"the no-plan gate is deliberately exercised (EC-040) since that is where a
reviewer will personally meet governance"* — which an always-AI run will not produce naturally.

This was a consequence of Decision J found on verification rather than at decision time, and it is the single finding
the eleven-record package introduced. It is recorded that way rather than folded quietly into the package that caused
it.

## Applied edits

### Edit 1 — `spec.md` EC-040's trigger restated

**OLD**

```
- **EC-040**: Deterministic stage 7 is reached with **no change plan** for the requirement — the case a
  reviewer creates by submitting their own requirement with AI off. Must suspend at the no-plan gate
  offering governance-only / human-implemented / abandon; must **not** no-op forward, and must not allow
  downstream stages to execute on the basis of an unimplemented change (FR-ORC-031, CR-001).
```

**NEW**

```
- **EC-040**: Stage 7 is reached with **no change plan** for the requirement. Must suspend at the no-plan gate
  offering governance-only / human-implemented / abandon; must **not** no-op forward, and must not allow
  downstream stages to execute on the basis of an unimplemented change (FR-ORC-031, CR-001).
  **How this condition arises, restated by CR-033.** It previously named one route: a reviewer submitting their own
  requirement with the AI disabled. **Decision J removed that route** — orchestration always uses AI, so stage 7 authors the
  change from stage 6's design output and a plan normally exists. The condition now arises when **stage 6 produced no
  usable design output**, or when **authoring yields nothing applicable** to the branch. Both are real failure states
  and neither is reviewer-reachable on demand, which is why the demonstration is **injected** (T141) rather than
  produced by an ordinary submission.
  **The obligation is unchanged and the gate is more load-bearing than before**: with no deterministic counterpart at
  stage 7 (ADR-004-A2), this gate is the **sole** guard against an unimplemented change advancing.
```

### Edit 2 — `spec.md` §Assumptions, AS-007's labelling rule extended to injected conditions

AS-007 already requires compressed time parameters to be labelled. The same discipline covers an injected condition,
and it is stated rather than assumed:

**OLD** `- **AS-007**: Demonstration runs may compress time-based parameters such as PVT-006, provided\n  every compressed value is labelled as compressed in the evidence.`

**NEW**

```
- **AS-007**: Demonstration runs may compress time-based parameters such as PVT-006, provided
  every compressed value is labelled as compressed in the evidence. **The same rule governs an injected condition**
  (CR-033): where evidence exists because a condition was deliberately induced rather than encountered — an injected
  transient fault (T136), an injected empty change plan (T141), an injected gate advance (T061a) — the evidence MUST
  say so in the same place it presents the result. **An induced demonstration presented as an encountered one is an
  evidence-integrity violation** under Principle X, and it is the failure mode that makes injection safe to use at all.
```

### Edit 3 — `tasks.md` T141 demonstrates the gate by injection, labelled

**OLD** — T141's Validate, Trace, Guard and Done fields.

**NEW**

```
  - **TDD**: EVIDENCE · **Validate**: run completes; zero executor changes in the diff; **and the stage-7 no-plan gate is exercised by injecting the empty-change-plan condition** (EC-040) — the design output is withheld so stage 7 is reached with nothing to apply, and the gate must suspend with its three options rather than no-op forward. **The evidence MUST label this as an injected demonstration** (AS-007 as extended by CR-033), in the same place it presents the suspension, exactly as T136 labels its injected transient fault
  - **Docs**: `docs/REVIEWER-GUIDE.md` · **Trace**: SC-016, EC-040 · **Guard**: **executors that recognised blessed demo inputs would be a rigged demonstration.** This run is the proof that they do not — and **the generic-executor proof never depended on the mode**: an executor that branched on recognising blessed inputs would be rigged whichever way it was invoked. **Why the gate is now injected rather than encountered** (CR-033): before Decision J a reviewer reached this gate by submitting their own requirement with the AI disabled, and that route no longer exists because stage 7 authors the change. Injection is the honest substitute, and it is **not** a weaker proof of the gate — **T065's own tests remain the structural proof**, one per option plus the negative test that a labelled no-op cannot advance downstream. What is lost is that a reviewer no longer meets the gate by accident, and the guide says so rather than implying otherwise · **Done**: run completes; the no-plan gate exercised **and labelled as injected**; diff clean of executor changes · **Approval**: none
```

### Edit 4 — `quickstart.md`'s stage-7 passage says where a reviewer actually meets the gate

The passage currently reads as though a reviewer will hit the gate by submitting their own requirement. Since Decision
J they will not, and the guide must not imply it.

**OLD** `This is the part worth watching for, because it is where the system refuses to pretend. Where **no change plan exists**\nfor the requirement, stage 7 does not record a no-op and flow onward:`

**NEW**

```
This is the part worth watching for, because it is where the system refuses to pretend. Where **no change plan exists**
for the requirement, stage 7 does not record a no-op and flow onward:
```

and immediately after the three-option table's trailing paragraph, the honest note:

```
**Where you will actually see this.** Because orchestration always uses AI, stage 7 authors the change from stage 6's
design output, so an ordinary submission of your own requirement will **not** reach this gate — the condition needs
stage 6 to have produced nothing usable. The committed out-of-scenario run therefore reaches it by **injecting the
empty-change-plan condition**, and the evidence **says so on the record it appears in** (CR-033). That is a deliberate
trade: you no longer meet this gate by accident, and we would rather tell you that than let a demonstration imply a
route that no longer exists. The gate's own behaviour — three options, and a labelled no-op that cannot advance
downstream — is proven structurally by its unit tests, independently of any run.
```

### Edit 5 — `tasks.md` header's stale policy set *(rider 1 — pre-existing LOW)*

A **live declaration**, not history: the task plan states which policy set governs it, and T097 builds 1.1.0.

**OLD** `**Governing constitution**: v1.1.0 · **Policy set**: `policy-set-1.0.0` · **Generated**: 2026-09-20`
**NEW** `**Governing constitution**: v1.1.0 · **Policy set**: `policy-set-1.1.0` (CR-015; corrected here by CR-033 — the header had not been bumped with the set) · **Generated**: 2026-09-20`

### Edit 6 — `spec.md` gate-discharge row annotated as historical *(rider 2 — pre-existing LOW)*

This row records **what was checked at that gate**, so `policy-set-1.0.0` is correct as history and wrong as a live
claim. It is annotated rather than changed, which is this project's standing treatment of retained provenance.

**OLD** `| Constitution check against v1.1.0 with policy version recorded | **DISCHARGED** — plan §Constitution Check, `policy-set-1.0.0` |`
**NEW** `| Constitution check against v1.1.0 with policy version recorded | **DISCHARGED** — plan §Constitution Check, against `policy-set-1.0.0`, **the set in force at that gate**. The set is now `policy-set-1.1.0` (CR-015: `POL-CHG-003` added, `POL-AUD-002` removed); this row is **a record of what was checked, not a statement of the current set** (annotated by CR-033) |`

### Deviation from the drafted edit text, corrected in this record

Draft 1 of Edits 1 and 3 wrote *"with AI off"* inside their own **NEW** text — while **V1 and V20 assert that string
appears nowhere**. The record would have defeated its own verification. Both were applied as *"with the AI disabled"*,
which says the same thing without re-minting the phrase being retired, and the quoted text above is corrected to match
what was applied rather than left describing something else.

This is the second time in this project that a record's own prose has re-introduced a string its verification row
asserts is absent. The general lesson is worth stating once: **when a record retires a phrase, its own explanatory
text is inside the search space.**

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR.** No requirement is added, removed or relaxed. EC-040's *obligation* is verbatim unchanged; only its stated trigger is corrected, and the correction is a narrowing of a claim about how the condition arises. T141's validation becomes **more specific**, naming the injection and requiring it to be labelled. |
| **Backward-compatibility impact** | None. Nothing implemented. |
| **Affected consumers** | `quickstart.md`'s stage-7 passage; `docs/REVIEWER-GUIDE.md` via T128, which already carries the capability framing. |
| **Affected tests** | **T141 gains a concrete injection and a labelling assertion** where it previously assumed a naturally-occurring condition — a strict improvement, because the old wording would have been satisfiable by a run that never reached the gate at all. **T065 is untouched**: one test per option plus the negative no-op test remain the structural proof, and they never depended on the executor mode. |
| **Affected documentation** | `quickstart.md`; `docs/LIMITATIONS.md` needs no new entry — the loss here is *reviewer convenience*, not a missing control, and it is disclosed in the guide where a reviewer meets it. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner — **given, 2026-09-21, option (a)**. |

## Post-application verification

| # | Edit | Command (`S=spec.md`, `T=tasks.md`, `Q=quickstart.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'with AI off' $S` | `0` — the unreachable trigger is gone | **EXECUTED: 0** — HOLD |
| V2 | 1 | `grep -c 'How this condition arises, restated by CR-033' $S` | `1` | **EXECUTED: 1** — HOLD |
| V3 | 1 | `grep -c 'no-op forward' $S` | `≥ 1` — **the obligation survives verbatim** | **EXECUTED: 1** — HOLD |
| V4 | 1 | `grep -c 'sole\*\* guard against an unimplemented change' $S` | `1` | **EXECUTED: 1** — HOLD |
| V5 | 2 | `grep -c 'The same rule governs an injected condition' $S` | `1` | **EXECUTED: 1** — HOLD |
| V6 | 2 | `grep -c 'induced demonstration presented as an encountered one' $S` | `1` | **EXECUTED: 1** — HOLD |
| V7 | 3 | `grep -c 'injecting the empty-change-plan condition' $T` | `1` | **EXECUTED: 1** — HOLD |
| V8 | 3 | `grep -c 'label this as an injected demonstration' $T` | `1` | **EXECUTED: 1** — HOLD |
| V9 | 3 | `grep -c 'T065' $T` | `≥ 3` — the structural proof still cited | **EXECUTED: 8** — HOLD |
| V10 | 3 | `grep -c 'where a reviewer will personally meet governance' $T` | `0` — the claim the route no longer supports | **EXECUTED: 0** — HOLD |
| V11 | 4 | `grep -c 'Where you will actually see this' $Q` | `1` | **EXECUTED: 1** — HOLD |
| V12 | 4 | `grep -c 'you no longer meet this gate by accident' $Q` | `1` | **EXECUTED: 1** — HOLD |
| V13 | 5 | `grep -c 'policy-set-1.0.0' $T` | `0` | **EXECUTED: 0** — HOLD |
| V14 | 5 | `grep -c 'policy-set-1.1.0' $T` | `2` — the header and T097 | **EXECUTED: 2** — HOLD |
| V15 | 6 | `grep -c 'the set in force at that gate' $S` | `1` | **EXECUTED: 1** — HOLD |
| V16 | 6 | `grep -c 'a record of what was checked, not a statement of the current set' $S` | `1` | **EXECUTED: 1** — HOLD |
| V17 | inv | distinct `EC-\d+` in `$S` | `43` — unchanged | **EXECUTED: 43** — HOLD |
| V18 | inv | `grep -c '^- \[ \] T[0-9]' $T` | `172` actionable; 173 lines with T087 | **EXECUTED: 172 + 1** — HOLD |
| V19 | inv | `git diff --stat .specify/memory/constitution.md` | **empty** | **EXECUTED: ''** — HOLD |
| V20 | orphan | `grep -rn 'AI off' spec.md plan.md tasks.md quickstart.md` | `0` across all four | **EXECUTED: 0** — HOLD |

## Cross-record orphan check

Run over this record against the eleven applied records and the artifacts, per `CLAUDE.md` §Other governance records.

| Question | Result |
|---|---|
| Does every `OLD` string this record quotes still exist in its target? | **Asserted at application** — all six located, or the edit stops. |
| Does this record quote text another record removes first? | **No.** CR-021..CR-032 are applied; this record is the sole editor of every string it touches, and each was read from the post-package state. |
| Does any **applied** record now assert something this record falsifies? | **Yes, one, and it is corrected forward rather than edited in place.** **CR-031's** struck-versus-kept table says T141 *"still proves what it existed to prove, that executors are generic rather than input-aware"* — **true and unaffected**. But CR-031 Edit 7 changed T141's premise to *"submitted like any other run"* **without noticing that its Validate field still asserted a naturally-occurring no-plan gate**. That is the gap this record closes, and CR-031's text is **not** altered: it recorded what was decided then. |
| Do the stated application orders agree? | **Yes.** This record is the sole member of its package and applies to the state CR-021..CR-032 left. |
| Does any survival of the removed thing remain unclaimed? | **V20 is that assertion**: zero occurrences of *"AI off"* across all four artifacts after application. |

**One finding, recorded as one**: the orphan check's third question caught that CR-031 Edit 7 fixed T141's *Artifact*
field and left the dependent claim in its *Validate* field. That is the same shape as the defect it caught in CR-019
last time — a record fixing the sentence it was looking at and leaving the one that depended on it.

## Residual risk

**A reviewer no longer meets the no-plan gate by accident, and that is a real loss of the best governance moment in the
demonstration.** The old route let a reviewer invent a requirement, watch stage 7 refuse to pretend, and personally
make the decision. Injection cannot reproduce that experience; it can only prove the behaviour.

**What is not lost**: the gate itself, its three options, the negative test that a labelled no-op cannot advance, and
its status as the sole guard at stage 7. Those are proven by T065 structurally and by T141 in a run.

**The honesty cost is paid in the guide rather than hidden.** The quickstart now tells a reviewer that an ordinary
submission will not reach this gate and that the committed demonstration injects the condition. A demonstration that
implied a route which no longer exists would be a small rigged demonstration — the exact failure FR-ORC-028 and CN-010
exist to prevent, and the reason option (c) was declined rather than the reason it was attractive.
