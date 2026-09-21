# Feature Specification: Agentic Software Engineering System: URL Shortener

**Feature Branch**: `main` (no branch created — the `before_specify` git-extension hook is not installed in this project; see CN-009)

**Feature Directory**: `specs/001-agentic-sdlc-url-shortener`

**Created**: 2026-09-17

**Status**: **Approved at Gate 2, 2026-09-18**; clarifications resolved at **Gate 3, 2026-09-19** by
Pravallika Veeravalli; amended by **CR-001..CR-007** and by the Gate 4 closing package, 2026-09-20, and by
**CR-010, CR-014 and CR-017** on 2026-09-20. **All six ambiguities are resolved**: AQ-001..003 at Gate 2, AQ-005
and AQ-006 at Gate 3, and **AQ-004 at the Gate 4 closing package (CR-003)**. Of the five Deferred Findings,
**DF-001, DF-002, DF-003 and DF-005 are resolved or closed; DF-004 alone remains open, by design and out of
scope**.

**Governing Constitution**: v1.1.0, ratified 2026-09-17, last amended 2026-09-18

**Input**: User description: "Build a production-grade prototype titled 'Agentic Software
Engineering System: URL Shortener' — a governed agentic execution system that transforms software
requirements into reviewable engineering outcomes across the complete SDLC, demonstrated over a
production-oriented URL shortener service."

---

## Reading Guide

This specification separates statement types deliberately, because the assessment evaluates
ambiguity management and requirement discipline (Constitution I).

| Marker | Meaning |
|--------|---------|
| **Confirmed** | Stated explicitly in the assignment input. Not negotiable without change control. |
| **Derived** | Not stated verbatim, but entailed by a Confirmed requirement or by the ratified constitution. The entailment source is named. |
| **Assumption-dependent** | Valid only while the referenced assumption (AS-nnn) holds. |
| **PVT-nnn** | Proposed validation target. A number **this specification proposes**, not a client requirement. Requires human approval before it constrains anything. |
| **AQ-nnn** | Ambiguity. All six are resolved: AQ-001..003 at Gate 2 (Clarification Log), AQ-005 and AQ-006 at Gate 3, AQ-004 at the Gate 4 closing package (CR-003). |
| **Material** | A defined term — see §Defined terms below. It gates whether a human is consulted, so it is not left to the reader. |

### Defined terms

**Material** (of a change, ambiguity, or risk): one whose resolution could alter an approved obligation (any
FR/NFR or gate condition), a scope boundary (§Exclusions), the security posture, or a binding validation target
(SC-\*/PVT-\*), or that determines which of two behaviours the system must exhibit. **Non-materiality must be
affirmatively shown, never assumed: when classification is uncertain, the item MUST be treated as material and
routed to the human.** Purely editorial changes altering no obligation are non-material. ("Key material" in the
credential sense is unrelated.)

*Added by CR-007 (approved 2026-09-20) after checklist finding CHK011 found the term used ten times and never
defined. The closing default is the operative part: the enumeration is a floor, not an exhaustive list, and the
burden of proof runs toward materiality so that doubt routes to the human rather than to agent inference.*

Two subsystems are specified and must not be conflated:

- **ORC** — the agentic SDLC orchestration system. This is the primary deliverable under assessment.
- **URL** — the URL shortener service. This is the demonstration domain, which must genuinely work.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - API Consumer Shortens and Follows Links (Priority: P1)

An API consumer submits a long destination URL and receives a short link. Later, anyone who
follows that short link arrives at the original destination. If the consumer submits something
that is not a usable or permitted destination, they are told why, in terms they can act on. If a
link has passed its expiry, following it does not silently fail and does not send them anywhere
unexpected.

**Why this priority**: The orchestration system's output must be working software. A governed
lifecycle over a non-functional stub would demonstrate nothing, so the demonstration domain has
to actually serve traffic. This story is the irreducible proof that the domain exists.

**Independent Test**: Fully testable through the service's public interface alone, with the
orchestration system switched off. Submit valid URLs, invalid URLs, duplicate URLs, and expired
links; observe creation, rejection, resolution, and expiry outcomes.

**Acceptance Scenarios**:

1. **Given** a syntactically valid destination URL using a permitted scheme, **When** the
   consumer requests a short link, **Then** a short link is returned with its code, its
   destination, its creation time, and its expiry state.
2. **Given** a previously created, unexpired short link, **When** any client follows it, **Then**
   the client is redirected to the exact original destination, and the redirect is counted.
3. **Given** a destination URL using a scheme outside the permitted set, **When** the consumer
   requests a short link, **Then** creation is refused with a reason identifying the scheme as
   the cause, and nothing is persisted.
4. **Given** a short link whose expiry has passed, **When** a client follows it, **Then** the
   client is not redirected and receives an outcome distinguishable from "code never existed".
5. **Given** a short code that was never issued, **When** a client follows it, **Then** the
   client receives a not-found outcome and no redirect occurs.

---

### User Story 2 - Human Reviewer Governs the Workflow (Priority: P1)

A human reviewer is asked to approve or reject work at defined points in the lifecycle. They can
see what they are approving, what evidence supports it, and what happens if they refuse. If they
say nothing at all, the workflow does not advance on their behalf — it stops in a state they can
return to. If they reject, the work returns to the stage that owns the problem with the requested
changes enumerated.

**Why this priority**: Also P1, and deliberately so. The differentiator under assessment is
governed autonomy, not automation throughput. A reviewer who can be bypassed — by timeout, by
retry, by re-entry at a later stage — is not a reviewer, and the system would fail Constitution
III regardless of how well the domain works. Neither US-1 nor US-2 alone demonstrates the thesis:
US-1 without US-2 is an ungoverned service, US-2 without US-1 is governance over nothing.

**Independent Test**: Testable without any domain code changing. Drive a workflow run to a gate,
then exercise each outcome — approve, reject, request changes, escalate, and deliberate silence —
and observe the resulting workflow state in each case.

**Acceptance Scenarios**:

1. **Given** a workflow run halted at a mandatory approval gate, **When** the reviewer inspects
   the gate, **Then** they see the stage, the artifacts awaiting decision, the supporting
   evidence, the policy check outcomes, the consequences of each available decision, **and the
   consequence of giving no decision at all** — the gate-wait deadline at which the run suspends and
   the date on which an untouched run is auto-abandoned.
2. **Given** a run halted at a mandatory gate, **When** no decision is recorded within the
   configured wait, **Then** the run enters `SAFE_STOP` — a suspended, non-terminal state — with
   state preserved and resumable, no downstream stage has executed, and the outcome matches the
   consequence disclosed in the original ask rather than being discovered afterwards.
3. **Given** a run halted at a mandatory gate, **When** the reviewer records `REJECTED`, **Then**
   the run terminates deterministically with the rejection reason recorded, and no downstream
   artifact is produced.
4. **Given** a run halted at a mandatory gate, **When** the reviewer records
   `CHANGES-REQUESTED` with enumerated changes, **Then** the run returns to the owning stage with
   those changes attached, and downstream stages are invalidated rather than reused.
5. **Given** any completed run, **When** a reviewer inspects the gate history, **Then** every
   mandatory gate shows an outcome, a deciding human, a timestamp, and a reason.

---

### User Story 3 - Software Engineer Runs and Steers the Lifecycle (Priority: P2)

A software engineer submits a requirement, watches the workflow decompose and execute it, and
inspects where it is. When the engineer changes an upstream requirement mid-flight, the system
tells them which downstream work is now invalid, re-plans only that part, and does not quietly
keep stale artifacts. When a stage fails for a transient reason, it retries within bounds rather
than escalating immediately; when it fails permanently, the engineer sees which failure class was
assigned and why.

**Why this priority**: P2 because it presupposes a working domain (US-1) and functioning gates
(US-2), but it is where non-linear and stateful behavior becomes observable — parallelism,
synchronization, replanning, and failure classification.

**Independent Test**: Testable by submitting requirements of each scenario class (DS-A, DS-B,
DS-C) and inspecting workflow state, the dependency graph, and the replanning records, without
needing a human approver present for non-gated stages.

**Acceptance Scenarios**:

1. **Given** a submitted requirement, **When** the engineer creates a workflow run, **Then** the
   run is assigned a durable identifier and an explicit stage graph with declared dependencies.
2. **Given** an executing run, **When** the engineer inspects it, **Then** they see per-stage
   status, entry and exit criteria evaluation, which stages ran in parallel, where paths
   synchronized, and the current blocking condition if any.
3. **Given** a run past decomposition, **When** an upstream requirement changes materially,
   **Then** the system identifies the affected downstream stages, invalidates them, re-plans
   them, and leaves unaffected paths untouched.
4. **Given** a stage whose executor fails with a transient fault, **When** the failure occurs,
   **Then** the stage retries within its declared bound with backoff, and each attempt is
   recorded with its outcome.
5. **Given** a stage whose executor fails permanently, **When** the bound is exhausted or the
   fault is classified permanent, **Then** the run applies its declared fallback, or stops safely
   if no fallback applies, and never presents the stage as succeeded.

---

### User Story 4 - Release Owner Determines Readiness (Priority: P3)

A release owner asks whether the work is releasable. They receive a determination that is either
a pass with evidence, or a block with every unmet condition named. They cannot be handed a pass
that was produced by the system on its own authority, and they cannot be handed a pass while a
mandatory policy check is failing or an exception is unapproved.

**Why this priority**: P3 because it is the terminal governance act and depends on all prior
stories, but it is the point where Constitution VI and the nine release-blocking conditions
become externally visible.

**Independent Test**: Testable by seeding each blocking condition individually and confirming the
readiness determination blocks and names that condition.

**Acceptance Scenarios**:

1. **Given** a run with all mandatory checks passing and all gates decided, **When** readiness is
   evaluated, **Then** the determination is presented as a recommendation requiring the release
   owner's recorded decision, not as a self-certified pass.
2. **Given** a run with a failing mandatory policy check, **When** readiness is evaluated,
   **Then** the determination is blocking and names the policy, the check, and the outcome.
3. **Given** a run with an `EXCEPTION-REQUESTED` policy outcome and no recorded approval, **When**
   readiness is evaluated, **Then** the exception is treated as a failure and readiness blocks.
4. **Given** a run whose tests were generated but never executed, **When** readiness is
   evaluated, **Then** readiness blocks on unexecuted validation and does not count generated
   tests as passing.

---

### User Story 5 - Assessment Reviewer Reconstructs an Execution (Priority: P3)

An assessment reviewer with repository access and no access to the original session reconstructs
what happened: which requirement produced which tasks, which tests were actually executed and
with what result, where the workflow retried or compensated, which human decided what and when,
and which figures were measured versus proposed.

**Why this priority**: P3 in execution order, but it is the acceptance test for the whole
submission. If reconstruction fails, every other story's evidence is unverifiable
(Constitution X).

**Independent Test**: Testable adversarially — hand the repository to a reader who did not run
it and ask them to answer a fixed set of reconstruction questions from artifacts alone.

**Acceptance Scenarios**:

1. **Given** a completed run, **When** the reviewer follows the traceability chain from any
   delivered requirement, **Then** they reach its scenario, decisions, tasks, implementation,
   executed tests with results, documentation, and evidence, in both directions.
2. **Given** a completed run, **When** the reviewer examines any audit record, **Then** it states
   actor type, action, timestamp, affected artifact or state, result, and reason.
3. **Given** any quantitative figure in the reporting, **When** the reviewer inspects it,
   **Then** it is labelled as measured — with conditions — or as a proposed target, never
   ambiguously.
4. **Given** a run that experienced a retry, a compensation, and a safe-stop, **When** the
   reviewer inspects the history, **Then** the three are distinguishable from one another and
   from ordinary success.

---

### Edge Cases

**Demonstration domain (URL)**

- **EC-001**: Two concurrent creation requests generate the same candidate short code. Uniqueness
  must hold; at most one may keep the code.
- **EC-002**: The same destination URL is submitted twice. Behavior must be defined and
  consistent — a new code, or the existing one — and must not depend on timing. (See AQ-002
  interaction and FR-URL-007.)
- **EC-003**: A creation request is retried by a client that never saw the first response.
  Duplicate side effects must not accumulate.
- **EC-004**: A destination URL resolves to a private, loopback, or link-local address. Treated
  as an abuse scenario (Constitution V).
- **EC-005**: A destination URL points at this service's own short-link space, creating a
  redirect chain or loop.
- **EC-006**: A destination URL is at or beyond the maximum accepted length.
- **EC-007**: A destination contains credentials in its authority component.
- **EC-008**: A short code differing only by case or by a visually confusable character is
  requested. Code alphabet must make the outcome deterministic.
- **EC-009**: A link expires between the moment resolution begins and the moment the redirect is
  issued.
- **EC-010**: The persistence layer is unavailable during creation. No partial link may become
  visible.
- **EC-011**: The persistence layer is unavailable during resolution of a link known to exist.
  Failure must be distinguishable from not-found and from expired.
- **EC-012**: Analytics recording fails while the redirect itself can succeed. Analytics must not
  take the redirect down (FR-URL-006).
- **EC-013**: A link is followed at very high rate by a single caller.
- **EC-014**: A link's expiry is set in the past at creation time.

**Orchestration (ORC)**

- **EC-015**: The process terminates mid-stage. On restart, the run must resume without
  re-applying committed effects.
- **EC-016**: The process terminates between a stage's effect and the recording of that effect.
  Recovery must not report a state it cannot substantiate.
- **EC-017**: Two parallel stages both modify the same downstream artifact. Synchronization must
  serialize or reject.
- **EC-018**: One branch of a parallel fan-out fails while siblings succeed. The join must not
  proceed as if complete.
- **EC-019**: A requirement changes while a dependent stage is mid-execution. Replanning must not
  corrupt the in-flight stage's state.
- **EC-020**: Replanning would invalidate a stage whose gate was already approved. The prior
  approval must not be silently carried forward.
- **EC-021**: An ambiguity is detected after implementation has begun on the affected path. Only
  the affected path suspends (DS-A).
- **EC-022**: A retry succeeds after a compensation for the same attempt has already been issued.
- **EC-023**: *(RETIRED by owner Decision K, 2026-09-20 — CR-032.)* A stage's declared fallback itself fails. **Unreachable by construction** once FR-ORC-015 is retired: there is no fallback to fail. The failure it guarded against — looping on a degraded path instead of stopping — is covered by PVT-007's bounded attempts and safe suspension on exhaustion.
- **EC-024**: A gate receives a decision from an actor with no recorded authority for it.
- **EC-025**: A policy check cannot be evaluated at all — neither pass, fail, nor
  not-applicable. Must not default to pass.
- **EC-026**: A run is resumed twice concurrently from the same persisted state.
- **EC-027**: An approved policy exception passes its expiry while the run is still in flight.
- **EC-028**: Evidence for a completed stage is missing or unreadable at readiness evaluation.
- **EC-029**: A stage reports success but produced no output artifact.
- **EC-030**: Cyclic dependencies are introduced into the stage graph by replanning.
- **EC-031**: An executor returns a malformed failure envelope, or a category outside the closed
  vocabulary. Must be treated as permanent, never retried (FR-ORC-014 rule 3).
- **EC-032**: An executor proposes `transient` for a category the stage never declared retryable, and
  vice versa. Neither side may act alone.
- **EC-033**: A `TIMEOUT` occurs on a stage whose effect is not declared idempotent. Must not be
  retried (FR-ORC-014 rule 4).
- **EC-034**: A stage registers declaring an irreversible effect without naming a compensating
  action. Must fail to load (FR-ORC-016 rule 3).
- **EC-035**: A stage declares an effect erasable that the structural rule places in an immutable
  store. Must be flagged for human review, not trusted.
- **EC-036**: A suspended run receives activity one day before its `auto-abandon-at`. The idle clock
  must reset rather than the run being reaped.
- **EC-037**: A run's idle retention expires while a gate is still pending. Abandonment is terminal
  and is not an approval; the gate is never satisfied by it.
- **EC-038**: The persistence layer restarts mid-run. The run must resume correctly once the store
  returns, with no state lost.
- **EC-039**: A creation request carries a known idempotency marker but different content. Must
  conflict, minting nothing and changing nothing.
- **EC-040**: Stage 7 is reached with **no change plan** for the requirement. Must suspend at the no-plan gate
  offering governance-only / human-implemented / abandon; must **not** no-op forward, and must not allow
  downstream stages to execute on the basis of an unimplemented change (FR-ORC-031, CR-001).
  **How this condition arises, restated by CR-033.** It previously named one route: a reviewer submitting their own
  requirement with the AI disabled. **Decision J removed that route** — orchestration always uses AI, so stage 7 authors
  the change from stage 6's design output and a plan normally exists. The condition now arises when **stage 6 produced
  no usable design output**, or when **authoring yields nothing applicable** to the branch. Both are real failure states
  and neither is reviewer-reachable on demand, which is why the demonstration is **injected** (T141) rather than
  produced by an ordinary submission.
  **The obligation is unchanged and the gate is more load-bearing than before**: with no deterministic counterpart at
  stage 7 (ADR-004-A2), this gate is the **sole** guard against an unimplemented change advancing.
- **EC-041**: Authentication attempted with an **expired** credential. Must be refused, and the refusal must be
  byte-identical to a revoked credential's, so the distinction is not disclosed (FR-URL-019, CR-002).
- **EC-042**: A client caches a redirect and follows it after the link has expired. Impossible under the
  temporary class required by FR-URL-007 — every follow reaches the service, so expiry is evaluated each time
  (CR-003).
- **EC-043**: *(Retired by Decision H, 2026-09-20.)* This case described a retention purge running against a suspended
  run's records. **No purge, deletion or archival path exists in this system** (NFR-AUD-003), so the case is
  unreachable rather than untested. Retained as a numbered entry rather than deleted, because CR-004 created it and an
  edge case that silently disappears is indistinguishable from one that was overlooked. The reasoning it carried — a
  live or suspended run's history must never age out — survives inside PVT-010's production recommendation.

---

## Requirements *(mandatory)*

### Functional Requirements — Demonstration Domain (URL)

- **FR-URL-001** — *Valid link creation.* **[Confirmed]** The service MUST create a short link
  from a valid destination URL and return the code, the destination, the creation time, and the
  expiry state.
  **Accept**: a permitted-scheme, well-formed URL yields a link retrievable by its code.
  **Reject (negative)**: creation MUST NOT succeed without persisting the link durably first; a
  code MUST NOT be returned that cannot subsequently be resolved.
  **Evidence**: creation test results; persisted record.

- **FR-URL-002** — *Invalid link rejection.* **[Confirmed]** The service MUST refuse malformed,
  empty, over-length, and disallowed-scheme destinations, with a reason identifying the cause.
  **Accept**: each rejection class returns a distinguishable, actionable reason.
  **Reject (negative)**: a rejected request MUST NOT persist any record, MUST NOT consume a short
  code from the keyspace, and MUST NOT echo the rejected input in a way that enables injection.
  **Evidence**: per-class rejection test results.

- **FR-URL-003** — *Input normalization.* **[Derived — Constitution V]** The service MUST
  normalize destinations before validation and storage, and the normalization MUST be defined and
  idempotent.
  **Accept**: destinations differing only in normalizable respects produce identical stored form.
  **Reject (negative)**: normalization MUST NOT alter the effective destination of a URL.
  **Evidence**: normalization unit tests including idempotency.

- **FR-URL-004** — *Scheme allow-listing.* **[Derived — Constitution V, non-waivable]** The
  service MUST accept only schemes on an explicit allow-list.
  **Accept**: allow-listed schemes are accepted; every other scheme is refused.
  **Reject (negative)**: no configuration, override, or exception may permit a non-allow-listed
  scheme to be stored or served.
  **Evidence**: allow-list test matrix including script, data, and file schemes.

- **FR-URL-005** — *Abuse and malicious-redirect controls.* **[Derived — Constitution V]** The
  service MUST address destinations targeting private, loopback, or link-local network space, and
  destinations that create redirect loops through its own short-link space, either by refusal or
  by an explicitly recorded accepted risk.
  **Accept**: EC-004 and EC-005 inputs produce the documented outcome.
  **Reject (negative)**: such destinations MUST NOT be silently accepted and served.
  **Evidence**: abuse-case test results; threat scenario documentation.

- **FR-URL-006** — *Short-code uniqueness.* **[Confirmed]** Every issued short code MUST be
  unique across all links, including under concurrent creation.
  **Accept**: concurrent creation attempts converge on distinct codes; the code alphabet and
  length are defined.
  **Reject (negative)**: a code MUST NOT be reissued while its link exists; two links MUST NOT
  ever share a code, and a collision MUST NOT be resolved by overwriting an existing link.
  **Evidence**: concurrency test results (EC-001); uniqueness constraint evidence.

- **FR-URL-007** — *Redirect resolution, temporary class.* **[Confirmed; permanence fixed by CR-003]** The
  service MUST resolve a known, unexpired code to its exact stored destination and redirect the client there,
  using a **temporary-class redirect**.
  **Accept**: the redirect target equals the stored destination byte-for-byte; the response is temporary-class,
  so every follow reaches the service.
  **Reject (negative)**: the service MUST NOT redirect to a destination derived from request input rather than
  from storage; MUST NOT rewrite the destination at resolution time; and MUST NOT use a **permanent-class**
  redirect, which would let clients cache the mapping and thereby defeat both analytics counting (FR-URL-010)
  and expiry enforcement (FR-URL-008).
  **Rationale recorded at CR-003**: a permanent redirect is cached by browsers, after which clicks never reach
  the service — analytics cannot count them and expiry cannot be enforced, because a cached redirect outlives
  the link's death. Temporary is the only class compatible with FR-URL-008 and FR-URL-010. The exact status code
  remains an implementation detail within the temporary class (CN-006).
  **Evidence**: resolution test results including round-trip equality; assertion that the response is
  temporary-class.

- **FR-URL-008** — *Expired-link behavior.* **[Confirmed]** An expired link MUST NOT redirect, and
  its outcome MUST be distinguishable from an unissued code.
  **Accept**: expired yields the expired outcome; unissued yields not-found; the two differ.
  **Reject (negative)**: an expired link MUST NOT redirect under any retry, cache, or race
  condition, including EC-009.
  **Dependency recorded at CR-003**: enforceability of this requirement **depends on FR-URL-007's temporary
  class**. Under a permanent-class redirect a client would hold a cached mapping that outlives the link's
  expiry and never consults the service again, so expiry would be unenforceable by construction rather than by
  defect.
  **Evidence**: expiry boundary test results; cache-behaviour test per EC-042.

- **FR-URL-009** — *Expiration semantics.* **[Derived — from Confirmed "expiration"]** The service
  MUST define whether expiry is caller-supplied, defaulted, or both, MUST reject an expiry
  already in the past at creation, and MUST treat the expiry instant consistently.
  **Accept**: EC-014 is refused; boundary behavior at the expiry instant is defined and tested.
  **Reject (negative)**: a link MUST NOT be created in an already-expired state.
  **Evidence**: expiry validation tests. *Default TTL: PVT-011.*

- **FR-URL-010** — *Redirect analytics capture, per-event, timestamp only.* **[Confirmed;
  granularity fixed by AQ-002]** The service MUST record one event per redirect, carrying the short
  code and the occurrence timestamp, and **nothing that identifies the follower**.
  **Accept**: following a link appends an event; events support time-series reporting such as
  clicks over time and visible bursts.
  **Reject (negative)**: the service MUST NOT store IP address, user agent, referrer, device
  identifier, geolocation, or any other follower-identifying field — this is a deliberate
  data-minimization decision, not an omission; analytics recording MUST NOT cause a resolvable
  redirect to fail (EC-012); redirect events are **not deleted or purged at any age** — this demonstration retains
  them indefinitely (NFR-AUD-003, CR-017). The 30-day figure that previously appeared as a retention cap is now a
  **production recommendation** recorded in PVT-010 and `docs/LIMITATIONS.md`, not a behaviour of this system.
  **Evidence**: analytics capture tests; degraded-analytics redirect test; stored-schema inspection
  confirming no follower-identifying field exists.

- **FR-URL-011** — *Analytics retrieval, creator-scoped.* **[Derived — from Confirmed "basic
  redirect analytics"; entitlement fixed by AQ-001]** The service MUST expose a link's recorded
  redirect events to **the creator that owns that link, and to no one else**.
  **Accept**: the owning creator retrieves the link's time-ordered redirect events; a different
  authenticated creator receives a refusal; an unauthenticated caller receives a refusal.
  **Reject (negative)**: retrieval MUST NOT expose analytics for a link the caller does not own,
  MUST NOT disclose through its refusal whether the code exists (no ownership oracle), and MUST NOT
  be reachable without creator authentication.
  **Evidence**: retrieval tests for owner, non-owner, and anonymous callers.

- **FR-URL-012** — *Duplicate requests: marker-only idempotency.* **[Confirmed; semantics fixed by
  CL-008]** Deduplication happens **only** through an explicit idempotency marker. Exactly three
  behaviors are defined:

  1. **No marker** → always mint a new code. There is **no destination-based deduplication at any
     scope, ever**.
  2. **Same marker, identical request** → **replay**: return the original link as **success** (not an
     error), labelled as a replay, with **zero new side effects**. This mechanizes EC-003 — a client
     that never saw the first response can retry safely.
  3. **Same marker, different request content** (for example a different expiry) → **explicit
     conflict error**; nothing is changed and nothing is minted.

  **Accept**: each of the three cases produces its defined outcome; the replay response is
  distinguishable as a replay; the conflict names the mismatch.
  **Reject (negative)**: the same destination submitted twice without a marker MUST NOT return an
  existing link; a replay MUST NOT alter any state, including the existing link's expiry; a conflict
  MUST NOT silently mint a new code past a detected caller error; deduplication MUST NOT span
  creators (which would break FR-URL-011 ownership and leak that another creator shortened the same
  URL).
  **Rationale recorded at Gate 3**: an idempotency key identifies one exact logical request, not a
  destination. Updating the existing link on replay is forbidden twice over — replay must not change
  state, and link editing is excluded scope for the same reason deletion is: the link is already
  circulating with a promised lifetime. Destination-based deduplication would force a silent lie
  whenever a second request carried a different expiry — either ignore what the caller asked, or
  mutate a circulating link. Keyspace cost of always minting is accepted as negligible against
  PVT-005.
  **Evidence**: tests for all three cases, plus a cross-creator non-deduplication test.

- **FR-URL-013** — *Concurrent request safety.* **[Confirmed]** The service MUST behave correctly
  under concurrent creation and concurrent resolution of the same link.
  **Accept**: invariants in FR-URL-006 and FR-URL-010 hold under concurrent load.
  **Reject (negative)**: concurrency MUST NOT produce lost analytics beyond the declared
  tolerance, duplicate codes, or partially visible links.
  **Evidence**: concurrency test results at PVT-003.

- **FR-URL-014** — *Persistence failure handling.* **[Confirmed]** The service MUST classify
  persistence failures and respond without corrupting or partially exposing state.
  **Accept**: EC-010 produces a failure response with nothing persisted; EC-011 produces a
  failure outcome distinguishable from not-found and from expired.
  **Reject (negative)**: a persistence failure MUST NOT be reported to the caller as success, and
  MUST NOT surface storage internals or secrets in the response.
  **Evidence**: fault-injection test results.

- **FR-URL-015** — *Operational health.* **[Confirmed]** The service MUST expose an operational
  health signal that distinguishes "process alive" from "able to serve requests".
  **Accept**: with the persistence dependency unavailable, readiness degrades while liveness is
  unaffected.
  **Reject (negative)**: health MUST NOT report ready while a required dependency is unavailable,
  and MUST NOT expose secrets, connection strings, or internal topology.
  **Evidence**: health probe tests under dependency failure.

- **FR-URL-016** — *Rate limiting, two-sided and two-tier.* **[Derived — Constitution V; design
  fixed by AQ-001]** The service MUST apply rate limiting on both sides of its traffic:
  - **Creation** — limited **per creator**. *Limit: PVT-012.*
  - **Redirects** — limited on two independent tiers, each enforced separately:
    - **per short code**, for hotspot protection, so no single link can be hammered. *Limit:
      PVT-013.*
    - **per creator aggregated across all their links**, for noisy-neighbor protection, so a
      creator with many links each individually under the per-code limit cannot collectively soak
      service capacity. *Limit: PVT-014.*

  **Accept**: exceeding any of the three limits produces a distinguishable throttled outcome naming
  which tier was exceeded; the per-creator-aggregate tier triggers on traffic spread across
  multiple links that individually stay under PVT-013.
  **Reject (negative)**: throttling MUST NOT be silently disabled by default configuration; the
  redirect tiers MUST NOT require the follower to authenticate (FR-URL-018); a throttled response
  MUST NOT disclose the owning creator's identity to the public follower.
  **Accepted trade-off**: followers of a popular creator's links may be throttled through no fault
  of their own. This deliberately prioritizes service protection over unlimited availability of any
  one tenant's links, and MUST be documented as an accepted trade-off in the threat model
  (NFR-SEC-003).
  **Evidence**: rate-limit tests per tier, including the multi-link aggregate case.

- **FR-URL-017** — *No sensitive data in telemetry.* **[Derived — Constitution V, non-waivable]**
  The service MUST NOT write secrets, credentials, or sensitive values to logs, traces, metrics,
  or error responses.
  **Accept**: an automated scan over the demonstration corpus finds zero occurrences.
  **Reject (negative)**: credentials embedded in a submitted destination (EC-007) MUST NOT appear
  in any log or trace record; creator API key material MUST NOT appear in any application log,
  trace, metric, or error response, at any log level, including startup and shutdown output.
  **Evidence**: secret-scan output over captured telemetry.

- **FR-URL-018** — *Creator identity; public redirects.* **[Confirmed — AQ-001]** Link creation and
  analytics retrieval MUST require an authenticated creator identity. Redirect resolution MUST be
  public and MUST NOT require authentication.
  **Accept**: creation without valid creator credentials is refused; every created link records the
  creator that made it; following a short link succeeds with no credentials presented.
  **Reject (negative)**: redirect resolution MUST NOT require, consume, or be affected by caller
  credentials — a short link that requires login is not a short link; creation MUST NOT succeed
  anonymously; a link MUST NOT exist without a recorded owning creator.
  **Evidence**: authenticated and anonymous creation tests; anonymous redirect test; ownership
  recorded on every created link.

- **FR-URL-019** — *Creator provisioning and key handling.* **[Confirmed — AQ-001]** Creator
  identities MUST be provisioned by a local operator script, not by an HTTP endpoint. Keys MUST be
  stored only as hashes. Plaintext key material MUST NOT be committed to the repository, and the
  **application** MUST NOT write key material anywhere.
  **Accept**: the operator script provisions a creator and may display the key once to the
  operator's terminal; the stored record contains only a hash; the service authenticates a
  presented key against that hash; the script **requires** an expiry parameter — a duration or the
  explicit literal `never` — and **refuses to provision without it**; an expired credential is
  refused with the same response shape as a revoked one.
  **Reject (negative)**: no HTTP key-issuance surface may exist; keys MUST NOT be committed in
  configuration; keys MUST NOT be generated at application startup and emitted to console or log
  streams; the stored form MUST NOT be reversible to the key; a credential MUST NOT be provisioned
  without an explicit expiry decision — there is **no default**, and a non-expiring credential is
  reachable only by passing the literal `never`; an expired credential MUST NOT authenticate, and
  its refusal MUST NOT be distinguishable from a revoked credential's.
  **Rationale recorded at Gate 2**: the ability to run commands on the machine **is** the trust
  boundary, in the demonstration and in production alike. Operator-invoked terminal output is not
  application logging; the distinction being drawn is that the application never logs keys. An
  HTTP issuance endpoint would create an unprotected surface whose only demonstration defence —
  localhost — is meaningless when everything runs on localhost.
  **Rationale recorded at CR-002**: no one gets to not think about credential lifetime at
  provisioning — an unconsidered eternal key must be impossible; explicitly choosing `never` is
  permitted, silently receiving it is not. This is deliberately **not** an account lifecycle
  (EX-005 stands): no renewal flow, no notification, and rotation remains
  provision-new-then-revoke-old.
  **Evidence**: provisioning script run; stored-hash inspection; secret-scan over repository and
  telemetry; absence-of-endpoint test.

### Functional Requirements — Orchestration (ORC)

- **FR-ORC-001** — *Twelve-stage lifecycle coordination.* **[Confirmed]** The orchestrator MUST
  coordinate: requirement ingestion, requirement normalization, ambiguity detection, human
  clarification, task decomposition, architecture and design, implementation, testing,
  documentation, security and risk validation, release-readiness determination, and final
  engineering summary.
  **Accept**: every stage exists as an addressable node with declared inputs, outputs, entry
  criteria, exit criteria, owner, failure behavior, and executor class per §Stage Executor Model.
  **Reject (negative)**: no stage may be implicit, undeclared, or reachable without its entry
  criteria being evaluated.
  **Evidence**: stage definitions; per-stage criteria evaluation records.

- **FR-ORC-002** — *Explicit dependency model.* **[Confirmed]** The orchestrator MUST represent
  execution as an explicit dependency graph or equivalent stateful transition model.
  **Accept**: the graph is inspectable, and edges are declared rather than inferred from call
  order; cycles are rejected (EC-030).
  **Reject (negative)**: a fixed linear call chain MUST NOT satisfy this requirement
  (Constitution II).
  **Evidence**: graph inspection output; cycle-rejection test.

- **FR-ORC-003** — *Parallelism and synchronization.* **[Confirmed]** The orchestrator MUST
  execute independent stages in parallel and MUST synchronize dependent stages at declared join
  points.
  **Accept**: at least one fan-out and one join execute in a demonstration run, with parallel
  execution observable in the run history.
  **Reject (negative)**: a join MUST NOT proceed while any required branch is incomplete or
  failed (EC-018); parallel branches MUST NOT corrupt a shared downstream artifact (EC-017).
  **Evidence**: run history showing overlap and join blocking.

- **FR-ORC-004** — *Workflow state persistence across both restart classes.* **[Confirmed; scope
  fixed by CL-009]** The orchestrator MUST persist workflow state durably enough to survive **both**
  an orchestrator-process restart **and** a restart of the persistence layer itself. A **disk-backed
  store is therefore required**; an in-memory store does not satisfy this requirement.
  **Accept**: after termination at an arbitrary point, state is reloaded and the run's position is
  unambiguous; after a persistence-layer restart, the run resumes correctly once the store returns;
  during the outage the store surfaces as `UNAVAILABLE` in the FR-ORC-014 envelope, is proposed
  transient, is intersected with the stage's declared retryable set, retries within its bound, and on
  exhaustion suspends per FR-ORC-017.
  **Reject (negative)**: state MUST NOT exist only in process memory, and MUST NOT live only in a
  store that is itself only in memory; recovery MUST NOT report a state it cannot substantiate from
  persisted records (EC-016); a store outage MUST NOT lose or corrupt run state.
  **Rationale recorded at Gate 3**: process-only durability is the loophole — an in-memory store
  technically satisfies the older wording while leaving the recovery guarantee nominal, protecting
  nothing.
  **Evidence**: kill-and-reload test results; persistence-layer restart fault-injection test with
  correct resumption.

- **FR-ORC-005** — *Context and decision lineage.* **[Confirmed]** The orchestrator MUST preserve
  cross-stage context, artifact provenance, and decision lineage.
  **Accept**: for any artifact, the run identifies which stage produced it, from which inputs,
  under which decisions.
  **Reject (negative)**: an artifact MUST NOT exist in the run without recorded provenance.
  **Evidence**: provenance query over a completed run.

- **FR-ORC-006** — *Stage entry and exit enforcement.* **[Confirmed]** The orchestrator MUST
  evaluate entry criteria before a stage executes and exit criteria before it is marked complete.
  **Accept**: a stage with unmet entry criteria does not execute; a stage with unmet exit criteria
  is not marked complete.
  **Reject (negative)**: a stage reporting success with no output artifact MUST NOT pass its exit
  criteria (EC-029).
  **Evidence**: criteria evaluation records; negative tests for both directions.

- **FR-ORC-007** — *Workflow creation.* **[Confirmed]** An engineer MUST be able to create a
  workflow run from a submitted requirement and receive a durable run identifier, through an **addressable
  submission surface defined in the API contract**.
  **Accept**: the run identifier appears in all subsequent logs, metrics, evidence, and state; the submission
  surface is declared in `contracts/openapi.yaml` and exercised by the contract suite, not only by an internal
  call path; the surface sits under the machine-access trust boundary and **neither requires nor accepts a creator
  credential** (CN-012).
  **Reject (negative)**: work MUST NOT be executed outside a created, identified run; a run MUST NOT be creatable
  only from inside the process, since a reviewer who cannot start a run cannot exercise the system (SC-016);
  a creator credential MUST NOT authorize run creation; the submission surface MUST NOT be reachable from the
  public redirect path; and submitted requirement text MUST be treated as **untrusted content** wherever it later
  reaches an executor (ADR-004-A1's argv-not-shell rule).
  **Evidence**: run creation tests; identifier propagation check; contract conformance on the submission operation;
  an absence test proving no creator credential is accepted there.

- **FR-ORC-008** — *Workflow inspection.* **[Confirmed]** A caller MUST be able to inspect a run's
  current state: per-stage status, graph position, parallel and join structure, blocking
  condition, retry history, and pending gates.
  **Accept**: inspection of an in-flight run reports its true current blocking condition.
  **Reject (negative)**: inspection MUST NOT report a stage as complete whose exit criteria were
  not satisfied; inspection MUST NOT require, consume, or be affected by a creator credential (CN-012).
  **Evidence**: inspection output at multiple run positions.

- **FR-ORC-009** — *Requirement normalization.* **[Confirmed]** The orchestrator MUST convert
  submitted requirements into a normalized, identified, testable form, keeping functional and
  non-functional requirements distinguishable.
  **Accept**: each normalized requirement carries a stable identifier, a type, and a testable
  statement.
  **Reject (negative)**: normalization MUST NOT discard, merge, or silently reinterpret a
  submitted requirement.
  **Evidence**: normalization input/output pairs.

- **FR-ORC-010** — *Ambiguity detection.* **[Confirmed]** The orchestrator MUST detect incomplete,
  unclear, conflicting, or untestable requirements and MUST record the checks it performed.
  **Accept**: a requirement with an internal conflict is flagged with the conflicting elements
  named; a complete requirement records the checks that passed and why clarification was not
  required (DS-A).
  **Reject (negative)**: an ambiguous requirement MUST NOT reach implementation; the system MUST
  NOT resolve material ambiguity by its own inference (Constitution I).
  **Evidence**: requirement-quality check records for all three demonstration scenarios.

- **FR-ORC-011** — *Human clarification.* **[Confirmed]** When material ambiguity is detected, the
  orchestrator MUST request human clarification, suspend only the affected path, and record the
  decision received.
  **Accept**: the affected path suspends while unaffected paths continue; the recorded decision
  identifies the human, the question, the answer, and the time; and the clarification request states
  its expiry consequences up front on the same terms as a gate request (FR-ORC-013).
  **Reject (negative)**: unaffected paths MUST NOT be suspended unnecessarily; the workflow MUST
  NOT proceed on the affected path without a recorded answer (EC-021).
  **Evidence**: DS-C run history showing selective suspension.

- **FR-ORC-012** — *Task decomposition.* **[Confirmed]** The orchestrator MUST decompose approved
  requirements into dependency-ordered tasks traceable to their requirements.
  **Accept**: every task names its requirements; every approved requirement has at least one task.
  **Reject (negative)**: a task MUST NOT exist without a traceable requirement; decomposition
  MUST NOT invent scope (Constitution I).
  **Evidence**: decomposition records; orphan-task check.

- **FR-ORC-013** — *Approval gates.* **[Confirmed]** The orchestrator MUST enforce human approval
  checkpoints with outcomes `APPROVED`, `REJECTED`, `CHANGES-REQUESTED`, `ESCALATED`, `TIMED-OUT`.
  **Accept**: each outcome produces its defined workflow effect; every gate record carries
  outcome, actor, timestamp, and reason; each gate record is materialized as a repository
  artifact within the commit that acts on the decision, not held only in workflow state; and **the
  gate request itself states its expiry consequences up front** — the gate-wait deadline at which
  the run suspends, and the `auto-abandon-at` date computed from last activity plus the retention
  period (FR-ORC-032); the decision-recording surface is declared in the API contract, accepts only the four
  **submittable** outcomes — `APPROVED`, `REJECTED`, `CHANGES-REQUESTED`, `ESCALATED` — and **cannot express
  `TIMED-OUT`**, which is produced only by deadline expiry, so silence is not merely rejected as an input but is
  **inexpressible** as one; the surface requires the decision's repository record path and refuses a decision that
  exists only in workflow state; and it sits under the machine-access trust boundary with no creator credential
  accepted (CN-012).
  **Rationale recorded at Gate 3 (CL-005 addendum)**: the person being asked must see the deadline in
  the ask, rather than having to discover it by inspecting the run. This is additive to the
  inspectable field, not a substitute for it.
  **Reject (negative)**: silence MUST NOT be recorded as approval; a mandatory gate MUST NOT be
  skipped, auto-satisfied, back-dated, or bypassed by re-entering the workflow downstream; a
  decision from an actor without recorded authority MUST NOT take effect (EC-024); the substantive
  work a decision authorizes MUST NOT begin before that decision's record exists.
  **Evidence**: one test per outcome, including the silence test; a test asserting the gate record
  is present as a repository artifact in the acting commit.
  *Added by Constitution Amendment 001 (v1.1.0, 2026-09-18) — see
  `docs/governance/gate-decisions/amendment-001-decision.md` sub-decision 6.*

- **FR-ORC-014** — *Bounded retries and two-vote retry eligibility.* **[Confirmed; classification
  model fixed by CL-006]** The orchestrator MUST classify failures as transient or permanent and MUST
  retry transient failures within a declared bound, with declared backoff and timeout. Classification
  works as follows:

  1. **Standard error envelope.** Every failure crosses the orchestration boundary in a fixed
     envelope: a category from a small closed vocabulary (`TIMEOUT`, `UNAVAILABLE`, `RATE_LIMITED`,
     `INVALID_INPUT`, `INTERNAL`, `UNKNOWN`), the executor's **proposed** classification, and
     free-form detail. Executors translate provider-specific errors into this envelope, so provider
     knowledge stays in the plugin; a stage's declared retryable set is expressed over the standard
     categories only.
  2. **Two yes-votes required.** `retries = declared retryable set ∩ executor proposal`. Either side
     may veto. The executor holds **veto power, never grant power**.
  3. **Default-deny.** An `UNKNOWN` category, an undeclared code, or a malformed envelope is treated
     as **permanent** and takes the safe path (suspension, FR-ORC-017).
  4. **Timeout gating.** A `TIMEOUT` is retryable only where the stage's declared contract marks its
     effect idempotent or repeat-safe. That contract is a **design-time stage declaration, never the
     executor's self-certification**, because a timeout means completion is unknown and replaying a
     maybe-completed non-idempotent effect violates exactly-once (FR-ORC-018).
  5. **Information flows down, authority does not.** The executor receives the attempt number and
     remaining budget and may adapt its strategy; it never gains decision authority.
  6. **Threshold escalation, never an automatic kill.** Each node declares an elapsed-time **escalation threshold**
     (PVT-016). On breach the orchestrator MUST escalate to the human — presenting elapsed versus expected and the
     node's last observed activity, drawn from existing state-transition, trace and audit records — and MUST offer
     exactly two choices: **keep waiting**, which re-arms the threshold, or **kill the node**. A kill fails the node
     by overrun, after which rules 1–4 apply unchanged, so a node whose effect is not declared idempotent is still
     not retried. The orchestrator MUST NOT kill a node on its own authority, and MUST NOT treat a breach as a
     failure until a human decides. **Absence of an answer follows the gate-wait deadline to suspension**
     (FR-ORC-017); it is never a kill and never an approval. *(Added by CR-010 under the owner's escalation-threshold
     decision, 2026-09-20.)*

  **Accept**: retry attempts are bounded, spaced per the declared backoff, and individually recorded;
  every retry decision produces a **two-signature audit record** — what the executor proposed and
  what the orchestrator ruled; a breached escalation threshold produces a human escalation carrying
  elapsed-versus-expected and last-observed activity, and each of the two choices produces its defined effect.
  **Reject (negative)**: retries MUST NOT be unbounded; a permanent failure MUST NOT be retried as
  if transient; an exhausted bound MUST NOT be reported as success; an unrecognized failure MUST NOT
  default to retryable; a declared retryable set MUST NOT force a retry the executor reported as
  hopeless; an executor MUST NOT obtain a retry the declaration never approved; a node MUST NOT be killed without a
  recorded human decision; a threshold breach MUST NOT be recorded as a failure before that decision; and
  **no new heartbeat or watchdog component** may be introduced to observe liveness — existing telemetry is the
  source.
  **Rationale recorded at Gate 3**: the orchestrator is final authority not because it diagnoses
  better — it never overrides the diagnosis — but because it alone holds the decision context the
  executor cannot see: attempts consumed, whether compensation was already issued for this attempt
  (EC-022), whether replanning invalidated the stage (EC-019, EC-020), and whether the run is blocked
  by safe-stop, a pending gate, or a mandatory policy `FAIL`. Executor = diagnosis; orchestrator =
  policy and memory. A refusal path (FR-ORC-021) can only exist outside the governed party.
  **Evidence**: retry histories under injected transient, permanent, unknown, and malformed-envelope
  faults; two-signature audit records; timeout-on-non-idempotent-effect refusal test; threshold-breach escalation
  records for both choices, and a silence case proving suspension rather than kill. *Bounds: PVT-007. Thresholds:
  PVT-016.*

- **FR-ORC-015** — *Fallback.* **[RETIRED by owner Decision K, 2026-09-20 — CR-032.]** This requirement obliged
  stages that can degrade to declare a fallback, activated when the primary path was exhausted and recorded as fallback
  rather than as primary success. **It is retired because its only implementation was removed by Decision J** (ADR-004
  Amendment 02): the six deterministic counterparts that constituted every declared fallback in the system existed to
  serve a keyless mode, and when the mode went, the column emptied.
  **What the system does instead, and it is not nothing**: a failing stage is classified into the closed envelope,
  retried only where the **declared retryable set intersects the executor's proposal** (FR-ORC-014, CR-022), bounded by
  PVT-007, gated on declared idempotency, and on exhaustion the run **suspends safely** — non-terminal, resumable,
  reason recorded (FR-ORC-017). **Bounded retry then safe suspension is the entire degradation story.**
  **Disclosed, not buried**: the assignment names fallback among its reliability controls, so `docs/LIMITATIONS.md`
  carries this retirement as a named absence with its reason (T147). The owner chose a **documented honest absence over
  a ceremonial presence** — a single counterpart kept only to make the behaviour claimable would be the
  exists-mainly-to-be-claimed defect FR-ORC-028 and CN-010 exist to prevent.
  Retired in place rather than deleted: a numbering gap is a question a reviewer cannot answer from the artifact.
  **Evidence**: fallback activation records.

- **FR-ORC-016** — *Rollback and compensation, structural default with declared overrides.*
  **[Confirmed; model fixed by CL-007]** The orchestrator MUST distinguish rollback of reversible,
  uncommitted effects from compensation of already-committed effects, and MUST apply the correct one.
  The choice is determined as follows:

  1. **Structural default.** Anything committed to version control, or written as an immutable
     governance or audit record, is **compensate-only**. Anything still in the local working tree and
     un-pushed is rollback-eligible. This central register is the common case (see §Compensation
     Register).
  2. **Declared override.** A stage MAY add entries where its correction requires product knowledge —
     for example, an unwanted short link is corrected by **setting it to expired, never deleted**.
  3. **Registration-time enforcement.** A stage declaring an irreversible effect **MUST name its
     compensating action or it does not load**. A declaration contradicting the structural rule —
     claiming erasable for an effect landing in an immutable store — is **flagged for human review,
     never silently trusted**.
  4. **Unknown effects are never erased.** An unclassified effect is compensate-only; where no
     compensating action is known, the orchestrator does nothing to the effect and suspends the run
     (FR-ORC-017) with the reason recorded.

  **Accept**: a reversible failure rolls back; a committed effect is compensated; the run history
  labels which occurred; a stage with an unnamed compensating action fails to load.
  **Reject (negative)**: compensation MUST NOT be described as rollback or vice versa; a compensation
  MUST NOT be applied twice for the same effect, including when a retry later succeeds (EC-022); an
  unclassified effect MUST NOT be erased; a contradictory reversibility declaration MUST NOT be
  trusted; an executor acting outside the provided effect channels MUST be refused, recorded as an
  autonomy violation (FR-ORC-021), and MUST NOT reach cleanup.
  **Rationale recorded at Gate 3**: one place holds the common answers; stage authors stay responsible
  only for what they uniquely know.
  **Evidence**: rollback and compensation run histories, separately labelled; registration-failure
  test; contradictory-declaration review flag; no-known-compensation suspension record.

- **FR-ORC-017** — *Safe-stop as a suspended, non-terminal state.* **[Confirmed; semantics fixed by
  CL-005]** The orchestrator MUST enter `SAFE_STOP` when continuation is unsafe, leaving state
  consistent and resumable and emitting a **suspension** outcome with a reason. `SAFE_STOP` is
  **not** a terminal state.
  - **Terminal states are exactly**: `COMPLETED`, `REJECTED`, `ABANDONED`.
  - A suspended run leaves `SAFE_STOP` only by **(a)** a human decision — resume or abandon — or
    **(b)** the pre-approved idle retention policy (FR-ORC-032).

  **Accept**: safe-stop triggers on gate timeout, unrecoverable failure, blocking policy failure,
  an unrecognized failure classification (FR-ORC-014), and an effect with no known compensating
  action (FR-ORC-016); state after safe-stop is resumable.
  **Reject (negative)**: safe-stop MUST NOT continue past the blocked gate on reduced scope without
  a new human decision; it MUST NOT leave partially applied effects unrecorded; it MUST NOT be
  recorded as a terminal outcome; and a suspended run MUST NOT advance by any means other than the
  two named above.
  **Evidence**: safe-stop records for each trigger class; state-machine test asserting `SAFE_STOP`
  is non-terminal and that the terminal set is exactly the three named states.

- **FR-ORC-032** — *Idle retention and auto-abandonment.* **[Confirmed — CL-005]** *(Placed here, out of numeric
  sequence, deliberately: CL-005 introduced it as the completion of FR-ORC-017's safe-stop semantics and set it
  adjacent to them. The identifier was taken at creation time — identifiers are never pre-allocated — so the number
  follows the registry and the position follows the reasoning.)* A suspended run
  MUST be automatically abandoned after a configurable idle period measured **from the run's last
  activity or state change**, never from its creation time.
  **Accept**: the idle clock resets on any activity; on expiry the run moves to `ABANDONED` — a
  terminal state — and an audit event is written citing the retention policy version, actor type
  `system`, and authority "pre-approved retention policy"; every suspended run exposes its computed
  `auto-abandon-at` timestamp via workflow inspection (FR-ORC-008).
  **Reject (negative)**: a run that received attention MUST NOT be reaped on age; policy-driven
  abandonment MUST NOT be recorded as an approval, and MUST NOT advance a run past a gate — silence
  still advances nothing (Constitution III); the auto-abandon timestamp MUST NOT be absent from a
  suspended run's inspection output.
  **Rationale recorded at Gate 3**: a feature is commonly worked for about a quarter, so a run
  awaiting a human answer can legitimately sit that long without being dead. *Idle period:
  PVT-015.*
  **Evidence**: idle-expiry test with clock control; audit event inspection; inspection output
  showing `auto-abandon-at`.

- **FR-ORC-018** — *Resumption.* **[Confirmed]** The orchestrator MUST resume a run after
  recoverable interruption and reach a deterministic terminal outcome without duplicating
  committed effects.
  **Accept**: resumption from a persisted state completes the run; committed effects occur once.
  **Reject (negative)**: concurrent resumption of the same persisted state MUST NOT produce two
  advancing runs (EC-026); resumption MUST NOT re-execute a committed stage's effects (EC-015).
  **Evidence**: interruption-and-resume tests at multiple points per scenario.

- **FR-ORC-019** — *Dynamic replanning.* **[Confirmed]** When upstream outputs change materially,
  the orchestrator MUST re-plan affected downstream stages, invalidate their artifacts, and
  preserve governance across the replan.
  **Accept**: affected stages are identified and re-planned; unaffected paths are untouched; a
  replan event is recorded with its cause.
  **Reject (negative)**: a previously granted approval for an invalidated stage MUST NOT be
  carried forward (EC-020); replanning MUST NOT corrupt an in-flight stage (EC-019) and MUST NOT
  introduce graph cycles (EC-030).
  **Evidence**: replan event records; DS-C run history.

- **FR-ORC-020** — *Brownfield impact analysis.* **[Confirmed — DS-B]** Before any change to
  existing code, the orchestrator MUST produce an impact analysis identifying impacted
  components, interfaces, data flows, tests, and documentation, plus regression risks and
  rollout/rollback considerations.
  **Accept**: the analysis exists, is complete across all seven dimensions, and precedes the
  first code modification in the run history.
  **Reject (negative)**: implementation MUST NOT begin on a brownfield change before the analysis
  is recorded; an analysis MUST NOT omit a dimension silently.
  **Evidence**: DS-B impact analysis artifact with timestamps preceding code changes.

- **FR-ORC-021** — *Bounded autonomy.* **[Confirmed]** Agent autonomy MUST be bounded by declared
  limits, and every autonomous action MUST be observable and attributable after the fact.
  **Accept**: declared limits exist per stage; an action outside them is refused and recorded.
  **Reject (negative)**: **no step in the workflow may submit, satisfy, or advance a human-owned decision** — no
  stage executor, deterministic or AI, has a code path that records a gate outcome, and an executor acting outside
  its provided effect channels is refused and recorded as an autonomy violation (FR-ORC-016).
  **Declared limitation — actor identity is asserted, not verified.** The `actorType` on a submitted gate decision is
  **declared by the caller and not authenticated**: governance surfaces carry no credential by design (CN-012), so
  enforcement rests on the **machine-access trust boundary** — the ability to reach the process. Anything that can
  reach the port can submit a decision asserting it is human. **Verified actor identity is out of scope for this
  demonstration** (owner decision, 2026-09-20; an operator-issued per-decision token was considered and declined).
  What this requirement therefore delivers is that **the system never approves its own gates**, not that an
  impersonating caller is detectable.
  **Evidence**: autonomy limit definitions; refusal records; an architecture assertion that **no executor package
  references the gate-decision path**; the limitation disclosed in `docs/LIMITATIONS.md`.

- **FR-ORC-022** — *Policy check outcomes.* **[Derived — Constitution VI]** Every applicable policy
  check MUST produce exactly one of `PASS`, `FAIL`, `EXCEPTION-REQUESTED`, `NOT-APPLICABLE`, and
  each run MUST record the policy version evaluated.
  **Accept**: outcomes are enumerable per run; a mandatory `FAIL` blocks downstream progression.
  **Reject (negative)**: an unevaluable check MUST NOT default to `PASS` (EC-025); an
  `EXCEPTION-REQUESTED` without recorded approval MUST be treated as `FAIL`; an expired exception
  MUST NOT continue to hold (EC-027).
  **Evidence**: policy evaluation records per run.

- **FR-ORC-023** — *Audit inspection.* **[Confirmed]** The orchestrator MUST record state
  transitions, decisions, approvals, retries, failures, replanning events, and terminal outcomes,
  each with actor type, action, timestamp, affected artifact or state, result, and reason.
  **Accept**: a reviewer reconstructs a run from persisted audit records alone, with no access to
  the original session.
  **Reject (negative)**: an audit record MUST NOT omit any of the six fields; audit records MUST
  NOT be mutable after the fact.
  **Evidence**: audit export; adversarial reconstruction result (US-5).

- **FR-ORC-024** — *Reliability and orchestration measurements.* **[Confirmed]** The orchestrator
  MUST expose measurements covering success, failure, retry, compensation, recovery, and latency.
  **Accept**: measurements are retrievable per run and in aggregate.
  **Reject (negative)**: demonstration measurements MUST NOT be presented as production
  measurements (Constitution IX); unmeasured targets MUST NOT be presented as measurements.
  **Evidence**: measurement output with explicit measurement conditions.

- **FR-ORC-025** — *Release-readiness determination.* **[Confirmed]** The orchestrator MUST
  determine release readiness and MUST block while any of the nine conditions in Constitution
  §Governance holds, naming every unmet condition.
  **Accept**: each condition, seeded individually, produces a blocking determination naming it.
  **Reject (negative)**: readiness MUST NOT be self-certified by an agent; generated-but-unexecuted
  tests MUST NOT count as passing; a pass MUST NOT be issued while an exception is unapproved or
  expired.
  **Evidence**: one negative test per blocking condition; recorded release owner decision.

- **FR-ORC-026** — *Final engineering summary.* **[Confirmed]** The orchestrator MUST produce a
  final engineering summary covering what was built, decisions and rejected alternatives,
  executed validation and results, residual risks and limitations, and the AI-assisted process
  including deviations from plan.
  **Accept**: the summary is generated from recorded evidence and every claim in it is traceable.
  **Reject (negative)**: the summary MUST NOT contain unverifiable claims, fabricated metrics, or
  undisclosed limitations (Constitution X).
  **Evidence**: the summary artifact plus its traceability links.

- **FR-ORC-027** — *Traceability maintenance.* **[Derived — Constitution X]** The orchestrator MUST
  maintain the chain requirement → scenario → decision → design → task → implementation → test →
  validation → documentation → evidence, navigable in both directions.
  **Accept**: 100% of delivered requirements traverse to executed tests and evidence, and every
  test traverses back to a requirement.
  **Reject (negative)**: a delivered requirement MUST NOT lack a test; a test MUST NOT lack a
  requirement.
  **Evidence**: generated traceability matrix with zero orphans.

- **FR-ORC-028** — *Single executor interface; generic engines only.* **[Confirmed — AQ-003]** Every
  stage MUST run behind one executor interface. Deterministic executors MUST be generic engines
  operating on data flowing through the workflow.
  **Accept**: all twelve stages are invoked through the same interface; an executor's behavior is a
  function of workflow data, not of which requirement is being processed; a requirement outside the
  three demonstration scenarios flows through the same path.
  **Reject (negative)**: no executor may branch on recognizing specific demonstration inputs; the
  system MUST NOT be restricted to three requirements — the three scenarios are required
  demonstrations, not a design ceiling.
  **Rationale recorded at Gate 2**: executors that recognize blessed demo inputs would be a rigged
  demonstration and would fail the reviewer's question "is the evidence strategy authentic?". The
  engine does not know the build; the pipeline definition does.
  **Evidence**: a run over a requirement outside DS-A/B/C, completing without executor changes;
  code inspection for input-specific branching.

- **FR-ORC-029** — *Executor kind recorded per execution.* **[Confirmed — AQ-003; amended by CR-001; flag removed
  by CR-028]** Each stage MUST declare its executor class per the approved executor map (see §Stage Executor Model),
  and **every run's evidence MUST record the executor kind for each stage execution**, drawn from
  **`DETERMINISTIC` / `AI` / `HUMAN`**.
  **Accept**: every recorded execution carries its kind; the five deterministic stages record `DETERMINISTIC`;
  AI-capable stages record `AI`; a stage executed by a human under the no-plan gate records `HUMAN` (FR-ORC-031).
  **Reject (negative)**: a deterministic execution MUST NOT be presented as AI work; an unlabelled stage execution MUST
  NOT appear in evidence; and **no run-level flag may claim a property of the run that the run does not control** — the
  reason CR-001 renamed the former flag, and the reason Decision J removed it rather than renaming it again.
  **Rationale recorded at CR-028**: with a single runtime path the label is the only thing that distinguishes what ran,
  so it carries more weight than it did when a flag also existed. `HUMAN` was never flag-selectable and still is not.
  **Evidence**: per-stage executor-kind labels in every run's evidence, with the pinned model id for AI executions.

- **FR-ORC-030** — *Injectable executors for deterministic reliability proofs.* **[Confirmed —
  AQ-003]** Automated tests MUST be able to inject scriptable fake executors — for example "fail
  twice then succeed" — so that retry, timeout, fallback, rollback and compensation, safe-stop,
  resumption, and replanning are proven deterministically.
  **Accept**: each reliability behavior in FR-ORC-014..019 has a test driven by a scripted executor
  with a deterministic outcome.
  **Reject (negative)**: reliability proofs MUST NOT depend on AI availability, AI variability, or
  network access.
  **Evidence**: reliability test suite passing with no AI key and no network.

- **FR-ORC-031** — *AI-authored implementation under governed verification.* **[Confirmed —
  AQ-003]** The implementation stage MUST be AI-capable: the AI authors the change from the design
  stage's output, the engine applies it on a branch, the **real** build and test suite verify it,
  and failure routes back with the report under bounded attempts before escalating to the human
  gate. **There is no deterministic fallback for this stage** (Decision J, ADR-004-A2).

  **No-change-plan gate** *(added by CR-001, approved 2026-09-20; its premise restated by CR-028)*. Where **no change
  plan exists** for the requirement, the stage MUST NOT proceed — and with no counterpart engine to apply one, the gate,
  not a fallback, is what prevents an unimplemented change from advancing. It MUST
  suspend at a human gate, stating its expiry consequences in the ask per CL-005 (FR-ORC-013), and
  present exactly three options:
  1. **Proceed as a governance-only run** — recorded decision; downstream stages continue; run evidence
     labels implementation as **intentionally skipped by human decision**.
  2. **Human-implemented** — the human makes the change themselves, externally in the working tree or by
     supplying change content with the decision, and the run proceeds into real testing that judges it.
     That execution is recorded with executor kind **`HUMAN`** (FR-ORC-029).
  3. **Abandon the run** — terminal `ABANDONED` with the reason recorded.

  **Accept**: an AI-authored change is applied on a branch, verified by the real suite, and either
  passes or routes back with its failure report; the bound is respected before escalation; an
  execution with no change plan suspends at the no-plan gate and each of the three options produces its defined
  effect.
  **Reject (negative)**: creative authoring MUST NOT happen outside a stage; a failing AI-authored
  change MUST NOT be reported as success or merged; the verification suite MUST NOT be stubbed or
  simulated for this stage; **a stage with nothing implemented MUST NOT record success, and MUST NOT
  allow downstream stages to execute, on the basis of a labelled no-op** — proceeding with nothing
  implemented requires a recorded human decision.
  **Rationale recorded at Gate 2**: the governed pipeline is the safety net for AI-authored code,
  which is the thesis of this assessment implemented literally. An AI patch failing tests and being
  caught is not a failed demonstration — it is the governance visibly working.
  **Rationale recorded at CR-001**: a labelled no-op that flows onward is quiet pretending. Proceeding
  with nothing implemented is a material fact a human must consciously accept, not discover in a label
  afterwards — nothing material advances on inference or silence.
  **Evidence**: a run where an AI-authored change fails verification and routes back; a run where one
  passes; a run exercising each of the three no-plan-gate options; all with executor-kind labels.

### Key Entities

- **KE-01 ShortLink**: an issued short code bound to a destination, with creation time, expiry,
  current state, and the owning creator (KE-23). Referenced by RedirectEvent.
- **KE-02 RedirectEvent**: a recorded act of following a short link, carrying the short code and the
  occurrence timestamp and no follower-identifying field (AQ-002). Belongs to one ShortLink.
- **KE-03 IdempotencyRecord**: the marker binding a creation request to the link it produced, so
  a repeat yields the same link (FR-URL-012).
- **KE-04 WorkflowRun**: one governed execution, with a durable identifier, current state, policy
  version evaluated, last-activity timestamp, computed `auto-abandon-at` when suspended, and its
  terminal outcome once it reaches one (`COMPLETED`, `REJECTED`, `ABANDONED`) — a suspended run has no
  terminal outcome yet (FR-ORC-017, FR-ORC-032).
- **KE-05 StageNode**: one of the twelve lifecycle stages within a run, with inputs, outputs,
  entry and exit criteria, owner, status, and failure behavior.
- **KE-06 DependencyEdge**: a declared precedence relation between StageNodes, including join
  semantics.
- **KE-07 RequirementRecord**: a normalized, identified requirement with type (functional /
  non-functional), statement, and status.
- **KE-08 AmbiguityRecord**: a detected ambiguity, its class, the affected path, and its
  resolution state.
- **KE-09 ClarificationDecision**: a human answer to an AmbiguityRecord, with actor, question,
  answer, and timestamp.
- **KE-10 ApprovalGate**: a checkpoint on a StageNode requiring a human decision, with its
  configured wait.
- **KE-11 GateDecision**: the recorded outcome of an ApprovalGate — outcome, actor, timestamp,
  reason.
- **KE-12 TaskRecord**: a decomposed unit of work, its requirements, its dependencies, and its
  validation status.
- **KE-13 ImpactAnalysis**: the brownfield pre-change assessment across the seven dimensions of
  FR-ORC-020.
- **KE-14 ReplanEvent**: a recorded replanning action, its cause, the stages invalidated, and the
  approvals voided.
- **KE-15 RetryAttempt**: one bounded attempt at a StageNode, with failure classification and
  outcome.
- **KE-16 CompensationAction**: a corrective action for an already-committed effect, distinct from
  a rollback.
- **KE-17 PolicyCheckResult**: one policy evaluation — policy, version, outcome, reason.
- **KE-18 PolicyException**: an approved deviation, with policy, reason, scope, approving
  authority, compensating control, approval timestamp, and expiry or review condition.
- **KE-19 AuditRecord**: an immutable event carrying actor type, action, timestamp, affected
  artifact or state, result, and reason.
- **KE-20 EvidenceArtifact**: a stored output supporting a completion claim, labelled measured or
  proposed.
- **KE-21 ReleaseReadinessReport**: the determination plus every unmet blocking condition.
- **KE-22 TraceLink**: a directed relation between two artifacts in the FR-ORC-027 chain.
- **KE-23 Creator**: a provisioned identity permitted to create links and read its own links'
  analytics — **and nothing else**. Owns zero or more ShortLinks. Provisioned by operator script, never
  self-service (FR-URL-019). A Creator is a **URL-shortener concept only** and has no standing in the
  orchestrator's actor model (CN-012).
- **KE-24 CreatorCredential**: the stored **hash** of a creator's API key, never the key itself, covering the
  full presented string. Belongs to one Creator. Carries a **nullable `expires_at`**, where null is reachable
  only by an explicit `never` at provisioning, and an optional `revoked_at`. *(Amended by CR-002.)*
- **KE-25 StageExecutor**: the bound implementation for a StageNode, carrying its executor class
  (deterministic engine, AI-capable, or human gate) and the **executor kind actually used** in a given
  run — `DETERMINISTIC`, `AI`, or `HUMAN` (FR-ORC-029). *(Amended by CR-001: `HUMAN` is an executor kind
  distinct from `HUMAN_GATE` — a human **implementing** a change is not the same as a human **deciding**
  a gate.)*
- **KE-26 FailureEnvelope**: a failure crossing the orchestration boundary — standard category,
  executor-proposed classification, and detail (FR-ORC-014).
- **KE-27 RetryRuling**: the orchestrator's two-signature retry decision — what the executor proposed
  and what the orchestrator ruled, with the reason (FR-ORC-014).
- **KE-28 EffectRecord**: an applied stage effect with its reversibility class and, where
  irreversible, its named compensating action (FR-ORC-016, §Compensation Register).
- **KE-29 StageEffectContract**: a stage's design-time declaration of its retryable categories and
  whether its effect is idempotent or repeat-safe (FR-ORC-014 rule 4).

---

## Demonstration Scenarios *(mandatory)*

Three scenarios are required. Each is a named, reproducible execution with recorded evidence.

### DS-A — Greenfield, well-specified requirement

**Path**: Requirement → Decomposition → Design → Implementation → Testing → Documentation →
Validation → Release Readiness.

A requirement that is complete, consistent, testable, and inside approved policy and architecture
boundaries MUST proceed **without an artificial clarification gate**. The run MUST record the
requirement-quality checks performed, the explicit reason clarification was not required, the
resulting decomposition, the API and schema impacts, the acceptance criteria, and traceable
execution evidence.

If material ambiguity emerges later in the run, the orchestrator MUST suspend **only the affected
path**, invoke governed clarification and impact analysis, and resume from the correct state after
explicit approval.

**Acceptance**: the run completes with no clarification gate fired; the quality-check record and
the no-clarification justification are present; late-emerging ambiguity suspends only its path.
**Negative acceptance**: a clarification gate MUST NOT fire merely to demonstrate that gates
exist; unaffected paths MUST NOT be suspended.
**Evidence**: DS-A run export — graph, per-stage criteria evaluation, quality checks, decomposition,
executed test results, traceability matrix.

### DS-B — Brownfield change against the existing URL shortener

An enhancement, refactor, or defect correction against code that already exists. Before any code
changes, the run MUST identify impacted components, impacted interfaces, impacted data flows,
impacted tests, impacted documentation, regression risks, and rollout or rollback considerations.

**Acceptance**: the impact analysis is complete across all seven dimensions and its timestamp
precedes the first code modification; the change lands with regressions detected by pre-existing
tests, not by inspection.
**Negative acceptance**: implementation MUST NOT begin before the analysis is recorded; the
analysis MUST NOT be reconstructed after the change.
**Evidence**: DS-B impact analysis artifact; ordered run history; test results before and after.

### DS-C — Ambiguous requirement

Input that is incomplete, unclear, or internally conflicting. The run MUST detect the ambiguity,
prevent unsafe implementation, request human clarification, record the decision, identify
downstream impact, update affected artifacts, and resume at the correct state.

**Acceptance**: ambiguity is detected before implementation; the affected path suspends; the
recorded decision drives a replan of exactly the affected downstream artifacts; the run resumes
and terminates deterministically.
**Negative acceptance**: the orchestrator MUST NOT resolve the ambiguity by inference; it MUST NOT
implement the affected path before clarification; it MUST NOT carry forward approvals voided by
the replan.
**Evidence**: DS-C ambiguity record, clarification decision, replan event, resumption record.

---

## Compensation Register *(mandatory)*

Confirmed at Gate 3 under CL-007. This is the central structural table FR-ORC-016 resolves against.
Stages append declared overrides; they never contradict a row here without human review.

| Effect | Reversible? | Correct action | Basis |
|--------|-------------|----------------|-------|
| Working-tree file writes (docs, summary, plan) | **Yes** | Rollback — discard | Discardable before commit |
| Local branch commits (stage 7) | **Yes** | Rollback — reset or delete branch | Nothing is pushed (CL-004); the only erasable class |
| Recorded gate decision | **No** | Compensate — write a superseding record referencing the prior one | `CLAUDE.md` immutability. EC-020's voided approval is a superseding record, never a deletion |
| Audit records | **No** | Compensate — append a corrective entry | NFR-AUD-001 immutability |
| Created short links | **No** | Compensate — set to expired, never delete | EX-003 excludes deletion. Rationale: dangling history, short-code reuse hijack, repeat-safe cleanup |
| Recorded redirect events | **No** | Compensate — append correction | Append-only analytics (FR-URL-010) |
| AI provider invocations | **No** | Nothing to compensate | Cannot be un-called; no external state changed |

Any effect not matching a row here is treated as **compensate-only** (FR-ORC-016 rule 4).

## Retention Posture *(CR-017, 2026-09-20)*

**This demonstration retains every record indefinitely.** No deletion, purge or archival path exists — for audit
records, run history, gate decisions, policy results, or redirect events. The audit tables' insert-and-select-only
grants and the tests asserting that the store rejects an UPDATE or a DELETE are unchanged, and with no retention path
anywhere the §Compensation Register's *"never delete"* rows are now **literally true system-wide** rather than true by
convention.

**What production would do, recorded as a recommendation and not built:**

| Data | Recommended production bound | Clock |
|---|---|---|
| Redirect events | 30 days | Record creation |
| Terminated-run history and audit records | 90 days | **Run termination, never record creation** (CR-004) — so a live or suspended run's history can never age out |

An archival job would move records past those bounds out of the live tables. It is not implemented, it is not
scheduled, and it is not a deferred task pretending to be a backlog item: it is **out of scope for a demonstration**,
recorded so that a reviewer can see the production shape was reasoned about rather than missed.

**Why this posture rather than an implementation.** A purge makes records inaccessible, which the owner rejected. An
archival move preserves access but requires an archive format, a location, a crash-reconciliation rule, and a DELETE
privilege against tables whose insert-and-select-only grants are the mechanism that makes audit immutability real —
an exception to a control for a housekeeping benefit no demonstration needs. Indefinite retention costs a
demonstration nothing and keeps every control intact.

## Stage Executor Model *(mandatory)*

Approved at Gate 2 under AQ-003. The orchestrator is not itself the AI — it **governs** agents,
which is the assignment's own principle: agents execute under defined autonomy boundaries and humans
own oversight. Stages whose work is genuinely creative are AI-capable; stages whose value is
repeatability are deterministic engines. Nothing is hardcoded to demonstration inputs (FR-ORC-028).

| Stage | Executor class |
|-------|----------------|
| 1 Requirement ingestion | Deterministic engine (intake, run creation) |
| 2 Requirement normalization | AI-capable |
| 3 Ambiguity detection | AI-capable — output feeds the human gate, so variability is safe |
| 4 Human clarification | Human gate — no executor |
| 5 Task decomposition | AI-capable |
| 6 Architecture & design | AI-capable |
| 7 Implementation | AI-capable — AI authors the change, engine applies on a branch, real build and tests verify. Deterministic fallback **applies a change plan**; where **no plan exists** the stage suspends at the no-plan gate (governance-only / human-implemented / abandon) and never no-ops forward. A human-implemented execution records executor kind `HUMAN` (FR-ORC-031, CR-001) |
| 8 Testing | Deterministic, **real** — executes the actual test suite |
| 9 Documentation | AI-capable |
| 10 Security & policy checks | Deterministic, **real** — policy verdicts must be repeatable |
| 11 Release readiness | Deterministic evaluation of the nine blocking conditions, plus the release owner's recorded decision |
| 12 Final engineering summary | Deterministic assembler from recorded evidence only — every claim must be traceable, so creativity is a liability here |

### What ambiguity detection is, and what it is not

Stage 3 is **AI-backed, and semantic**. It reasons over the submitted requirement rather than matching patterns in it,
which is why CL-003 made this stage AI-capable and why **Decision J's removal of the keyless mode removed no capability
here** — there was never a deterministic detector that could do this work.

**What that buys**: conflicts between *different concepts* are detectable. The demonstration input — expire after a
week, retain analytics indefinitely, still redirect for trusted partners — is a contradiction between link lifetime,
analytics retention and redirect entitlement. **No two clauses bound the same field**, so no structural rule would fire
on it; recognising it requires understanding what expiry means for redirects.

**What it costs, stated because it is the honest counterweight**:

- **Detection is non-deterministic.** The same input may be classified differently across runs. This is acceptable
  *here specifically* because the output feeds a **human gate** — a false positive costs a question, and the human is
  the decider either way. It would not be acceptable at stage 10, which is why policy verdicts are deterministic.
- **A miss is possible and is not distinguishable from an absence of ambiguity** without a human reading the
  requirement. The recorded `no_clarification_reason` is what makes a non-detection inspectable rather than silent
  (DS-A's requirement), and it is the only control against a quiet miss.
- **The structural alternative was considered and would have been narrower, not safer.** A deterministic checker could
  decide six classes — missing acceptance criteria, undefined term, unbounded quantifier, missing actor,
  self-referential constraint, and contradictory bounds on the *same named field*. It could not decide the
  demonstration input. **A checker that fired on words like "but" or "indefinitely" would look like semantic detection
  while being a vocabulary list** — it would pass the demonstration and fail the next input, and a reviewer would
  reasonably read it as tuned to the demo, which is the rigged-demonstration failure FR-ORC-028 and CN-010 exist to
  prevent. An honest narrow capability beats a broad-looking keyword match; an honest semantic capability with declared
  variability beats both.

**Binding conditions** (FR-ORC-028, FR-ORC-029, FR-ORC-030):

- One executor interface for every stage; no executor branches on recognizing specific inputs.
- Tests inject scriptable fake executors, so every reliability proof is deterministic and
  independent of AI availability.
- **Orchestration always uses AI** (Decision J, ADR-004-A2). There is no run-level switch and no deterministic
  counterpart for an AI-capable stage. The five genuinely deterministic stages — S1, S8, S10, S11, S12 — keep real
  engines and record `DETERMINISTIC`. **Tests never call live AI**: reliability proofs are driven by injected fakes
  (FR-ORC-030), which is why the test suite remains keyless even though the orchestrator does not.
- `HUMAN` is **never selectable by any caller or configuration**; it is recorded as the outcome of a no-plan-gate
  decision (FR-ORC-031).
- Every run's evidence records each stage execution's **executor kind** — `DETERMINISTIC`, `AI`, or
  `HUMAN`. Deterministic executions are labelled demonstration executions and are never presented as AI
  work.

## Non-Functional Requirements *(mandatory)*

Every numeric figure below is a **proposed** validation target (PVT-nnn), not a client
requirement. None constrains implementation until approved.

- **NFR-SEC-001** — All external input is validated and normalized before use; only allow-listed
  schemes are accepted. *Verifiable: allow-list and normalization test matrices.*
- **NFR-SEC-002** — Zero secrets or sensitive values in logs, traces, metrics, or error
  responses. *Verifiable: automated scan over the demonstration corpus; zero findings required.*
- **NFR-SEC-003** — Threat scenarios, authentication assumptions, rate-limiting behavior, trust
  boundaries, and accepted security trade-offs are documented — explicitly including the
  operator-script trust boundary (FR-URL-019) and the noisy-neighbor throttling trade-off
  (FR-URL-016). *Verifiable: document review against both named items.*
- **NFR-SEC-004** — Dependency vulnerability scanning and secret scanning run as part of release
  readiness. *Verifiable: scan execution evidence in the readiness report.*
- **NFR-SEC-005** — **Data minimization**: the service stores no personal data. No
  follower-identifying field is captured (AQ-002), and no plaintext credential is stored anywhere
  (FR-URL-019). *Verifiable: stored-schema inspection; zero personal-data fields; secret scan over
  repository and telemetry.*
- **NFR-REL-001** — Every orchestration run reaches a terminal state (`COMPLETED`, `REJECTED`,
  `ABANDONED`) **or** a durable suspended state (`SAFE_STOP`) from which only a human decision or
  resumption proceeds. *(Reworded at Gate 3 per CL-005; the prior wording conflicted with
  FR-ORC-017's resumability.)* *Verifiable: zero runs in an indeterminate state — that is, in neither
  the terminal set nor a durable suspended state — across the demonstration corpus.*
- **NFR-REL-002** — Transient failures are retried within declared bounds; permanent failures are
  not retried as transient. *Verifiable: fault-injection results. Bounds: PVT-007.*
- **NFR-REL-003** — Committed effects occur exactly once across interruption and resumption.
  *Verifiable: duplicate-effect count of zero under interruption testing.*
- **NFR-SCA-001** — Redirect resolution sustains PVT-003 concurrent clients without error-rate
  degradation beyond PVT-004. *Verifiable: load test at declared conditions.*
- **NFR-SCA-002** — Short-code keyspace supports PVT-005 links before collision-retry pressure
  materially affects creation latency. *Verifiable: keyspace calculation plus collision test.*
- **NFR-SCA-003** — Creation and resolution scale independently, and the design states which
  component becomes the first bottleneck and why. *Verifiable: documented analysis plus the
  measurement that supports it.*
- **NFR-MNT-001** — Domain, delivery, persistence, orchestration, policy, telemetry, and
  infrastructure concerns are separated into independently testable units. *Verifiable: dependency
  direction check; no domain dependency on delivery or persistence detail.*
- **NFR-MNT-002** — External dependencies are reached only through interfaces owned by this
  codebase. *Verifiable: boundary inspection.*
- **NFR-OBS-001** — Every run carries a correlation identifier propagated across logs, metrics,
  traces, and persisted state. *Verifiable: identifier present on 100% of records for a sampled
  run.*
- **NFR-OBS-002** — Logs, metrics, traces, and workflow history suffice to reconstruct an
  execution without the original session. *Verifiable: adversarial reconstruction by a reader who
  did not run it.*
- **NFR-AUD-001** — Every audit record carries all six mandatory fields, and audit records are
  immutable after write. *Verifiable: schema validation over the full audit corpus; mutation
  attempt rejected.*
- **NFR-AUD-002** — Audit evidence retains policy outcomes and exceptions. **Because this demonstration retains all
  records indefinitely (NFR-AUD-003), FR-ORC-023's reconstruction requirement is satisfied for every run without
  qualification** — there is no purge that could defeat it. The **run-termination clock rule** decided at CR-004
  survives as part of the production recommendation in PVT-010: were retention implemented, the clock would start at
  run termination, never record creation, so a live or suspended run's history could never age out. *(Amended by
  CR-017; CR-004's reasoning is preserved, not discarded.)* *Verifiable: presence check per run; a reconstruction
  performed on the oldest run in the corpus.*
- **NFR-AUD-003** — **Indefinite retention in this demonstration; production archival recorded as a recommendation.**
  No record is deleted, purged, archived, or otherwise removed from its live table by any retention path, because no
  such path exists. Audit records, run history, gate decisions, policy results and redirect events are retained
  indefinitely. The **production recommendation** — an archival job moving redirect events past 30 days and terminated
  runs' history past 90 days out of the live tables, with the run-termination clock rule of CR-004 — is documented in
  `docs/LIMITATIONS.md` as work a real production deployment would do and this demonstration deliberately does not
  *(CR-017)*. *Verifiable: an architecture test asserting **no deletion, purge or archival path exists anywhere** in
  the codebase; the existing audit-immutability tests, which assert the store rejects an UPDATE or a DELETE, continue
  to pass **unmodified**; and a review confirming the production recommendation is documented.*
- **NFR-PERF-001** — Redirect resolution completes within PVT-001 at the p95 under stated
  conditions. *Verifiable: measured, with conditions declared.*
- **NFR-PERF-002** — Link creation completes within PVT-002 at the p95 under stated conditions.
  *Verifiable: measured, with conditions declared.*
- **NFR-REC-001** — A run interrupted at any stage boundary resumes and reaches a terminal state, for
  **both** interruption classes: orchestrator-process restart and persistence-layer restart (CL-009).
  *Verifiable: interruption at every stage boundary in at least one scenario, plus a persistence-layer
  restart injected mid-run.*
- **NFR-REC-002** — Mean time to recovery is defined, instrumented, and reported. **Deferred to
  Plan stage** by the human owner at Gate 1: the MTTR definition and its measurement rules are a
  Plan-stage deliverable, so no target is proposed here. *Verifiable once defined.*
- **NFR-TST-001** — The portfolio includes unit, integration, API contract, orchestration state
  transition, reliability, security, and end-to-end tests. *Verifiable: test inventory by category,
  each category non-empty.*
- **NFR-TST-002** — Red-green-refactor is evidenced, not asserted: the failing-first state is
  recorded for tests where TDD was practical. *Verifiable: commit or execution history showing red
  before green.*
- **NFR-TST-003** — Coverage of domain and orchestration transition logic meets PVT-008.
  *Verifiable: measured coverage report.*
- **NFR-CHG-001** — Changes to approved requirements, architecture, schemas, workflow states,
  security controls, or release criteria pass through recorded impact analysis and change
  approval. *Verifiable: change records exist for every such change.*
- **NFR-CHG-002** — Replanning voids approvals for invalidated stages rather than carrying them
  forward. *Verifiable: negative test on EC-020.*
- **NFR-AUT-001** — Autonomy limits are declared per stage, and **no workflow step submits or satisfies a
  human-owned decision**. Actor identity on a submitted decision is **declared, not verified** (FR-ORC-021's declared
  limitation, CN-012): an impersonating caller with process access is not detectable, and verified actor identity is
  out of scope for this demonstration. *Verifiable: per-stage autonomy declarations; an architecture test asserting no
  executor package references the gate-decision path; a refusal test for a self-identified agent actor. **Not**
  verifiable, and not claimed: that a caller asserting `actorType: human` is in fact human.*
- **NFR-AUT-002** — Absence of human response never advances a mandatory gate. *Verifiable: the
  silence test in FR-ORC-013.*
- **NFR-AUT-003** — Every stage execution in every run's evidence is labelled with the executor kind
  actually used, and deterministic executions are never presented as AI work. *Verifiable: zero
  unlabelled stage executions across the demonstration corpus.* **Now the sole distinguisher of what ran** — with the
  run-level flag removed (CR-028), no other field records it.
- **NFR-AUT-004** — *(RETIRED by owner Decision J, 2026-09-20 — CR-028.)* Derived from CN-011 and retired with it.
  **NFR-AUT-003 is unaffected and is now more load-bearing**: every execution in every run's evidence carries its
  executor kind, and a deterministic execution is never presented as AI work. With one runtime path, the label is the
  only thing distinguishing what actually ran.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A valid destination submitted by an API consumer yields a short link that resolves
  to the exact original destination, for 100% of the acceptance corpus.
- **SC-002**: Disallowed or malformed destinations are refused with an actionable reason for 100%
  of the negative corpus, and zero disallowed-scheme destinations are ever stored.
- **SC-003**: Expired links never redirect, and the expired outcome is distinguishable from
  not-found in 100% of expiry tests.
- **SC-004**: An assessment reviewer with repository access and no session access reconstructs
  all three demonstration runs end-to-end from persisted evidence alone.
- **SC-005**: No mandatory approval gate can be passed without a recorded human decision,
  demonstrated by a silence test that produces safe-stop rather than progression.
- **SC-006**: An interrupted run resumes and reaches a deterministic terminal outcome with zero
  duplicated committed effects, at every stage boundary tested.
- **SC-007**: A material upstream change re-plans exactly the affected downstream stages and
  leaves unaffected paths unchanged, demonstrated in DS-C.
- **SC-008**: Brownfield changes are preceded by an impact analysis covering all seven required
  dimensions, in 100% of DS-B runs.
- **SC-009**: Release readiness blocks and names the condition for each of the nine blocking
  conditions, verified by one negative test per condition.
- **SC-010**: 100% of delivered requirements trace to at least one task, one executed test with a
  recorded result, and one evidence artifact — and every test traces back to a requirement.
- **SC-011**: Zero secrets or sensitive values appear in logs, traces, metrics, or error
  responses across the demonstration corpus.
- **SC-012**: Every quantitative figure in the final engineering summary is labelled measured
  (with conditions) or proposed, with zero unlabelled figures.
- **SC-013**: Redirect resolution meets PVT-001 at PVT-003 concurrency under declared conditions.
  *(Target pending approval.)*
- **SC-014**: *(RETIRED by owner Decision J, 2026-09-20 — CR-028.)* This criterion required the complete system to run
  end to end with no AI key and no network. **The narrower claim that survives is FR-ORC-030's**: the reliability suite
  and the full test suite pass with no AI credential and no network, because they are driven by injected fakes. The
  *orchestrator* no longer runs keyless, and no criterion claims it does.
- **SC-015**: Zero stage executions appear in evidence without an executor-**kind** label, and zero
  deterministic executions are presented as AI work.
- **SC-016**: A requirement outside the three demonstration scenarios completes a full run with no
  executor code changes, demonstrating that executors are generic engines rather than input-aware.
- **SC-017**: Zero personal data fields exist in the stored schema, and zero plaintext credentials
  exist in the repository or in application telemetry.

---

## Validation Targets *(approved)*

**Sixteen targets, of which fifteen constrain implementation and one is retired.**

- **PVT-001..PVT-009 and PVT-011..PVT-015** — fourteen values approved by Pravallika Veeravalli at the Gate 4
  closing package, 2026-09-20 (`docs/governance/gate-decisions/gate-04-closing-package.md`, CR-005). They
  **constrain implementation** and are acceptance thresholds, no longer proposals. **PVT-009** was subsequently
  restated by **CR-010** — the same 0.5%, as an observable append-failure ceiling rather than a loss budget.
- **PVT-016** — added by **CR-010** (per-node escalation thresholds). Binding in the same sense: every node must
  have a threshold and breaching one must ask the human.
- **PVT-010** — **retired as a binding target by the owner's explicit decision, 2026-09-20 (CR-017)**, and replaced
  by a production recommendation. It is retained in the table as a recommendation, not deleted, so that a reader can
  see what production would do and that the demonstration deliberately does not do it. **It is not measured and not
  enforced.**

Fifteen binding, one retired, sixteen rows. Any row's binding status is stated in its own Conditions cell, so the
count above can be checked against the table rather than trusted.

They originated as this specification's own proposals — the assignment supplied no numeric targets — and were
held as non-binding until approval, per Constitution X and §Assessment Scope's demonstration-versus-production
rule. Four were named explicitly in the approval: **PVT-009** as redefined by ADR-014 (an append-failure-rate
ceiling, not a loss budget), **PVT-013/014** (the owner's two-tier redirect limits), **PVT-015** (90-day idle
retention), and **PVT-001**, approved with the knowledge that the analytics append now sits inside its budget and
is measured separately.

Every figure measured against these thresholds remains a **demonstration measurement** and must be labelled as
such (Constitution IX, AS-009). Approval binds the target; it does not convert a demonstration figure into a
production statistic.

| ID | Target | Proposed value | Basis | Conditions |
|----|--------|----------------|-------|------------|
| PVT-001 | Redirect resolution latency, p95 | ≤ 50 ms | Redirect is on the user's critical path; a short link should feel instant | Single instance, warm, local persistence, PVT-003 concurrency |
| PVT-002 | Link creation latency, p95 | ≤ 200 ms | Creation is an explicit, tolerant action | Same conditions as PVT-001 |
| PVT-003 | Sustained concurrent clients | 100 | Demonstrates concurrency invariants without turning the prototype into a load-testing exercise | Local, single instance |
| PVT-004 | Error-rate ceiling under PVT-003 | ≤ 0.1% non-4xx | Distinguishes load degradation from correctness failure | Excludes deliberate 4xx |
| PVT-005 | Keyspace before collision pressure | ≥ 10^9 links | Sets code length and alphabet | Uniform random issuance |
| PVT-006 | Gate wait before safe-stop | 24 h | Long enough for a real reviewer; short enough to demonstrate the timeout path | Configurable; demonstration runs may use a compressed value, labelled as such |
| PVT-007 | Retry bound and backoff | 3 attempts, exponential from 1 s | Bounded per Constitution VIII without masking permanent faults | Per transient-classified stage |
| PVT-008 | Coverage, domain and orchestration transitions | ≥ 85% branch | Meaningful for governed logic without coverage theatre | **Denominator declared before measurement, not chosen after it.** Included: `domain`, `application`, `orchestration/state`, `orchestration/graph`, `orchestration/reliability`, `orchestration/replan`, `policy`. Excluded with a stated ground each, every one covered by a different test tier rather than by nothing: `config`, `delivery`, `persistence`, `audit/telemetry`, `orchestration/executor/ai`, and generated sources — see T145 (CR-027). **"Infrastructure" is no longer an undefined term here**: a threshold whose denominator is selectable is not a threshold |
| PVT-009 | Analytics append-failure ceiling, **observable** | ≤ 0.5% of appends may **fail**, every failure counted and visible, **zero silent failures** | **Redefined by ADR-014 from a loss budget to a failure ceiling — the same number, a strictly stronger promise.** A loss budget permits events to vanish unremarked; a failure ceiling permits a bounded number of *counted, visible* failures and no silent ones. 0% was rejected: it cannot survive its own fault-injection test — when the store is deliberately killed, appends must fail and be counted, and that is the design working. A target unfalsifiable under fault injection is not a target | At PVT-003 concurrency. Measured from the **recording port's failure counter** (ADR-014 Condition 2), which every append passes through. No append failure may block or delay the redirect (EC-012, FR-URL-010) |
| PVT-010 | Retention — **production recommendation, not a demonstration target** | *Recommended for production*: 90 days audit and terminated-run history, 30 days redirect events | **This demonstration retains all records indefinitely** (NFR-AUD-003, Decision H, CR-017). Retention was reduced from a binding target to a recorded recommendation because implementing a purge or an archival move added an archive format, a crash-reconciliation rule, and a DELETE privilege against the audit tables' insert-and-select-only grant — machinery the assessment does not ask for, against a housekeeping benefit a demonstration does not need. **Withdrawn as a binding target by the owner's explicit decision, 2026-09-20, not by implication** | **Not measured and not enforced.** If it were implemented, the audit and run-history clocks would run **from run termination, never record creation** (CR-004 — DF-003's reasoning is preserved inside the recommendation), so a live or suspended run's history could never age out. No personal data is held (AQ-002), so retention is not a privacy control here and its absence creates no exposure |
| PVT-011 | Default link TTL when unspecified | 30 days | Bounded default rather than unbounded growth | Caller may override within limits |
| PVT-012 | Creation rate limit | 60 requests/minute per creator | Demonstrates throttling without impeding tests | Per authenticated creator (FR-URL-018) |
| PVT-013 | Redirect rate limit, per short code | 600 requests/minute per code | Hotspot protection: no single link can be hammered | Public, unauthenticated traffic |
| PVT-014 | Redirect rate limit, per creator aggregated | 3,000 requests/minute across all a creator's links | Noisy-neighbor protection: many links each under PVT-013 cannot collectively soak capacity | Deliberately below the sum of per-code limits, which is what makes the tier bite |
| PVT-015 | Suspended-run idle retention before auto-abandonment | 90 days from last activity | A feature is commonly worked for about a quarter, so a run awaiting a human answer can legitimately sit that long without being dead | Configurable; demonstration runs use compressed values labelled per AS-007. On expiry the run becomes `ABANDONED` — terminal, and **never an approval** (FR-ORC-032, CL-005). Its records then **remain in the tables like every other record** (NFR-AUD-003). The PVT-010 boundary that DF-003 raised is **dissolved**: with nothing ever purged, an abandonment cannot coincide with its own history becoming unreadable |
| PVT-016 | Per-node **escalation thresholds** | See §Per-node escalation thresholds | Constitution VIII requires timeout behavior defined explicitly, and the durations were nowhere stated. These are the elapsed times at which the orchestrator **asks the human**, never automatic **kill-timers**: a breach escalates with elapsed-versus-expected and the last observed activity, offering **keep waiting** or **kill the node**. This unifies every node with the human-decision model already used at stages 4 and 11 — one rule: threshold reached, ask the human | Configuration; demonstration runs may compress values, labelled per AS-007. A **kill** decision fails the node by overrun, after which the existing failure classification and idempotency gating apply **unchanged** (FR-ORC-014 rules 1–4) — the threshold sets **duration, never retry eligibility**. **No answer** follows the gate-wait deadline (PVT-006) to suspension: silence is never approval (Constitution III) |

### Per-node escalation thresholds (PVT-016)

| Node | Threshold | Ground |
|---|---|---|
| S1 Ingestion | 5 s | Deterministic: validate a submission and write one row. A slow S1 is a store problem, which the envelope already classifies |
| S2 Normalization | 120 s | One AI call through the CLI subprocess: process startup plus model latency on a short prompt |
| S3 Ambiguity detection | 120 s | Same shape as S2 |
| S4 Human clarification | **N/A** | Already waiting on a human. Its threshold *is* the gate wait (PVT-006), which is the model every other node now follows |
| S5 Decomposition | 180 s | Longer output than S2/S3: a dependency-ordered task set |
| S6 Architecture & design | 300 s | Longest prompt and output: design plus, for a brownfield change, the seven-dimension impact analysis |
| S7 Implementation | 600 s **per node** | AI authors, the engine applies on a branch, the build runs. Per fan-out child, not per stage |
| S8 Testing | 1800 s | The real suite including Testcontainers startup. The only node whose elapsed time is dominated by our own tests, so the threshold must not be tight enough to make a slow machine look stalled |
| S9 Documentation | 180 s | AI call over the run's delivered change and results |
| S10 Security & policy | 600 s | Deterministic but dominated by the dependency-vulnerability scan, which fetches advisory data |
| S11 Release readiness | 30 s evaluation, then **PVT-006** | The evaluator reads persisted rows; the wait that follows is already a human decision |
| S12 Final summary | 60 s | Deterministic assembly from persisted evidence |

**What a breach does** — four steps, none of them automatic:

1. The orchestrator **escalates to the human**, presenting elapsed versus expected and the **last observed activity**
   for that node. Liveness is read from telemetry that already exists — the node's state-transition history, its
   trace spans and its audit events. **No heartbeat or watchdog subsystem is introduced** (Constitution VII's
   prohibition on unjustified complexity).
2. The human chooses **keep waiting** — the threshold is re-armed and the node continues — or **kill the node**.
3. A **kill** fails the node by overrun. From that point the existing machinery applies with nothing added: the
   failure crosses the boundary in the standard envelope, the two-vote rule decides retry eligibility, and a node
   whose effect is not declared idempotent is **not** retried (FR-ORC-014 rules 2 and 4, EC-033).
4. **No answer** is not a third option. The gate-wait deadline (PVT-006) elapses and the run suspends
   (FR-ORC-017). Silence advances nothing, here as everywhere.

**Counter-case, weighed and accepted by the owner, recorded because a decision without its counter-case is not a
decision**: an unattended run now **stalls and then suspends** where an automatic kill-and-retry might have
self-healed. The owner accepted this on the ground that **auto-retrying possibly-half-finished work is the exact
danger the retry rules exist to prevent** — a threshold breach means completion is *unknown*, which is the same
epistemic state as a timeout, and suspension is the declared safe outcome for it.

---

## Constraints

- **CN-001**: SpecKit is the sole lifecycle framework. Competing planning, task, memory, or
  execution methodologies MUST NOT be introduced or simulated. *(Constitution §Workflow)*
- **CN-002**: No technology selection at this stage — no language, framework, database, cloud,
  agent framework, or deployment platform. Selection is human-owned and lands in an approved ADR
  at Plan stage. *(Assignment; Constitution §Assessment Scope)*
- **CN-003**: Implementation may not begin before specification, plan, tasks, and analysis are
  approved. *(Constitution I, non-waivable)*
- **CN-004**: Red-green-refactor applies wherever technically practical; completion requires
  executed validation. *(Constitution IV)*
- **CN-005**: Evidence may not be fabricated, simulated-and-presented-as-real, or back-filled;
  proposed evidence must be labelled distinctly from executed evidence. *(Constitution X,
  non-waivable)*
- **CN-006**: HTTP is treated as intrinsic to the demonstration domain, not as a technology
  choice: "redirect resolution" has no meaning outside it. This specification therefore describes
  redirect *behavior* without fixing status codes, which remain a Plan-stage decision. Recorded
  explicitly so the judgment is reviewable rather than implicit.
- **CN-007**: Delivery timebox and scope controls are a Plan-stage deliverable, deferred by the
  human owner at Gate 1. This specification does not assume a timebox, and scope reduction
  against it is a human decision.
- **CN-008**: The demonstration domain must be genuinely working software, not a stub.
  *(Constitution §Assessment Scope)*
- **CN-009**: *Resolved at Gate 2.* Work stays on `main`. The human owner chose a single linear
  history as easiest for reviewers to follow and as matching the assessment guide's commit
  progression. No feature branches for lifecycle stages.
- **CN-010**: Executor behavior MUST NOT depend on recognizing specific demonstration inputs, and
  the system MUST NOT be restricted to the three demonstration requirements. *(Gate 2, AQ-003 —
  see FR-ORC-028 and §Stage Executor Model.)*
- **CN-011**: *(RETIRED by owner Decision J, 2026-09-20 — ADR-004 Amendment 02, CR-028.)* This constraint required a
  reviewer-default path runnable with no AI key, and made AI capability a per-run mode. **Orchestration now always uses
  AI**: there is no keyless mode and no run-level switch. A fresh orchestration run requires an authenticated Claude
  Code CLI. **What remains true and is not this constraint**: the URL shortener and the entire test suite run with no AI
  and no network, because reliability proofs use injected fakes (FR-ORC-030), and the committed scenario evidence is
  readable end to end with no AI setup. Retired in place rather than deleted: it was a Gate 2 condition and the
  retirement is part of the record. The counter-case the owner weighed — the assignment asks for a runnable prototype,
  and this constraint was the cleanest autonomy boundary in the design — is recorded verbatim in ADR-004-A2.
  *(Gate 2, AQ-003 — FR-ORC-029.)*
- **CN-012**: **The shortener's actor model and the orchestrator's actor model are separate domains, and no
  credential class crosses the boundary.** Creator credentials (FR-URL-018, FR-URL-019) authorize link creation and
  owner-scoped analytics retrieval, and nothing else. They MUST NOT authenticate or authorize any orchestrator
  governance surface — run creation (FR-ORC-007), run inspection (FR-ORC-008), or gate-decision recording
  (FR-ORC-013) — and such a surface MUST NOT accept, require, read, or be affected by one. Orchestrator governance
  surfaces sit under the **machine-access trust boundary**: the ability to run commands on the host *is* the
  boundary, exactly as it is for creator provisioning (FR-URL-019).
  **Accept**: no orchestrator surface references a creator credential; an architecture test asserts it mechanically.
  **Reject (negative)**: a creator credential MUST NOT grant any orchestrator capability; an orchestrator surface
  MUST NOT be reachable from the public redirect path; and the two identity models MUST NOT share a store, a filter,
  or a header.
  **Rationale recorded at CR-014, owner's ruling verbatim**: *"I don't think creators matter in orchestrator it is a
  url shortner concept, don't leak those into orchestrator."* The boundary is the same one AQ-001 drew when it put
  provisioning in an operator script rather than an HTTP endpoint — machine access is the trust boundary, and
  restating it here keeps one boundary rather than inventing a second.
  **Alternative considered and not taken**: a separate reviewer/engineer credential class, with its own provisioning
  and its own store. Rejected as more machinery than the assessment needs; it is the production answer and is
  recorded as future work, not as an oversight. The cost is accepted and disclosed: **anything that can reach the
  port can drive the orchestrator**, which is a localhost-demonstration posture and is stated as such in
  `docs/LIMITATIONS.md`. **The consequence this note previously left unstated**: because gate decisions are submitted
  over such a surface, the `actorType` field is a **declaration rather than a verified fact**, so the governance claim
  this project can defend is that *no workflow step approves anything* — not that impersonation is prevented
  (FR-ORC-021's declared limitation, NFR-AUT-001). The two statements were written in different places and never
  connected; the review connected them.
  *(Owner Decision 3 on the `/speckit-analyze` findings, 2026-09-20.)*

---

## Assumptions

Each assumption is a reasonable default chosen where the input was silent. Any of them may be
overridden by the human owner; requirements marked **[Assumption-dependent]** change if so.

- **AS-001**: A single deployment serves both link creation and redirect resolution. No
  multi-region or multi-tenant topology is assumed.
- **AS-002**: Short codes are opaque and system-generated. Custom or vanity aliases are not in
  scope (EX-002).
- **AS-003**: Link destinations are public web resources. The service does not fetch, validate
  liveness of, or render destinations.
- **AS-004**: *Superseded at Gate 2 by AQ-002.* Redirect analytics are per-event with
  timestamp only and no follower-identifying field — now a decided requirement (FR-URL-010), not an
  assumption.
- **AS-005**: The orchestration system governs this repository's own lifecycle. It is not assumed
  to govern arbitrary external repositories.
- **AS-006**: One human fills the reviewer, approver, and release-owner roles in demonstration
  runs. The model still distinguishes the roles, and the distinction is what is tested.
- **AS-007**: Demonstration runs may compress time-based parameters such as PVT-006, provided
  every compressed value is labelled as compressed in the evidence. **The same rule governs an injected condition**
  (CR-033): where evidence exists because a condition was deliberately induced rather than encountered — an injected
  transient fault (T136), an injected empty change plan (T141), an injected gate advance (T061a) — the evidence MUST
  say so in the same place it presents the result. **An induced demonstration presented as an encountered one is an
  evidence-integrity violation** under Principle X, and it is the failure mode that makes injection safe to use at all.
- **AS-008**: Persistence is durable and transactional enough to make FR-URL-006 and FR-ORC-004
  achievable. Which technology provides that is a Plan-stage decision (CN-002).
- **AS-009**: Measurements are taken on a single developer machine. All figures are
  demonstration measurements, never production measurements (Constitution IX).
- **AS-010**: Link deletion and link editing are not required (EX-003).

---

## Ambiguities

Three material ambiguities were raised as blocking. All three were resolved by the human owner at
Gate 2 on 2026-09-18; the decisions and their reasoning are in the Clarification Log below and,
verbatim, in `docs/governance/gate-decisions/gate-02-specify.md`.

- **AQ-001** — *Resolved.* API authentication and link ownership. → Clarification Log CL-001.
- **AQ-002** — *Resolved.* Analytics granularity and retention. → Clarification Log CL-002.
- **AQ-003** — *Resolved.* Stage executor model. → Clarification Log CL-003.

Ambiguities raised at Gate 2 as non-blocking, dispositioned at Gate 3:

- **AQ-004** — *Resolved at the Gate 4 closing package, 2026-09-20 (CR-003).* Redirects are **temporary, never
  permanent**; the exact status code remains an implementation detail within the temporary class (CN-006).
  → FR-URL-007, Deferred Findings DF-002.
- **AQ-005** — *Resolved at Gate 3.* → Clarification Log CL-008.
- **AQ-006** — *Resolved at Gate 3.* → Clarification Log CL-009.

---

## Deferred Findings

Recorded uncertainty. Each carries an owner and a decision point, per Constitution I's prohibition on silent
interpretation. **Status as of the Gate 4 closing package, 2026-09-20:**

| Finding | Status |
|---|---|
| DF-001 — analytics exactness | **RESOLVED** by ADR-014 (accepted with conditions at Gate 4) |
| DF-002 — redirect permanence | **RESOLVED** by the closing package; applied as CR-003 |
| DF-003 — audit vs idle retention boundary | **RESOLVED** by the closing package; applied as CR-004 |
| DF-004 — deferred production enhancements | **OPEN BY DESIGN** — recorded, not adopted; out of scope |
| DF-005 — outstanding Gate 1 / Gate 3 items | **CLOSED** by the closing package; applied as CR-005 |

Entries below retain their original text for provenance, annotated with their disposition.

### DF-001 — Analytics exactness contradiction *(contradiction in the approved spec)* — **RESOLVED 2026-09-20**

**Resolution**: the analytics append is **synchronous in a separate transaction**, its failure isolated, counted
and surfaced, and the redirect succeeds regardless (ADR-014, accepted with three conditions at Gate 4). **PVT-009
is retained and redefined**: it is an *observable append-failure ceiling*, not a loss budget — at most 0.5% of
appends may fail, every failure is counted and visible through the recording port's failure counter, and no
failure is silent or blocking (CR-010). The contradiction the finding names is therefore dissolved rather than
traded away: FR-URL-010's "appends an event" and SC-001's 100% correctness both hold, because SC-001 is scoped to
redirect correctness and analytics completeness is governed by PVT-009 as redefined. Original finding retained
below for provenance.

- **Finding**: PVT-009 proposes an analytics recording tolerance of "≤ 0.5% loss" under load, while
  FR-URL-010 states that following a link "appends an event" and SC-001 requires correctness across
  100% of the acceptance corpus. Exact-append and a 0.5% loss budget cannot both hold.
- **Impact**: determines whether analytics recording is synchronous and transactional with the
  redirect, or asynchronous and best-effort. Changes the concurrency design, the redirect latency
  budget (PVT-001), and what the acceptance tests may assert.
- **Current assumption**: none adopted. Unresolved.
- **Owner**: human owner.
- **Required decision point**: Plan gate, before any acceptance test is written against either reading.

### DF-002 — Redirect permanence *(AQ-004)* — **RESOLVED 2026-09-20**

**Resolution**: redirects are **temporary, never permanent** (FR-URL-007, CR-003). Owner's reasoning: a permanent
redirect is cached by browsers, after which clicks never reach the service — analytics cannot count them and
expiry cannot be enforced, because a cached redirect outlives the link's death. Temporary is the only class
compatible with FR-URL-008 and FR-URL-010. The exact status code remains an implementation detail within the
temporary class. Original finding retained below for provenance.

- **Finding**: the spec does not state whether a redirect is permanent or temporary by default.
- **Impact**: a permanent redirect invites intermediary and browser caching, which silently
  undercounts analytics (FR-URL-010) and can defeat expiry (FR-URL-008) because a cached redirect
  bypasses the service entirely. A temporary redirect preserves both at the cost of every follow
  reaching the service.
- **Current assumption**: none adopted. CN-006 already defers the mechanism to Plan; the *semantic*
  choice is what remains open.
- **Owner**: human owner.
- **Required decision point**: Plan gate. It interacts with DF-001 — caching changes what analytics
  exactness even means.

### DF-003 — Audit-versus-idle retention boundary — **RESOLVED 2026-09-20**

**Resolution**: the audit retention clock **starts at run termination**, not record creation (NFR-AUD-002,
PVT-010, CR-004). Owner's reasoning: a run's history must never age out while the run is alive or freshly
terminal; with idle retention and audit retention both at 90 days, a day-90 auto-abandonment must not coincide
with its own earliest records becoming purgeable. This rule was chosen over the alternative (audit retention
strictly greater than idle retention) because it holds for any pair of values, including the deliberate 90/90
alignment. Original finding retained below for provenance.

- **Finding**: PVT-010 proposes 90-day audit retention and PVT-015 proposes 90-day suspended-run idle
  retention. A run suspended on day 0 is auto-abandoned on day 90, at the same time its earliest audit
  records become purge-eligible.
- **Impact**: the abandonment event would be recorded against a run whose beginning is aging out,
  undercutting FR-ORC-023's reconstruction requirement and potentially tripping release-readiness
  condition 9 (unverifiable evidence).
- **Current assumption**: none adopted. The 90/90 alignment is deliberate and sound; only the boundary
  rule is missing.
- **Owner**: human owner.
- **Required decision point**: Plan gate. Two candidate rules: measure audit retention from run
  termination rather than record creation, or set audit retention strictly greater than idle retention.

### DF-004 — Deferred production enhancements *(out of timebox scope, recorded not adopted)*

| Item | Source | Rationale for deferral |
|---|---|---|
| Run-level retry circuit breaker (whole-run retry ceiling) | CL-006 | Per-stage bounds plus run-level blocks are the in-scope controls |
| Email/webhook expiry notification for suspended runs | CL-005 | The inspectable `auto-abandon-at` plus the deadline in the ask serve as the standing warning instead |

- **Owner**: human owner. **Required decision point**: post-assessment; neither is in scope now.

### DF-005 — Gate 1 and Gate 3 items still outstanding at the Plan gate — **CLOSED 2026-09-20**

All constituent items are approved or discharged at the Gate 4 closing package (CR-005):

| Item | Disposition |
|---|---|
| MTTR definition, formula, measurement rules | **APPROVED** — plan §7, including the declared human-wait exclusion and unrecovered failures counted separately |
| 2–3 day timebox and scope controls | **APPROVED** — plan §14: milestones, four checkpoints, four stop conditions, cuts hit backlog before evidence |
| Versioned API/schema deliverables with contract validation | **DISCHARGED** — plan §2 and ADR-005; parser validation executed 2026-09-20, one real defect found and fixed; meta-schema lint remains a Slice 1 task |
| Approval of every PVT value | **APPROVED** — all 15, see §Validation Targets |
| Constitution check against v1.1.0 with policy version recorded | **DISCHARGED** — plan §Constitution Check, against `policy-set-1.0.0`, **the set in force at that gate**. The set is now `policy-set-1.1.0` (CR-015: `POL-CHG-003` added, `POL-AUD-002` removed); this row is **a record of what was checked, not a statement of the current set** (annotated by CR-033) |

Original finding retained above for provenance.

---

## Clarification Log

Decisions resolving ambiguities, with the date and deciding human. Append-only.

### CL-001 — AQ-001, API authentication and link ownership | 2026-09-18 | Pravallika Veeravalli

**Decision**: a custom design, rejecting all three offered options because they conflated two
populations that must be treated differently — **link creators**, who are identified, own their
links, and alone may read their analytics; and **link followers**, the public, who must never need
authentication because a short link requiring login is useless.

Resolved as: creator provisioning by local operator script, never an HTTP endpoint (the ability to
run commands on the machine **is** the trust boundary); keys stored only as hashes, never committed,
and never written by the application to logs, traces, metrics, or error responses; real ownership
recorded per link with owner-only analytics; public unauthenticated redirects; and two-sided rate
limiting, two-tier on the redirect side (per code for hotspot protection, per creator aggregated for
noisy-neighbor protection). Options rejected en route, in order: keys in a committed config file,
keys generated at startup and printed to console, and an HTTP key-issuance endpoint.

**Accepted trade-off**: followers of a popular creator's links may be throttled through no fault of
their own — a deliberate choice of service protection over unlimited per-tenant availability, to be
documented in the threat model.

**Applied to**: FR-URL-011, FR-URL-016, FR-URL-017, FR-URL-018 *(new)*, FR-URL-019 *(new)*, KE-01,
KE-23, KE-24 *(new)*, NFR-SEC-003, NFR-SEC-005 *(new)*, PVT-012, PVT-013 *(new)*, PVT-014 *(new)*,
SC-017 *(new)*, EX-005.

### CL-002 — AQ-002, Analytics granularity and retention | 2026-09-18 | Pravallika Veeravalli

**Decision**: Option B — per-event records carrying timestamp only, no follower-identifying field.

Counters alone cannot answer "when", cannot show a spike, and cannot provide stored evidence for the
CL-001 rate-limiting design. Storing IP addresses would pull privacy obligations, retention
justification, and anonymization design into a short assessment for no graded benefit, and holding
personal data in a demonstration is exactly what a reviewer probes. *"I deliberately stored no
personal data"* is the stronger position: data minimization as practiced discipline, not accident.
Retention stays at the proposed 30-day cap as housekeeping, subject to Plan-gate approval; note that
CL-001's ownership model already governs who may **read** analytics, whereas this decision governs
what is **stored**.

**Applied to**: FR-URL-010, FR-URL-011, KE-02, NFR-SEC-005, PVT-010, SC-017 *(new)*.

### CL-003 — AQ-003, Stage executor model | 2026-09-18 | Pravallika Veeravalli

**Decision**: a custom model ("C+") — pluggable executors, AI-capable where the work is creative,
deterministic engines where repeatability is the point, nothing hardcoded to demonstration inputs.

The reasoning, recorded in three steps. First, the orchestrator is not itself the AI; it **governs**
agents, which is the assignment's own principle — but a system with zero real AI inside would make
the title hollow, so pure determinism was rejected. Second, input-specific hardcoding is rejected
outright: executors recognizing blessed demo inputs would be a rigged demonstration and would fail
the reviewer's own authenticity question, so deterministic executors must be generic engines over
workflow data, and the system must not be capped at three requirements. Third, a deterministic
engine can apply, build, and verify but cannot **author** a change, so the implementation stage is
AI-capable at runtime rather than having creative work happen off-stage: the AI authors from the
design output, the engine applies on a branch, the real suite verifies, failure routes back under a
bound, then the human gate. An AI patch failing tests and being caught is the governance visibly
working.

**Applied to**: §Stage Executor Model *(new)*, FR-ORC-028..031 *(new)*, FR-ORC-001, NFR-AUT-003
*(new)*, NFR-AUT-004 *(new)*, CN-010 *(new)*, CN-011 *(new)*, KE-25 *(new)*, SC-014..016 *(new)*,
FR-ORC-013 and FR-ORC-023 (executor mode in gate and audit evidence).

### Change-control amendments to this specification

Approved changes to this specification after Gate 2 / Gate 3, applied through the change-control workflow
rather than as clarifications. Append-only.

- **CR-001** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Human executor kind and the
  no-change-plan gate.* Six edits: FR-ORC-029 (run-level flag renamed `ai: on | off` default `off`;
  executor-kind vocabulary extended to `DETERMINISTIC` / `AI` / `HUMAN`), FR-ORC-031 (no-plan gate with
  three options; labelled no-op prohibited), KE-25 (`HUMAN` as an executor kind distinct from
  `HUMAN_GATE`), §Stage Executor Model stage 7 row and binding conditions, and new EC-040. Record:
  `docs/governance/change-control/CR-001-human-executor-and-no-plan-gate.md`.
- **CR-002** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Credential expiry required at
  provisioning.* FR-URL-019 accept and reject criteria plus rationale; KE-24 (`expires_at`, `revoked_at`, hash
  over the full presented string); new EC-041. Record:
  `docs/governance/change-control/CR-002-credential-expiry-required-at-provisioning.md`.
- **CR-003** | approved and applied 2026-09-20 | Pravallika Veeravalli | *DF-002 resolved — redirects are
  temporary, never permanent.* FR-URL-007 (temporary class required, permanent prohibited); FR-URL-008 (expiry
  enforceability depends on it); DF-002 and AQ-004 marked resolved; new EC-042. Record:
  `docs/governance/change-control/CR-003-redirect-permanence-resolved.md`.
- **CR-004** | approved and applied 2026-09-20 | Pravallika Veeravalli | *DF-003 resolved — audit retention
  clock starts at run termination.* PVT-010 conditions; NFR-AUD-002; DF-003 marked resolved; new EC-043. Record:
  `docs/governance/change-control/CR-004-audit-retention-clock-resolved.md`.
- **CR-005** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Gate 4 closing approvals reflected in
  the specification.* §Proposed Validation Targets retitled §Validation Targets with all 15 approved and binding;
  DF-005 closed; `executorModeUsed` renamed `executorKindUsed` in the contracts. Record:
  `docs/governance/change-control/CR-005-gate-4-closing-approvals.md`.
- **CR-010** | approved and applied 2026-09-20 | Pravallika Veeravalli | *PVT-009 as an observable append-failure
  ceiling; per-node escalation thresholds; stale resolution statuses corrected.* PVT-009 restated (same 0.5%, a
  failure ceiling rather than a loss budget); DF-001 given its resolution paragraph; new **PVT-016** escalation
  thresholds with FR-ORC-014 **rule 6** — a breached threshold **asks the human**, never kills on the orchestrator's
  authority, and silence suspends; AQ-004 and the status header corrected; FR-ORC-032's out-of-sequence placement
  explained. Record: `docs/governance/change-control/CR-010-pvt-009-restatement-and-stale-statuses.md`.
- **CR-014** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Credential-domain separation.* New
  **CN-012**: the shortener's and the orchestrator's actor models are separate domains and no credential class
  crosses the boundary; FR-ORC-007 gains an addressable submission surface; FR-ORC-008 and FR-ORC-013 exclude creator
  credentials; FR-ORC-013's decision surface **cannot express `TIMED-OUT`**; KE-23 scoped. Owner's ruling: *"I don't
  think creators matter in orchestrator it is a url shortner concept, don't leak those into orchestrator."* Record:
  `docs/governance/change-control/CR-014-credential-domain-separation.md`.
- **CR-017** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Retention posture — indefinite retention,
  production archival recorded as a recommendation.* New NFR-AUD-003 with an architecture test asserting no deletion
  path exists; **PVT-010 withdrawn as a binding target** by the owner's explicit decision and replaced by a
  production recommendation; NFR-AUD-002 amended with CR-004's clock rule preserved inside that recommendation;
  FR-URL-010's retention cap restated as a recommendation; PVT-015 conditions; EC-043 retired as unreachable; new
  §Retention Posture. **Revises the *status* of the 30-day click-record cap proposed by CL-002 at Gate 2** — from a
  bound this system implements to a recommendation for production. **CL-002's substance is untouched**:
  timestamp-only records, no follower-identifying field, data minimization as the deciding ground; and its own
  finding that no personal data is held is what makes indefinite retention safe. Owner's grounds: *"let us not worry
  about archival at all, let the records stay in the table for ever, but record this that, Ideally we would have a
  prod archival job if we were doing this in a real prod env."* Record:
  `docs/governance/change-control/CR-017-retention-as-archival.md`.

- **CR-021** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Actor-authority claim qualified; no mechanism
  built.* FR-ORC-021's reject clause restated as **no workflow step submits, satisfies or advances a human-owned
  decision**, with a **declared limitation** that actor identity is asserted and not verified — governance surfaces carry
  no credential by design (CN-012), so enforcement rests on the machine-access boundary. NFR-AUT-001 narrowed to match
  and given a checkable assertion it lacked; the CN-012 residual note connected to its consequence. **The constitution is
  not touched**: Principle III's policy stands, and every gate in this project is in fact decided by the human owner.
  Owner's grounds: *"I don't think we should solve this problem at all… it is ok if agents act as humans, I don't think
  that is easily solvable at this point and especially in this assessment."* An operator-issued per-decision token was
  put to her and **declined, recorded as declined rather than unconsidered**. Record:
  `docs/governance/change-control/CR-021-actor-authority-claim-qualified.md`.

- **CR-028** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Keyless mode removed (Decision J,
  ADR-004-A2).* **CN-011, SC-014 and NFR-AUT-004 retired in place.** FR-ORC-029's run-level flag removed, executor-kind
  recording kept and strengthened; FR-ORC-031's deterministic fallback replaced by the no-plan gate; §Stage Executor
  Model's binding conditions restated. **CL-003's own text is unaltered** — it records what was decided at Gate 3, and
  retiring a constraint does not rewrite the clarification that created it. Owner's grounds: *"we don't need the non-AI
  mode anymore because… reviewers are not going to look at… they're just going to look at the old run information from
  the ADRs and other files that we are pushing into GitHub"*; the counter-case — the assignment asks for a runnable
  prototype — was put to her and she answered *"do it."* Record:
  `docs/governance/change-control/CR-028-keyless-mode-removed-specification.md`.

- **CR-032** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Fallback retired (Decision K).* **FR-ORC-015
  and EC-023 retired in place**; T087 retired in place with a negative architecture assertion so the behaviour cannot be
  reintroduced unnoticed; `fallback` removed from the recovery-mechanism enum; the plan's §6 Fallback row kept as a
  visible absence. **Bounded retry then safe suspension is the entire degradation story.** The retirement is a
  consequence of Decision J found on verification rather than at decision time: the six deterministic counterparts
  Decision J struck were every declared fallback in the system. The assignment names fallback among its reliability
  controls, so the absence is disclosed in `docs/LIMITATIONS.md` (T147) rather than left for a reviewer to notice.
  Owner's answer, verbatim: **"A"** — a **documented honest absence over a ceremonial presence**. Options declined: one
  token counterpart, and re-pointing fallback at a genuine degradation (new scope). Record:
  `docs/governance/change-control/CR-032-fallback-retired-decision-k.md`.

- **CR-025** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Ambiguity detection stated to be semantic and
  AI-backed.* §Stage Executor Model gains what stage 3's detection **is and is not**: semantic reasoning with declared
  non-determinism, acceptable because its output feeds a human gate and would not be at stage 10; a miss is
  indistinguishable from an absence of ambiguity unless `no_clarification_reason` is substantive, which is the only
  control against a quiet miss. The structural alternative is recorded as **narrower, not safer**. The record's original
  A/B/C fork dissolved under Decision J — with no keyless path there is no default path on which the scenario fails to
  fire. Record: `docs/governance/change-control/CR-025-deterministic-ambiguity-detection-scope.md`.

- **CR-027** | approved and applied 2026-09-20 | Pravallika Veeravalli | *Silence-test falsifiability and a published
  coverage denominator.* PVT-008's conditions cell now names the **denominator declared before measurement** — included
  and excluded packages with a ground for each and the task covering each exclusion instead — because a threshold whose
  denominator is selectable is not a threshold. New task T061a proves T061 **fails** when a gate advances on silence,
  closing an inconsistency the pre-implementation review found: four other harnesses carry a falsifiability fixture and
  the one guarding *silence is never approval* did not. Record:
  `docs/governance/change-control/CR-027-silence-test-falsifiability-coverage-exclusions-cosmetics.md`.

- **CR-023** | **WITHDRAWN 2026-09-20 by owner ruling; never applied.** *A distinct short escalation deadline for node
  overrun (PVT-017).* Proposed a 30-minute operational deadline for the node-overrun gate, distinct from PVT-006's 24-hour
  deliberative wait. The owner ruled against it: *"I don't think we should have any timeout as I said. Just wait till the
  user responds. Yeah, that's all. Keep it simple."* The overrun ask is an **ordinary ask under the uniform rule** —
  PVT-006 like every other gate, expiry to safe suspension, resume re-asks. **No edit was required anywhere**: the
  approved text already said so, and §Validation Targets still carries **sixteen** rows. **PVT-017 was never minted** —
  the identifier is unused and free for a future target; it appears in this project only in this entry and in the
  withdrawn record, never as a target. Reviewer finding A2 is **declined by owner, cost accepted** — a stalled node may wait
  the uniform deadline before the run parks itself. Record:
  `docs/governance/change-control/CR-023-overrun-escalation-deadline.md`.

- **CR-033** | approved and applied 2026-09-21 | Pravallika Veeravalli | *EC-040's trigger restated for an always-AI
  system; the no-plan gate demonstrated by injection.* **EC-040's obligation is verbatim unchanged** — a no-op may not
  advance, the gate offers its three options, and after CR-028 it is the **sole** guard at stage 7. Only its stated
  *trigger* is corrected: it named *"a reviewer submitting their own requirement with the AI disabled"*, a route
  Decision J removed. The condition now arises when stage 6 produced no usable design output, or when authoring yields
  nothing applicable — neither reviewer-reachable on demand, so **T141 reaches the gate by injecting the
  empty-change-plan condition** and the evidence must say so where it presents the suspension. **AS-007's labelling rule
  is extended** to cover injected conditions generally (T136's transient fault, T141's empty plan, T061a's gate
  advance), with the ground stated: an induced demonstration presented as an encountered one is an evidence-integrity
  violation under Principle X. `quickstart.md` states the trade rather than implying a route that no longer exists.
  T065's unit tests remain the structural proof. **Two pre-existing LOW findings closed with it**: the task-plan
  header's policy set corrected to `policy-set-1.1.0`, and the gate-discharge row above annotated as a record of what
  was checked at that gate. Owner's decision: **option (a)** of three presented with costs — *"Let's go with ur
  recommendation."* Record:
  `docs/governance/change-control/CR-033-no-plan-gate-trigger-restated-and-injected.md`.

### CL-004 — Branch strategy | 2026-09-18 | Pravallika Veeravalli

**Decision**: stay on `main`. A single linear history is easiest for reviewers to follow and matches
the assessment guide's commit progression. **Applied to**: CN-009.

---

### Session 2026-09-19 — `/speckit-clarify`, Gate 3

Five questions asked and answered. Three addressed defects found in the approved specification — a
contradiction, a non-testable requirement, and an unenumerated recovery register. Two resolved AQ-005
and AQ-006. Full reasoning verbatim in `docs/governance/gate-decisions/gate-03-clarify.md`.

- Q: When a run enters safe-stop, is the run finished or suspended awaiting resumption? → A: Suspended
  and non-terminal; terminal states are exactly `COMPLETED`, `REJECTED`, `ABANDONED`; 90-day idle
  retention from last activity.
- Q: How does the orchestrator decide transient vs permanent, and what happens to an unrecognized
  failure? → A: Executor proposes, orchestrator validates, either may veto; `retries = declared set ∩
  executor proposal`; unrecognized is permanent.
- Q: How does the orchestrator know whether an effect can be rolled back or only compensated? → A:
  Structural default plus declared overrides, with registration-time enforcement; unclassified is
  compensate-only.
- Q: If the same destination is submitted twice without an idempotency marker, return the existing
  link or mint a new code? → A: Always mint; deduplication is marker-only.
- Q: Must a run survive a persistence-layer restart or only an orchestrator-process restart? → A:
  Both; a disk-backed store is required.

### CL-005 — Safe-stop semantics and run lifecycle | 2026-09-19 | Pravallika Veeravalli

**Defect resolved**: FR-ORC-017 called `SAFE_STOP` both "resumable" and "terminal", contradicting
NFR-REL-001.

**Decision**: `SAFE_STOP` is a distinct **non-terminal suspended** state. Terminal states are exactly
`COMPLETED`, `REJECTED`, `ABANDONED`. A suspended run moves only by a human decision — resume or
abandon — or by the pre-approved idle retention policy. NFR-REL-001 reworded accordingly.

Retention policy as designed by the owner: the idle clock runs from **last activity**, never from
creation, because a run that received attention must not be reaped on age; after a generously high
configurable idle period the run is automatically abandoned; every suspended run exposes its computed
`auto-abandon-at` via inspection, serving as a standing warning in place of notification
infrastructure; auto-abandonment writes an audit event citing the retention policy version, actor type
`system`, and the pre-approved policy as authority. **Policy-driven abandonment is never an approval —
silence still advances nothing.**

**Retention period corrected to 90 days** (from an initial 30) on the owner's reasoning that a feature
is commonly worked for about a quarter, and that 90 aligns with PVT-010's audit-retention proposal.

**Addendum — deadline in the ask**: a gate decision request and a clarification request must each
state their expiry consequences up front: the gate-wait deadline at which the run suspends, and the
`auto-abandon-at` date computed from last activity plus retention. Additive to the inspectable field —
the person being asked should not have to discover the deadline by inspecting the run.

**Applied to**: FR-ORC-017, FR-ORC-032 *(new)*, FR-ORC-011, FR-ORC-013, NFR-REL-001, US-2 scenarios 1
and 2, PVT-015 *(new)*, EC-036, EC-037 *(new)*, KE-04. Deferred production enhancement:
email/webhook expiry notification.

### CL-006 — Failure classification and retry eligibility | 2026-09-19 | Pravallika Veeravalli

**Defect resolved**: FR-ORC-014 required classification without naming the decider, the method, or the
unknown-failure disposition, leaving it untestable.

**Decision**: executor proposes, orchestrator validates, either side may veto. A standard error
envelope over a closed category vocabulary keeps provider knowledge in the plugin. `retries = declared
retryable set ∩ executor proposal` — the executor holds **veto power, never grant power**. Unrecognized
categories, undeclared codes, and malformed envelopes are **permanent** and take the suspension path,
matching the EC-025 default-deny precedent. A `TIMEOUT` is retryable only where the stage's
**design-time** contract declares its effect idempotent, never on executor self-certification, because
a timeout leaves completion unknown and replaying a maybe-completed effect violates exactly-once.
Information flows down — attempt number and remaining budget — but authority does not.

**Why the orchestrator is final authority**: not because it diagnoses better (it never overrides the
diagnosis) but because it alone holds the decision context — attempts consumed, compensation already
issued, replanning invalidation, safe-stop or pending-gate or policy-`FAIL` blocks. Executor =
diagnosis; orchestrator = policy and memory. A refusal path can only exist outside the governed party,
which matters doubly with six AI-backed executors.

**Alternatives rejected**: no-classification retry-everything violates Principle VIII outright and
would fail the plan's constitution check; a static list with always-retried timeouts violates
exactly-once; a repaired static list is legal but couples the core to every plugin's error taxonomy,
burns bounded attempts on failures known to be deterministic, and forfeits the executor's veto;
executor self-classification with unknown-defaults-to-retry is fail-open, puts the autonomy limit
inside the governed component so no refusal path can exist, and reduces the audit answer to "the agent
said so" — a gate satisfiable by the governed party's testimony is not a gate.

**Doubt resolved**: the orchestrator's check does not duplicate the executor's work — the layers hold
disjoint information, the check is a set-membership lookup rather than a second diagnosis, and every
decision yields a two-signature audit record, maker-checker style.

**Applied to**: FR-ORC-014, FR-ORC-004, KE-26, KE-27, KE-29 *(new)*, EC-031, EC-032, EC-033 *(new)*,
NFR-REL-002. Deferred production enhancement: run-level retry circuit breaker.

### CL-007 — Rollback versus compensation | 2026-09-19 | Pravallika Veeravalli

**Defect resolved**: FR-ORC-016 required the distinction without enumerating which effects are
reversible, leaving the orchestrator no basis to choose.

**Decision**: structural default plus declared overrides. The orchestrator holds the common effect
kinds centrally — a small table written once — and each stage adds lines only where its correction
requires product knowledge, such as an unwanted short link being corrected by setting it to expired
rather than deleted. Registration-time enforcement: a stage declaring an irreversible effect **must
name its compensating action or it does not load**, and a declaration contradicting the structural rule
is **flagged for human review rather than silently trusted**. Unknown or unclassified effects are never
erased: compensate-only, and where no fix is known, touch nothing and suspend the run with the reason
recorded. Executors acting outside the provided effect channels are refused, recorded as autonomy
violations, and never reach cleanup.

**Effect inventory confirmed** as the §Compensation Register: local un-pushed work is the only erasable
class; gate decisions and audit records are corrected by superseding or appended entries; short links
are corrected by expiry and never deleted (rationale: dangling history, short-code reuse hijack,
repeat-safe cleanup); AI invocations have nothing to recover.

**Applied to**: FR-ORC-016, §Compensation Register *(new)*, KE-28 *(new)*, EC-034, EC-035 *(new)*,
FR-ORC-021.

### CL-008 — Duplicate destination behavior | 2026-09-19 | Pravallika Veeravalli

**Resolves AQ-005.**

**Decision**: marker-only deduplication, with three exact behaviors — no marker always mints a new
code with no destination-based deduplication at any scope; same marker with identical request replays
the original link as labelled success with zero new side effects, mechanizing EC-003; same marker with
different content returns an explicit conflict, minting nothing and changing nothing.

**Reasoning**: an idempotency key identifies one exact logical request, not a destination. Updating the
existing link on replay is forbidden twice over — replay must not change state, and link editing is
excluded scope for the same reason deletion is, since the link is already circulating with a promised
lifetime. Silently minting past a detected caller bug would be guessing past a surfaced mistake.
Per-creator deduplication was rejected because a second request with a different expiry forces a silent
lie: ignore what the caller asked, or mutate a circulating link. Global deduplication was rejected
because it breaks the CL-001 ownership model and leaks that another creator shortened the same URL.
Keyspace cost of always minting is accepted as negligible against PVT-005.

**Applied to**: FR-URL-012, EC-002, EC-003, EC-039 *(new)*, KE-03, AQ-005.

### CL-009 — State recovery scope | 2026-09-19 | Pravallika Veeravalli

**Resolves AQ-006.**

**Decision**: runs survive both an orchestrator-process restart and a persistence-layer restart; a
disk-backed store is therefore required.

**Reasoning**: this is the only option both honest and lean. Process-only durability is not merely
cheap, it is the loophole — an in-memory store technically satisfies the prior wording while making the
recovery guarantee nominal, protecting nothing. Full-host-restart scope adds test-harness cost for no
design difference once the store is disk-backed. Differentiated guarantees would mean two recovery
stories to build, test, and defend. A store outage flows through machinery already decided:
`UNAVAILABLE` in the CL-006 envelope, proposed transient, intersected with the stage's declared
retryable set, bounded retries, suspension on exhaustion with the deadline shown in the ask, correct
resumption when the store returns.

**Applied to**: FR-ORC-004, NFR-REC-001, EC-038 *(new)*, EC-011, EC-016, AQ-006. Constrains the
technology-selection ADR.

---

## Exclusions

- **EX-001**: Multi-region deployment, geo-routing, and CDN behavior.
- **EX-002**: Custom or vanity short codes (AS-002).
- **EX-003**: Link deletion, editing, or bulk management (AS-010).
- **EX-004**: End-user-facing web UI. Interfaces are programmatic; human governance surfaces are
  for reviewers, not consumers.
- **EX-005**: Self-service signup, account management, and billing. Creator identities exist as
  **provisioned operators** (FR-URL-019), not as a user-account feature; ownership and per-creator
  limits are in scope, the account lifecycle around them is not. *(Reworded at Gate 2 under
  AQ-001 — the exclusion is narrowed, not deleted.)*
- **EX-006**: Destination content inspection, malware scanning, and reputation services. FR-URL-005
  addresses network-level abuse only.
- **EX-007**: Production deployment, production monitoring, and real-traffic operation. All
  figures are demonstration figures (AS-009).
- **EX-008**: Governing repositories other than this one (AS-005).
- **EX-009**: Click-fraud detection and bot filtering in analytics.
- **EX-010**: Internationalized domain name normalization beyond what FR-URL-003 requires.

---

## Traceability

Bidirectional traceability is a requirement (FR-ORC-027), so the matrix is seeded here and
completed by downstream stages. Left-to-right answers "is this requirement delivered and proven";
right-to-left answers "why does this artifact exist".

**Design** and **ADR** were added by **CR-006** (approved 2026-09-20), closing a contradiction in which the
approved plan claimed to add them while the matrix carried only eight of the ten chain links. Both columns are
**derived** from the ADRs' own Traceability sections and the plan's designing sections, so any cell is verifiable
by opening the artifact it names. **Task**, **Test**, and **Evidence** remain reserved for the Tasks and Implement
stages. A requirement with no Design or ADR reference is an orphan in the same sense as one with no test
(`POL-TRC-001`).

| Requirement | Journey | Scenario | Edge cases | Design | ADR | Task | Test | Evidence |
|---|---|---|---|---|---|---|---|---|
| FR-URL-001 | US-1 | DS-A | — | Plan §2 | ADR-007 | T039, T042 | UrlShortenerAcceptanceIT (SC-001), CreateLinkConformanceIT | `docs/evidence/`, CI GREEN |
| FR-URL-002 | US-1 | DS-A | EC-006, EC-007 | Plan §2 | ADR-007 | T033, T042 | UrlSyntaxValidatorTest, UrlShortenerAcceptanceIT (SC-002) | `docs/evidence/`, CI GREEN |
| FR-URL-003 | US-1 | DS-A | EC-008 | Plan §2, §8 | ADR-007 | T034 | DestinationNormalizerTest, CreateLinkConformanceIT (ordering) | `docs/evidence/`, CI GREEN |
| FR-URL-004 | US-1 | DS-A | — | Plan §2, §8 | ADR-013 | T035 | SchemeAllowListTest, UrlShortenerAcceptanceIT (row 1) | `docs/evidence/`, CI GREEN |
| FR-URL-005 | US-1 | DS-B | EC-004, EC-005 | Plan §2, §8 | ADR-013 | T036 | AbuseGuardTest, CreateLinkConformanceIT (EC-004/EC-005 wired) | `docs/evidence/`, CI GREEN |
| FR-URL-006 | US-1 | DS-A | EC-001, EC-008 | Plan §2 | ADR-002, ADR-007 | T038, T039, T040 | ShortCodeGeneratorTest, CreateLinkUseCaseTest, ConcurrentCreationIT | `docs/evidence/`, CI GREEN |
| FR-URL-007 | US-1 | DS-A | — | Plan §2 | ADR-014 | T043, T044 | ResolveLinkUseCaseTest, RedirectIT | `docs/evidence/`, CI GREEN |
| FR-URL-008 | US-1 | DS-A | EC-009 | Plan §2 | ADR-014 | T046, T047 | ExpiryPolicyTest, ExpiredLinkIT, UrlShortenerAcceptanceIT (SC-003) | `docs/evidence/`, CI GREEN |
| FR-URL-009 | US-1 | DS-A | EC-014 | Plan §2 | ADR-002 | T046 | ExpiryPolicyTest | `docs/evidence/`, CI GREEN |
| FR-URL-010 | US-1 | DS-B | EC-012, EC-013 | Plan §2, §7 | ADR-014, ADR-010 | T048, T050 | AnalyticsRecorderTest, AnalyticsRecordingIT, AnalyticsDurabilityIT | `docs/evidence/`, CI GREEN |
| FR-URL-011 | US-1 | DS-B | — | Plan §2 | ADR-013, ADR-014 | T051 | GetAnalyticsUseCaseTest, AuthenticationIT, UrlShortenerAcceptanceIT (row 6) | `docs/evidence/`, CI GREEN |
| FR-URL-012 | US-1 | DS-A | EC-002, EC-003, EC-039 | Plan §2 | ADR-007 | T041, T042 | IdempotencyResolverTest, UrlShortenerAcceptanceIT (rows 7-9) | `docs/evidence/`, CI GREEN |
| FR-URL-013 | US-1 | DS-B | EC-001, EC-013 | Plan §2 | ADR-002, ADR-007, ADR-014 | T040 | ConcurrentCreationIT at PVT-003 | `docs/evidence/`, CI GREEN |
| FR-URL-014 | US-1 | DS-B | EC-010, EC-011 | Plan §2 | ADR-002, ADR-014 | T045 | RedirectIT (EC-011), CreationFailureIsolationIT (EC-010) | `docs/evidence/`, CI GREEN |
| FR-URL-015 | US-1 | DS-B | EC-011 | Plan §2 | ADR-012 | T056 | HealthPayloadTest, ReadinessDegradationIT | `docs/evidence/`, CI GREEN |
| FR-URL-016 | US-1 | DS-B | EC-013 | Plan §2, §8 | ADR-013 | T054, T055, **T136a pending** | **PARTIAL** — RateLimiterTest, RateLimitIT (PVT-012, PVT-013). PVT-014 **not built**: deferred to T136a, disclosed in `baseline-omissions.md` | `docs/evidence/`, CI GREEN |
| FR-URL-017 | US-1, US-5 | DS-A | EC-007 | Plan §7, §8 | ADR-010, ADR-013 | T037 | CredentialRedactionTest, CredentialTelemetryIT, scan.sh --telemetry | `docs/evidence/`, CI GREEN |
| FR-URL-018 | US-1 | DS-A | — | Plan §2, §8 | ADR-013 | T044, T052 | AuthenticationIT, RedirectIT | `docs/evidence/`, CI GREEN |
| FR-URL-019 | US-1, US-5 | DS-A | — | Plan §8, §Project Structure | ADR-013, ADR-012 | T052, T053 | ProvisionCreatorTest, ProvisionCreatorIT, AuthenticationIT (EC-041) | `docs/evidence/`, CI GREEN |
| FR-ORC-001 | US-3 | DS-A | EC-029 | Plan §3 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-002 | US-3 | DS-A | EC-030 | Plan §3 | ADR-003, ADR-008 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-003 | US-3 | DS-A | EC-017, EC-018 | Plan §3 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-004 | US-3 | DS-A | EC-015, EC-016, EC-038 | Plan §3 | ADR-002, ADR-008 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-005 | US-5 | DS-A | — | Plan §3 | ADR-008, ADR-010 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-006 | US-3 | DS-A | EC-029 | Plan §3 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-007 | US-3 | DS-A | — | Plan §3 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-008 | US-3 | DS-A | — | Plan §3 | ADR-008 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-009 | US-3 | DS-A | — | Plan §3 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-010 | US-3 | DS-A, DS-C | EC-021 | Plan §3 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-011 | US-2 | DS-C | EC-021 | Plan §3 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-012 | US-3 | DS-A | — | Plan §3 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-013 | US-2 | DS-A, DS-C | EC-024 | Plan §3, §5 | ADR-005 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-014 | US-3 | DS-B | EC-022, EC-031, EC-032, EC-033 | Plan §3, §6 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-015 *(RETIRED — CR-032)* | US-3 | DS-B | EC-023 *(retired)* | Plan §6 (retired row) | ADR-003, **ADR-004-A2** | T147 (disclosure) | — | `docs/LIMITATIONS.md` |
| FR-ORC-016 | US-3 | DS-B | EC-022, EC-034, EC-035 | Plan §3, §6 | ADR-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-017 | US-2 | DS-C | EC-023 (retired, CR-032), EC-025 | Plan §3, §6 | ADR-003, ADR-008 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-018 | US-3 | DS-C | EC-015, EC-026 | Plan §3, §6 | ADR-002, ADR-008 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-019 | US-3 | DS-C | EC-019, EC-020, EC-030 | Plan §3, §9 | ADR-009 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-020 | US-3 | DS-B | — | Plan §3 | ADR-006 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-021 | US-2 | DS-A | EC-024 | Plan §3, §5 | ADR-004 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-022 | US-4 | DS-B | EC-025, EC-027 | Plan §9 | ADR-005 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-023 | US-5 | DS-A, DS-B, DS-C | EC-028 | Plan §7 | ADR-010, ADR-005 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-024 | US-5 | DS-B | — | Plan §7 | ADR-010 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-025 | US-4 | DS-A | EC-027, EC-028 | Plan §9 | ADR-011 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-026 | US-5 | DS-A | — | Plan §7 | ADR-010 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-027 | US-5 | DS-A, DS-B, DS-C | — | Plan §13 | ADR-006 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-028 | US-3, US-5 | DS-A, DS-B, DS-C | — | Spec §Stage Executor Model | ADR-004, ADR-006 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-029 | US-5 | DS-A, DS-B, DS-C | — | Spec §Stage Executor Model | ADR-004 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-030 | US-3 | DS-B, DS-C | EC-015, EC-018, EC-022, EC-023 (retired, CR-032) | Plan §10 | ADR-011, ADR-004 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-031 | US-3 | DS-B | EC-023 (retired, CR-032), EC-040 | Spec §Stage Executor Model | ADR-004 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-032 | US-2, US-3 | DS-C | EC-036, EC-037 | Plan §3, §5 | ADR-008 | *Tasks stage* | *Tasks stage* | *Implement stage* |
