# T137 — DS-B before/after test results (per-creator aggregate redirect tier, T136a)

## Before-state — traffic spread across links passed UNTHROTTLED

`RateLimitIT.aggregateTrafficPassesUnthrottled` (real, integration-level, through HTTP) existed unmodified
from commit `9b07818` (T055) through `24c2034` (this turn's own CR-070 commit) — real, already-verified,
passing, part of this repository's own fast/integration tier throughout that span, not a fixture written
for this turn. It sent 12 follows spread across 4 links owned by the same creator, each individually inside
the per-code limit, and asserted all 12 succeeded (`307`), because the aggregate tier did not exist.
Citable directly: `git show 9b07818:src/test/java/agentic/shortener/delivery/ratelimit/RateLimitIT.java`.

## After-state — the same shape of traffic IS throttled, once the aggregate limit is hit

Replaced in commit `576f9bf` (this turn) by `RateLimitIT.aggregateTrafficIsThrottledAfterT136a` — real,
integration-level, through HTTP, run against the real, landed `AggregateRedirectLimiter`. With the
aggregate limit lowered to 6 for this test class only (the same pattern already used for the other two
tiers, via `@SpringBootTest` property overrides — PVT-014's own real target, 3000/minute, is asserted
separately and directly by `RateLimiterTest.aggregateLimitIsThreeThousandPerMinutePerCreator` and by
`throttlingIsOnByDefault` reading the shipped `application.yml`):

- Six follows, spread round-robin across the same four links (at most two per code — comfortably under
  each code's own per-code limit of four), all succeed (`307`).
- The seventh follow — against a code that has itself only been followed once — is throttled (`429`),
  proving the refusal comes from the AGGREGATE tier, not the per-code one.
- The throttled response names the tier (`"per-creator-aggregate"`) and conforms to the openapi contract's
  own `429` schema (`harness.assertConforms`).
- The owning creator's real id never appears anywhere in the throttled response body.

Verified directly this turn: `./scripts/build.sh -Dit.test=RateLimitIT verify` — 8 tests, 0 failures.

## Unregressed: the existing per-code and per-creator-creation tiers

Both tiers' own existing tests were re-run this turn, unmodified, alongside the new tier:
- `RateLimitIT.creationIsThrottled`, `creatorsAreIndependentThroughHttp` (PVT-012) — pass.
- `RateLimitIT.redirectsAreThrottledPerCode`, `oneHammeredLinkDoesNotAffectAnother` (PVT-013) — pass.
- `RateLimitIT.throttledFollowerLearnsNothingAboutTheCreator` (the per-code tier's own non-disclosure
  proof) — pass, untouched by this turn's changes.
- Unit level: `RateLimiterTest` — 27 tests (the original 14 for T054/T055, unmodified, plus 13 new for
  T136a), 0 failures.

## Non-disclosure, both levels

- **Structural** (`RateLimiterTest.aggregateTierDecisionCannotIdentifyTheCreator`): reflects on
  `RateLimitDecision`'s own record components and asserts none is of type `UUID` — the creator id this
  tier's own `check()` necessarily takes as input has no field to travel through to reach a response.
- **HTTP-level** (`RateLimitIT.aggregateTrafficIsThrottledAfterT136a`): the real throttled response body
  is asserted not to contain the real, live caller's own creator id.

The aggregate tier's own name (`per-creator-aggregate`) legitimately contains the word "creator" — unlike
the per-code tier, which never learns one at all and so is held to the stricter bar of never even naming
one in its tier name (`RateLimiterTest.redirectTierCannotIdentifyTheCreator`). Naming the DIMENSION a
throttle rule protects is what FR-URL-016 (clause 1f) requires for every tier, including the existing,
already-shipped, already-authenticated `CreationRateLimiter.TIER` (`"per-creator-creation"`), which already
names the same dimension without controversy. What FR-URL-016's clause 1g forbids is disclosing a fact
ABOUT a specific creator (identity, exact traffic, popularity) — a different, narrower bar, which both
non-disclosure tests above hold this tier to directly.

## Latency and failure posture — not separately re-measured this turn

T136a's own `Validate` clause additionally calls for redirect latency to be re-measured against PVT-001
with the ownership lookup in the hot path, and for the declared (in-process) failure posture to be
exercised with the counter store down. Neither is captured in this file: PVT-001-class performance
measurement is the same T145a–d deferral already disclosed in `docs/LIMITATIONS.md` (compressed-time,
single-host demonstration scope), and the in-process counter has no external store to take down (the
security gate itself settled this — `docs/governance/gate-decisions/ds-b/t136a-security-gate-approved.md`,
item 2 — making the fail-open/fail-closed question moot for this implementation, not a case needing a
fault-injection test). Stated here rather than silently omitted.
