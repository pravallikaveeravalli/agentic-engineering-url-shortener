# CR-052 — S3's materiality predicate sharpened to CR-007's actual bar; DS-A subject simplified

| Field | Value |
|---|---|
| **Change request** | CR-052 |
| **Title** | Sharpen `AmbiguityDetectionAiExecutor`'s materiality predicate to a genuine behavioural-fork test (not linguistic imperfection); remove the non-behavioural clause from DS-A's minimal subject that the over-fire traced to |
| **Raised by** | Owner's diagnosis of attempt 6's result: a trivial, auth-free, input-free, fixed-output endpoint drew 4 `MATERIAL_PENDING` findings, 3 behaviorally inert |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — decisive fix, last DS-A iteration.** "We fix the detector, not the requirement." Conversational instruction to sharpen the detector directly — **not a formal gate decision**; no `docs/governance/gate-decisions/` record is produced (CLAUDE.md's own trigger rule) |
| **Decision date** | 2026-09-22 |
| **Classification** | **MODERATE** — behaviour change to an AI-capable stage's own classification output (S3, FR-ORC-010), governed by CR-007's already-approved predicate, sharpening how that predicate is applied rather than redefining it |
| **Artifacts changed** | `docs/evidence/ds-a/design.md` (Current subject updated to CR-052's simplified wording), `docs/evidence/ds-a/requirement-completeness-ceiling-finding.md` (narrative corrected forward) |
| **Non-approved files changed** | `src/main/java/agentic/shortener/orchestration/executor/ai/stages/AmbiguityDetectionAiExecutor.java` (prompt sharpened + a second `noClarificationReason` parsing gap fixed), `AmbiguityDetectionAiExecutorTest.java` (5 new tests), `AmbiguityDetectionMaterialityCompensatingCheck.java` (re-run, unmodified), `docs/evidence/cr-052/ds-c-compensating-check.md` (new), `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`REQUIREMENT` constant only) |
| **Applied** | 2026-09-22 |

---

## Why this is sharpening a real over-fire, not re-tuning to pass a demo

Attempt 6's result (`docs/evidence/ds-a/pending-gate-s4-context-v1-version.md`) is the evidence: a
maximally minimal requirement — no auth, no inputs, a fixed output, nine short clauses stating the endpoint's
entire behaviour unconditionally — still drew four `MATERIAL_PENDING` findings. Three of them (an undefined
term, "reachable"; a self-referential critique of a closing clause's own scoping; an unbounded-quantifier
critique of that same clause's own phrasing) all traced to one closing, summarizing sentence, and none of the
three changed any actual buildable behaviour: deleting that sentence entirely would not change what a
conforming implementation must do, since the other eight clauses already pin the endpoint's complete
behaviour with no conditional logic anywhere. That is a textbook case of CR-007's own predicate — "does
resolving this change a behaviour the system MUST exhibit?" — being applied to *linguistic* rigor (is this
sentence internally well-formed?) rather than to *behavioural* rigor (does an implementation choice actually
fork here?). Confirmed by the fourth finding, which was different in kind: no stated behaviour for a
non-`GET` method or a path variant is a genuine, verified-open gap (no existing artifact resolves it for any
endpoint in this system), and it survives this CR unchanged — it is not addressed by sharpening the
predicate, only by (potentially) a future decision on its own merits.

## What changed

**The predicate itself** (`AmbiguityDetectionAiExecutor.buildPrompt()`), stated generally:

- `MATERIAL_PENDING` now requires a genuine fork with **buildable, observable consequences** — two
  conformant implementations that would actually behave differently in a way an approved obligation cares
  about — not merely a logical or definitional imperfection in the requirement's own prose.
- `NOT_MATERIAL` now explicitly names the class of thing that is **not** material even though it may be a
  real observation about the text: an undefined term whose ordinary meaning already suffices; a
  self-referential or purely summarizing clause whose deletion changes no required behaviour; an unbounded
  or unquantified phrasing no stated obligation actually constrains; a dimension already settled by a
  reasonable, uncontested default (e.g. a framework's own standard behaviour for an unaddressed case). Every
  such item must still be recorded, with a substantive reason — never dropped — but does not open a gate.
- The closing default is **preserved exactly, and its scope is clarified, not weakened**: "uncertain" means
  genuinely unsure which required behaviour applies once implemented — explicitly NOT "the wording could be
  phrased more precisely." Precision of wording alone, with no behavioural fork behind it, is stated as never
  material. The burden of proof still runs toward materiality for every genuine fork.

Verified generally, with zero DS-A-specific vocabulary, by the existing `promptContainsNoDsASpecificVocabulary`
test (unmodified, still passing) plus a new equivalent check
(`sharpenedPromptStillContainsNoDsASpecificVocabulary`) that additionally forbids "version," "reachable,"
"reachability," and "health" — the exact terms attempt 6's own findings used — proving the fix was not
authored by encoding attempt 6's specific answer into the prompt.

**A second, real, unrelated bug found live while re-running the compensating check**: the model returned an
empty string (`"noClarificationReason":""`) for a `MATERIAL_PENDING` record — a third variant of "no reason
given," beyond the omitted-key and explicit-JSON-null cases CR-049 already handled. `AmbiguityRecord`'s own
constructor already treats blank the same as null; the adapter's own parsing did not, and rejected the
response as malformed. Fixed by extending the existing blank-check
(`!reasonNode.asText().isBlank()`), proven by a fixture regression test needing no live call — see
`emptyStringNoClarificationReasonOnMaterialIsAccepted`, RED-phase capture at
`docs/evidence/red-phase/20260922T022710Z-CR052-blank-noClarificationReason-rejected.txt`.

**DS-A's minimal subject** (`docs/evidence/ds-a/design.md`): the closing "no failure mode by design" clause
that attempt 6's three inert findings traced to is removed. The requirement keeps every genuinely behavioural
dimension (method, path, auth=none, fixed body + its stated provenance, status, content-type, caching) and
states them as coherent prose, not a checklist.

## RED-FIRST, and the non-negotiable compensating control

1. RED: `promptStatesTheSharpenedBehavioralForkTest` and `promptScopesUncertainToBehaviouralDoubtNotWordingPrecision`
   captured genuinely failing before the prompt changed —
   `docs/evidence/red-phase/20260922T022152Z-CR052-materiality-sharpening-missing.txt` (2 failures, the
   vocabulary-absence test passed trivially, matching CR-049's own precedent since nothing existed to find
   yet either).
2. Prompt sharpened; all 20 `AmbiguityDetectionAiExecutorTest` cases green (17 pre-existing + 3 new
   materiality tests, one of which is the blank-string regression added after the live bug was found).
3. **Non-negotiable**: DS-C's canonical contradiction (`spec.md`'s own demonstration text, verbatim) re-run
   live against the sharpened predicate, three times across this turn (once immediately after sharpening,
   once after the blank-string bug surfaced and was fixed, once as final confirmation) — `MATERIAL_PENDING`
   fired on the core semantic contradiction **every single time**, alongside five to six further genuine
   `MATERIAL_PENDING` findings per run and one to two correctly reasoned `NOT_MATERIAL` resolutions per run.
   Full detail: `docs/evidence/cr-052/ds-c-compensating-check.md`. The sharpening did not blunt detection.
4. Full `scripts/ci.sh` green before this CR's own commit.

## The case against sharpening (recorded, per CR-007's own discipline) — and why it does not apply here

**Risk**: sharpening the predicate could let a real ambiguity through by classifying it `NOT_MATERIAL` when
it should not be — the exact rigged-demonstration failure FR-ORC-028/CN-010 exist to prevent.

**Answer**: the DS-C compensating check is the control against exactly this, run live, not asserted, three
times, with the core finding never once failing to fire. CR-007's own uncertain-default is preserved and its
scope clarified rather than loosened — "uncertain" still routes to `MATERIAL_PENDING`; only "the wording
could be more precise" is excluded from counting as uncertainty, which is a narrowing of what counts as
grounds for doubt, not a loosening of what happens when doubt exists. The sharpening only ever produced
`NOT_MATERIAL` for items this agent independently verified had no behavioural fork (the closing clause in
attempt 6, "indefinite" and "redirect analytics field scope" in the compensating check) — never for anything
resembling the genuine contradiction DS-C exists to catch.

## Update to `requirement-completeness-ceiling-finding.md`

That document is corrected forward, not edited to erase its prior claim — a new section states plainly that
part of what first looked like an irreducible completeness ceiling was, on the evidence of attempt 6, in
fact a correctable detector over-fire, now fixed by this CR. What remains true and unretracted: detection
itself is thorough (proven again by this CR's own compensating checks), and a text-only detector genuinely
cannot see the codebase (unchanged structural fact, orthogonal to materiality classification).

## Conditions attached to the approval

1. **Predicate stated generally, zero DS-A-specific vocabulary** — honoured, proven by a dedicated test that
   additionally forbids the exact terms attempt 6's findings used.
2. **Compensating check non-negotiable, run live before any DS-A re-run** — honoured, three real runs, core
   finding fired every time.
3. **This is the last DS-A iteration this turn** — honoured; if the sixth-overall/second-against-CR-052 live
   attempt still does not clear S3, this agent stops and reports the residual findings rather than
   attempting a further revision.
4. **No gate-decision record** — honoured.

## Cross-record orphan check

- CR-007 itself is unaffected — its predicate is applied more precisely, not redefined; the "genuine
  behavioural fork" framing is this CR's own elaboration of CR-007's "determines which of two behaviours the
  system MUST exhibit" clause, not a new rule.
- CR-049's own claims about the pre-sharpening predicate remain accurate as filed — it correctly reported
  what that version of the prompt did; this CR does not retroactively change that history, only supersedes
  the prompt going forward.
- CR-051 is not edited in place; its wording is kept in `design.md`, clearly labelled superseded, with the
  attempt-6 evidence that motivated its replacement cited directly.
- `requirement-completeness-ceiling-finding.md` is corrected forward with a new section, not rewritten;
  its prior claims are still readable and now contextualized rather than erased.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If the next DS-A live attempt still finds a genuinely material item after this sharpening and simplification, per the owner's own explicit instruction this agent stops and reports it — no further wording revision this turn | Immediately following this CR |
| If any future prompt sharpening is proposed, the DS-C compensating check must be re-run live as the non-negotiable control before trusting the change, exactly as this CR did | Whenever S3's prompt next changes |
