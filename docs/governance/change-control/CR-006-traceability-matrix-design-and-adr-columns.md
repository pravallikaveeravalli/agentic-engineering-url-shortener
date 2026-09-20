# Change Request CR-006 — Traceability Matrix Gains Design and ADR Columns

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20 |
| Approving authority | Pravallika Veeravalli (human owner) |
| Raised by | Executing agent, from checklist finding CHK037 |
| Affected approved artifacts | `specs/001-agentic-sdlc-url-shortener/spec.md` §Traceability |
| Governing constitution | v1.1.0; `POL-CHG-001`, `POL-TRC-001` |

## The finding

`CHK037` found that the approved plan and the approved specification contradict each other. `plan.md:710`
states: *"This plan adds the Design and ADR columns."* It does not — the matrix header in `spec.md` reads
`Requirement | Journey | Scenario | Edge cases | Task | Test | Evidence`.

Two of the ten links in the chain FR-ORC-027 mandates — Design and ADR — are defined in prose and have no
representation in the matrix that implements the chain. That also trips `CHK034` (chain link representation) and
`CHK228` (artifacts agreeing on shared structure).

## Owner's decision

**APPROVED, via change control.** Owner's grounds:

> The approved plan already commits to these columns, so this closes a contradiction between two approved
> artifacts without changing either's intent, and the ADRs' Traceability sections make every cell verifiable.
> The against-case (touching approved spec text) is honored by routing it as a CR, not housekeeping. Populate
> from the existing ADR traceability sections.

**Recorded case against, acknowledged by the owner**: this edits approved specification text for what is
arguably a documentation gap rather than a requirement change. Honoured by routing it through change control
rather than treating it as housekeeping, so the edit carries an approval record rather than none.

## Applied edits

| # | Location | Change |
|---|---|---|
| 1 | §Traceability matrix header | Two columns inserted between `Edge cases` and `Task`: **`Design`** and **`ADR`**, placing them in the chain order FR-ORC-027 states |
| 2 | All 51 matrix rows | `Design` populated with the designing plan section; `ADR` populated from the ADRs' own Traceability sections |
| 3 | §Traceability preamble | Notes that Design and ADR are now populated and that Task/Test/Evidence remain reserved for later stages |
| 4 | `plan.md` §13 | Sentence corrected from a forward-looking claim to a statement of fact, with the CR referenced |

Population is **derived, not invented**: every ADR cell comes from the requirement identifiers that ADR already
lists in its own Traceability section, so a reviewer can verify any cell by opening the ADR it names.

## Impact analysis

**Version impact**: PATCH on the specification. No obligation changes; two columns of existing traceability
information are surfaced in the matrix that already carried the other eight links.

**Backward-compatibility impact**: none.

**Affected consumers**: the traceability report generator (`POL-TRC-001`), which must now populate and check ten
columns rather than eight.

**Affected tests**: the zero-orphan traceability assertion extends to the two new columns — a requirement with no
Design or ADR reference is an orphan in the new sense, and the report must say so.

**Affected documentation**: `plan.md` §13's claim corrected in the same change.

**Rollout / migration**: none.

## Residual risk

Low. The one genuine risk is that a populated cell drifts from the ADR it cites. Mitigated by populating from the
ADRs rather than independently, and by `POL-TRC-001` treating a dangling reference as a traceability failure.
