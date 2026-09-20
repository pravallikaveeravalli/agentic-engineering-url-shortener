# Change Request CR-004 — DF-003 Resolved: Audit Retention Clock Starts at Run Termination

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20 |
| Approving authority | Pravallika Veeravalli (human owner), Gate 4 closing package |
| Raised by | Executing agent, implementing the owner's DF-003 resolution |
| Affected approved artifacts | `specs/001-agentic-sdlc-url-shortener/spec.md`; `plan.md` |
| Governing constitution | v1.1.0; `POL-CHG-001`, `POL-AUD-002` |
| Prior record | `docs/governance/gate-decisions/gate-04-closing-package.md` |

## The decision

**DF-003 — RESOLVED: the audit retention clock starts at run termination**, not record creation. The owner's
reasoning, verbatim:

> A run's history must never age out while the run is alive or freshly terminal; with idle retention and audit
> retention both at 90 days, a day-90 auto-abandonment must not coincide with its own earliest records becoming
> purgeable.

## Applied edits

| # | Location | Change |
|---|---|---|
| 1 | **PVT-010** conditions | Retention measured **from run termination**, not record creation; a run's records are not purgeable while it is live or suspended |
| 2 | **NFR-AUD-002** | Added that retention is anchored to run termination, so reconstruction under FR-ORC-023 cannot be defeated by a purge |
| 3 | **§Deferred Findings, DF-003** | Marked **RESOLVED**, owner's reasoning recorded, the chosen rule named |
| 4 | **New EC-043** | A retention purge runs while a suspended run's records are older than the retention window — must not purge, because the clock has not started |
| 5 | `plan.md` §Decisions Required | DF-003 row marked resolved |

## Why this rule rather than the alternative

Two candidate rules were recorded when DF-003 was raised: anchor audit retention to run termination, or set audit
retention strictly greater than idle retention. The owner chose the first. It is the stronger of the two because
it holds for **any** pair of retention values — including the 90/90 alignment she deliberately chose for
coherence — whereas the second would have required maintaining an inequality between two independently
configurable numbers, and would break silently the moment someone equalised them.

## Impact analysis

**Version impact**: MINOR on the specification — a constraint is added to an existing target and an NFR
clarified; nothing removed or redefined.

**Backward-compatibility impact**: none. No data exists.

**Affected consumers**: internal only — the retention/purge job and the audit store.

**Affected tests**: EC-043 — a purge attempted against a live or suspended run's records must remove nothing; a
purge after termination plus the retention window must remove them; and an assertion that a run
auto-abandoned at the idle-retention boundary retains its full history at that moment.

**Affected documentation**: `plan.md` §7 observability retention note; `data-model.md` needs no change, since
retention is a policy over rows rather than a field.

**Rollout / migration**: none. The purge job's query predicate is anchored to `workflow_run.terminal_state` and
its termination timestamp rather than to `audit_record.occurred_at`.

**Interaction recorded**: this closes the boundary the owner herself opened when she aligned PVT-015 (idle
retention) with PVT-010 (audit retention) at 90 days each. The alignment is sound; it simply needed the clock
rule to make it safe.

## Residual risk

Low. Audit rows for long-lived or long-suspended runs persist longer than 90 days from creation, which is the
intended consequence — storage cost at demonstration scale is negligible, and `POL-AUD-002` remains an advisory
check on retention being configured within declared bounds rather than a hard cap on row age.
