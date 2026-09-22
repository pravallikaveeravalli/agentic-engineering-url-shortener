# CR-063 — Matcher regression found by the PART 1 sanity check: plural "parameters" and a credential-only
finding both fixed before the full run

| Field | Value |
|---|---|
| **Change request** | CR-063 |
| **Title** | `applyOwnerClarificationAndResume`'s ACCESS branch: `parameter`/`parameters` and `credential`/`credentials` are now all standalone whole-word triggers |
| **Raised by** | This session's own analysis of `GreenfieldCleanRequirementSanityCheck`'s real, live S3 output — exactly the failure mode the owner's own mandated sanity check exists to catch before a full run |
| **Decided by** | Pravallika Veeravalli (standing delegation covers this: a routine matcher-reliability fix, not a new policy decision — the underlying answer, `ACCESS_CLARIFICATION`, is unchanged) |
| **Decision** | **APPROVED under the standing delegation** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — a matching-precision fix; no standing answer's own text changed |
| **Artifacts changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (ACCESS branch) |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRunMatcherTest.java` (two new regression tests, built from the real sanity-check output) |

---

## What the sanity check found

Two real findings in the clean requirement's own real S3 output (`docs/evidence/ds-a/
sanity-check-clean-requirement.md`) were genuinely material and genuinely already covered by
`ACCESS_CLARIFICATION`, but would NOT have matched under CR-061's own matcher:

1. A finding about unexpected query parameters used the real plural "query **parameters**" — CR-061's own
   `containsWord(lower, "parameter")` uses `\bparameter\b`, which does not match "parameters" (the boundary
   after "parameter" fails against the following "s"). A real, live regression in last turn's own fix,
   caught here before it could stall PART 2's full run.
2. A finding about invalid/malformed credentials never used the word "access" anywhere in its own text, so
   the existing `access`+`auth` compound condition could not have matched it either, even though
   `ACCESS_CLARIFICATION`'s own substance ("no credential required, no error-input handling beyond the
   framework default") directly answers it.

## The fix

`parameter`, `parameters`, `credential`, `credentials` are now all standalone whole-word OR-conditions in the
ACCESS branch (no longer requiring "access" to co-occur for the credential case). No standing answer's own
text changed — this is purely a matching-precision fix, closing a real gap the sanity check's own real output
exposed, exactly as the sanity check exists to do.

## RED-FIRST verification

Two new tests in `DsALiveRunMatcherTest`, built directly from the real sanity-check output (not invented
fixtures): `pluralParametersStillMatches` (proves the singular-only regression, then proves the fix) and
`credentialFindingMatchesWithoutTheWordAccess` (proves "access" is genuinely absent from the real text, then
proves the new standalone trigger catches it anyway). 8/8 tests green in `DsALiveRunMatcherTest`.

## Scope

**What changed**: matcher OR-conditions only.

**What did not change**: `ACCESS_QUESTION`/`ACCESS_CLARIFICATION`'s own text; every other branch; the
`SEMANTIC_CONTRADICTION` class-level exclusion (CR-059).

## Conditions attached to the approval

1. **Found and fixed BEFORE the full run, not after a live failure** — honoured; this is the sanity check
   working exactly as the owner intended it to.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If the full live run's own S3 output phrases either finding differently again, or surfaces a third collision, report it honestly rather than patching reactively a fourth time without stepping back (the same standing note CR-061 already carries) | The full live run, immediately following |
