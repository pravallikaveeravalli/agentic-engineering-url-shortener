# Contracts

**Status**: **Derived design artifacts.** Approved indirectly via `plan.md`'s approval at the Gate 4 closing
package (2026-09-20) and through ADR-005 — they were **not** independently gated. Every change to a file in this
directory requires a change-control record (`POL-CHG-001`), regardless of release state.

Versioned interface deliverables for feature 001. Each is a **source of truth**, not documentation
generated from code — drift must be detectable, which is why validation runs against these files
rather than deriving them (ADR-005).

| File | Version | What validates it |
|---|---|---|
| `openapi.yaml` | **2.0.0** (CR-013) | Contract tests assert live responses conform to this document. A deliberate drift must fail the build. |
| `workflow-state.schema.json` | **2.0.0** (CR-013) | Persisted run snapshots validated against it, including the terminal/suspended conditionals and the fan-out node model; node-key uniqueness and the join invariant are asserted by T074's test, which JSON Schema cannot express |
| `approval.schema.json` | 1.0.0 | Gate decision records validated; `repositoryRecordPath` pattern enforces materialization |
| `audit-event.schema.json` | 1.0.0 | Every audit record validated for all six mandatory fields (`POL-AUD-001`) |
| `policy-evaluation.schema.json` | 1.0.0 | S10 output validated for exactly four outcome values and complete exception fields |

Persistence schema lives separately as Flyway migrations (`db/migration/V*.sql`), forward-only.

## Compatibility rules

| Change | Classification | Requirement |
|---|---|---|
| New optional field, new endpoint, new enum value in a response | MINOR | Change-control record |
| Removing/narrowing a field, renaming, changing a status code, new required request field | MAJOR | New path version (`/v2`) + change-control record + migration plan |
| Wording, description, example fixes | PATCH | Change-control record only if a consumer-visible meaning shifts |

**When these rules bind.** They protect *consumers of a served contract*, so they bind **from the first served
release onward**. This document has never been served and has no consumer, so a MAJOR change before first service is
**recorded with its MAJOR classification and applied in place**, without a new path prefix. The classification is
never softened to MINOR to avoid the prefix — that would be the goalpost-moving this table exists to prevent.

**When these rules are armed.** From the moment the orchestrator is used to make a change in a **scenario
demonstration**, they are live and enforced: **any breaking change the orchestrator makes during a demonstration
MUST produce a new major version.** The demonstrations are where a reviewer watches change control work, so the rule
has to bite there rather than being described. A breaking change landing in a demonstration run without its major
version bump is a mandatory policy `FAIL` (`POL-CHG-003`), which blocks downstream progression and release readiness.
*(Owner ruling, 2026-09-20: "at this point we can edit the existing v1 APIs nobody is using them but during demo
using orchestrator we have to follow v1-v2 if there is a breaking change.")*

Database migrations are forward-only. A column is deprecated across two versions before removal.

## Required change-impact record

Every change to any file here records, under `docs/governance/change-control/`: owner, version impact,
backward-compatibility impact, affected consumers, tests to update, documentation to update,
rollout/migration steps, and the required change approval (`POL-CHG-001`).

## Verification status

**Parse-validated 2026-09-20** — all four JSON Schemas via `python3`, `openapi.yaml` via `ruby -ryaml`. One real
defect was found and fixed: `RunInspection.ai` was written `enum: [on, off]`, which YAML 1.1 coerces to
`[true, false]`; it is now quoted. See ADR-005 §Validation for the executed output.

**Not yet meta-schema linted** against OpenAPI 3.1 or JSON Schema 2020-12 — validators are not present in this
environment, so structural conformance remains unverified. Slice 1 task (T011).

**`nodes` model, v2.0.0 (CR-013)**: the fixed twelve-element `stages` array was replaced because it could not
represent S7's per-task fan-out. **Re-parsed 2026-09-20 after the change** — `workflow-state.schema.json` via
`python3 json`, `openapi.yaml` via `ruby -ryaml`, both clean; the document now declares **eight paths**, adding
`POST /v1/runs` and the gate-decision operation. Meta-schema lint remains owed and now has a second reason to run
early.

## Resolved items formerly affecting these contracts

- **DF-002** — **resolved** at the Gate 4 closing package (CR-003): redirects are temporary, never permanent.
  The `307` is now the approved class rather than a provisional position.
- **DF-001** — **resolved** by ADR-014: the append is synchronous in a **separate** transaction with failure
  isolated. As anticipated, this changed no wire contract, only what the analytics tests may assert.
- **`executorModeUsed`** — renamed to **`executorKindUsed`** (CR-005), and the kind enum extended with `HUMAN`
  per CR-001.
