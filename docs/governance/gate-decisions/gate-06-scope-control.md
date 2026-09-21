# Gate Decision Record — Gate 6: Scope-Control Acknowledgement

| Field | Value |
|---|---|
| Gate | Scope-control acknowledgement (**T007** — blocks Phase 1; no code may be written until this record exists) |
| Outcome | **APPROVED, with one condition** — the AI-reduction option is **struck** from the End-Day-2-AM checkpoint |
| Deciding human | Pravallika Veeravalli |
| Decision date | 2026-09-21 |
| Artifact approved | The six Phase 0 registers in `docs/delivery/` — `scope-register.md`, `backlog.md`, `milestones.md`, `critical-path.md`, `checkpoints.md`, `stop-conditions.md` — as the **operative controls** for the build |
| Governing constitution | v1.1.0 · **Policy set**: `policy-set-1.1.0` |
| Recorded in | This record; `plan.md` §14 and `docs/delivery/checkpoints.md` via **CR-034**; the commit landing this record with the condition's edits |
| Provenance of the text | **The proposal text was the supervisor's; the decision and its adoption are hers.** Recorded explicitly, as this project has recorded it throughout |

## Reason / verification performed

The gate was presented with the four acknowledgements spelled out, and the owner approved it.

**Her decision, verbatim:**

> **Go ahead**

Given in response to a presentation that stated the four things she was acknowledging:

1. **Cuts come only from `backlog.md`** — never from the nine slices in `scope-register.md`, never from mandatory
   validation, never from reviewer evidence.
2. **Checkpoints observe and escalate; they never cut.** A scope reduction happens only on her recorded order at a
   checkpoint.
3. **Two conditions halt non-waivably** — fabrication pressure, which not even she can waive, and an unresolved
   mandatory policy `FAIL`.
4. **The two time-shaped conditions escalate to her** rather than freezing work, per her Gate 5 time-attitude
   amendment (CR-009).

**What was actually verified before the gate was presented**, so the acknowledgement rests on checked artifacts rather
than on a claim that they exist:

- **51 acceptance criteria executed** against the six registers, drawn from T001–T006's own Validate, Done and Guard
  fields. All pass.
- **One real gap was found and closed during that validation**: `backlog.md` was missing two items T002's Done field
  names explicitly — artifact-level consumption tracking (ADR-009 §Backlog, Option B) and the full-stack Compose
  profile (ADR-012 §Backlog, Option B). The register had been built from `plan.md` §14 alone, and these two are
  ADR-deferred. Added as B5 and B6.
- **The check that hid it was also fixed.** The first validation script allowed the plan-§14 list to satisfy T002's
  Done criterion through a disjunction. It is now six separate assertions, one per named item, with no `or`. The gap
  surfaced only after that change, and it is recorded here because a gate that rests on a permissive check rests on
  nothing.
- **T001's Trace criterion** reads *"links each slice to the FRs it delivers"*, and Slice 1 delivers no FR — it
  delivers NFR-MNT-001/002 and five ADRs. The register now states that, so the one blank FR cell is answered rather
  than left to a reader's guess.

## Conditions attached to the approval

### The AI-reduction option is STRUCK from the End-Day-2-AM checkpoint

**Not offered-with-a-warning. Removed.**

The supervisor raised this at the gate presentation as a recommended condition; the owner adopted it.

**Her grounds, as recorded:**

1. **Her Gate 5 overrule already rejected trading away AI wiring.** Her words then: *"AI is the one writing the
   code."* She overruled a pre-emptive reduction on the ground that its estimate priced AI-authored work at human
   authoring speed. An option that re-offers the same trade at a checkpoint lets a rejected decision return through a
   side door.
2. **Decision J changed what the option means.** The plan's wording was *"the rest running deterministic"*. ADR-004
   Amendment 02 removed every deterministic counterpart for an AI-capable stage, so the option's true meaning became
   **"leave stages with no executor at all — parts of the orchestrator unbuilt."** That is not a scope reduction; it is
   shipping an incomplete orchestration engine, which is the graded artifact.
3. **CR-009 removed the pressure the option existed to serve.** There is no external submission deadline. The option
   was a schedule-relief valve for a schedule that does not need relieving.

**A misleading menu item is struck rather than annotated.** An option that reads as cheaper than it is will be chosen
under pressure precisely because it reads cheap — and the annotation would be read last, if at all.

**What remains available at End Day 2 AM**: the checkpoint still observes Slice 4 against its milestone, still records
the position honestly, and still escalates. It escalates **without a pre-drawn reduction option**, because none of the
options in `backlog.md` help with an incomplete orchestration model and inventing one under time pressure is the
improvisation this register set exists to prevent. If Slice 4 is late, the escalation says so and asks — and stop
condition 3's rule stands: **the options offered exclude abandoning the orchestration model.**

## Carried-forward enforcement points

| Item | Due at | Enforced by |
|---|---|---|
| **Fabrication-pressure halt** — non-waivable, no exception path, **not waivable by the owner either** | **Armed continuously**, from this gate to release readiness | `stop-conditions.md` condition 1; Constitution Principle X; CN-005. Includes the induced-condition form: an injected condition presented as encountered is fabrication by omission (AS-007 as extended by CR-033) |
| **Mandatory policy `FAIL` block** — non-waivable | Slice 9, release readiness | `stop-conditions.md` condition 2; Constitution VI and XI. All twelve checks in `policy-set-1.1.0` are mandatory; there is no advisory tier to demote a check into |
| **Engine-core-late condition ESCALATES, never halts** | End of Day 2 | `stop-conditions.md` condition 3. Options offered exclude abandoning the orchestration model. **With this gate's condition, they also exclude reducing AI stages** |
| **Timebox-exhausted condition ESCALATES, never cuts** | End of the box | `stop-conditions.md` condition 4. The agent does not pre-empt a scope decision that is hers |
| **A cut happens only on her recorded order at a checkpoint** | Each of the four checkpoints | `checkpoints.md` §The distinction this file exists to state; CR-009 |
| **Options come from `backlog.md` and nowhere else** | Each of the four checkpoints | `checkpoints.md` §Where options come from. Inventing an option under pressure is the improvisation the registers prevent |
| **`failure_event` capture precedes Slice 8** | Before the three scenarios run | `critical-path.md`. Otherwise the scenarios produce no MTTR population, and back-filling it would be fabrication rather than a shortcut |
| **The baseline-omissions entry precedes Slice 3's acceptance sweep** | Slice 3 | `scope-register.md`; T055a before T057. Otherwise the sweep documents a gap with no disclosure behind it |
| **A recorded *partial* is legal; a silent one is not** | Slice 3 through release readiness | CR-024, her ruling of 2026-09-20. FR-URL-016 reads *partial* until the brownfield run closes it, and if that run does not happen, release readiness reports a real gap |
| **Every [GATE] task holds for the human** | Each `[GATE]` task in `tasks.md` | Constitution III. Silence does not satisfy a gate, and it did not satisfy this one |

## What this gate does not do

It does not approve the implementation, any architecture, or any deviation from the approved artifacts. It acknowledges
that the six registers are the controls **against which later decisions will be made**, and it arms the enforcement
points above. Phase 1 begins only after this record is committed.
