# Finding — S7's "blind diff" design cannot reliably modify existing files

**Status: OPEN. Real, decisive, twice-confirmed. Requires an owner decision on how S7 should proceed —
not something this agent should redesign unilaterally under time pressure.**

## What happened

T132's own implementation attempt (`GET /v1/version`, CR-054) reached S7 for the first time this project has
ever dispatched it live against real, non-fixture AI output (`docs/evidence/ds-a/
run-snapshot-ATTEMPT-14-BLOCKED-S7-corrupt-patch.md`, `...-ATTEMPT-15-BLOCKED-S7-corrupt-patch.md`). Both
attempts failed identically: `git apply` refused the model's own diff with `error: corrupt patch at line N`
(line 41 in attempt 14, line 16 in attempt 15 — different line numbers, same failure class). No branch was
left behind either time (`GitWorktreeBranchApplier`'s own design: a patch that never applies removes the
branch it would have held — confirmed, `git branch --list "ds-run/*"` empty after both).

## Root cause, confirmed by reading the real diffs

`ImplementationAiExecutor`'s own prompt is explicit: *"Do not use any tools. Do not read or write any
files."* The model authors a unified diff **blind** — with no access to the actual, current content of any
file it is asked to modify. For a **wholly new file**, this is structurally safe: `--- /dev/null` / `+++
b/newfile` with `@@ -0,0 +1,N @@` requires no knowledge of anything that already exists, and both attempts'
`VersionController.java` hunk was well-formed both times (the `git apply` failure was never attributed to
that hunk).

For an **existing file**, the model has no way to know real line numbers or surrounding context — and it
predictably fabricates plausible-looking but structurally wrong hunk headers. Both real attempts show this
directly:

- Attempt 14/15's `pom.xml` hunk: `@@ -1,7 +1,12 @@` — claiming the `spring-boot-maven-plugin` block starts
  at line 1 of `pom.xml`. It does not; `pom.xml` opens with an XML declaration and `<project>` element, and
  the plugin block is well inside the file.
- Attempt 15's `openapi.yaml` hunks: **`@@ -1,5 +1,26 @@` used twice**, for two different, unrelated hunks
  (the new path entry, and the new `Version` schema) — the model reused a guessed placeholder header rather
  than two real, distinct line references.

This is not stochastic bad luck on either attempt — it is the predictable consequence of asking a model to
produce byte-accurate line-numbered context for content it has never been shown, twice, independently, with
the same signature failure both times.

## Why this was never caught before

T131c (`GitWorktreeBranchApplier`, `ScriptTestSuiteRunner`) and T073e (`ImplementationAiExecutor`) were both
built and unit/fixture-tested — real git mechanics, real build/test invocation, real diff *parsing* — but
**no live run in this codebase had ever reached S7 with real AI output before this attempt.** Every prior
DS-A/DS-C live attempt stopped at S4 or S6. Fixture-based tests supply a hand-written, always-valid diff, so
they could never surface this: the gap is specifically in what a *real* model does when asked to blindly
diff an *existing* file, which no fixture reproduces by construction.

## What this does NOT mean

- **Not a defect in `GitWorktreeBranchApplier` or `ScriptTestSuiteRunner`.** Both behaved exactly as
  designed: a bad patch is refused, the branch it would have held is cleaned up, nothing corrupt is ever
  committed to a real branch.
- **Not a defect in `ImplementationAiExecutor`'s own refusal-of-malformed-output logic.** It correctly
  classified this as `INVALID_INPUT`/permanent (per its own "conflict = permanent" contract) rather than
  retrying the identical broken patch indefinitely — the governed pipeline caught a bad artifact and stopped,
  exactly as designed.
- **Not evidence against DS-A's own "genuine work, not theatre" demonstration.** S1 through S6 all ran for
  real, including two real owner-driven clarification/approval cycles; S7 dispatching a real, live AI call
  through a real git worktree and correctly refusing a corrupt result **is** the governed pipeline working —
  it is simply working correctly in a way that surfaces a real limitation in one stage's own prompt design.

## What this DOES mean — a real design question for the owner

`ImplementationAiExecutor`'s "author blind, no tool use" design (T073e) is reliable for tasks that only
create new files, and is **not reliably able to correctly diff an existing file** without being shown that
file's real content. Most real feature work touches at least one existing file (here: `pom.xml`, the OpenAPI
contract) — so this is a real, load-bearing gap in S7's own current design, not an edge case.

**Not decided here — genuinely the owner's own call**, options disclosed, not recommended:

1. **Give the model real file context**: read the target files' current content and include it in S7's own
   prompt (a real, disclosed architecture change — S7's own "no tool use" guard exists for a reason worth
   checking before altering: `ImplementationAiExecutor`'s own javadoc cites a real T073e finding that letting
   an agentic CLI use tools caused it to enter an uncompletable tool-use loop headlessly. Giving it *read*
   content inline, without letting it *use tools itself*, may avoid that specific failure while still fixing
   this one — but that needs verifying, not assuming.)
2. **Constrain S7's own scope to new-file-only changes**, and handle existing-file edits through a different,
   more targeted mechanism (e.g., a smaller, more structured instruction format than a raw unified diff).
3. **Accept the current design and retry policy as-is**, understanding that any task touching an existing
   file has a real, non-trivial failure rate, and rely on `RetryPolicy`'s bound plus a human noticing and
   re-triggering — though `INVALID_INPUT`/"conflict = permanent" specifically means the SAME task is never
   automatically retried today, so this would need its own design change too.
4. Some other decision.

## What was NOT done

- No third live attempt was made — two independent, identical-class failures is decisive evidence of a
  structural pattern, not something a third roll of the dice was likely to fix, and this session's own
  standing discipline is not to loop chasing a lucky outcome.
- No redesign of `ImplementationAiExecutor` was attempted — this is a real architecture decision, not a bug
  fix, and belongs with the owner.
- The run's own final state (`SAFE_STOP`, S7.1 `FAILED`) was not forced past or hidden.
