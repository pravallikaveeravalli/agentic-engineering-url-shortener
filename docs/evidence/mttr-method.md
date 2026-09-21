# MTTR measurement method — population, exclusions, limitations

Task T120. FR-ORC-024, NFR-REC-002, plan §7, Constitution IX. Read this before quoting any MTTR or
reliability-measurement figure from this project's evidence: Constitution IX requires that "every figure
must state whether it was measured and under what conditions" (T120's own Guard clause) — a bare number
is not reportable.

## Declared population

Every MTTR figure this project reports is computed by `MttrCalculator.calculate` over a population of
`FailureEvent` rows drawn from exactly these sources, and no others:

1. **The three demonstration scenarios** — DS-A (greenfield), DS-B (brownfield), DS-C (ambiguous
   requirement), as defined in `spec.md`'s Demonstration Scenarios section. Each scenario's own run
   produces whatever `failure_event` rows its injected faults or real dependency behavior generate.
2. **The reliability test suite** — the scripted-executor tests under
   `src/test/java/agentic/shortener/orchestration/reliability/` and `src/test/java/agentic/shortener/
   recovery/` that exercise `RetryPolicy`, `ResumeService`, and the compensation/rollback paths against
   injected failures (`ScriptedExecutor`, fault-injecting fakes). These are the source of most of the
   *volume* in any reported population — the three scenarios alone rarely produce enough failure events
   for a meaningful average.

A figure computed over any other source (production traffic, a different project, a manually
constructed population not traceable to one of the two sources above) is out of scope for this method
and must not be presented as if it came from it.

## Declared exclusions

- **Human wait is excluded from every duration**, not merely from the average. `HumanWaitTracker`
  measures time in `AWAITING_APPROVAL` (a node state) and `SAFE_STOP` (a run state) within each
  recovery's window and subtracts it before the duration is even stored on the `FailureEvent` row — see
  T117. This is a declared exclusion, not an accident of the query: including it would make MTTR a
  function of when a person was at a keyboard, measuring reviewer latency rather than system recovery.
- **Unrecovered failures are excluded from the MTTR denominator**, and reported as a separate count
  (`MttrResult.unrecoveredCount`) rather than dropped — see T119. Folding them into the denominator
  produces a smaller, flattering number that hides exactly the failures that never came back.

## Declared limitations

Every MTTR or reliability figure quoted from this project's evidence is subject to all of the following,
none of which is disclosed anywhere else if not disclosed here:

- **Single developer machine.** No distributed load, no production-scale concurrency, no network
  partition beyond what a local Testcontainers Postgres restart or a scripted fake can simulate.
- **Injected faults, not observed ones.** Every failure a reported MTTR is computed over was deliberately
  scripted (`ScriptedExecutor`, a fault-injecting fake, or a scenario's own deliberately malformed input)
  — none is a fault this system encountered running unattended in the wild. A scripted fault's recovery
  time is not evidence about how long a real, unanticipated fault would take to recover from.
- **Small populations.** The reliability suite and three scenarios together produce a population in the
  tens of events, not the thousands a production SLO would be computed over. A mean over a small
  population is sensitive to individual outliers in a way a mean over a large one is not.
- **Possibly compressed time parameters.** Some tests use shortened backoff/timeout windows so the suite
  runs in seconds rather than minutes (`RetryPolicy`'s real 1s/2s backoff is representative; a few
  timeout-related tests are not). A duration measured against a compressed parameter is not directly
  comparable to the same code path's duration under its real, uncompressed configuration.

## Every figure carries its own label

T121 (`MeasurementLabel`) enforces this structurally rather than by convention: a figure this project
reports through `MttrCalculator` or `RunMetrics` is never a bare number. It is wrapped in a
`MeasurementLabel` that states either `MEASURED` (with the conditions above, restated inline) or
`PROPOSED` (not yet measured — a target or an estimate). A demonstration measurement presented as a
production statistic, without this labelling, is condition 9 — a release-blocking condition, per T121's
own Guard clause.
