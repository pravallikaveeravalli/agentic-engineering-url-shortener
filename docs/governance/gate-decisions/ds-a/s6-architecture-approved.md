# DS-A live run — S6 `ARCHITECTURE_APPROVAL` gate decision

**Scope note**: a **product-internal** orchestration-run gate decision (DS-A's own S6), never this
repository's own numbered SpecKit lifecycle gates.

| Field | Value |
|---|---|
| **Gate** | S6 — `ARCHITECTURE_APPROVAL`, DS-A scenario, live run T132 (requirement: `GET /v1/version`, CR-054) |
| **Outcome** | `APPROVED` |
| **Deciding human** | Pravallika Veeravalli |
| **Decision date** | 2026-09-22 |
| **Artifact approved** | The real S6 design output captured in `docs/evidence/ds-a/run-snapshot-ATTEMPT-12-RESUMED-TO-S6-new-feature-under-delegation.md`: `VersionController` alongside the existing `HealthController`'s no-auth pattern; the version value sourced from Spring Boot's own `build-info`/`BuildProperties` mechanism (a new `spring-boot-maven-plugin` execution); response headers via `ResponseEntity.cacheControl(CacheControl.noStore())`; an ArchUnit no-auth-regression guard; an OpenAPI contract addition mirroring `/health/live`/`/health/ready`'s own `security: []` convention |
| **Where else recorded** | `docs/evidence/ds-a/pending-gate-s6-context-new-feature.md` (the context this decision answers) |

## Reason / verification performed

The owner reviewed the real S5 decomposition (five dependency-ordered tasks) and the real S6 design output
and approved it as sound. This session performed no independent architectural review beyond what was already
disclosed to the owner in the pending-gate context — the decision is the owner's own.

## Conditions attached to the approval

None beyond what the design itself already states (route pattern matching `HealthController`, version
sourced from build metadata never hardcoded, headers asserted semantically per the S4 clarifications already
on record).

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| S7's real implementation must match this approved design — any material deviation is a new decision, not an implementation detail | S7 dispatch, immediately following this record |
