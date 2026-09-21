# agentic-sdlc-url-shortener

A URL shortener (**application plane**) with an embedded, governed AI-SDLC orchestration engine
(**control plane**) — one JVM process, per [ADR-012](docs/governance/adr/ADR-012-deployment-and-local-execution.md).
The application plane is an ordinary URL shortener a client calls over HTTP. The control plane is the
system that built it: a twelve-stage, gated, policy-enforced workflow engine that took a requirement from
intake through implementation, testing, documentation, and a mechanically-evaluated release-readiness
determination — six of its twelve stages backed by a real, live AI call (Gemini, via the `agy` CLI), the
rest deterministic and repeatable by design.

This README describes what is actually built and running today. For step-by-step setup and run
instructions, see [`specs/001-agentic-sdlc-url-shortener/quickstart.md`](specs/001-agentic-sdlc-url-shortener/quickstart.md)
— this file does not duplicate those commands.

## Architecture

Two planes, one process:

| Plane | Package | What it does |
|---|---|---|
| Application | `agentic.shortener.domain`, `.delivery`, `.application`, `.persistence` | Shorten a URL, redirect a short code, record analytics, provision creator API keys |
| Control | `agentic.shortener.orchestration`, `.policy`, `.audit` | Run the twelve-stage requirement pipeline, enforce policy, evaluate release readiness, capture the audit trail |
| Cross-cutting | `agentic.shortener.config` | Spring wiring shared by both planes |

Persistence is PostgreSQL 16 via Flyway migrations (`src/main/resources/db/migration/`), one schema shared
by both planes — the application's domain tables and the control plane's governance tables
(`audit_record`, `state_transition`, `gate_decision`, `failure_event`, `policy_check_result`,
`compensation_record`) sit side by side, the governance ones append-only by database privilege, not just
by convention (proven in `AuditImmutabilityIT`, `GovernanceImmutabilityIT`).

### The control plane's twelve stages

S1 intake → S2 normalization → S3 ambiguity detection → S4 clarification (conditional, human-gate-driven,
no executor of its own) → S5 decomposition → S6 design → S7 implementation (fans out, with its own join) →
S8 testing → S9 documentation → S10 compliance evaluation → S11 release-readiness evaluation → S12 summary
assembly. Five stages are deterministic engines (S1, S8, S10, S11, S12); six are AI-capable (S2, S3, S5,
S6, S7, S9), each behind the same `StageExecutor` interface — an executor cannot tell whether it is talking
to a real model or a scripted fake (`FR-ORC-030`), so the reliability, retry, and compensation machinery
proven against injected fakes still holds for the real path. The live AI transport is the Gemini CLI
(`agy`), per [ADR-004 Amendment 03](docs/governance/adr/ADR-004-amendment-03-gemini-cli-for-live-demonstration-runs.md);
`docs/evidence/ai-demos/` carries the captured evidence for each of the six stages' live demonstration runs.

## What is built

- **Application plane**: create a short link with validation, abuse guarding, and idempotency; redirect
  with expiry enforcement; per-creator and per-code rate limiting; analytics recording durable against a
  redirect that must never fail because analytics did; creator provisioning via a local operator script
  (never an HTTP endpoint — see Threat model below).
- **Control plane**: the full twelve-stage pipeline; bounded retry with exponential backoff and a
  two-signature retry ruling; rollback/compensation for reversible and irreversible effects; safe-stop and
  resumption across an orchestrator-process restart, proven end to end; store-restart resumption is built
  on the same mechanism, but its own integration test is `@Disabled` — a local Docker/Colima port-forward
  limitation, not a code defect, documented in the test itself; dynamic replanning with downstream
  invalidation and approval voiding; a twelve-check policy engine (`policy-set-1.1.0`) with an exception
  workflow; a release-readiness evaluator over all nine constitutional blocking conditions; the audit trail, MTTR
  measurement (with human wait and unrecovered failures excluded from the figures that would otherwise
  flatter them), structured telemetry, and a mechanical traceability report with a zero-orphan check.

## What is deliberately not built

Every omission below is a **recorded decision**, not a silent gap:

- **`docs/delivery/baseline-omissions.md`** — scheduled omissions with a named closing run. Currently one:
  the per-creator-aggregate redirect rate-limit tier (PVT-014), deferred to the brownfield scenario.
- **`docs/delivery/backlog.md`** — indefinite deferrals: a run-level retry circuit breaker, expiry
  notification (email/webhook), an SDK-based AI transport (a CLI subprocess is what is built), analytics
  partitioning, artifact-level consumption tracking, a full-stack Compose profile, and analytics beyond a
  time series. Custom aliases, link deletion/editing, and multi-region are out of scope entirely, not
  deferred.

## Threat model

Two trade-offs NFR-SEC-003 requires this project to disclose by name.

### The operator-script trust boundary

Creator identities are provisioned by a local operator script (`scripts/provision-creator.sh`), never by an
HTTP endpoint (FR-URL-019) — because **the ability to run commands on the machine is the trust boundary**,
in the demonstration and in a real deployment alike. Concretely:

- API keys are stored only as hashes; the application itself never writes key material anywhere.
- The script displays a newly-provisioned key once, to the operator's own terminal, and cannot recover it
  afterward — a lost key is replaced, not retrieved.
- The script requires an explicit expiry (a duration, or the literal `never`) and refuses to provision
  without one; an expired key simply stops working, with no notification, because no notification
  infrastructure exists.
- Security depends wholly on key entropy, making the generator itself a component whose correctness
  matters; a bearer secret sent over the wire is exposed to anything that logs requests without default
  redaction (`FR-URL-017`'s secret-scan discipline exists specifically for that residual risk).

### The noisy-neighbour throttling trade-off

The redirect path is rate-limited in tiers: per-code (`PVT-013`, 600/min) and — once the deferred
per-creator-aggregate tier lands — a per-creator aggregate (`PVT-014`, 3,000/min) meant to catch
many links each individually under the per-code limit from collectively soaking capacity. **Accepted
trade-off**: followers of a popular creator's links may be throttled through no fault of their own, because
no single one of those links exceeded its own limit. This is deliberate — service protection is chosen over
guaranteeing availability to any one tenant. As shipped today, only the per-code tier is enforced; the
aggregate tier's absence is itself asserted by a dedicated test
(`RateLimiterTest.theAggregateTierIsNotPartiallyPresent`) rather than left an unstated gap.

## Governance

Every material decision behind this project is a repository artifact, not a conversation: architecture
decisions in [`docs/governance/adr/`](docs/governance/adr/), change requests in
[`docs/governance/change-control/`](docs/governance/change-control/), and human gate decisions in
[`docs/governance/gate-decisions/`](docs/governance/gate-decisions/). See
[`specs/001-agentic-sdlc-url-shortener/quickstart.md`](specs/001-agentic-sdlc-url-shortener/quickstart.md)
for how to run and reconstruct any of it yourself.
