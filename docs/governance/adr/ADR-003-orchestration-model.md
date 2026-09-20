# ADR-003: Orchestration Model — Build Versus Adopt

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

The governed, stateful, non-linear orchestration system is the assessment's **central differentiator**
(spec §Reading Guide; Constitution §Assessment Scope). It must satisfy FR-ORC-001..032: twelve stages
with declared entry and exit criteria, an explicit dependency graph, parallel execution with
synchronisation, conditional branching, human gates whose silence never advances a run, two-vote retry
eligibility (CL-006), rollback distinguished from compensation (CL-007), safe-stop as a suspended
non-terminal state (CL-005), resumption across two restart classes (CL-009), dynamic replanning with
approval voiding, and audit evidence sufficient to reconstruct a run without the original session.

The decision is whether to author this engine or configure an existing workflow product.

**Scope context**: the approved scope is a single deployment with no multi-region or multi-tenant topology
(AS-001) and no production operation (EX-007). That bounds what class of engine the problem calls for, and is
the starting point for the comparison rather than an afterthought.

## Decision Drivers

1. **Fit of the candidate's native semantics to the approved clarifications** — CL-005, CL-006, CL-007,
   EC-020. What can be configured versus what must be authored regardless.
2. **Right-sizing to the approved scope** — one process, one store, twelve stages (AS-001, EX-007). Is the
   candidate solving this problem or a larger one?
3. **Complexity justification under Constitution VII** — complexity not justified by a requirement is
   a `FAIL` against Principle VII.
4. **Total timebox cost** — setup plus programming model plus adapters plus the custom semantics, versus
   the custom semantics plus a small state core.
5. **Demonstrability of governance semantics** — prohibited transitions, silence-never-approves,
   two-signature retry rulings, approval voiding.
6. **Reviewer setup burden** — additional servers are a cost paid by whoever runs this.

## Options Considered

### Option A — Purpose-built explicit persisted DAG

- **Approach**: stage nodes and dependency edges as first-class persisted data; explicit run and stage
  state machines; allowed and prohibited transitions enumerated in code and enforced; executors behind
  one interface.
- **Advantages**: every governance semantic the spec demands is expressible exactly, including the ones
  no product models natively — the two-vote retry intersection, the compensation register's
  registration-time enforcement, and approval voiding by superseding record. No extra infrastructure.
  The graded artifact is authored.
- **Disadvantages**: correctness of transitions, persistence, and resumption is ours to get right.
- **Risks**: reinventing workflow semantics subtly wrongly — the classic failure mode.
- **Implementation impact**: the largest single body of work in the plan (Slices 4–6).
- **Assessment implications**: maximal. It is also the only option where a reviewer's questions about
  state semantics have first-hand answers.

### Option B — Temporal

- **Approach**: Temporal server plus workers; workflows as durable code.
- **Advantages**: battle-tested durable execution, retries, and resumption; history is inherently
  replayable. Runs comfortably on one machine — its dev server starts with a single command, and a
  container runtime is already a prerequisite here (ADR-002), so operational feasibility is **not** an
  objection to it.
- **Disadvantages**: its durability covers **its own workflow state**, not our domain — links, audit
  records, and gate records remain in our store under our transactions, so CL-009's restart machinery is
  still owed either way. Its retry model is per-activity policy, not a two-party intersection with an
  executor veto, so CL-006 must be authored on top regardless. Human gates become signal-and-wait, where
  "silence suspends rather than advances" is author-enforced anyway. Most structurally: flow is expressed
  as workflow **code**, so the dependency graph is implicit in call order — the opposite of FR-ORC-002's
  requirement for inspectable data with declared edges.
- **Risks**: a second stateful service in every demonstration, whose own unavailability the safe-stop story
  must then also cover; workflow-determinism constraints as a specialist programming model to learn and
  defend; permanent explanation of concept mismatches between its model and the approved semantics.
- **Implementation impact**: replaces the scheduling loop; leaves the governance layer, the domain-store
  durability work, and a parallel graph representation still to be built.
- **Assessment implications**: the adoption ledger (Rationale §3) does not balance — and a reviewer would
  reasonably ask why a distributed execution engine is present in a single-process system.

### Option C — Camunda / BPMN engine

- **Approach**: model the twelve stages as a BPMN process; engine executes it.
- **Advantages**: gates, parallel gateways, and compensation are native BPMN concepts; visual model is
  attractive to reviewers.
- **Disadvantages**: BPMN compensation semantics do not map cleanly onto CL-007's structural-default
  register with registration-time enforcement; the two-vote retry ruling has no BPMN analogue; heavy
  engine plus modelling toolchain.
- **Risks**: fighting the engine's semantics to satisfy approved clarifications, then having to explain
  the mismatch.
- **Assessment implications**: mixed — visually strong, semantically awkward, and still outsources the
  core.

### Option D — Spring StateMachine (or equivalent library)

- **Approach**: library-managed stage state machine; graph, persistence, replanning, gates hand-built.
- **Advantages**: removes some transition boilerplate; no server.
- **Disadvantages**: covers only the smallest part of the problem. The graph, per-run topology
  materialisation, replanning with approval voiding, the gate model, the compensation register, and the
  retry ruling — the actual work — remain ours. Adds a dependency for modest gain.
- **Risks**: library constraints leaking into the state model.
- **Assessment implications**: neutral; neither the credit of authoring nor the savings of adopting.

## Decision

**Build a purpose-built orchestration engine**: explicit persisted dependency graph, explicit run and
stage state machines with enumerated allowed *and prohibited* transitions, one executor interface, and
no external workflow product or server.

## Rationale

### Owner's critique of an earlier draft (2026-09-20)

An earlier draft of this Rationale led with the fact that the orchestration model is the graded
deliverable. The owner rejected that as a leading argument and required the section restructured:

> "This is the graded deliverable, so we must author it" is context, not engineering justification — as a
> leading argument it is circular and invites exactly the "why reinvent the wheel" challenge it should be
> answering. The rationale must stand on engineering grounds that would hold even outside an assessment.

The Rationale below is restructured accordingly. Every argument in it would hold for a team building this
system with no assessment attached.

### 1. Right-sizing: price versus need, and one structural conflict

**Second owner correction (2026-09-20).** An earlier version of this section argued that Temporal exists for
distributed execution while this system is one process. The owner rejected that framing:

> As written, "Temporal exists for distributed execution; this system is one process" can be read as claiming
> Temporal cannot run small. That is false — its dev server runs on a single machine with one command, and we
> have a working container runtime. An argument a reviewer can falsify in one sentence is worse than no
> argument.

Correct. Operational feasibility is conceded outright: Temporal runs on one machine trivially, and Docker is
already a prerequisite under ADR-002. The argument is **price versus need**, not capability.

**What Temporal's differentiating machinery buys.** Durable execution *across processes and workers*:
workflows surviving deploys, running for weeks, replaying deterministically across a fleet. Our runs are
**minutes long in one process**. That machinery is not unavailable to us — it is simply not addressing a
problem we have.

**And critically, it does not cover the durability we actually need.** Temporal's durability covers **its own
workflow state**, not our domain. Links, audit records, gate decisions, and policy results still live in our
store under our own transactions. So CL-009's requirement — that a run survive a **persistence-layer** restart,
with the application observing `UNAVAILABLE`, retrying under the two-vote rule, suspending, and resuming — must
be built for the domain store **regardless of adoption**. Adoption replaces the scheduling loop. It does not
replace the durability work.

**One structural conflict**, not a preference: FR-ORC-002 requires the dependency graph to be inspectable data
with "edges declared rather than inferred from call order". Temporal's model is the inverse — flow is expressed
as workflow code, and the graph is implicit in that code's call order. Satisfying FR-ORC-002 on Temporal means
maintaining a **parallel data representation of the graph alongside the workflow code**, which is precisely the
artifact being built here. Adoption would therefore not remove that artifact; it would duplicate it and add a
synchronisation obligation between the two.

### 2. Requirements mismatch, itemised

No candidate product natively models the approved semantics. Each of the following would have to be
authored on top of whichever engine were adopted:

| Approved semantic | Source | Why no product models it |
|---|---|---|
| Retry as a **set intersection** of a design-time declared retryable set with the executor's proposal — executor holds **veto, never grant** | CL-006 | Products model retry as per-activity policy, a single-party decision. A two-party intersection with asymmetric authority is not expressible as configuration |
| Compensation register with **registration-time enforcement** — a stage declaring an irreversible effect **fails to load** without a named compensating action | CL-007 | Saga compensation is registered per activity; refusing to *load* an improperly declared stage is a lifecycle constraint outside the products' model |
| Safe-stop as a **suspended non-terminal state** with silence-never-advances and idle retention to `ABANDONED` | CL-005 | Timeouts are modelled as failures or as signal waits; a durable non-terminal suspended state with its own retention policy is ours to define |
| Replanning that **voids approvals by superseding record**, never deletion | FR-ORC-019, EC-020 | No product has a notion of a human approval that a topology change invalidates |
| Dependency graph as **inspectable data**, with edges "declared rather than inferred from call order" | **FR-ORC-002** | Temporal's model is the inverse: flow is workflow code and the graph is implicit in call order. Satisfying this on Temporal means maintaining a parallel data representation of the graph — the very artifact being built — plus keeping the two in sync. A **structural conflict with an approved requirement**, not a preference |

Adoption therefore removes **the cheapest layer** — the scheduling loop and its durability plumbing — while
leaving every hard part in place and adding infrastructure. That is the wrong trade regardless of who is
grading it.

### 3. The adoption ledger, stated honestly

What adoption would genuinely save, and what it would still leave owed:

| | Item |
|---|---|
| **Saved** | The scheduling loop — dispatch, waiting, timers, sequencing |
| **Saved** | Temporal's own workflow-state durability and replay |
| **Still owed** | The entire governance layer: CL-005's suspended non-terminal safe-stop with retention, CL-006's two-vote retry with executor veto, CL-007's registration-time-enforced compensation register, approval voiding by superseding record |
| **Still owed** | The domain-store restart machinery for CL-009 — Temporal's durability does not cover links, audit records, or gate records |
| **Still owed** | A parallel data representation of the dependency graph, to satisfy FR-ORC-002 alongside workflow code |
| **Added cost** | One more stateful service in every demonstration, whose own unavailability the safe-stop story must then also handle |
| **Added cost** | A specialist programming model — workflow determinism constraints — to learn inside the timebox and defend live |
| **Added cost** | Permanent explanation of concept mismatches between the product's model and the approved semantics |

Two saved items against three still owed and three added. The ledger does not balance, and this accounting
holds before any assessment consideration enters.

### 4. Constitution VII

The test Principle VII applies is not whether a component *can* run here, but whether a requirement calls
for it. Per §3's ledger, adoption would add a stateful service and a specialist programming model while
leaving the governance layer, the domain-store durability work, and the graph representation still to be
built — so the added infrastructure answers no requirement that remains unmet without it. Under our own
compliance check that is complexity unjustified by a requirement, and it would be recorded as a **`FAIL`**
against Principle VII in the plan's constitution check — by the project's own rules, not by preference.

### When adoption would be the right decision

Stated plainly rather than defensively: at **production scale** this decision should be revisited. If
workflows became long-running in the sense Temporal means — spanning days, surviving deployments, executing
across distributed workers — or if a team rather than one person had to maintain the engine, or if failure
domains multiplied across services, then the durability and visibility guarantees of a mature product would
outweigh the cost of adopting it, and hand-rolled state management would become a liability rather than a
right-sized choice.

That future is deliberately provided for. The **executor interface** and the **persisted graph** are the
migration seams: the stage definitions, the domain, and the governance semantics sit above them, so a later
migration would replace the scheduling and durability layer while preserving everything that encodes the
approved clarifications. This is not an accident of the design; it is why those two boundaries exist.

### Assessment implications — context, not justification

Recorded last and deliberately not load-bearing: the orchestration model is also the deliverable under
assessment, so authoring it means the candidate can answer questions about its state semantics first-hand
rather than about a product's documentation. This is a **consequence** of the decision the four grounds
above already reach, not a reason for it.

## Consequences

**Positive**: exact expression of every approved governance semantic; no extra infrastructure; a component
sized to the problem rather than to a distributed one. Secondarily, reviewer questions about state semantics
have first-hand answers.

**Negative**: transition correctness, durability, and resumption are our responsibility; this is the
plan's largest work item.

**Operational**: nothing beyond the application process and the store. `quickstart.md` needs no
workflow-server setup.

**Testing**: the burden shifts onto us and is met deliberately — prohibited-transition tests are
first-class, not an afterthought; scriptable fake executors make every reliability proof deterministic
(FR-ORC-030).

**Governance**: this is a deliberate complexity **acceptance**, recorded in plan §Complexity Tracking. It is
justified on right-sizing grounds — the approved scope is one process and one store (AS-001, EX-007), and the
approved semantics (CL-005, CL-006, CL-007, EC-020) are not expressible as configuration of any candidate
product. Adopting one would itself be the unjustified complexity Principle VII forbids.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Subtly wrong state semantics | Prohibited transitions enumerated and each one tested as a rejection, not merely absent from the happy path |
| Resumption bugs causing duplicated committed effects | Effect records consulted before re-dispatch; run-level lease prevents concurrent resumption (EC-026); exactly-once asserted in the resumption suite |
| Engine work consumes the timebox, starving evidence | Plan §14 stop condition 1: if Slice 4 is incomplete by end of Day 2, feature work stops — the engine cannot be traded away |
| Reviewer sees "not invented here" as naivety | This ADR is the answer: the rejection of Temporal is reasoned on Constitution VII and on semantic mismatch, not on unfamiliarity |

## Reversibility

**Moderate.** The executor interface and the persisted graph are the seams. Migrating to Temporal later
would preserve the domain and the stage definitions while replacing the scheduling and durability
layer. Within this assessment, reversal after Slice 4 is not affordable.

## Traceability

- **Requirements**: FR-ORC-001 through FR-ORC-032, most directly FR-ORC-002 (explicit graph),
  FR-ORC-003 (parallel and join), FR-ORC-013 (gates), FR-ORC-014 (retry), FR-ORC-016
  (rollback/compensation), FR-ORC-017 (safe-stop), FR-ORC-018 (resumption), FR-ORC-019 (replanning).
- **Specification**: CL-005, CL-006, CL-007, CL-009, §Stage Executor Model, EC-015..EC-038.
- **Plan**: §12 ADR-003, §3 (all), §6, §Complexity Tracking, §14 Slices 4–6 and stop condition 1.
- **Expected tasks**: Slice 4 graph and state model; Slice 5 gates; Slice 6 reliability controls.
- **Related**: ADR-008 (state persistence and graph representation), ADR-009 (replanning), ADR-006.

## Validation

The orchestration suite validates the decision directly: every allowed transition exercised, every
prohibited transition asserted rejected, one fan-out and one join with the join blocking on an
incomplete branch, the silence test producing suspension rather than progression, the two-vote retry
matrix, rollback and compensation separately labelled, and interruption-and-resume at every stage
boundary. Constitution VII compliance is validated by the plan's constitution check re-run at
`/speckit-analyze`.
