# Change Request CR-003 — DF-002 Resolved: Redirects Are Temporary, Never Permanent

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20 |
| Approving authority | Pravallika Veeravalli (human owner), Gate 4 closing package |
| Raised by | Executing agent, implementing the owner's DF-002 resolution |
| Affected approved artifacts | `specs/001-agentic-sdlc-url-shortener/spec.md`; `contracts/openapi.yaml`; `plan.md` |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Prior record | `docs/governance/gate-decisions/gate-04-closing-package.md` |

## The decision

**DF-002 — RESOLVED: redirects are temporary, never permanent.** The owner's reasoning, verbatim:

> A permanent redirect is cached by browsers, after which clicks never reach the service — analytics cannot
> count them (ADR-014 would be structurally blinded) and expiry cannot be enforced (a cached redirect outlives
> the link's death). Temporary is the only class compatible with FR-URL-008 and FR-URL-010. Exact status code is
> an implementation detail within the temporary class.

Note what the decision does **not** do: it does not fix a status code. It fixes the *class*, and leaves the
specific code an implementation detail, which keeps CN-006's deferral of mechanism intact.

## Applied edits

| # | Location | Change |
|---|---|---|
| 1 | **FR-URL-007** | Added requirement that resolution MUST use a **temporary-class** redirect, with negative criterion that a permanent-class redirect is prohibited because it defeats analytics counting and expiry enforcement |
| 2 | **FR-URL-008** | Added that expiry enforcement **depends** on the temporary class — a cached permanent redirect would outlive the link's death and bypass the service entirely |
| 3 | **§Deferred Findings, DF-002** | Marked **RESOLVED** at the Gate 4 closing package, with the owner's reasoning recorded |
| 4 | **§Ambiguities, AQ-004** | Marked resolved, pointing at DF-002 and this CR |
| 5 | **New EC-042** | A client caches a redirect and follows it after expiry — impossible under the temporary class; asserted by test |
| 6 | `contracts/openapi.yaml` | `307` description changed from "provisional status code pending DF-002" to the approved temporary class |
| 7 | `contracts/README.md` | DF-002 removed from open items |
| 8 | `plan.md` §Decisions Required | DF-002 row marked resolved |

## Impact analysis

**Version impact**: MINOR on the specification — behaviour is constrained, nothing removed or redefined. On the
contract, a description-only change (PATCH): the `307` response already present is now approved rather than
provisional.

**Backward-compatibility impact**: none. No published consumer; the status code does not change.

**Affected consumers**: internal only — the redirect resolution path.

**Affected tests**: assertion that the redirect response is temporary-class; a cache-behaviour test covering
EC-042 (an expired link must not be reachable from a cached redirect).

**Affected documentation**: `quickstart.md` already notes the status code is provisional — updated to approved.

**Rollout / migration**: none. No data change.

**Interaction recorded**: this resolution is what makes ADR-014's analytics consistency model meaningful. Had
DF-002 resolved the other way, no consistency model could have counted redirects the service never sees, and
ADR-014's careful separation of exactness from failure tolerance would have been moot.

## Residual risk

Low, and in the correctness direction. The cost is that every follow reaches the service, forgoing client-side
caching — accepted deliberately, because the alternative silently breaks two approved requirements.
