# ADR-004 Amendment 02 — Orchestration Always Uses AI: the Keyless Mode and the Run-Level Switch Are Removed

**Successor record to [ADR-004](./ADR-004-ai-provider-and-autonomy-bounding.md)**, which is Accepted and is **not
edited in place**. This record supersedes two elements of its decision content. **[Amendment 01](./ADR-004-amendment-01-transport-claude-code-cli.md)
— the Claude Code CLI transport — is untouched and remains in force.**

| Field | Value |
|---|---|
| Amends | `ADR-004` (Accepted 2026-09-20, Gate 4): the keyless default, the run-level flag, and deterministic counterparts as first-class requirements |
| Does **not** amend | `ADR-004-A1` (CLI transport). The provider, the owned interface, the pinned model recorded from the response, and the argv-not-shell rule all stand |
| Status | **ACCEPTED** — 2026-09-20, by recorded decision of the human owner. Status set by her decision, never by the acting agent. |
| Deciding human | Pravallika Veeravalli (human owner) |
| Recorded in | This record; `spec.md`, contracts, `plan.md`, `quickstart.md`, `tasks.md` via **CR-028..CR-031**; the recast **CR-025**; the fallback consequence via **CR-032** (Decision K) |
| Governing constitution | v1.1.0 — **not amended** |

## Decision

**Orchestration always uses AI.** There is no keyless mode, no run-level `ai` switch, and no deterministic counterpart
engine for any AI-capable stage. A fresh orchestration run requires an authenticated Claude Code CLI on the machine.

## The owner's premise, verbatim

> we don't need the non-AI mode anymore because… reviewers are not going to run it. They're just going to look at the
> old run information from the ADRs and other files that we are pushing into GitHub… I don't think they would need to
> run the same requirement again.

## The counter-case, put to her explicitly and accepted

This is recorded at length because it is the strongest argument against the decision and she chose against it knowingly.

1. **The assignment's first deliverable is a prototype that is "runnable end-to-end", with instructions validated from
   a fresh checkout.** Removing the keyless path means a reviewer cannot run the orchestrator at all without
   installing and authenticating a specific commercial CLI. The shortener and the full test suite still run with
   nothing; the *orchestrator* does not.
2. **The switch was the cleanest demonstrated autonomy boundary in the design.** A run-level flag that gates whether
   AI participates, defaulting to off, with per-execution labels proving which ran, was a concrete, inspectable
   answer to "how is agent autonomy bounded". That specific answer is now gone.
3. **It weakens a claim already made and approved.** SC-014 and CN-011 were Gate 2 conditions. Retiring them is a
   narrowing of approved scope, not a clarification.

**Her decision after hearing all three: *"do it."***

## The honest ground in the decision's favour

**Neither the assignment nor the interviewer guide ever asked for a no-AI runtime mode.** It was our invention — a
design we added to make the system reviewable without credentials, then wrote three requirements and a success
criterion around. The counter-case above is real, but the first bullet is the only one grounded in a stated
requirement, and even that asks for a runnable prototype rather than a credential-free one.

An honest single AI-backed path with committed evidence is defensible. Two paths where one exists mainly to be
claimed is worse than one path described accurately.

## What is struck

| Element | Where it lived |
|---|---|
| Run-level flag `ai: on \| off`, default `off` | `spec.md` FR-ORC-029 and §Stage Executor Model; both contracts; `plan.md`; `quickstart.md`; `tasks.md` T018 |
| **CN-011** — reviewer-default path runnable with no AI key | `spec.md` §Constraints |
| **SC-014** — complete system runs end to end with no AI key and no network | `spec.md` §Success Criteria |
| **NFR-AUT-004** — keyless, network-free reviewer-default path | `spec.md` §NFR |
| Deterministic counterparts for the **six** AI-capable stages (S2, S3, S5, S6, S7, S9) | `plan.md` §3 Fallback column; `tasks.md` T071 |
| "First-class requirement, not a degraded mode" framing for those counterparts | ADR-004 §Decision |

## What survives — stated explicitly so nothing is over-deleted

| Element | Status |
|---|---|
| **Per-stage executor-kind labelling** `DETERMINISTIC` / `AI` / `HUMAN` as a **runtime outcome** | **Survives.** The five genuinely deterministic stages (S1, S8, S10, S11, S12) keep their real engines and their `DETERMINISTIC` labels. The label records what ran; it was never the flag |
| **NFR-AUT-003** — every execution labelled, deterministic never presented as AI work | **Survives unchanged.** More load-bearing now, not less |
| **Injected scriptable fakes** in the test suite (T072) | **Survives.** Tests never call live AI — that was FR-ORC-030's point and it is independent of the runtime flag |
| **The stage-7 no-plan gate** and the `HUMAN` executor kind | **Survives.** `HUMAN` was never flag-selectable; it is a gate outcome |
| **ADR-004-A1** — CLI transport, argv-not-shell, pinned model read from the response | **Untouched** |
| **FR-ORC-030** — reliability proofs driven by fakes, never the live provider | **Survives, and is now the only thing standing between the reliability suite and a credential requirement** |

## The consequence the owner's scope did not name — **and it needs her decision**

**Striking the six deterministic counterparts leaves the system with zero declared fallbacks.**

`plan.md` §3's Fallback column has exactly twelve entries. **Six are the deterministic counterparts being struck.**
The other six read *"None — refuse"*, *"None — suspension only"*, *"None — real results only"*, *"None — verdicts must
be repeatable"*, *"None — never invent content"*.

So after this amendment:

- **FR-ORC-015** (*"Stages that can degrade MUST declare fallback behavior"*) is satisfied by no stage — it becomes
  vacuous rather than violated.
- **T087** (declared fallback handler) has nothing to handle; **EC-023** (a failing fallback escalates to safe-stop) is
  unreachable.
- **ADR-004-A1's risk mitigation dies**: *"CLI absent or unauthenticated → bounded retry → fallback to the
  deterministic counterpart, stamped `DETERMINISTIC`. AI mode degrades; it never fails the run."* It now fails the
  run, or rather suspends it.
- **T073's third assertion** — *"CLI unavailable → fallback stamped `DETERMINISTIC`, run not failed"* — must be
  rewritten.
- The MTTR `recovery_mechanism` enum's `fallback` value becomes unexercisable, and **T116 requires each of its six
  mechanisms to be exercised**.
- **Fallback is one of the five reliability behaviours the assessment names explicitly** — retries, fallback,
  rollback, compensation, safe-stop. The design would retain four.

### RESOLVED — owner **Decision K**, 2026-09-20: **option (a)**

**Her answer, verbatim: "A".** Given after the collision was put to her explicitly — the assignment names fallback
among its required reliability controls, and a reviewer using the assignment as a checklist will mark the behaviour
absent. She chose knowing that.

**FR-ORC-015 is retired**, with EC-023, T087, and the `fallback` value in the MTTR recovery-mechanism enum — all
**retired in place**, no numbering gaps, reasons attached. **Bounded retry then safe suspension is the entire
degradation story.** `docs/LIMITATIONS.md` carries the absence as a named entry stating what the assignment asks, what
is demonstrated instead, and why. Traceability reflects a recorded retirement rather than a silent hole. Carried by
**CR-032**.

**Why not (b) or (c), in her terms**: the original fallback design was machinery no document asked for, and Decision J
removed it. A token counterpart kept only to make fallback claimable is a **ceremonial presence** — the
exists-to-be-claimed defect this project rejects by name (FR-ORC-028, CN-010). A re-pointed genuine degradation was
declined as new scope. **A documented honest absence over a ceremonial presence.**

**The three options as they were put to her, retained unaltered so the choice is readable:**

**(a) Accept the loss.** Retire FR-ORC-015 as not-applicable with the reasoning recorded; strike T087 and EC-023;
remove `fallback` from the recovery-mechanism enum. *For*: honest, no invented machinery, and a system with one real
path has nothing to fall back to by construction. *Against*: drops an explicitly assessed behaviour, and a reviewer
comparing the assessment's five named behaviours against four delivered will ask.

**(b) Keep exactly one deterministic counterpart** as the declared fallback for one AI stage — S2 normalization is the
cheapest, being a structural transformation. *For*: preserves the behaviour, the test, the edge case and the MTTR
mechanism for roughly one task's work. *Against*: one counterpart existing only to keep a behaviour demonstrable is
the "exists mainly to be claimed" problem this amendment's own reasoning objects to.

**(c) Re-point fallback at something that genuinely degrades.** Candidate: S10's dependency-vulnerability scan falling
back to a committed, dated advisory snapshot when the live feed is unavailable — a real degradation with a real
correctness cost that must be labelled. *For*: fallback becomes a genuine engineering case rather than a mode switch,
and it is arguably a better demonstration than the counterparts were. *Against*: new scope, and it requires the
snapshot to exist.

## Disclosure (required under option (a) as chosen, and under any other)

Plainly, and where a reviewer looks first — the setup instructions and the reviewer guide:

> **A fresh orchestration run requires an authenticated Claude Code CLI on the machine.** There is no keyless mode.
>
> **You do not need it to review this submission.** The committed scenario evidence is complete and readable end to
> end with no AI setup: run exports, per-node executor-kind labels, the pinned model id, gate decisions, retry
> rulings, replan events and test results are all in the repository.
>
> **The URL shortener and the entire test suite run with no AI and no network.** Orchestration reliability proofs use
> injected fakes (FR-ORC-030), so the test suite never calls a live provider.

## Reversibility

**Moderate, and lower than ADR-004's original assessment.** Restoring a keyless mode means rebuilding six counterpart
engines and reinstating three requirements and a success criterion. The provider interface still makes *provider*
swaps cheap — that is what ADR-004 assessed as High — but the *mode* is not behind that seam, and this amendment
removes it rather than swapping it.

**Decision K lowers it further.** Restoring fallback now means reinstating a retired requirement, a retired edge case,
a retired task and an enum value — and, because T014 will assert that no `FallbackHandler` type exists, the
reinstatement cannot happen quietly. That assertion is deliberate: it makes the retirement enforced rather than merely
recorded.

## Traceability

- **Requirements retired in place**: CN-011, SC-014, NFR-AUT-004 (this amendment, via CR-028); **FR-ORC-015 and
  EC-023** (Decision K, via CR-032). Amended: FR-ORC-029 (flag removed), FR-ORC-031 (fallback clause replaced by the
  no-plan gate).
- **Requirements surviving and now load-bearing**: NFR-AUT-003, FR-ORC-030.
- **ADR**: amends ADR-004; ADR-004-A1 untouched; ADR-011 (testing) unaffected because fakes were always the
  reliability mechanism.
- **Change control**: **CR-028** (spec), **CR-029** (contracts), **CR-030** (plan, quickstart, data model, research),
  **CR-031** (tasks), **CR-025 recast**, **CR-032** (fallback retirement, Decision K).

## Validation

1. No `ai` flag appears in either contract, the specification, the plan, the quickstart or the tasks — asserted by the
   package's orphan check, not by memory.
2. The five deterministic stages keep real engines and `DETERMINISTIC` labels; an execution with no label still fails
   (NFR-AUT-003).
3. The test suite passes with no AI credential and no network (FR-ORC-030) — **this is the only keyless claim that
   survives, and it survives because it was never about the flag**.
4. The disclosure appears in the setup instructions and the reviewer guide before any run instruction.
