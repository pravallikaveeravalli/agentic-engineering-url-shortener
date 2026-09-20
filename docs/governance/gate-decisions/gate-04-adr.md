# Gate Decision Record — Gate 4: Architecture and Technology Selection (ADR)

| Field | Value |
|---|---|
| Gate | Architecture and technology selection (ADR) — blocks Implement |
| Outcome | **APPROVED — all 14 ADRs ACCEPTED**, three of them as amended or conditioned |
| Deciding human | Pravallika Veeravalli |
| Decision date | 2026-09-20 (first verdict and gate close on the same date) |
| Artifacts approved | `docs/governance/adr/ADR-001` … `ADR-014`, all fourteen |
| Governing constitution | v1.1.0 (last amended 2026-09-18) |
| Recorded in | This record; each ADR's Status field; the commit closing the gate |

## How this record was maintained

This record was kept as a **living draft in the working tree** while the owner reviewed the fourteen ADRs one by
one, with each verdict appended the moment it was given, so that no decision existed only in conversation
(Constitution v1.1.0 §Repository as source of truth — the gap Amendment 001 closed). It is committed **once**,
at gate close: a disk-held draft survives a session reset, which is the actual risk, without fourteen
micro-commits polluting the history. Owner's decision on that mechanism, recorded 2026-09-20.

## Provenance of the acceptance grounds

The acceptance grounds recorded for **ADR-005 through ADR-013** were drafted at the owner's request by an
**independent review that verified every claim against the repository**, and the owner **adopts them as her
acceptance reasoning**. Recorded as provenance in the same manner as the earlier adopted argument in ADR-001
(the static-typing-as-review-layer corollary): the owner adopted these grounds, she did not originate them, and
the distinction between adopting a reviewed argument and authoring one is kept legible per Principle X.

The grounds for **ADR-001 through ADR-004** and **ADR-014** are the owner's own, recorded from her verdicts in
sequence, including the points where she rejected the drafted reasoning and required it restructured.

## Verdict ledger

| ADR | Decision | Verdict | Date |
|---|---|---|---|
| 001 | Programming language and web framework | **ACCEPTED** | 2026-09-20 |
| 002 | Persistence strategy | **ACCEPTED** | 2026-09-20 |
| 003 | Orchestration model — build vs adopt | **ACCEPTED** | 2026-09-20 |
| 004 | AI provider and autonomy bounding | **ACCEPTED** | 2026-09-20 |
| 005 | API contract validation approach | **ACCEPTED** with the count fix | 2026-09-20 |
| 006 | Application architecture and plane separation | **ACCEPTED** | 2026-09-20 |
| 007 | Short-code generation and collision handling | **ACCEPTED** | 2026-09-20 |
| 008 | Workflow state persistence and graph representation | **ACCEPTED** | 2026-09-20 |
| 009 | Replanning impact computation | **ACCEPTED** | 2026-09-20 |
| 010 | Observability and audit model | **ACCEPTED AS AMENDED** by owner scoping | 2026-09-20 |
| 011 | Testing strategy | **ACCEPTED** | 2026-09-20 |
| 012 | Deployment and local execution model | **ACCEPTED** | 2026-09-20 |
| 013 | Authentication and authorization mechanism | **ACCEPTED** with three refinements | 2026-09-20 |
| 014 | Analytics consistency — resolves DF-001 | **ACCEPTED** with three conditions | 2026-09-20 |

---

## ADR-001 — Programming Language and Web Framework

**Verdict: ACCEPTED** — confirmed explicitly by the owner, 2026-09-20.

**Decision**: Java 21 (LTS) with Spring Boot 3.x — Spring Web, Bean Validation, Spring Data JPA,
Actuator, Flyway, JUnit 5 with Testcontainers.

### Reason / verification performed

The owner reviewed the ADR, required a revision to how the velocity trade-off was priced, then accepted it.
Two requirements dominated the decision: the artifact must be defended live by the owner, whose depth is in
this stack; and FR-URL-006, FR-URL-013 and CL-009 require proving behavior against a real store under
contention and across restarts, which this stack makes routine.

### Revision history required before acceptance

The owner did not accept the ADR as originally drafted. She identified that its velocity argument was
mispriced and required it reframed before deciding status.

**Owner observation, recorded verbatim in the ADR (2026-09-20):**

> Option B's headline advantage — authoring velocity — is priced against human typing speed. In this
> project the implementation stage is AI-authored under my approved executor model (CL-003), and an AI
> generates Java boilerplate at essentially the same rate as Python, so raw authoring velocity largely
> collapses as a differentiator. What survives of the velocity argument is iteration cycle time only:
> JVM compilation, Spring context startup, and Testcontainers boot add seconds to every red-green
> cycle, and the authoring agent waits on builds like anyone else; verbose code also adds review volume
> at my checkpoints. That residual is real but far smaller than the ADR currently implies.

Sections revised in consequence: Decision Drivers item 4 (reframed from "velocity" to "iteration cycle
time and review volume"); Option A Disadvantages and Risks; Option B Advantages and Assessment
implications; Rationale paragraph 2; Consequences (Negative); and the Risks table. The net effect the owner
required to be stated: the counter-case to Option A **narrows**, which strengthens the recommendation rather
than weakening it. The honest sentence was retained with its basis corrected — Option B remains a legitimate
choice if velocity is weighted above defensibility, but that velocity now rests on cycle time and review
volume, not on authoring speed.

### Adopted corollary — static typing as a review layer

Raised by the executing agent during revision, **adopted by the owner into her acceptance rationale**: if
authoring velocity collapses as a differentiator, weak static typing matters *more* in an AI-authored
implementation, not less, because the compiler is one of the very few reviewers that reads every line an AI
produces. Java's verbosity thereby partly reverts from ceremony into a free, always-on review layer, making
the case for Option A materially stronger under CL-003.

**Provenance**: the owner adopted this argument; she did not originate it. Recorded as such in the ADR and
here, because the distinction between adopting an agent's argument and authoring one must stay legible
(Principle X).

### Conditions attached

1. Status flip to `Accepted` occurs at the Gate 4 commit, not before.
2. Reversibility is **Low** — this is the gating decision for Slice 1, which cannot begin without it.

---

## ADR-002 — Persistence Strategy

**Verdict: ACCEPTED**, with the reweighted rationale — owner, 2026-09-20.

**Decision**: PostgreSQL 16 via Docker Compose for development and demonstration, and via Testcontainers in
the test suite, all behind repository interfaces owned by this codebase.

### Reason / verification performed

The owner accepted the decision but **rejected part of its reasoning** and required the Rationale reweighted
before recording the verdict. Her correction, recorded verbatim in the ADR:

> The "green evidence for an untested property" framing is a critique of test design, not of the database.
> If SQLite were otherwise the right choice, the correct remedy would be honest tests that state exactly
> what they exercise — uniqueness under serialized writes — plus a recorded design argument that no write
> concurrency exists by construction. A database is not rejected to escape misleading test naming; test
> naming is ours to fix.

The owner reweighted the Rationale to rest on exactly two deciding grounds, both properties of the stores
themselves rather than of our test craft:

1. **SQLite is embedded**, so there is no store process that can die independently of the application. The
   CL-009 demonstration — store killed mid-run, application observes `UNAVAILABLE`, bounded retries,
   suspension, resumption — is **physically impossible**, which disqualifies Option B against a rule the
   owner wrote at Gate 3.
2. **SQLite has no privilege system**, so audit append-only can only ever be a convention. Postgres makes it
   a database-enforced denial, with a test proving an UPDATE is rejected by the store.

The concurrency point is **retained only in its honest form and ranked subordinate** to those two: the
specification requires demonstrated behavior under genuine concurrency (FR-URL-013's evidence obligation,
EC-017's parallel-branch writes), and a single-writer embedded store cannot produce that evidence at any level
of test honesty, because the demonstrations cannot occur — serialized writers do not contend.

Options C and D stand rejected on the grounds already written.

### Conditions attached

1. All wording implying the danger was that tests would deceive is removed; the tests are ours and would be
   written honestly either way.
2. Status flip to `Accepted` occurs at the Gate 4 commit, not before.

---

## ADR-003 — Orchestration Model: Build Versus Adopt

**Verdict: ACCEPTED** — owner, 2026-09-20, after two rounds of challenge.

**Decision**: build a purpose-built orchestration engine — explicit persisted dependency graph, explicit run
and stage state machines with enumerated allowed *and prohibited* transitions, one executor interface, no
external workflow product or server.

### Reason / verification performed

Recorded in the owner's terms:

> I challenged this ADR twice before accepting. First, "it is the graded deliverable" was circular as a
> leading argument — the rationale was restructured to stand on engineering grounds that hold with no
> assessment attached. Second, the right-sizing claim as originally phrased could be falsified in one sentence
> ("Temporal cannot run small" — it can, trivially); it was restated as price versus need. I accept on the
> corrected grounds: the adoption ledger does not balance (two saved items against three still owed and three
> added); Temporal's durability protects its own workflow state while our domain-store restart machinery under
> CL-009 must be built regardless; and FR-ORC-002's requirement that the graph be inspectable data with
> declared edges is a structural conflict with a flow-as-code engine — adoption would duplicate the graded
> artifact and add a synchronisation obligation. The steel-man stands: at production scale adoption would
> merit re-evaluation, and the executor interface plus persisted graph are the deliberate migration seams.

### Revision history required before acceptance

**Round 1 — circular leading argument.** The owner rejected "this is the graded deliverable, so we must author
it" as context rather than engineering justification, noting it invited the "why reinvent the wheel" challenge
it should answer. The Rationale was restructured to four grounds — right-sizing, itemised requirements
mismatch, cost accounting, Constitution VII — with the graded-deliverable point demoted to a closing
contextual note explicitly marked not load-bearing. Decision Drivers were reordered and the Context paragraph
reframed to scope rather than assessment stakes.

**Round 2 — falsifiable right-sizing claim.** The owner rejected the phrasing as readable to mean Temporal
cannot run small, which is false and refutable in one sentence. §1 was restated as price versus need, opening
by conceding operational feasibility outright. The durability distinction was added (Temporal covers its own
workflow state, not our domain, so CL-009's machinery is owed either way), FR-ORC-002 was added to the
mismatch table as a structural conflict, and §3 was rewritten as an honest adoption ledger. Option B's entry
and §4's Principle VII test were also corrected, since both rested on the refuted framing.

### Conditions attached

1. The steel-man paragraph — when adoption would be right — stands unaltered.
2. The executor interface and persisted graph are preserved as deliberate migration seams, not incidentally.
3. Status flip to `Accepted` occurs at the Gate 4 commit, not before.

---

## ADR-004 — AI Provider and Autonomy Bounding

**Verdict: ACCEPTED** — owner, 2026-09-20.

**Decision**: Anthropic Claude API behind an owned `StageAiProvider` interface, model pinned and recorded per
run; run-level flag `ai: on | off` defaulting to `off`; every AI-capable stage has a working deterministic
counterpart serving as both the keyless default and the declared fallback.

### Reason / verification performed

Recorded in the owner's terms:

> The keyless-default paradox ("required to exist and required not to be required") is resolved by making the
> deterministic counterpart a first-class requirement rather than a degraded mode; my stage-7 no-plan gate
> replaces the silent no-op so a run never advances with nothing implemented except by recorded human choice;
> the flag is renamed ai: on|off so the run-level name claims only what it controls; provider choice is
> low-stakes behind the owned seam and falls to Anthropic on stage-7 authoring quality; every reliability proof
> stays independent of AI availability.

### Owner design decisions incorporated before acceptance

**The stage-7 no-plan gate.** The owner rejected the earlier labelled-no-op behavior: *"A labeled no-op that
flows onward is quiet pretending — proceeding with nothing implemented is a material fact a human must
consciously accept, not discover in a label afterwards."* Replaced with suspension at a human gate offering
governance-only / human-implemented / abandon, introducing `HUMAN` as a third executor kind. Routed through
change control as **CR-001** because it extended approved specification text.

**The flag rename.** The owner rejected "deterministic mode" as a run-level name: *"A run-level name must not
claim a property the run cannot guarantee — a run started keyless can contain a `HUMAN` execution at the
stage-7 no-plan gate."* Renamed to `ai: on | off`, default `off`, documented as "with AI off (the keyless
default)". Per-execution kind labels unchanged.

### Conditions attached

1. `ai: on` is never a prerequisite for anything graded; the full suite and all reliability proofs run keyless
   and offline.
2. Reliability proofs use injected fakes, never the live provider.
3. Status flip to `Accepted` occurs at the Gate 4 commit, not before.

---

## ADR-014 — Analytics Consistency Model (resolves DF-001)

**Verdict: ACCEPTED, with three owner conditions** — owner, 2026-09-20.

**Decision**: the redirect resolves and issues; the event is appended in a **separate transaction**; an append
failure is isolated, counted and logged while the redirect still succeeds; every accepted append is durable
immediately with nothing buffered in memory. **PVT-009 is retained but redefined** as the ceiling on the
analytics *append failure rate*, not a designed-in loss budget. **SC-001 is scoped** explicitly to redirect
correctness.

This also **resolves DF-001**, the contradiction recorded in the approved specification between PVT-009's loss
tolerance and FR-URL-010/SC-001's exactness.

### Reason / verification performed

Recorded in the owner's terms:

> Analytics must be isolated from the product (separate transaction is the isolation mechanism); accepted-event
> durability is non-negotiable now (no buffer that a process kill can eat); the scale critique is real at extreme
> volume and is answered by documented mitigation plus the port, not by building infrastructure my approved scope
> excludes.

Note for the record: this ADR **corrected the plan**. Plan §Decisions Required item 1 and `research.md` both
recorded a provisional position of a same-transaction append. Working it against FR-URL-010's *negative*
criterion showed that position violates EC-012 — a failed append inside the redirect's transaction fails the
redirect. The ADR corrects it; the plan and research still carry the superseded provisional text and should be
updated at or before the Plan gate.

### Conditions attached to the approval

**Condition 1 — the scale analysis is recorded, not lost.** Written into ADR-014 as a new **§Scale analysis**
section and **designated the recorded answer to NFR-SCA-003's first-bottleneck obligation**, with the
designation reflected in plan §2:

- A single Postgres sustains thousands to tens of thousands of small inserts per second; 10k clicks/second is
  ~860M clicks/day — large-business territory. **Insert throughput is not the first thing to break.**
- **The first bottleneck is table growth, not write rate**: index bloat, retention deletes competing with
  hot-path inserts, and analytical queries over an ever-growing `redirect_event` table.
- **Designed mitigation, recorded now**: time-partitioning of `redirect_event` with retention enforced by
  **partition drop**, making 30-day retention an instant metadata operation. The demonstration implementation
  uses a plain table; partitioning is the documented **first mitigation step** — production-grade discipline
  rather than production-scale infrastructure.
- **Evolution ladder when scale demands**, in order, recorded as the production path and out of demo scope:
  batched appends → asynchronous pipeline (Kafka-class) → columnar analytics store (ClickHouse-class).

**Condition 2 — portability is a requirement, not an accident.** Every append MUST pass through the **analytics
recording port**, an owned interface, so buffering or a Kafka-backed pipeline can later replace the direct table
append as an implementation swap with **zero contract change**. Implementation **MUST NOT bypass the port
anywhere**.

**Condition 3 — for now, the Postgres table is the store**, per this ADR as written. PVT-009 stands as
redefined — failure-rate ceiling, not loss budget — subject to approval of the PVT set.

---

## ADR-005 — API Contract Validation Approach

**Verdict: ACCEPTED, with the count fix** — owner, 2026-09-20. The five-versus-four JSON Schema inconsistency
was corrected; four exist (`approval`, `audit-event`, `policy-evaluation`, `workflow-state`).

**Owner's adopted grounds**: the Gate 1 carry-forward demands executable contract validation, which disqualifies
review-only conformance; generating the contract from code would make drift undetectable by construction, since
the promise would redraw itself to match the code; the deliberate-drift test makes the enforcement itself
falsifiable.

## ADR-006 — Application Architecture and Plane Separation

**Verdict: ACCEPTED** — owner, 2026-09-20.

**Owner's adopted grounds**: Option A is accepted because NFR-MNT-001 asks for a dependency-direction check, and
a package-level architecture test satisfies that literally, where multi-module buys compile-time enforcement at
build-plumbing cost Constitution VII would question inside the timebox. Two deployables would import distributed
failure modes no requirement asks for. The AS-005/DS-B relationship is preserved: the control plane reaches the
application plane as a governed subject — filesystem, build, test suite — never by import, and the test asserts
exactly that. The ADR requires the test demonstrated failing on a deliberate violation; that falsifiability is
why a test-enforced rather than compiler-enforced boundary is signable.

## ADR-007 — Short-Code Generation and Collision Handling

**Verdict: ACCEPTED** — owner, 2026-09-20.

**Owner's adopted grounds**: CSPRNG generation with a database unique constraint and bounded retry is accepted
because it places FR-URL-006's uniqueness guarantee in the store and makes the prohibited overwrite
*structurally inexpressible* — insert-and-catch has no branch in which an existing link can be replaced
(EC-001). A destination-derived code is destination-based deduplication at global scope, which CL-008 prohibits
at any scope, ever. Sequential codes would make the destination space enumerable, inconsistent with the
ownership posture fixed in FR-URL-011. The arithmetic holds: a **57-character** confusable-free alphabet at
length 7 gives about **2×10¹²**, three orders above PVT-005, and the fixed exclusion set plus case-sensitivity
gives EC-008 its deterministic answer.

## ADR-008 — Workflow State Persistence and Graph Representation

**Verdict: ACCEPTED** — owner, 2026-09-20.

**Owner's adopted grounds**: the hybrid — current-state rows plus append-only transition log — with per-run
materialised edges is accepted because NFR-AUD-001 and FR-ORC-023 require an immutable history regardless, so
the log is machinery already being paid for, and writing state and transition in one transaction closes the
divergence gap that pure event sourcing would otherwise be bought to close; for a twelve-node graph that cost is
unjustified under Constitution VII. Per-run edges are the only representation in which a replanned instance's
topology stays queryable and EC-030's cycle check has an instance to validate; a code-only graph would make
replan history unreconstructable, failing FR-ORC-023. The CL-005 invariants already exist as conditionals in
`contracts/workflow-state.schema.json`: `terminalState` non-null iff terminal, `autoAbandonAt` non-null iff
`SAFE_STOP`.

## ADR-009 — Replanning Impact Computation

**Verdict: ACCEPTED** — owner, 2026-09-20.

**Owner's adopted grounds**: transitive downstream closure over persisted `dependency_edge` rows is accepted
because the affected set decides which approvals are voided (EC-020, NFR-CHG-002), so it must be deterministic
and **independently recomputable by a reviewer** — and closure over declared edges (FR-ORC-002) errs only toward
over-invalidation, a human-attention cost, never toward a surviving stale artifact. The AI option is disqualified
on the ground fixed in CL-006: the governed component proposes, it never rules on governance scope, and
CN-011/FR-ORC-030 forbid governance behaviour depending on AI availability. Voiding by superseding gate record,
never deletion, matches the Compensation Register row for gate decisions exactly. Deferring
consumption-tracking is correct: on partial FR-ORC-005 data it under-invalidates, which is the dangerous
direction.

## ADR-010 — Observability and Audit Model

**Verdict: ACCEPTED AS AMENDED BY OWNER SCOPING** — owner, 2026-09-20.

**Owner's adopted grounds**: audit as append-only relational data with immutability enforced by
INSERT/SELECT-only privilege is accepted, so a reviewer reconstructs per FR-ORC-023 from SQL and disproves
mutability by a failing UPDATE — with the fail-closed rule scoped as below.

### The owner's scoping, recorded in three parts

**(1) Fail-closed applies exactly to orchestrator governance writes** — `audit_record` / `failure_event`, gate
decisions, policy verdicts, state transitions, compensation records.

> A human overseer consumes that trail, so an invisible hole in it is itself a governance failure, and the
> failure surfaces visibly through the CL-006 envelope — UNAVAILABLE, proposed transient, bounded retry, suspend
> per FR-ORC-017 — the path FR-ORC-004's own criteria already prescribe for a store outage.

**(2) No public shortener operation writes an audit row** — create, resolve, analytics read — **verified against
every FR-URL requirement**, so those paths are **structurally incapable** of audit-write failure, not merely
exempted.

**(3) `redirect_event` is reclassified as domain analytics data, not audit.** It keeps its append-only privilege,
grounded independently in FR-URL-010 via the Compensation Register, but its failure semantics come **solely from
ADR-014**. Necessary for coherence: EC-012 forbids the append failing a redirect, and a code-plus-timestamp-only
event (CL-002) structurally cannot carry NFR-AUD-001's six mandatory fields.

**Boundary case retained as fail-closed**: orchestrator-initiated compensation expiring a link. The actor is the
orchestrator, its compensation record is in the fail-closed set, and EC-022's no-double-compensation guarantee
depends on that record existing.

**Specification impact assessed: none.** The spec never classifies `redirect_event` as audit — the Compensation
Register already calls it "append-only analytics (FR-URL-010)" — and the fail-closed rule existed only in ADR
text. No change-control record was required, and none was raised. The amendments were applied to ADR-010
directly.

## ADR-011 — Testing Strategy

**Verdict: ACCEPTED** — owner, 2026-09-20.

**Owner's adopted grounds**: the two-tier split with scriptable fake executors is accepted because each property
is proven by the only mechanism able to prove it — FR-URL-006/EC-001 uniqueness needs a real unique constraint,
CL-009 recovery needs a restartable store, ADR-010 immutability needs a real privilege denial. Mocks and
in-memory substitutes would pass while proving nothing, and the in-memory option specifically reimports the
loophole CL-009 was adopted to close. Excluding live AI from graded suites is **mandated, not chosen**:
FR-ORC-030 and NFR-AUT-004/SC-014 require deterministic, keyless, network-free reliability proofs. Stored
failing-run output implements NFR-TST-002's evidenced-not-asserted, the deliberate-failure tests make the
harnesses themselves falsifiable, and coverage stays a measurement against unapproved PVT-008 rather than
redefining Principle XI's completion standard.

## ADR-012 — Deployment and Local Execution Model

**Verdict: ACCEPTED** — owner, 2026-09-20.

**Owner's adopted grounds**: store-only Compose with the application on the host is accepted because CL-009's
proof requires an independently restartable store process — disqualifying an embedded store — and
`docker compose restart db` plus killing the host process perform both restart classes directly. Full
containerization charges an image rebuild per iteration across the whole timebox against a benefit realised once
by the reviewer; recording it as backlog under plan §14's no-displacement rule is the right side of that trade.
Kubernetes would deliver production-scale infrastructure EX-007 excludes, not the production-grade discipline
assessed. FR-URL-019's operator script correctly needs only store connectivity, and `quickstart.md`'s first
table states both prerequisites and the no-key, no-network expectation.

## ADR-013 — Authentication and Authorization Mechanism

**Verdict: ACCEPTED**, incorporating three owner refinements — owner, 2026-09-20.

The owner required three refinements folded before the verdict. Each is recorded in ADR-013 with its reasoning.

**1. Transport — `Authorization: Bearer <key>` replacing the custom `X-Creator-Key` header.** Adopting the
independent reviewer's point:

> Proxies, frameworks, and log scrubbers redact the Authorization header by default; a custom header receives no
> such default protection, so this is free reduction of exactly the leak risk FR-URL-017 treats as non-waivable.

**2. Key form — `crk_` + base64url of ≥256 CSPRNG bits.** A leaked key is identifiable as a key on sight (the
GitHub/Stripe pattern), and the `POL-SEC-002` secret scan gains a reliable pattern to match instead of hunting
anonymous high-entropy strings. The prefix carries no information and does not reduce entropy; stored form
remains SHA-256 of the full presented string.

**3. Expiry — a REQUIRED provisioning parameter: a duration, or the explicit literal `never`. No default.**

> No one gets to not think about credential lifetime at provisioning — an unconsidered eternal key must be
> impossible; explicitly choosing `never` is permitted, silently receiving it is not.

Mechanics: nullable `expires_at` on `creator_credential`, null only via explicit `never`; the authentication
filter rejects expired credentials with the **same response shape** as revoked ones. Deliberately **not** an
account lifecycle — EX-005 stands: no renewal flow, no notification, rotation remains
provision-new-then-revoke-old.

**Specification impact**: refinements 1 and 2 touch no approved text and were applied directly. Refinement 3
adds an obligation to FR-URL-019 and a field to KE-24, and is routed through **CR-002 — still open, awaiting the
owner's decision.**

---

## Change control executed during this gate

**CR-001 — Human Executor Kind and the No-Change-Plan Gate.** **APPROVED and APPLIED**, Pravallika
Veeravalli, 2026-09-20. Six edits to the approved specification (FR-ORC-029, FR-ORC-031, KE-25, §Stage
Executor Model stage-7 row and binding conditions, new EC-040) plus two additive contract enum changes, applied
exactly as proposed. Recorded in the specification's new §Change-control amendments section and in
`docs/governance/change-control/CR-001-human-executor-and-no-plan-gate.md`.

Noted for the record: this is the first exercise of the change-control workflow, and it worked as designed —
the change was identified as touching approved text, routed rather than applied silently, and approved before
any edit to `spec.md`.

**CR-002 — Credential Expiry Required at Provisioning.** **PROPOSED, still open.** Raised from ADR-013
refinement 3. Five proposed edits to approved text: FR-URL-019 accept criteria, reject criteria and rationale;
KE-24 (`expires_at`, `revoked_at`); new `EC-041` for expired-credential refusal indistinguishability. Classified
MINOR. **`spec.md` is unaltered for these.** ADR-013's other two refinements needed no spec change and were
applied directly.

**Consequence to be aware of**: the unapproved artifacts (`contracts/openapi.yaml`, `quickstart.md`,
`data-model.md`) now describe required expiry while `spec.md` does not yet. That delta is deliberate and closes
when CR-002 is decided. It is recorded here rather than left for a reviewer to discover as an inconsistency.

## Summary of owner interventions

The owner intervened on six of the fourteen ADRs, twice on two of them. Recorded because the interventions are
part of the decision record, not noise around it.

| ADR | Intervention |
|---|---|
| 001 | **Twice.** Velocity mispriced against human typing speed when implementation is AI-authored; then adoption of the agent-raised static-typing-as-review-layer corollary into her rationale |
| 002 | Rationale reweighted — "green evidence" rejected as a critique of test design rather than of the database; reduced to two store-property grounds with concurrency subordinate |
| 003 | **Twice.** "It is the graded deliverable" rejected as circular; then the right-sizing claim rejected as falsifiable in one sentence and restated as price versus need |
| 004 | Stage-7 no-plan gate replacing the labelled no-op; flag renamed `ai: on \| off` |
| 010 | Fail-closed rule scoped to orchestrator governance writes; `redirect_event` reclassified as domain analytics |
| 013 | Three refinements: bearer transport, `crk_` prefix, required expiry |
| 014 | Three conditions: scale analysis recorded as NFR-SCA-003's answer; port mandatory with no bypass; Postgres table for now with PVT-009 as a failure-rate ceiling |

Two of these produced corrections to work that had already been drafted and would otherwise have shipped
wrong: ADR-003's right-sizing argument was refutable in one sentence, and ADR-014 found that the plan's own
provisional position on DF-001 violated EC-012.

---

## Carried-forward enforcement points

Accumulated as verdicts land; finalized when the gate closes.

| Item | Due at |
|---|---|
| ADR-001 is the gating decision for Slice 1; Slice 2's walking skeleton validates it within hours | Slice 1–2 |
| Fast unit tier kept container-free and context-free so the red-green loop stays short (ADR-001 risk mitigation) | Slice 1 |
| Small, reviewable commits and gate requests carrying only what the decision needs, to contain review volume | Every gate |
| Test names and assertions must state exactly what they exercise; no test may imply a property it does not demonstrate (ADR-002 owner principle, applies beyond persistence) | Every test-bearing slice |
| Audit immutability proven by a test asserting an UPDATE is rejected **by the store**, not by application code | Slice 7 |
| CL-009 demonstration performed against a killable store process | Slice 6 |
| Executor interface and persisted graph maintained as clean migration seams, so production-scale re-evaluation of ADR-003 stays available | Slices 4–6 |
| Prohibited-transition tests are first-class, since transition correctness is ours under ADR-003 | Slice 4 |
| Mode-capability framing (reviewers may submit any requirement; what keyless deterministic mode produces; what AI mode adds; committed DS-A/B/C evidence is AI-mode with pinned model id) carried from `quickstart.md` into the reviewer navigation guide when that is generated — owner instruction 2026-09-20 | Reviewer navigation guide |
| Committed evidence must include an **out-of-scenario** run over a requirement outside DS-A/B/C (FR-ORC-028, SC-016) | Slice 8 |
| The out-of-scenario evidence run should **deliberately exercise the no-plan gate** (EC-040), since that is where a reviewer will personally encounter governance | Slice 8 |
| One test per no-plan-gate option, plus a negative test that a labelled no-op cannot advance downstream stages | Slice 6 |
| `ADR-011` still says "AI mode" at its demonstration tier — needs the `ai: on` phrasing when reviewed | ADR-011 review |
| Recommendation open: rename per-stage `executorModeUsed` → `executorKindUsed`, since "mode" now means the run-level flag (recorded in CR-001, not applied) | Owner decision |
| Architecture test asserting **no analytics append bypasses the recording port** (ADR-014 Condition 2), demonstrated to fail on a deliberate bypass | Slice 3 |
| Time-partitioning of `redirect_event` with retention-by-partition-drop recorded as the designed first mitigation; **not built** in the demonstration | Backlog / production path |
| Plan §Decisions Required item 1 and `research.md` still carry the superseded same-transaction provisional position for DF-001 — supersede them with ADR-014 | At or before Plan gate |
| NFR-SCA-003 discharged by ADR-014 §Scale analysis; the supporting measurement still owed (documented analysis **plus** measurement) | Slice 7 |
| Architecture test demonstrated **failing** on a deliberate violation — the owner's stated condition for signing a test-enforced rather than compiler-enforced boundary (ADR-006) | Slice 1 |
| Deliberate-drift test demonstrated failing, making contract enforcement falsifiable (ADR-005) | Slice 1 |
| Secret-scan test asserting the scan **matches** a planted `crk_` string, proving the pattern works (ADR-013) | Slice 3 |
| Fail-closed behaviour implemented only for the governance set; public shortener paths write no audit rows (ADR-010 scoping) | Slice 7 |

## Items the gate leaves open for the human owner

Gate 4 closes on architecture and technology selection. It does **not** close these, which remain the owner's:

| Item | Status | Due |
|---|---|---|
| **CR-002** — credential expiry spec edits | PROPOSED, `spec.md` unaltered | Owner decision |
| **DF-002** — redirect permanence (AQ-004) | Unresolved. Interacts with ADR-014: a cached permanent redirect never reaches the service, so no consistency model can count it | Plan gate |
| **DF-003** — audit vs idle retention boundary | Unresolved. Both currently 90 days, so a run auto-abandoned at day 90 has its earliest audit records aging out simultaneously | Plan gate |
| **PVT set** — all 15 proposed validation targets | Unapproved. None constrains implementation until approved; PVT-009's redefinition as a failure-rate ceiling is part of this approval | Plan gate |
| **2–3 day timebox and scope controls** | Unapproved | Plan gate |
| **MTTR method**, population, and the declared human-wait exclusion | Unapproved | Plan gate |
| **Plan gate itself** | Not yet held. The plan remains PROPOSED; Gate 4 approved the ADRs it references, not the plan | Plan gate |
| `executorModeUsed` → `executorKindUsed` rename recommendation | Open, recorded in CR-001, not applied | Owner decision |
