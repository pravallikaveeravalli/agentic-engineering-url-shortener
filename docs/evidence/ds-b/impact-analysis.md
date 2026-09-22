# DS-B brownfield impact analysis — the per-creator aggregate redirect tier (T135)

**Analysis timestamp**: 2026-09-22T23:35:00Z (UTC), preceding any code modification. At this timestamp, no
commit exists touching `src/main/java/agentic/shortener/delivery/ratelimit/AggregateRedirectLimiter.java` (it
does not exist), and `git log -- src/main/java/agentic/shortener/delivery/ratelimit/` shows no commit after
this analysis's own commit touches that directory. This ordering is independently checkable by any reviewer:
compare this file's own commit timestamp against the first commit that creates or modifies
`AggregateRedirectLimiter.java`. **This document is the analysis itself — written before the change, not
reconstructed after it** (T135's own Guard clause).

## Subject

FR-URL-016's third, deliberately deferred rate-limit tier: redirect traffic limited **per creator,
aggregated across all of that creator's links** — PVT-014, 3,000 requests/minute — enforced independently of
the existing per-code tier (PVT-013, 600 requests/minute/code). The deferral is recorded in
`docs/delivery/baseline-omissions.md`'s Entry 1: two of three tiers are built (PVT-012 creation, PVT-013
per-code redirect); the third is explicitly named as "no configuration key... and no partial implementation,"
closed only by this brownfield run (T136a). Absent that closing run, release readiness must report
FR-URL-016 as **NOT MET** — not an accepted risk, since no authority has accepted it.

---

## 1. Impacted components

- **`RedirectController`** (`src/main/java/agentic/shortener/delivery/RedirectController.java`) — the
  redirect hot path itself. Currently: throttle-check (per-code) → store lookup → analytics record. A third
  check must be inserted somewhere in this sequence (see §2, ordering).
- **A new class, `AggregateRedirectLimiter`** (does not yet exist) — the natural home for the new tier,
  siblings to the existing `CreationRateLimiter`/`RedirectRateLimiter`, most likely wrapping a second
  `FixedWindowCounter` instance keyed by creator id rather than short code.
- **`PersistenceConfiguration`** (`src/main/java/agentic/shortener/config/PersistenceConfiguration.java`,
  lines 156–167) — where `CreationRateLimiter`/`RedirectRateLimiter` are currently constructed as `@Bean`s
  from `@Value`-injected `application.yml` properties; a third bean and a third property
  (`shortener.ratelimit.aggregate-per-creator-per-minute` or similarly named) would be added here.
- **`application.yml`** — currently has no aggregate-tier key at all (verified: `RateLimiterTest`'s own
  `throttlingIsOnByDefault` test asserts this key is *absent*, as the documented baseline-omission marker).
- **NOT impacted**: `CreationRateLimiter`, the creation path, `LinkController`, and — critically — the
  existing `RedirectRateLimiter` class itself, which is not modified; the two tiers stay structurally
  independent, each enforced separately, per FR-URL-016's own text.

## 2. Impacted interfaces — the hot-path ownership lookup (substantive point 1 of 6)

The redirect path is **public and anonymous by requirement** (FR-URL-018; ADR-013 confirms explicitly: "the
redirect path does not touch the authentication filter at all"). A per-*creator* tier therefore requires
resolving `short code → owning creator` **inside the hot path**, with no caller-supplied identity to read it
from.

`ShortLink` already carries `creatorId` as a field, populated at construction/rehydration
(`domain/link/ShortLink.java`), and the existing single repository lookup
(`ResolveLinkUseCase`/`ShortLinkRepository.findByShortCode`, already on the hot path for the per-code check
and the redirect itself) already returns the full `ShortLink`, including `creatorId` — **so no additional
database round-trip is structurally required** to learn the creator. This is the good news the impact
analysis surfaces, not a design decision it makes.

**The real interface consequence**: today, `RedirectController`'s own comment states throttling runs
*deliberately before* the store lookup ("a limiter that ran after the lookup would still do the work the
limit exists to prevent") — but the aggregate tier's key (the creator id) is not known until *after* that
same lookup. The per-code tier can and must stay pre-lookup (it protects exactly the lookup the comment
describes); the aggregate tier cannot start there and must be evaluated *after* the lookup succeeds, before
the redirect response is actually returned. This is a genuine, real ordering change to `RedirectController`'s
own sequence — a new post-lookup, pre-response checkpoint — not a drop-in call alongside the existing one.

## 3. Impacted data flows — latency (substantive point 2 of 6) and failure posture (substantive point 3 of 6)

**Latency**: the added lookup sits inside PVT-001's own redirect budget (p95 ≤ 50ms, single instance, warm,
local persistence, PVT-003 concurrency). Because the creator id is already available from the existing
lookup (§2), the *aggregate limiter's own* added cost is expected to be small — one more `FixedWindowCounter`
map operation, the same O(1) shape as the existing per-code check — but "expected small" is exactly the
claim T136a's own Validate clause requires to be **measured, not assumed**, against a real PVT-001 benchmark
with the new check actually in place, not estimated here.

**Limiter failure posture**: the existing counter (`FixedWindowCounter`) is in-process, in-memory — there is
no external "counter store" to become unavailable in the current per-code/per-creator-creation
implementation, and nothing in this analysis assumes the aggregate tier will introduce one. If a future
design decision changes that (e.g., a shared/distributed counter for multi-instance correctness — `
FixedWindowCounter`'s own javadoc already discloses the current in-process, per-instance limitation), the
open/closed failure-mode choice becomes a real, undecided question with a real security consequence (fail
open = the tier silently stops protecting; fail closed = a store outage throttles all redirects). **This
analysis does not resolve that question** — it names it as exactly the kind of decision T136a's own security
gate exists to require the human owner to make explicitly, not by silent default.

## 4. Impacted tests — regression surface (substantive point 5 of 6)

- **`RateLimiterTest.java`** (`src/test/java/agentic/shortener/delivery/ratelimit/`) — the direct regression
  surface. Currently asserts: creation limit independence, redirect per-code limit and independence,
  `redirectTierCannotIdentifyTheCreator` (a reflection-based non-disclosure proof for the *existing* tier),
  `theDeferredTierIsDisclosed`, `theAggregateTierIsNotPartiallyPresent` (asserts no `PVT_014`/
  `aggregatePerCreator` symbol exists **today** — this specific assertion will need to be retired or
  inverted once the tier is real, a planned, expected test change, not an accidental break), and
  `throttlingIsOnByDefault` (asserts the config key is absent today — same status).
- **`RateLimitIT.java`** — the before-state proof: `aggregateTrafficPassesUnthrottled` sends 12 requests
  spread across 4 links, each individually under PVT-013, and asserts all 12 pass unthrottled **today**.
  This test's own comment already states a future failure here is expected and desired once T136a lands —
  it is the literal before/after pivot T136/T137 exist to capture. `theDisclosureExistsBeforeTheSweep` and
  `throttledFollowerLearnsNothingAboutTheCreator` must continue to pass unmodified — the new tier must not
  weaken the existing disclosure guarantee for the *per-code* tier's own refusal, and must independently
  supply the same non-disclosure guarantee for its own refusal (§5).
- **`BaselineOmissionsTest.java`** (`src/test/java/agentic/shortener/delivery/`) — asserts every
  baseline-omissions register entry names a closing run; Entry 1's own closing-run reference (T136a) should
  remain accurate and, once T136a lands for real, the register entry itself moves from open to closed.
- **Unaffected**: every test under `src/test/java/agentic/shortener/delivery/` NOT touching rate limiting —
  creation, analytics, redirect-outcome recording — since this change touches no shared code path outside
  the rate-limit module and the one new post-lookup checkpoint in `RedirectController`.

## 5. Non-disclosure (substantive point 4 of 6)

FR-URL-016's own negative criterion: a throttled response must name **which tier** was exceeded (existing
`RateLimitDecision.tier` field already supports this — the per-code tier already returns `"per-code"`) but
**must not disclose the owning creator** to a public, anonymous follower. The existing per-code tier already
proves this is achievable (`redirectTierCannotIdentifyTheCreator`'s own reflection-based test). The aggregate
tier's own refusal must be held to the identical bar: a caller throttled by the aggregate tier learns "you
are being rate-limited" and, per FR-URL-016, which *tier* (e.g. `"per-creator-aggregate"`), but nothing that
would let them infer which creator owns the link, how popular that creator is, or any other creator-scoped
fact. This is a structural design constraint on the new class's own response shape, not an afterthought
check.

## 6. Documentation and threat-model update (substantive point 6 of 6)

- `docs/delivery/baseline-omissions.md`'s Entry 1 is the record this run closes — its own text must be
  updated (not by this analysis, but by T136a's own commit) to reflect PVT-014 as delivered, with the closing
  run's evidence referenced.
- `research.md`'s threat model, **T-08 (noisy neighbor)**: "many links each under the per-code limit
  collectively soak capacity... Accepted trade-off: followers of a popular creator may be throttled through
  no fault of their own. Deliberate: service protection over unlimited per-tenant availability." This
  accepted trade-off already exists in the threat model, written in anticipation of exactly this tier — no
  new threat-model *entry* is needed, but T-08's own status should be updated from "mitigation deferred" to
  "mitigation delivered" once T136a lands, and NFR-SEC-003's own documentation obligation is what requires
  this update to actually happen, not merely be possible.
- `plan.md` §8 (Security) and §11 (DS-B's own scenario design) already describe this tier in detail (the
  six substantive points enumerated here are drawn directly from §11's own text) — no plan revision is
  needed; the plan already anticipated this analysis's own content.

## 7. Rollout / rollback

This codebase has **no boolean feature-flag mechanism** — ADR-004's own Amendment 02/CR-028 record is cited
in `application.yml` as having deliberately *removed* a prior mode-flag pattern ("There is NO mode flag...
the former `ai: off` default is gone with the flag itself"), a precedent against introducing one here.
The established pattern instead is **externalized numeric configuration via `@Value`/`application.yml`**,
and — more specifically for an undeployed tier — **config-key presence as the enablement signal itself**:
today, the aggregate tier's absence *is* its own "off" state (no key, no bean, no enforcement), proven by
`throttlingIsOnByDefault`'s own assertion that the key is missing. Once implemented, "rollback" in this
codebase's own established idiom means: revert the commit that adds the bean/property (restoring the
config-key-absent state), not flip a runtime toggle — consistent with how the two existing tiers are already
deployed and would themselves be rolled back. This is disclosed here as the real, existing rollback
mechanism, not proposed as new design.

---

## Summary — the six substantive points, located

| # | Point | Where addressed above |
|---|---|---|
| 1 | Hot-path ownership lookup | §2 — no new DB round-trip required, but a real ordering change to `RedirectController` (post-lookup, not pre-lookup like the per-code tier) |
| 2 | Latency against PVT-001 | §3 — expected small, must be measured by T136a, not assumed here |
| 3 | Limiter failure posture | §3 — no external store exists today; the open/closed question is named, not resolved, for T136a's own security gate |
| 4 | Non-disclosure of the creator | §5 — held to the same bar the existing per-code tier already proves achievable |
| 5 | Per-code regression surface | §4 — `RateLimiterTest`/`RateLimitIT`'s exact tests named, including which specific assertions are EXPECTED to flip once the tier is real |
| 6 | Threat-model update | §6 — T-08/NFR-SEC-003 already anticipate this tier; status update owed once T136a lands |

**This analysis makes no code change and resolves no open design question** (the failure-posture question in
particular is explicitly left to T136a's own security-sensitive owner gate). It is the seven-dimension record
plan §11 and T135's own Artifact field require, written before any implementation, so that T136a's own
before/after evidence can be measured against a real, dated prior understanding rather than a claim made
after the fact.
