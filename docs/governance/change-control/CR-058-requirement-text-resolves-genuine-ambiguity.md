# CR-058 — Greenfield requirement text sharpened to remove the genuine ambiguity CR-056's wording left open

| Field | Value |
|---|---|
| **Change request** | CR-058 |
| **Title** | `DsALiveRun`'s own `REQUIREMENT` constant: the version-sourcing mechanism is now fully specified (fixed literal, new resource file, no pom.xml/resource-filtering), and the missing-file question is foreclosed by construction |
| **Raised by** | This session's own analysis, per CR-057's own finding: attempt 18's real S3 output showed CR-056's wording left two genuinely material forks open (docs/evidence/ds-a/run-snapshot-ATTEMPT-18-...) |
| **Decided by** | Pravallika Veeravalli (standing delegation covers this: a routine implementation/wording detail, not a change to the endpoint's own approved contract) |
| **Decision** | **APPROVED under the standing delegation** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — no change to the endpoint's own contract (path, method, auth posture, response shape, all unchanged from CR-054); resolves ambiguity in requirement text, does not change what S3/S4 are permitted to find material elsewhere |
| **Artifacts changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`REQUIREMENT` constant) |
| **Non-approved files changed** | none |

---

## Why

CR-057's own investigation found the two `MATERIAL_PENDING` findings attempt 18's real S3 pass raised were
genuinely material, not a classifier defect — and concluded the fix belongs in the requirement text. This CR
is that fix: the version value's source is now a fully specified fixed literal in a new, build-time-bundled
resource file (never `pom.xml`, never Maven resource filtering — both explicitly ruled out), and the
"resource file missing/malformed at runtime" question is foreclosed by construction (a committed classpath
resource cannot be absent at runtime short of build corruption, out of scope). Both are the exact two forks
attempt 18 found; neither survives this wording.

## Scope

**What changed**: requirement text only, one driver-code constant.

**What did not change**: the endpoint's own approved contract; CR-054's own no-auth/response-shape wording,
verbatim; `AmbiguityDetectionAiExecutor`'s own classifier (untouched, per CR-057's own conclusion).

## Conditions attached to the approval

None beyond CR-057's own carried-forward note (a future requirement edit that removes a default without
naming a replacement should expect S3 to correctly flag it material).

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If this run's real, live S3 pass still finds material ambiguity despite this fix, that is itself real evidence to report honestly, not force past | The live re-run immediately following this CR |
