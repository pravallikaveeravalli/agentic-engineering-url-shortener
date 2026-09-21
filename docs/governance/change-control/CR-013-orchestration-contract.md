# Change Request CR-013 — Orchestration Contract: Instance-Keyed Nodes, Governance-Surface Authority, Run-Creation and Gate-Decision Operations

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, from `/speckit-analyze` findings **H4**, **H7**, **H9**, **L7** |
| Revision | **Draft 2**, 2026-09-20 — owner **Decision D** replaced Edit 15's versioning rule and armed it for the scenario demonstrations (new Edit 17) |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"go ahead."* |
| Affected approved artifacts | `contracts/workflow-state.schema.json`, `contracts/openapi.yaml`, `contracts/README.md` — *"Every change to a file in this directory requires a change-control record (`POL-CHG-001`), regardless of release state."* |
| Governing constitution | v1.1.0; `POL-CHG-001`, `POL-CHG-002` |
| Source decisions | Owner **Decision 2** (instance-keyed node identity), owner **Decision 3** (credential-domain separation), FR-ORC-007, FR-ORC-013 |
| Application order | **Must apply in the same act as CR-011.** The two records change one model across two artifact classes; applying either alone leaves the design document and the contract disagreeing. |

## Why this change exists

Three defects, one of them structural.

**H4 — the state contract cannot represent the parallelism the graded deliverable rests on.**
`workflow-state.schema.json` fixes `stages` at `minItems: 12, maxItems: 12` with an integer `stageNumber` as
identity, and edges as integer `fromStage`/`toStage`. `plan.md` §3 shows `S7.1 / S7.2 / S7.n` fanning out per
task, and `tasks.md` T076 requires *"S7 per-task fan-out with `ALL` join"*. **Those cannot both be true.** T074
validates persisted snapshots against this schema, so the fan-out would fail contract validation — or, worse, be
quietly flattened to one S7 row and the parallelism claim would rest on nothing a reviewer could query.

**H7 — there is no way to start a run or record a decision.** The document carries `GET /v1/runs/{runId}` and
nothing else on the control plane. FR-ORC-007 requires an engineer to *"create a workflow run from a submitted
requirement and receive a durable run identifier"*; US-2 requires a reviewer to record a gate outcome. Neither has
an operation. `quickstart.md` implies a CLI, but no task builds one and a CLI has no contract — so the governance
surface the assessment is *about* is the one surface with no interface definition.

**H9 — the one control-plane operation that exists is secured with the wrong credential class.**
`GET /v1/runs/{runId}` requires `creatorApiKey` — the *link creator* credential whose entire purpose under CL-001
was to separate two populations that must be treated differently. Nothing provisions an engineer or reviewer
identity, and no run-scoped authorization is defined anywhere, so the operation either lets any creator read any
run or forces reviewers to hold link-creation keys.

## Owner decision recorded — Decision 3, verbatim

> I don't think creators matter in orchestrator it is a url shortner concept, don't leak those into orchestrator.

Resolved principle: the shortener's actor model and the orchestrator's actor model are **separate domains, and no
credential class crosses the boundary**. Orchestrator governance surfaces sit under the **machine-access trust
boundary** — the ability to run commands on the host is the boundary — consistent with the owner's AQ-001 ruling
that operator terminal output is not application logging. A separate reviewer credential class was considered and
**not taken**: more machinery than the assessment needs. Recorded as future work.

## Owner approval — 2026-09-20

Approved by **Pravallika Veeravalli**: *"go ahead."*

Her approval followed a three-item list and carries all three, recorded as relayed:

1. **The package as revised**, including the §11 brownfield wording.
2. **PVT-010 explicitly withdrawn** — the binding 30/90-day deletion target is formally retired and replaced by the
   documented production recommendation, **by her explicit decision, not by implication**.
3. **Policy set `1.1.0`**, on the ground she herself set for the API contract: **version discipline binds from first
   real use**, and no run has ever evaluated `1.0.0`.

## Applied edits — `contracts/workflow-state.schema.json`

### Edit 1 — `version` and `description`

**OLD**

```json
  "version": "1.0.0",
  "description": "Persisted run state. Validated against persisted snapshots in contract tests so the invariants below are machine-enforced rather than documented.",
```

**NEW**

```json
  "version": "2.0.0",
  "description": "Persisted run state. Validated against persisted snapshots in contract tests so the invariants below are machine-enforced rather than documented. v2.0.0 (CR-013) replaces integer stage identity with instance-keyed node identity, because the previous fixed twelve-element array could not represent S7's per-task fan-out (FR-ORC-003).",
```

### Edit 2 — `required`: `stages` → `nodes`

**OLD**

```json
  "required": ["runId", "state", "policySetVersion", "ai", "lastActivityAt", "stages", "edges"],
```

**NEW**

```json
  "required": ["runId", "state", "policySetVersion", "ai", "lastActivityAt", "nodes", "edges"],
```

### Edit 3 — `stages` property replaced by `nodes`

**OLD** — the whole `"stages"` block (`workflow-state.schema.json:28-56`), beginning
`"stages": { "type": "array", "minItems": 12, "maxItems": 12,` and ending with its closing braces.

**NEW**

```json
    "nodes": {
      "type": "array",
      "minItems": 13,
      "description": "Execution nodes, not stages. A freshly materialised run holds thirteen: eleven SINGLETON stages, the S7 FAN_OUT_PARENT, and its JOIN. FAN_OUT_CHILD nodes are appended when the preceding stage yields tasks, so there is no upper bound (FR-ORC-003). nodeKey uniqueness and the join invariant are asserted by T074's test — JSON Schema cannot express uniqueness by property.",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["nodeKey", "stageNumber", "nodeRole", "name", "state", "executorClass", "attemptsUsed"],
        "properties": {
          "nodeKey": {
            "type": "string",
            "pattern": "^S([1-9]|1[0-2])(\\.([1-9][0-9]*|join))?$",
            "description": "Node identity. \"S1\".. \"S12\" for a singleton or fan-out parent; \"S7.1\".. \"S7.n\" for a fan-out child; \"S7.join\" for its join node. Unique per run."
          },
          "stageNumber": {
            "type": "integer",
            "minimum": 1,
            "maximum": 12,
            "description": "The stage this node belongs to. A STORED value, never derived by parsing nodeKey — T074 asserts the column wins when the two deliberately disagree."
          },
          "nodeRole": { "type": "string", "enum": ["SINGLETON", "FAN_OUT_PARENT", "FAN_OUT_CHILD", "JOIN"] },
          "parentNodeKey": {
            "type": ["string", "null"],
            "description": "Set on FAN_OUT_CHILD and JOIN nodes; null on SINGLETON and FAN_OUT_PARENT."
          },
          "name": { "type": "string" },
          "state": {
            "type": "string",
            "enum": ["BLOCKED", "READY", "RUNNING", "AWAITING_APPROVAL", "RETRY_WAIT", "FALLBACK",
                     "ROLLING_BACK", "COMPENSATING", "SUCCEEDED", "FAILED", "INVALIDATED", "SKIPPED"]
          },
          "executorClass": { "type": "string", "enum": ["DETERMINISTIC", "AI_CAPABLE", "HUMAN_GATE"] },
          "executorKindUsed": {
            "type": ["string", "null"],
            "enum": ["DETERMINISTIC", "AI", "HUMAN", null],
            "description": "Executor kind actually used. HUMAN is recorded for a node the human implemented under the no-plan gate (FR-ORC-031); it is never flag-selectable. Required on every executed node (FR-ORC-029)."
          },
          "attemptsUsed": { "type": "integer", "minimum": 0 },
          "blockingReason": { "type": ["string", "null"] },
          "enteredAt": { "type": ["string", "null"], "format": "date-time" },
          "exitedAt": { "type": ["string", "null"], "format": "date-time" }
        },
        "allOf": [
          {
            "description": "A FAN_OUT_CHILD or JOIN node names its parent; a SINGLETON or FAN_OUT_PARENT does not.",
            "if": { "properties": { "nodeRole": { "enum": ["FAN_OUT_CHILD", "JOIN"] } } },
            "then": { "properties": { "parentNodeKey": { "type": "string", "minLength": 2 } } },
            "else": { "properties": { "parentNodeKey": { "const": null } } }
          }
        ]
      }
    },
```

### Edit 4 — `edges` addressed by node key

**OLD**

```json
          "fromStage": { "type": "integer", "minimum": 1, "maximum": 12 },
          "toStage": { "type": "integer", "minimum": 1, "maximum": 12 },
```

**NEW**

```json
          "fromNodeKey": { "type": "string" },
          "toNodeKey": { "type": "string" },
```

and, in the same block:

**OLD** `"required": ["fromStage", "toStage", "joinSemantics"],`
**NEW** `"required": ["fromNodeKey", "toNodeKey", "joinSemantics"],`

### Edit 5 — `replanHistory.invalidatedStages` addressed by node key

**OLD**

```json
          "invalidatedStages": { "type": "array", "items": { "type": "integer" } },
```

**NEW**

```json
          "invalidatedNodes": {
            "type": "array",
            "items": { "type": "string" },
            "description": "Node keys invalidated by the replan. Node-level, not stage-level: a replan may invalidate one fan-out child and leave its siblings intact (FR-ORC-019, ADR-009)."
          },
```

and in the same block: **OLD** `"required": ["occurredAt", "cause", "invalidatedStages"],`
**NEW** `"required": ["occurredAt", "cause", "invalidatedNodes"],`

*This edit is a consequence the analyze pass did not name and the model forces: with per-node identity, invalidation
is per node. Recorded explicitly rather than absorbed, because it strengthens FR-ORC-019 — "leaves unaffected paths
untouched" becomes assertable at the granularity the fan-out actually has.*

## Applied edits — `contracts/openapi.yaml`

### Edit 6 — `info.version` and description

**OLD** `  version: 1.0.0`
**NEW** `  version: 2.0.0`

and appended to `info.description`:

```
    Control-plane operations (run creation, run inspection, gate decisions) carry NO credential. The shortener's
    actor model and the orchestrator's actor model are separate domains and no credential class crosses the
    boundary (CN-012, owner ruling 2026-09-20). Orchestrator governance surfaces sit under the machine-access
    trust boundary, as creator provisioning does (FR-URL-019).
```

### Edit 7 — new `POST /v1/runs` inserted before `/v1/runs/{runId}`

Closes **H7** for FR-ORC-007.

```yaml
  /v1/runs:
    post:
      tags: [orchestration]
      summary: Create a workflow run from a submitted requirement
      operationId: createRun
      description: >
        Admits a requirement and creates a governed run with a durable identifier that propagates to every log,
        metric, trace, audit row and evidence artifact (FR-ORC-007, NFR-OBS-001). Stage S1.

        Carries NO credential: this is an orchestrator governance surface under the machine-access trust boundary
        (CN-012). A creator API key is neither required nor accepted here.
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CreateRunRequest' }
      responses:
        '201':
          description: Run created and persisted with its identifier
          content:
            application/json:
              schema: { $ref: '#/components/schemas/RunInspection' }
        '400':
          description: Malformed submission. Nothing is persisted and no run identifier is issued.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Error' }
        '503':
          description: Store unavailable. No run is created; the caller may retry.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Error' }
```

### Edit 8 — new `POST /v1/runs/{runId}/gates/{gateId}/decision`

Closes **H7** for FR-ORC-013, and enforces Constitution §Gate semantics **at the contract boundary**.

```yaml
  /v1/runs/{runId}/gates/{gateId}/decision:
    post:
      tags: [orchestration]
      summary: Record a human decision on a pending gate
      operationId: recordGateDecision
      description: >
        Records one of the four submittable outcomes against a pending gate (FR-ORC-013).

        TIMED_OUT is deliberately NOT submittable: it is produced by the gate-wait deadline expiring, never by a
        caller, and silence is never an input. Absence of a call to this operation is what produces suspension.

        repositoryRecordPath is REQUIRED: the decision's repository record must already exist in the working tree
        before the decision takes effect (Constitution v1.1.0 §Gate semantics, FR-ORC-013). The contract refuses a
        decision that exists only in workflow state.

        Carries NO credential (CN-012). Actor authority is asserted from the recorded actor, and an agent actor is
        refused for any approving outcome at every autonomy setting (FR-ORC-021, EC-024).
      security: []
      parameters:
        - name: runId
          in: path
          required: true
          schema: { type: string }
        - name: gateId
          in: path
          required: true
          schema: { type: string }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/GateDecisionRequest' }
      responses:
        '200':
          description: Decision recorded; the run state reflects its effect
          content:
            application/json:
              schema: { $ref: '#/components/schemas/RunInspection' }
        '400':
          description: >
            Malformed decision — unknown outcome, CHANGES_REQUESTED with an empty change list, or a missing
            repositoryRecordPath.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Error' }
        '403':
          description: >
            The actor has no recorded authority for this gate class, or an agent actor attempted an approving
            outcome. Recorded as an autonomy violation (EC-024, FR-ORC-021).
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Error' }
        '404':
          description: No such run, or no such pending gate on it
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Error' }
        '409':
          description: >
            The gate already carries a decision. Gate decisions are append-only and corrected by a superseding
            record, never overwritten (Compensation Register).
          content:
            application/json:
              schema: { $ref: '#/components/schemas/Error' }
```

### Edit 9 — `GET /v1/runs/{runId}`: credential removed

**OLD**

```yaml
      security:
        - creatorApiKey: []
```

**NEW**

```yaml
      security: []
```

and appended to that operation's `description`:

```
        Carries NO credential (CN-012). A creator API key is neither required nor accepted: run inspection is an
        orchestrator governance surface, and creators are a URL-shortener concept that must not leak into the
        orchestrator (owner ruling, 2026-09-20).
```

### Edit 10 — two new request schemas under `components.schemas`

```yaml
    CreateRunRequest:
      type: object
      required: [requirement]
      additionalProperties: false
      properties:
        requirement:
          type: string
          minLength: 1
          maxLength: 20000
          description: >
            The requirement text to govern. Treated as UNTRUSTED CONTENT throughout: it reaches AI stages as prompt
            input, so the CLI adapter MUST pass it as an argv element and never as a shell string (ADR-004-A1).
        ai:
          type: string
          enum: ["on", "off"]
          default: "off"
          description: Whether AI executors participate in this run. Default off — the keyless reviewer path (CN-011).

    GateDecisionRequest:
      type: object
      required: [outcome, actorType, actorName, reason, repositoryRecordPath]
      additionalProperties: false
      properties:
        outcome:
          type: string
          enum: [APPROVED, REJECTED, CHANGES_REQUESTED, ESCALATED]
          description: >
            Four submittable outcomes. TIMED_OUT is absent by design — it is produced by deadline expiry, never
            submitted, because silence must never be expressible as an input (Constitution III).
        actorType:
          type: string
          enum: [human]
          description: >
            Only a human may submit a gate decision. The system actor is permitted solely for TIMED_OUT and
            retention-driven abandonment, neither of which is an approval and neither of which uses this operation.
        actorName: { type: string, minLength: 1 }
        reason: { type: string, minLength: 1, description: Mandatory. A decision without a reason is not a gate record. }
        repositoryRecordPath:
          type: string
          pattern: '^docs/governance/'
          description: >
            Path to the decision's materialized repository record, which MUST already exist. Writing that record is
            not itself work the decision authorizes, so this rule cannot deadlock (FR-ORC-013).
        changes:
          type: array
          items: { type: string, minLength: 1 }
          description: Required and non-empty when outcome is CHANGES_REQUESTED; the changes are enumerated, never implied.
        noPlanOption:
          type: string
          enum: [GOVERNANCE_ONLY, HUMAN_IMPLEMENTED, ABANDON]
          description: >
            Required only on the no-change-plan gate class (FR-ORC-031, CR-001). HUMAN_IMPLEMENTED records
            executorKindUsed HUMAN on the implementation node.
```

### Edit 11 — `RunInspection`: nodes, node keys, and `additionalProperties`

**OLD** `      required: [runId, state, policySetVersion, ai, stages]`
**NEW** `      required: [runId, state, policySetVersion, ai, nodes]`

**OLD** — the `stages:` block at `openapi.yaml:426-447`
**NEW**

```yaml
        nodes:
          type: array
          items:
            type: object
            additionalProperties: false
            required: [nodeKey, stageNumber, nodeRole, name, state, executorClass]
            properties:
              nodeKey:
                type: string
                pattern: '^S([1-9]|1[0-2])(\.([1-9][0-9]*|join))?$'
              stageNumber: { type: integer, minimum: 1, maximum: 12 }
              nodeRole: { type: string, enum: [SINGLETON, FAN_OUT_PARENT, FAN_OUT_CHILD, JOIN] }
              parentNodeKey: { type: [string, 'null'] }
              name: { type: string }
              state:
                type: string
                enum: [BLOCKED, READY, RUNNING, AWAITING_APPROVAL, RETRY_WAIT, FALLBACK,
                       ROLLING_BACK, COMPENSATING, SUCCEEDED, FAILED, INVALIDATED, SKIPPED]
              executorClass: { type: string, enum: [DETERMINISTIC, AI_CAPABLE, HUMAN_GATE] }
              executorKindUsed:
                type: [string, 'null']
                enum: [DETERMINISTIC, AI, HUMAN, null]
                description: >
                  Executor kind actually used. Required on every executed node (FR-ORC-029). HUMAN is recorded for
                  a node the human implemented under the no-plan gate (FR-ORC-031); it is never flag-selectable.
              attemptsUsed: { type: integer, minimum: 0 }
              dependsOn:
                type: array
                items: { type: string }
                description: Node keys this node depends on, with join semantics carried on the edge (FR-ORC-003).
```

### Edit 12 — `pendingGate`: `gateId` and `additionalProperties`

Closes **L7** — this and the stage item were the only permissive objects in the document, against T012's guard
that *"validation must reject `additionalProperties`; a permissive config gives false confidence."*

**OLD**

```yaml
        pendingGate:
          type: [object, 'null']
          properties:
            gateClass: { type: string }
            waitDeadline: { type: string, format: date-time }
            disclosedAutoAbandonAt: { type: string, format: date-time }
```

**NEW**

```yaml
        pendingGate:
          type: [object, 'null']
          additionalProperties: false
          required: [gateId, gateClass, waitDeadline, disclosedAutoAbandonAt, nodeKey]
          properties:
            gateId: { type: string, description: Addresses this gate in the decision operation. }
            nodeKey: { type: string, description: The node the gate is attached to. }
            gateClass: { type: string }
            waitDeadline: { type: string, format: date-time }
            disclosedAutoAbandonAt: { type: string, format: date-time }
```

### Edit 13 — `tags`: orchestration description

**OLD** `    description: Control-plane run inspection`
**NEW** `    description: Control-plane governance surfaces — run creation, inspection, and gate decisions. No credential crosses from the shortener's actor model (CN-012).`

## Applied edits — `contracts/README.md`

### Edit 14 — version table

`openapi.yaml` → `2.0.0`; `workflow-state.schema.json` → `2.0.0`, with its "what validates it" cell extended:
*"…including the terminal/suspended conditionals and the fan-out node model; node-key uniqueness and the join
invariant are asserted by T074's test, which JSON Schema cannot express."*

### Edit 15 — compatibility rules gain their precondition and their arming condition

**Replaces draft 1's version of this edit**, under owner **Decision D**. Her ruling, verbatim:

> at this point we can edit the existing v1 APIs nobody is using them but during demo using orchestrator we have to
> follow v1-v2 if there is a breaking change

Two parts, and the second is the one draft 1 missed entirely.

**NEW**, appended beneath the compatibility table:

```
**When these rules bind.** They protect *consumers of a served contract*, so they bind **from the first served
release onward**. This document has never been served and has no consumer, so a MAJOR change before first service is
**recorded with its MAJOR classification and applied in place**, without a new path prefix. The classification is
never softened to MINOR to avoid the prefix — that would be the goalpost-moving this table exists to prevent.

**When these rules are armed.** From the moment the orchestrator is used to make a change in a **scenario
demonstration**, they are live and enforced: **any breaking change the orchestrator makes during a demonstration
MUST produce a new major version.** The demonstrations are where a reviewer watches change control work, so the rule
has to bite there rather than being described. A breaking change landing in a demonstration run without its major
version bump is a **mandatory policy `FAIL`** (`POL-CHG-003`), which blocks downstream progression and release
readiness.
```

### Edit 17 — `POL-CHG-003` added; policy set becomes `policy-set-1.1.0`

Decision D requires that *"the demos' policy checks can actually enforce it."* They currently cannot:
`POL-CHG-001` checks only that a contract or schema change **has a change-control record** — it says nothing about
version classification, so a breaking change with a record and no major bump passes today.

**NEW policy**, added to `plan.md` §9's table (routed in **CR-015**, recorded here because this record owns the
contract rule it enforces):

```
| `POL-CHG-003` | Change control | **Mandatory** | A contract or schema change classified MAJOR carries a new major version → PASS/FAIL. Evaluated against the served baseline recorded in `contracts/README.md`; NOT-APPLICABLE before first service |
```

**Consequence, flagged rather than absorbed**: adding a check changes the policy set, so it becomes
**`policy-set-1.1.0`**. Every run records the policy version it evaluated (FR-ORC-022, Constitution VI), so this is a
visible, versioned change and not a silent one. The plan's front matter, its §9 heading, T097's loadable definition
and T098's completeness test all move from `1.0.0` to `1.1.0` (**CR-015**, **CR-016**).

**The alternative was to widen `POL-CHG-001`** to cover version classification. Rejected: it would change an existing
mandatory check's meaning while keeping its identifier, so a run recording `POL-CHG-001: PASS` would mean two
different things depending on when it ran. A new identifier and a version bump keep the audit trail honest, which is
the whole reason the policy set is versioned.

### Edit 16 — verification status

Appended:

```
**`nodes` model, v2.0.0 (CR-013)**: the fixed twelve-element `stages` array was replaced because it could not
represent S7's per-task fan-out. Re-parse and re-lint are required after application — the meta-schema lint owed by
T011 now has a second reason to run early.
```

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MAJOR** for both files. `stages` → `nodes`, `fromStage`/`toStage` → node keys, `invalidatedStages` → `invalidatedNodes` are renames and narrowings; Edits 7, 8, 10 are additive (MINOR in isolation) and are bundled here because they share Decision 3's authority model. Classified honestly rather than split to keep a MINOR label. |
| **Backward-compatibility impact** | **None in fact.** Nothing implemented, nothing served, no consumer, no persisted row. Edit 15 records *why* that matters instead of leaving the `/v2` rule silently unapplied. |
| **Affected consumers** | `data-model.md` (**CR-011** — same act). `plan.md` §2 deliverables table, §3 topology, §4 lineage (**CR-015**). `tasks.md` T011, T012, T029, T030, T058, T062, T063a, T067, T067a, T074, T076, T082a, T096 (**CR-016**). |
| **Affected tests** | **Changed:** T011 re-parses and lints both files; T012's conformance harness covers three new operations; T074 asserts the node invariants; T076 asserts fan-out with concrete node keys; T096 asserts node-level invalidation. **New:** T063a (no creator credential on any orchestrator surface), T067a and T082a (the new operations). |
| **Affected documentation** | `contracts/README.md`, `quickstart.md` §5 (**CR-012**), `docs/REVIEWER-GUIDE.md` (T128). |
| **Rollout / migration** | None — pre-implementation. `V4__orchestration_graph.sql` (T074) is authored against the new model from the outset, so no migration is created and then changed. |
| **Required approval** | Human owner. Decisions 2, 3 and D are given; approval here confirms the wire shape, the two new operations' request contracts, and the policy-set version bump. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

| # | Edit | Verification command (from repo root, `C=specs/001-agentic-sdlc-url-shortener/contracts`) | Expected | Result |
|---|---|---|---|---|
| V1 | 3 | `grep -c 'maxItems' $C/workflow-state.schema.json` | `0` | **EXECUTED: 0** — HOLDS |
| V2 | 3 | `grep -c '"stages"' $C/workflow-state.schema.json` | `0` | **EXECUTED: 0** — HOLDS |
| V3 | 3 | `grep -c 'nodeKey' $C/workflow-state.schema.json` | `≥ 4` | **EXECUTED: 4** — HOLDS |
| V4 | 4 | `grep -c 'fromStage' $C/workflow-state.schema.json` | `0` | **EXECUTED: 0** — HOLDS |
| V5 | 5 | `grep -c 'invalidatedStages' $C/workflow-state.schema.json` | `0` | **EXECUTED: 0** — HOLDS |
| V6 | 1, 6, 14 | `grep -c '2.0.0' $C/workflow-state.schema.json $C/openapi.yaml $C/README.md` | `≥ 1` each | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V7 | 7 | `grep -c 'operationId: createRun' $C/openapi.yaml` | `1` | **EXECUTED: 1** — HOLDS |
| V8 | 8 | `grep -c 'operationId: recordGateDecision' $C/openapi.yaml` | `1` | **EXECUTED: 1** — HOLDS |
| V9 | 9 | `grep -c 'creatorApiKey' $C/openapi.yaml` | `3` — the scheme definition plus **createLink and getLinkAnalytics only**; **no orchestration operation** | **EXECUTED: 3** — HOLDS |
| V10 | 9 | `python3 -c "import sys;t=open('$C/openapi.yaml').read();import re;print([m for m in re.findall(r'operationId: (\w+)[\s\S]{0,400}?(security: \[\]\|creatorApiKey)', t)])"` | every orchestration operation pairs with `security: []` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V11 | 10 | `grep -c 'repositoryRecordPath' $C/openapi.yaml` | `≥ 1` | **EXECUTED: 4** — HOLDS |
| V12 | 10 | `grep -c 'TIMED_OUT' $C/openapi.yaml` | `0` in the `GateDecisionRequest` enum — assert by reading, not counting | EXECUTED: `ruby -ryaml` → outcome enum `["APPROVED", "REJECTED", "CHANGES_REQUESTED", "ESCALATED"]`; TIMED_OUT present: **false**. **HOLDS** |
| V13 | 11, 12 | `grep -c 'additionalProperties: false' $C/openapi.yaml` | `≥ 8` — one per object schema including `nodes` items and `pendingGate` | **EXECUTED: 11** — HOLDS |
| V14 | 11 | `grep -c 'stageNumber' $C/openapi.yaml` | `≥ 1`, and `grep -c 'stages:' $C/openapi.yaml` → `0` | **EXECUTED: 0** — HOLDS |
| V15 | all | `python3 -c "import json;json.load(open('$C/workflow-state.schema.json'));print('parse ok')"` | `parse ok` | **EXECUTED: 1** — HOLDS |
| V16 | all | `ruby -ryaml -e "YAML.load_file('$C/openapi.yaml'); puts 'parse ok'"` | `parse ok` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V17 | all | `ruby -ryaml -e "puts YAML.load_file('$C/openapi.yaml')['paths'].keys.sort.inspect"` | **8 paths** — the six that existed plus `/v1/runs` and `/v1/runs/{runId}/gates/{gateId}/decision` | **EXECUTED: 8** — HOLDS |
| V18 | cross | `grep -c 'node_key' specs/001-agentic-sdlc-url-shortener/data-model.md` | `≥ 5` — proves **CR-011** landed in the same act | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V19 | 15 | `grep -c 'When these rules are armed' $C/README.md` | `1` | **EXECUTED: 1** — HOLDS |
| V20 | 15 | `grep -c 'MUST produce a new major version' $C/README.md` | `1` | **EXECUTED: 1** — HOLDS |
| V21 | 17 | `grep -c 'POL-CHG-003' specs/001-agentic-sdlc-url-shortener/plan.md` | `≥ 1` — proves **CR-015** landed the policy | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V22 | 17 | `grep -c 'policy-set-1.0.0' specs/001-agentic-sdlc-url-shortener/plan.md` | `0` — the bump is complete, not partial | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |


**Check correction recorded, 2026-09-20.** V17 was first written expecting **7 paths**. On execution it returned
**8**. The expectation was simply **my arithmetic error**: the document held six paths and this record adds two, so
eight is correct. Unlike the three earlier corrections in this package — which were absence-greps defeated by
deliberately retained provenance text — this one had no subtlety behind it. Recorded rather than quietly fixed,
because a verification table whose expected values are adjusted after seeing the output is worth nothing, and the
only thing that separates a correction from that is saying which it was.

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V15–V17 must re-execute the parsers**, because the last time these files changed shape a real
YAML-1.1 defect was found only by running one. **V12 requires reading**, since a `grep -c` of `0` would also pass
if the whole schema were missing.

## Open questions resolved and remaining

**Draft 1's open question is closed** by Decision D: the never-served draft revises in place, and the rule arms at
first service and during the demonstrations.

**What remains** is the policy-set version bump in Edit 17. It is a consequence of Decision D rather than a separate
choice, but it changes a number every run records, so it is flagged rather than assumed. If the owner would rather
widen `POL-CHG-001` and keep `policy-set-1.0.0`, that is a legitimate call — it trades audit-trail clarity for one
fewer version, and the rejection reasoning above is what she would be overriding.

## Residual risk

The two new operations are **unauthenticated by design** (CN-012), and the honest statement of that is: *anything
that can reach the port can start a run and record a gate decision.* On a localhost demonstration instance that is
exactly the operator-shell boundary the owner already accepted for creator provisioning, and `actorType: human`
plus `repositoryRecordPath` mean a decision still cannot be recorded without a materialized repository artifact
naming who decided. It is **not** a production posture, and `docs/LIMITATIONS.md` (T147) must say so in those
terms rather than implying the boundary is enforced by the application. The rejected alternative — a reviewer
credential class — remains the production answer and is recorded as future work, not as a gap discovered later.
