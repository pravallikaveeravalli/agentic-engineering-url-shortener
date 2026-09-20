# ADR-010: Observability and Audit Model

## Status

**Accepted as amended** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4, subject to her scoping
of the fail-closed rule and the reclassification of `redirect_event` as domain analytics (see §Scoping of the
fail-closed rule). Decision record: `docs/governance/gate-decisions/gate-04-adr.md`.

## Context

Constitution IX requires a correlation identifier per run, recording of state transitions, decisions,
approvals, retries, failures, replanning events and terminal outcomes, and audit evidence carrying six
mandatory fields — actor type, action, timestamp, affected artifact or state, result, reason. NFR-AUD-001
requires audit records to be **immutable after write**. FR-ORC-023 requires reconstruction of a run
without the original session. `POL-AUD-001` makes the six-field assertion a **mandatory release-blocking**
policy check.

The MTTR method in plan §7 additionally requires a queryable population of failure events carrying nine
specific timestamps and attributes, with unrecovered failures excluded from the denominator and reported
separately, and human wait time excluded as a declared exclusion.

The question is where audit lives and how immutability is actually guaranteed.

**Assessment implication**: "audit-grade evidence" is an explicitly assessed behaviour. A reviewer will
try to answer reconstruction questions independently — and will notice if immutability is a promise
rather than a control.

## Decision Drivers

1. **Enforceable immutability** — a claim is not a control.
2. **Independent queryability** — reconstruction must not depend on the application under assessment.
3. **Six-field completeness as an executable check** — `POL-AUD-001`.
4. **MTTR computability** from a structured population.
5. **Separation of operational signal from governance evidence.**
6. **Complexity justification** — Constitution VII.

## Options Considered

### Option A — Audit as first-class relational data, separate from logs

- **Approach**: an append-only `audit_record` table plus a `failure_event` table, both in the same store.
  Structured logs, metrics, and traces exist **separately** for operational signal. Immutability enforced
  by privilege: the application role is granted INSERT and SELECT on audit tables and **denied UPDATE and
  DELETE**. Corrections are new rows referencing the corrected record.
- **Advantages**: immutability is a database-enforced control, not a convention — an attempt to mutate
  fails at the store. Fully queryable independently of the application, so a reviewer can reconstruct from
  SQL. `POL-AUD-001`'s six-field assertion becomes a real query. `failure_event` gives MTTR a structured
  population. Clean separation: operational signal is allowed to be lossy and rotated; governance evidence
  is not.
- **Disadvantages**: two places to look — logs for operations, tables for governance. Audit writes join the
  transaction path.
- **Risks**: audit write failure could block a business operation if not deliberately handled.
- **Implementation impact**: moderate; one audit writer, one privilege migration.
- **Assessment implications**: strongest — immutability demonstrable by a failing UPDATE.

### Option B — Audit derived from application logs

- **Approach**: emit structured log lines; treat the log stream as the audit trail; ship or parse as needed.
- **Advantages**: no extra tables; one emission path.
- **Disadvantages**: **immutability is unachievable** — logs rotate, are truncated, and are writable by
  whatever owns the file or stream. The six-field assertion cannot be executed as a check without parsing
  the whole stream, and absent fields would be discovered late or never. Reconstruction depends on log
  retention, which is an operational accident rather than a governance guarantee.
- **Risks**: `POL-AUD-001` becomes unenforceable; evidence disappears through routine rotation.
- **Assessment implications**: negative — fails NFR-AUD-001 on its own terms.

### Option C — External audit service or append-only log store

- **Approach**: ship audit events to a dedicated service or immutable log product.
- **Advantages**: purpose-built immutability; tamper evidence.
- **Disadvantages**: additional infrastructure a reviewer must run, for a single-host demonstration —
  unjustified complexity under Constitution VII, and it adds a dependency whose unavailability would need
  its own failure handling.
- **Risks**: reviewer cannot run the system; operational surface for no requirement.
- **Assessment implications**: over-engineered; the privilege-based control in A achieves the required
  property locally.

### Option D — Audit in the same tables as state, via history columns

- **Approach**: keep prior values in versioned columns on the state tables.
- **Advantages**: no separate audit table.
- **Disadvantages**: mixes current state with history, complicating both; cannot express the six-field
  shape (actor type, action, reason have no natural home on a state row); privilege separation becomes
  impossible because the same tables need UPDATE for ordinary state changes.
- **Risks**: immutability directly incompatible with the table's normal use.
- **Assessment implications**: negative — the privilege control that makes A credible cannot exist here.

## Decision

**Option A.**

- **`audit_record`** — append-only, carrying all six mandatory fields, validated against
  `contracts/audit-event.schema.json`. Corrections are appended with a `correctsEventId` reference; nothing
  is edited.
- **`failure_event`** — the MTTR population, carrying `failure_detected_at`, `recovery_started_at`,
  `recovery_completed_at`, `individual_recovery_duration`, `human_wait_duration`, `recovery_mechanism`,
  `recovered`, plus run and stage references.
- **Immutability by privilege**: the application role holds INSERT and SELECT on the **governance set** —
  `audit_record`, `failure_event`, `gate_decision`, `state_transition`, `policy_check_result`,
  `compensation_record` — and is **denied UPDATE and DELETE**. This is also the least-privilege control
  referenced in plan §8. `redirect_event` carries the same append-only privilege but is **not** part of the
  governance set; see §Scoping below.
- **Correlation identifier**: the run id propagates to every audit row, log line, metric label, and trace
  span. Application-plane requests carry their own request id, correlated where a run touches them.
- **Operational signals separately**: structured JSON logs, metrics, and traces for operations. These are
  explicitly **not** the audit trail and may be rotated.

### Scoping of the fail-closed rule (owner amendment, 2026-09-20)

An earlier draft stated the fail-closed rule unscoped: "an audit write failure is a permanent failure for the
operation it describes." The owner accepted the decision **as amended by her scoping**, which narrows that rule
to where it belongs and reclassifies one table. Recorded in three parts.

**(1) Fail-closed applies exactly to orchestrator governance writes** — `audit_record`, `failure_event`, gate
decisions, policy verdicts, state transitions, and compensation records. A governance write that cannot be
recorded means the governed action must not be treated as having happened.

> A human overseer consumes that trail, so an invisible hole in it is itself a governance failure, and the
> failure surfaces visibly through the CL-006 envelope — `UNAVAILABLE`, proposed transient, bounded retry,
> suspend per FR-ORC-017 — the path FR-ORC-004's own criteria already prescribe for a store outage.

So fail-closed does not introduce a novel failure mode: it routes into machinery already specified.

**(2) No public shortener operation writes an audit row.** Verified against every FR-URL requirement: create,
resolve, and analytics read produce no `audit_record` insert. Those paths are therefore **structurally
incapable** of audit-write failure rather than exempted from a rule that could otherwise bite them — a
distinction worth keeping precise, because an exemption invites erosion while a structural property does not.

**(3) `redirect_event` is reclassified as domain analytics data, not audit.** It keeps its append-only
privilege, grounded **independently** in FR-URL-010 via the Compensation Register, but its failure semantics
come **solely from ADR-014**: the append runs in a separate transaction, a failure is isolated and counted, and
the redirect still succeeds.

This reclassification is necessary for coherence, not cosmetic. EC-012 forbids the analytics append from
failing a resolvable redirect, which is the exact opposite of fail-closed. And a code-plus-timestamp-only event
(CL-002) **structurally cannot carry** NFR-AUD-001's six mandatory fields — it has no actor type, no action, no
result, no reason — so treating it as an audit record was a category error that would have made `POL-AUD-001`
unsatisfiable against it.

**Boundary case, explicitly retained as fail-closed**: orchestrator-initiated compensation that expires a link.
The actor is the orchestrator, its `compensation_record` sits in the governance set of part (1), and EC-022's
no-double-compensation guarantee **depends on that record existing**. The link expiry is a domain write; the
compensation record that authorises and bounds it is governance, and it fails closed.

## Rationale

The decisive property is that **immutability must be a control rather than a claim**, and privilege
separation gives that locally and cheaply: a reviewer can attempt an UPDATE against an audit table and
watch it fail. Option B cannot offer this in any form, which is why it fails NFR-AUD-001 outright rather
than merely weakly. Option C could offer it, but at the cost of infrastructure that Constitution VII would
require justifying, and the requirement is satisfied without it.

The second decisive property is **independent queryability**. FR-ORC-023's real test is whether someone
with database access and no session can reconstruct a run. Relational audit data makes that an SQL
exercise; a log stream makes it a parsing exercise dependent on retention, and Option D makes it impossible
to express the six-field shape at all.

The audit-write-failure rule deserves its own note. Treating it as permanent is a deliberate strictness:
the alternative — log and continue — would allow governed actions to occur with no record, which is the
precise condition Principle X's non-fabrication clause exists to prevent. Per the owner's scoping, that
strictness is confined to **orchestrator governance writes**, where a human overseer depends on the trail; it
does not reach public shortener operations, which write no audit rows at all, nor `redirect_event`, whose
failure semantics belong to ADR-014 because EC-012 requires the opposite behaviour.

## Consequences

**Positive**: immutability enforced by the store; reconstruction possible from SQL alone; `POL-AUD-001`
executable; MTTR computable over a structured population; governance evidence insulated from log rotation.

**Negative**: two surfaces to consult (logs for operations, tables for governance); audit writes sit on the
transaction path; the audit-failure-is-permanent rule will occasionally fail an operation that would
otherwise have succeeded — accepted deliberately.

**Operational**: one privilege migration; audit row growth proportional to activity, negligible at
demonstration scale. Retention interacts with **DF-003**, which remains open — the boundary rule between
audit retention and suspended-run idle retention is still the owner's to decide.

**Testing**: a test asserts an UPDATE against an audit table is rejected. Schema validation runs every
audit record against the audit-event contract. An MTTR test asserts unrecovered events are excluded from
the denominator and counted separately, and that human wait time is excluded per the declared exclusion.

**Governance**: `POL-AUD-001` (six fields) and `POL-AUD-002` (retention bounds) become executable. Every
figure derived from this data must be labelled a demonstration measurement (Constitution IX, NFR-AUT-003).

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Audit write failure blocking business operations | **Scoped away**: no public shortener operation writes an audit row, so create/resolve/analytics cannot be blocked by this rule. Within orchestrator governance writes the rule is deliberate — an unrecordable governed action must not be treated as having happened — and it routes through the CL-006 envelope to suspension rather than hard failure |
| `redirect_event` treated as audit, making `POL-AUD-001` unsatisfiable against it | Reclassified as domain analytics (owner amendment): a code-plus-timestamp event cannot carry the six mandatory fields, and EC-012 requires its failure to be isolated, not fail-closed |
| Privilege grants forgotten, leaving immutability nominal | The privilege migration is a Slice 1/7 deliverable; the failing-UPDATE test proves the grant is in force |
| Sensitive data written into `reason` or `affectedArtifact` | Secret scan (`POL-SEC-002`) covers captured telemetry and audit content; FR-URL-017's prohibition is non-waivable |
| MTTR misreported as a production statistic | Every output labelled demonstration measurement with its population and exclusions stated (plan §7) |
| Retention purging records needed for reconstruction | **Open**: DF-003 unresolved; flagged rather than assumed |

## Reversibility

**High** for the storage location — audit emission sits behind an `AuditWriter` interface, so Option C could
be added later as an additional sink without changing call sites. **Low** for the *shape*: the six mandatory
fields are constitutional, so the record structure is fixed by governance rather than by this decision.

## Traceability

- **Requirements**: FR-ORC-023 (audit inspection and reconstruction), FR-ORC-024 (reliability and
  orchestration measurements), FR-ORC-005 (lineage), FR-URL-017 (no secrets in telemetry),
  NFR-AUD-001/002, NFR-OBS-001/002, NFR-AUT-003.
- **Specification**: EC-028 (missing evidence at readiness), KE-19 (AuditRecord), KE-20
  (EvidenceArtifact), SC-004, SC-011, SC-012.
- **Plan**: §7 (all, including the MTTR method and its declared exclusions), §8 least privilege and audit
  integrity, §9 `POL-AUD-001`/`POL-AUD-002`, §14 Slice 7.
- **Contracts**: `contracts/audit-event.schema.json`.
- **Expected tasks**: Slice 7 audit writer, privilege migration, `failure_event` capture, MTTR query,
  failing-UPDATE test.
- **Open dependency**: **DF-003** — retention boundary, owner decision pending.
- **Related**: ADR-002 (privilege enforcement), ADR-008 (transition log), ADR-011 (testing).

## Validation

Executable: a test asserting UPDATE against an audit table is rejected by the store; schema validation of
every audit record against the audit-event contract; an MTTR calculation test over a seeded
`failure_event` population asserting the denominator excludes unrecovered events, that unrecovered events
are reported separately, and that human wait time is excluded; and the `quickstart.md` §5 reconstruction
exercise performed against the database by someone who did not run the workflow.
