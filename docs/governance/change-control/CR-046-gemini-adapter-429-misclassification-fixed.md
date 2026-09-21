# CR-046 — Gemini adapter 429/quota-exhaustion misclassification fixed

| Field | Value |
|---|---|
| **Change request** | CR-046 |
| **Title** | Add task T131d; `GeminiCliStageAiProvider` recognizes a rate-limit/quota-exhaustion `error` field and reports it distinctly; `ProviderFailureTranslator` classifies it `RATE_LIMITED` |
| **Raised by** | A real failure encountered during T132's first live DS-A attempt, root-caused before any fix was written |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — fix it**, 2026-09-21. Owner's own framing: "a real correctness bug you found" |
| **Decision date** | 2026-09-21 |
| **Classification** | **MINOR** — narrows which non-`SUCCESS` responses fall into `MalformedProviderOutputException`/`INTERNAL`, adding one new, more specific outcome (`RATE_LIMITED`) for a signal that was previously indistinguishable from a genuinely malformed response. No existing passing test's expected classification changes: `nonSuccessStatusIsMalformed` (an `ERROR` status with no `error` field) is untouched and still green |
| **Artifacts changed** | `specs/001-agentic-sdlc-url-shortener/tasks.md` (T131d added; T132's `Deps` updated) |
| **Non-approved files changed** | `src/main/java/agentic/shortener/orchestration/executor/ai/GeminiCliStageAiProvider.java` (modified — committed under CR-042), `src/main/java/agentic/shortener/orchestration/reliability/ProviderFailureTranslator.java` (modified — committed under T083), `ProviderRateLimitedException.java` (new), plus tests in `GeminiCliStageAiProviderTest.java` and `FailureEnvelopeTest.java` |
| **Applied** | 2026-09-21 |

---

## Reason / verification performed

T132's first live DS-A attempt reached S3 (ambiguity detection) and the run correctly `SAFE_STOP`ped with
`UNRECOVERABLE_FAILURE`. Root-caused directly, not guessed: an isolated `agy` call outside the pipeline,
using the exact same prompt shape, reproduced the identical response — a fully-formed, substantive
six-ambiguity JSON answer, carrying a top-level `status: "ERROR"` **and** an `"error"` field:
`"API error (attempt 1): RESOURCE_EXHAUSTED (code 429): Individual quota reached. Please upgrade your
subscription to increase your limits. Resets in 1h10m0s."` A genuine, external Gemini API account-level
quota exhaustion — not a defect in `Conductor`, in the requirement, or in any other part of this session's
own work.

`GeminiCliStageAiProvider.parse()` read only the `status` field and, on anything but `"SUCCESS"`, threw
`MalformedProviderOutputException` unconditionally — which `ProviderFailureTranslator` maps to
`FailureCategory.INTERNAL` (permanent, never retried). A 429/quota signal is exactly the shape
`FailureCategory.RATE_LIMITED` exists to classify, and it is in S3's own declared retryable set
(`{TIMEOUT, UNAVAILABLE, RATE_LIMITED}`, `StageEffectContracts`) — the distinction was simply never drawn.

## Scope

**Where the fix lives, and why there — read directly from `FailureEnvelopeTest.coreLearnsNoVendorTaxonomy`
before writing any code**: T083's own architecture permits exactly one class,
`ProviderFailureTranslator`, to hold both the `FailureCategory` vocabulary and a provider exception type.
This CR does not weaken that: `GeminiCliStageAiProvider` (a "plugin" class, already doing agy-specific
parsing of `status`/`response`) recognizes agy's own `error` wording and chooses which EXCEPTION TYPE to
throw — the same kind of provider-specific parsing it already does. `ProviderFailureTranslator` alone
decides which `FailureCategory` that type maps to, via one new `Rule` entry. Neither class's existing
responsibility changed shape; the new exception type (`ProviderRateLimitedException`) is deliberately
generic in name and javadoc, matching `MalformedProviderOutputException`'s own precedent, so it carries no
vendor-specific meaning of its own.

**Explicitly not changed**: the response content agy returned alongside the error is still discarded — this
remains a failure outcome, not a partial success; no other non-`SUCCESS` shape's classification changes (a
dedicated negative test, `errorFieldNotNamingARateLimitStaysMalformed`, proves an `error` field that does
not name a recognizable rate-limit/quota signal still falls through to `MalformedProviderOutputException`
unchanged); `ClaudeCodeCliStageAiProvider` is untouched — this CR is scoped to the Gemini adapter alone,
since the finding came from its own observed wire shape, not a shared code path.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | Root cause confirmed directly — an isolated `agy` call outside the pipeline, not guessed | reproduced the exact response shape, including the `error` field | **EXECUTED — HOLDS** |
| 2 | `ProviderRateLimitedException` written first, then two tests naming it (`GeminiCliStageAiProviderTest#8`, `FailureEnvelopeTest`), before either `parse()` or the translator recognized it | genuine compile-then-assertion RED | **EXECUTED — HOLDS**, `docs/evidence/red-phase/20260921T231711Z-T131d-gemini-429-misclassification.txt` |
| 3 | `GeminiCliStageAiProvider.parse()` recognizes `RESOURCE_EXHAUSTED`/`429` in the `error` field and throws the new type; `ProviderFailureTranslator` gains one rule mapping it to `RATE_LIMITED` | `GeminiCliStageAiProviderTest` 9/9, `FailureEnvelopeTest` 11/11 | **EXECUTED — HOLDS** |
| 4 | A dedicated negative test proves the fix does not over-match — an `error` field naming something other than a rate limit still classifies `MalformedProviderOutputException`/`INTERNAL` | `errorFieldNotNamingARateLimitStaysMalformed` green | **EXECUTED — HOLDS** |
| 5 | `DependencyDirectionTest` (the architecture suite, including the vendor-taxonomy check's own package scan) | green, unaffected | **EXECUTED — HOLDS** |

## Conditions attached to the approval

1. **No live AI scenario run this turn** — the owner's own instruction, since the quota that caused this
   finding has not yet reset. This CR's own verification is entirely fast-tier (stub scripts), never a live
   `agy` call.
2. **Fix the real bug found; do not use it as license to broaden retry elsewhere** — honoured; the change is
   scoped to exactly the one new, verified signal.

## Cross-record orphan check

- CR-042 (which added `GeminiCliStageAiProvider`) made no claim about how non-`SUCCESS` responses classify
  beyond "malformed" — this CR narrows that classification for one specific, real case; it does not falsify
  anything CR-042 asserted.
- No other applied record claims S3's declared retryable set is unreachable in practice — this CR does not
  change the DECLARED set (`StageEffectContracts`, unmodified); it only makes the adapter capable of
  actually producing the `RATE_LIMITED` category that set already named.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Retry the live DS-A run once Gemini quota resets (~70 minutes from the last attempt, per `agy`'s own disclosed reset time) | Next live-AI-capable turn, per the owner's own sequencing |
| If a future `agy` response names a rate-limit condition in wording this fix does not recognize, extend `isRateLimitSignal` rather than assume the fix is complete forever | Whenever such a response is actually observed — not speculatively now |
