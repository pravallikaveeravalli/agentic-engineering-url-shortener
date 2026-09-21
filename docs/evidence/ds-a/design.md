# DS-A — Greenfield run design

Task T131. Req: DS-A, FR-ORC-010. Scn: DS-A. ADR: ADR-004.

## The input

> Expose the remaining time-to-expiry for a short link to its owning creator.

Named in `plan.md` §DS-A as the proposed subject. This document is the check plan.md's own DS-A
entry calls for before the run: verifying the input against the four well-formedness criteria
spec.md's DS-A section states (`spec.md:1086`) — **complete, consistent, testable, and inside
approved policy and architecture boundaries** — genuinely, against the real system as it exists
today, not asserted. T131's own Guard clause is explicit that a trivially easy input chosen to
guarantee a clean run would be a rigged demonstration; the check below is written so a reader can
verify it was not.

## 1. Complete

Every element needed to build this without further guessing is already present in the delivered
system, not invented for the demonstration:

- **The data already exists.** `ShortLink` carries `expiresAt` (an `Instant`), and `ExpiryPolicy`
  (`src/main/java/agentic/shortener/domain/link/ExpiryPolicy.java`, T046) already defines the
  expiry boundary rule precisely: "at `expiresAt` the link is expired" — the exact rule a
  remaining-time calculation has to agree with (`now` to `expiresAt`, floored at zero once past
  it, matching `ShortLink.isExpiredAt`'s own boundary).
- **The ownership and authentication model already exists.** `CreatorAuthFilter` (T052) resolves
  the caller identity before a use case ever runs; `ShortLink` carries the owning creator's
  identity for exactly this kind of ownership check.
- **The precedent for the response shape already exists.** `GetAnalyticsUseCase`
  (`src/main/java/agentic/shortener/application/GetAnalyticsUseCase.java`, T051, FR-URL-011) is a
  creator-scoped read of a per-link fact with the identical shape this requirement needs: a
  `permitted` boolean and the fact itself, with a single shared `REFUSED` constant so a non-owner
  and a nonexistent code produce byte-identical output. There is nothing this requirement needs
  that is not already a solved problem somewhere in the delivered codebase — decomposition can
  name concrete existing files to extend rather than open questions to resolve.

## 2. Consistent

- **No conflict with FR-URL-009** (expiration semantics, T046) — the requirement reads the same
  `expiresAt` field FR-URL-009 already defines and does not change expiry's own definition or
  boundary rule.
- **No conflict with FR-URL-010** (redirect analytics, per-event, timestamp only) — remaining
  time-to-expiry is derived from `ShortLink.expiresAt`, not from `RedirectEvent`; it adds no
  follower-identifying field and touches no analytics-retention rule.
- **No conflict with FR-URL-011's non-disclosure principle** (analytics retrieval,
  creator-scoped) — the same principle this new capability must honor for the same reason: a
  non-owner asking about a code that exists and a non-owner asking about a code that does not
  exist must be indistinguishable, or the endpoint becomes an ownership oracle over the whole
  keyspace. This is a real constraint the requirement must be decomposed against, not a rubber
  stamp — see the acceptance criteria below.
- **No conflict with the two-plane architecture** (ADR-006) — this is an application-plane
  read, symmetric with `GetAnalyticsUseCase`; it introduces no orchestration-plane dependency and
  requires no change to `DependencyDirectionTest`'s rules.

## 3. Testable

Concrete accept/reject criteria, stated so a test can assert them directly:

- **Accept**: the owning, authenticated creator retrieves a non-negative remaining duration for
  a link that has not yet expired.
- **Accept, boundary**: a link at or past `expiresAt` reports zero remaining time (not negative,
  not an error) — consistent with `ExpiryPolicy`'s existing "at `expiresAt` the link is expired"
  rule rather than a new one invented for this endpoint.
- **Reject**: a different authenticated creator receives the same refusal `GetAnalyticsUseCase`
  already defines for non-owners.
- **Reject**: an unauthenticated caller receives a refusal, not the value.
- **Reject, non-disclosure**: a non-owner's refusal for a real code and a non-owner's refusal for
  a code that was never issued are byte-identical — the same structural test
  `GetAnalyticsUseCase`'s own `REFUSED` constant already proves for analytics, extended to this
  new use case.

## 4. Inside approved policy and architecture boundaries

- Introduces no new technology (ADR-001 Java 21/Spring Boot stack, unchanged).
- Introduces no new persistence concern (ADR-002) — reads a field the schema already carries.
- Fits the existing authentication mechanism (ADR-013) without modification.
- Is a pure addition to the application plane; nothing about it requires touching the
  orchestration plane the twelve-stage pipeline runs in.
- No policy check in `policy-set-1.1.0` governs application-plane feature scope, so none is
  implicated by this specific input; the governance surface this run exercises is the
  orchestration pipeline's own gates (S6 architecture approval, S11 release readiness), not a
  policy about which application features may exist.

## Conclusion

All four criteria hold against the system as it is actually built today, for reasons specific to
this requirement rather than asserted generically. Per `spec.md`'s DS-A section, this input MUST
proceed through S1→S2→S3→**S4 SKIPPED**→S5→S6→S7→S8→S9‖S10→S11→S12 with no clarification gate
firing, and the run (T132) must record the quality checks performed and the explicit
no-clarification reason.
