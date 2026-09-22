# Governance Evidence Index

**Task**: T148. **Purpose**: a single navigable index of every governance record in this repository —
ADRs, change-control records, gate decisions, amendments, delegations, and exceptions — as they
actually exist on disk. This document is an index, not a source of truth: where it disagrees with the
record it links to, the record wins.

**Governing documents**:
- [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — v1.1.0 (amended once,
  see [Amendments](#4-amendments) below), states the *what* and *why* of the governance model.
- [`CLAUDE.md`](../../CLAUDE.md) — repository operational guidance, states the *how* (where records
  live, timing, structure, immutability) delegated to it by the constitution's §Runtime guidance.

**At a glance**:

| Class | Count |
|---|---|
| ADRs | 14 decisions + 4 amendments to ADR-004 |
| Change-control records | 70 (CR-001–CR-070) |
| Gate decisions | 9 program-level + 4 ds-a + 1 ds-b + 2 ds-c = 16 |
| Amendments | 1 (constitution v1.0.0 → v1.1.0) |
| Delegations | 1 (standing routine-clarification delegation) |
| Exceptions | 0 — `docs/governance/exceptions/` does not exist on disk; none has ever been raised |

---

## 1. Architecture Decision Records

Directory: [`docs/governance/adr/`](../governance/adr/) — see its own
[`README.md`](../governance/adr/README.md) for numbering conventions, the dependency graph between
decisions, and the "evaluated but no ADR needed" list (human-approval model, retry/fallback/safe-stop,
rollback-vs-compensation — settled directly by CL-005/006/007 in `spec.md` instead). All 14 base ADRs
were accepted at Gate 4 on 2026-09-20 (record: [gate-04-adr.md](../governance/gate-decisions/gate-04-adr.md)).

| ID | Title | Status | Summary |
|---|---|---|---|
| [ADR-001](../governance/adr/ADR-001-language-and-framework.md) | Programming Language and Web Framework | Accepted — 2026-09-20 | Java 21 + Spring Boot 3, chosen over Python/TS/Go for defensibility and concurrency-testing needs. |
| [ADR-002](../governance/adr/ADR-002-persistence-strategy.md) | Persistence Strategy | Accepted — 2026-09-20 | PostgreSQL 16 via Docker Compose/Testcontainers, chosen for independent restartability and privilege-enforced audit immutability. |
| [ADR-003](../governance/adr/ADR-003-orchestration-model.md) | Orchestration Model — Build vs Adopt | Accepted — 2026-09-20 | Build a purpose-built persisted DAG engine rather than adopt Temporal/Camunda/Spring StateMachine. |
| [ADR-004](../governance/adr/ADR-004-ai-provider-and-autonomy-bounding.md) | AI Provider and Autonomy Bounding | Accepted — 2026-09-20; superseded in part by A2 | Anthropic Claude behind an owned `StageAiProvider` interface; run-level `ai:on\|off` flag (default off); stage-7 no-plan human gate. |
| [ADR-004-A1](../governance/adr/ADR-004-amendment-01-transport-claude-code-cli.md) | Amendment 01 — Transport: Local Claude Code CLI Adapter | Accepted — 2026-09-20 | Claude adapter implemented as a local Claude Code CLI subprocess (argv, not shell), not an SDK call. |
| [ADR-004-A2](../governance/adr/ADR-004-amendment-02-always-ai-no-keyless-mode.md) | Amendment 02 — Always-AI, Keyless Mode Removed | Accepted — 2026-09-20 | Removes keyless mode and the `ai` flag; every run requires authenticated Claude CLI; retires FR-ORC-015 fallback (Decision K). |
| [ADR-004-A3](../governance/adr/ADR-004-amendment-03-gemini-cli-for-live-demonstration-runs.md) | Amendment 03 — Gemini CLI for Live-Demonstration Runs | Accepted — 2026-09-21; rationale later corrected by A4 | Adds `GeminiCliStageAiProvider` as a second transport for live demo runs, believing Claude CLI could not nest as a subprocess. |
| [ADR-004-A4](../governance/adr/ADR-004-amendment-04-claude-cli-nesting-corrected.md) | Amendment 04 — Nesting-Guard Finding Corrected | Accepted — 2026-09-21 | Corrects A3: the "cannot nest claude" finding was a Bash-tool allowlist artifact; restores `ClaudeCodeCliStageAiProvider` for live/scenario runs. |
| [ADR-005](../governance/adr/ADR-005-contract-validation-approach.md) | API Contract Validation Approach | Accepted — 2026-09-20 (schema count corrected 5→4) | Hand-authored `openapi.yaml` validated by executable contract tests + 4 JSON Schemas, plus a deliberate-drift test. |
| [ADR-006](../governance/adr/ADR-006-application-architecture.md) | Application Architecture and Plane Separation | Accepted — 2026-09-20 | Single deployable, package-level plane split, enforced by an automated dependency-direction architecture test. |
| [ADR-007](../governance/adr/ADR-007-short-code-generation.md) | Short-Code Generation and Collision Handling | Accepted — 2026-09-20 | CSPRNG 7-char codes, confusable-free alphabet, DB unique constraint, insert-and-catch bounded retry. |
| [ADR-008](../governance/adr/ADR-008-workflow-state-and-graph-representation.md) | Workflow State Persistence and Graph Representation | Accepted — 2026-09-20 | Hybrid current-state rows + append-only transition log, with per-run materialized dependency edges. |
| [ADR-009](../governance/adr/ADR-009-replanning-impact-computation.md) | Replanning — Impact Computation | Accepted — 2026-09-20 | Impact set = deterministic transitive downstream closure over persisted edges; AI-determined impact disqualified as non-deterministic. |
| [ADR-010](../governance/adr/ADR-010-observability-and-audit-model.md) | Observability and Audit Model | Accepted as amended — 2026-09-20 | Audit as first-class relational append-only data (INSERT/SELECT only); fail-closed scoped to orchestrator governance writes only. |
| [ADR-011](../governance/adr/ADR-011-testing-strategy.md) | Testing Strategy | Accepted — 2026-09-20 | Two-tier testing (fast unit + Testcontainers integration) with scriptable fake executors; live AI excluded from graded suites. |
| [ADR-012](../governance/adr/ADR-012-deployment-and-local-execution.md) | Deployment and Local Execution Model | Accepted — 2026-09-20 | Docker Compose for Postgres only; application runs on host; full containerization is a backlog option. |
| [ADR-013](../governance/adr/ADR-013-authentication-and-authorization-mechanism.md) | Authentication and Authorization Mechanism | Accepted — 2026-09-20, with three owner refinements | Opaque ≥256-bit CSPRNG key, `crk_` prefix, SHA-256 hash + constant-time compare, Bearer transport, mandatory expiry. |
| [ADR-014](../governance/adr/ADR-014-analytics-consistency.md) | Analytics Consistency Model (resolves DF-001) | Accepted with three conditions — 2026-09-20 | Redirect and analytics-event append happen in separate transactions; append failure isolated, redirect still succeeds. |

## 2. Change-Control Records

Directory: [`docs/governance/change-control/`](../governance/change-control/) — 70 records, CR-001
through CR-070, no gaps. Note: from roughly CR-050 onward several records are recorded as
**"Conversational approval"** rather than a formal `APPROVED` gate decision — per this repo's own
CLAUDE.md rule ("Conversational assent, agreement, or encouragement is not a gate decision"), those
CRs sit below the gate-record bar even though they are materialized change-control artifacts; this is
noted per-row below rather than smoothed over.

| ID | Title | Status | Summary |
|---|---|---|---|
| [CR-001](../governance/change-control/CR-001-human-executor-and-no-plan-gate.md) | Human Executor Kind and the No-Change-Plan Gate | Approved and applied | Adds HUMAN executor kind and a no-plan gate when stage 7 has no change plan; renames run flag to `ai: on\|off`. |
| [CR-002](../governance/change-control/CR-002-credential-expiry-required-at-provisioning.md) | Credential Expiry Required at Provisioning | Approved and applied | Credential expiry becomes a required provisioning parameter (duration or "never"); expired credentials refused like revoked ones. |
| [CR-003](../governance/change-control/CR-003-redirect-permanence-resolved.md) | DF-002 Resolved: Redirects Are Temporary | Approved and applied | Resolves DF-002: redirects must be temporary-class, never permanent, for analytics/expiry correctness. |
| [CR-004](../governance/change-control/CR-004-audit-retention-clock-resolved.md) | DF-003 Resolved: Audit Retention Clock Starts at Run Termination | Approved and applied | Audit/run-history retention clock anchors to run termination, not record creation. |
| [CR-005](../governance/change-control/CR-005-gate-4-closing-approvals.md) | Gate 4 Closing Approvals Reflected in the Specification | Approved and applied | Converts 15 PVTs to approved binding targets; closes DF-005; renames `executorModeUsed`; fixes CR-001's incomplete application. |
| [CR-006](../governance/change-control/CR-006-traceability-matrix-design-and-adr-columns.md) | Traceability Matrix Gains Design and ADR Columns | Approved and applied | Adds Design and ADR columns to the traceability matrix, populated from ADRs. |
| [CR-007](../governance/change-control/CR-007-define-material.md) | Define "Material" | Approved and applied | Formal definition of "material" (change/ambiguity/risk); materiality-uncertain defaults to material. |
| [CR-008](../governance/change-control/CR-008-ai-transport-wording.md) | AI Transport Wording in Plan and Quickstart | Approved and applied | Documents AI transport as local Claude Code CLI subprocess, not raw API; flags argv-not-shell requirement. |
| [CR-009](../governance/change-control/CR-009-time-attitude-and-ai-scope-in-plan-14.md) | Time-Attitude Semantics and AI Scope in Plan §14 | Approved and applied | Time-based stop conditions reclassified as ESCALATE, not auto-halt; restores all six AI adapters to scope. |
| [CR-010](../governance/change-control/CR-010-pvt-009-restatement-and-stale-statuses.md) | PVT-009 Restated; Escalation Thresholds; Stale Statuses Corrected | Approved and applied | PVT-009 redefined as observable append-failure ceiling; adds PVT-016 per-node escalation thresholds; fixes stale status claims. |
| [CR-011](../governance/change-control/CR-011-data-model-alignment.md) | Data Model Aligned with CR-001, CR-005 and Instance-Keyed Nodes | Approved and applied | Updates data-model.md to instance-keyed node identity and the `executorKindUsed` rename that CR-001/CR-005 had missed. |
| [CR-012](../governance/change-control/CR-012-quickstart-resolved-findings-and-vocabulary.md) | Quickstart: Resolved Findings Stated as Resolved; Vocabulary; DS-B Subject | Approved and applied | Corrects quickstart.md's stale "unresolved" claims about DF-001/002/003; updates executor-kind vocabulary and DS-B description. |
| [CR-013](../governance/change-control/CR-013-orchestration-contract.md) | Orchestration Contract: Instance-Keyed Nodes, Governance Ops | Approved and applied | Replaces fixed 12-stage array with instance-keyed nodes; adds run-creation/gate-decision endpoints; bumps contracts to v2.0.0. |
| [CR-014](../governance/change-control/CR-014-credential-domain-separation.md) | Credential-Domain Separation and the Orchestrator Governance Surface | Approved and applied | CN-012: shortener creator credentials must never authenticate orchestrator surfaces. |
| [CR-015](../governance/change-control/CR-015-plan-ds-b-retention-timeouts-and-corrections.md) | Plan: Noisy-Neighbour Brownfield Subject, Thresholds, Node Identity | Approved and applied | Replaces DS-B subject with per-creator aggregate redirect rate-limit tier; adds PVT-016 thresholds; adds 10th gate class. |
| [CR-016](../governance/change-control/CR-016-task-plan-coverage-and-sequencing.md) | Task Plan: Coverage Gaps Closed, Noisy-Neighbour Subject | Approved and applied | Adds 13 new tasks (governance surfaces, sync tests, overrun gate, measurement tasks); task count 158→171. |
| [CR-017](../governance/change-control/CR-017-retention-as-archival.md) | Retention Posture: Indefinite Retention, Archival as Recommendation | Approved and applied | Withdraws binding retention/purge target (PVT-010); demo retains all records indefinitely; archival documented as recommendation only. |
| [CR-018](../governance/change-control/CR-018-validation-targets-preamble.md) | Validation-Targets Preamble Restated | Approved and applied | Fixes stale "15 values" preamble after CR-010/CR-017 changed the PVT count/status to 16 targets. |
| [CR-019](../governance/change-control/CR-019-research-ai-provider-alignment.md) | Phase-0 Research: AI-Provider Section Aligned to CLI Transport | Approved and applied | Corrects research.md's stale "Anthropic API"/"deterministic default" claims to match CR-008/CR-001. |
| [CR-020](../governance/change-control/CR-020-baseline-omissions-task-and-citations.md) | Baseline-Omissions Register Gains Creating Task; Uncited IDs Added | Approved and applied | Adds task T055a to create the promised baseline-omissions register file; adds 24 missing identifier citations. |
| [CR-021](../governance/change-control/CR-021-actor-authority-claim-qualified.md) | Actor-Authority Claim Qualified to Match Design | Applied | Narrows FR-ORC-021/NFR-AUT-001: actor identity on gate decisions is declared, not verified (no mechanism built). |
| [CR-022](../governance/change-control/CR-022-declared-retryable-sets.md) | The Twelve Declared Retryable Category Sets | Applied | Instantiates concrete declared-retryable failure-category sets per node (previously unspecified, defaulted to empty). |
| [CR-023](../governance/change-control/CR-023-overrun-escalation-deadline.md) | A Distinct Escalation Deadline for Node Overrun | **Withdrawn — never applied** | Proposed a separate 30-min PVT-017 deadline for node-overrun gates; owner rejected in favor of uniform PVT-006 (24h). |
| [CR-024](../governance/change-control/CR-024-acceptance-sweep-scoped-to-built-tiers.md) | User-Story-One Acceptance Sweep Scoped to What Is Built | Applied | Scopes T057's acceptance sweep to exclude the deliberately-deferred rate-limit tier; introduces "partial" as a traceability state. |
| [CR-025](../governance/change-control/CR-025-deterministic-ambiguity-detection-scope.md) | Ambiguity Detection Is AI-Backed | Applied | States plainly that S3 ambiguity detection is semantic/AI-backed, not deterministic, now that keyless mode is removed. |
| [CR-026](../governance/change-control/CR-026-scope-versus-timebox-statement.md) | Scope Versus Timebox, Reconciled in Writing | Applied | Explains why 173 tasks and a 2–3 day timebox both stand — timebox is a reporting instrument, not a promise. |
| [CR-027](../governance/change-control/CR-027-silence-test-falsifiability-coverage-exclusions-cosmetics.md) | Silence-Test Falsifiability, Coverage Exclusions, Two Cosmetic Defects | Applied | Adds falsifiability fixture (T061a); publishes coverage-denominator exclusion list; fixes two cross-references. |
| [CR-028](../governance/change-control/CR-028-keyless-mode-removed-specification.md) | Keyless Mode Removed: Specification | Applied | Retires CN-011, SC-014, NFR-AUT-004; orchestration always uses AI (Decision J); removes run-level `ai` flag. |
| [CR-029](../governance/change-control/CR-029-keyless-mode-removed-contracts.md) | Keyless Mode Removed: Contracts | Applied | Removes the `ai` flag field from workflow-state schema and openapi.yaml; bumps contracts to v3.0.0. |
| [CR-030](../governance/change-control/CR-030-keyless-mode-removed-plan-quickstart-data-model.md) | Keyless Mode Removed: Plan, Quickstart, Data Model, Research | Applied | Removes keyless-mode language from plan/quickstart/data-model/research; empties plan §3's Fallback column. |
| [CR-031](../governance/change-control/CR-031-keyless-mode-removed-task-plan.md) | Keyless Mode Removed: Task Plan | Applied | T071 revised to 5 real deterministic engines; T073 loses fallback assertion; net task count unchanged. |
| [CR-032](../governance/change-control/CR-032-fallback-retired-decision-k.md) | Fallback Retired: a Documented Honest Absence (Decision K) | Applied | Retires FR-ORC-015 and EC-023; degradation story becomes bounded-retry-then-suspend only. |
| [CR-033](../governance/change-control/CR-033-no-plan-gate-trigger-restated-and-injected.md) | EC-040's Trigger Restated for an Always-AI System | Applied | Restates the no-plan-gate trigger (no longer reachable via "AI off"); demonstrates the gate via injected fault. |
| [CR-034](../governance/change-control/CR-034-ai-reduction-option-struck.md) | The AI-Reduction Checkpoint Option Is Struck | Applied | Removes the End-Day-2 AI-stage-reduction checkpoint option, since it would mean shipping an incomplete orchestrator. |
| [CR-035](../governance/change-control/CR-035-slice1-exit-condition-corrected.md) | Slice 1's Exit Condition Corrected | Applied | Corrects an unreachable Slice-1 exit condition back to plan.md's own wording; defers the drift proof to Slice 2. |
| [CR-036](../governance/change-control/CR-036-slice-2-contract-baseline.md) | The Contract Baseline: Response Examples, Migration Rules | Approved at Gate 7 (classification corrected PATCH→MINOR) | Adds 18 OpenAPI response examples; discloses 3 of 5 contract schema files were previously unlinted despite T011 marked complete. |
| [CR-037](../governance/change-control/CR-037-provisioning-script-relocated.md) | Operator Provisioning Script Relocated Out of `ops/` | Approved | Moves `provision-creator.sh` from `ops/` to `scripts/` (real deliverable location); path-only change. |
| [CR-038](../governance/change-control/CR-038-control-plane-controllers-relocated.md) | Control-Plane Controllers Relocated | Approved | Relocates 3 orchestrator HTTP controllers to `orchestration/api/` to satisfy the plane-separation dependency-direction rule. |
| [CR-039](../governance/change-control/CR-039-any-join-semantics-removed.md) | ANY Join Semantics Removed | Approved (MAJOR) | Removes the never-honored ANY join value; ALL is the only join semantic. |
| [CR-040](../governance/change-control/CR-040-gate-classes-widened-to-ten.md) | Persisted Gate-Class Vocabulary Widened to Ten | Approved (MINOR) | Widens `approval.schema.json`'s gateClass enum from 8 to 10, adding NO_CHANGE_PLAN and NODE_OVERRUN. |
| [CR-041](../governance/change-control/CR-041-escalation-target-added-to-gate-decision-request.md) | `escalationTarget` Added to GateDecisionRequest | Approved (MINOR) | Adds the missing `escalationTarget` field to the wire-contract, closing a gap vs. the persisted model. |
| [CR-042](../governance/change-control/CR-042-gemini-cli-adapter-for-live-demonstration-runs.md) | Gemini CLI Adapter for Live-Demonstration Runs | Approved (MINOR); later corrected by CR-047 | Adds a second StageAiProvider (Gemini CLI) after believing Claude CLI couldn't be spawned as a nested subprocess. |
| [CR-043](../governance/change-control/CR-043-policy-evaluation-schema-synced-to-policy-set-1-1-0.md) | `policy-evaluation.schema.json` Synced to policy-set-1.1.0 | Applied (MINOR) | Bumps the policy-evaluation schema's version/policyId enum to match a decision CR-013 already made but never applied here. |
| [CR-044](../governance/change-control/CR-044-conductor-run-orchestration-driver-added-as-t131a.md) | Task-Plan Gap: Run-Orchestration Driver Never Tasked (T131a) | Approved (MAJOR) | Adds T131a to build "Conductor," the run-dispatch/orchestration-loop engine, found missing entirely from the task plan. |
| [CR-045](../governance/change-control/CR-045-conductor-runtime-wiring-t131b-t131c-t082a.md) | Conductor Runtime Wiring: T131b, T131c, T082a | Approved (MAJOR/MINOR) | Adds gate-decision-resumes-run wiring, real BranchApplier/TestSuiteRunner, and the run-submission controller. |
| [CR-046](../governance/change-control/CR-046-gemini-adapter-429-misclassification-fixed.md) | Gemini Adapter 429/Quota Misclassification Fixed | Approved (MINOR) | Fixes the adapter treating 429/quota-exhausted as permanent INTERNAL failure instead of retryable RATE_LIMITED. |
| [CR-047](../governance/change-control/CR-047-claude-cli-nesting-guard-corrected.md) | Amendment 03's Nesting-Guard Finding Corrected | Approved (MINOR) | Corrects CR-042's claim that Claude CLI can't nest — the failure was the agent's own Bash allowlist; restores Claude CLI as transport. |
| [CR-048](../governance/change-control/CR-048-ds-a-requirement-tightened-after-ambiguity-findings.md) | DS-A Greenfield Requirement Tightened After Two Ambiguity Findings | Approved (MINOR) | Rewrites the DS-A demo requirement to pre-answer six real ambiguity gaps found independently by Gemini and Claude. |
| [CR-049](../governance/change-control/CR-049-s3-materiality-classification-conformance.md) | S3 Conforms to CR-007's Materiality Predicate | Approved (MODERATE) | Wires CR-007's materiality predicate into S3's actual prompt (never applied there); fixes a JSON-null parsing bug found while verifying. |
| [CR-050](../governance/change-control/CR-050-ds-a-requirement-self-contained-final.md) | DS-A Requirement Made Fully Self-Contained (supersedes CR-048) | *Conversational approval* (LOW) | Folds four more owner decisions into the DS-A requirement text since S3 has no codebase visibility. |
| [CR-051](../governance/change-control/CR-051-ds-a-clean-pass-subject-replaced.md) | DS-A Clean-Pass Subject Replaced with a Minimal Requirement | *Conversational approval* (LOW) | Retires the expiry-endpoint subject (too rich a surface to pass cleanly); replaces with `GET /v1/version`. |
| [CR-052](../governance/change-control/CR-052-materiality-predicate-sharpened-to-behavioral-fork.md) | S3's Materiality Predicate Sharpened to a Genuine Behavioral Fork | *Conversational approval* (MODERATE) | Sharpens S3's predicate to require an actual behavioral fork, not linguistic imperfection, after false-positive findings persisted. |
| [CR-053](../governance/change-control/CR-053-ds-a-subject-switched-to-zero-behavior-test-addition.md) | DS-A Subject Switched to a Zero-Runtime-Behaviour Test Addition | *Conversational approval* (LOW) | Retires the /v1/version subject; switches to adding unit tests to an existing untested class. |
| [CR-054](../governance/change-control/CR-054-ds-a-subject-switched-to-new-feature.md) | DS-A Subject Switched to a Genuinely New Feature; Routine Clarifications Delegated | *Conversational approval* (LOW) | Switches back to a new-feature subject; establishes the standing delegation for routine S4 clarifications (see [Delegations](#5-delegations)). |
| [CR-055](../governance/change-control/CR-055-s7-existing-file-content-injection.md) | S7 Reads Existing Files Through Orchestration, Not the Executor | *Conversational approval* (MEDIUM) | Conductor pre-fetches file content and injects it into StageInput; executor stays a pure function. |
| [CR-056](../governance/change-control/CR-056-ds-a-new-files-only-pivot.md) | Greenfield Pivots to New-Files-Only After CR-055 Didn't Fully Fix S7 | *Conversational approval* (LOW) | Pivots DS-A to new-files-only sourcing after a live attempt still corrupt-patched an existing-file edit. |
| [CR-057](../governance/change-control/CR-057-architecture-gate-conditional-on-materiality.md) | Architecture Gate Made Conditional on S6's Own Materiality Classification | Approved (MEDIUM); Part 2 declined | Fixes S6 firing unconditionally, contrary to plan §5's "material design decisions" trigger; declines to also weaken the ambiguity classifier. |
| [CR-058](../governance/change-control/CR-058-requirement-text-resolves-genuine-ambiguity.md) | Greenfield Requirement Sharpened to Remove Ambiguity CR-056 Left Open | Approved under standing delegation (LOW) | Fully specifies version-sourcing mechanism in the DS-A requirement to close two material forks CR-057 found. |
| [CR-059](../governance/change-control/CR-059-requirement-text-simplified-new-files-only.md) | Greenfield Requirement Replaced with Simpler Wording; S4 Matcher Fixed | Approved (LOW/MEDIUM) | Replaces DS-A requirement with simpler owner wording; fixes clarification matcher for SEMANTIC_CONTRADICTION findings. |
| [CR-060](../governance/change-control/CR-060-search-replace-implementation-editor.md) | S7 Authors a JSON Change Set, Never a Unified Diff | Approved (MEDIUM) | Replaces S7's unified-diff output (which LLMs kept corrupting) with a structured JSON change-set applied deterministically. |
| [CR-061](../governance/change-control/CR-061-matcher-whole-word.md) | Clarification Matcher: Whole-Word Matching, Not Substring | Approved (MEDIUM) | Replaces fragile substring keyword-matching with whole-word regex matching, closing false-match collisions. |
| [CR-062](../governance/change-control/CR-062-clean-greenfield-requirement.md) | Greenfield Requirement Replaced with Clean, Minimal Real Feature | Approved (LOW) | Removes implementation-workaround clauses now that CR-060 gives real existing-file editing capability. |
| [CR-063](../governance/change-control/CR-063-matcher-plural-and-credential-fix.md) | Matcher Regression: Plural "Parameters" and Credential-Only Finding Fixed | Approved under standing delegation (LOW) | Fixes two more clarification-matcher gaps found by a pre-run sanity check. |
| [CR-064](../governance/change-control/CR-064-version-endpoint-openapi-path-count.md) | `GET /v1/version` Legitimately Grows OpenAPI to Nine Paths | Approved (LOW) | Bumps a hardcoded contract-lint test's expected path count from 8 to 9. |
| [CR-065](../governance/change-control/CR-065-testingengine-executed-behaviors.md) | TestingEngine's Test-Results Artifact Gains a Real `executedBehaviors` Array | Approved under standing delegation (LOW) | Wires a real, Surefire-derived executed-test-names list, closing a producer/consumer shape gap with the documentation stage's drift guard. |
| [CR-066](../governance/change-control/CR-066-live-ai-output-robustness.md) | Live-AI Output Robustness: S7 JSON Extraction Hardened, S9 Prompt Honest | Approved (MEDIUM) | Hardens S7's JSON extraction with a bounded self-correction retry; makes S9's prompt an explicit verbatim allow-list. |
| [CR-067](../governance/change-control/CR-067-s3-qualitychecks-unconditional-prompt.md) | S3's Prompt States `qualityChecksPerformed` Is Required Unconditionally | Approved (LOW) | Fixes S3's prompt so the model never omits `qualityChecksPerformed` on NOT_MATERIAL records. |
| [CR-068](../governance/change-control/CR-068-build-failure-detail-captures-stdout.md) | GitWorktreeBranchApplier's Build-Failure Detail Now Captures stdout | Approved under standing delegation (LOW) | Fixes blank build-failure diagnostics — Maven compile errors print to stdout, not stderr, under `-q`. |
| [CR-069](../governance/change-control/CR-069-ds-b-test-file-bookkeeping-followup.md) | DS-B's Own Hand-Authored Test-File Follow-Up | Approved under standing delegation (LOW) | Retires/rewrites two sibling rate-limit tests' own stale tier-absence assertions, hand-authored and disclosed as such, never presented as S7's own output. |
| [CR-070](../governance/change-control/CR-070-s3-ambiguityclass-resolutionstate-disjoint-prompt.md) | S3's Prompt States `ambiguityClass`/`resolutionState` Are Disjoint Vocabularies | Approved under standing delegation (LOW) | Fixes S3's prompt after a real, live DS-B attempt used a `resolutionState` value as an `ambiguityClass` value. |

## 3. Gate Decisions

Directory: [`docs/governance/gate-decisions/`](../governance/gate-decisions/). All decisions below
were made by Pravallika Veeravalli (the deciding human throughout this repository's history).

### 3.1 Program-level gates

| File | Gate | Outcome | Date | Artifact approved | Summary |
|---|---|---|---|---|---|
| [gate-01-constitution.md](../governance/gate-decisions/gate-01-constitution.md) | Gate 1: Constitution Ratification | Approved | 2026-09-17 | `.specify/memory/constitution.md` v1.0.0 | Ratified the project constitution v1.0.0. |
| [gate-02-specify.md](../governance/gate-decisions/gate-02-specify.md) | Gate 2: Specification Approval | Approved, 3 blocking clarifications resolved first | 2026-09-18 | `spec.md` | Resolved AQ-001 (custom auth/ownership), AQ-002 (per-event analytics), AQ-003 (pluggable AI/deterministic executor model). |
| [gate-03-clarify.md](../governance/gate-decisions/gate-03-clarify.md) | Gate 3: Clarification Resolution | Approved — all 5 answers folded | 2026-09-19 | `spec.md` (CL-005…CL-009) | Resolved safe-stop, retry classification, rollback-vs-compensation, duplicate-destination handling, restart-recovery scope. |
| [gate-04-adr.md](../governance/gate-decisions/gate-04-adr.md) | Gate 4: Architecture and Technology Selection | Approved — all 14 ADRs accepted, 3 amended/conditioned | 2026-09-20 | ADR-001…ADR-014 | Accepted all 14 ADRs; processed CR-001 (applied), opened CR-002. |
| [gate-04-closing-package.md](../governance/gate-decisions/gate-04-closing-package.md) | Gate 4 Closing Package | Approved — 7 decisions | 2026-09-20 | `plan.md`, CR-002, DF-002/DF-003, all 15 PVTs, §14 controls, MTTR method | Approved the technical plan; resolved DF-002/DF-003 and remaining PVT/timebox items. |
| [gate-05-task-plan.md](../governance/gate-decisions/gate-05-task-plan.md) | Gate 5: Task Plan Approval | Approved — 158 tasks, with time-attitude amendment | 2026-09-20 | `tasks.md` | Approved the 158-task plan; time-based stop triggers escalate rather than auto-cut scope. |
| [gate-06-scope-control.md](../governance/gate-decisions/gate-06-scope-control.md) | Gate 6: Scope-Control Acknowledgement | Approved, one condition | 2026-09-21 | 6 Phase-0 delivery registers (`docs/delivery/`) | Acknowledged the registers as operative controls; struck the AI-reduction scope-cut option. |
| [gate-07-contract-baseline.md](../governance/gate-decisions/gate-07-contract-baseline.md) | Gate 7: Contract and Schema Baseline | Approved, classification corrected PATCH→MINOR | 2026-09-21 | [CR-036](../governance/change-control/CR-036-slice-2-contract-baseline.md) (contracts baseline at Slice 2) | Froze the Slice-2 contract baseline; ordered an overclaim sweep. |
| [amendment-001-decision.md](../governance/gate-decisions/amendment-001-decision.md) | Constitution Amendment 001 (v1.0.0 → v1.1.0) | Approved — all 6 sub-decisions | 2026-09-18 | [proposal-001](../governance/amendments/proposal-001-gate-record-materialization.md) | Requires gate records to be materialized as repository artifacts; bumped constitution to v1.1.0. |

### 3.2 DS-A scenario gates

| File | Subject | Outcome | Date | Summary |
|---|---|---|---|---|
| [ds-a/s4-clarifications-consolidated.md](../governance/gate-decisions/ds-a/s4-clarifications-consolidated.md) | S4 ambiguity, consolidated (T132, `GET /v1/version`) | Approved | 2026-09-22 | Consolidates answers to three live S4 findings (Content-Type/Cache-Control conformance, observability-scope exclusion). |
| [ds-a/s4-header-conformance-clarification.md](../governance/gate-decisions/ds-a/s4-header-conformance-clarification.md) | S4 header-conformance clarification (T132) | Approved | 2026-09-22 | Resolves two live findings on semantic (not exact-string) header conformance; folded into the consolidated record above. |
| [ds-a/s4-routine-clarifications-under-delegation.md](../governance/gate-decisions/ds-a/s4-routine-clarifications-under-delegation.md) | Routine S4 clarifications under standing delegation (T132, CR-054) | Approved | 2026-09-22 | Applies the standing delegation to resolve routine findings without a fresh live round-trip. |
| [ds-a/s6-architecture-approved.md](../governance/gate-decisions/ds-a/s6-architecture-approved.md) | S6 architecture approval (T132, CR-054) | Approved | 2026-09-22 | Approves the `VersionController` design (build-metadata-sourced version, no-auth pattern matching `HealthController`). |

### 3.3 DS-B scenario gates

| File | Subject | Outcome | Date | Summary |
|---|---|---|---|---|
| [ds-b/t136a-security-gate-approved.md](../governance/gate-decisions/ds-b/t136a-security-gate-approved.md) | T136a brownfield security-sensitive gate (per-creator aggregate redirect tier) | Approved, all 4 items | 2026-09-22 | Approves `AggregateRedirectLimiter` (post-lookup, in-process counter); confirms accepted throttling trade-off and non-disclosure requirement. |

### 3.4 DS-C scenario gates

| File | Subject | Outcome | Date | Summary |
|---|---|---|---|---|
| [ds-c/s4-central-contradiction-clarification.md](../governance/gate-decisions/ds-c/s4-central-contradiction-clarification.md) | S4 central contradiction + 4 sub-questions (T138/T139) | Approved | 2026-09-22 | Resolves the expire-vs-retain-vs-trusted-partner contradiction: expiry changes redirect eligibility, not data existence. |
| [ds-c/scenario-accepted-as-demonstrated.md](../governance/gate-decisions/ds-c/scenario-accepted-as-demonstrated.md) | Scenario accepted as demonstrated; trusted-partner feature not built | Accepted as demonstrated | 2026-09-22 | Accepts the detect→clarify→replan→resume mechanism as proven; declines to build the trusted-partner feature itself. |

## 4. Amendments

Directory: [`docs/governance/amendments/`](../governance/amendments/). Amendment *proposals* live here;
the decision on a proposal is a gate-decision record (see [3.1](#31-program-level-gates)), and the
change itself lands in the constitution's own `## Amendment History` section.

| File | Title | Status | Summary |
|---|---|---|---|
| [proposal-001-gate-record-materialization.md](../governance/amendments/proposal-001-gate-record-materialization.md) | Gate Record Materialization | Accepted 2026-09-18 — applied as constitution v1.1.0 | Requires gate records be materialized as repository artifacts with a named responsible actor and deadline. Decided by [amendment-001-decision.md](../governance/gate-decisions/amendment-001-decision.md). |

## 5. Delegations

Directory: [`docs/governance/delegations/`](../governance/delegations/).

| File | Title | Status | Summary |
|---|---|---|---|
| [routine-clarification-delegation.md](../governance/delegations/routine-clarification-delegation.md) | Standing delegation — routine S4 clarification resolution | Granted 2026-09-21, restated/acted on 2026-09-22 | Owner delegates recording of routine, already-decided-in-substance S4 clarifications to the acting session; excludes substantive gates (S6, S11, T136a, final submission) and any genuinely novel finding. |

## 6. Exceptions

Directory: `docs/governance/exceptions/` — **does not exist on disk.** This is a designated location
under the constitution's §Exception procedure and Principle VI, not a materialized record set: no
policy exception has been raised in this repository, so the directory has never been created. A
reviewer should not read its absence as an oversight — it is the honest state of "zero exceptions to
date."
