# Implementation Plan: Agentic Software Engineering System: URL Shortener

**Branch**: `main` (single linear history per CL-004) | **Date**: 2026-09-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-agentic-sdlc-url-shortener/spec.md`

**Governing constitution**: v1.1.0 (ratified 2026-09-17, last amended 2026-09-18)

**Policy version evaluated**: `policy-set-1.0.0` (defined in §9 of this plan)

**Status**: **APPROVED — 2026-09-20 by Pravallika Veeravalli**, at the Gate 4 closing package
(`docs/governance/gate-decisions/gate-04-closing-package.md`): *"This is the Human Architecture Approval of the
lifecycle."* Technology selections in §12 are **accepted** as ADR-001..005 (`docs/governance/adr/`), approved at
`gate-04-adr.md`. Further changes to this plan pass through change control.

---

## Summary

Build a locally runnable, production-*disciplined* prototype in two parts. The **application plane**
is a URL shortener that genuinely works: creator-authenticated link creation, public unauthenticated
redirects, timestamp-only analytics, expiry, idempotency, and health. The **control plane** is the
graded deliverable: a governed, stateful, non-linear orchestration engine that drives a twelve-stage
SDLC over an explicit persisted dependency graph, with human approval gates that cannot be satisfied
by silence, two-vote retry eligibility, a compensation register distinguishing rollback from
compensation, safe-stop as a suspended non-terminal state, resumption across both restart classes,
and audit evidence sufficient for a reviewer to reconstruct any run without the original session.

The approach is deliberately **not** distributed: one process, one relational store, one explicit
state machine. Production-grade discipline (contracts, migrations, policy gates, traceability,
executed evidence) is demonstrated without production-scale infrastructure, per Constitution VII's
prohibition on unjustifiable complexity.

---

## Technical Context

Every row is **ACCEPTED** at Gate 4 (2026-09-20), each by the ADR named in its Status column. Rationale,
alternatives, consequences, and reversibility for each are in the ADR itself; §12 carries the evaluation these
decisions came from.

| Field | Selected | Status |
|---|---|---|
| Language/Version | Java 21 (LTS) | ACCEPTED — ADR-001 |
| Primary Dependencies | Spring Boot 3.x (web, validation, data-jpa, actuator), Flyway, JUnit 5, Testcontainers, AssertJ, Awaitility | ACCEPTED — ADR-001, ADR-005 |
| Storage | PostgreSQL 16 via Docker Compose; disk-backed as **required** by CL-009 | ACCEPTED — ADR-002 |
| Testing | JUnit 5 + Testcontainers (real Postgres), OpenAPI response validation, deterministic fake executors | ACCEPTED — ADR-005 |
| Target Platform | Single JVM process, Linux/macOS developer machine; Docker for the store | ACCEPTED |
| Project Type | Web service plus embedded orchestration engine; one deployable, two internal planes | ACCEPTED |
| AI provider (AI-capable stages) | Anthropic Claude API behind an interface; **never required** — the run-level flag is `ai: on \| off`, default `off`, so the keyless path is the reviewer default (CN-011) | ACCEPTED — ADR-004 |
| Performance Goals | PVT-001 redirect p95 ≤ 50 ms; PVT-002 create p95 ≤ 200 ms; PVT-003 100 concurrent | **All 15 PVTs APPROVED** 2026-09-20 (CR-005) — now binding acceptance thresholds |
| Constraints | 2–3 day timebox (§14); no push; no distributed components; no production deployment (EX-007) | **APPROVED** 2026-09-20 |
| Scale/Scope | Demonstration scale only. Every measurement is a demonstration measurement (AS-009, Constitution IX) | Confirmed |

**Inputs that were unresolved when this plan was written — all now resolved** at the Gate 4 closing package:
**DF-001** (analytics exactness, resolved by ADR-014 and correcting this plan's provisional position),
**DF-002** (redirect permanence, CR-003), **DF-003** (retention boundary, CR-004), **DF-005** (PVT approvals,
timebox, MTTR rules, contract deliverables, CR-005). §Decisions Required at the end of this document retains
each provisional position alongside its disposition, including the one that was wrong.

---

## Constitution Check

*GATE: must pass before Phase 0. Re-checked after Phase 1 design — see §Post-Design Re-Check.*

Enumerated against **v1.1.0**, policy version `policy-set-1.0.0`, per the Amendment 001
carry-forward.

| Principle | Outcome | Justification |
|---|---|---|
| I. Specification Before Implementation | **PASS** | Spec approved at Gate 2, clarified at Gate 3. No code written. Every requirement carries a stable ID; 51 FRs trace bidirectionally. |
| II. Explicit Agentic Orchestration | **PASS** | §3 defines an explicit persisted DAG with 12 nodes, per-node entry/exit criteria, two fan-out/join points, conditional branching, replanning, and durable state. Not a call chain. |
| III. Human Governance | **PASS** | §5 defines 8 mandatory gate classes with the five outcomes; silence produces suspension, never progression; deadline disclosed in the ask (CL-005). |
| IV. Test-Driven Engineering | **PASS** | §10 assigns red-green-refactor to domain and orchestration-transition behavior, with the red phase recorded. All seven required test categories are planned and non-empty. |
| V. Security and Privacy by Design | **PASS** | §8 covers scheme allow-list, SSRF/internal-address handling, abuse cases, two-sided rate limiting, hashed credentials, no personal data (NFR-SEC-005), threat model. |
| VI. Compliance and Change-Control | **PASS** | §9 defines `policy-set-1.0.0` across 7 domains with mandatory/advisory split, four outcomes, exception workflow, and the 9 release-blocking conditions. |
| VII. Architecture and Maintainability | **PASS** | §1–2 separate the seven concerns; §12 rejects Temporal/Camunda precisely to avoid unjustified complexity. Single process, single store. |
| VIII. Reliability and Recovery | **PASS** | §6 implements CL-006's two-vote model, CL-007's compensation register, CL-005's suspension semantics, CL-009's dual-restart recovery. |
| IX. Observability and Auditability | **PASS** | §7 defines run correlation IDs, the six mandatory audit fields, and the MTTR method with declared population and exclusions. Demonstration measurements labelled as such. |
| X. Traceability and Repository Integrity | **PASS** | §13 defines the full ten-link chain. No fabricated evidence; provisional positions are labelled provisional. |
| XI. Evidence-Based Completion | **PASS** | §10 release-readiness checks and §14's minimum defensible outcome bind completion to executed evidence. |
| **Technology neutrality (CN-002)** | **PASS** | Satisfied as intended: no selection was made by this plan; §12 evaluated, and the ADR gate decided. ADR-001..014 were accepted 2026-09-20 (`gate-04-adr.md`), so implementation now proceeds on an **approved** stack as CN-002 requires. |

**No principle is `FAIL`.** The four items that were unapproved inputs when this check was first written — DF-001,
DF-002, DF-003, DF-005 — were dispositions rather than violations, and all are now resolved at the Gate 4 closing
package. This check is re-verified at `/speckit-analyze` per the constitution.

---

## 1. System Context

### Boundary

Inside: the URL shortener service, the orchestration engine, the policy engine, the audit store, the
evidence store, the operator provisioning script. Outside: the AI provider, the container runtime and
database process, git, the human owner.

### Actors

| Actor | Type | Authority |
|---|---|---|
| Link follower | Human/machine, anonymous | Follow short links only. Never authenticates (FR-URL-018). |
| Creator | Authenticated machine client | Create links; read **own** link analytics (FR-URL-011). |
| Operator | Human with shell access | Provision creators via local script. **Shell access is the trust boundary** (FR-URL-019). |
| Engineer | Human | Submit requirements, create and inspect runs, trigger replanning. |
| Reviewer / Approver | Human | Decide gates. Cannot be substituted by an agent at any autonomy setting. |
| Release owner | Human | Release readiness. Never self-certified by an agent (FR-ORC-025). |
| Assessment reviewer | Human, read-only | Reconstruct runs from artifacts alone. |
| Stage executor | Agent (AI-capable or deterministic) | Bounded per declaration; proposes, never rules (CL-006). |

### External dependencies and failure modes

| Dependency | Failure mode | Handling |
|---|---|---|
| PostgreSQL | Unavailable, restarting | `UNAVAILABLE` → proposed transient → bounded retry → suspension (CL-009). Disk-backed, so state survives. |
| AI provider | Unavailable, rate-limited, timeout, unusable output | `UNAVAILABLE`/`RATE_LIMITED`/`TIMEOUT`/`INTERNAL` in the envelope; fallback to the deterministic counterpart where declared, stamped `DETERMINISTIC` (FR-ORC-015). |
| Git (stage 7 effects) | Dirty tree, conflict | Permanent classification; rollback of local un-pushed work is the only erasable class (Compensation Register). |
| Clock | Skew | Single-host; timestamps from one source. Idle retention uses injectable clock for tests (EC-036). |

### Trust boundaries

1. **Public internet → redirect endpoint.** Unauthenticated by design. Input is a short code only.
2. **Creator client → creation/analytics API.** API key presented; validated against stored hash.
3. **Operator shell → provisioning script.** The ability to run commands on the host *is* the
   boundary; no HTTP issuance surface exists.
4. **Orchestrator → stage executor.** Executors are the governed party. They propose classifications
   and produce effects only through provided channels; anything else is refused and recorded as an
   autonomy violation (CL-007).
5. **Service → AI provider.** Untrusted output. AI text is never executed; stage 7 patches are applied
   on a branch and verified by the real test suite before anything is accepted (FR-ORC-031).

### Data ownership

| Data | Owner | Mutability |
|---|---|---|
| ShortLink, RedirectEvent, Creator, CreatorCredential, IdempotencyRecord | Application plane | Links mutable only to expire; events append-only |
| WorkflowRun, StageNode, DependencyEdge, TaskRecord, ReplanEvent | Control plane | State transitions only along allowed edges |
| AuditRecord, GateDecision, PolicyCheckResult, PolicyException | Governance | **Append-only, immutable** |
| Gate decision records, ADRs, change-control records | Repository (git) | Immutable once filed; corrected by superseding records |

### Plane responsibilities

**Application plane**: validation, normalization, code generation, persistence, redirect resolution,
analytics capture and retrieval, expiry, rate limiting, health/readiness, request telemetry.
Knows nothing about workflows.

**Control plane**: the stage graph and its persisted state, entry/exit criteria evaluation, executor
dispatch and bounding, failure classification rulings, retry/fallback/rollback/compensation, gates and
their deadlines, suspension and retention, replanning, policy evaluation, audit and evidence emission,
release-readiness determination, final summary assembly.

The control plane treats the application plane as *the codebase under governance* in DS-B, not as a
library it calls.

---

## 2. URL Shortener Architecture

### Components

| Component | Responsibility | Key requirements |
|---|---|---|
| API delivery | Request binding, auth filter, error mapping, rate-limit enforcement | FR-URL-018, FR-URL-016 |
| URL validation | Syntax, length, scheme allow-list, normalization, abuse checks | FR-URL-002..005 |
| Short-code generation | Alphabet + length, CSPRNG, collision retry under unique constraint | FR-URL-006, PVT-005 |
| Domain logic | Link lifecycle, expiry semantics, idempotency resolution | FR-URL-009, FR-URL-012 |
| Redirect resolution | Code lookup, expiry check, redirect emission, event append | FR-URL-007, FR-URL-008 |
| Persistence | Repositories behind interfaces; Flyway-versioned schema | NFR-MNT-002, CL-009 |
| Analytics | Append event **only via the analytics recording port** (ADR-014 Condition 2 — no bypass anywhere); retrieve time series scoped to owner | FR-URL-010, FR-URL-011 |
| Expiration | Expiry evaluation at read; compensation target for unwanted links | FR-URL-008, Compensation Register |
| Health/readiness | Liveness independent of store; readiness dependent on it | FR-URL-015 |
| Telemetry | Structured logs, metrics, traces; secret-safe | FR-URL-017, NFR-OBS-001 |
| Configuration | Secure defaults; throttling on by default; `ai: off` by default | FR-URL-016, CN-011 |

### Scalability: first bottleneck — NFR-SCA-003 discharged

NFR-SCA-003 requires the design to state which component becomes the first bottleneck and why.
**That obligation is discharged by [ADR-014](../../docs/governance/adr/ADR-014-analytics-consistency.md)
§Scale analysis**, designated as its recorded answer by the owner at ADR-014's acceptance (2026-09-20).

In summary: insert throughput is **not** the first constraint — a single PostgreSQL instance sustains
thousands to tens of thousands of small inserts per second, and 10k clicks/second is ~860M clicks/day. The
first bottleneck is **`redirect_event` table growth** — index bloat, retention deletes competing with hot-path
inserts, and analytical queries over an ever-growing table. Designed first mitigation, recorded but not built:
**time-partitioning with retention enforced by partition drop**, which makes 30-day retention an instant
metadata operation. Evolution ladder beyond that, out of demonstration scope: batched appends → asynchronous
pipeline (Kafka-class) → columnar analytics store (ClickHouse-class), each reachable behind the analytics
recording port.

Creation and resolution scale independently because they share no write path: creation writes `short_link` and
`idempotency_record`; resolution reads `short_link` and appends `redirect_event`.

### Versioned API and schema deliverables

Produced in Phase 1 (`contracts/`), all under explicit version control:

| Deliverable | Artifact | Version | Validation |
|---|---|---|---|
| API contract | `contracts/openapi.yaml` (OpenAPI 3.1) | `v1` path prefix; document `info.version` semver | Executable: live responses validated against the document in contract tests |
| Request/response/error schemas | Components within the OpenAPI document | With document | Same |
| Workflow-state schema | `contracts/workflow-state.schema.json` | semver | JSON Schema validation test over persisted snapshots |
| Approval schema | `contracts/approval.schema.json` | semver | Schema test + gate-record cross-check |
| Audit-event schema | `contracts/audit-event.schema.json` | semver | Schema test asserting all six mandatory fields present |
| Policy-evaluation schema | `contracts/policy-evaluation.schema.json` | semver | Schema test over the four outcome values |
| Persistence schema | `db/migration/V*.sql` (Flyway) | Sequential versions, forward-only | Migration applies from empty; Testcontainers-verified |

**Compatibility and migration rules.** Additive changes (new optional field, new endpoint) are MINOR
and backward compatible. Removing or narrowing a field, renaming, or changing a status code is MAJOR
and requires a new path version. Database migrations are forward-only; a column is deprecated before
removal across two versions. No breaking change ships without the change-control record in §9.

**Change-impact template.** Every API or schema change records: owner, version impact
(MAJOR/MINOR/PATCH), backward-compatibility impact, affected consumers, tests to update, docs to
update, rollout/migration steps, and the required change approval. Materialized under
`docs/governance/change-control/` per `CLAUDE.md`.

---

## 3. Agentic Orchestration Architecture

### Run state machine (CL-005)

```
PENDING ──► RUNNING ──► COMPLETED
               │  ▲         (terminal)
               │  └──── resume (human)
               ▼
           SAFE_STOP ──► ABANDONED   (terminal; human OR retention policy)
          (suspended,     
           NON-terminal)  
               │
               └────────► REJECTED   (terminal; human)
```

Terminal set is **exactly** `COMPLETED`, `REJECTED`, `ABANDONED`. `SAFE_STOP` is durable and
non-terminal. Only a human decision or the pre-approved retention policy moves a suspended run, and
retention-driven abandonment is never an approval (FR-ORC-032).

### Stage state machine

```
BLOCKED ─► READY ─► RUNNING ─┬─► SUCCEEDED
                             ├─► AWAITING_APPROVAL ─┬─► SUCCEEDED   (APPROVED)
                             │                      ├─► (run REJECTED)
                             │                      ├─► READY       (CHANGES-REQUESTED)
                             │                      └─► (run SAFE_STOP)  (TIMED-OUT / ESCALATED)
                             ├─► RETRY_WAIT ─► RUNNING          (two-vote pass, bound not exhausted)
                             ├─► FALLBACK ─► SUCCEEDED | FAILED
                             ├─► ROLLING_BACK ─► FAILED         (erasable effects only)
                             ├─► COMPENSATING ─► FAILED         (irreversible effects)
                             └─► FAILED ─► (run SAFE_STOP)
INVALIDATED  ◄── replanning (from any non-terminal stage state)
SKIPPED      ◄── conditional branch not taken (e.g. S4 when no ambiguity)
```

**Prohibited transitions, enforced and tested**: `RUNNING → SUCCEEDED` without exit criteria met;
`AWAITING_APPROVAL → SUCCEEDED` without a recorded human decision; `RETRY_WAIT → RUNNING` when the
bound is exhausted or either retry vote is absent; `FAILED → SUCCEEDED`; any transition out of
`SAFE_STOP` except by human decision or retention; `COMPENSATING` on an effect classed erasable;
`ROLLING_BACK` on an effect landing in an immutable store.

### Graph topology — dependencies, parallelism, branching

```
S1 Ingestion
   └─► S2 Normalization
          └─► S3 Ambiguity detection
                 ├──[material ambiguity]──► S4 Human clarification ──┐
                 └──[none: quality checks recorded, DS-A]───────────►│
                                                                     ▼
                                                            S5 Decomposition
                                                                     │
                                                            S6 Architecture & design
                                                                     │
                                                    ┌──── FAN-OUT per task ────┐
                                                    ▼         ▼         ▼
                                              S7.1 Impl   S7.2 Impl  S7.n Impl
                                                    └──── JOIN (all must succeed) ──┐
                                                                                    ▼
                                                                            S8 Testing (real suite)
                                                                                    │
                                                          ┌──── FAN-OUT ────┐
                                                          ▼                 ▼
                                                 S9 Documentation   S10 Security & policy
                                                          └──── JOIN ───────┘
                                                                     ▼
                                                        S11 Release readiness (gate)
                                                                     ▼
                                                        S12 Final engineering summary
```

Two fan-out/join points: task-level parallelism in S7, and the S9∥S10 pair. One conditional branch at
S3 — **S4 fires only on material ambiguity**, which is DS-A's explicit requirement that a well-formed
requirement must not meet an artificial gate.

### Per-node definitions

Common to all nodes: **audit events** `STAGE_ENTERED`, `STAGE_EXITED`, `CRITERIA_EVALUATED`,
`EXECUTOR_DISPATCHED`, `FAILURE_CLASSIFIED`, `RETRY_RULED`, plus node-specific events named below.
Every event carries the six mandatory fields (actor type, action, timestamp, affected artifact/state,
result, reason). Default retry policy is PVT-007 (3 attempts, exponential from 1 s) gated by the
two-vote rule; default timeout is per-stage and classified per CL-006 rule 4.

| # | Node | Purpose | Inputs → Outputs | Pre / Post | Actor | Timeout & retry | Failure class | Fallback |
|---|---|---|---|---|---|---|---|---|
| S1 | Ingestion | Admit a requirement, create the run | raw requirement → `WorkflowRun`, correlation ID | Pre: well-formed submission. Post: run persisted with ID | Deterministic engine | Short; `INTERNAL` permanent | Permanent on malformed input | None — refuse |
| S2 | Normalization | Identified, testable, typed requirements | raw → `RequirementRecord[]` | Pre: S1 ok. Post: every requirement has ID + type + testable statement | AI-capable | PVT-007; `TIMEOUT` retryable (idempotent) | Per envelope | Deterministic structural normalizer |
| S3 | Ambiguity detection | Find incompleteness, conflict, untestability | requirements → `AmbiguityRecord[]` + quality-check record | Pre: S2 ok. Post: checks recorded; if none, the **reason clarification was not required** is recorded | AI-capable | PVT-007; retryable | Per envelope | Deterministic rule checks |
| S4 | Human clarification | Resolve material ambiguity | ambiguities → `ClarificationDecision[]` | Pre: ≥1 material ambiguity. Post: every material item answered | **Human** | Gate wait PVT-006 → `SAFE_STOP`; no retry | N/A | None — suspension only |
| S5 | Decomposition | Dependency-ordered tasks | requirements → `TaskRecord[]` + edges | Pre: no unresolved material ambiguity. Post: every task traces to ≥1 requirement; no orphans | AI-capable | PVT-007; retryable | Per envelope | Deterministic template decomposition |
| S6 | Architecture & design | Design + API/schema impact | tasks → design doc, contract deltas | Pre: S5 ok. Post: impacted contracts identified | AI-capable | PVT-007; retryable | Per envelope | Deterministic impact analyzer |
| S7 | Implementation (fan-out) | Author and apply change per task | task + design → branch commit | Pre: task approved, design ok. Post: patch applied on branch, buildable | AI-capable, or **`HUMAN`** under no-plan gate option 2 | PVT-007; `TIMEOUT` **not** retryable (non-idempotent git effect) | Per envelope; conflict = permanent | Deterministic **plan-applying**; where **no change plan exists**, suspends at the no-plan gate — never no-ops forward (CR-001) |
| S8 | Testing | Execute the real suite | branch → test results | Pre: S7 join complete. Post: results recorded with pass/fail per test | Deterministic, **real** | Long timeout; retry only on `UNAVAILABLE` | Test failure = permanent (routes back to S7 within bound) | None — real results only |
| S9 | Documentation | Update docs with behavior | change + results → doc updates | Pre: S8 ok. Post: docs reflect delivered behavior | AI-capable | PVT-007; retryable | Per envelope | Deterministic doc stub generator |
| S10 | Security & policy | Evaluate `policy-set-1.0.0` | change + deps → `PolicyCheckResult[]` | Pre: S8 ok. Post: every applicable policy has one of four outcomes | Deterministic, **real** | Retry on `UNAVAILABLE` only | Unevaluable = **not** `PASS` (EC-025) | None — verdicts must be repeatable |
| S11 | Release readiness | Evaluate 9 blocking conditions, then human decision | all evidence → `ReleaseReadinessReport` + `GateDecision` | Pre: S9∥S10 join complete. Post: recorded human decision | Deterministic eval + **Human** | Gate wait PVT-006 → `SAFE_STOP` | N/A | None |
| S12 | Final summary | Assemble from recorded evidence only | evidence → summary artifact | Pre: S11 approved. Post: every claim traceable | Deterministic assembler | Short | Missing evidence = permanent | None — never invent content |

### Replanning

Trigger: a material change to an upstream artifact, or a clarification answer that alters a
requirement. Procedure: compute the affected downstream set from the graph; transition each to
`INVALIDATED`; **void any approval attached to an invalidated stage** (EC-020) by writing a
superseding gate record; re-plan the affected subgraph; leave unaffected paths untouched; emit
`REPLAN_EVENT` with cause, invalidated stages, and voided approvals. Cycle detection runs before the
new subgraph is committed (EC-030). An in-flight stage is not mutated mid-execution; it completes or
is cancelled at its next checkpoint (EC-019).

---

## 4. State and Decision Lineage

All of the following are persisted in the relational store, keyed by run correlation ID, and
reconstructable without the originating session (FR-ORC-023, NFR-OBS-002):

| Preserved | Where | Notes |
|---|---|---|
| Workflow instance state | `workflow_run`, `stage_node`, `dependency_edge` | Current state + full transition history |
| Normalized requirements | `requirement_record` | With type and testable statement |
| Task decomposition + dependencies | `task_record`, `task_edge` | Every task → ≥1 requirement |
| Decisions and assumptions | `clarification_decision`, `assumption_record` | Actor, question, answer, timestamp |
| Approvals and rejections | `gate_decision` (append-only) + repository gate records | Materialized per Amendment 001 |
| Artifact versions | `artifact_version` | Content hash + producing stage (provenance) |
| Test outcomes | `test_result` | Per test, with executed-at; generated-but-unexecuted is never a pass |
| Risk outcomes | `policy_check_result`, `policy_exception` | Four outcomes; exceptions carry all seven fields |
| Replanning history | `replan_event` | Cause, invalidated stages, voided approvals |
| Correlation identifiers | Every table + every log/metric/trace | Propagated end to end |
| Terminal status | `workflow_run.terminal_state` | Null while suspended — a suspended run has no terminal outcome (KE-04) |
| Failure/recovery events | `failure_event` | The MTTR population (§7) |

---

## 5. Human-in-the-Loop Controls

| Gate class | Trigger | Blocks | Outcomes |
|---|---|---|---|
| Unresolved ambiguity | S3 finds material ambiguity | S5 onward, affected path only | five standard |
| Architecture approval | S6 produces material design decisions | S7 | five standard |
| Security-sensitive action | Change touches auth, scheme list, credentials, telemetry redaction | Merge of that change | five standard |
| Destructive/irreversible action | Effect classed irreversible with no prior approval | That action | five standard |
| Constitutional exception | Any principle check not `PASS` | Downstream progression | five standard |
| Material risk acceptance | Residual risk above declared threshold | Release readiness | five standard |
| **No change plan at implementation** | Deterministic S7 reached with no change plan for the requirement | S8 onward | five standard, plus three named choices: governance-only / human-implemented / abandon (CR-001) |
| Release readiness | S11 | Submission | five standard |
| Final submission | Post-S12 | Submission | five standard |

**Uniform semantics**: outcomes are `APPROVED`, `REJECTED`, `CHANGES-REQUESTED`, `ESCALATED`,
`TIMED-OUT`. Every gate request states its expiry consequences up front — the gate-wait deadline at
which the run suspends, and the computed `auto-abandon-at` (CL-005 addendum). `TIMED-OUT` suspends;
it never advances. Every decision is materialized as a repository record before the work it authorizes
begins (Constitution §Gate semantics, v1.1.0). An agent cannot take any of these actions at any
autonomy setting (FR-ORC-021).

---

## 6. Reliability

Implements CL-006, CL-007, CL-005, CL-009 directly.

| Concern | Design |
|---|---|
| Failure envelope | Closed category set: `TIMEOUT`, `UNAVAILABLE`, `RATE_LIMITED`, `INVALID_INPUT`, `INTERNAL`, `UNKNOWN`; plus executor-**proposed** classification and detail. Executors translate provider errors into it. |
| Transient vs permanent | `retries = stage's declared retryable set ∩ executor proposal`. Either side vetoes. Executor has veto, never grant. |
| Unknown / malformed | **Permanent.** Default-deny, matching EC-025. Suspension path. |
| Retry bounds & backoff | PVT-007 (3 attempts, exponential from 1 s), per stage, individually recorded with a **two-signature** ruling (executor proposal + orchestrator ruling). |
| Timeout | Per-stage. Retryable **only** where the stage's design-time contract declares the effect idempotent/repeat-safe — never executor self-certification (S7 is explicitly not retryable on timeout). |
| Idempotency | Application plane: marker-based, three semantics (CL-008). Control plane: stage effects keyed so resumption cannot re-apply a committed effect. |
| Duplicate execution protection | Resumption reads persisted effect records before re-dispatch; concurrent resumption of one run is prevented by a run-level lease (EC-026). |
| Fallback | Declared per stage (table §3). Recorded **as fallback**, never as primary success. A failing fallback escalates to suspension (EC-023). |
| Rollback | Only for effects the Compensation Register classes erasable — local un-pushed work. |
| Compensation | For irreversible effects, via the register's named action: superseding gate record, appended audit correction, link set to expired. Applied at most once per effect even if a retry later succeeds (EC-022). |
| Safe-stop | Suspended, non-terminal, resumable, reason recorded, deadlines disclosed. |
| Resumption | Across orchestrator-process **and** persistence-layer restart. Deterministic terminal outcome; committed effects exactly once. |
| Partial failure | A join does not proceed while any branch is incomplete or failed (EC-018). Siblings' successes are preserved, not discarded. |
| Dependency unavailability | Store: `UNAVAILABLE` → transient → retry → suspend. AI provider: fallback to deterministic where declared. |

---

## 7. Observability

### Signals

| Signal | Content |
|---|---|
| Logs | Structured JSON, run correlation ID on every line, secret-safe (FR-URL-017) |
| Metrics | Counters and timers per §Measurements below |
| Traces | Span per stage execution and per retry attempt, parented to the run |
| Audit events | Append-only, six mandatory fields, immutable (NFR-AUD-001) |
| State-transition history | Every stage transition with from/to/reason |
| Decision history | Clarifications and gate decisions with actor and timestamp |
| Approval history | Gate decisions plus their materialized repository records |
| Retry evidence | Per-attempt record with the two-signature ruling |
| Compensation evidence | Effect, reversibility class, action taken, labelled rollback **or** compensation |
| Replanning evidence | Cause, invalidated stages, voided approvals |

### Measurements

| Measure | Definition |
|---|---|
| Workflow success rate | runs reaching `COMPLETED` ÷ runs reaching a terminal state |
| Failure rate | runs reaching `REJECTED` or `ABANDONED`, plus runs suspended on failure, each reported separately |
| Retry frequency | retry attempts ÷ stage executions |
| Rollback/compensation frequency | each counted separately ÷ stage executions |
| End-to-end latency | run creation → terminal state, **with suspended time reported separately** |
| Unrecovered failure count | reported on its own, never folded into MTTR |

### Mean Time to Recovery

```
MTTR = Σ (recovery_completed_at − failure_detected_at) over RECOVERED failure events
       ─────────────────────────────────────────────────────────────────────────────
                        count(RECOVERED failure events)
```

Each `failure_event` row captures: `failure_detected_at`, `recovery_started_at`,
`recovery_completed_at`, `individual_recovery_duration`, `recovery_mechanism`
(`retry` | `fallback` | `rollback` | `compensation` | `resume` | `human`), `recovered` (boolean),
`run_id`, `stage_id`, `failure_category`.

- **Population**: failure events in demonstration runs, scenarios DS-A/B/C plus the reliability suite.
- **Denominator**: recovered events only. **Unrecovered failures are excluded from the denominator and
  reported separately as a count**, per the mandated method.
- **Declared exclusion — human wait time.** Where a recovery required a human decision, time in
  `AWAITING_APPROVAL` or `SAFE_STOP` is **excluded** from the recovery duration, because including it
  would measure reviewer latency rather than system recovery and would make MTTR a function of when a
  person happened to be at a keyboard. Human decision latency is reported as its own separate metric.
  This exclusion is declared, not silent.
- **Demonstration-data limitations.** All figures come from a single developer machine with injected
  faults, small populations (single-digit to low-double-digit event counts), and possibly compressed
  time parameters labelled per AS-007. **They are demonstration measurements and must never be
  presented as production statistics** (Constitution IX, NFR-AUT-003).

---

## 8. Security

| Area | Design |
|---|---|
| Input validation | Syntax, length ceiling, scheme allow-list, normalization before storage and before validation decisions |
| Allowed schemes | Explicit allow-list; **non-waivable** (FR-URL-004). No config or exception path can widen it |
| Malicious redirect | Destination stored verbatim and never recomputed from request input at resolution time |
| Internal/local addresses | Private, loopback, and link-local targets refused or recorded as accepted risk (FR-URL-005) |
| Abuse cases | Redirect loops through own space, credential-bearing authority components, high-rate single-code traffic |
| Rate limiting | Two-sided, two-tier: per-creator creation (PVT-012); per-code redirect (PVT-013); per-creator-aggregate redirect (PVT-014). Noisy-neighbor trade-off documented as accepted |
| Authentication | Creators authenticate for create/analytics; redirects never authenticate |
| Secrets management | Keys provisioned by operator script, stored **only as hashes**, never committed, never written by the application at any log level including startup |
| Dependency risk | Vulnerability scan in release readiness; approved-dependency policy in §9 |
| Audit integrity | Append-only, immutable, six mandatory fields; corrections are new entries |
| Least privilege | Store credentials scoped to the application schema; no superuser at runtime |
| Secure defaults | Throttling on; `ai: off`; verbose error detail off; readiness fails closed |
| Threat model | Deliverable in Phase 1 (`research.md` §Threat Model), covering the trust boundaries in §1 plus the two named accepted trade-offs |

---

## 9. Compliance and Change Control

### `policy-set-1.0.0`

| ID | Domain | Mandatory? | Evaluation input → output |
|---|---|---|---|
| `POL-SEC-001` | Security | **Mandatory** | Scheme allow-list present and non-empty → PASS/FAIL |
| `POL-SEC-002` | Security | **Mandatory** | Secret scan over repo + telemetry → PASS/FAIL |
| `POL-SEC-003` | Security | **Mandatory** | Dependency vulnerability scan → PASS/FAIL |
| `POL-PRIV-001` | Privacy | **Mandatory** | Stored schema contains no personal-data field → PASS/FAIL |
| `POL-AUD-001` | Audit retention | **Mandatory** | Every audit record has all six fields → PASS/FAIL |
| `POL-AUD-002` | Audit retention | Advisory | Retention configured within declared bounds → PASS/FAIL |
| `POL-DEP-001` | Approved dependencies | **Mandatory** | All dependencies on the approved list → PASS/FAIL |
| `POL-LIC-001` | Licensing | **Mandatory** | All dependency licences on the permitted list → PASS/FAIL |
| `POL-CHG-001` | Change control | **Mandatory** | Contract/schema change has a change-control record → PASS/FAIL |
| `POL-CHG-002` | Change control | **Mandatory** | Every mandatory gate has a materialized repository record → PASS/FAIL |
| `POL-TST-001` | Compliance | **Mandatory** | Required test categories non-empty and executed → PASS/FAIL |
| `POL-TRC-001` | Compliance | **Mandatory** | Zero traceability orphans → PASS/FAIL |

Outcomes are exactly `PASS`, `FAIL`, `EXCEPTION-REQUESTED`, `NOT-APPLICABLE`. **Unevaluable is never
`PASS`** (EC-025). Every run records the policy set version it evaluated.

### Workflows

**Compliance check** is stage S10 — deterministic and real, because verdicts must be repeatable.

**Change request**: material change to requirements, architecture, contracts, schemas, security
controls, or release criteria → impact analysis across the seven dimensions → human change approval →
version increments → revalidation of affected tests and docs. Materialized under
`docs/governance/change-control/`.

**Policy exception**: proposal records policy, precise clause, reason, scope, duration, compensating
control, residual risk → explicit human approval recording approving authority, timestamp, and expiry
or review condition → materialized under `docs/governance/exceptions/`. An unapproved or **expired**
exception is a violation and blocks progression (EC-027).

### How a failed mandatory policy blocks release

S10 writes `PolicyCheckResult` rows. S11's evaluator reads them and applies the nine conditions; a
mandatory `FAIL`, or an `EXCEPTION-REQUESTED` without recorded approval, or an expired exception, sets
the report to **blocking** and names the policy, the check, and the outcome. The release owner cannot
be presented with a pass while any blocking condition holds, and **no agent may self-certify**
(FR-ORC-025). Demonstrated by one negative test per condition (SC-009).

### Upstream change → downstream impact

A change to an approved artifact triggers the replanning procedure in §3: affected downstream stages
`INVALIDATED`, approvals voided by superseding record, subgraph re-planned, `REPLAN_EVENT` emitted.
Contracts, schemas, tests, and docs are re-versioned and revalidated as part of the invalidated
stages' re-execution — never left stale (Constitution §Workflow).

---

## 10. Testing

Red-green-refactor applies to all domain behavior and all orchestration transition behavior; the red
phase is **recorded** (NFR-TST-002). Where TDD is impractical the reason is recorded against the task
and executed validation is still required.

| Category | Coverage |
|---|---|
| Domain unit | Validation, normalization idempotency, scheme allow-list matrix, code generation, expiry boundary, idempotency resolution |
| API contract | Live responses validated against `openapi.yaml`; all error shapes; **executable**, not review-based |
| Persistence | Migration from empty; unique constraint under concurrency; repository round-trips (Testcontainers, real store) |
| Integration | Create → resolve → analytics across real store and API |
| Orchestration state transition | Every allowed transition; **every prohibited transition rejected** |
| Approval & rejection | One test per outcome including the **silence test** producing suspension, not progression |
| Retry | Two-vote matrix: both yes; declared-only; executor-only; unknown category; malformed envelope |
| Timeout | Timeout on idempotent stage retries; timeout on S7 (non-idempotent) does **not** retry |
| Fallback | Activation recorded as fallback; failing fallback escalates to suspension |
| Rollback / compensation | Separately labelled; register rows exercised; double-compensation prevented; registration failure when compensating action unnamed; contradictory declaration flagged |
| Safe-stop | Each trigger class; suspension asserted **non-terminal**; terminal set asserted to be exactly three |
| Resumption | Interruption at every stage boundary (process kill); persistence-layer restart mid-run; concurrent-resume prevention |
| Replanning | Affected-only invalidation; approval voiding; unaffected paths untouched; cycle rejection |
| Concurrency | Concurrent creation (code uniqueness), concurrent resolution, PVT-003 load |
| Security | Scheme matrix, SSRF/internal targets, secret scan over telemetry, credential-in-URL non-logging, rate-limit tiers |
| End-to-end | DS-A, DS-B, DS-C complete runs producing evidence |
| Release-readiness | One negative test per each of the nine blocking conditions |

**Executor injection** (FR-ORC-030): scriptable fakes (`fail twice then succeed`, `timeout`, `return
malformed envelope`) make every reliability proof deterministic and independent of AI availability and
network. The reliability suite must pass with **no AI key and no network** (NFR-AUT-004, SC-014).

---

## 11. Scenario Designs

### DS-A — Greenfield

- **Input**: a complete, consistent, testable requirement inside policy and architecture bounds —
  proposed: *"Expose the remaining time-to-expiry for a short link to its owning creator."*
- **Interpretation**: S2 normalizes to one functional requirement; S3 records the quality checks
  performed **and the explicit reason clarification was not required**.
- **Decomposition**: S5 yields domain calculation, API field, contract update, tests, docs.
- **Path**: S1→S2→S3→**S4 SKIPPED**→S5→S6→S7 (fan-out)→S8→S9∥S10→S11→S12.
- **Approvals**: architecture (S6), release readiness (S11). **No clarification gate fires.**
- **Failure paths**: none injected. A late-emerging ambiguity would suspend only the affected path.
- **Validation**: real suite green; contract validation passes.
- **Evidence**: quality-check record, no-clarification justification, decomposition, contract delta,
  executed results, traceability matrix, per-stage executor mode labels.
- **Terminal outcome**: `COMPLETED`.

### DS-B — Brownfield

- **Input**: a change against existing shortener code — proposed: *"Redirect analytics must not lose
  events under concurrent load"* (defect/enhancement against FR-URL-010, and the natural home for
  DF-001's resolution).
- **Interpretation**: S2 normalizes; S3 finds no material ambiguity **if** DF-001 is disposed of
  first — otherwise this scenario legitimately becomes ambiguous, which is a useful property, not a
  problem.
- **Decomposition**: impact analysis precedes any code change.
- **Path**: as DS-A, with S6 producing the **seven-dimension impact analysis** (components,
  interfaces, data flows, tests, documentation, regression risks, rollout/rollback) whose timestamp
  **precedes** the first modification, and with an injected transient fault in S7 exercising retry and
  a compensation case exercising the register.
- **Approvals**: architecture, security-sensitive (analytics path), release readiness.
- **Failure paths**: transient `UNAVAILABLE` → two-vote retry → success; one irreversible effect →
  compensation, labelled as compensation.
- **Validation**: pre-existing tests detect the regression; new concurrency test proves the fix.
- **Evidence**: impact analysis with ordering proof, retry two-signature records, compensation record,
  before/after test results.
- **Terminal outcome**: `COMPLETED`.

### DS-C — Ambiguous requirement

- **Input**: internally conflicting — proposed: *"Links should expire after a week but remain
  available for historical analytics indefinitely, and expired links should still redirect for
  trusted partners."*
- **Interpretation**: S3 flags the conflict, naming the conflicting elements; unsafe implementation is
  prevented.
- **Decomposition**: deferred on the affected path only.
- **Path**: S1→S2→S3→**S4 fires**→ suspension awaiting human → clarification recorded → impact
  analysis → **replan** of exactly the affected downstream artifacts → resume → S5..S12. Unaffected
  paths continue throughout.
- **Approvals**: clarification (S4), architecture, release readiness.
- **Failure paths**: demonstrates the **silence path** first — no answer within the gate wait →
  `SAFE_STOP` with deadlines disclosed in the original ask → then resumption after the answer.
- **Validation**: post-replan suite green; voided approvals proven not carried forward.
- **Evidence**: ambiguity record, clarification decision, replan event with voided approvals,
  suspension and resumption records.
- **Terminal outcome**: `COMPLETED` (a rejection variant is also exercised in the gate tests).

---

## 12. Technology Decisions — PROPOSED, pending ADR gate

Presented as candidate ADRs. **None is selected.** `DECISION-LOG` entry #1's Java intent is treated as
a candidate, not settled, per Gate 1's explicit carry-forward.

### ADR-001 — Language and framework

- **Question**: what runtime and web framework?
- **Options**: (a) Java 21 + Spring Boot 3; (b) Python 3.12 + FastAPI; (c) TypeScript + NestJS; (d) Go.
- **Criteria**: interview defensibility for the owner, transactional persistence maturity, test
  tooling (real-store integration, concurrency), timebox velocity, reviewer familiarity.
- **Recommended**: **(a) Java 21 + Spring Boot 3.**
- **Rationale**: deepest stack for the owner, which matters because this artifact will be defended
  live; strongest transactional and locking story for FR-URL-006/013; Testcontainers makes real-store
  testing routine; Flyway gives first-class versioned migrations.
- **Consequences**: more ceremony per feature; slower raw velocity against a 2–3 day box.
- **Risks**: verbosity consumes timebox. **Mitigation**: §14's stop conditions cut AI-mode breadth
  before cutting governance or evidence.
- **Honest counter-case**: (b) FastAPI would likely deliver the same functional surface faster. It is
  recommended against **only** because defensibility outweighs velocity here — if the owner disagrees,
  (b) is a legitimate choice and the architecture in §1–11 survives the swap.
- **Reversibility**: low once implementation starts. **Decide before Slice 1.**
- **Validation**: walking skeleton (Slice 2) proves the stack end to end within hours.

### ADR-002 — Persistence

- **Question**: what store, given CL-009 mandates disk-backed and persistence-restart survival?
- **Options**: (a) PostgreSQL 16 in Docker; (b) SQLite file; (c) H2 file mode.
- **Criteria**: durability across restart, real concurrency semantics, unique-constraint behavior
  under contention, test ergonomics, setup burden on a reviewer.
- **Recommended**: **(a) PostgreSQL 16 via Docker Compose**, with Testcontainers in tests.
- **Rationale**: CL-009's restart test needs a restartable store process; FR-URL-006's concurrent
  uniqueness needs real constraint enforcement under contention, which SQLite's coarse write locking
  would mask rather than exercise.
- **Consequences**: reviewers need Docker. **Mitigation**: documented in `quickstart.md`.
- **Risks**: container startup adds test time. **Reversibility**: high — behind repository interfaces.
- **Validation**: migration-from-empty test plus the persistence-restart resumption test.

### ADR-003 — Orchestration engine

- **Question**: build the state machine or adopt one?
- **Options**: (a) purpose-built explicit persisted DAG; (b) Temporal; (c) Camunda/BPMN; (d) Spring
  StateMachine.
- **Criteria**: is the orchestration model the graded artifact, demonstrability of governance,
  complexity justification under Constitution VII, timebox.
- **Recommended**: **(a) purpose-built.**
- **Rationale**: the orchestration model **is** the assessed deliverable — adopting Temporal would
  outsource the graded artifact and leave the candidate demonstrating configuration rather than
  design. (b) and (c) also add infrastructure whose complexity cannot be justified by the
  requirements, which Constitution VII would score as a `FAIL`.
- **Consequences**: we own correctness of transitions, persistence, and resumption. That is the point.
- **Risks**: reinventing subtly wrong semantics. **Mitigation**: prohibited-transition tests and the
  explicit state machines in §3.
- **Reversibility**: moderate. **Validation**: the state-transition and resumption suites.

### ADR-004 — AI provider for AI-capable stages

- **Question**: which provider, and how is it bounded?
- **Options**: (a) Anthropic Claude API; (b) OpenAI; (c) local model; (d) none (deterministic only).
- **Criteria**: quality on code-authoring and analysis, availability of a clean interface boundary,
  cost, and the hard constraint CN-011 that the reviewer default must run with no key.
- **Recommended**: **(a) Anthropic Claude API** behind a provider interface, with the latest available
  Claude model selected at implementation time; the run-level flag is `ai: on | off` and defaults to `off`.
- **Rationale**: (d) alone would make the "agentic" claim hollow (CL-003). Keeping the provider behind
  an interface satisfies NFR-MNT-002 and keeps (b)/(c) open.
- **Consequences**: an API key and cost for demonstration runs only.
- **Risks**: non-determinism in graded runs. **Mitigation**: reliability proofs use injected fakes, never
  the live provider (FR-ORC-030).
- **Reversibility**: high. **Validation**: SC-014 — full run with no key, no network.

### ADR-005 — Contract validation approach

- **Question**: how is the API contract made executable rather than decorative?
- **Options**: (a) OpenAPI document as source of truth + runtime response validation in tests;
  (b) generate code from the spec; (c) generate the spec from code.
- **Criteria**: does it actually fail a build on drift; effort; clarity of the versioned deliverable.
- **Recommended**: **(a).**
- **Rationale**: (c) cannot detect drift — the spec becomes whatever the code says, which makes the
  contract unfalsifiable. (b) is heavier than the timebox warrants.
- **Consequences**: the document is hand-maintained and the test enforces conformance.
- **Reversibility**: high. **Validation**: a deliberate drift is introduced and the contract test must fail.

---

## 13. Traceability

Chain: **Requirement → Scenario → Design → ADR → Task → Code → Test → Validation → Documentation →
Evidence.**

Implementation: the spec's traceability matrix already binds Requirement → Journey → Scenario → Edge
cases, with Task/Test/Evidence columns reserved. This plan adds the Design and ADR columns. `tasks.md`
fills Task. Implementation fills Code, Test, Validation, Documentation, Evidence. A generated
traceability report asserts **zero orphans in both directions** and is enforced by `POL-TRC-001` as a
mandatory release-blocking policy — so an incomplete chain cannot reach release readiness.

---

## 14. Delivery Sequence

**Timebox: 2–3 days — APPROVED 2026-09-20** at the Gate 4 closing package, with the milestones, checkpoints and stop conditions below. Sequenced as vertical slices; each ends with
executed tests and committed evidence, never with unverified code.

| Slice | Content | Milestone | Must-have? |
|---|---|---|---|
| 1 Engineering baseline | Build, layout, Flyway, OpenAPI skeleton, contract-test harness, CI-equivalent local script | **Day 1 AM** | Yes |
| 2 Walking skeleton | One endpoint + health + real store round-trip, end to end | **Day 1 AM** | Yes |
| 3 Core URL behavior | TDD: validation, scheme list, code gen + uniqueness, redirect, expiry, idempotency (3 semantics), analytics, rate limiting, provisioning script | **Day 1 PM** | Yes |
| 4 Orchestration state model | Persisted DAG, 12 nodes, transitions, prohibited-transition enforcement, inspection | **Day 2 AM** | Yes |
| 5 Approval governance | Gates, five outcomes, deadline-in-ask, silence→suspension, retention/auto-abandon | **Day 2 midday** | Yes |
| 6 Reliability controls | Envelope, two-vote retry, timeout gating, fallback, Compensation Register, resume across both restart classes | **Day 2 PM** | Yes |
| 7 Observability | Audit events, correlation IDs, metrics, `failure_event` capture, MTTR calculation | **Day 3 AM** | Yes |
| 8 Three scenarios | DS-A, DS-B, DS-C executed with evidence | **Day 3 midday** | Yes |
| 9 Release readiness | Policy evaluation, 9 blocking conditions with negative tests, final engineering summary | **Day 3 PM** | Yes |

**Critical path**: 1 → 2 → 4 → 5 → 6 → 8 → 9. Slice 3 is required as DS-B's subject matter. Slice 7 is
partially parallelizable with 6 but its `failure_event` capture must exist before Slice 8 runs, or the
scenarios produce no MTTR population.

**Scope-control checkpoints** — at each, compare progress to the milestone and cut from the backlog
only, never from mandatory validation or reviewer evidence:

| Checkpoint | If behind |
|---|---|
| End Day 1 | Reduce Slice 3 to the FR-URL requirements DS-B needs; defer rate-limit tier sophistication |
| End Day 2 AM | Reduce AI-capable stages from six to **two** (S2 normalization, S7 implementation); the rest run deterministic. Mode labelling makes this honest and visible, not hidden |
| End Day 2 PM | Cut DS-B's injected compensation case to a unit-level proof; keep the scenario |
| End Day 3 AM | Reduce MTTR population to the reliability suite only, and say so in the declared population |

**Backlog — explicitly deferred, never allowed to displace mandatory work**: run-level retry circuit
breaker (DF-004); email/webhook expiry notification (DF-004); AI participation for S3, S5, S6, S9; custom
aliases, link deletion/editing, multi-region (EX-001..003); analytics beyond time series.

**Stop conditions** — hard stops requiring an owner decision rather than silent continuation:

1. Slice 4 incomplete by end of Day 2 → stop feature work; the orchestration model is the graded
   artifact and cannot be traded away.
2. Any mandatory policy `FAIL` unresolved at Slice 9 → release readiness blocks; report blocked
   rather than weaken the check (Constitution XI is non-waivable).
3. Fabrication pressure — any temptation to present a proposed or simulated result as executed →
   stop and report. Non-waivable (Principle X).
4. Timebox exhausted with slices incomplete → report exactly what is done, what is not, and why;
   scaling scope down is the owner's call, not the agent's.

**Minimum defensible release-readiness outcome**: working URL shortener with executed domain tests;
the persisted twelve-node graph with prohibited-transition enforcement; at least one real human gate
with a passing silence test; two-vote retry plus one compensation and one resumption proof; audit
records sufficient to reconstruct one run end to end; all three scenarios executed even if DS-B's
fault injection is reduced; policy evaluation with at least one demonstrated blocking `FAIL`; and a
final summary whose every claim traces to recorded evidence. Anything less is reported as incomplete
rather than relabelled as done.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-agentic-sdlc-url-shortener/
├── spec.md              # Approved Gate 2, clarified Gate 3
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # /speckit-tasks — NOT created here
```

### Source Code (repository root)

Layout is technology-neutral in shape; the concrete build files depend on ADR-001.

```text
src/main/
├── domain/              # Link, Creator, code generation, validation, expiry — no framework deps
├── application/         # Use cases: create, resolve, analytics, provision
├── delivery/            # HTTP endpoints, auth filter, rate limiting, error mapping
├── persistence/         # Repository implementations, Flyway migrations
├── orchestration/
│   ├── graph/           # StageNode, DependencyEdge, cycle detection
│   ├── state/           # Run and stage state machines, allowed/prohibited transitions
│   ├── executor/        # Executor interface, deterministic engines, AI-capable adapters, fakes
│   ├── reliability/     # Envelope, two-vote ruling, retry, fallback, compensation register
│   ├── gates/           # Gate definitions, outcomes, deadlines, retention
│   └── replan/          # Impact computation, invalidation, approval voiding
├── policy/              # policy-set-1.0.0 evaluation, exceptions, release readiness
├── audit/               # Append-only audit, failure_event, MTTR calculation
└── config/              # Secure defaults, mode selection

tests/
├── unit/                # Domain, state machine, classification
├── contract/            # OpenAPI conformance, JSON Schema validation
├── integration/         # Real store via Testcontainers
├── orchestration/       # Transitions, gates, retry, fallback, compensation, resume, replan
├── security/            # Schemes, SSRF, secrets, rate limits
├── concurrency/         # Uniqueness, analytics under load
└── e2e/                 # DS-A, DS-B, DS-C

ops/scripts/             # Operator provisioning script (no HTTP surface)
docs/governance/         # Gate records, ADRs, exceptions, change-control
```

**Structure Decision**: single deployable with an internal plane split — `domain`/`application`/
`delivery`/`persistence` for the application plane, `orchestration`/`policy`/`audit` for the control
plane. Domain has no framework or persistence dependency, satisfying NFR-MNT-001's dependency-direction
check. One project, not three: there is no second consumer to justify module extraction, and
Constitution VII forbids complexity the requirements do not demand.

---

## Post-Design Re-Check (Constitution, after Phase 1)

Re-evaluated against v1.1.0 after `research.md`, `data-model.md`, `contracts/`, and `quickstart.md`:
**all twelve principles remain `PASS`.** The design introduced no new component whose complexity is
unjustified by a requirement; the single deliberate build-versus-adopt decision (ADR-003) is justified
by the orchestration model being the graded artifact rather than by preference.

## Complexity Tracking

No constitution violations requiring justification. Recorded for transparency: building the
orchestration engine rather than adopting one is a deliberate complexity **acceptance**, justified in
ADR-003 — adopting Temporal or Camunda would reduce authored complexity while outsourcing the assessed
deliverable and adding unjustifiable infrastructure.

---

## Decisions Required at the Plan Gate — **ALL RESOLVED 2026-09-20**

Every item below was decided at the Gate 4 closing package. Retained with its disposition rather than deleted,
so the provisional positions this plan held — and where one of them was **wrong** — stay visible.

| # | Decision | Disposition |
|---|---|---|
| 1 | **DF-001** analytics exactness | **RESOLVED by ADR-014, correcting this plan.** The provisional position recorded here — synchronous and transactional *with* the redirect — was **wrong**: a same-transaction append means a failed append fails the redirect, which EC-012 explicitly prohibits. Resolved as a **separate transaction** with failure isolated and counted, and PVT-009 redefined as an append-failure-rate ceiling rather than a loss budget |
| 2 | **DF-002** redirect permanence | **RESOLVED — temporary, never permanent** (CR-003). The provisional position was correct and is now the approved requirement in FR-URL-007 |
| 3 | **DF-003** audit vs idle retention boundary | **RESOLVED — clock starts at run termination** (CR-004). Provisional position confirmed; chosen over the alternative because it holds for any pair of retention values |
| 4 | **ADR-001..005** technology selections | **ACCEPTED** at `gate-04-adr.md`, together with ADR-006..014 |
| 5 | **All 15 PVT values** | **APPROVED** (CR-005). They now constrain implementation as acceptance thresholds |
| 6 | **2–3 day timebox and §14 scope controls** | **APPROVED** — milestones, four checkpoints, four stop conditions, cuts hit backlog before evidence |
| 7 | MTTR method, population, **declared human-wait exclusion** | **APPROVED** as specified in §7 |
| 8 | Versioned API/schema deliverables and executable contract validation | **APPROVED** (§2, ADR-005). Parser validation executed 2026-09-20 — one real defect found and fixed; meta-schema lint remains a Slice 1 task |
