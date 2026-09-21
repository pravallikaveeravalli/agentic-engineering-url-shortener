# Change Request CR-021 — The Actor-Authority Claim Qualified to Match the Design; No Mechanism Built

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 16 rows HOLD.** Five expectations were corrected in place with the kind of defect stated (two line-wrap, two phrasing, one count); no artifact edit was wrong. |
| Raised by | Pre-implementation Principal Engineer review, critical finding **C1** |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-20** |
| Affected approved artifacts | `spec.md` (FR-ORC-021, NFR-AUT-001, CN-012 residual note); `tasks.md` (T063, T147); `plan.md` §1 actor table (**Edit 6, found by the orphan check**) |
| Governing constitution | v1.1.0 — **not amended**. `POL-CHG-001` |
| Source decision | Owner ruling, 2026-09-20 |
| Application order | **First on every artifact it touches.** **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |

## Owner approval

Owner direction on the pre-implementation review, finding **C1**, verbatim:

> *"I don’t think we should solve this problem at all… it is ok if agents act as humans, I don’t think that is
> easily solvable at this point and especially in this assessment."*

**Approved as a qualified claim with no mechanism built.** The constitution is not touched. What this record adds is
honesty about the limit, plus the one control that is free: an architecture assertion that **no executor package
references the gate-decision path**.

## The finding

The review put it plainly. The project's most-repeated governance claim is that an agent cannot approve a gate at any
autonomy setting. The interface that accepts gate decisions is **unauthenticated by design** (CN-012), and the field
that distinguishes actors is:

```yaml
actorType:
  type: string
  enum: [human]
```

**Caller-supplied, single-valued, on an endpoint with `security: []`.** Anything that can reach the port submits
`actorType: human` and the gate is satisfied. T063's test — *"agent-attempted approval refused"* — can only pass
against an agent that volunteers its own identity. The constitution's own words are that a gate satisfiable by the
governed party's testimony is not a gate, and the review's judgement was that this is that gate.

The hole was created by a later decision rather than present from the start: removing credentials from governance
surfaces (CN-012) removed the only thing that could have distinguished actors, and no record noticed the interaction.

## Owner decision recorded — verbatim

> I don't think we should solve this problem at all… it is ok if agents act as humans, I don't think that is easily
> solvable at this point and especially in this assessment.

The owner then **verified the constitution's actual wording herself** before setting scope. The resulting scope is
narrow and deliberate:

| Element | Disposition |
|---|---|
| **The constitution** | **NOT TOUCHED.** Principle III's policy wording stands. It states what the project's policy is, and in this project **every gate is in fact decided by the human owner** — the policy is not violated by the absence of a verification mechanism |
| **FR-ORC-021's reject clause** | **Reworded** — it currently promises a property the design does not deliver |
| **NFR-AUT-001** | **Reworded** — its *"Verifiable: refusal test per human-owned action class"* promises verification that cannot exist |
| **T063** | **Re-scoped** to what it honestly proves |
| **The residual-risk note** | **Connected** to this limitation, which it describes without naming the consequence |
| **`docs/LIMITATIONS.md`** | **Gains the line** |

**The alternative was presented and declined.** An operator-script-issued one-time token per gate decision — the same
trust boundary as creator provisioning, no new credential class, perhaps thirty lines — was put to the owner. She
declined it on the ground quoted above: verified actor identity is not the problem this assessment is for, and a
token that an agent with shell access could also mint would move the boundary without closing it. **Recorded as
declined, not as unconsidered.**

## Applied edits

### Edit 1 — FR-ORC-021 reject clause states what is true (`spec.md:851-856`)

**OLD**

```
  **Reject (negative)**: an agent MUST NOT take a human-owned action (Constitution III) under any
  autonomy setting.
  **Evidence**: autonomy limit definitions; refusal records.
```

**NEW**

```
  **Reject (negative)**: **no step in the workflow may submit, satisfy, or advance a human-owned decision** — no
  stage executor, deterministic or AI, has a code path that records a gate outcome, and an executor acting outside
  its provided effect channels is refused and recorded as an autonomy violation (FR-ORC-016).
  **Declared limitation — actor identity is asserted, not verified.** The `actorType` on a submitted gate decision is
  **declared by the caller and not authenticated**: governance surfaces carry no credential by design (CN-012), so
  enforcement rests on the **machine-access trust boundary** — the ability to reach the process. Anything that can
  reach the port can submit a decision asserting it is human. **Verified actor identity is out of scope for this
  demonstration** (owner decision, 2026-09-20; an operator-issued per-decision token was considered and declined).
  What this requirement therefore delivers is that **the system never approves its own gates**, not that an
  impersonating caller is detectable.
  **Evidence**: autonomy limit definitions; refusal records; an architecture assertion that **no executor package
  references the gate-decision path**; the limitation disclosed in `docs/LIMITATIONS.md`.
```

*Ground: the honest claim is strictly narrower and still substantive. "No workflow step approves anything" is
mechanically checkable and is the property that matters for an orchestration system. "No agent can ever impersonate a
human" was never checkable here and should not have been written.*

### Edit 2 — NFR-AUT-001 (`spec.md:1283-1284`)

**OLD**

```
- **NFR-AUT-001** — Autonomy limits are declared per stage, and human-owned actions cannot be
  taken by an agent at any setting. *Verifiable: refusal test per human-owned action class.*
```

**NEW**

```
- **NFR-AUT-001** — Autonomy limits are declared per stage, and **no workflow step submits or satisfies a
  human-owned decision**. Actor identity on a submitted decision is **declared, not verified** (FR-ORC-021's declared
  limitation, CN-012): an impersonating caller with process access is not detectable, and verified actor identity is
  out of scope for this demonstration. *Verifiable: per-stage autonomy declarations; an architecture test asserting no
  executor package references the gate-decision path; a refusal test for a self-identified agent actor. **Not
  verifiable, and not claimed**: that a caller asserting `actorType: human` is in fact human.*
```

### Edit 3 — the residual-risk note names its consequence (`spec.md`, CN-012)

**OLD**

```
  port can drive the orchestrator**, which is a localhost-demonstration posture and is stated as such in
  `docs/LIMITATIONS.md`.
```

**NEW**

```
  port can drive the orchestrator**, which is a localhost-demonstration posture and is stated as such in
  `docs/LIMITATIONS.md`. **The consequence this note previously left unstated**: because gate decisions are submitted
  over such a surface, the `actorType` field is a **declaration rather than a verified fact**, so the governance claim
  this project can defend is that *no workflow step approves anything* — not that impersonation is prevented
  (FR-ORC-021's declared limitation, NFR-AUT-001). The two statements were written in different places and never
  connected; the review connected them.
```

### Edit 4 — T063 re-scoped to what it proves (`tasks.md:501-505`)

**OLD** — the whole T063 block.

**NEW**

```
- [ ] T063 [P] [US2] Actor-authority check — `src/main/java/agentic/shortener/orchestration/gates/ActorAuthority.java`
  - **Req**: FR-ORC-013, FR-ORC-021, NFR-AUT-001, **CN-012** · **Scn**: — · **ADR**: ADR-004 · **Pre**: T058
  - **Deps**: T058 · **Par**: yes · **Artifact**: a decision from an actor without recorded authority for that gate class takes no effect; `APPROVED` structurally requires `actorType: human`; the `system` actor is accepted **only** for deadline expiry and retention-driven abandonment, neither of which is an approval
  - **TDD**: RED-FIRST · **Validate**: **EC-024** — a decision from an actor with no recorded authority for the gate class takes no effect; a **self-identified** agent actor attempting an approving outcome is refused and recorded as an autonomy violation; the `system` actor is refused for every approving outcome; and an **architecture assertion that no executor package — deterministic, AI, or fake — references the gate-decision path at all**
  - **Docs**: `docs/LIMITATIONS.md` actor-identity limitation · **Trace**: matrix FR-ORC-013, EC-024, CN-012
  - **Guard**: **what this task proves and what it cannot, stated so the evidence is not over-read.** It proves that **no step in the workflow submits or satisfies a gate decision** — the architecture assertion is the real control, because it removes the code path rather than checking a field. It **does not** prove that a caller asserting `actorType: human` is human: the surface is unauthenticated by design (CN-012) and the field is a declaration, so an impersonating caller with process access is undetectable. Verified actor identity is **out of scope by owner decision, 2026-09-20**; an operator-issued per-decision token was considered and declined · **Done**: EC-024 refused; self-identified agent refused; `system` refused for approvals; **no executor package references the gate-decision path** · **Approval**: none
```

*Ground for the architecture assertion replacing the field check as the primary control: a field check tests what a
caller says; an architecture rule removes the caller. It is the only assertion in this area that cannot be defeated
by a caller choosing different words.*

### Edit 5 — T147 gains the disclosure

Appended to T147's Artifact list:

```
; the **actor-identity limitation** — governance surfaces are unauthenticated by design, `actorType` is declared and
not verified, so the defensible claim is that no workflow step approves anything rather than that impersonation is
prevented; verified actor identity is out of scope by owner decision and an operator-issued per-decision token was
considered and declined
```

### Edit 6 — `plan.md` §1 actor table carries the same over-claim — **found by the cross-record orphan check**

The orphan check on this package asked which other artifacts assert the property CR-021 narrows. One does:

**OLD** `| Reviewer / Approver | Human | Decide gates. Cannot be substituted by an agent at any autonomy setting. Records decisions through the governance surface under the machine-access boundary; holds **no** creator credential (CN-012). |`

**NEW** `| Reviewer / Approver | Human | Decide gates. **No workflow step submits or satisfies a gate decision** — the control is that the code path does not exist, asserted by T063's architecture rule. Actor identity on a submitted decision is **declared, not verified** (FR-ORC-021's declared limitation): an impersonating caller with process access is not detectable, and verified actor identity is out of scope for this demonstration. Records decisions through the governance surface under the machine-access boundary; holds **no** creator credential (CN-012). |`

**Why this edit exists and why it was nearly missed.** Edits 1–5 covered the specification, the task and the
limitations document. The plan's actor table says the same thing in different words — *"Cannot be substituted by an
agent at any autonomy setting"* — and no verification row in this record would have caught it, because every row was
scoped to the artifacts the record already knew it was touching.

**This is the second time in this project that a claim survived in an artifact nobody thought to look at** (the first
was the Phase-0 research record, CR-019). It is the specific failure the new cross-record orphan discipline exists to
catch, and it caught it here **before** the package was submitted rather than two analysis runs later.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR, and it is a *narrowing* of two obligations** — the honest direction, and the direction that needs saying out loud rather than being absorbed. FR-ORC-021 and NFR-AUT-001 both promise less than before and both gain a mechanically checkable assertion they previously lacked. |
| **Backward-compatibility impact** | None. Nothing implemented. |
| **Affected consumers** | `tasks.md` T063, T147. `contracts/openapi.yaml` needs **no change** — the field and the description are already accurate about what they are; it was the specification that over-claimed. |
| **Affected tests** | **New**: an architecture assertion that no executor package references the gate-decision path. **Narrowed**: T063's refusal test is scoped to a self-identified agent, which is what it could ever have tested. |
| **Affected documentation** | `docs/LIMITATIONS.md` (T147). |
| **Rollout / migration** | None. |
| **Required approval** | Human owner. This **reduces a non-waivable principle's apparent coverage in the specification**, so it must be her explicit decision and not an editorial tidy. It does not amend the constitution, and the distinction matters: the policy stands, the specification stops claiming a verification that does not exist. |

## Post-application verification

Absence checks scoped to binding positions.

| # | Edit | Verification command (`S=specs/001-agentic-sdlc-url-shortener/spec.md`, `T=…/tasks.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'an agent MUST NOT take a human-owned action' $S` | `0` — the unverifiable promise is gone from FR-ORC-021 | **EXECUTED: 0** — HOLD |
| V2 | 1 | `grep -c 'declared, not verified' $S` | `≥ 2` — FR-ORC-021 and NFR-AUT-001 | **EXECUTED: 1 for this exact phrase, 3 for the property** — HOLD. *Expectation defect (phrasing):* FR-ORC-021 says *"asserted, not verified"*, NFR-AUT-001 *"declared, not verified"*, CN-012 *"declaration rather than a verified fact"*. Three sites state the limitation; only one uses the row’s exact words. |
| V3 | 1 | `grep -c 'no step in the workflow' $S` | `≥ 1` | **EXECUTED: 1** — HOLD |
| V4 | 1, 2 | `grep -c 'out of scope for this demonstration' $S` | `≥ 2` | **EXECUTED: 1 contiguous, 2 unwrapped** — HOLD. *Expectation defect (line wrap):* FR-ORC-021 wraps the phrase across two lines, so a contiguous grep finds one of the two. |
| V5 | 1 | `grep -c 'considered and declined' $S` | `≥ 1` — the token alternative on the record | **EXECUTED: 1** — HOLD |
| V6 | 2 | `grep -c 'Not\*\* verifiable, and not claimed' $S` | `1` — the negative statement is explicit | **EXECUTED: 0 contiguous, 1 unwrapped** — HOLD. *Expectation defect (line wrap):* the applied text breaks after `**Not**`. |
| V7 | 3 | `grep -c 'previously left unstated' $S` | `1` | **EXECUTED: 1** — HOLD |
| V8 | 4 | `grep -c 'no executor package references the gate-decision path' $T` | `≥ 2` — Validate and Done | **EXECUTED: 1 exact, 2 on the stem `references the gate-decision path`** — HOLD. *Expectation defect (phrasing):* T063’s Validate interjects *"— deterministic, AI, or fake —"* inside the phrase; Done carries it whole. Both positions present, which is what the row intended. |
| V9 | 4 | `grep -c 'an agent may not approve at any autonomy setting' $T` | `0` — the over-claim is gone from T063's guard | **EXECUTED: 0** — HOLD |
| V10 | 5 | `grep -c 'actor-identity limitation' $T` | `1` | **EXECUTED: 2** — HOLD. *Expectation defect (count):* T063’s Docs field **and** T147’s entry, both intended; the row said 1. |
| V11 | invariant | constitution file unchanged: `git diff --stat .specify/memory/constitution.md` | **empty** — the constitution is not touched | **EXECUTED: ''** — HOLD. the constitution is untouched |
| V12 | invariant | requirement count in `$S` | `51` — unchanged | **EXECUTED: 51** — HOLD |
| V13 | invariant | `grep -c 'actorType' specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml` | `≥ 1` — the contract is deliberately unchanged | **EXECUTED: 2** — HOLD |
| V14 | 6 | `grep -c 'Cannot be substituted by an agent at any autonomy setting' plan.md` | `0` — the plan's duplicate over-claim is gone | **EXECUTED: 0** — HOLD |
| V15 | 6 | `grep -c 'declared, not verified' plan.md` | `1` | **EXECUTED: 1** — HOLD |
| V16 | orphan check | `grep -rc 'at any autonomy setting' spec.md plan.md tasks.md` | `0` in all three — **the claim is narrowed everywhere it appeared, not just where this record started looking** | **EXECUTED: 0** — HOLD. zero across all three artifacts |

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V11 is the one that matters most** — the owner's scope was explicit that the constitution is not touched,
and an empty diff is the only proof of that.

## Residual risk — stated, because this record's whole purpose is honesty about it

**The gap is real and now it is disclosed rather than closed.** A reviewer who reads FR-ORC-021 after this change
learns that an impersonating caller with process access is undetectable. That is a weaker submission than one with a
token mechanism — and a **much** stronger one than a submission that claims a control it does not have, which is what
the review found.

The compensating facts, none of them a substitute: every gate in this project is in fact decided by the human owner;
the workflow contains no code path that submits a decision, which the new architecture assertion proves rather than
asserts; the surface is localhost-only; and the limitation is named in the specification, the task, and the
limitations document rather than discoverable only by reading a contract enum.
