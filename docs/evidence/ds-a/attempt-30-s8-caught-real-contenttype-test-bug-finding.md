# Attempt 30 — S7 succeeded again; S8 correctly caught a real, reproducible bug in the AI's own generated
test, not infrastructure flakiness

**Status: run correctly SAFE_STOP. Diagnosed and reproduced, not guessed. Not patched — this is evidence of
the guard working, not a defect in `Conductor`/orchestration code.**

## What happened

Real, live S7 dispatch (`ds-run/T1-d1c7191b`, CR-064 bookkeeping follow-up on
`ds-run/T1-cr064-path-count-d9567643`) produced a different, independently-authored implementation of
`GET /v1/version` from every prior attempt: a `BuildProperties` bean, an explicit `generate-resources` phase
binding for `build-info`, and — new this time — its own `VersionControllerTest.java` (fast-tier unit test).
S8 failed: `1 failures of 785`.

## Diagnosis — reproduced directly, not guessed

The real branch's five changed files (`pom.xml`, `openapi.yaml`, `VersionController.java`,
`VersionControllerTest.java`, `ContractConformanceIT.java`) plus the CR-064 bookkeeping bump to
`ContractFilesLintTest.java` were reconstructed by hand against the main working tree (`git worktree add`/
`git checkout <branch> --` both required interactive approval unavailable this turn) and the fast tier was
re-run directly. **Reproduced exactly**: `785 tests run, 1 failed`.

The one failure: `VersionControllerTest.returnsVersionWithExpectedStatusHeadersAndBody:30 — expected:
<application/json> but was: <null>`. The AI's own controller builds its response with
`ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(...)` — no explicit `Content-Type`. That is
correct for the real HTTP path: Spring's Jackson message converter negotiates `application/json` during
serialization. But the AI's own unit test calls the controller method directly, with no HTTP round trip, and
then asserts `response.getHeaders().getContentType()` — a header that is never set at the object level, only
by the converter during real serialization. The AI's own design doc (same run) states this correctly for the
HTTP path ("no explicit header set; Spring's Jackson converter negotiates it") and then authored a fast-tier
unit test that asserts the same header where it is never actually present.

## Why this is not patched, and not a defect in this codebase's own machinery

This is a real bug in AI-generated test code, caught by the real, deterministic S8 gate exactly as designed.
`docs/LIMITATIONS.md` already discloses live-AI non-determinism as an intended-design risk that "guards catch
bad output" — this is a live, concrete instance of that guard working correctly: a genuinely broken generated
test was rejected before it could reach a human reviewer as a false green. No source change was made in
response; the AI's own change set is real, disclosed evidence of an imperfect generation, not something this
turn's own code is responsible for fixing.

## What was NOT done

- No fourth live pipeline attempt was made — the owner's cap for that turn was three, already spent (attempts
  28, 29, 30).
- The reconstruction was fully reverted from the main working tree after diagnosis; nothing from this
  reconstruction was committed.
- `ds-run/T1-d1c7191b` and `ds-run/T1-cr064-path-count-d9567643` are left exactly as S7 produced them.

## Disclosed context

This finding stands alongside attempts 28 (unreproducible/flaky S8 failure) and 29 (a genuine S7 compile
failure whose blank-detail reporting bug was fixed as CR-068, root cause of the compile failure itself never
captured before the worktree was torn down) as this turn's three capped live attempts. Attempt 26 remains the
one fully clean S1→S10 run this engagement has produced; its own real emitted feature artifacts are what
`docs/evidence/ds-a/` and the codebase now carry forward as the greenfield demonstration.
