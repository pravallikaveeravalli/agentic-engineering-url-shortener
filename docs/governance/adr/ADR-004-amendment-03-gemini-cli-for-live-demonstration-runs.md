# ADR-004 Amendment 03 — Gemini CLI (`agy`) as the Live-Demonstration Transport

**Successor record to [ADR-004](./ADR-004-ai-provider-and-autonomy-bounding.md)**, which is Accepted and is
**not edited in place**. This record amends one thing within it: which transport T073a-f's live demonstration
runs (AS-007) actually use. ADR-004's decision content — Anthropic Claude behind an owned interface, the
Claude Code CLI adapter as the documented primary/production transport (ADR-004-A1) — is unchanged and
restated below so a reader of this record alone is not misled.

| Field | Value |
|---|---|
| Amends | `docs/governance/adr/ADR-004-ai-provider-and-autonomy-bounding.md` (Accepted 2026-09-20, Gate 4), alongside ADR-004-A1 (transport) and ADR-004-A2 (flag removal) |
| Scope of amendment | **Live-demonstration transport only.** `StageAiProvider`, the flag, the deterministic counterparts, the kind labels, the no-plan gate, and `ClaudeCodeCliStageAiProvider` itself are all untouched — the Claude adapter remains the documented primary/production path, built and unit-tested exactly as ADR-004-A1 left it |
| Status | **Accepted** — 2026-09-21 |
| Deciding human | Pravallika Veeravalli (human owner) |
| Decision date | 2026-09-21 |
| Recorded in | This record; `plan.md` §12 via CR-042; `tasks.md` T073a-f |
| Provenance | Decision is the owner's own, made after a spawned worker proved `agy -p` invocable from this environment. The nesting-guard finding and the against-case enumeration were raised by the executing agent and are marked as such where they appear |
| Governing constitution | v1.1.0 |

## Decision

For **T073a-f's live demonstration runs only** (AS-007-labelled, one recorded run per adapter), a second
`StageAiProvider` implementation is added — `GeminiCliStageAiProvider`, a subprocess invocation of the
locally installed, already-authenticated Gemini CLI (`agy`):

```
agy -p "<stage prompt>" --model gemini-3.8-flash-high --output-format json </dev/null
```

The model is **pinned in configuration** (`gemini-3.8-flash-high`) and, because agy's own response JSON
names no resolved model id to read instead, **the pinned id is recorded as the model used** — a disclosed
departure from ADR-004-A1's "read from the response" rule, forced by what this particular CLI's output
shape actually contains, not a relaxation of FR-ORC-029 elsewhere. `ClaudeCodeCliStageAiProvider` remains
the documented primary/production adapter, unmodified and still unit-tested to the same five assertions
ADR-004-A1 established.

## Reason / verification performed

The concrete, structural reason: **a Claude Code build agent cannot spawn the `claude` CLI as a nested
subprocess.** This was found directly, not inferred — two identical headless attempts (`claude -p ... --model
... --output-format json`) both returned an immediate "This command requires approval" with no interactive
prompt ever resolving, and a compound-command variant hit the identical block. That is a deliberate
recursive-invocation guard on the `claude` binary specifically, confirmed by the owner rather than worked
around by this agent. The SAME environment CAN spawn `agy` — proven live: a spawned worker invoked
`agy -p "Reply with exactly the word: pong" --model gemini-3.8-flash-high --output-format json` and received
a genuine Gemini response (`{"status":"SUCCESS","response":"pong\n",...}`), confirmed a second time directly
in this session before `GeminiCliStageAiProvider` was written.

**The owner's reasoning, in substance:** the live runs exist to demonstrate that `StageAiProvider`'s seam is
real and pluggable, under a real environmental constraint — not to prove any one vendor's superiority. Since
the CLI actually invocable from this build agent is `agy`, using it for the live demonstration runs **is**
the demonstration: the seam absorbs a transport swap with zero change to any executor, prompt-building logic,
guard, or requirement, exactly as ADR-004-A1's own "Reversibility: High" already claimed and exactly as this
amendment now proves a second time, under different pressure than the first.

## Consequences the owner acknowledges and accepts

- **T073a-f's evidence trail names two different models across the session** — three adapters' live runs
  (Normalization, AmbiguityDetection, Decomposition) were exercised earlier under Claude, in a session that
  could still spawn `claude`; the remainder run under Gemini. Both are genuine AS-007 demonstration
  executions of the SAME adapter code against the SAME `StageAiProvider` seam — the model differs, the claim
  each run makes does not.
- **agy's model-id field is a pin, not a reading**, for as long as this amendment is in force. If a future
  `agy` version adds a resolved-model field, `GeminiCliStageAiProvider` should be updated to read it, the same
  way the Claude adapter reads `modelUsage` — this amendment records today's honest state, not a permanent
  design choice.
- **A second CLI dependency** for anyone wanting to reproduce these six specific demonstration runs. Bounded
  the same way ADR-004-A1 bounds the Claude CLI: AI mode is optional for everything graded (CN-011), and
  `ClaudeCodeCliStageAiProvider` remains the documented production path regardless of which CLI produced a
  given demonstration run's evidence.

## The case against this transport, recorded

1. **Introducing a second live-call adapter for six demonstration runs is more moving parts than reusing
   one.** The counter: the six runs cannot happen at all under the primary adapter from this environment —
   the nesting guard is not a preference to route around, it is a hard block this session already tested
   twice. A second adapter is the only way to get real, live, worker-driven evidence rather than none.
2. **Recording a pinned model id instead of a response-read one is a step down from ADR-004-A1's own bar.**
   Accepted as a real, disclosed cost — not hidden behind a claim that agy's response was read when it
   wasn't. FR-ORC-029's spirit (never fabricate what model answered) is upheld; its letter (read, not assumed)
   is honestly not fully met by this CLI's current output shape, and that gap is stated here rather than
   smoothed over.
3. **Two adapters is more surface for T014's plane-boundary and CN-010 architecture rules to hold across.**
   Mitigated by `GeminiCliStageAiProvider` living in the exact same package as `ClaudeCodeCliStageAiProvider`
   and implementing the exact same interface — no new package, no new rule needed.

None of these outweighs the owner's reason: real evidence from a transport this environment can actually
invoke beats no evidence from one it cannot.

## Explicitly unchanged from ADR-004 and ADR-004-A1

| Element | Status |
|---|---|
| Anthropic Claude reached through **one owned interface** (`StageAiProvider`) | Unchanged |
| `ClaudeCodeCliStageAiProvider` as the documented primary/production adapter | Unchanged, unmodified, still unit-tested to its own five assertions |
| Deterministic counterparts as first-class requirements | Unchanged |
| Per-stage executor-kind labels (`DETERMINISTIC` / `AI` / `HUMAN`) | Unchanged |
| The stage-7 no-plan gate and its three options | Unchanged |
| Reliability proofs driven by injected fakes, never a live provider (FR-ORC-030) | Unchanged — `GeminiCliStageAiProviderTest` proves this exactly as `ClaudeCodeCliStageAiProviderTest` does for Claude |
| CN-011 and the keyless reviewer default | Untouched |

## Specification impact: none

Assessed rather than assumed, on the same grounds ADR-004-A1 used: `spec.md` names no transport and no CLI
anywhere; every key-related statement remains true under this addition (there is still no API key in either
adapter). No change request against `spec.md` was required, and none was raised. `plan.md`'s ADR-004
narrative is routed through CR-042.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Model id recorded is a pin, not a verified reading | Disclosed explicitly, here and in the adapter's own javadoc — never presented as a response-read value it is not |
| Two CLI-shaped adapters could drift in argv-safety discipline | `GeminiCliStageAiProviderTest`'s assertion 1 proves the identical argv-array, never-a-shell-string property, the same command-injection-critical test `ClaudeCodeCliStageAiProviderTest` runs for Claude |
| agy absent or unauthenticated | Classified `UNAVAILABLE` via the same `ProviderFailureTranslator.forSubprocessProvider()` every adapter in this package uses — no new vendor taxonomy |
| agy output shape drifts between versions | Classified `INTERNAL`, permanent, never silently coerced — same discipline as the Claude adapter |
| Reader confuses this amendment with a vendor-superiority claim | This record states plainly, twice, that the reason is environmental (a nesting guard), not qualitative |

## Reversibility

**High, and this record is itself the second proof of it** — ADR-004-A1 demonstrated the seam survives a
transport swap once (Claude API → Claude CLI, hypothetically); this amendment demonstrates it survives a
swap to a **different vendor's CLI entirely**, under real environmental pressure, with zero change to
`StageAiProvider`, any of the six T073a-f executors, any prompt-building logic, or any requirement.

## Traceability

- **Requirements**: FR-ORC-029 (model id recorded per run — met by disclosed pin, not a response reading, for
  this transport specifically), FR-ORC-030 (fakes, not a live provider, for reliability proofs).
- **ADR**: ADR-004 (amended), ADR-004-A1 (the primary transport this amendment sits alongside, unmodified).
- **Change control**: **CR-042** for `plan.md` wording.
- **Tasks**: T073a-f (the six adapters whose live demonstration runs use this transport).

## Validation

1. **Argv-not-shell test** — identical property to ADR-004-A1's own assertion 1, proven for `agy`.
2. **Model-id-is-the-pin test** — the recorded id equals the configured pin, and the test's own name and
   assertion message say why, rather than implying a response reading that does not happen.
3. **Non-SUCCESS-status test** — agy's own explicit `status` field, refused when not `"SUCCESS"`.
4. **Malformed-output test** — non-JSON output classified `INTERNAL`, permanent.
5. **Never-in-reliability-suite test** — identical property to ADR-004-A1's own assertion 5, proven for `agy`.
