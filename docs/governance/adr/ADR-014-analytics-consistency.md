# ADR-014: Analytics Consistency Model — Resolves DF-001

## Status

**Accepted with three conditions** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Conditions:
the scale analysis is recorded as NFR-SCA-003's answer; every append passes through the analytics recording port
with no bypass anywhere; the Postgres table is the store for now with PVT-009 as a failure-rate ceiling.
Decision record: `docs/governance/gate-decisions/gate-04-adr.md`.

**This ADR carries a correction to the plan's provisional position.** See §Rationale.

## Context

DF-001 is an unresolved contradiction recorded in the approved specification: **PVT-009** proposes an analytics
recording tolerance of "≤ 0.5% loss" under load, while **FR-URL-010** states that following a link "appends an
event" and **SC-001** requires correctness across 100% of the acceptance corpus. Exact-append and a loss budget
cannot both hold.

CL-002 (Gate 2) settled *what* is stored — per-event records carrying the short code and timestamp only, no
follower-identifying field — and the owner noted the stored events "double as evidence when demonstrating the
per-code and per-creator-aggregate limits". What was never settled is the **consistency model**: how the append
relates to the redirect in time and in transactional scope.

Two requirements bound the answer, and they pull in opposite directions:

- **FR-URL-010's negative criterion**: analytics recording **MUST NOT** cause a resolvable redirect to fail
  (EC-012).
- **FR-URL-010's positive criterion**: following a link **appends an event**.

**PVT-001** (redirect p95 ≤ 50 ms) constrains how much work may sit on the redirect path. DF-002 (redirect
permanence) is still open and interacts: a cached permanent redirect never reaches the service, so no
consistency model can count a redirect the service never sees.

**Assessment implication**: this is the one open finding whose resolution changes what acceptance tests may
assert, which is why plan §Decisions Required flags it as needing settlement *before any acceptance test is
written*.

## Decision Drivers

1. **Compliance with both of FR-URL-010's criteria simultaneously** — the binding constraint.
2. **Redirect latency budget** — PVT-001.
3. **Truthfulness of the resulting claim** — Principle X forbids claiming exactness the design cannot deliver.
4. **Evidentiary value** — the events support the rate-limiting demonstration (CL-002).
5. **Durability across restart** — an event acknowledged and then lost would be worse than one never accepted.
6. **Simplicity** — Constitution VII.

## Options Considered

### Option A — Synchronous, in the same transaction as the redirect resolution

- **Approach**: resolve the code and append the event in one transaction; commit before issuing the redirect.
- **Advantages**: makes "appends an event" literally and strictly true; no loss window at all; PVT-009 becomes
  redundant and can be retired.
- **Disadvantages**: **violates FR-URL-010's negative criterion.** If the event append fails, the transaction
  rolls back and the redirect fails — so analytics recording has caused a resolvable redirect to fail, which
  EC-012 explicitly prohibits. It also puts a write inside PVT-001's 50 ms budget on the hottest path.
- **Risks**: non-compliance with an approved negative acceptance criterion, not merely a performance concern.
- **Implementation impact**: simplest to write.
- **Assessment implications**: **disqualified on compliance.** A reviewer checking FR-URL-010's negative
  criterion against this design would find a direct contradiction.

### Option B — Synchronous append in its own transaction, failure isolated and counted

- **Approach**: resolve the code; append the event in a **separate** transaction; if the append fails, log and
  **count** the failure, and still issue the redirect. The redirect's success never depends on the append.
- **Advantages**: satisfies **both** FR-URL-010 criteria — every successful append is durable and immediate, so
  the positive criterion holds in normal operation, and an append failure cannot break a resolvable redirect, so
  the negative criterion holds. Loss is confined to the genuine failure case, is **measured** rather than
  designed in, and is reportable. Events are durable the moment they are acknowledged, preserving their
  evidentiary value for the rate-limiting demonstration. No queue, no drain process, no buffer to lose on crash.
- **Disadvantages**: one write remains on the redirect path, consuming part of PVT-001's budget. A narrow
  window exists where the redirect succeeds and the event is absent.
- **Risks**: under heavy store pressure the append becomes the redirect's slowest component.
- **Implementation impact**: small — separate transaction boundary plus a failure counter.
- **Assessment implications**: strong; and it gives PVT-009 an honest meaning (see Decision).

### Option C — Asynchronous in-process queue with a bounded buffer, best-effort drain

- **Approach**: enqueue the event in memory; a background worker drains to the store; drop on overflow.
- **Advantages**: minimal redirect latency impact; naturally satisfies the negative criterion.
- **Disadvantages**: events in the buffer are **lost on process termination** — and the system is explicitly
  tested by killing the process mid-run (FR-ORC-018, EC-015). An in-memory buffer reintroduces precisely the
  volatility CL-009 was adopted to eliminate, in a different component. Loss becomes a designed-in property, so
  FR-URL-010's "appends an event" would have to be softened, and PVT-009's tolerance would become a *target*
  rather than a ceiling on failures.
- **Risks**: inconsistency with the durability posture the owner established at Gate 3; loss during exactly the
  restart demonstrations the assessment relies on.
- **Implementation impact**: moderate — worker, bounded buffer, overflow policy, shutdown drain.
- **Assessment implications**: weakens the durability story for a latency gain that PVT-001 does not obviously
  require.

### Option D — Transactional outbox with asynchronous drain

- **Approach**: write the event to an outbox table in the resolution transaction; a separate process drains it.
- **Advantages**: the canonical pattern for atomicity between a business write and an external publication.
- **Disadvantages**: **there is no second write to be atomic with.** On a redirect, the event *is* the only
  write — there is no business mutation whose atomicity the outbox would protect. The pattern reduces to
  "write the event, then move it", which is strictly more machinery than Option B for no additional guarantee,
  and it inherits Option A's problem if the outbox write shares the resolution transaction.
- **Risks**: pattern applied by reflex rather than by fit; extra drain process to operate and recover.
- **Assessment implications**: would read as over-engineering; the absence of a co-write makes the pattern
  inapplicable.

## Decision

**Option B**, with a consequential redefinition:

1. The redirect resolves and issues; the event is appended in a **separate transaction**.
2. An append failure is **isolated, counted, and logged** — the redirect still succeeds. FR-URL-010's negative
   criterion holds.
3. Every accepted append is **durable immediately**; nothing is buffered in memory. FR-URL-010's positive
   criterion holds for every append that occurs.
4. **PVT-009 is retained but redefined**: it becomes the ceiling on the *analytics append failure rate under
   load*, **not** a designed-in lossiness budget. Proposed value unchanged at ≤ 0.5%; still requires approval.
5. **SC-001 is scoped explicitly** to redirect correctness — the client reaching the exact stored destination —
   which is what it was always about. Analytics completeness is governed by PVT-009 as redefined.

This resolves DF-001's contradiction without weakening any approved requirement: exactness applies to redirect
behaviour, durability applies to every accepted event, and the only loss is measured failure rather than
designed tolerance.

### Owner conditions attached at acceptance (2026-09-20)

**Owner's reasoning, recorded:**

> Analytics must be isolated from the product (separate transaction is the isolation mechanism);
> accepted-event durability is non-negotiable now (no buffer that a process kill can eat); the scale critique
> is real at extreme volume and is answered by documented mitigation plus the port, not by building
> infrastructure my approved scope excludes.

**Condition 1 — the scale analysis is recorded, not lost.** See §Scale analysis below, designated as the
recorded answer to NFR-SCA-003's first-bottleneck obligation.

**Condition 2 — portability is a requirement, not an accident.** Every append MUST pass through the **analytics
recording port**, an interface owned by this codebase, so that buffering or a Kafka-backed pipeline can later
replace the direct table append as an **implementation swap with zero contract change**. Implementation **MUST
NOT bypass the port anywhere** — not for convenience, not for a hot path, not in tests that then fail to
exercise it.

**Condition 3 — for now, the Postgres table is the store**, per this ADR as written. PVT-009 stands as
redefined here — a **failure-rate ceiling, not a loss budget** — subject to approval of the PVT set.

## Rationale

**Correction to the plan.** Plan §Decisions Required and `research.md` both record a *provisional* position of
"synchronous and transactional with the redirect" — Option A. Working the options against FR-URL-010's negative
criterion shows that position is **wrong**: a same-transaction append means an append failure fails the
redirect, which EC-012 explicitly prohibits. The provisional position was reached by optimising for the
positive criterion in isolation and did not test it against the negative one. It is corrected here rather than
carried forward, and the plan should be updated on approval of this ADR.

Option B is chosen because it is the only option that satisfies both criteria at once. The insight is that the
contradiction DF-001 records is not actually between "exact" and "lossy" — it is between two different subjects.
Redirect correctness can be exact. Analytics completeness can be *durable-when-accepted* with a measured failure
rate. Once PVT-009 is read as a failure-rate ceiling rather than a loss budget, FR-URL-010, SC-001, and PVT-009
are mutually consistent and all three are testable.

Option C is rejected on consistency with the owner's own Gate 3 reasoning: CL-009 was adopted because an
in-memory store "technically satisfies the wording while making the recovery guarantee nominal". An in-memory
analytics buffer is the same loophole in a different component, and it would lose events during the very
process-kill tests the assessment depends on.

Option D is rejected because the outbox pattern presupposes a co-write to be atomic with, and a redirect has
none.

## Consequences

**Positive**: both FR-URL-010 criteria satisfied; DF-001's contradiction resolved without weakening any
requirement; events durable on acknowledgement, preserving their evidentiary role for the rate-limiting
demonstration; no queue, worker, buffer, or drain process to build or recover.

**Negative**: one store write remains on the redirect path, consuming part of PVT-001's 50 ms budget. A narrow
failure window exists in which a redirect succeeds and its event is absent — accepted, measured, and reported
rather than hidden.

**Operational**: the analytics append failure rate becomes a metric worth watching; a rising rate signals store
pressure before it becomes a redirect-latency problem. The redirect path's latency profile now includes a write,
which should be measured separately so the two components are distinguishable.

**Testing**: a test asserting a redirect succeeds while the analytics append is forced to fail (EC-012). A test
asserting an accepted event is durable across a store restart. A load test measuring the append failure rate
against PVT-009 as redefined. A test asserting no follower-identifying field is ever stored (CL-002,
`POL-PRIV-001`).

**Governance**: this ADR **amends the plan's provisional position**, which is a change to an approved-track
artifact and therefore needs the change-control record required by `POL-CHG-001` once approved. PVT-009's
redefinition is a change to a proposed validation target and needs owner approval as part of the PVT set.

## Scale analysis — the recorded answer to NFR-SCA-003

NFR-SCA-003 requires the design to state **which component becomes the first bottleneck and why**. This section
is that statement, recorded at ADR-014's acceptance (owner, 2026-09-20) so the analysis survives as an artifact
rather than as a conversation.

### Insert throughput is not the first thing to break

> **Figures in this section are industry reasoning aids, not measurements of this system.** They are
> order-of-magnitude values drawn from general knowledge of PostgreSQL behaviour, used to establish *which*
> component constrains first. Nothing here has been measured on this codebase, and none of it may be cited as a
> measurement of it. The measurement NFR-SCA-003 additionally requires remains owed (Slice 7). Labelled per
> Constitution IX at the owner's instruction, 2026-09-20.

A single PostgreSQL instance sustains **thousands to tens of thousands of small inserts per second** *(industry
reasoning aid)*. For perspective: ten thousand clicks per second is roughly **860 million clicks per day**
*(arithmetic on that aid)* — large-business territory, well beyond anything this prototype targets. The
synchronous append chosen in this ADR therefore does not put write rate on the critical path in any plausible
near-term scenario.

### The first bottleneck is table growth, not write rate

What breaks first is the **size** of `redirect_event`, not the rate of writing to it:

- **Index bloat** as the table grows.
- **Retention deletes** — removing 30-day-old rows from a large table is expensive, and the delete competes with
  the inserts on the hot path.
- **Analytical queries** over an ever-growing table, which is what FR-URL-011's time-series retrieval becomes at
  volume.

So the honest answer to "what breaks first" is the analytics table's growth characteristics, and specifically
retention enforcement — not the append itself.

### Designed mitigation, recorded now

**Time-partitioning of `redirect_event`, with retention enforced by partition drop.** This converts the
expensive part — deleting 30 days of rows — into an **instant metadata operation**, and it keeps indexes
per-partition rather than monolithic.

**The demonstration implementation uses a plain table.** Partitioning is recorded as the documented **first
mitigation step**, not built now. That is the production-grade-discipline-versus-production-scale-infrastructure
distinction the plan commits to: the bottleneck is identified and its remedy designed, without constructing
infrastructure the approved scope excludes (EX-007, AS-001).

### Evolution ladder, when scale demands it

In order, recorded as the production path and explicitly **out of demonstration scope**:

1. **Batched appends** — amortise per-insert overhead.
2. **Asynchronous pipeline** (Kafka-class) — decouple the append from the redirect entirely.
3. **Columnar analytics store** (ClickHouse-class) — move analytical reads off the transactional store.

Each rung is reachable **behind the analytics recording port** (Condition 2), which is why that port is a
requirement rather than a nicety: rungs 1 and 2 are implementation swaps with zero contract change.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Append write pushes redirect p95 past PVT-001 | Redirect latency measured with the append component isolated, so the trade is visible; PVT-001 itself is unapproved and can be set with this cost known |
| Failure window under-reported | Append failures counted and surfaced as a metric, not merely logged; PVT-009 gives the count a threshold |
| Someone later "optimises" to an in-memory buffer | Recorded here as Option C with its rejection reason — event loss during the process-kill tests. Condition 3 fixes the store as the Postgres table for now |
| Implementation bypasses the analytics recording port for a "hot path" shortcut | Condition 2 makes the port mandatory; an architecture test asserts no append reaches the store except through it, and a bypass fails the build |
| Table growth degrades retention and analytical reads at volume | §Scale analysis identifies this as the **first** bottleneck and records time-partitioning with retention-by-partition-drop as the designed first mitigation |
| DF-002 resolving to a permanent redirect | Would mean cached redirects never reach the service, so *no* consistency model could count them. That is a property of caching, not of this decision, but the interaction must be stated when DF-002 is decided |
| Redefining PVT-009 read as moving a goalpost | The redefinition is recorded explicitly with its reasoning, and it makes PVT-009 *stricter* in character — a failure ceiling rather than a permitted loss |

## Reversibility

**High.** The append sits behind an analytics recording port. Switching to Option C or D later is an
implementation change behind that port, with no change to the wire contract — `contracts/openapi.yaml` is
unaffected either way, which is why `contracts/README.md` records DF-001 as affecting assertions rather than
the contract.

## Traceability

- **Requirements**: FR-URL-010 (both criteria), FR-URL-011 (retrieval of the recorded events), FR-URL-013
  (concurrency), FR-URL-014 (persistence failure handling), FR-URL-016 (events as rate-limiting evidence),
  NFR-SEC-005 (no personal data).
- **Specification**: **DF-001** (the finding this resolves), CL-002, EC-012, EC-013, PVT-001, PVT-009, SC-001,
  SC-003, SC-017, KE-02 (RedirectEvent).
- **Plan**: §Decisions Required item 1 (**corrected here**), §2 analytics component, §7 measurements.
- **Research**: `research.md` §Provisional positions — DF-001 (**corrected here**).
- **Expected tasks**: Slice 3 analytics append with isolated failure handling, the EC-012 test, and the
  append-failure metric; Slice 8 DS-B, whose subject matter is analytics under concurrent load.
- **Related**: ADR-002 (durability), ADR-007 (no destination-derived codes), ADR-010 (metric capture),
  **DF-002** (open, interacts).

## Validation

Executable: a redirect succeeding while the analytics append is forced to fail, proving EC-012; an accepted
event surviving a store restart, proving durability on acknowledgement; a load test at PVT-003 concurrency
measuring the append failure rate against PVT-009 as redefined; a schema assertion that no
follower-identifying field exists; and redirect latency measured with the append component reported separately
so PVT-001's budget consumption is visible rather than inferred.
