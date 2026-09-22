# CR-069 — DS-B's own hand-authored test-file follow-up, applied on top of S7's real branch, never
presented as AI output

| Field | Value |
|---|---|
| **Change request** | CR-069 |
| **Title** | `RateLimiterTest`'s stale tier-absence assertions and `RateLimitIT`'s before-state test are retired/rewritten by a hand-authored, disclosed follow-up — not by S7 itself |
| **Raised by** | This session's own investigation, DS-B turn: S6/S7's own file scope (`existingFilesToModify`) does not include sibling structural-assertion tests, exactly DS-A's own attempt-21 finding (`docs/evidence/ds-a/attempt-21-s7-succeeded-s8-finding.md`) |
| **Decided by** | Pravallika Veeravalli (standing delegation: a disclosed, hand-authored bookkeeping follow-up, mirroring CR-064's own precedent in kind though not in mechanical derivability) |
| **Decision** | **APPROVED under the standing delegation** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — test-file bookkeeping only; no production code touched by this CR |
| **Artifacts changed** | `src/test/java/agentic/shortener/delivery/ratelimit/RateLimiterTest.java`, `src/test/java/agentic/shortener/delivery/ratelimit/RateLimitIT.java` |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsBTestFollowup.java` (the applier itself) |

---

## Why this exists, and why it is NOT AI-authored

`RateLimiterTest.theAggregateTierIsNotPartiallyPresent` and `throttlingIsOnByDefault`'s own missing-key
assertion, and `RateLimitIT.aggregateTrafficPassesUnthrottled`, all assert the aggregate tier's own
**absence** — a premise T136a makes false on purpose. None of the three is named in S6's own
`existingFilesToModify` (the design stage names files the FEATURE itself requires touching; these three
tests are bystanders whose own assertions merely depend on the tier's absence, exactly the scope gap
DS-A's attempt-21 finding already diagnosed for a different sibling test).

Unlike CR-064's own one-line, mechanically-derivable path-count bump ("how many paths does the document
contain" has exactly one correct answer), rewriting these three assertions requires real judgement: which
demonstration numbers to use for the after-state test, how to restructure a before/after HTTP test so the
aggregate tier — not the per-code tier — is what fires. That judgement is exercised here, by this harness,
openly disclosed as hand-authored rather than S7's own output, never presented as something a live AI call
produced.

## The fix

`DsBTestFollowup.changeSet` reads the real, live `application.yml` S7's own dispatch produced (via `git
show <branch>:...`) to extract whatever key name the model actually chose for the new tier's configuration
property — never assumed in advance — and builds three EDIT operations against the real, current content of
the two test files:

1. `RateLimiterTest.theAggregateTierIsNotPartiallyPresent` → replaced with
   `theAggregateTierIsNowBuiltAndDisclosedAsClosed`, asserting `docs/delivery/baseline-omissions.md`'s own
   entry 1 says `Status: Closed` — the durable, governance-level fact, not a specific class's own Java
   symbol names (which this follow-up must not assume in advance of S7's real, live output).
2. `RateLimiterTest.throttlingIsOnByDefault`'s missing-key assertion → replaced with an assertion the real
   key equals 3000 (PVT-014).
3. `RateLimitIT.aggregateTrafficPassesUnthrottled` (the before-state) → replaced with
   `aggregateTrafficIsThrottledAfterT136a` (the after-state): six follows spread across four codes, all
   within each code's own per-code budget, all allowed; a seventh throttled by the aggregate tier alone,
   naming the tier and never the creator.

Applied via the same real `GitWorktreeBranchApplier` machinery CR-060 already built, on the same branch
lineage S7 produced — one commit history, honest about which parts came from which source.

## Scope

**What changed**: two test files' own stale assertions. **What did not change**: any production code;
`ContractFilesLintTest`'s own path-count assertion (unaffected — this feature adds no new OpenAPI path);
`RateLimitIT.throttledFollowerLearnsNothingAboutTheCreator` and
`redirectTierCannotIdentifyTheCreator`/`theDisclosureExistsBeforeTheSweep`, all left untouched, continuing
to prove the per-code tier's own non-disclosure guarantee is unregressed.

## Conditions attached to the approval

1. **Never presented as AI output** — honoured; disclosed here and in `DsBTestFollowup`'s own javadoc.
2. **The real key name is read from the real branch, never assumed** — honoured.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| This follow-up is applied only on top of a real S7 branch; if the branch's own `existingFilesToModify` ever comes to include these test files directly, this CR's own follow-up would double-apply and must be checked for idempotency, matching CR-064's own precedent | Whenever S6's own design output changes shape for this feature |
