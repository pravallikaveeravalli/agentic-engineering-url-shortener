# Must-Have Scope Register

**Task**: T001 · **Requirement**: CN-007 · **Derived from**: `plan.md` §14 Delivery Sequence (approved 2026-09-20 at
the Gate 4 closing package, amended by CR-009 and CR-026).

**What this file is.** The operational checklist for the nine slices: what each one must contain, what ends it, and
which requirements it delivers. **It does not restate the plan** — the plan holds the reasoning, the milestones and the
checkpoint options. This is the list you read while working to know whether a slice is done.

**What "must-have" means here.** All nine slices are must-have. Nothing on this page is optional, and nothing on it may
be cut at a checkpoint. What a checkpoint may reduce is listed in `backlog.md` and in `checkpoints.md`, never here.

**The scope is the commitment, not the schedule** (CR-009, plan §Scope versus timebox). 173 tasks against a 2–3 day box
do not reconcile, and they are not meant to: the box reports progress, the scope states what the approved requirements
need. A slice is finished when its exit condition holds, not when its milestone passes.

---

## The nine slices

| # | Slice | Must-have contents | Exit condition — the slice is not finished until this holds | Requirements delivered |
|---|---|---|---|---|
| **1** | Engineering baseline | Build file and package layout enforcing plane separation; Flyway migration baseline; OpenAPI skeleton served and parsed; contract-test harness with a **deliberate-drift proof**; local CI-equivalent script; architecture test for dependency direction, the fallback-absence rule and the no-executor-references-gate-decision rule | The CI-equivalent script runs green from a clean checkout, **and** the contract-test harness **exists and runs** — plan §14’s own wording for this slice. **The drift-failure demonstration is T013, in Slice 2**, because it depends on T012 which depends on T032’s live endpoint: a conformance harness has nothing to validate a response against until an endpoint exists. *(Corrected by CR-035 on the owner’s decision. The earlier wording required the drift proof here, which the dependency graph makes unreachable — it was this register author’s addition, not the plan’s.)* The principle it was reaching for is not abandoned: **a harness that has never failed is unvalidated**, and T013 discharges it one slice later — exactly as T015 discharges it for the architecture rule within this slice | NFR-MNT-001, NFR-MNT-002; ADR-001, ADR-002, ADR-005, ADR-006, ADR-012 |
| **2** | Walking skeleton | One real endpoint, health/readiness, and a real round-trip to PostgreSQL through Testcontainers | A request reaches the store and returns, in a test, against a **real** store — not a mock | FR-URL-015 |
| **3** | Core URL behaviour | Validation, scheme allow-list, abuse controls, code generation with uniqueness, redirect (temporary class), expiry, three idempotency semantics, per-event analytics capture and creator-scoped retrieval, concurrency safety, persistence-failure isolation, creator provisioning, and **two of three rate-limit tiers** — per-creator creation (PVT-012) and per-code redirect (PVT-013) | Every row of `quickstart.md` §2 passes as a test; the **aggregate-tier case passes unthrottled and is recorded as the brownfield before-state**, with its entry present in `baseline-omissions.md` **before** the sweep runs | FR-URL-001..015, **FR-URL-016 partial — two tiers**, FR-URL-017, FR-URL-018, FR-URL-019; SC-001, SC-002, SC-003, SC-017 |
| **4** | Orchestration state model | Persisted DAG with **13 nodes on a fresh run** (12 singletons + 1 join; S7 fans out to instance-keyed children), transitions, prohibited-transition enforcement, run creation and inspection surfaces | A run's full state survives **both** restart classes — orchestrator process and persistence layer — and a prohibited transition is refused by test, not by convention | FR-ORC-001..008 |
| **5** | Approval governance | Gates with five outcomes, the deadline stated **in the ask**, silence → suspension, actor-authority check, no-plan gate with three options, idle retention and auto-abandonment | **T061 passes and T061a proves it can fail.** Silence must be demonstrated not to advance, and the demonstration must itself be falsifiable | FR-ORC-013, FR-ORC-021, FR-ORC-031, FR-ORC-032; NFR-AUT-001, NFR-AUT-002; SC-005 |
| **6** | Reliability controls | Closed failure envelope; **two-vote retry against the twelve declared retryable sets**; design-time timeout gating; Compensation Register with structural default and declared overrides; safe-stop; resumption across both restart classes; dynamic replanning | Each behaviour is proven by an **injected** scenario through a scriptable fake, never by a live provider — and each injected condition is **labelled as injected** in its evidence (AS-007 as extended by CR-033) | FR-ORC-014, FR-ORC-016..019, FR-ORC-030; SC-006, SC-007 |
| **7** | Observability | Audit events carrying the six mandatory fields; correlation IDs; metrics, logs and traces; `failure_event` capture; MTTR calculation over a **declared population**. **No retention work** — this demonstration retains everything indefinitely (NFR-AUD-003, CR-017) | One run is reconstructable **from the database alone**, and `failure_event` capture exists **before** Slice 8 runs, or the scenarios produce no MTTR population | FR-ORC-005, FR-ORC-023, FR-ORC-024; NFR-AUD-001..003; SC-008, SC-009 |
| **8** | Three scenarios | DS-A greenfield, DS-B brownfield (**the deferred aggregate redirect tier, implemented under governance**), DS-C ambiguous requirement — each executed with committed evidence; plus the out-of-scenario generic-executor run | All three executed with evidence a reader can follow end to end **with no AI setup**; DS-B closes FR-URL-016's *partial* matrix reference; the out-of-scenario run shows **zero executor changes** in the diff | FR-ORC-009..012, FR-ORC-020, FR-ORC-028, FR-ORC-029; **FR-URL-016 completed**; SC-011, SC-012, SC-016 |
| **9** | Release readiness | `policy-set-1.1.0` evaluated — twelve checks, all mandatory; nine blocking conditions each with a **negative** test; limitations and residual-risk disclosure; traceability matrix populated; final engineering summary assembled deterministically from recorded evidence | At least one mandatory policy **`FAIL` demonstrably blocks**; every summary claim traces to recorded evidence; **no undisclosed limitation** | FR-ORC-022, FR-ORC-025, FR-ORC-026, FR-ORC-027; SC-004, SC-010, SC-013, SC-015 |

---

## What may never be cut, at any checkpoint, by anyone

Stated here as well as in `checkpoints.md`, because this is the file consulted while working:

1. **Mandatory validation.** Every RED-FIRST task's captured red phase; every negative test; every contract,
   architecture, integration and orchestration assertion.
2. **Reviewer evidence.** The three scenarios, the out-of-scenario run, the audit reconstruction, and the committed
   run exports a reviewer reads without running anything.
3. **The two non-waivable halts.** Fabrication pressure and an unresolved mandatory policy `FAIL` (see
   `stop-conditions.md`).
4. **Disclosure.** A limitation may be disclosed; it may not be omitted. An omission relabelled as a design choice
   after the fact is the specific failure `baseline-omissions.md` exists to prevent.

## The one deliberate baseline omission

**Slice 3 builds two of FR-URL-016's three rate-limit tiers.** The per-creator aggregate redirect tier (PVT-014) is
**deferred to Slice 8's brownfield run**, where it is implemented under governance.

**It is not backlog and it is not reduced scope.** FR-URL-016 is binding in full. Its matrix reference reads
***partial*** until the brownfield run closes it, and **if that run does not happen, release readiness reports a real
gap** — it may not be relabelled as a design choice afterwards (CR-024, owner ruling 2026-09-20: a partially built
requirement never counts as complete; a recorded *partial* is legal, a silent one is not).

Written up per-entry in `baseline-omissions.md` (T055a), which must exist **before** Slice 3's acceptance sweep records
the aggregate case as passing unthrottled.

## Traceability

Every requirement class is delivered by at least one slice above, and the slice → task mapping lives in `tasks.md`'s
coverage map.

**Slice 1 delivers no functional requirement, and that is correct rather than a gap.** The engineering baseline delivers
**NFR-MNT-001 and NFR-MNT-002** (dependency direction and plane separation, enforced by an architecture test) and the
implementation of five accepted ADRs. Nothing in FR-URL or FR-ORC is satisfiable by a build file, a migration baseline
and a contract harness. It is named here explicitly because a reader checking *"which FRs does each slice deliver"*
would otherwise find one blank cell and have to guess whether it was an omission.

Two deliberate exclusions, both recorded rather than silent:

- **FR-ORC-015** (fallback) and **EC-023** — **retired**, Decision K / CR-032. No slice delivers them. Bounded retry
  then safe suspension is the entire degradation story, disclosed as a named absence in `docs/LIMITATIONS.md`.
- **CN-011, SC-014, NFR-AUT-004** — **retired**, Decision J / CR-028. No slice delivers a keyless orchestration run.
  What survives is narrower and still true: the shortener and the whole test suite run with no AI and no network.
