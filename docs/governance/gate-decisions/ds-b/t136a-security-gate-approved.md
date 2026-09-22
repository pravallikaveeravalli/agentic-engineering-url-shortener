# T136a — brownfield security-sensitive gate decision (per-creator aggregate redirect tier)

**Scope note**: a **product-internal**, security-sensitive change-gate decision, never this repository's own
numbered SpecKit lifecycle gates.

| Field | Value |
|---|---|
| **Gate** | T136a — brownfield governed implementation of the per-creator aggregate redirect tier (`AggregateRedirectLimiter`), FR-URL-016/PVT-014 |
| **Outcome** | `APPROVED`, all four items |
| **Deciding human** | Pravallika Veeravalli |
| **Decision date** | 2026-09-22 |
| **Artifact approved** | The scope and design stated in `docs/evidence/ds-b/impact-analysis.md` (T135) and restated concretely in `docs/evidence/ds-b/t136a-security-gate-context.md` |
| **Where else recorded** | `docs/delivery/baseline-omissions.md` Entry 1 (the deferral this decision closes, once the build lands) |

## The four items approved

1. **Build `AggregateRedirectLimiter` as scoped**: a new class alongside `CreationRateLimiter`/
   `RedirectRateLimiter`, using the same in-process `FixedWindowCounter` pattern; a new post-lookup
   checkpoint in `RedirectController` (not pre-lookup like the per-code tier, since the creator id is not
   known until after the existing store lookup already on the hot path).
2. **In-process failure posture confirmed**: no external counter store is introduced; the fail-open/fail-
   closed question `docs/evidence/ds-b/t136a-security-gate-context.md` named is moot for this implementation,
   by the owner's own confirmation — not a default nobody chose, but a decision made explicitly at this gate.
3. **The accepted throttling trade-off stands**: followers of a popular creator's links may be throttled
   through no fault of their own — already stated in FR-URL-016 and the threat model's T-08 entry, restated
   and reconfirmed here, not newly proposed.
4. **The non-disclosure bar holds**: the aggregate tier's own refusal must be structurally unable to reveal
   the owning creator, proven the same way the existing per-code tier already proves it (a reflection-based
   test, not a documentation convention).

## Reason / verification performed

The owner reviewed `docs/evidence/ds-b/t136a-security-gate-context.md` — the change, the already-accepted
trade-off, the one genuinely open question (failure posture) and its answer, and the concrete non-disclosure
bar — and approved all four items as presented. This session performed no independent security review beyond
what was already disclosed at the gate; the decision is the owner's own.

## Conditions attached to the approval

1. **The build itself is a separate, later turn** — this decision authorizes it; it does not constitute the
   implementation. `AggregateRedirectLimiter` does not exist yet as of this record.
2. **T136a's own `Validate` clause governs the build**: before-state (already proven live,
   `RateLimitIT.aggregateTrafficPassesUnthrottled`), after-state (the same traffic throttled, response naming
   the tier, not the creator), the per-code and per-creator-creation tiers proven unregressed, redirect
   latency re-measured against PVT-001 with the ownership lookup in place, the declared (in-process) failure
   posture exercised.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| The build must follow the post-lookup checkpoint ordering this decision approves — a pre-lookup implementation would not have the creator id available and was already ruled out by the impact analysis | T136a's own implementation turn |
| `docs/delivery/baseline-omissions.md` Entry 1 is updated to closed only once the real build and its evidence exist, not by this approval alone | T136a's own implementation turn |
