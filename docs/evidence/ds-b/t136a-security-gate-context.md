# T136a — security-sensitive gate context for the owner

**This is preparation only — no code has been written, and nothing here is a request buried in other work.**
T136a (`AggregateRedirectLimiter`, the per-creator aggregate redirect tier) requires the owner's own
security-sensitive approval before any implementation begins (`tasks.md`'s own class: "security-sensitive —
an abuse control with a disclosure criterion"). This document exists so that approval can be given quickly,
with everything relevant in one place, rather than reconstructed at decision time.

## What the change is, plainly

FR-URL-016 defines rate limiting on two sides. The creation side and one of the two redirect-side tiers are
already built and delivered. The **third tier — deliberately deferred at baseline, disclosed in
`docs/delivery/baseline-omissions.md`** — limits a creator's redirect traffic **in aggregate, across every
link they own**, at 3,000 requests/minute (PVT-014), independent of the existing per-code limit (600
requests/minute/code). It exists to stop one creator's popular links, collectively, from soaking capacity
that other creators' traffic needs — the "noisy neighbour" problem, already named in the threat model (T-08).

**Why it needs a security-sensitive gate at all**: the redirect path is public and anonymous by requirement
(FR-URL-018) — no follower ever authenticates. To limit traffic *per creator*, the system has to resolve
`short code → owning creator` on every redirect, inside the hot path, for a caller who never identified
themselves as anyone. That is a real design decision with a real security dimension: what the system does
with that ownership fact, and what it reveals, matters.

## The security-relevant trade-off already accepted, named here for the record

FR-URL-016's own text, and the threat model's T-08 entry, already state and accept this trade-off:
**followers of a popular creator's links may be throttled through no fault of their own** — a deliberate
choice (service protection over unlimited per-tenant availability), not a side effect discovered later. This
is not new information; it is restated here because it is the actual security posture this gate approves,
not a hypothetical.

## The one open question this gate genuinely needs to decide: limiter failure posture

Today's two existing tiers use an **in-process, in-memory counter** (`FixedWindowCounter`) — there is no
external store to fail. If the aggregate tier is built the same way, this question does not arise for this
implementation. If a future design instead uses a shared or distributed counter (for multi-instance
correctness), a real, undecided question exists:

- **Fail open** (serve the redirect, unlimited, if the counter is unavailable): the tier silently stops
  protecting during an outage — noisy-neighbour traffic passes through exactly when the protection matters
  most.
- **Fail closed** (refuse/throttle redirects if the counter is unavailable): a counter-store outage becomes a
  redirect-path outage for everyone, even creators whose traffic was never near the limit — arguably worse,
  since the redirect path is the product's own critical path (PVT-001).

**This document does not recommend an answer.** `docs/evidence/ds-b/impact-analysis.md` (T135) names this
question explicitly as one this gate — not the impact analysis — must decide, and states plainly that
in-process counters (the pattern already used, and the one this analysis expects T136a to follow unless the
owner directs otherwise) make the question moot for a single-instance deployment. If the owner intends the
in-process pattern to continue, that itself is worth saying explicitly at the gate, so it is a decision on
the record rather than a default nobody chose.

## What non-disclosure requires, concretely

The existing per-code tier already proves the achievable bar (`redirectTierCannotIdentifyTheCreator`, a
reflection-based test proving the class structurally cannot expose a creator identity). The aggregate tier's
own refusal must meet the identical bar: a throttled caller learns *that* they are rate-limited and *which
tier* (FR-URL-016's own requirement — e.g. a `"per-creator-aggregate"` tier name in the response, mirroring
the existing `"per-code"` naming), and nothing else — never the creator's identity, popularity, or any other
creator-scoped fact.

## Exactly what the owner is being asked to approve

1. **Proceed with `AggregateRedirectLimiter`** as scoped in `docs/evidence/ds-b/impact-analysis.md` (T135):
   a new class alongside the existing two limiters, a new post-lookup checkpoint in `RedirectController`
   (not pre-lookup like the per-code tier, since the creator id is not known until after the existing store
   lookup), using the same in-process `FixedWindowCounter` pattern as the existing tiers unless directed
   otherwise.
2. **Confirm (or redirect) the failure-posture default**: in-process counter, no external store, therefore no
   fail-open/fail-closed question for this specific implementation — or direct a different design if a
   shared/distributed counter is actually wanted, in which case the open/closed choice above needs an
   explicit answer.
3. **Confirm the accepted trade-off stands**: followers of a popular creator may be throttled through no
   fault of their own (already stated in FR-URL-016 and T-08; restated here for the gate record, not newly
   proposed).
4. **Confirm the non-disclosure bar**: the aggregate tier's refusal must be as unable to identify the creator
   as the existing per-code tier already is, proven the same way (a structural, reflection-based test, not a
   convention).

## What happens after approval

T136a's own `Validate` clause (`tasks.md`) becomes executable: before-state (today's multi-link traffic
passing unthrottled — already proven live by `RateLimitIT.aggregateTrafficPassesUnthrottled`), after-state
(the same traffic throttled, the response naming the tier, not the creator), the per-code and per-creator-
creation tiers proven unregressed, redirect latency re-measured against PVT-001 with the ownership lookup in
place, and the declared failure posture exercised. T136's own retry/compensation evidence
(`docs/evidence/ds-b/t136-status.md`) becomes capturable inside that same run once the implementation exists.
