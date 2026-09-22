# Tasks: Agentic Software Engineering System: URL Shortener

**Input**: Design documents from `/specs/001-agentic-sdlc-url-shortener/`

**Prerequisites**: `plan.md` (APPROVED 2026-09-20), `spec.md` (approved Gate 2, clarified Gate 3, amended CR-001..007),
ADR-001..014 (all Accepted, Gate 4), `data-model.md`, `contracts/`, `research.md`, `quickstart.md`

**Governing constitution**: v1.1.0 · **Policy set**: `policy-set-1.1.0` (CR-015; corrected here by CR-033 — the header had not been bumped with the set) · **Generated**: 2026-09-20

---

## How to read a task

Every task carries the seventeen fields the task format requires, compressed into four field lines beneath its
checklist line. Field keys are fixed:

```
- [ ] <TaskID> [P?] [USn?] <objective summary> — <exact target path>
  - **Req**: <requirement IDs> · **Scn**: <scenario IDs> · **ADR**: <ADR refs> · **Pre**: <prerequisites>
  - **Deps**: <task IDs> · **Par**: <yes|no + reason> · **Artifact**: <expected output>
  - **TDD**: <obligation> · **Validate**: <command or evidence> · **Docs**: <documentation impact> · **Trace**: <traceability update>
  - **Guard**: <risk or guardrail> · **Done**: <completion criteria> · **Approval**: <human approval requirement>
```

`[P]` marks genuinely parallel tasks — different files, no dependency on an incomplete task, no shared critical
file. `[GATE]` marks a task whose completion requires a recorded human decision materialized per `CLAUDE.md`.

**Letter-suffixed IDs** (`T073a`–`T073f`) mark work inserted after the plan was first numbered. The alternative
was renumbering 79 downstream tasks and every `Deps`/`Pre` reference, dependency-graph edge, parallel-execution
example and coverage-map row that cites them — which risks dangling references, and a broken reference in a
traceability instrument is worse than a suffixed identifier. Suffixed IDs are stable, ordered, and unique, which
is what the format requires of them. Say so if you would rather take the renumber.

**Change-request and gate identifiers are written `CR-<next>` and `gate-<next>`, never hard-coded.** Other work
appends to the same registries, so a task plan that pre-allocates a number creates a collision the moment anything
lands first. This happened **twice**: once for change requests (recorded in `CR-008`'s numbering note) and once for
gates — T007 had reserved `gate-05`, and the task-plan approval itself turned out to be the fifth gate
(`gate-05-task-plan.md`). The acting agent takes the next free identifier at the time the record is filed.

**Root package** is fixed by T008 as `agentic.shortener`; all Java paths below assume
`src/main/java/agentic/shortener/…` and `src/test/java/agentic/shortener/…`.

**TDD obligation values**: `RED-FIRST` (failing test required, red phase captured per T020) · `TEST-WITH`
(test written alongside; red phase impractical, reason recorded) · `N/A-DOC` (documentation or governance
artifact, no test) · `EVIDENCE` (task's output *is* executed evidence).

---

## Coverage map — the 41 requested groups

| # | Requested group | Tasks |
|---|---|---|
| 1 | Engineering baseline | T008–T020 |
| 2 | Walking skeleton | T032 |
| 3 | Domain model | T021–T024 |
| 4 | API contracts | T011–T013, T029–T031 |
| 5 | Persistence abstraction | T025–T027 |
| 6 | URL shortening | T038–T042 |
| 7 | Redirect resolution | T043–T045 |
| 8 | Expiration | T046–T047 |
| 9 | Analytics | T048–T051 |
| 10 | Validation and security | T033–T037, T052–T057 |
| 11a | Orchestration domain | T069–T073, **T073a–T073f** (six AI adapters) |
| 11b | Compliance policy model & versioning | T097–T099 |
| 12 | Compliance evaluation & enforcement | T100–T102 |
| 13 | Policy exception & compensating control | T103–T105 |
| 14 | Change control & downstream impact analysis | T106–T108 |
| 15 | Dependency graph | T074–T076 |
| 16 | Workflow state machine | T077–T080 |
| 17 | Context & decision lineage | T081–T082 |
| 18 | Approval gates | T058–T068 |
| 19 | Retry and timeout | T083–T086 |
| 20 | Fallback | ~~T087~~ **retired (CR-032)** — bounded retry (T085) then safe suspension (T088) is the degradation story; disclosed in T147 |
| 21 | Rollback or compensation | T088–T090 |
| 22 | Safe-stop | T091–T092 |
| 23 | Resume and recovery | T093–T095 |
| 24 | Dynamic replanning | T096 |
| 25 | Audit trail | T111–T113 |
| 26 | Logs, metrics, traces | T114 |
| 27 | Greenfield scenario | T131–T134 |
| 28 | Brownfield scenario | T135–T137, T136a |
| 29 | Ambiguous requirement scenario | T138–T140 |
| 30 | Unit tests | throughout, RED-FIRST tasks |
| 31 | Contract tests | T012–T013, T030 |
| 32 | Integration tests | T016, T032, T143 |
| 33 | Orchestration tests | T078–T080, T085–T096 |
| 34 | Security tests | T053–T057 |
| 35 | End-to-end tests | T143 |
| 36 | Documentation | T126–T128 |
| 37 | Setup and quick start | T009, T127 |
| 38 | Release-readiness validation | T109–T110, T146 |
| 39 | Final engineering summary | T129 |
| 40 | Traceability matrix | T123–T125 |
| 41 | Reviewer navigation guide | T128 |
| — | MTTR instrumentation & reporting | T115–T122 |
| — | Timebox & scope control | T001–T007 |
| — | Evidence tasks | T141–T142, T144–T145, T147–T150 |
| — | Checklist evaluation materialized | T144a, T144b |
| — | Performance and scalability measurement | T145a–T145d |
| — | Orchestration governance surfaces | T063a, T067a, T082a |
| — | Synchronization (concurrent downstream write) | T076a |
| — | Node-overrun escalation | T086a |
| — | Deferred aggregate redirect tier (brownfield subject) | T055 defers · **T055a discloses** · T136a implements |

---

## Phase 0: Timebox and Scope Control

**Goal**: make the approved §14 controls operable before any code exists, so cuts are decided against a written
register rather than improvised under pressure.

**Synchronization point**: T007 gates all of Phase 1.

- [x] T001 Must-have scope register — `docs/delivery/scope-register.md`
  - **Req**: CN-007 · **Scn**: all · **ADR**: — · **Pre**: plan §14 approved
  - **Deps**: none · **Par**: no (first task) · **Artifact**: table of the nine slices with per-slice must-have items
  - **TDD**: N/A-DOC · **Validate**: every slice in plan §14 has a row; reviewer can read it in one page · **Docs**: new file · **Trace**: links each slice to the FRs it delivers
  - **Guard**: register must not restate the plan; it must be the operational checklist derived from it · **Done**: nine rows, each naming its exit condition · **Approval**: none
- [x] T002 [P] Deferred-scope backlog register — `docs/delivery/backlog.md`
  - **Req**: DF-004 · **Scn**: — · **ADR**: ADR-009, ADR-012, ADR-014 · **Pre**: T001
  - **Deps**: T001 · **Par**: yes (separate file) · **Artifact**: labelled backlog with source of each deferral
  - **TDD**: N/A-DOC · **Validate**: every DF-004 item and every ADR-deferred item present · **Docs**: new file · **Trace**: each item cites the record that deferred it
  - **Guard**: backlog items MUST NOT be pulled forward without an owner decision; the file states this. **AI wiring for any stage MUST NOT appear in this register at the outset** — the initial build wires all six (owner decision 2026-09-20); it may re-enter the backlog only by a recorded checkpoint decision · **Done**: run-level circuit breaker, expiry notification, artifact-consumption tracking, full-stack Compose, `redirect_event` partitioning, SDK transport adapter all present — **and no AI stage listed** · **Approval**: none
- [x] T003 [P] Day-level milestone schedule — `docs/delivery/milestones.md`
  - **Req**: CN-007 · **Scn**: — · **ADR**: — · **Pre**: T001
  - **Deps**: T001 · **Par**: yes · **Artifact**: Day 1 AM/PM, Day 2 AM/midday/PM, Day 3 AM/midday/PM with the slice due at each
  - **TDD**: N/A-DOC · **Validate**: matches plan §14 exactly; no milestone invented · **Docs**: new file · **Trace**: milestone → slice → tasks
  - **Guard**: **the schedule is a control instrument, not a promise, and not a source of pressure.** Completeness takes priority over speed (owner amendment, Gate 5); there is no external submission deadline. Slippage is observed at a checkpoint (T005) and **escalated to the owner for direction** — never a silent extension, and never a self-executing cut · **Done**: nine milestones mapped, with the control-not-promise framing stated in the file · **Approval**: none
- [x] T004 [P] Critical-path declaration — `docs/delivery/critical-path.md`
  - **Req**: CN-007 · **Scn**: — · **ADR**: ADR-003 · **Pre**: T001
  - **Deps**: T001 · **Par**: yes · **Artifact**: path `1→2→4→5→6→8→9` with the reason each link is non-removable
  - **TDD**: N/A-DOC · **Validate**: Slice 3 correctly marked as DS-B's subject rather than critical-path · **Docs**: new file · **Trace**: — 
  - **Guard**: must state that Slice 7's `failure_event` capture precedes Slice 8 or the scenarios produce no MTTR population · **Done**: dependency reasons stated per link · **Approval**: none
- [x] T005 Checkpoint decision procedure — `docs/delivery/checkpoints.md`
  - **Req**: CN-007 · **Scn**: — · **ADR**: ADR-004 · **Pre**: T001, T002, T003
  - **Deps**: T001, T002, T003 · **Par**: no (consumes all three) · **Artifact**: four checkpoints with the cut each authorises
  - **TDD**: N/A-DOC · **Validate**: each checkpoint names what it cuts *from the backlog* and what it may never cut · **Docs**: new file · **Trace**: — 
  - **Guard**: a checkpoint MUST NOT authorise cutting mandatory validation or reviewer evidence; stated explicitly. **A checkpoint observes and reports; it does not itself cut.** Under the Gate 5 time-attitude amendment, **a scope cut happens only if the owner orders one at a checkpoint, on the record** — the checkpoint's output is an escalation with options, not an executed reduction. **AI wiring reduction is the first option the owner would be offered** (plan §14), never a default the worker applies. The file must state that distinction, because it is the difference between a controlled cut and a quietly narrowed scope · **Done**: four checkpoints present, each stating its observation, the options it escalates, and what it may never cut · **Approval**: none
- [x] T006 [P] Stop-conditions register — `docs/delivery/stop-conditions.md`
  - **Req**: CN-007, CN-005, Constitution X · **Scn**: — · **ADR**: — · **Pre**: T001
  - **Deps**: T001 · **Par**: yes · **Artifact**: four stop conditions, each with the required report
  - **TDD**: N/A-DOC · **Validate**: fabrication-pressure condition present and marked non-waivable; each condition classified **HALT** or **ESCALATE** per the table below · **Docs**: new file · **Trace**: — 
  - **Guard**: a stop condition halts and reports; it never authorises silent scope reduction. Under the Gate 5 amendment the four conditions are **classified by what the trigger does**, because two of them are time-based and the owner ruled that time must not freeze work on its own:

    | Condition | Behaviour | Why |
    |---|---|---|
    | **Fabrication pressure** — any temptation to present proposed or simulated results as executed | **HALT**, non-waivable | Principle X. No escalation, no discretion, no exception path |
    | **Mandatory policy `FAIL`** unresolved | **HALT** — blocks downstream progression and release readiness | Constitution VI; Principle XI is non-waivable |
    | **Engine core late** — Slice 4 incomplete at end of Day 2 | **ESCALATE to the owner for direction** | Time-based. Previously written as an automatic halt to feature work; under the amendment it reports and asks |
    | **Timebox exhausted** with slices incomplete | **ESCALATE to the owner for direction** | Time-based. Reports exactly what is done, what is not, and why — the owner decides whether anything is cut |

  - **Done**: four conditions present, each classified HALT or ESCALATE with its reason; the two non-waivable halts marked as such · **Approval**: none
- [x] T007 [GATE] Scope-control acknowledgement — `docs/governance/gate-decisions/gate-06-scope-control.md`
  - **Req**: Constitution III · **Scn**: — · **ADR**: — · **Pre**: T001–T006
  - **Deps**: T001–T006 · **Par**: no · **Artifact**: gate record acknowledging the registers as the operative controls
  - **TDD**: N/A-DOC · **Validate**: record carries the four mandatory fields and the `CLAUDE.md` structure · **Docs**: gate record · **Trace**: gate → registers
  - **Guard**: written before Phase 1 begins; silence does not satisfy it · **Done**: outcome, deciding human, date, reason recorded · **Approval**: **REQUIRED — human owner**

---

## Phase 1: Engineering Baseline

**Goal**: a repository in which every later task's validation command exists and can fail. No feature work.

**Blocking**: T007. **Synchronization point**: T020 gates Phase 2.

- [x] T008 Build, root package, and layer directories — `pom.xml`, `src/main/java/agentic/shortener/`, `src/test/java/agentic/shortener/`
  - **Req**: NFR-MNT-001 · **Scn**: — · **ADR**: **ADR-001** (Accepted) · **Pre**: T007
  - **Deps**: T007 · **Par**: no (root artifact) · **Artifact**: Java 21 + Spring Boot 3 build; packages `domain`, `application`, `delivery`, `persistence`, `orchestration`, `policy`, `audit`, `config`
  - **TDD**: N/A-DOC · **Validate**: `./mvnw -q compile` succeeds on an empty tree · **Docs**: `README.md` build section · **Trace**: declares root package for every later path; CN-002, CN-003
  - **Guard**: implementation MUST NOT start on an unapproved stack — ADR-001 is Accepted, so this is unblocked · **Done**: compile passes; eight packages exist · **Approval**: none
- [x] T009 [P] Store-only Docker Compose and reviewer prerequisites — `docker-compose.yml`
  - **Req**: FR-ORC-004, CL-009 · **Scn**: — · **ADR**: **ADR-002**, **ADR-012** · **Pre**: T008
  - **Deps**: T008 · **Par**: yes · **Artifact**: PostgreSQL 16 service only; app runs on host
  - **TDD**: N/A-DOC · **Validate**: `docker compose up -d` then `docker compose restart db` both succeed · **Docs**: `quickstart.md` prerequisites already state Docker + JDK · **Trace**: — 
  - **Guard**: the store MUST be an independently restartable process or CL-009's proof is impossible · **Done**: store starts, restarts, and accepts connections · **Approval**: none
- [x] T010 Flyway baseline migration harness — `src/main/resources/db/migration/V1__baseline.sql`
  - **Req**: plan §2 versioned deliverables · **Scn**: — · **ADR**: ADR-002 · **Pre**: T009
  - **Deps**: T009 · **Par**: no (schema root) · **Artifact**: forward-only migration chain, applies from empty
  - **TDD**: TEST-WITH · **Validate**: `./mvnw -q -Dtest=MigrationFromEmptyIntegrationTest test` applies V1 to a fresh container (built as `MigrationFromEmptyIntegrationTest`, T123's traceability sweep found this line's original name stale) · **Docs**: `contracts/README.md` persistence section · **Trace**: — 
  - **Guard**: migrations are forward-only; a column is deprecated across two versions before removal · **Done**: migration applies from empty in a Testcontainers instance · **Approval**: none
- [x] T011 [P] Contract parse and meta-schema lint — `src/test/java/agentic/shortener/contract/ContractFilesLintTest.java`
  - **Req**: NFR-TST-001 · **Scn**: — · **ADR**: **ADR-005** · **Pre**: T008
  - **Deps**: T008 · **Par**: yes · **Artifact**: test asserting all five contract files parse **and** validate against OpenAPI 3.1 / JSON Schema 2020-12 meta-schemas
  - **TDD**: RED-FIRST · **Validate**: `./mvnw -q -Dtest=ContractFilesLintTest test` — **re-run after CR-013**: both contract files changed shape to v2.0.0, so parse **and** meta-schema lint must pass against the new node model · **Docs**: updates ADR-005 §Validation residual — the meta-schema lint it records as owed · **Trace**: closes ADR-005's disclosed residual
  - **Guard**: **this closes a known gap.** Parse validity was confirmed 2026-09-20; structural conformance was not. Any defect found is a Phase-1 defect, reported not hidden · **Done**: lint passes, or defects are fixed and re-run · **Approval**: none
- [x] T012 OpenAPI response-validation harness — `src/test/java/agentic/shortener/contract/OpenApiConformanceTest.java`
  - **Req**: NFR-TST-001, plan §2 · **Scn**: — · **ADR**: **ADR-005** · **Pre**: T011, T032
  - **Deps**: T011, T032 · **Par**: no (needs a live endpoint) · **Artifact**: harness validating live responses against `contracts/openapi.yaml`, covering the **three** orchestration operations — `createRun`, `inspectRun`, `recordGateDecision`
  - **TDD**: RED-FIRST · **Validate**: `./mvnw -q -Dtest=OpenApiConformanceTest test` · **Docs**: `contracts/README.md` verification status · **Trace**: — 
  - **Guard**: validation must reject `additionalProperties`; a permissive config gives false confidence · **Done**: harness validates at least the walking-skeleton endpoint · **Approval**: none
- [x] T013 Deliberate contract-drift test — `src/test/java/agentic/shortener/contract/ContractDriftDetectionTest.java`
  - **Req**: ADR-005 Validation · **Scn**: — · **ADR**: **ADR-005** · **Pre**: T012
  - **Deps**: T012 · **Par**: no · **Artifact**: test proving the harness **fails** when a response diverges from the document
  - **TDD**: EVIDENCE · **Validate**: test passes only when the injected drift is detected · **Docs**: ADR-005 §Validation · **Trace**: — 
  - **Guard**: **a harness that has never failed is unvalidated.** This is the falsifiability proof ADR-005 requires · **Done**: drift detected; removing the drift makes conformance pass again · **Approval**: none
- [x] T014 [P] Dependency-direction architecture test — `src/test/java/agentic/shortener/arch/DependencyDirectionTest.java`
  - **Req**: **NFR-MNT-001**, NFR-MNT-002 · **Scn**: — · **ADR**: **ADR-006** · **Pre**: T008
  - **Deps**: T008 · **Par**: yes · **Artifact**: ArchUnit-style test asserting `domain` has no dependency on framework, persistence, delivery, or control-plane packages; plus the **fallback-absence rule** (CR-032) and the **no-executor-references-gate-decision rule** (CR-021, shared with T063)
  - **TDD**: RED-FIRST · **Validate**: `./mvnw -q -Dtest=DependencyDirectionTest test` · **Docs**: ADR-006 §Validation · **Trace**: NFR-MNT-001's "dependency-direction check"
  - **Guard**: also asserts the application plane does not import control-plane packages, and — **added by CR-032** — that **no `FallbackHandler` type exists** in `orchestration/reliability`, so the FR-ORC-015 retirement cannot be quietly undone by a later implementer · **Done**: rules encoded and passing; the fallback-absence rule present · **Approval**: none
- [x] T015 Architecture-violation falsifiability test — `src/test/java/agentic/shortener/arch/DependencyDirectionFalsifiabilityTest.java`
  - **Req**: NFR-MNT-001 · **Scn**: — · **ADR**: **ADR-006** · **Pre**: T014
  - **Deps**: T014 · **Par**: no · **Artifact**: proof that T014 fails on a deliberately introduced violation
  - **TDD**: EVIDENCE · **Validate**: violation introduced in a fixture → T014's rule reports a failure · **Docs**: ADR-006 §Validation · **Trace**: — 
  - **Guard**: **this is the owner's stated condition for signing a test-enforced rather than compiler-enforced boundary.** Without it the boundary is a comment · **Done**: failure demonstrated and recorded · **Approval**: none
- [x] T016 [P] Testcontainers integration harness — `src/test/java/agentic/shortener/support/PostgresIntegrationTest.java`
  - **Req**: NFR-TST-001 · **Scn**: — · **ADR**: **ADR-002**, **ADR-011** · **Pre**: T010
  - **Deps**: T010 · **Par**: yes · **Artifact**: base class providing a real Postgres per suite
  - **TDD**: N/A-DOC · **Validate**: a trivial repository round-trip passes against the container · **Docs**: `quickstart.md` test tiers · **Trace**: — 
  - **Guard**: mocks and in-memory substitutes are prohibited where the property needs a real mechanism (ADR-011) · **Done**: container starts and a round-trip succeeds · **Approval**: none
- [x] T017 [P] Two-tier test separation — `pom.xml` surefire/failsafe profiles
  - **Req**: NFR-TST-002, ADR-011 · **Scn**: — · **ADR**: **ADR-011** · **Pre**: T016
  - **Deps**: T016 · **Par**: yes · **Artifact**: fast tier (no container, no framework context) separate from integration tier
  - **TDD**: N/A-DOC · **Validate**: `./mvnw -q test` runs the fast tier without Docker · **Docs**: `quickstart.md` · **Trace**: — 
  - **Guard**: if the red-green loop requires a container it will be abandoned and TDD becomes a claim · **Done**: fast tier runs container-free · **Approval**: none
- [x] T018 [P] Secure configuration defaults — `src/main/resources/application.yml`
  - **Req**: FR-URL-016, plan §8 · **Scn**: — · **ADR**: **ADR-004**, **ADR-004-A2** · **Pre**: T008
  - **Deps**: T008 · **Par**: yes · **Artifact**: throttling on, verbose error detail off, readiness fails closed
  - **TDD**: TEST-WITH · **Validate**: config test asserting each default · **Docs**: `quickstart.md` prerequisites · **Trace**: FR-URL-016
  - **Guard**: **no mode flag exists** (ADR-004-A2); the remaining defaults are unaffected by its removal · **Done**: three defaults asserted · **Approval**: none
- [x] T019 [P] Secret and dependency scanning wiring — `.github/workflows/scan.yml` *(or)* `scripts/scan.sh`
  - **Req**: **NFR-SEC-002**, NFR-SEC-004, `POL-SEC-002`, `POL-SEC-003` · **Scn**: — · **ADR**: ADR-013 · **Pre**: T008
  - **Deps**: T008 · **Par**: yes · **Artifact**: runnable scan over repository **and** captured telemetry
  - **TDD**: TEST-WITH · **Validate**: `scripts/scan.sh` exits non-zero on a planted `crk_` string · **Docs**: `quickstart.md` · **Trace**: `POL-SEC-002`
  - **Guard**: scan must **match a planted credential**, proving the pattern works rather than assuming it (ADR-013) · **Done**: planted-secret detection demonstrated · **Approval**: none
- [x] T020 Red-phase evidence capture mechanism — `scripts/record-red.sh`, `docs/evidence/red-phase/`
  - **Req**: **NFR-TST-002** · **Scn**: — · **ADR**: **ADR-011** · **Pre**: T017
  - **Deps**: T017 · **Par**: no (every later RED-FIRST task depends on it) · **Artifact**: script storing a failing run's output as a dated evidence artifact
  - **TDD**: N/A-DOC · **Validate**: script run against a deliberately failing test produces a stored artifact naming the test and the failure reason · **Docs**: `quickstart.md` evidence section · **Trace**: NFR-TST-002's "evidenced, not asserted"; CN-004
  - **Guard**: the red phase must be observed to fail **for the expected reason**; a compile error does not satisfy it, and the captured output must show which · **Done**: one captured artifact exists · **Approval**: none

---

## Phase 2: Foundational — Domain, Persistence, Contracts

**Goal**: entities, repository boundaries, governance tables, and the first end-to-end path.

**Blocking**: T020. **Synchronization point**: T032 gates Phase 3.

- [x] T021 [P] ShortLink entity — `src/main/java/agentic/shortener/domain/link/ShortLink.java`
  - **Req**: **FR-URL-001**, FR-URL-008, FR-URL-009 · **Scn**: DS-A · **ADR**: ADR-007 · **Pre**: T020
  - **Deps**: T020 · **Par**: yes · **Artifact**: `short_code` (PK, unique, fixed alphabet), `destination` (non-empty, **≤ 2048**, allow-listed scheme, normalized), `creator_id` (**required — no link without an owner**), `created_at` (immutable), `expires_at` (**must be future at creation**), `state` (`ACTIVE` | `EXPIRED`)
  - **TDD**: RED-FIRST · **Validate**: `./mvnw -q -Dtest=ShortLinkTest test` · **Docs**: `data-model.md` KE-01 · **Trace**: matrix FR-URL-001
  - **Guard**: **never deleted** — the only permitted mutation is `ACTIVE → EXPIRED`, which is also its compensating action · **Done**: invariants enforced in the type, not by callers · **Approval**: none
- [x] T022 [P] Creator and CreatorCredential entities — `src/main/java/agentic/shortener/domain/creator/`
  - **Req**: **FR-URL-018**, FR-URL-019 · **Scn**: DS-A · **ADR**: **ADR-013** · **Pre**: T020
  - **Deps**: T020 · **Par**: yes · **Artifact**: Creator(`id`, `name`, `created_at`, `active`); CreatorCredential(`id`, `creator_id`, `key_hash`, `created_at`, `expires_at?`, `revoked_at?`) — **`key_hash` only, SHA-256 of the full presented string including the `crk_` prefix**; `expires_at` nullable, **null reachable only via explicit `never`**
  - **TDD**: RED-FIRST · **Validate**: `./mvnw -q -Dtest=CreatorCredentialTest test` · **Docs**: `data-model.md` KE-23/KE-24 · **Trace**: matrix FR-URL-018/019
  - **Guard**: the type MUST NOT be constructible from plaintext key material · **Done**: no field or accessor exposes a plaintext key · **Approval**: none
- [x] T023 [P] IdempotencyRecord entity — `src/main/java/agentic/shortener/domain/idempotency/IdempotencyRecord.java`
  - **Req**: **FR-URL-012** · **Scn**: DS-A · **ADR**: ADR-007 · **Pre**: T020
  - **Deps**: T020 · **Par**: yes · **Artifact**: `marker` (PK, scoped per creator), `request_fingerprint` (hash), `short_code`, `created_at`
  - **TDD**: RED-FIRST · **Validate**: `./mvnw -q -Dtest=IdempotencyRecordTest test` · **Docs**: `data-model.md` KE-03 · **Trace**: matrix FR-URL-012
  - **Guard**: fingerprint must distinguish same-marker-different-content, which is CL-008 case 3 · **Done**: fingerprint comparison covered by test · **Approval**: none
- [x] T024 [P] RedirectEvent entity — `src/main/java/agentic/shortener/domain/analytics/RedirectEvent.java`
  - **Req**: **FR-URL-010**, NFR-SEC-005 · **Scn**: DS-B · **ADR**: **ADR-014** · **Pre**: T020
  - **Deps**: T020 · **Par**: yes · **Artifact**: `id`, `short_code`, `occurred_at` — **and nothing else**
  - **TDD**: RED-FIRST · **Validate**: schema-assertion test proving no IP, user agent, referrer, device identifier, or geolocation field exists · **Docs**: `data-model.md` KE-02 · **Trace**: matrix FR-URL-010, `POL-PRIV-001`, CL-002
  - **Guard**: **absence is the requirement.** A test asserts the absence; a reviewer can read it · **Done**: absence test passes · **Approval**: none
- [x] T025 Repository interfaces — `src/main/java/agentic/shortener/domain/**/[X]Repository.java`
  - **Req**: **NFR-MNT-002** · **Scn**: — · **ADR**: **ADR-002** · **Pre**: T021–T024
  - **Deps**: T021, T022, T023, T024 · **Par**: no (spans all entities) · **Artifact**: interfaces owned by the domain; no framework or JPA type in a signature
  - **TDD**: TEST-WITH · **Validate**: T014 confirms no outward dependency from `domain` · **Docs**: `data-model.md` · **Trace**: NFR-MNT-002
  - **Guard**: this boundary is what makes ADR-002's reversibility real; a leaked persistence type destroys it · **Done**: T014 passes with the interfaces present · **Approval**: none
- [x] T026 Repository implementations and domain migration — `src/main/java/agentic/shortener/persistence/`, `V2__domain.sql`
  - **Req**: FR-URL-006, FR-URL-014 · **Scn**: DS-A · **ADR**: ADR-002 · **Pre**: T025
  - **Deps**: T025 · **Par**: no (shared migration file) · **Artifact**: implementations plus tables with a **unique index on `short_code`**
  - **TDD**: RED-FIRST · **Validate**: `./mvnw -q -Dtest=*RepositoryIT verify` against Testcontainers · **Docs**: `contracts/README.md` · **Trace**: matrix FR-URL-006
  - **Guard**: uniqueness is guaranteed by the **database constraint**, never by application check-then-insert · **Done**: round-trips pass; unique index present in the migration · **Approval**: none
- [x] T027 Governance tables and append-only privilege grants — `V3__governance.sql`
  - **Req**: **NFR-AUD-001**, FR-ORC-023 · **Scn**: all · **ADR**: **ADR-010** · **Pre**: T026
  - **Deps**: T026 · **Par**: no (schema) · **Artifact**: `audit_record`, `failure_event`, `gate_decision`, `state_transition`, `policy_check_result`, `compensation_record`; app role granted **INSERT and SELECT only**, UPDATE and DELETE **denied**
  - **TDD**: TEST-WITH · **Validate**: migration applies; grants present · **Docs**: `data-model.md` governance section · **Trace**: NFR-AUD-001
  - **Guard**: `redirect_event` keeps append-only privilege but is **domain analytics, not audit** (ADR-010 scoping) — its failure semantics come from ADR-014 · **Done**: six governance tables plus grants · **Approval**: none
- [x] T028 Audit-immutability failing-UPDATE test — `src/test/java/agentic/shortener/audit/AuditImmutabilityIT.java`
  - **Req**: **NFR-AUD-001** · **Scn**: — · **ADR**: **ADR-010** · **Pre**: T027
  - **Deps**: T027 · **Par**: no · **Artifact**: test asserting an UPDATE against `audit_record` is **rejected by the store**
  - **TDD**: EVIDENCE · **Validate**: `./mvnw -q -Dtest=AuditImmutabilityIT verify` · **Docs**: ADR-010 §Validation · **Trace**: NFR-AUD-001
  - **Guard**: **immutability must be a control, not a claim.** If this test passes because the UPDATE succeeded, the grant is missing · **Done**: UPDATE rejected; DELETE rejected · **Approval**: none
- [x] T029 [P] Schema versioning and compatibility rules — `contracts/README.md`
  - **Req**: plan §2 · **Scn**: — · **ADR**: **ADR-005** · **Pre**: T011
  - **Deps**: T011 · **Par**: yes · **Artifact**: MAJOR/MINOR/PATCH classification table already present, extended with the migration deprecation rule
  - **TDD**: N/A-DOC · **Validate**: every contract file has a stated version and a classification rule · **Docs**: this file · **Trace**: — 
  - **Guard**: additive enum values are MINOR; renaming a property is MAJOR — recorded so a future change is classified, not argued · **Done**: rules cover all five files plus migrations · **Approval**: none
- [x] T030 [P] Representative examples per contract response — `contracts/openapi.yaml`
  - **Req**: plan §2 · **Scn**: DS-A · **ADR**: ADR-005 · **Pre**: T011
  - **Deps**: T011 · **Par**: yes · **Artifact**: at least one example per response code, including every error shape
  - **TDD**: TEST-WITH · **Validate**: contract test asserts each example validates against its own schema · **Docs**: `contracts/openapi.yaml` · **Trace**: — 
  - **Guard**: examples that do not validate against their schema are worse than none · **Done**: every response code has a validating example · **Approval**: none
- [x] T031 [GATE] Contract and schema change-control approval — `docs/governance/change-control/CR-036-slice-2-contract-baseline.md`
  - **Req**: **`POL-CHG-001`** · **Scn**: — · **ADR**: ADR-005 · **Pre**: T029, T030, T011
  - **Deps**: T011, T029, T030 · **Par**: no · **Artifact**: change-control record for the Slice-1 contract baseline with the eight impact fields
  - **TDD**: N/A-DOC · **Validate**: record names owner, version impact, compatibility impact, affected consumers, tests, docs, rollout, approval · **Docs**: change-control record · **Trace**: `POL-CHG-001`
  - **Guard**: a contract change without this record is a **mandatory release-blocking FAIL** · **Approval**: **REQUIRED — human owner**
  - **Done**: record materialized and approved before any contract-dependent implementation proceeds
- [x] T032 Walking skeleton — `src/main/java/agentic/shortener/delivery/HealthController.java`, one create endpoint, one repository call
  - **Req**: FR-URL-015 · **Scn**: DS-A · **ADR**: **ADR-001**, ADR-012 · **Pre**: T026, T031
  - **Deps**: T026, T031 · **Par**: no (**synchronization point**) · **Artifact**: one endpoint + liveness + readiness + a real store round-trip, end to end
  - **TDD**: RED-FIRST · **Validate**: `./mvnw -q verify` — skeleton IT passes against Testcontainers · **Docs**: `quickstart.md` · **Trace**: matrix FR-URL-015
  - **Guard**: **if this is not working by end of Day 1 AM, ADR-001 is the first thing to re-examine** (plan §14 checkpoint) · **Done**: endpoint responds, readiness degrades when the store is stopped, liveness does not · **Approval**: none

---

## Phase 3: User Story 1 — API Consumer Shortens and Follows Links (P1)

**Story goal**: the demonstration domain genuinely works — create, follow, expire, count, refuse.

**Independent test criteria**: fully testable through the public interface with the orchestration engine switched
off. Submit valid URLs, invalid URLs, duplicate requests, and expired links; observe creation, rejection,
resolution, and expiry outcomes.

**Blocking**: T032. **Synchronization point**: T057 gates Phase 4.

### Validation and security foundation (must precede creation)

- [x] T033 [P] [US1] URL syntax and length validation — `src/main/java/agentic/shortener/domain/validation/UrlSyntaxValidator.java`
  - **Req**: **FR-URL-002**, NFR-SEC-001 · **Scn**: DS-A · **ADR**: ADR-007 · **Pre**: T032
  - **Deps**: T032 · **Par**: yes · **Artifact**: rejects malformed, empty, and **over-length (> 2048)** destinations with a cause-identifying reason
  - **TDD**: RED-FIRST · **Validate**: `-Dtest=UrlSyntaxValidatorTest` covering EC-006 · **Docs**: — · **Trace**: matrix FR-URL-002
  - **Guard**: a rejected request MUST NOT persist anything, MUST NOT consume a code, and MUST NOT echo input in a way enabling injection · **Done**: each rejection class returns a distinguishable reason · **Approval**: none
- [x] T034 [P] [US1] Destination normalization — `src/main/java/agentic/shortener/domain/validation/DestinationNormalizer.java`
  - **Req**: **FR-URL-003**, NFR-SEC-001 · **Scn**: DS-A · **ADR**: ADR-007 · **Pre**: T032
  - **Deps**: T032 · **Par**: yes · **Artifact**: normalization applied **before** validation and storage, and **idempotent**
  - **TDD**: RED-FIRST · **Validate**: idempotency property test — `normalize(normalize(x)) == normalize(x)` · **Docs**: — · **Trace**: matrix FR-URL-003
  - **Guard**: normalization MUST NOT alter the effective destination; a test asserts round-trip equivalence · **Done**: idempotency and non-alteration both asserted · **Approval**: none
- [x] T035 [US1] Scheme allow-list — `src/main/java/agentic/shortener/domain/validation/SchemeAllowList.java`
  - **Req**: **FR-URL-004** (non-waivable), NFR-SEC-001 · **Scn**: DS-A · **ADR**: ADR-013 · **Pre**: T033, T034
  - **Deps**: T033, T034 · **Par**: no (consumes both) · **Artifact**: explicit allow-list; every other scheme refused
  - **TDD**: RED-FIRST · **Validate**: allow-list matrix test including `javascript:`, `data:`, `file:` · **Docs**: plan §8 · **Trace**: matrix FR-URL-004, `POL-SEC-001`
  - **Guard**: **no configuration, override, or exception may widen this.** A test asserts that a config attempt to add a scheme does not take effect · **Done**: matrix passes; widening attempt refused · **Approval**: none
- [x] T036 [P] [US1] Abuse and malicious-redirect controls — `src/main/java/agentic/shortener/domain/validation/AbuseGuard.java`
  - **Req**: **FR-URL-005** · **Scn**: DS-B · **ADR**: ADR-013 · **Pre**: T035
  - **Deps**: T035 · **Par**: yes · **Artifact**: private, loopback, link-local targets and own-space redirect loops each produce the documented outcome
  - **TDD**: RED-FIRST · **Validate**: `-Dtest=AbuseGuardTest` covering EC-004, EC-005 · **Docs**: plan §8 threat model T-02, T-01 · **Trace**: matrix FR-URL-005
  - **Guard**: such destinations MUST NOT be silently accepted and served · **Done**: EC-004 and EC-005 inputs refused or recorded as accepted risk · **Approval**: none
- [x] T037 [P] [US1] Credential-bearing authority handling — `src/main/java/agentic/shortener/domain/validation/`
  - **Req**: **FR-URL-017** (non-waivable) · **Scn**: DS-A · **ADR**: ADR-010, ADR-013 · **Pre**: T035
  - **Deps**: T035 · **Par**: yes · **Artifact**: credentials embedded in a submitted destination never reach any log or trace
  - **TDD**: RED-FIRST · **Validate**: `-Dtest=CredentialRedactionTest` covering EC-007, plus T019 scan over captured telemetry · **Docs**: plan §8 T-03 · **Trace**: matrix FR-URL-017
  - **Guard**: this clause is **non-waivable**; a failure here is not a bug to schedule, it is a stop condition · **Done**: no occurrence in captured telemetry · **Approval**: none

### URL shortening

- [x] T038 [US1] Short-code alphabet and generator — `src/main/java/agentic/shortener/domain/shortcode/ShortCodeGenerator.java`
  - **Req**: **FR-URL-006**, NFR-SCA-002 · **Scn**: DS-A · **ADR**: **ADR-007** · **Pre**: T035
  - **Deps**: T035 · **Par**: no (interface consumed by T039–T041) · **Artifact**: **CSPRNG**; 57-character confusable-free alphabet excluding `0`/`O` and `1`/`l`/`I`; length 7 (≈ 2×10¹², three orders above **PVT-005**); case-sensitive
  - **TDD**: RED-FIRST · **Validate**: `-Dtest=ShortCodeGeneratorTest` asserting alphabet exclusions, length, and RNG source · **Docs**: ADR-007 · **Trace**: matrix FR-URL-006, EC-008
  - **Guard**: a general-purpose PRNG makes codes predictable and the link space enumerable — the test asserts the **secure** RNG · **Done**: alphabet, length, and RNG asserted · **Approval**: none
- [x] T039 [US1] Insert-and-catch collision handling with bounded retry — `src/main/java/agentic/shortener/application/CreateLinkUseCase.java`
  - **Req**: **FR-URL-006**, FR-URL-001 · **Scn**: DS-A · **ADR**: **ADR-007**, ADR-002 · **Pre**: T038, T026
  - **Deps**: T026, T038 · **Par**: no (core write path) · **Artifact**: insert, catch unique-violation, regenerate, bounded retry; **never read-then-overwrite**
  - **TDD**: RED-FIRST · **Validate**: forced-collision test with a stubbed generator returning a duplicate → retry then success, **existing link untouched** · **Docs**: ADR-007 · **Trace**: matrix FR-URL-006, EC-001
  - **Guard**: **the prohibited overwrite must be structurally inexpressible** — there is no branch in which an existing link is replaced. A check-then-insert implementation fails the concurrency test · **Done**: forced-collision and bound-exhaustion tests pass; exhaustion fails rather than reusing · **Approval**: none
- [x] T040 [US1] Concurrent-creation uniqueness proof — `src/test/java/agentic/shortener/concurrency/ConcurrentCreationIT.java`
  - **Req**: **FR-URL-013**, FR-URL-006 · **Scn**: DS-B · **ADR**: **ADR-002**, ADR-011 · **Pre**: T039
  - **Deps**: T039 · **Par**: no · **Artifact**: concurrent creations converge on distinct codes with no overwrite, against a **real** Postgres
  - **TDD**: EVIDENCE · **Validate**: `-Dtest=ConcurrentCreationIT verify` at **PVT-003** concurrency · **Docs**: — · **Trace**: matrix FR-URL-013, EC-001
  - **Guard**: deterministic barriers, not sleeps. A mocked or in-memory store cannot exhibit the contention this proves (ADR-011) · **Done**: distinct codes; zero overwrites; no flakiness across ten runs · **Approval**: none
- [x] T041 [US1] Marker-only idempotency, three semantics — `src/main/java/agentic/shortener/application/IdempotencyResolver.java`
  - **Req**: **FR-URL-012** · **Scn**: DS-A · **ADR**: ADR-007 · **Pre**: T039, T023
  - **Deps**: T023, T039 · **Par**: no (guards the create path) · **Artifact**: **(1)** no marker → always mint; **(2)** same marker + identical request → replay the **original** as success, labelled `replay: true`, **zero new side effects**; **(3)** same marker + different content → **explicit conflict**, nothing minted, nothing changed
  - **TDD**: RED-FIRST · **Validate**: one test per case plus a **cross-creator non-deduplication** test · **Docs**: `contracts/openapi.yaml` 200/201/409 · **Trace**: matrix FR-URL-012, EC-002, EC-003, EC-039
  - **Guard**: **no destination-based deduplication at any scope, ever** (CL-008). A replay MUST NOT alter the existing link's expiry · **Done**: three cases plus cross-creator case pass · **Approval**: none
- [x] T042 [US1] Create endpoint and error mapping — `src/main/java/agentic/shortener/delivery/LinkController.java`
  - **Req**: FR-URL-001, FR-URL-002 · **Scn**: DS-A · **ADR**: ADR-005 · **Pre**: T041, T012
  - **Deps**: T012, T041 · **Par**: no (shared controller) · **Artifact**: `POST /v1/links` returning code, destination, creation time, expiry state; error shapes per contract
  - **TDD**: RED-FIRST · **Validate**: `-Dtest=OpenApiConformanceTest` now covers create; 400/401/409/429/503 shapes asserted · **Docs**: `contracts/openapi.yaml` · **Trace**: matrix FR-URL-001
  - **Guard**: a code MUST NOT be returned that cannot subsequently be resolved — persist durably first · **Done**: conformance passes for every declared response code · **Approval**: none

### Redirect resolution

- [x] T043 [US1] Resolution use case, temporary class — `src/main/java/agentic/shortener/application/ResolveLinkUseCase.java`
  - **Req**: **FR-URL-007** · **Scn**: DS-A · **ADR**: **ADR-014** · **Pre**: T026, T046
  - **Deps**: T026, T046 · **Par**: no (core read path) · **Artifact**: resolves code → exact stored destination; **temporary-class** redirect
  - **TDD**: RED-FIRST · **Validate**: round-trip byte-equality test; response asserted temporary-class · **Docs**: `contracts/openapi.yaml` 307 · **Trace**: matrix FR-URL-007, CR-003, CN-006
  - **Guard**: **permanent-class redirect is prohibited** — a cached permanent redirect defeats both analytics counting and expiry enforcement (CR-003). Target MUST come from storage, never from request input · **Done**: byte-equality and temporary-class both asserted · **Approval**: none
- [x] T044 [P] [US1] Public unauthenticated redirect path — `src/main/java/agentic/shortener/delivery/RedirectController.java`
  - **Req**: **FR-URL-018** · **Scn**: DS-A · **ADR**: **ADR-013** · **Pre**: T043, T052
  - **Deps**: T043, T052 · **Par**: yes (separate controller) · **Artifact**: `GET /{shortCode}` reachable with **no credential**; does not touch the auth filter
  - **TDD**: RED-FIRST · **Validate**: anonymous redirect test succeeds; auth filter asserted not invoked · **Docs**: `contracts/openapi.yaml` · **Trace**: matrix FR-URL-018
  - **Guard**: **a short link that requires login is not a short link.** The redirect path MUST NOT read, require, or be affected by credentials · **Done**: anonymous success; filter bypass asserted · **Approval**: none
- [x] T045 [P] [US1] Not-found and persistence-failure distinction — `src/main/java/agentic/shortener/delivery/`
  - **Req**: **FR-URL-014**, FR-URL-008 · **Scn**: DS-B · **ADR**: ADR-002 · **Pre**: T043
  - **Deps**: T043 · **Par**: yes · **Artifact**: never-issued → `404`; expired → `410`; store unavailable for a known code → `503` — **three distinguishable outcomes**
  - **TDD**: RED-FIRST · **Validate**: fault-injection test covering EC-010, EC-011 · **Docs**: `contracts/openapi.yaml` · **Trace**: matrix FR-URL-014
  - **Guard**: a persistence failure MUST NOT be reported as success and MUST NOT surface storage internals or secrets · **Done**: all three distinguishable; nothing persisted on EC-010 · **Approval**: none

### Expiration

- [x] T046 [P] [US1] Expiry semantics and validation — `src/main/java/agentic/shortener/domain/link/ExpiryPolicy.java`
  - **Req**: **FR-URL-009** · **Scn**: DS-A · **ADR**: ADR-002 · **Pre**: T021
  - **Deps**: T021 · **Par**: yes · **Artifact**: caller-supplied or defaulted to **PVT-011 (30 days)**; **past expiry at creation is refused**; expiry instant treated consistently
  - **TDD**: RED-FIRST · **Validate**: `-Dtest=ExpiryPolicyTest` covering EC-014 and the boundary instant · **Docs**: `contracts/openapi.yaml` `expiresAt` · **Trace**: matrix FR-URL-009
  - **Guard**: a link MUST NOT be created already expired; injectable clock so the boundary is testable without sleeping · **Done**: EC-014 refused; boundary behaviour defined and asserted · **Approval**: none
- [x] T047 [P] [US1] Expired-link behaviour and cache-bypass proof — `src/test/java/agentic/shortener/link/ExpiredLinkIT.java`
  - **Req**: **FR-URL-008** · **Scn**: DS-A · **ADR**: **ADR-014** · **Pre**: T046, T043
  - **Deps**: T043, T046 · **Par**: yes · **Artifact**: expired link never redirects; outcome distinguishable from never-issued
  - **TDD**: RED-FIRST · **Validate**: expiry-boundary test plus **EC-042** — a cached redirect cannot bypass expiry, because the temporary class forces every follow to the service · **Docs**: — · **Trace**: matrix FR-URL-008, EC-009, EC-042
  - **Guard**: expired MUST NOT redirect under any retry, cache, or race, including EC-009's mid-resolution expiry · **Done**: boundary and cache-bypass tests pass · **Approval**: none

### Analytics

- [x] T048 [US1] Analytics recording port — `src/main/java/agentic/shortener/domain/analytics/AnalyticsRecordingPort.java`
  - **Req**: FR-URL-010 · **Scn**: DS-B · **ADR**: **ADR-014 Condition 2** · **Pre**: T024
  - **Deps**: T024 · **Par**: no (every append depends on it) · **Artifact**: owned interface through which **every** append passes
  - **TDD**: TEST-WITH · **Validate**: T049's architecture rule · **Docs**: plan §2 analytics row · **Trace**: matrix FR-URL-010
  - **Guard**: **Condition 2 — implementation MUST NOT bypass the port anywhere**: not for convenience, not for a hot path, not in tests that then fail to exercise it · **Done**: port defined; no direct store access from the resolution path · **Approval**: none
- [x] T049 [US1] Port-bypass architecture test — `src/test/java/agentic/shortener/arch/AnalyticsPortBypassTest.java`
  - **Req**: FR-URL-010 · **Scn**: — · **ADR**: **ADR-014 Condition 2** · **Pre**: T048
  - **Deps**: T048 · **Par**: no · **Artifact**: test asserting no append reaches the store except through the port, **demonstrated to fail on a deliberate bypass**
  - **TDD**: EVIDENCE · **Validate**: bypass fixture → rule reports failure · **Docs**: ADR-014 §Risks · **Trace**: — 
  - **Guard**: this is the mechanism that keeps ADR-014's evolution ladder reachable; without it the port is advisory · **Done**: rule passes clean and fails on bypass · **Approval**: none
- [x] T050 [US1] Synchronous separate-transaction append with isolated failure — `src/main/java/agentic/shortener/persistence/analytics/`
  - **Req**: **FR-URL-010** · **Scn**: DS-B · **ADR**: **ADR-014** · **Pre**: T048, T043
  - **Deps**: T043, T048 · **Par**: no (resolution path) · **Artifact**: append in its **own transaction**; failure **isolated, counted, logged**; redirect still succeeds
  - **TDD**: RED-FIRST · **Validate**: **EC-012** — redirect succeeds while the append is forced to fail; and an accepted event survives a store restart · **Docs**: ADR-014 · **Trace**: matrix FR-URL-010, EC-012
  - **Guard**: **same-transaction append is prohibited** — it would fail the redirect, violating EC-012. This corrected the plan's original provisional position · **Done**: EC-012 test passes; durability-on-acknowledgement proven; failure counter exposed · **Approval**: none
- [x] T051 [P] [US1] Owner-scoped analytics retrieval — `src/main/java/agentic/shortener/application/GetAnalyticsUseCase.java`
  - **Req**: **FR-URL-011** · **Scn**: DS-B · **ADR**: **ADR-013**, ADR-014 · **Pre**: T050, T052
  - **Deps**: T050, T052 · **Par**: yes · **Artifact**: time-ordered events readable **only by the owning creator**
  - **TDD**: RED-FIRST · **Validate**: owner → `200`; non-owner → `404`; anonymous → `401`/`404`; **non-owner and not-found responses byte-identical** · **Docs**: `contracts/openapi.yaml` · **Trace**: matrix FR-URL-011
  - **Guard**: **no ownership oracle** — the refusal MUST NOT disclose whether the code exists · **Done**: three caller classes tested; byte-identity asserted · **Approval**: none

### Authentication, provisioning, rate limiting, health

- [x] T052 [US1] Bearer authentication filter — `src/main/java/agentic/shortener/delivery/auth/CreatorAuthFilter.java`
  - **Req**: **FR-URL-018**, FR-URL-019 · **Scn**: DS-A · **ADR**: **ADR-013** · **Pre**: T022
  - **Deps**: T022 · **Par**: no (guards create and analytics) · **Artifact**: `Authorization: Bearer <key>`; SHA-256 of the full presented string; **constant-time comparison**; expired and revoked refused with the **same response shape**
  - **TDD**: RED-FIRST · **Validate**: authenticated vs anonymous create; **EC-041** expired-vs-revoked byte-identity; constant-time unit test · **Docs**: `contracts/openapi.yaml` `creatorApiKey` · **Trace**: matrix FR-URL-018, EC-041, CL-001
  - **Guard**: bearer transport chosen because proxies and scrubbers redact `Authorization` **by default** — a custom header would forfeit that. Redirects never enter this filter · **Done**: all four tests pass · **Approval**: none
- [x] T053 [US1] Operator provisioning script with required expiry — `scripts/provision-creator.sh`
  - **Req**: **FR-URL-019** · **Scn**: — · **ADR**: **ADR-013**, ADR-012 · **Pre**: T052
  - **Deps**: T052 · **Par**: no · **Artifact**: `crk_` + base64url of **≥ 256 CSPRNG bits**; `--expires <duration|never>` **required, no default**; key displayed **once** to the operator terminal; only the hash stored
  - **TDD**: RED-FIRST · **Validate**: script **exits non-zero without `--expires`**; `never` yields null `expires_at` and nothing else does; hash-only storage asserted; needs store connectivity but **not** a running application
  - **Docs**: `quickstart.md` provisioning section · **Trace**: matrix FR-URL-019, CR-002
  - **Guard**: **no HTTP issuance surface may exist** — an absence test asserts it. The application never writes key material at any level, including startup · **Done**: four tests pass; absence-of-endpoint test passes · **Approval**: none
- [x] T054 [P] [US1] Per-creator creation rate limit — `src/main/java/agentic/shortener/delivery/ratelimit/CreationRateLimiter.java`
  - **Req**: **FR-URL-016**, EC-013 · **Scn**: DS-B · **ADR**: ADR-013 · **Pre**: T052
  - **Deps**: T052 · **Par**: yes · **Artifact**: **PVT-012 — 60 requests/minute per creator**; throttled outcome names the tier
  - **TDD**: RED-FIRST · **Validate**: `-Dtest=RateLimiterTest` at and above the limit (built as `RateLimiterTest`/`RateLimitIT`, T123's traceability sweep found this line's original name stale) · **Docs**: `contracts/openapi.yaml` 429 · **Trace**: matrix FR-URL-016
  - **Guard**: throttling MUST NOT be silently disabled by default configuration (T018 asserts on-by-default) · **Done**: limit enforced; tier named in the response · **Approval**: none
- [x] T055 [US1] Two-tier redirect rate limiting — `src/main/java/agentic/shortener/delivery/ratelimit/RedirectRateLimiter.java`
  - **Req**: **FR-URL-016** · **Scn**: DS-B · **ADR**: ADR-013 · **Pre**: T044, T054
  - **Deps**: T044, T054 · **Par**: no (shares the limiter module) · **Artifact**: **per short code — PVT-013, 600/min** (hotspot). The **per-creator aggregate tier (PVT-014) is deliberately deferred** to the brownfield scenario (T136a) and disclosed in the baseline-omissions record (**T055a**)
  - **TDD**: RED-FIRST · **Validate**: per-code tier tests at and above the limit. **The multi-link aggregate case is NOT tested here** — it is the brownfield scenario's **before-state**, where it must pass **unthrottled** · **Docs**: plan §8 · **Trace**: matrix FR-URL-016
  - **Guard**: **FR-URL-016 remains binding in full**; deferring the third tier is a scheduled omission with a named closure (T136a), never an accepted gap. PVT-014 sits deliberately **below** the sum of per-code limits, which is what makes the tier bite. A throttled response MUST NOT disclose the owning creator to a public follower. **Accepted trade-off**: followers of a popular creator may be throttled through no fault of their own — document it in the threat model · **Done**: two tiers proven; the third deferred and disclosed · **Approval**: none
- [x] T055a [P] [US1] Baseline-omissions register — `docs/delivery/baseline-omissions.md`
  - **Req**: **FR-URL-016**, **PVT-014**, CN-007 · **Scn**: **DS-B** (its before-state presumes this is written) · **ADR**: ADR-013 · **Pre**: T055
  - **Deps**: T055 · **Par**: yes (own file) · **Artifact**: the written record of every **deliberate** baseline omission — currently exactly **one**: the per-creator aggregate redirect tier (**PVT-014**), deferred from Slice 3. Each entry names five things: **what is omitted**, **which requirement it belongs to**, **that the requirement remains binding in full**, **the run that closes it**, and **what release readiness must report if that run does not happen**
  - **TDD**: N/A-DOC · **Validate**: the entry exists **before T057's acceptance sweep** records the multi-link case as passing unthrottled — otherwise the sweep documents a gap with no disclosure behind it; a reviewer can read the register in one page and answer *"what is missing, and who closes it"*; cross-checked against `docs/LIMITATIONS.md` (T147) so the two cannot disagree
  - **Docs**: new file; referenced by T055's **Artifact** field, T136a, T147 and plan §14 · **Trace**: FR-URL-016, PVT-014, DS-B
  - **Guard**: **the whole defence of deferring a binding requirement is that the omission is written down with a named closure** — without this file the deferral is indistinguishable from an oversight, and plan §14's promise that release readiness reports the gap has no artifact behind it. It MUST NOT become a general backlog: `docs/delivery/backlog.md` (T002) holds items deferred **indefinitely**, this holds **scheduled omissions with closures**, and an entry that loses its closing run belongs in neither — it is a gap, and must be reported as one · **Done**: the aggregate-tier entry present with all five fields; **no entry without a named closing run** · **Approval**: none
- [x] T056 [P] [US1] Health and readiness separation — `src/main/java/agentic/shortener/delivery/HealthController.java`
  - **Req**: **FR-URL-015** · **Scn**: DS-B · **ADR**: ADR-012 · **Pre**: T032
  - **Deps**: T032 · **Par**: yes · **Artifact**: liveness independent of the store; readiness dependent on it and **failing closed**
  - **TDD**: RED-FIRST · **Validate**: with the store stopped, readiness `503` while liveness stays `200` (EC-011) · **Docs**: `contracts/openapi.yaml` · **Trace**: matrix FR-URL-015
  - **Guard**: health MUST NOT expose secrets, connection strings, or internal topology — only dependency names and states · **Done**: degradation test passes; payload asserted free of connection detail · **Approval**: none
- [x] T057 [US1] US1 acceptance sweep — `src/test/java/agentic/shortener/e2e/UrlShortenerAcceptanceIT.java`
  - **Req**: FR-URL-001..015, **FR-URL-016 (creation and per-code redirect tiers only — the per-creator aggregate tier is deferred, see Guard)**, FR-URL-017..019, SC-001, SC-002, SC-003, SC-017 · **Scn**: DS-A · **ADR**: — · **Pre**: T033–T056, T055a
  - **Deps**: T033–T056 · **Par**: no (**synchronization point**) · **Artifact**: the full negative-check table from `quickstart.md` §2 executed as tests
  - **TDD**: EVIDENCE · **Validate**: `./mvnw -q verify -Dgroups=us1` — every row of the quickstart table asserted; **plus a positive assertion of the deferral**: the multi-link aggregate case passes **unthrottled**, recorded as the brownfield scenario’s before-state rather than as a defect, and the baseline-omissions register (T055a) is asserted to carry its entry · **Docs**: `quickstart.md`, `docs/delivery/baseline-omissions.md` · **Trace**: matrix rows FR-URL-001..015 and FR-URL-017..019 gain Test references; **FR-URL-016 gains a *partial* Test reference naming the two built tiers and the deferred third**
  - **Guard**: US1 must be demonstrable **with the orchestration engine switched off**; the sweep must not depend on Phase 5. **This sweep does not claim FR-URL-016 whole.** Two of its three tiers are built and asserted here; the per-creator aggregate tier (PVT-014) is deliberately deferred to the brownfield run (T136a) and disclosed in the baseline-omissions register (T055a). **An acceptance sweep that recorded a partially built requirement as passing would put a green false link into the traceability chain** — the hardest kind to find later, and exactly what `POL-TRC-001` exists to prevent. A **recorded *partial* is a legal matrix state and a silent partial is not** (owner ruling, 2026-09-20). The deferral is therefore asserted as a fact of this build, not skipped · **Done**: all rows pass; the multi-link case asserted unthrottled and recorded as the brownfield before-state; the register entry asserted present; **FR-URL-016’s matrix reference reads *partial* until T136a closes it**; SC-001/002/003/017 evidenced · **Approval**: none

---

## Phase 4: User Story 2 — Human Reviewer Governs the Workflow (P1)

**Story goal**: gates that cannot be satisfied by silence, timeout, retry, or downstream re-entry.

**Independent test criteria**: testable with no domain code changing — drive a run to a gate and exercise every
outcome, including deliberate silence.

**Blocking**: T057 (US1 complete) and T080 (stage state machine). **Synchronization point**: T068.

- [x] T058 [US2] ApprovalGate and GateDecision entities — `src/main/java/agentic/shortener/orchestration/gates/`
  - **Req**: **FR-ORC-013** · **Scn**: DS-A, DS-C · **ADR**: **ADR-005**, ADR-008 · **Pre**: T027, T080
  - **Deps**: T027, T080 · **Par**: no · **Artifact**: ApprovalGate(`id`, `stage_id`, `gate_class`, `wait_deadline`, `disclosed_auto_abandon_at`, `requested_at`); GateDecision(`outcome`, `actor`, `decided_at`, `reason`, `repository_record_path`, `supersedes?`)
  - **TDD**: RED-FIRST · **Validate**: schema validation against `contracts/approval.schema.json` · **Docs**: `data-model.md` KE-10/KE-11 · **Trace**: matrix FR-ORC-013, PVT-006
  - **Guard**: `repository_record_path` is **required** — a decision existing only in workflow state does not satisfy the gate · **Done**: entities validate against the contract, including its conditional rules · **Approval**: none
- [x] T059 [US2] Five gate outcomes and their workflow effects — `src/main/java/agentic/shortener/orchestration/gates/GateOutcomeHandler.java`
  - **Req**: **FR-ORC-013** · **Scn**: DS-A, DS-C · **ADR**: ADR-008 · **Pre**: T058
  - **Deps**: T058 · **Par**: no · **Artifact**: `APPROVED` → stage succeeds; `REJECTED` → run terminates; `CHANGES_REQUESTED` → return to owning stage with changes enumerated and downstream **invalidated**; `ESCALATED` → records what exceeds authority; `TIMED_OUT` → suspension
  - **TDD**: RED-FIRST · **Validate**: one test per outcome · **Docs**: plan §5 · **Trace**: matrix FR-ORC-013
  - **Guard**: **there is no sixth value and none meaning "no response, proceed."** `CHANGES_REQUESTED` with an empty change list is invalid · **Done**: five outcome tests pass · **Approval**: none
- [x] T060 [US2] Deadline disclosure in the gate request — `src/main/java/agentic/shortener/orchestration/gates/GateRequestPresenter.java`
  - **Req**: FR-ORC-013, FR-ORC-011 · **Scn**: DS-C · **ADR**: ADR-008 · **Pre**: T059, T092
  - **Deps**: T059, T092 · **Par**: no · **Artifact**: request states the **gate-wait deadline** at which the run suspends **and** the computed `auto-abandon-at`
  - **TDD**: RED-FIRST · **Validate**: request payload asserted to carry both deadlines · **Docs**: `contracts/openapi.yaml` `pendingGate` · **Trace**: matrix FR-ORC-013, CL-005 addendum
  - **Guard**: **the person being asked must see the deadline in the ask**, not discover it by inspecting the run. This is additive to the inspectable field, not a substitute · **Done**: both deadlines present in every gate request · **Approval**: none
- [x] T061 [US2] Silence produces suspension — `src/test/java/agentic/shortener/orchestration/gates/SilenceTest.java`
  - **Req**: **FR-ORC-013**, **NFR-AUT-002**, SC-005 · **Scn**: DS-C · **ADR**: ADR-008 · **Pre**: T059, T091
  - **Deps**: T059, T091 · **Par**: no · **Artifact**: no decision within the wait → `SAFE_STOP`, state preserved, **no downstream stage executed**
  - **TDD**: EVIDENCE · **Validate**: clock-controlled test; asserts zero downstream executions · **Docs**: — · **Trace**: matrix FR-ORC-013, SC-005
  - **Guard**: **this is the single most important negative test in the suite.** If it passes because the run advanced, the governance claim is void · **Done**: suspension asserted; downstream execution count is zero · **Approval**: none
- [x] T061a [US2] Silence-test falsifiability proof — `src/test/java/agentic/shortener/orchestration/gates/SilenceTestFalsifiabilityTest.java`
  - **Req**: **FR-ORC-013**, **NFR-AUT-002**, SC-005 · **Scn**: DS-C · **ADR**: ADR-008 · **Pre**: T061
  - **Deps**: T061 · **Par**: no (fixture must run against T061's assertion) · **Artifact**: proof that T061 **fails** when a gate advances on silence — a fixture in which the gate handler is deliberately made to treat deadline expiry as `APPROVED`, against which T061's assertion must report a failure
  - **TDD**: EVIDENCE · **Validate**: with the fixture active, T061 **fails** and names the advanced stage; with the fixture removed, T061 passes; and the captured failure output is stored as red-phase evidence (T020) so the proof is readable rather than asserted
  - **Docs**: — · **Trace**: matrix FR-ORC-013, SC-005, NFR-AUT-002
  - **Guard**: **T061's own guard says the governance claim is void if it passes because the run advanced — and nothing proved it would not.** Four other harnesses in this plan carry a falsifiability fixture on exactly that reasoning; the one guarding silence-is-never-approval did not, which is the inconsistency the pre-implementation review found. This closes it. The fixture MUST be a test-scope override that cannot reach production configuration · **Done**: T061 demonstrated failing on the injected advance, and passing with it removed · **Approval**: none
- [x] T062 [P] [US2] Gate-record materialization enforcement — `src/main/java/agentic/shortener/orchestration/gates/MaterializationCheck.java`
  - **Req**: **FR-ORC-013** (CR-001) · **Scn**: DS-A · **ADR**: — · **Pre**: T058
  - **Deps**: T058 · **Par**: yes · **Artifact**: authorized work cannot begin until the decision's record exists in the working tree; record committed no later than the acting commit
  - **TDD**: RED-FIRST · **Validate**: test asserting a decision without `repository_record_path` cannot authorize a downstream stage · **Docs**: `CLAUDE.md` gate records · **Trace**: matrix FR-ORC-013
  - **Guard**: writing the record is **not** "work the decision authorizes", so the rule cannot deadlock · **Done**: unmaterialized decision refused · **Approval**: none
- [x] T063 [P] [US2] Actor-authority check — `src/main/java/agentic/shortener/orchestration/gates/ActorAuthority.java`
  - **Req**: FR-ORC-013, FR-ORC-021, NFR-AUT-001, **CN-012** · **Scn**: — · **ADR**: ADR-004 · **Pre**: T058
  - **Deps**: T058 · **Par**: yes · **Artifact**: a decision from an actor without recorded authority for that gate class takes no effect; `APPROVED` structurally requires `actorType: human`; the `system` actor is accepted **only** for deadline expiry and retention-driven abandonment, neither of which is an approval
  - **TDD**: RED-FIRST · **Validate**: **EC-024** — a decision from an actor with no recorded authority for the gate class takes no effect; a **self-identified** agent actor attempting an approving outcome is refused and recorded as an autonomy violation; the `system` actor is refused for every approving outcome; and an **architecture assertion that no executor package — deterministic, AI, or fake — references the gate-decision path at all**
  - **Docs**: `docs/LIMITATIONS.md` actor-identity limitation · **Trace**: matrix FR-ORC-013, EC-024, CN-012
  - **Guard**: **what this task proves and what it cannot, stated so the evidence is not over-read.** It proves that **no step in the workflow submits or satisfies a gate decision** — the architecture assertion is the real control, because it removes the code path rather than checking a field. It **does not** prove that a caller asserting `actorType: human` is human: the surface is unauthenticated by design (CN-012) and the field is a declaration, so an impersonating caller with process access is undetectable. Verified actor identity is **out of scope by owner decision, 2026-09-20**; an operator-issued per-decision token was considered and declined · **Done**: EC-024 refused; self-identified agent refused; `system` refused for approvals; **no executor package references the gate-decision path** · **Approval**: none
- [x] T063a [P] [US2] Credential-boundary architecture test — `src/test/java/agentic/shortener/arch/CredentialBoundaryTest.java`
  - **Req**: **CN-012**, FR-URL-018, FR-ORC-008, **NFR-AUT-001** · **Scn**: — · **ADR**: **ADR-006**, ADR-013 · **Pre**: T052, T069
  - **Deps**: T052, T069 · **Par**: yes (own test file) · **Artifact**: ArchUnit-style rule asserting **no `orchestration`, `policy` or `audit` package references the creator credential type, the auth filter, or the `Authorization` header**, and no control-plane controller declares a security requirement
  - **TDD**: RED-FIRST · **Validate**: rule passes clean; then a **fixture that deliberately injects a creator-credential reference into an orchestration class makes the rule fail** — the falsifiability half, in the shape of T015 and T049 · **Docs**: plan §1 trust boundary 6 · **Trace**: matrix CN-012, NFR-AUT-001
  - **Guard**: **the two identity models must be provably separate, and this is the proof.** The owner's ruling is that creators are a URL-shortener concept that must not leak into the orchestrator (CN-012). What this test **cannot** prove is that the governance surfaces are unreachable — they are unauthenticated by design, and that asymmetry is disclosed in T147, not papered over here · **Done**: rule green, and demonstrated to fail on the injected violation · **Approval**: none
- [x] T064 [P] [US2] Eight gate classes registered — `src/main/java/agentic/shortener/orchestration/gates/GateClass.java`
  - **Req**: plan §5 · **Scn**: all · **ADR**: — · **Pre**: T059
  - **Deps**: T059 · **Par**: yes · **Artifact**: **the ten plan-§5 classes**, of which the no-change-plan and **node-overrun** classes are two, each with what it blocks
  - **TDD**: RED-FIRST · **Validate**: enum completeness test asserting the count **against plan §5's table row count**, so the enum and the plan cannot drift apart silently · **Docs**: plan §5 · **Trace**: — 
  - **Guard**: a gate class present in the plan but absent from the enum is a silent skip · **Done**: ten classes; each names its blocked scope · **Approval**: none
- [x] T065 [US2] No-change-plan gate with three options — `src/main/java/agentic/shortener/orchestration/gates/NoPlanGate.java`
  - **Req**: **FR-ORC-031** (CR-001) · **Scn**: DS-A · **ADR**: **ADR-004** · **Pre**: T064, T060
  - **Deps**: T060, T064 · **Par**: no · **Artifact**: **(1)** governance-only run — downstream continues, implementation labelled **intentionally skipped by human decision**; **(2)** human-implemented — real testing judges it, executor kind `HUMAN`; **(3)** abandon → `ABANDONED`
  - **TDD**: RED-FIRST · **Validate**: one test per option, plus a **negative test that a labelled no-op cannot advance downstream stages** · **Docs**: `quickstart.md` stage-7 section · **Trace**: matrix FR-ORC-031, EC-040
  - **Guard**: **a labelled no-op that flows onward is quiet pretending.** Proceeding with nothing implemented requires a recorded human decision · **Done**: three options plus the negative test pass · **Approval**: none
- [x] T066 [P] [US2] HUMAN executor kind recorded — `src/main/java/agentic/shortener/orchestration/executor/ExecutorKind.java`
  - **Req**: **FR-ORC-029** (CR-001) · **Scn**: DS-A · **ADR**: ADR-004 · **Pre**: T065
  - **Deps**: T065 · **Par**: yes · **Artifact**: `DETERMINISTIC` | `AI` | `HUMAN`; `HUMAN` **never selectable by any caller or configuration**, only a no-plan-gate outcome
  - **TDD**: RED-FIRST · **Validate**: test asserting `HUMAN` is recorded **only** as a no-plan-gate outcome and cannot be requested by any caller or configuration · **Docs**: `contracts/workflow-state.schema.json` `executorKindUsed` · **Trace**: matrix FR-ORC-029, KE-25
  - **Guard**: `executorClass` (design-time declaration) deliberately does **not** include `HUMAN` — confirmed by the owner. Adding it would let a stage be declared human-implemented up front · **Done**: three kinds; `HUMAN` unrequestable by any caller · **Approval**: none
- [x] T067 [P] [US2] Gate inspection view — `src/main/java/agentic/shortener/orchestration/api/RunInspectionController.java`
  - **Req**: FR-ORC-008, FR-ORC-013 · **Scn**: DS-C · **ADR**: ADR-008 · **Pre**: T060
  - **Deps**: T060 · **Par**: yes · **Artifact**: reviewer sees stage, artifacts awaiting decision, supporting evidence, policy outcomes, **consequences of each decision and of no decision**
  - **TDD**: RED-FIRST · **Validate**: conformance against `RunInspection` schema; `pendingGate` carries both deadlines · **Docs**: `contracts/openapi.yaml` · **Trace**: matrix FR-ORC-008, US-2 scenario 1
  - **Guard**: inspection returns **nodes keyed by `node_key`** with `dependsOn` as node keys, and carries **no** creator credential (CN-012) — run inspection is an orchestrator surface. Inspection MUST NOT report a stage complete whose exit criteria were unmet · **Done**: schema conformance passes · **Approval**: none
- [x] T067a [US2] Gate-decision recording surface — `src/main/java/agentic/shortener/orchestration/api/GateDecisionController.java`
  - **Req**: **FR-ORC-013**, FR-ORC-021, **CN-012**, SC-005 · **Scn**: DS-A, DS-C · **ADR**: **ADR-005**, ADR-008 · **Pre**: T058, T059, T060, T012, T063a
  - **Deps**: T012, T058, T059, T060 · **Par**: no (shared delivery wiring with T067) · **Artifact**: `POST /v1/runs/{runId}/gates/{gateId}/decision` per `contracts/openapi.yaml` (CR-013) — accepts **only** `APPROVED`, `REJECTED`, `CHANGES_REQUESTED`, `ESCALATED`; requires `actorType: human`, a non-empty `reason`, and `repositoryRecordPath`; carries **no credential**
  - **TDD**: RED-FIRST · **Validate**: one test per submittable outcome; **`TIMED_OUT` rejected as an unknown value at the schema boundary**; `CHANGES_REQUESTED` with an empty change list → `400`; missing `repositoryRecordPath` → `400`; agent actor attempting `APPROVED` → `403` recorded as an autonomy violation (EC-024); second decision on the same gate → `409`; creator credential presented → **ignored, never consulted** · **Docs**: `contracts/openapi.yaml`, plan §5 · **Trace**: matrix FR-ORC-013, EC-024, SC-005
  - **Guard**: **`TIMED_OUT` is deliberately inexpressible on this surface.** Constitution III's "silence is never approval" stops being a rule that code must remember and becomes a property of the interface: a caller has no syntax for it, and the only thing that produces it is the deadline. `repositoryRecordPath` being required is the same move for §Gate semantics — a decision that exists only in workflow state cannot be submitted · **Done**: seven tests pass; contract conformance green · **Approval**: none
- [x] T068 [US2] US2 gate-governance sweep — `src/test/java/agentic/shortener/orchestration/gates/GateGovernanceIT.java`
  - **Req**: FR-ORC-013, SC-005 · **Scn**: DS-A, DS-C · **ADR**: — · **Pre**: T058–T067
  - **Deps**: T058–T067 · **Par**: no (**synchronization point**) · **Artifact**: every outcome, the silence path, materialization refusal, authority refusal, and all three no-plan options in one suite
  - **TDD**: EVIDENCE · **Validate**: `./mvnw -q verify -Dgroups=us2` · **Docs**: — · **Trace**: matrix FR-ORC-013 gains Test reference; SC-005 evidenced
  - **Guard**: US2 must be provable **without domain code changing** — the suite uses fake executors, not the shortener · **Done**: suite green; silence test explicitly reported · **Approval**: none

---

## Phase 5: User Story 3 — Software Engineer Runs and Steers the Lifecycle (P2)

**Story goal**: the graded deliverable — an explicit persisted graph with genuine parallelism, two-vote retry,
a compensation register, suspension, resumption across both restart classes, and deterministic replanning.

**Independent test criteria**: submit requirements of each scenario class and inspect graph state, transitions,
and replan records, driven entirely by **injected scriptable executors** — no AI, no network.

**Blocking**: T020 (Phase 1 complete). T080 unblocks Phase 4. **Synchronization point**: T096.

### Orchestration domain and executors

- [x] T069 [US3] Executor interface — `src/main/java/agentic/shortener/orchestration/executor/StageExecutor.java`
  - **Req**: **FR-ORC-028** · **Scn**: all · **ADR**: **ADR-004**, **ADR-006** · **Pre**: T020
  - **Deps**: T020 · **Par**: no (every executor depends on it) · **Artifact**: one interface for all twelve stages; input is workflow data
  - **TDD**: RED-FIRST · **Validate**: `-Dtest=StageExecutorContractTest` — the contract every implementation must satisfy · **Docs**: spec §Stage Executor Model · **Trace**: matrix FR-ORC-028
  - **Guard**: **no executor may branch on recognizing specific demonstration inputs** (CN-010). Deterministic executors are generic engines over workflow data — the engine does not know the build · **Done**: interface defined; contract test applies to any implementation · **Approval**: none
- [x] T070 [P] [US3] Stage effect contracts — `src/main/java/agentic/shortener/orchestration/executor/StageEffectContract.java`
  - **Req**: FR-ORC-014 rule 4, FR-ORC-016 rule 3 · **Scn**: DS-B · **ADR**: **ADR-003** · **Pre**: T069
  - **Deps**: T069 · **Par**: yes · **Artifact**: per stage — `retryable_categories`, `effect_idempotent` (**design-time, never executor self-certified**), `effect_reversibility`, `compensating_action?`
  - **TDD**: RED-FIRST · **Validate**: **registration fails to load** a stage declaring `IRREVERSIBLE` without a named compensating action (EC-034); and the loaded contract for each of the twelve nodes matches **plan §3’s `Declared retryable set` column exactly** — asserted from the plan’s table so a drifted declaration fails a test rather than silently narrowing what retries · **Docs**: `data-model.md` KE-29 · **Trace**: matrix FR-ORC-014, EC-034
  - **Guard**: a declaration contradicting the structural reversibility rule is **flagged for human review, never silently trusted** (EC-035) · **Done**: EC-034 load-failure and EC-035 review-flag both proven · **Approval**: none
- [x] T071 [P] [US3] Deterministic stage engines (five) — `src/main/java/agentic/shortener/orchestration/executor/deterministic/`
  - **Req**: FR-ORC-029 · **Scn**: all · **ADR**: **ADR-004**, **ADR-004-A2** · **Pre**: T069
  - **Deps**: T069 · **Par**: yes · **Artifact**: **five** deterministic stage engines — S1 ingestion, S8 testing, S10 security and policy, S11 release-readiness evaluation, S12 summary assembly — the genuinely deterministic stages. **No counterpart engine exists for any AI-capable stage** (ADR-004-A2)
  - **TDD**: RED-FIRST · **Validate**: each of the five engines produces its declared output deterministically — identical input, identical output, asserted per engine. **Completion of a run is not evidence that an engine is correct**, which is the gap the pre-implementation review found in this task's previous validation · **Docs**: spec §Stage Executor Model · **Trace**: matrix FR-ORC-029
  - **Guard**: **these five are not fallbacks and never were** — they are the real executors for stages whose value is repeatability (CL-003) · **Done**: five engines, each asserted deterministic on identical input · **Approval**: none
- [x] T072 [P] [US3] Scriptable fake executors — `src/test/java/agentic/shortener/orchestration/fakes/ScriptedExecutor.java`
  - **Req**: **FR-ORC-030** · **Scn**: DS-B, DS-C · **ADR**: **ADR-011** · **Pre**: T069
  - **Deps**: T069 · **Par**: yes · **Artifact**: scripts including `fail twice then succeed`, `timeout`, `return malformed envelope`, `propose transient for an undeclared category`
  - **TDD**: N/A-DOC · **Validate**: each script produces its scripted outcome deterministically · **Docs**: ADR-011 · **Trace**: matrix FR-ORC-030
  - **Guard**: **every reliability proof in T085–T096 uses these, never the live provider.** Reliability evidence must not depend on AI availability or variability · **Done**: four scripts available and deterministic · **Approval**: none
- [x] T073 [P] [US3] AI provider adapter — Claude Code CLI subprocess — `src/main/java/agentic/shortener/orchestration/executor/ai/ClaudeCodeCliStageAiProvider.java`
  - **Req**: FR-ORC-029, FR-ORC-031 · **Scn**: DS-A, DS-B, DS-C · **ADR**: **ADR-004**, **ADR-004-A1**, **ADR-004-A2** · **Pre**: T069, T018
  - **Deps**: T018, T069 · **Par**: yes · **Artifact**: adapter behind `StageAiProvider` invoking `claude -p <prompt> --model <pinned> --output-format json` as a **subprocess**; model **pinned in configuration**, and the **actually-used model id read from the response JSON** and recorded in run evidence
  - **TDD**: RED-FIRST · **Validate**: five assertions — **(1) argv array, never a shell string**, proven with a prompt containing shell metacharacters passed through verbatim and uninterpreted; (2) recorded model id equals the id in the CLI response JSON; (3) CLI unavailable → `UNAVAILABLE` → bounded retry per S7’s declared set → **suspension on exhaustion**, with the reason recorded — **there is no fallback** (ADR-004-A2), so the run does not silently degrade; it stops and asks; (4) non-JSON output → `INTERNAL` → **permanent**, never retried; (5) never exercised in the graded reliability suite
  - **Docs**: ADR-004-A1, `quickstart.md` prerequisites · **Trace**: matrix FR-ORC-029
  - **Guard**: **the stage prompt is untrusted content originating in submitted requirements.** A `/bin/sh -c` invocation with an interpolated prompt would be a remote-code-execution path from a requirement field — assertion (1) is the security-critical test of this task, not a style preference. AI output is **never executed as text**; stage-7 patches are applied on a branch and judged by the real suite (T-12)
  - **Done**: five assertions pass; SDK adapter remains a backlog item (T002), not a dropped option · **Approval**: none

#### AI adapters for all six AI-capable stages (owner decision, 2026-09-20)

**All six are in the initial build** — S2, S3, S5, S6, S7, S9, per the CL-003 executor map. The owner rejected the
earlier plan's pre-emptive reduction to two:

> The wiring estimate prices the work at human authoring speed, but the implementation is AI-authored — the same
> correction I made to ADR-001's velocity argument applies to the plan's own scoping. Do not reduce scope
> pre-emptively on human-paced estimates.

**The §14 checkpoint valve is unchanged.** If a checkpoint shows genuine time pressure, reducing AI wiring remains
the **first** cut — but taken **at a checkpoint, on the record**, never pre-emptively in the plan. Accepted costs,
in the owner's terms: demo-run verification time per AI stage (real CLI calls, latency, shared quota) and
prompt-output debugging. Both are accepted, and both are exactly what checkpoints exist to observe.

Each adapter shares the same shape: prompt template, output parsing, tests, and demo verification, all per
ADR-004 as amended — CLI transport through T073, argv-array rule, executor-kind labels, pinned model recorded
from the response JSON. Each is `[P]` because each touches its own files and depends only on T073.

- [x] T073a [P] [US3] S2 normalization AI adapter — `src/main/java/agentic/shortener/orchestration/executor/ai/stages/NormalizationAiExecutor.java`
  - **Req**: FR-ORC-009, FR-ORC-029 · **Scn**: DS-A, DS-C · **ADR**: **ADR-004**, ADR-004-A1 · **Pre**: T073, T082
  - **Deps**: T073, T082 · **Par**: yes · **Artifact**: prompt template producing identified, typed, testable `RequirementRecord`s; parser mapping output to the entity
  - **TDD**: RED-FIRST · **Validate**: parse test over recorded CLI fixtures; **negative** — malformed output classified `INTERNAL`/permanent, never coerced to an empty requirement set; demo verification with a real CLI call recorded as evidence
  - **Docs**: spec §Stage Executor Model · **Trace**: matrix FR-ORC-009
  - **Guard**: normalization MUST NOT discard, merge, or silently reinterpret a submitted requirement — the parser asserts input-count preservation · **Done**: parse tests pass; one recorded demo run stamped `AI` with the model id · **Approval**: none
- [x] T073b [P] [US3] S3 ambiguity-detection AI adapter — `src/main/java/agentic/shortener/orchestration/executor/ai/stages/AmbiguityDetectionAiExecutor.java`
  - **Req**: **FR-ORC-010**, FR-ORC-029 · **Scn**: DS-A, DS-C · **ADR**: ADR-004 · **Pre**: T073, T082
  - **Deps**: T073, T082 · **Par**: yes · **Artifact**: prompt producing `AmbiguityRecord`s with class and affected path, **plus the quality checks performed and `no_clarification_reason` when none is found**
  - **TDD**: RED-FIRST · **Validate**: DS-C's conflicting input → conflict detected with the conflicting elements **named**; DS-A's clean input → no ambiguity **and** a populated `no_clarification_reason`, asserted to be **substantive** — naming the checks performed — rather than a placeholder, because it is the only artifact that makes a non-detection inspectable; demo verification recorded
  - **Docs**: spec §DS-A, §DS-C · **Trace**: matrix FR-ORC-010
  - **Guard**: **output feeds a human gate, so variability is safe here** — but the adapter MUST NOT resolve material ambiguity itself, and an uncertain classification MUST be treated as material and routed to the human (CR-007's definition). **This adapter is the only detector of semantic contradiction in the system, and after Decision J it is the only detector of anything at this stage.** A false positive costs a question — the reason variability is acceptable here. A **miss** is the real risk: it is indistinguishable from an absence of ambiguity unless `no_clarification_reason` is recorded and readable, which is why that field is required rather than optional · **Done**: both inputs behave correctly; demo run recorded · **Approval**: none
  - **CR-049 finding (2026-09-21)**: this Guard's own CR-007 conformance requirement was never actually implemented — `buildPrompt()` asked for `MATERIAL_PENDING`/`NOT_MATERIAL` with no predicate stated anywhere, so the model had no criterion to apply. Found live via T132's two DS-A stops at S4, both false positives against fake-fixture and unit-test coverage that never exercised the real model's own unguided judgment. Fixed by CR-049, which adds CR-007's predicate to the prompt verbatim in substance, proves it contains zero DS-A-specific vocabulary, and proves via a live DS-C compensating check that genuine ambiguity is still correctly classified material after the fix.
- [x] T073c [P] [US3] S5 decomposition AI adapter — `src/main/java/agentic/shortener/orchestration/executor/ai/stages/DecompositionAiExecutor.java`
  - **Req**: FR-ORC-012, FR-ORC-029 · **Scn**: DS-A · **ADR**: ADR-004 · **Pre**: T073, T082
  - **Deps**: T073, T082 · **Par**: yes · **Artifact**: prompt producing dependency-ordered `TaskRecord`s, each tracing to ≥1 requirement
  - **TDD**: RED-FIRST · **Validate**: **orphan-task rejection** — a produced task with zero requirement references is refused, not stored; no-invented-scope check against the input requirement set; demo verification recorded
  - **Docs**: — · **Trace**: matrix FR-ORC-012
  - **Guard**: decomposition MUST NOT invent scope (Constitution I). The parser rejects tasks referencing requirements absent from the input · **Done**: orphan and invented-scope tests pass; demo run recorded · **Approval**: none
- [x] T073d [P] [US3] S6 architecture-and-design AI adapter — `src/main/java/agentic/shortener/orchestration/executor/ai/stages/DesignAiExecutor.java`
  - **Req**: FR-ORC-020, FR-ORC-029 · **Scn**: DS-A, DS-B · **ADR**: ADR-004, **ADR-006** · **Pre**: T073, T107
  - **Deps**: T073, T107 · **Par**: yes · **Artifact**: prompt producing a design output plus **contract and schema impact identification**; for brownfield, the seven-dimension impact analysis
  - **TDD**: RED-FIRST · **Validate**: all **seven** impact dimensions present or the output is rejected; demo verification recorded
  - **Docs**: plan §9 · **Trace**: matrix FR-ORC-020, KE-13
  - **Guard**: **T107 sits in this phase deliberately** — see the parallelism note; this adapter is the one of the six with a same-phase prerequisite beyond the transport. An impact analysis omitting a dimension MUST be rejected rather than accepted partially — the parser enforces completeness so a silent omission cannot reach the gate · **Done**: seven-dimension enforcement proven; demo run recorded · **Approval**: none
- [x] T073e [P] [US3] S7 implementation AI adapter — AI authors, engine applies, real suite judges — `src/main/java/agentic/shortener/orchestration/executor/ai/stages/ImplementationAiExecutor.java`
  - **Req**: **FR-ORC-031**, FR-ORC-029 · **Scn**: DS-B · **ADR**: **ADR-004**, ADR-004-A1, **ADR-004-A2** · **Pre**: T073, T065
  - **Deps**: T065, T073 · **Par**: yes · **Artifact**: AI authors the change from the design output; the **engine** applies it on a branch; the **real** build and test suite verify; failure routes back with the report under bounded attempts before escalating to the gate
  - **TDD**: RED-FIRST · **Validate**: a run where an AI-authored change **fails** verification and routes back; a run where one passes; both with executor-kind labels. **Timeout here is NOT retryable** — the git effect is non-idempotent (EC-033)
  - **Docs**: spec §FR-ORC-031 · **Trace**: matrix FR-ORC-031
  - **Guard**: **AI output is never executed as text.** The patch is applied on a branch and judged by the real suite — that pipeline *is* the safety net, and a failing AI patch being caught is the governance working, not a failed demonstration. Where no change plan exists in deterministic mode, the no-plan gate fires instead (T065) · **Done**: both runs recorded; failure-routing proven · **Approval**: none
- [x] T073f [P] [US3] S9 documentation AI adapter — `src/main/java/agentic/shortener/orchestration/executor/ai/stages/DocumentationAiExecutor.java`
  - **Req**: FR-ORC-029, Constitution X · **Scn**: DS-A, DS-B · **ADR**: ADR-004 · **Pre**: T073
  - **Deps**: T073 · **Par**: yes · **Artifact**: prompt producing documentation updates reflecting the change delivered in the same run
  - **TDD**: RED-FIRST · **Validate**: output references only behaviour present in the run's test results — a documented behaviour with no corresponding executed test is rejected; demo verification recorded
  - **Docs**: — · **Trace**: matrix FR-ORC-026 (feeds T129's assembler), T149's drift check
  - **Guard**: documentation MUST NOT describe behaviour the run did not deliver — this is the guard against the drift T149 exists to detect, applied at the point of authorship rather than only at audit · **Done**: rejection test passes; demo run recorded · **Approval**: none

### Dependency graph

- [x] T074 [US3] Graph tables and per-run materialization — `V4__orchestration_graph.sql`, `src/main/java/agentic/shortener/orchestration/graph/`
  - **Req**: **FR-ORC-002**, FR-ORC-003 · **Scn**: all · **ADR**: **ADR-008** · **Pre**: T027
  - **Deps**: T027 · **Par**: no (schema) · **Artifact**: `stage_node` with **node-keyed identity** — `node_key`, `stage_number`, `node_role`, `parent_node_key` — and `dependency_edge` with `from_node_key`/`to_node_key` and `join_semantics` (`ALL` — the only join semantic, CR-039); **edges materialized per run** from a static template (CR-011/CR-013)
  - **TDD**: RED-FIRST · **Validate**: schema validation against `contracts/workflow-state.schema.json`; **thirteen** nodes on a fresh run (eleven singletons, the S7 fan-out parent, its join); node keys unique; every stage 1–12 present exactly once as `SINGLETON` or `FAN_OUT_PARENT`; **`stage_number` read from the column, never parsed from the key** — asserted with a node whose key and column deliberately disagree · **Docs**: `data-model.md` KE-05/KE-06 · **Trace**: matrix FR-ORC-002
  - **Guard**: **edges are declared data, never inferred from call order.** Per-run materialization is what lets a replanned instance's topology differ and stay queryable — a code-only graph makes replan history unreconstructable · **Done**: graph persisted and queryable per run · **Approval**: none
- [x] T075 [P] [US3] Cycle detection before commit — `src/main/java/agentic/shortener/orchestration/graph/CycleDetector.java`
  - **Req**: FR-ORC-002, FR-ORC-019 · **Scn**: DS-C · **ADR**: ADR-008 · **Pre**: T074
  - **Deps**: T074 · **Par**: yes · **Artifact**: rejection of a cycle in the **instance** topology **before** a replanned subgraph is committed
  - **TDD**: RED-FIRST · **Validate**: EC-030 test — cycle-introducing replan rejected, nothing committed · **Docs**: — · **Trace**: matrix FR-ORC-002, EC-030
  - **Guard**: detection must run pre-commit; a post-commit check leaves a corrupt graph persisted · **Done**: EC-030 rejected pre-commit · **Approval**: none
- [x] T076 [P] [US3] Fan-out, join, and conditional-branch topology — `src/main/java/agentic/shortener/orchestration/graph/StageTemplate.java`
  - **Req**: **FR-ORC-003**, FR-ORC-001 · **Scn**: DS-A · **ADR**: ADR-003 · **Pre**: T074
  - **Deps**: T074 · **Par**: yes · **Artifact**: S7 per-task fan-out — children `S7.1..S7.n` with an explicit `S7.join` (`ALL`) gating S8; the S9∥S10 pair expressed as **two incoming `ALL` edges on S11, no join node**; S3→S4 conditional with `SKIPPED` as the not-taken state
  - **TDD**: RED-FIRST · **Validate**: **EC-018** — a join does not proceed while any required branch is incomplete or failed; overlap observable in run history · **Docs**: plan §3 topology · **Trace**: matrix FR-ORC-003, EC-018
  - **Guard**: a linear chain would satisfy none of this; the test asserts genuine overlap, not merely a graph that permits it · **Done**: two fan-out/join points and one conditional proven · **Approval**: none
- [x] T076a [P] [US3] Concurrent downstream-artifact write serialization — `src/main/java/agentic/shortener/orchestration/graph/ArtifactWriteGuard.java`
  - **Req**: **FR-ORC-003**, FR-ORC-005 · **Scn**: DS-A · **ADR**: **ADR-008** · **Pre**: T076, T080, T081
  - **Deps**: T076, T080, T081 · **Par**: yes · **Artifact**: two parallel nodes writing the same downstream artifact are **serialized or the second is rejected** — never both applied, never silently last-write-wins; the outcome is recorded with the losing node named
  - **TDD**: RED-FIRST · **Validate**: **EC-017** — two fan-out children scripted to write one artifact concurrently; exactly one write lands; the other is serialized behind it or refused with a recorded reason; `artifact_version` shows one content hash per logical artifact per write, never a lost update · **Docs**: plan §3, §6 · **Trace**: matrix FR-ORC-003, EC-017
  - **Guard**: **this is the one synchronization edge case the plan named and nothing covered** (analyze finding M7). FR-ORC-003's negative criterion requires it: *"parallel branches MUST NOT corrupt a shared downstream artifact."* A join-blocking test (T076, EC-018) proves ordering at the join and says nothing about concurrent writes before it · **Done**: EC-017 proven in both dispositions — serialized and rejected — with the choice declared per artifact class · **Approval**: none

### Workflow state machine

- [x] T077 [US3] Run state machine — `src/main/java/agentic/shortener/orchestration/state/RunState.java`
  - **Req**: **FR-ORC-017** (CL-005), NFR-REL-001 · **Scn**: all · **ADR**: **ADR-008** · **Pre**: T074
  - **Deps**: T074 · **Par**: no (state root) · **Artifact**: `PENDING`, `RUNNING`, `SAFE_STOP`, `COMPLETED`, `REJECTED`, `ABANDONED`; **terminal set is exactly the last three**; `SAFE_STOP` non-terminal
  - **TDD**: RED-FIRST · **Validate**: state-machine test asserting `SAFE_STOP` is non-terminal and the terminal set is exactly three; schema conditionals — `terminalState` non-null **iff** terminal, `autoAbandonAt` non-null **iff** `SAFE_STOP` · **Docs**: `data-model.md` KE-04 · **Trace**: matrix FR-ORC-017
  - **Guard**: **a state cannot be both terminal and resumable.** This was a contradiction in the approved spec until CL-005 resolved it; the test is what keeps it resolved · **Done**: both schema conditionals enforced · **Approval**: none
- [x] T078 [US3] Stage state machine and allowed transitions — `src/main/java/agentic/shortener/orchestration/state/StageState.java`
  - **Req**: FR-ORC-006 · **Scn**: all · **ADR**: ADR-008 · **Pre**: T077
  - **Deps**: T077 · **Par**: no · **Artifact**: twelve stage states with entry/exit criteria evaluated before execution and before completion
  - **TDD**: RED-FIRST · **Validate**: a stage with unmet entry criteria does not execute; one with unmet exit criteria is not marked complete · **Docs**: plan §3 · **Trace**: matrix FR-ORC-006
  - **Guard**: a stage reporting success with **no output artifact** must fail its exit criteria (EC-029) · **Done**: both directions asserted; EC-029 covered · **Approval**: none
- [x] T079 [US3] **Prohibited**-transition rejection tests — `src/test/java/agentic/shortener/orchestration/state/ProhibitedTransitionTest.java`
  - **Req**: FR-ORC-006, FR-ORC-013 · **Scn**: all · **ADR**: **ADR-003**, ADR-008 · **Pre**: T078
  - **Deps**: T078 · **Par**: no · **Artifact**: each prohibited transition asserted **rejected**, not merely absent
  - **TDD**: EVIDENCE · **Validate**: `RUNNING→SUCCEEDED` without exit criteria; `AWAITING_APPROVAL→SUCCEEDED` without a recorded decision; `RETRY_WAIT→RUNNING` with an exhausted bound or a missing vote; `FAILED→SUCCEEDED`; leaving `SAFE_STOP` other than by human decision or retention; `COMPENSATING` on an erasable effect; `ROLLING_BACK` on an immutable-store effect
  - **Docs**: plan §3 · **Trace**: matrix FR-ORC-006 · **Guard**: **building the engine means owning transition correctness** — this suite is the control that makes ADR-003's build decision defensible · **Done**: seven prohibited transitions each rejected · **Approval**: none
- [x] T080 [US3] Atomic state-plus-transition write — `src/main/java/agentic/shortener/orchestration/store/JdbcRunStore.java` (private `insertTransition`; also duplicated in `SafeStopHandler.java` for its own SAFE_STOP-crossing transitions — see that class's own javadoc for why. Built without a standalone `TransitionWriter` class; T123's traceability sweep found this line's original name stale)
  - **Req**: FR-ORC-004, NFR-AUD-001 · **Scn**: all · **ADR**: **ADR-008** · **Pre**: T079, T027
  - **Deps**: T027, T079 · **Par**: no (**synchronization point — unblocks Phase 4**) · **Artifact**: current state and its append-only `state_transition` row written in **one transaction**
  - **TDD**: RED-FIRST · **Validate**: test asserting a state change **cannot commit** without its transition row · **Docs**: `data-model.md` · **Trace**: matrix FR-ORC-004
  - **Guard**: this single transaction is what closes the divergence gap pure event sourcing would otherwise be bought to close — the cheap fix ADR-008 relies on · **Done**: divergence impossible; transition history complete · **Approval**: none

### Context and decision lineage

- [x] T081 [P] [US3] Artifact provenance and versioning — `src/main/java/agentic/shortener/orchestration/lineage/ArtifactProvenance.java` (built as `ArtifactProvenance`, alongside `LineageStore.java`/`ArtifactProvenanceQuery.java`; T123's traceability sweep found this line's original name stale)
  - **Req**: **FR-ORC-005** · **Scn**: all · **ADR**: ADR-008, ADR-010 · **Pre**: T080
  - **Deps**: T080 · **Par**: yes · **Artifact**: content hash plus producing stage; for any artifact, which stage produced it, from which inputs, under which decisions
  - **TDD**: RED-FIRST · **Validate**: provenance query over a completed run returns a complete chain · **Docs**: `data-model.md` KE-11/artifact_version · **Trace**: matrix FR-ORC-005
  - **Guard**: an artifact MUST NOT exist in a run without recorded provenance · **Done**: no orphan artifacts in a completed run · **Approval**: none
- [x] T082 [P] [US3] Requirement, ambiguity, clarification, and task records — `src/main/java/agentic/shortener/orchestration/lineage/`
  - **Req**: FR-ORC-009, FR-ORC-010, FR-ORC-011, FR-ORC-012 · **Scn**: DS-A, DS-C · **ADR**: ADR-008 · **Pre**: T080
  - **Deps**: T080 · **Par**: yes · **Artifact**: RequirementRecord(`external_id`, `type`, `statement`, `status`); AmbiguityRecord(`ambiguity_class`, `affected_path`, `resolution_state`, `quality_checks_performed`, **`no_clarification_reason?`**); ClarificationDecision(`actor`, `question`, `answer`, `decided_at`); TaskRecord(`requirement_ids` **≥ 1, enforced**)
  - **TDD**: RED-FIRST · **Validate**: orphan-task test — a TaskRecord with zero requirement IDs is rejected · **Docs**: `data-model.md` KE-07..KE-12 · **Trace**: matrix FR-ORC-009..012, KE-08, KE-09
  - **Guard**: `no_clarification_reason` is what DS-A requires recorded when a well-formed requirement correctly skips S4 · **Done**: orphan task rejected; normalization preserves every submitted requirement · **Approval**: none
- [x] T082a [US3] Run creation and requirement submission surface — `src/main/java/agentic/shortener/orchestration/api/RunSubmissionController.java`
  - **Req**: **FR-ORC-007**, FR-ORC-001, NFR-OBS-001, **CN-012**, SC-016 · **Scn**: all · **ADR**: **ADR-005**, ADR-008 · **Pre**: T074, T077, T080, T012, T063a, T131a
  - **Deps**: T012, T074, T077, T080, T131a · **Par**: no (shared delivery wiring) · **Artifact**: `POST /v1/runs` per `contracts/openapi.yaml` (CR-013) — admits a requirement, materializes the run's thirteen nodes and their edges, returns a **durable run identifier**; **no credential**. (This body's earlier "optional `ai` flag defaulting off" is stale — CR-028/Decision J retired keyless mode project-wide before this task was built; `contracts/openapi.yaml`'s own `CreateRunRequest` schema already carries no such flag, and none is built.)
  - **TDD**: RED-FIRST · **Validate**: submission returns a persisted run id that then appears in `RunInspection`; a malformed submission returns `400` with **nothing persisted and no identifier issued**; store unavailable → `503` with no partial run; the returned run holds **thirteen nodes** with S4 `BLOCKED` and S7's parent plus join present; the identifier propagates to audit rows, log lines, metric labels and trace spans for that run; a presented creator credential is **never read** · **Docs**: `contracts/openapi.yaml`, `quickstart.md` §4 · **Trace**: matrix FR-ORC-007, SC-016, NFR-OBS-001
  - **Guard**: **FR-ORC-007 had zero tasks and zero contract operations before this** (analyze finding H7) — a Confirmed requirement with no way to satisfy it, which also meant a reviewer could not start a run of their own and SC-016 was unreachable through any documented interface. Submitted requirement text is **untrusted content**: it reaches AI stages as prompt input, so it must flow to the CLI adapter as an argv element and never as a shell string (ADR-004-A1, T073). **The response returns before the run is fully driven** — `Conductor.submit` performs only the fast, deterministic half (thirteen nodes persisted, S1 admitted); the rest is advanced on a background thread after the response, matching the contract's own `runCreated` example (S1 `SUCCEEDED`, S2 already `RUNNING`) · **Done**: `RunSubmissionControllerIT` (4 tests) + `RunSubmissionControllerTest` (1 test, the 503 path) pass; identifier propagation asserted; node materialization asserted · **Approval**: none

- [x] T107 [P] [US4] Seven-dimension brownfield impact analysis — `src/main/java/agentic/shortener/orchestration/impact/ImpactAnalysis.java`
  - **Req**: **FR-ORC-020**, SC-008 · **Scn**: **DS-B** · **ADR**: **ADR-006** · **Pre**: T082
  - **Deps**: T082 · **Par**: yes · **Artifact**: impacted components, interfaces, data flows, tests, documentation, regression risks, rollout/rollback — **all seven, none omitted silently**
  - **TDD**: RED-FIRST · **Validate**: analysis exists and its **timestamp precedes the first code modification**; omission of any dimension rejected · **Docs**: plan §9 · **Trace**: matrix FR-ORC-020
  - **Guard**: **moved into Phase 5 by CR-016** — this is the orchestration impact-analysis model, its file already lives under `orchestration/impact/`, and it never needed the change-request model it was previously chained to; that spurious dependency is what pushed T073d behind a Phase-6 synchronization point. Implementation MUST NOT begin on a brownfield change before the analysis is recorded, and the analysis MUST NOT be reconstructed afterwards · **Done**: seven dimensions enforced; ordering asserted · **Approval**: none
### Reliability: retry, timeout, fallback

- [x] T083 [US3] Standard failure envelope — `src/main/java/agentic/shortener/orchestration/reliability/FailureEnvelope.java`
  - **Req**: **FR-ORC-014** (CL-006) · **Scn**: DS-B · **ADR**: **ADR-003** · **Pre**: T069
  - **Deps**: T069 · **Par**: no (consumed by T084–T087) · **Artifact**: closed category set `TIMEOUT` | `UNAVAILABLE` | `RATE_LIMITED` | `INVALID_INPUT` | `INTERNAL` | `UNKNOWN`, plus the executor's **proposed** classification and detail
  - **TDD**: RED-FIRST · **Validate**: executors translate provider errors into the envelope; a stage's declared retryable set is expressed over **standard categories only** · **Docs**: plan §6 · **Trace**: matrix FR-ORC-014, KE-26
  - **Guard**: provider knowledge stays in the plugin; the core never learns a vendor taxonomy (this is why the repaired static-list option was rejected) · **Done**: six categories; translation tested for at least two provider error shapes · **Approval**: none
- [x] T084 [US3] Two-vote retry ruling — `src/main/java/agentic/shortener/orchestration/reliability/RetryRuling.java`
  - **Req**: **FR-ORC-014** (CL-006), NFR-REL-002 · **Scn**: DS-B · **ADR**: **ADR-003** · **Pre**: T083, T070
  - **Deps**: T070, T083 · **Par**: no · **Artifact**: `retries = declared retryable set ∩ executor proposal`; **executor holds veto, never grant**; every ruling recorded with **both signatures**
  - **TDD**: RED-FIRST · **Validate**: four-case matrix — both yes → retry; declared-only → no retry; executor-only → no retry; `UNKNOWN` or malformed → **permanent** (EC-031, EC-032) · **Docs**: plan §6 · **Trace**: matrix FR-ORC-014, KE-27
  - **Guard**: **default-deny.** An unrecognized failure never defaults to retryable, mirroring EC-025's rule for policy checks. The executor diagnoses; the orchestrator rules, because only it holds attempts consumed, compensation already issued, replan invalidation, and blocking state · **Done**: four cases pass; two-signature record present · **Approval**: none
- [x] T085 [P] [US3] Bounded retry with backoff — `src/main/java/agentic/shortener/orchestration/reliability/RetryPolicy.java`
  - **Req**: FR-ORC-014 · **Scn**: DS-B · **ADR**: ADR-003 · **Pre**: T084, T072
  - **Deps**: T072, T084 · **Par**: yes · **Artifact**: **PVT-007 — 3 attempts, exponential from 1 s**; each attempt individually recorded
  - **TDD**: EVIDENCE · **Validate**: `fail twice then succeed` script → two recorded attempts then success; bound exhaustion → **failure, never success** · **Docs**: plan §6 · **Trace**: matrix FR-ORC-014, KE-15
  - **Guard**: **retries MUST NOT be unbounded**; a run-level circuit breaker is deferred to backlog, so per-stage bounds plus run-level blocks are the in-scope controls · **Done**: bounded retry and exhaustion behaviour proven · **Approval**: none
- [x] T086 [P] [US3] Timeout gating on idempotency — `src/main/java/agentic/shortener/orchestration/reliability/TimeoutPolicy.java`
  - **Req**: FR-ORC-014 rule 4, FR-ORC-018 · **Scn**: DS-B · **ADR**: ADR-003 · **Pre**: T084, T070
  - **Deps**: T070, T084 · **Par**: yes · **Artifact**: per-node **escalation thresholds** are PVT-016's schedule, and `TIMEOUT` is retryable **only** where the stage's design-time contract declares the effect idempotent or repeat-safe. **Timeout gating on idempotency is unchanged**: this task keeps deciding retry *eligibility*, and **T086a** decides what a threshold breach *does*. The two are separate because duration and eligibility are separate decisions
  - **TDD**: EVIDENCE · **Validate**: **EC-033** — timeout on S7 (non-idempotent git effect) is **not** retried; timeout on an idempotent stage is; and each node's configured threshold matches **PVT-016**, asserted from configuration so a drifted value fails a test rather than changing behaviour silently · **Docs**: plan §6 · **Trace**: matrix FR-ORC-014, EC-033
  - **Guard**: a timeout means completion is **unknown**; replaying a maybe-completed non-idempotent effect violates exactly-once. The contract is design-time, never executor self-certification · **Done**: both cases proven · **Approval**: none
- [ ] T086a [US3] Node-overrun escalation gate — `src/main/java/agentic/shortener/orchestration/reliability/OverrunEscalation.java`
  - **Req**: **FR-ORC-014 rule 6**, **PVT-016**, FR-ORC-013, FR-ORC-017, NFR-AUT-002 · **Scn**: DS-B · **ADR**: ADR-003, ADR-008 · **Pre**: T086, T059, T060, T064
  - **Deps**: T059, T060, T064, T086 · **Par**: no (consumes the gate machinery and the threshold policy) · **Artifact**: on breach of a node's **PVT-016 escalation threshold**, raise a **node-overrun gate** carrying elapsed-versus-expected and the node's **last observed activity**, offering **keep waiting** (re-arm the threshold) or **kill the node** (fail by overrun). Liveness is read from **existing** state-transition rows, trace spans and audit events
  - **TDD**: RED-FIRST · **Validate**: breach → gate raised with both figures present; **keep waiting** → threshold re-armed and the node continues, with the re-arm recorded; **kill** → node fails by overrun, then the standard envelope and two-vote rule apply and a **non-idempotent node is not retried** (EC-033); **silence** → the gate-wait deadline elapses and the run **suspends**, never kills; and an architecture assertion that **no new liveness component exists** — no scheduler, heartbeat or watchdog class outside the existing telemetry packages
  - **Docs**: plan §3, §5, §6 · **Trace**: matrix FR-ORC-014, PVT-016, FR-ORC-013
  - **Guard**: **the orchestrator may never kill a node on its own authority.** A slow node is not a failed node — the orchestrator can see that expected progress has not happened, but only a human can decide whether that means wait or stop, and killing on a timer would put that decision back inside the governed system. The owner accepted the counter-case explicitly: an unattended run **stalls and then suspends** where an automatic kill-and-retry might have self-healed, because auto-retrying possibly-half-finished work is the exact danger the retry rules exist to prevent · **Done**: four behaviours proven (both choices, silence, no-new-component); overrun never recorded as a failure before the human decides · **Approval**: none
- [ ] ~~T087~~ **[RETIRED — Decision K, CR-032]** Declared fallback — *no artifact; nothing is built*
  - **Req**: ~~FR-ORC-015~~ *(retired, CR-032)* · **Scn**: ~~DS-B~~ · **ADR**: ADR-004, **ADR-004-A2** · **Pre**: —
  - **Deps**: — · **Par**: n/a · **Artifact**: **none.** Retired with FR-ORC-015: every declared fallback was a deterministic counterpart struck by Decision J, so there is nothing for a handler to activate
  - **TDD**: N/A-RETIRED · **Validate**: nothing to validate; **the absence is asserted instead** — T014's architecture test asserts **no `FallbackHandler` type exists** in `orchestration/reliability`, so a later implementer cannot reintroduce the behaviour without the retirement being revisited · **Docs**: `docs/LIMITATIONS.md` (T147) · **Trace**: matrix FR-ORC-015 *(retired)*, EC-023 *(retired)*
  - **Guard**: **retired in place, not deleted** — a numbering gap is a question a reviewer cannot answer from the artifact, and a struck task with its reason attached is the record of a decision. The owner chose a documented honest absence over a ceremonial presence: one counterpart kept purely to keep "fallback" claimable would be the exists-mainly-to-be-claimed defect this project rejects · **Done**: retirement recorded; the negative architecture assertion in T014 present; the limitations entry present · **Approval**: none

- [x] T088 [US3] Compensation register — `src/main/java/agentic/shortener/orchestration/reliability/CompensationRegister.java`
  - **Req**: **FR-ORC-016** (CL-007) · **Scn**: DS-B · **ADR**: **ADR-003** · **Pre**: T070, T027
  - **Deps**: T027, T070 · **Par**: no (consumed by T089–T090) · **Artifact**: the seven confirmed effect classes — working-tree writes and local un-pushed commits **erasable**; gate decisions, audit records, short links, redirect events **compensate-only**; AI invocations **nothing to compensate**
  - **TDD**: RED-FIRST · **Validate**: structural default derived from where the effect lands; unclassified → **compensate-only**; `ROLLING_BACK` on an immutable-store effect refused · **Docs**: spec §Compensation Register · **Trace**: matrix FR-ORC-016, KE-28
  - **Guard**: **an unwanted short link is corrected by setting it to expired, never deleted** — links are never deleted (T-13, EX-003). Where no compensating action is known: touch nothing and suspend · **Done**: seven rows encoded; unclassified default proven · **Approval**: none
- [x] T089 [P] [US3] Rollback of erasable effects — `src/main/java/agentic/shortener/orchestration/reliability/RollbackHandler.java`
  - **Req**: FR-ORC-016 · **Scn**: DS-B · **ADR**: ADR-003 · **Pre**: T088
  - **Deps**: T088 · **Par**: yes · **Artifact**: working-tree discard and local branch reset, labelled **`ROLLBACK`**
  - **TDD**: EVIDENCE · **Validate**: rollback run history labelled rollback, distinct from compensation · **Docs**: plan §6 · **Trace**: matrix FR-ORC-016, CL-004, CN-009
  - **Guard**: **local un-pushed work is the only erasable class.** Rollback on anything else is an invalid rollback claim · **Done**: rollback applied and labelled; immutable-store attempt refused · **Approval**: none
- [x] T090 [P] [US3] Compensation of irreversible effects — `src/main/java/agentic/shortener/orchestration/reliability/CompensationHandler.java`
  - **Req**: FR-ORC-016 · **Scn**: DS-B · **ADR**: ADR-003 · **Pre**: T088
  - **Deps**: T088 · **Par**: yes · **Artifact**: superseding gate record, appended audit correction, link set to expired — each labelled **`COMPENSATION`**
  - **TDD**: EVIDENCE · **Validate**: **EC-022** — at most one correction per effect, **even when a later retry succeeds** · **Docs**: plan §6 · **Trace**: matrix FR-ORC-016, KE-16, EC-022
  - **Guard**: compensation MUST NOT be described as rollback or vice versa; the run history must let a reviewer tell which occurred · **Done**: three compensating actions proven; double-compensation prevented · **Approval**: none
- [x] T091 [US3] Safe-stop suspension — `src/main/java/agentic/shortener/orchestration/state/SafeStopHandler.java`
  - **Req**: **FR-ORC-017** · **Scn**: DS-C · **ADR**: ADR-008 · **Pre**: T077, T084, T088
  - **Deps**: T077, T084, T088 · **Par**: no · **Artifact**: triggers — gate timeout, unrecoverable failure, blocking policy `FAIL`, **unrecognized failure classification**, **effect with no known compensating action**. State consistent, resumable, reason emitted
  - **TDD**: EVIDENCE · **Validate**: one record per trigger class; state after safe-stop asserted resumable · **Docs**: plan §3, §6 · **Trace**: matrix FR-ORC-017
  - **Guard**: safe-stop MUST NOT continue past the blocked gate on reduced scope without a new human decision, and MUST NOT leave partially applied effects unrecorded · **Done**: five trigger classes recorded · **Approval**: none
- [x] T092 [P] [US3] Idle retention and auto-abandonment — `src/main/java/agentic/shortener/orchestration/state/RetentionPolicy.java`
  - **Req**: **FR-ORC-032** (CL-005) · **Scn**: DS-C · **ADR**: ADR-008 · **Pre**: T091
  - **Deps**: T091 · **Par**: yes · **Artifact**: idle clock from **last activity, never creation**; **PVT-015 — 90 days**; expiry → `ABANDONED` with an audit event citing the retention policy version, actor type `system`, authority "pre-approved retention policy"; `auto-abandon-at` exposed via inspection
  - **TDD**: RED-FIRST · **Validate**: **EC-036** — activity one day before expiry **resets** the clock; **EC-037** — expiry while a gate is pending abandons terminally and **does not satisfy the gate** · **Docs**: plan §3 · **Trace**: matrix FR-ORC-032, EC-036, EC-037
  - **Guard**: auto-abandonment is a **run lifecycle** transition and is **not** a data purge — there is no purge anywhere (NFR-AUD-003, CR-017); an abandoned run's records stay in the tables like every other record. **Policy-driven abandonment is never an approval.** A run that received attention must not be reaped on age. Demonstration runs may compress the period, labelled per AS-007 · **Done**: EC-036 and EC-037 both proven · **Approval**: none
- [x] T093 [US3] Resumption across orchestrator-process restart — `src/main/java/agentic/shortener/orchestration/recovery/ResumeService.java`
  - **Req**: **FR-ORC-018**, NFR-REL-003, SC-006 · **Scn**: DS-C · **ADR**: ADR-008 · **Pre**: T080, T091
  - **Deps**: T080, T091 · **Par**: no · **Artifact**: resumption from persisted state reaching a deterministic terminal outcome, **committed effects exactly once**
  - **TDD**: EVIDENCE · **Validate**: process killed at **every stage boundary**; each resumes; zero duplicated committed effects (EC-015) · **Docs**: plan §6 · **Trace**: matrix FR-ORC-018, EC-015
  - **Guard**: recovery MUST NOT report a state it cannot substantiate from persisted records (EC-016) · **Done**: every stage boundary proven; duplicate count zero · **Approval**: none
- [ ] T094 [P] [US3] Resumption across persistence-layer restart — `src/test/java/agentic/shortener/recovery/StoreRestartResumeIT.java`
  - **Req**: **FR-ORC-004** (CL-009), NFR-REC-001 · **Scn**: DS-C · **ADR**: **ADR-002**, ADR-012 · **Pre**: T093
  - **Deps**: T093 · **Par**: yes · **Artifact**: `docker compose restart db` mid-run → `UNAVAILABLE` in the envelope → proposed transient → bounded retry → suspension on exhaustion → correct resumption when the store returns
  - **TDD**: EVIDENCE · **Validate**: **EC-038** against a **real restartable store process** · **Docs**: `quickstart.md` §3 · **Trace**: matrix FR-ORC-004, EC-038
  - **Guard**: **this proof is why an embedded store was disqualified** — there would be nothing to kill. An in-memory substitute here would reimport the very loophole CL-009 closed · **Done**: EC-038 proven end to end · **Approval**: none
- [x] T095 [P] [US3] Run lease preventing concurrent resumption — `src/main/java/agentic/shortener/orchestration/recovery/RunLease.java`
  - **Req**: FR-ORC-018 · **Scn**: — · **ADR**: ADR-008 · **Pre**: T093
  - **Deps**: T093 · **Par**: yes · **Artifact**: run-level lease making concurrent resumption of one persisted state impossible
  - **TDD**: RED-FIRST · **Validate**: **EC-026** — two concurrent resume attempts; exactly one advances · **Docs**: plan §6 · **Trace**: matrix FR-ORC-018, EC-026
  - **Guard**: without this, resumption is the most likely source of duplicated committed effects · **Done**: EC-026 proven · **Approval**: none
- [x] T096 [US3] Dynamic replanning with approval voiding — `src/main/java/agentic/shortener/orchestration/replan/ReplanService.java`
  - **Req**: **FR-ORC-019**, NFR-CHG-002, SC-007 · **Scn**: DS-C · **ADR**: **ADR-009** · **Pre**: T074, T075, T058
  - **Deps**: T058, T074, T075 · **Par**: no (**synchronization point**) · **Artifact**: **transitive downstream closure** over persisted `dependency_edge` rows from the changed artifact's producing stage; affected **nodes** → `INVALIDATED`; approvals voided by **superseding gate record**; `ReplanEvent` recording cause, **`invalidatedNodes` as node keys**, voided approvals — node-level, so a replan may invalidate one fan-out child and leave its siblings intact
  - **TDD**: EVIDENCE · **Validate**: affected set equals the computed closure; **unaffected parallel branches untouched**; **EC-020** — a voided approval cannot satisfy the re-planned stage; **EC-019** — in-flight stage not corrupted; **EC-030** — cycle-introducing replan rejected
  - **Docs**: plan §3, §9 · **Trace**: matrix FR-ORC-019, KE-14, EC-019, EC-020, EC-030
  - **Guard**: **the affected set decides which human approvals are voided, so it must be deterministic and independently recomputable by a reviewer.** An AI-determined set is disqualified: the governed component proposes, it never rules on governance scope. Closure over declared edges errs toward over-invalidation — a human-attention cost, never a surviving stale artifact · **Done**: five assertions pass; replan event queryable · **Approval**: none

---

## Phase 6: User Story 4 — Release Owner Determines Readiness (P3)

**Story goal**: policy evaluation that actually blocks, exceptions that expire, change control that precedes
change, and a readiness determination no agent can self-certify.

**Independent test criteria**: seed each blocking condition individually and confirm the determination blocks and
names it.

**Blocking**: T096. **Synchronization point**: T110.

- [x] T097 [US4] Policy model and version — `src/main/java/agentic/shortener/policy/PolicySet.java`
  - **Req**: **FR-ORC-022**, Constitution VI · **Scn**: all · **ADR**: **ADR-005** · **Pre**: T027
  - **Deps**: T027 · **Par**: no (consumed by T098–T105) · **Artifact**: `policy-set-1.1.0` as a versioned, loadable definition covering all seven domains
  - **TDD**: RED-FIRST · **Validate**: every run records the **policy version evaluated**; a run without it is invalid · **Docs**: plan §9 · **Trace**: matrix FR-ORC-022
  - **Guard**: **policy execution without a recorded policy version is a detection target** — the schema requires `policySetVersion` as a const · **Done**: version recorded per run; missing version rejected · **Approval**: none
- [x] T098 [P] [US4] Twelve policy definitions with mandatory/advisory split — `src/main/java/agentic/shortener/policy/definitions/`
  - **Req**: FR-ORC-022 · **Scn**: all · **ADR**: ADR-005 · **Pre**: T097
  - **Deps**: T097 · **Par**: yes · **Artifact**: `POL-SEC-001..003`, `POL-PRIV-001`, `POL-AUD-001`, `POL-DEP-001`, `POL-LIC-001`, `POL-CHG-001..003`, `POL-TST-001`, `POL-TRC-001` — **twelve, all mandatory**. `POL-CHG-003` added (a MAJOR contract change carries a new major version, CR-013); `POL-AUD-002` **removed** because with no retention path it could never meaningfully evaluate (CR-017)
  - **TDD**: RED-FIRST · **Validate**: completeness test counting **twelve** against plan §9's table and asserting **no advisory instance exists**; each carries its domain and mandatory flag · **Docs**: plan §9 · **Trace**: matrix FR-ORC-022
  - **Guard**: a policy in the plan but absent from the definitions is a silent gap in compliance coverage · **Done**: twelve defined, all mandatory; split matches the plan · **Approval**: none
- [x] T099 [P] [US4] Policy-evaluation schema conformance — `src/test/java/agentic/shortener/policy/PolicyEvaluationSchemaTest.java`
  - **Req**: FR-ORC-022 · **Scn**: — · **ADR**: ADR-005 · **Pre**: T098
  - **Deps**: T098 · **Par**: yes · **Artifact**: output validated against `contracts/policy-evaluation.schema.json`
  - **TDD**: RED-FIRST · **Validate**: **exactly four** outcome values accepted; a fifth rejected · **Docs**: `contracts/` · **Trace**: matrix FR-ORC-022
  - **Guard**: there is no `UNKNOWN` or `SKIPPED` outcome; the schema is the enforcement · **Done**: four accepted, fifth rejected · **Approval**: none
- [x] T100 [US4] Compliance evaluation stage (S10) — `src/main/java/agentic/shortener/policy/PolicySetEvaluator.java` (implements the `PolicyEvaluator` port T071 already built for S10's `PolicyEvaluationEngine`; T123's traceability sweep found this line's original "PolicyEvaluationStage.java" name stale — the stage class itself predates this task, this task fills the port)
  - **Req**: **FR-ORC-022** · **Scn**: DS-B · **ADR**: **ADR-004** · **Pre**: T099, T069
  - **Deps**: T069, T099 · **Par**: no · **Artifact**: deterministic, **real** evaluation producing one outcome per applicable policy
  - **TDD**: RED-FIRST · **Validate**: repeatable verdicts across runs on identical input · **Docs**: plan §9 · **Trace**: matrix FR-ORC-022, KE-17
  - **Guard**: **verdicts must be repeatable**, which is why this stage is deterministic and never AI-capable · **Done**: identical input yields identical verdicts · **Approval**: none
- [x] T101 [P] [US4] Unevaluable check never defaults to PASS — `src/test/java/agentic/shortener/policy/UnevaluableCheckTest.java`
  - **Req**: FR-ORC-022 · **Scn**: — · **ADR**: — · **Pre**: T100
  - **Deps**: T100 · **Par**: yes · **Artifact**: a check that cannot be evaluated records `FAIL`, not `PASS`
  - **TDD**: EVIDENCE · **Validate**: **EC-025** · **Docs**: plan §9 · **Trace**: matrix FR-ORC-022, EC-025
  - **Guard**: this is the precedent the whole default-deny family rests on — retry classification and effect reversibility both cite it · **Done**: EC-025 proven · **Approval**: none
- [x] T102 [US4] Mandatory FAIL blocks downstream progression — `src/main/java/agentic/shortener/policy/BlockingEnforcer.java`
  - **Req**: **FR-ORC-022** · **Scn**: DS-B · **ADR**: — · **Pre**: T101, T091
  - **Deps**: T091, T101 · **Par**: no · **Artifact**: a mandatory `FAIL` halts downstream stages and routes to safe-stop
  - **TDD**: EVIDENCE · **Validate**: seeded mandatory `FAIL` → downstream stage count zero; run suspended with the policy named · **Docs**: plan §9 · **Trace**: matrix FR-ORC-022
  - **Guard**: **a compliance failure that does not block is a detection target.** Reporting without blocking fails this task · **Done**: blocking proven; policy named in the reason · **Approval**: none
- [x] T103 [P] [US4] Policy exception workflow with seven fields — `src/main/java/agentic/shortener/policy/PolicyException.java`
  - **Req**: Constitution §Exception procedure · **Scn**: — · **ADR**: — · **Pre**: T100
  - **Deps**: T100 · **Par**: yes · **Artifact**: policy, **precise clause**, reason, scope, approving authority, **compensating control**, residual risk, approval timestamp, expiry or review condition — plus `repositoryRecordPath` under `docs/governance/exceptions/`
  - **TDD**: RED-FIRST · **Validate**: schema rejects an exception missing any field, especially the **compensating control** · **Docs**: `CLAUDE.md` exceptions location · **Trace**: matrix FR-ORC-022, KE-18
  - **Guard**: **a missing compensating control is a detection target**; the schema is the control · **Done**: all seven required; omission rejected · **Approval**: none
- [x] T104 [P] [US4] Unapproved exception treated as FAIL — `src/test/java/agentic/shortener/policy/UnapprovedExceptionTest.java`
  - **Req**: FR-ORC-022 · **Scn**: — · **ADR**: — · **Pre**: T103
  - **Deps**: T103 · **Par**: yes · **Artifact**: `EXCEPTION_REQUESTED` without recorded approval evaluates as `FAIL`
  - **TDD**: EVIDENCE · **Validate**: blocking behaviour asserted · **Docs**: plan §9 · **Trace**: matrix FR-ORC-022
  - **Guard**: an unapproved exception must not silently function as a waiver · **Done**: treated as `FAIL`; progression blocked · **Approval**: none
- [x] T105 [P] [US4] Expired exception treated as violation — `src/test/java/agentic/shortener/policy/ExpiredExceptionTest.java`
  - **Req**: FR-ORC-022 · **Scn**: — · **ADR**: — · **Pre**: T103
  - **Deps**: T103 · **Par**: yes · **Artifact**: an approved exception passing its expiry mid-run blocks progression
  - **TDD**: EVIDENCE · **Validate**: **EC-027** with clock control · **Docs**: plan §9 · **Trace**: matrix FR-ORC-022, EC-027
  - **Guard**: expiry must be evaluated **at use**, not only at approval · **Done**: EC-027 proven · **Approval**: none
- [x] T106 [P] [US4] Change-request and impact-analysis model — `src/main/java/agentic/shortener/policy/ChangeRequest.java`
  - **Req**: Constitution VI, `POL-CHG-001`, NFR-CHG-001 · **Scn**: DS-B · **ADR**: — · **Pre**: T100
  - **Deps**: T100 · **Par**: yes · **Artifact**: record carrying owner, version impact, compatibility impact, affected consumers, tests, documentation, rollout steps, approval
  - **TDD**: RED-FIRST · **Validate**: a contract or schema change without a record → `POL-CHG-001` `FAIL` · **Docs**: `CLAUDE.md` change-control location · **Trace**: matrix FR-ORC-022
  - **Guard**: **material change implemented without impact analysis is a detection target**; the policy check is the detector · **Done**: eight fields required; missing record blocks · **Approval**: none
- [x] T108 [P] [US4] Upstream change triggers downstream invalidation — `src/test/java/agentic/shortener/policy/UpstreamChangeIT.java`
  - **Req**: Constitution §Workflow, FR-ORC-019 · **Scn**: DS-C · **ADR**: ADR-009 · **Pre**: T096, T106
  - **Deps**: T096, T106 · **Par**: yes · **Artifact**: a change to an approved artifact invalidates affected downstream stages rather than leaving stale artifacts
  - **TDD**: EVIDENCE · **Validate**: stale-artifact count zero after an upstream change · **Docs**: plan §9 · **Trace**: matrix FR-ORC-019
  - **Guard**: stages MUST NOT be reordered for convenience, and returning to an earlier stage MUST re-run affected downstream stages · **Done**: zero stale artifacts · **Approval**: none
- [x] T109 [US4] Release-readiness evaluator — `src/main/java/agentic/shortener/policy/ReleaseReadinessEvaluator.java`
  - **Req**: **FR-ORC-025** · **Scn**: DS-A · **ADR**: **ADR-011** · **Pre**: T102, T105, T107
  - **Deps**: T102, T105, T107 · **Par**: no · **Artifact**: all **nine** constitutional blocking conditions evaluated; report **names every unmet condition**
  - **TDD**: RED-FIRST · **Validate**: report is blocking when any condition holds and names it · **Docs**: plan §9 · **Trace**: matrix FR-ORC-025, KE-21
  - **Guard**: **readiness MUST NOT be self-certified by an agent** — the evaluator produces a recommendation requiring the release owner's recorded decision. Generated-but-unexecuted tests never count as passing · **Done**: nine conditions evaluated; recommendation-not-certification asserted · **Approval**: none
- [x] T110 [US4] Nine negative release-readiness tests — `src/test/java/agentic/shortener/policy/ReleaseBlockingConditionsIT.java`
  - **Req**: FR-ORC-025, **SC-009** · **Scn**: DS-A · **ADR**: ADR-011 · **Pre**: T109
  - **Deps**: T109 · **Par**: no (**synchronization point**) · **Artifact**: one negative test per blocking condition, each seeded individually
  - **TDD**: EVIDENCE · **Validate**: nine tests, each producing a blocking determination that **names its condition** · **Docs**: — · **Trace**: matrix FR-ORC-025, SC-009
  - **Guard**: this is the suite that proves a failed mandatory policy **prevents** release readiness rather than merely being reported · **Done**: nine tests pass · **Approval**: none
- [x] T110a [P] [US4] Missing-evidence blocking condition — `src/test/java/agentic/shortener/policy/MissingEvidenceBlockingTest.java`
  - **Req**: **FR-ORC-025**, FR-ORC-023, SC-009 · **Scn**: DS-A · **ADR**: ADR-010, ADR-011 · **Pre**: T109, T110
  - **Deps**: T109 · **Par**: yes · **Artifact**: an evidence artifact that is **missing or unreadable** at readiness evaluation produces a **blocking** determination naming constitutional condition 9, never a pass and never a warning
  - **TDD**: EVIDENCE · **Validate**: **EC-028** — seed a completed stage whose evidence artifact is deleted, then one whose artifact is present but unparseable; both block; both name condition 9 and the artifact · **Docs**: plan §9 · **Trace**: matrix FR-ORC-025, EC-028
  - **Guard**: **unverifiable evidence is a release-blocking condition, not an inconvenience.** T110's nine tests seed each condition; EC-028 is the input that makes condition 9 concrete, and it had no explicit assertion anywhere (analyze finding M9). An unreadable artifact must not be treated as absent-and-therefore-not-applicable — that is the `EC-025` default-deny family again · **Done**: both seeds block and name condition 9 · **Approval**: none

---

## Phase 7: User Story 5 — Assessment Reviewer Reconstructs an Execution (P3)

**Story goal**: evidence a reviewer can query independently, and measurements labelled honestly.

**Independent test criteria**: adversarial — hand the repository to a reader who did not run it and ask them to
answer the `quickstart.md` §5 reconstruction questions from artifacts alone.

**Blocking**: T096. **Synchronization point**: T130.

### Audit trail and signals

- [x] T111 [US5] Audit writer with six mandatory fields — `src/main/java/agentic/shortener/audit/AuditWriter.java`
  - **Req**: **FR-ORC-023**, NFR-AUD-001 · **Scn**: all · **ADR**: **ADR-010** · **Pre**: T027, T080
  - **Deps**: T027, T080 · **Par**: no (every stage writes through it) · **Artifact**: append-only writer requiring actor type, action, timestamp, affected artifact/state, result, **non-empty reason**
  - **TDD**: RED-FIRST · **Validate**: every record validated against `contracts/audit-event.schema.json`; a record missing any field rejected; `POL-AUD-001` passes · **Docs**: plan §7 · **Trace**: matrix FR-ORC-023, KE-19
  - **Guard**: **fail-closed applies to orchestrator governance writes only** (ADR-010 scoping) — no public shortener operation writes an audit row, so create, resolve, and analytics read are structurally incapable of audit-write failure. An audit write failure routes through the CL-006 envelope to suspension, not hard failure · **Done**: six fields enforced; corrections appended with `correctsEventId`, never edits · **Approval**: none
- [x] T112 [P] [US5] Correlation identifier propagation — `src/main/java/agentic/shortener/audit/CorrelationContext.java`
  - **Req**: **NFR-OBS-001** · **Scn**: all · **ADR**: ADR-010 · **Pre**: T111
  - **Deps**: T111 · **Par**: yes · **Artifact**: run id on every audit row, log line, metric label, trace span, and persisted row
  - **TDD**: RED-FIRST · **Validate**: identifier present on **100%** of records for a sampled run · **Docs**: plan §7 · **Trace**: matrix FR-ORC-023
  - **Guard**: application-plane requests carry their own request id, correlated where a run touches them — the two ids must not be conflated · **Done**: 100% presence asserted · **Approval**: none
- [x] T113 [P] [US5] Immutability enforcement test over the governance set — `src/test/java/agentic/shortener/audit/GovernanceImmutabilityIT.java`
  - **Req**: NFR-AUD-001 · **Scn**: — · **ADR**: **ADR-010** · **Pre**: T111, T028
  - **Deps**: T028, T111 · **Par**: yes · **Artifact**: UPDATE and DELETE rejected on all six governance tables
  - **TDD**: EVIDENCE · **Validate**: six tables × two operations = twelve rejections — **unchanged, deliberately**; plus an architecture assertion that **no deletion, purge or archival path exists anywhere** in the codebase (NFR-AUD-003, CR-017) · **Docs**: ADR-010 · **Trace**: NFR-AUD-001
  - **Guard**: `redirect_event` is **excluded from this set** — it is domain analytics, and a code-plus-timestamp event structurally cannot carry the six mandatory fields · **Done**: twelve rejections proven · **Approval**: none
- [x] T114 [P] [US5] Structured logs, metrics, and traces — `src/main/java/agentic/shortener/audit/telemetry/`
  - **Req**: NFR-OBS-001, NFR-OBS-002, **FR-URL-017**, SC-011 · **Scn**: all · **ADR**: ADR-010 · **Pre**: T112
  - **Deps**: T112 · **Par**: yes · **Artifact**: structured JSON logs, counters and timers, span per stage execution and per retry attempt — **explicitly not the audit trail, and rotatable**
  - **TDD**: RED-FIRST · **Validate**: T019's secret scan over **captured telemetry** returns zero findings · **Docs**: plan §7 · **Trace**: matrix FR-URL-017
  - **Guard**: operational signal may be lossy and rotated; governance evidence may not. Conflating them is what makes log-derived audit unachievable · **Done**: three signal types; scan clean · **Approval**: none

### MTTR instrumentation and reporting

- [x] T115 [US5] `failure_event` capture with nine fields — `src/main/java/agentic/shortener/audit/FailureEvent.java`, `V5__failure_event.sql`
  - **Req**: **FR-ORC-024**, plan §7 · **Scn**: DS-B · **ADR**: **ADR-010** · **Pre**: T111
  - **Deps**: T111 · **Par**: no (consumed by T116–T122) · **Artifact**: `failure_detected_at`, `recovery_started_at`, `recovery_completed_at`, `individual_recovery_duration`, `human_wait_duration`, `recovery_mechanism`, `recovered`, `run_id`, `stage_id`, `failure_category`
  - **TDD**: RED-FIRST · **Validate**: schema test asserting all nine present and non-null where applicable · **Docs**: plan §7 · **Trace**: matrix FR-ORC-024
  - **Guard**: **this table must exist before Slice 8 runs**, or the scenarios produce no MTTR population — a critical-path dependency recorded in T004 · **Done**: nine fields captured · **Approval**: none
- [x] T116 [P] [US5] Recovery-mechanism classification — `src/main/java/agentic/shortener/audit/RecoveryMechanism.java`
  - **Req**: FR-ORC-024 · **Scn**: DS-B · **ADR**: ADR-010 · **Pre**: T115
  - **Deps**: T115 · **Par**: yes · **Artifact**: `retry` | `rollback` | `compensation` | `resume` | `human` — **five values. `fallback` removed with FR-ORC-015 (Decision K, CR-032)**: an enum value that can never be emitted is a claim, not a classification
  - **TDD**: RED-FIRST · **Validate**: each mechanism produces its own classification in a scripted scenario; and a negative assertion that **no recovery event carries a `fallback` mechanism**, because a value no code path can produce should not be reachable from the API either · **Docs**: plan §7 · **Trace**: matrix FR-ORC-024
  - **Guard**: rollback and compensation must remain **distinguishable here too**, not merged into one bucket · **Done**: five mechanisms each exercised; `fallback` unreachable · **Approval**: none
- [x] T117 [P] [US5] Human-wait exclusion capture — `src/main/java/agentic/shortener/audit/HumanWaitTracker.java`
  - **Req**: plan §7 declared exclusion · **Scn**: DS-C · **ADR**: ADR-010 · **Pre**: T115, T091
  - **Deps**: T091, T115 · **Par**: yes · **Artifact**: time in `AWAITING_APPROVAL` and `SAFE_STOP` measured and stored **separately** from recovery duration
  - **TDD**: RED-FIRST · **Validate**: a recovery spanning a gate wait excludes the wait from `individual_recovery_duration` and records it in `human_wait_duration` · **Docs**: plan §7 · **Trace**: matrix FR-ORC-024
  - **Guard**: **including human wait would make MTTR a function of when a person was at a keyboard**, measuring reviewer latency rather than system recovery. The exclusion is **declared**, never silent · **Done**: separation proven on a gate-spanning recovery · **Approval**: none
- [x] T118 [US5] MTTR calculation — `src/main/java/agentic/shortener/audit/MttrCalculator.java`
  - **Req**: FR-ORC-024, NFR-REC-002, plan §7 · **Scn**: DS-B · **ADR**: ADR-010 · **Pre**: T116, T117
  - **Deps**: T116, T117 · **Par**: no · **Artifact**: `MTTR = Σ(recovery_completed_at − failure_detected_at) over RECOVERED events ÷ count(RECOVERED events)`, with human wait excluded from each duration
  - **TDD**: RED-FIRST · **Validate**: calculation test over a seeded population with a hand-computed expected value · **Docs**: plan §7 · **Trace**: matrix FR-ORC-024
  - **Guard**: the formula is mandated; this task implements it exactly rather than an approximation · **Done**: hand-computed value matches · **Approval**: none
- [x] T119 [P] [US5] Unrecovered failures excluded from the denominator — `src/test/java/agentic/shortener/audit/MttrDenominatorTest.java`
  - **Req**: plan §7 · **Scn**: DS-B · **ADR**: — · **Pre**: T118
  - **Deps**: T118 · **Par**: yes · **Artifact**: `recovered = false` rows excluded from the denominator **and counted separately**
  - **TDD**: EVIDENCE · **Validate**: seeded population with unrecovered rows; denominator asserted; separate count reported · **Docs**: plan §7 · **Trace**: matrix FR-ORC-024
  - **Guard**: **folding unrecovered failures into MTTR would flatter the number** — the mandated method forbids it and the report must show the separate count · **Done**: exclusion and separate count both asserted · **Approval**: none
- [x] T120 [P] [US5] Measurement population and exclusion declaration — `docs/evidence/mttr-method.md`
  - **Req**: plan §7, Constitution IX · **Scn**: DS-A, DS-B, DS-C · **ADR**: ADR-010 · **Pre**: T118
  - **Deps**: T118 · **Par**: yes · **Artifact**: declared population (DS-A/B/C plus the reliability suite), declared exclusions (human wait), and declared limitations
  - **TDD**: N/A-DOC · **Validate**: document states population, exclusions, and limitations before any figure is quoted · **Docs**: new file · **Trace**: NFR-AUT-003
  - **Guard**: every figure must state **whether it was measured and under what conditions**; a bare number is not reportable · **Done**: population, exclusions, limitations all stated · **Approval**: none
- [x] T121 [P] [US5] Demonstration-data labelling — `src/main/java/agentic/shortener/audit/MeasurementLabel.java`
  - **Req**: **NFR-AUT-003**, Constitution IX · **Scn**: all · **ADR**: ADR-010 · **Pre**: T118
  - **Deps**: T118 · **Par**: yes · **Artifact**: every emitted figure carries `MEASURED` (with conditions) or `PROPOSED`; demonstration measurements labelled as such
  - **TDD**: RED-FIRST · **Validate**: **zero unlabelled figures** across the demonstration corpus (SC-012) · **Docs**: plan §7 · **Trace**: matrix FR-ORC-024, KE-20, SC-012
  - **Guard**: **a demonstration measurement presented as a production statistic is a release-blocking condition** (condition 9). Single developer machine, injected faults, small populations, possibly compressed time parameters — all disclosed · **Done**: zero unlabelled figures · **Approval**: none
- [x] T122 [P] [US5] Reliability and orchestration measurement exposure — `src/main/java/agentic/shortener/audit/RunMetrics.java`
  - **Req**: **FR-ORC-024** · **Scn**: DS-B · **ADR**: ADR-010 · **Pre**: T121
  - **Deps**: T121 · **Par**: yes · **Artifact**: workflow success rate, failure rate, retry frequency, rollback and compensation frequencies **counted separately**, end-to-end latency with suspended time reported separately, unrecovered failure count
  - **TDD**: RED-FIRST · **Validate**: retrievable per run and in aggregate; **PVT-001's measured value comes from T145b**, with the analytics-append component from **T145d** — this task exposes the measures, it does not produce the load · **Docs**: plan §7 · **Trace**: matrix FR-ORC-024
  - **Guard**: PVT-001 was approved with explicit knowledge that the append sits inside its budget — the separate reporting is how that trade stays visible rather than inferred · **Done**: six measures exposed; append component isolated · **Approval**: none

### Traceability, documentation, and reporting

- [x] T123 [US5] Traceability report generator — `src/main/java/agentic/shortener/audit/TraceabilityReporter.java`
  - **Req**: **FR-ORC-027**, `POL-TRC-001` · **Scn**: all · **ADR**: **ADR-006** · **Pre**: T111
  - **Deps**: T111 · **Par**: no · **Artifact**: report over the ten-link chain — requirement → scenario → design → ADR → task → code → test → validation → documentation → evidence
  - **TDD**: RED-FIRST · **Validate**: report generated **mechanically**, not by hand · **Docs**: plan §13 · **Trace**: matrix FR-ORC-027, KE-22
  - **Guard**: the chain now has ten populated columns after CR-006 added Design and ADR — a requirement with no Design or ADR reference is an orphan in the same sense as one with no test · **Done**: report covers all ten links · **Approval**: none
- [x] T124 [P] [US5] Zero-orphan bidirectional assertion — `src/test/java/agentic/shortener/audit/ZeroOrphanTest.java`
  - **Req**: FR-ORC-027, **SC-010** · **Scn**: all · **ADR**: — · **Pre**: T123
  - **Deps**: T123 · **Par**: yes · **Artifact**: zero orphan requirements, tasks, implementations, and tests — in **both** directions
  - **TDD**: EVIDENCE · **Validate**: every delivered requirement reaches an executed test and evidence; every test traces back to a requirement · **Docs**: — · **Trace**: matrix, SC-010
  - **Guard**: **orphan detection must be a mechanism, not a prohibition.** This is that mechanism · **Done**: four orphan classes each asserted zero · **Approval**: none
- [x] T125 [P] [US5] Traceability gap blocks release readiness — `src/test/java/agentic/shortener/policy/TraceabilityBlockingTest.java`
  - **Req**: `POL-TRC-001`, Constitution §Governance condition 6 · **Scn**: — · **ADR**: — · **Pre**: T124, T109
  - **Deps**: T109, T124 · **Par**: yes · **Artifact**: a seeded orphan makes readiness blocking
  - **TDD**: EVIDENCE · **Validate**: orphan seeded → readiness blocks and names condition 6 · **Docs**: — · **Trace**: `POL-TRC-001`
  - **Guard**: reporting an orphan without blocking would satisfy neither the policy nor the constitution · **Done**: blocking proven · **Approval**: none
- [x] T126 [P] [US5] Documentation updated with delivered behaviour — `README.md`, `docs/`
  - **Req**: Constitution X, NFR-SEC-003 · **Scn**: all · **ADR**: — · **Pre**: T057, T110
  - **Deps**: T057, T110 · **Par**: yes · **Artifact**: docs describing behaviour actually implemented; threat model including the **operator-script trust boundary** and the **noisy-neighbour throttling trade-off**
  - **TDD**: N/A-DOC · **Validate**: no documented behaviour absent from the system; none present but undocumented · **Docs**: this task · **Trace**: NFR-SEC-003
  - **Guard**: **documentation is updated in the same change as the behaviour it describes**, never retrofitted · **Done**: both named trade-offs documented; drift check clean · **Approval**: none
- [x] T127 [P] [US5] Quickstart verified end to end on a clean machine — `specs/001-agentic-sdlc-url-shortener/quickstart.md`
  - **Req**: plan §14 · **Scn**: all · **ADR**: **ADR-012** · **Pre**: T110
  - **Deps**: T110 · **Par**: yes · **Artifact**: every command in the guide executed; both restart demonstrations performed
  - **TDD**: EVIDENCE · **Validate**: the **fast and integration tiers** run with no AI key and no network; the orchestration run requires an authenticated CLI, and the guide says so before any run instruction · **Docs**: `quickstart.md` corrections applied · **Trace**: FR-ORC-030
  - **Guard**: if a reviewer cannot run it, they cannot verify it — setup burden is a governance concern, not a convenience · **Done**: guide executes clean; corrections committed · **Approval**: none
- [x] T128 [P] [US5] Reviewer navigation guide — `docs/REVIEWER-GUIDE.md`
  - **Req**: US-5, SC-004 · **Scn**: all · **ADR**: — · **Pre**: T124, T127
  - **Deps**: T124, T127 · **Par**: yes · **Artifact**: where each artifact lives, which gate approved what, how to answer each reconstruction question, **what the baseline deliberately omitted, which run closed it, and where that run's committed evidence is**, and **what ambiguity detection is** — semantic, AI-backed, with declared variability and a recorded reason when nothing is found
  - **TDD**: N/A-DOC · **Validate**: a reader who did not run the project answers the `quickstart.md` §5 questions using only this guide · **Docs**: new file · **Trace**: SC-004
  - **Guard**: **carries the capability framing forward** — reviewers may submit any requirement; **every run is AI-backed** and a fresh run needs an authenticated CLI, while reviewing needs nothing; committed DS evidence carries per-node executor-kind labels and a pinned model id (owner instruction, carried from `gate-04-adr.md`) · **Done**: adversarial reader succeeds · **Approval**: none
- [x] T129 [US5] Final engineering summary assembler — `src/main/java/agentic/shortener/orchestration/summary/SummaryAssembler.java`, `docs/ENGINEERING-SUMMARY.md`
  - **Req**: **FR-ORC-026** · **Scn**: all · **ADR**: **ADR-010** · **Pre**: T121, T124, T126
  - **Deps**: T121, T124, T126 · **Par**: no · **Artifact**: deterministic assembly **from recorded evidence only** — what was built, decisions and rejected alternatives, executed validation and results, residual risks and limitations, and the AI-assisted process **including deviations from plan**
  - **TDD**: RED-FIRST · **Validate**: every claim in the summary traces to a recorded artifact; a claim without a trace fails the build · **Docs**: new file · **Trace**: matrix FR-ORC-026, CN-001
  - **Guard**: **creativity is a liability here** — the assembler is deterministic precisely so it cannot invent. No unverifiable claims, no fabricated metrics, no undisclosed limitations · **Done**: zero untraceable claims · **Approval**: none
- [ ] T130 [US5] US5 adversarial reconstruction — `docs/evidence/reconstruction-report.md`
  - **Req**: **FR-ORC-023**, SC-004 · **Scn**: DS-A, DS-B, DS-C · **ADR**: ADR-010 · **Pre**: T111–T129
  - **Deps**: T111–T129 · **Par**: no (**synchronization point**) · **Artifact**: a reader with repository access and **no session access** reconstructs all three runs
  - **TDD**: EVIDENCE · **Validate**: the `quickstart.md` §5 questions answered from artifacts alone, including a reconstruction performed against **the oldest run in the corpus** — proving NFR-AUD-002's guarantee holds without qualification now that nothing is ever purged (NFR-AUD-003); retry, compensation, and safe-stop distinguishable from each other and from ordinary success · **Docs**: new file · **Trace**: SC-004
  - **Guard**: **this is the acceptance test for the whole submission.** If reconstruction fails, every other passing item's evidence is unverifiable · **Done**: five questions answered; distinguishability confirmed · **Approval**: none

---

## Phase 8: Demonstration Scenarios

**Goal**: three recorded, reproducible runs plus one out-of-scenario run proving executors are generic.

**Blocking**: T115 (`failure_event` must exist or there is no MTTR population) and T110.
**Synchronization point**: T142.

- [x] T131 [US3] DS-A greenfield run design — `docs/evidence/ds-a/design.md`
  - **Req**: **DS-A**, FR-ORC-010 · **Scn**: **DS-A** · **ADR**: ADR-004 · **Pre**: T110, T115
  - **Deps**: T110, T115 · **Par**: no · **Artifact**: input *"Expose the remaining time-to-expiry for a short link to its owning creator"* — complete, consistent, testable, within policy and architecture bounds
  - **TDD**: N/A-DOC · **Validate**: input verified against the four well-formedness criteria before the run · **Docs**: new file · **Trace**: DS-A
  - **Guard**: the input must be **genuinely** well-formed; choosing a trivially easy one to guarantee a clean run would be a rigged demonstration · **Done**: four criteria evidenced · **Approval**: none
- [x] T131a [US3] Run-orchestration driver (the conductor) — `src/main/java/agentic/shortener/orchestration/conductor/Conductor.java`, `FanOutPlanner.java`
  - **Req**: FR-ORC-001, FR-ORC-004, FR-ORC-006, FR-ORC-013, FR-ORC-017 · **Scn**: all · **ADR**: — (composes ADR-003/ADR-004/ADR-008/ADR-010 machinery; introduces no new architecture decision of its own) · **Pre**: T110, T115
  - **Deps**: T071, T078, T084–T092, T096, T058–T063 · **Par**: no · **Artifact**: the missing sequencing loop — readiness computation over `StageTemplate`/`JdbcRunStore` (previously zero production callers of `StageCriteria.mayEnter`), concurrent dispatch of every ready node per round, S4's conditional clarification and S6/S11's approval-required handling, `FanOutPlanner` as S7's child-count extension point — composing `RetryPolicy`, `GateStore`/`GateRequestPresenter`/`GateOutcomeHandler`, `SafeStopHandler`, `ArtifactWriteGuard`, `AuditWriter`, `StageTelemetry` unmodified
  - **TDD**: RED-FIRST · **Validate**: a genuine 2-party concurrency barrier proves S9/S10 and a real 2-child S7 fan-out dispatch concurrently, not merely declared parallel; a material-ambiguity S4 gate stops the run (downstream stays `BLOCKED`, a repeated `advance()` with nothing decided changes nothing) and a recorded decision resumes it; the run reaches `COMPLETED` with every node in a join-satisfying terminal state · **Docs**: CR-044 (task-plan gap, added on owner authority) · **Trace**: FR-ORC-001, FR-ORC-004, FR-ORC-006, FR-ORC-013, FR-ORC-017
  - **Guard**: **compose what Phases 1–7 already built; invent none of their logic** (owner instruction) — every collaborator below the readiness computation itself is an unmodified call into an already-tested class · **Done**: `ConductorIT`'s two tests green; full suite green (729 fast, 367 integration) · **Approval**: none (owner pre-approved building it; CR-044 records the task-plan addition)
- [x] T131b [US3] Gate decisions resume the run — `src/main/java/agentic/shortener/orchestration/api/GateDecisionController.java`
  - **Req**: FR-ORC-013 · **Scn**: all · **ADR**: — · **Pre**: T131a, T067a
  - **Deps**: T131a, T067a · **Par**: no (modifies a committed class) · **Artifact**: `GateOutcomeHandler.apply` only ever changed the gated node's (and, on `REJECTED`, the run's) own state — nothing previously drove the run any further once a decision landed. `GateDecisionController` now calls `Conductor#advance` unconditionally after every applied decision; `Conductor#dispatchOne` now wraps its own dispatch so an unanticipated `RuntimeException` (a run whose earlier artifacts were never produced by any `Conductor` instance — a hand-built fixture, or the disclosed crash-restart gap) suspends the run through `SafeStopHandler` instead of propagating to an HTTP `500`
  - **TDD**: RED-FIRST · **Validate**: `GateDecisionControllerIT`'s own already-committed 11 tests, re-run, caught the missing defensive catch immediately (a real `500` on `CHANGES_REQUESTED`) before the fix; green after · **Docs**: CR-045 (modifies a committed class, per the owner's own instruction to note it) · **Trace**: FR-ORC-013
  - **Guard**: a decision applied but never resumed is indistinguishable from a decision lost — this is what makes the whole gate mechanism actually advance a run rather than only recording that someone answered · **Done**: `GateDecisionControllerIT` green (11/11); genuine red-phase capture in `docs/evidence/red-phase/` · **Approval**: none
- [x] T131c [US3] Real `BranchApplier` and `TestSuiteRunner` — `src/main/java/agentic/shortener/orchestration/conductor/GitWorktreeBranchApplier.java`, `ScriptTestSuiteRunner.java`, `ProcessRunner.java`
  - **Req**: FR-ORC-014, FR-ORC-029 · **Scn**: all · **ADR**: ADR-004 · **Pre**: T131a
  - **Deps**: T131a · **Par**: yes · **Artifact**: the real S7/S8 production plumbing T073e's own live-demo class explicitly left unbuilt ("that production implementation is out of this task's scope") — `git worktree`-isolated patch apply + buildability check (never the process's own working tree), and a real fast-tier test run against the resulting branch, real `Surefire`-report parsing reused from T129's own regex
  - **TDD**: RED-FIRST · **Validate**: a clean, buildable patch creates a real branch with a real commit; a patch that does not apply leaves no branch behind; a patch that applies but fails the (injected) build check keeps the branch (a reviewer may want to see exactly what failed); real Surefire report files sum correctly; no report directory at all is reported honestly, never as an invented pass or a fabricated failure count · **Docs**: CR-044/CR-045 · **Trace**: FR-ORC-014, FR-ORC-029
  - **Guard**: **never operates on this process's own working tree** — every apply, commit, and build/test check happens in an isolated `git worktree`, torn down afterward, so a live demonstration run can never corrupt or lose whatever this session itself has uncommitted · **Done**: 5 tests green (`GitWorktreeBranchApplierTest`, `ScriptTestSuiteRunnerTest`); genuine red-phase capture · **Approval**: none
- [x] T131d [US3] Gemini adapter 429/quota misclassification fixed — `src/main/java/agentic/shortener/orchestration/executor/ai/GeminiCliStageAiProvider.java`, `src/main/java/agentic/shortener/orchestration/reliability/ProviderRateLimitedException.java`, `ProviderFailureTranslator.java`
  - **Req**: FR-ORC-014 · **Scn**: all · **ADR**: ADR-004 (Amendment 03) · **Pre**: T131c
  - **Deps**: T131c · **Par**: yes · **Artifact**: found live during T132's own first DS-A attempt — `agy` can answer `status=ERROR` carrying a fully-formed, substantive response ALONGSIDE an `"error"` field naming a genuine Gemini API quota exhaustion (`RESOURCE_EXHAUSTED`, HTTP 429); the adapter previously folded every non-`SUCCESS` status into `MalformedProviderOutputException` → `FailureCategory.INTERNAL` (permanent), discarding the distinction. `ProviderRateLimitedException` is a new, generic (non-vendor-named) type in `orchestration.reliability`, matching `MalformedProviderOutputException`'s own precedent; the adapter recognizes agy's specific `error` wording (provider-level parsing, already its job) and throws the new type, and `ProviderFailureTranslator` — the one class `FailureEnvelopeTest` permits to hold both `FailureCategory` and a provider exception type — maps it to `RATE_LIMITED`
  - **TDD**: RED-FIRST · **Validate**: a real captured response shape (`RESOURCE_EXHAUSTED (code 429)`) is classified `RATE_LIMITED` and proposed retryable; an `ERROR` status with no error field, or one naming something else, still falls through to the existing `MalformedProviderOutputException` path unchanged (negative test) · **Docs**: CR-046 (modifies two committed classes) · **Trace**: FR-ORC-014
  - **Guard**: **the provider decides which exception TYPE to throw; only the translator decides the CATEGORY** — T083's own architecture, unweakened. Did not silently retry-classify every non-SUCCESS status as retryable; only the specific, verified 429/quota signal · **Done**: `GeminiCliStageAiProviderTest` (9/9), `FailureEnvelopeTest` (11/11); genuine red-phase capture · **Approval**: none
- [ ] T132 [US3] DS-A executed run — no artificial clarification gate — `docs/evidence/ds-a/run.json`
  - **Req**: **DS-A**, FR-ORC-010, FR-ORC-012 · **Scn**: **DS-A** · **ADR**: ADR-004 · **Pre**: T131, T131a, T131b, T131c, T131d
  - **Deps**: T131, T131a, T131b, T131c, T131d · **Par**: no · **Artifact**: path S1→S2→S3→**S4 `SKIPPED`**→S5→S6→S7 fan-out→S8→S9∥S10→S11→S12, terminal `COMPLETED`
  - **TDD**: EVIDENCE · **Validate**: stage 4 `SKIPPED`; **requirement-quality checks recorded**; **`no_clarification_reason` populated**; decomposition, API/schema impacts, and acceptance criteria all present · **Docs**: — · **Trace**: DS-A row in the matrix
  - **Guard**: **a clarification gate MUST NOT fire merely to demonstrate that gates exist.** This is the task that proves governance is not theatre — a well-formed requirement passes through without an artificial stop, while the quality checks that justified skipping are on the record · **Done**: skip recorded with its justification; run `COMPLETED` · **Approval**: none
  - **Progress (2026-09-22)**: twelve live attempts across four subjects (full arc:
    `docs/evidence/ds-a/requirement-completeness-ceiling-finding.md`). The twelfth — `GET /v1/version`
    (CR-054), routine S4 findings resolved under the owner's standing delegation
    (`docs/governance/delegations/routine-clarification-delegation.md`) — reached **S6, `AWAITING_APPROVAL`**,
    the furthest any live attempt has gone: S4 did not `SKIP` (two real, routine findings genuinely fired and
    were resolved through the governed clarification path, not an artificial gate), S5 ran for real
    (decomposition), S6 opened its own real architecture gate. `docs/evidence/ds-a/
    run-snapshot-ATTEMPT-12-RESUMED-TO-S6-new-feature-under-delegation.md`,
    `docs/evidence/ds-a/pending-gate-s6-context-new-feature.md`. **Not yet `COMPLETED`** — this task's own
    literal Artifact (`S4 SKIPPED`, terminal `COMPLETED`) is not what this run produced, and is not claimed
    here; the owner's own S6 and S11 decisions are still needed before `docs/evidence/ds-a/run.json` can be
    written as a true terminal record. Left unchecked honestly rather than marked done against a different
    outcome than its own Artifact field states.
- [ ] T133 [P] [US3] DS-A late-ambiguity escalation — `docs/evidence/ds-a/late-ambiguity.json`
  - **Req**: **DS-A**, FR-ORC-011, EC-021 · **Scn**: **DS-A** · **ADR**: ADR-009 · **Pre**: T132, T096
  - **Deps**: T096, T132 · **Par**: yes · **Artifact**: material ambiguity emerging mid-run suspends **only the affected path**; governed clarification and impact analysis follow; resumption from the correct state after explicit approval
  - **TDD**: EVIDENCE · **Validate**: unaffected paths asserted to continue; affected path asserted suspended; resumption point asserted correct · **Docs**: — · **Trace**: DS-A, EC-021
  - **Guard**: this is the half of DS-A that keeps the no-gate-fired result honest — the system is not simply incapable of detecting ambiguity, it detects it and escalates selectively · **Done**: selective suspension and correct resumption proven · **Approval**: none
- [ ] T134 [P] [US3] DS-A evidence bundle — `docs/evidence/ds-a/bundle/`
  - **Req**: DS-A, SC-010 · **Scn**: DS-A · **ADR**: — · **Pre**: T132, T133
  - **Deps**: T132, T133 · **Par**: yes · **Artifact**: graph export, per-stage criteria evaluation, quality checks, decomposition, executed test results, traceability matrix, **per-stage executor-mode labels**
  - **TDD**: EVIDENCE · **Validate**: bundle complete against DS-A's stated evidence list · **Docs**: — · **Trace**: DS-A
  - **Guard**: every figure in the bundle labelled measured or proposed · **Done**: seven evidence items present · **Approval**: none
- [x] T135 [US3] DS-B brownfield impact analysis and ordering proof — `docs/evidence/ds-b/impact-analysis.md`
  - **Req**: **FR-ORC-020**, **DS-B** · **Scn**: **DS-B** · **ADR**: ADR-006 · **Pre**: T055, T107, T110
  - **Deps**: T055, T107, T110 · **Par**: no · **Artifact**: subject *"A creator's redirect traffic must be limited in aggregate across all their links, not only per link"* — FR-URL-016's third tier (PVT-014); all seven dimensions, carrying the **six substantive points** plan §11 enumerates (hot-path ownership lookup, latency against PVT-001, limiter failure posture, non-disclosure of the creator, per-code regression surface, threat-model update); **timestamp preceding the first code modification**
  - **TDD**: EVIDENCE · **Validate**: ordering asserted by timestamp comparison against the first commit touching `delivery/ratelimit/` · **Docs**: new file · **Trace**: DS-B, matrix FR-ORC-020
  - **Guard**: the analysis MUST NOT be reconstructed after the change; the ordering proof is what makes that claim checkable · **Done**: seven dimensions; ordering proven · **Approval**: none
  - **Done (2026-09-22)**: `docs/evidence/ds-b/impact-analysis.md` — all seven dimensions (components,
    interfaces, data flows, tests, docs, regression risks, rollout/rollback), the six substantive points
    each explicitly located in a summary table. Real findings, not template filler: `ShortLink` already
    carries `creatorId`, so no new DB round-trip is structurally required, but the aggregate check must move
    to a NEW post-lookup checkpoint since the per-code tier's own pre-lookup ordering can't supply a creator
    id that isn't known until after the lookup; the limiter failure-posture question is named and explicitly
    left open for T136a's own security gate, not resolved here; the exact `RateLimiterTest`/`RateLimitIT`
    assertions expected to flip once the tier is real are named (`theAggregateTierIsNotPartiallyPresent`,
    `throttlingIsOnByDefault`, `aggregateTrafficPassesUnthrottled`). Ordering proof: no
    `AggregateRedirectLimiter.java` exists yet, and `git log -- .../delivery/ratelimit/` shows no commit
    after this analysis's own commit touches that directory, independently checkable by any reviewer.
    Analysis only — no code changes; T136a's own real code is a separate, later, owner-gated turn.
- [ ] T136 [US3] DS-B executed run with injected retry and compensation — `docs/evidence/ds-b/run.json`
  - **Req**: DS-B, FR-ORC-014, FR-ORC-016 · **Scn**: **DS-B** · **ADR**: ADR-003 · **Pre**: T055, T135, T085, T090
  - **Deps**: T055, T085, T090, T135 · **Par**: no · **Artifact**: transient `UNAVAILABLE` → two-vote retry → success; one irreversible effect → compensation, **labelled as compensation** — both inside the **aggregate-tier** run
  - **TDD**: EVIDENCE · **Validate**: two-signature retry records present; compensation distinguishable from rollback; the **per-code tier proven still working** by pre-existing tests, not inspection; the before-state is the multi-link case passing **unthrottled** · **Docs**: — · **Trace**: DS-B
  - **Guard**: if the checkpoint at End-Day-2-PM fires, this reduces to a unit-level compensation proof **but the scenario is kept** (plan §14) · **Done**: retry and compensation both evidenced; regression caught by existing tests · **Approval**: none
- [ ] T136a [US3] Brownfield governed implementation of the per-creator aggregate redirect tier — `src/main/java/agentic/shortener/delivery/ratelimit/AggregateRedirectLimiter.java`, `docs/evidence/ds-b/aggregate-tier-run.json`
  - **Req**: **FR-URL-016**, **PVT-014**, FR-URL-018, NFR-PERF-001, FR-ORC-020 · **Scn**: **DS-B** · **ADR**: **ADR-013**, ADR-006 · **Pre**: T055, T135, T136, T107
  - **Deps**: T055, T107, T135, T136 · **Par**: no (the scenario's subject change) · **Artifact**: the third rate-limit tier the baseline deferred, **authored inside the brownfield run** — redirect traffic counted **per creator aggregated across all their links** at **PVT-014 (3,000 requests/minute)**, enforced **independently** of the per-code tier; code → owning-creator resolution added to the redirect path; a declared **limiter failure posture** for an unavailable counter store
  - **TDD**: RED-FIRST · **Validate**: **before-state** — FR-URL-016's own multi-link case (traffic across several links, each **under** PVT-013) passes **unthrottled**, captured as evidence **before** the impact analysis; **after-state** — the same traffic throttled, the response **naming the aggregate tier**; **no ownership disclosure** — the throttled response is asserted byte-identical for a public follower regardless of which creator owns the link; **per-code and creation tiers unregressed**; **latency re-measured** against PVT-001 with the ownership lookup in the hot path; the declared failure posture exercised with the counter store down
  - **Docs**: plan §8 rate limiting, `docs/evidence/ds-b/`, threat model T-08 · **Trace**: matrix FR-URL-016, PVT-014, NFR-PERF-001, DS-B
  - **Guard**: **the redirect path is public and anonymous by requirement** (FR-URL-018), so counting per creator means a code → creator lookup **inside the hot path** — the design decision this whole scenario exists to expose, and it must be measured against PVT-001 rather than assumed cheap. Two negative criteria come from the requirement itself and are not optional: the throttled response **must not disclose the owning creator** to a public follower, and **PVT-014 sits deliberately below the sum of per-code limits**, which is what makes the tier bite at all. The accepted trade-off — followers of a popular creator may be throttled through no fault of their own — is documented in the threat model, not discovered by a reviewer · **Done**: before-state, after-state, non-disclosure, unregressed tiers, re-measured latency and failure posture all evidenced; **FR-URL-016’s matrix reference upgraded from *partial* to complete** — this run is what closes it · **Approval**: **REQUIRED — human owner** (**security-sensitive** change gate — an abuse control with a disclosure criterion; class settled under Decision E)
  - **Security gate APPROVED (2026-09-22)**: `docs/governance/gate-decisions/ds-b/t136a-security-gate-approved.md`
    — all four items (scope, in-process failure posture, the accepted trade-off, the non-disclosure bar).
    **The build itself has not started** — `AggregateRedirectLimiter` does not exist yet; this is a separate,
    later turn. Task left unchecked honestly.
- [ ] T137 [P] [US3] DS-B before/after test results — `docs/evidence/ds-b/test-results/`
  - **Req**: DS-B, NFR-TST-002 · **Scn**: DS-B · **ADR**: ADR-011 · **Pre**: T136
  - **Deps**: T136 · **Par**: yes · **Artifact**: throttling outcomes for the multi-link case before and after, plus the **unregressed per-code tier** and the **re-measured redirect latency** against PVT-001
  - **TDD**: EVIDENCE · **Validate**: before-state shows the multi-link traffic passing **unthrottled**; after-state shows it **throttled with the aggregate tier named and the creator not named** · **Docs**: — · **Trace**: DS-B
  - **Guard**: a green after-state alone proves nothing; the before-state is what makes it evidence · **Done**: both states captured · **Approval**: none
- [x] T138 [US3] DS-C ambiguity detection and silence path — `docs/evidence/ds-c/silence.json`
  - **Req**: **DS-C**, FR-ORC-010, SC-005 · **Scn**: **DS-C** · **ADR**: ADR-008 · **Pre**: T061, T110
  - **Deps**: T061, T110 · **Par**: no · **Artifact**: input *"Links should expire after a week but remain available for historical analytics indefinitely, and expired links should still redirect for trusted partners"*; conflict flagged with the conflicting elements **named**; **silence path demonstrated first** → `SAFE_STOP` with deadlines disclosed in the original ask
  - **TDD**: EVIDENCE · **Validate**: ambiguity detected **before** implementation; unsafe implementation prevented; suspension recorded · **Docs**: new file · **Trace**: DS-C, SC-005
  - **Guard**: **the orchestrator MUST NOT resolve the ambiguity by inference.** Demonstrating silence *before* the answer is what proves the gate is real · **Done**: conflict named; silence produces suspension · **Approval**: none
  - **Live evidence (2026-09-22)**: `DsCLiveRun` (T138), driven through the real, unmodified `Conductor` against
    the real Claude CLI adapter — `docs/evidence/ds-c/silence.json`,
    `docs/evidence/ds-c/pending-gate-s4-context.md`. Input used is spec.md's own canonical §DS-C
    demonstration text, verbatim (the same three-sentence form CR-049's and CR-052's own live compensating
    checks already used, semantically identical to this task's own quoted Artifact string, wording not
    forced to match token-for-token) — a real `SEMANTIC_CONTRADICTION` classified `MATERIAL_PENDING` fired
    (5 `MATERIAL_PENDING` findings total, 2 correctly-reasoned `NOT_MATERIAL`), `Conductor` opened S4's real
    gate, S5–S12 stayed `BLOCKED` (proven structurally: the live driver's own executor map supports only
    S1–S3, so reaching any later stage would itself have failed the test), run `RUNNING`, not terminal. The
    silence→`SAFE_STOP`-on-timeout mechanism itself (the wait-deadline elapsing with no decision) is proven
    separately, deterministically, by `SilenceTest`/`SilenceTestFalsifiabilityTest`/`SafeStopAndRetentionIT`
    — this live run demonstrates the three things its own **Validate** line actually names (detection before
    implementation, unsafe implementation prevented, suspension recorded), not a real-time wait-deadline
    expiry. T139 (clarification/replan) and T140 (rejection variant) remain open, pending the owner's real
    clarification decision on the findings this run surfaced.
- [x] T139 [US3] DS-C clarification, replan, and resumption — `docs/evidence/ds-c/replan.json`
  - **Req**: DS-C, FR-ORC-011, FR-ORC-019 · **Scn**: **DS-C** · **ADR**: **ADR-009** · **Pre**: T138, T096
  - **Deps**: T096, T138 · **Par**: no · **Artifact**: clarification decision recorded with human, question, answer, time; **replan event naming invalidated stages and voided approvals**; resumption to a deterministic terminal outcome
  - **TDD**: EVIDENCE · **Validate**: affected downstream artifacts re-planned; **unaffected paths untouched**; voided approvals proven **not carried forward** (EC-020) · **Docs**: — · **Trace**: DS-C, EC-020
  - **Guard**: over-invalidation is acceptable and expected under ADR-009's closure approach; a **surviving stale artifact is not** · **Done**: replan event complete; EC-020 proven · **Approval**: none
  - **Live evidence (2026-09-22)**: `DsCClarificationRun.driveDsCThroughClarificationAndReplan` — real owner
    clarification (5 ratified answers) recorded via `LineageStore` (`RequirementRecord`/`AmbiguityRecord`/
    `ClarificationDecision` for each real live finding), then a real `GateDecision` (`APPROVED`, human actor)
    applied through `GateOutcomeHandler`, resumed via `Conductor.advance`. Run proceeded S4→`SUCCEEDED`,
    S5→`SUCCEEDED` (real decomposition), S6→`AWAITING_APPROVAL` (its own real architecture gate) — a
    genuine, deterministic further stopping point, not a defect. **A disclosed, real scope decision**:
    `ReplanService`'s own downstream-invalidation walk (T096) was NOT invoked here — it invalidates a
    node's entire downstream closure unconditionally, without distinguishing already-executed work from
    work that never started, and `INVALIDATED` does not satisfy a join; `ReplanServiceTest` itself never
    demonstrates real resumption after invalidation (`appendReplannedEdges` is only exercised there to
    prove cycle rejection). Since T138 already proved nothing downstream of S4 had executed, invoking it
    would have invalidated never-attempted work and stranded the run with no proven re-dispatch path.
    Downstream-impact analysis was instead done for real (S5–S12 checked `BLOCKED`, proven per-node) and
    disclosed as empty in `docs/evidence/ds-c/replan.json`'s own `downstreamImpactAnalysis` field — spec.md's
    own "identify downstream impact" is satisfied by that proof, not by forcing an unrelated mechanism. Two
    live attempts were needed; the first surfaced three different, genuinely new findings and correctly
    stopped before recording any decision rather than forcing a match — see
    `docs/evidence/ds-c/pending-gate-s4-context.md`'s own T139 section. `RunState` is `RUNNING` (paused at
    S6), not yet terminal — reaching a truly terminal outcome needs the owner's further S6/S11 decisions,
    same as DS-A's own T132 chain.
  - **Scenario accepted as demonstrated (2026-09-22)**: `docs/governance/gate-decisions/ds-c/
    scenario-accepted-as-demonstrated.md`. The owner's own decision: DS-C's orchestration mechanism
    (detect → clarify → replan → resume, plus T140's rejection variant) is proven; the trusted-partner
    feature itself is **deliberately not built** — the run stays intentionally suspended at S6, which is
    the accepted final state, not an open item. The genuine late-ambiguity replan demonstration (invalidating
    already-executed work) remains T133's own, separate, still-open task — not implicitly closed here.
- [x] T140 [P] [US3] DS-C rejection variant — `docs/evidence/ds-c/rejection.json`
  - **Req**: FR-ORC-013 · **Scn**: DS-C · **ADR**: — · **Pre**: T139
  - **Deps**: T139 · **Par**: yes · **Artifact**: the same gate answered `REJECTED` → run terminates deterministically, reason recorded, **no downstream artifact produced**
  - **TDD**: EVIDENCE · **Validate**: terminal `REJECTED`; downstream artifact count zero · **Docs**: — · **Trace**: DS-C
  - **Guard**: a governance demonstration that only ever shows approval is incomplete · **Done**: rejection path evidenced · **Approval**: none
  - **Live evidence (2026-09-22)**: `DsCClarificationRun.driveDsCToRejection` — a separate live run, the same
    real S4 gate answered `REJECTED`, reason text explicitly labelled "T140 REJECTION-PATH DEMONSTRATION,
    not a real rejection of the requirement" so it is never misread as an actual owner decision. Confirmed:
    `RunState` → `REJECTED` deterministically; S5 through S12 all remained `BLOCKED` (zero downstream
    artifacts, proven per-node). `docs/evidence/ds-c/rejection.json`.
- [ ] T141 [US3] Out-of-scenario run — generic-executor proof — `docs/evidence/out-of-scenario/run.json`
  - **Req**: **FR-ORC-028**, **SC-016**, CN-010 · **Scn**: none by design · **ADR**: **ADR-004** · **Pre**: T132, T110
  - **Deps**: T110, T132 · **Par**: no · **Artifact**: a requirement **outside DS-A/B/C**, submitted like any other run, completing the governed lifecycle with **no executor code changes**
  - **TDD**: EVIDENCE · **Validate**: run completes; zero executor changes in the diff; **and the stage-7 no-plan gate is exercised by injecting the empty-change-plan condition** (EC-040) — the design output is withheld so stage 7 is reached with nothing to apply, and the gate must suspend with its three options rather than no-op forward. **The evidence MUST label this as an injected demonstration** (AS-007 as extended by CR-033), in the same place it presents the suspension, exactly as T136 labels its injected transient fault
  - **Docs**: `docs/REVIEWER-GUIDE.md` · **Trace**: SC-016, EC-040 · **Guard**: **executors that recognised blessed demo inputs would be a rigged demonstration.** This run is the proof that they do not — and **the generic-executor proof never depended on the mode**: an executor that branched on recognising blessed inputs would be rigged whichever way it was invoked. **Why the gate is now injected rather than encountered** (CR-033): before Decision J a reviewer reached this gate by submitting their own requirement with the AI disabled, and that route no longer exists because stage 7 authors the change. Injection is the honest substitute, and it is **not** a weaker proof of the gate — **T065’s own tests remain the structural proof**, one per option plus the negative test that a labelled no-op cannot advance downstream. What is lost is that a reviewer no longer meets the gate by accident, and the guide says so rather than implying otherwise · **Done**: run completes; the no-plan gate exercised **and labelled as injected**; diff clean of executor changes · **Approval**: none
- [ ] T142 [US3] Scenario evidence consolidation with executor-kind labels — `docs/evidence/README.md`
  - **Req**: FR-ORC-029, SC-015 · **Scn**: all · **ADR**: ADR-004 · **Pre**: T134, T137, T140, T141
  - **Deps**: T134, T137, T140, T141 · **Par**: no (**synchronization point**) · **Artifact**: index of all four runs; **per-node executor-kind labels**; **pinned model id** for every `AI` execution
  - **TDD**: EVIDENCE · **Validate**: **zero unlabelled stage executions**; no deterministic execution presented as AI work (SC-015) · **Docs**: new file · **Trace**: SC-015
  - **Guard**: **every run is AI-backed** (ADR-004-A2); per-node executor-kind labels distinguish what actually ran within each, and the pinned model id accompanies every `AI` execution. Mislabelling any execution is an evidence-integrity violation · **Done**: four runs indexed; zero unlabelled executions · **Approval**: none

---

## Phase 9: Polish, Cross-Cutting, and Release

**Blocking**: T142. **Final synchronization point**: T152.

- [ ] T143 End-to-end suite across all seven categories — `src/test/java/agentic/shortener/e2e/`
  - **Req**: **NFR-TST-001**, `POL-TST-001` · **Scn**: all · **ADR**: ADR-011 · **Pre**: T142
  - **Deps**: T142 · **Par**: no · **Artifact**: unit, integration, API contract, orchestration transition, reliability, security, end-to-end — **each category non-empty and executed**
  - **TDD**: EVIDENCE · **Validate**: `./mvnw -q verify` with a per-category inventory report · **Docs**: — · **Trace**: `POL-TST-001`
  - **Guard**: a category present but empty fails `POL-TST-001`; generated-but-unexecuted tests never count · **Done**: seven categories, each with ≥1 executed test · **Approval**: none
- [ ] T144 [P] Red-phase evidence audit — `docs/evidence/red-phase/index.md`
  - **Req**: **NFR-TST-002** · **Scn**: — · **ADR**: ADR-011 · **Pre**: T143
  - **Deps**: T143 · **Par**: yes · **Artifact**: index of captured failing-run outputs, one per RED-FIRST task, each showing the failure was **for the expected reason**
  - **TDD**: EVIDENCE · **Validate**: every RED-FIRST task has a stored artifact; tasks where TDD was impractical carry a recorded reason **and** executed validation · **Docs**: new file · **Trace**: NFR-TST-002
  - **Guard**: **evidenced, not asserted.** A compile error in the captured output does not satisfy the red phase · **Done**: index complete; no RED-FIRST task without evidence · **Approval**: none
- [ ] T144a [P] Checklist `[Now]` re-evaluation written to the results register — `specs/001-agentic-sdlc-url-shortener/checklists/EVALUATION-RESULTS.md`
  - **Req**: **Constitution §Assessment Scope** (repository as source of truth), **Constitution XI.10**, `POL-TRC-001` · **Scn**: — · **ADR**: — · **Pre**: T143
  - **Deps**: T143 · **Par**: yes (own file) · **Artifact**: §Pass 2 of the register populated — **per-item** outcome (`PASS` / `FAIL` / `NOT-EVALUABLE`), the artifact and location inspected, and for every `FAIL` the finding and its disposition record, across all **231 `[Now]`** items
  - **TDD**: EVIDENCE · **Validate**: every one of the 231 `[Now]` items has an outcome row; a count check reconciles rows against items so a partial pass cannot read as complete; each `FAIL` names a disposition record that exists · **Docs**: the register · **Trace**: Constitution §Governance condition 9, `POL-TRC-001`
  - **Guard**: **this is what converts §1.3's disclosure into a result.** The register currently records ~224 `[Now]` determinations as **NOT MATERIALIZED** — reported in conversation, unrecoverable as evidence, and therefore not citable (analyze finding H10). Back-filling them from recollection is prohibited (Constitution X), so the only lawful closure is to **evaluate them again against the artifacts** and write the outcomes down. A `FAIL` found on re-evaluation is a finding to disposition, not a regression to hide · **Done**: 231 rows present and reconciled; no item without an outcome · **Approval**: none
- [ ] T144b [P] `[Pre-Release]` checklist evaluation — `specs/001-agentic-sdlc-url-shortener/checklists/EVALUATION-RESULTS.md`
  - **Req**: Constitution XI, `POL-TRC-001` · **Scn**: all · **ADR**: — · **Pre**: T144a, T145b, T145c, T145d, T146
  - **Deps**: T144a, T146 · **Par**: yes · **Artifact**: §Pass 3 populated — the **35 `[Pre-Release]`** items plus the one `[Post-Tasks]` item, evaluable only now that tests, measurements and runs exist
  - **TDD**: EVIDENCE · **Validate**: all 36 items have outcomes; **CHK055** (the scalability measurement) is closed by T145d's evidence; **CHK244** (measured-versus-proposed labelling) reconciled against T121's zero-unlabelled-figures assertion · **Docs**: the register · **Trace**: Constitution §Governance conditions 4 and 9
  - **Guard**: these items were deferred honestly at generation because nothing existed to evaluate them against. Deferring them **again** at release readiness would turn an honest deferral into an undisclosed gap — condition 8 · **Done**: 36 rows present; CHK055 and CHK244 explicitly closed · **Approval**: none
- [ ] T145 [P] Coverage measurement — `docs/evidence/coverage/`
  - **Req**: **NFR-TST-003**, PVT-008 · **Scn**: — · **ADR**: ADR-011 · **Pre**: T143
  - **Deps**: T143 · **Par**: yes · **Artifact**: branch coverage against **PVT-008 (≥ 85%)** over a **denominator declared before measurement**. **Included**: `domain`, `application`, `orchestration/state`, `orchestration/graph`, `orchestration/reliability`, `orchestration/replan`, `policy`. **Excluded, with the ground for each**: `config` (declarative wiring, no branches worth covering), `delivery` (framework-bound controllers, covered by contract and integration tests rather than unit branches), `persistence` (repository implementations, covered by real-store integration tests), `audit/telemetry` (emission plumbing, asserted by the secret-scan and correlation tests), `orchestration/executor/ai` (provider adapters, exercised by recorded-fixture parse tests and demo verification), and generated sources
  - **TDD**: EVIDENCE · **Validate**: measured report with the exclusion set stated; the exclusion set is **written into the coverage configuration and committed before the first measurement**, and the report states it — a denominator chosen after seeing the number is not a threshold; and each excluded package is named alongside **the specific task that covers it instead** — `delivery`→T012/T030s, `persistence`→T040s, `audit/telemetry`→T100a/T104, `orchestration/executor/ai`→T073a–T073f — so that "covered elsewhere" is checkable rather than asserted · **Docs**: new file · **Trace**: NFR-TST-003
  - **Guard**: **coverage is a measurement, not the definition of done** — Principle XI's eleven points remain the standard. PVT-008 is now an approved threshold, so a miss is a failure rather than an observation. **The exclusion list is published first precisely because it is where a coverage number can be quietly manufactured.** Six exclusions, each with a stated ground and each covered by a different test tier rather than by nothing — that second clause is what makes an exclusion legitimate rather than convenient. **`orchestration/executor/ai` is the one that got harder to justify** (Decision J, ADR-004-A2): with no deterministic counterpart behind any AI-capable stage, these six adapters are the *only* code path at half the nodes, so excluding them from branch coverage excludes the sole executor of six stages. It stays excluded because branch coverage of a prompt-and-parse adapter measures almost nothing — but the compensating tier is named and load-bearing rather than nominal · **Done**: measured; threshold compared; exclusions declared with their covering task each · **Approval**: none
- [ ] T145a [P] Load harness — `src/test/java/agentic/shortener/perf/LoadHarness.java`
  - **Req**: **NFR-SCA-001**, PVT-003, PVT-004 · **Scn**: — · **ADR**: **ADR-011** · **Pre**: T143
  - **Deps**: T143 · **Par**: yes · **Artifact**: a deterministic generator sustaining **PVT-003 (100 concurrent clients)** with per-request latency capture, percentile computation, and a 4xx/5xx split so **deliberate 4xx are excluded from the error rate** as PVT-004's conditions require
  - **TDD**: TEST-WITH · **Validate**: harness self-test — a stubbed endpoint with injected fixed latency yields the expected p95 within tolerance, proving the **measurement instrument** before any measurement is believed · **Docs**: `docs/evidence/perf/method.md` · **Trace**: NFR-SCA-001
  - **Guard**: **an uncalibrated instrument produces numbers, not measurements.** The self-test is what makes every figure downstream citable. Barriers and warm-up are explicit: a cold-JVM p95 measures class loading, not the system · **Done**: self-test passes; method document states warm-up, duration, and the 4xx exclusion · **Approval**: none
- [ ] T145b Redirect latency and error-rate measurement — `src/test/java/agentic/shortener/perf/RedirectLoadIT.java`
  - **Req**: **NFR-PERF-001**, **NFR-SCA-001**, **PVT-001**, **PVT-004**, **SC-013** · **Scn**: — · **ADR**: ADR-011, ADR-014 · **Pre**: T145a, T043, T044, T050
  - **Deps**: T145a · **Par**: **no — load measurements MUST NOT run concurrently**; contended CPU invalidates a p95, so T145b, T145c and T145d run in sequence · **Artifact**: redirect resolution measured at **PVT-003** concurrency against a real store: **p95 ≤ PVT-001 (50 ms)** and **non-4xx error rate ≤ PVT-004 (0.1%)**
  - **TDD**: EVIDENCE · **Validate**: measured p95 and error rate reported with their conditions; **the analytics-append component reported separately** (PVT-001 was approved knowing the append sits inside its budget); every figure labelled `MEASURED` with conditions, never bare (SC-012) · **Docs**: `docs/evidence/perf/` · **Trace**: matrix NFR-PERF-001, NFR-SCA-001, PVT-001, PVT-004, SC-013
  - **Guard**: **PVT-001 and PVT-004 became binding acceptance thresholds at CR-005 and had no task measuring them** (analyze finding H6) — a threshold nobody measures is a target in name only, and would fail `POL-TRC-001`. A miss is a **failure to report**, not an observation to smooth: these are demonstration measurements on one machine (AS-009) and must never be presented as production statistics · **Done**: p95 and error rate measured, labelled, and compared to thresholds; append component isolated · **Approval**: none
- [ ] T145c Creation latency measurement — `src/test/java/agentic/shortener/perf/CreationLoadIT.java`
  - **Req**: **NFR-PERF-002**, **PVT-002** · **Scn**: — · **ADR**: ADR-011, ADR-007 · **Pre**: T145b, T039, T041
  - **Deps**: T145b · **Par**: no (exclusive machine access — see T145b) · **Artifact**: link creation measured at PVT-003 concurrency: **p95 ≤ PVT-002 (200 ms)**, with **collision-retry frequency reported separately** so a latency figure cannot hide keyspace pressure
  - **TDD**: EVIDENCE · **Validate**: measured p95 labelled with conditions; collision retries counted and reported; **NFR-SCA-002** cross-checked — observed collision pressure against the PVT-005 keyspace calculation · **Docs**: `docs/evidence/perf/` · **Trace**: matrix NFR-PERF-002, PVT-002, NFR-SCA-002
  - **Guard**: reporting creation latency without the collision-retry count would let a comfortable p95 conceal a keyspace that is tighter than PVT-005 assumes. The two numbers belong together · **Done**: p95 and retry frequency both measured and labelled · **Approval**: none
- [ ] T145d Analytics append-failure ceiling and first-bottleneck measurement — `src/test/java/agentic/shortener/perf/AnalyticsAppendCeilingIT.java`
  - **Req**: **PVT-009** (as redefined), **FR-URL-010**, **NFR-SCA-003**, EC-012 · **Scn**: DS-B · **ADR**: **ADR-014** · **Pre**: T145c, T048, T049, T050
  - **Deps**: T145c · **Par**: no (exclusive machine access — see T145b) · **Artifact**: at PVT-003 concurrency, **append failures ≤ 0.5% of appends, every failure counted and visible on the recording port's failure counter, zero silent failures, zero redirects blocked or delayed** — plus the **measurement that supports NFR-SCA-003's bottleneck analysis**: append throughput and `redirect_event` growth rate
  - **TDD**: EVIDENCE · **Validate**: under normal load the failure count is reported against the 0.5% ceiling; under a **deliberately killed store** failures occur, are **counted**, and **no redirect fails** (EC-012) — the fault-injection case is mandatory, because a ceiling that is never approached is unfalsifiable; the counter's total is reconciled against the append attempt count so a **silent** failure would show as a discrepancy · **Docs**: `docs/evidence/perf/`, ADR-014 §Scale analysis gains its supporting measurement · **Trace**: matrix PVT-009, FR-URL-010, NFR-SCA-003, EC-012
  - **Guard**: **this is the task that makes PVT-009 a real target.** The owner's ground for keeping 0.5% rather than 0% was that 0% cannot survive its own fault-injection test — so the fault-injection case is not an extra, it is the case that gives the number meaning. It also discharges **NFR-SCA-003's** owed measurement (analyze finding M8, checklist CHK055): the analysis says `redirect_event` growth is the first bottleneck, and this is the measurement behind that claim · **Done**: ceiling measured under normal load and under fault injection; counter reconciled; NFR-SCA-003 measurement recorded · **Approval**: none
- [ ] T146 Release-readiness evaluation run — `docs/evidence/release-readiness-report.md`
  - **Req**: **FR-ORC-025** · **Scn**: DS-A · **ADR**: ADR-011 · **Pre**: T143, T144, T145, T130
  - **Deps**: T130, T143, T144, T145 · **Par**: no · **Artifact**: the nine conditions evaluated against real evidence; every unmet condition named
  - **TDD**: EVIDENCE · **Validate**: dependency-vulnerability and secret scans **executed** with results attached; traceability complete; documentation current · **Docs**: new file · **Trace**: Constitution §Governance
  - **Guard**: **if any mandatory policy remains `FAIL`, this task reports blocked rather than weakening the check** — stop condition 2 · **Done**: report produced with a determination and named conditions · **Approval**: none
- [ ] T147 [P] Limitations and residual-risk disclosure — `docs/LIMITATIONS.md`
  - **Req**: Constitution XI, FR-ORC-015 (retired — this task is its disclosure, T123's traceability sweep found the reference missing here though spec.md's own matrix row already names T147) · **Scn**: — · **ADR**: — · **Pre**: T146
  - **Deps**: T146 · **Par**: yes · **Artifact**: every known limitation and residual risk, including single-host measurement, compressed time parameters, meta-schema lint status, deferred backlog items, **the deliberately deferred per-creator aggregate redirect tier and which run closed it** (cross-checked against the baseline-omissions register, T055a), the **retention posture** — indefinite retention, unbounded table growth by design, production archival recorded as a recommendation (NFR-AUD-003, CR-017) — the **governance-surface posture** (*separation of the two identity models is proven by test; reachability of the governance surfaces is not, because they are unauthenticated by design*), and **PVT-016**'s values as engineering judgement rather than measurement, with the accepted consequence that an unattended run stalls and then suspends **for up to the uniform PVT-006 gate-wait deadline** (owner ruling, 2026-09-20: no separate overrun timeout; reviewer finding A2 declined, cost accepted); the **actor-identity limitation** — governance surfaces are unauthenticated by design, `actorType` is declared and not verified, so the defensible claim is that no workflow step approves anything rather than that impersonation is prevented; verified actor identity is out of scope by owner decision and an operator-issued per-decision token was considered and declined; and — stated as its own named entry because the assignment names the control — **fallback is not demonstrated**: FR-ORC-015 is retired (Decision K, CR-032) because its only implementation was the six deterministic counterparts Decision J struck; **bounded retry then safe suspension is the entire degradation story**; and the reason a token counterpart was not kept is that a control present in the documentation and absent in the engineering is the exists-mainly-to-be-claimed defect this project rejects — **a documented honest absence over a ceremonial presence**, with a re-pointed genuine degradation (a dated advisory snapshot for the S10 vulnerability scan) considered and declined as new scope
  - **TDD**: N/A-DOC · **Validate**: cross-checked against every ADR's Risks section and every DF entry; the fallback retirement is present as a **named absence** stating what the assignment asks, what is demonstrated instead, and why — **not** as a silent omission · **Docs**: new file · **Trace**: Constitution XI point 8
  - **Guard**: **undisclosed limitations are a release-blocking condition** (condition 8). Disclosure is cheaper than discovery · **Done**: no ADR risk or DF item absent · **Approval**: none
- [ ] T148 [P] Governance-evidence index — `docs/evidence/governance-index.md`
  - **Req**: Constitution §Gate semantics, `POL-CHG-002` · **Scn**: — · **ADR**: — · **Pre**: T146
  - **Deps**: T146 · **Par**: yes · **Artifact**: every gate decision, change request, exception, and ADR with its outcome, authority, date, and commit
  - **TDD**: N/A-DOC · **Validate**: `POL-CHG-002` — every mandatory gate has a materialized record; zero unmaterialized outcomes · **Docs**: new file · **Trace**: Constitution §Governance condition 3
  - **Guard**: an unmaterialized gate outcome blocks release readiness; this index is how it is detected rather than assumed · **Done**: every gate, CR, exception, and ADR indexed · **Approval**: none
- [ ] T149 [P] Documentation-behaviour drift check — `docs/evidence/drift-check.md`
  - **Req**: Constitution X, §Governance condition 7 · **Scn**: — · **ADR**: — · **Pre**: T126, T143
  - **Deps**: T126, T143 · **Par**: yes · **Artifact**: every documented behaviour matched to an executed test; every implemented behaviour matched to documentation
  - **TDD**: EVIDENCE · **Validate**: zero documented-but-absent and zero present-but-undocumented behaviours · **Docs**: new file · **Trace**: condition 7
  - **Guard**: **undocumented architecture drift is a detection target**; T014's architecture test covers structure, this covers behaviour · **Done**: both counts zero · **Approval**: none
- [ ] T150 [P] Traceability matrix final population — `specs/001-agentic-sdlc-url-shortener/spec.md` §Traceability
  - **Req**: FR-ORC-027, SC-010 · **Scn**: all · **ADR**: — · **Pre**: T123, T143
  - **Deps**: T123, T143 · **Par**: yes · **Artifact**: Task, Test, and Evidence columns populated for all 51 requirements; Design and ADR already populated by CR-006
  - **TDD**: EVIDENCE · **Validate**: T124's zero-orphan assertion passes over the **complete** ten-column matrix, carrying the new tasks and the new tests, with `POL-TRC-001` green over **all tasks in the plan** — the count is not hard-coded here, because it has already gone stale twice (171 → 172 → 173) and a count in a validation criterion is a maintenance liability with no benefit · **Docs**: spec §Traceability · **Trace**: SC-010
  - **Guard**: this edits **approved** specification text — it requires a change-control record (`POL-CHG-001`), raised as part of T151 · **Done**: ten columns populated; zero orphans · **Approval**: none
- [ ] T151 [GATE] Change-control record for final traceability population — `docs/governance/change-control/CR-<next>-traceability-final-population.md`
  - **Req**: `POL-CHG-001` · **Scn**: — · **ADR**: — · **Pre**: T150
  - **Deps**: T150 · **Par**: no · **Artifact**: change-control record with the eight impact fields for the matrix population
  - **TDD**: N/A-DOC · **Validate**: record materialized before the spec edit is committed · **Docs**: change-control record · **Trace**: `POL-CHG-001`
  - **Guard**: the matrix is approved text; populating it is a change, not housekeeping · **Approval**: **REQUIRED — human owner**
  - **Done**: record approved; spec edit committed with it
- [ ] T152 [GATE] Release readiness and final submission decision — `docs/governance/gate-decisions/gate-<next>-release-readiness.md`
  - **Req**: **FR-ORC-025**, Constitution III · **Scn**: all · **ADR**: — · **Pre**: T146–T151
  - **Deps**: T146, T147, T148, T149, T150, T151 · **Par**: no (**final synchronization point**) · **Artifact**: gate record carrying the readiness determination, the release owner's decision, and the submission decision
  - **TDD**: N/A-DOC · **Validate**: record carries the four mandatory fields plus `CLAUDE.md` structure; every unmet condition disclosed · **Docs**: gate record · **Trace**: Constitution §Governance
  - **Guard**: **readiness MUST NOT be self-certified by an agent.** If conditions remain unmet, the honest outcome is a recorded block, and the minimum defensible outcome in plan §14 governs what is reported as delivered · **Done**: human decision recorded · **Approval**: **REQUIRED — human owner (release owner)**

---

## Dependencies and Story Completion Order

```
Phase 0 (T001–T007)  ──[T007 GATE]──► Phase 1 (T008–T020)
                                            │
                                     [T020]─┴──► Phase 2 (T021–T032) ──[T031 GATE, T032 skeleton]──┐
                                            │                                                       │
                                            └──► Phase 5 orchestration (T069–T096)                  │
                                                        │                                           │
                                                   [T080]│                                          ▼
                                                        ▼                              Phase 3 US1 (T033–T057)
                                            Phase 4 US2 (T058–T068) ◄──[T057]──────────────────┘
                                                        │
                                                   [T096]▼
                                            Phase 6 US4 (T097–T106, T108–T110, T110a)
                                            [T107 now sits in Phase 5]
                                                        │
                                            Phase 7 US5 (T111–T130) ◄──[T096]
                                                        │
                                            Phase 8 Scenarios (T131–T142) ◄──[T110, T115]
                                                        │
                                            Phase 9 Release (T143–T152)
```

**Story completion order**: US1 (P1) → US2 (P1) → US3 (P2) → US4 (P3) → US5 (P3). US1 and US3 can progress
concurrently after T032 and T020 respectively; US2 requires both T057 and T080, which is the first genuine
convergence.

**Synchronization points**: T007, T020, T032, T057, T068, T080, T096, T110, T130, T142, T152. **T136a is a gated task** (security-sensitive change) and is therefore a de facto synchronization point in Phase 8.

**Human approval tasks**: T007, T031, **T136a**, T151, T152 — **five**. T136a's class is **security-sensitive** (an abuse control with a disclosure criterion). Each blocks its successors and cannot be satisfied by silence.

**Architecture-sensitive blocking**: every task citing an ADR was unblocked only because ADR-001..014 were
Accepted at Gate 4 (2026-09-20). Had any remained Proposed, its dependent tasks would be blocked, not deferred.

---

## Parallel Execution Examples

**Phase 1** — after T008: `T009`, `T011`, `T014`, `T016`, `T018`, `T019` are independent (six parallel).
**Phase 2** — after T020: `T021`, `T022`, `T023`, `T024` are independent entities (four parallel); `T029`, `T030` parallel after T011.
**Phase 3** — after T032: `T033`, `T034` parallel; then `T036`, `T037` parallel after T035; `T044`, `T045` parallel after T043; `T046`, `T047` parallel; `T054`, `T056` parallel.
**Phase 5** — after T069: `T070`, `T071`, `T072`, `T073` parallel (four). The six AI adapters then unblock in
**three waves, not one** — the earlier claim that all six were simultaneously parallel was wrong, and the affordability argument
is restated rather than patched:
  · **Wave 1** — `T073a`, `T073b`, `T073c`, `T073f` parallel once T073 and **T082** are done. Four at once: the
    widest simultaneous window in the plan. **Removing the deterministic counterparts (ADR-004-A2) did not widen
    this wave**, and it is worth saying so rather than claiming a gain: T071 sat in the same Phase-5 parallel group
    as T073, so an adapter waiting on both unblocked at the same moment either way. The counterpart dependency was
    never the binding constraint here.
  · **Wave 2** — `T073d`, which additionally needs **T107** (the seven-dimension impact-analysis model). T107 moves into
    this phase for exactly this reason; it never needed the change-request model it was previously chained to.
  · **Wave 3** — `T073e`, which additionally needs **T065** (the no-plan gate) in Phase 4, so it is the last of the six
    to unblock.
**Why the six-adapter scope is affordable**: each adapter touches only its own files and shares nothing but the transport
— so they do not contend, and **five of six can run at once** once the counterpart dependency is gone (ADR-004-A2). It is **not**
affordable because all six start simultaneously; they do not, and claiming they did overstated the schedule in the one
place the owner's scope decision relied on it. Also after T074: `T075`, `T076` parallel; after T084: `T085`, `T086`,
`T087` parallel; after T088: `T089`, `T090` parallel; after T093: `T094`, `T095` parallel.
**Phase 6** — after T100: `T103`, `T106`, `T107` parallel; `T104`, `T105` parallel after T103.
**Phase 7** — after T111: `T112`, `T113`, `T114` parallel; after T118: `T119`, `T120`, `T121` parallel; `T126`, `T127`, `T128` parallel.
**Phase 9** — after T146: `T147`, `T148`, `T149`, `T150` parallel (four).

**Deliberately NOT parallel**: T026 and T027 (both migrations); T042 and T044 (controller wiring); T038/T039/T041 (all guard the create path); T083/T084 (envelope consumed by ruling); T074/T077/T080 (state and graph roots); T111 (every stage writes through it).

---

## Implementation Strategy

**MVP scope**: **Phase 0 → Phase 1 → Phase 2 → Phase 3 (US1)**, ending at T057. That delivers a genuinely
working URL shortener with executed domain tests, a validated contract, and a real store — demonstrable with the
orchestration engine switched off, which is US1's stated independent test criterion.

**Why US1 is the MVP rather than US3**: US3 is the graded differentiator, but it has nothing to govern until the
domain exists, and DS-B's subject matter is the shortener's analytics path. A governed lifecycle over
non-functional software would demonstrate nothing (CN-008).

**Incremental delivery after MVP**: US2 (gates) → US3 (orchestration) → US4 (compliance) → US5 (evidence) →
scenarios → release. Each phase ends with executed tests and committed evidence, never with unverified code.

**Minimum defensible outcome if the timebox binds** (plan §14): working shortener with executed domain tests; the
persisted twelve-node graph with prohibited-transition enforcement; at least one real human gate with a passing
silence test; two-vote retry plus one compensation and one resumption proof; audit records sufficient to
reconstruct one run; all three scenarios executed even if DS-B's fault injection is reduced; policy evaluation
with at least one demonstrated blocking `FAIL`; and a final summary whose every claim traces to evidence.
**Anything less is reported as incomplete rather than relabelled done.**

**Scope-control discipline**: cuts come from `docs/delivery/backlog.md` (T002) and never from mandatory
validation or reviewer evidence. The four checkpoints in T005 are the only authorised cut points; the four stop
conditions in T006 halt rather than reduce.

**AI wiring is in scope for all six stages from the outset** (T073a–T073f, owner decision 2026-09-20). The earlier
draft of this plan reduced it to two pre-emptively; the owner rejected that, on the ground that the estimate
behind it priced the work at human authoring speed while the implementation is AI-authored — the same correction
she applied to ADR-001's velocity argument, now applied to this plan's own scoping. Reducing AI wiring remains the
**first** cut available at the End-Day-2-AM checkpoint, taken **on the record** if genuine time pressure appears.
The costs that would make that call — per-stage demo verification time against a real CLI, and prompt-output
debugging — are accepted, and observing them is precisely what the checkpoint is for.


