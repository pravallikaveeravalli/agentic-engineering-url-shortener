# DS-A — Greenfield run design

Task T131 (revised, CR-053). Req: DS-A, FR-ORC-010. Scn: DS-A. ADR: ADR-004.

**Read this first if you're asking "why did this take so many attempts?"**:
`docs/evidence/ds-a/requirement-completeness-ceiling-finding.md`'s own headline section states the answer
plainly, in five points, before any of the detail below.

## Current subject (CR-053) — a zero-runtime-behaviour test-addition

**The `GET /v1/version` endpoint is retired from DS-A's clean-pass demonstration role.** Ten-plus live
attempts against it (CR-051, CR-052 — kept below as the "Retired subject" section, evidence intact, nothing
deleted), across three requirement revisions, each genuinely and honestly reached a real gate (S4 on a real
finding, or S6 once the owner's own ratified clarifications resolved S4). The pattern across all of them —
now the headline of `docs/evidence/ds-a/requirement-completeness-ceiling-finding.md` — is that a real,
non-deterministic AI detector, even sharpened to gate only on a genuine behavioural fork (CR-052), keeps
finding *some* new material-looking item on almost any requirement that describes runtime behaviour, because
behaviour, however minimal, still has edges.

**The owner's own insight, acted on here**: choose a greenfield change with **no runtime behaviour at all**.
A unit test verifies behaviour that already exists in already-delivered code — it introduces or changes
nothing the system does, so there is structurally no behavioural fork for CR-052's own predicate to ever find
material.

> Add unit tests for `FixedWindowCounter`
> (`src/main/java/agentic/shortener/delivery/ratelimit/FixedWindowCounter.java`), a package-private per-key
> fixed-window rate counter, covering its existing, already-defined behaviour across its public surface: the
> constructor's positive-limit validation (throws `IllegalArgumentException` for a non-positive limit, per
> its own existing message); the `check(key, tier)` decision (returns an allowed `RateLimitDecision` while a
> key's count within the current one-minute window is at or below the configured limit, and a throttled
> `RateLimitDecision` once the count exceeds it, with `retryAfterSeconds` computed from the remaining time in
> the window and floored at one second); window rollover (a key's window resets to a fresh count of one once
> the prior window's one-minute duration has elapsed); and the pruning behaviour (an entry whose window has
> already expired is removed once the map's size exceeds the existing 10,000-entry threshold). No
> production-code change; this adds test coverage only, using an injected `Clock` to control time
> deterministically, matching this codebase's own existing pattern for testing time-dependent logic.

**Verified as a genuine gap, not a contrived one** (full detail: `docs/governance/change-control/CR-053-...md`):
no `FixedWindowCounterTest`/`IT` exists; no test anywhere constructs the class directly by name. It is
exercised only indirectly, as a private implementation detail, through `RateLimiterTest`'s own tests of its
two callers (`CreationRateLimiter`, `RedirectRateLimiter`) — which incidentally cover the constructor guard
and one retry-after value, but never the class's own direct contract, and never its pruning behaviour at all
(no existing test constructs anywhere near 10,000 distinct keys).

### Well-formedness against the four criteria

1. **Complete.** Every dimension the requirement asks for — which methods, which behaviours, which inputs —
   is closed by pointing at code that already, fully determines the answer; nothing is left for a test author
   to invent or guess.
2. **Consistent.** No conflict with any existing test or approved artifact — a pure test-coverage addition,
   touching no production code.
3. **Testable**, trivially — a test either exercises the stated behaviour correctly or it does not; there is
   no accept/reject ambiguity since nothing about the production class's own behaviour is being specified or
   changed.
4. **Inside approved policy and architecture boundaries.** No new technology, no architectural surface at
   all — test-only, in the same package the class already lives in, matching this codebase's own established
   convention for package-private classes.

Per `spec.md`'s DS-A section, this input MUST proceed through
S1→S2→S3→**S4 SKIPPED**→S5→S6→S7→S8→S9‖S10→S11→S12 with no clarification gate firing.

---

## Retired subject (`GET /v1/version`, CR-051/CR-052) — a genuinely minimal requirement, behavioural surface only

**The expiry endpoint is retired from DS-A's clean-pass demonstration role.** Five live attempts against it
(CR-048, CR-049, CR-050 — kept below as the "Retired subject" section, evidence intact, nothing deleted)
established a real, principled finding: for a feature with genuine surface, S3's own required completeness
bar (FR-ORC-010) is not reliably reachable in a small, fixed number of wording revisions, because S3 has no
repository access and can only treat a fact as already-settled if the requirement text states it directly —
see `docs/evidence/ds-a/requirement-completeness-ceiling-finding.md` for the full analysis, and CR-051 for
the owner's decision. DS-A's own defining property — a *complete* requirement proceeds without an artificial
gate — is demonstrated instead on a feature chosen honestly for a small enough surface that completeness is
actually achievable, not tuned to any specific past S3 finding.

CR-051's own first minimal wording (below, superseded) added a closing "no failure mode by design" clause,
intended to close the "what does an unhealthy response look like?" question. Live evidence
(`docs/evidence/ds-a/pending-gate-s4-context-v1-version.md`, attempt 6) showed that clause backfired: three
of four `MATERIAL_PENDING` findings traced to it — an undefined term ("reachable"), a self-referential
critique of its own scoping, and an unbounded-quantifier critique of its own "for every possible input,
state, and code path" phrasing — none of which changed any actual required, buildable behaviour, since the
requirement's other eight clauses already state the endpoint's entire behaviour unconditionally. That result
was the evidence CR-052 (`docs/governance/change-control/CR-052-...md`) used to sharpen S3's own materiality
predicate to CR-007's real bar — a genuine behavioural fork, not linguistic imperfection — and, per the
owner's own instruction, to simplify this requirement by removing the non-behavioural clause that invited
those three findings in the first place, rather than trying to out-word a detector that was, on its own
terms, over-firing.

> Add a public `GET /v1/version` endpoint that requires no authentication, accepts no path or query
> parameters, and always returns HTTP `200` with `Content-Type: application/json`, header `Cache-Control:
> no-store`, and body exactly `{"version": "0.1.0-SNAPSHOT"}`. The version string is a fixed literal encoded
> directly in the endpoint's own implementation — it is never computed, never read from a build manifest,
> never derived from git or environment state, and never changes without a deliberate code edit to this
> endpoint itself. The endpoint performs no dependency or downstream checks and reads and writes no
> persisted data.

**Verified against real source before adopting** (unchanged from CR-051, restated here): the owner's first
proposal (`GET /v1/health`, a public liveness endpoint) was checked against the delivered codebase and found
to duplicate an already-existing, already-approved feature — `HealthController.live()` already serves
`GET /health/live` (T032, FR-URL-015, ADR-012). Adding a second, differently-named liveness endpoint would
itself be a Criterion-2 (Consistent) violation. `GET /v1/version` was verified clean instead: no existing
route under `/v1/version` or any `/v1/` prefix collision, no existing version/build-info endpoint anywhere in
the codebase, and `CreatorAuthFilter`'s registered patterns do not cover it — unauthenticated by default, no
filter change needed.

### Well-formedness against the four criteria — genuinely, not asserted

1. **Complete.** Every dimension a response could vary on is closed: status code, content type, cache
   header, and body are all stated exactly; the version string's own provenance (fixed literal, never
   computed) is stated so no reader could imagine it varies by build; auth (none) and inputs (none) are each
   closed. No closing "no failure mode" clause is needed to state this — it followed already, from the other
   clauses being unconditional — and CR-052's own finding is that adding one only introduced surface for a
   detector to (over-)scrutinize without adding any obligation.
2. **Consistent.** No conflict with `HealthController`'s existing liveness/readiness pair (FR-URL-015,
   ADR-012) — a version identifier is a distinct concept from process liveness or store readiness, and this
   requirement does not restate, alter, or compete with either existing probe. No conflict with the two-plane
   architecture (ADR-006) — a static, stateless application-plane read.
3. **Testable.** A single, unconditional accept criterion: any `GET /v1/version` request, with or without
   credentials, with or without extra query parameters, MUST return exactly `200`,
   `Content-Type: application/json`, `Cache-Control: no-store`, body `{"version": "0.1.0-SNAPSHOT"}` — no
   reject/refusal criterion exists because none is specified, by design.
4. **Inside approved policy and architecture boundaries.** No new technology, no new persistence concern, no
   new authentication mechanism (explicitly none required); a pure application-plane addition exercising no
   orchestration-plane dependency, exactly like the retired subject's own boundary analysis below.

Per `spec.md`'s DS-A section, this input MUST proceed through
S1→S2→S3→**S4 SKIPPED**→S5→S6→S7→S8→S9‖S10→S11→S12 with no clarification gate firing; the live run (T132)
records the quality checks performed and the explicit no-clarification reason as its own evidence of this.

### Prior wording (CR-051, superseded by CR-052 above — kept as evidence)

> Add a public `GET /v1/version` endpoint that requires no authentication, accepts no path or query
> parameters, and always returns HTTP `200` with `Content-Type: application/json`, header `Cache-Control:
> no-store`, and body exactly `{"version": "0.1.0-SNAPSHOT"}`. The version string is a fixed literal encoded
> directly in the endpoint's own implementation — it is never computed, never read from a build manifest,
> never derived from git or environment state, and never changes without a deliberate code edit to this
> endpoint itself. The endpoint performs no dependency or downstream checks, reads and writes no persisted
> data, and has no failure mode by design: process-reachable is the only condition it reports, and there is
> no input, state, or code path by which it could ever return anything other than this exact response.

Attempt 6 against this wording is preserved at
`docs/evidence/ds-a/run-snapshot-ATTEMPT-6-STOPPED-AT-S4-minimal-v1-version-subject.md` and
`docs/evidence/ds-a/pending-gate-s4-context-v1-version.md`.

---

## Retired subject (expiry endpoint, CR-048/049/050) — kept as evidence, no longer DS-A's live subject

The sections below document the expiry-endpoint arc that led to CR-051's decision. Preserved in full,
unmodified, as the primary evidence for
`docs/evidence/ds-a/requirement-completeness-ceiling-finding.md`'s conclusion — not DS-A's current
demonstration subject.

## Revision history

**The original wording tested genuinely ambiguous** — kept as real evidence, not deleted. The first
requirement proposed here, *"Expose the remaining time-to-expiry for a short link to its owning creator,"*
was verified in this document against the four criteria and judged well-formed as a *concept*. It was not
well-formed as a *specification*: driven live through T132 twice, it produced material-ambiguity findings
from **two independent models** —

- Gemini (`gemini-3.8-flash-high`), reproduced directly outside the pipeline:
  `docs/evidence/ds-a/run-snapshot-ATTEMPT-1-BLOCKED-gemini-quota-exhausted.md`.
- Claude (`claude-sonnet-5`), live through the real `Conductor`, run suspended at S4's own
  `UNRESOLVED_AMBIGUITY` gate exactly as designed: `docs/evidence/ds-a/pending-gate-s4-context.md`,
  `docs/evidence/ds-a/run-snapshot-ATTEMPT-2-STOPPED-AT-S4-ambiguity-gate.md`.

Both named the same handful of real gaps: no stated unit/format for the value, undefined behavior for a
non-expiring link, undefined delivery medium, no stated bounds, no stated non-owner/anonymous behavior, and
an implicit "owning creator" identity never tied to the system's actual authentication model. The owner's
decision, having reviewed both findings: **tighten the wording to pre-answer every one of those points**
rather than route around the finding — this is what a real requirements author does in response to genuine
ambiguity-detection feedback, not a rigged shortcut (T131/T132's own Guard clauses forbid choosing a
trivially easy substitute instead). This document, and the requirement below, are that revision.

**CR-048's revision itself then genuinely reached S4 twice more** (attempts 3 and 4,
`docs/evidence/ds-a/run-snapshot-ATTEMPT-3-STOPPED-AT-S4-ambiguity-gate-revised-wording.md` and
`...-ATTEMPT-4-STOPPED-AT-S4-post-calibration.md`), each time on different, genuine findings — attempt 3
surfaced five new gaps (the expired-link `expiresAt` value, the boundary instant, rounding, an unbounded
401 cross-reference, a creator/owner predicate asymmetry); attempt 4, run after CR-049 calibrated S3 to
CR-007's materiality predicate, confirmed the calibration works (two genuine `NOT_MATERIAL` resolutions,
cross-checked by a non-negotiable live DS-C compensating check) while still finding four open items —
rounding, the boundary instant, link retention after expiry, and malformed-`{code}` handling — full detail
in `docs/evidence/ds-a/pending-gate-s4-context.md`. The owner resolved all four on the record: floored
rounding as a genuine choice, the other three as verified restatements of the delivered system's own
existing behavior. **CR-050 folds all four in and additionally states, directly in the requirement's own
text, every fact S3 has no way to discover on its own** (S3 reasons only over the requirement text, never
the repository — see `pending-gate-s4-context.md`'s structural finding). The wording immediately below is
CR-050's; CR-048's superseded wording is kept beneath it as evidence.

## The input (revised, CR-050 — current)

> Add a read-only endpoint `GET /v1/links/{code}/expiry` that returns, to the authenticated creator who owns
> `{code}` and to no one else, a JSON body `{"code": <string>, "expiresAt": <ISO-8601 UTC timestamp> | null,
> "secondsRemaining": <integer ≥ 0> | null}`.
>
> For a link with no configured expiration, both `expiresAt` and `secondsRemaining` are `null`.
>
> For a link with a configured expiration, the link is expired at and after the exact instant `expiresAt` is
> reached — the boundary instant itself counts as expired, not as still remaining — matching this system's
> own existing expiry rule (`ExpiryPolicy`). While not yet expired, `secondsRemaining` is the count of whole
> seconds between the current time and `expiresAt`, rounded down (floored) — chosen deliberately so this
> value never overstates the time actually left. Once expired, `secondsRemaining` is `0`, never negative, and
> `expiresAt` continues to report the link's original, unaltered stored expiration timestamp — this system
> never edits or clears a link's stored `expiresAt` once set.
>
> This system never deletes or purges a short link for any reason, including having expired; an unwanted
> link is only ever transitioned to an expired state, per this system's own existing compensation policy
> (never delete, only mark expired). Accordingly, an expired link's expiry information remains queryable
> through this endpoint indefinitely, always in the expired shape described above, and never produces a
> not-found response on account of having expired.
>
> The "owning creator" is the single creator identity already recorded as this link's owner at the time it
> was created; this system has no concept of transferring or sharing ownership, so "the creator" and "the
> owner" always name the same principal for any given link.
>
> An unauthenticated caller receives the same `401` refusal every other authenticated endpoint in this
> system already produces. Every other caller who is not the owning creator — an authenticated creator who
> owns a different link, a caller presenting a code that was never issued, or a caller presenting a
> syntactically malformed code (wrong length or characters outside the code alphabet) — receives the
> identical `404` response already used for an unknown code, so that link existence, ownership, and validity
> are never distinguishable from one another to anyone but the owner.

**Revision provenance**: this wording is CR-050, filed after attempt 4 (`docs/evidence/ds-a/run-snapshot-ATTEMPT-4-STOPPED-AT-S4-post-calibration.md`,
`pending-gate-s4-context.md`) surfaced four open items the owner then resolved on the record — one genuine
choice (floored rounding — "never over-report the time left"), three verified restatements of behavior the
delivered system already has (the boundary instant per `ExpiryPolicy`; never-purged per `spec.md`'s EX-003
and Compensation Register; malformed-code handling per `LinkController`'s unconstrained analytics-family
route producing the same lookup-miss `404` as an unissued code). See CR-050 for the verification evidence
and the full reconciliation. The CR-048 wording immediately below is superseded but kept as evidence.

### The input (CR-048, superseded by CR-050 above — kept as evidence)

> Add a read-only endpoint `GET /v1/links/{code}/expiry` that returns, to the authenticated creator that
> owns `{code}` and to no one else, a JSON body `{"code": <string>, "expiresAt": <ISO-8601 UTC timestamp> |
> null, "secondsRemaining": <integer ≥ 0> | null}`. For a non-expiring link both `expiresAt` and
> `secondsRemaining` are `null`; for an already-expired link `secondsRemaining` is `0`. An unauthenticated
> caller receives the same `401` refusal every authenticated endpoint already uses; an authenticated caller
> who does not own `{code}` receives the same `404` response used for an unknown code.

**One refinement against the owner's own proposed wording, made and disclosed rather than applied
silently**: her draft said "a non-owning or anonymous caller receives the same 404... used for an unknown
code" — a single refusal for both cases. The real, already-delivered system does not do that: `LinkController`'s
existing analytics endpoint (`GET /v1/links/{shortCode}/analytics`, T051) returns `401` for an
unauthenticated caller (`CreatorAuthFilter` refuses it by routing, before any handler runs) and a
**separate**, non-disclosing `404` only for an *authenticated* caller who does not own the code. Matching
the requirement to the existing, delivered pattern exactly — rather than inventing a new one that happens to
be simpler — is what Criterion 2 (Consistent) below actually requires.

This document is the check plan.md's own DS-A entry calls for before the run: verifying the input against
the four well-formedness criteria spec.md's DS-A section states (`spec.md:1086`) — **complete, consistent,
testable, and inside approved policy and architecture boundaries** — genuinely, against the real system as
it exists today, not asserted. T131's own Guard clause is explicit that a trivially easy input chosen to
guarantee a clean run would be a rigged demonstration; the check below is written so a reader can verify it
was not — every real gap the ambiguity detector found, across four attempts, is answered by name.

## 1. Complete

Every element needed to build this without further guessing is already present in the delivered system, and
every point the ambiguity detector flagged — across all four attempts, CR-048's six and CR-050's additional
four — is now pre-answered directly in the requirement's own text:

| Gap found | Resolved by this wording |
|---|---|
| No unit/format for the value | `secondsRemaining` is explicitly an integer count of whole seconds; `expiresAt` is explicitly ISO-8601 UTC |
| Non-expiring link undefined | Both fields explicitly `null` |
| Already-expired link's `secondsRemaining` undefined | Explicitly `0`, never negative |
| Already-expired link's `expiresAt` undefined (attempt 3 finding) | Explicitly stated: continues to report the original, unaltered stored timestamp |
| The `now == expiresAt` boundary instant unassigned (attempts 3 and 4) | Explicitly stated in the text itself as expired — not left as a cross-reference to `ExpiryPolicy`, since S3 cannot see that file |
| No stated rounding rule (attempts 3 and 4) | Explicitly floored — the owner's own genuine choice, "never over-report the time left" |
| Link retention/purge after expiry unstated (attempt 4, new) | Explicitly never purged, queryable indefinitely in the expired shape — restates `spec.md`'s own EX-003/Compensation Register text directly, verified against `JdbcShortLinkRepository`/`ShortLinkRepository` (no delete method exists) |
| Malformed `{code}` handling unstated (attempt 4, new) | Explicitly the same `404` as an unknown code — verified against `LinkController`'s unconstrained `/v1/links/{shortCode}/...` route (unlike `RedirectController`'s regex-constrained one), so a malformed code reaches the same lookup-miss `404` as an unissued one |
| Delivery medium undefined | Explicitly a `GET` HTTP endpoint returning JSON |
| "Owning creator" identity undefined | Explicitly stated as a domain invariant: the creator recorded at creation time, with no ownership-transfer concept, so "creator" and "owner" always name the same principal |
| Non-owner/anonymous behavior undefined | Explicitly split: `401` for anonymous (routing-level refusal), `404` for every other non-owner case including malformed and unissued codes (non-disclosing refusal) |

- **The data already exists.** `ShortLink` carries `expiresAt` (an `Instant`), and `ExpiryPolicy`
  (`src/main/java/agentic/shortener/domain/link/ExpiryPolicy.java`, T046) already defines the expiry
  boundary rule precisely: "at `expiresAt` the link is expired" — the exact rule `secondsRemaining`'s
  zero-floor has to agree with.
- **The ownership and authentication model already exists.** `CreatorAuthFilter` (T052) resolves the caller
  identity before a use case ever runs, refusing an unauthenticated request with `401` by routing;
  `ShortLink` carries the owning creator's identity for exactly this kind of ownership check.
- **The precedent for the response shape already exists.** `GetAnalyticsUseCase`
  (`src/main/java/agentic/shortener/application/GetAnalyticsUseCase.java`, T051, FR-URL-011) and its
  controller method (`LinkController.analytics`) are a creator-scoped read of a per-link fact with the
  identical two-refusal shape this requirement now names explicitly: `401` at the filter, a single shared
  `404` refusal value for both "not yours" and "does not exist." There is nothing this requirement needs
  that is not already a solved, working pattern in the delivered codebase.

## 2. Consistent

- **No conflict with FR-URL-009** (expiration semantics, T046) — reads the same `expiresAt` field FR-URL-009
  already defines; does not change expiry's own definition or boundary rule.
- **No conflict with FR-URL-010** (redirect analytics, per-event, timestamp only) — derived from
  `ShortLink.expiresAt`, not from `RedirectEvent`; adds no follower-identifying field.
- **Matches FR-URL-011's non-disclosure principle exactly**, now including the refinement above: a
  non-owner-of-a-real-code and a non-owner-of-a-nonexistent-code get byte-identical `404`s; an anonymous
  caller is refused earlier, at `401`, by the same filter every other authenticated endpoint already uses —
  not a new rule, the existing one applied.
- **No conflict with the two-plane architecture** (ADR-006) — an application-plane read, symmetric with the
  analytics endpoint; introduces no orchestration-plane dependency.

## 3. Testable

Concrete accept/reject criteria, directly assertable, each naming the exact response shape:

- **Accept**: the owning, authenticated creator retrieves `expiresAt` and `secondsRemaining` for a link that
  has not yet expired; `secondsRemaining` is a positive integer.
- **Accept, non-expiring**: a link created with `--expires never` (or its API equivalent) returns
  `expiresAt: null, secondsRemaining: null`.
- **Accept, boundary**: a link at or past `expiresAt` returns `secondsRemaining: 0`, never negative, never an
  error.
- **Reject, anonymous**: an unauthenticated request receives `401`, the same refusal every other
  authenticated endpoint already produces, and never reaches the ownership check at all.
- **Reject, non-owner**: a different authenticated creator receives `404` — the same code and body an
  unknown code would produce.
- **Reject, non-disclosure**: the `404` for a real code owned by someone else and the `404` for a code that
  was never issued are byte-identical, proven the same structural way `GetAnalyticsUseCase`'s own shared
  refusal value is already proven.

## 4. Inside approved policy and architecture boundaries

- Introduces no new technology (ADR-001 Java 21/Spring Boot stack, unchanged).
- Introduces no new persistence concern (ADR-002) — reads a field the schema already carries.
- Fits the existing authentication mechanism (ADR-013) without modification.
- A pure addition to the application plane; nothing about it requires touching the orchestration plane the
  twelve-stage pipeline runs in.
- No policy check in `policy-set-1.1.0` governs application-plane feature scope; the governance surface this
  run exercises is the orchestration pipeline's own gates (S6 architecture approval, S11 release readiness),
  not a policy about which application features may exist.

## Conclusion

All four criteria hold against the system as it is actually built today, and every real gap found across
four live attempts — by two independent models, and by the owner's own materiality calibration (CR-049) —
is now answered by name, directly in the requirement's own text, not left for S3 to infer from a codebase it
cannot see. Per `spec.md`'s DS-A section, this input MUST proceed through
S1→S2→S3→**S4 SKIPPED**→S5→S6→S7→S8→S9‖S10→S11→S12 with no clarification gate firing, and the run (T132)
must record the quality checks performed and the explicit no-clarification reason.
