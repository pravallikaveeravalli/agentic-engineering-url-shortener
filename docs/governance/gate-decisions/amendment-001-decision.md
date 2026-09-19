# Gate Decision Record — Constitution Amendment 001 (v1.0.0 → v1.1.0)

| Field | Value |
|---|---|
| Gate | Constitution amendment approval (§Amendment procedure and versioning) |
| Outcome | **APPROVED** — all six requested sub-decisions |
| Deciding human | Pravallika Veeravalli |
| Decision date | 2026-09-18 |
| Artifact approved | `docs/governance/amendments/proposal-001-gate-record-materialization.md` |
| Resulting version | Constitution v1.1.0 (MINOR), last amended 2026-09-18 |
| Recorded in | This record; the constitution's `## Amendment History` section; the commit applying the amendment |

## Reason / verification performed

The human owner identified, during and after Gate 1, that constitution v1.0.0 specified what a gate
record must contain (§Gate semantics: outcome, deciding human, timestamp, reason) and barred
conversation-only approvals from being cited (§Assessment Scope → Repository as source of truth),
but never operationalized materialization: it named no location, no responsible actor, and no
deadline relative to the work a decision authorizes.

The observed consequence was that Gate 1's reasoned decision initially existed only in conversation
and in the candidate's git-ignored working log; the repository record
(`docs/governance/gate-decisions/gate-01-constitution.md`, commit `c347606`) was materialized after
the fact, and only because the owner asked where her decision had been logged. The owner further
identified that the process convention subsequently issued in conversation was itself
non-artifactual by the constitution's own standard and would not survive a session reset on any
authoritative basis.

The owner requested a written amendment proposal under §Amendment procedure, reviewed it, and
approved all six sub-decisions it put forward.

## Sub-decisions

| # | Sub-decision | Outcome |
|---|---|---|
| 1 | Edits 1–3 (core): gate records as repository artifacts; materialized by the acting agent before authorized work begins; committed no later than the acting commit; conversation-only process rules carry no authority; `CLAUDE.md` must define the mechanics and may not weaken constitutional obligations | **APPROVED** |
| 2 | Edit 4: release readiness condition 3 extended to block on unmaterialized gate outcomes | **APPROVED** |
| 3 | Classification MINOR, target v1.1.0 | **CONFIRMED** |
| 4 | Proposed `CLAUDE.md` content | **APPROVED with one owner addition** (see below) |
| 5 | Change-entry location: Option A — new `## Amendment History` section in the constitution, above the version footer | **APPROVED as the fifth edit** |
| 6 | FR-ORC-013 follow-up acceptance criterion applied now, while the specification is Draft | **APPROVED to apply now** |

### Reasoning recorded by the owner on sub-decision 3

The amendment adds a responsible actor, a deadline, and a work-precondition that no reading of
v1.0.0 derives. The honest classification is taken while the re-analysis obligation is still
vacuous — no feature has reached Analyze, so MINOR costs nothing now and would cost progressively
more at every later stage.

### Owner addition on sub-decision 4

`CLAUDE.md` must carry, after the gate-records section, a section titled **"Other governance
records"**: policy exceptions materialized under `docs/governance/exceptions/`, and change-control
records (change requests, impact analyses) under `docs/governance/change-control/`, following the
same materialization, timing, verbatim-fidelity, and immutability pattern as gate records.

**Owner's reason**: review of the constitution found the same class of gap for Principle VI
records — required fields defined, no location assigned.

## Conditions attached to the approval

1. The decision record is written before the work it authorizes begins, per the newly approved
   §Gate semantics ordering rule.
2. The constitution footer is set to `**Version**: 1.1.0 | **Ratified**: 2026-09-17 | **Last
   Amended**: 2026-09-18` — the original ratification date is preserved, not overwritten.
3. The proposal's Status is updated to ACCEPTED with a reference to this record; the proposal is
   not otherwise altered, so the reasoning reviewed at decision time remains as reviewed.
4. Gate 1 remains valid and unmodified under v1.0.0. Its late record materialization was not a
   violation of the version then in force, and MUST NOT be re-characterized as one. Neither may
   the amendment be cited to claim Gate 1 complied with a rule that did not exist when it was
   decided.
5. The specification edit (FR-ORC-013) is applied but `specs/` is **not** committed. The
   specification remains Draft pending Gate 2.
6. Governance artifacts are committed as a single commit; nothing is pushed.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Plan-stage constitution check must enumerate principles against v1.1.0, and record the policy version evaluated | Plan gate |
| Every subsequent gate record co-committed with the artifact it approves | Each gate |
| Release readiness blocks on any unmaterialized gate outcome (condition 3 as amended) | Release readiness gate |
| `docs/governance/exceptions/` used for any policy exception raised under Principle VI | First exception |
| `docs/governance/change-control/` used for any change request or impact analysis | First change request |
| Gate 2 (specification approval) still open — AQ-001, AQ-002, AQ-003 unanswered | Specify gate |

Enforcement points carried forward from Gate 1 remain in force and are not superseded by this
decision: MTTR definition and measurement rules, 2–3 day timebox and scope controls, and versioned
API/schema deliverables with contract validation — all due at the Plan gate; technology-selection
ADR due at the ADR gate.
