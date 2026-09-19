# Agentic Software Engineering System: URL Shortener Constitution

## Core Principles

### I. Specification Before Implementation

- Production implementation MUST NOT begin before the applicable specification is approved by
  the human owner.
- Every requirement MUST be testable and MUST carry a stable identifier that downstream
  artifacts can reference.
- Functional and non-functional requirements MUST remain separately identifiable; they MUST NOT
  be merged into undifferentiated prose.
- Every material ambiguity MUST be either resolved through clarification or recorded as an
  explicit, labelled assumption with its risk stated. Silent interpretation is prohibited.
- A material change to an upstream artifact MUST trigger documented downstream impact analysis
  before dependent work continues.
- Existing code MUST NOT become an undocumented substitute for the specification. Where
  implemented behavior diverges from approved requirements, the divergence MUST be reported as a
  conflict, not absorbed into the specification.

**Rationale**: Reviewers assess requirement understanding and ambiguity management. Both are
only observable when requirements exist independently of, and prior to, the code that satisfies
them.

### II. Explicit Agentic Orchestration

- The orchestration system MUST be modelled as an explicit dependency graph or an equivalent
  stateful model. A fixed linear chain of agent invocations does NOT satisfy this principle.
- The model MUST support sequential paths, parallel paths, synchronization/join points,
  conditional branching, dynamic replanning, interruption, and resumption.
- Every stage MUST declare, in a machine-readable form: inputs, outputs, entry criteria, exit
  criteria, owner (human or agent), and failure behavior.
- Workflow state, cross-stage context, artifact provenance, and decision lineage MUST be
  persisted durably enough to survive process termination and to be reconstructed afterwards.
- Agent autonomy MUST be bounded by declared limits, and every autonomous action MUST be
  observable and auditable after the fact.

**Rationale**: The orchestration system is the primary deliverable. Non-linear, stateful
behavior is only credible when the state and the graph are explicit artifacts rather than
emergent properties of a script.

### III. Human Governance

- Humans retain ownership of: requirements and their interpretation, architecture, technology
  selection, security-sensitive decisions, material exceptions, destructive or irreversible
  changes, acceptance of material risk, release readiness, and final quality.
- High-impact actions MUST NOT proceed without explicit recorded human approval.
- Every approval gate MUST define its outcomes. The permitted outcomes are: `APPROVED`,
  `REJECTED`, `CHANGES-REQUESTED`, `ESCALATED`, and `TIMED-OUT`.
- `TIMED-OUT` MUST transition the run to a safe-stop state with workflow state preserved and
  resumable. It MUST NOT advance the workflow.
- Absence of a response MUST NOT be interpreted as approval, under any timeout, retry, or
  convenience condition.
- A mandatory gate MUST NOT be skipped, auto-satisfied, back-dated, or bypassed by re-entering
  the workflow at a later stage.

**Rationale**: Controlled autonomy is the assessed behavior. A gate that can be satisfied by
silence is not a gate.

### IV. Test-Driven Engineering

- Red-green-refactor TDD MUST be applied to domain behavior and to orchestration behavior
  wherever technically practical.
- A failing test MUST be written before the implementation it covers.
- The test MUST be observed to fail for the expected reason before implementation begins; a test
  that fails for an unrelated reason (compilation error, wiring fault) does NOT satisfy the red
  phase.
- Implementation MUST be limited to the minimum behavior needed to reach green.
- Refactoring MUST occur only while tests are green.
- The test portfolio MUST include unit, integration, API contract, orchestration state
  transition, reliability, security, and end-to-end tests.
- A task is complete only when its validation has been executed and its result recorded.
  Generated code with unexecuted tests is NOT complete.
- Where TDD is genuinely impractical for a unit of work, the reason MUST be recorded against
  that task and the work MUST still carry executed validation.

**Rationale**: Testing discipline is assessed on evidence of the cycle, not on final coverage
numbers, which can be produced retroactively.

### V. Security and Privacy by Design

- All external input MUST be validated and normalized before use.
- Acceptable URL schemes MUST be restricted by allow-list. Scheme restriction MUST NOT be waived.
- Malicious redirect, redirect-loop, internal-network targeting, and abuse scenarios MUST be
  identified and addressed or explicitly accepted as recorded risk.
- Secrets, credentials, tokens, and sensitive user data MUST NOT be written to logs, traces,
  metrics, or error responses. This clause MUST NOT be waived.
- Configuration defaults MUST be secure; insecure behavior MUST require explicit opt-in.
- Least privilege MUST be applied, and trust boundaries MUST be documented explicitly.
- Authentication assumptions, rate limiting behavior, threat scenarios, and accepted security
  trade-offs MUST be documented.
- Dependency vulnerability scanning and secret scanning MUST be part of release readiness.

**Rationale**: A URL shortener is an open redirect engine by construction; its security posture
is a requirement, not a hardening afterthought.

### VI. Compliance and Change-Control Policy Enforcement

- The project MUST define versioned policy guardrails covering, at minimum: security,
  compliance, privacy, audit retention, approved dependencies, software licensing, and change
  control.
- Every orchestration run MUST record the policy version it evaluated.
- Every applicable policy check MUST produce exactly one outcome from: `PASS`, `FAIL`,
  `EXCEPTION-REQUESTED`, `NOT-APPLICABLE`.
- A `FAIL` on a mandatory policy check MUST block downstream workflow progression.
- A policy exception MUST require explicit human approval. An `EXCEPTION-REQUESTED` outcome
  without recorded approval MUST be treated as `FAIL`.
- Every approved exception MUST record: the applicable policy, the reason, the scope, the
  approving authority, the compensating control, the approval timestamp, and the expiry or
  review condition.
- Changes to approved requirements, architecture, schemas, workflow states, security controls,
  or release criteria MUST pass through formal impact analysis and change approval.
- Release readiness MUST fail while any mandatory compliance or change-control policy remains
  violated or carries an unapproved exception.
- Policy outcomes and exceptions MUST be included in audit and traceability evidence.

**Rationale**: Governance is only demonstrable if policy evaluation produces enumerable,
machine-checkable outcomes that can block progress.

### VII. Architecture and Maintainability

- Domain logic, API delivery, persistence, orchestration, policy enforcement, telemetry, and
  infrastructure concerns MUST be separated into distinct, independently testable units.
- External dependencies MUST be reached through explicit interfaces owned by this codebase.
- Material architectural decisions MUST be recorded with the alternatives considered and the
  reason each was rejected.
- Complexity that cannot be justified by an approved requirement or by demonstrability MUST NOT
  be introduced.
- Code MUST remain modular, readable, testable, and replaceable.

**Rationale**: Architecture is assessed on the decisions and their justification, which requires
rejected alternatives to be visible.

### VIII. Reliability and Recovery

- Failures MUST be classified as transient or permanent, and the classification MUST drive
  handling.
- Retries MUST be bounded, and backoff and timeout behavior MUST be defined explicitly.
- Operations that may be repeated MUST be idempotent, guarded by an idempotency key, or
  otherwise safe under replay.
- Fallback behavior MUST be defined for each stage that can degrade.
- Rollback (undoing an uncommitted or reversible change) MUST be distinguished from compensation
  (issuing a corrective action for an already-committed effect).
- Safe-stop conditions MUST be defined, and a safe-stop MUST leave state consistent and
  resumable.
- Every run MUST reach a deterministic terminal outcome; an abandoned or indeterminate run is a
  defect.
- Controlled resumption after interruption MUST be supported without duplicating committed
  effects.
- Evidence of success, failure, retry, compensation, recovery, and latency MUST be captured.

**Rationale**: Retries, fallback, rollback, compensation, and safe-stop are explicitly assessed
behaviors and must be distinguishable from one another in the evidence.

### IX. Observability and Auditability

- Every orchestration execution MUST be assigned a correlation or run identifier that propagates
  across stages, logs, metrics, traces, and persisted state.
- State transitions, decisions, approvals, retries, failures, replanning events, and terminal
  outcomes MUST be recorded.
- Every audit record MUST include: actor type (human or agent), action, timestamp, affected
  artifact or state, result, and reason.
- Logs, metrics, traces, and workflow history MUST be sufficient to reconstruct an execution
  after the fact without access to the original session.
- Metrics produced by demonstration or synthetic scenarios MUST be labelled as such and MUST NOT
  be presented as production measurements.

**Rationale**: Auditability is assessed by whether a reviewer can independently reconstruct what
happened, including the parts that went wrong.

### X. Traceability and Repository Integrity

- Traceability MUST be maintained across the chain: requirement → scenario → decision → design →
  task → implementation → test → validation → documentation → evidence.
- Commits MUST be small and logically coherent; unrelated changes MUST NOT be bundled.
- Commit messages MUST describe engineering intent, not file inventories.
- Approval, test, execution, metric, log, trace, or operational evidence MUST NOT be fabricated,
  simulated-and-presented-as-real, or back-filled. This clause MUST NOT be waived.
- Proposed or illustrative evidence MUST be labelled distinctly from executed evidence.
- AI-generated artifacts MUST be reviewed and owned by the human before they are treated as
  approved.
- Documentation MUST be updated in the same change as the behavior it describes.

**Rationale**: Every other principle is unverifiable if the evidence trail can be manufactured.

### XI. Evidence-Based Completion

A feature or task is complete only when all of the following hold:

1. Applicable requirements are identified by identifier.
2. Decisions and assumptions are recorded.
3. Acceptance criteria are satisfied.
4. Required tests have been executed successfully, with results available.
5. Security and reliability checks are complete.
6. Documentation is current.
7. Traceability is complete across the chain in Principle X.
8. Residual risks and limitations are disclosed.
9. Required human approval is recorded.
10. Evidence is available for independent reviewer verification.

Partial satisfaction MUST be reported as incomplete with the unmet items named. "Done" MUST NOT
be asserted on the basis of generated code alone.

**Rationale**: A single explicit definition of done prevents completion from being negotiated
downward under time pressure.

## Assessment Scope and Evidence Standards

- **Demonstration domain**: The URL shortener is the demonstration domain. It MUST be genuinely
  working software, not a stub, because the governed lifecycle cannot be demonstrated over work
  that does not run.
- **Primary deliverable**: The governed, stateful, non-linear agentic engineering orchestration
  system is the central differentiator under assessment. Effort allocation MUST reflect this,
  without reducing the demonstration domain to a non-functional placeholder.
- **Technology selection is deferred**: This constitution is deliberately technology-neutral. No
  language, framework, database, or platform is mandated or implied here. Technology selection is
  human-owned (Principle III) and MUST be recorded in an approved Architecture Decision Record at
  the Plan stage, with alternatives and rejection reasons. Implementation MUST NOT proceed on an
  unapproved stack.
- **No invented requirements**: Requirements MUST trace to the assessment brief or to an approved
  specification or clarification. Scope MUST NOT be expanded on the basis of inferred reviewer
  preference.
- **Demonstration versus production**: Any figure, threshold, latency, throughput, or
  availability claim MUST state whether it was measured, and under what conditions. Unmeasured
  targets MUST be labelled as targets.
- **Repository as source of truth**: Approvals, decisions, and evidence count only when recorded
  in the repository. Statements made only in conversation are not artifacts and MUST NOT be cited
  as approval. A decision stated in conversation becomes citable only once materialized under
  §Development Workflow and Quality Gates → Gate semantics. Process rules, conventions, and
  standing instructions issued only in conversation carry no authority; to bind future work they
  MUST be recorded in this constitution or in the operational guidance under §Runtime guidance.
- **Disclosure of AI assistance**: The AI-assisted development process MUST be described
  truthfully, including the division of labour between human and agent, and including where the
  process deviated from plan.

## Development Workflow and Quality Gates

**Authoritative lifecycle order**. SpecKit is the sole lifecycle framework. Competing planning,
task, memory, or execution methodologies MUST NOT be introduced or simulated. The order is:

1. Constitution
2. Specify
3. Clarify
4. Plan
5. Architecture Decision Records
6. Checklist
7. Tasks
8. Analyze
9. Implement
10. Converge

Stages MUST NOT be reordered to make implementation more convenient. Returning to an earlier
stage is permitted and expected; doing so MUST re-run the affected downstream stages rather than
leaving stale artifacts in place.

**Mandatory human gates**. Each of the following requires an explicit recorded human outcome
before the next stage begins:

| Gate | Owner | Blocks |
|------|-------|--------|
| Constitution ratification | Human | All downstream stages |
| Specification approval | Human | Plan |
| Clarification resolution | Human | Plan |
| Architecture and technology selection (ADR) | Human | Implement |
| Task plan approval | Human | Implement |
| Analysis findings disposition | Human | Implement |
| Security-sensitive change approval | Human | Merge of that change |
| Destructive or irreversible action approval | Human | That action |
| Policy exception approval | Human | Downstream progression |
| Release readiness | Human | Submission |

**Gate semantics**. Gate outcomes are those defined in Principle III. A gate record MUST capture
the outcome, the deciding human, the timestamp, and the reason. `CHANGES-REQUESTED` returns work
to the owning stage with the requested changes enumerated. `ESCALATED` records what decision
exceeds the current owner's authority. A gate record MUST be materialized as a repository artifact;
a conversational statement of the decision does not satisfy this requirement. The agent acting on
the decision is responsible for materializing the record, and MUST NOT begin the substantive work
the decision authorizes before that record exists in the working tree. The record MUST be committed
no later than the commit that acts on the decision; where the decision approves no artifact, it
MUST be committed immediately in its own governance commit. The location and structure of gate
records are defined in the operational guidance referenced under §Runtime guidance, which MUST
define them; silence or contradiction there is itself a compliance failure.

**Safe-stop**. On `TIMED-OUT`, on an unrecoverable failure, or on a blocking policy `FAIL`, the
run MUST persist its state, emit its terminal outcome and reason, and stop. It MUST NOT continue
past the blocked gate on a reduced scope without a new human decision.

**Implementation discipline**. During Implement, each task MUST follow red-green-refactor per
Principle IV, MUST record executed validation, and MUST update traceability and documentation in
the same change. Tasks MUST be small enough to review independently and MUST respect declared
dependency order.

**Conflict handling**. Any material conflict between requirements, plan, ADRs, tasks, and
existing implementation MUST be reported and resolved by the human. It MUST NOT be silently
worked around, and implementation behavior MUST NOT be used to override approved requirements.

## Governance

This constitution supersedes all other practices, conventions, and conversational instructions
for this repository. Where a conflict exists, the authority order is: official assessment
requirements, then this constitution, then approved specification and clarifications, then
approved plan and contracts, then approved ADRs, then the generated task plan, then current
implementation, then informal conversation.

**Compliance assessment during planning**. Every plan MUST include a constitution check that
enumerates each principle and records one of `PASS`, `FAIL`, `EXCEPTION-REQUESTED`, or
`NOT-APPLICABLE`, with justification for anything other than `PASS`. The `/speckit-analyze` stage
MUST re-verify the check against the produced artifacts. A plan carrying an unresolved `FAIL` on
a mandatory principle MUST NOT proceed to Implement. Complexity introduced without a
requirement-based or demonstrability-based justification MUST be recorded as a `FAIL` against
Principle VII.

**Exception procedure**. An exception is proposed by recording: the principle or policy
concerned, the precise clause, the reason, the scope and duration, the compensating control, and
the residual risk. It MUST be approved explicitly by the human owner, who records the approval
timestamp and the expiry or review condition. An unapproved or expired exception MUST be treated
as a violation and MUST block downstream progression. Exceptions MUST be included in audit and
traceability evidence (Principle VI) and MUST be disclosed in release readiness.

**Non-waivable principles**. The following MUST NOT be waived by any exception, for any reason:

- Principle I — the requirement that approved specification precedes production implementation.
- Principle III — human ownership, explicit approval of high-impact actions, the prohibition on
  treating silence as approval, and the prohibition on silently skipping mandatory gates.
- Principle V — the prohibition on exposing secrets or sensitive data in logs and telemetry, and
  the requirement that acceptable URL schemes be allow-listed.
- Principle X — the prohibition on fabricating approval, test, execution, metric, or operational
  evidence.
- Principle XI — the definition of completion. Completion criteria may be reported as unmet; they
  MUST NOT be redefined downward to declare completion.

All other clauses are waivable only through the exception procedure above, with a recorded
compensating control.

**Conflict resolution between principles**. Where two principles genuinely conflict, they are
resolved in tiers, highest first:

1. **Legitimacy and safety** — III (Human Governance), V (Security and Privacy), VI (Compliance
   and Change Control), and the non-fabrication clause of X.
2. **Correctness and evidence** — I (Specification Before Implementation), IV (Test-Driven
   Engineering), XI (Evidence-Based Completion), and the remainder of X.
3. **Engineering quality** — II (Orchestration), VII (Architecture), VIII (Reliability), IX
   (Observability).

Within a tier, the conflict MUST be escalated to the human owner and the resolution MUST be
recorded as an Architecture Decision Record naming both principles, the trade-off, and the
accepted cost. Conflicts MUST NOT be resolved by the agent's own preference or by choosing the
cheaper path.

**Amendment procedure and versioning**. Amendments require: a written proposal, downstream impact
analysis across specification, plan, ADRs, tasks, and implementation, explicit human approval, a
version increment, an updated amendment date, and a recorded change entry. Versioning follows
semantic versioning:

- **MAJOR** — backward-incompatible governance change: a principle is removed, redefined, or has
  its non-waivable status changed; the authority order changes; a mandatory gate is removed.
- **MINOR** — a principle or section is added, or existing guidance is materially expanded or
  tightened.
- **PATCH** — clarification, wording, formatting, or typo correction with no change in obligation.

On any MAJOR or MINOR amendment, active features MUST re-run `/speckit-analyze`, and any
consequently invalidated approval MUST be re-obtained. Amendments MUST NOT be applied
retroactively to claim compliance for work already completed under a prior version; the version
in force at the time of the work MUST be identifiable.

**Non-compliance blocks release readiness**. Release readiness MUST fail while any of the
following holds:

1. A mandatory principle check is `FAIL` with no approved exception.
2. A mandatory policy check is `FAIL`, or an exception is unapproved or expired.
3. A mandatory human gate lacks a recorded outcome, or its outcome is not materialized as a
   repository artifact.
4. Required tests have not been executed, or executed tests are failing without recorded
   acceptance.
5. Dependency vulnerability scanning or secret scanning has not been run, or has unaddressed
   findings without recorded acceptance.
6. Traceability is incomplete for any delivered requirement.
7. Documentation does not reflect delivered behavior.
8. Residual risks and known limitations are not disclosed.
9. Any evidence presented is unverifiable, or demonstration evidence is presented as production
   measurement.

Release readiness is a human decision (Principle III) and MUST NOT be self-certified by an agent.

**Runtime guidance**. This constitution states obligations. Operational, repository-specific
development guidance is maintained separately in `CLAUDE.md` and MUST NOT contradict this
document; where it does, this document prevails. `CLAUDE.md` MUST define the gate record location
convention and record structure required by §Gate semantics. Operational guidance MAY be revised
without a constitutional amendment, but MUST NOT be used to weaken, delay, or condition an
obligation stated here.

## Amendment History

Every amendment records a change entry here, per §Amendment procedure and versioning. Entries are
append-only: an entry is never edited or removed once recorded.

- **1.0.0 | 2026-09-17 | Initial ratification** | Eleven principles, assessment scope and evidence
  standards, development workflow and quality gates, and governance established. Ratified by
  Pravallika Veeravalli. Decision record:
  `docs/governance/gate-decisions/gate-01-constitution.md`.
- **1.1.0 | 2026-09-18 | MINOR** | Gate decision records MUST be materialized as repository
  artifacts by the agent acting on the decision, no later than the commit acting on it;
  conversation-only process rules carry no authority; `CLAUDE.md` MUST define gate record location
  and structure; release readiness blocks on unmaterialized gate outcomes. Raised by Pravallika
  Veeravalli after Gate 1 review found v1.0.0 specified gate-record contents and barred
  conversation-only approvals without naming a location, actor, or deadline. Gate 1 (2026-09-17)
  was decided under v1.0.0, remains valid and unmodified, and its late record materialization was
  not a violation of the version then in force. Proposal:
  `docs/governance/amendments/proposal-001-gate-record-materialization.md`. Decision record:
  `docs/governance/gate-decisions/amendment-001-decision.md`.

**Version**: 1.1.0 | **Ratified**: 2026-09-17 | **Last Amended**: 2026-09-18
