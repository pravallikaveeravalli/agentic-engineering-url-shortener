# Limitations and Residual-Risk Disclosure

Task T147. Constitution XI, FR-ORC-015 (retired). This is the disclosure the constitution requires: every
known limitation and residual risk this build carries, stated plainly, cross-checked against every ADR's own
Risks section and every DF entry. Disclosure is cheaper than discovery.

## 1. Performance and load measurements — deferred, not implemented

T145a–T145d (the load harness and its three measurement suites — redirect latency, creation latency, and the
analytics append-failure ceiling / first-bottleneck measurement) are **deferred**, not implemented, by the
owner's own standing decision (this session, time-constrained). None of the following was executed:

- Redirect resolution p95 latency ≤ 50 ms (PVT-001) at 100 concurrent clients (PVT-003).
- Link creation p95 latency ≤ 200 ms (PVT-002) at the same concurrency, with collision-retry frequency
  reported separately.
- Non-4xx error rate ≤ 0.1% (PVT-004) under sustained load.
- Analytics append-failure ceiling ≤ 0.5% of appends (T145d's own threshold), with the append-throughput /
  `redirect_event` growth-rate measurement that would support NFR-SCA-003's own first-bottleneck analysis.

**Stated honestly**: these are demonstration measurements this build never ran, not measurements that ran and
passed. In a real production rollout, all four would be executed before any release decision relies on them —
the harness and its three suites are specified (T145a–d), not built. The four tasks are marked **deferred**
in `tasks.md`, not silently skipped.

## 2. The live-AI pipeline is genuinely non-deterministic — the guards are the control, not a bug

S2 (normalization), S3 (ambiguity detection), S6 (design), S7 (implementation), and S9 (documentation) are
real, live AI-capable stages (ADR-004). The same requirement, submitted twice, can produce different real
findings, different design reasoning, and occasionally a malformed answer — this was proven repeatedly, live,
across dozens of real dispatches this engagement (`docs/evidence/ds-a/`, `docs/evidence/ds-c/`). This is not
a defect to be engineered away; it is the accepted shape of a system whose whole premise is that AI output
feeds a human gate rather than executing unchecked. What the system actually guarantees is not "the AI never
gets it wrong" but "a wrong answer is caught before it does damage":

- A malformed or drifting S7 change set is refused by structural validation before any git effect occurs, with
  one bounded self-correction retry (CR-066) before the stage fails permanently.
- A design or implementation output claiming behaviour the real test results do not support is refused by
  S9's own drift guard (T149, Constitution X) — proven live, catching a real hallucinated test name
  (`docs/evidence/ds-a/attempts-24-25-finding.md`).
- S3's own materiality classification occasionally omits a required field on a live dispatch
  (`qualityChecksPerformed` on a `NOT_MATERIAL` record) — a real, observed, intermittent gap, addressed by
  strengthening S3's own prompt (see `docs/governance/change-control/` for the CR covering this fix), never by
  relaxing the domain's own dual-field requirement (`AmbiguityRecord`), which is deliberate design (T082): the
  field is what lets a reviewer tell "nothing was checked" from "checked and found nothing."
- S10 and S11 are deliberately deterministic (T071) specifically so that release-readiness and policy
  evaluation do not inherit the AI stages' own variance.

**Residual risk, stated plainly**: a live run can still fail on AI output the guards correctly refuse,
requiring a fresh attempt. This is disclosed, observed behaviour (`docs/evidence/ds-a/attempts-*-finding.md`),
not a hidden one — every real occurrence found this engagement is on the record, with its own diagnosis.

## 3. The requirement-completeness ceiling

S3's own ambiguity detection reads only normalized requirement text — never the codebase — so any fact
"obviously" true of the delivered system reads as unstated to it. Five live attempts on a rich feature each
closed the prior gaps and surfaced new genuine ones, never converging to zero real findings. This is a real,
documented property of natural-language requirement description, not a defect in the detector: see
`docs/evidence/ds-a/requirement-completeness-ceiling-finding.md` for the full finding, including two later
corrections (CR-052 found part of the apparent ceiling was correctable over-firing, fixed and re-verified;
CR-053's own zero-runtime-behaviour control subject still drew two genuine findings, showing the ceiling
scales with feature surface, not with runtime behaviour specifically). Completeness should be expected to
scale with what a requirement describes, not assumed to reach zero.

## 4. The deferred per-creator aggregate redirect tier

The per-creator aggregate rate limit (PVT-014) is deliberately deferred to the brownfield scenario (T136a),
disclosed at its own point of deferral in `docs/delivery/baseline-omissions.md` (T055a) — this entry
cross-references that record rather than duplicating it. **As of this disclosure, T136a has not closed it**:
its own security gate is approved (`docs/governance/gate-decisions/ds-b/t136a-security-gate-approved.md`),
but `AggregateRedirectLimiter` does not exist yet — the build itself is separate, later work. Until T136a
lands, only the per-short-code tier (PVT-013, 600/min) is enforced; the multi-link aggregate case passes
unthrottled, by design, as the brownfield scenario's own documented before-state.

## 5. Retention posture — indefinite, by owner decision

No record — audit, run history, gate decisions, policy results, or redirect events — is ever deleted, purged,
archived, or otherwise removed by this build. This was a deliberate simplification (Decision H, this
session): *"let us not worry about archival at all, let the records stay in the table for ever, but record
this that, ideally we would have a prod archival job if we were doing this in a real prod env"*
(`docs/governance/change-control/CR-017-retention-as-archival.md`). Production archival — an 90-day audit/run
history window and a 30-day redirect-event window — is recorded as a **recommendation**, never implemented,
never enforced, and never measured (PVT-010, NFR-AUD-003). Unbounded table growth is the accepted, disclosed
consequence; `ADR-014`'s own Risks table already names time-partitioning with retention-by-partition-drop as
the designed first mitigation if this build were ever taken toward production.

## 6. Governance-surface posture and the actor-identity limitation

Governance surfaces (run submission, gate decisions) carry no credential by design — this is the same
`localhost`/single-tenant demonstration posture ADR-002 and ADR-012 already disclose for the data store and
deployment story, extended deliberately to the orchestrator's own control surface (Decision at CR-014, owner's
ruling verbatim: *"I don't think creators matter in orchestrator it is a url shortner concept, don't leak
those into orchestrator"*). Two separate identity models exist (URL-shortener creators; the orchestrator's own
`actorType`), and their separation is proven by an architecture test (no executor package references the
gate-decision path) — but that test proves separation, not that the governance surfaces are unreachable, and
they are unauthenticated by design.

**The actor-identity limitation, stated precisely**: `actorType` on a submitted gate decision is *declared by
the caller and not authenticated*. Anything that can reach the process can submit a decision asserting it is
human. Verified actor identity was considered and explicitly declined (owner decision, this session; an
operator-issued per-decision token was considered and declined as new scope). **The defensible claim this
system supports is that no workflow step ever approves its own gate — not that an impersonating caller is
detectable.** Those are different guarantees, and only the first one is made here.

## 7. PVT-016 — engineering judgement, not measurement

The per-node escalation thresholds (PVT-016) — the elapsed times at which the orchestrator asks a human
"keep waiting or kill the node?" — are engineering judgement, not the product of a load measurement (which
T145a–d's own deferral means this build never ran). A distinct, shorter overrun-escalation deadline (PVT-017,
proposed at 30 minutes) was considered and **withdrawn** by explicit owner ruling: *"I don't think we should
have any timeout as I said. Just wait till the user responds... Keep it simple"*
(`docs/governance/change-control/CR-023-overrun-escalation-deadline.md`). The accepted consequence: an
unattended, stalled run waits up to the uniform gate-wait deadline (PVT-006, 24 hours) before it suspends
itself — the same rule every other gate in this system already follows, silence never treated as approval.

## 8. Fallback is retired, not demonstrated — a named absence

FR-ORC-015 (fallback to a deterministic counterpart when an AI-capable stage is unavailable) is **retired**
(Decision K, `docs/governance/change-control/CR-032-fallback-retired-decision-k.md`), because its only real
implementation was six deterministic counterparts built for a keyless mode that no longer exists — retiring
the mode retired their only reason to exist. A single token counterpart was considered and declined as
*ceremonial*: a control present in the documentation and absent in the engineering is exactly the
exists-mainly-to-be-claimed defect this project rejects by name. **Bounded retry then safe suspension is the
entire degradation story** for every AI-capable stage in this system: the declared-retryable-set intersection
rule, `PVT-007`'s bound, idempotency gating, default-deny on an unrecognized failure, and a non-terminal,
resumable suspension with the reason recorded. This is stated here as a documented honest absence, not
discovered as a silent gap.

## 9. Single-host measurement and compressed time parameters

Every measurement, timing figure, and load number this build ever produces (deferred or otherwise) comes from
a single deployment serving both creation and redirect resolution on one host (AS-001) — never a
multi-instance or horizontally-scaled topology. Demonstration runs may also compress time-based parameters
(such as the gate-wait deadline, PVT-006) for practicality, labelled per AS-007 wherever that happens. Every
figure this project has ever produced or will produce is a demonstration measurement and must never be read
as a production statistic.

## 10. Meta-schema lint coverage

`ContractFilesLintTest` (T011) closes ADR-005's own previously-disclosed gap (contract files were
parse-validated once, 2026-09-20, but never checked for structural conformance) for the three governance
schemas (`approval.schema.json`, `audit-event.schema.json`, `policy-evaluation.schema.json` — compiled and
proven to reject an empty object) and for `workflow-state.schema.json` (a fuller structural check, including
the CR-029 field-removal assertions). `openapi.yaml` is covered separately, by `OpenApiConformanceTest` and
`ResponseExamplesTest`, not by this class.

## 11. Cross-checked against every ADR's own Risks section and every DF entry

Every ADR (`docs/governance/adr/ADR-001` through `ADR-014`) carries its own Risks and Mitigations table;
none is duplicated here. Every entry marked "Accepted and disclosed" in those tables is disclosed by that
ADR's own record, referenced rather than restated. Deferred Findings (`spec.md` §Deferred Findings): DF-001,
DF-002, DF-003, and DF-005 are all resolved (by ADR-014, CR-003, CR-017/CR-004, and the closing package
respectively); **DF-004 remains open by design** — a run-level retry circuit breaker and an email/webhook
expiry notification for suspended runs, both recorded and deliberately not adopted, out of scope for this
assessment.
