# ADR-002: Persistence Strategy

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

CL-009 (Gate 3) settled the *requirement*: a run must survive both an orchestrator-process restart and
a **restart of the persistence layer itself**, and a disk-backed store is therefore mandatory. The
owner's reasoning was recorded: process-only durability is not merely cheap, it is the loophole — an
in-memory store technically satisfies the older wording while making the recovery guarantee nominal.

What remains open is *which* disk-backed store. The store must also support FR-URL-006's uniqueness
guarantee under concurrent creation (EC-001), FR-URL-013's concurrent-resolution invariants, and the
append-only immutability that NFR-AUD-001 requires of audit records.

**Assessment implication**: a reviewer will ask how uniqueness is guaranteed under concurrency and how
restart recovery was proven. Both answers depend on this choice, and a store that cannot exhibit
contention makes the corresponding tests weaker than they appear.

## Decision Drivers

1. **Restartability as a separate process** — CL-009's test needs something that can actually be
   restarted independently of the application.
2. **Genuine concurrency semantics** — unique-constraint enforcement under real contention.
3. **Append-only enforceability** — ability to withhold UPDATE/DELETE rights from the app role.
4. **Test ergonomics** — can the suite run the real store reproducibly?
5. **Reviewer setup burden.**
6. **Transactional isolation guarantees.**

## Options Considered

### Option A — PostgreSQL 16 in Docker Compose (Testcontainers in tests)

- **Approach**: containerized Postgres; Flyway migrations; Testcontainers-managed instance per suite.
- **Advantages**: restartable as an independent process, which is exactly what CL-009's proof needs;
  real row-level locking and unique-constraint behavior under contention; role-level privilege control
  makes audit immutability an enforced grant rather than a convention; mature isolation semantics.
- **Disadvantages**: reviewers need Docker; container startup adds seconds to the suite.
- **Risks**: Docker unavailable on a reviewer's machine blocks everything.
- **Implementation impact**: one Compose file; repository interfaces keep the rest of the code
  store-agnostic.
- **Assessment implications**: strongest answers to concurrency and recovery questions.

### Option B — SQLite in file mode

- **Approach**: single file; no server process.
- **Advantages**: zero setup; genuinely disk-backed, so it satisfies CL-009's literal wording; fastest
  tests.
- **Disadvantages**: **embedded — there is no store process that can die independently of the
  application**, so CL-009's persistence-restart demonstration cannot be performed at all. **No privilege
  system**, so audit append-only can only ever be a convention rather than an enforced denial.
  Additionally, coarse write locking serializes writers, so concurrent creation does not contend.
- **Risks**: two required demonstrations become impossible rather than merely weaker — the store cannot be
  killed mid-run, and an UPDATE against an audit table cannot be refused by anything except application
  code.
- **Implementation impact**: simplest.
- **Assessment implications**: the CL-009 and audit-immutability demonstrations have nothing to run
  against, and no concurrency evidence can be produced because no concurrency occurs.

### Option C — H2 in file mode

- **Approach**: embedded Java database, persisted to disk.
- **Advantages**: no Docker; familiar in the Spring ecosystem; disk-backed.
- **Disadvantages**: same structural weaknesses as B — embedded, so no independent process to restart,
  and no meaningful privilege separation. Also carries less production credibility than either A or B.
- **Risks**: as B, plus behavioral divergence from the store a real deployment would use.
- **Assessment implications**: weakest of the three on defensibility.

### Option D — PostgreSQL, embedded/managed binary (e.g. embedded-postgres)

- **Approach**: real Postgres binary managed by the test harness, no Docker.
- **Advantages**: real Postgres semantics without Docker; restartable.
- **Disadvantages**: extra dependency, platform-specific binaries, less predictable across machines.
- **Risks**: environment-specific failures that cost timebox to diagnose.
- **Assessment implications**: equivalent to A technically; worse operationally.

## Decision

**PostgreSQL 16, run via Docker Compose for development and demonstration, and via Testcontainers in
the test suite.** All access is behind repository interfaces owned by this codebase (NFR-MNT-002).

## Rationale

### Correction to an earlier framing (owner, 2026-09-20)

An earlier draft of this Rationale rejected Option B partly on the grounds that it "would produce green
tests for properties that were never actually tested." The owner rejected that reasoning as misdirected and
required it removed:

> The "green evidence for an untested property" framing is a critique of test design, not of the database.
> If SQLite were otherwise the right choice, the correct remedy would be honest tests that state exactly
> what they exercise — uniqueness under serialized writes — plus a recorded design argument that no write
> concurrency exists by construction. A database is not rejected to escape misleading test naming; test
> naming is ours to fix.

That is correct, and it changes the weighting rather than the outcome. Test honesty is within our control
and would have been exercised either way, so it cannot carry the decision. The Rationale below rests only
on properties of the stores themselves.

### Deciding grounds for Option A over Option B — exactly two

**1. Option B is embedded, so the CL-009 demonstration is physically impossible.** There is no store
process that can die independently of the application. The demonstration CL-009 requires — the store killed
mid-run, the application observing `UNAVAILABLE`, bounded retries under the two-vote rule, suspension on
exhaustion, and correct resumption when the store returns — has nothing to kill. This disqualifies Option B
against a rule the owner herself wrote at Gate 3, and no amount of test craft substitutes for a process that
does not exist.

**2. Option B has no privilege system, so audit append-only can only ever be a convention.** NFR-AUD-001
requires audit records to be immutable after write. Under Option A that is a database-enforced denial: the
application role is granted INSERT and SELECT on audit tables and denied UPDATE and DELETE, and a test
proves an UPDATE is **rejected by the store**. Under Option B the same property can only be a promise kept
by application code — which is precisely the class of guarantee that fails when it matters.

### Subordinate: concurrency evidence

Retained in its honest form and ranked below the two grounds above. The specification requires *demonstrated*
behavior under genuine concurrency — FR-URL-013's evidence obligation, and EC-017's parallel-branch writes to
a shared downstream artifact. A single-writer embedded store cannot produce that evidence at any level of test
honesty, because the demonstrations simply cannot occur: serialized writers do not contend, so there is no
contention to observe. This is a statement about what the store can exhibit, not about how tests would be
named.

Options C and D are rejected on the grounds already stated in their entries.

## Consequences

**Positive**: restart recovery and concurrency are provable rather than asserted; audit immutability is
privilege-enforced; isolation semantics are well understood and defensible.

**Negative**: Docker becomes a hard prerequisite; the container-backed suite is slower than an
in-process store would be.

**Operational**: one additional process. `docker compose restart db` is also the demonstration
mechanism for CL-009, so the operational dependency doubles as the proof harness.

**Testing**: persistence, integration, concurrency, and resumption suites all run against real
Postgres. The fast unit suite stays container-free so the TDD loop remains quick.

**Governance**: enables `POL-PRIV-001` (schema assertion that no personal-data field exists) and
`POL-AUD-001` (six-field assertion) to be executed as real queries against a real schema.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Docker unavailable to a reviewer | `quickstart.md` states the prerequisite plainly up front; the fast unit suite still runs without it |
| Container startup inflates suite time, discouraging frequent runs | Split suites: unit (no container) vs integration (container), so the red-green loop stays fast |
| Store becomes a single point of failure for demonstrations | Accepted and disclosed — single-node by design (AS-001); EX-007 excludes production operation |
| Schema drift between migrations and entities | Migration-from-empty test plus entity-mapping validation in the integration suite |

## Reversibility

**High.** Repository interfaces isolate the store; swapping to Option D or another SQL store is a
configuration and migration-dialect change, not a redesign. Reversibility is deliberately purchased by
the interface boundary that NFR-MNT-002 requires anyway.

## Traceability

- **Requirements**: FR-ORC-004 (dual-restart durability), FR-URL-006 (uniqueness), FR-URL-013
  (concurrency), FR-URL-014 (persistence failure handling), NFR-AUD-001 (immutability), NFR-REC-001.
- **Specification**: CL-009, AS-008, EC-001, EC-010, EC-011, EC-016, EC-026, EC-038.
- **Plan**: §12 ADR-002, §2 persistence component, §6 dependency unavailability, §14 Slice 1 and 6.
- **Expected tasks**: Slice 1 migration harness; Slice 3 uniqueness-under-concurrency tests; Slice 6
  persistence-restart resumption test.
- **Related**: ADR-001, ADR-008 (state persistence shape), ADR-010 (audit immutability), ADR-012.

## Validation

Three executable proofs: a migration-from-empty test; a concurrent-creation test asserting distinct
codes with no overwrite under contention (EC-001); and a persistence-restart test that interrupts a run
mid-flight, restarts the store container, and asserts the run resumes and reaches a terminal state with
committed effects occurring exactly once (EC-038). The audit immutability grant is validated by a test
asserting that an UPDATE against an audit table is rejected.
