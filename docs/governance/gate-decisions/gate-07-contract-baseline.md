# Gate Decision Record — Gate 7: Contract and Schema Baseline

| Field | Value |
|---|---|
| Gate | Contract and schema change-control approval (**T031** — blocks T032, and therefore Slice 2's exit condition) |
| Outcome | **APPROVED**, with the record's version classification **corrected from PATCH to MINOR** at the gate |
| Deciding human | Pravallika Veeravalli |
| Decision date | 2026-09-21 |
| Artifact approved | `docs/governance/change-control/CR-036-slice-2-contract-baseline.md`, and through it the Slice-2 state of `contracts/openapi.yaml` and `contracts/README.md` |
| Governing constitution | v1.1.0 · **Policy set**: `policy-set-1.1.0` · `POL-CHG-001` |
| Recorded in | This record; CR-036's status and §2 Version impact; `tasks.md` T031; the commit landing both |
| Provenance of the text | **The two-item presentation was the supervisor's; the decision and its adoption are hers.** Recorded explicitly, as throughout this project |
| Delegation | **Not a delegated decision.** T031 is a mandatory `[GATE]`, which the owner's 2026-09-21 delegation protocol explicitly excludes from delegation. This is her own decision |

## Reason / verification performed

**Her decision, verbatim:**

> **Yes to both**

Answering a two-item presentation: approve the contract baseline **with the MINOR classification**, and
order the overclaim sweep. This record carries the first; the sweep is ordered separately and reported
at the Slice 2 boundary.

### What she approved

The Slice-2 contract baseline: 18 response examples added to `openapi.yaml`, and three edits to
`contracts/README.md` — a migration classification table, a concrete deprecation procedure, and a
correction to the recorded meta-schema lint status.

`POL-CHG-001` makes this gate necessary rather than optional: **a contract change without an approved
record is a mandatory release-blocking `FAIL`**. Both contract files changed in Slice 2, so the gate
falls here and not at release.

### What was actually verified before she was asked

- **43 fast-tier tests pass, 0 failures**, including the two new assertions: every JSON response
  declares an example, and every example validates against the schema its own response points at.
- **All 26 JSON responses** now carry an example. The gap was **measured, not estimated** — the test
  was written first and reported 18 of 26 missing, while its companion assertion passed, which
  established that the 8 pre-existing examples were already correct.
- **All five contract files** parse, compile as schemas, and are shown to *discriminate* — an empty
  object must be rejected, because a schema that accepts anything is decorative.
- `openapi.yaml` re-parses after the edits, and still declares 8 paths at `info.version: 3.0.0`.

### The correction she made, and why it was invited

**The record classified all three edits PATCH and then argued against its own label.** The owner read
that argument and set **MINOR**.

The argument the draft made against itself, which is the reason the correction was invited rather than
resisted:

- **The examples now carry binding governance rules.** The 403 example states that `actorType` accepts
  only `human` **and** that actor identity is declared rather than verified. The 409 states that a
  decision is not replaceable. A consumer reading those learns constraints they would otherwise have
  to infer, which puts the examples closer to specification than to decoration.
- **The deprecation procedure is newly mandatory.** It was *"deprecate across two versions"* — advice.
  It is now a two-step procedure with a stated failure mode, and it constrains the next person who
  wants to drop a column.

**CR-036's drafted PATCH reasoning is left standing in the record rather than rewritten.** The
disagreement is the useful part: a reader can see what was proposed, what was argued against it, and
which way the owner went. A record that silently reads MINOR would lose that.

### The disclosure she approved the baseline *with in view*

**A task already marked complete was incomplete, and the gate should not pass without it being said.**

T011's Artifact field names **all five** contract files. The implementation covered **two**.
`approval.schema.json`, `audit-event.schema.json` and `policy-evaluation.schema.json` shipped unlinted
while T011 sat ticked in `tasks.md`, and the T011 commit message claimed ADR-005's meta-schema residual
was discharged. **It was three-fifths discharged.** All five pass now, and `contracts/README.md` records
that the residual was owed twice rather than quietly closing it.

**Why this belongs in a gate record rather than only in a commit.** The gate freezes the contract
baseline. A reviewer who later finds that the baseline's own lint was overclaimed once is entitled to
see that the owner knew when she froze it. She did.

**The failure mode, named because it has now happened twice in two slices.** The worker wrote both the
implementation and its verification, so the verification inherited the worker's misreading of the task.
Nothing in a red-green cycle can catch that — the test passed the code because the same reading produced
both. Only reading a *different* artifact exposed it: CR-035's instance surfaced from the task graph,
this one from `contracts/README.md`'s five-file table. **That is what the sweep ordered alongside this
gate exists to address.**

## Conditions attached to the approval

**One, and it is discharged by this record.** CR-036's §2 Version impact must read **MINOR**, with the
correction attributed to the gate rather than presented as the original classification. Applied before
this record was filed; CR-036 §2 carries it.

No condition is attached to the contract content itself. The baseline is approved as it stands.

## Carried-forward enforcement points

| Item | Due at | Enforced by |
|---|---|---|
| **The contract baseline is frozen.** Any further change to either file needs its own record, classified against `contracts/README.md`'s table | Every subsequent contract change | `POL-CHG-001`; a change without a record is a mandatory release-blocking `FAIL` |
| **The examples must keep validating.** An example is the artifact most likely to drift, because changing a schema does not break an example the way it breaks code | Every build | `ResponseExamplesTest`, fast tier. T030's guard: a non-validating example is *worse than none* |
| **All five contract files stay linted.** The two-file version of this check is what let three schemas ship unchecked | Every build | `ContractFilesLintTest`, extended at this gate |
| **The overclaim sweep** — for every completed task, does its validation assert everything the task's Artifact field names? | One-time now; then **every slice boundary** | Ordered by the owner with this gate. Findings fixed red-first where code, recorded where record; register or plan text through change control |
| **The migration deprecation procedure** — two migrations to remove a column, never one | The next migration that removes or renames | `contracts/README.md`, newly mandatory as of this gate. The live example is V1's superseded `api_key_hash` and `key_expires_at`, deliberately not dropped by V2 |
| **T032 is unblocked** and is Slice 2's exit condition | Immediately | `tasks.md` T032; Slice 2 does not close until the walking skeleton round-trips against a real store |

## What this gate does not do

It does not approve the walking skeleton, any implementation beyond the contract documents, or the
Slice 2 exit condition. It freezes the contract baseline so that T032 has a stable document to conform
to, and it arms the enforcement points above.
