# CR-067 — S3's prompt states `qualityChecksPerformed` is required unconditionally, never redundant with
`noClarificationReason`; the domain invariant itself untouched

| Field | Value |
|---|---|
| **Change request** | CR-067 |
| **Title** | `AmbiguityDetectionAiExecutor`'s prompt explicitly states `qualityChecksPerformed` is required on every element regardless of `resolutionState`, with a worked example showing it is never redundant with `noClarificationReason` |
| **Raised by** | Owner's own direction, this session (2026-09-22): "Strengthen S3's prompt so it ALWAYS emits `qualityChecksPerformed` for every record — do NOT relax the domain invariant (it's deliberate design)" |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — implemented and tested this turn** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — a prompt-wording fix; no change to `AmbiguityRecord`'s own constructor, `AmbiguityDetectionAiExecutor`'s own validation, or any produced-artifact shape |
| **Artifacts changed** | `src/main/java/agentic/shortener/orchestration/executor/ai/stages/AmbiguityDetectionAiExecutor.java` |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/executor/ai/stages/AmbiguityDetectionAiExecutorTest.java` |

---

## The real, live, twice-observed defect

Two independent, real live occurrences — attempt 18 and attempt 27
(`docs/evidence/ds-a/run-snapshot-ATTEMPT-18-BLOCKED-S3-qualityChecksPerformed-omitted-on-NOT_MATERIAL.md`,
`...ATTEMPT-27-BLOCKED-S3-qualityChecksPerformed-omitted-again.md`) — showed the model omitting
`qualityChecksPerformed` on a `NOT_MATERIAL` record while supplying a substantive `noClarificationReason`,
apparently treating the latter as sufficient on its own. `AmbiguityRecord`'s own constructor refuses this
unconditionally (a real, deliberate domain invariant, T082 — the field is what lets a reviewer tell "nothing
was checked" from "checked and found nothing," for a `MATERIAL_PENDING` finding as much as a `NOT_MATERIAL`
one), so both real dispatches failed the whole stage.

## Why the domain invariant is not touched

Relaxing `AmbiguityRecord`'s own requirement would weaken a real, already-reasoned control — exactly what the
owner's own instruction explicitly ruled out. The defect is not that the requirement is wrong; it is that the
prompt's own original wording placed `qualityChecksPerformed`'s requirement (a plain, unqualified "substantive
string") directly adjacent to `noClarificationReason`'s own conditional requirement ("REQUIRED when
resolutionState is NOT_MATERIAL, omitted otherwise"), with no explicit statement that the two fields are never
mutually substitutable — a live model reading that adjacency plausibly read them as a related pair where one
field's own substance could stand in for both.

## The fix

The prompt now states, at the point `qualityChecksPerformed` is first named in the schema description, "REQUIRED
ON EVERY ELEMENT, regardless of resolutionState, with NO exception" — not a single mention relied on alone. A
new paragraph explicitly distinguishes the two fields' own separate jobs (`qualityChecksPerformed` = proof a
check happened at all, for every element; `noClarificationReason` = the narrower, NOT_MATERIAL-only question
of why no fork exists), states plainly they are "never redundant with each other," and gives a worked example
showing both fields present, genuinely different in content, for the same `NOT_MATERIAL` element.

## RED-FIRST verification

New test (`promptStatesQualityChecksPerformedIsUnconditional`) proves the captured prompt contains the
unconditional requirement statement and the explicit non-redundancy warning. 19 tests total in
`AmbiguityDetectionAiExecutorTest`, all green. `ci.sh` green.

## Scope

**What changed**: prompt wording only.

**What did not change**: `AmbiguityRecord`'s own constructor guards; `AmbiguityDetectionAiExecutor`'s own
parsing/validation logic; the produced artifact's own shape.

## Conditions attached to the approval

1. **The domain invariant is not relaxed** — honoured; `AmbiguityRecord.java` is untouched by this CR.
2. **Tested** — honoured, see above.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If a third live occurrence surfaces despite this fix, that is real evidence the prompt-level fix alone is insufficient and the question becomes genuinely open again (relax vs. strengthen further vs. accept as a residual risk) — report honestly, do not patch reactively a third time without stepping back | Whenever S3 next dispatches live |
