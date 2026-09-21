# Change Request CR-018 — Validation-Targets Preamble Restated to Match Its Own Table

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, from the post-application `/speckit-analyze` re-run — residual finding **2** (MEDIUM) |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"fix the leftovers and let us move things forward here."* |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/spec.md` §Validation Targets |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decision | Owner authority, 2026-09-20, scoped to the four residual findings |
| Application order | First of the three residual records. Independent of CR-019 and CR-020. |

## Why this change exists

CR-010 added a sixteenth validation target and CR-017 withdrew one as binding. **The preamble above the table was
not updated by either**, so it still reads *"All 15 values below… now constrain implementation and are acceptance
thresholds, no longer proposals."*

The table is correct. The sentence introducing it is not, and it contradicts the very row CR-017 rewrote — a reader
arriving at §Validation Targets is told fifteen values bind, then finds sixteen rows, one of which says plainly that
it does not bind. **This is the same defect class the whole remediation package existed to close**: a summary
statement left behind by the edits it summarises.

It is recorded as a MEDIUM rather than shrugged off because the preamble is where a reader forms their model of what
constrains implementation, and a wrong count there undermines the table even when the table is right.

## Applied edits

### Edit 1 — §Validation Targets preamble (`spec.md:1338-1340`)

**OLD**

```
**All 15 values below were approved by Pravallika Veeravalli at the Gate 4 closing package, 2026-09-20**
(`docs/governance/gate-decisions/gate-04-closing-package.md`, CR-005). They now **constrain implementation** and
are acceptance thresholds, no longer proposals.
```

**NEW**

```
**Sixteen targets, of which fifteen constrain implementation and one is retired.**

- **PVT-001..PVT-009 and PVT-011..PVT-015** — fourteen values approved by Pravallika Veeravalli at the Gate 4
  closing package, 2026-09-20 (`docs/governance/gate-decisions/gate-04-closing-package.md`, CR-005). They
  **constrain implementation** and are acceptance thresholds, no longer proposals. **PVT-009** was subsequently
  restated by **CR-010** — the same 0.5%, as an observable append-failure ceiling rather than a loss budget.
- **PVT-016** — added by **CR-010** (per-node escalation thresholds). Binding in the same sense: every node must
  have a threshold and breaching one must ask the human.
- **PVT-010** — **retired as a binding target by the owner's explicit decision, 2026-09-20 (CR-017)**, and replaced
  by a production recommendation. It is retained in the table as a recommendation, not deleted, so that a reader can
  see what production would do and that the demonstration deliberately does not do it. **It is not measured and not
  enforced.**

Fifteen binding, one retired, sixteen rows. Any row's binding status is stated in its own Conditions cell, so the
count above can be checked against the table rather than trusted.
```

*Ground for restating rather than patching the number: a count alone would have to be re-edited on every future
change to the set. Naming which targets bind, which was added, and which was retired makes the paragraph
self-checking against the table beneath it — which is the property its predecessor lacked.*

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **PATCH.** No obligation changes. The preamble is brought into agreement with a table that already carries the decisions; nothing binds or unbinds as a result of this edit. |
| **Backward-compatibility impact** | None. |
| **Affected consumers** | None. No task, contract or plan section cites the preamble. |
| **Affected tests** | None. |
| **Affected documentation** | This section only. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner — the section is approved text. Granted 2026-09-20 within the four-residual scope. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

Absence checks are scoped to **binding positions** per the lesson recorded across CR-010 through CR-017: this
project retains superseded text with attribution, so whole-file absence greps are the wrong assertion.

| # | Edit | Verification command (from repo root, `S=specs/001-agentic-sdlc-url-shortener/spec.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'All 15 values below were approved' $S` | `0` — the stale count is gone from the preamble | **EXECUTED: 0** — HOLDS |
| V2 | 1 | `grep -c 'Sixteen targets, of which fifteen constrain' $S` | `1` | **EXECUTED: 1** — HOLDS |
| V3 | 1 | `grep -c 'retired as a binding target' $S` | `1` | **EXECUTED: 1** — HOLDS |
| V4 | 1 | `grep -c 'Fifteen binding, one retired, sixteen rows' $S` | `1` | **EXECUTED: 1** — HOLDS |
| V5 | 1 | `grep -c 'all 15 approved and binding' $S` | `1` — **deliberately retained**: this is CR-005's own change-log entry, a historical record of what CR-005 did, and must not be rewritten | EXECUTED: 1 hit, inside CR-005's own change-log entry — a historical record of what CR-005 did. **Correctly retained. HOLDS** |
| V6 | invariant | count of `PVT-\d{3}` identifiers in the file | `16` — unchanged by this edit | **EXECUTED: 16** — HOLDS |
| V7 | invariant | count of lines beginning `| PVT-` in §Validation Targets | `16` — the preamble's claim matches the table it introduces | **EXECUTED: 16** — HOLDS |
| V8 | regression | `grep -c 'production recommendation, not a demonstration target' $S` | `1` — CR-017's row survived | **EXECUTED: 1** — HOLDS |

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V7 is the check that matters** — it compares the preamble's count against the table rather than against my
arithmetic, which is what failed in CR-013's first attempt.
