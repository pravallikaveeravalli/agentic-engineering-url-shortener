# CR-064 — `GET /v1/version` legitimately grows the OpenAPI document to nine paths; `ContractFilesLintTest`'s
own change-controlled count bumped 8 → 9

| Field | Value |
|---|---|
| **Change request** | CR-064 |
| **Title** | The real `GET /v1/version` feature (attempt 21, CR-062) adds a ninth path to `contracts/openapi.yaml`; `ContractFilesLintTest.openApiDocumentParsesAndDeclaresItsVersions`'s own hardcoded count and its comment updated to match, following CR-013's own established precedent for this exact assertion |
| **Raised by** | Owner's own direction, this session (2026-09-22): "Adding `/v1/version` legitimately changes the API surface, so `ContractFilesLintTest`'s path count must go 8 → 9, and that is a change-controlled edit" |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — a change-controlled bookkeeping fact kept in sync with a real, already-approved API surface change; no behavioural change, no schema change beyond the new path itself (already covered by CR-062's own approved contract) |
| **Artifacts changed** | `src/test/java/agentic/shortener/contract/ContractFilesLintTest.java` (count `8`→`9`, comment updated) |
| **Non-approved files changed** | none |

---

## Why a hardcoded bump, not a dynamic count

`ContractFilesLintTest`'s own existing comment states its real purpose precisely: *"a different count means
the document and the record disagree."* This is a deliberate governance cross-check — CR-013 already
established the pattern of bumping this exact assertion (and its comment) alongside whatever change legitimately
grows the API surface, forcing a conscious, recorded decision at each addition rather than letting the count
drift silently. Making the assertion dynamic (count whatever paths currently exist, no fixed expectation)
would quietly remove that control — a real regression in governance value, not a simplification. This CR
preserves the check's own purpose by following CR-013's own precedent: bump the number, update the comment to
name what changed and why, record it here.

## The change

```
- // Eight paths after CR-013 added createRun and recordGateDecision.
- assertEquals(8, document.path("paths").size(),
-         "CR-013 took the document to eight paths; a different count means the document and "
-                 + "the record disagree");
+ // Nine paths after CR-013 added createRun and recordGateDecision, and CR-064 added GET
+ // /v1/version.
+ assertEquals(9, document.path("paths").size(),
+         "CR-064 took the document to nine paths; a different count means the document and "
+                 + "the record disagree");
```

## How it lands: as a real, governed commit onto the feature's own branch, not a separate hand-authored patch

Per the owner's own explicit instruction, this edit is applied as part of the SAME real, AI-authored change
set the live pipeline produces — not hand-authored by this agent as a separate, out-of-band patch. `DsALiveRun`
now wraps the real `ImplementationAiExecutor` for S7: after the model's own real diff for the feature succeeds
and is committed, a second, purely mechanical follow-up apply (same `GitWorktreeBranchApplier` machinery
CR-060 already built and proved — a real search/replace, a real commit, a real compile check) lands this exact
bump on top of it, on the SAME branch lineage. The follow-up's own content is not model-authored (there is no
creative discretion in "what is the current path count" — it is a deterministic fact derivable from the
document itself), so it is applied directly rather than routed through another live AI call for a decision
that has only one correct answer.

## Scope

**What changed**: one test assertion and its own comment.

**What did not change**: the endpoint's own approved contract (CR-054/062); the OpenAPI document's real
content beyond what CR-062's own feature already added.

## Conditions attached to the approval

1. **The underlying scope gap stays disclosed, not silently closed** — `docs/evidence/ds-a/
   attempt-21-s7-succeeded-s8-finding.md` remains on the record describing why S6/S7 do not yet catch this
   class of sibling-test staleness on their own; this CR fixes THIS instance, not the general gap.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| A future API-surface change will hit the SAME gap again (S6 still has no visibility into sibling structural-assertion tests) unless the underlying finding's own disclosed options are acted on | Whenever the next path is legitimately added to `openapi.yaml` |
