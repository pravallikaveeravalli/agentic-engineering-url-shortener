# CR-062 — Greenfield requirement replaced with the clean, minimal real feature — no implementation-workaround
clauses, now that CR-060 gives the code-writer real existing-file editing capability

| Field | Value |
|---|---|
| **Change request** | CR-062 |
| **Title** | `DsALiveRun`'s `REQUIREMENT` constant replaced with the owner's own minimal, clean wording — no new-files-only constraint, no placeholder-value clause, no OpenAPI-follow-up carve-out |
| **Raised by** | Owner's own direction, this session (2026-09-22): "The code-writer now edits existing files reliably (CR-060)... greenfield can be the REAL feature with a clean requirement — no new-files workaround, no placeholder, no 'note OpenAPI as follow-up.'" |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — requirement text replaced verbatim as supplied** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — same endpoint contract (path, method, no-auth, response shape) as every prior wording since CR-054; removes implementation-steering clauses only, all of which existed solely to route around CR-060's now-fixed limitation |
| **Artifacts changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`REQUIREMENT` constant) |
| **Non-approved files changed** | none |

---

## Why this is safe now

CR-056/058/059's own accreted wording all existed for one reason: `ImplementationAiExecutor` could not
reliably edit an existing file (`pom.xml`, the OpenAPI contract), so the requirement text routed around that
gap with a new-files-only constraint and a placeholder value. CR-060 fixed the actual capability — proven,
this session, on a real `pom.xml` edit and a real `.java` file edit, with a genuine `javac` compile. The
workaround clauses are no longer load-bearing; removing them lets the code-writer make its own real
implementation choices (`build-info` via a real `pom.xml` edit; a real OpenAPI contract entry) on their own
merits.

## Governed, not blind: the sanity check

Per the owner's own explicit instruction, this requirement's own real S3 output was inspected in isolation
(`GreenfieldCleanRequirementSanityCheck`, S1→S2→S3 only, no full pipeline) BEFORE any full run — see
`docs/evidence/ds-a/sanity-check-clean-requirement.md`. Three real `MATERIAL_PENDING` findings, all
genuinely material, all already covered by the standing delegation's own six answers. The sanity check itself
found and fixed a real regression in the matcher (CR-063) before it could affect the full run.

## Scope

**What changed**: requirement text only.

**What did not change**: the endpoint's own approved contract (unchanged since CR-054); S6's own conditional
architecture gate (CR-057); the matcher's own whole-word matching principle (CR-061), extended by CR-063.

## Conditions attached to the approval

1. **Sanity-checked before the full run, not assumed clean** — honoured, see above.
2. **No live greenfield run in the SAME turn as an unverified matcher change** — honoured; CR-063's fix was
   tested before PART 2 was attempted.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| The full live run (PART 2) is the real-world test of this requirement + the CR-060 editor fix together | Immediately following this CR, same turn |
