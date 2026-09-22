# CR-059 — Greenfield requirement text replaced with the owner's own simpler, self-contained, new-files-only
wording; S4 clarification matcher fixed to never auto-answer a SEMANTIC_CONTRADICTION

| Field | Value |
|---|---|
| **Change request** | CR-059 |
| **Title** | `DsALiveRun`'s own `REQUIREMENT` constant replaced (owner's own wording, this session); `applyOwnerClarificationAndResume`'s matching logic fixed to never route a `SEMANTIC_CONTRADICTION` to a pre-written routine answer |
| **Raised by** | Owner's own direction, this session (2026-09-22): fully-specified requirement text supplied verbatim, plus "if the stale-clarification-matching bug interferes, that's a real bug — fix the matching to use THIS run's own finding, don't let a stale prior answer misapply" |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — requirement text replaced verbatim as supplied; matcher fixed** |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** for the requirement text (same endpoint contract as CR-054/056/058, no auth/behaviour change); **MEDIUM** for the matcher fix (a real governance-adjacent bug: a fabricated, contradictory answer was previously capable of being recorded as attributed to the human owner) |
| **Artifacts changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`REQUIREMENT` constant, `applyOwnerClarificationAndResume`) |
| **Non-approved files changed** | none |

---

## The requirement text

Supplied verbatim by the owner this turn, replacing CR-056/058's own iterative attempts at the same
new-files-only goal: explicitly accepts a static, honestly-disclosed placeholder version value (removing
attempt 19's own "release version vs. SNAPSHOT" wording trap entirely, rather than patching around it again),
explicitly excludes the OpenAPI contract edit (a documented follow-up, not a blocker), and keeps the endpoint's
own contract (path, method, no-auth, response shape) identical to CR-054's own original wording.

## The matcher fix (docs/evidence/ds-a/s4-clarification-matching-stale-answer-finding.md)

Attempt 19 found, live: `applyOwnerClarificationAndResume`'s own keyword-substring matching routed a real,
new `SEMANTIC_CONTRADICTION` finding to `VERSION_VALUE_CLARIFICATION` — a routine, pre-written answer for a
*different* question — because the finding's own text happened to contain the word "literal". The applied
answer did not address the real question and directly contradicted the run's own requirement text, and it was
recorded as a `ClarificationDecision`/`GateDecision` attributed to the human owner without her actual review.

**Fix, principled rather than a one-off patch**: a `SEMANTIC_CONTRADICTION` is now never matched to any
pre-written routine answer, full stop — by definition it names an internal inconsistency in *that specific
run's own* requirement wording, which no answer written before that wording existed could have anticipated.
It always falls through to `unansweredFindings` and is reported, exactly like any other genuinely novel
finding. (The narrower, coincidental trigger — `"literal"` in the version-value branch's own OR-list — is also
removed, but the class-level exclusion is the real fix; removing one keyword would not have prevented the next
collision.)

## Scope

**What changed**: the requirement text (replaced, not appended-to, superseding CR-056/058's own text);
one structural addition to the matcher (a class-level exclusion, ahead of every keyword branch).

**What did not change**: `AmbiguityDetectionAiExecutor`'s own classifier; CR-057's own conditional-gate logic;
the endpoint's own approved contract.

## Conditions attached to the approval

1. **One attempt only, this turn** — the owner's own explicit instruction; honoured, see the live-run report.
2. **A `SEMANTIC_CONTRADICTION` is reported, never auto-resolved, going forward** — honoured, structurally
   (the exclusion is unconditional, not keyword-dependent).

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| CR-056/058's own retired requirement-text comments remain in `DsALiveRun.java` as historical trail (their own CR records are unedited, immutable) — the live `REQUIREMENT` constant itself is CR-059's, not theirs | N/A, informational |
