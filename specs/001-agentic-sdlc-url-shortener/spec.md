# Feature Specification: Agentic Software Engineering System: URL Shortener

**Feature Branch**: `main` (no branch created — the `before_specify` git-extension hook is not installed in this project; see CN-009)

**Feature Directory**: `specs/001-agentic-sdlc-url-shortener`

**Created**: 2026-09-17

**Status**: **Approved at Gate 2, 2026-09-18** by Pravallika Veeravalli. AQ-001, AQ-002, AQ-003
resolved — see Clarification Log. AQ-004..006 remain open for `/speckit-clarify`.

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
| **AQ-nnn** | Ambiguity. AQ-001..003 were blocking and are resolved at Gate 2 (see Clarification Log). AQ-004..006 remain open, deferred to `/speckit-clarify`. |

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
   evidence, the policy check outcomes, and the consequences of each available decision.
2. **Given** a run halted at a mandatory gate, **When** no decision is recorded within the
   configured wait, **Then** the run enters safe-stop with state preserved and resumable, and no
   downstream stage has executed.
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
- **EC-023**: A stage's declared fallback itself fails.
- **EC-024**: A gate receives a decision from an actor with no recorded authority for it.
- **EC-025**: A policy check cannot be evaluated at all — neither pass, fail, nor
  not-applicable. Must not default to pass.
- **EC-026**: A run is resumed twice concurrently from the same persisted state.
- **EC-027**: An approved policy exception passes its expiry while the run is still in flight.
- **EC-028**: Evidence for a completed stage is missing or unreadable at readiness evaluation.
- **EC-029**: A stage reports success but produced no output artifact.
- **EC-030**: Cyclic dependencies are introduced into the stage graph by replanning.

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

- **FR-URL-007** — *Redirect resolution.* **[Confirmed]** The service MUST resolve a known,
  unexpired code to its exact stored destination and redirect the client there.
  **Accept**: the redirect target equals the stored destination byte-for-byte.
  **Reject (negative)**: the service MUST NOT redirect to a destination derived from request
  input rather than from storage, and MUST NOT rewrite the destination at resolution time.
  **Evidence**: resolution test results including round-trip equality.

- **FR-URL-008** — *Expired-link behavior.* **[Confirmed]** An expired link MUST NOT redirect, and
  its outcome MUST be distinguishable from an unissued code.
  **Accept**: expired yields the expired outcome; unissued yields not-found; the two differ.
  **Reject (negative)**: an expired link MUST NOT redirect under any retry, cache, or race
  condition, including EC-009.
  **Evidence**: expiry boundary test results.

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
  redirect to fail (EC-012).
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

- **FR-URL-012** — *Duplicate request and idempotency behavior.* **[Confirmed]** The service MUST
  define and enforce its behavior when the same creation request is submitted more than once.
  **Accept**: a repeated creation request carrying the same idempotency marker yields the same
  link rather than a second link.
  **Reject (negative)**: a client-side retry MUST NOT create duplicate links or duplicate
  side effects (EC-003).
  **Evidence**: idempotency replay tests.

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
  presented key against that hash.
  **Reject (negative)**: no HTTP key-issuance surface may exist; keys MUST NOT be committed in
  configuration; keys MUST NOT be generated at application startup and emitted to console or log
  streams; the stored form MUST NOT be reversible to the key.
  **Rationale recorded at Gate 2**: the ability to run commands on the machine **is** the trust
  boundary, in the demonstration and in production alike. Operator-invoked terminal output is not
  application logging; the distinction being drawn is that the application never logs keys. An
  HTTP issuance endpoint would create an unprotected surface whose only demonstration defence —
  localhost — is meaningless when everything runs on localhost.
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

- **FR-ORC-004** — *Workflow state persistence.* **[Confirmed]** The orchestrator MUST persist
  workflow state durably enough to survive process termination.
  **Accept**: after termination at an arbitrary point, state is reloaded and the run's position
  is unambiguous.
  **Reject (negative)**: state MUST NOT exist only in process memory; recovery MUST NOT report a
  state it cannot substantiate from persisted records (EC-016).
  **Evidence**: kill-and-reload test results.

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
  workflow run from a submitted requirement and receive a durable run identifier.
  **Accept**: the run identifier appears in all subsequent logs, metrics, evidence, and state.
  **Reject (negative)**: work MUST NOT be executed outside a created, identified run.
  **Evidence**: run creation tests; identifier propagation check.

- **FR-ORC-008** — *Workflow inspection.* **[Confirmed]** A caller MUST be able to inspect a run's
  current state: per-stage status, graph position, parallel and join structure, blocking
  condition, retry history, and pending gates.
  **Accept**: inspection of an in-flight run reports its true current blocking condition.
  **Reject (negative)**: inspection MUST NOT report a stage as complete whose exit criteria were
  not satisfied.
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
  identifies the human, the question, the answer, and the time.
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
  outcome, actor, timestamp, and reason; and each gate record is materialized as a repository
  artifact within the commit that acts on the decision, not held only in workflow state.
  **Reject (negative)**: silence MUST NOT be recorded as approval; a mandatory gate MUST NOT be
  skipped, auto-satisfied, back-dated, or bypassed by re-entering the workflow downstream; a
  decision from an actor without recorded authority MUST NOT take effect (EC-024); the substantive
  work a decision authorizes MUST NOT begin before that decision's record exists.
  **Evidence**: one test per outcome, including the silence test; a test asserting the gate record
  is present as a repository artifact in the acting commit.
  *Added by Constitution Amendment 001 (v1.1.0, 2026-09-18) — see
  `docs/governance/gate-decisions/amendment-001-decision.md` sub-decision 6.*

- **FR-ORC-014** — *Bounded retries and timeouts.* **[Confirmed]** The orchestrator MUST classify
  failures as transient or permanent and MUST retry transient failures within a declared bound,
  with declared backoff and timeout.
  **Accept**: retry attempts are bounded, spaced per the declared backoff, and individually
  recorded with their outcome.
  **Reject (negative)**: retries MUST NOT be unbounded; a permanent failure MUST NOT be retried
  as if transient; an exhausted bound MUST NOT be reported as success.
  **Evidence**: retry histories under injected transient and permanent faults. *Bounds: PVT-007.*

- **FR-ORC-015** — *Fallback.* **[Confirmed]** Stages that can degrade MUST declare fallback
  behavior, and the orchestrator MUST apply it when the primary path is exhausted.
  **Accept**: the fallback executes and is recorded as a fallback, not as primary success.
  **Reject (negative)**: a failing fallback MUST NOT loop indefinitely and MUST escalate to
  safe-stop (EC-023).
  **Evidence**: fallback activation records.

- **FR-ORC-016** — *Rollback and compensation.* **[Confirmed]** The orchestrator MUST distinguish
  rollback of reversible, uncommitted effects from compensation of already-committed effects, and
  MUST apply the correct one.
  **Accept**: a reversible failure rolls back; a committed effect is compensated; the run history
  labels which occurred.
  **Reject (negative)**: compensation MUST NOT be described as rollback or vice versa; a
  compensation MUST NOT be applied twice for the same effect, including when a retry later
  succeeds (EC-022).
  **Evidence**: rollback and compensation run histories, separately labelled.

- **FR-ORC-017** — *Safe-stop.* **[Confirmed]** The orchestrator MUST enter safe-stop when
  continuation is unsafe, leaving state consistent and resumable and emitting a terminal outcome
  with a reason.
  **Accept**: safe-stop triggers on gate timeout, unrecoverable failure, and blocking policy
  failure; state after safe-stop is resumable.
  **Reject (negative)**: safe-stop MUST NOT continue past the blocked gate on reduced scope
  without a new human decision; it MUST NOT leave partially applied effects unrecorded.
  **Evidence**: safe-stop records for each trigger class.

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
  **Reject (negative)**: an agent MUST NOT take a human-owned action (Constitution III) under any
  autonomy setting.
  **Evidence**: autonomy limit definitions; refusal records.

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

- **FR-ORC-029** — *Per-stage executor mode, selectable and labelled.* **[Confirmed — AQ-003]** Each
  stage MUST declare its executor class per the approved executor map (see §Stage Executor Model). A
  configuration flag MUST select AI mode or deterministic mode per run, and every run's evidence
  MUST label each stage's executor mode.
  **Accept**: the reviewer default is deterministic and the full system runs with **no AI key
  present**; recorded demonstration runs in AI mode carry per-stage mode labels.
  **Reject (negative)**: a deterministic execution MUST NOT be presented as AI work; an unlabelled
  stage execution MUST NOT appear in evidence; the system MUST NOT require an AI key to run its
  reviewer-default path.
  **Evidence**: a complete run with no AI key configured; per-stage mode labels in run evidence for
  both modes.

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
  gate. A deterministic plan-applying mode MUST exist as fallback.
  **Accept**: an AI-authored change is applied on a branch, verified by the real suite, and either
  passes or routes back with its failure report; the bound is respected before escalation.
  **Reject (negative)**: creative authoring MUST NOT happen outside a stage; a failing AI-authored
  change MUST NOT be reported as success or merged; the verification suite MUST NOT be stubbed or
  simulated for this stage.
  **Rationale recorded at Gate 2**: the governed pipeline is the safety net for AI-authored code,
  which is the thesis of this assessment implemented literally. An AI patch failing tests and being
  caught is not a failed demonstration — it is the governance visibly working.
  **Evidence**: a run where an AI-authored change fails verification and routes back; a run where
  one passes; both with executor mode labels.

### Key Entities

- **KE-01 ShortLink**: an issued short code bound to a destination, with creation time, expiry,
  current state, and the owning creator (KE-23). Referenced by RedirectEvent.
- **KE-02 RedirectEvent**: a recorded act of following a short link, carrying the short code and the
  occurrence timestamp and no follower-identifying field (AQ-002). Belongs to one ShortLink.
- **KE-03 IdempotencyRecord**: the marker binding a creation request to the link it produced, so
  a repeat yields the same link (FR-URL-012).
- **KE-04 WorkflowRun**: one governed execution, with a durable identifier, current state, policy
  version evaluated, and terminal outcome.
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
  analytics. Owns zero or more ShortLinks. Provisioned by operator script, never self-service
  (FR-URL-019).
- **KE-24 CreatorCredential**: the stored **hash** of a creator's API key, never the key itself.
  Belongs to one Creator.
- **KE-25 StageExecutor**: the bound implementation for a StageNode, carrying its executor class
  (deterministic engine, AI-capable, or human gate) and the mode actually used in a given run
  (FR-ORC-029).

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
| 7 Implementation | AI-capable — AI authors the change, engine applies on a branch, real build and tests verify; deterministic plan-applying mode as fallback (FR-ORC-031) |
| 8 Testing | Deterministic, **real** — executes the actual test suite |
| 9 Documentation | AI-capable |
| 10 Security & policy checks | Deterministic, **real** — policy verdicts must be repeatable |
| 11 Release readiness | Deterministic evaluation of the nine blocking conditions, plus the release owner's recorded decision |
| 12 Final engineering summary | Deterministic assembler from recorded evidence only — every claim must be traceable, so creativity is a liability here |

**Binding conditions** (FR-ORC-028, FR-ORC-029, FR-ORC-030):

- One executor interface for every stage; no executor branches on recognizing specific inputs.
- Tests inject scriptable fake executors, so every reliability proof is deterministic and
  independent of AI availability.
- A configuration flag selects mode per run. **Reviewer default is deterministic**, runnable with no
  AI key present. Recorded demonstration runs use AI mode.
- Every run's evidence labels each stage's executor mode. Deterministic executions are labelled
  demonstration executions and are never presented as AI work.

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
- **NFR-REL-001** — Every orchestration run reaches a deterministic terminal outcome. *Verifiable:
  zero runs in an indeterminate state across the demonstration corpus.*
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
- **NFR-AUD-002** — Audit evidence retains policy outcomes and exceptions. *Verifiable: presence
  check per run. Retention: PVT-010.*
- **NFR-PERF-001** — Redirect resolution completes within PVT-001 at the p95 under stated
  conditions. *Verifiable: measured, with conditions declared.*
- **NFR-PERF-002** — Link creation completes within PVT-002 at the p95 under stated conditions.
  *Verifiable: measured, with conditions declared.*
- **NFR-REC-001** — A run interrupted at any stage boundary resumes and terminates deterministically.
  *Verifiable: interruption at every stage boundary in at least one scenario.*
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
- **NFR-AUT-001** — Autonomy limits are declared per stage, and human-owned actions cannot be
  taken by an agent at any setting. *Verifiable: refusal test per human-owned action class.*
- **NFR-AUT-002** — Absence of human response never advances a mandatory gate. *Verifiable: the
  silence test in FR-ORC-013.*
- **NFR-AUT-003** — Every stage execution in every run's evidence is labelled with the executor mode
  actually used, and deterministic executions are never presented as AI work. *Verifiable: zero
  unlabelled stage executions across the demonstration corpus.*
- **NFR-AUT-004** — The reviewer-default deterministic path runs end to end with no AI key and no
  network access. *Verifiable: full run in a key-less, network-isolated environment.*

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
- **SC-014**: The complete system runs end to end with no AI key and no network access, exercising
  the reviewer-default deterministic path.
- **SC-015**: Zero stage executions appear in evidence without an executor-mode label, and zero
  deterministic executions are presented as AI work.
- **SC-016**: A requirement outside the three demonstration scenarios completes a full run with no
  executor code changes, demonstrating that executors are generic engines rather than input-aware.
- **SC-017**: Zero personal data fields exist in the stored schema, and zero plaintext credentials
  exist in the repository or in application telemetry.

---

## Proposed Validation Targets *(require human approval)*

These are this specification's proposals. The assignment supplies no numeric targets, so none of
these is a client requirement (Constitution X, Assessment Scope §demonstration versus production).
Approval is requested at the Plan-stage gate.

| ID | Target | Proposed value | Basis | Conditions |
|----|--------|----------------|-------|------------|
| PVT-001 | Redirect resolution latency, p95 | ≤ 50 ms | Redirect is on the user's critical path; a short link should feel instant | Single instance, warm, local persistence, PVT-003 concurrency |
| PVT-002 | Link creation latency, p95 | ≤ 200 ms | Creation is an explicit, tolerant action | Same conditions as PVT-001 |
| PVT-003 | Sustained concurrent clients | 100 | Demonstrates concurrency invariants without turning the prototype into a load-testing exercise | Local, single instance |
| PVT-004 | Error-rate ceiling under PVT-003 | ≤ 0.1% non-4xx | Distinguishes load degradation from correctness failure | Excludes deliberate 4xx |
| PVT-005 | Keyspace before collision pressure | ≥ 10^9 links | Sets code length and alphabet | Uniform random issuance |
| PVT-006 | Gate wait before safe-stop | 24 h | Long enough for a real reviewer; short enough to demonstrate the timeout path | Configurable; demonstration runs may use a compressed value, labelled as such |
| PVT-007 | Retry bound and backoff | 3 attempts, exponential from 1 s | Bounded per Constitution VIII without masking permanent faults | Per transient-classified stage |
| PVT-008 | Coverage, domain and orchestration transitions | ≥ 85% branch | Meaningful for governed logic without coverage theatre | Excludes generated and infrastructure code |
| PVT-009 | Analytics recording tolerance under load | ≤ 0.5% loss | Analytics must never take down a redirect (FR-URL-010) | At PVT-003 |
| PVT-010 | Audit and analytics retention | 90 days audit, 30 days redirect events | Housekeeping cap; no personal data is held (AQ-002), so retention is not a privacy control here | Prototype scope |
| PVT-011 | Default link TTL when unspecified | 30 days | Bounded default rather than unbounded growth | Caller may override within limits |
| PVT-012 | Creation rate limit | 60 requests/minute per creator | Demonstrates throttling without impeding tests | Per authenticated creator (FR-URL-018) |
| PVT-013 | Redirect rate limit, per short code | 600 requests/minute per code | Hotspot protection: no single link can be hammered | Public, unauthenticated traffic |
| PVT-014 | Redirect rate limit, per creator aggregated | 3,000 requests/minute across all a creator's links | Noisy-neighbor protection: many links each under PVT-013 cannot collectively soak capacity | Deliberately below the sum of per-code limits, which is what makes the tier bite |

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
- **CN-011**: The reviewer-default path MUST be runnable with no AI key present. AI capability is a
  per-run mode, never a prerequisite for the system to function. *(Gate 2, AQ-003 — FR-ORC-029.)*

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
  every compressed value is labelled as compressed in the evidence.
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

Non-blocking ambiguities, still open and deferred to `/speckit-clarify`:

- **AQ-004**: Whether redirects should be permanent or temporary by default (CN-006 defers the
  mechanism; the caching and analytics consequences differ).
- **AQ-005**: Whether the same destination submitted twice without an idempotency marker should
  return the existing code or mint a new one (EC-002, FR-URL-012).
- **AQ-006**: Whether orchestration runs must survive a full restart of the persistence layer, or
  only of the orchestrator process (FR-ORC-004 currently reads as the latter).

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
EX-005.

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

**Applied to**: FR-URL-010, FR-URL-011, KE-02, NFR-SEC-005, PVT-010.

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

### CL-004 — Branch strategy | 2026-09-18 | Pravallika Veeravalli

**Decision**: stay on `main`. A single linear history is easiest for reviewers to follow and matches
the assessment guide's commit progression. **Applied to**: CN-009.

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

| Requirement | Journey | Scenario | Edge cases | Task | Test | Evidence |
|-------------|---------|----------|------------|------|------|----------|
| FR-URL-001 | US-1 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-002 | US-1 | DS-A | EC-006, EC-007 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-003 | US-1 | DS-A | EC-008 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-004 | US-1 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-005 | US-1 | DS-B | EC-004, EC-005 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-006 | US-1 | DS-A | EC-001, EC-008 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-007 | US-1 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-008 | US-1 | DS-A | EC-009 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-009 | US-1 | DS-A | EC-014 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-010 | US-1 | DS-B | EC-012, EC-013 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-011 | US-1 | DS-B | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-012 | US-1 | DS-A | EC-002, EC-003 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-013 | US-1 | DS-B | EC-001, EC-013 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-014 | US-1 | DS-B | EC-010, EC-011 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-015 | US-1 | DS-B | EC-011 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-016 | US-1 | DS-B | EC-013 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-017 | US-1, US-5 | DS-A | EC-007 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-018 | US-1 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-URL-019 | US-1, US-5 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-001 | US-3 | DS-A | EC-029 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-002 | US-3 | DS-A | EC-030 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-003 | US-3 | DS-A | EC-017, EC-018 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-004 | US-3 | DS-A | EC-015, EC-016 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-005 | US-5 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-006 | US-3 | DS-A | EC-029 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-007 | US-3 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-008 | US-3 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-009 | US-3 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-010 | US-3 | DS-A, DS-C | EC-021 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-011 | US-2 | DS-C | EC-021 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-012 | US-3 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-013 | US-2 | DS-A, DS-C | EC-024 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-014 | US-3 | DS-B | EC-022 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-015 | US-3 | DS-B | EC-023 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-016 | US-3 | DS-B | EC-022 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-017 | US-2 | DS-C | EC-023, EC-025 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-018 | US-3 | DS-C | EC-015, EC-026 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-019 | US-3 | DS-C | EC-019, EC-020, EC-030 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-020 | US-3 | DS-B | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-021 | US-2 | DS-A | EC-024 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-022 | US-4 | DS-B | EC-025, EC-027 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-023 | US-5 | DS-A, DS-B, DS-C | EC-028 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-024 | US-5 | DS-B | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-025 | US-4 | DS-A | EC-027, EC-028 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-026 | US-5 | DS-A | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-027 | US-5 | DS-A, DS-B, DS-C | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-028 | US-3, US-5 | DS-A, DS-B, DS-C | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-029 | US-5 | DS-A, DS-B, DS-C | — | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-030 | US-3 | DS-B, DS-C | EC-015, EC-018, EC-022, EC-023 | *Tasks stage* | *Tasks stage* | *Implement stage* |
| FR-ORC-031 | US-3 | DS-B | EC-023 | *Tasks stage* | *Tasks stage* | *Implement stage* |
