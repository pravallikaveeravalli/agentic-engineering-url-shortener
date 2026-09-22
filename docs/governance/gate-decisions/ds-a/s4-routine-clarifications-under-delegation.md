# DS-A live run — S4 `UNRESOLVED_AMBIGUITY` gate decision (routine clarifications, under standing delegation)

**Scope note** (same as every prior file in this directory): a **product-internal** orchestration-run gate
decision, never this repository's own numbered SpecKit lifecycle gates.

| Field | Value |
|---|---|
| **Gate** | S4 — `UNRESOLVED_AMBIGUITY`, DS-A scenario, live run T132 (requirement: `GET /v1/version`, CR-054's wording — a genuinely new feature) |
| **Outcome** | `APPROVED` |
| **Deciding human** | Pravallika Veeravalli |
| **Decision date** | 2026-09-22 |
| **Recorded under** | The owner's own standing delegation of routine clarification resolution — `docs/governance/delegations/routine-clarification-delegation.md` ("answer any clarification urself, don't wait for me," 2026-09-21) |
| **Artifact approved** | Whichever of this run's own real, live `MATERIAL_PENDING` findings matched one of the delegation's standing answers (media-type conformance, caching conformance, non-GET/unmapped-method handling, version/build-metadata sourcing, public/no-auth access) — see `docs/evidence/ds-a/run-snapshot-ATTEMPT-12-...md` for the exact findings this run actually produced |
| **Where else recorded** | `clarification_decision`/`ambiguity_record` tables (via `LineageStore.resolveWithClarification`); `docs/evidence/ds-a/pending-gate-s4-context-v1version-new-feature.md` |

## Reason / verification performed

Each finding resolved under this decision was checked against the delegation's own standing-answers list
(quoted in full in the delegation record) before being recorded — never invented, never a fresh live
judgment call by the acting session. Any finding that did **not** match one of the standing answers was left
unresolved and reported to the owner rather than folded into this decision — the delegation's safety valve is
structural in the driver's own code (`DsALiveRun.applyOwnerClarificationAndResume`), not merely asserted here.

This is **not** a case of the acting session exercising independent judgment about what the owner would want;
it is the owner's own, already-stated positions, applied to whichever of them this run's real S3 output
actually needed. `ActorAuthority`/FR-ORC-021 is unaffected: the decision recorded here carries `actorType =
"human"`, `identity = "Pravallika Veeravalli"`, because the decision's content is hers.

## Conditions attached to the approval

1. **S7's implementation (if this run proceeds that far) MUST source the version value from the
   application's own build metadata** (e.g. the project version), never a hardcoded arbitrary literal and
   never runtime-computed from unrelated state.
2. **Content-Type/Cache-Control conformance MUST be asserted semantically** (media type / non-cacheability),
   not by exact string equality, matching the same condition already recorded for the retired `v1/version`
   subject.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If a future run under this same delegation surfaces a finding that does not match any standing answer, it must be reported to the owner plainly — the delegation's safety valve does not expire or loosen with repeated use | Every future use of the delegation |
