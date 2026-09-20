# Gate Decision Record — Gate 4 Closing Package

**Successor record to [`gate-04-adr.md`](./gate-04-adr.md)**, which is filed and remains unedited per
`CLAUDE.md` §Immutability. That record closed the fourteen ADRs. This one closes the items it left open.

| Field | Value |
|---|---|
| Gate | Gate 4 closing package — architecture approval of the lifecycle |
| Outcome | **ALL ITEMS APPROVED** — 7 decisions, closing every item `gate-04-adr.md` held open |
| Deciding human | Pravallika Veeravalli |
| Decision date | 2026-09-20 |
| Artifacts approved | `specs/001-agentic-sdlc-url-shortener/plan.md` (the technical plan), CR-002, the resolutions of DF-002 and DF-003, all 15 PVT values, the §14 timebox and scope controls, the MTTR method |
| Prior record | `docs/governance/gate-decisions/gate-04-adr.md` (14 ADRs accepted, same date) |
| Governing constitution | v1.1.0 (last amended 2026-09-18) |
| Recorded in | This record; the specification's §Change-control amendments; CR-002..CR-005; the commits applying them |

## Reason / verification performed

The owner reviewed and approved every item left open at ADR close. Her reasoning is recorded per decision below,
verbatim where her own wording carries it.

### 1. CR-002 — Credential expiry required at provisioning: **APPROVED**

> Apply the five specification edits for required credential expiry; it formalizes the decision I already made
> at ADR-013.

### 2. DF-002 — Redirect permanence: **RESOLVED — redirects are temporary, never permanent**

> A permanent redirect is cached by browsers, after which clicks never reach the service — analytics cannot
> count them (ADR-014 would be structurally blinded) and expiry cannot be enforced (a cached redirect outlives
> the link's death). Temporary is the only class compatible with FR-URL-008 and FR-URL-010. Exact status code is
> an implementation detail within the temporary class.

### 3. DF-003 — Retention boundary: **RESOLVED — the audit retention clock starts at run termination**

> A run's history must never age out while the run is alive or freshly terminal; with idle retention and audit
> retention both at 90 days, a day-90 auto-abandonment must not coincide with its own earliest records becoming
> purgeable.

### 4. PVT set — **all 15 APPROVED as tabled**

Including **PVT-009 as redefined** (append-failure-rate ceiling, not loss budget), **PVT-013/014** (the owner's
two-tier redirect limits), **PVT-015** (90-day idle retention), and **PVT-001 approved with the knowledge that
the analytics append now sits inside its budget, measured separately**.

### 5. Timebox and scope controls (plan §14) — **APPROVED**

Day-level milestones, the four scope-control checkpoints, the four stop conditions, and the rule that cuts hit
backlog before evidence.

### 6. MTTR method — **APPROVED**

The mandated formula, the nine captured fields, unrecovered failures excluded from the denominator and reported
separately, and the declared human-wait exclusion reported as its own metric.

### 7. The technical plan itself — **APPROVED**

> This is the Human Architecture Approval of the lifecycle.

`plan.md` moves from PROPOSED to APPROVED. The mandatory gate "Architecture and technology selection (ADR)" in
the constitution's gate table is now satisfied by `gate-04-adr.md` together with this record.

## Conditions attached to the approval

1. Change requests raised for these resolutions are approved **provided each implements exactly the decisions
   stated above**. Raised, marked approved on the owner's authority dated 2026-09-20, applied, and each reported.
2. Housekeeping authorised without further approval: rename the contract field to `executorKindUsed` per CR-001's
   recommendation; run parser validation over all contract files now that permission exists and **replace
   ADR-005's disclosed verification gap with executed evidence**; apply ADR-014's approved correction to plan
   §Decisions Required and `research.md`.
3. Recorded per convention as a successor record rather than by editing `gate-04-adr.md`.
4. Committed with conventional messages honouring co-commit rules. **Not pushed.**
5. `/speckit-checklist` is **not** run — the supervisor initiates it.

## Change requests raised under this package

| CR | Scope | Status |
|---|---|---|
| **CR-002** | Credential expiry required at provisioning (5 spec edits) | APPROVED 2026-09-20, applied |
| **CR-003** | DF-002 resolution — redirects are temporary, never permanent | APPROVED 2026-09-20, applied |
| **CR-004** | DF-003 resolution — audit retention clock starts at run termination | APPROVED 2026-09-20, applied |
| **CR-005** | Gate 4 closing approvals reflected in the specification: PVT set approved, DF-005 closed, `executorKindUsed` rename | APPROVED 2026-09-20, applied |

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Redirect implementation MUST use a temporary-class redirect; a permanent redirect is prohibited because it defeats both analytics counting and expiry enforcement | Slice 3 |
| Audit retention measured from run termination, never record creation; a purge job MUST NOT be able to remove records of a live or freshly terminal run | Slice 7 |
| PVT-001 measured with the analytics append component **reported separately**, so its budget consumption is visible rather than inferred | Slice 7 |
| All 15 PVT values now **constrain implementation** — they are approved acceptance thresholds, no longer proposals | All slices |
| §14 stop conditions are in force: cuts hit backlog before evidence; Slice 4 incompletion by end of Day 2 halts feature work; fabrication pressure is a hard stop | All slices |
| MTTR reported with its population, the human-wait exclusion, and unrecovered failures counted separately — always labelled a demonstration measurement | Slice 7 |
| Plan is APPROVED; further changes to it pass through change control (Constitution §Compliance and Change-Control) | Ongoing |
| Enforcement points from `gate-04-adr.md` remain in force and are not superseded by this record | As stated there |
