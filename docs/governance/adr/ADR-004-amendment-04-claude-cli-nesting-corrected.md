# ADR-004 Amendment 04 — Amendment 03's Nesting-Guard Finding Corrected; Claude CLI Restored as the Live/Scenario Transport

**Successor record to [ADR-004](./ADR-004-ai-provider-and-autonomy-bounding.md)** and to
[Amendment 03](./ADR-004-amendment-03-gemini-cli-for-live-demonstration-runs.md), **neither edited in
place**. This record corrects one factual claim Amendment 03 made and restates the resulting decision;
everything else in ADR-004 and Amendment 03 not addressed below is unchanged.

| Field | Value |
|---|---|
| Amends | Amendment 03's own "Reason / verification performed" section, specifically its central claim: "a Claude Code build agent cannot spawn the `claude` CLI as a nested subprocess" |
| Scope of amendment | **The nesting-guard finding, and which transport live/scenario runs use.** `StageAiProvider`, the flag, the deterministic counterparts, the kind labels, the no-plan gate, and both adapter implementations themselves are untouched by this record — this is a factual correction plus a transport decision, not an architecture change |
| Status | **Accepted** — 2026-09-21 |
| Deciding human | Pravallika Veeravalli (human owner) |
| Decision date | 2026-09-21 |
| Recorded in | This record; `tasks.md` (a note against T073a-f); production wiring in `OrchestrationConfiguration` |
| Provenance | Correction and decision are the owner's own. The verification below — a real, nested `claude` call succeeding — was performed by the executing agent at the owner's direction, after the owner's own correction of what the earlier block actually was |
| Governing constitution | v1.1.0 |

## What Amendment 03 got wrong, and why

Amendment 03 stated: "a Claude Code build agent cannot spawn the `claude` CLI as a nested subprocess,"
citing two headless attempts that both returned "This command requires approval" with no interactive
prompt ever resolving. That finding was real — the block genuinely happened — but its **cause** was
misdiagnosed as a structural, binary-level recursive-invocation guard on `claude` itself.

**The actual cause**: those two attempts invoked `claude` directly as a Bash-tool command, using a
shell-redirect suffix (`... --output-format json </dev/null`). Claude Code's own Bash-tool allowlist
matches commands by their literal shape; the `</dev/null` redirect syntax changed that shape enough that
the match failed, and the tool fell back to requiring interactive approval — the exact same "This command
requires approval" wording the earlier finding quoted, and the correct explanation for it. **This is a
property of the agent's own Bash tool, not of the `claude` binary, and it has nothing to do with whether a
process is "nested" inside a Claude Code session.**

`ClaudeCodeCliStageAiProvider` was never subject to this at all: it invokes `claude` from compiled Java via
`ProcessBuilder`, an argv array with no shell and no redirect syntax of any kind — a completely different
code path from the agent typing a command into its own Bash tool. Verified directly, not assumed, before
this record was written:

```
./scripts/build.sh -q -Dtest=ClaudeCodeCliStageAiProviderLiveDemo -DfailIfNoTests=false test
```

```
CLAUDE LIVE VERIFICATION: modelId=claude-sonnet-5
CLAUDE LIVE VERIFICATION: content=pong
```

A real, live, nested `claude -p "Reply with exactly the word: pong" --model claude-sonnet-5 --output-format
json` call, issued from inside this Maven-forked JVM while itself running inside a Claude Code session,
returned a genuine answer in 6.4 seconds. There is no structural guard preventing this.

## Decision

**Live and scenario-demonstration AI stages use `ClaudeCodeCliStageAiProvider` — ADR-004's original,
documented primary/production transport — restored to that role for exactly what it was always meant for.**
Model pinned per `application.yml`'s existing `shortener.claude-cli` configuration (`claude-sonnet-5`);
unlike the Gemini adapter, the Claude CLI's own response JSON carries a resolvable `modelUsage` field, so
the model id recorded per FR-ORC-029 is **read from the response**, not a pin — the stronger property
ADR-004-A1 always specified and Amendment 03 could not offer for its own transport.

**`GeminiCliStageAiProvider` is retained, not removed.** It remains a real, tested, second `StageAiProvider`
implementation — its own live demonstrations (`docs/evidence/ai-demos/`) stay valid evidence of exactly what
they always proved: the seam survives a transport swap to a different vendor's CLI entirely, with zero
change to any executor. That property is a strength of this design, not a workaround for an environmental
block that, per this record, never actually applied to either adapter's own invocation path. Nothing about
T131d's own 429/quota-classification fix is affected — it lives in `GeminiCliStageAiProvider` and
`ProviderFailureTranslator`, both unmodified by this record.

## Consequences

- **T073a-f's evidence trail is unaffected** — those six demonstrations remain genuine, valid AS-007
  executions under whichever transport actually ran them; this record does not retroactively invalidate or
  re-run them.
- **Live/scenario runs from this point forward (T132 onward) use the Claude adapter**, sidestepping the
  Gemini quota exhaustion Amendment 03's own transport hit during T132's first attempt
  (`docs/evidence/ds-a/run-snapshot-ATTEMPT-1-BLOCKED-gemini-quota-exhausted.md`) — a real, independent
  reason to prefer it now, separate from the nesting-guard correction itself.
- **The Claude CLI's own model-id-read property is exercised for the first time in this session's own
  production wiring** — `OrchestrationConfiguration`'s `stageAiProvider` bean, previously undecided between
  the two adapters for the Conductor's own default, now names `ClaudeCodeCliStageAiProvider` explicitly.

## Explicitly unchanged from ADR-004, ADR-004-A1, and ADR-004-A2

Everything Amendment 03's own "Explicitly unchanged" table names remains true under this record too — the
one owned `StageAiProvider` interface, both adapters' own argv-not-shell discipline, the deterministic
counterparts, the executor-kind labels, the no-plan gate, reliability proofs driven by injected fakes never
a live provider (FR-ORC-030), and CN-011/the keyless reviewer default.

## Traceability

- **Requirements**: FR-ORC-029 (model id recorded per run — now met by a genuine response reading for the
  live/scenario transport, not a pin), FR-ORC-030 (unaffected).
- **ADR**: ADR-004 (amended), Amendment 01 (the transport this record restores to active use), Amendment 03
  (corrected, not superseded outright — its transport addition and its own six adapters' evidence remain
  valid; only its nesting-guard *rationale* is corrected here).
- **Tasks**: T073a-f (unaffected); T132 onward (transport for live/scenario runs).

## Validation

The live verification quoted above, captured in
`src/test/java/agentic/shortener/orchestration/executor/ai/ClaudeCodeCliStageAiProviderLiveDemo.java` —
re-runnable by anyone with an authenticated `claude` CLI on `PATH`, exactly as the equivalent Gemini
live-demo classes already are.
