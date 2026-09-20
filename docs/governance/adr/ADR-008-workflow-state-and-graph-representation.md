# ADR-008: Workflow State Persistence and Dependency Graph Representation

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

Two questions are decided together here because they cannot be decided apart: **how run state is
persisted**, and **how the dependency graph is represented**. Replanning mutates a run's topology
(FR-ORC-019), so the storage shape and the graph shape constrain each other.

Requirements in play: FR-ORC-004 requires durability across an orchestrator-process restart *and* a
persistence-layer restart (CL-009). FR-ORC-023 requires a reviewer to reconstruct a run from persisted
records **without the original session**. FR-ORC-005 requires artifact provenance and decision lineage.
FR-ORC-008 requires inspection to report per-stage status, graph position, parallel and join structure,
and the current blocking condition. FR-ORC-002 requires edges to be *declared* rather than inferred from
call order, and EC-030 requires cycle rejection when replanning introduces one. CL-005 adds that a
suspended run has no terminal outcome and exposes a computed `auto-abandon-at`.

**Assessment implication**: this determines whether "audit-grade evidence" and "state persistence" are
real. A reviewer will try to answer questions from the database alone.

## Decision Drivers

1. **Reconstruction without the session** — FR-ORC-023.
2. **Queryability** — inspection and replanning need to ask questions like "which stages await approval"
   and "what is downstream of stage 6".
3. **Durability across both restart classes.**
4. **Replanning support** — instance topology must be able to differ from the template.
5. **Complexity justification** for twelve nodes — Constitution VII.
6. **Schema evolvability** across the timebox.

## Options Considered

### Option A — Current-state rows plus an append-only transition log (hybrid)

- **Approach**: `workflow_run` and `stage_node` hold current state; every transition is appended to an
  immutable log carrying from-state, to-state, actor, timestamp, and reason. `dependency_edge` rows
  materialise the graph **per run instance**.
- **Advantages**: current state is directly queryable, so inspection and replanning are simple reads;
  the transition log satisfies reconstruction and is the audit trail already required by NFR-AUD-001,
  so it is not additional machinery; durability follows from ADR-002; per-run edges let a replanned
  instance legitimately differ from the template.
- **Disadvantages**: current state and log can in principle diverge if a transition writes one without
  the other.
- **Risks**: that divergence, if transitions are not written atomically.
- **Implementation impact**: moderate; one transition-writing helper used by every state change.
- **Assessment implications**: strong — answers both "what is true now" and "how did it get here".

### Option B — Pure event sourcing, state derived by replay

- **Approach**: only events are stored; current state is a projection replayed on demand or maintained
  as a cache.
- **Advantages**: perfect lineage by construction; divergence between state and history is impossible;
  temporal queries come free.
- **Disadvantages**: projection machinery, replay cost, and event-versioning discipline — substantial
  infrastructure for a twelve-node graph. Inspection becomes a replay rather than a read. Replanning
  against a projected topology is harder to reason about.
- **Risks**: over-engineering that Constitution VII would flag; timebox consumed by plumbing rather than
  by governance behaviour.
- **Implementation impact**: highest.
- **Assessment implications**: technically impressive, but the complexity is not justified by any
  requirement — and "we built event sourcing for twelve nodes" is a weaker answer than it sounds.

### Option C — Current state only, no history

- **Approach**: rows updated in place.
- **Advantages**: simplest; smallest schema.
- **Disadvantages**: **fails FR-ORC-023 outright** — a reviewer cannot reconstruct retries, replans, or
  gate history from current state. Also fails NFR-AUD-001's immutability, since in-place updates destroy
  the prior value.
- **Risks**: non-compliance, not merely weakness.
- **Assessment implications**: disqualified.

### Option D — Serialised state blob per run

- **Approach**: the whole run object graph serialised into one column per run.
- **Advantages**: trivial to persist and restore; no schema design.
- **Disadvantages**: **opaque** — no way to query "which runs are suspended" or "which stages await
  approval" without deserialising every run. Schema evolution becomes a migration of serialised payloads.
  Audit assertions such as `POL-AUD-001`'s six-field check cannot be executed as queries. It defeats the
  auditability the whole design rests on.
- **Risks**: auditability reduced to application-mediated access, undermining independent verification.
- **Assessment implications**: negative — evidence a reviewer cannot query independently is weak evidence.

### Graph representation sub-options

- **G1 — Code-only static graph**: the twelve-node topology lives in code; runs reference it.
  *Advantage*: no graph tables. *Fatal disadvantage*: replanning alters a run's topology, and a code-only
  graph cannot record that a particular run's structure differed — so the replan history would be
  unreconstructable and EC-030's cycle check would have nothing instance-level to validate.
- **G2 — Per-run materialised edges**: a static template defines the default topology; each run
  materialises its own node and edge rows at creation, and replanning mutates the *instance*.
  *Advantage*: instance topology is recorded, queryable, and independently verifiable; cycle detection
  runs against the instance before commit. *Disadvantage*: more rows.

## Decision

**Option A with G2.**

- `workflow_run` and `stage_node` hold current state, written in the same transaction as an appended
  `state_transition` row — so the log and the state can never diverge.
- `dependency_edge` rows are **materialised per run** from a static template at run creation; replanning
  mutates instance rows, and cycle detection runs against the instance topology **before** the replanned
  subgraph is committed (EC-030).
- The transition log is append-only and, per ADR-010, the application role holds INSERT and SELECT but
  not UPDATE or DELETE on it.
- `terminal_state` is null while suspended; `auto_abandon_at` is non-null only while suspended — both
  invariants are already expressed as conditionals in `contracts/workflow-state.schema.json` and are
  validated against persisted snapshots.

## Rationale

Option A is chosen because the transition log is **not additional machinery** — NFR-AUD-001 and
FR-ORC-023 require an immutable history regardless, so the hybrid gets reconstruction essentially for
free while keeping current state cheaply queryable for inspection and replanning. Option B's guarantee
against divergence is real, but it is purchasable far more cheaply here: writing the state change and its
transition row in one transaction closes the same gap without projections or replay.

The decisive argument against Option D, and for G2 over G1, is the same: **evidence a reviewer cannot
query independently is weak evidence**. FR-ORC-023's test is whether someone with database access and no
session can reconstruct a run. A serialised blob, or a topology that exists only in code, forces every
question through the application — which means the audit trail's credibility depends on the very software
under assessment.

Option B was also weighed on its assessment value and rejected: building event sourcing for a twelve-node
graph is the kind of complexity Constitution VII asks to be justified by requirements, and no requirement
asks for temporal queries or replay.

## Consequences

**Positive**: current state queryable; history immutable and complete; replan history reconstructable;
instance topology independently verifiable; schema-level invariants machine-checked against the
workflow-state contract.

**Negative**: every state change must write two rows atomically — a discipline that must not be bypassed;
more tables than Option D.

**Operational**: row growth is proportional to transitions, which at demonstration scale is negligible.
Retention interacts with DF-003, still open.

**Testing**: state-transition tests assert both the new current state **and** the appended transition row.
A divergence test asserts that a state change without its transition row is impossible. Workflow-state
snapshots are validated against `contracts/workflow-state.schema.json`, including the terminal/suspended
conditionals.

**Governance**: enables `POL-AUD-001` (six-field check) as a real query; makes the suspended-versus-terminal
distinction from CL-005 a schema-enforced invariant rather than a convention; makes approval voiding on
replan (EC-020) reconstructable, since the replan event and the superseding gate record are both persisted.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Current state and transition log diverging | Both written in one transaction; a test asserts a state change cannot be committed without its transition row |
| Replanning corrupting instance topology | Cycle detection against the instance before commit (EC-030); in-flight stages not mutated mid-execution (EC-019) |
| Concurrent resumption double-advancing a run | Run-level lease, asserted by the concurrent-resume test (EC-026) |
| Schema churn across slices | Forward-only Flyway migrations; contract schemas versioned per `contracts/README.md` |
| Unbounded row growth | Demonstration scale makes this moot; retention rule pending DF-003 |

## Reversibility

**Moderate.** Moving from A to B later is feasible precisely because the transition log already contains
the event stream — it would become the source rather than the companion. Moving from G2 to G1 is not
possible without abandoning replanning history, so G2 is the direction that preserves options.

## Traceability

- **Requirements**: FR-ORC-002 (declared edges), FR-ORC-004 (durability), FR-ORC-005 (provenance and
  lineage), FR-ORC-008 (inspection), FR-ORC-017/032 (suspension and retention state),
  FR-ORC-019 (replanning), FR-ORC-023 (audit reconstruction), NFR-AUD-001, NFR-OBS-002.
- **Specification**: CL-005, CL-009, EC-015, EC-016, EC-019, EC-020, EC-026, EC-030, EC-038, KE-04, KE-05,
  KE-06.
- **Plan**: §3 state machines and topology, §4 state and decision lineage, §14 Slices 4 and 6.
- **Contracts**: `contracts/workflow-state.schema.json` (the invariants named above).
- **Expected tasks**: Slice 4 graph and state tables, transition writer, cycle detection, inspection
  endpoint; Slice 6 resumption across both restart classes.
- **Related**: ADR-002 (store and privilege enforcement), ADR-003 (engine), ADR-009 (replanning),
  ADR-010 (audit model).

## Validation

Executable proofs: state-transition tests asserting current state and appended transition row together;
a test asserting a state change without its transition row cannot commit; schema validation of persisted
snapshots against the workflow-state contract including both conditionals; a cycle-rejection test on a
replanned subgraph; interruption-and-resume at every stage boundary plus a persistence-layer restart; and
the reconstruction exercise from `quickstart.md` §5, answered from the database alone by someone who did
not run the workflow.
