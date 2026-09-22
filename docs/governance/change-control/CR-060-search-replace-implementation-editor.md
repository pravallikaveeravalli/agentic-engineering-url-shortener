# CR-060 — S7's implementation stage authors a JSON change set (CREATE / search-replace EDIT), never a
unified diff; `GitWorktreeBranchApplier` applies it deterministically

| Field | Value |
|---|---|
| **Change request** | CR-060 |
| **Title** | Replace `ImplementationAiExecutor`'s unified-diff output format with a JSON change set (per-file CREATE full-content, or EDIT search/replace pairs); `GitWorktreeBranchApplier` applies it deterministically instead of `git apply` on a fabricated patch |
| **Raised by** | Owner's own root-cause diagnosis, this session (2026-09-22): "`ImplementationAiExecutor` asks the model for a UNIFIED DIFF... which LLMs botch even when given the file content... Real coding agents don't do this — they use search/replace or full-file edits applied deterministically. Owner approved that fix ('B')." |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — implemented and RED-FIRST-verified this turn. No live greenfield run attempted this turn (explicitly deferred to next turn, after a sanity check, per the owner's own instruction).** |
| **Decision date** | 2026-09-22 |
| **Classification** | **MEDIUM** — changes S7's own AI-provider output contract and its applier's own mechanism; does not change `StageInput`'s closed field list, `BranchApplier`'s own method signature (only its parameter's semantic content and name), or any approved architecture beyond what CR-055 already established (orchestration does I/O via pre-fetched content; executor stays pure) |
| **Artifacts changed** | `src/main/java/agentic/shortener/orchestration/executor/ai/stages/ImplementationAiExecutor.java`, `src/main/java/agentic/shortener/orchestration/executor/ai/stages/BranchApplier.java`, `src/main/java/agentic/shortener/orchestration/conductor/GitWorktreeBranchApplier.java`, `src/main/java/agentic/shortener/orchestration/conductor/RepoExistingFileReader.java` (doc-only) |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/executor/ai/stages/ImplementationAiExecutorTest.java`, `src/test/java/agentic/shortener/orchestration/conductor/GitWorktreeBranchApplierTest.java` (both rewritten for the new format) |

---

## Root cause (established, not re-litigated here)

Four real, live attempts (docs/evidence/ds-a's own attempts 14, 15, 17, 19) all failed the same way: `git
apply: corrupt patch`, even after CR-055 gave the model the real, current content of every existing file it
needed to edit. A unified diff still requires the model to invent exact line numbers and correctly-counted
`@@` hunk headers for content it can only read, never interactively edit — a format mismatch between what an
LLM is reliably good at (finding and replacing a snippet it can see) and what `git apply` requires (precise,
line-accurate patch syntax). This is not a prompting problem CR-055 could fully close; it is a format problem.

## The fix

**`ImplementationAiExecutor`** now asks the model for JSON: `{"files": [{"path", "action": "CREATE",
"content"} | {"path", "action": "EDIT", "edits": [{"search", "replace"}, ...]}]}`. `extractChangeSet`
(replacing `extractPatch`) validates the JSON's own SHAPE eagerly — non-JSON, a missing `files` array, an
unrecognized `action`, a CREATE missing `content`, or an EDIT missing `edits`/`search`/`replace` all fail as
`INTERNAL`/permanent ("could not even be understood"), exactly the same category boundary the unified-diff
version used for "not recognizably a patch." The executor still performs no file I/O of its own, still never
executes AI output as text, and still receives existing-file content only through CR-055's own
`INPUT_EXISTING_FILES_KEY` injection — nothing about the executor's own purity changed.

**`GitWorktreeBranchApplier`** now applies the change set directly in the isolated worktree: a `CREATE`
writes its content verbatim (refusing if the target path already exists — never a silent overwrite); an
`EDIT` reads the target file's REAL current content fresh from the worktree (never assumed from what the
model was shown), and requires each `search` string to occur **exactly once** before replacing it — zero
occurrences or more than one both fail the whole change set loudly, before anything is committed. A path
resolving outside the worktree is refused (the same guard `RepoExistingFileReader` already applies for
reads — a change set is AI-authored content, effectively untrusted input to this writer). Failure semantics
are unchanged: an apply failure leaves no branch behind; an applied-but-non-compiling change keeps its branch
for inspection; success keeps the branch and compiles cleanly.

## Governance findings, verified rather than assumed

1. **No ADR governs the diff/patch mechanism.** Searched every `docs/governance/adr/*.md` for "unified
   diff", "git apply", "BranchApplier" — no hits. This CR is the correct and sufficient governance record;
   nothing needed amending.
2. **`StageInput`'s own closed field list is unaffected** — the change set flows through the same
   `inputArtifacts` map / provider-response content path the unified diff already used; no new top-level
   field, no `StageExecutorContractTest` change needed (confirmed by reading it, not assumed — same finding
   CR-055 and CR-057 already established for their own changes).
3. **`BranchApplier`'s own method signature is unchanged** (`apply(String taskId, String changeSet)`) — only
   the parameter's name (`patch` → `changeSet`, for clarity, source-compatible) and what the string now
   contains. No caller outside this package needed a signature-level change.

## RED-FIRST verification — the key result

`GitWorktreeBranchApplierTest` (real `git` subprocesses against a fixture repo, `@TempDir`, never the real
project repository):

- **Regression**: a clean `CREATE` still succeeds, branch created, exact committed content verified.
- **THE KEY TEST** (`editingTwoExistingFilesAppliesCorrectlyAndCompiles`): a single change set EDITs a
  `pom.xml`-shaped existing file (adding a `<plugin>`/`<executions>` block, the exact real-world edit that
  corrupt-patched in production) AND an existing `.java` file, in one call, through the real
  `GitWorktreeBranchApplier`. The build check is real `javac` — genuine compilation, not a trivial `true`.
  **Result: PASSED.** The committed `pom.xml` content is asserted byte-exact against the expected result (no
  corruption, no fabricated content); the committed `.java` content is asserted to contain the real
  replacement and NOT the original text; `javac` genuinely compiles it.
- Negative coverage, all real and passing: a search string that does not match (refused, no branch left); a
  search string matching more than once (refused as ambiguous, names the real occurrence count); `CREATE` on
  an already-existing file (refused); `EDIT` on a missing file (refused); a path escaping the worktree
  (refused); a non-JSON answer (refused, no branch left); a change that applies but fails the build check
  (branch kept, per the existing disclosed policy).

`ImplementationAiExecutorTest`: updated for the new JSON shape throughout (`VALID_CHANGE_SET` replaces
`VALID_DIFF`), plus new negative coverage for an unrecognized `action` and a malformed `EDIT` entry; the
existing-file-content-in-prompt regression tests updated for the new prompt wording, still passing.

All 24 tests across both files green (`./scripts/build.sh -Dtest=ImplementationAiExecutorTest,
GitWorktreeBranchApplierTest test`), `ci.sh` green.

## Scope

**What changed**: S7's own AI output contract and applier mechanism.

**What did not change**: S7's own three-collaborator split (AI authors / engine applies / real suite
verifies, S8's own job); the "no tool use" prompt constraint and its own documented rationale (the Gemini
tool-use-loop finding); `INVALID_INPUT`/"conflict = permanent" semantics for a change that does not apply or
does not build.

## Conditions attached to the approval

1. **FIX-ONLY this turn — no live greenfield run** — honoured; see the report for confirmation.
2. **RED-FIRST proof that existing-file editing (pom.xml AND a .java file) genuinely works, including a real
   compile** — honoured, see the key test above.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| The live sanity check + full greenfield run against this new mechanism | Next turn, per the owner's own explicit instruction |
| If the live model still produces a malformed change set (wrong JSON shape, or a search string that doesn't match), that is real, disclosed evidence about THIS fix's own real-world reliability — report honestly, do not force past | The next turn's live attempt |
