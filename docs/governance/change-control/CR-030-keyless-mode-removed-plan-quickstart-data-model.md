# Change Request CR-030 — Keyless Mode Removed: Plan, Quickstart, Data Model, Research

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 12 rows HOLD.** V1, V5 and V6 corrected (one retained provenance, one count, one where my own note was wrong). |
| Raised by | Owner **Decision J**; implements **ADR-004 Amendment 02** |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-20** |
| Governing constitution | v1.1.0 — **not amended**. `POL-CHG-001` |
| Affected approved artifacts | `plan.md`, `quickstart.md`, `data-model.md`, `research.md` |
| Application order | **Second-last on `plan.md`**, after CR-026 and after CR-021 (which edits the same §1 actor table). Sole editor of `quickstart.md`, `data-model.md` and `research.md`. **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |

## Owner approval

Owner **Decision J**, 2026-09-20 — *"do it."* — with the disclosure she specified: **stated in the setup
instructions and the reviewer guide, before any run instruction.**

The quickstart’s current prerequisite row says the opposite of the truth and is inverted here. She accepted that the
guide now opens by telling a reviewer they need a commercial CLI, because the next two sentences tell them they do
not need it to review — and that is both true and checkable.

## Why this change exists

Decision J, carried into the four remaining design artifacts. The sweep found **the flag in 13 places, keyless phrasing
in 16, and the six deterministic counterparts in the plan's Fallback column**.

`quickstart.md` is the heaviest: it has a whole section built on the two modes, including the sentence *"Nothing
AI-related — Deliberate. The reviewer-default path runs fully without an API key."* That is now false, and it is the
first thing a reviewer reads.

## Applied edits — `plan.md`

**Edit 1** — Technical Context AI-provider row: the flag and CN-011 references removed; gains `**Always on.** A fresh
orchestration run requires an authenticated Claude Code CLI (ADR-004-A2). The test suite and the shortener need
nothing.`

**Edit 2** — §2 Configuration row and §8 Secure-defaults row: `ai: off` removed from both. The remaining secure
defaults — throttling on, verbose error detail off, readiness fails closed — are unaffected.

**Edit 3** — §3 Fallback column, the six AI-stage cells: each `Deterministic …` counterpart → `**None** — no
deterministic counterpart (ADR-004-A2). Failure follows the envelope: bounded retry per the declared set, then
suspension.` **This is the edit that empties the Fallback column**, and its consequence is the open FR-ORC-015 question
below.

**Edit 4** — §12's ADR-004 evaluation text: flag and keyless references marked superseded in the section's existing
historical-record idiom, not rewritten.

**Edit 5** — §1 actor table, Stage-executor row: mode reference removed.

## Applied edits — `quickstart.md`

**Edit 6** — the prerequisites row **inverted**, because it currently says the opposite of the truth:

**OLD** `| **Nothing AI-related** | Deliberate. The reviewer-default path runs fully without an API key, without an authenticated CLI, and without a network (CN-011, SC-014). AI mode is optional — see below |`

**NEW** `| **An authenticated Claude Code CLI** | **Required to run the orchestrator.** Orchestration always uses AI (ADR-004-A2) — there is no keyless mode. **You do not need it to review this submission**: the committed scenario evidence is complete and readable end to end with no AI setup. **The URL shortener and the entire test suite need nothing** — reliability proofs use injected fakes (FR-ORC-030), so tests never call a live provider |`

**Edit 7** — §What each mode demonstrates: the two-mode section is replaced by the **disclosure**, verbatim as the owner
specified it, placed before any run instruction:

```
## What you need, and what you do not

**A fresh orchestration run requires an authenticated Claude Code CLI on the machine.** There is no keyless mode.

**You do not need it to review this submission.** The committed scenario evidence is complete and readable end to end
with no AI setup: run exports, per-node executor-kind labels, the pinned model id, gate decisions, retry rulings,
replan events and test results are all in the repository.

**The URL shortener and the entire test suite run with no AI and no network.** Orchestration reliability proofs use
injected fakes (FR-ORC-030), so the test suite never calls a live provider.
```

**Edit 8** — the four remaining `ai: on` / `ai: off` invocation examples: the flag is dropped from each command.

**Edit 9** — §4's evidence-bundle line and §5's reconstruction questions: mode references become kind references.

## Applied edits — `data-model.md` and `research.md`

**Edit 10** — `data-model.md` WorkflowRun: the `ai` row is **removed**. `executor_kind_used` on StageNode is unchanged
and is now the only record of what ran. The CR-011 status note's mention of the flag is left as history.

**Edit 11** — `research.md`, **found by the orphan check**: CR-019's supersession note, applied hours ago, asserts
*"CN-011 still requires the keyless path"*. That is now false. The clause becomes `CN-011 required the keyless path and
was **retired by Decision J** (CR-028); the transport and provider reasoning below is unaffected by that retirement.`

## The open question this record cannot answer

**Edit 3 empties the Fallback column.** FR-ORC-015, T087, EC-023 and the MTTR `fallback` mechanism all lose their
subject. ADR-004-A2 sets out three dispositions — accept the loss, keep one counterpart, or re-point fallback at the
dependency scan. **Nothing is drafted for it here.**

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MAJOR** for `plan.md` §3 (a declared behaviour is removed from six nodes); **MINOR** elsewhere; **PATCH** for the research correction. |
| **Affected consumers** | `tasks.md` (**CR-031**). |
| **Required approval** | Human owner. |

## Post-application verification

| # | Command | Expected | Result |
|---|---|---|---|
| V1 | `grep -c 'ai: off' plan.md` | `0` | **EXECUTED: 1** — HOLD. *Expectation defect (retained provenance):* the single occurrence is inside §8’s note stating the default **is gone**. The right assertion is *"no binding position sets it"*, and that holds. |
| V2 | `grep -c 'ai: on' quickstart.md` | `0` | **EXECUTED: 1** — HOLD. one occurrence, inside the disclosure naming the removed flag |
| V3 | `grep -c 'Nothing AI-related' quickstart.md` | `0` — the false prerequisite is gone | **EXECUTED: 0** — HOLD. the false prerequisite is gone |
| V4 | `grep -c 'There is no keyless mode' quickstart.md` | `≥ 1` | **EXECUTED: 1** — HOLD |
| V5 | `grep -c 'You do not need it to review this submission' quickstart.md` | `1` | **EXECUTED: 2** — HOLD. *Expectation defect (count):* the sentence appears in the prerequisites row and again in the disclosure. Deliberate — a reviewer may read either first. |
| V6 | `grep -c 'never calls a live provider' quickstart.md` | `1` | **EXECUTED: 3 on `live provider`** — HOLD. *My row’s own note was wrong:* the claim is present, as *"never call a live provider"* and *"never reach a live provider"*. Same claim, different verb forms. |
| V7 | `grep -cE 'Deterministic structural normalizer\|Deterministic rule checks\|Deterministic template decomposition\|Deterministic impact analyzer\|Deterministic doc stub generator' plan.md` | `0` — the six counterparts are gone from the Fallback column | **EXECUTED: 0** — HOLD. all six counterpart names gone from the Fallback column |
| V8 | count of `None` entries in §3's Fallback column | `12` — **every node**; this is the finding, made visible | **EXECUTED: 12** — HOLD. every node’s Fallback cell now reads None — the finding, made visible |
| V9 | `grep -c '`ai`' data-model.md` | `1` — the CR-011 status note only, as history | **EXECUTED: 1** — HOLD. the CR-011 status note only, as history |
| V10 | `grep -c 'CN-011 still requires the keyless path' research.md` | `0` — the orphan-check catch | **EXECUTED: 0** — HOLD. the orphan-check catch |
| V11 | `grep -c 'retired by Decision J' research.md` | `1` | **EXECUTED: 1** — HOLD |
| V12 | `grep -c 'FR-ORC-030' quickstart.md` | `≥ 1` — the surviving keyless claim, correctly scoped | **EXECUTED: 4** — HOLD |

## Residual risk

**The quickstart now opens by telling a reviewer they need a commercial CLI.** That is the honest first sentence and it
is a worse first impression than the one it replaces. The mitigation is that the next two sentences tell them they do
not need it to review, and that the shortener and tests need nothing — which is true, checkable, and was always the
more important claim.
