# ADR-004: AI Provider and Autonomy Bounding for AI-Capable Stages

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

CL-003 (Gate 2) settled the executor model: stages whose work is genuinely creative are AI-capable,
stages whose value is repeatability are deterministic engines, and nothing is hardcoded to
demonstration inputs. The owner rejected a purely deterministic system because it "would make the title
hollow", and rejected input-specific recognition because it "would be a rigged demo".

Two hard constraints bound this decision. **CN-011**: the reviewer-default path must run with no AI key
present. **FR-ORC-030 / NFR-AUT-004**: reliability proofs must be deterministic and must not depend on
AI availability, variability, or network access. So the AI is required to exist and required not to be
required.

Six stages are AI-capable per the approved executor map: S2 normalisation, S3 ambiguity detection, S5
decomposition, S6 design, S7 implementation, S9 documentation.

**Assessment implication**: a reviewer will ask both "is there real AI here?" and "can I run it without
credentials?". Both answers must be yes.

## Decision Drivers

1. **CN-011 compliance** — keyless default is non-negotiable.
2. **Quality on code authoring and requirement analysis** — S7 authors patches verified by a real suite.
3. **Interface isolation** — NFR-MNT-002 requires external dependencies behind owned interfaces.
4. **Determinism of graded reliability evidence.**
5. **Cost and setup burden.**
6. **Auditability** — which mode actually ran must be recorded per stage (FR-ORC-029).

## Options Considered

### Option A — Anthropic Claude API behind a provider interface

- **Approach**: one `StageAiProvider` interface; a Claude-backed implementation; model id pinned in
  configuration and recorded in run evidence; deterministic implementations for every AI-capable stage
  as the default.
- **Advantages**: strong performance on code authoring and requirement analysis, which is what S6 and
  S7 need; clean interface keeps the dependency at one seam; pinning the model id makes runs
  reproducible and auditable.
- **Disadvantages**: API key and cost for demonstration runs; network dependency for AI-mode runs.
- **Risks**: non-determinism appearing in graded evidence; provider outage mid-demonstration.
- **Implementation impact**: one adapter plus prompt construction per AI-capable stage; deterministic
  counterparts required regardless.
- **Assessment implications**: satisfies both "real AI" and "runs without credentials".

### Option B — OpenAI API behind the same interface

- **Approach**: identical to A with a different adapter.
- **Advantages**: equivalent capability for this purpose.
- **Disadvantages**: no differentiator; identical cost and network profile.
- **Risks**: as A.
- **Assessment implications**: neutral — the interface makes this swappable, so the choice carries
  little weight.

### Option C — Local model (e.g. via a local inference server)

- **Approach**: small local model behind the same interface.
- **Advantages**: no key, no per-call cost, no network; arguably the strongest CN-011 story.
- **Disadvantages**: substantial setup burden pushed onto the reviewer (weights, runtime, hardware);
  materially weaker authoring quality, which undermines S7's whole point — an AI that cannot author a
  competent patch makes the governed-verification demonstration less interesting, not more.
- **Risks**: reviewer cannot run it; poor output quality misread as a flaw in the orchestration.
- **Assessment implications**: mixed — better on the keyless axis, worse on every other.

### Option D — Deterministic only, no AI at all

- **Approach**: drop AI-capable stages; all twelve deterministic.
- **Advantages**: simplest; fully deterministic; zero cost.
- **Disadvantages**: **rejected by the approved CL-003 decision** — the owner explicitly ruled that
  zero real AI makes the system's title hollow.
- **Risks**: would require re-opening an approved clarification through change control.
- **Assessment implications**: contradicts an approved decision; not available without a change request.

## Decision

**Anthropic Claude API, reached only through an owned `StageAiProvider` interface**, with the specific
model pinned in configuration and recorded in each run's evidence. **Deterministic mode is the default
for every AI-capable stage**; AI participation is opt-in per run. Every AI-capable stage has a working
deterministic counterpart, which serves simultaneously as the CN-011 default and as the declared
fallback under FR-ORC-015.

### The run-level flag: `ai: on | off`, default `off` (owner decision, 2026-09-20)

The run-level flag is named **`ai`**, taking `on` or `off`, defaulting to `off`. Documentation phrasing is
"with AI off (the keyless default)". An earlier draft called this "deterministic mode", which the owner
rejected:

> A run-level name must not claim a property the run cannot guarantee — a run started keyless can contain a
> `HUMAN` execution at the stage-7 no-plan gate, so "deterministic mode" over-promises at the run level. The
> flag controls exactly one thing, whether AI executors participate, and is now named for exactly that. A
> keyless run containing a human execution contradicts nothing under this name.

**Per-execution kind labels are unchanged**: `DETERMINISTIC` / `AI` / `HUMAN`, each literally true of the
single execution it stamps. Engines genuinely are deterministic; the run-level *mode* was the only dishonest
word, and it is gone.

### The no-change-plan gate at stage 7 (owner decision, 2026-09-20)

A deterministic counterpart is only meaningful where there is something for it to do. At stage 7 the
deterministic counterpart **applies a change plan**; for a requirement novel to the system — the case a
reviewer creates the moment they submit their own requirement keyless — **no plan exists**. An earlier draft of
this ADR left that case as a labelled no-op that flowed onward. The owner rejected that:

> A labeled no-op that flows onward is quiet pretending — proceeding with nothing implemented is a material
> fact a human must consciously accept, not discover in a label afterwards. This is the same principle as my
> earlier decisions: nothing material advances on inference or silence.

**Approved behavior.** When stage 7 executes deterministically and no change plan exists for the requirement,
it **MUST NOT no-op forward**. It suspends at a human gate, with the expiry consequence stated in the ask per
CL-005, presenting exactly three options:

| Option | Effect | Recorded as |
|---|---|---|
| **1. Proceed as a governance-only run** | Downstream stages continue; nothing is implemented | Recorded decision; run evidence labels implementation **intentionally skipped by human decision** |
| **2. Human-implemented** | The human makes the change themselves — externally in the working tree, or by supplying change content with the decision — and the run proceeds into **real testing that judges it** | Executor kind **`HUMAN`**, completing the executor model as **AI / deterministic / human** |
| **3. Abandon the run** | Terminal | `ABANDONED` |

**The three prepared scenarios are unaffected** — their change plans exist, so this gate never fires for them.
The out-of-scenario evidence run (SC-016) should deliberately exercise it, because it demonstrates governance
exactly where a reviewer will personally encounter it.

**Specification impact**: this extends approved text (FR-ORC-029's mode vocabulary, FR-ORC-031's fallback
behavior, KE-25's executor kinds, §Stage Executor Model) and is therefore routed through change control as
**CR-001**, not folded in silently.

## Rationale

Option D is unavailable — it contradicts CL-003, an approved clarification, and could only be revisited
through the change-control workflow. Option C's advantage on the keyless axis is already secured by the
architecture rather than by the provider: because deterministic counterparts are mandatory anyway (for
CN-011 and for fallback), a heavyweight local model buys nothing the design does not already have,
while costing reviewer setup and authoring quality. Between A and B the interface makes the choice
largely reversible; A is selected on authoring quality for S7, which is the stage whose output the real
test suite must actually judge.

The structurally important part of this decision is not the vendor. It is that **the deterministic
counterpart is a first-class requirement, not a degraded mode** — it is what a reviewer runs by default,
and it is what makes every reliability proof independent of AI behaviour.

## Consequences

**Positive**: real AI where work is creative, satisfying CL-003; keyless reviewer default satisfying
CN-011; provider swappable at one seam; reliability evidence unaffected by AI variability.

**Negative**: two implementations per AI-capable stage — deterministic and AI-backed — which is real
duplicated effort across six stages. Additionally, a reviewer exploring novel requirements keyless will meet
the no-plan gate on every one of them, which is a decision asked rather than a run completed silently — the
intended trade, but a real one.

**Governance (no-plan gate)**: a third executor kind, `HUMAN`, enters the model, so "who executed this stage"
now has three answers rather than two and every one of them is recorded. The gate also closes a path by which a
run could have reached a terminal outcome with nothing implemented — which tightens Principle X's evidence
integrity, since a governance-only run is now an explicit human decision on the record rather than an inference
a reader must make from a label.

**Operational**: API key and cost for demonstration runs only. No credential is needed to run tests or
the default path.

**Testing**: the AI adapter is never exercised in the reliability suite; scriptable fakes are used
instead (FR-ORC-030). The provider is exercised only in labelled demonstration runs with `ai: on`.

**Governance**: every run's evidence must label each stage's executor mode actually used (FR-ORC-029,
NFR-AUT-003). A deterministic execution presented as AI work would be an evidence-integrity violation
under Principle X. The model id is recorded so an AI-mode run is attributable to a specific model
version.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Non-determinism contaminating graded evidence | Reliability proofs use injected fakes only; AI-mode runs are labelled demonstration runs and are never the basis of a reliability claim |
| Provider outage during demonstration | Deterministic counterpart is the declared fallback; the run continues with those executions stamped `DETERMINISTIC` rather than failing |
| Timebox pressure from six duplicate implementations | Plan §14 Day 2 checkpoint reduces AI-capable stages from six to two (S2, S5/S7 priority), with mode labelling making the reduction visible rather than hidden |
| Prompt-injected content from a requirement influencing an executor | AI output is never executed as text; S7 patches are applied on a branch and judged by the real suite (T-12); autonomy limits refuse out-of-channel effects |
| Secret leakage of the API key | Key read from environment only, never logged at any level, covered by the secret scan (`POL-SEC-002`) |

## Reversibility

**High.** One adapter behind one interface. Switching to Option B, or adding Option C as an additional
implementation, is an adapter change with no impact on the orchestration core or the stage definitions.

## Traceability

- **Requirements**: FR-ORC-028 (single executor interface), FR-ORC-029 (mode selection and labelling),
  FR-ORC-030 (injectable executors), FR-ORC-031 (AI-authored implementation under governed
  verification), FR-ORC-015 (fallback), FR-ORC-021 (bounded autonomy).
- **Specification**: CL-003, CN-010, CN-011, §Stage Executor Model, NFR-AUT-001..004, SC-014, SC-015.
- **Plan**: §12 ADR-004, §3 per-node table, §Stage Executor Model, §14 Day 2 checkpoint.
- **Expected tasks**: Slice 4–6 executor interface and deterministic engines; Slice 8 AI-mode
  demonstration runs.
- **Related**: ADR-003 (orchestration model), ADR-011 (testing strategy).

## Validation

Three proofs. **SC-014**: a complete run in a key-less, network-isolated environment. **SC-015**: zero
stage executions appear in evidence without a mode label, and no deterministic execution is presented
as AI work. **FR-ORC-031**: a demonstration run in which an AI-authored change fails the real test suite
and is routed back within its bound — that failure being caught is the governance working, not a failed
demonstration.
