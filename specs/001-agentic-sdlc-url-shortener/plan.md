# Implementation Plan: Agentic Software Engineering System: URL Shortener

**Branch**: `main` (single linear history per CL-004) | **Date**: 2026-09-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-agentic-sdlc-url-shortener/spec.md`

**Governing constitution**: v1.1.0 (ratified 2026-09-17, last amended 2026-09-18)

**Policy version evaluated**: `policy-set-1.1.0` (defined in §9 of this plan)

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
| AI provider (AI-capable stages) | Anthropic Claude behind an owned interface; **transport is the local Claude Code CLI in headless mode** (ADR-004-A1), with the SDK adapter recorded as the production alternative behind the same seam. **Always on.** A fresh orchestration run requires an authenticated Claude Code CLI (ADR-004-A2); there is no keyless mode and no run-level switch. **The test suite and the shortener need nothing** — reliability proofs use injected fakes (FR-ORC-030) | ACCEPTED — ADR-004, amended by ADR-004-A1 (CR-008) and ADR-004-A2 (CR-028) |
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

Enumerated against **v1.1.0**, policy version `policy-set-1.1.0`, per the Amendment 001
carry-forward.

| Principle | Outcome | Justification |
|---|---|---|
| I. Specification Before Implementation | **PASS** | Spec approved at Gate 2, clarified at Gate 3. No code written. Every requirement carries a stable ID; 51 FRs trace bidirectionally. |
| II. Explicit Agentic Orchestration | **PASS** | §3 defines an explicit persisted DAG with 12 nodes, per-node entry/exit criteria, two fan-out/join points, conditional branching, replanning, and durable state. Not a call chain. |
| III. Human Governance | **PASS** | §5 defines **10** mandatory gate classes with the five outcomes; silence produces suspension, never progression; deadline disclosed in the ask (CL-005). |
| IV. Test-Driven Engineering | **PASS** | §10 assigns red-green-refactor to domain and orchestration-transition behavior, with the red phase recorded. All seven required test categories are planned and non-empty. |
| V. Security and Privacy by Design | **PASS** | §8 covers scheme allow-list, SSRF/internal-address handling, abuse cases, two-sided rate limiting, hashed credentials, no personal data (NFR-SEC-005), threat model. |
| VI. Compliance and Change-Control | **PASS** | §9 defines `policy-set-1.1.0` across 7 domains, four outcomes, exception workflow, and the 9 release-blocking conditions. **Twelve checks, all mandatory**: `POL-CHG-003` added so the major-version rule is enforced rather than described (CR-013), and `POL-AUD-002` removed because with no retention path it could never meaningfully evaluate (CR-017). The mandatory/advisory split remains defined and currently has no advisory instance. |
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
| Engineer | Human | Submit requirements, create and inspect runs, trigger replanning. Reached under the **machine-access** boundary — **never a creator credential** (CN-012). |
| Reviewer / Approver | Human | Decide gates. **No workflow step submits or satisfies a gate decision** — the control is that the code path does not exist, asserted by T063’s architecture rule. Actor identity on a submitted decision is **declared, not verified** (FR-ORC-021’s declared limitation): an impersonating caller with process access is not detectable, and verified actor identity is out of scope for this demonstration. Records decisions through the governance surface under the machine-access boundary; holds **no** creator credential (CN-012). |
| Release owner | Human | Release readiness. Never self-certified by an agent (FR-ORC-025). Same boundary as Reviewer (CN-012). |
| Assessment reviewer | Human, read-only | Reconstruct runs from artifacts alone. |
| Stage executor | Agent (AI-capable or deterministic) | Bounded per declaration; proposes, never rules (CL-006). **No mode selects between them** — each stage’s executor class is fixed by the approved executor map (ADR-004-A2). |

### External dependencies and failure modes

| Dependency | Failure mode | Handling |
|---|---|---|
| PostgreSQL | Unavailable, restarting | `UNAVAILABLE` → proposed transient → bounded retry → suspension (CL-009). Disk-backed, so state survives. |
| AI provider | Unavailable, rate-limited, timeout, unusable output | `UNAVAILABLE`/`RATE_LIMITED`/`TIMEOUT`/`INTERNAL` in the envelope; **bounded retry per the node’s declared retryable set** (§3), then **safe suspension** on exhaustion. **No fallback** — FR-ORC-015 retired, Decision K (CR-032); `INTERNAL` is permanent and must surface, never be re-rolled. |
| Git (stage 7 effects) | Dirty tree, conflict | Permanent classification; rollback of local un-pushed work is the only erasable class (Compensation Register). |
| Clock | Skew | Single-host; timestamps from one source. Idle retention uses injectable clock for tests (EC-036). |

### Trust boundaries

1. **Public internet → redirect endpoint.** Unauthenticated by design. Input is a short code only.
2. **Creator client → creation/analytics API.** API key presented; validated against stored hash. **This boundary
   covers the shortener only.** A creator credential grants nothing in the control plane (CN-012).
3. **Operator shell → provisioning script.** The ability to run commands on the host *is* the
   boundary; no HTTP issuance surface exists.
4. **Orchestrator → stage executor.** Executors are the governed party. They propose classifications
   and produce effects only through provided channels; anything else is refused and recorded as an
   autonomy violation (CL-007).
5. **Service → AI provider.** Untrusted output. AI text is never executed; stage 7 patches are applied
   on a branch and verified by the real test suite before anything is accepted (FR-ORC-031).
6. **Operator/engineer/reviewer shell → orchestrator governance surfaces.** Run creation, run inspection and
   gate-decision recording carry **no credential**: the ability to reach the process *is* the boundary, the same one
   drawn for creator provisioning (FR-URL-019). The two identity models are provably separate — an architecture test
   asserts no control-plane package reads a creator credential — and the boundary itself is enforced by deployment,
   not by code. Stated in `docs/LIMITATIONS.md` in exactly those terms: **separation is proven, reachability is
   not.** A reviewer credential class is the production answer and is recorded as future work (CN-012).

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
| Configuration | Secure defaults; throttling on by default. **No mode flag exists** (ADR-004-A2) | FR-URL-016 |

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
| API contract | `contracts/openapi.yaml` (OpenAPI 3.1) | `v1` path prefix; `info.version` **2.0.0** (CR-013) | Executable: live responses validated against the document in contract tests |
| Request/response/error schemas | Components within the OpenAPI document | With document | Same |
| Workflow-state schema | `contracts/workflow-state.schema.json` | semver, **2.0.0** (CR-013) | JSON Schema validation test over persisted snapshots; the fan-out node model's key uniqueness and join invariant are asserted by T074's test, which JSON Schema cannot express |
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
                                                    └──► S7.join (ALL must succeed) ──┐
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

**Node identity.** Nodes are keyed by string, not by stage number (CR-011/CR-013): `"S1".."S12"` for a singleton or
fan-out-parent stage, `"S7.1".."S7.n"` for fan-out children, `"S7.join"` for the join that gates S8. A freshly
materialised run holds thirteen nodes — eleven singletons, the S7 parent, and its join — and children are appended
when S6 yields tasks, so **there is no fixed node count per run**. Edges address node keys, which is what makes a
replanned instance's topology queryable and lets a replan invalidate one fan-out child while leaving its siblings
intact (FR-ORC-019). The S9∥S10 pair needs no join node: S11 simply carries two incoming `ALL` edges.

Two fan-out/join points: task-level parallelism in S7, and the S9∥S10 pair. One conditional branch at
S3 — **S4 fires only on material ambiguity**, which is DS-A's explicit requirement that a well-formed
requirement must not meet an artificial gate.

### Per-node definitions

Common to all nodes: **audit events** `STAGE_ENTERED`, `STAGE_EXITED`, `CRITERIA_EVALUATED`,
`EXECUTOR_DISPATCHED`, `FAILURE_CLASSIFIED`, `RETRY_RULED`, plus node-specific events named below.
Every event carries the six mandatory fields (actor type, action, timestamp, affected artifact/state,
result, reason). Default retry policy is PVT-007 (3 attempts, exponential from 1 s) gated by the
two-vote rule. Per-node elapsed-time **escalation thresholds** are **PVT-016**'s schedule. Breaching one does not
fail the node: it **escalates to the human** with elapsed-versus-expected and last-observed activity, offering **keep
waiting** or **kill the node** (FR-ORC-014 rule 6). A kill fails the node by overrun, after which CL-006 rule 4 and
the node's design-time idempotency declaration decide retry eligibility — **the threshold sets duration, never
eligibility** — and an unanswered escalation follows the gate-wait deadline to suspension.

**Three categories are never retryable at any node**, so they are stated once rather than in twelve rows:
`INVALID_INPUT` (a deterministic content error — the same input fails identically), `INTERNAL` (classified
permanent by CL-006 and ADR-004-A1, because malformed provider output must be surfaced rather than re-rolled), and
`UNKNOWN` (FR-ORC-014 rule 3, default-deny). Every declared set is therefore drawn from
`{TIMEOUT, UNAVAILABLE, RATE_LIMITED}`. `TIMEOUT` appears only where the node’s design-time contract declares its
effect idempotent or repeat-safe, and two nodes (S8, S10) exclude it even though rule 4 would permit it — see
their grounds (CR-022).

| # | Node | Purpose | Inputs → Outputs | Pre / Post | Actor | Timeout & retry | Declared retryable set | Failure class | Fallback |
|---|---|---|---|---|---|---|---|---|---|
| S1 | Ingestion | Admit a requirement, create the run | raw requirement → `WorkflowRun`, correlation ID | Pre: well-formed submission. Post: run persisted with ID | Deterministic engine | **PVT-016: 5 s**; `INTERNAL` permanent | `{UNAVAILABLE}` | Permanent on malformed input | None — refuse |
| S2 | Normalization | Identified, testable, typed requirements | raw → `RequirementRecord[]` | Pre: S1 ok. Post: every requirement has ID + type + testable statement | AI-capable | **PVT-016: 120 s**; PVT-007 attempts; `TIMEOUT` retryable (idempotent) | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | Per envelope | **None** — no deterministic counterpart (ADR-004-A2). Failure follows the envelope: bounded retry per the declared set, then suspension. |
| S3 | Ambiguity detection | Find incompleteness, conflict, untestability — **semantic, AI-backed** (spec §What ambiguity detection is); output feeds a human gate, so variability is safe here and would not be at S10 | requirements → `AmbiguityRecord[]` + quality-check record | Pre: S2 ok. Post: checks recorded; if none, the **reason clarification was not required** is recorded | AI-capable | **PVT-016: 120 s**; PVT-007 attempts; retryable | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | Per envelope | **None** — no deterministic counterpart (ADR-004-A2). Failure follows the envelope: bounded retry per the declared set, then suspension. |
| S4 | Human clarification | Resolve material ambiguity | ambiguities → `ClarificationDecision[]` | Pre: ≥1 material ambiguity. Post: every material item answered | **Human** | **No execution threshold** (PVT-016: N/A) — already waiting on a human; its threshold *is* the gate wait PVT-006 → `SAFE_STOP`; no retry | `{}` — **empty**; no executor | N/A | None — suspension only |
| S5 | Decomposition | Dependency-ordered tasks | requirements → `TaskRecord[]` + edges | Pre: no unresolved material ambiguity. Post: every task traces to ≥1 requirement; no orphans | AI-capable | **PVT-016: 180 s**; PVT-007 attempts; retryable | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | Per envelope | **None** — no deterministic counterpart (ADR-004-A2). Failure follows the envelope: bounded retry per the declared set, then suspension. |
| S6 | Architecture & design | Design + API/schema impact | tasks → design doc, contract deltas | Pre: S5 ok. Post: impacted contracts identified | AI-capable | **PVT-016: 300 s**; PVT-007 attempts; retryable | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | Per envelope | **None** — no deterministic counterpart (ADR-004-A2). Failure follows the envelope: bounded retry per the declared set, then suspension. |
| S7 | Implementation (fan-out) | Author and apply change per task | task + design → branch commit | Pre: task approved, design ok. Post: patch applied on branch, buildable | AI-capable, or **`HUMAN`** under no-plan gate option 2 | **PVT-016: 600 s per node**; PVT-007 attempts; `TIMEOUT` **not** retryable (non-idempotent git effect) | `{UNAVAILABLE, RATE_LIMITED}` — `TIMEOUT` excluded (EC-033) | Per envelope; conflict = permanent | **None** — no deterministic counterpart (ADR-004-A2). Failure follows the envelope: bounded retry per the declared set, then suspension. **Where no change plan exists**, the stage suspends at the **no-plan gate** and never no-ops forward (CR-001) — with the counterpart gone, the gate is the sole guard (CR-028). |
| S8 | Testing | Execute the real suite | branch → test results | Pre: S7 join complete. Post: results recorded with pass/fail per test | Deterministic, **real** | **PVT-016: 1800 s** threshold — the real suite plus Testcontainers startup; retry only on `UNAVAILABLE` | `{UNAVAILABLE}` — `TIMEOUT` excluded deliberately | Test failure = permanent (routes back to S7 within bound) | None — real results only |
| S9 | Documentation | Update docs with behavior | change + results → doc updates | Pre: S8 ok. Post: docs reflect delivered behavior | AI-capable | **PVT-016: 180 s**; PVT-007 attempts; retryable | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | Per envelope | **None** — no deterministic counterpart (ADR-004-A2). Failure follows the envelope: bounded retry per the declared set, then suspension. |
| S10 | Security & policy | Evaluate `policy-set-1.1.0` | change + deps → `PolicyCheckResult[]` | Pre: S8 ok. Post: every applicable policy has one of four outcomes | Deterministic, **real** | **PVT-016: 600 s** — dominated by the dependency-vulnerability scan; retry on `UNAVAILABLE` only | `{UNAVAILABLE, RATE_LIMITED}` — **widened**; `TIMEOUT` excluded | Unevaluable = **not** `PASS` (EC-025) | None — verdicts must be repeatable |
| S11 | Release readiness | Evaluate 9 blocking conditions, then human decision | all evidence → `ReleaseReadinessReport` + `GateDecision` | Pre: S9∥S10 join complete. Post: recorded human decision | Deterministic eval + **Human** | **PVT-016: 30 s** evaluation, then gate wait PVT-006 → `SAFE_STOP` | `{UNAVAILABLE}` | N/A | None |
| S12 | Final summary | Assemble from recorded evidence only | evidence → summary artifact | Pre: S11 approved. Post: every claim traceable | Deterministic assembler | **PVT-016: 60 s** | `{TIMEOUT, UNAVAILABLE}` | Missing evidence = permanent | None — never invent content |

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
| Workflow instance state | `workflow_run`, `stage_node` (node-keyed), `dependency_edge` (node-to-node) | Current state + full transition history. Node keys, not stage numbers, so a replanned topology and a per-task fan-out are both queryable (CR-011/CR-013) |
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
| Retention | **No tables.** Every record is retained indefinitely; there is no purge, archival or deletion path anywhere (NFR-AUD-003, CR-017). Production archival is a recorded recommendation, not a component |

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
| **Node overrun** | A node's PVT-016 escalation threshold is breached | That node only; siblings and unaffected paths continue | five standard, plus two named choices: **keep waiting** (re-arm the threshold) / **kill the node** (fail by overrun, then normal classification and idempotency-gated retry) |
| Release readiness | S11 | Submission | five standard |
| Final submission | Post-S12 | Submission | five standard |

**Uniform semantics**: outcomes are `APPROVED`, `REJECTED`, `CHANGES-REQUESTED`, `ESCALATED`,
`TIMED-OUT`. Every gate request states its expiry consequences up front — the gate-wait deadline at
which the run suspends, and the computed `auto-abandon-at` (CL-005 addendum). `TIMED-OUT` suspends;
it never advances. Every decision is materialized as a repository record before the work it authorizes
begins (Constitution §Gate semantics, v1.1.0). An agent cannot take any of these actions at any
autonomy setting (FR-ORC-021).

The **node-overrun** class is the one gate the orchestrator raises about itself rather than about an artifact. It
exists because a slow node is not a failed node: the orchestrator can observe that expected progress has not
happened, but only a human can decide whether that means *wait* or *stop*. Killing on a timer would be the
orchestrator deciding, which is the authority boundary this whole design keeps on the other side of the line.

---

## 6. Reliability

Implements CL-006, CL-007, CL-005, CL-009 directly.

| Concern | Design |
|---|---|
| Failure envelope | Closed category set: `TIMEOUT`, `UNAVAILABLE`, `RATE_LIMITED`, `INVALID_INPUT`, `INTERNAL`, `UNKNOWN`; plus executor-**proposed** classification and detail. Executors translate provider errors into it. |
| Transient vs permanent | `retries = node's declared retryable set ∩ executor proposal`. Either side vetoes. Executor has veto, never grant. **The declared operand is enumerated per node in §3’s `Declared retryable set` column** (CR-022) — twelve concrete sets drawn from `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}`, with `INVALID_INPUT`, `INTERNAL` and `UNKNOWN` never retryable anywhere. An unspecified declared set would make the intersection empty under default-deny, so the enumeration is what keeps the retry design alive rather than nominally present. |
| Unknown / malformed | **Permanent.** Default-deny, matching EC-025. Suspension path. |
| Retry bounds & backoff | PVT-007 (3 attempts, exponential from 1 s), per stage, individually recorded with a **two-signature** ruling (executor proposal + orchestrator ruling). |
| Timeout / overrun | Per-node **escalation thresholds** are **PVT-016**'s schedule (§3). A breach **asks the human** — keep waiting, or kill the node — and never kills on the orchestrator's own authority (FR-ORC-014 rule 6). Liveness comes from existing state-transition, trace and audit records; **no watchdog component is introduced**. A kill fails the node by overrun; retry is then gated exactly as before — retryable **only** where the design-time contract declares the effect idempotent/repeat-safe, never executor self-certification (S7 is explicitly not retryable). Duration and eligibility stay separate decisions: PVT-016 sets the first, CL-006 rule 4 the second. An unanswered escalation suspends. |
| Idempotency | Application plane: marker-based, three semantics (CL-008). Control plane: stage effects keyed so resumption cannot re-apply a committed effect. |
| Duplicate execution protection | Resumption reads persisted effect records before re-dispatch; concurrent resumption of one run is prevented by a run-level lease (EC-026). |
| Fallback | **Retired — FR-ORC-015, Decision K (CR-032).** Every declared fallback in this design was a deterministic counterpart for an AI-capable stage, and Decision J removed those. **Bounded retry then safe suspension is the whole degradation story**; the row is kept rather than deleted so the absence is visible in the table where a reader looks for it. Disclosed as a named limitation in `docs/LIMITATIONS.md`. |
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
| Secure defaults | Throttling on; verbose error detail off; readiness fails closed — **three defaults**; the former `ai: off` is gone with the flag (ADR-004-A2) |
| Threat model | Deliverable in Phase 1 (`research.md` §Threat Model), covering the trust boundaries in §1 plus the two named accepted trade-offs |

---

## 9. Compliance and Change Control

### `policy-set-1.1.0`

| ID | Domain | Mandatory? | Evaluation input → output |
|---|---|---|---|
| `POL-SEC-001` | Security | **Mandatory** | Scheme allow-list present and non-empty → PASS/FAIL |
| `POL-SEC-002` | Security | **Mandatory** | Secret scan over repo + telemetry → PASS/FAIL |
| `POL-SEC-003` | Security | **Mandatory** | Dependency vulnerability scan → PASS/FAIL |
| `POL-PRIV-001` | Privacy | **Mandatory** | Stored schema contains no personal-data field → PASS/FAIL |
| `POL-AUD-001` | Audit retention | **Mandatory** | Every audit record has all six fields → PASS/FAIL |
| `POL-DEP-001` | Approved dependencies | **Mandatory** | All dependencies on the approved list → PASS/FAIL |
| `POL-LIC-001` | Licensing | **Mandatory** | All dependency licences on the permitted list → PASS/FAIL |
| `POL-CHG-001` | Change control | **Mandatory** | Contract/schema change has a change-control record → PASS/FAIL |
| `POL-CHG-002` | Change control | **Mandatory** | Every mandatory gate has a materialized repository record → PASS/FAIL |
| `POL-TST-001` | Compliance | **Mandatory** | Required test categories non-empty and executed → PASS/FAIL |
| `POL-TRC-001` | Compliance | **Mandatory** | Zero traceability orphans → PASS/FAIL |
| `POL-CHG-003` | Change control | **Mandatory** | A contract or schema change classified MAJOR carries a new major version → PASS/FAIL. Evaluated against the served baseline recorded in `contracts/README.md`; `NOT-APPLICABLE` before first service, **armed during scenario demonstrations** (CR-013) |

Outcomes are exactly `PASS`, `FAIL`, `EXCEPTION-REQUESTED`, `NOT-APPLICABLE`. **Unevaluable is never
`PASS`** (EC-025). Every run records the policy set version it evaluated. **v1.1.0 adds `POL-CHG-003`** (CR-013) and
**removes `POL-AUD-002`** (CR-017); `v1.0.0` remains the version of record for any run executed before this
amendment — an amended policy set is **never applied retroactively** to claim compliance (Constitution §Amendment
procedure).

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

- **Subject provenance**: **neither the assignment nor the interviewer guide names a brownfield subject.** The owner
  verified that before choosing, so the subject is ours to select — and it is selected to be something the baseline
  genuinely lacks, **from inside already-approved scope** rather than invented for the demonstration.
- **Input**: a change against existing shortener code — **"A creator's redirect traffic must be limited in aggregate
  across all their links, not only per link."** This is FR-URL-016's third tier — **PVT-014, 3,000 requests/minute per
  creator aggregated** — the noisy-neighbour control.
- **Why this subject**: the baseline deliberately builds **two of the three** rate-limit tiers — per-creator creation
  (PVT-012) and per-code redirect (PVT-013) — and **defers the per-creator aggregate redirect tier**. FR-URL-016
  remains **binding in full**; the deferral is disclosed in the baseline-omissions record and is satisfied at release
  readiness by this run's evidence.
- **Why it is a genuine brownfield change rather than a contrivance**: the redirect path is **public and anonymous** by
  requirement (FR-URL-018), so counting traffic per *creator* means resolving code → owning creator **inside the hot
  path**. That is a real design question with real consequences, in components that already exist, against a latency
  budget that is already a binding target.
- **Interpretation**: S2 normalizes to one functional requirement (the third tier) plus its non-functional latency
  constraint; S3 finds no material ambiguity — FR-URL-016 already states the tier, its limit and its negative criteria,
  so the absence of ambiguity is genuine rather than suppressed.
- **Decomposition**: impact analysis precedes any code change.
- **Path**: as DS-A, with S6 producing the **seven-dimension impact analysis** whose timestamp **precedes** the first
  modification, and with an injected transient fault in S7 exercising retry and a compensation case exercising the
  register.
- **The impact analysis has real content**, which is what makes the seven dimensions worth a reviewer's time:
  - **Impacted components and interfaces**: the redirect controller and the rate-limit module; code → creator
    resolution added to the resolution path.
  - **Impacted data flows**: the ownership lookup enters the hot path; counters keyed per creator as well as per code.
  - **Latency**: the added lookup sits inside PVT-001's redirect budget and must be **measured, not assumed**.
  - **Limiter failure posture**: when the counter store is unavailable, does the tier fail **open** (serve, unlimited)
    or **closed** (throttle)? A decision with a security consequence, taken explicitly rather than by default.
  - **Disclosure**: the throttled response must name the tier that was exceeded **without disclosing the owning
    creator to a public follower** — FR-URL-016's own negative criterion.
  - **Impacted tests**: the per-code tier's tests are the regression surface; the multi-link aggregate case is the new
    one.
  - **Documentation and rollout/rollback**: the threat model's noisy-neighbour accepted trade-off (NFR-SEC-003), and
    the tier being disableable without redeploying the redirect path.
- **Approvals**: architecture; **security-sensitive** — this is an abuse control, which is precisely what §5's
  security-sensitive class covers; release readiness.
- **Failure paths**: transient `UNAVAILABLE` → two-vote retry → success; one irreversible effect → compensation,
  labelled as compensation.
- **Validation**: **before-state** — FR-URL-016's own multi-link case: traffic spread across several links, each
  staying **under** PVT-013, passes **unthrottled** in the baseline, captured as evidence **before** the impact
  analysis. **After-state** — the same traffic is throttled and the response **names the aggregate tier** without
  naming the creator. The per-code and per-creator-creation tiers are proven **unregressed**. Redirect latency is
  **re-measured** against PVT-001 with the ownership lookup in place.
- **Evidence**: impact analysis with ordering proof, retry two-signature records, compensation record, before/after
  throttling results, the unregressed per-code tier, and the re-measured latency.
- **Terminal outcome**: `COMPLETED`.
- **Superseded candidate subjects, all retained for provenance** — recorded because the path to this subject is part of
  the reasoning, and hiding it would make the final choice look easier than it was:
  1. *"Redirect analytics must not lose events under concurrent load."* **Voided by its own resolution** — ADR-014
     decided it and Slice 3 (T050) implements it, so by Phase 8 there would have been no defect left to analyse.
  2. *Archival retention of records past their bounds.* **Withdrawn** when the owner simplified retention to
     indefinite retention with a documented production recommendation (CR-017, Decision H): with no archival job in
     scope, the subject had nothing to implement.
  3. *Custom short aliases.* **Rejected as out of scope** — EX-002 and AS-002 exclude vanity codes, so building one
     would have expanded approved scope rather than filled a gap inside it.

### DS-C — Ambiguous requirement

- **Input**: internally conflicting — proposed: *"Links should expire after a week but remain
  available for historical analytics indefinitely, and expired links should still redirect for
  trusted partners."*
- **Interpretation**: S3 flags the conflict, naming the conflicting elements; unsafe implementation is
  prevented. **Detection is semantic and AI-backed** (CR-025). Draft 1 of that record asked whether a keyless run could
  detect this input; Decision J removed the keyless mode, so the question is moot — but the capability is stated rather
  than assumed, because a reader is entitled to know whether "finds conflict" means reasoning or pattern-matching.
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

## 12. Technology Decisions — the evaluation that produced ADR-001..005 *(historical record)*

**All of these were decided at Gate 4, 2026-09-20**, and the decisions of record are ADR-001..ADR-014 in
`docs/governance/adr/`, not this section. It is retained **unaltered below** as the evaluation the ADRs came from —
the options, criteria and counter-cases as they stood before the gate — because an ADR that cites its evaluation is
weaker if the evaluation is edited afterwards to agree with it.

Read the section in that light: where the text below says *"recommended"* or *"pending"*, it is describing the state
at authoring time. **Nothing here should be read as an open question.**

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
- **Options**: (a) Anthropic Claude; (b) OpenAI; (c) local model; (d) none (deterministic only).
- **Criteria**: quality on code-authoring and analysis, availability of a clean interface boundary,
  cost, and the hard constraint CN-011 that the reviewer default must run with no key. *(CN-011 was **retired by Decision J**, CR-028; this section records the criteria as they stood at Gate 4 and is not rewritten.)*
- **Recommended**: **(a) Anthropic Claude** behind a provider interface, with the specific model pinned at
  implementation time; the run-level flag is `ai: on | off` and defaults to `off`. *(The flag was **removed entirely by Decision J**, ADR-004-A2 / CR-028. Recorded as it stood, per this section’s historical idiom.)*
- **Rationale**: (d) alone would make the "agentic" claim hollow (CL-003). Keeping the provider behind
  an interface satisfies NFR-MNT-002 and keeps (b)/(c) open.
- **Transport — settled separately by [ADR-004-A1](../../docs/governance/adr/ADR-004-amendment-01-transport-claude-code-cli.md) (CR-008)**:
  ADR-004 fixes the provider and the interface boundary; *how* the boundary is reached is a sub-decision. The
  implemented adapter is a **subprocess invocation of the locally installed, authenticated Claude Code CLI in
  headless mode**, with the model pinned in configuration and the **actually-used model id read from the
  response JSON** and recorded per run (FR-ORC-029). The Anthropic API/SDK adapter is the recorded production
  alternative behind the same interface, built only if time permits (backlog, T002).
- **A second live-demonstration transport — [ADR-004-A3](../../docs/governance/adr/ADR-004-amendment-03-gemini-cli-for-live-demonstration-runs.md) (CR-042)**:
  `ClaudeCodeCliStageAiProvider` remains the documented primary/production adapter, unmodified. T073a-f's
  live demonstration runs (AS-007) use a second implementation, `GeminiCliStageAiProvider` (the Gemini CLI,
  `agy`, model `gemini-3.8-flash-high`), because a Claude Code build agent cannot spawn `claude` as a nested
  subprocess but can spawn `agy` — a real environmental constraint, not a vendor judgment. Since `agy`'s own
  response names no resolved model id, the **pinned** id is recorded as the model used, disclosed as a pin
  rather than a response reading. This amendment is itself a second demonstration of the interface's
  reversibility, alongside ADR-004-A1's own.
- **Consequences**: an authenticated Claude Code CLI on the machine for demonstration runs only, drawing on the
  same subscription quota as the development tooling. The SDK path would instead need an API key and per-call
  cost. Either way, **nothing graded requires either** (CN-011).
- **Risks**: non-determinism in graded runs. **Mitigation**: reliability proofs use injected fakes, never
  the live provider (FR-ORC-030). **Transport-specific risk**: the stage prompt is untrusted content, so the
  subprocess MUST be invoked with an **argv array, never a shell string** — see ADR-004-A1 §Risks.
- **Reversibility**: high — demonstrated by ADR-004-A1, which changed transport without touching the interface,
  the stage definitions, the flag, the labels, or any requirement, and demonstrated a second time by
  ADR-004-A3's swap to a different vendor's CLI entirely under the same constraint. **Validation**: SC-014 —
  full run with no key, no network.

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
cases, with Task/Test/Evidence columns reserved. The **Design and ADR columns are populated** in that matrix —
added by CR-006 after checklist finding CHK037 caught that this sentence previously claimed they existed when
they did not. `tasks.md`
fills Task. Implementation fills Code, Test, Validation, Documentation, Evidence. A generated
traceability report asserts **zero orphans in both directions** and is enforced by `POL-TRC-001` as a
mandatory release-blocking policy — so an incomplete chain cannot reach release readiness.

---

## 14. Delivery Sequence

**Timebox: 2–3 days — APPROVED 2026-09-20** at the Gate 4 closing package, with the milestones, checkpoints and
stop conditions below.

**It is a control instrument, not a promise, and not a source of pressure** — see §Scope versus timebox below for what that means against a 173-task plan. **Completeness takes priority over
speed**, and **no external submission deadline exists** (Gate 5 time-attitude amendment, CR-009). The schedule
exists so that slippage is *observed and escalated* rather than absorbed silently — not so that work is rushed or
quietly narrowed to fit it.

**Scope versus timebox — reconciled, because the arithmetic invites the question.** The plan carries **173 tasks**. The
box is **2–3 days**. Those two numbers do not reconcile on any honest reading of a task that requires a captured red
phase, a real database, and executed evidence — and a reader who multiplies them is right to ask which number is
fiction.

**Neither is fiction, because they are not the same kind of number.**

- **The scope is the commitment.** 173 tasks is what the approved requirements need. It was not sized to fit a
  schedule, and it will not be cut to fit one: cuts come from the backlog and never from mandatory validation or
  reviewer evidence, and a scope reduction happens **only on the owner's recorded order at a checkpoint** (CR-009).
- **The timebox is a reporting instrument.** Its function is to make slippage *visible and escalated* rather than
  absorbed silently. The day-level milestones below are the resolution at which that reporting happens — they are
  **observation points, not commitments**.
- **No external deadline exists.** There is no submission date this box is protecting. Completeness is the commitment;
  the box is how progress against it is narrated (CR-009, Gate 5 time-attitude amendment).

**What this means concretely when the arithmetic bites**, which it will: the End-Day-1 and End-Day-2 checkpoints will
show the plan behind its milestones. That is the instrument working, not failing. Each checkpoint **escalates with
options and does not cut**; the owner decides whether anything is reduced; and the two controls that protect
correctness rather than schedule — the fabrication-pressure halt and the mandatory-policy-`FAIL` block — are untouched
by any of it.

**Owner Decision J removed work without removing tasks, and that distinction matters here.** Striking the six
deterministic counterparts (ADR-004 Amendment 02) takes real implementation out of T071 — five engines instead of
eleven — yet the task count does not move, because T071 is one task either way. So the arithmetic above is unchanged
by Decision J even though the day's work is lighter. **Removing work is not the same as removing scope**, and this
statement deliberately does not claim the decision bought schedule: the milestones are unchanged, the checkpoints will
still escalate, and any credit for a lighter T071 belongs at a checkpoint where it can be observed rather than in a
forecast.

**Why this is stated rather than left implicit.** A reviewer who finds a 173-task plan in a 2–3 day box and no
acknowledgement of the gap reasonably concludes that one of the two was not thought about. The gap was thought about,
the answer was decided at Gate 5, and until now it lived in a change-control record rather than beside the numbers it
explains.

Sequenced as vertical slices; each ends with
executed tests and committed evidence, never with unverified code.

| Slice | Content | Milestone | Must-have? |
|---|---|---|---|
| 1 Engineering baseline | Build, layout, Flyway, OpenAPI skeleton, contract-test harness, CI-equivalent local script | **Day 1 AM** | Yes |
| 2 Walking skeleton | One endpoint + health + real store round-trip, end to end | **Day 1 AM** | Yes |
| 3 Core URL behavior | TDD: validation, scheme list, code gen + uniqueness, redirect, expiry, idempotency (3 semantics), analytics, provisioning script, and **two of the three rate-limit tiers** — per-creator creation (PVT-012) and per-code redirect (PVT-013). **The per-creator aggregate redirect tier (PVT-014) is deliberately deferred to the brownfield scenario** | **Day 1 PM** | Yes |
| 4 Orchestration state model | Persisted DAG, 12 nodes, transitions, prohibited-transition enforcement, inspection | **Day 2 AM** | Yes |
| 5 Approval governance | Gates, five outcomes, deadline-in-ask, silence→suspension, retention/auto-abandon | **Day 2 midday** | Yes |
| 6 Reliability controls | Envelope, two-vote retry, timeout gating, fallback, Compensation Register, resume across both restart classes | **Day 2 PM** | Yes |
| 7 Observability | Audit events, correlation IDs, metrics, `failure_event` capture, MTTR calculation. **No retention work: this demonstration retains everything indefinitely** (NFR-AUD-003, CR-017) | **Day 3 AM** | Yes |
| 8 Three scenarios | DS-A, DS-B, DS-C executed with evidence | **Day 3 midday** | Yes |
| 9 Release readiness | Policy evaluation, 9 blocking conditions with negative tests, final engineering summary | **Day 3 PM** | Yes |

**Critical path**: 1 → 2 → 4 → 5 → 6 → 8 → 9. Slice 3 is required as DS-B's subject matter. Slice 7 is
partially parallelizable with 6 but its `failure_event` capture must exist before Slice 8 runs, or the
scenarios produce no MTTR population.

**Deliberate baseline omission.** Slice 3 builds **two of the three** rate-limit tiers and **defers the per-creator
aggregate redirect tier** (PVT-014). That deferred tier is **the brownfield scenario's subject**, implemented under
governance in Slice 8.

This is a scheduled omission with a named closure, not deferred scope: **FR-URL-016 is binding in full and is
satisfied at release readiness by the brownfield run's evidence.** If that run does not happen, the omission becomes a
real gap and release readiness must report it as one — it may not be relabelled as a design choice after the fact.

**Scope-control checkpoints** — at each, compare progress to the milestone. **A checkpoint observes and escalates
with options; it does not itself cut.** A scope reduction happens **only on the owner's recorded order at that
checkpoint** (CR-009). Nothing may be cut from mandatory validation or reviewer evidence at any checkpoint, by
anyone.

| Checkpoint | Option escalated if behind |
|---|---|
| End Day 1 | Reduce Slice 3 to the FR-URL requirements DS-B needs; defer rate-limit tier sophistication |
| End Day 2 AM | **No reduction option is offered. The former AI-reduction option is STRUCK** — Gate 6 condition, 2026-09-21 (CR-034). It read *"reduce AI-capable stages from six toward two, the rest running deterministic"*, and **Decision J removed every deterministic counterpart** (ADR-004-A2), so its true meaning became *leave stages with no executor at all — parts of the orchestrator unbuilt*. That is not a scope reduction; it is **shipping an incomplete graded artifact**. The owner’s Gate 5 overrule had already rejected the same trade. This checkpoint **observes Slice 4 against its milestone, records the position, and escalates without a pre-drawn option**; stop condition 3’s rule stands — the **options offered exclude abandoning the orchestration model** |
| End Day 2 PM | Cut DS-B's injected compensation case to a unit-level proof; keep the scenario |
| End Day 3 AM | Reduce MTTR population to the reliability suite only, and say so in the declared population |

**Backlog — explicitly deferred, never allowed to displace mandatory work**: run-level retry circuit
breaker (DF-004); email/webhook expiry notification (DF-004); the Anthropic SDK transport adapter (ADR-004-A1);
`redirect_event` time-partitioning (ADR-014); custom aliases, link deletion/editing, multi-region (EX-001..003);
analytics beyond time series.

**The aggregate redirect tier is NOT a backlog item.** It is deliberately deferred from the baseline and implemented
by the brownfield run in Slice 8. Listing it as backlog would be exactly the artifacts-disagreeing defect CHK228 exists
to catch, and would let a scheduled omission read as an accepted one.

**Retention is not a backlog item either, for a different reason**: there is no retention work at all. This
demonstration retains every record indefinitely, and production archival is a **recorded recommendation** rather than
deferred scope (NFR-AUD-003, CR-017).

**AI wiring for all six AI-capable stages is in scope from the outset** (`tasks.md` T073a–T073f). It is *not* a
backlog item. The owner overruled an earlier pre-emptive reduction on the ground that its estimate priced
AI-authored work at human authoring speed. **As of the Gate 6 condition (CR-034) there is no checkpoint option to
reduce it either**: the End-Day-2-AM option that once carried that route is struck, because Decision J made its true
meaning *leave stages with no executor*. Any reduction would now require a fresh owner decision on the record, with
what it costs stated — not the selection of a pre-drawn menu item.

**Stop conditions** — classified by what the trigger does, per the Gate 5 time-attitude amendment (CR-009). Two
are correctness controls that **halt**; two are time-based and **escalate for the owner's direction**, because time
must not freeze work or narrow scope on its own:

1. **ESCALATE** — Slice 4 incomplete by end of Day 2 → report the position and ask the owner for direction. The
   orchestration model is the graded artifact and cannot be traded away, so the options offered exclude
   abandoning it.
2. **HALT** — any mandatory policy `FAIL` unresolved at Slice 9 → release readiness blocks; report blocked
   rather than weaken the check (Constitution XI is non-waivable).
3. **HALT, non-waivable** — fabrication pressure, meaning any temptation to present a proposed or simulated
   result as executed → stop and report. No escalation, no discretion, no exception path (Principle X).
4. **ESCALATE** — timebox exhausted with slices incomplete → report exactly what is done, what is not, and why.
   Scaling scope down is the owner's call, not the agent's, and the agent does not pre-empt it.

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
├── policy/              # policy-set-1.1.0 evaluation, exceptions, release readiness
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
**all eleven principles remain `PASS`, as does the CN-002 technology-neutrality row** (twelve rows, eleven principles). The design introduced no new component whose complexity is
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
