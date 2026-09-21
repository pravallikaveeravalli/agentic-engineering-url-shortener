# Change Request CR-020 — Task Plan: the Baseline-Omissions Register Gains Its Creating Task; Uncited Identifiers Added

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, from the post-application `/speckit-analyze` re-run — residual findings **1** (HIGH) and **4** (LOW) |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"fix the leftovers and let us move things forward here."* |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/tasks.md` — approved at Gate 5, amended by CR-016 |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decision | Owner authority, 2026-09-20, scoped to the four residual findings |
| Application order | Third of the three residual records. Independent of CR-018 and CR-019. |
| Task count | **171 → 172** (one addition, no removals) |

## Why this change exists

### Residual 1 (HIGH) — an artifact promised by three places and created by none

`docs/delivery/baseline-omissions.md` is referenced by **T055's guard** (*"the deferral is disclosed in the
baseline-omissions record"*), by **T147**, and by **plan.md §14**. **No task creates it.** Its creator was the
retention-configuration task, and Decision H struck that task — which orphaned the file.

**This matters more than a missing document.** The entire defence of deferring a binding requirement — FR-URL-016's
third rate-limit tier — is that the omission is *written down with a named closure*. `plan.md` §14 states that if the
brownfield run does not happen, *"release readiness must report it as one — it may not be relabelled as a design
choice after the fact."* That promise had no artifact behind it. A scheduled omission and an accepted gap look
identical unless the schedule is written somewhere a reviewer can read, and CR-016's own residual-risk section says
so in those words.

It is also a clean example of a second-order defect: the strike was correct, the finding it created was invisible to
every per-record verification table because **each record verified its own edits and none verified what another
record's strike had orphaned**.

### Residual 4 (LOW) — identifiers covered behaviourally but uncited

Sixteen entity identifiers, three clarification identifiers, seven constraint identifiers and one validation target
are satisfied by tasks that never name them. The traceability report (T123) and the zero-orphan assertion (T124) are
required to generate **mechanically**; an identifier that appears in no task's requirement field cannot be reached by
a generator, so the chain would have to be argued rather than computed.

## Applied edits

### Edit 1 — new task `T055a`, placed immediately after the baseline limiter work

Placed there deliberately: the register's first and only entry is the tier T055 defers, so the disclosure sits beside
the deferral rather than in a documentation phase three slices later, where it could be written after the gap already
existed.

**NEW**

```
- [ ] T055a [P] [US1] Baseline-omissions register — `docs/delivery/baseline-omissions.md`
  - **Req**: **FR-URL-016**, **PVT-014**, CN-007 · **Scn**: **DS-B** (its before-state presumes this is written) · **ADR**: ADR-013 · **Pre**: T055
  - **Deps**: T055 · **Par**: yes (own file) · **Artifact**: the written record of every **deliberate** baseline omission — currently exactly **one**: the per-creator aggregate redirect tier (**PVT-014**), deferred from Slice 3. Each entry names five things: **what is omitted**, **which requirement it belongs to**, **that the requirement remains binding in full**, **the run that closes it**, and **what release readiness must report if that run does not happen**
  - **TDD**: N/A-DOC · **Validate**: the entry exists **before T057's acceptance sweep** records the multi-link case as passing unthrottled — otherwise the sweep documents a gap with no disclosure behind it; a reviewer can read the register in one page and answer *"what is missing, and who closes it"*; cross-checked against `docs/LIMITATIONS.md` (T147) so the two cannot disagree
  - **Docs**: new file; referenced by T055's guard, T136a, T147 and plan §14 · **Trace**: FR-URL-016, PVT-014, DS-B
  - **Guard**: **the whole defence of deferring a binding requirement is that the omission is written down with a named closure** — without this file the deferral is indistinguishable from an oversight, and plan §14's promise that release readiness reports the gap has no artifact behind it. It MUST NOT become a general backlog: `docs/delivery/backlog.md` (T002) holds items deferred **indefinitely**, this holds **scheduled omissions with closures**, and an entry that loses its closing run belongs in neither — it is a gap, and must be reported as one · **Done**: the aggregate-tier entry present with all five fields; **no entry without a named closing run** · **Approval**: none
```

### Edit 2 — T055's **Artifact** field points at the task that creates the register

**OLD** `and disclosed in the baseline-omissions record`
**NEW** `and disclosed in the baseline-omissions record (**T055a**)`

**Drafting correction recorded, 2026-09-20.** This edit was first written against T055's **Guard** field, quoting
*"the deferral is disclosed in the baseline-omissions record"*. That string does not exist: the reference lives in
T055's **Artifact** field and reads *"and disclosed in the baseline-omissions record"*. The application asserted the
quote and **stopped**, which is the intended behaviour — an edit whose OLD text cannot be found is a defect in the
record, not something to approximate. Corrected to the actual text and the actual field.

Worth one line of reflection: the OLD/NEW discipline caught this because it **requires an exact match before
touching anything**. A looser instruction — "point T055 at the new task" — would have applied cleanly and left the
record describing an edit to a field it never touched.

### Edit 3 — T147 points at it too

**OLD** `**the deliberately deferred per-creator aggregate redirect tier and which run closed it**`
**NEW** `**the deliberately deferred per-creator aggregate redirect tier and which run closed it** (cross-checked against the baseline-omissions register, T055a)`

### Edit 4 — coverage map row

**OLD** `| — | Deferred aggregate redirect tier (brownfield subject) | T055 defers · T136a implements |`
**NEW** `| — | Deferred aggregate redirect tier (brownfield subject) | T055 defers · **T055a discloses** · T136a implements |`

### Edit 5 — uncited identifiers added to task requirement fields

Residual 4. Each identifier is added to a task that already satisfies it; **no task gains an obligation it did not
already have**, and no identifier is placed on a task that does not deliver it.

| Identifier | Task | What already satisfies it |
|---|---|---|
| `KE-08`, `KE-09` | T082 | AmbiguityRecord and ClarificationDecision are in its artifact list |
| `KE-13` | T107 | The seven-dimension ImpactAnalysis model |
| `KE-14` | T096 | `ReplanEvent` with cause, invalidated nodes, voided approvals |
| `KE-15` | T085 | Per-attempt retry records |
| `KE-16` | T090 | The three compensating actions |
| `KE-17` | T100 | `PolicyCheckResult` rows, one outcome per applicable policy |
| `KE-18` | T103 | PolicyException with its seven fields |
| `KE-19` | T111 | The audit writer and its six mandatory fields |
| `KE-20` | T121 | `MEASURED`/`PROPOSED` labelling on every emitted figure |
| `KE-21` | T109 | `ReleaseReadinessReport` naming every unmet condition |
| `KE-22` | T123 | TraceLink over the ten-link chain |
| `KE-25` | T066 | The executor-kind vocabulary and its flag-selection refusal |
| `KE-26` | T083 | The standard failure envelope |
| `KE-27` | T084 | The two-signature retry ruling |
| `KE-28` | T088 | EffectRecord's reversibility class and compensating action |
| `CL-001` | T052 | Creator authentication against the stored hash |
| `CL-002` | T024 | Timestamp-only redirect events, no follower-identifying field |
| `CL-004`, `CN-009` | T089 | Rollback of **local un-pushed** work — the only erasable class, which rests on staying on `main` and never pushing |
| `CN-001` | T129 | The final summary describes the AI-assisted process and its deviations |
| `CN-002`, `CN-003` | T008 | Its guard already refuses to start on an unapproved stack |
| `CN-004` | T020 | Red-phase capture — red-green-refactor made evidential |
| `CN-005` | T006 | The fabrication-pressure stop condition, non-waivable |
| `CN-006` | T043 | Temporary-class redirect; the exact code left as an implementation detail |
| `PVT-006` | T058 | `wait_deadline` on the ApprovalGate entity |

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR** for Edit 1 — a new task is a new obligation. **PATCH** for Edits 2–5: pointers and citations that add no work. |
| **Backward-compatibility impact** | None. |
| **Affected consumers** | `docs/delivery/*` registers must reflect **172** tasks. `plan.md` §14 already promises the register; this makes the promise deliverable and needs no plan edit. |
| **Affected tests** | None new. T055a is `N/A-DOC`; its validation is a review plus an ordering check against T057. |
| **Affected documentation** | `docs/delivery/baseline-omissions.md` (new, created by T055a). |
| **Rollout / migration** | None. |
| **Required approval** | Human owner — `tasks.md` is approved text. Granted 2026-09-20 within the four-residual scope. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

| # | Edit | Verification command (from repo root, `T=specs/001-agentic-sdlc-url-shortener/tasks.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c '^- \[ \] T' $T` | `172` | **EXECUTED: 172** — HOLDS |
| V2 | 1 | `grep -c '^- \[ \] T055a ' $T` | `1` | **EXECUTED: 1** — HOLDS |
| V3 | 1 | T055a carries all fourteen field keys | all present | **EXECUTED: 14** — HOLDS |
| V4 | 1 | `grep -c 'no entry without a named closing run' $T` | `1` | **EXECUTED: 1** — HOLDS |
| V5 | 1, 2, 3 | `grep -c 'baseline-omissions' $T` | `3` — T055's Artifact, T055a's target path, and T147's cross-check. **Edit 4's coverage-map row names T055a without the filename**, so it does not contribute; the original expectation of ≥4 assumed it did | **EXECUTED: 3** — HOLDS |
| V6 | 1 | a task exists whose **target path** is `docs/delivery/baseline-omissions.md` | `1` — this is the check whose failure raised the finding | **EXECUTED: 1** — HOLDS |
| V7 | 5 | every identifier in Edit 5's table appears in `tasks.md` | all 24 present | **EXECUTED: 27** — HOLDS |
| V8 | 5 | entity identifiers `KE-\d\d` cited in tasks | `≥ 28` of 29 — `KE-24` is covered inside T022's artifact text without its identifier, and `KE-01..KE-07`, `KE-10..KE-12` were already cited | **EXECUTED: 29** — HOLDS |
| V9 | 5 | constraint identifiers `CN-\d{3}` cited in tasks | `≥ 11` of 12 | **EXECUTED: 12** — HOLDS |
| V10 | invariant | `grep -c 'Approval\*\*: \*\*REQUIRED' $T` | `5` — T055a adds no gate | **EXECUTED: 5** — HOLDS |
| V11 | regression | `grep -c 'ESCALATE' $T` | `≥ 4` — Gate 5's time-attitude amendment intact | **EXECUTED: 6** — HOLDS |
| V12 | regression | `grep -c 'gate-<next>' $T` | `≥ 2` — the no-preallocation convention intact | **EXECUTED: 3** — HOLDS |


**Check correction recorded, 2026-09-20.** V5 expected **≥4** occurrences and execution returned **3**. The fourth was
assumed to come from Edit 4's coverage-map row, which names the creating task (`T055a`) but not the filename — so the
count was always going to be three. **No edit is missing**; V6 independently proves a task targets the path, which is
the assertion that actually matters and the one whose absence raised the finding.

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V6 is the discriminating check** — the finding was raised precisely because references existed while no task
targeted the path, so counting references (V5) is not sufficient and never was.
