# Change Request CR-002 — Credential Expiry Required at Provisioning

| Field | Value |
|---|---|
| Status | **PROPOSED — awaiting human change approval.** No approved artifact has been edited. |
| Raised by | Pravallika Veeravalli (owner refinement 3 to ADR-013, 2026-09-20) |
| Raised during | Gate 4, ADR-013 review |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/spec.md` (approved Gate 2, clarified Gate 3, amended by CR-001) |
| Change owner | Human owner |
| Governing constitution | v1.1.0 — §Compliance and Change-Control Policy Enforcement; `POL-CHG-001` |

## The decision requiring the change

ADR-013 refinement 3, in the owner's terms:

> No one gets to not think about credential lifetime at provisioning — an unconsidered eternal key must be
> impossible; explicitly choosing `never` is permitted, silently receiving it is not.

**Approved behavior**: the provisioning script takes a **REQUIRED** expiry parameter — a duration, or the
explicit literal **`never`**. There is no default. Mechanics: nullable `expires_at` on `creator_credential`,
null reachable only via the explicit `never`; the authentication filter rejects expired credentials with the
**same response shape** as revoked ones. Deliberately **not** an account lifecycle — EX-005 stands: no renewal
flow, no notification, rotation remains provision-new-then-revoke-old.

## Why this needs change control and the other two refinements did not

Of the three refinements the owner made to ADR-013, only this one touches approved specification text.

| Refinement | Spec impact | Route |
|---|---|---|
| **1. `Authorization: Bearer` transport** | **None.** FR-URL-018 and FR-URL-019 are silent on transport mechanics; the header name appeared only in `contracts/openapi.yaml` and ADR-013 | Applied directly — contract and ADR are unapproved |
| **2. `crk_` key prefix** | **None.** The spec requires hash-only storage and a non-reversible stored form; it does not constrain the rendered form | Applied directly |
| **3. Required expiry** | **Yes** — adds an obligation to FR-URL-019's accept criteria and a field to KE-24 | **This request** |

## Proposed edits to `spec.md`

| # | Location | Current text | Proposed change |
|---|---|---|---|
| 1 | **FR-URL-019** Accept criteria (~L483) | "the operator script provisions a creator and may display the key once to the operator's terminal; the stored record contains only a hash; the service authenticates a presented key against that hash." | Add: "the script **requires** an expiry parameter — a duration or the explicit literal `never` — and **refuses to provision without it**; an expired credential is refused with the same response shape as a revoked one." |
| 2 | **FR-URL-019** Reject criteria (~L486) | "no HTTP key-issuance surface may exist; keys MUST NOT be committed in configuration; keys MUST NOT be generated at application startup…; the stored form MUST NOT be reversible to the key." | Add: "a credential MUST NOT be provisioned without an explicit expiry decision — there is **no default**, and a non-expiring credential is reachable only by passing the literal `never`; an expired credential MUST NOT authenticate, and its refusal MUST NOT be distinguishable from a revoked credential's." |
| 3 | **FR-URL-019** Rationale | — | Add the owner's reasoning verbatim (quoted above). |
| 4 | **KE-24 CreatorCredential** (~L957) | "the stored **hash** of a creator's API key, never the key itself. Belongs to one Creator." | Add: "Carries a **nullable `expires_at`**, where null is reachable only by an explicit `never` at provisioning, and an optional `revoked_at`. The stored hash covers the full presented string." |
| 5 | **New edge case** | — | `EC-041`: authentication attempted with an expired credential. Must be refused, and the refusal must be byte-identical to a revoked credential's. |

### Assessed as needing no change

- **FR-URL-018** — creator identity for creation and analytics; unaffected by credential lifetime.
- **FR-URL-011** — owner-only analytics; an expired credential simply fails authentication first.
- **EX-005** — explicitly reaffirmed rather than changed: required expiry is a provisioning parameter, not an
  account lifecycle. No renewal, no notification, no self-service.
- **NFR-SEC-003** — threat-model documentation obligation already covers authentication assumptions; the
  expiry decision is additional content for it, not a new requirement.
- **PVT set** — no new proposed validation target. A *default* duration would have needed one; requiring an
  explicit choice deliberately avoids proposing a number.

## Impact analysis

**Version impact**: MINOR on the specification — an obligation is added and an entity gains two optional
fields; nothing is removed or redefined.

**Backward-compatibility impact**: none. No released consumer. The contract change for refinement 1
(`securitySchemes.creatorApiKey` from `apiKey`/header to `http`/`bearer`) would be **breaking** against a
published contract, but the file is unpublished and uncommitted, so it is pre-first-version. Recorded here for
completeness since `POL-CHG-001` governs contract changes regardless of approval state.

**Affected consumers**: none external. Internally: the provisioning script, the authentication filter, the
credential repository, and the `creator_credential` migration.

**Affected tests**: script exits non-zero without the expiry parameter; `never` produces a null `expires_at`
and nothing else does; expired-key rejection; expired and revoked responses byte-identical; plus refinement 1's
contract test asserting the bearer scheme and refinement 2's generator and secret-scan-pattern tests.

**Affected documentation**: ADR-013, `quickstart.md`, `data-model.md`, `contracts/openapi.yaml` — all
**unapproved**, already updated. `spec.md` awaits this decision.

**Rollout / migration steps**: `creator_credential` gains nullable `expires_at`. No data migration — no
credentials exist yet. If approved, the five spec edits are applied in one change and recorded in the
specification's §Change-control amendments.

**Required change approval**: human owner.

## Residual risk

Low, and in the tightening direction. The change makes an unbounded credential lifetime impossible to obtain
accidentally. Two disclosed costs: an operator may choose `never` reflexively, which is accepted because the
requirement is that the decision be *made* rather than which way it goes; and an expiring credential fails with
no warning, because no notification infrastructure exists and EX-005 excludes building one.

## Decision requested

Approve, reject, or request changes to the five specification edits. Nothing in `spec.md` has been altered
pending this decision. Refinements 1 and 2 are already applied to the unapproved artifacts and are recorded here
only for traceability.
