# Change Request CR-007 — Define "Material"

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20 |
| Approving authority | Pravallika Veeravalli (human owner) |
| Raised by | Executing agent, from checklist finding CHK011 |
| Affected approved artifacts | `specs/001-agentic-sdlc-url-shortener/spec.md` §Reading Guide |
| Governing constitution | v1.1.0; `POL-CHG-001` |

## The finding

`CHK011` found that **"material" is used ten times in the specification and never defined**, while gating real
behavior: material ambiguity fires a clarification gate (FR-ORC-010, FR-ORC-011), material change triggers
replanning and change control (FR-ORC-019, Constitution VI), and material risk requires explicit acceptance
(Constitution III, plan §5). A term that decides whether a human is consulted cannot be left to the reader.

## Owner's decision and definition

**APPROVED as a CR**, with the definition supplied verbatim by the owner:

> **Material** (of a change, ambiguity, or risk): one whose resolution could alter an approved obligation (any
> FR/NFR or gate condition), a scope boundary (§Exclusions), the security posture, or a binding validation target
> (SC-\*/PVT-\*), or that determines which of two behaviours the system must exhibit. Non-materiality must be
> affirmatively shown, never assumed: when classification is uncertain, the item MUST be treated as material and
> routed to the human. Purely editorial changes altering no obligation are non-material. ("Key material" in the
> credential sense is unrelated.)

**Owner's grounds**:

> The closing default — uncertainty is material, non-materiality must be demonstrated — makes the enumeration a
> floor, not a loophole, and routes doubt to me rather than to agent inference.

**Recorded case against, acknowledged by the owner**: enumerations invite negative-space lawyering — an agent
could argue that something outside the four named categories is therefore non-material. The owner's answer is the
closing default: the burden of proof runs toward materiality, so the enumeration cannot be read as exhaustive.

## Applied edits

| # | Location | Change |
|---|---|---|
| 1 | §Reading Guide | Definition added verbatim as a defined term, placed where a reader meets the vocabulary before the requirements that use it |
| 2 | §Reading Guide marker table | Row added for the term, so it is discoverable alongside the Confirmed/Derived/PVT/AQ markers |

The definition is added **once**, in the Reading Guide, rather than restated at each of the ten use sites — a
single definition cannot drift from itself.

## Impact analysis

**Version impact**: **MINOR**. This is not purely editorial: it changes obligations by making
non-materiality something that must be affirmatively shown. Behaviour previously permitted — an agent judging an
ambiguity immaterial by its own reading and proceeding — is now prohibited unless it can demonstrate
non-materiality.

**Backward-compatibility impact**: none on interfaces. On process, it **tightens**: doubt now routes to the human.

**Affected consumers**: the ambiguity-detection stage (FR-ORC-010), the replanning trigger (FR-ORC-019), and the
change-control entry test (Constitution VI) all now have a decidable predicate rather than a judgment call.

**Affected tests**: ambiguity-detection tests should include a case that is *uncertain* rather than clearly
ambiguous or clearly clear, asserting it is treated as material and routed to the human — the default is the part
most likely to be implemented wrongly.

**Affected documentation**: none beyond the specification; the term is used in the plan and ADRs with the same
meaning the definition now fixes.

**Rollout / migration**: none.

## Residual risk

Low, and in the tightening direction. The cost is more items routed to the human than strictly necessary, which
the owner accepted as the correct direction for a term that decides whether she is consulted at all.
