# Attempts 1–3 — DS-B's real, live brownfield build. Retry genuinely demonstrated, twice; compensation
captured as a real, separate proof; the full build did not land within this turn's own capped attempts

**Status: T136's retry evidence real and solid. T136's compensation evidence real, but captured separately,
not inside a run that also completed the tier. T136a (the real code) and T137 (before/after tests) did NOT
land this turn — genuinely blocked, not forced past, not fabricated.**

All three attempts used the real, live Claude Code CLI (`claude-sonnet-5`), the real Conductor, the real
`GitWorktreeBranchApplier`, and the real `RetryPolicy`/`RetryRuling`/`ProviderFailureTranslator` machinery —
nothing simulated except the one deliberately-injected transient failure T136 itself calls for (see below).

## What ran, and what a review can independently check

- `docs/evidence/ds-b/run-snapshot-ATTEMPT-3-BLOCKED-S3-ambiguityclass-resolutionstate-confusion.md` — the
  last attempt's own full snapshot (node states, real model output). Attempts 1 and 2's own raw snapshots
  were overwritten in place before being archived (a real process gap this finding discloses rather than
  hides — `DsBLiveRun`'s own snapshot writer, inherited from `DsALiveRun`, overwrites one file per run; the
  archiving discipline used later in DS-A was not carried over to the first DS-B attempts). Their real
  content is preserved here as direct quotes from the real, captured build console output.
- `docs/evidence/ds-b/t136-compensation-only.md` — the real, separate compensation proof (below).
- `src/test/java/agentic/shortener/orchestration/conductor/DsBLiveRun.java`,
  `DsBTestFollowup.java` — the real driver and its hand-authored test-file follow-up (CR-069), both
  committed, both compiled, both exercised live.

## T136's retry evidence — genuinely demonstrated, twice

S3's real provider was wrapped so its first invocation throws a real `IOException` (harness-injected, never
a real provider fault) and its second invocation reaches the real Claude CLI. This is not a simulated log
line: it is `RetryPolicy`'s own real code, running against a real classified failure.

**Attempt 1** (real telemetry, `agentic.shortener.telemetry.stage` logger):
```
{"event":"RETRY_ATTEMPT_STARTED","stageNumber":3,"attemptNumber":1}
DS-B LIVE RUN: T136 retry demonstration -- S3 attempt 1: threw a real IOException (harness-injected); ...
{"event":"RETRY_ATTEMPT_COMPLETED","stageNumber":3,"attemptNumber":1,"outcome":"FAILED","durationMillis":1}
{"event":"RETRY_ATTEMPT_STARTED","stageNumber":3,"attemptNumber":2}
DS-B LIVE RUN: T136 retry demonstration -- S3 attempt 2 succeeded for real.
{"event":"RETRY_ATTEMPT_COMPLETED","stageNumber":3,"attemptNumber":2,"outcome":"SUCCEEDED","durationMillis":151712}
```
Attempt 2 reproduced the identical pattern (attempt 1 FAILED in 1ms — the injected exception, never touching
the network; attempt 2 SUCCEEDED after a real ~higher-latency live call). `ProviderFailureTranslator`
classified the injected `IOException` as `UNAVAILABLE`; S3's own declared retryable set (`{TIMEOUT,
UNAVAILABLE, RATE_LIMITED}`) includes it; the translator proposed it retryable — `RetryRuling`'s real
two-vote rule (`declared ∩ executor-proposed`) is what actually authorized the retry, not this harness.

## S4 — real, substantive, live ambiguity findings, answered across two iterations

**Attempt 1** reached S4 with 4 real `MATERIAL_PENDING` findings. One was pre-answered correctly by this
run's own prepared delegation (the expired/never-issued scoping question). Three were not, and the run
correctly stopped rather than force an answer nobody gave:

1. A real tension between R1a ("a single, true global count... across every link") and R1e ("in-process,
   per-instance counter... no external store").
2. Evaluation order relative to the two pre-existing tiers, unstated by the requirement.
3. Whether an ownerless ("anonymous") short link exists, and how the tier treats it.

All three are genuine, well-reasoned findings — not noise. Each was answered under the owner's standing
delegation, grounded in an already-established fact (not invented):
- (1) is the same in-process/single-instance limitation the two existing tiers already carry and disclose
  (`FixedWindowCounter`'s own javadoc).
- (3) is already foreclosed by `ShortLink.create()`'s own domain invariant — a non-null `creatorId` is
  required at construction (`"there is no link without an owner"`, KE-01).
- (2) follows directly from the requirement's own stated ordering (the aggregate check is explicitly
  post-lookup; the per-code check is unconditionally pre-lookup, so it is evaluated first by construction).

**Attempt 2**, with those three answers added, got past all but 2 findings — the same multi-instance tension
(reworded: "true aggregate ceiling" instead of "true global count", proving why a brittle exact-phrase match
would have missed it — broadened to a concept-level match) and a genuinely NEW, sharp finding this run had
not anticipated: that naming the tier `"per-creator-aggregate"` in a throttled response is itself a weak
popularity signal about the owning creator, in apparent tension with the non-disclosure clause. Investigated,
not waved away: the owner's own already-approved security-gate context
(`docs/governance/gate-decisions/ds-b/t136a-security-gate-approved.md`) already names `"per-creator-
aggregate"` as the example tier name, and the existing `CreationRateLimiter.TIER = "per-creator-creation"`
already names the creator dimension without disclosing anything about a specific creator — naming which rule
fired is a different, required fact from disclosing the creator's identity or exact traffic. Answered on that
basis and added to the matcher.

## Attempt 3 — a genuine, new, live-AI defect at S3 (not this codebase's own defect)

With both new answers in place, attempt 3's own real S3 output (8 findings, all well-reasoned) used
`"NOT_MATERIAL"` — a `ResolutionState` value — as the value of the `ambiguityClass` field on two of its eight
elements. `AmbiguityClass` has no `NOT_MATERIAL` constant; `AmbiguityDetectionAiExecutor.toRecord` correctly
refused it (`AmbiguityClass.valueOf` throwing, translated to a permanent `MalformedProviderOutputException` —
never retried, matching T073's own "malformed output is INTERNAL and permanent" contract). This is a genuine,
first-time-ever live-AI quality defect at S3 — never observed in any of DS-A's ~30 attempts — and it blocked
S4 from ever opening this attempt.

**Not forced past.** Per this turn's own capped-attempts discipline (3 total), no fourth full-build attempt
was made. The prompt was hardened (CR-070: `AmbiguityDetectionAiExecutor`'s own schema description now
explicitly forbids `ambiguityClass` ever being `MATERIAL_PENDING`/`NOT_MATERIAL`, naming the real confusion
observed), a new unit test (`promptStatesAmbiguityClassAndResolutionStateAreDisjoint`) proves the prompt
states it, and `ci.sh` is green — but **this fix is not yet re-verified against a further live attempt**; that
is explicitly left for the next DS-B turn, not claimed as solved by inspection alone.

## T136's compensation evidence — captured separately, honestly labelled as such

Since no attempt reached S7, `demonstrateCompensation` (which runs after S7 succeeds, compensating this same
run's own real S1 audit record) was never exercised inside a completed build. Per `plan.md` §14's own
pre-authorized End-Day-2-PM reduction ("cut DS-B's injected compensation case to a unit-level proof; keep the
scenario"), a separate, minimal, honestly-labelled proof was run instead
(`DsBLiveRun#compensationOnlyDemonstration`): a real `Conductor.submit()` of the same aggregate-tier
requirement (never advanced — no AI provider invoked), producing a real `RUN_CREATED` audit_record row, which
the real, already-unit-tested `CompensationHandler` (T090) then compensated:

```
DS-B COMPENSATION-ONLY DEMO: submitted the real aggregate-tier requirement, runId=40374185-... (never
advanced -- no AI provider invoked by this method).
DS-B COMPENSATION-ONLY DEMO: compensation recorded -- kind=COMPENSATION, action=COMPENSATION: appended a
corrective audit entry for audit:1
```

Real machinery, real effect, real correction — but genuinely NOT "inside a run that also completed the
aggregate tier," since no attempt reached that point. Stated precisely rather than folded into a claim it
does not support.

## What did NOT happen this turn

- **T136a's own real code does not exist.** `AggregateRedirectLimiter.java` was never created; S7 was never
  dispatched in any of the 3 attempts (all 3 stopped at or before S4/S3).
- **T137's before/after test evidence was not captured** — there is no after-state to capture; the existing
  `RateLimitIT.aggregateTrafficPassesUnthrottled` before-state test is untouched and still passes.
- **No 4th live pipeline attempt was made** — the cap was honoured.
- `docs/delivery/baseline-omissions.md` Entry 1 remains open; it was not touched.

## Disclosed options for the owner (not decided here)

1. **Authorize a 4th-turn attempt now that CR-070 is in place** — the prompt hardening is real and tested,
   but unverified live; a fresh attempt is the natural next step, not a repeat of a known-failing state.
2. **Accept this turn's own real, substantial progress as the stopping point** — retry genuinely proven twice
   live; compensation genuinely proven as a real, separate unit-level exercise (matching the plan's own
   pre-authorized reduction); three genuine, well-reasoned S4 findings correctly surfaced and answered; a
   real, new S3 defect found, diagnosed, and fixed (not yet re-verified) — real evidence of a governed
   pipeline working honestly, even though the tier itself is not yet built.
3. **Revise the REQUIREMENT text itself** to pre-empt more of what S3 keeps finding (the multi-instance
   tension, the tier-naming question) rather than relying on delegation-time answers — trading a shorter S4
   loop against a less naturally-derived design (DS-A's own CR-059→CR-062 history argues against
   over-specifying away genuine, useful ambiguity detection).
4. Some other decision.
