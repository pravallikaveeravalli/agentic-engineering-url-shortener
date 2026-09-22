# Finding — `DsALiveRun`'s own S4 clarification-matching logic misapplied a stale, contradictory answer to a
genuinely new finding

**Status: OPEN, RECURRED ONCE AFTER A PARTIAL FIX. Real, live, found during PART 4's greenfield rerun (attempt
19, 2026-09-22); CR-059 closed the specific class that caused attempt 19 (never auto-match a
`SEMANTIC_CONTRADICTION`), but attempt 20 (same day, next turn) found a SECOND, structurally identical
instance the narrower fix did not cover — see the addendum below. Not fixed further here — it concerns the
trustworthiness of the routine-clarification delegation's own matching mechanism, which this agent should not
unilaterally redesign a second time under time pressure any more than the first.**

## What happened

Attempt 19 (`docs/evidence/ds-a/run-snapshot-ATTEMPT-19-BLOCKED-S7-corrupt-patch-stale-clarification-
misapplied.md`) re-ran greenfield with CR-058's own genuinely-unambiguous requirement text. S3's real, live
output found a real, NEW `SEMANTIC_CONTRADICTION`: the requirement's own phrase "a fixed literal string
matching this project's current release version" contradicts `pom.xml`'s actual `<version>0.1.0-SNAPSHOT`,
since "SNAPSHOT" is Maven's own term of art for a pre-release build — the semantic opposite of "release
version." A real, correct, well-caught finding — CR-058's own wording had this genuine defect.

`DsALiveRun.applyOwnerClarificationAndResume`'s own keyword-matching logic (`affectedPath.toLowerCase()`
substring checks) matched this NEW finding's own text against `lower.contains("literal")` — the same branch
written for a **different, earlier** finding ("what is the exact source of the 'version' field's value").
That branch applied the standing, pre-written `VERSION_VALUE_CLARIFICATION` answer: *"The application's own
build version string, sourced from build metadata (e.g. the project version) — never a hardcoded arbitrary
literal..."* — an answer that (a) does not address the real question asked (the SNAPSHOT/release-version
contradiction) and (b) **directly contradicts CR-058's own requirement text**, which explicitly mandates a
hardcoded literal and explicitly prohibits build-info/build-metadata sourcing.

This stale, mismatched answer was then recorded as a real `ClarificationDecision` and a real, materialized
`GateDecision` — attributed to the human owner — and fed downstream. S6's own real design reasoning then
explicitly chose to bind `spring-boot-maven-plugin`'s `build-info` goal (the exact mechanism CR-058's own text
prohibited), evidently because the applied "clarification" told it to source from build metadata. This
directly caused another existing-file (`pom.xml`) edit at S7, which corrupt-patched again (`error: corrupt
patch at line 21`) — the same failure class as attempts 14/15/17, but this time **caused by a governance bug
in the delegation's own matching logic, not by S7's own diff-generation limitation.**

## Why this was left unresolved rather than fixed live

This is not a routine clarification-answer gap (the kind the standing delegation exists to cover, and which
this driver's own safety valve already handles correctly by failing loudly). It is a defect in the **matching
mechanism itself** — a keyword-substring check too coarse to distinguish "the version's sourcing mechanism"
from "the version's wording is internally contradictory," which caused a **fabricated, contradictory answer**
to be recorded and attributed to the human owner. `ActorAuthority` (FR-ORC-021) requires every recorded
decision's content to genuinely be the human's own; this instance's content was not reviewed by her before
being recorded (it was an automated substring match), and it turned out to be wrong. Fixing the matching logic
is itself a real design decision (tighten the keyword rules? require exact-question matching only? something
else?) that deserves the owner's own judgment, not a fast unilateral patch layered on top of an already very
large turn — the same discipline this session has applied to every other genuinely novel decision.

## What was NOT done

- No third live attempt was made this turn — per the owner's own "no retry-loop" standing discipline, and
  because a third attempt against the SAME matching logic would risk the identical mismatch again.
- The stale/wrong `GateDecision` and `ClarificationDecision` recorded during attempt 19 were NOT corrected or
  edited in place (immutability discipline) — they stand as real, disclosed evidence of the defect, in a run
  that was allowed to fail honestly (`SAFE_STOP`) rather than forced past.
- `DsALiveRun`'s own matching logic was NOT redesigned here.

## Disclosed options for the owner (not decided here)

1. Tighten the matching predicate (e.g. require the ambiguity's own `ambiguityClass` to match the expected
   class too, not just a keyword in `affectedPath`) — reduces false-positive matches like this one, at the
   cost of more findings falling through to "genuinely novel, reported."
2. Require near-exact question matching (e.g. semantic similarity against the standing question text) rather
   than keyword substrings — stronger guarantee, more implementation work.
3. Retire the keyword-matching mechanism for anything beyond the ORIGINAL, already-validated question set, and
   route every S4 finding on a materially reworded requirement back through fresh owner review — simplest,
   most conservative, costs the delegation's own convenience.
4. Some other decision.

## Addendum (2026-09-22, next turn) — the SAME class of bug recurred once, CR-059's fix was too narrow

CR-059 closed the specific trigger that caused attempt 19 (never auto-match a `SEMANTIC_CONTRADICTION`), on
the reasoning that a contradiction is always run-specific. That reasoning was correct for that finding, but
the underlying fragility — a plain `String.contains(...)` substring check, not a whole-word match — is more
general than the one class excluded. Attempt 20
(`docs/evidence/ds-a/run-snapshot-ATTEMPT-20-STOPPED-AT-S4-two-novel-findings-plus-resource-source-collision.md`)
found it again, differently: a real `UNDEFINED_TERM` finding about what "honestly-disclosed" means (a
documentation/format question) was auto-matched to `VERSION_VALUE_CLARIFICATION` because its own text
contains the word **"resource"**, which itself contains **"source"** as a literal substring
(`re` + `source` = re**source**) — the version-value branch's own `lower.contains("source")` check fired on a
word that has nothing to do with version-sourcing at all. The applied answer was wrong here too, and in this
instance directly self-contradictory with the run's own requirement text (the stock answer says "never a
hardcoded arbitrary literal"; this run's own requirement explicitly permits exactly that).

**This attempt's run still stopped correctly overall** — two OTHER genuinely novel findings in the same batch
were correctly left unanswered, failing the test loudly as designed, so no incorrect answer reached S5/S6.
But the mismatched third answer was still recorded as a real `ClarificationDecision`/`GateDecision` in that
run's own history, attributed to the human owner without her actual review — the same governance concern as
attempt 19, structurally, just not the one CR-059 closed.

**Not fixed a second time under time pressure, on the same principle as before**: a substring-matching
mechanism appears to have more of these collisions than any one turn can enumerate and patch reactively
(`"literal"`, now `"resource"`⊃`"source"` — English is full of such containments). Option 1 above
(class-matching, not just keyword-matching) or option 2 (near-exact question matching) both look more
likely to close the whole family at once than another one-off exclusion; option 3 (retire keyword-matching
for anything beyond the original validated set) is the most conservative and simplest to reason about. This
is now two real, live occurrences of the same underlying fragility — worth the owner's own decision on which
of the four options to take, rather than a third reactive patch.
