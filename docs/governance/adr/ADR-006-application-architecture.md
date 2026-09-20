# ADR-006: Application Architecture and Plane Separation

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

Constitution VII requires domain logic, API delivery, persistence, orchestration, policy enforcement,
telemetry, and infrastructure to be separated into independently testable units, and forbids complexity
unjustified by requirements. NFR-MNT-001 makes the separation **verifiable** — it specifies a
dependency-direction check, with no domain dependency on delivery or persistence detail. NFR-MNT-002
requires external dependencies behind owned interfaces.

The system has two planes with different responsibilities: an application plane (the URL shortener) and
a control plane (the orchestration engine). Critically, under AS-005 the control plane governs *this
repository's own lifecycle*, and in DS-B it treats the application plane as **the codebase under
governance**, not as a library it calls. So the two planes must be genuinely separable, but they also
ship together.

Plan §Project Structure proposed a single project with an internal plane split. This ADR formalises that
and tests it against the alternatives.

**Assessment implication**: reviewers assess architecture and maintainability. A separation that is
claimed but not enforced is worth little; a separation enforced by heavy machinery may itself be the
unjustified complexity the constitution warns about.

## Decision Drivers

1. **Verifiable separation** — NFR-MNT-001 asks for a check, not a claim.
2. **Complexity justification** — Constitution VII.
3. **Independent testability** of domain logic without framework or store.
4. **Timebox**, and reviewer comprehension of the layout.
5. **Plane independence** — the control plane must be able to treat the application plane as a subject.

## Options Considered

### Option A — Single project, package-level plane split, automated dependency-direction test

- **Approach**: one build unit. Packages: `domain`, `application`, `delivery`, `persistence` for the
  application plane; `orchestration` (with `graph`, `state`, `executor`, `reliability`, `gates`,
  `replan`), `policy`, `audit` for the control plane; plus `config`. An automated architecture test
  asserts the dependency rules — `domain` depends on nothing framework- or persistence-related, and no
  control-plane package is depended upon by the application plane.
- **Advantages**: separation is machine-enforced, satisfying NFR-MNT-001 literally; one build, fast
  loop; a reviewer can read the whole thing in one tree; refactoring across the boundary is cheap while
  the design is still settling.
- **Disadvantages**: boundaries are enforced by a test rather than by the compiler, so a violation is
  caught at test time rather than at compile time.
- **Risks**: if the architecture test is weak or omitted, the separation degrades to convention.
- **Implementation impact**: lowest of the options; one test class carries the enforcement.
- **Assessment implications**: strong, provided the architecture test genuinely exists and fails on
  violation.

### Option B — Multi-module build with compile-enforced boundaries

- **Approach**: separate modules per plane and per layer; module dependencies express the rules.
- **Advantages**: strongest enforcement — a violation will not compile. Clearest statement of intent.
- **Disadvantages**: build complexity multiplies; cross-boundary refactoring becomes expensive at
  exactly the point in the timebox where the design is least settled; more configuration for a reviewer
  to navigate.
- **Risks**: time spent on build plumbing rather than on the graded orchestration engine.
- **Implementation impact**: significant build setup in Slice 1, the slice with least slack.
- **Assessment implications**: defensible and arguably more rigorous, but hard to justify against
  Constitution VII when a test achieves the required *verifiability* for a fraction of the cost.

### Option C — Two deployables (shortener service and orchestrator service)

- **Approach**: separate processes, communicating over HTTP.
- **Advantages**: maximal plane independence; the control plane's treatment of the application as a
  subject becomes physically obvious.
- **Disadvantages**: introduces a distributed system — network failure modes, two deployments, two
  configurations, cross-process transactions — none of which any requirement asks for. The plan
  explicitly commits to avoiding unjustified distributed complexity.
- **Risks**: an entire class of failure modes added for presentational benefit; directly contradicts
  Constitution VII.
- **Assessment implications**: **negative** — would be recorded as a `FAIL` against Principle VII in
  the plan's constitution check.

### Option D — Strict hexagonal with ports and adapters as separate modules

- **Approach**: Option B plus formal ports/adapters structure throughout.
- **Advantages**: textbook separation; every external dependency behind an explicit port.
- **Disadvantages**: highest ceremony; for 29 entities and a 12-node graph inside 2–3 days, the
  abstraction count exceeds what the requirements justify.
- **Risks**: over-abstraction obscuring the orchestration logic a reviewer needs to follow.
- **Assessment implications**: risks reading as pattern application rather than judgment.

## Decision

**Option A.** A single deployable and single build unit, with an internal plane split at package level,
and an **automated dependency-direction test** enforcing the rules. Specifically:

- `domain` has no dependency on any framework, persistence, delivery, or control-plane package.
- `application` depends on `domain` and on repository *interfaces* only.
- `persistence` and `delivery` depend inward; nothing depends on them except `config`.
- Control-plane packages (`orchestration`, `policy`, `audit`) do not depend on application-plane
  internals; where DS-B requires the control plane to act on the application code, it does so as a
  subject — through the filesystem, the build, and the test suite — not by importing it.

## Rationale

NFR-MNT-001 asks for separation that is **checked**, and an architecture test satisfies that literally
and cheaply. Option B's compile-time enforcement is stronger in principle, but the marginal gain over an
automated check does not justify multiplying build complexity inside a 2–3 day box — and Constitution VII
would question that multiplication.

Option C is the one to reject firmly. Splitting into two services would make plane independence visually
obvious while importing a distributed system's entire failure surface for no requirement. The plan
already commits to avoiding exactly this, and the constitution treats unjustified complexity as a
compliance failure rather than a matter of taste.

The subtle point worth recording: the control plane's independence from the application plane is
**semantic, not physical**. DS-B requires the orchestrator to govern the shortener's code — analysing
impact, applying patches on a branch, running its real suite. That relationship is one of subject and
governor, and it works within one process precisely because the control plane reaches the application
through the filesystem and the build rather than through imports.

## Consequences

**Positive**: separation is enforced and demonstrable; single fast build; domain unit-testable with no
container or framework; cheap refactoring while the design settles.

**Negative**: boundary violations surface at test time rather than compile time; discipline depends on
the architecture test continuing to exist and to be meaningful.

**Operational**: one process, one artifact, one configuration. `quickstart.md` stays short.

**Testing**: the architecture test becomes a first-class test, not an optional extra. Domain tests need
no container, which keeps the red-green loop fast under ADR-002's container-backed integration suite.

**Governance**: satisfies Constitution VII's separation requirement and NFR-MNT-001's verifiability; the
rejection of Option C is itself the Principle VII justification for the shape chosen.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Architecture test omitted or weakened under time pressure | It is a Slice 1 deliverable, before there is any code to violate the rules; plan §14 forbids cutting validation |
| Package boundaries eroding as slices accumulate | The test runs in the standard suite, so erosion fails a build rather than accruing silently |
| Control plane accidentally importing application internals for convenience | Explicit rule in the architecture test; DS-B's subject relationship is via filesystem and build, which the test can assert |
| Reviewer reads "one project" as insufficient separation | This ADR is the answer, and the architecture test is the evidence |

## Reversibility

**Moderate to high.** Package-level boundaries that are already enforced by a test are the natural
precursor to Option B: extracting modules later is mostly mechanical precisely because the dependency
rules are already clean and verified. Choosing A now does not foreclose B.

## Traceability

- **Requirements**: NFR-MNT-001 (separation with dependency-direction check), NFR-MNT-002 (owned
  interfaces), FR-ORC-020 (brownfield impact analysis over the application plane), FR-ORC-028 (single
  executor interface).
- **Specification**: AS-005 (governs this repository), DS-B, CN-008, §Reading Guide's ORC/URL split.
- **Plan**: §1 plane responsibilities, §Project Structure and its Structure Decision, §Complexity
  Tracking.
- **Expected tasks**: Slice 1 project layout and architecture test; every subsequent slice places code
  within these boundaries.
- **Related**: ADR-001 (framework), ADR-002 (repository interfaces), ADR-003 (orchestration core as
  plain objects), ADR-012 (single deployable).

## Validation

The architecture test is the validation and is executable: it asserts `domain` has no outbound dependency
on framework, persistence, delivery, or control-plane packages, and that the application plane does not
depend on control-plane packages. It must be demonstrated to **fail** on a deliberately introduced
violation — an unfalsifiable architecture test is worth no more than a comment. Domain unit tests running
with no container and no framework context are the secondary proof that the separation is real.
