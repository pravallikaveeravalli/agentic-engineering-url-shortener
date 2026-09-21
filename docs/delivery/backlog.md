# Deferred-Scope Backlog

**Task**: T002 · **Requirement**: DF-004 · **ADRs**: ADR-009, ADR-012, ADR-014 · **Derived from**: `plan.md` §14
Backlog, spec §DF-004, and the named ADR risk sections.

## The rule that governs this file

**A backlog item MUST NOT be pulled forward without an owner decision.** Nothing here may displace mandatory work, and
nothing here may be started because it looked cheap. The reverse also holds: **nothing may be moved *into* this file to
relieve schedule pressure** except on the owner's recorded order at a checkpoint (CR-009, Gate 5 time-attitude
amendment). A checkpoint escalates with options; it does not itself cut.

**This file is where cuts come from when a cut is ordered** — never from mandatory validation, never from reviewer
evidence, and never from the nine slices in `scope-register.md`.

---

## Deferred items

| # | Item | Source that deferred it | Why it is deferred rather than dropped |
|---|---|---|---|
| **B1** | **Run-level retry circuit breaker** — a whole-run retry ceiling above the per-stage bounds | **DF-004**, from CL-006 | Per-stage bounds (PVT-007) plus run-level blocking are the in-scope controls. A whole-run ceiling is a production concern; nothing in the approved scope needs it to demonstrate bounded retry |
| **B2** | **Email/webhook expiry notification for suspended runs** | **DF-004**, from CL-005 | The inspectable `auto-abandon-at` plus the deadline stated in the ask serve as the standing warning instead. Notification is delivery infrastructure, not governance |
| **B3** | **Anthropic SDK transport adapter** | **ADR-004-A1** (CR-008) | The CLI subprocess is the implemented transport. The SDK adapter sits behind the same `StageAiProvider` seam, so it is a swap rather than a redesign — which is the point of the seam, and the reason deferring it costs nothing architecturally. **Recorded as a dropped *option*, never as a dropped requirement** |
| **B4** | **`redirect_event` time-partitioning** | **ADR-014** §Scale analysis | Unbounded table growth is accepted **by design** in this demonstration (NFR-AUD-003, CR-017). Partitioning is the production answer to a problem this demonstration deliberately does not have |
| **B5** | **Artifact-level consumption tracking** — replanning invalidation computed from *recorded artifact consumption* rather than declared edges | **ADR-009** §Backlog (Option B) | The adopted approach is transitive closure over declared edges, which is a **superset** of true consumption — so it over-invalidates rather than under-invalidates. Option B is the precision refinement, and it is **only safe once consumption recording is provably complete**: on partial data it under-invalidates, leaving a stale artifact alive, which is the worse failure direction. Complete recording is not achievable inside this timebox, so the imprecise-but-safe option is adopted and the precise one deferred |
| **B6** | **Full-stack Compose profile** — app and store both in Compose, for reviewer convenience | **ADR-012** §Backlog (Option B) | The adopted model runs the store in Compose and the app on the host, which keeps the debugger and the test runner attached to the thing being graded. A full-stack profile is reviewer convenience, not a capability, and would add a build-image step to every change |
| **B7** | **Custom aliases; link deletion and editing; multi-region** | **EX-001, EX-002, EX-003** — excluded at the specification gate | Excluded scope, not deferred work. Listed here so a reader who wonders where they went finds an answer rather than a silence |
| **B8** | **Analytics beyond time series** | `plan.md` §14 | FR-URL-011's creator-scoped time-series retrieval is what the requirement asks for. Anything richer is product scope |

---

## What is deliberately **not** in this file, and why each absence matters

A backlog is also defined by what it refuses to absorb. Three things belong elsewhere, and listing them here would each
create a specific, named defect.

### The per-creator aggregate redirect tier is **NOT** a backlog item

**PVT-014 / FR-URL-016's third tier is a scheduled baseline omission with a named closure**, implemented under
governance by the brownfield run in Slice 8. It is written up in `baseline-omissions.md`.

**Listing it here would let a scheduled omission read as an accepted one** — the artifacts-disagreeing defect CHK228
exists to catch. FR-URL-016 is **binding in full**; its matrix reference reads ***partial*** until the brownfield run
closes it, and if that run does not happen, **release readiness reports a real gap** rather than relabelling it.

### Retention work is **NOT** a backlog item, for a different reason

**There is no retention work at all.** This demonstration retains every record indefinitely; production archival is a
**recorded recommendation**, not deferred scope (NFR-AUD-003, CR-017). A backlog entry would imply work someone intends
to do, and nobody does.

### AI wiring for any stage is **NOT** a backlog item — and this is the one the owner ruled on directly

**All six AI-capable stages are wired from the outset** (`tasks.md` T073a–T073f). The owner **overruled an earlier
pre-emptive reduction**, on the ground that its estimate priced AI-authored work at human authoring speed.

Reduction re-enters **only by a recorded checkpoint order** (CR-009). It is the **first option offered** at the End
Day 2 AM checkpoint — offered, never applied by default, and never written into this file in advance. Writing it here at
the outset would be the pre-emptive reduction the owner already rejected, arriving through a side door.

**Since Decision J (ADR-004 Amendment 02) there is no keyless mode and no deterministic counterpart for any AI-capable
stage.** That raises the stakes on this rule rather than lowering them: a reduction ordered at a checkpoint would now
mean a stage with **no executor at all**, not a stage falling back to a deterministic engine. Any such order therefore
needs the owner to be told what the reduction now costs, which is more than it cost when the plan was written.

---

## Traceability

| Item | Cites |
|---|---|
| B1, B2 | spec §DF-004 (OPEN BY DESIGN — recorded, not adopted) |
| B3 | ADR-004-A1, CR-008 |
| B4 | ADR-014 §Scale analysis; NFR-AUD-003, CR-017 |
| B5 | ADR-009 §Backlog — Option B, artifact-level consumption tracking |
| B6 | ADR-012 §Backlog — Option B, full-stack Compose profile |
| B7 | spec §Exclusions EX-001..003 |
| B8 | `plan.md` §14 |
| Absences | `baseline-omissions.md` (T055a); CR-017; CR-009; ADR-004-A2 |
