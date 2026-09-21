# CR-038 — Control-plane controllers relocated to the orchestrator side

| Field | Value |
|---|---|
| **Change request** | CR-038 |
| **Title** | Relocate T067, T067a and T082a from `delivery/` to `orchestration/api/`; correct T076a's `Deps` |
| **Raised by** | Acting agent (material conflict reported at the Slice 4 boundary) |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED** — ruling 1 and ruling 3, 2026-09-21 |
| **Decision date** | 2026-09-21 |
| **Classification** | **PATCH** — target paths and one dependency edge in the task plan; no requirement, interface, schema or behaviour changes |
| **Artifacts changed** | `specs/001-agentic-sdlc-url-shortener/tasks.md` — T067, T067a, T082a `Artifact` paths; T076a `Deps`/`Pre` |
| **Non-approved files changed** | `src/test/java/agentic/shortener/arch/DependencyDirectionTest.java` (one added assertion) |
| **Also recorded in** | Gate-decision context: none — this is an owner ruling on a reported conflict, not a gate decision |
| **Applied** | 2026-09-21 |

---

## Reason / the conflict this resolves

Three tasks named a target path inside the **application plane's** delivery package:

| Task | Path as approved |
|---|---|
| T067 Gate inspection view | `src/main/java/agentic/shortener/delivery/RunInspectionController.java` |
| T067a Gate-decision recording surface | `src/main/java/agentic/shortener/delivery/GateDecisionController.java` |
| T082a Run creation and requirement submission surface | `src/main/java/agentic/shortener/delivery/RunSubmissionController.java` |

All three must read or write orchestration state. T014's ArchUnit rule
`applicationPlaneDoesNotImportControlPlane` forbids exactly that:

> no classes that reside in any package `agentic.shortener.domain..`, `agentic.shortener.application..`,
> `agentic.shortener.delivery..`, `agentic.shortener.persistence..` should depend on classes that reside in
> `agentic.shortener.orchestration..`
>
> because NFR-MNT-002 and ADR-006: two planes in one deployable stay separable only if the dependency runs
> one way. The shortener must be demonstrable with the orchestration engine switched off.

So each of the three, implemented at its approved path, would have failed a mandatory architecture test.
This is a **material conflict between two approved artifacts**, reported rather than worked around.

**The owner's ruling, in her words:**

> they belong there — the run-store already lives there for the same reason, and the task's own text calls
> run-inspection an orchestrator surface. Keeps your wall intact.

The rule is **preserved, not weakened.** The paths were the defect, and two things in the approved artifacts
already said so:

1. **T067's own `Guard`** reads *"run inspection is an **orchestrator surface**"* — the task text disagreed
   with the task path.
2. **`JdbcRunStore` is already inside `orchestration`** for the mirror-image reason, recorded when T074 was
   built: the control plane needs persistence, and `persistence` may not depend on `orchestration`, so the
   control plane's own store lives on the control-plane side.

## The package chosen, and why not `orchestration/delivery`

**Chosen: `agentic.shortener.orchestration.api`.**

`orchestration.delivery` was the obvious symmetric name and is **rejected**, for the same reason the store
is not called `orchestration.persistence`:

- **The established precedent gives the control-plane copy a different name.** The app plane's persistence
  layer is `persistence`; the control plane's is `orchestration.store`. It does not reuse the segment.
- **A shared segment name is a future trap.** T014's rule is written as `agentic.shortener.delivery..`,
  which does not match `agentic.shortener.orchestration.delivery`. But a later "simplification" of that rule
  to `..delivery..` — a plausible edit — would silently catch the control-plane controllers and break the
  very rule this relocation exists to satisfy. A distinct segment cannot be caught by that glob at all.

This is the project's standing preference for **structurally inexpressible over detectable**, applied to a
package name.

## Scope

**Paths only.** Explicitly unchanged:

- FR-ORC-007, FR-ORC-008, FR-ORC-013, CN-012, SC-005, SC-016 — every clause;
- every `Validate`, `Guard` and `Done` clause of T067, T067a and T082a, including T067a's seven tests and
  T082a's five;
- the HTTP paths in `contracts/openapi.yaml` — `GET /v1/runs/{runId}`, `POST /v1/runs`,
  `POST /v1/runs/{runId}/gates/{gateId}/decision`. **A Java package is not a URL**, and no route moves;
- T014's rule text, which is not edited;
- CN-012's credential boundary: these surfaces remain unauthenticated by design, and T063a's
  `CredentialBoundaryTest` still applies to them — relocating them into `orchestration` brings them
  *within* that test's subject packages, which strengthens rather than weakens it.

## Ruling 3, folded in as housekeeping

T076a's `Deps` read `T076, T080`, while its `Validate` clause asserts against `artifact_version` — the table
T081 creates. The dependency was real and unrecorded, which is a traceability defect: a reader sequencing
work from the `Deps` graph would have scheduled T076a before the table existed.

Corrected to `T076, T080, T081` in both `Pre` and `Deps`. No other task's dependencies change.

For the record: the work was in fact done in the correct order — T076a and T081 landed in one commit
(`43c66b2`) with V5 creating `artifact_version` — so nothing built on a missing table. The defect was in the
plan's graph, not in the build.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | `tasks.md` T067 `Artifact` path | reads `src/main/java/agentic/shortener/orchestration/api/RunInspectionController.java` | **EXECUTED — HOLDS** |
| 2 | `tasks.md` T067a `Artifact` path | reads `.../orchestration/api/GateDecisionController.java` | **EXECUTED — HOLDS** |
| 3 | `tasks.md` T082a `Artifact` path | reads `.../orchestration/api/RunSubmissionController.java` | **EXECUTED — HOLDS** |
| 4 | `tasks.md` T076a `Pre` and `Deps` | both read `T076, T080, T081` | **EXECUTED — HOLDS** |
| 5 | `grep -rn "delivery/RunInspectionController\|delivery/GateDecisionController\|delivery/RunSubmissionController" specs src scripts` | no matches outside applied records | **EXECUTED — HOLDS: none** |
| 6 | New assertion in `DependencyDirectionTest`: no class in `agentic.shortener.delivery..` is named `Run*Controller` or `GateDecision*` | passes | **EXECUTED — HOLDS** |
| 7 | `-Dtest=DependencyDirectionTest` | green, including the unchanged `applicationPlaneDoesNotImportControlPlane` | **EXECUTED — HOLDS: 6/6** |
| 8 | Fast tier | green | **EXECUTED — HOLDS: 413/413** |

Step 6 is the part that stops this drifting back. The relocation is currently only a path in a task plan,
and the three controllers do not exist yet; an assertion that the app plane's delivery package holds no
control-plane controller is what makes the ruling hold when they are built, rather than depending on whoever
builds them reading this record.

## Conditions attached to the approval

1. **T014's rule is preserved, not weakened.** Its text is not edited, and step 7 confirms it still passes.
2. **Keep the app-plane / control-plane boundary test green** — step 7.
3. **Pick the orchestration-side package that sits correctly relative to `JdbcRunStore`.** Done, with the
   reasoning and the rejected alternative recorded above.

## Cross-record orphan check — finding, corrected forward

**CR-016 quotes the superseded paths.** `CR-016-task-plan-coverage-and-sequencing.md` introduced T067a and
T082a and quotes their task lines verbatim, including
`src/main/java/agentic/shortener/delivery/GateDecisionController.java` and
`src/main/java/agentic/shortener/delivery/RunSubmissionController.java`.

**CR-016 is not edited.** An applied record's content is immutable, and its quotations are the record of what
was approved *then*. This paragraph is the forward correction the orphan-check procedure requires:

> As of CR-038 (2026-09-21), the target paths CR-016 quotes for T067a and T082a are superseded. The
> approved paths are `src/main/java/agentic/shortener/orchestration/api/GateDecisionController.java` and
> `src/main/java/agentic/shortener/orchestration/api/RunSubmissionController.java`. CR-016's quoted text
> remains accurate as a record of the task lines at the time it was applied, and is not accurate as a
> statement of where those files now belong.

No other applied record quotes these three paths. Verified by repository-wide search (verification step 5).

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| T067, T067a and T082a are implemented under `orchestration/api/`, not `delivery/` | When each is built |
| `orchestration/api` is never given a `delivery` segment in its name | Any future control-plane surface |
| T063a's `CredentialBoundaryTest` covers `orchestration/api` once it exists — control-plane controllers must declare no security requirement (CN-012) | T063a |
| T068's US2 sweep depends on T067; it was blocked by this conflict and is unblocked by this record | T068 |
