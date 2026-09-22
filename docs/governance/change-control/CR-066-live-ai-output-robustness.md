# CR-066 — Live-AI output robustness: S7's JSON extraction hardened with one bounded self-correction retry;
S9's prompt made honest with an explicit verbatim allow-list, drift guard itself untouched

| Field | Value |
|---|---|
| **Change request** | CR-066 |
| **Title** | `ImplementationAiExecutor` (S7): robust JSON-candidate extraction + one internal, bounded self-correction retry on a genuine parse failure. `DocumentationAiExecutor` (S9): prompt prints the real `executedBehaviors` as its own explicit, bounded, verbatim-only allow-list instead of burying it inside the raw results JSON |
| **Raised by** | Owner's own direction, this session (2026-09-22): "Harden the live-AI output robustness (helps ALL scenarios), then get ONE clean greenfield run to S11" — motivated by two real, live failure points from attempts 24–25 (`docs/evidence/ds-a/attempts-24-25-finding.md`) |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — both fixes implemented and tested this turn** |
| **Decision date** | 2026-09-22 |
| **Classification** | **MEDIUM** — changes S7's own internal retry behaviour (still exactly one `StageOutcome` per Conductor dispatch, never a second stage attempt) and S9's own prompt construction; does not touch either stage's failure-classification contract, `StageInput`'s closed field list, or T149's own drift-check guard |
| **Artifacts changed** | `src/main/java/agentic/shortener/orchestration/executor/ai/stages/ImplementationAiExecutor.java`, `src/main/java/agentic/shortener/orchestration/executor/ai/stages/DocumentationAiExecutor.java` |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/executor/ai/stages/ImplementationAiExecutorTest.java`, `src/test/java/agentic/shortener/orchestration/executor/ai/stages/DocumentationAiExecutorTest.java` (both extended with new coverage) |

---

## PART 1 — S7's robust extraction + bounded retry

**Robust extraction** (`extractJsonCandidate`): tries, in order, a fenced block ANYWHERE in the answer (not
only a leading one), the whole answer if it already looks like bare JSON, and — as a last resort — the
substring between the first `{` and the last `}`, tolerating stray prose with no fence at all. This never
decides a candidate is *valid* by itself; the existing structural validation in `extractChangeSet` still
fully parses and checks whatever candidate is chosen, so a wrong guess still fails loudly rather than being
applied speculatively.

**One bounded, internal self-correction retry**: on a genuine parse failure, `execute()` issues exactly one
further call to the provider, showing it the specific error and its own prior bad answer, before failing the
stage. This is safe specifically because it happens strictly before `BranchApplier.apply` is ever called — no
git effect has occurred yet, so EC-033's own non-idempotent-git-effect concern (the reason S7 is excluded from
the ORCHESTRATOR's ordinary declared-retryable set) does not apply to it. From `Conductor`'s own point of
view this remains exactly one `StageOutcome` per dispatch, never a second stage attempt — `RetryPolicy`'s own
budget is untouched.

**Prompt tightened at the source**: explicit escaping rules (literal newlines inside a string value must be
written as `\n`; quotes and backslashes escaped), an explicit "no code fence, no prose" instruction, and a
reminder that even a large file's content is still a single, fully-closed JSON string.

## PART 2 — S9's honest producer, guard untouched

`DocumentationAiExecutor.buildPrompt` now takes the already-parsed `executedBehaviors` set directly (not the
raw `results` JSON string) and prints it as its own explicit, bulleted, bounded list, with an unambiguous
"copy verbatim, character for character, or not at all" instruction — never "consult the results below" as
one instruction buried among several, and never mixed in with `total`/`failed`/`report` noise. **The actual
guard — `parse`'s own cross-check of `behaviorsDescribed` against `executedBehaviors` — is completely
untouched.** This is deliberately a producer-honesty fix, not a guard change: the fix makes it easier for the
model to comply, never easier for a non-compliant answer to pass.

## Why this is the right target, verified against real evidence rather than assumed

Both real failures motivating this CR are documented, live, and attributable to a specific cause:
attempt 25's own JSON failed to parse entirely (not a search/replace mismatch — CR-060's own mechanism was
never reached); attempt 24's own drift-guard rejection came from the model naming a plausible but real,
existing test (`TraceabilityReporterTest.detectsOrphanImplementation`) that was not in THAT run's own actual
results — exactly the shape a buried, easy-to-skim-past instruction produces, and exactly what an explicit,
visually separated allow-list is meant to reduce.

## RED-FIRST verification

`ImplementationAiExecutorTest`: a malformed-then-valid two-call sequence succeeds, with the applier proven to
receive the CORRECTED change set and the retry prompt proven to show the model its own prior failure; a
malformed-twice sequence still fails after exactly two calls (bounded, not open-ended); a fenced block not at
the very start of the answer is still extracted; JSON surrounded by stray prose with no fence at all is still
extracted. `DocumentationAiExecutorTest`: the prompt is proven to contain each real behaviour as its own
bulleted allow-list entry and an explicit verbatim instruction. 30 tests total, all green
(`./scripts/build.sh -Dtest=ImplementationAiExecutorTest,DocumentationAiExecutorTest test`), `ci.sh` green.

## Scope

**What changed**: S7's own internal extraction/retry logic and prompt wording; S9's own prompt construction.

**What did not change**: either stage's own `StageOutcome`/failure-category contract; `StageInput`'s closed
field list; T149's own drift-check cross-check logic; `RetryPolicy`'s own stage-level retry budget.

## Conditions attached to the approval

1. **The drift guard is never weakened, only the producer made more honest** — honoured; `parse`'s own
   cross-check is byte-for-byte unchanged.
2. **The retry is bounded, not open-ended, and never a second Conductor-visible stage attempt** — honoured
   and tested.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| These fixes benefit every future AI-capable stage dispatch, not only greenfield — brownfield's own future S7/S9 dispatches inherit them automatically | Whenever brownfield reaches S7/S9 for real |
| If a clean end-to-end run still does not materialize within the owner's own capped attempts this turn, the failure pattern (which stage, which specific cause) is real, disclosed evidence for her own accept-as-demonstrated-vs-keep-pushing decision — not something to force past | This turn's own PART 3 |
