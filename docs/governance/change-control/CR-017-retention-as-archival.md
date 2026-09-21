# Change Request CR-017 — Retention Posture: Indefinite Retention, with Production Archival Recorded as a Recommendation

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, implementing owner **Decision H** |
| Revision | **Draft 3**, 2026-09-20. Drafts 1 and 2 designed a retention *purge* and then an *archival move*. **Both are withdrawn** — see §What this record replaced. |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"go ahead."* |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/spec.md`; the policy set in `plan.md` §9 (routed in **CR-015**) |
| Governing constitution | v1.1.0; `POL-CHG-001`. Touches **Principle VI** (audit retention policy) and **Principle IX** (reconstruction) |
| Source decision | Owner **Decision H**, 2026-09-20 |
| Application order | **Third on `spec.md`**, after CR-010 and CR-014. |

## Owner decision recorded — Decision H, verbatim

> Actually I think we are making this too complicated… let us not worry about archival at all, let the records stay in
> the table for ever, but record this that, Ideally we would have a prod archival job if we were doing this in a real
> prod env.

## What this record replaced, and why that is worth recording

`/speckit-analyze` finding **H5** was that retention had **zero coverage**: approved targets existed and nothing
implemented or tested them. This record has now resolved that finding three different ways.

| Draft | Design | Outcome |
|---|---|---|
| 1 | A retention **purge** — delete rows past their bound | Withdrawn: the owner ruled that nothing should become inaccessible |
| 2 | An **archival move** — append to a readable archive, then remove from the live table | Withdrawn under Decision H. It required an archive format, a location, a crash-reconciliation rule, a new delete privilege against the audit tables' insert-and-select-only grant, and an exception to a control the owner had personally amended at Gate 4 |
| **3 (this one)** | **Retain everything indefinitely; record production archival as a recommendation** | The posture the owner chose |

**Draft 2 collapsed under its own weight, and the honest reading is that it was over-built for a demonstration.** Its
last open question was whether to grant DELETE on the audit tables — which would have meant carving an exception into
`ADR-010`'s immutability-by-privilege control in order to satisfy a housekeeping bound that nothing in the assessment
asks for. Decision H removes the need for the exception entirely: **the tables keep their insert-and-select-only
grants, and the tests asserting that the store *rejects* an UPDATE or DELETE stay exactly as they are.**

The finding is still closed, but by a different mechanism: instead of *implementing* retention, the specification
**states the posture honestly and records what production would do**. A documented, tested "we retain everything, and
here is the production recommendation" is verifiable. An unimplemented binding target was not.

## Owner approval — 2026-09-20

Approved by **Pravallika Veeravalli**: *"go ahead."*

Her approval followed a three-item list and carries all three, recorded as relayed:

1. **The package as revised**, including the §11 brownfield wording.
2. **PVT-010 explicitly withdrawn** — the binding 30/90-day deletion target is formally retired and replaced by the
   documented production recommendation, **by her explicit decision, not by implication**.
3. **Policy set `1.1.0`**, on the ground she herself set for the API contract: **version discipline binds from first
   real use**, and no run has ever evaluated `1.0.0`.

## Applied edits

### Edit 1 — §Validation Targets, PVT-010 revised from a binding target to a recorded recommendation

**OLD** *(as corrected by CR-010 Edit 3)*

```
| PVT-010 | Audit and analytics retention | 90 days audit, 30 days redirect events | Housekeeping cap; no personal data is held (AQ-002), so retention is not a privacy control here | **Audit retention measured from run termination, never from record creation** (CR-004 — DF-003 resolved): a run's records are not purgeable while it is live or suspended. Prototype scope |
```

**NEW**

```
| PVT-010 | Retention — **production recommendation, not a demonstration target** | *Recommended for production*: 90 days audit and terminated-run history, 30 days redirect events | **This demonstration retains all records indefinitely** (NFR-AUD-003, Decision H). Retention was reduced from a binding target to a recorded recommendation because implementing a purge or an archival move added an archive format, a crash-reconciliation rule, and a DELETE privilege against the audit tables' insert-and-select-only grant — machinery the assessment does not ask for, against a housekeeping benefit a demonstration does not need | **Not measured and not enforced.** If it were implemented, the audit and run-history clocks would run **from run termination, never from record creation** (CR-004 — DF-003's reasoning is preserved inside the recommendation), so a live or suspended run's history could never age out. No personal data is held (AQ-002), so retention is not a privacy control here and its absence creates no exposure |
```

### Edit 2 — **NFR-AUD-003**, the posture stated positively and made testable

Appended after NFR-AUD-002. Decision H's posture needs an identifier, because *"we did not build retention"* and
*"we deliberately retain everything"* look identical in an artifact unless one of them is written down and tested.

**NEW**

```
- **NFR-AUD-003** — **Indefinite retention in this demonstration; production archival recorded as a recommendation.**
  No record is deleted, purged, archived, or otherwise removed from its live table by any retention path, because no
  such path exists. Audit records, run history, gate decisions, policy results and redirect events are retained
  indefinitely. The **production recommendation** — an archival job moving redirect events past 30 days and terminated
  runs' history past 90 days out of the live tables, with the run-termination clock rule of CR-004 — is documented in
  `docs/LIMITATIONS.md` as work a real production deployment would do and this demonstration deliberately does not
  *(Decision H)*. *Verifiable: an architecture test asserting **no deletion, purge or archival path exists anywhere**
  in the codebase; the existing audit-immutability tests, which assert the store rejects an UPDATE or a DELETE,
  continue to pass **unmodified**; and a review confirming the production recommendation is documented.*
```

*The architecture test is the substance of this edit. It converts an absence into an asserted property, which is the
difference between a posture and an omission.*

### Edit 3 — NFR-AUD-002, the clock rule preserved inside the recommendation

**OLD**

```
- **NFR-AUD-002** — Audit evidence retains policy outcomes and exceptions, and its retention clock is anchored
  to **run termination** rather than record creation, so a purge cannot defeat FR-ORC-023's reconstruction
  requirement for a run that is still live, suspended, or freshly terminal *(CR-004)*. *Verifiable: presence
  check per run; purge test per EC-043. Retention: PVT-010.*
```

**NEW**

```
- **NFR-AUD-002** — Audit evidence retains policy outcomes and exceptions. **Because this demonstration retains all
  records indefinitely (NFR-AUD-003), FR-ORC-023's reconstruction requirement is satisfied for every run without
  qualification** — there is no purge that could defeat it. The **run-termination clock rule** decided at CR-004
  survives as part of the production recommendation in PVT-010: were retention implemented, the clock would start at
  run termination rather than record creation, so a live or suspended run's history could never age out. *(Amended by
  Decision H; CR-004's reasoning is preserved, not discarded.)* *Verifiable: presence check per run; a reconstruction
  performed on the oldest run in the corpus.*
```

### Edit 4 — FR-URL-010, click records retained indefinitely

Appended to FR-URL-010's **Reject (negative)**:

```
; redirect events are **not deleted or purged at any age** — this demonstration retains them indefinitely
(NFR-AUD-003, Decision H). The 30-day figure that previously appeared as a retention cap is now a **production
recommendation** recorded in PVT-010 and `docs/LIMITATIONS.md`, not a behaviour of this system
```

**Scope of this edit against the Gate-2 analytics decision (CL-002), stated rather than slipped.** CL-002's substance
is **untouched**: per-event records carrying timestamp only, no follower-identifying field, data minimization as the
deciding ground. What changes is the **status of the 30-day cap** it proposed — from a housekeeping bound this system
implements to a recommendation for production. CL-002's own finding that *"no personal data is held… so retention is
not a privacy control here"* is what makes indefinite retention safe: there is nothing held that time makes more
sensitive.

### Edit 5 — §Validation Targets, PVT-015 conditions

**OLD**

```
| Configurable; demonstration runs use compressed values labelled per AS-007. *Boundary against PVT-010 audit retention is an open finding — see Deferred Findings DF-003* |
```

**NEW**

```
| Configurable; demonstration runs use compressed values labelled per AS-007. On expiry the run becomes `ABANDONED` — terminal, and **never an approval** (FR-ORC-032, CL-005). Its records then **remain in the tables like every other record** (NFR-AUD-003). The PVT-010 boundary that DF-003 raised is **dissolved**: with nothing ever purged, an abandonment cannot coincide with its own history becoming unreadable |
```

### Edit 6 — EC-043 retired

The edge case described a purge that no longer exists in any form.

**OLD**

```
- **EC-043**: A retention purge runs while a suspended run's audit records are older than the retention window.
  Must purge nothing — the clock starts at run termination, which has not occurred (NFR-AUD-002, CR-004).
```

**NEW**

```
- **EC-043**: *(Retired by Decision H, 2026-09-20.)* This case described a retention purge running against a
  suspended run's records. **No purge, deletion or archival path exists in this system** (NFR-AUD-003), so the case is
  unreachable rather than untested. Retained as a numbered entry rather than deleted, because CR-004 created it and an
  edge case that silently disappears is indistinguishable from one that was overlooked. The reasoning it carried — a
  live or suspended run's history must never age out — survives inside PVT-010's production recommendation.
```

*Ground for retiring rather than deleting: the edge-case list is a traceability instrument, and a gap in its numbering
is a question a reviewer cannot answer from the artifact.*

### Edit 7 — new §Retention posture subsection

Decision H requires the recommendation recorded **in the specification** as well as in the limitations document.

**NEW**, appended after §Compensation Register:

```
## Retention Posture *(Decision H, 2026-09-20)*

**This demonstration retains every record indefinitely.** No deletion, purge or archival path exists — for audit
records, run history, gate decisions, policy results, or redirect events. The audit tables' insert-and-select-only
grants and the tests asserting that the store rejects an UPDATE or a DELETE are unchanged, and with no retention path
anywhere the Compensation Register's *"never delete"* rows are now **literally true system-wide** rather than true by
convention.

**What production would do, recorded as a recommendation and not built:**

| Data | Recommended production bound | Clock |
|---|---|---|
| Redirect events | 30 days | Record creation |
| Terminated-run history and audit records | 90 days | **Run termination, never record creation** (CR-004) — so a live or suspended run's history can never age out |

An archival job would move records past those bounds out of the live tables. It is not implemented, it is not
scheduled, and it is not a deferred task pretending to be a backlog item: it is **out of scope for a demonstration**,
recorded so that a reviewer can see the production shape was reasoned about rather than missed.

**Why this posture rather than an implementation.** A purge makes records inaccessible, which the owner rejected. An
archival move preserves access but requires an archive format, a location, a crash-reconciliation rule, and a DELETE
privilege against tables whose insert-and-select-only grants are the mechanism that makes audit immutability real —
an exception to a control for a housekeeping benefit no demonstration needs. Indefinite retention costs a
demonstration nothing and keeps every control intact.
```

### Edit 8 — §Clarification Log, change-control amendment entry

Appended after CR-005's entry:

```
- **CR-017** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Retention posture — indefinite retention,
  production archival recorded as a recommendation.* New NFR-AUD-003 with an architecture test asserting no deletion
  path exists; PVT-010 revised from a binding target to a production recommendation; NFR-AUD-002 amended with CR-004's
  clock rule preserved inside that recommendation; FR-URL-010's retention cap restated as a recommendation; PVT-015
  conditions; EC-043 retired as unreachable; new §Retention Posture. **Revises the *status* of the 30-day
  click-record cap proposed by CL-002 at Gate 2** — from a bound this system implements to a recommendation for
  production. CL-002's substance is untouched: timestamp-only records, no follower-identifying field, data
  minimization as the deciding ground; and its own finding that no personal data is held is what makes indefinite
  retention safe. Owner's grounds: *"let us not worry about archival at all, let the records stay in the table for
  ever, but record this that, Ideally we would have a prod archival job if we were doing this in a real prod env."*
  Record: `docs/governance/change-control/CR-017-retention-as-archival.md`.
```

### Edit 9 — `POL-AUD-002` removed *(routed in CR-015; recorded here because this record owns the reason)*

`POL-AUD-002` is *"Retention configured within declared bounds → PASS/FAIL"*, advisory. With no retention path and no
configured bounds, it can never evaluate meaningfully, and Decision H is explicit that **a policy that can never
meaningfully evaluate must not stay**.

It is **removed**, not left to return `NOT-APPLICABLE` forever. A permanently-not-applicable check is noise in every
run's policy record and invites the reading that the outcome vocabulary has a "doesn't matter" value, which it does
not.

**Consequence flagged rather than absorbed**: `POL-AUD-002` was the policy set's **only advisory check**. Removing it
leaves twelve checks in `policy-set-1.1.0` (eleven surviving mandatory plus `POL-CHG-003` from CR-013), **all
mandatory**. The mandatory/advisory split remains defined but has no advisory instance — which is honest, and better
than keeping a check alive to populate a category.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR**, and it is the first edit in this package that **relaxes** an obligation: PVT-010 stops binding. Recorded plainly rather than presented as a clarification. NFR-AUD-003 is a new obligation that partly compensates — it forbids any deletion path and requires a test proving none exists. EC-043 is retired and `POL-AUD-002` removed. |
| **Backward-compatibility impact** | None. Nothing implemented. |
| **Affected consumers** | `plan.md` §9 policy table, §4 lineage, §14 sequencing (**CR-015**). `tasks.md` — the retention tasks are withdrawn (**CR-016**). `quickstart.md` (**CR-012**). `data-model.md` — **no change**; the archive entities drafted at revision 2 are withdrawn (**CR-011**). |
| **Affected tests** | **New:** one architecture test asserting no deletion, purge or archival path exists; a reconstruction on the oldest run in the corpus. **Unchanged, deliberately:** every audit-immutability test, including the two asserting the store rejects an UPDATE and a DELETE — Decision H means they need no exception. **Retired:** the archival-move, crash-reconciliation and four-state purge tests drafted at revision 2, none of which was ever applied. |
| **Affected documentation** | `docs/LIMITATIONS.md` carries the production recommendation; the specification carries §Retention Posture. |
| **Rollout / migration** | None. No retention tables, no archive destination, no migration. |
| **Required approval** | Human owner. This record **withdraws a binding acceptance threshold** she approved at the Gate 4 closing package, so it needs her explicit yes rather than inheriting Decision H by implication. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

| # | Edit | Verification command (from repo root, `S=specs/001-agentic-sdlc-url-shortener/spec.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 2 | `grep -c 'NFR-AUD-003' $S` | `≥ 4` | **EXECUTED: 7** — HOLDS |
| V2 | 2 | `grep -c 'Indefinite retention in this demonstration' $S` | `1` | **EXECUTED: 1** — HOLDS |
| V3 | 2 | `grep -c 'no deletion, purge or archival path exists' $S` | `≥ 1` | **EXECUTED: 1** — HOLDS |
| V4 | 1 | `grep -c 'production recommendation, not a demonstration target' $S` | `1` | **EXECUTED: 1** — HOLDS |
| V5 | 1, 3 | `grep -c 'run termination, never record creation' $S` | `≥ 1` — CR-004's rule preserved inside the recommendation | **EXECUTED: 2** — HOLDS |
| V6 | 4 | `grep -c 'not deleted or purged at any age' $S` | `1` | **EXECUTED: 1** — HOLDS |
| V7 | 4 | `grep -c "CL-002's substance is untouched" $S` | `1` — the revision's scope stated, not slipped | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V8 | 6 | `grep -c 'Retired by Decision H' $S` | `1` | **EXECUTED: 1** — HOLDS |
| V9 | 6 | `grep -c 'Must purge nothing' $S` | `0` — the old edge case's text is gone | **EXECUTED: 0** — HOLDS |
| V10 | 7 | `grep -c 'Retention Posture' $S` | `≥ 2` | **EXECUTED: 2** — HOLDS |
| V11 | 7 | `grep -c 'literally true system-wide' $S` | `1` | **EXECUTED: 1** — HOLDS |
| V12 | 8 | `grep -c 'CR-017' $S` | `≥ 3` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V13 | 8 | `grep -c 'let the records stay in the table for ever' $S` | `1` — the owner's grounds verbatim | **EXECUTED: 1** — HOLDS |
| V14 | all | `grep -c 'archive' $S` | reviewed by reading: every occurrence must be inside the **production recommendation**, never a behaviour of this system | EXECUTED: 13 hits across NFR-AUD-003, PVT-010, EC-043, §Retention Posture and the change-control entry. Every one is inside the **production recommendation** or states the absence. **HOLDS** |
| V15 | all | `python3 -c "import re,pathlib;t=pathlib.Path('$S').read_text();print(len(set(re.findall(r'NFR-[A-Z]{3,4}-\d{3}',t))))"` | `31` — 30 approved + NFR-AUD-003 | **EXECUTED: 31** — HOLDS |
| V16 | all | `python3 -c "import re,pathlib;t=pathlib.Path('$S').read_text();print(len(set(re.findall(r'EC-\d{3}',t))))"` | `43` — **unchanged**; EC-043 is retired in place, not deleted | **EXECUTED: 43** — HOLDS |
| V17 | all | `grep -c '^- \*\*FR-' $S` | `51` — unchanged | **EXECUTED: 51** — HOLDS |
| V18 | regression | `grep -c 'Still open' $S` | `0` — proves CR-010's edits survived | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V19 | regression | `grep -c 'CN-012' $S` | `≥ 5` — proves CR-014's edits survived | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V20 | 9, cross | `grep -c 'POL-AUD-002' specs/001-agentic-sdlc-url-shortener/plan.md` | `0` — proves **CR-015** removed it | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V21 | cross | `grep -c 'archive' specs/001-agentic-sdlc-url-shortener/data-model.md` | `0` — proves the revision-2 archive entities were **not** applied | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V22 | cross | `grep -c 'INSERT and SELECT only' specs/001-agentic-sdlc-url-shortener/tasks.md` | `≥ 1` — the audit grants are **untouched**, and T028's delete-rejection test needs no exception | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V21 and V22 are the checks that prove revision 2 was withdrawn rather than half-applied** — the failure mode
for a withdrawn design is a fragment of it surviving in one artifact.

## Residual risk

**The honest risk is that "we retain everything" reads as "we never got to retention."** The two are indistinguishable
in an artifact unless the posture is stated, tested, and given a production counterpart — which is exactly what
NFR-AUD-003, its architecture test, and §Retention Posture are for. Without the test, this change would be an
omission with a paragraph attached.

**Second, unbounded table growth is real**, and in a demonstration it is irrelevant — a handful of runs and a few
thousand redirect events. The statement that belongs in `docs/LIMITATIONS.md` is precise: *growth is unbounded by
design, it is acceptable at demonstration scale, and the production recommendation in PVT-010 is what would address
it.* Not a hedge, and not a claim that the problem is solved.

## Nothing needs an owner call beyond the approval itself

Revision 2's two open questions — the DELETE privilege against the audit tables, and where the archive file lives —
are both **moot**. Decision H removes the archive, so the grants stay as approved and there is no archive to place.
