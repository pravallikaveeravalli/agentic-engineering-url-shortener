# ADR-004 Amendment 01 — StageAiProvider Transport: Local Claude Code CLI Adapter

**Successor record to [ADR-004](./ADR-004-ai-provider-and-autonomy-bounding.md)**, which is Accepted and is
**not edited in place**. This record amends one implementation detail within it: the transport by which
`StageAiProvider` reaches the model. ADR-004's decision content is unchanged and restated below so a reader of
this record alone is not misled.

| Field | Value |
|---|---|
| Amends | `docs/governance/adr/ADR-004-ai-provider-and-autonomy-bounding.md` (Accepted 2026-09-20, Gate 4) |
| Scope of amendment | **Transport only.** The provider interface, the flag, the deterministic counterparts, the kind labels, and the no-plan gate are untouched |
| Status | **Accepted** — 2026-09-20 |
| Deciding human | Pravallika Veeravalli (human owner) |
| Decision date | 2026-09-20 |
| Recorded in | This record; `plan.md` and `quickstart.md` via **CR-008**; `tasks.md` T073 |
| Provenance | Decision and reason are the owner's own. The command-injection risk and the against-case enumeration were raised by the executing agent and are marked as such where they appear |
| Governing constitution | v1.1.0 |

## Decision

The implemented adapter for `StageAiProvider` is a **subprocess invocation of the locally installed,
already-authenticated Claude Code CLI in headless mode**:

```
claude -p "<stage prompt>" --model <pinned model> --output-format json
```

The model is **pinned in configuration**, and the **actually-used model id is read from the response JSON** and
recorded in run evidence, exactly as FR-ORC-029 requires.

## Reason / verification performed

The owner's reason, recorded verbatim:

> I already have a Claude subscription; I do not have API credits and do not want to buy them when this
> alternative exists. The provider interface exists precisely so this choice is a swap behind one seam.

That last clause is the substantive point: ADR-004 selected Anthropic Claude **behind an owned interface** and
assessed the provider choice as **High reversibility** for exactly this reason. This amendment is the interface
boundary doing the job it was created to do, not a departure from the decision.

## Consequences the owner acknowledges and accepts

Recorded in her terms:

- The adapter is a **subprocess rather than an SDK call** — spawn, JSON parse, error mapping, all behind the
  seam. CLI startup latency is **noise at stage timescales**.
- Demonstration runs **draw on the same subscription quota as the development tooling**. Runs are few and short.
- There is a closure worth one line in the record: **the tool that built the system serves as its stage
  executor.**

## The case against this transport, recorded

Per the project's convention that a decision without its counter-case recorded is not a decision. The owner
weighed and accepted these; they are not objections she failed to consider.

1. **An SDK call is the more conventional engineering choice.** A typed client with structured errors is easier
   to reason about than spawn-and-parse. The counter is that the difference lives entirely behind one interface,
   and the error mapping into the CL-006 envelope has to be written either way.
2. **A subprocess is a wider attack surface than a library call**, because it introduces an execution boundary
   that untrusted prompt text crosses. This is the one consequence that is *not* merely stylistic, and it is why
   the argv-not-shell rule below is a mandatory test rather than a coding note.
3. **Shared subscription quota couples demonstration runs to development tooling.** A quota exhausted while
   building would also block a demonstration. Mitigated by deterministic fallback, and bounded by runs being few
   and short.
4. **The CLI is a product surface, not a stable API.** Its flags and JSON shape can change between versions
   without the deprecation discipline an SDK carries. Mitigated by pinning what we can and by classifying
   malformed output as permanent rather than silently coercing it.
5. **A reviewer wanting to exercise AI mode must install and authenticate a specific tool** rather than paste a
   key. Bounded by AI mode being optional for everything graded (CN-011).

None of these outweighs the owner's reason, and the fifth is the only one a reviewer experiences — which is why
the quickstart states both credential paths plainly rather than presenting the CLI as the only option.

## Explicitly unchanged from ADR-004

| Element | Status |
|---|---|
| Anthropic Claude reached through **one owned interface** | Unchanged |
| Run-level flag `ai: on \| off`, **default `off`** | Unchanged |
| Deterministic counterparts as **first-class requirements**, not degraded modes | Unchanged |
| Per-stage executor-kind labels (`DETERMINISTIC` / `AI` / `HUMAN`) | Unchanged |
| The stage-7 no-plan gate and its three options | Unchanged |
| **CN-011** and the keyless reviewer default | **Untouched** |
| Reliability proofs driven by injected fakes, never the live provider (FR-ORC-030) | Unchanged |
| SC-014 — full run with no AI key and no network | Unchanged and still satisfiable |

**The Anthropic API/SDK adapter is recorded as the alternative implementation behind the same interface — the
production path, built only if time permits.** It is therefore a backlog item (`docs/delivery/backlog.md`, T002),
not a dropped option.

## Specification impact: none

Assessed rather than assumed. Every mention of a key in `spec.md` is of the form *"no AI key present"* or *"no AI
key and no network"* (FR-ORC-029, NFR-AUT-004, SC-014, CN-011). Those statements remain **true** under this
transport — there is no API key in either implementation — and the specification names no transport anywhere. No
change request against `spec.md` was required, and none was raised.

`plan.md` and `quickstart.md` **do** carry transport wording and are routed through **CR-008**.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| **Command injection via the stage prompt** | The subprocess MUST be invoked with an **argv array, never a shell string**. Stage prompts are untrusted content — they originate in submitted requirements. A `/bin/sh -c` invocation with an interpolated prompt would be a remote-code-execution path from a requirement field. Asserted by test |
| CLI absent or unauthenticated on the machine | Classified `UNAVAILABLE` in the CL-006 envelope → proposed transient → bounded retry → fallback to the deterministic counterpart, stamped `DETERMINISTIC`. AI mode degrades; it never fails the run |
| CLI output not valid JSON, or schema drift in its response | Classified `INTERNAL` → **permanent** under default-deny; never retried as transient. Parse failure must not be silently coerced into an empty result |
| Subprocess hangs | Stage timeout applies. S7's effect is non-idempotent, so a timeout there is **not** retryable (FR-ORC-014 rule 4, EC-033) |
| Subscription quota exhausted mid-demonstration | Classified `RATE_LIMITED` → transient → bounded retry → deterministic fallback. Disclosed in `docs/LIMITATIONS.md` (T147) |
| Model id in evidence not the model actually used | The id is read **from the response JSON**, not from configuration — so the record reflects what ran, which is stronger than recording the pin alone |
| CLI writes prompt or response to its own logs outside our control | Stage prompts contain requirement text, not secrets. FR-ORC-017's prohibition binds **our application**; this boundary is disclosed rather than claimed to be controlled |

## Reversibility

**High, and demonstrably so** — this record is itself the evidence. Swapping transport required no change to the
interface, the stage definitions, the flag, the labels, or any requirement. The SDK adapter can be added
alongside as a second implementation without touching either.

## Traceability

- **Requirements**: FR-ORC-029 (model id recorded per run), FR-ORC-015 (fallback), FR-ORC-030 (fakes, not the
  provider, for reliability proofs), FR-ORC-031 (AI-authored implementation under governed verification),
  NFR-MNT-002 (owned interface), NFR-AUT-004 / SC-014 (keyless, network-free default), CN-011.
- **ADR**: ADR-004 (amended), ADR-003 (envelope classification), ADR-011 (AI excluded from graded suites).
- **Change control**: **CR-008** for `plan.md` and `quickstart.md` wording.
- **Tasks**: T073 (adapter), T002 (SDK adapter recorded as backlog), T147 (quota limitation disclosed).

## Validation

1. **SC-014** — full run with `ai: off`, no API key, no network: unchanged and must still pass.
2. **Argv-not-shell test** — a stage prompt containing shell metacharacters must be passed through verbatim and
   must not be interpreted. This is the security-critical assertion of this amendment.
3. **Model-id capture test** — the id recorded in run evidence equals the id in the CLI response JSON.
4. **Fallback test** — with the CLI unavailable, the stage falls back to its deterministic counterpart and the
   execution is stamped `DETERMINISTIC`, never `AI`.
5. **Malformed-output test** — non-JSON output classified `INTERNAL` and permanent, not retried.
