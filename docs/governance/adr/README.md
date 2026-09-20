# Architecture Decision Records

Feature 001 — Agentic Software Engineering System: URL Shortener.
Governing constitution v1.1.0. Created 2026-09-19.

**All fourteen ADRs are `Accepted`** — 2026-09-20, by Pravallika Veeravalli at Gate 4. Decision record:
[`gate-04-adr.md`](../gate-decisions/gate-04-adr.md), which carries the acceptance grounds, the owner's
interventions and revision histories, and the provenance of the adopted grounds.

Three were accepted with qualification: **ADR-005** with a count fix, **ADR-010** as amended by the owner's
scoping of the fail-closed rule, **ADR-014** with three conditions. **ADR-013** incorporates three owner
refinements, one of which ( required credential expiry ) has an open specification impact routed through
**CR-002**.

## Inventory

| ADR | Topic | Status | Reversibility | Decision |
|---|---|---|---|---|
| [001](./ADR-001-language-and-framework.md) | Programming language and web framework | Accepted | **Low** | Java 21 + Spring Boot 3 |
| [002](./ADR-002-persistence-strategy.md) | Persistence strategy | Accepted | High | PostgreSQL 16 via Docker + Testcontainers |
| [003](./ADR-003-orchestration-model.md) | Orchestration model — build vs adopt | Accepted | Moderate | Purpose-built persisted DAG |
| [004](./ADR-004-ai-provider-and-autonomy-bounding.md) | AI provider and autonomy bounding | Accepted | High | Anthropic Claude behind an interface; `ai: off` default; stage-7 no-plan gate |
| [005](./ADR-005-contract-validation-approach.md) | API contract validation approach | Accepted | High | Spec as source of truth + executable response validation |
| [006](./ADR-006-application-architecture.md) | Application architecture and plane separation | Accepted | Moderate–High | Single project, package split, enforced by architecture test |
| [007](./ADR-007-short-code-generation.md) | Short-code generation and collision handling | Accepted | High | CSPRNG random + unique constraint + bounded retry |
| [008](./ADR-008-workflow-state-and-graph-representation.md) | Workflow state persistence and graph representation | Accepted | Moderate | Current state + append-only transition log; per-run materialised edges |
| [009](./ADR-009-replanning-impact-computation.md) | Dynamic replanning — impact computation | Accepted | High | Downstream transitive closure over declared edges |
| [010](./ADR-010-observability-and-audit-model.md) | Observability and audit model | Accepted | High (location) / Low (shape) | Relational append-only audit; immutability by privilege |
| [011](./ADR-011-testing-strategy.md) | Testing strategy | Accepted | High | Real store for data paths; injected fakes for reliability |
| [012](./ADR-012-deployment-and-local-execution.md) | Deployment and local execution model | Accepted | High | Compose for store only; app on host |
| [013](./ADR-013-authentication-and-authorization-mechanism.md) | Authentication and authorization mechanism | Accepted | High | Opaque 256-bit key, SHA-256, constant-time compare |
| [014](./ADR-014-analytics-consistency.md) | Analytics consistency model — **resolves DF-001** | Accepted | High | Synchronous append, separate transaction, failure isolated |

## Evaluated and judged NOT to require an ADR

The gate asked for all sixteen candidate areas to be evaluated. Three were assessed as **already decided by
approved artifacts**, where an ADR would duplicate a closed decision and risk drifting from it.

| Candidate area | Where it was decided | Why no ADR |
|---|---|---|
| **Human approval model** | Constitution III; the ten-row mandatory gate table in §Development Workflow and Quality Gates; CL-005's deadline-in-the-ask addendum | The model, the five outcomes, silence-never-approves, and materialization are all settled and non-waivable. No open architectural choice remains — only implementation, covered by ADR-008 and plan §5. |
| **Retry, fallback and safe-stop policy** | CL-005 and CL-006, approved at Gate 3 | The owner decided the two-vote intersection, executor veto without grant, default-deny on unknown, design-time timeout gating, and suspension semantics. Re-deciding these in an ADR could only restate or contradict an approved clarification. Mechanism is covered by ADR-008 and ADR-011. |
| **Rollback versus compensation** | CL-007 and the specification's §Compensation Register, approved at Gate 3 with the effect inventory confirmed | Structural default, declared overrides, registration-time enforcement, and the seven-row register are settled. Nothing architectural is open. |

**Offer, not action**: if the candidate prefers ADR-form records for architectural discoverability, pointer ADRs
can be created that cite CL-005 / CL-006 / CL-007 without re-deciding anything. That requires an instruction —
it has not been done, because writing an ADR that appears to decide an already-approved question invites drift.

A fourth item was **folded rather than separated**: *dependency graph representation* is decided inside
[ADR-008](./ADR-008-workflow-state-and-graph-representation.md), because storage shape and graph shape constrain
each other — replanning mutates per-run topology, so they cannot be decided independently.

## Decision sequence

Reversibility dictates order. **ADR-001 must be decided first** — it is the only Low-reversibility decision and
Slice 1 cannot start without it.

```
ADR-001 (language)  ──► ADR-002 (store) ──► ADR-006 (architecture) ──► ADR-012 (execution)
                                │
                                ├──► ADR-008 (state + graph) ──► ADR-009 (replanning)
                                │              │
                                │              └──► ADR-010 (audit)
                                ├──► ADR-007 (short codes)
                                ├──► ADR-013 (auth)
                                └──► ADR-014 (analytics)

ADR-003 (orchestration model) ── independent of stack; decidable now
ADR-004 (AI provider)         ── independent; High reversibility
ADR-005 (contract validation) ── independent; High reversibility
ADR-011 (testing)             ── depends on ADR-002 and ADR-004
```

## Cross-cutting notes

- **ADR-014 corrects the plan.** Plan §Decisions Required item 1 and `research.md` both recorded a provisional
  position of same-transaction analytics append. That position violates FR-URL-010's negative criterion
  (EC-012): a failed append in the redirect's transaction fails the redirect. ADR-014 corrects it and, on
  approval, the plan and research should be updated.
- **ADR-005 discloses an unverified artifact.** The Phase 1 contract files have not been parse-validated —
  execution was refused by the environment's permission layer during authoring. Validating them is the first
  Slice 1 task.
- **DF-002 and DF-003 remain open** and are not resolved by any ADR here. DF-002 (redirect permanence) interacts
  with ADR-014; DF-003 (audit vs idle retention boundary) interacts with ADR-010.
- **CLAUDE.md does not define an ADR location.** These records are filed at `docs/governance/adr/` following the
  plan's §Project Structure. Constitution §Runtime guidance (as amended in v1.1.0) requires `CLAUDE.md` to define
  record locations, and ADRs are currently absent from it — worth a one-line addition.
