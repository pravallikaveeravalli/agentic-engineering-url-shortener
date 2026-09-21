# CR-043 — `policy-evaluation.schema.json` synced to `policy-set-1.1.0`

| Field | Value |
|---|---|
| **Change request** | CR-043 |
| **Title** | Update `contracts/policy-evaluation.schema.json`'s `policySetVersion` const and `policyId` enum from `policy-set-1.0.0` (eleven ids) to `policy-set-1.1.0` (twelve ids) |
| **Raised by** | Acting agent (found while implementing T098/T099) |
| **Decided by** | Mechanical completion of CR-013's already-approved decision — see Provenance below |
| **Decision** | **APPLIED**, 2026-09-21 |
| **Classification** | **MINOR** — a `const`/`enum` value swap on an unreleased contract (no run has ever evaluated `1.0.0`, per CR-013's own finding), not a shape change |
| **Artifacts changed** | `contracts/policy-evaluation.schema.json`, `contracts/README.md` (version column) |
| **Non-approved files changed** | none |
| **Applied** | 2026-09-21 |

---

## Reason — a gap in CR-013's own scope list, not a new decision

CR-013 (2026-09-20, APPROVED, "go ahead") adopted `policy-set-1.1.0` on the ground stated in its own
record: "version discipline binds from first real use, and no run has ever evaluated `1.0.0`." That CR's
"Affected approved artifacts" list named `workflow-state.schema.json`, `openapi.yaml`, and
`contracts/README.md` — but not `policy-evaluation.schema.json` itself, even though that schema's own
`policySetVersion` field is a hard `const: "policy-set-1.0.0"` and its `policyId` enum still lists eleven
ids (`POL-AUD-002` present, `POL-CHG-003` absent) — the exact two symbols CR-013's decision retired and
added. `ADR-005`'s own verified parse-check table confirms the file was still at `title=PolicyEvaluation,
version=1.0.0` as of Gate 4, after CR-013's approval.

This is not a new policy decision — CR-013 already decided the version bump and the id delta; this record
completes it, applying CR-013's already-approved outcome to the one artifact its own scope list missed.
Found while implementing T099 (the schema-conformance test for T098's twelve definitions cannot pass
against a schema that only lists eleven and pins the wrong version), reported here rather than silently
worked around, matching this project's own POL-CHG-001 — the exact check this CR's subject-matter policy
is going into enforcement code for.

## What changed

| # | Before | After |
|---|---|---|
| 1 | top-level `"version": "1.0.0"` | `"version": "1.1.0"` |
| 2 | `policySetVersion.const = "policy-set-1.0.0"` | `policySetVersion.const = "policy-set-1.1.0"` |
| 3 | `policyId` enum: eleven ids, includes `POL-AUD-002`, excludes `POL-CHG-003` | `policyId` enum: twelve ids, excludes `POL-AUD-002`, includes `POL-CHG-003` |
| 4 | `contracts/README.md` row: `policy-evaluation.schema.json \| 1.0.0` | `\| 1.1.0` |

Nothing else in the schema changed: the four-outcome enum, the `domain` enum, the exception object's nine
required fields, the `releaseBlockingConditions` shape, and every `additionalProperties: false` boundary
are all unmodified.

## Scope

Explicitly unchanged:

- The `domain` enum's snake_case values — `PolicyDefinitions` (T098) is written to match them exactly,
  rather than this schema being changed to match a different Java-side convention.
- `openapi.yaml`, `workflow-state.schema.json` — already corrected by CR-013 itself.
- The `results[].exception` object's shape — already correct, unaffected by the version/id delta.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | `policySetVersion.const` and top-level `version` bumped to `1.1.0` | schema still parses as valid JSON Schema | **EXECUTED — HOLDS** |
| 2 | `policyId` enum: `POL-AUD-002` removed, `POL-CHG-003` added | twelve entries, matching `PolicyDefinitions.DEFAULT` exactly | **EXECUTED — HOLDS** (`PolicyEvaluationSchemaTest`, T099) |
| 3 | `contracts/README.md` version column updated | `1.1.0` | **EXECUTED — HOLDS** |
| 4 | Fast tier + `scripts/ci.sh` | green | **EXECUTED — HOLDS** |

## Cross-record orphan check

CR-013 is not corrected — it recorded what it reviewed and approved at the time, and its own text already
states the 1.1.0 decision this record merely extends to one more artifact; nothing in CR-013's applied
text becomes false by this record. No other applied record quotes `policy-evaluation.schema.json`'s
`policyId` enum or `policySetVersion` const verbatim. `ADR-005`'s verified-parse-check table, which quotes
this file's version as `1.0.0`, is now a historical snapshot (true as of Gate 4, when it was captured) —
not corrected in place, since ADR records are immutable; this CR is the record that supersedes that one
fact going forward.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Any future policy-set version bump updates this schema in the SAME change as the version decision, not afterward | The next policy-set version change (if any) |
