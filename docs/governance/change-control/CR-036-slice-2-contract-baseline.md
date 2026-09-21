# Change Request CR-036 — The Contract Baseline: Response Examples, Migration Rules, and a Reopened Lint Residual

| Field | Value |
|---|---|
| Status | **APPROVED at Gate 7** — 2026-09-21, Pravallika Veeravalli. **Classification corrected PATCH → MINOR at the gate**, on her decision. |
| Raised by | Task **T031**, which requires a change-control record for the contract baseline before any contract-dependent implementation proceeds |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-21**, Gate 7 (`docs/governance/gate-decisions/gate-07-contract-baseline.md`) |
| Affected approved artifacts | `contracts/openapi.yaml` (18 response examples added), `contracts/README.md` (migration classification, deprecation procedure, lint-status correction) |
| Governing constitution | v1.1.0 — **not amended**. `POL-CHG-001` |
| Application order | Sole record. Applies to the state left by CR-021..CR-035. **Already applied to the working tree**; this record exists to have the change approved, per T031's Done condition |
| Task count | **173 task lines, 172 actionable** — unchanged |

## What this gate is for

T031's guard states the stakes plainly: **a contract change without this record is a mandatory
release-blocking `FAIL`** (`POL-CHG-001`). Slice 2 changed both contract files, so the gate is
reached now rather than at release.

**It is also the first gate where the record is the artifact and the work is already done.** The edits
are in the working tree and their tests pass; what is pending is your approval of them. T032 — the
walking skeleton — depends on this gate, so Slice 2's exit condition is not reachable until you
decide.

---

## The eight impact fields `POL-CHG-001` requires

### 1. Owner

Pravallika Veeravalli. The changes were drafted by the worker under the approved task plan; the
decision is hers.

### 2. Version impact

**MINOR for both files — corrected at the gate.**

> **The gate corrected this field.** The record as drafted classified all three edits **PATCH**, and then argued against its own label in the paragraphs below. The owner read the argument and **adopted MINOR**: the response examples now carry binding governance rules, and the migration deprecation procedure is newly mandatory rather than advisory. Her words, verbatim: **"Yes to both."**
>
> The drafted reasoning is left standing below rather than rewritten, because the disagreement is the useful part of the record: a reader can see what was proposed, what was argued against it, and which way the owner went.

**As drafted — PATCH, with the argument against it:**

| Change | Classification | Why |
|---|---|---|
| 18 response examples added to `openapi.yaml` | **PATCH** | The table in `contracts/README.md` classifies *"Wording, description, example fixes"* as PATCH, with a record required *"only if a consumer-visible meaning shifts"*. No schema, path, status code or field changed. |
| Migration classification table + deprecation procedure in `README.md` | **PATCH** | Documentation of rules that already bound. Nothing newly constrains a consumer. |
| Lint-status correction in `README.md` | **PATCH** | A statement of fact about what was verified. |

**Why a record exists anyway despite PATCH not requiring one — and why this argument carried.** Two of the three edits *do* shift a consumer-visible meaning, and pretending otherwise would be exactly the goalpost-moving the classification table exists to prevent. **The owner agreed and set MINOR:**

- **The examples now state governance rules a consumer will act on.** The 403 example says
  `actorType` accepts only `human` *and* that actor identity is declared rather than verified. The 409
  says a decision is not replaceable. A consumer reading those examples learns constraints they would
  otherwise have to infer, so the examples are closer to specification than decoration.
- **The migration deprecation procedure is newly binding on a future change.** It was "deprecate
  across two versions" before, which is advice; it is now a two-step procedure with a stated failure
  mode. That constrains the next person who wants to drop a column.

**Resolved: MINOR.** The draft asked to be corrected rather than defended, on the ground that a future reviewer finding a MINOR change filed as PATCH is worse than being corrected at the gate. The owner corrected it. Under `contracts/README.md`’s table, MINOR requires a change-control record — which is this record — and no new path prefix, since neither document has been served.

### 3. Backward-compatibility impact

**None.** Neither document has ever been served and neither has a consumer. Under the pre-first-service
rule in `contracts/README.md`, even a MAJOR change applies in place without a path prefix at this
stage — and nothing here is MAJOR.

### 4. Affected consumers

**None today.** After Slice 2's T032, the walking skeleton becomes the first consumer of
`openapi.yaml` through T012's conformance harness, which is why this gate precedes it.

### 5. Affected tests

| Test | Change |
|---|---|
| `ResponseExamplesTest` | **New (T030).** Two assertions, kept separate so a failure names which problem it is: every JSON response declares an example, and every example validates against its own schema. |
| `ContractFilesLintTest` | **Extended.** Covered two files; now covers all five, and the three schema files gain a compile-and-discriminate check. |

Both are fast-tier. **43 fast-tier tests pass, 0 failures.**

### 6. Documentation

`contracts/README.md` carries all three README edits. No other document changes.

### 7. Rollout and migration

**None.** No schema migration, no data change, no deployment step. `V2__domain.sql` and
`V3__governance.sql` landed under T026 and T027 and are not part of this record.

### 8. Approval

**Human owner, and it is the gate.** T031's Done condition is that the record is *"materialized and
approved before any contract-dependent implementation proceeds"*, and T032 is that implementation.

---

## The finding this gate should not pass without you seeing

**A task I had already marked complete was incomplete, and the contract baseline is where it
surfaced.**

T011's Artifact field says a test asserting **all five** contract files parse and validate. My
implementation covered **two**. `approval.schema.json`, `audit-event.schema.json` and
`policy-evaluation.schema.json` were shipped unlinted while T011 sat ticked in `tasks.md`.

**How it surfaced**: T029 required the README's rules to cover all five files, so I read the five-file
table — and the table did not match my test's two-file list.

**What it means for ADR-005.** ADR-005 disclosed the meta-schema lint as an owed residual. T011 was
supposed to discharge it. It discharged three-fifths of it, and the commit message said it was
discharged. All five now pass, and `contracts/README.md` records that the residual was owed twice
rather than quietly closing it.

**Why it matters beyond these three files.** The pattern is the one CR-035 already flagged: I wrote the
task's implementation *and* its verification, so the verification inherited my misreading of the task.
Nothing in the red-green cycle could catch it, because the test I wrote passed the code I wrote. Only
reading a *different* artifact — the README — exposed the gap.

**This is a second instance of that failure mode in two slices.** CR-035 recorded the first and flagged
that the other eight slice exit conditions had not been checked for reachability. This one suggests the
same question is worth asking of task *artifacts*: not "does my test pass?" but "does my test assert
everything the task's Artifact field names?" I have not done that sweep, and it is not in this record's
scope.

---

## Post-application verification

Already executed; results recorded because the edits are in the tree.

| # | Check | Expected | Result |
|---|---|---|---|
| V1 | `ResponseExamplesTest` — every JSON response has an example | pass | **EXECUTED: 2/2 pass** — HOLD |
| V2 | `ResponseExamplesTest` — every example validates against its schema | pass | **EXECUTED: pass** — HOLD |
| V3 | `ContractFilesLintTest` covers all five files | 5 | **EXECUTED: 4/4 tests pass, five files asserted** — HOLD |
| V4 | the three previously-unlinted schemas compile and discriminate | pass | **EXECUTED: pass** — HOLD |
| V5 | `openapi.yaml` parses as YAML after the edits | parses | **EXECUTED: YAML OK** — HOLD, after a self-inflicted defect (below) |
| V6 | full fast tier | 0 failures | **EXECUTED: 43 tests, 0 failures, 0 errors** — HOLD |
| V7 | `openapi.yaml` still declares 8 paths and `info.version: 3.0.0` | unchanged | **EXECUTED: unchanged** — HOLD |
| V8 | README states the lint correction | 1 | **EXECUTED: present** — HOLD |

**V5 records a defect of mine, caught by the harness.** My first insertion used a two-line implicit
string continuation, which is not valid YAML. `ContractFilesLintTest` failed on the parse at line 230
column 113; four lines were joined and the document re-parsed clean. That is T011 doing the job it
exists for, on the same run in which its own coverage gap was being fixed.

## Cross-record orphan check

| Question | Result |
|---|---|
| Does every quoted `OLD` string exist? | No `OLD`/`NEW` pairs: the edits are additions plus one corrected sentence. |
| Does another record edit these files? | **No.** CR-029 last touched both contract files and is applied; nothing between CR-030 and CR-035 touches them. |
| Does any **applied** record now assert something this falsifies? | **Yes, one, and it is corrected forward.** **CR-029's** verification recorded the contract files as re-parsed and conformant — true for the two files it changed, and it did not claim the other three were linted. But my **T011 commit message** did claim the residual was discharged, and that claim was three-fifths true. A commit message is not a change record and cannot be corrected forward the same way, so the correction lives in `contracts/README.md` where a reader will meet it. |
| Do application orders agree? | Sole record. |
| Unclaimed survivals? | None: the change is additive. |
| Is the validation suite inside the search space? | **Yes, and it was the thing at fault.** `ContractFilesLintTest` asserted less than T011's Artifact required. Extended rather than replaced, so the history of what it used to check stays visible in the diff. |

## Residual risk

**The examples will drift, and nothing will notice except this test.** An example is the artifact most
likely to fall out of step with a schema, because changing a schema does not break an example the way
it breaks code. `ResponseExamplesTest` is the only thing standing between a reader and a confidently
wrong example — which is why T030's guard says a non-validating example is *worse than none*.

**The 18 examples are demonstration values, not measurements.** Timestamps, run identifiers and short
codes are invented. They are labelled as examples by the document's own structure, so no separate
disclosure is needed — but they should not be read as recorded output from a run.
