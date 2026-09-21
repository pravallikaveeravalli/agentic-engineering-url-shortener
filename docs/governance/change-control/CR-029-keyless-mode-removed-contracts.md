# Change Request CR-029 — Keyless Mode Removed: Contracts

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 9 rows HOLD** (12 executed checks; V5 and V8 are per-file). Both documents re-parsed. |
| Raised by | Owner **Decision J**; implements **ADR-004 Amendment 02** |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-20** |
| Governing constitution | v1.1.0 — **not amended**. `POL-CHG-001` |
| Affected approved artifacts | `contracts/workflow-state.schema.json`, `contracts/openapi.yaml`, `contracts/README.md` |
| Application order | The contracts’ sole editor in this package. **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |

## Owner approval

Owner **Decision J**, 2026-09-20 — *"do it."* Carried into the contracts.

Both documents go to **v3.0.0**: a field is removed from a `required` array, which is the largest change either
document can carry. Classified MAJOR rather than softened, and applied in place under the pre-first-service rule
whose precondition is exactly this situation — no implementation, no consumer, no persisted snapshot.

## Why this change exists

Decision J removes the run-level switch. It appears in **six places across the three contract files**: the state
schema's `required` array and its `ai` property, the API document's `CreateRunRequest.ai` and `RunInspection.ai`, and
two version rows. A flag in a `required` array is not cosmetic — a persisted snapshot without it fails validation, so
this must be removed rather than deprecated.

## Applied edits

### Edit 1 — state schema `required` array

**OLD** `"required": ["runId", "state", "policySetVersion", "ai", "lastActivityAt", "nodes", "edges"],`
**NEW** `"required": ["runId", "state", "policySetVersion", "lastActivityAt", "nodes", "edges"],`

### Edit 2 — state schema `ai` property removed entirely

The whole `"ai": { … }` block is deleted. **Nothing replaces it**: there is no field recording which mode a run used,
because there are no modes. What a run used is recorded **per node** in `executorKindUsed`, which is unchanged and is
now the only such record.

### Edit 3 — state schema version and description

`2.0.0` → **`3.0.0`**. A field is removed from a `required` array, which is the MAJOR-est change this document can
carry, and the compatibility rule in `contracts/README.md` says so. Description gains: `v3.0.0 (CR-029) removes the
run-level ai flag: orchestration always uses AI (ADR-004-A2), so there is no mode to record. What ran is recorded per
node in executorKindUsed.`

### Edit 4 — `CreateRunRequest.ai` removed

The `ai` property and its description are deleted. `requirement` remains the only property, still `required`, still
carrying the untrusted-content warning and the argv-not-shell reference.

### Edit 5 — `RunInspection.ai` removed

Deleted from `properties` **and from the `required` array**. A client reading a run inspection no longer receives a
mode, because none exists.

### Edit 6 — `info.version` and the orchestration tag

`2.0.0` → **`3.0.0`**. The tag description's mode reference is removed.

### Edit 7 — `contracts/README.md`

Version rows updated to 3.0.0; the verification-status section gains: `**v3.0.0 (CR-029)**: the run-level ai flag
removed from both documents. Re-parse required after application — the last two shape changes each found a real defect
only by running a parser.`

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MAJOR** on both documents. A required field is removed. Classified honestly; the pre-first-service rule in `contracts/README.md` means it applies in place without a path prefix, and that rule's precondition is exactly this situation. |
| **Backward-compatibility impact** | None in fact — nothing implemented, no consumer, no persisted snapshot. |
| **Affected consumers** | `data-model.md` (**CR-030**), T082a's run-creation surface and T074's snapshot validation (**CR-031**). |
| **Affected tests** | T011 re-parses and re-lints; T012's conformance drops a field; T074's snapshot assertion drops it; T066's flag-selection test loses its flag. |
| **Required approval** | Human owner. |

## Post-application verification

| # | Command (`C=specs/001-agentic-sdlc-url-shortener/contracts`) | Expected | Result |
|---|---|---|---|
| V1 | `grep -c '"ai"' $C/workflow-state.schema.json` | `0` | **EXECUTED: 0** — HOLD |
| V2 | `grep -cE '^\s+ai:' $C/openapi.yaml` | `0` | **EXECUTED: 0** — HOLD |
| V3 | `grep -c 'keyless' $C/openapi.yaml` | `0` | **EXECUTED: 0** — HOLD |
| V4 | `grep -c 'CN-011' $C/openapi.yaml` | `0` — the retired constraint is no longer cited as a live ground | **EXECUTED: 0** — HOLD |
| V5 | `grep -c '3.0.0' $C/workflow-state.schema.json $C/openapi.yaml $C/README.md` | `≥ 1` each | **EXECUTED: schema 2, openapi 1, README 3** — HOLD |
| V6 | `python3 -c "import json;json.load(open('$C/workflow-state.schema.json'))"` | parses | **EXECUTED: parses** — HOLD |
| V7 | `ruby -ryaml -e "YAML.load_file('$C/openapi.yaml')"` | parses | **EXECUTED: parses** — HOLD |
| V8 | `grep -c 'executorKindUsed' $C/workflow-state.schema.json $C/openapi.yaml` | `≥ 1` each — **the surviving record of what ran** | **EXECUTED: schema 2, openapi 2** — HOLD |
| V9 | openapi path count | `8` — unchanged | **EXECUTED: 8** — HOLD |

## Residual risk

**Removing the field rather than deprecating it is correct here and would be wrong later.** There is no consumer and no
stored snapshot, so a clean removal costs nothing. The same edit after first service would need a deprecation cycle,
which is what the armed-during-demonstrations rule (CR-013) exists to enforce.
