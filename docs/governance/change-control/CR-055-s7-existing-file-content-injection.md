# CR-055 — S7 reads existing files through the orchestration, not the executor: `Conductor` pre-fetches
content the design names, `ImplementationAiExecutor` stays a pure function over its input

| Field | Value |
|---|---|
| **Change request** | CR-055 |
| **Title** | Fix S7's inability to correctly diff existing files, by having `Conductor` pre-fetch the content S6's design names and inject it into `StageInput`, preserving `ImplementationAiExecutor`'s "no tool use" purity |
| **Raised by** | Owner's own direction, this session (2026-09-22): "Fix S7's existing-file limitation the clean way... the orchestration reads the existing files the change targets and injects their current content into the `StageInput`... keeping the executor a pure function over its input (no direct file I/O in the executor)" |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — fix implemented, RED-FIRST-verified, `ci.sh` green.** Conversational instruction, not a formal gate decision; no `docs/governance/gate-decisions/` record is produced (CLAUDE.md's own trigger rule — this is an engineering fix under standing delegation, not a gate) |
| **Decision date** | 2026-09-22 |
| **Classification** | **MEDIUM** — production behavioural change to `Conductor`'s own S7 dispatch path and to `ImplementationAiExecutor`'s prompt; additive to `StageInput`'s existing open `inputArtifacts` map (no change to `StageInput`'s own closed field list or `StageExecutorContractTest`); no schema, API, or approved-architecture change |
| **Artifacts changed** | `src/main/java/agentic/shortener/orchestration/conductor/ExistingFileReader.java` (new), `src/main/java/agentic/shortener/orchestration/conductor/RepoExistingFileReader.java` (new), `src/main/java/agentic/shortener/orchestration/conductor/Conductor.java`, `src/main/java/agentic/shortener/orchestration/executor/ai/stages/DesignAiExecutor.java`, `src/main/java/agentic/shortener/orchestration/executor/ai/stages/ImplementationAiExecutor.java`, `src/main/java/agentic/shortener/config/OrchestrationConfiguration.java` |
| **Non-approved files changed** | test call-sites updated only to compile against the new `Conductor` constructor parameter: `DsALiveRun.java`, `DsCClarificationRun.java`, `DsCLiveRun.java`, `ArtifactAliasingIT.java`, `ConductorIT.java`, `RunSubmissionControllerTest.java`; new test files `RepoExistingFileReaderTest.java`, `ExistingFileInjectionIT.java`, plus additions to `DesignAiExecutorTest.java` and `ImplementationAiExecutorTest.java` |

---

## The defect this fixes

Documented in `docs/evidence/ds-a/s7-existing-file-diff-finding.md`: `ImplementationAiExecutor`'s prompt
deliberately says "Do not use any tools. Do not read or write any files" (a real, live-verified finding from
T073e's own work against the Gemini CLI adapter, whose agentic tool-use loop could not complete headless).
With zero access to real file content, the model authors existing-file diff hunks blind — two independent,
real live attempts (attempts 14 and 15 against DS-A's own greenfield run) both produced `git apply: corrupt
patch` for `pom.xml`/`openapi.yaml` hunks with fabricated line numbers, while new-file hunks were well-formed
both times. `GitWorktreeBranchApplier` correctly refused both — the governance working as designed, not a
demonstration failure — but the limitation blocks any real edit to existing code, including brownfield's own
T136a (`RedirectController`).

## The fix: the orchestration reads, the executor stays pure

**`ExistingFileReader`** (new, `orchestration.conductor`, functional interface) — `Map<String,String>
read(List<String> relativePaths)`; a missing key means "does not exist yet" (new file), never an error.
**`RepoExistingFileReader`** (new) — the real implementation, reading from `repoRoot`, silently omitting
non-existent paths, and refusing any path that resolves outside `repoRoot` (a design output is AI-authored
content — effectively untrusted input to this reader).

**`DesignAiExecutor`** (S6) now additionally asks for, and passes through verbatim, `"existingFilesToModify":
[string, ...]` — repository-relative paths of files the design requires *editing*, not creating. An absent or
empty list (the greenfield case) round-trips as an empty array, never a missing field, so `Conductor` always
has a list to read.

**`Conductor.dispatchExecutor`**, for stage 7 only, calls a new private `withExistingFileContent(artifacts)`:
parses the accumulated `"design"` artifact, reads `existingFilesToModify`, calls `existingFileReader.read(...)`,
and — only if there is anything to add — merges the result as a new `"existingFiles"` JSON-object-string key
(`path -> exact content`) into a locally-copied artifacts map used for that one dispatch's `StageInput`. This
is computed fresh per dispatch from data already in `artifacts`; it is never written back to the
run-accumulated/persisted artifact map, because it is dispatch-scoped context (what S7 needs to see), not a
stage-produced artifact for downstream stages to consume.

**`ImplementationAiExecutor`** gains one new, strictly *optional* input key, `INPUT_EXISTING_FILES_KEY =
"existingFiles"` (unlike `INPUT_TASK_KEY`/`INPUT_DESIGN_KEY`, which remain required). When present and
non-blank, `buildPrompt` prepends a block showing the exact content and instructing the model that hunks
against it must be byte-accurate; when absent, the prompt is byte-for-byte what it always was. **The executor
still performs no file I/O of its own** — every byte it sees arrives through `StageInput`, exactly as the
directive required; only `Conductor` (the orchestration) ever touches the filesystem for this purpose.

## Governance findings, verified rather than assumed

1. **`StageInput`'s own closed field list, and `StageExecutorContractTest`, need no change.** The new content
   flows through `inputArtifacts()`, the same open `Map<String,String>` every other stage already uses for its
   own distinctly-keyed content (`"requirements"`, `"tasks"`, `"design"`, `"ambiguities"`, ...). No new
   top-level `StageInput` field was added, so the contract test's `inputIsWorkflowDataOnly()` assertion is
   unaffected — confirmed by reading the test, not assumed.
2. **No ADR governs S7's prior "no tool use" choice**, so none required amendment. Searched every ADR
   (`docs/governance/adr/`) for "tool", "file read", and S7-specific language: ADR-004 ("AI Provider and
   Autonomy Bounding") discusses S7's role and autonomy limits generally but never states "no file reads" as
   an architectural decision; that constraint exists only as a prompt-level, empirically-verified finding
   documented in `ImplementationAiExecutor`'s own javadoc (the Gemini tool-use-loop incident). This CR is
   therefore the correct and sufficient governance record for the change; it does not silently override any
   accepted ADR.

## RED-FIRST verification

New test coverage, run and confirmed green (`./scripts/build.sh test` for the fast tier, `-Dtest=
ExistingFileInjectionIT test` for the integration tier, then full `./scripts/ci.sh`):

- `RepoExistingFileReaderTest` — real file read by relative path, a missing path silently omitted (not an
  error), a path-traversal attempt refused, an empty request returns empty.
- `DesignAiExecutorTest` — `existingFilesToModify` round-trips verbatim; an omitted field still produces an
  empty array (never a missing key).
- `ImplementationAiExecutorTest` — existing-file content, when present, reaches the prompt byte-accurately
  with the "byte-accurate" instruction; **regression check**: with no `existingFiles` key at all (the exact
  shape every prior S7 dispatch used), the prompt carries no existing-file block whatsoever — new-file
  creation is provably unaffected.
- `ExistingFileInjectionIT` — the real end-to-end proof: a `Conductor` wired with a real `RepoExistingFileReader`
  against a `@TempDir`, a real on-disk `pom.xml`, and an S6 stub naming it under `existingFilesToModify`
  produces an S7 `StageInput` whose `"existingFiles"` key genuinely contains that file's real, current
  content; a companion test proves an empty `existingFilesToModify` (greenfield) leaves S7 with no
  `"existingFiles"` key at all.

## Scope

**What changed**: S7's dispatch path can now show the model real, current content for files a design names as
requiring modification; new-file-only changes are unaffected (verified by dedicated regression tests, not
merely assumed).

**What did not change**: `ImplementationAiExecutor` still performs no file I/O of its own, still refuses
anything that does not look like a diff, still treats a failed build as `INVALID_INPUT`/permanent. `StageInput`'s
own contract is unchanged.

## Conditions attached to the approval

None stated beyond the directive itself.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Re-run DS-A's live greenfield attempt with this fix in place, and report honestly whether S7 now produces a correct, applicable diff for the real `pom.xml`/OpenAPI edits, or whether the timebox's pre-authorized new-files-only fallback is used instead | Immediately following this CR, same session |
| If brownfield (T136a, which edits `RedirectController`, an existing file) is attempted, confirm this same fix is what unblocks it, rather than assuming it without a real dispatch | T136a's own future turn |
