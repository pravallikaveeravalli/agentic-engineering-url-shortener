# Attempt 22 — S7 AND S8 both succeeded for real (a new milestone). S9 hit a genuine, previously-unexercised
structural mismatch between what S8 produces and what S9 requires.

**Status: OPEN. Real, live, decisive. Run correctly `SAFE_STOP`. Not forced past.**

## What happened

CR-064's bookkeeping follow-up (`DsALiveRun.s7WithBookkeepingFollowup`) worked exactly as designed: S7's real,
live change (`ds-run/T1-5218f637` — `pom.xml`, `contracts/openapi.yaml`, a new `VersionController.java`, and
this attempt's own `VersionResponse.java`) succeeded, and the deterministic path-count bump landed cleanly on
top of it (`ds-run/T1-cr064-path-count-e444449a`, diff verified byte-exact — CR-013's own precedent followed
correctly). **S8 then SUCCEEDED — the first time the real fast-tier suite has ever passed against a live S7
change in this task's entire history**, confirming CR-064's fix genuinely closed attempt 21's own finding.

**S9 then failed**, for a reason with nothing to do with CR-060, CR-064, or the feature itself:

```
MalformedProviderOutputException: the 'results' artifact carried no executed behaviours — S8's own
output is malformed, which is an upstream defect, not this stage's own
```

## Root cause, read directly from both real classes

`TestingEngine` (S8's own real deterministic engine) produces its `"test-results"` artifact as
`report.report()` — `ScriptTestSuiteRunner.summarize()`'s own plain, human-readable **string**:
`"109 fast-tier test class report(s), 775 tests run, 0 failed."` — aggregate counts only, never a list of
which behaviours were exercised.

`DocumentationAiExecutor` (S9) requires the OPPOSITE shape: it parses `results` as JSON and requires a
non-empty `"executedBehaviors"` array naming individual behaviours, which it later cross-checks the model's
own `behaviorsDescribed` claims against (T149's drift check, Constitution X — documentation must never
describe behaviour the run did not verifiably execute).

**These two contracts have never been compatible, and no test in this codebase's own history caught it**,
because S8 has never succeeded against a REAL, live S7 change before THIS turn — every prior fixture-based
test of S9 supplies its own hand-written `results` JSON already shaped the way `DocumentationAiExecutor`
expects, which is exactly the same category of gap the `"tasks"`→`"task"` artifact-alias defect and the S7
existing-file-diff limitation both were: a real defect that only a genuine end-to-end live dispatch, not a
fixture, can surface.

## Why this is a real, structural gap, not an alias

The `"tasks"`→`"task"` fix (earlier this engagement) was a pure KEY NAME mismatch — the alias mechanism
already built into `Conductor.ARTIFACT_ALIASES` was sufficient. This is different and larger: the two
producers/consumers disagree on **shape and semantic content**, not merely the artifact's key name. Aliasing
`"test-results"` to `"results"` (already done) makes the STRING reach S9 under the right key; it does nothing
about the fact that the string itself is not the JSON object with a behaviour list S9 requires. Fixing this
properly means `TestingEngine`/`ScriptTestSuiteRunner` would need to report WHICH individual behaviours were
verified — a real design question (every JUnit test's own display name? a higher-level, requirement-mapped
behaviour list? something else derived from the real surefire reports?) that this agent should not decide
unilaterally under time pressure, matching this session's own consistent discipline for every prior
architecturally-significant finding.

## What was NOT done

- No fix was attempted to `TestingEngine`, `ScriptTestSuiteRunner`, or `DocumentationAiExecutor` this turn —
  this is a real design decision (what does "an executed behaviour" mean, concretely, derived from a real
  test run?), not a mechanical bump like CR-064's own fix.
- The run was not forced past S9. `RunState` is `SAFE_STOP`.
- Both real branches (`ds-run/T1-5218f637`, `ds-run/T1-cr064-path-count-e444449a`) are kept as real evidence,
  not deleted — the second one is genuine, decisive proof that S7's editor fix (CR-060) and the bookkeeping
  follow-up (CR-064) both work correctly end-to-end, all the way through a fully green S8.

## Disclosed options for the owner (not decided here)

1. **Have `TestingEngine` enumerate real test names** from the real surefire XML/txt reports already produced
   under `target/surefire-reports` (already being read for pass/fail counts) as the `executedBehaviors` list
   — mechanical, derivable from data already captured, but couples "a behaviour" to "a JUnit test method
   name," which may be finer- or coarser-grained than what documentation should actually describe.
2. **Have S6's design (or S5's tasks) name the specific behaviours** a change is expected to deliver, and have
   S8 report which of THOSE were verified — closer to what T149's drift check conceptually wants (behaviour,
   not test-method-name), but a larger design change spanning three stages.
3. **Relax `DocumentationAiExecutor`'s own guard** to accept a non-behaviour-enumerated results summary when
   S8 reports zero failures, documenting only that "the suite passed" rather than naming specific behaviours —
   simplest, but weakens T149's own drift-check intent (documentation could still describe something never
   individually verified, only that SOMETHING passed).
4. Some other decision.

## Real evidence this attempt also produced

- Confirms CR-060 (search/replace editor) works reliably across TWO independent real dispatches now (attempt
  21's S7, and this attempt's own S7 — a different, slightly different real implementation choice each time,
  proving it is genuinely re-derived by the model each run, not a memorized/cached answer).
- Confirms CR-064's own bookkeeping follow-up mechanism works correctly, live, on a real feature branch.
- Confirms S8 (the real 775-test fast tier) can pass cleanly against a live, AI-authored change once its own
  sibling bookkeeping test is current — the FIRST fully green real test run against any live S7 output in
  this task's history.
