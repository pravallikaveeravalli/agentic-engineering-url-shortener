# Gate Decision Record — Gate 3: Clarification Resolution

| Field | Value |
|---|---|
| Gate | Clarification resolution (blocks Plan) |
| Outcome | **APPROVED** — all five clarification answers approved for folding; effect inventory **CONFIRMED** as presented |
| Deciding human | Pravallika Veeravalli |
| Decision date | 2026-09-19 |
| Artifact approved | `specs/001-agentic-sdlc-url-shortener/spec.md` (clarifications CL-005..CL-009) |
| Governing constitution | v1.1.0 (last amended 2026-09-18) |
| Recorded in | This record; the specification's Clarification Log (CL-005..CL-009); the commit landing the folded specification |

## Reason / verification performed

A rigorous ambiguity and underspecification review of the approved specification was run against
the owner's prioritized list. Five questions were asked and answered, one at a time. Three of the
five addressed defects the review found in the approved specification itself — a contradiction, a
non-testable requirement, and an unenumerated recovery register — rather than merely filling gaps.
The remaining two resolved AQ-005 and AQ-006, carried forward from Gate 2.

All spec writes were held until every answer was in, so the interacting decisions could be folded in
one coherent pass rather than leaving the specification transiently self-contradictory.

---

### CL-005 — Q1, Safe-stop and run state semantics

**Defect found**: FR-ORC-017 described `SAFE_STOP` as leaving state "resumable" while "emitting a
terminal outcome", against NFR-REL-001's "every run reaches a deterministic terminal outcome". A
state cannot be both terminal and resumable.

**Decision**: Option B, refined by the owner.

> SAFE_STOP is a distinct non-terminal suspended state. Terminal states are exactly COMPLETED,
> REJECTED, ABANDONED. A suspended run moves only by (a) a human decision — resume or abandon — or
> (b) the pre-approved retention policy below. Reword NFR-REL-001 accordingly ("every run reaches a
> terminal state or a durable suspended state from which only a human decision or resumption
> proceeds").
>
> Retention policy (my design, record this reasoning):
> 1. The idle clock runs from the run's LAST activity/state change, not from creation time — a run
>    that received attention must not be reaped on age.
> 2. After a generously high configurable idle period — propose 30 days as a new PVT for my approval
>    at the Plan gate; demonstration runs may use compressed values labeled per AS-007 — the run is
>    automatically abandoned.
> 3. Every suspended run exposes its computed auto-abandon-at timestamp via workflow inspection: a
>    standing warning, in place of notification infrastructure. Record email/webhook expiry
>    notification as a proposed production enhancement, out of demo scope.
> 4. Auto-abandonment writes an audit event citing the retention policy version, actor type system,
>    authority: this pre-approved policy. Policy-driven abandonment is NEVER an approval; silence
>    still advances nothing.

**Correction to the retention period, issued by the owner before answering Q2:**

> the proposed retention period is 90 days idle, not 30.
>
> My reasoning, record it: in software engineering it is common for a feature to be worked on for
> around 90 days — a quarter — so a suspended run waiting on a human answer can legitimately sit
> that long without being dead. 90 days also aligns with the 90-day audit-retention proposal already
> in PVT-010. Still configurable, still a proposed validation target for my approval at the Plan
> gate, and demonstration runs still use compressed labeled values.

**Addendum, deadline stated in the ask, issued by the owner:**

> When the orchestrator presents a human with a gate decision or clarification request, the request
> itself MUST state the expiry consequence up front — for example: "If there is no response by
> <gate-wait deadline>, this run suspends. If it remains untouched, it will be auto-abandoned on
> <date computed from the retention policy>."
>
> The shown auto-abandon date is computed from the policy (last activity + retention period). This
> is in addition to the inspectable auto-abandon-at field I already decided — the point is that the
> person being asked sees the deadline in the ask itself, rather than having to discover it by
> inspecting the run.

### CL-006 — Q2, Failure classification and retry eligibility

**Defect found**: FR-ORC-014 required failures to be "classified as transient or permanent" without
naming who decides, how, or what happens to an unclassifiable failure — leaving the requirement
untestable.

**Decision**: Option C — executor proposes, orchestrator validates, either side can veto. The owner
recorded that she worked through this at length, including an independent second-opinion review.

> The design as I approve it:
>
> 1. A standard error envelope at the orchestration boundary: a small fixed category vocabulary
>    (e.g. TIMEOUT, UNAVAILABLE, RATE_LIMITED, INVALID_INPUT, INTERNAL, UNKNOWN), the executor's
>    proposed classification (transient/permanent), and free-form detail. Executors translate their
>    provider-specific errors into this envelope, so provider knowledge stays in the plugin; each
>    stage's declared retryable set is expressed over the standard categories only.
> 2. A retry requires BOTH yes-votes: the executor's proposal AND membership in the stage's declared
>    retryable set. Either side can veto — the declared set cannot force a retry the executor
>    reports as hopeless, and the executor cannot obtain a retry the declaration never approved.
>    Formally: retries = declared set ∩ executor proposal. The executor holds veto power, never
>    grant power.
> 3. Anything unrecognized — an UNKNOWN category, an undeclared code, a malformed envelope — is
>    treated as permanent and takes the safe path (suspension per my Q1 design). This is the same
>    default-deny rule I already approved for policy checks (EC-025: the unevaluable never defaults
>    to PASS).
> 4. A timeout is retryable only where the stage's declared contract marks its effect
>    idempotent/repeat-safe. That contract is a stage-level declaration fixed at design time — never
>    the executor's self-certification — because a timeout means completion is unknown, and
>    replaying a maybe-completed non-idempotent effect violates exactly-once (FR-ORC-018).
> 5. The orchestrator is the final authority not because it understands the error better — it never
>    overrides the diagnosis — but because it alone holds the decision context the executor cannot
>    see: attempts consumed against the bound, whether compensation was already issued for this
>    attempt (EC-022), whether replanning invalidated the stage (EC-019/EC-020), and whether the run
>    is blocked by safe-stop, a pending gate, or a mandatory policy FAIL. Executor = diagnosis;
>    orchestrator = policy + memory.
> 6. Share information downward freely: the executor receives the attempt number and remaining
>    budget as input and may adapt its strategy. Information flows down; authority does not.
>    FR-ORC-021 requires that an action outside declared limits be refused and recorded — a refusal
>    path can only exist outside the governed party, which matters doubly with six AI-backed
>    executors.
> 7. Record as a deferred production enhancement, out of timebox scope: a run-level retry circuit
>    breaker (whole-run retry ceiling). Per-stage bounds plus the run-level blocks above are the
>    in-scope controls.

**Alternatives rejected, in the owner's words:**

> - D (no classification, retry everything): violates Principle VIII outright — "classification MUST
>   drive handling" — and would FAIL the plan's constitution check. Never a legal option.
> - A as offered (static list, timeouts always retried): the timeout clause violates
>   duplicate-execution protection / FR-ORC-018.
> - A′ (static list with the timeout clause repaired): legal, and the simplest compliant option, but
>   rejected for three reasons: the central list must absorb every executor's error taxonomy,
>   coupling the core to its plugins against the modularity principle; it burns bounded attempts on
>   failures the executor knows are deterministic, polluting retry history with predetermined
>   outcomes; and it forfeits the executor's veto.
> - B (executor self-classifies, unknown defaults to retry): rejected as fail-open — the direct
>   opposite of my EC-025 default-deny precedent — and structurally incompatible with FR-ORC-021: the
>   autonomy limit would live inside the governed component, so no refusal path can exist, and the
>   audit answer to "why did this retry?" degenerates to "the agent said so." A gate satisfiable by
>   the governed party's testimony is not a gate.

**Doubt raised and resolved, recorded at the owner's request:**

> I questioned whether the orchestrator's check duplicates the executor's effort. It does not — the
> layers hold disjoint information (proximate cause vs. policy-and-run-state), the check is a
> set-membership lookup rather than a second diagnosis, and every retry decision produces a
> two-signature audit record: what the executor claimed and what the orchestrator ruled,
> maker-checker style.

### CL-007 — Q3, Rollback versus compensation

**Defect found**: FR-ORC-016 required the two to be distinguished and the correct one applied, but no
inventory existed of which effects are reversible — so the orchestrator had no basis to choose.

**Decision**: Option C — structural default plus declared overrides.

> the orchestrator handles the common effect kinds centrally — the small lookup table of roughly six
> rows, written once while building — and each stage adds its own lines to the dictionary only where
> its fix requires product knowledge (for example: an unwanted short link is corrected by setting it
> to expired, never deleted). This gives one place for the common answers and keeps stage authors
> responsible only for what they uniquely know.
>
> Carry forward into the fold, from my earlier exchange on option A: registration-time enforcement
> stays — a stage declaring an irreversible effect MUST name its compensating action or it does not
> load; and a declaration that contradicts the structural rule (claiming erasable for an effect
> landing in an immutable store) is flagged for human review rather than silently trusted.
>
> Unknown or unclassified effects are never erased: treat as compensate-only; where no fix is known,
> do nothing to the effect and suspend the run per my Q1 design — reason recorded, deadline shown in
> the ask, retention policy applicable. Executors acting outside the provided effect channels are
> refused and recorded as autonomy violations, and never reach cleanup.

**Confirmed effect inventory — the compensation register.** Confirmed by the owner exactly as
presented, including keep-no-deletion for short links, append-only audit and gate records, local
un-pushed work as the only erasable class, and AI invocations as nothing-to-recover.

| Effect | Reversible? | Basis |
|---|---|---|
| Working-tree file writes (docs, summary, plan) | **Yes** — rollback | Discardable before commit |
| Local branch commits, stage 7 | **Yes** — rollback | Reset or branch delete; nothing is pushed (CL-004) |
| Recorded gate decision | **No** — compensate by superseding record | `CLAUDE.md` immutability: a changed decision is a new record referencing the prior one; EC-020's voided approval is a superseding record, never a deletion |
| Audit records | **No** — compensate by further entry | NFR-AUD-001 immutability |
| Created short links | **No** — compensate by setting expired | EX-003 excludes deletion; owner's rationale: dangling history, short-code reuse hijack, repeat-safe cleanup |
| Recorded redirect events | **No** — compensate | Append-only analytics (FR-URL-010) |
| AI provider invocations | **No** — nothing to compensate | Cannot be un-called; no external state changed |

### CL-008 — Q4, Duplicate destination behavior (AQ-005)

**Decision**: Option D — marker-only deduplication.

> 1. No idempotency marker → always mint a new code. No destination-based deduplication at any
>    scope, ever.
> 2. Same marker + identical request → replay: return the ORIGINAL link as success (not an error),
>    labeled as a replay, with zero new side effects. This is EC-003 mechanized — a client that
>    never saw the first response must be able to retry safely.
> 3. Same marker + different request content (for example a different expiry) → explicit conflict
>    error; nothing is changed, nothing is minted. Record my reasoning: an idempotency key identifies
>    one exact logical request, not a destination. Updating the existing link on replay is forbidden
>    twice over — replay must not change state, and link editing is excluded scope for the same
>    reason deletion is (the link is already circulating with a promised lifetime). Silently minting
>    a new code past a detected caller bug would be guessing past a surfaced mistake.
>
> Why not the others, record it: B's deduplication forces a silent lie when the second request
> carries a different expiry — ignore what the caller asked or mutate a circulating link, both
> invisible. C breaks my AQ-001 ownership model outright (whose link, whose analytics) and leaks
> cross-creator information (one creator learns another shortened the same URL). Keyspace cost of
> always-minting is accepted — negligible against the billion-scale keyspace target.
>
> This is the same principle as my previous three answers: act on what was explicitly declared,
> never on inferred intent.

### CL-009 — Q5, State recovery scope (AQ-006)

**Decision**: Option B — runs survive both an orchestrator-process restart and a persistence-layer
restart; a disk-backed store is therefore required.

> B is the only option that is both honest and lean. A is not merely cheap — it is the loophole: an
> in-memory store technically satisfies the current wording while making the recovery guarantee
> nominal, protecting nothing. C is B plus test-harness cost for no design difference once the store
> is disk-backed — waste for a demonstration. D means two different recovery guarantees to build,
> test, and defend — waste of a different kind. A store outage flows through the machinery I have
> already decided: UNAVAILABLE in the Q2 envelope → proposed transient → intersected with the
> stage's declared retryable set → bounded retries → on exhaustion, suspension per my Q1 design with
> the deadline shown in the ask → correct resumption when the store returns.

## Conditions attached to the approval

1. This decision record is written before the folding pass begins, per Constitution §Gate semantics.
2. All five clarifications are folded in a single coherent pass, including the SC-017 back-references
   to CL-001 and CL-002.
3. Specification quality validation is re-run and the checklist result reported.
4. The specification, its checklist, and this gate record are committed together with the canonical
   message `docs(spec): resolve critical ambiguities and record assumptions`.
5. Nothing is pushed. No further lifecycle stage is run.
6. The full deferred-findings list is reported for the owner's disposition before the Plan stage.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| Idle retention period (90 days) approved as a PVT value | Plan gate |
| Gate-wait period and compressed demonstration values labelled per AS-007 | Plan gate |
| Run-level retry circuit breaker — deferred production enhancement, out of timebox scope | Post-assessment |
| Email/webhook expiry notification — deferred production enhancement, out of demo scope | Post-assessment |
| Disk-backed store requirement constrains the technology-selection ADR | ADR gate |
| Audit-versus-idle retention boundary rule (both currently 90 days) | Plan gate |
| PVT-009 analytics-loss tolerance versus FR-URL-010/SC-001 exactness | Plan gate |
| AQ-004 redirect permanence | Plan gate |
| MTTR definition, formula, and measurement rules | Plan gate |
| 2–3 day timebox and scope controls | Plan gate |
| Versioned API/schema deliverables with contract validation | Plan gate |
| Plan-stage constitution check enumerated against v1.1.0, policy version recorded | Plan gate |
| Technology-selection ADR | ADR gate |
