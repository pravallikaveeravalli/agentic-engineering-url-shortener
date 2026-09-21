# Phase 1 Data Model: Agentic Software Engineering System: URL Shortener

**Date**: 2026-09-19 | **Plan**: [plan.md](./plan.md) | **Spec entities**: KE-01..KE-29

**Status**: **Derived design artifact.** Approved indirectly via `plan.md`'s approval at the Gate 4 closing
package (2026-09-20) — it was **not** independently gated. Changes to it pass through change control, as they do
for the plan. The authoritative entity definitions are the spec's `KE-*` entries; this document elaborates them
and must not contradict them.

Amended by **CR-011** (2026-09-20): aligned with CR-001's `ai` flag, CR-005's `executorKindUsed` rename, and the
owner's instance-keyed node-identity decision. Before that amendment this document was the only artifact still
carrying the pre-CR-001 run-level `executor_mode` enum.

Technology-neutral. Types are logical; the physical mapping follows ADR-002. Field-level validation
traces to the FR that requires it.

---

## Application plane

### ShortLink (KE-01)

| Field | Type | Constraints | Source |
|---|---|---|---|
| `short_code` | string | **PK**, unique, fixed alphabet, length per PVT-005 | FR-URL-006 |
| `destination` | string | Non-empty, ≤ max length, allow-listed scheme, normalized | FR-URL-002..004 |
| `creator_id` | ref Creator | **Required** — no link without an owner | FR-URL-018 |
| `created_at` | timestamp | Immutable | FR-URL-001 |
| `expires_at` | timestamp | Must be future at creation | FR-URL-009 |
| `state` | enum | `ACTIVE` \| `EXPIRED` | FR-URL-008 |

**Rules**: destination normalized before validation and storage, idempotently, without altering the
effective target (FR-URL-003). **Never deleted** — the only permitted mutation is `ACTIVE → EXPIRED`,
which is also its compensating action (Compensation Register, T-13).

### RedirectEvent (KE-02)

| Field | Type | Constraints |
|---|---|---|
| `id` | id | PK |
| `short_code` | ref ShortLink | Required |
| `occurred_at` | timestamp | Required |

**Rules**: append-only. **No follower-identifying field may exist** — no IP, user agent, referrer,
device identifier, or geolocation (FR-URL-010, NFR-SEC-005). The absence is enforced by
`POL-PRIV-001` as a schema assertion, not by convention.

### Creator (KE-23) / CreatorCredential (KE-24)

| Entity | Fields | Rules |
|---|---|---|
| Creator | `id`, `name`, `created_at`, `active` | Provisioned by operator script only; no HTTP issuance surface (FR-URL-019) |
| CreatorCredential | `id`, `creator_id`, `key_hash`, `created_at`, `expires_at?`, `revoked_at?` | **`key_hash` only** — SHA-256 of the full presented string including the `crk_` prefix; never plaintext, never reversible, never logged. **`expires_at` is nullable, and null is reachable only by the operator passing the explicit literal `never`** — the provisioning script has no default (ADR-013). Expired and revoked credentials are refused with an identical response shape |

### IdempotencyRecord (KE-03)

| Field | Type | Purpose |
|---|---|---|
| `marker` | string | PK (scoped per creator) |
| `request_fingerprint` | hash | Detects same-marker-different-content → conflict (CL-008 case 3) |
| `short_code` | ref ShortLink | The link the original request produced |
| `created_at` | timestamp | |

**Rules**: same marker + matching fingerprint → replay the original as labelled success, zero new side
effects. Same marker + differing fingerprint → conflict; mint nothing, change nothing.

---

## Control plane

### WorkflowRun (KE-04)

| Field | Type | Constraints |
|---|---|---|
| `id` | correlation id | PK; propagated to every log, metric, trace, and row |
| `state` | enum | `PENDING` \| `RUNNING` \| `SAFE_STOP` \| `COMPLETED` \| `REJECTED` \| `ABANDONED` |
| `terminal_state` | enum? | **Null while suspended** — a suspended run has no terminal outcome |
| `policy_set_version` | string | Required; the version evaluated |
| `last_activity_at` | timestamp | Drives the idle clock — **never** creation time |
| `auto_abandon_at` | timestamp? | Computed; exposed via inspection when suspended |
| `ai` | enum | `on` \| `off`, **default `off`** — whether AI executors participate. Named for the one thing it controls: a run with `ai: off` may still contain a `HUMAN` execution at the stage-7 no-plan gate, so a run-level determinism claim would over-promise (FR-ORC-029, renamed by **CR-001**) |
| `suspension_reason` | text? | Required when `SAFE_STOP` |

**Terminal set is exactly** `COMPLETED`, `REJECTED`, `ABANDONED` (CL-005).

### StageNode (KE-05) / DependencyEdge (KE-06)

| Entity | Fields |
|---|---|
| StageNode | `id`, `run_id`, `node_key` (string — `"S1".."S12"` for a singleton or fan-out-parent stage, `"S7.1".."S7.n"` for a fan-out child, `"S7.join"` for its join node; **unique per run**), `stage_number` (1–12 — the stage the node belongs to, no longer the node's identity), `node_role` (`SINGLETON` \| `FAN_OUT_PARENT` \| `FAN_OUT_CHILD` \| `JOIN`), `parent_node_key?` (set on a `FAN_OUT_CHILD` and a `JOIN`, null otherwise), `state`, `executor_class`, `executor_kind_used`, `attempts_used`, `entered_at`, `exited_at?`, `blocking_reason?` |
| DependencyEdge | `id`, `run_id`, `from_node_key`, `to_node_key`, `join_semantics` (`ALL` \| `ANY`) — edges address **nodes**, never stage numbers, so each fan-out child's edges are individually declared and a replanned instance's topology stays queryable (FR-ORC-002, ADR-008) |

**Stage states**: `BLOCKED`, `READY`, `RUNNING`, `AWAITING_APPROVAL`, `RETRY_WAIT`, `FALLBACK`,
`ROLLING_BACK`, `COMPENSATING`, `SUCCEEDED`, `FAILED`, `INVALIDATED`, `SKIPPED`. Allowed and
**prohibited** transitions per `plan.md` §3; prohibited ones are rejected and tested.

`executor_kind_used` — `DETERMINISTIC` | `AI` | `HUMAN` — is **required on every executed node**; an unlabelled
execution may not appear in evidence (FR-ORC-029; renamed from `executor_mode_used` by **CR-005** and extended
with `HUMAN` by **CR-001**). `HUMAN` is **never flag-selectable**: it is recorded only as the outcome of a
no-plan-gate decision (FR-ORC-031). Note the deliberate asymmetry with `executor_class`, whose vocabulary is
`DETERMINISTIC | AI_CAPABLE | HUMAN_GATE` and which **does not include `HUMAN`** — a stage may not be declared
human-implemented up front.

### StageEffectContract (KE-29)

| Field | Type | Purpose |
|---|---|---|
| `stage_number` | int | PK |
| `retryable_categories` | set | The declared half of the two-vote rule |
| `effect_idempotent` | boolean | **Design-time** declaration; gates timeout retry. Never executor self-certified |
| `effect_reversibility` | enum | `ERASABLE` \| `IRREVERSIBLE` \| `UNCLASSIFIED` |
| `compensating_action` | string? | **Required when `IRREVERSIBLE`, or the stage does not load** |

### FailureEnvelope (KE-26) / RetryRuling (KE-27)

| Entity | Fields | Rules |
|---|---|---|
| FailureEnvelope | `category` (closed set), `proposed_classification`, `detail` | `UNKNOWN`, undeclared, or malformed → permanent |
| RetryRuling | `id`, `stage_id`, `attempt`, `envelope`, `executor_proposal`, `orchestrator_ruling`, `reason`, `decided_at` | **Two-signature**: both sides recorded. Retry only where both vote yes |

### EffectRecord (KE-28)

`id`, `stage_id`, `effect_type`, `reversibility_class`, `applied_at`, `correction_action?`,
`correction_applied_at?`, `correction_kind` (`ROLLBACK` \| `COMPENSATION`).

**Rules**: at most one correction per effect, even if a later retry succeeds (EC-022). `ROLLBACK` is
prohibited on an effect landing in an immutable store.

### Gates and decisions (KE-10, KE-11)

| Entity | Fields |
|---|---|
| ApprovalGate | `id`, `stage_id`, `gate_class`, `wait_deadline`, `disclosed_auto_abandon_at`, `requested_at` |
| GateDecision | `id`, `gate_id`, `outcome`, `actor`, `decided_at`, `reason`, `repository_record_path`, `supersedes?` |

**Rules**: `outcome` ∈ five standard values. `repository_record_path` is **required** — a decision is
not effective until materialized (Constitution v1.1.0). `supersedes` carries approval voiding on
replan (EC-020) — never deletion. Both deadlines must be present at request time (CL-005 addendum).

### Requirements, tasks, clarifications (KE-07, KE-08, KE-09, KE-12)

| Entity | Fields |
|---|---|
| RequirementRecord | `id`, `run_id`, `external_id`, `type` (`FUNCTIONAL` \| `NON_FUNCTIONAL`), `statement`, `status` |
| AmbiguityRecord | `id`, `requirement_id`, `ambiguity_class`, `affected_path`, `resolution_state`, `quality_checks_performed`, `no_clarification_reason?` |
| ClarificationDecision | `id`, `ambiguity_id`, `actor`, `question`, `answer`, `decided_at` |
| TaskRecord | `id`, `run_id`, `requirement_ids` (**≥1, enforced**), `depends_on`, `validation_status` |

`no_clarification_reason` is what DS-A requires to be recorded when a well-formed requirement correctly
skips S4.

### Governance and evidence (KE-13, KE-14, KE-17..KE-22)

| Entity | Key fields |
|---|---|
| ImpactAnalysis | `id`, `run_id`, seven required dimensions, `created_at` (**must precede first code change**) |
| ReplanEvent | `id`, `run_id`, `cause`, `invalidated_stages`, `voided_approvals`, `occurred_at` |
| PolicyCheckResult | `id`, `run_id`, `policy_id`, `policy_version`, `outcome` (four values), `reason` |
| PolicyException | `policy_id`, `clause`, `reason`, `scope`, `approving_authority`, `compensating_control`, `residual_risk`, `approved_at`, `expires_at` — **all seven required** |
| AuditRecord | `actor_type`, `action`, `occurred_at`, `affected_artifact`, `result`, `reason` — **all six required**, append-only, immutable |
| EvidenceArtifact | `id`, `run_id`, `kind`, `location`, `measurement_label` (`MEASURED` \| `PROPOSED`) |
| ReleaseReadinessReport | `id`, `run_id`, `blocking` (bool), `unmet_conditions[]`, `evaluated_at` |
| TraceLink | `from_artifact`, `to_artifact`, `link_type` |
| ArtifactVersion | `id`, `stage_id`, `content_hash`, `produced_at` |
| TestResult | `id`, `task_id`, `test_id`, `outcome`, `executed_at` — **null `executed_at` is never a pass** |

### FailureEvent — the MTTR population

| Field | Purpose |
|---|---|
| `failure_detected_at` | MTTR start |
| `recovery_started_at` | Diagnostic |
| `recovery_completed_at` | MTTR end |
| `individual_recovery_duration` | Derived, **excluding** time in `AWAITING_APPROVAL`/`SAFE_STOP` |
| `human_wait_duration` | The declared exclusion, reported separately |
| `recovery_mechanism` | `retry` \| `fallback` \| `rollback` \| `compensation` \| `resume` \| `human` |
| `recovered` | boolean — **false rows are excluded from the MTTR denominator** and counted separately |

---

## Cross-cutting invariants

1. No ShortLink without a Creator; no RedirectEvent without a ShortLink.
2. No TaskRecord without ≥1 RequirementRecord; no RequirementRecord delivered without a TaskRecord
   (`POL-TRC-001`, zero orphans both directions).
3. AuditRecord, GateDecision, PolicyCheckResult, RedirectEvent are **append-only**.
4. `terminal_state` is non-null **iff** `state` ∈ the three terminal values.
5. `auto_abandon_at` is non-null **iff** `state = SAFE_STOP`.
6. Every executed StageNode has a non-null `executor_kind_used`.
7. A StageEffectContract with `IRREVERSIBLE` and no `compensating_action` **fails to load** (EC-034).
8. Every RetryRuling carries both signatures; a retry with one is invalid (EC-032).
9. `node_key` is unique per run. Every stage 1–12 appears exactly once as either a `SINGLETON` or a
   `FAN_OUT_PARENT`, so a freshly materialised run holds **thirteen** nodes: eleven singletons, the S7 fan-out
   parent, and its `JOIN`. There is **no fixed upper bound** — the old "exactly twelve" invariant was wrong,
   because it made S7's per-task fan-out unrepresentable (analyze finding H4).
10. A `FAN_OUT_PARENT` has exactly one `JOIN` node from the outset and **zero or more** `FAN_OUT_CHILD` nodes,
    created when the preceding stage yields tasks. The join does not proceed while any child is incomplete or
    failed (EC-018, FR-ORC-003). Uniqueness and this join invariant are asserted by T074's test rather than by
    JSON Schema, which cannot express uniqueness-by-property.
