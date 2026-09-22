# DS-A — Greenfield run design

Task T131 (revised, CR-048). Req: DS-A, FR-ORC-010. Scn: DS-A. ADR: ADR-004.

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

## The input (revised)

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
was not — every one of the six real gaps the ambiguity detector found, twice, is answered by name.

## 1. Complete

Every element needed to build this without further guessing is already present in the delivered system, and
every point the ambiguity detector flagged is now pre-answered in the requirement's own text:

| Gap found (both models) | Resolved by this wording |
|---|---|
| No unit/format for the value | `secondsRemaining` is explicitly an integer count of seconds; `expiresAt` is explicitly ISO-8601 UTC |
| Non-expiring link undefined | Both fields explicitly `null` |
| Already-expired link undefined | `secondsRemaining` explicitly `0`, never negative — matching `ExpiryPolicy`'s existing "at `expiresAt` the link is expired" boundary |
| Delivery medium undefined | Explicitly a `GET` HTTP endpoint returning JSON |
| "Owning creator" identity undefined | Tied explicitly to "authenticated creator" — the existing `CreatorAuthFilter`/creator-API-key identity, the same one `GetAnalyticsUseCase` already uses |
| Non-owner/anonymous behavior undefined | Explicitly split: `401` for anonymous (routing-level refusal), `404` for a wrong owner (non-disclosing refusal) — see the refinement note above |

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

All four criteria hold against the system as it is actually built today, and every real gap two independent
models found in the prior wording is now answered by name in the requirement's own text — not asserted
resolved, checked against the delivered codebase's own existing patterns line by line. Per `spec.md`'s DS-A
section, this input MUST proceed through S1→S2→S3→**S4 SKIPPED**→S5→S6→S7→S8→S9‖S10→S11→S12 with no
clarification gate firing, and the run (T132) must record the quality checks performed and the explicit
no-clarification reason.
