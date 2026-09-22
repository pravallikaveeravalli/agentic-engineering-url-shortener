# CR-061 — `DsALiveRun`'s clarification matcher: whole-word matching, not substring, closing the family of
collisions attempts 19/20 found live

| Field | Value |
|---|---|
| **Change request** | CR-061 |
| **Title** | `applyOwnerClarificationAndResume`'s keyword matching replaced with whole-word (`\bword\b`) matching, closing the general substring-collision family rather than patching one more instance |
| **Raised by** | Owner's own direction, this session (2026-09-22): "Replace the fragile substring matching with near-exact question matching (or match on the finding's ambiguity class + a distinctive key), so an answer only applies to the finding it was actually written for" |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — implemented and tested this turn** |
| **Decision date** | 2026-09-22 |
| **Classification** | **MEDIUM** — governance-adjacent: this matcher decides whether a `GateDecision`/`ClarificationDecision` attributed to the human owner is recorded automatically; a wrong match fabricates content she did not actually review |
| **Artifacts changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`applyOwnerClarificationAndResume`, new `containsWord`/`containsAnyWord` helpers) |
| **Non-approved files changed** | new `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRunMatcherTest.java` |

---

## The two real, live incidents (docs/evidence/ds-a/s4-clarification-matching-stale-answer-finding.md)

- **Attempt 19**: a real `SEMANTIC_CONTRADICTION` (pom.xml's `SNAPSHOT` version contradicting the requirement
  text's own "release version" phrasing) was routed to `VERSION_VALUE_CLARIFICATION` because its own text
  contained the word "literal" — a keyword written for a completely different, earlier question.
- **Attempt 20**: a real `UNDEFINED_TERM` (what "honestly-disclosed" means) was routed the same way, because
  its own text contained "resource", and `String.contains("source")` matches inside "re**source**".

Both recorded a `ClarificationDecision`/`GateDecision` attributed to the human owner that she never actually
reviewed, and in both cases the applied answer was wrong for the real question asked.

## The fix

Every keyword check in `applyOwnerClarificationAndResume` that used plain `String.contains(...)` on a single
short or prefix-style word is now `containsWord`/`containsAnyWord` — whole-word matching via `\bword\b`
regex, never a raw substring. This closes the general family (any short English word that happens to be a
substring of a longer, unrelated word), not merely the two instances already found. Two deliberate
exceptions, left as substring checks: `"content-type"`, `"application/json"`, `"cache-control"`,
`"no-store"`, `"query string"`, `"path segment"` — hyphenated/slashed/multi-word compound tokens with no
realistic collision risk, where a word-boundary split would be awkward and adds no real safety.

Word-form lists were widened alongside the tightening, so legitimate matches this delegation exists to catch
are not lost to the stricter boundary: `"hardcod"` (a deliberate prefix) became the explicit set
`{"hardcoded", "hardcoding", "hardcode"}`; the version-source branch now also matches `"sourced"`/`"sourcing"`
and `"built"` alongside `"source"`/`"build"`; the access/auth branch now matches
`{"auth", "authentication", "authorization", "authenticated", "authorized", "unauthenticated",
"unauthorized"}` explicitly, rather than the bare substring `"auth"` (which also matches inside "**auth**or",
an unrelated word never intended to trigger this branch — the same class of risk, caught here proactively
rather than waiting for a third live incident).

`SEMANTIC_CONTRADICTION`'s own class-level exclusion (introduced last turn) is unchanged and unaffected by
this fix — it addresses a different, structural concern (no pre-written answer can ever be correct for a
finding about THIS run's own wording, regardless of matching precision) and remains in place independently.

## RED-FIRST verification

New `DsALiveRunMatcherTest` (6 tests, all real, no live AI call, no database) — `containsWord`/
`containsAnyWord` made package-private specifically so this test can call them directly:

- Attempt 20's real finding text: `containsWord(..., "source")` now returns `false` against "resource" —
  proves the actual historical bug is fixed.
- Regression: a genuine "what is the source of the version value" finding still matches `"source"` as a
  standalone word — proves the fix did not overcorrect into never matching anything.
- Regression: `"sourced from build metadata"` phrasing still matches via the widened word-form list.
- `"auth"` matches real authentication/authorization findings; `"author"` does NOT match `"auth"` as a word
  — proves the proactively-fixed collision is real and closed.
- An unrelated finding continues to match nothing (falls through to `unansweredFindings`, as designed).

All 6 green. Full fast-tier + `ci.sh` green (see the same run this turn's other CR covers).

## Scope

**What changed**: matching logic only, within one live-demo driver class.

**What did not change**: `ActorAuthority`, the delegation's own safety valve (an unmatched finding still
falls through to `unansweredFindings` and fails loudly), the six standing answers' own question/answer text.

## Conditions attached to the approval

1. **Closes the family, not the one instance** — honoured; the fix is structural (word-boundary matching
   applied uniformly, plus a proactive fix for the "auth"/"author" risk that had not yet caused a live
   incident but shares the same root cause).
2. **Tested** — honoured, see above.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| The next turn's live run is the real-world test of this fix under genuine model non-determinism — report honestly if a further collision surfaces, rather than patching reactively a fourth time without stepping back | Next turn's live attempt |
