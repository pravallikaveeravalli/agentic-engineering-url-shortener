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

**Fidelity**: record the decision verbatim where the human's own wording carries the reasoning. Do
not summarize away conditions, and do not add conditions the human did not state.

**Immutability**: a filed gate record's decision content — outcome, reasoning, conditions, dates —
is not edited afterwards. A changed decision is a new record that references the prior one.
Formatting-only revisions that alter none of the decision content may be made on the human owner's
explicit instruction, with that instruction noted in the commit message.

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

**Architecture Decision Records** — `docs/governance/adr/`, one file per decision named
`ADR-NNN-<slug>.md`, with an index at `README.md`. An ADR's `Status` is set to `Accepted` only by a
recorded human decision at the ADR gate, never by the acting agent. A superseded ADR is marked
superseded with a reference to the record replacing it, rather than edited in place.

**Cross-record orphan check** — every change-control package ends with one, before it is reported as ready and before
anything is applied. Records in this repository are immutable once applied and they quote each other's text, so a record
applied on Monday can be made false by a record drafted on Tuesday, and neither file shows it. The check asks five
questions of the package as a whole: does every `OLD` string a record quotes still exist in its target; does any record
quote text another record removes first; does any *already applied* record now assert something the new package
falsifies; do the records' stated application orders agree with each other; and does any survival of the thing being
removed remain unclaimed by any record. A falsified claim in an applied record is **never edited in place** — it is
corrected forward by a record in the current package, which is itself an orphan-check finding and is labelled as one.
The check has found real defects in every package it has been run on; treat a clean result as the exception to verify,
not the expectation.

## Lifecycle

SpecKit is the sole lifecycle framework. The authoritative stage order and the mandatory human gate
table are in the constitution's §Development Workflow and Quality Gates; this file does not restate
them. Active feature state is persisted in `.specify/feature.json`.
