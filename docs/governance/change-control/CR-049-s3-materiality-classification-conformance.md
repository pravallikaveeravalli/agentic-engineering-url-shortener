# CR-049 — S3 (ambiguity detection) conforms to CR-007's materiality predicate

| Field | Value |
|---|---|
| **Change request** | CR-049 |
| **Title** | Add CR-007's materiality-classification predicate to `AmbiguityDetectionAiExecutor`'s prompt; fix a related JSON-null parsing gap found while verifying it |
| **Raised by** | Owner's own diagnosis of T132's two DS-A stops at S4 |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — conformance fix**, 2026-09-21. Owner's own framing: "a conformance fix to an approved definition, not a tuning to pass the demo" |
| **Decision date** | 2026-09-21 |
| **Classification** | **MODERATE** — behaviour change to an AI-capable stage's own classification output (S3, FR-ORC-010), governed by an already-approved definition (CR-007) it was not, until now, actually applying |
| **Artifacts changed** | `specs/001-agentic-sdlc-url-shortener/tasks.md` (a note against the T073b/T131 lineage, added below) |
| **Non-approved files changed** | `src/main/java/agentic/shortener/orchestration/executor/ai/stages/AmbiguityDetectionAiExecutor.java` (prompt + a parsing fix), `src/test/java/agentic/shortener/.../AmbiguityDetectionAiExecutorTest.java` (3 new tests), `AmbiguityDetectionMaterialityCompensatingCheck.java` (new, live verification), `docs/evidence/cr-049/ds-c-compensating-check.md` (new) |
| **Applied** | 2026-09-21 |

---

## Why this is conformance, not gaming — the risk stated first, per the owner's own instruction

`spec.md`'s §"What ambiguity detection is, and what it is not" warns explicitly: a detector "tuned to the
demo" — one that "would pass the demonstration and fail the next input" — is the rigged-demonstration
failure FR-ORC-028/CN-010 exist to prevent. **This change does not touch DS-A's specific findings at all.**
Verified directly by a dedicated test (`promptContainsNoDsASpecificVocabulary`) that fails if the prompt
ever contains "expiry", "expires", "rounding", "401", "creator", "owner", "secondsRemaining", or "link" —
none of DS-A's five attempt-3 findings are named, referenced, or exempted anywhere in the change. What was
added is CR-007's own materiality predicate, already approved and already binding on this exact
classification (CR-007's own "Affected consumers": "the ambiguity-detection stage (FR-ORC-010)... now [has]
a decidable predicate rather than a judgment call") — S3 was simply never given it to apply.

## Reason / verification performed

T132's two DS-A live attempts (original wording, then CR-048's fully-specified revision) both stopped at
S4, and reading `AmbiguityDetectionAiExecutor.buildPrompt()` directly confirmed why: it asks the model to
classify `resolutionState` as `MATERIAL_PENDING` or `NOT_MATERIAL` **without stating any criterion for the
choice anywhere in the prompt**. With no predicate to apply, the model has no basis to ever choose
`NOT_MATERIAL` except its own unguided impression — exactly the "judgment call" CR-007 replaced with a
decidable predicate for every OTHER consumer of the term, but never wired into this one.

**Verified before writing the fix, not assumed**: the two independently-trained models that both flagged
the DS-A wording (Claude, Gemini) prove *detection* is sound — real ambiguities were genuinely found, not
hallucinated. The problem is specifically the absent materiality FILTER downstream of detection.

## Scope

**What changed**: `buildPrompt()` now states CR-007's predicate verbatim in substance — an ambiguity is
`MATERIAL_PENDING` iff its resolution could alter an approved obligation or determines a required
behaviour, AND is not already fixed by an existing approved artifact; `NOT_MATERIAL` iff resolution is
affirmatively shown to alter nothing; **uncertain classifications default to `MATERIAL_PENDING`**, preserved
exactly, unweakened — this is the anti-loophole CR-007's own "Recorded case against" names, and RED-FIRST
tests assert both the predicate's presence AND the closing default's presence in the actual prompt string.

**A second, unrelated, real bug found and fixed while verifying live**: `toRecord()`'s own
`noClarificationReason` handling used `element.has(field)`, which is true for an explicit JSON `null` value,
not only for a present key — so a `MATERIAL_PENDING` record with `"noClarificationReason": null` (a
reasonable, common way for a model to represent "no value" explicitly rather than omitting the key) was
rejected as malformed, even though `AmbiguityRecord`'s own constructor correctly requires no reason for
anything but `NOT_MATERIAL`. Found live via the DS-C compensating check itself (Claude's real answer
included the explicit-null form), fixed by checking `!element.get(field).isNull()` instead of `has(field)`
alone, proven by a dedicated regression test using a fixed fixture (no live call needed to prove the parser
fix itself).

**What did not change**: the six structural ambiguity classes, the SEMANTIC_CONTRADICTION detection
capability itself, the always-non-empty-array guard, the substantive-`affectedPath`/`qualityChecksPerformed`
guards, the `RESOLVED`-is-refused guard — all unmodified, all still tested green.

## Per-finding reconciliation — DS-A's five attempt-3 findings, against real existing artifacts

Checked honestly against the actual codebase, per the owner's own instruction — not assumed, not forced to
an outcome:

| Finding | Already fixed by an existing approved artifact? | Verdict expected under the calibrated predicate |
|---|---|---|
| `expiresAt` value for an expired link unstated | **No** — the CR-048 requirement text itself never states this; `ExpiryPolicy` fixes the *boundary rule* (expired vs not) but not what the *response field* should contain once past it. This is a genuine gap in the requirement's own text | Genuinely open — MATERIAL if S3 still finds it |
| The exact instant `now == expiresAt` | **Yes** — `ExpiryPolicy`'s own existing rule ("at `expiresAt` the link is expired") already fixes this; the requirement text just never restates it | Should now classify NOT_MATERIAL, citing `ExpiryPolicy` |
| No stated rounding rule for `secondsRemaining` | **Partially** — no SC/PVT target pins an exact rounding rule; "integer >= 0" is satisfied by floor, round, or ceil equally for any conformant implementation, so no obligation is altered by the choice | Should now classify NOT_MATERIAL, citing that every conformant choice satisfies the stated obligation equally |
| 401 response is a cross-reference, not inlined | **Yes** — `CreatorAuthFilter`'s existing, already-delivered 401 response IS the shape every authenticated endpoint uses; the reference resolves to a real, existing artifact, it is just not restated inline | Should now classify NOT_MATERIAL, citing the existing filter |
| Creator-vs-owner predicate asymmetry | **Yes** — the domain has no concept of ownership transfer; `ShortLink` ties one creator identity to a link permanently, so "owns" and "is the creator of" are the same predicate in the actual system today | Should now classify NOT_MATERIAL, citing `ShortLink`'s own domain model |

**This agent did not invent any of these five answers.** Four of five are facts already true of the
delivered system (`ExpiryPolicy`, `CreatorAuthFilter`, `ShortLink`, and the stated `PVT`/SC` obligations'
own silence on rounding) — CR-049 does not decide anything; it lets S3 discover what is already decided. The
first (the expired-link `expiresAt` value) is genuinely open and undecided by any existing artifact — see
the DS-A re-run's own outcome for how S3 actually classifies it.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | RED-FIRST: two new tests naming the materiality predicate, before the prompt stated it | genuine assertion-failure red (the vocabulary-absence test passed trivially, as expected, since nothing about materiality existed yet either) | **EXECUTED — HOLDS**, `docs/evidence/red-phase/20260922T004037Z-CR049-s3-materiality-classification-missing.txt` |
| 2 | Prompt updated with CR-007's predicate, stated generally, zero DS-A vocabulary | both new tests green; 13/13 (later 14/14) total | **EXECUTED — HOLDS** |
| 3 | DS-C compensating check written and run LIVE, real `claude-sonnet-5` call | initially failed on an unrelated real bug (the JSON-null parsing gap) | **EXECUTED — caught a genuine defect** |
| 4 | Null-parsing bug fixed, RED-FIRST (fixture regression test) | fixture test green; 14/14 total | **EXECUTED — HOLDS** |
| 5 | DS-C compensating check re-run live | **PASS** — 7 findings, all `MATERIAL_PENDING`, zero `NOT_MATERIAL` | **EXECUTED — HOLDS**, `docs/evidence/cr-049/ds-c-compensating-check.md` |
| 6 | Fast + integration suites, `scripts/ci.sh` | green | pending this CR's own commit |
| 7 | DS-A (T132) re-run live against the calibrated S3 | see the companion evidence for this run | pending / see run evidence |

## Conditions attached to the approval

1. **Zero DS-A-specific vocabulary in the predicate** — the owner's own explicit, load-bearing condition.
   Honoured and proven by a dedicated, always-run test, not merely a claim.
2. **The uncertain-default MUST be preserved exactly** — honoured; the prompt states it in capitals as a
   non-optional instruction, and a dedicated test asserts the word "uncertain" is present in the real prompt
   text.
3. **Compensating verification before any DS-A re-run** — honoured; the DS-C check ran and passed before
   this CR's own commit, let alone before touching T132 again.
4. **Per-finding reconciliation done honestly, not forced** — honoured; the table above cites the specific
   existing artifact for four of five findings and states plainly that the fifth is genuinely open.

## Cross-record orphan check

- CR-007 itself is unaffected — this CR applies its existing predicate to a consumer that was not yet
  applying it; it does not redefine or re-decide materiality.
- No other applied record claims S3 already conformed to CR-007 — checked directly: nothing in `tasks.md`,
  `plan.md`, or any prior CR asserted this before now.
- T131/T132's own design and evidence documents (`docs/evidence/ds-a/design.md`,
  `pending-gate-s4-context.md`) remain accurate as filed — both attempts genuinely reached S4 under the
  PRE-calibration S3; nothing about this CR retroactively changes what those runs actually did.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If DS-A's re-run still finds the genuinely-open `expiresAt`-for-expired-links gap material, that is a real, distinct S4 clarification for the owner — not evidence the calibration is wrong | T132's own re-run, immediately following this CR |
| If any future stage's prompt asks for a materiality-shaped classification without stating CR-007's predicate, the same gap this CR fixed could recur there too | Whenever such a prompt is next written or audited |
