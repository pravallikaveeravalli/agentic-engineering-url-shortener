# Gate Decision Record — Gate 2: Specification Approval

| Field | Value |
|---|---|
| Gate | Specification approval (blocks Plan) |
| Outcome | **APPROVED**, with the three blocking clarifications answered and the resulting spec updates applied before commit |
| Deciding human | Pravallika Veeravalli |
| Decision date | 2026-09-18 |
| Artifact approved | `specs/001-agentic-sdlc-url-shortener/spec.md` |
| Governing constitution | v1.1.0 (last amended 2026-09-18) |
| Recorded in | This record; the specification's Clarification Log; the commit landing the updated specification |

## Reason / verification performed

The owner reviewed the specification against the assessment guide's Gate 2 rejection checklist —
premature technology choice, silent ambiguity resolution, missing negative behavior, missing
scenarios, linear-chain orchestration, missing human approval, missing recovery/safe-stop,
untestable, untraceable — and found none violated.

The two Gate 1 carry-forwards due at this stage are both present: the three demonstration scenarios
(DS-A / DS-B / DS-C) and measurable scalability NFRs (NFR-SCA-001..003).

The owner's answers to the three blocking ambiguities follow, recorded with her reasoning in full so
the record shows why each decision was made, not just what it is.

---

### AQ-001 — API authentication and link ownership: CUSTOM design (none of A/B/C as offered)

> I rejected all three offered options because they conflate two different populations that this
> product must treat differently:
>
> - **Link creators** — the people calling the API to make short links. They SHOULD be identified,
>   they own their links, and only they may see their links' analytics.
> - **Link followers** — the public clicking short links. They must NEVER need authentication; a
>   short link that requires login is useless. Option A had no identity at all, B had identity but
>   no ownership, and C put its weight on a full account model without separating the redirect side.
>
> **My design:**
>
> 1. **Creator provisioning is a local operator script, not an HTTP endpoint.** I considered and
>    rejected, in order: keys committed in a config file (violates the spirit of Principle V and
>    would trip the secret scan required at release readiness); keys generated at app startup and
>    printed to the console (the console is a log stream — startup output gets captured by Docker/
>    systemd/CI, which collides with the non-waivable no-secrets-in-logs clause); and a key-issuance
>    HTTP endpoint (creates an unprotected issuance surface whose only demo defense — localhost —
>    is meaningless when everything runs on localhost). A local script solves the bootstrap problem
>    cleanly: the ability to run commands on the machine IS the trust boundary, in the demo and in
>    production alike. The script may print the key once to the operator's terminal — that is
>    operator-invoked interactive output, not application logging; the distinction I am drawing is
>    that the APPLICATION never logs keys anywhere.
> 2. **Keys are stored only as hashes in the database.** No plaintext key material is ever
>    committed to the repository or written by the application to logs, traces, metrics, or error
>    responses.
> 3. **Ownership is real:** every link records the creator identity that made it; analytics for a
>    link are retrievable only by its creator.
> 4. **Redirect resolution is public, never authenticated.**
> 5. **Rate limiting is two-sided and, on the redirect side, two-tier:**
>    - Creation: limited per creator.
>    - Redirects: limited per short code (hotspot protection — no single link can be hammered) AND
>      per creator aggregated across all their links (noisy-neighbor protection — I specifically
>      want to guard against a creator with 100 links, each individually under the per-code limit,
>      collectively soaking the service's capacity). I accept the consequence that the public
>      clicking a popular creator's links can be throttled through no fault of their own: that is a
>      deliberate trade prioritizing service protection over unlimited availability of any one
>      tenant's links, and it should be documented as such in the threat model.
>    - All limit values are proposed validation targets for my approval at the Plan gate, not
>      confirmed requirements.
> 6. **Scope boundary:** self-service signup, account management, and billing remain out of scope.
>    Reword EX-005 accordingly rather than deleting it — creator identities exist as provisioned
>    operators, not as a user-account feature.

### AQ-002 — Analytics granularity and retention: OPTION B (per-event records, timestamp only)

> My reasons:
>
> 1. Option A (counters only) cannot answer "when" — no time series, no visible spike — and it
>    cannot provide stored evidence for the rate-limiting design I chose in AQ-001. An analytics
>    demo that is a single integer is also unconvincing.
> 2. Option C stores IP addresses, which is personal data. That would pull privacy obligations,
>    retention justification, and anonymization design into a 2–3 day assessment for zero graded
>    benefit — and volunteering to hold personal data in a demo for a financial firm is exactly the
>    kind of thing a reviewer probes. "I deliberately stored no personal data" is the stronger
>    position: data minimization as a practiced discipline, not an accident.
> 3. Option B gives real time-series analytics (clicks over time, bursts visible) with zero
>    personal data, and the stored events double as evidence when demonstrating the per-code and
>    per-creator-aggregate limits.
> 4. Retention: keep the proposed 30-day cap for redirect events (PVT-010) as a housekeeping
>    measure, subject to my approval at the Plan gate. Note my AQ-001 ownership model already
>    restricts who can READ analytics; B is about what we STORE.

### AQ-003 — Stage executors: CUSTOM ("C+") — pluggable executors, AI-capable where work is creative, deterministic engines where repeatability is the point, nothing input-hardcoded

> I worked through this in several steps and want the reasoning recorded:
>
> 1. I initially questioned how a system can be called an AI-native SDLC orchestrator if its stages
>    are scripted. The resolution I accept: the orchestrator is not itself the AI — it GOVERNS
>    agents (the assignment's own principle: agents execute under defined autonomy boundaries;
>    humans own oversight). But a system with zero real AI inside would make the title hollow, so
>    pure option B is out.
> 2. I explicitly reject input-specific hardcoding. Executors that recognize "the three blessed
>    demo inputs" would be a rigged demo and would fail the assessors' own question "is the
>    evidence strategy authentic?". Deterministic executors must be GENERIC ENGINES that operate on
>    data flowing through the workflow (the Jenkins model: the engine doesn't know the build; the
>    pipeline definition does). I verified the interviewers' documents do not restrict the system
>    to three requirements — the three scenarios are the required demonstrations, not a design
>    ceiling.
> 3. A deterministic engine can apply, build, and verify — but it cannot CREATE a code change.
>    Someone intelligent must author the patch. I chose to make the implementation stage AI-capable
>    at runtime rather than have creative work happen off-stage: the AI authors the change from the
>    design stage's output, the engine applies it on a branch, the REAL test suite verifies it, and
>    failure routes back with the report (bounded attempts, then my gate). The governed pipeline
>    itself is the safety net for AI-authored code — which is the entire thesis of this assessment,
>    implemented literally. An AI patch failing tests and being caught is not a failed demo; it is
>    the governance visibly working.

**The per-stage executor map approved by the owner:**

| Stage | Executor |
|---|---|
| 1 Requirement ingestion | Deterministic engine (intake, run creation) |
| 2 Requirement normalization | AI-capable |
| 3 Ambiguity detection | AI-capable (output feeds the human gate, so variability is safe) |
| 4 Human clarification | Human gate — no executor |
| 5 Task decomposition | AI-capable |
| 6 Architecture & design | AI-capable |
| 7 Implementation | AI-capable (AI authors the change; engine applies on a branch; real build and tests verify; deterministic plan-applying mode as fallback) |
| 8 Testing | Deterministic, REAL — executes the actual test suite |
| 9 Documentation | AI-capable |
| 10 Security & policy checks | Deterministic, REAL — policy verdicts must be repeatable |
| 11 Release readiness | Deterministic evaluation of the nine blocking conditions + the owner's recorded decision |
| 12 Final engineering summary | Deterministic assembler from recorded evidence only — every claim must be traceable, so creativity is a liability here |

**Architectural conditions attached to this answer, in the owner's words:**

> - Every stage runs behind ONE executor interface. No executor may branch on recognizing specific
>   demo inputs.
> - Automated tests inject fake executors (scriptable: "fail twice then succeed") so that retry,
>   timeout, fallback, rollback/compensation, safe-stop, resumption, and replanning proofs are
>   fully deterministic and never depend on AI availability or variability.
> - A configuration flag selects AI mode vs deterministic mode per run. The reviewer default is
>   deterministic (no AI key needed to run everything out of the box); my recorded demo runs will
>   show AI mode. Every run's evidence must label each stage's executor mode — deterministic
>   executions are labeled as demonstration executions per the assessment guide's evidence
>   integrity rules, never presented as AI work.

### Branch decision

> Stay on `main`. A single linear history is easiest for reviewers to follow and matches the
> guide's commit progression. (CN-009 resolved.)

## Conditions attached to the approval

1. This decision record is written before the specification updates begin, per Constitution
   §Gate semantics as amended in v1.1.0.
2. The specification is updated to incorporate all three answers before commit — at minimum:
   FR-URL-011 entitlement (owner-only); FR-URL-016 rewritten for the two-sided, two-tier limiting
   design; EX-005 reworded; key-handling requirements from AQ-001 (hashed at rest; never committed;
   application never logs keys; operator-script provisioning as the documented trust boundary); KE
   entities as needed (creator identity); new proposed validation targets for the per-code and
   per-creator-aggregate redirect limits; RedirectEvent granularity per AQ-002; and the executor
   model from AQ-003 (executor interface, mode labeling in run evidence, no input-specific
   branching) wherever the specification describes orchestration behavior and evidence.
3. The three resolved `[NEEDS CLARIFICATION]` markers are removed and the resolutions recorded in
   the specification's Clarification Log dated 2026-09-18. AQ-004..006 remain open for
   `/speckit-clarify`.
4. Specification quality validation is re-run and the checklist file updated; the owner expects all
   16 items to pass.
5. The accepted proposal's stale field is corrected to `| Applied | Yes — v1.1.0, 2026-09-18 |`.
6. The updated specification, its checklist, this gate record, and the proposal fix are committed as
   one commit with the canonical message
   `docs(spec): define governed agentic URL shortener requirements`.
7. Nothing is pushed. `/speckit-clarify` is not run — the owner initiates it.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| All limit values from the AQ-001 rate-limiting design remain proposed validation targets requiring approval | Plan gate |
| 30-day redirect-event retention (PVT-010) remains a proposal requiring approval | Plan gate |
| Noisy-neighbor throttling trade-off documented explicitly in the threat model | Plan gate |
| MTTR definition, formula, and measurement rules | Plan gate |
| 2–3 day timebox and scope controls | Plan gate |
| Versioned API/schema deliverables with contract validation | Plan gate |
| Plan-stage constitution check enumerated against v1.1.0, policy version recorded | Plan gate |
| Technology-selection ADR (no preference treated as settled) | ADR gate |
| AQ-004 (redirect permanence), AQ-005 (duplicate destination without idempotency marker), AQ-006 (persistence-layer restart scope) | Clarify stage |
| Reviewer-default deterministic mode must run with no AI key present | Implement / Release readiness |
| Every run's evidence labels each stage's executor mode; deterministic runs never presented as AI work | Implement / Release readiness |
