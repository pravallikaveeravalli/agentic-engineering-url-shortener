# CR-049 compensating check — DS-C's canonical contradiction, post-calibration

**Real, live Claude call** (`claude-sonnet-5`, via `AmbiguityDetectionMaterialityCompensatingCheck`), run
against S3 (`AmbiguityDetectionAiExecutor`) AFTER the materiality-classification predicate was added to its
prompt — the same recalibration applied to fix T132's DS-A false-positive stops. Input is spec.md's own
canonical demonstration text, verbatim (`spec.md` §"What ambiguity detection is"):

> R1: Short links expire after a week.
> R2: Redirect analytics are retained indefinitely.
> R3: Expired links should still redirect for trusted partners.

**Result: PASS.** Seven distinct ambiguities detected, **every one classified `MATERIAL_PENDING`**, zero
classified `NOT_MATERIAL`. The recalibrated S3 did not go soft — it still correctly finds the canonical
contradiction determines required behaviour with no existing approved artifact resolving it, exactly as
CR-007's predicate requires.

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 55.38 s
```

## The seven findings, captured verbatim (not paraphrased)

1. **SEMANTIC_CONTRADICTION** — R1 and R3 together render "expire" internally inconsistent: R1 treats
   expiry as the link ceasing to function, R3 requires an expired link to keep functioning for a subset of
   callers.
2. **CONTRADICTORY_BOUNDS** — the link-validity duration is bounded to one week (R1) but unbounded for
   trusted-partner access (R3), with no stated precedence rule.
3. **UNDEFINED_TERM** — "trusted partners" names no defined category; the term determines a
   security-relevant access exception, not a cosmetic label.
4. **MISSING_ACTOR** — no actor is named as responsible for designating, authenticating, revoking, or
   auditing "trusted" status.
5. **UNDEFINED_TERM** — "after a week" names no reference point the week is measured from (creation? last
   access?), which changes when expiry actually takes effect.
6. **UNBOUNDED_QUANTIFIER** — "retained indefinitely" places no upper bound or exception on analytics
   storage, bearing directly on security/privacy posture and storage cost.
7. **MISSING_ACCEPTANCE_CRITERIA** — "should still redirect for trusted partners" states no verifiable
   success condition (response shape, logging behaviour, rate limits).

Every `qualityChecksPerformed` field states what was searched for and confirms it was not found in the
requirement set — matching the recalibrated prompt's own "affirmatively show" standard for materiality,
applied here to confirm genuine materiality rather than absence of it.

## What this proves, and what it does not

**Proves**: the materiality predicate added to S3's prompt is not a blanket softener — a genuinely
ambiguous, genuinely under-specified input still produces multiple `MATERIAL_PENDING` findings after
calibration, at the same rate of genuine substantive engagement as before.

**Does not prove**: that DS-A's own revised requirement will now clear S3 — that is a separate, independent
question the DS-A re-run itself answers, not assumed from this check.
