# CR-042 — Gemini CLI (`agy`) adapter added for T073a-f's live demonstration runs

| Field | Value |
|---|---|
| **Change request** | CR-042 |
| **Title** | Add `GeminiCliStageAiProvider`; route T073a-f's live demonstration runs through it instead of `ClaudeCodeCliStageAiProvider` |
| **Raised by** | Owner ruling, relayed to the acting agent |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED**, 2026-09-21 |
| **Decision date** | 2026-09-21 |
| **Classification** | **MINOR** — adds a second `StageAiProvider` implementation behind the existing interface; nothing existing is removed, narrowed, or replaced. `ClaudeCodeCliStageAiProvider` remains the documented primary/production adapter, unmodified |
| **Artifacts changed** | `plan.md` §12 ADR-004 narrative (this CR's own application step); `docs/governance/adr/README.md` (index row) |
| **Non-approved files changed** | `GeminiCliStageAiProvider.java` (new), `GeminiCliStageAiProviderTest.java` (new), `ADR-004-amendment-03-gemini-cli-for-live-demonstration-runs.md` (new) |
| **Applied** | 2026-09-21 |

---

## Reason / verification performed

A Claude Code build agent (this session) cannot spawn the `claude` CLI as a nested subprocess — verified
directly, not assumed: two identical headless invocations both returned an immediate "This command requires
approval" with no interactive resolution, and a compound-command variant hit the same block. This is a
structural, deliberate recursive-invocation guard, confirmed by the owner, not a permission the owner could
grant by allow-listing (allow-listing `claude` was tried in an earlier turn and did not change the outcome).

The same environment CAN spawn `agy` (the Gemini CLI) — proven live, twice: once by a spawned worker in an
earlier turn, and once directly in this session as verification before any code was written:

```
$ agy -p "Reply with exactly the word: pong" --model gemini-3.8-flash-high --output-format json </dev/null
{"conversation_id":"87164e5d-...","status":"SUCCESS","response":"pong\n","duration_seconds":1.66,...}
```

**The owner's ruling:** live demonstration runs for T073a-f use the Gemini adapter, model
`gemini-3.8-flash-high`, specifically because it is invocable and `claude` is not — not because of any
quality or vendor judgment. The full reasoning and its acceptance are recorded in
[ADR-004 Amendment 03](../adr/ADR-004-amendment-03-gemini-cli-for-live-demonstration-runs.md), which this CR
routes into `plan.md`.

## Scope

Explicitly unchanged:

- `StageAiProvider` — the interface itself. `GeminiCliStageAiProvider` implements it exactly as
  `ClaudeCodeCliStageAiProvider` does; this CR is the interface doing the job ADR-004 built it to do.
- `ClaudeCodeCliStageAiProvider` — byte-identical to before this CR, still the documented primary/production
  adapter, still unit-tested to its own five ADR-004-A1 assertions.
- Every T073a-f executor's own prompt-building, parsing, and guard logic — none of the six adapter classes
  changed; only which `StageAiProvider` implementation their live-demo harness constructs.
- FR-ORC-030 (reliability proofs never call a live provider) — `GeminiCliStageAiProviderTest` uses stub
  scripts exclusively, the identical discipline `ClaudeCodeCliStageAiProviderTest` already established.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | `agy` invocable from this session, verified before writing any adapter code | real JSON response, `status:"SUCCESS"` | **EXECUTED — HOLDS** |
| 2 | `GeminiCliStageAiProvider` implements `StageAiProvider`, argv array only | `GeminiCliStageAiProviderTest` assertion 1 (argv-not-shell, the security-critical one) | **EXECUTED — HOLDS** |
| 3 | Model id: agy's response has no resolvable-model field; the pin is recorded, disclosed as such | assertion 2 passes, and its message states the pin is recorded because there is nothing to read instead | **EXECUTED — HOLDS** |
| 4 | Malformed / non-SUCCESS / CLI-unavailable classification matches the same `ProviderFailureTranslator` every adapter in this package uses | assertions 3-6 | **EXECUTED — HOLDS** |
| 5 | Never referenced by a reliability-proof test | assertion 7 | **EXECUTED — HOLDS** |
| 6 | ADR-004-amendment-03 filed, README index updated | both present | **EXECUTED — HOLDS** |
| 7 | Fast + integration suites, `scripts/ci.sh` | green | **EXECUTED — HOLDS** |

## Conditions attached to the approval

1. **`ClaudeCodeCliStageAiProvider` stays intact as the documented primary/production path** — not replaced,
   not deprecated. Honoured; verification step 2's own test file is untouched by this CR.
2. **Record the model-id gap honestly** — a pin, not a verified reading, stated plainly rather than
   implied otherwise. Honoured; the adapter's own javadoc, the ADR amendment, and the test's own assertion
   message all say so in the same words.
3. **Route via change control**, since this touches `plan.md`'s ADR-004 narrative — this record.

## Cross-record orphan check

No other applied record quotes `ClaudeCodeCliStageAiProvider`'s field names or claims it is the ONLY
`StageAiProvider` implementation — ADR-004-A1 describes it as "the implemented adapter," true both before
and after this CR (it remains implemented; it is simply no longer the sole implementation). ADR-004-A1 is
not corrected and needs no forward reference: this CR adds alongside it, exactly as ADR-004-A1's own
"Reversibility: High... the SDK adapter can be added alongside as a second implementation without touching
either" already anticipated for a different second implementation. `docs/delivery/backlog.md`'s T002 (the
SDK adapter) is unaffected — still a distinct, unbuilt backlog item.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If a future `agy` version adds a resolved-model field, read it instead of the pin | Whenever that CLI capability appears — `GeminiCliStageAiProvider`'s own javadoc names this explicitly |
| T073a-f's evidence trail names both models across the session (three earlier runs under Claude, the remainder under Gemini) — both are genuine AS-007 executions of the same adapter code | The six live-demo evidence captures, wherever they land |
