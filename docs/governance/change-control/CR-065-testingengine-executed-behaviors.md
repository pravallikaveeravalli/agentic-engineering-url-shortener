# CR-065 — `TestingEngine`'s own `"test-results"` artifact gains a real `executedBehaviors` array, derived
from the real suite's own per-test records; the summary string is kept alongside, not replaced

| Field | Value |
|---|---|
| **Change request** | CR-065 |
| **Title** | `TestSuiteReport` gains an `executedBehaviors` field; `ScriptTestSuiteRunner` derives it from the real Surefire XML reports' own `<testcase>` records; `TestingEngine` emits a JSON artifact (summary + behaviors) instead of a bare summary string |
| **Raised by** | Owner's own direction, this session (2026-09-22): "Make `TestingEngine` emit the executed behaviors as the structured array `DocumentationAiExecutor` consumes: each executed test IS an executed behavior — derive `executedBehaviors` from the real JUnit test names/results" |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — mechanical wiring fix, one obviously-correct answer, implemented under the standing delegation** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — closes a real producer/consumer shape gap (attempt 22's own finding); does not touch T149's own drift-check guard, does not weaken any control, does not ask S5/S6 to invent a behaviour taxonomy |
| **Artifacts changed** | `src/main/java/agentic/shortener/orchestration/executor/deterministic/TestSuiteReport.java`, `src/main/java/agentic/shortener/orchestration/conductor/ScriptTestSuiteRunner.java`, `src/main/java/agentic/shortener/orchestration/executor/deterministic/TestingEngine.java` |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/executor/deterministic/TestPorts.java`, `src/test/java/agentic/shortener/orchestration/executor/deterministic/EngineBehaviourTest.java` (both updated to compile against and assert the new shape) |

---

## Which of attempt 22's own four disclosed options this is

Option 1 from `docs/evidence/ds-a/attempt-22-s9-executedbehaviors-finding.md`: real test names, derived from
data the real suite already produces (Surefire's own XML reports, already read for pass/fail counts — this
CR reads the same directory's `.xml` siblings for the individual `<testcase>` records those `.txt` summaries
never carried). Deliberately NOT option 2 (S5/S6 naming behaviours ahead of time — over-scoped, spans three
stages) and NOT option 3 (relaxing the drift-check guard — would weaken a real control, explicitly ruled out).

## The change

`TestSuiteReport` gains a fourth component, `List<String> executedBehaviors` — each entry
`"<fully-qualified-class-name>.<test-method-name>"`, taken directly from Surefire's own `<testcase
name="..." classname="...">` elements. `ScriptTestSuiteRunner.summarize()` now also walks each `.xml` report
file (previously only `.txt` files were read, for the aggregate count) and collects every `<testcase>`
element's own name/classname pair — a real test only appears once the class report shows zero failures for
it, and since `TestingEngine` never reaches the succeeded path unless `report.failed() == 0`, every entry in
this list genuinely passed. `TestingEngine.execute()` now serializes a small JSON object —
`{"total": N, "failed": N, "report": "<the existing summary string, verbatim>", "executedBehaviors":
[...]}` — as the `"test-results"` artifact, keeping the summary string available under its own key for any
other consumer, not renaming or removing it.

## Why this preserves T149's drift check, not weakens it

`DocumentationAiExecutor`'s own guard is unchanged: it still requires `executedBehaviors` non-empty, still
cross-checks the model's own `behaviorsDescribed` claims against it, still refuses any behaviour named that
has no corresponding entry. What changes is only that the list is now genuinely populated with real,
individually-verified test identifiers instead of never being populated at all — the guard becomes usable
for the first time under real, live conditions, not loosened.

## Scope

**What changed**: `TestSuiteReport`'s own shape (additive field); `ScriptTestSuiteRunner`'s own summarize
logic (additive read); `TestingEngine`'s own produced artifact content (string → JSON object carrying that
same string).

**What did not change**: `TestSuiteRunner`'s own interface signature; `TestingEngine`'s own `INPUT_KEY`/
`OUTPUT_KEY`; `DocumentationAiExecutor`'s own contract (it already expected exactly this shape — confirmed by
reading its own existing test fixture, which already used `executedBehaviors`/`total`/`failed` before this
CR).

## Conditions attached to the approval

1. **No weakening of T149's drift-check guard** — honoured, `DocumentationAiExecutor` itself is untouched.
2. **No over-scoped requirement on S5/S6** — honoured, no other stage's own contract changed.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If a future stage similarly reveals a first-time producer/consumer shape gap, prefer the same discipline: the minimal, mechanically-derivable fix, governed by a CR, over a guard relaxation or an over-scoped upstream requirement | Whenever the next stage runs for real for the first time |
