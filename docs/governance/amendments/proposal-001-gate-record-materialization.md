# Constitution Amendment Proposal 001 — Gate Record Materialization

| Field | Value |
|---|---|
| Status | **ACCEPTED** 2026-09-18 — all six sub-decisions approved. Decision record: `docs/governance/gate-decisions/amendment-001-decision.md`. Applied as constitution v1.1.0. |
| Raised by | Pravallika Veeravalli (candidate, human owner) |
| Drafted | 2026-09-18 |
| Affects | Constitution v1.0.0 (ratified 2026-09-17) |
| Proposed version | **1.1.0 (MINOR)** |
| Applied | **No.** The constitution file is unmodified. No version bump has been made. |

This proposal is itself materialized as a repository artifact rather than delivered only in
conversation, because delivering it conversationally would reproduce the defect it describes.

---

## 1. The gap being closed

Constitution v1.0.0 contains two clauses that are individually sound and jointly insufficient:

> **Gate semantics**. Gate outcomes are those defined in Principle III. A gate record MUST capture
> the outcome, the deciding human, the timestamp, and the reason.

> - **Repository as source of truth**: Approvals, decisions, and evidence count only when recorded
>   in the repository. Statements made only in conversation are not artifacts and MUST NOT be cited
>   as approval.

The first says what a gate record must *contain*. The second says conversation is not an artifact.
Neither says **where** a gate record lives, **who** creates it, or **by when** relative to the work
the decision authorizes. A literal reading of §Gate semantics is satisfied by a conversational
message carrying four fields, which §Repository as source of truth then declares non-citable — and
no clause in v1.0.0 resolves the tension or assigns the duty to close it.

**Observed consequence.** Gate 1's reasoned decision initially existed only in conversation and in
the candidate's git-ignored working log. The repository record
(`docs/governance/gate-decisions/gate-01-constitution.md`, commit `c347606`) was materialized after
the fact, and only because the human owner asked where her decision had been logged. Note carefully:
**no clause of v1.0.0 was violated by that delay**, which is precisely the finding. The obligation
was not late — it did not exist.

**Compounding consequence.** The process convention subsequently issued to the agent in
conversation on 2026-09-18 is, by the constitution's own standard, a non-artifactual rule. It binds
nothing, survives no session reset on any authoritative basis, and cannot be cited by a reviewer.
The agent's local memory entry is likewise a convenience, not authority. This proposal closes that
loop too (Edit 2, second sentence).

**Related gap surfaced while drafting** (flagged, not amended here): §Amendment procedure requires
"a recorded change entry", but the constitution defines no location for one and contains no
amendment-history section. §5 proposes the entry line; the human owner must also decide where
change entries live. Recommendation in §5.

---

## 2. Proposed clause edits

Four edits. Edits 1–3 are the core of the request. Edit 4 is recommended but separable, so the
human owner can approve a minimal surface if preferred.

### Edit 1 — §Development Workflow and Quality Gates → **Gate semantics** (core)

**Current text:**

> **Gate semantics**. Gate outcomes are those defined in Principle III. A gate record MUST capture
> the outcome, the deciding human, the timestamp, and the reason. `CHANGES-REQUESTED` returns work
> to the owning stage with the requested changes enumerated. `ESCALATED` records what decision
> exceeds the current owner's authority.

**Proposed text** (additions are the final four sentences; existing text unchanged):

> **Gate semantics**. Gate outcomes are those defined in Principle III. A gate record MUST capture
> the outcome, the deciding human, the timestamp, and the reason. `CHANGES-REQUESTED` returns work
> to the owning stage with the requested changes enumerated. `ESCALATED` records what decision
> exceeds the current owner's authority. A gate record MUST be materialized as a repository
> artifact; a conversational statement of the decision does not satisfy this requirement. The agent
> acting on the decision is responsible for materializing the record, and MUST NOT begin the
> substantive work the decision authorizes before that record exists in the working tree. The
> record MUST be committed no later than the commit that acts on the decision; where the decision
> approves no artifact, it MUST be committed immediately in its own governance commit. The location
> and structure of gate records are defined in the operational guidance referenced under §Runtime
> guidance, which MUST define them; silence or contradiction there is itself a compliance failure.

**Note on the ordering rule.** Materializing the record is not itself "work the decision
authorizes", so the clause cannot deadlock. The sequence it mandates is: decision received → record
written to working tree → authorized work performed → both committed together.

### Edit 2 — §Assessment Scope and Evidence Standards → **Repository as source of truth** (core)

**Current text:**

> - **Repository as source of truth**: Approvals, decisions, and evidence count only when recorded
>   in the repository. Statements made only in conversation are not artifacts and MUST NOT be cited
>   as approval.

**Proposed text** (additions are the final two sentences):

> - **Repository as source of truth**: Approvals, decisions, and evidence count only when recorded
>   in the repository. Statements made only in conversation are not artifacts and MUST NOT be cited
>   as approval. A decision stated in conversation becomes citable only once materialized under
>   §Development Workflow and Quality Gates → Gate semantics. Process rules, conventions, and
>   standing instructions issued only in conversation carry no authority; to bind future work they
>   MUST be recorded in this constitution or in the operational guidance under §Runtime guidance.

The second added sentence is what makes the current conversational convention — and any future one
— either authoritative or explicitly void, rather than ambiguously binding.

### Edit 3 — §Governance → **Runtime guidance** (core)

**Current text:**

> **Runtime guidance**. This constitution states obligations. Operational, repository-specific
> development guidance is maintained separately in `CLAUDE.md` and MUST NOT contradict this
> document; where it does, this document prevails.

**Proposed text** (additions are the final two sentences):

> **Runtime guidance**. This constitution states obligations. Operational, repository-specific
> development guidance is maintained separately in `CLAUDE.md` and MUST NOT contradict this
> document; where it does, this document prevails. `CLAUDE.md` MUST define the gate record location
> convention and record structure required by §Gate semantics. Operational guidance MAY be revised
> without a constitutional amendment, but MUST NOT be used to weaken, delay, or condition an
> obligation stated here.

### Edit 4 — §Governance → **Non-compliance blocks release readiness**, condition 3 (recommended, separable)

**Current text:**

> 3. A mandatory human gate lacks a recorded outcome.

**Proposed text:**

> 3. A mandatory human gate lacks a recorded outcome, or its outcome is not materialized as a
>    repository artifact.

**Why recommended.** Without it the new obligation has no terminal enforcement point: a submission
could reach release readiness with every gate decided conversationally and condition 3 still
reading as satisfied. With it, the gap is caught at the last gate even if missed at every earlier
one. **Why separable.** It tightens release readiness, which is the human owner's own gate; the
owner may reasonably prefer to approve Edits 1–3 and leave readiness criteria untouched.

---

## 3. Version classification: MINOR (1.0.0 → 1.1.0)

The constitution's own definitions:

> - **MINOR** — a principle or section is added, or existing guidance is materially expanded or
>   tightened.
> - **PATCH** — clarification, wording, formatting, or typo correction with no change in obligation.

**Classification: MINOR.**

**The case for PATCH, stated fairly.** One could argue the obligation already existed as the logical
conjunction of §Gate semantics and §Repository as source of truth — a gate record must have four
fields, and only repository records count, therefore gate records must be in the repository — making
these edits mere clarification with no new obligation.

**Why that argument fails.** Three of the added obligations are not derivable from the existing text
by any reading:

1. **A responsible actor.** v1.0.0 names no one. "The agent acting on the decision is responsible"
   is new.
2. **A deadline.** v1.0.0 imposes none. "No later than the commit that acts on the decision" is new,
   and it is the operative fix — the Gate 1 record was eventually materialized, so a
   location-only clarification would not have prevented the observed consequence.
3. **A precondition on work.** "MUST NOT begin the substantive work before the record exists" is a
   new constraint on agent behavior.

The empirical test settles it: under v1.0.0 the Gate 1 delay violated nothing. Under the proposed
text it would. An amendment that converts previously-compliant conduct into non-compliant conduct
has changed obligations, which the constitution's own PATCH definition excludes ("with no change in
obligation"). Edit 4 independently tightens release readiness, which is squarely MINOR.

**Judgment note.** Classifying this as PATCH would be the cheaper path — PATCH avoids the mandatory
re-analysis and approval re-obtainment that MINOR triggers. That is exactly the reasoning
Constitution X and §Conflict resolution warn against, and the saving is illusory here (see §4).
MINOR is both correct and nearly free at this moment.

---

## 4. Downstream impact analysis

Required by §Amendment procedure across specification, plan, ADRs, tasks, and implementation.

### Already-closed Gate 1 — remains valid and unmodified

Governed by:

> Amendments MUST NOT be applied retroactively to claim compliance for work already completed under
> a prior version; the version in force at the time of the work MUST be identifiable.

- Gate 1 was decided on 2026-09-17 under v1.0.0 and **remains valid under v1.0.0**.
- `docs/governance/gate-decisions/gate-01-constitution.md` and commit `c347606` MUST NOT be
  modified, re-dated, or re-characterized.
- The late materialization of that record **was not a violation** and MUST NOT be recorded as one.
  It was the absence of an obligation, which is the finding itself.
- Symmetrically, the amendment MUST NOT be cited to claim Gate 1 was compliant with a rule that did
  not exist when it was decided.
- Recommendation: the amendment's change entry states this explicitly, so a reviewer reading
  `gate-01` against v1.1.0 does not mistake it for a breach. Drafted into §5.

### Draft specification (`specs/001-agentic-sdlc-url-shortener/spec.md`, Draft, uncommitted)

- **No requirement is invalidated.** The amendment governs *this project's* governance process, not
  the product's requirements. The spec's Confirmed and Derived requirements are untouched.
- **One genuine product-level consequence, raised not slipped in.** Per AS-005 the orchestration
  system governs this repository's own lifecycle, so the amendment arguably entails an acceptance
  criterion on **FR-ORC-013 (Approval gates)**: that the orchestrator materialize gate records as
  repository artifacts within the commit acting on the decision, not merely record them in workflow
  state. **FR-ORC-023 (Audit inspection)** already requires the six audit fields and immutability,
  so the delta is materialization and timing only. This is proposed as a follow-up spec edit for the
  human owner's decision — it is **not** applied by this amendment, and applying it silently would
  breach §No invented requirements.
- **Cost of doing this now is zero**: the spec is Draft and uncommitted, so incorporating the
  FR-ORC-013 criterion costs one edit before Gate 2 rather than a change-controlled revision after.
- **Gate 2 is unaffected and remains open.** The three blocking clarifications (AQ-001, AQ-002,
  AQ-003) are independent of this amendment.

### Mandatory re-analysis obligation

> On any MAJOR or MINOR amendment, active features MUST re-run `/speckit-analyze`, and any
> consequently invalidated approval MUST be re-obtained.

- **Currently vacuous, deliberately.** No feature has reached Analyze; no plan, ADRs, or tasks
  exist; the only closed gate is Gate 1, whose approval this amendment cannot invalidate
  retroactively. So the re-analysis obligation costs nothing today.
- **This is the argument for amending now rather than later.** The same MINOR amendment after Plan
  would force re-analysis of a plan; after Tasks, of a task graph; after Implement, of executed
  evidence and possibly re-obtained approvals. The cost rises monotonically with every stage.

### Future stages

- **Plan stage**: its constitution check must enumerate principles against **v1.1.0**, not v1.0.0,
  and must record the policy version evaluated.
- **Every subsequent gate** (Specification approval, Clarification resolution, ADR, Task plan,
  Analysis disposition, Release readiness) carries the materialization obligation, with the
  record co-committed with the artifact it approves.
- **Release readiness**: gains condition 3's materialization test if Edit 4 is approved.

### New and superseded files

| File | Effect |
|---|---|
| `.specify/memory/constitution.md` | Edits 1–4; version → 1.1.0; Last Amended → 2026-09-18 |
| `CLAUDE.md` | **Does not currently exist** — created by this amendment (§5 content). Edit 3 makes its existence mandatory. |
| Amendment change-entry location | Undecided — see §5 recommendation |
| Agent local memory entry | Superseded by `CLAUDE.md` as authority; retained only as a convenience pointer. It is not a repository artifact and carries no authority under Edit 2. |

---

## 5. Where the operational specifics belong

**Recommendation: split them.** The obligation belongs in the constitution; the mechanics belong in
`CLAUDE.md`, which §Runtime guidance already designates.

| Concern | Home | Why |
|---|---|---|
| Records MUST be repository artifacts | Constitution | A durable governance obligation |
| Responsible actor (the acting agent) | Constitution | Assigns accountability; must not be revisable without approval |
| Deadline (no later than the acting commit) | Constitution | The operative fix; the whole point of the amendment |
| Exact path `docs/governance/gate-decisions/gate-0N-<slug>.md` | `CLAUDE.md` | A directory rename should not require a constitutional amendment and a version bump |
| Record template and section order | `CLAUDE.md` | Formatting detail; evolves with practice |

Edit 3 prevents this split from becoming an escape hatch: `CLAUDE.md` *must* define these, cannot
contradict the constitution, and cannot weaken, delay, or condition the obligation. So the mechanics
stay cheap to change while the duty stays hard to change — and because `CLAUDE.md` is a committed
repository file, it survives session reset on an authoritative basis, which the conversational
convention does not.

### Proposed `CLAUDE.md` content (replaces the conversational convention)

> ## Gate decision records
>
> Constitution §Gate semantics requires every mandatory gate decision to be materialized as a
> repository artifact. This section defines where and how.
>
> **Location**: `docs/governance/gate-decisions/gate-0N-<stage-slug>.md`, where `N` is the gate
> number and `<stage-slug>` names the stage — for example `gate-01-constitution.md`,
> `gate-02-specify.md`.
>
> **Trigger**: a formal gate decision from the human owner. Conversational assent, agreement, or
> encouragement is not a gate decision and MUST NOT produce a gate record.
>
> **Timing**: write the record to the working tree as soon as the decision is received, before
> beginning the work it authorizes. Commit it together with the artifact it approves; when it
> approves no artifact, commit it immediately on its own with a `docs(governance)` message.
>
> **Required structure**:
>
> 1. A field table carrying, at minimum: gate, outcome, deciding human, decision date, artifact
>    approved, and where else the decision is recorded.
> 2. `## Reason / verification performed` — the decision's reasoning and what was actually checked,
>    in the deciding human's terms.
> 3. `## Conditions attached to the approval` — omit only if none.
> 4. `## Carried-forward enforcement points` — an item/due-at table; omit only if none.
> 5. `## Process note` — the division of labour between human and agent for this decision.
>
> **Fidelity**: record the decision verbatim where the human's own wording carries the reasoning.
> Do not summarize away conditions, and do not add conditions the human did not state.
>
> **Immutability**: a filed gate record is not edited afterwards. A changed decision is a new
> record that references the prior one.

---

## 6. Proposed change entry

Single line for the amendment record:

> `1.1.0 | 2026-09-18 | MINOR | Gate decision records MUST be materialized as repository artifacts by the agent acting on the decision, no later than the commit acting on it; conversation-only process rules carry no authority; CLAUDE.md MUST define gate record location and structure; release readiness blocks on unmaterialized gate outcomes. Raised by Pravallika Veeravalli after Gate 1 review found v1.0.0 specified gate-record contents and barred conversation-only approvals without naming a location, actor, or deadline. Gate 1 (2026-09-17) was decided under v1.0.0, remains valid and unmodified, and its late record materialization was not a violation of the version then in force.`

**Where change entries live — decision required.** §Amendment procedure mandates a recorded change
entry but defines no location, and the constitution has no amendment-history section. Two options:

| Option | Where | Trade-off |
|---|---|---|
| A *(recommended)* | New `## Amendment History` section in the constitution, above the version footer | Self-contained; a reviewer reading the constitution sees its history without hunting. Grows the file over time. |
| B | `docs/governance/amendment-log.md`, with proposals kept alongside in `docs/governance/amendments/` | Keeps the constitution lean; groups proposal with entry. Splits governance history across two locations. |

Option A is recommended because the version footer already lives in the constitution, so history and
version stay in one place and cannot drift apart. Choosing A would add a fifth edit to §2, which is
why it is raised as a decision rather than assumed.

---

## 7. Decision requested

The human owner is asked to record, per Constitution §Gate semantics:

1. **Approve / reject / request changes** on Edits 1–3 (core).
2. **Approve or decline** Edit 4 (release readiness condition 3).
3. **Confirm or reclassify** the MINOR classification and the 1.1.0 target.
4. **Approve or revise** the proposed `CLAUDE.md` content.
5. **Choose** change-entry location: Option A or Option B.
6. **Decide** whether the FR-ORC-013 follow-up criterion is added to the draft specification before
   Gate 2, or deferred to change control after it.

Nothing in this proposal has been applied. The constitution remains at v1.0.0, unmodified.
