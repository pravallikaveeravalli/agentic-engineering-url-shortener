# Operational Guidance

Repository-specific operational guidance, delegated by the constitution's §Runtime guidance. This
file states *how*; the constitution states *what* and *why*. Where the two conflict, the
constitution prevails, and this guidance MUST NOT be used to weaken, delay, or condition any
obligation stated there.

**Governing constitution**: `.specify/memory/constitution.md` v1.1.0.

## Gate decision records

Constitution §Gate semantics requires every mandatory gate decision to be materialized as a
repository artifact. This section defines where and how.

**Location**: `docs/governance/gate-decisions/gate-0N-<stage-slug>.md`, where `N` is the gate number
and `<stage-slug>` names the stage — for example `gate-01-constitution.md`, `gate-02-specify.md`.

**Trigger**: a formal gate decision from the human owner. Conversational assent, agreement, or
encouragement is not a gate decision and MUST NOT produce a gate record.

**Timing**: write the record to the working tree as soon as the decision is received, before
beginning the work it authorizes. Commit it together with the artifact it approves; when it approves
no artifact, commit it immediately on its own with a `docs(governance)` message.

**Required structure**:

1. A field table carrying, at minimum: gate, outcome, deciding human, decision date, artifact
   approved, and where else the decision is recorded.
2. `## Reason / verification performed` — the decision's reasoning and what was actually checked, in
   the deciding human's terms.
3. `## Conditions attached to the approval` — omit only if none.
4. `## Carried-forward enforcement points` — an item/due-at table; omit only if none.
5. `## Process note` — the division of labour between human and agent for this decision.

**Fidelity**: record the decision verbatim where the human's own wording carries the reasoning. Do
not summarize away conditions, and do not add conditions the human did not state.

**Immutability**: a filed gate record is not edited afterwards. A changed decision is a new record
that references the prior one.

## Other governance records

The same materialization, timing, verbatim-fidelity, and immutability pattern applies to the other
record classes the constitution requires. Required fields are defined by the constitution; the
locations are defined here.

**Policy exceptions** — `docs/governance/exceptions/`. Constitution §Exception procedure and
Principle VI require each exception to record the applicable policy, the precise clause, the reason,
the scope and duration, the approving authority, the compensating control, the residual risk, the
approval timestamp, and the expiry or review condition. An unapproved or expired exception is a
violation and blocks downstream progression.

**Change-control records** — `docs/governance/change-control/`. Constitution §Compliance and
Change-Control Policy Enforcement requires changes to approved requirements, architecture, schemas,
workflow states, security controls, or release criteria to pass through formal impact analysis and
change approval. Change requests and their impact analyses are materialized here.

**Amendment proposals** — `docs/governance/amendments/`. Change entries themselves live in the
constitution's `## Amendment History` section, not here.

## Lifecycle

SpecKit is the sole lifecycle framework. The authoritative stage order and the mandatory human gate
table are in the constitution's §Development Workflow and Quality Gates; this file does not restate
them. Active feature state is persisted in `.specify/feature.json`.
