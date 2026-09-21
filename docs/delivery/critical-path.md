# Critical-Path Declaration

**Task**: T004 · **Requirement**: CN-007 · **ADR**: ADR-003 · **Derived from**: `plan.md` §14.

## The path

```
1 ──► 2 ──► 4 ──► 5 ──► 6 ──► 8 ──► 9
```

**Slice 3 is not on the critical path.** **Slice 7 is partially parallel with 6**, with one hard ordering constraint
below. Both are must-have; neither being off the path makes either optional.

---

## Why each link is non-removable

| Link | Why it cannot be removed or reordered |
|---|---|
| **1 → 2** | The walking skeleton is what proves the baseline is real. Without Slice 1's build, migrations, contract harness and architecture test, Slice 2 has nothing to be a skeleton *of* — and a skeleton that runs without a validated harness proves only that code compiled |
| **2 → 4** | The orchestration state model persists to the same store the skeleton proved reachable. Building a persisted DAG before a single real round-trip has succeeded would put two unproven things in series, so a failure could not be localised |
| **4 → 5** | A gate is a **state transition with a human in it**. Gates cannot be built before the states they suspend and resume exist, or the five outcomes have nothing to act on |
| **5 → 6** | Reliability controls end in **safe-stop and resumption**, which are gate-adjacent states. Retry and compensation without the suspension machinery would have nowhere to escalate to on exhaustion — and since Decision J there is **no fallback to absorb an exhausted retry**, so suspension is the only terminus. That makes this link stronger than it was when the plan was written |
| **6 → 8** | The three scenarios **exercise** reliability: DS-B injects a transient fault and a compensation case. Running the scenarios before the controls exist would produce narrative, not evidence |
| **8 → 9** | Release readiness **evaluates recorded evidence**. Its nine blocking conditions and the final summary are assembled from what the scenarios produced. Evaluating before the scenarios run would mean evaluating nothing |

---

## Slice 3 — required as DS-B's subject matter, not as a path link

Slice 3 builds the core URL behaviour and **two of FR-URL-016's three rate-limit tiers**. The third — the per-creator
aggregate redirect tier (PVT-014) — is **deliberately deferred to Slice 8's brownfield run**, where it is implemented
under governance.

**That is precisely why Slice 3 is off the critical path and still mandatory**: DS-B needs a working shortener with a
*known, written-down gap* to be a brownfield scenario at all. A brownfield run against a system with nothing missing
would be a greenfield run wearing a different label.

The deferral is recorded in `baseline-omissions.md` (T055a), which must exist **before** Slice 3's acceptance sweep
records the aggregate case as passing unthrottled. FR-URL-016 remains **binding in full**, its matrix reference reads
***partial*** until the brownfield run closes it, and if that run does not happen **release readiness reports a real
gap** rather than relabelling it as a design choice.

---

## Slice 7's `failure_event` capture must precede Slice 8

**This is the ordering constraint the plan calls out explicitly, and it is the one most easily lost**, because Slice 7
is otherwise parallelizable with Slice 6 and reads like observability polish.

**`failure_event` capture must exist before Slice 8 runs, or the scenarios produce no MTTR population.**

The reason is that MTTR is computed over failures that were *recorded when they happened*. It cannot be back-filled
after the scenarios have run — and attempting to would be fabrication under CN-005 and Principle X, not a shortcut.
A scenario that failed, recovered, and left no `failure_event` row has no recoverable measurement: the number would
have to be reconstructed from memory or invented.

So the parallelism is real but bounded:

```
        6 Reliability ─────────────┐
                                   ├──► 8 Scenarios ──► 9 Release readiness
        7 Observability ───────────┘
           └─ `failure_event` capture is the part that MUST land before 8
```

The rest of Slice 7 — metrics, traces, the audit inspection surface, the MTTR *calculation* itself — may follow Slice 8,
because those read the rows rather than create them. **Only the capture is order-critical.**

---

## What this file is not

It is not a schedule — that is `milestones.md`. It is not a statement that off-path slices are lower priority; all nine
are must-have per `scope-register.md`. It records **which orderings are forced by dependency** so that a checkpoint
discussion about sequence is had against a written reason rather than an intuition.
