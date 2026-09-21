# CR-047 — Amendment 03's nesting-guard finding corrected; Claude CLI restored as the live/scenario transport

| Field | Value |
|---|---|
| **Change request** | CR-047 |
| **Title** | Sync `plan.md`'s ADR-004 narrative with [ADR-004 Amendment 04](../adr/ADR-004-amendment-04-claude-cli-nesting-corrected.md); wire `ClaudeCodeCliStageAiProvider` as the active live/scenario transport |
| **Raised by** | Owner correction, relayed to the acting agent |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED**, 2026-09-21 |
| **Decision date** | 2026-09-21 |
| **Classification** | **MINOR** — corrects a factual claim and restores a production adapter to active use; adds no new architecture, removes nothing, and both `StageAiProvider` implementations remain built and tested |
| **Artifacts changed** | `plan.md` §12 ADR-004 narrative (this CR's own application step); `docs/governance/adr/README.md` (index rows for 004-A3 and the new 004-A4) |
| **Non-approved files changed** | `ADR-004-amendment-04-claude-cli-nesting-corrected.md` (new), `ClaudeCodeCliStageAiProviderLiveDemo.java` (new, the verification), `src/main/java/agentic/shortener/config/OrchestrationConfiguration.java` (the `stageAiProvider` bean now names the Claude adapter explicitly, applied in this CR's own follow-on wiring step) |
| **Applied** | 2026-09-21 |

---

## Reason / verification performed

Amendment 03 (CR-042) stated a Claude Code build agent cannot spawn `claude` as a nested subprocess, citing
two headless Bash-tool attempts that both returned "This command requires approval." The owner corrected
this directly: those attempts used a shell-redirect suffix (`</dev/null`) typed straight into the agent's own
Bash tool, and it was **that tool's own allowlist pattern-matching** that failed to recognize the resulting
command shape — not a guard on the `claude` binary, and not anything related to process nesting.
`ClaudeCodeCliStageAiProvider` was never actually subject to the failure Amendment 03 attributed to it: it
invokes `claude` from compiled Java via `ProcessBuilder`, an argv array with no shell involved at all — a
different code path entirely from the agent's own Bash tool.

Verified live, before this record or any production wiring change was written:

```
$ ./scripts/build.sh -q -Dtest=ClaudeCodeCliStageAiProviderLiveDemo -DfailIfNoTests=false test
CLAUDE LIVE VERIFICATION: modelId=claude-sonnet-5
CLAUDE LIVE VERIFICATION: content=pong
```

A real, nested, nested-inside-a-Claude-Code-session `claude` CLI call, answered in 6.4 seconds.

## Scope

Explicitly unchanged:

- `StageAiProvider` — the interface itself, untouched.
- `GeminiCliStageAiProvider` — byte-identical, still built, still tested, still the transport behind
  T073a-f's own six live-demonstration evidence files, which remain valid AS-007 executions and are not
  retroactively invalidated by this correction.
- `ClaudeCodeCliStageAiProvider` — byte-identical; this CR restores it to active use in production wiring, it
  does not modify its implementation.
- T131d's 429/quota-classification fix (`ProviderRateLimitedException`, `ProviderFailureTranslator`) —
  entirely orthogonal, lives in the Gemini adapter's own error-parsing path, unaffected by which adapter is
  the active default.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | Real, live, nested `claude` call verified BEFORE any governance record or wiring change | genuine response, model id read from `modelUsage` | **EXECUTED — HOLDS** |
| 2 | ADR-004 Amendment 04 filed, correcting Amendment 03's rationale without editing it in place | new record, both prior records untouched | **EXECUTED — HOLDS** |
| 3 | `docs/governance/adr/README.md` index updated — 004-A3's row corrected to point at 004-A4; 004-A4 added | both rows present and accurate | **EXECUTED — HOLDS** |
| 4 | `plan.md` §12 synced — the nesting-guard claim corrected, ADR-004-A4 cited, reversibility narrative extended to a third demonstrated swap | present | **EXECUTED — HOLDS** |
| 5 | `OrchestrationConfiguration.stageAiProvider` wired to `ClaudeCodeCliStageAiProvider` | Spring context loads; existing `@SpringBootTest`s (e.g. `GateDecisionControllerIT`) still green | **EXECUTED — HOLDS** (see this CR's companion commit) |
| 6 | Fast + integration suites, `scripts/ci.sh` | green | **EXECUTED — HOLDS** |

## Conditions attached to the approval

1. **Retain the Gemini adapter and its evidence** — the owner's explicit instruction ("the seam works with
   two real providers — a strength, not a workaround"). Honoured; nothing about `GeminiCliStageAiProvider`
   or its six demonstration files changed.
2. **Verify before wiring, not after** — honoured; step 1 above happened before any governance record was
   written.

## Cross-record orphan check

- Amendment 03's own "Reason / verification performed" section still asserts the (now-corrected) nesting
  claim — **left as filed, per this project's own immutability discipline**; Amendment 04 is the correction
  record referencing it, and the README index row for 004-A3 now points a reader at 004-A4 before they would
  otherwise trust the uncorrected claim.
- No other applied record cites Amendment 03's nesting-guard rationale as a premise for something else that
  would now be falsified — checked directly: only the README index and `plan.md`'s own narrative repeated
  it, both corrected in this CR.
- CR-042 itself is unaffected — its own subject (adding `GeminiCliStageAiProvider` as a second transport,
  and syncing `plan.md` for that addition) remains true; this CR does not reopen or contradict it.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If a future Bash-tool allowlist change makes direct `claude ...` invocation reliable for the agent's own shell commands too, note it does not change this record's conclusion — `ClaudeCodeCliStageAiProvider` never depended on that | Informational; no action currently required |
