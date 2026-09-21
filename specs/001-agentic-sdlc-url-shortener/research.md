# Phase 0 Research: Agentic Software Engineering System: URL Shortener

**Date**: 2026-09-19 | **Plan**: [plan.md](./plan.md) | **Constitution**: v1.1.0

**Status**: complete. No `NEEDS CLARIFICATION` markers remain in the plan's Technical Context. Four
items remain **unapproved inputs** rather than unknowns — they are decisions the human owner owes at
the Plan gate, tracked in `plan.md` §Decisions Required, and each carries a labelled provisional
position here.

---

## Resolved: technology candidates

Each candidate is a **proposal**, not a decision (CN-002). Full option tables in `plan.md` §12.

### Language and framework (ADR-001)

- **Decision**: Java 21 + Spring Boot 3 — *recommended, pending ADR gate.*
- **Rationale**: defensibility in live discussion outweighs raw velocity for an artifact the owner must
  defend; strongest transactional/locking story for concurrent short-code uniqueness; Testcontainers
  makes real-store testing routine rather than exceptional.
- **Alternatives considered**: Python 3.12 + FastAPI would likely reach the same functional surface
  faster and is recommended against *only* on defensibility grounds — recorded because it is a genuine
  contender, not a straw man. TypeScript + NestJS offers no advantage over either for this owner. Go
  gives excellent concurrency primitives but weaker ORM/migration ergonomics for the governance tables.

### Persistence (ADR-002)

- **Decision**: PostgreSQL 16 via Docker Compose; Testcontainers in tests — *recommended.*
- **Rationale**: CL-009 requires surviving a persistence-layer restart, which requires a restartable
  store *process*. FR-URL-006 requires unique-code enforcement under genuine contention.
- **Alternatives considered**: SQLite file mode satisfies disk-backing but its coarse write locking
  would **mask** the concurrency behavior FR-URL-013 is meant to prove — a case where the simpler
  option makes a required test weaker, so simplicity was rejected on evidentiary grounds. H2 file mode
  has the same weakness plus less production credibility.

### Orchestration engine (ADR-003)

- **Decision**: purpose-built explicit persisted DAG — *recommended.*
- **Rationale**: the orchestration model is the graded deliverable. Adopting Temporal would leave the
  candidate demonstrating configuration of someone else's state machine.
- **Alternatives considered**: Temporal (adds a server and worker topology whose complexity
  Constitution VII would not justify); Camunda/BPMN (heavier, and BPMN's semantics do not map cleanly
  onto the two-vote retry ruling or the compensation register); Spring StateMachine (would help with
  stage states but not the graph, persistence, replanning, or gate semantics — most of the work).

### AI provider (ADR-004) — **TRANSPORT AND FLAG SUPERSEDED 2026-09-20; the provider choice stands**

**Superseded in two respects** by ADR-004 as accepted at Gate 4 and by its Amendment 01 (CR-008):

1. **Transport.** The implemented adapter is a **subprocess invocation of the locally installed, authenticated
   Claude Code CLI in headless mode**, not an API call. The owner's ground, recorded at ADR-004-A1: *"I already have
   a Claude subscription; I do not have API credits and do not want to buy them when this alternative exists. The
   provider interface exists precisely so this choice is a swap behind one seam."* The **Anthropic API/SDK adapter is
   the recorded production alternative behind the same interface**, built only if time permits.
2. **The flag — since REMOVED ENTIRELY by Decision J (ADR-004-A2, CR-028); recorded as it stood.** "Deterministic mode default" is the vocabulary **CR-001 abolished**. The run-level flag was
   **`ai: on | off`, default `off`**, named for the one thing it controls — whether AI executors participate — because
   a keyless run can still contain a `HUMAN` execution at the stage-7 no-plan gate, so a run-level determinism label
   would over-promise.

**What stands unchanged**: the provider choice itself — Anthropic Claude, reached through **one interface owned by
this codebase** — and the reason it is behind an interface. ADR-004 assessed the provider choice as **High
reversibility** for exactly this reason, and Amendment 01 is that assessment being proved rather than a departure
from it.

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
where the work is creative, CN-011 required the keyless path and was **retired by Decision J** (CR-028) — the transport and provider reasoning here is unaffected by that retirement — and the three rejected alternatives were rejected
on grounds the transport change does not touch. Only the transport and the flag name moved.

### Contract validation (ADR-005)

- **Decision**: OpenAPI 3.1 document as source of truth, with executable response validation in
  contract tests — *recommended.*
- **Rationale**: only an approach that can **fail a build on drift** makes the contract falsifiable.
- **Alternatives considered**: generating the spec from code was rejected because the spec then becomes
  whatever the code says, so drift is undetectable by construction; generating code from the spec is
  heavier than the timebox justifies.

---

## Resolved: patterns adopted

### Two-vote retry eligibility

Set-intersection of a stage's design-time declared retryable categories with the executor's proposed
classification, executor holding veto but not grant (CL-006). Researched against the standard
alternatives: centrally-declared-only couples the core to every plugin's error taxonomy; executor-only
is fail-open and puts the autonomy limit inside the governed component. The maker-checker shape — a
recorded proposal plus a recorded ruling — is what makes "why did this retry?" answerable from audit
alone.

### Structural default plus declared override for reversibility

Effect reversibility derived from **where the effect lands** (version control / immutable store /
working tree), with per-stage overrides where correction requires product knowledge, and
registration-time enforcement that an irreversible effect must name its compensating action (CL-007).
Saga-style uniform compensation was rejected because it erases the rollback/compensation distinction
the constitution requires be visible.

### Suspension as a first-class non-terminal state

`SAFE_STOP` distinct from the three terminal states, with idle retention measured from last activity
(CL-005). Researched against the common pattern of treating timeout as failure: that pattern makes a
gate timeout indistinguishable from a system fault, which would defeat the silence test (SC-005).

### Marker-only idempotency

Three semantics — mint, labelled replay, explicit conflict (CL-008) — matching the mainstream
idempotency-key contract. Destination-based deduplication was rejected because a second request with a
different expiry forces either ignoring the caller or mutating a link already in circulation.

---

## Threat Model

Trust boundaries per `plan.md` §1. Assets: destinations, creator credentials, analytics, audit
integrity, and the governance record itself.

| ID | Threat | Vector | Mitigation | Residual |
|---|---|---|---|---|
| T-01 | Open-redirect abuse | Attacker shortens a malicious destination and distributes the short link | Scheme allow-list (non-waivable); abuse checks; destination stored verbatim and never recomputed | **Accepted**: content reputation is out of scope (EX-006). Disclosed. |
| T-02 | SSRF / internal reconnaissance | Destination points at private, loopback, or link-local space | Refusal or explicitly recorded accepted risk (FR-URL-005) | Low |
| T-03 | Credential leakage via telemetry | Key or credential-bearing URL written to a log | Application never writes key material at any level including startup; secret scan over repo *and* captured telemetry; non-waivable clause | Low |
| T-04 | Credential theft at rest | Store compromise | Keys stored only as hashes; no plaintext anywhere; no committed keys | Low |
| T-05 | Unauthorized key issuance | Attacker mints a creator identity | **No HTTP issuance surface exists.** Shell access is the trust boundary | Low, and honestly bounded: host compromise is game over regardless |
| T-06 | Analytics exposure across tenants | Creator reads another's analytics | Owner-only entitlement; refusal does not reveal whether the code exists (no ownership oracle) | Low |
| T-07 | Redirect hotspot denial | Single link hammered | Per-code redirect tier (PVT-013) | Low |
| T-08 | Noisy neighbor | Many links each under the per-code limit collectively soak capacity | Per-creator-aggregate tier (PVT-014) | **Accepted trade-off**: followers of a popular creator may be throttled through no fault of their own. Deliberate: service protection over unlimited per-tenant availability |
| T-09 | Privacy exposure | Personal data held in analytics | No follower-identifying field is stored at all (NFR-SEC-005) | None by construction — data minimization, not anonymization |
| T-10 | Audit tampering | Record altered to hide a decision | Append-only, immutable; corrections are new entries; policy check on the six mandatory fields | Low |
| T-11 | Governance bypass | Gate satisfied without a human | Silence produces suspension; materialized record required before authorized work; release readiness blocks on unmaterialized outcomes | Low |
| T-12 | Malicious AI-authored code | AI stage emits harmful changes | Applied only on a branch; verified by the **real** suite; never pushed; AI output never executed as text | Moderate — mitigated by pipeline, not eliminated. Disclosed |
| T-13 | Short-code reuse hijack | Deleted code reissued, inheriting circulating links | Links are **never deleted**, only expired (Compensation Register) | None by construction |

---

## Provisional positions on unapproved inputs

Labelled provisional. Not decisions, and not to be cited as such.

### DF-001 — analytics exactness — **SUPERSEDED 2026-09-20; the position below was wrong**

**Superseded by ADR-014**, accepted at Gate 4. The provisional position recorded here — synchronous and
transactional *with* the redirect — **violates FR-URL-010's negative criterion**: if the append shares the
redirect's transaction, a failed append rolls the transaction back and fails the redirect, which EC-012
explicitly prohibits. The position was reached by optimising for the positive criterion in isolation and never
testing it against the negative one.

**Resolved as**: synchronous append in a **separate** transaction, failure isolated and counted, redirect
succeeds regardless; PVT-009 retained but redefined as an append-failure-rate ceiling rather than a loss budget;
SC-001 scoped explicitly to redirect correctness. See
`docs/governance/adr/ADR-014-analytics-consistency.md`.

Retained verbatim below for provenance, because a superseded position that was *wrong* is more instructive than
one that was merely provisional:

> **Provisional**: synchronous and transactional with the redirect. This makes FR-URL-010's "appends an
> event" and SC-001's 100% literally true, and reduces PVT-009's 0.5% loss tolerance to redundancy
> (retire it). **Cost**: the event write sits inside the redirect's latency budget, pressuring PVT-001.
> **The alternative**: async best-effort protects latency but requires the spec to admit lossy analytics,
> which changes what the acceptance tests may assert. Genuinely a trade, not an oversight — which is why
> it is the owner's call.

### DF-002 — redirect permanence — **RESOLVED 2026-09-20, position confirmed**

Resolved as **temporary, never permanent** (CR-003). The provisional position below was correct and is now the
approved requirement in FR-URL-007.

> **Provisional**: temporary redirect. A cached permanent redirect bypasses the service, silently
> undercounting analytics **and** defeating expiry, because an expired link would still be followed from
> cache. Permanence would buy client-side speed at the cost of two requirements.

### DF-003 — retention boundary — **RESOLVED 2026-09-20, position confirmed**

Resolved as the audit retention clock starting at **run termination** (CR-004), chosen over the alternative
(audit retention strictly greater than idle retention) because it holds for any pair of values.

> **Provisional**: measure audit retention from **run termination**, not record creation, so a run's
> earliest records cannot age out while the run is still live or being abandoned.

### DF-005 — remaining Plan-gate items — **CLOSED 2026-09-20**

All approved or discharged at the Gate 4 closing package (CR-005): PVT values approved as binding thresholds,
timebox and scope controls approved, MTTR method approved including the declared human-wait exclusion, and the
contract deliverables discharged — with parser validation executed the same day, finding and fixing one real
defect.
