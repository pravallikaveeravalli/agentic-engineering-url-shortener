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

### AI provider (ADR-004)

- **Decision**: Anthropic Claude API behind a provider interface; latest available Claude model chosen
  at implementation time; deterministic mode default — *recommended.*
- **Rationale**: CL-003 requires real AI where work is creative, while CN-011 requires the
  reviewer-default path to run with no key. An interface satisfies both and keeps providers swappable.
- **Alternatives considered**: OpenAI (equivalent for this purpose; no differentiator); local model
  (removes the key requirement but adds setup burden for a reviewer and weakens authoring quality);
  deterministic-only (rejected by CL-003 as making the "agentic" claim hollow).

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

### DF-001 — analytics exactness

**Provisional**: synchronous and transactional with the redirect. This makes FR-URL-010's "appends an
event" and SC-001's 100% literally true, and reduces PVT-009's 0.5% loss tolerance to redundancy
(retire it). **Cost**: the event write sits inside the redirect's latency budget, pressuring PVT-001.
**The alternative**: async best-effort protects latency but requires the spec to admit lossy analytics,
which changes what the acceptance tests may assert. Genuinely a trade, not an oversight — which is why
it is the owner's call.

### DF-002 — redirect permanence

**Provisional**: temporary redirect. A cached permanent redirect bypasses the service, silently
undercounting analytics **and** defeating expiry, because an expired link would still be followed from
cache. Permanence would buy client-side speed at the cost of two requirements.

### DF-003 — retention boundary

**Provisional**: measure audit retention from **run termination**, not record creation, so a run's
earliest records cannot age out while the run is still live or being abandoned.

### DF-005 — remaining Plan-gate items

PVT values, timebox, MTTR method with its declared human-wait exclusion, and the contract deliverables
are all tabled in `plan.md` for approval. None is assumed to be granted.
