# CR-051 — DS-A's clean-pass demonstration subject replaced with a genuinely minimal requirement

| Field | Value |
|---|---|
| **Change request** | CR-051 |
| **Title** | Retire the expiry-endpoint requirement from DS-A's clean-pass role; demonstrate the "complete requirement proceeds without a gate" property on a genuinely minimal feature instead |
| **Raised by** | Owner's decision after attempt 5's findings, reviewed against `docs/evidence/ds-a/requirement-completeness-ceiling-finding.md` |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — Option A**: "demonstrate the clean pass with a genuinely minimal requirement; keep the expiry saga as a documented finding." Conversational approval of a scenario-subject and requirement-text revision — **not a formal gate decision**; no `docs/governance/gate-decisions/` record is produced (CLAUDE.md's own trigger rule) |
| **Decision date** | 2026-09-21 |
| **Classification** | **LOW** — no change to any implemented behavior, policy, architecture, or approved spec/plan/tasks artifact; changes only which requirement DS-A's live demonstration evaluates, and adds one new, genuinely minimal endpoint to prove the pipeline with |
| **Artifacts changed** | `docs/evidence/ds-a/design.md` (new "current subject" section), `docs/evidence/ds-a/requirement-completeness-ceiling-finding.md` (new, the Part 1 finding this CR cites) |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`REQUIREMENT` constant only) |
| **Applied** | 2026-09-21 |

---

## Why the expiry requirement is retired from this role

Five live attempts (`docs/evidence/ds-a/pending-gate-s4-context.md`), across two independent models,
repeatedly closed every gap the prior attempt found and surfaced new, genuine ones — most recently, CR-050's
fully self-contained revision closed all four of attempt 4's items outright, and still surfaced two new
open items on the very same pass. `docs/evidence/ds-a/requirement-completeness-ceiling-finding.md` (filed
alongside this CR) states the root cause: S3 has no repository access (`StageInput`'s closed field list) and
its job, per FR-ORC-010, is requirement *completeness*, not system completeness — so a rich-surface feature
can always have one more unstated dimension, discoverable only empirically, one revision at a time. This is
a correct, principled detector behavior, independently proven sound by the DS-C compensating check (7/7
`MATERIAL_PENDING` on spec.md's own canonical contradiction) — not a defect to route around by further
wording attempts. The owner's decision: stop forcing a rich feature through, keep the expiry arc as a
documented engineering finding (an asset), and demonstrate DS-A's own defining property — a complete
requirement proceeds without an artificial gate — on a feature honestly small enough for completeness to be
achievable.

## Verification performed before choosing the replacement subject

The owner's proposed wording named `GET /v1/health` as a public liveness endpoint. Verified against real
source before adopting it, per standing practice — and this one **did not** check out as proposed:

- `src/main/java/agentic/shortener/delivery/HealthController.java` already implements `GET /health/live`
  (T032, FR-URL-015, ADR-012): unauthenticated, no dependency checks, always `200 {"status": "UP"}`, "no
  failure mode by design" in exactly the sense the owner's proposed wording describes. Adding a second,
  differently-named liveness endpoint (`/v1/health`) alongside an existing one that already does the same
  thing would be a genuine duplication — a real Criterion-2 (Consistent) conflict with an already-approved,
  already-delivered artifact, not a clean demonstration subject.
- No route or path conflict exists for `/v1/health` itself (`RedirectController`'s single-segment regex
  route cannot match a multi-segment path; Actuator is scoped to `/actuator/health`, confirmed via
  `application.yml`'s `management.endpoints.web.exposure.include: health` with no base-path override) — the
  problem is conceptual duplication of FR-URL-015, not a literal collision.
- **Adopted the owner's own stated fallback instead**: `GET /v1/version`. Verified clean: no existing route
  under `/v1/version` or any prefix of it (`/v1/`'s only existing children are `/v1/links` and `/v1/runs`);
  no existing version/build-info endpoint anywhere in `src/main/java/`; `CreatorAuthFilter`'s registered URL
  patterns (`/v1/links`, `/v1/links/*`, `AuthConfiguration.java`) do not cover `/v1/version`, so it is
  unauthenticated by default with no filter change needed.

## The final, genuinely minimal requirement

> Add a public `GET /v1/version` endpoint that requires no authentication, accepts no path or query
> parameters, and always returns HTTP `200` with `Content-Type: application/json`, header `Cache-Control:
> no-store`, and body exactly `{"version": "0.1.0-SNAPSHOT"}`. The version string is a fixed literal encoded
> directly in the endpoint's own implementation — it is never computed, never read from a build manifest,
> never derived from git or environment state, and never changes without a deliberate code edit to this
> endpoint itself. The endpoint performs no dependency or downstream checks, reads and writes no persisted
> data, and has no failure mode by design: process-reachable is the only condition it reports, and there is
> no input, state, or code path by which it could ever return anything other than this exact response.

Chosen honestly for a genuinely small surface — not tuned to the detector's specific past findings.
Compare, dimension by dimension, to every gap class the five expiry attempts found: no auth means no
401-vs-404 precedence question (attempt 5, item 1); no inputs means no malformed/unknown/non-owner handling
at all; a fixed body means no computation, no rounding rule, no boundary instant (attempts 3–5); an explicit
`Cache-Control: no-store` directly answers the freshness/caching dimension attempt 5 raised (item 2) for
this endpoint, rather than leaving it silent; and "no failure mode by design" is stated as a positive,
closed fact rather than left for a reader to infer from an absence.

## Scope

**What changed**: DS-A's own scenario subject (`docs/evidence/ds-a/design.md`) and the string literal
`DsALiveRun.REQUIREMENT`. No production code exists yet for `/v1/version` — S7 (implementation) is where the
live run, if it clears S3, would build it for real, exactly as DS-A's own scenario is designed to exercise.

**What did not change**: `HealthController`, `AuthConfiguration`, `CreatorAuthFilter`, and every other file
cited above — all read for verification, none modified. CR-048/CR-049/CR-050 and the expiry-endpoint arc
they produced are unaffected and unmodified; they are cited, not altered, by
`requirement-completeness-ceiling-finding.md`.

## Conditions attached to the approval

1. **The expiry saga preserved intact, not deleted** — honoured; all five `run-snapshot-ATTEMPT-N-...md`
   files, `pending-gate-s4-context.md`, and CR-048/049/050 remain in place, unmodified.
2. **A rigorous findings document, not a throwaway note** — honoured;
   `docs/evidence/ds-a/requirement-completeness-ceiling-finding.md` states the root cause, cites FR-ORC-010
   and the DS-C compensating check, and explains why this is a correct detector behavior rather than a
   defect.
3. **The replacement chosen honestly for minimality, not tuned to the detector** — honoured; verified
   against real source (surfacing a real duplication with the owner's own first proposal, `/v1/health`, and
   using the owner's own stated fallback instead), and its minimality is argued structurally (no auth, no
   inputs, fixed output) rather than by reference to any specific past S3 finding.
4. **No gate-decision record** — honoured; this file is the only record of this revision, filed under
   `docs/governance/change-control/`, not `docs/governance/gate-decisions/`.

## Cross-record orphan check

- CR-048/CR-049/CR-050 are not edited in place; none of their claims are falsified by this CR — they each
  describe what a specific attempt against the expiry wording actually produced at the time, and remain
  accurate as filed.
- No other applied record asserts DS-A's scenario subject is the expiry endpoint going forward — `plan.md`
  and `tasks.md` reference the DS-A scenario by its criteria and stage behavior, not by quoting a specific
  feature, so neither is falsified.
- `HealthController`'s own existing route (`/health/live`) is unaffected; this CR does not touch it, only
  cites it as the reason `/v1/health` was not chosen.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If DS-A's next run still finds a genuinely material item on this minimal a requirement, that is a materially different, higher-priority finding than the expiry arc — a minimal, auth-free, input-free, fixed-output endpoint failing to clear S3 would call the calibration itself back into question, not just this wording | T132's own re-run, immediately following this CR |
| If S3 flags an unconstrained dimension as material despite an explicit statement closing it (e.g., flags caching as unresolved despite the explicit `Cache-Control: no-store`), that is a distinct, separate finding about possible residual CR-007 misapplication — report it plainly, do not paper over it | T132's own re-run, immediately following this CR |
