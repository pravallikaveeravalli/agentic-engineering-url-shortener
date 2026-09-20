# Change Request CR-008 — AI Transport Wording in Plan and Quickstart

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20 |
| Approving authority | Pravallika Veeravalli (human owner) |
| Raised by | Executing agent, implementing the owner's transport decision |
| Affected approved artifacts | `specs/001-agentic-sdlc-url-shortener/plan.md` (APPROVED 2026-09-20); `specs/001-agentic-sdlc-url-shortener/quickstart.md` (derived, approved via the plan) |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decision | `docs/governance/adr/ADR-004-amendment-01-transport-claude-code-cli.md` |

**Numbering note, corrected.** This record was first drafted as CR-010, on the reasoning that CR-008 and CR-009
were reserved by `tasks.md` T031 (Slice-1 contract baseline) and T151 (final traceability population), which
hard-coded those identifiers. That reasoning was wrong in a way worth recording: **`tasks.md` is uncommitted and
its review is still pending**, so a reservation living only in an unapproved artifact has no authority over the
committed registry — which runs CR-001..CR-007 with no gap. Leaving CR-008 and CR-009 empty on that basis would
have put an unexplained hole in the registry, justified by a file a reviewer cannot yet see.

Corrected to **CR-008**. `tasks.md` T031 and T151 were amended in the same change to reference "the next available
CR identifier" rather than hard-coding numbers, which removes the collision at its source: a task plan should not
pre-allocate identifiers in a registry that other work is still appending to.

## The decision requiring the change

ADR-004 Amendment 01: the `StageAiProvider` transport is a subprocess invocation of the locally installed,
already-authenticated **Claude Code CLI** in headless mode, with the model pinned in configuration and the
**actually-used model id read from the response JSON** and recorded in run evidence.

Owner's reason, verbatim:

> I already have a Claude subscription; I do not have API credits and do not want to buy them when this
> alternative exists. The provider interface exists precisely so this choice is a swap behind one seam.

## Applied edits

| # | Location | Was | Now |
|---|---|---|---|
| 1 | `plan.md` Technical Context, AI-provider row | "Anthropic Claude API behind an interface" | Claude reached through an owned interface, **transport is the local Claude Code CLI in headless mode** (ADR-004-A1); SDK adapter recorded as the production alternative |
| 2 | `plan.md` §12 ADR-004 **Approach** | "Anthropic Claude API" as option (a) | Option (a) restated as "Anthropic Claude behind an owned interface", with transport named as a **sub-decision** settled by ADR-004-A1 rather than by ADR-004 |
| 3 | `plan.md` §12 ADR-004 **Consequences** | "an API key and cost for demonstration runs only" | "an authenticated Claude Code CLI on the machine for demonstration runs only; the SDK path would instead need an API key and per-call cost" |
| 4 | `quickstart.md` prerequisites table | "**No AI API key** — Deliberate…" | Prerequisite restated as **not required at all** for anything graded, with the AI-mode requirement named as *either* an authenticated CLI *or* an API key if the SDK adapter is built |
| 5 | `quickstart.md` §"With `ai: on`" heading and body | "one flag plus your own API key" | Reviewer-facing wording per the owner: AI mode requires **either an authenticated Claude Code CLI on the machine, or an API key if the SDK adapter is built; entirely optional, never required for anything graded** |

## Assessed as needing no change

- **`spec.md`** — every key mention is *"no AI key present"* or *"no AI key and no network"* (FR-ORC-029,
  NFR-AUT-004, SC-014, CN-011). Those remain **true** under this transport, because neither implementation uses
  an API key in the keyless default path, and the specification names no transport anywhere. **No spec edit was
  made.**
- **`contracts/`** — no wire-contract surface describes the AI transport.
- **ADR-004** — Accepted and **not edited in place**, per the owner's instruction. The amendment is a successor
  record.
- **CN-011, SC-014, NFR-AUT-004** — untouched; the keyless reviewer default is unaffected.

## Impact analysis

**Version impact**: **PATCH** on both artifacts. No obligation changes. The transport was never an approved
obligation — ADR-004 fixed the provider and the interface boundary, and explicitly assessed provider choice as
High reversibility. What changes is a description of how that boundary is filled.

**Backward-compatibility impact**: none. No consumer, no interface change.

**Affected consumers**: internal only — the `StageAiProvider` adapter (T073) and the reviewer reading
`quickstart.md`.

**Affected tests**: T073 gains the argv-not-shell assertion, the model-id-from-response assertion, the
CLI-unavailable fallback assertion, and the malformed-output classification assertion. SC-014 is unchanged and
must still pass.

**Affected documentation**: the two files edited here, plus `tasks.md` T073 (unapproved, edited directly) and
`docs/LIMITATIONS.md` at T147, which must disclose the shared-quota consequence.

**Rollout / migration**: none. No code exists yet.

## Residual risk

**One genuine new risk, raised by the executing agent and recorded in the amendment**: the stage prompt is
untrusted content originating in submitted requirements, and it is now passed to a subprocess. The adapter MUST
use an **argv array, never a shell string** — a `/bin/sh -c` invocation with an interpolated prompt would be a
remote-code-execution path from a requirement field. Recorded as a mitigation with a mandatory test rather than
left as an implementation assumption.

Otherwise low. The shared-subscription-quota consequence is accepted and disclosed; CLI absence degrades to the
deterministic counterpart rather than failing a run.
