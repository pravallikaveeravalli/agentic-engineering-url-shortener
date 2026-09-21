# CR-041 — `escalationTarget` added to `GateDecisionRequest`

| Field | Value |
|---|---|
| **Change request** | CR-041 |
| **Title** | Add optional `escalationTarget` to `GateDecisionRequest` in `openapi.yaml`, required when `outcome=ESCALATED` |
| **Raised by** | Acting agent (material conflict reported while implementing T067a) |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED**, 2026-09-21 |
| **Decision date** | 2026-09-21 |
| **Classification** | **MINOR** — adds one optional field, conditionally required, to a frozen (Gate 7) request schema. Purely additive: `additionalProperties: false` meant the field was previously REJECTED if a caller sent it; nothing existing narrows, moves, or is removed |
| **Artifacts changed** | `contracts/openapi.yaml` (`GateDecisionRequest`) |
| **Non-approved files changed** | `GateDecisionController.java` (new), `GateDecisionControllerIT.java` (new), `GateStore.java` (`decisionRecorded`), `OrchestrationConfiguration.java` (three new beans) |
| **Applied** | 2026-09-21 |

---

## Reason / the conflict this resolves

`approval.schema.json` (T058, the PERSISTED `GateDecision` record) has carried `escalationTarget` since
the beginning, conditionally required by its own `allOf`/`if`/`then` when `outcome=ESCALATED`, and
`GateDecision.java`'s constructor has enforced the same rule structurally from T058 onward: *"ESCALATED
requires an escalation target: what decision exceeds the current owner's authority. Recording only that
something exceeded it leaves the escalation unactionable."*

`openapi.yaml`'s `GateDecisionRequest` — the WIRE contract for `POST
/v1/runs/{runId}/gates/{gateId}/decision`, frozen at Gate 7 under CR-013 — names `outcome` enum
`[APPROVED, REJECTED, CHANGES_REQUESTED, ESCALATED]`, so ESCALATED is a documented, submittable outcome
on this surface. But the request schema carried no `escalationTarget` field at all, and
`additionalProperties: false` meant a caller who tried to send one would have that request rejected
outright as malformed. CR-013's own record makes no mention of `escalationTarget`, confirming this was
not a decision anyone made on purpose — an oversight nobody caught when Gate 7 froze the contract, found
while implementing T067a (the endpoint this gap blocks) rather than papered over.

Without this field, there was no way for an HTTP caller to submit an ESCALATED decision that could ever
pass `GateDecision`'s own constructor — a real conflict between two approved artifacts (the wire contract
and the domain model it must build), reported rather than worked around, and now resolved by the owner's
approval.

**The owner's ruling:** "add it."

## Provenance — why `escalationTarget`, and why now, is not new scope

Two questions the owner asked to have answered on the record before approving:

- **Is `ESCALATED` itself required, or was it invented?** Required, on two independent grounds. The
  interviewers' guide states plainly: *"Approval, rejection, escalation, timeout, and safe-stop behavior
  must be defined."* Separately, the constitution's own Gate 1 table names ESCALATED as one of the five
  standard gate outcomes. `ESCALATED` was already built (T058, T059) and already documented in this same
  request schema's `outcome` enum before this CR — this change adds the field ESCALATED needs to be
  submittable, it does not add ESCALATED itself.
- **What kind of field is `escalationTarget`?** A recorded string, declared by the submitting actor —
  the same trust model `actorName` already uses on this exact request schema. There is no user or
  authority registry in this system to verify it against, and none is added by this change.

## What this field does and does not do

- **Does**: is recorded in the persisted `gate_decision` row (already had a column for it — T058/V8 — this
  CR only closes the wire-level gap to reach that column), and is surfaced back to a reviewer through run
  inspection (`GET /v1/runs/{runId}`) and the decision-lineage audit trail.
- **Does NOT**: notify or deliver anything to whoever or whatever is named. There is no notifier in this
  system. A production deployment wiring a real escalation path would add one on top of this field; this
  system records and surfaces the target, honestly, and stops there. Documented in the field's own
  `openapi.yaml` description so this limitation is visible to a reader of the contract, not only to a
  reader of this CR.

## Scope

Explicitly unchanged:

- `approval.schema.json`'s own `escalationTarget` field and `if`/`then` — already correct since T058; this
  CR brings the wire contract into alignment with it, not the reverse.
- `GateDecision.java`'s constructor guard — already correct since T058; unmodified.
- Every other field of `GateDecisionRequest`, and every other outcome's own handling.
- `additionalProperties: false` on `GateDecisionRequest` — kept. A caller sending an unrecognized field is
  still rejected; `escalationTarget` is now a recognized one.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | `openapi.yaml` `GateDecisionRequest.escalationTarget` added, `type: string, minLength: 1` | present, optional (not in the base `required` list) | **EXECUTED — HOLDS** |
| 2 | `allOf`/`if`/`then` added: `outcome == ESCALATED` requires `escalationTarget` | mirrors `approval.schema.json`'s own conditional exactly | **EXECUTED — HOLDS** |
| 3 | YAML parses as valid JSON Schema (OpenAPI 3.1.0, JSON-Schema-2020-12-compatible) | parses; `escalationTarget` present under `properties`, `allOf` present as a sibling of `properties` | **EXECUTED — HOLDS** (verified via `ruby -ryaml`) |
| 4 | `GateDecisionController` (T067a) wires the field through to `GateDecision`'s constructor | an ESCALATED request without `escalationTarget` is refused 400 by `GateDecision`'s own constructor guard (surfaced through the controller); with it, 200 | **EXECUTED — HOLDS** (`GateDecisionControllerIT`, 11/11) |
| 5 | Red-phase: the controller's own `escalationTarget` pass-through disabled, real HTTP calls re-run | both tests exercising ESCALATED fail, naming the exact constructor refusal message | **EXECUTED — HOLDS** (docs/evidence/red-phase/20260921T183000Z-T067a-escalation-target-wiring.txt) |
| 6 | Fast tier + integration tier + `scripts/ci.sh` | green | **EXECUTED — HOLDS** |

## Conditions attached to the approval

1. **Mirror `approval.schema.json`'s existing `if`/`then` exactly** — not a new shape invented for this
   surface. Honoured; verification step 2.
2. **Document the no-notification limitation honestly** in the contract itself, not only in this record.
   Honoured — see the field's own `description` in `openapi.yaml`.
3. **Route via change control** (frozen contract, owner authority) — this record.

## Cross-record orphan check

No other applied record quotes `GateDecisionRequest`'s field list or its `required` array verbatim.
CR-013 (which froze the original contract) is not corrected — it recorded the schema as it was reviewed
and approved at the time; this CR is the first record to touch `GateDecisionRequest` since, and does not
claim CR-013 was wrong to approve what it reviewed, only that a field was missing. `approval.schema.json`
is untouched by this CR and needed no correction — it already had the shape this CR brings the wire
contract into alignment with.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| `escalationTarget`'s no-notification limitation stays true | Any future task that adds a notifier — that task must either wire it through this field or replace this CR's framing, not add a silent side channel |
| The wire contract and `approval.schema.json` are checked against each other before either is assumed complete | Any future field added to one that the domain model (or vice versa) structurally requires |
