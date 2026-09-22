# Red-Phase Evidence Index and Audit

**Task**: T144. **Purpose**: index the "red-phase" (failing-test-written-before-implementation,
RED-FIRST) evidence captured in this directory and in commit history, and state honestly how much of
the task universe it actually covers. This is an audit, not a scorecard — gaps are called out rather
than smoothed over.

## 1. What's actually in this directory

`docs/evidence/red-phase/` contains **85 files** (confirmed by directory listing), each named
`<UTC-timestamp>-<task-or-CR-id>-<label>.txt`. They fall into **two distinct capture formats**, split
roughly by when in the build they were captured — not a random mix:

- **Structured format** (the earlier ~half of the files, roughly the T010–T073 range): a Markdown
  header block —
  ```
  # Red-phase evidence
  - **Label**: ...
  - **Captured (UTC)**: ...
  - **Command**: `./mvnw -Dtest=... test`
  - **Exit code**: 1
  - **Requirement**: NFR-TST-002 (red phase evidenced, not asserted); CN-004
  ## Why this file exists
  ## Captured output
  ```
  followed by a genuine pasted Maven/JUnit console log — real stack traces, real compiler errors
  (`cannot find symbol: class ShortLink`), real ArchUnit violations, a real Flyway migration failure
  from a deliberately injected `BIGGSERIAL` typo. These are authentic captured failing-test output,
  not narrative claims.

- **Raw/narrative format** (roughly T073b/T093 onward through the CR-04x/CR-05x tail): either
  unheadered raw pasted terminal output, or a prose **"Sabotage:"** write-up describing a deliberately
  injected code defect, the test failure it produced, and the fix (e.g.
  [`20260921T244500Z-T101-T102-blocking-enforcer-mandatory-fail.txt`](20260921T244500Z-T101-T102-blocking-enforcer-mandatory-fail.txt)
  flips `case FAIL -> true` to `false` and shows the existing test catching it).

  **This second category is honestly closer to mutation/regression testing than to strict TDD
  red-phase evidence.** It proves an *already-written* test is non-vacuous by injecting a defect into
  already-written code and showing the test catches it — valuable, but a different claim than "this
  test was red before the implementation existed." This index does not conflate the two; see the
  per-file table below and treat the "format" implied by a file's position in the timeline as
  approximate, not a hard per-file certification.

Almost every file cites a specific requirement/rule ID (`NFR-TST-002`, `CR-021`, `POL-TRC-001`,
`CR-007`, etc.), and all are labelled with a task (`T0xx`) or change-control (`CR-0xx`) identifier —
traceability at the label level is real and consistent.

**Known duplicate**: [`20260921T093552Z-T048-T051-analytics.txt`](20260921T093552Z-T048-T051-analytics.txt)
and [`20260921T093620Z-T048-T051-analytics.txt`](20260921T093620Z-T048-T051-analytics.txt) carry the
same task label 68 seconds apart. Not deduplicated here since their content wasn't diffed byte-for-byte
— flagged for a follow-up check rather than silently merged or silently duplicated in this index.

## 2. Commit-history evidence for RED-FIRST discipline

- `git log --all --grep="RED-FIRST" --oneline` → **14 commits**, all from the T082-onward, late-build
  period (e.g. `65919cb fix(orchestration): build-failure detail captures stdout... (CR-068)`,
  `1287e47 feat(conductor): T131a -- the missing run-orchestration driver, CR-044`). The label
  "RED-FIRST" does not appear in commit messages from the start of the project — it starts partway
  through.
- `git log --all --grep="red-phase" -i --oneline` → **45 commits**, spanning from early feature work
  (`825cb32 feat(analytics): T048, T049, T050, T051 ...`) through the late build.
- `git log --all --grep="red phase" -i --oneline` → **3 commits** (`825cb32`, `212deb7`, `4f3f924`).

**Honest gap**: across 178 total commits, standalone `test(...)` commits are rare (**3** in the entire
history), and no clean "`test(TaskX)` commit immediately followed by `feat(TaskX)` commit for the same
task" pair exists anywhere in the log. The dominant pattern is that each `feat(...)` commit bundles
test-writing and implementation together. **This means git history alone does not demonstrate
red-before-green — that claim rests entirely on this evidence directory's internal timestamps**, spot-
checked against commit timestamps below.

Spot-check of file timestamp vs. its implementing commit's timestamp (5 consistent, 1 anomaly found):

| Evidence file | Captured (UTC) | Implementing commit | Committed (UTC) | Consistent? |
|---|---|---|---|---|
| T021–T024 | 06:26:35 | `2db1af7` | 06:29:34 | Yes — ~3 min before |
| T026 | 06:32:22 | `8342594` | 06:36:05 | Yes — ~3.7 min before |
| T107 | 15:58:32 | `d991ed4` | 15:59:02 | Yes — 30s before |
| T129 | 21:15:43 | `a8735ae` | 21:33:59 | Yes — ~18 min before |
| T131a | 22:16:19 | `1287e47` | 22:21:58 | Yes — ~5.6 min before |
| **T093 / T095** | 21:00:00 / 21:15:00 | `e3cfc13` | 17:45:26 | **No — evidence file postdates its commit by >3 hours** |

The T093/T095 anomaly is flagged, not resolved, here: both files are in the header-less raw-log
category, so their only timestamp signal is the filename itself, which may reflect when the file was
saved into the evidence folder rather than an authoritative capture-before-commit claim. Treat
red-first for T093/T095 specifically as **unconfirmed** pending a closer look, not as established.

## 3. Task-ID coverage vs. the task universe

Distinct task tokens referenced in filenames (T-series): T010, T014, T021, T024, T026, T030, T032,
T033, T035–T039, T042, T043, T047, T048, T051–T055, T058–T065, T067, T067a, T069–T071, T073 (+
T073a–T073f), T074, T076, T076a, T077, T079, T081–T086, T088–T093, T095, T096, T101, T102, T106–T115,
T117–T119, T121–T123, T129, T131a–T131d — 87 distinct tokens. Plus CR-049 and CR-052 (each appearing
under two sub-labels), and 3 files with no task/CR token at all in the label
([`20260921T190000Z-gemini-adapter-status-guard.txt`](20260921T190000Z-gemini-adapter-status-guard.txt),
[`20260922T003330Z-tasks-to-task-artifact-alias-missing.txt`](20260922T003330Z-tasks-to-task-artifact-alias-missing.txt),
[`20260922T005100Z-normalization-duplicate-externalIds-accepted.txt`](20260922T005100Z-normalization-duplicate-externalIds-accepted.txt)).

The task universe (`specs/001-agentic-sdlc-url-shortener/tasks.md`, 1,498 lines) runs from T001 to
roughly T152. **Gaps with zero red-phase file** in the T010–T131 span covered by this directory: T011–
T013, T015–T020, T022–T023, T025, T027–T029, T031, T034, T040–T041, T044–T046, T049–T050, T056–T057,
T066, T068, T072, T075, T078, T080, T087, T094 (its own commit `2f79fa3` is explicitly labelled
"written and verified half-way; DISABLED here" — a deliberately deferred item, not a silent omission),
T097–T100, T103–T105, T116, T120, T124–T128, T130.

**Nothing exists beyond T131d** — no red-phase file for T132 onward, even though commit messages show
substantial later work (T132 greenfield demo, T136a, T138/T139 DS-C). That later work is documented
instead through the `docs/evidence/ds-a/`, `docs/evidence/ds-b/`, `docs/evidence/ds-c/` run-snapshot
trails and the [governance index](../governance-index.md)'s change-control records (CR-048 onward),
which is a different evidence convention, not a gap in this one — but it means this directory's
coverage claim should be read as "T010–T131d," not "the whole build."

**Bottom line on coverage**: of roughly 150 numbered tasks, this directory carries red-phase-labelled
evidence for well under half by raw task-ID count. Some of that gap is legitimate (Phase-0 scope-
control tasks T001–T009, and T132+ live-demo tasks, plausibly fall outside this convention rather than
lacking discipline) — that judgment call belongs to the project owner, not to this index, which reports
the raw gap rather than resolving it.

## 4. Full file index

| Timestamp (UTC) | Task/CR ID(s) | File |
|---|---|---|
| 2026-09-21 06:00:37 | T014 | [DependencyDirectionTest](20260921T060037Z-T014-DependencyDirectionTest.txt) |
| 2026-09-21 06:14:00 | T010 | [MigrationFromEmpty — INJECTED BIGGSERIAL typo](20260921T061400Z-T010-MigrationFromEmpty-INJECTED-BIGGSERIAL-typo.txt) |
| 2026-09-21 06:26:35 | T021–T024 | [domain entities](20260921T062635Z-T021-T024-domain-entities.txt) |
| 2026-09-21 06:32:22 | T026 | [ShortLinkRepositoryIT](20260921T063222Z-T026-ShortLinkRepositoryIT.txt) |
| 2026-09-21 06:40:22 | T030 | [ResponseExamplesTest](20260921T064022Z-T030-ResponseExamplesTest.txt) |
| 2026-09-21 07:02:35 | T032 | [WalkingSkeletonIT](20260921T070235Z-T032-WalkingSkeletonIT.txt) |
| 2026-09-21 08:35:33 | T033–T035 | [validation](20260921T083533Z-T033-T035-validation.txt) |
| 2026-09-21 08:42:23 | T035 | [INJECTED echo reaches the 400 body](20260921T084223Z-T035-INJECTED-echo-reaches-the-400-body.txt) |
| 2026-09-21 08:53:42 | T036–T038 | [abuse credentials shortcode](20260921T085342Z-T036-T038-abuse-credentials-shortcode.txt) |
| 2026-09-21 08:53:55 | T037 | [telemetry capture](20260921T085355Z-T037-telemetry-capture.txt) |
| 2026-09-21 09:07:28 | T039–T042 | [create and idempotency](20260921T090728Z-T039-T042-create-and-idempotency.txt) |
| 2026-09-21 09:24:36 | T043–T047 | [redirect and expiry](20260921T092436Z-T043-T047-redirect-and-expiry.txt) |
| 2026-09-21 09:35:52 | T048–T051 | [analytics](20260921T093552Z-T048-T051-analytics.txt) *(see duplicate note, §1)* |
| 2026-09-21 09:36:20 | T048–T051 | [analytics](20260921T093620Z-T048-T051-analytics.txt) *(see duplicate note, §1)* |
| 2026-09-21 10:09:17 | T052 | [INJECTED per-denial Decision](20260921T100917Z-T052-INJECTED-per-denial-Decision.txt) |
| 2026-09-21 10:14:15 | T053 | [INJECTED default expiry](20260921T101415Z-T053-INJECTED-default-expiry.txt) |
| 2026-09-21 10:14:27 | T053 | [INJECTED default expiry, behavioural](20260921T101427Z-T053-INJECTED-default-expiry-behavioural.txt) |
| 2026-09-21 10:16:21 | T054–T055 | [rate limiting](20260921T101621Z-T054-T055-rate-limiting.txt) |
| 2026-09-21 11:29:46 | T074–T076 | [graph template](20260921T112946Z-T074-T076-graph-template.txt) |
| 2026-09-21 11:34:20 | T077–T079 | [state machines](20260921T113420Z-T077-T079-state-machines.txt) |
| 2026-09-21 11:49:33 | T076a, T081 | [artifact writes](20260921T114933Z-T076a-T081-artifact-writes.txt) |
| 2026-09-21 11:59:59 | T082 | [slice4 durability](20260921T115959Z-T082-slice4-durability.txt) |
| 2026-09-21 12:04:55 | T081 | [provenance query](20260921T120455Z-T081-provenance-query.txt) |
| 2026-09-21 12:17:47 | T069, T083 | [executor interface and envelope](20260921T121747Z-T069-T083-executor-interface-and-envelope.txt) |
| 2026-09-21 12:24:59 | T070 | [stage effect contracts](20260921T122459Z-T070-stage-effect-contracts.txt) |
| 2026-09-21 12:31:11 | T071 | [deterministic engines](20260921T123111Z-T071-deterministic-engines.txt) |
| 2026-09-21 12:35:50 | T084 | [two-vote retry ruling](20260921T123550Z-T084-two-vote-retry-ruling.txt) |
| 2026-09-21 12:38:35 | T085–T086 | [retry and timeout](20260921T123835Z-T085-T086-retry-and-timeout.txt) |
| 2026-09-21 12:43:17 | T088 | [compensation register](20260921T124317Z-T088-compensation-register.txt) |
| 2026-09-21 12:43:34 | T088 | [compensation register store](20260921T124334Z-T088-compensation-register-store.txt) |
| 2026-09-21 12:48:09 | T091–T092 | [safe stop and retention](20260921T124809Z-T091-T092-safe-stop-and-retention.txt) |
| 2026-09-21 13:20:06 | T089 | [rollback handler](20260921T132006Z-T089-rollback-handler.txt) |
| 2026-09-21 13:22:55 | T090 | [compensation handler](20260921T132255Z-T090-compensation-handler.txt) |
| 2026-09-21 13:38:45 | T073 | [claude-code-cli adapter](20260921T133845Z-T073-claude-code-cli-adapter.txt) |
| 2026-09-21 13:45:38 | T082 | [requirement lineage](20260921T134538Z-T082-requirement-lineage.txt) |
| 2026-09-21 14:17:09 | T064 | [gate class registry](20260921T141709Z-T064-gate-class-registry.txt) |
| 2026-09-21 14:20:54 | T058 | [approval gate and gate decision](20260921T142054Z-T058-approval-gate-and-gate-decision.txt) |
| 2026-09-21 14:28:37 | T059 | [gate outcome handler](20260921T142837Z-T059-gate-outcome-handler.txt) |
| 2026-09-21 14:34:32 | T062 | [materialization check](20260921T143432Z-T062-materialization-check.txt) |
| 2026-09-21 14:35:58 | T063 | [actor authority](20260921T143558Z-T063-actor-authority.txt) |
| 2026-09-21 14:41:39 | T060 | [gate request presenter](20260921T144139Z-T060-gate-request-presenter.txt) |
| 2026-09-21 14:47:16 | T061 | [silence produces suspension](20260921T144716Z-T061-silence-produces-suspension.txt) |
| 2026-09-21 14:49:36 | T061a | [silence falsifiability](20260921T144936Z-T061a-silence-falsifiability.txt) |
| 2026-09-21 14:55:56 | T065 | [no-plan gate](20260921T145556Z-T065-no-plan-gate.txt) |
| 2026-09-21 15:02:39 | T067 | [run inspection query](20260921T150239Z-T067-run-inspection-query.txt) |
| 2026-09-21 15:58:32 | T107 | [impact analysis](20260921T155832Z-T107-impact-analysis.txt) |
| 2026-09-21 16:05:00 | T073a | [normalization](20260921T160500Z-T073a-normalization.txt) |
| 2026-09-21 16:09:00 | T073b | [ambiguity detection](20260921T160900Z-T073b-ambiguity-detection.txt) |
| 2026-09-21 16:13:00 | T073c | [decomposition](20260921T161300Z-T073c-decomposition.txt) |
| 2026-09-21 16:17:00 | T073d | [design](20260921T161700Z-T073d-design.txt) |
| 2026-09-21 16:22:00 | T073e | [implementation](20260921T162200Z-T073e-implementation.txt) |
| 2026-09-21 16:25:00 | T073f | [documentation](20260921T162500Z-T073f-documentation.txt) |
| 2026-09-21 18:00:00 | T073 | [model id, real CLI fix](20260921T180000Z-T073-model-id-real-cli-fix.txt) |
| 2026-09-21 18:30:00 | T067a | [escalation target wiring](20260921T183000Z-T067a-escalation-target-wiring.txt) |
| 2026-09-21 19:00:00 | — | [gemini adapter status guard](20260921T190000Z-gemini-adapter-status-guard.txt) |
| 2026-09-21 21:00:00 | T093 | [resume service](20260921T210000Z-T093-resume-service.txt) *(timestamp anomaly, §2)* |
| 2026-09-21 21:15:00 | T095 | [run lease](20260921T211500Z-T095-run-lease.txt) *(timestamp anomaly, §2)* |
| 2026-09-21 21:15:43 | T129 | [summary assembler](20260921T211543Z-T129-summary-assembler.txt) |
| 2026-09-21 22:00:00 | T096 | [EC-019 downstream invalidation](20260921T220000Z-T096-ec019-downstream-invalidation.txt) |
| 2026-09-21 22:15:00 | T096 | [EC-030 cycle rejection](20260921T221500Z-T096-ec030-cycle-rejection.txt) |
| 2026-09-21 22:16:19 | T131a | [conductor executor-kind violation](20260921T221619Z-T131a-conductor-executor-kind-violation.txt) |
| 2026-09-21 22:28:57 | T131c | [branch applier and test runner](20260921T222857Z-T131c-branch-applier-and-test-runner.txt) |
| 2026-09-21 22:30:00 | T111 | [audit writer](20260921T223000Z-T111-audit-writer.txt) |
| 2026-09-21 22:35:00 | T112 | [correlation context](20260921T223500Z-T112-correlation-context.txt) |
| 2026-09-21 22:39:32 | T131b | [gate-decision advance unhandled exception](20260921T223932Z-T131b-gate-decision-advance-unhandled-exception.txt) |
| 2026-09-21 22:45:00 | T113 | [governance immutability scan](20260921T224500Z-T113-governance-immutability-scan.txt) |
| 2026-09-21 22:50:03 | T082a | [self-inflicted secret-scan match](20260921T225003Z-T082a-self-inflicted-secret-scan-match.txt) |
| 2026-09-21 23:17:11 | T131d | [gemini 429 misclassification](20260921T231711Z-T131d-gemini-429-misclassification.txt) |
| 2026-09-21 23:50:00 | T114 | [stage telemetry Spring wiring](20260921T235000Z-T114-stage-telemetry-spring-wiring.txt) |
| 2026-09-22 00:00:00 | T115 | [failure_event duration consistency](20260921T240000Z-T115-failure-event-duration-consistency.txt) |
| 2026-09-22 00:05:00 | T117 | [human-wait window clipping](20260921T240500Z-T117-human-wait-window-clipping.txt) |
| 2026-09-22 00:10:00 | T118–T119 | [MTTR denominator](20260921T241000Z-T118-T119-mttr-denominator.txt) |
| 2026-09-22 00:15:00 | T121 | [measurement label, blank conditions](20260921T241500Z-T121-measurement-label-blank-conditions.txt) |
| 2026-09-22 00:20:00 | T122 | [run metrics latency/suspended separation](20260921T242000Z-T122-run-metrics-latency-suspended-separation.txt) |
| 2026-09-22 00:30:00 | T123 | [orphan requirement detection](20260921T243000Z-T123-orphan-requirement-detection.txt) |
| 2026-09-22 00:45:00 | T101–T102 | [blocking enforcer mandatory-fail](20260921T244500Z-T101-T102-blocking-enforcer-mandatory-fail.txt) |
| 2026-09-22 00:50:00 | T106 | [change-request missing-record detector](20260921T245000Z-T106-change-request-missing-record-detector.txt) |
| 2026-09-22 00:55:00 | T108 | [stale artifact query](20260921T245500Z-T108-stale-artifact-query.txt) |
| 2026-09-22 01:00:00 | T109–T110 | [traceability relative-path resolution](20260921T250000Z-T109-T110-traceability-relative-path-resolution.txt) |
| 2026-09-22 01:05:00 | T110a | [missing evidence-artifact detection](20260921T250500Z-T110a-missing-evidence-artifact-detection.txt) |
| 2026-09-22 00:33:30 | — | [tasks-to-task artifact alias missing](20260922T003330Z-tasks-to-task-artifact-alias-missing.txt) |
| 2026-09-22 00:40:37 | CR-049 | [S3 materiality-classification missing](20260922T004037Z-CR049-s3-materiality-classification-missing.txt) |
| 2026-09-22 00:51:00 | — | [normalization duplicate externalIds accepted](20260922T005100Z-normalization-duplicate-externalIds-accepted.txt) |
| 2026-09-22 02:21:52 | CR-052 | [materiality sharpening missing](20260922T022152Z-CR052-materiality-sharpening-missing.txt) |
| 2026-09-22 02:27:10 | CR-052 | [blank noClarificationReason rejected](20260922T022710Z-CR052-blank-noClarificationReason-rejected.txt) |

## 5. Related evidence outside this directory

- [Governance-evidence index](../governance-index.md) — the change-control records (CR-044 through
  CR-068) covering the late-build Conductor/live-AI-robustness work that this directory's coverage
  stops short of (T131d is the last T-series file here).
- `docs/evidence/ds-a/`, `docs/evidence/ds-b/`, `docs/evidence/ds-c/` — the scenario run-snapshot
  trails documenting T132+ live demonstration runs, which use a different evidence convention (attempt
  logs and gate-decision records, not red-phase capture files).
