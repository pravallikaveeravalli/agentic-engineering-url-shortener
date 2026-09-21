# Quickstart: Validation Guide

**Feature**: 001-agentic-sdlc-url-shortener | **Date**: 2026-09-19 | **Plan**: [plan.md](./plan.md)

**Status**: **Derived design artifact.** Approved indirectly via `plan.md`'s approval at the Gate 4 closing
package (2026-09-20) — it was **not** independently gated. Changes pass through change control. Nothing in this
guide has been executed: it is the validation design, not a record of results.

How a reviewer proves the system works, without reading the source. Commands are shown in the shape they will
take; exact invocations follow **ADR-001 — Java 21 + Spring Boot 3, Accepted 2026-09-20** — and are finalised
when **T127** executes this guide end to end on a clean machine. Nothing here has been executed yet: this is the
validation design, not a record of results.

---

## Prerequisites

| Requirement | Why |
|---|---|
| Docker | The store must be a restartable process (ADR-002, CL-009) |
| JDK 21 *(pending ADR-001)* | Build and run |
| **Nothing AI-related** | Deliberate. The reviewer-default path runs fully without an API key, without an authenticated CLI, and without a network (CN-011, SC-014). AI mode is optional — see below |
| No network access needed | The reliability suite must pass offline (NFR-AUT-004) |

---

## What each mode demonstrates — read this before running anything

**You are not limited to the three prepared scenarios. Submit any requirement you like.** The system is
required to process arbitrary requirements without any change to its executors (FR-ORC-028, SC-016), and the
committed evidence includes a run over a requirement outside DS-A/B/C precisely so that claim is verifiable
rather than asserted. If it only worked on three blessed inputs, it would be a rigged demonstration.

One flag controls this: **`ai: on | off`**, defaulting to **`off`**. **With AI off — the keyless default — the
governance machine runs on any input you give it. Turning AI on adds the creative intelligence.** Both are
real; they differ in what the stages *produce*, not in whether the lifecycle actually runs.

The flag is named for the one thing it controls: whether AI executors participate. It deliberately does **not**
claim the run is "deterministic", because a keyless run can legitimately contain a `HUMAN` execution at the
stage-7 gate described below.

### With AI off — the reviewer default, no key, no network

Submit a requirement of your own invention and you get **the complete governed lifecycle, for real**:

- **Normalization** — your requirement is parsed into identified, typed, testable requirement records.
- **Ambiguity detection** — genuine. Submit something vague or self-contradictory and the run **actually
  suspends and asks you**. It does not pretend to detect ambiguity; the affected path stops.
- **Decomposition** — tasks derived from your requirement, dependency-ordered, each tracing to a requirement.
- **Gates awaiting *your* approval** — you are the reviewer. Approve, reject, request changes, escalate, or
  say nothing and watch the run suspend rather than advance.
- **Bounded retries, fallback, compensation, safe-stop, resumption, replanning** — all genuine, all
  deterministic, all provable by injected fault scenarios.
- **Audit trail and terminal outcome** — reconstructable from the database alone.

What it does **not** produce: genuinely authored code, or creative artifacts beyond template grade.
Documentation and design outputs are structural rather than written. **Every stage execution is labeled
`DETERMINISTIC`** in the run evidence (FR-ORC-029), so you never have to guess which you are looking at.

#### And at stage 7 it stops and asks you

This is the part worth watching for, because it is where the system refuses to pretend. Stage 7's deterministic
executor **applies a change plan** — and for a requirement you just invented, no plan exists. Rather than
recording a no-op and flowing onward, **the run suspends at a gate and asks you to decide**, stating its expiry
consequences up front like every other gate. You get exactly three options:

| Option | What happens |
|---|---|
| **Proceed as a governance-only run** | Downstream stages continue. Your decision is recorded, and the evidence labels implementation as **intentionally skipped by human decision** — not as done |
| **Implement it yourself** | Make the change in the working tree, or supply the change content with your decision, and the run continues into **real testing that judges your change**. That execution is recorded with executor kind **`HUMAN`** |
| **Abandon the run** | Terminal outcome `ABANDONED`, reason recorded |

A labelled no-op flowing onward would be quiet pretending: proceeding with nothing implemented is a material
fact you should consciously accept, not discover in a label afterwards. The same principle governs every gate
here — nothing material advances on inference or silence.

Two consequences worth knowing. The **prepared scenarios never hit this gate**, because their change plans
exist. And the executor model has three kinds, not two: **AI, deterministic, and human** — the third completed
by option 2 above, and every stage execution records which one ran it.

### With `ai: on` — one flag plus one AI credential of your own, entirely optional

**What AI mode needs**: **either** an authenticated **Claude Code CLI** on the machine (the implemented adapter —
it shells out to the CLI in headless mode), **or** an API key if the SDK adapter has been built. **Entirely
optional, never required for anything graded.**

Setting `ai: on` turns the AI-capable stages on, and the same lifecycle then produces
**genuine creative output — including authored code at stage 7**, which the engine applies on a branch and the
**real** test suite judges. A failing AI-authored change routed back to be reworked is the governance visibly
working, not a broken demonstration.

`ai: on` is **never a prerequisite for anything graded**. The full test suite, all reliability proofs, and the
release-readiness checks run keyless and offline by design.

### The committed scenario evidence

The recorded DS-A, DS-B and DS-C runs in the evidence bundle were executed by the candidate with **`ai: on`**,
labeled per stage with the **pinned model id** so any figure or artifact is attributable to a specific model
version. Alongside them sits the out-of-scenario run described above. You can re-run any scenario yourself with
the flag either way.

### Summary

| | `ai: off` (keyless default) | `ai: on` |
|---|---|---|
| Any requirement you invent | Yes | Yes |
| Governed lifecycle, gates, your own approvals | Yes, genuine | Yes, genuine |
| Ambiguity genuinely detected and suspended | Yes | Yes |
| Retries, compensation, safe-stop, resumption, replanning | Yes, genuine | Yes, genuine |
| Audit trail and reconstruction | Yes | Yes |
| Creative artifacts | Template grade | Genuine |
| Authored code at stage 7 | No — suspends and asks you instead | Yes |
| Executor kinds you may see | `DETERMINISTIC`, or `HUMAN` if you implement it yourself | `AI`, `DETERMINISTIC`, `HUMAN` |
| Requires an AI credential or network | **No** — neither a key nor an authenticated CLI | Yes — an authenticated Claude Code CLI, or an API key if the SDK adapter is built |
| Required for anything graded | — | **No** |

---

## 1. Start dependencies and run the suite

```
docker compose up -d        # PostgreSQL only
<build tool> test           # full suite, no AI key, no network required
```

**Expected**: all tests pass. The run prints per-category counts covering unit, contract,
persistence, integration, orchestration-transition, approval, retry, timeout, fallback,
rollback/compensation, safe-stop, resumption, replanning, concurrency, security, and end-to-end.

**This is the single most important check**: if it needs a key or a network, CN-011 is violated.

---

## 2. Prove the URL shortener works

### Provision a creator — no HTTP endpoint exists for this

```
<run script> ops/scripts/provision-creator.sh --name demo --expires 90d
#   or, to deliberately create a non-expiring credential:
<run script> ops/scripts/provision-creator.sh --name demo --expires never
```

**The `--expires` parameter is required and has no default.** Omit it and the script exits non-zero without
provisioning anything. That is deliberate: no one gets to *not think* about credential lifetime at provisioning.
An unconsidered eternal key is impossible; explicitly choosing `never` is permitted, silently receiving it is
not.

**Expected**: prints the key **once** to your terminal, in the form `crk_<base64url>`. The `crk_` prefix means a
leaked key is identifiable as a key on sight, and gives the secret scan a deterministic pattern to match rather
than anonymous high-entropy strings to guess at; it carries no information and does not reduce entropy. That
terminal output is operator-invoked interactive output — the application itself never writes key material
anywhere (FR-URL-019). Only a SHA-256 of the full string is stored.

Rotation is **provision-new-then-revoke-old**. There is no renewal flow and no expiry notification, by design
(EX-005) — an expiring key simply stops working on its date, which is why the parameter forces the choice up
front.

### Create, follow, inspect

```
curl -X POST localhost:8080/v1/links -H 'Authorization: Bearer <key>' \
     -H 'Content-Type: application/json' \
     -d '{"destination":"https://example.com/target"}'
```
→ `201` with a `shortCode`, `replay: false`.

```
curl -i localhost:8080/<shortCode>        # no credentials — deliberately public
```
→ redirect to the destination. **Note**: the class is **temporary, never permanent** (FR-URL-007, DF-002 resolved by CR-003); the exact code within that class is an implementation detail (CN-006).

```
curl localhost:8080/v1/links/<shortCode>/analytics -H 'Authorization: Bearer <key>'
```
→ `200` with timestamp-only events. **Inspect the payload**: there is no IP, user agent, referrer, or
device field anywhere. That absence is the point (NFR-SEC-005).

### Negative checks that should all fail correctly

| Attempt | Expected |
|---|---|
| `{"destination":"javascript:alert(1)"}` | `400 SCHEME_NOT_ALLOWED`, nothing persisted |
| Create with no `Authorization` header | `401` — creation is never anonymous |
| Create with an **expired** key, then with a **revoked** key | `401` both times, with **byte-identical** responses — the distinction is not disclosed |
| Follow an expired link | `410 LINK_EXPIRED`, **not** a redirect, and distinguishable from `404` |
| Follow a never-issued code | `404 CODE_NOT_FOUND` |
| Read another creator's analytics | `404` — indistinguishable from not-found, so it cannot be used as an ownership oracle |
| Same `Idempotency-Key`, identical body | `200` with `replay: true`, no second link created |
| Same `Idempotency-Key`, different `expiresAt` | `409 IDEMPOTENCY_CONFLICT`, nothing minted, nothing changed |
| Same destination twice, **no** key | Two different short codes — there is no destination-based deduplication |
| Stop the database, then `GET /health/ready` | `503`, while `/health/live` stays `200` |

---

## 3. Prove the governance actually governs

These are the checks that distinguish this from an ungoverned service.

### Silence does not approve

Start a run that reaches a gate, then **do nothing**.

```
curl localhost:8080/v1/runs/<runId> -H 'Authorization: Bearer <key>'
```

**Expected**: after the gate wait elapses, `state: "SAFE_STOP"`, `terminalState: null`,
`autoAbandonAt` populated, and **no downstream stage executed**. A suspended run is not a finished
run. Confirm the original gate request had already disclosed both deadlines — the reviewer should not
have had to inspect the run to discover them.

### Prohibited transitions are refused

The orchestration suite asserts these are rejected, not merely absent: `RUNNING → SUCCEEDED` without
exit criteria; `AWAITING_APPROVAL → SUCCEEDED` without a recorded human decision; `FAILED →
SUCCEEDED`; leaving `SAFE_STOP` other than by human decision or retention.

### Retry requires two votes

The retry suite covers: both vote yes → retry; declared-only → no retry; executor-only → no retry;
`UNKNOWN` category → permanent; malformed envelope → permanent. Each retry record carries **both**
signatures — the executor's proposal and the orchestrator's ruling.

### Rollback and compensation are distinguishable

Check the run history labels them differently. A short link corrected during a run is **set to
expired, never deleted** (Compensation Register).

### Resumption survives both restart classes

```
# mid-run: kill the application, restart it       -> run resumes, terminal outcome reached
# mid-run: docker compose restart <db>, wait      -> run resumes once the store returns
```

**Expected** in both: committed effects occur exactly once. Zero duplicates.

### Release readiness blocks

The release-readiness suite seeds each of the nine blocking conditions individually and asserts the
report is blocking and **names that condition**. Confirm no report can be a pass while a mandatory
policy is `FAIL` or an exception is unapproved or expired.

---

## 4. Run the three scenarios

```
<run> scenario DS-A      # greenfield
<run> scenario DS-B      # brownfield
<run> scenario DS-C      # ambiguous
```

| Scenario | What to look for |
|---|---|
| **DS-A** | Stage 4 is `SKIPPED`. The run records the quality checks performed **and the explicit reason clarification was not required**. No gate fires artificially. |
| **DS-B** | The subject is the **per-creator aggregate redirect limit** — FR-URL-016's third tier (PVT-014), deliberately deferred from the baseline, which builds only the per-creator creation tier and the per-code redirect tier. Confirm the **before-state** first: traffic spread across several links, each staying **under** the per-code limit, passes **unthrottled**. Then the seven-dimension impact analysis whose timestamp **precedes** the first code modification. Then the governed change: the same traffic is throttled, the response **names the aggregate tier without naming the creator**, the per-code tier is unregressed, and redirect latency is re-measured against PVT-001 with the ownership lookup now in the hot path. Retry and compensation records present. |
| **DS-C** | Ambiguity detected before implementation; only the affected path suspends; a replan event lists invalidated stages and **voided approvals**; the run resumes and terminates. |

Each emits an evidence bundle carrying a per-node **executor-kind label** — `DETERMINISTIC`, `AI` or `HUMAN`
(FR-ORC-029; "kind", not "mode", since **CR-001/CR-005**). Deterministic executions are labelled as such and never
presented as AI work.

### Now run one of your own

```
<run> submit --requirement "<anything you want built or changed>"
```

This is the check that matters most, and it is the one the prepared scenarios cannot make for you: an
arbitrary requirement must complete the same governed lifecycle with **no executor changes** (FR-ORC-028,
SC-016). Try a well-formed requirement and watch stage 4 be skipped with the reason recorded; then try a vague
or self-contradictory one and watch it suspend and ask you. A committed out-of-scenario run is in the evidence
bundle, but re-running it with input of your own choosing is the stronger test.

---

## 5. Reconstruct a run as an assessment reviewer would

The real acceptance test for the submission: answer these from artifacts alone, with no access to the
session that produced them.

1. Which requirement produced which tasks?
2. Which tests were executed, and with what result? (A generated-but-unexecuted test is never a pass.)
3. Where did the run retry, fall back, roll back, or compensate — and which was which?
4. Who decided what, when, and why? Is each decision materialized as a repository record?
5. Which figures were measured, under what conditions, and which are proposed targets?
6. What did the baseline deliberately omit, which run implemented it, and where is that run's evidence?

```
<run> evidence export --run <runId>
<run> traceability report        # must show zero orphans in both directions
<run> metrics mttr --run <runId>
```

The MTTR output must state its population, name its exclusions — including the declared exclusion of
human wait time — count unrecovered failures **separately**, and label everything as demonstration
measurement.

---

## Honest limitations

- Single developer machine, single process, small fault populations. **Every figure is a demonstration
  measurement, never a production statistic** (Constitution IX, AS-009).
- Time parameters may be compressed in demonstration runs; where they are, the evidence labels them
  (AS-007).
- Deferred findings **DF-001** (analytics exactness), **DF-002** (redirect permanence) and **DF-003** (retention
  boundary) were **all resolved on 2026-09-20** — by ADR-014, CR-003 and CR-004 respectively. The behaviour this
  guide shows reflects those **approved** decisions: the analytics append is synchronous in its own transaction with
  failure isolated and counted (PVT-009 as an observable failure ceiling); and redirects are temporary, never
  permanent. **Retention was then simplified**: this demonstration retains every record indefinitely, and the
  production archival job — including CR-004's run-termination clock rule — is recorded as a **recommendation**
  rather than built (NFR-AUD-003, CR-017). **DF-004** — the run-level retry circuit breaker and expiry notification
  — remains open by design and out of scope.
