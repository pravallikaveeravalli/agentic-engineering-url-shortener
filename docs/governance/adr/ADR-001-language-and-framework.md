# ADR-001: Programming Language and Web Framework

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

The specification is deliberately technology-neutral (CN-002), and the constitution places technology
selection under human ownership, requiring an approved ADR before implementation proceeds. Nothing may
be built on an unapproved stack.

`ops/DECISION-LOG.md` entry #1 records a pre-setup *intent* toward Java + Spring Boot. Gate 1's
carried-forward enforcement explicitly says **no preference is to be treated as settled**, so that
entry is a candidate here, not an input that shortcuts the evaluation.

Relevant constraints: a 2–3 day timebox; the artifact must be defended live by the owner; 51
functional requirements spanning an application plane and a control plane; CL-009 requires surviving a
persistence-layer restart; FR-URL-006 and FR-URL-013 require correct short-code uniqueness under
genuine concurrency; NFR-TST-001 requires seven test categories including real-store integration.

**Assessment implication**: reviewers assess engineering judgment, not language fashion. The stack
matters mainly insofar as it lets governance, reliability, and evidence be demonstrated convincingly
inside the timebox — and insofar as the owner can defend every line of it.

## Decision Drivers

1. **Defensibility in live discussion** — the owner must explain any part of this on demand.
2. **Transactional and locking maturity** — needed for uniqueness under contention and for
   append-only audit guarantees.
3. **Test tooling for real dependencies and concurrency** — mocks cannot prove CL-009 or FR-URL-013.
4. **Iteration cycle time and review volume against a 2–3 day box** — see the owner's observation below;
   this driver is deliberately *not* framed as authoring speed.
5. **Reviewer setup burden** — a reviewer must be able to run everything.
6. **Migration tooling** — versioned schema is a required deliverable (plan §2).

### Owner observation on how velocity should be priced (recorded 2026-09-20)

The human owner corrected the framing of this driver before deciding the ADR's status:

> Option B's headline advantage — authoring velocity — is priced against human typing speed. In this
> project the implementation stage is AI-authored under my approved executor model (CL-003), and an AI
> generates Java boilerplate at essentially the same rate as Python, so raw authoring velocity largely
> collapses as a differentiator. What survives of the velocity argument is iteration cycle time only:
> JVM compilation, Spring context startup, and Testcontainers boot add seconds to every red-green
> cycle, and the authoring agent waits on builds like anyone else; verbose code also adds review volume
> at my checkpoints. That residual is real but far smaller than the ADR currently implies.

This observation is load-bearing for the comparison below, so the velocity claims in Options A and B and
in the Rationale are stated in these terms rather than in terms of authoring speed.

**Adopted corollary — static typing as a review layer.** Raised by the executing agent during this
revision, and **adopted by the owner into her acceptance rationale**: if authoring velocity collapses as a
differentiator, then weak static typing matters *more* in an AI-authored implementation, not less, because
the compiler is one of the very few reviewers that reads every line an AI produces. On that reading Java's
verbosity partly reverts from ceremony into a free, always-on review layer — which makes the case for
Option A materially stronger under CL-003 than it would be for a human-authored implementation.

Provenance is recorded deliberately: the owner **adopted** this point, she did not originate it. It is
part of her rationale because she weighed it and took it, and the distinction between adopting an agent's
argument and authoring one is exactly the kind of thing Principle X requires to stay legible.

## Options Considered

### Option A — Java 21 (LTS) + Spring Boot 3.x

- **Approach**: JVM service; Spring Web, Validation, Data JPA, Actuator; Flyway; JUnit 5 +
  Testcontainers.
- **Advantages**: deepest stack for this owner; strongest transaction/locking semantics of the four;
  Testcontainers makes real-Postgres testing routine; Flyway is first-class; Actuator gives
  liveness/readiness separation cheaply (FR-URL-015).
- **Disadvantages**: most ceremony per unit of behavior. Because the implementation stage is AI-authored
  (CL-003), that ceremony costs little in authoring time; it costs **iteration cycle time** — JVM
  compilation, Spring context startup, and Testcontainers boot on every red-green cycle — and it costs
  **review volume** at the owner's checkpoints, since more code has to be read to approve the same
  behavior.
- **Risks**: accumulated cycle time slowing the red-green loop enough to squeeze Slices 7–9, where the
  graded evidence lives; review volume slowing the owner's gate turnaround.
- **Implementation impact**: more files, more boilerplate; strong compile-time safety across a
  12-state machine and 29 entities.
- **Assessment implications**: a reviewer probing transactions, isolation, or concurrency gets
  substantive answers. Risk of looking heavyweight for a prototype.

### Option B — Python 3.12 + FastAPI

- **Approach**: ASGI service; Pydantic models; SQLAlchemy + Alembic; pytest + Testcontainers.
- **Advantages**: **faster iteration cycles** — no compilation step, no framework context startup, so the
  red-green loop is shorter and the authoring agent spends less time waiting on builds. **Lower review
  volume** at the owner's checkpoints for equivalent behavior. Pydantic gives schema validation close to
  the contract deliverables.

  Note what is *not* claimed: authoring velocity. Under CL-003 the implementation stage is AI-authored,
  and an AI emits Java boilerplate at essentially the same rate as Python, so "less code to write" is
  not a differentiator here. Option B's velocity advantage is **cycle time and review volume only** —
  real, but much narrower than the conventional framing suggests.
- **Disadvantages**: weaker static guarantees across a large state machine — and this matters more, not
  less, when code is AI-authored, because the compiler is one of the few reviewers that reads every line
  (see the adopted corollary under Decision Drivers); owner's depth is lower, which matters under live
  questioning.
- **Risks**: subtle state-machine errors surfacing only at runtime; async/sync mixing around the store.
- **Implementation impact**: fewer lines, more discipline needed around typing.
- **Assessment implications**: delivers a shorter feedback loop, which converts into more attempts per
  hour rather than into more code per hour. A real advantage that this ADR does not dismiss — but a
  smaller one than it would be for a human-authored implementation.

### Option C — TypeScript + NestJS

- **Approach**: Node service; Prisma or TypeORM; Jest.
- **Advantages**: strong typing; DI structure similar to Spring.
- **Disadvantages**: no advantage over A or B for this owner; migration and transaction ergonomics
  weaker than both.
- **Risks**: ORM transaction semantics less predictable under concurrency.
- **Assessment implications**: neutral to negative — hardest of the four to defend on the
  concurrency requirements.

### Option D — Go

- **Approach**: stdlib or chi; sqlc or plain SQL; goose migrations.
- **Advantages**: excellent concurrency primitives; small, fast, trivial to run.
- **Disadvantages**: most hand-rolled plumbing for 29 entities and a policy engine; weakest
  migration/ORM ecosystem of the four.
- **Risks**: time spent on plumbing rather than on the graded orchestration model.
- **Assessment implications**: attractive on concurrency, costly on breadth.

## Decision

**Java 21 (LTS) with Spring Boot 3.x**, using Spring Web, Bean Validation, Spring Data JPA,
Actuator, Flyway for migrations, and JUnit 5 with Testcontainers for tests.

## Rationale

Two requirements dominate. First, this artifact will be **defended live**, and the owner's depth is in
this stack — a defensible answer to "why does this hold under concurrent creation?" is worth more than
saved hours. Second, FR-URL-006, FR-URL-013, and CL-009 require proving behavior against a *real*
store under contention and across restarts, and this stack makes that testing ordinary rather than
exceptional.

Option B is recommended against **on defensibility grounds, and its counter-case is narrower than the
conventional framing implies.** The owner's observation above is the reason: velocity comparisons between
these stacks are normally priced against human typing speed, but under CL-003 the implementation stage is
AI-authored, and an AI emits Java boilerplate at essentially the same rate as Python. Raw authoring
velocity therefore largely collapses as a differentiator, and what survives is **iteration cycle time**
— compilation, framework startup, and Testcontainers boot on every red-green cycle, which the authoring
agent waits on like anyone else — plus **review volume** at the owner's own checkpoints.

That residual is real and should not be dismissed: a slower loop means fewer attempts per hour, and more
code to read means slower gate turnaround. But it is a materially smaller cost than "Option B would
deliver more of the plan", which is how the trade is usually stated and how an earlier draft of this ADR
stated it. Verbosity also cuts the other way once authoring is automated: static typing is one of the few
reviewers that reads every line an AI produces, which is worth more, not less, across a 12-state machine
and 29 entities.

The net effect is that the counter-case to Option A narrows, which **strengthens** the recommendation
rather than weakening it. The honest sentence still stands, with its basis corrected: if the owner weighs
velocity above defensibility, Option B remains a legitimate choice — but that velocity now rests on cycle
time and review volume, not on authoring speed, and the architecture in plan §1–11 survives the
substitution unchanged either way.

## Consequences

**Positive**: strong transactional guarantees; mature migration tooling; real-dependency testing is
low-friction; compile-time safety across the state machine; readiness/liveness separation is close to
free.

**Negative**: highest boilerplate of the four, which costs iteration cycle time (compilation, context
startup, container boot per red-green cycle) and review volume at the owner's checkpoints — not authoring
time, since implementation is AI-authored (CL-003).

**Operational**: JDK 21 plus Docker required; single JVM process; no additional runtime components.

**Testing**: Testcontainers spins a real Postgres per suite — slower tests, but the only way to make
CL-009's restart proof and FR-URL-013's concurrency proof real rather than simulated.

**Governance**: none adverse. The choice is recorded, alternatives and rejection reasons are stated,
satisfying Constitution VII and the Assessment Scope requirement.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Iteration cycle time accumulating and squeezing graded evidence | Fast unit tier kept container-free and context-free so the loop stays short; plan §14 stop conditions cut AI-mode breadth and backlog items **before** cutting governance, validation, or evidence |
| Review volume slowing the owner's gate turnaround | Small, reviewable commits (Constitution X); gate requests carry only what the decision needs, not whole diffs |
| Framework magic obscures the orchestration logic under review | Orchestration core written as plain objects with no framework annotations; dependency-direction test enforces it (ADR-006) |
| Slower test cycles discourage TDD | Fast unit suite kept separate from the container-backed suite so the red-green loop stays quick |

## Reversibility

**Low.** Reversing after Slice 1 means rewriting everything. This decision must be made **before
Slice 1 begins** and is the single most time-critical item at this gate.

## Traceability

- **Requirements**: all 51 FRs depend on this; most directly FR-URL-006, FR-URL-013, FR-URL-015,
  FR-ORC-004.
- **Specification**: CN-002 (no selection before ADR), CN-011 (no AI key required), NFR-MNT-001/002,
  NFR-TST-001.
- **Plan**: §12 ADR-001, Technical Context, §Project Structure, §14 Slice 1–2.
- **Expected tasks**: the Slice 1 engineering-baseline tasks (build setup, layout, migration harness,
  contract-test harness) and Slice 2 walking-skeleton tasks.
- **Related**: ADR-002 (store), ADR-006 (architecture), ADR-011 (testing), ADR-012 (execution model).
- **Supersedes nothing.** `DECISION-LOG` #1 is a candidate intent, not a prior decision.

## Validation

The Slice 2 walking skeleton validates the choice end to end within hours: one endpoint, health and
readiness, a real-store round trip, one migration applied from empty, and one contract test asserting
the response conforms to `contracts/openapi.yaml`. If that skeleton is not working by the end of Day 1
AM, the stack choice is the first thing to re-examine at the Day 1 scope-control checkpoint.
