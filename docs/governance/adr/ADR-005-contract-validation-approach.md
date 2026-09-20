# ADR-005: API Contract Validation Approach

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4, with the JSON Schema count
corrected from five to four. Decision record: `docs/governance/gate-decisions/gate-04-adr.md`.

## Context

Gate 1 carried forward a requirement due at the Plan gate: **versioned API and schema deliverables with
contract validation**. Plan §2 names seven such deliverables — the OpenAPI document plus workflow-state,
approval, audit-event, policy-evaluation, and persistence schemas — and `policy-set-1.0.0` makes
`POL-CHG-001` a mandatory release-blocking check on change-control records for contract changes.

The question here is narrow but consequential: by what mechanism does the contract become **falsifiable**
— able to fail a build when implementation and contract diverge — rather than decorative documentation
that drifts silently.

**Assessment implication**: a contract nobody can violate is not a contract. Reviewers will look for
whether drift is detectable, not merely whether a spec file exists.

## Decision Drivers

1. **Can it fail a build on drift?** — the only question that really matters.
2. **Is the versioned artifact a human-readable deliverable?** — required by the Gate 1 carry-forward.
3. **Effort inside the timebox.**
4. **Coverage across both the HTTP contract and the four JSON schemas.**
5. **Clarity for a reviewer reading the contract cold.**

## Options Considered

### Option A — Specification as source of truth, with executable response validation

- **Approach**: `contracts/openapi.yaml` is hand-maintained and authoritative. Contract tests exercise
  live endpoints and validate actual responses against the document. The four JSON Schemas validate
  persisted snapshots, gate decisions, audit records, and policy evaluations.
- **Advantages**: drift fails the build, in both directions — an undocumented response shape or a
  documented-but-unimplemented field is caught. The versioned artifact is human-readable and reviewable.
  Works uniformly for the HTTP contract and the four schemas.
- **Disadvantages**: the document is maintained by hand, so it can lag until a test catches it.
- **Risks**: validation configured too loosely (for example ignoring `additionalProperties`) would give
  false confidence.
- **Implementation impact**: one validation harness in Slice 1; schemas already authored in Phase 1.
- **Assessment implications**: strong — the contract is demonstrably enforced.

### Option B — Generate code from the specification

- **Approach**: OpenAPI generator produces server interfaces; implementation fills them.
- **Advantages**: the contract cannot drift from the signatures, because the signatures are generated.
- **Disadvantages**: generated-code toolchain, build plumbing, and generated artifacts in the tree —
  heavier than a 2–3 day box justifies. Generation constrains the HTTP layer's shape and does not cover
  the four JSON Schemas at all. It also guarantees only *signature* conformance, not response-body
  conformance, so response validation is still wanted.
- **Risks**: toolchain friction consuming Slice 1; generated code confusing a reviewer about authorship.
- **Assessment implications**: neutral — buys signature safety at disproportionate cost.

### Option C — Generate the specification from code (annotation-derived)

- **Approach**: annotate controllers; export OpenAPI at build time.
- **Advantages**: zero maintenance; the document always matches the code.
- **Disadvantages**: **drift becomes undetectable by construction.** The specification becomes whatever
  the code says, so a breaking change silently rewrites the contract instead of failing anything. It
  also cannot express intent the code does not carry — the compatibility rules, the change-approval
  requirement, or a deliberate `404`-rather-than-`403` decision.
- **Risks**: a MAJOR-breaking change ships as a documentation update; `POL-CHG-001` becomes
  unenforceable because there is no independent contract to violate.
- **Assessment implications**: **materially negative** — it produces the appearance of a contract
  deliverable with none of the control.

### Option D — Review-based conformance, no executable validation

- **Approach**: maintain the document; rely on code review to keep it accurate.
- **Advantages**: no harness effort.
- **Disadvantages**: unenforceable; Principle XI treats unexecuted validation as incomplete.
- **Risks**: contract accuracy decays invisibly.
- **Assessment implications**: fails the Gate 1 carry-forward, which asked for contract *validation*.

## Decision

**Option A.** `contracts/openapi.yaml` (OpenAPI 3.1) is the authoritative, hand-maintained source of
truth, and contract tests validate live responses against it. The four JSON Schemas in `contracts/`
validate persisted workflow-state snapshots, gate decisions, audit records, and policy-evaluation
output. Persistence schema is versioned separately as forward-only Flyway migrations.

A **deliberate drift test** is part of the deliverable: a known divergence is introduced and the contract
test must fail. Without that, the harness itself is unvalidated.

## Rationale

Option C is the instructive rejection, and it is the option most projects drift into. Deriving the spec
from the code makes the contract unfalsifiable: there is no independent statement left for the
implementation to violate, so `POL-CHG-001`'s mandatory check has nothing to check, and a breaking
change presents as a documentation diff. The Gate 1 carry-forward asked for contract *validation*, which
presupposes something independent to validate against.

Option A also has the property that the four JSON Schemas — which cover the governance artifacts, not
just HTTP — are validated by the same discipline. Those schemas carry real enforcement: `approval.schema.json`
structurally requires a human actor for an `APPROVED` outcome and requires a materialized
`repositoryRecordPath`; `audit-event.schema.json` requires all six mandatory fields;
`policy-evaluation.schema.json` permits exactly four outcome values. Those constraints are governance
controls expressed as schema, and they only bite if something validates against them.

## Consequences

**Positive**: drift fails the build; the contract is reviewable as a document; governance constraints are
machine-checked; `POL-CHG-001` becomes enforceable.

**Negative**: hand-maintenance means the document can briefly lag the code until a test catches it.

**Operational**: none — validation runs in the test suite, not at runtime.

**Testing**: adds a contract test category (already required by NFR-TST-001) plus schema-validation tests
over persisted artifacts. Includes the deliberate-drift test that validates the harness.

**Governance**: `POL-CHG-001` (change-control record for contract changes) and `POL-AUD-001` (six
mandatory audit fields) both become executable checks rather than review items. Every contract change
requires the eight-field change-impact record in `docs/governance/change-control/`.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Validation too permissive, giving false confidence | `additionalProperties: false` on request/response schemas; the deliberate-drift test proves the harness detects divergence |
| Document lags implementation between commits | Contract test runs in the standard suite, so the lag cannot survive a green build |
| Hand-authored schemas contain syntax errors | **Open item**: parse validity of the Phase 1 contract files has *not* been executed — see §Validation |
| Schema evolution breaks old persisted snapshots | Schemas versioned; compatibility rules in `contracts/README.md`; forward-only migrations |

## Reversibility

**High.** The harness is test-side. Moving to Option B later would add generation without invalidating
the document or the schemas; the document remains the source either way.

## Traceability

- **Requirements**: NFR-TST-001 (API contract tests), FR-ORC-013 and FR-ORC-023 (via approval and
  audit schemas), FR-ORC-022 (policy evaluation schema), FR-ORC-004 (workflow-state schema).
- **Specification**: CN-006 (HTTP as domain-intrinsic, status codes deferred), NFR-AUD-001, NFR-MNT-002.
- **Plan**: §12 ADR-005, §2 versioned deliverables table and compatibility rules, §9 `POL-CHG-001`,
  §14 Slice 1.
- **Gate carry-forward**: Gate 1 — "versioned API/schema deliverables with contract validation", due at
  the Plan gate.
- **Expected tasks**: Slice 1 contract-test harness and deliberate-drift test; Slice 4–7 schema
  validation over persisted artifacts.
- **Related**: ADR-001 (harness availability), ADR-010 (audit model), ADR-014 (analytics — affects what
  the analytics tests assert, not the wire contract).

## Validation

The harness is validated by the deliberate-drift test: a response field is changed without updating the
document, and the contract test must fail. If it passes, the harness is not actually validating.

**Disclosed gap**: the Phase 1 contract files (`openapi.yaml` and the four JSON Schemas) have **not been
parse-validated** — attempts to execute a parser in the authoring session were refused by the environment's
permission layer. They are hand-written and unverified. First task in Slice 1 is to parse and schema-lint
all five files; any defect found is a defect in the Phase 1 artifacts, not in this decision. This is
recorded rather than assumed away because Principle X forbids presenting unverified work as verified.
