# Change Request CR-019 — Phase-0 Research: AI-Provider Section Aligned to the CLI Transport and the `ai` Flag

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, from `/speckit-analyze` finding **M6** — raised in the first analysis, **dropped from the package when the edit buckets were re-sorted**, and found still open by the post-application re-run |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"fix the leftovers and let us move things forward here."* |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/research.md` — Phase-0 output, approved indirectly via `plan.md`'s approval at the Gate 4 closing package |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decision | Owner authority, 2026-09-20, scoped to the four residual findings |
| Application order | Second of the three residual records. Independent of CR-018 and CR-020. |

## Why this change exists

`research.md`'s AI-provider section still reads:

> **Decision**: Anthropic Claude **API** behind a provider interface; latest available Claude model chosen at
> implementation time; **deterministic mode default** — *recommended.*

Two things in that sentence are no longer true:

1. **The transport is not the API.** ADR-004 Amendment 01 (CR-008) settled it as a **subprocess invocation of the
   locally installed Claude Code CLI in headless mode**, with the SDK adapter recorded as the production alternative
   behind the same interface. The owner's ground was explicit: *"I already have a Claude subscription; I do not have
   API credits and do not want to buy them when this alternative exists."*
2. **"Deterministic mode" is the vocabulary CR-001 abolished**, on the owner's reasoning that a run-level label must
   not claim a property the run cannot guarantee — a keyless run can still contain a `HUMAN` execution at the
   stage-7 no-plan gate. The flag is `ai: on | off`, default `off`.

**Why this was missed, stated plainly.** I raised it as M6 in the first analysis and placed it in the "hygiene"
bucket. When the owner's Decision message re-sorted which items needed change control, M6 fell between the two
lists and no record picked it up. That is my omission, not a decision anyone made — and it is exactly the class of
gap the per-record verification tables catch inside a record but cannot catch *between* records.

**The inconsistency that makes it a real finding rather than a typo**: this same file carries **supersession notes on
its three deferred-findings sections** — DF-001 marked *"SUPERSEDED… the position below was wrong"*, DF-002 and DF-003
marked resolved with their original text retained. The AI-provider section got no such note, so a reader sees a
document that carefully flags its own stale positions in one place and silently carries one in another.

## Applied edits

### Edit 1 — §AI provider (ADR-004) aligned, with a supersession note in the file's own idiom

**OLD**

```
### AI provider (ADR-004)

- **Decision**: Anthropic Claude API behind a provider interface; latest available Claude model chosen
  at implementation time; deterministic mode default — *recommended.*
- **Rationale**: CL-003 requires real AI where work is creative, while CN-011 requires the
  reviewer-default path to run with no key. An interface satisfies both and keeps providers swappable.
- **Alternatives considered**: OpenAI (equivalent for this purpose; no differentiator); local model
  (removes the key requirement but adds setup burden for a reviewer and weakens authoring quality);
  deterministic-only (rejected by CL-003 as making the "agentic" claim hollow).
```

**NEW**

```
### AI provider (ADR-004) — **TRANSPORT AND FLAG SUPERSEDED 2026-09-20; the provider choice stands**

**Superseded in two respects** by ADR-004 as accepted at Gate 4 and by its Amendment 01 (CR-008):

1. **Transport.** The implemented adapter is a **subprocess invocation of the locally installed, authenticated
   Claude Code CLI in headless mode**, not an API call. The owner's ground, recorded at ADR-004-A1: *"I already have
   a Claude subscription; I do not have API credits and do not want to buy them when this alternative exists. The
   provider interface exists precisely so this choice is a swap behind one seam."* The **Anthropic API/SDK adapter is
   the recorded production alternative behind the same interface**, built only if time permits.
2. **The flag.** "Deterministic mode default" is the vocabulary **CR-001 abolished**. The run-level flag is
   **`ai: on | off`, default `off`**, named for the one thing it controls — whether AI executors participate — because
   a keyless run can still contain a `HUMAN` execution at the stage-7 no-plan gate, so a run-level determinism label
   would over-promise.

**What stands unchanged**: the provider choice itself — Anthropic Claude, reached through **one interface owned by this
codebase** — and the reason it is behind an interface. ADR-004 assessed the provider choice as **High reversibility**
for exactly this reason, and Amendment 01 is that assessment being proved rather than a departure from it.

Retained verbatim below for provenance, in the same idiom this file uses for its superseded deferred-findings
positions:

> - **Decision**: Anthropic Claude API behind a provider interface; latest available Claude model chosen
>   at implementation time; deterministic mode default — *recommended.*
> - **Rationale**: CL-003 requires real AI where work is creative, while CN-011 requires the
>   reviewer-default path to run with no key. An interface satisfies both and keeps providers swappable.
> - **Alternatives considered**: OpenAI (equivalent for this purpose; no differentiator); local model
>   (removes the key requirement but adds setup burden for a reviewer and weakens authoring quality);
>   deterministic-only (rejected by CL-003 as making the "agentic" claim hollow).

The **Rationale** and **Alternatives** above remain sound and were not superseded — CL-003 still requires real AI
where the work is creative, CN-011 still requires the keyless path, and the three rejected alternatives were rejected
on grounds the transport change does not touch. Only the transport and the flag name moved.
```

*Ground for retaining the original as a quotation rather than rewriting in place: this file's own convention. Its
DF-001 section keeps a position that turned out to be **wrong** on the express reasoning that *"a superseded position
that was wrong is more instructive than one that was merely provisional."* The same convention applied here costs
nothing and keeps the document internally consistent.*

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **PATCH.** No obligation changes. The decisions of record are ADR-004 and its amendment; this brings the Phase-0 evaluation into agreement with them and adds the supersession note the file's own convention calls for. |
| **Backward-compatibility impact** | None. |
| **Affected consumers** | None. No task, contract or plan section cites this section — which is why it survived three passes of change control unnoticed. |
| **Affected tests** | None. |
| **Affected documentation** | This section only. `plan.md` §12 and `quickstart.md` already carry the corrected transport wording (CR-008). |
| **Rollout / migration** | None. |
| **Required approval** | Human owner — a Phase-0 deliverable approved with the plan. Granted 2026-09-20 within the four-residual scope. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

| # | Edit | Verification command (from repo root, `R=specs/001-agentic-sdlc-url-shortener/research.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'TRANSPORT AND FLAG SUPERSEDED' $R` | `1` | **EXECUTED: 1** — HOLDS |
| V2 | 1 | `grep -c 'Claude Code CLI in headless mode' $R` | `1` | **EXECUTED: 1** — HOLDS |
| V3 | 1 | `grep -c 'ai: on | off' $R` | `1` | **EXECUTED: 1** — HOLDS |
| V4 | 1 | `grep -c 'I do not have API credits' $R` | `1` — the owner's ground recorded verbatim | **EXECUTED: 1** — HOLDS |
| V5 | 1 | `grep -c 'Anthropic Claude API behind a provider interface' $R` | `1` — **deliberately retained**, inside the quoted provenance block only. Asserted **by reading**, because this is the string the edit supersedes and a count alone cannot tell a quotation from a live claim | EXECUTED: 1 hit, inside the retained quotation block only. **HOLDS** |
| V6 | 1 | `grep -ci 'deterministic mode default' $R` — **case-insensitive** | `2` — the lowercase original inside the quoted block, and the capitalised reference in the supersession text that names it as the abolished vocabulary. A case-sensitive count returns 1 and misses the second | EXECUTED (case-insensitive): 2 hits — the lowercase original in the quotation, and the capitalised reference naming it as the abolished vocabulary. **HOLDS** |
| V7 | 1 | `grep -c 'Rationale\*\* and \*\*Alternatives\*\* above remain sound' $R` | `1` — the edit states precisely what was **not** superseded | **EXECUTED: 1** — HOLDS |
| V8 | regression | `grep -c 'SUPERSEDED 2026-09-20; the position below was wrong' $R` | `1` — the DF-001 note this edit's idiom follows is untouched | **EXECUTED: 1** — HOLDS |


**Check correction recorded, 2026-09-20.** V6 was case-sensitive and returned **1** where the file legitimately holds
**two** occurrences: the lowercase original inside the retained quotation, and the capitalised *"Deterministic mode
default"* in the supersession text that names it as the vocabulary CR-001 abolished. Both are correct and both must
stay. **The edit was right; the expected value was wrong** — the same case-sensitivity slip already recorded once in
CR-015, which is worth noting because it means the lesson had not yet become a habit.

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V5 and V6 are deliberately read-required.** They are the two strings the edit supersedes, and they must
still appear — inside the quotation. A grep expecting zero would have been the wrong assertion, which is the lesson
CR-010, CR-011, CR-012 and CR-016 each recorded the hard way.
