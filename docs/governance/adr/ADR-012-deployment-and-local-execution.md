# ADR-012: Deployment and Local Execution Model

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

EX-007 places production deployment, production monitoring, and real-traffic operation **out of scope**. AS-001
assumes a single deployment serving both creation and redirect resolution, with no multi-region or
multi-tenant topology. AS-009 records that all measurements come from a single developer machine, and that
every figure is therefore a demonstration measurement.

What is in scope is that a reviewer can **run the whole thing**, including the specific demonstrations that
CL-009 requires: interrupting a run and restarting the orchestrator process, and separately restarting the
persistence layer while a run is in flight.

FR-URL-019 adds a constraint that shapes the operational surface: creator provisioning is a **local operator
script**, never an HTTP endpoint, because the ability to run commands on the host *is* the trust boundary.

**Assessment implication**: a submission a reviewer cannot run is a submission they cannot verify. Setup
burden is therefore a governance concern, not a convenience one.

## Decision Drivers

1. **Reviewer can run everything**, including both restart demonstrations.
2. **Store must be independently restartable** — CL-009's proof depends on it.
3. **Minimal setup burden** — every prerequisite is a chance for a reviewer to stop.
4. **Fast development loop** inside a 2–3 day box.
5. **No production infrastructure** — EX-007, Constitution VII.
6. **Operator script must be runnable** without the application being up.

## Options Considered

### Option A — Docker Compose for the store only; application runs on the host

- **Approach**: `docker compose up -d` starts Postgres. The application runs via the build tool on the host.
  The operator provisioning script runs on the host against the store.
- **Advantages**: fastest development loop — no image rebuild per change. `docker compose restart db` is a
  clean, obvious mechanism for CL-009's persistence-restart demonstration. Killing and restarting the
  application process for the other restart class is trivial. Minimal Compose file. Debugger attaches
  naturally.
- **Disadvantages**: the reviewer needs both Docker **and** a JDK (pending ADR-001); "works on my machine"
  risk is higher than with full containerisation.
- **Risks**: toolchain version mismatch on a reviewer's machine.
- **Implementation impact**: smallest.
- **Assessment implications**: good, provided prerequisites are stated plainly.

### Option B — Full containerisation: application and store both in Compose

- **Approach**: both services in Compose; `docker compose up` starts everything.
- **Advantages**: most reproducible — one prerequisite (Docker) and one command. No toolchain version
  concerns. Closest to how a service would actually ship.
- **Disadvantages**: image rebuild on every change slows the inner loop materially across a 2–3 day box.
  Debugging is more awkward. The process-restart demonstration becomes `docker compose restart app`, which is
  fine, but the development cost is paid continuously while the benefit is realised once, by the reviewer.
- **Risks**: timebox erosion from rebuild cycles; more Compose configuration to get right early.
- **Implementation impact**: moderate — Dockerfile, layer caching, wiring.
- **Assessment implications**: strongest on reproducibility, and genuinely attractive for exactly that reason.

### Option C — Everything on the host, embedded store

- **Approach**: no Docker; embedded or file-based store.
- **Advantages**: no container prerequisite at all.
- **Disadvantages**: **contradicts ADR-002 and CL-009** — an embedded store has no independently restartable
  process, so the persistence-restart proof cannot be performed at all.
- **Risks**: an approved requirement becomes undemonstrable.
- **Assessment implications**: disqualified by an upstream decision.

### Option D — Kubernetes manifests or a production-shaped deployment

- **Approach**: k8s manifests, Helm, or similar.
- **Advantages**: looks production-grade.
- **Disadvantages**: EX-007 excludes production deployment; Constitution VII forbids complexity no requirement
  justifies; and it raises the setup burden enormously for a single-host demonstration.
- **Risks**: would be recorded as a Principle VII `FAIL`; reviewer likely cannot run it.
- **Assessment implications**: negative — it confuses production-*scale* infrastructure with
  production-*grade* discipline, the exact distinction the plan is asked to maintain.

## Decision

**Option A** for development and for the graded demonstrations:

- `docker compose up -d` starts **Postgres only**.
- The application runs on the host via the build tool.
- The operator provisioning script (FR-URL-019) runs on the host and requires only store connectivity — not a
  running application, which is correct, since an HTTP issuance surface is explicitly prohibited.
- **Restart demonstrations**: `docker compose restart db` for the persistence-layer class; stopping and
  restarting the application process for the orchestrator class.
- **Option B is recorded as a backlog item**, not adopted: a full-stack Compose profile purely for reviewer
  convenience, to be added only if Slice 9 completes with slack. It must never displace mandatory validation
  or evidence (plan §14).

## Rationale

Option B is the genuine contender and the disagreement is narrow: it is better for the reviewer and worse for
the timebox. The deciding consideration is that the reviewer's difficulty under Option A is **one extra
documented prerequisite**, while the developer's cost under Option B is **an image rebuild on every
iteration for two to three days** — paid continuously, against a benefit realised once. Given that plan §14's
stop conditions already treat timebox erosion as the primary risk to graded evidence, the trade favours A,
with B available as a later convenience rather than an upfront cost.

Option C is disqualified upstream rather than on preference: without an independently restartable store there
is no way to perform CL-009's proof, and the owner adopted that requirement specifically to close the
in-memory loophole.

Option D is worth stating plainly because it is a common instinct: production manifests would demonstrate
production-*scale* infrastructure, which EX-007 excludes, rather than production-*grade* discipline, which is
what is actually assessed and which is delivered through contracts, migrations, policy gates, traceability,
and executed evidence.

## Consequences

**Positive**: fast inner loop; both restart demonstrations trivially performable; minimal configuration; the
operator script's independence from the application is naturally preserved.

**Negative**: two prerequisites for a reviewer (Docker plus a JDK); higher environment-variance risk than
full containerisation.

**Operational**: one Compose file, one store container, one host process. No orchestration platform, no
service mesh, no ingress. Configuration via environment variables with secure defaults — throttling on, AI
mode off, verbose error detail off, readiness failing closed.

**Testing**: Testcontainers manages its own store instance, so the test suite does not depend on the
developer's Compose stack being up. The fast tier needs neither.

**Governance**: consistent with EX-007 and AS-001. Every measurement taken in this environment is a
demonstration measurement and must be labelled as such (AS-009, Constitution IX) — this decision is the
reason that labelling obligation exists.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Reviewer lacks Docker or the right JDK | `quickstart.md` states both prerequisites in its first table; the fast test tier runs without Docker |
| "Works on my machine" divergence | Testcontainers pins the store image; build tool wrapper pins its own version; Option B remains available as the reproducibility escape hatch |
| Compose stack and Testcontainers instance confused for one another | Distinct ports and database names, documented |
| Demonstration figures mistaken for production statistics | Every metric output labelled with its population, conditions, and demonstration status (plan §7) |
| Backlog Option B displacing mandatory work | Plan §14 explicitly prohibits backlog items from displacing validation or evidence |

## Reversibility

**High.** Adding a Dockerfile and an application service to the existing Compose file is additive and touches
no application code. Nothing in the design depends on where the process runs.

## Traceability

- **Requirements**: FR-URL-015 (health and readiness), FR-URL-019 (operator-script provisioning),
  FR-ORC-004 and FR-ORC-018 (both restart classes), NFR-REC-001, SC-014 (keyless, network-free run).
- **Specification**: AS-001, AS-009, EX-007, CN-008, CL-009, EC-038.
- **Plan**: Technical Context (target platform), §12 ADR-002's Docker dependency, §14 Slices 1–2,
  §Project Structure (`ops/scripts/`).
- **Expected tasks**: Slice 1 Compose file, configuration defaults, operator script skeleton; Slice 6 the two
  restart demonstrations.
- **Backlog**: full-stack Compose profile (Option B) for reviewer convenience.
- **Related**: ADR-001 (toolchain prerequisite), ADR-002 (store as separate process), ADR-011 (Testcontainers
  independence).

## Validation

Validated by `quickstart.md` being followed end to end on a clean machine: `docker compose up -d`, run the
suite, provision a creator, create and follow a link, then perform both restart demonstrations and observe
correct resumption with committed effects occurring exactly once. SC-014 is validated by running the fast and
integration tiers with no AI key and no network.
