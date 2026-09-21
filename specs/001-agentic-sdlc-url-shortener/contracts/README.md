# Contracts

**Status**: **Derived design artifacts.** Approved indirectly via `plan.md`'s approval at the Gate 4 closing
package (2026-09-20) and through ADR-005 — they were **not** independently gated. Every change to a file in this
directory requires a change-control record (`POL-CHG-001`), regardless of release state.

Versioned interface deliverables for feature 001. Each is a **source of truth**, not documentation
generated from code — drift must be detectable, which is why validation runs against these files
rather than deriving them (ADR-005).

| File | Version | What validates it |
|---|---|---|
| `openapi.yaml` | **3.0.0** (CR-029) | Contract tests assert live responses conform to this document. A deliberate drift must fail the build. |
| `workflow-state.schema.json` | **3.0.0** (CR-029) | Persisted run snapshots validated against it, including the terminal/suspended conditionals and the fan-out node model; node-key uniqueness and the join invariant are asserted by T074's test, which JSON Schema cannot express |
| `approval.schema.json` | 1.0.0 | Gate decision records validated; `repositoryRecordPath` pattern enforces materialization |
| `audit-event.schema.json` | 1.0.0 | Every audit record validated for all six mandatory fields (`POL-AUD-001`) |
| `policy-evaluation.schema.json` | 1.1.0 | S10 output validated for exactly four outcome values and complete exception fields (CR-043: twelve `policyId`s, `POL-CHG-003` added / `POL-AUD-002` removed, matching `policy-set-1.1.0`) |

Persistence schema lives separately as Flyway migrations (`db/migration/V*.sql`), forward-only.

## Compatibility rules

| Change | Classification | Requirement |
|---|---|---|
| New optional field, new endpoint, new enum value in a response | MINOR | Change-control record |
| Removing/narrowing a field, renaming, changing a status code, new required request field | MAJOR | New path version (`/v2`) + change-control record + migration plan |
| Wording, description, example fixes | PATCH | Change-control record only if a consumer-visible meaning shifts |

### Migrations: the same classification, a different mechanism (T029)

Database migrations are versioned deliverables too, and the table above classifies them — but a
migration cannot be "applied in place", so the mechanism differs and is stated rather than inferred.

| Migration change | Classification | Requirement |
|---|---|---|
| New table, new nullable column, new index, new CHECK on new data | **MINOR** | Change-control record; a new `V<n>__*.sql` |
| New **NOT NULL** column on a populated table, new CHECK that existing rows could violate | **MAJOR** | Change-control record; **two** migrations — one adding it nullable and backfilling, one enforcing it |
| Dropping or renaming a column, narrowing a type, removing a constraint | **MAJOR** | Change-control record; **deprecate across two versions before removal** (below) |
| Comment, index name, non-semantic reordering | **PATCH** | No record unless a consumer-visible meaning shifts |

**The deprecation rule, stated concretely because "deprecate across two versions" is otherwise
advice rather than a procedure.** To remove column `X`:

1. **Version n** — stop writing `X`, stop reading `X`. The column stays, keeps its data, and gains a
   `COMMENT` naming the version that will drop it and the record that authorised the removal.
2. **Version n+1** — drop `X`, once no code has referenced it for a full version.

A single migration doing both is a MAJOR change pretending to be one step: a rollback to version n
would then find the data already gone.

**There is a live example of this rule in the repository**, which is why it is worth spelling out.
`V1__baseline.sql` put `api_key_hash` and `key_expires_at` on `creator` directly. That shape cannot
express credential rotation — a creator holds several credentials over time, one revoked and one
current — so `V2__domain.sql` introduced `creator_credential`. **The V1 columns were not dropped.**
They are written once with a non-secret placeholder digest, never read, and await a formal
deprecation comment plus a later removal under this rule. Dropping them in V2 would have been exactly
the single-migration shortcut the rule forbids.

**Migrations are forward-only** (ADR-002). There are no `down` scripts: a down script is a second,
untested code path that runs only when something has already gone wrong.

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

**v3.0.0 (CR-029)**: the run-level `ai` flag removed from both documents — orchestration always uses AI
(ADR-004-A2), so there is no mode to record, and what ran is recorded per node in `executorKindUsed`. The field
was in the state schema’s `required` array, so this is a removal rather than a deprecation; the pre-first-service
rule below permits it in place, and its precondition — nothing implemented, no consumer, no persisted snapshot —
is exactly this situation. **Re-parse required after application**: the last two shape changes each found a real
defect only by running a parser.

**Meta-schema lint status, corrected 2026-09-21 (T011).** ADR-005 recorded a meta-schema lint as an
owed residual. It is now discharged for **all five files**: each parses, each compiles as a schema,
and each is shown to *discriminate* — an empty object must be rejected. The first version of that test
covered only `openapi.yaml` and `workflow-state.schema.json`, so `approval`, `audit-event` and
`policy-evaluation` shipped unlinted while T011 was marked complete. The gap was found when T029 read
this file's own five-file table, and it is recorded here rather than quietly closed.

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
