# DS-C live run — S4 `UNRESOLVED_AMBIGUITY` gate decision (central contradiction + four sub-questions)

**Scope note** (same as the DS-A precedent this mirrors): this directory
(`docs/governance/gate-decisions/ds-c/`) records decisions at **product-internal** orchestration-run gates
(DS-C's own S4), never at this repository's own numbered SpecKit lifecycle gates.

| Field | Value |
|---|---|
| **Gate** | S4 — `UNRESOLVED_AMBIGUITY`, DS-C scenario, live run T138/T139 (requirement: spec.md's own canonical demonstration text — expire after a week / retain analytics indefinitely / still redirect for trusted partners) |
| **Outcome** | `APPROVED` |
| **Deciding human** | Pravallika Veeravalli |
| **Decision date** | 2026-09-22 |
| **Artifact approved** | Five clarifications resolving the run's own real, live findings (`docs/evidence/ds-c/silence.json`, `docs/evidence/ds-c/pending-gate-s4-context.md`): the central semantic contradiction, trusted-partner identity, the 168-hour boundary instant, denied-access analytics, and analytics metadata scope |
| **Where else recorded** | `clarification_decision`/`ambiguity_record` tables (via `LineageStore.resolveWithClarification`); `docs/evidence/ds-c/replan.json` (T139's own evidence, including the downstream-impact analysis) |

## Reason / verification performed

Five real questions, each answered by the owner directly, in her own terms:

1. **Central — does an expired link's target-URL mapping survive past expiry?** "YES, the mapping survives
   (consistent with the system's existing never-delete / only-expire retention policy — Compensation
   Register / EX-003). Expiration changes redirect eligibility, not data existence: an expired link refuses
   redirects for normal/anonymous callers but continues to redirect for trusted partners. This resolves the
   contradiction — the standard-path 'no longer resolve' means 'for normal callers,' while the mapping
   persists so the trusted-partner path is implementable."
2. **Trusted-partner identity**: "API-key / signed-header based, consistent with the existing creator-auth
   model; maintained by the system's existing auth/partner administration."
3. **168-hour boundary instant**: "at exactly the boundary the link is expired (validity is exclusive of the
   instant), consistent with the existing `ExpiryPolicy` rule."
4. **Denied access → analytics?**: "Yes — refused/blocked access attempts on expired links are recorded
   (consistent with indefinite, append-only retention; useful for abuse/audit)."
5. **"Associated metadata" scope**: "the fields the existing redirect-analytics already captures — no new
   fields introduced for indefinite retention; enumerate against the existing analytics schema."

Four of the five are explicitly grounded by the owner in already-approved artifacts (EX-003/Compensation
Register, the existing creator-auth model, `ExpiryPolicy`, the existing analytics schema) — restatements of
existing system behavior into this new requirement's own text, the same discipline CR-050 used for DS-A. The
central resolution is the owner's own genuine reconciliation of the contradiction. This agent performed no
independent verification of any of the five, consistent with `ActorAuthority`/FR-ORC-021: the deciding actor
is the human, not the agent.

## Conditions attached to the approval

1. **A future implementation of this feature MUST treat "expire" as eligibility, not deletion** — an
   expired link's row/mapping is never removed; only its default-caller redirect eligibility changes. This
   is the single most load-bearing condition of this decision and must not be silently reinterpreted.
2. **Trusted-partner identification MUST reuse the existing creator-auth mechanism** (API key / signed
   header) rather than introduce a new, parallel identity system.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If a future implementation of this feature deletes an expired link's mapping rather than merely changing its default-caller eligibility, that directly contradicts this recorded decision | Whenever this feature is actually implemented (beyond S6's own architecture stage) |
| This decision's own downstream-impact analysis (empty, nothing had executed) is specific to THIS run, at THIS point — a future replan touching an already-executed stage is a different case and must use `ReplanService` for real, not this precedent | Any future replan on a run where downstream work already exists |
