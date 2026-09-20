# ADR-011: Testing Strategy

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

Constitution IV mandates red-green-refactor wherever technically practical, requires the failing test to be
**observed to fail for the expected reason**, and states that a task is complete only when validation has
been *executed* — generated code with unexecuted tests is not complete. NFR-TST-001 requires seven
categories: unit, integration, API contract, orchestration state transition, reliability, security, and
end-to-end. NFR-TST-002 requires the red phase to be **evidenced, not asserted**. NFR-TST-003 proposes
≥85% branch coverage of domain and orchestration transition logic (PVT-008, unapproved).

Two constraints shape the approach decisively. **FR-ORC-030** requires reliability proofs — retry, timeout,
fallback, rollback, compensation, safe-stop, resumption, replanning — to be driven by injectable scriptable
executors so they are deterministic. **NFR-AUT-004 / SC-014** require the suite to pass with **no AI key and
no network access**.

**Assessment implication**: testing discipline is assessed on evidence of the cycle, not on a final coverage
number, which can be produced retroactively.

## Decision Drivers

1. **Can the test prove the property it claims?** — the governing question.
2. **Determinism of reliability evidence** — FR-ORC-030.
3. **Keyless, network-free execution** — CN-011, NFR-AUT-004.
4. **Red-phase evidencing** — NFR-TST-002.
5. **Fast inner loop** so TDD is actually practised rather than nominally claimed.
6. **Timebox.**

## Options Considered

### Option A — Real dependencies for data paths, injected fakes for executors

- **Approach**: real Postgres via Testcontainers for persistence, integration, concurrency, and resumption
  suites. Scriptable fake executors (`fail twice then succeed`, `timeout`, `return malformed envelope`,
  `propose transient for an undeclared category`) for every reliability proof. Live AI exercised **only** in
  labelled demonstration runs, never in the graded suites. Domain unit tests with no container and no
  framework context.
- **Advantages**: each property is proven by the mechanism that can actually prove it — uniqueness under
  contention needs a real unique constraint; restart recovery needs a real restartable store; retry
  semantics need a deterministically failing executor. Satisfies FR-ORC-030 and NFR-AUT-004 directly. Fast
  unit tier keeps the red-green loop quick despite the container tier.
- **Disadvantages**: two test tiers to maintain; container tier is slower.
- **Risks**: container tier gets skipped under time pressure, silently weakening evidence.
- **Implementation impact**: harness in Slice 1; fakes in Slice 4–6.
- **Assessment implications**: strongest — the evidence supports the claims made from it.

### Option B — Mocks throughout

- **Approach**: mock the repository layer and all collaborators; no real store.
- **Advantages**: fastest; no Docker; simplest setup.
- **Disadvantages**: **cannot prove the properties that matter here.** A mocked repository cannot exhibit a
  unique-constraint violation under concurrency (FR-URL-006, EC-001), cannot be restarted (CL-009, EC-038),
  and cannot demonstrate that an UPDATE against an audit table is rejected (ADR-010). Those tests would pass
  while proving nothing about the real system.
- **Risks**: green evidence for untested properties — the failure mode Principle X treats most seriously.
- **Assessment implications**: negative. Coverage would look fine and mean little.

### Option C — In-memory store substitute for all tests

- **Approach**: swap an in-memory implementation behind the repository interfaces.
- **Advantages**: fast; no Docker.
- **Disadvantages**: contradicts the *purpose* of CL-009, which the owner adopted precisely because an
  in-memory store makes the recovery guarantee nominal. Using one in tests reproduces the loophole inside
  the evidence: the recovery test would pass against a store that cannot be restarted.
- **Risks**: the approved clarification satisfied in letter and defeated in substance.
- **Assessment implications**: negative, and specifically inconsistent with an approved decision.

### Option D — Live AI in the graded suites

- **Approach**: exercise the real provider in orchestration and reliability tests.
- **Advantages**: tests the real integration path.
- **Disadvantages**: **disqualified by FR-ORC-030 and NFR-AUT-004.** Reliability proofs would depend on AI
  availability, latency, and variability; the same test could pass and fail across runs; the suite would
  require a key and a network, breaching CN-011.
- **Risks**: non-reproducible evidence for the behaviours most central to the assessment.
- **Assessment implications**: disqualifying.

## Decision

**Option A**, structured as two tiers plus a demonstration tier:

| Tier | Contents | Dependencies |
|---|---|---|
| **Fast** | Domain unit, state-machine transitions, failure classification, architecture test | None — no container, no framework, no network |
| **Integration** | Persistence, API contract, concurrency, security, resumption, orchestration, gates, reliability, replanning, end-to-end | Real Postgres via Testcontainers; **scriptable fake executors**; no AI, no network |
| **Demonstration** | DS-A / DS-B / DS-C in AI mode | AI provider; explicitly labelled demonstration runs, never the basis of a reliability claim |

**Red-phase evidencing** (NFR-TST-002): each TDD task records the failing test run's output as an evidence
artifact before the implementing change, and commit granularity shows red preceding green. Where the red
phase is genuinely impractical for a unit of work, the reason is recorded against that task and executed
validation is still required (Constitution IV's own allowance).

**Prohibited-transition testing** is first-class: every prohibited transition in ADR-008's state machine is
asserted **rejected**, not merely absent from the happy path.

**Deliberate-failure tests** validate the harnesses themselves: the drift test for the contract harness
(ADR-005), the failing-UPDATE test for audit immutability (ADR-010), and a violation test for the
architecture rules (ADR-006). A harness that has never failed is unvalidated.

## Rationale

The organising principle is that **a test must be capable of failing for the reason it claims to check**.
That single criterion disqualifies B, C, and D on specific grounds rather than on preference: mocks cannot
exhibit constraint violations, in-memory stores cannot be restarted, and a live AI cannot produce
deterministic reliability evidence.

Option C deserves the sharpest note because it is the most tempting. CL-009 was adopted precisely because an
in-memory store satisfies durability wording while protecting nothing; using one in the test suite would
reproduce that loophole inside the evidence, which is worse than having no test — it manufactures confidence.

The two-tier split exists so that TDD is genuinely practicable. If every red-green cycle required a
container, the cycle would be abandoned under timebox pressure and the discipline would become a claim rather
than a practice.

## Consequences

**Positive**: evidence supports the claims drawn from it; reliability proofs are deterministic and run
offline; the inner loop stays fast; harnesses are themselves validated.

**Negative**: two tiers to maintain; the integration tier is slower; fake executors are additional code
whose own correctness matters.

**Operational**: Docker required for the integration tier; nothing required for the fast tier. `quickstart.md`
already states this.

**Testing**: coverage is measured against PVT-008 for domain and orchestration transition logic, excluding
generated and infrastructure code — recorded as a measurement, never as the definition of done, which
remains Principle XI's eleven-point standard.

**Governance**: `POL-TST-001` (required categories non-empty and executed) becomes an executable check. A
generated-but-unexecuted test can never count as passing, since `TestResult.executed_at` is null for it
(data-model invariant).

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Integration tier skipped under time pressure | Plan §14 forbids cutting mandatory validation; stop condition 2 blocks release readiness on unexecuted validation |
| Fake executors diverge from real executor behaviour | Both implement the same single interface (FR-ORC-028); the interface contract is itself unit-tested |
| Coverage treated as the definition of done | Principle XI is the definition; coverage is one measurement among several, and PVT-008 is unapproved |
| Red phase claimed but not evidenced | Failing-run output stored as an evidence artifact; absence is visible in the traceability report |
| Flaky concurrency tests eroding trust | Deterministic barriers rather than sleeps; forced-collision test uses a stubbed generator rather than relying on chance |

## Reversibility

**High.** Test-side decisions carry no production consequence. Adding a live-AI contract test later, or
adopting a different container strategy, changes no application code.

## Traceability

- **Requirements**: NFR-TST-001 (seven categories), NFR-TST-002 (evidenced red phase), NFR-TST-003
  (coverage), FR-ORC-030 (injectable executors), FR-URL-006/013 (concurrency), FR-ORC-004/018 (resumption),
  FR-ORC-025 (release-readiness negative tests), SC-014.
- **Specification**: CL-006, CL-007, CL-009, CN-011, NFR-AUT-004, EC-001, EC-031..EC-039.
- **Plan**: §10 (all categories), §14 every slice's executed-tests exit condition, stop condition 2.
- **Expected tasks**: Slice 1 harnesses and architecture test; Slices 3–9 tests accompanying each behaviour;
  Slice 9 the nine release-readiness negative tests.
- **Related**: ADR-002 (real store), ADR-004 (AI excluded from graded suites), ADR-005 (drift test),
  ADR-006 (architecture test), ADR-010 (immutability test).

## Validation

The strategy validates itself through its deliberate-failure tests: the contract drift test, the audit
failing-UPDATE test, and the architecture violation test must each be demonstrated to fail when the property
they guard is broken. Beyond that, `POL-TST-001` asserts every required category is non-empty and executed,
and SC-014 is proven by running the full fast and integration tiers in a key-less, network-isolated
environment.
