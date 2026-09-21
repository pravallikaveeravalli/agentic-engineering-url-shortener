# Change Request CR-010 — PVT-009 as an Observable Append-Failure Ceiling; Escalation Thresholds; Stale Resolution Statuses Corrected

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, from `/speckit-analyze` findings **C1** (CRITICAL), **H1**, **M3**, **L1**, **L2** |
| Revision | **Draft 3**, 2026-09-20 — owner **Decision A** recast the timeout model as escalation thresholds (Edits 4 and 9). **Edit 10 withdrawn** under owner **Decision H**: with no retention path anywhere, the Compensation Register needs no lifecycle distinction and stays exactly as approved |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"go ahead."* |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/spec.md` (approved Gate 2, clarified Gate 3, amended CR-001..CR-007) |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decisions | Owner **Decision 1** (PVT-009) and **Decision A** (escalation thresholds), 2026-09-20; ADR-014 Condition 3; CR-003; Gate 4 closing package |
| Application order | **First in the package.** CR-014 and CR-017 also edit `spec.md`, at disjoint locations, and apply after this record in that order. |

## Why this change exists

`/speckit-analyze` found a CRITICAL defect: **DF-001 is recorded RESOLVED on the strength of a redefinition that
was never written into the artifact that binds implementation.**

ADR-014 §Decision item 4 and its Condition 3 redefine PVT-009 as *"a failure-rate ceiling, not a loss budget"*.
`spec.md` §Validation Targets says so in its preamble. The **table row itself was never edited** and still reads
`≤ 0.5% loss`. DF-001's own text states why that matters: *"Exact-append and a 0.5% loss budget cannot both
hold."* It still does not hold. An implementer reading the binding table would build to a permitted-loss target
against a requirement that permits none.

The root cause is visible in the artifact's own shape: **DF-002 and DF-003 each carry a `**Resolution**:`
paragraph naming what changed; DF-001 does not.** It was marked resolved in the status table alone. Edit 2 closes
that asymmetry so the same omission cannot recur for a future finding.

## Owner decision recorded — Decision 1, verbatim grounds

> PVT-009 is restated as an **observable append-failure ceiling, keeping 0.5%**: under load at PVT-003
> concurrency, at most 0.5% of analytics appends may fail; every failure MUST be counted and visible via the
> recording port's failure counter; no failure is ever silent; no failure blocks or delays the redirect.

Acceptance grounds, recorded as the owner stated them:

1. **Same number, strictly stronger promise** than the loss budget it replaces. A loss budget permits events to
   vanish unremarked. A failure ceiling permits a bounded number of *counted, visible* failures and no silent
   ones.
2. **0% was rejected because it cannot survive its own fault-injection test.** When the database is deliberately
   killed, failures must happen and be counted — that is the design working.
3. **A target unfalsifiable under fault injection is not a target.**

## Owner approval — 2026-09-20

Approved by **Pravallika Veeravalli**: *"go ahead."*

Her approval followed a three-item list and carries all three, recorded as relayed:

1. **The package as revised**, including the §11 brownfield wording.
2. **PVT-010 explicitly withdrawn** — the binding 30/90-day deletion target is formally retired and replaced by the
   documented production recommendation, **by her explicit decision, not by implication**.
3. **Policy set `1.1.0`**, on the ground she herself set for the API contract: **version discipline binds from first
   real use**, and no run has ever evaluated `1.0.0`.

## Applied edits

### Edit 1 — §Validation Targets, PVT-009 row (`spec.md:1287`)

**OLD**

```
| PVT-009 | Analytics recording tolerance under load | ≤ 0.5% loss | Analytics must never take down a redirect (FR-URL-010) | At PVT-003 |
```

**NEW**

```
| PVT-009 | Analytics append-failure ceiling, **observable** | ≤ 0.5% of appends may **fail**, every failure counted and visible, **zero silent failures** | **Redefined by ADR-014 from a loss budget to a failure ceiling — the same number, a strictly stronger promise.** A loss budget permits events to vanish unremarked; a failure ceiling permits a bounded number of *counted, visible* failures and no silent ones. 0% was rejected: it cannot survive its own fault-injection test — when the store is deliberately killed, appends must fail and be counted, and that is the design working. A target unfalsifiable under fault injection is not a target | At PVT-003 concurrency. Measured from the **recording port's failure counter** (ADR-014 Condition 2), which every append passes through. No append failure may block or delay the redirect (EC-012, FR-URL-010) |
```

### Edit 2 — §Deferred Findings, DF-001 section heading and resolution paragraph (`spec.md:1393`)

**OLD**

```
### DF-001 — Analytics exactness contradiction *(contradiction in the approved spec)*

- **Finding**: PVT-009 proposes an analytics recording tolerance of "≤ 0.5% loss" under load, while
```

**NEW**

```
### DF-001 — Analytics exactness contradiction *(contradiction in the approved spec)* — **RESOLVED 2026-09-20**

**Resolution**: the analytics append is **synchronous in a separate transaction**, its failure isolated, counted
and surfaced, and the redirect succeeds regardless (ADR-014, accepted with three conditions at Gate 4). **PVT-009
is retained and redefined**: it is an *observable append-failure ceiling*, not a loss budget — at most 0.5% of
appends may fail, every failure is counted and visible through the recording port's failure counter, and no
failure is silent or blocking (CR-010). The contradiction the finding names is therefore dissolved rather than
traded away: FR-URL-010's "appends an event" and SC-001's 100% correctness both hold, because SC-001 is scoped to
redirect correctness and analytics completeness is governed by PVT-009 as redefined. Original finding retained
below for provenance.

- **Finding**: PVT-009 proposes an analytics recording tolerance of "≤ 0.5% loss" under load, while
```

### Edit 3 — §Validation Targets, PVT-010 conditions cell (`spec.md:1288`)

**OLD**

```
(CR-003/CR-004 — DF-003 resolved)
```

**NEW**

```
(CR-004 — DF-003 resolved)
```

*Ground: CR-003 resolved redirect permanence (DF-002). DF-003's resolution is CR-004 alone. A wrong citation in a
traceability instrument is a defect even when the adjacent claim is true.*

**PVT-010's *status* changes separately.** Owner **Decision H** revises retention from a binding target to a recorded
**production recommendation**, which rewrites this row's Target and Conditions cells. That edit lives in **CR-017**;
this record touches only the citation.

### Edit 4 — §Validation Targets, new PVT-016 row appended to the table

Closes **M3** under owner **Decision A**. Constitution VIII requires timeout behavior *"defined explicitly"*; the
gating *semantics* were settled by CL-006 rule 4 but the **durations were nowhere stated** — `plan.md` §3 carried
only *"Short"*, *"Long timeout"*, *"PVT-007"*, and PVT-007 bounds attempts and backoff, not elapsed time.

**Decision A changes what these numbers are.** They are **escalation thresholds, not kill-timers.** The owner's
design, recorded verbatim:

> the orchestrator should check if the stuff that is supposed to happen is happening. And then maybe like if it goes
> past the specified timeout, it will ask the user if they are okay to proceed or if they want to kill the stage…
> because it's taking too long.

**NEW row**

```
| PVT-016 | Per-node **escalation thresholds** | See §Per-node escalation thresholds | Constitution VIII requires timeout behavior defined explicitly, and the durations were nowhere stated. These are the elapsed times at which the orchestrator **asks the human**, never automatic kill-timers: a breach escalates with elapsed-versus-expected and the last observed activity, offering **keep waiting** or **kill the node**. This unifies every node with the human-decision model already used at stages 4 and 11 — one rule: threshold reached, ask the human | Configuration; demonstration runs may compress values, labelled per AS-007. A **kill** decision fails the node by overrun, after which the existing failure classification and idempotency gating apply **unchanged** (FR-ORC-014 rule 4) — the threshold sets **duration, never retry eligibility**. **No answer** follows the gate-wait deadline (PVT-006) to suspension: silence is never approval (Constitution III) |
```

**NEW subsection**, placed immediately after the §Validation Targets table:

```
### Per-node escalation thresholds (PVT-016)

| Node | Threshold | Ground |
|---|---|---|
| S1 Ingestion | 5 s | Deterministic: validate a submission and write one row. A slow S1 is a store problem, which the envelope already classifies |
| S2 Normalization | 120 s | One AI call through the CLI subprocess: process startup plus model latency on a short prompt |
| S3 Ambiguity detection | 120 s | Same shape as S2 |
| S4 Human clarification | **N/A** | Already waiting on a human. Its threshold *is* the gate wait (PVT-006), which is the model every other node now follows |
| S5 Decomposition | 180 s | Longer output than S2/S3: a dependency-ordered task set |
| S6 Architecture & design | 300 s | Longest prompt and output: design plus, for a brownfield change, the seven-dimension impact analysis |
| S7 Implementation | 600 s **per node** | AI authors, the engine applies on a branch, the build runs. Per fan-out child, not per stage |
| S8 Testing | 1800 s | The real suite including Testcontainers startup. The only node whose elapsed time is dominated by our own tests, so the threshold must not be tight enough to make a slow machine look stalled |
| S9 Documentation | 180 s | AI call over the run's delivered change and results |
| S10 Security & policy | 600 s | Deterministic but dominated by the dependency-vulnerability scan, which fetches advisory data |
| S11 Release readiness | 30 s evaluation, then **PVT-006** | The evaluator reads persisted rows; the wait that follows is already a human decision |
| S12 Final summary | 60 s | Deterministic assembly from persisted evidence |

**What a breach does** — four steps, none of them automatic:

1. The orchestrator **escalates to the human**, presenting elapsed versus expected and the **last observed activity**
   for that node. Liveness is read from telemetry that already exists — the node's state-transition history, its
   trace spans and its audit events. **No heartbeat or watchdog subsystem is introduced** (Constitution VII's
   prohibition on unjustified complexity).
2. The human chooses **keep waiting** — the threshold is re-armed and the node continues — or **kill the node**.
3. A **kill** fails the node by overrun. From that point the existing machinery applies with nothing added: the
   failure crosses the boundary in the standard envelope, the two-vote rule decides retry eligibility, and a node
   whose effect is not declared idempotent is **not** retried (FR-ORC-014 rules 2 and 4, EC-033).
4. **No answer** is not a third option. The gate-wait deadline (PVT-006) elapses and the run suspends
   (FR-ORC-017). Silence advances nothing, here as everywhere.

**Counter-case, weighed and accepted by the owner, recorded because a decision without its counter-case is not a
decision**: an unattended run now **stalls and then suspends** where an automatic kill-and-retry might have
self-healed. The owner accepted this on the ground that **auto-retrying possibly-half-finished work is the exact
danger the retry rules exist to prevent** — a threshold breach means completion is *unknown*, which is the same
epistemic state as a timeout, and suspension is the declared safe outcome for it.
```

**Consequence this edit forces, flagged for the owner rather than absorbed.** A threshold breach is a **human
decision point with a deadline and a silence path**, which is precisely what a gate is. Implementing it faithfully
therefore adds a **tenth gate class — "node overrun"** — to `plan.md` §5's nine, carrying the five standard outcomes
plus two named choices (keep waiting / kill), in the same shape the no-change-plan gate carries three. That is a
consequence I derived from Decision A, **not a decision I made**; it is routed in CR-015 and CR-016, and it moves
finding **M2**'s count fix from *nine* to *ten*. If the owner intends the overrun escalation to be something
lighter than a gate class, it cannot inherit the deadline-and-suspension machinery Decision A relies on, and that
trade needs her call.

### Edit 5 — §Ambiguities, AQ-004 entry (`spec.md:1371-1372`)

**OLD**

```
- **AQ-004** — *Still open.* Whether redirects should be permanent or temporary by default (CN-006
  defers the mechanism; the caching and analytics consequences differ). → Deferred Findings DF-002.
```

**NEW**

```
- **AQ-004** — *Resolved at the Gate 4 closing package, 2026-09-20 (CR-003).* Redirects are **temporary, never
  permanent**; the exact status code remains an implementation detail within the temporary class (CN-006).
  → FR-URL-007, Deferred Findings DF-002.
```

*This is the edit CR-003 recorded as made. It was not made. See §Anti-recurrence below.*

### Edit 6 — Status header (`spec.md:9-12`)

**OLD**

```
**Status**: **Approved at Gate 2, 2026-09-18**; clarifications resolved at **Gate 3, 2026-09-19** by
Pravallika Veeravalli. AQ-001..003 resolved at Gate 2; AQ-005, AQ-006 resolved at Gate 3. AQ-004 and
four further items remain open and are recorded under Deferred Findings for disposition at the Plan
gate.
```

**NEW**

```
**Status**: **Approved at Gate 2, 2026-09-18**; clarifications resolved at **Gate 3, 2026-09-19** by
Pravallika Veeravalli; amended by **CR-001..CR-007** and by the Gate 4 closing package, 2026-09-20.
**All six ambiguities are resolved**: AQ-001..003 at Gate 2, AQ-005 and AQ-006 at Gate 3, and **AQ-004 at the
Gate 4 closing package (CR-003)**. Of the five Deferred Findings, **DF-001, DF-002, DF-003 and DF-005 are
resolved or closed; DF-004 alone remains open, by design and out of scope.**
```

### Edit 7 — §Reading Guide, `AQ-nnn` marker row (`spec.md:34`)

**OLD**

```
| **AQ-nnn** | Ambiguity. AQ-001..003 were blocking and are resolved at Gate 2 (see Clarification Log). AQ-004..006 remain open, deferred to `/speckit-clarify`. |
```

**NEW**

```
| **AQ-nnn** | Ambiguity. All six are resolved: AQ-001..003 at Gate 2 (Clarification Log), AQ-005 and AQ-006 at Gate 3, AQ-004 at the Gate 4 closing package (CR-003). |
```

### Edit 8 — FR-ORC-032 out-of-sequence placement note (`spec.md:768`)

Closes **L1** without moving approved text. Moving the block would renumber nothing but would break every line
reference a reviewer or gate record holds, for a cosmetic gain. The honest fix is to state why the position is
what it is.

**OLD**

```
- **FR-ORC-032** — *Idle retention and auto-abandonment.* **[Confirmed — CL-005]** A suspended run
```

**NEW**

```
- **FR-ORC-032** — *Idle retention and auto-abandonment.* **[Confirmed — CL-005]** *(Placed here, out of numeric
  sequence, deliberately: CL-005 introduced it as the completion of FR-ORC-017's safe-stop semantics and set it
  adjacent to them. The identifier was taken at creation time — identifiers are never pre-allocated — so the
  number follows the registry and the position follows the reasoning.)* A suspended run
```

### Edit 9 — FR-ORC-014 gains rule 6, threshold escalation (`spec.md:673-710`)

Decision A introduces orchestrator behaviour the specification does not currently state. A number in
§Validation Targets binds nothing on its own; the obligation has to live in the requirement.

**NEW rule**, appended after FR-ORC-014's rule 5:

```
  6. **Threshold escalation, never an automatic kill.** Each node declares an elapsed-time **escalation threshold**
     (PVT-016). On breach the orchestrator MUST escalate to the human — presenting elapsed versus expected and the
     node's last observed activity, drawn from existing state-transition, trace and audit records — and MUST offer
     exactly two choices: **keep waiting**, which re-arms the threshold, or **kill the node**. A kill fails the node
     by overrun, after which rules 1–4 apply unchanged, so a node whose effect is not declared idempotent is still
     not retried. The orchestrator MUST NOT kill a node on its own authority, and MUST NOT treat a breach as a
     failure until a human decides. **Absence of an answer follows the gate-wait deadline to suspension**
     (FR-ORC-017); it is never a kill and never an approval.
```

and appended to FR-ORC-014's **Accept**:

```
; a breached escalation threshold produces a human escalation carrying elapsed-versus-expected and last-observed
activity, and each of the two choices produces its defined effect
```

and to its **Reject (negative)**:

```
; a node MUST NOT be killed without a recorded human decision; a threshold breach MUST NOT be recorded as a failure
before that decision; and no new heartbeat or watchdog component may be introduced to observe liveness — existing
telemetry is the source
```

and to its **Evidence**:

```
; threshold-breach escalation records for both choices, and a silence case proving suspension rather than kill
```

*Ground for the no-new-machinery clause: it is the owner's constraint — do not build new machinery — expressed as a
negative acceptance criterion, so it is testable rather than aspirational.*

### Edit 10 — WITHDRAWN under owner Decision H

Draft 2 added a note to §Compensation Register distinguishing *corrective deletion* (forbidden) from a *retention
lifecycle* (permitted as an archival move). **Withdrawn.** Decision H removes every retention path from the system, so
there is no lifecycle to distinguish and **the register stays exactly as approved** — its *"never delete"* rows are now
**literally true system-wide** rather than true-by-convention, which is a stronger position than the distinction would
have bought.

Recorded as withdrawn rather than deleted, so the register's untouched state is visibly a decision and not an
oversight. The retention posture itself is stated in **CR-017**.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR.** Edit 1 changes what an acceptance test must assert; Edits 4 and 9 add a binding target and a new orchestrator obligation; Edit 10 is withdrawn. None removes an obligation — PVT-009 becomes stricter in character, PVT-016 and rule 6 are additive, and rule 6 moves authority **toward** the human. Edits 2, 3, 5, 6, 7, 8 are PATCH-class corrections of statements decided elsewhere, bundled here because they touch the same artifact and must be verified together. |
| **Backward-compatibility impact** | None. No interface, no consumer, nothing implemented. |
| **Affected consumers** | `tasks.md` — T050 gains the ceiling as an assertion; new T145d measures it; T086 and the plan's §3 column consume PVT-016 as **thresholds**; a **tenth gate class** follows from Edit 9. All routed through **CR-015** and **CR-016**. The specification's retention clauses are routed through **CR-017**. |
| **Affected tests** | **New:** PVT-009 ceiling measurement under load (T145d); threshold-breach escalation tests for both choices plus a silence case (T086 extension); an assertion that **no new liveness component exists**. **Unchanged:** T050's EC-012 test — Edit 1 adds the *rate* assertion the spec previously lacked. All in CR-016. |
| **Affected documentation** | `plan.md` §2 analytics row, §3 timeout column, §6 timeout row (CR-015). `quickstart.md` needs no PVT-009 text. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner. Edit 1 restates a **binding acceptance threshold**; Edit 4 creates one. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

This section is the anti-recurrence control. **Two change requests in this repository (CR-001, CR-008) recorded
edits they did not make.** Each row below is an executable check whose **actual output must be pasted into the
Result column** before the Status field may change. An unexecuted row is a blocking defect in this record, not a
formality.

| # | Edit | Verification command (from repo root) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -n '0.5% loss' specs/001-agentic-sdlc-url-shortener/spec.md` — then assert the **PVT-009 table row** is not among the hits | **2 hits, both inside DF-001's retained original finding**; the binding row is clean | **EXECUTED: 1** — HOLDS |
| V2 | 1 | `grep -c 'append-failure ceiling' specs/001-agentic-sdlc-url-shortener/spec.md` | `≥ 2` (preamble + row) | **EXECUTED: 2** — HOLDS |
| V3 | 2 | `grep -n 'DF-001 — Analytics exactness contradiction' specs/001-agentic-sdlc-url-shortener/spec.md` | line ends `**RESOLVED 2026-09-20**` | **EXECUTED: 1** — HOLDS |
| V4 | 3 | `grep -c 'CR-003/CR-004' specs/001-agentic-sdlc-url-shortener/spec.md` | `0` | **EXECUTED: 0** — HOLDS |
| V5 | 4 | `grep -c 'PVT-016' specs/001-agentic-sdlc-url-shortener/spec.md` | `≥ 4` (row, subsection heading, rule 6, cross-ref) | **EXECUTED: 5** — HOLDS |
| V6 | 4 | `grep -c 'escalation threshold' specs/001-agentic-sdlc-url-shortener/spec.md` | `≥ 4` | **EXECUTED: 6** — HOLDS |
| V6b | 4 | `grep -c 'kill-timers' specs/001-agentic-sdlc-url-shortener/spec.md` | `1` — the negative framing is explicit, not implied | **EXECUTED: 1** — HOLDS |
| V6c | 4 | `grep -c 'keep waiting' specs/001-agentic-sdlc-url-shortener/spec.md` | `≥ 2` | **EXECUTED: 3** — HOLDS |
| V7 | 5 | `grep -c 'Still open' specs/001-agentic-sdlc-url-shortener/spec.md` | `0` | **EXECUTED: 0** — HOLDS |
| V8 | 6 | `grep -c 'four further items remain open' specs/001-agentic-sdlc-url-shortener/spec.md` | `0` | **EXECUTED: 0** — HOLDS |
| V9 | 7 | `grep -c 'AQ-004..006 remain open' specs/001-agentic-sdlc-url-shortener/spec.md` | `0` | **EXECUTED: 0** — HOLDS |
| V10 | 8 | `grep -c 'out of numeric' specs/001-agentic-sdlc-url-shortener/spec.md` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V11 | all | `grep -c '^- \*\*FR-' specs/001-agentic-sdlc-url-shortener/spec.md` | `51` — **unchanged**; this CR adds no requirement | **EXECUTED: 51** — HOLDS |
| V12 | all | `python3 -c "import re,pathlib;t=pathlib.Path('specs/001-agentic-sdlc-url-shortener/spec.md').read_text();print(len(set(re.findall(r'PVT-\d{3}',t))))"` | `16` (15 approved + PVT-016) | **EXECUTED: 16** — HOLDS |
| V13 | 9 | `grep -c 'MUST NOT kill a node on its own authority' specs/001-agentic-sdlc-url-shortener/spec.md` | `1` | **EXECUTED: 1** — HOLDS |
| V14 | 9 | `grep -c 'no new heartbeat or watchdog' specs/001-agentic-sdlc-url-shortener/spec.md` | `1` — the no-new-machinery constraint made testable | **EXECUTED: 1** — HOLDS |
| V15 | 10 | `grep -c 'Correction is not the same as lifecycle' specs/001-agentic-sdlc-url-shortener/spec.md` | `0` — Edit 10 is **withdrawn**; the register must be untouched | **EXECUTED: 0** — HOLDS |
| V16 | 10 | `python3 -c "import pathlib;t=pathlib.Path('specs/001-agentic-sdlc-url-shortener/spec.md').read_text();s=t.split('## Compensation Register')[1].split('## Stage Executor Model')[0];print(len(s.splitlines()))"` | unchanged from its approved length — the register is byte-identical apart from nothing | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |


**Check correction recorded, 2026-09-20.** V1 was first written as `grep -c '0.5% loss' … | 0`. On execution it
returned **2**. Both hits are inside **DF-001's retained original finding** — the text Edit 2 explicitly preserves
*"below for provenance"* — not in the binding table row. **The edit was correct and the check was wrong**: a
whole-file absence grep cannot distinguish a superseded statement from a deliberately quoted one. V1 is therefore
narrowed to the binding row, which is what the edit actually changes. This is a correction to the *check*, not a
loosening of the *record*: the record's requirement — that the binding row no longer states a loss budget — is
unchanged and is now precisely testable.

**Rule of this record**: the Status line may read `APPROVED and APPLIED` only when every Result cell holds an
actual executed output. A row whose result is absent, or whose result does not match Expected, blocks the record.
If a check fails, the edit is fixed and the check re-run — the record is never adjusted to match the outcome.

## Anti-recurrence: why CR-003's edit was missed

CR-003 recorded *"DF-002 and AQ-004 marked resolved"*. DF-002 was marked; **AQ-004 was not**, in either of the
two places that state it (§Ambiguities and the §Reading Guide marker table). Nothing in CR-003 required the claim
to be checked against the file, so a true summary of intent was recorded as a completed fact.

Three controls follow from this, and they bind the whole remediation package:

1. **Per-edit verification with recorded output** (above) — an applied-status claim must carry its evidence.
2. **Count-invariant checks** (V11, V12) — a cheap whole-artifact assertion catches an edit that landed in the
   wrong place or not at all.
3. **Grep for the old string, not only the new one.** V1, V4, V7, V8, V9 assert the *absence* of superseded text.
   Asserting only the presence of new text is what lets a partially applied edit pass.

## Residual risk

Edit 4's values are **proposed engineering judgement, not measurement**. They are demonstration-scale figures
chosen from the shape of each node's work, and S8's 1800 s in particular is a guess about Testcontainers startup on
an unknown machine.

**Under Decision A the cost of a badly chosen value falls sharply**, and that is worth stating because it is the
strongest argument for the design. A kill-timer set too low destroys work; a *threshold* set too low merely asks a
question too early, and the human says keep waiting. The values are configuration and may be tuned without a change
request; **what PVT-016 binds is that a threshold exists for every node and that breaching one asks a human.**

The risk that remains is the one the owner named and accepted: **an unattended run stalls rather than self-healing.**
It is recorded in Edit 4's counter-case and must appear in `docs/LIMITATIONS.md` (T147) in her own terms — auto-retrying
possibly-half-finished work is the danger the retry rules exist to prevent, and suspension is the declared safe outcome.
