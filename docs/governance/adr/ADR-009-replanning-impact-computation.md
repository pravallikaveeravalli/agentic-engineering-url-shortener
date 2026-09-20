# ADR-009: Dynamic Replanning — Impact Computation Mechanism

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

FR-ORC-019 requires that when upstream outputs change materially, the orchestrator re-plans **affected**
downstream stages, invalidates their artifacts, and preserves governance across the replan — with the
explicit negative criteria that a previously granted approval for an invalidated stage **must not** be
carried forward (EC-020), that replanning must not corrupt an in-flight stage (EC-019), and that it must
not introduce graph cycles (EC-030).

The requirement says *which* stages must be re-planned — the affected ones, leaving unaffected paths
untouched. It does not say **how the affected set is computed**, and that is the decision here.

This has a governance cost that makes precision matter more than it first appears: every invalidated
stage that already held an approval requires a **superseding gate record** to void it, and the human must
then be asked again. Over-invalidation therefore does not merely waste compute — it burns human attention
and manufactures gate churn, which in turn makes the governance trail noisier and harder to reconstruct.

**Assessment implication**: dynamic replanning is one of the explicitly assessed behaviours. How the
affected set is derived, and whether the derivation is deterministic, is exactly what a reviewer will
probe.

## Decision Drivers

1. **Correctness** — no stale artifact may survive; nothing unaffected may be disturbed (FR-ORC-019).
2. **Determinism and testability** — replanning scope decides approval voiding, so it must be
   reproducible.
3. **Auditability** — a reviewer must be able to see *why* a stage was invalidated.
4. **Precision** — over-invalidation has a human cost, not just a compute cost.
5. **Timebox and available data.**
6. **Cycle safety** — EC-030.

## Options Considered

### Option A — Graph reachability: transitive downstream closure from the changed artifact's producing stage

- **Approach**: identify the stage that produced the changed artifact; compute the transitive closure of
  downstream stages over the persisted `dependency_edge` rows; invalidate that set.
- **Advantages**: deterministic and cheap; uses the instance edge table ADR-008 already materialises; the
  reason for each invalidation is explainable as "downstream of stage N"; trivially testable.
- **Disadvantages**: over-invalidates relative to actual data flow — a downstream stage that never
  consumed the changed artifact is still invalidated because it is reachable.
- **Risks**: unnecessary approval voiding, and therefore unnecessary human re-asks.
- **Implementation impact**: small — a graph traversal.
- **Assessment implications**: defensible. "Affected" is interpreted as "declared-dependent", which is a
  sound and explainable reading.

### Option B — Artifact-level consumption tracking

- **Approach**: record which stage consumed which artifact version; invalidate only stages that actually
  consumed the changed artifact, transitively through their own outputs.
- **Advantages**: most precise; minimises approval voiding and human churn; makes the invalidation reason
  specific — "consumed artifact X version 3".
- **Disadvantages**: requires consumption to be recorded reliably at every stage. FR-ORC-005 already
  requires provenance (which stage *produced* what, from which inputs), so the data is **partially**
  available — but partial coverage is the trap: a stage whose consumption was not recorded would be
  silently excluded from the affected set, leaving a stale artifact. That is a correctness failure, and it
  fails in the dangerous direction.
- **Risks**: under-invalidation from incomplete consumption data — worse than over-invalidation, because
  stale artifacts survive.
- **Implementation impact**: moderate; needs consumption recording to be complete and enforced before it
  can be trusted.
- **Assessment implications**: strongest if complete, unsafe if partial.

### Option C — Conservative: invalidate everything after the earliest affected stage

- **Approach**: find the earliest affected stage number; invalidate every stage after it.
- **Advantages**: simplest possible; cannot leave a stale artifact.
- **Disadvantages**: **violates FR-ORC-019's requirement that unaffected paths be left untouched.** With
  the S9∥S10 parallel pair, a change affecting only documentation would invalidate the security and policy
  stage too, voiding its approval for no reason. It also ignores the graph entirely, which undercuts the
  point of having an explicit dependency model.
- **Risks**: non-compliance with an approved requirement; maximal gate churn.
- **Assessment implications**: negative — it would read as not actually using the dependency graph.

### Option D — AI-determined impact set

- **Approach**: an AI-capable executor reasons about which stages are affected.
- **Advantages**: could in principle be more semantically precise than graph reachability.
- **Disadvantages**: **disqualified.** The affected set determines which human approvals get voided, so a
  non-deterministic component would decide governance scope. The same change could void different
  approvals on different runs, which makes replanning untestable and the governance trail
  unreproducible. It would also breach CN-011/FR-ORC-030 by making a core governance behaviour dependent
  on AI availability, and the reliability suite could not prove it deterministically.
- **Risks**: non-reproducible governance; a reviewer could not verify why an approval was voided.
- **Assessment implications**: **disqualifying.** Governance scope is not an inference target.

## Decision

**Option A for this assessment**: transitive downstream closure over the persisted per-run
`dependency_edge` rows, computed from the stage that produced the changed artifact.

Specifically:

- The affected set is the downstream transitive closure; stages outside it are untouched.
- Each invalidated stage transitions to `INVALIDATED`, and any approval it held is voided by writing a
  **superseding gate record** (never a deletion, never an edit) — EC-020.
- An in-flight stage is not mutated mid-execution; it completes or is cancelled at its next checkpoint
  (EC-019).
- Cycle detection runs against the instance topology **before** the replanned subgraph is committed
  (EC-030).
- A `ReplanEvent` records the cause, the invalidated stages, and the voided approvals, so the
  invalidation is explainable from persisted data alone.

**Option B is recorded as the intended refinement**, deferred to the backlog rather than adopted: it is
only safe once artifact consumption is recorded completely, and adopting it on partial data would fail in
the under-invalidation direction.

## Rationale

Option A is selected because it is **deterministic, explainable, and safe in the right direction**. Where
it errs, it errs toward invalidating too much, which costs human attention but never leaves a stale
artifact behind. Option B errs in the opposite direction whenever its consumption data is incomplete, and
a surviving stale artifact is a correctness failure that Constitution I treats as serious — so B is not
adoptable until consumption recording is provably complete, which is not achievable inside this timebox.

Option D is the important disqualification and is worth stating plainly: **the replan scope decides which
human approvals are voided, so it must not be an inference.** Allowing a model to determine it would mean
the same upstream change could void different approvals on different runs, which makes the governance
trail unreproducible and the behaviour untestable. This mirrors the reasoning the owner already applied in
CL-006, where the executor proposes but never rules — and for the same underlying reason.

Option C is rejected on plain non-compliance: FR-ORC-019 requires unaffected paths to be left untouched,
and with a parallel pair in the graph, "everything after stage N" demonstrably disturbs unaffected work.

## Consequences

**Positive**: deterministic and reproducible replanning; invalidation reason explainable from the
`ReplanEvent` and the edge table; cheap to compute; testable in the reliability suite without AI.

**Negative**: over-invalidation relative to true data flow, which means some approvals are voided and
re-requested unnecessarily. This is a real cost borne by the human reviewer and is accepted knowingly.

**Operational**: replan frequency and invalidated-stage counts are worth exposing as metrics — a high
invalidation ratio is the signal that Option B's precision is becoming worth its cost.

**Testing**: replanning tests assert the affected set is exactly the downstream closure, that unaffected
paths are untouched, that approvals on invalidated stages are voided by superseding record rather than
deleted, that an in-flight stage is not corrupted, and that a cycle-introducing replan is rejected.

**Governance**: approval voiding is explicit and reconstructable. Because the mechanism is deterministic, a
reviewer can independently recompute the affected set from the edge table and confirm the voiding was
correct — which is the property Option D would destroy.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Over-invalidation causing gate churn and reviewer fatigue | Accepted and disclosed; invalidation ratio exposed as a metric; Option B recorded as the refinement path |
| Stale artifact surviving a replan | Closure is over declared edges, which is a superset of true consumption — the failure direction is toward too much, not too little |
| Replan corrupting an in-flight stage | No mid-execution mutation; checkpoint-based cancellation (EC-019) |
| Cycle introduced by a replanned subgraph | Instance-level cycle detection before commit (EC-030) |
| Voided approval silently reused | Voiding writes a superseding gate record; a test asserts the prior approval cannot satisfy the re-planned stage |

## Reversibility

**High.** Impact computation sits behind one interface with a single responsibility — given a changed
artifact, return the affected stage set. Substituting Option B later is an implementation change behind
that interface, and the `ReplanEvent` record shape does not change.

## Traceability

- **Requirements**: FR-ORC-019 (replanning), FR-ORC-005 (provenance, which Option B would depend on),
  FR-ORC-002 (declared edges, which the closure traverses), FR-ORC-013 (gate outcomes and voiding),
  NFR-CHG-002 (replanning voids approvals rather than carrying them forward).
- **Specification**: EC-019, EC-020, EC-030, KE-14 (ReplanEvent), DS-C (the scenario that demonstrates it).
- **Plan**: §3 replanning procedure, §9 upstream change to downstream impact, §14 Slice 6 and Slice 8.
- **Expected tasks**: Slice 6 impact computation, invalidation, approval voiding, cycle detection; Slice 8
  DS-C demonstration run.
- **Backlog**: artifact-level consumption tracking (Option B) as the precision refinement.
- **Related**: ADR-008 (the edge table traversed), ADR-003 (engine), ADR-004 (why AI is excluded here).

## Validation

Executable proofs in the replanning suite, all driven by deterministic fake executors: the affected set
equals the computed downstream closure; unaffected parallel branches are untouched; an approval on an
invalidated stage is voided by a superseding record and cannot satisfy the re-planned stage; an in-flight
stage is not corrupted; a cycle-introducing replan is rejected before commit. DS-C provides the end-to-end
demonstration with a persisted `ReplanEvent` naming its cause, invalidated stages, and voided approvals.
