# ADR-007: Short-Code Generation and Collision Handling

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4. Decision record:
`docs/governance/gate-decisions/gate-04-adr.md`.

## Context

FR-URL-006 requires every issued short code to be unique across all links, **including under concurrent
creation**, with an explicit negative criterion: a collision **must not** be resolved by overwriting an
existing link, and two links must never share a code. EC-001 makes the concurrent-collision case a named
edge case; EC-008 requires deterministic behaviour for codes differing only by case or by visually
confusable characters. PVT-005 proposes a keyspace supporting at least 10⁹ links before collision
pressure materially affects creation latency.

CL-008 (Gate 3) is directly relevant and constrains this decision in a way that is easy to miss:
deduplication is **marker-only**, with *no destination-based deduplication at any scope, ever*.

**Assessment implication**: this is the canonical URL-shortener design question. A reviewer will
certainly ask how codes are generated, why, and what happens on collision.

## Decision Drivers

1. **Uniqueness under genuine concurrency** — FR-URL-006, EC-001.
2. **No overwrite on collision** — explicit negative criterion.
3. **Compliance with CL-008** — the generation strategy must not reintroduce destination-based
   deduplication by the back door.
4. **Enumeration resistance** — codes should not let anyone walk the link space and harvest
   destinations, given the creator-ownership model (FR-URL-011).
5. **Keyspace adequacy** — PVT-005.
6. **Determinism of the confusable-character outcome** — EC-008.
7. **Simplicity** — Constitution VII.

## Options Considered

### Option A — CSPRNG random string, unique constraint, bounded retry on collision

- **Approach**: generate N characters from a fixed alphabet using a cryptographically secure RNG; insert
  and rely on the database's unique constraint; on a unique-violation, regenerate and retry within a
  bound.
- **Advantages**: codes are unpredictable, so the link space is not enumerable. Uniqueness is guaranteed
  by the store rather than by application logic, which is exactly what the negative criterion wants —
  the database refuses the duplicate, so overwriting is not even expressible. Stateless generation, so
  no coordination between concurrent creators. Retry bound is naturally observable and testable.
- **Disadvantages**: a collision costs a round trip; collision probability must be managed by keyspace
  sizing.
- **Risks**: an undersized keyspace turns collisions from rare into routine.
- **Implementation impact**: small — generator, insert-and-catch, bounded retry.
- **Assessment implications**: strong; the uniqueness argument rests on a database guarantee rather than
  on a race-prone check-then-insert.

### Option B — Monotonic counter, Base62-encoded

- **Approach**: a database sequence yields an integer; encode to Base62.
- **Advantages**: collisions are impossible by construction; codes are dense and short; no retry logic.
- **Disadvantages**: codes are **sequential and therefore enumerable**. Anyone holding one code can walk
  neighbours and harvest every destination in the system. That is a substantive information-disclosure
  problem given that links belong to creators and their analytics are deliberately owner-only
  (FR-URL-011) — it would be incongruent to protect the click counts while leaving the destinations
  trivially discoverable. It also leaks total volume and creation rate.
- **Risks**: enumeration harvesting; an abuse case the spec's threat model would have to accept
  explicitly.
- **Implementation impact**: smallest.
- **Assessment implications**: mixed. Elegant on uniqueness, weak on the privacy posture the rest of the
  design commits to.

### Option C — Hash of the destination, truncated

- **Approach**: hash the normalized destination; take the first N characters.
- **Advantages**: no coordination; identical destinations map to identical codes, which sounds like a
  space saving.
- **Disadvantages**: **directly violates CL-008.** Hashing the destination *is* destination-based
  deduplication — the same destination necessarily yields the same code at global scope, which is
  precisely Option C of the approved AQ-005 decision that the owner rejected for breaking the ownership
  model and leaking cross-creator information. It would also make expiry incoherent: two creators
  wanting different expiries for the same destination cannot both be served by one code.
- **Risks**: contradicts an approved clarification; would require a change request to even consider.
- **Assessment implications**: **disqualified.** Recorded because it is a superficially attractive option
  whose incompatibility is non-obvious until CL-008 is applied.

### Option D — Pre-generated code pool, claimed transactionally

- **Approach**: pre-populate a table of unused codes; creation claims a row.
- **Advantages**: no collisions at creation time; predictable latency.
- **Disadvantages**: an extra table, a background top-up process, and claim contention under concurrency
  — machinery justified only at a scale this prototype explicitly does not target. Pool exhaustion
  becomes a new failure mode.
- **Risks**: contention shifted rather than removed; more state to recover after restart.
- **Assessment implications**: over-engineered for demonstration scale; Constitution VII concern.

## Decision

**Option A**, with these specifics:

- **Alphabet**: an unambiguous set that excludes visually confusable characters — no `0`/`O`, no
  `1`/`l`/`I`. Codes are **case-sensitive** and the exclusion set is fixed and documented, which gives
  EC-008 a deterministic answer.
- **Length**: fixed, sized so the keyspace comfortably exceeds PVT-005. With a ~56-character unambiguous
  alphabet, 7 characters yields on the order of 10¹² combinations — three orders of magnitude above the
  10⁹ target, keeping collision probability negligible at demonstration scale and well beyond.
- **Generation**: cryptographically secure RNG, not a general-purpose PRNG, so codes are not predictable
  from previously issued ones.
- **Collision handling**: insert and catch the unique-constraint violation; regenerate and retry within
  a bounded number of attempts; **never** read-then-overwrite. Exhausting the bound is a failure, not a
  silent reuse.

## Rationale

Option A places the uniqueness guarantee in the database's unique constraint, which is the only place it
can be guaranteed under concurrency without coordination. Equally important, it makes the prohibited
behaviour *inexpressible*: because the code path is insert-and-catch rather than check-then-insert, there
is no branch in which an existing link could be overwritten, so FR-URL-006's negative criterion is
satisfied structurally rather than by care.

Option B is the genuine contender and is rejected on a coherence argument rather than a technical one.
The design already decided — at Gate 2 — that analytics are owner-only and that a refusal must not reveal
whether a code exists, specifically to avoid an ownership oracle. Sequential codes would make the entire
destination space enumerable, which is a far larger disclosure than the one those measures protect
against. Choosing B would leave the security posture internally inconsistent.

Option C is disqualified outright by CL-008 and is recorded to show the check was made: a
destination-derived code is destination-based deduplication whatever it is called.

## Consequences

**Positive**: unpredictable codes; uniqueness guaranteed by the store; overwrite structurally impossible;
confusable-character behaviour deterministic; no coordination between concurrent creators.

**Negative**: collisions cost a retry round trip; keyspace sizing becomes a decision with consequences
rather than an afterthought; codes are longer than Base62-encoded counters would be.

**Operational**: a unique index on the short code is required. Collision-retry frequency is worth a
metric — a rising rate is the early signal of keyspace pressure.

**Testing**: three specific tests. A concurrent-creation test asserting distinct codes and no overwrite
under contention (EC-001). A forced-collision test with a stubbed generator returning a duplicate,
asserting retry-then-success and asserting the existing link is untouched. A bound-exhaustion test
asserting failure rather than reuse.

**Governance**: the destination-based-deduplication prohibition (CL-008) is respected, and this ADR
records the reasoning so a future reader does not reintroduce hashing as an optimisation.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Undersized keyspace making collisions routine | Length sized three orders of magnitude above PVT-005; collision-retry rate exposed as a metric |
| Weak RNG making codes predictable | Cryptographically secure RNG mandated by this decision, asserted in a unit test |
| Check-then-insert creeping in during implementation | Insert-and-catch is the stated mechanism; the concurrent-creation test would fail under a racy alternative |
| Someone later adds destination hashing as a "space saving" | Recorded here as disqualified by CL-008, with the reason |
| Retry bound exhausted under pathological load | Treated as a failure with a distinguishable outcome; never a silent reuse |

## Reversibility

**High.** Generation sits behind a single `ShortCodeGenerator` interface. Switching alphabet or length is
configuration plus a migration consideration for existing codes; switching to Option B or D would be a
generator substitution, and existing codes remain valid because nothing derives meaning from a code's
shape.

## Traceability

- **Requirements**: FR-URL-006 (uniqueness, no overwrite), FR-URL-013 (concurrency), FR-URL-001
  (creation), FR-URL-012 (interaction with marker-only idempotency).
- **Specification**: CL-008, EC-001, EC-008, PVT-005, AS-002 (opaque, system-generated codes), EX-002
  (no custom aliases), T-13 (short-code reuse hijack — links are never deleted, so codes are not
  recycled).
- **Plan**: §2 short-code generation component, §14 Slice 3.
- **Expected tasks**: Slice 3 generator, unique index migration, collision-retry path, and the three
  tests named above.
- **Related**: ADR-002 (the unique constraint that carries the guarantee), ADR-013 (ownership model that
  motivates enumeration resistance).

## Validation

Executable: the concurrent-creation test under contention; the forced-collision test with a stubbed
generator proving retry-then-success with the existing link untouched; the bound-exhaustion test proving
failure rather than reuse; a unit test asserting the alphabet excludes the confusable set; and a keyspace
calculation recorded alongside PVT-005 for review. Enumeration resistance is argued, not measured —
recorded as a design property rather than a test result, since predictability is not something a test can
demonstrate the absence of.
