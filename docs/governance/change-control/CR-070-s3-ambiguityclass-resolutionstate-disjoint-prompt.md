# CR-070 — S3's prompt now states `ambiguityClass` and `resolutionState` are disjoint vocabularies, naming
the real confusion a live DS-B attempt produced

| Field | Value |
|---|---|
| **Change request** | CR-070 |
| **Title** | `AmbiguityDetectionAiExecutor`'s prompt explicitly forbids `ambiguityClass` ever being a `resolutionState` value |
| **Raised by** | This session's own investigation, this turn: DS-B attempt 3 of 3 (capped) — the real, live S3 model output used `"NOT_MATERIAL"` (a `ResolutionState` value) as the value of `ambiguityClass` on two of eight elements |
| **Decided by** | Pravallika Veeravalli (standing delegation: a mechanical, obviously-correct prompt hardening mirroring CR-067's own precedent, no design ambiguity) |
| **Decision** | **APPROVED under the standing delegation** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — prompt hardening only; the existing validation (`AmbiguityClass.valueOf` throwing on an unrecognized value) already correctly refused the malformed output and is unchanged |
| **Artifacts changed** | `src/main/java/agentic/shortener/orchestration/executor/ai/stages/AmbiguityDetectionAiExecutor.java` |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/executor/ai/stages/AmbiguityDetectionAiExecutorTest.java` |

---

## Confirmed empirically, not assumed

A real, live DS-B attempt this turn (`docs/evidence/ds-b/attempts-1-3-finding.md`, attempt 3 of 3 capped)
produced a real S3 answer containing:

```json
{"ambiguityClass": "NOT_MATERIAL", "affectedPath": "...", "resolutionState": "NOT_MATERIAL", ...}
```

`ambiguityClass` has no `NOT_MATERIAL` constant (`AmbiguityClass` is `MISSING_ACCEPTANCE_CRITERIA |
UNDEFINED_TERM | UNBOUNDED_QUANTIFIER | MISSING_ACTOR | SELF_REFERENTIAL_CONSTRAINT | CONTRADICTORY_BOUNDS |
SEMANTIC_CONTRADICTION`) — `NOT_MATERIAL` belongs only to `ResolutionState`. This is the model confusing two
disjoint enum vocabularies on the same JSON element, occurring on two of eight elements in the same real
answer.

The existing code already refused this correctly: `AmbiguityDetectionAiExecutor.toRecord`'s own
`AmbiguityClass.valueOf(...)` throws `IllegalArgumentException`, caught and translated to a permanent
`MalformedProviderOutputException` (T073's own "malformed output is INTERNAL and permanent, never retried"
contract) — no incorrect state was ever recorded. The gap this CR closes is prompt clarity, not validation
correctness.

## The fix

`buildPrompt` now adds one explicit paragraph, after the schema, naming the exact confusion observed live and
stating both fields draw from disjoint vocabularies, with `ambiguityClass` explicitly forbidden from ever
being `MATERIAL_PENDING` or `NOT_MATERIAL`. The schema's own enumeration of the seven valid `ambiguityClass`
values (already present) is unchanged; this adds a negative constraint on top of it, mirroring CR-067's own
established style — add a "never confuse X with Y" clause once a real confusion is observed live, rather than
assume the positive enumeration alone is sufficient.

## RED-FIRST verification

New test `promptStatesAmbiguityClassAndResolutionStateAreDisjoint` captures the prompt via a provider lambda
and asserts it states the two fields are disjoint and explicitly forbids the real confusion observed. 14
tests in `AmbiguityDetectionAiExecutorTest`, all green. `ci.sh` green.

## Scope

**What changed**: one additional prompt paragraph. **What did not change**: `toRecord`'s own validation
(already correct), `AmbiguityClass`/`ResolutionState`'s own enum definitions, `AmbiguityRecord`'s own
constructor invariants.

## Conditions attached to the approval

1. **Empirically confirmed, not assumed** — honoured; see the real, live attempt-3 output quoted above.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| This fix is NOT yet re-verified against a further live DS-B attempt — this turn's own capped-attempts discipline was already exhausted (3 of 3) before this defect was found and fixed | The next live DS-B attempt |
