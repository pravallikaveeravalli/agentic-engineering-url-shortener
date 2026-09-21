# Change Request CR-015 — Plan: Noisy-Neighbour Tier as the Brownfield Subject, Escalation Thresholds, Node Identity, and Five Corrections

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, from `/speckit-analyze` findings **H8**, **H5**, **H4**, **H9**, **M2**, **M3**, **M4**, **L4** |
| Revision | **Draft 3**, 2026-09-20 — owner **Decision H** withdrew archival retention; **Decision I** re-pointed the brownfield subject to the noisy-neighbour aggregate redirect tier. Carried from draft 2: **Decision A** (escalation thresholds, Edits 4–5, 13), **Decision D** (policy, Edit 14), **Decision E** (gate class), **Decision F** (replay struck) |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"go ahead."* |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/plan.md` — *"Further changes to this plan pass through change control."* |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decisions | Owner **Decisions A, D, E, F, H, I**, **Decision 2** (node identity), **Decision 3** (credential separation) |
| Application order | Fifth, after CR-010 (PVT-016 must exist in `spec.md` before §3 cites it) and after CR-013/CR-011 (node model). |

## Why this change exists

**H8 is the finding that forced Decision 4.** DS-B's subject was *"Redirect analytics must not lose events under
concurrent load"* — a defect that ADR-014 resolved and that **T050 implements in Phase 3, five phases before DS-B
runs in Phase 8**. T137 requires a *"before-state that shows the regression"* which T050 structurally prevents from
existing. A brownfield run against already-correct code has nothing to analyse and nothing to change; it is the
hollow-run condition EC-040 exists to forbid, arriving by a different door.

The plan half-anticipated this: §11 already said *"S3 finds no material ambiguity **if** DF-001 is disposed of
first."* It has been disposed of. The conditional came true.

**Owner Decision I supplies the replacement.** The subject is FR-URL-016's **per-creator aggregate redirect tier**
(PVT-014) — the noisy-neighbour control. The baseline deliberately builds two of the three rate-limit tiers and defers
this one, so the before-state is the requirement's own multi-link case passing unthrottled. Her confirmation:

> noisy neighbour sounds good, let us keep things going here

**Two earlier candidates were tried and set aside, and both are recorded in §11 rather than quietly dropped**:
analytics loss under concurrent load (voided by its own resolution) and archival retention (withdrawn when Decision H
simplified retention to indefinite retention with a documented production recommendation). Custom aliases were
considered and rejected as excluded scope (EX-002, AS-002).

**Finding H5 is still closed**, but by **CR-017** rather than by the scenario: the specification now states the
retention posture honestly and records what production would do, which is verifiable, where an unimplemented binding
target was not.

## Owner decision recorded — Decision I

> noisy neighbour sounds good, let us keep things going here

The design this implements:

| Element | Resolution |
|---|---|
| Baseline build | **Builds two of the three rate-limit tiers** — per-creator creation (PVT-012) and per-code redirect (PVT-013) — and **deliberately defers the per-creator aggregate redirect tier** (PVT-014) |
| FR-URL-016 | Remains **binding in full**. The deferral is disclosed in the baseline-omissions record and satisfied at release readiness by the brownfield run's evidence |
| Before-state | Genuine and captured **before** the impact analysis: the requirement's **own** multi-link case — traffic spread across several links, each under the per-code limit — passes **unthrottled** |
| The brownfield run | Implements the aggregate tier under governance, with an impact analysis that has real content because aggregate counting means resolving code → owning creator **inside a public, anonymous hot path** |
| After-state | The same traffic throttled; the response names the tier **without naming the creator**; the per-code tier unregressed; redirect latency re-measured against PVT-001 |
| Gate class | **Security-sensitive** — an abuse control, which is what §5's security-sensitive class covers. The destructive/irreversible question is moot |
| ~~Live-demo path~~ | **STRUCK under Decision F.** One governed run per scenario, evidence committed, nothing beyond what the assessment asks for |

**Why this subject is the strongest of the three candidates**: it is inside already-approved scope, so nothing is
invented; the gap is real, because the tier genuinely is not built; and the impact analysis is not a formality — the
redirect path is public and anonymous by requirement, so counting per creator forces a design decision with a latency
consequence and a disclosure consequence, both of which the requirement itself already constrains.

## Owner approval — 2026-09-20

Approved by **Pravallika Veeravalli**: *"go ahead."*

Her approval followed a three-item list and carries all three, recorded as relayed:

1. **The package as revised**, including the §11 brownfield wording.
2. **PVT-010 explicitly withdrawn** — the binding 30/90-day deletion target is formally retired and replaced by the
   documented production recommendation, **by her explicit decision, not by implication**.
3. **Policy set `1.1.0`**, on the ground she herself set for the API contract: **version discipline binds from first
   real use**, and no run has ever evaluated `1.0.0`.

## Applied edits

### Edit 1 — §11 DS-B block replaced (`plan.md:578-597`)

**OLD** — the entire `### DS-B — Brownfield` block, from *"**Input**: a change against existing shortener code —
proposed: *"Redirect analytics must not lose events under concurrent load""* through *"**Terminal outcome**:
`COMPLETED`."*

**NEW**

```
### DS-B — Brownfield

- **Subject provenance**: **neither the assignment nor the interviewer guide names a brownfield subject.** The owner
  verified that before choosing, so the subject is ours to select — and it is selected to be something the baseline
  genuinely lacks, **from inside already-approved scope** rather than invented for the demonstration.
- **Input**: a change against existing shortener code — **"A creator's redirect traffic must be limited in aggregate
  across all their links, not only per link."** This is FR-URL-016's third tier — **PVT-014, 3,000 requests/minute per
  creator aggregated** — the noisy-neighbour control.
- **Why this subject**: the baseline deliberately builds **two of the three** rate-limit tiers — per-creator creation
  (PVT-012) and per-code redirect (PVT-013) — and **defers the per-creator aggregate redirect tier**. FR-URL-016
  remains **binding in full**; the deferral is disclosed in the baseline-omissions record and is satisfied at release
  readiness by this run's evidence.
- **Why it is a genuine brownfield change rather than a contrivance**: the redirect path is **public and anonymous** by
  requirement (FR-URL-018), so counting traffic per *creator* means resolving code → owning creator **inside the hot
  path**. That is a real design question with real consequences, in components that already exist, against a latency
  budget that is already a binding target.
- **Interpretation**: S2 normalizes to one functional requirement (the third tier) plus its non-functional latency
  constraint; S3 finds no material ambiguity — FR-URL-016 already states the tier, its limit and its negative criteria,
  so the absence of ambiguity is genuine rather than suppressed.
- **Decomposition**: impact analysis precedes any code change.
- **Path**: as DS-A, with S6 producing the **seven-dimension impact analysis** whose timestamp **precedes** the first
  modification, and with an injected transient fault in S7 exercising retry and a compensation case exercising the
  register.
- **The impact analysis has real content**, which is what makes the seven dimensions worth a reviewer's time:
  - **Impacted components and interfaces**: the redirect controller and the rate-limit module; code → creator
    resolution added to the resolution path.
  - **Impacted data flows**: the ownership lookup enters the hot path; counters keyed per creator as well as per code.
  - **Latency**: the added lookup sits inside PVT-001's redirect budget and must be **measured, not assumed**.
  - **Limiter failure posture**: when the counter store is unavailable, does the tier fail **open** (serve, unlimited)
    or **closed** (throttle)? A decision with a security consequence, taken explicitly rather than by default.
  - **Disclosure**: the throttled response must name the tier that was exceeded **without disclosing the owning
    creator to a public follower** — FR-URL-016's own negative criterion.
  - **Impacted tests**: the per-code tier's tests are the regression surface; the multi-link aggregate case is the new
    one.
  - **Documentation and rollout/rollback**: the threat model's noisy-neighbour accepted trade-off (NFR-SEC-003), and
    the tier being disableable without redeploying the redirect path.
- **Approvals**: architecture; **security-sensitive** — this is an abuse control, which is precisely what §5's
  security-sensitive class covers; release readiness.
- **Failure paths**: transient `UNAVAILABLE` → two-vote retry → success; one irreversible effect → compensation,
  labelled as compensation.
- **Validation**: **before-state** — FR-URL-016's own multi-link case: traffic spread across several links, each
  staying **under** PVT-013, passes **unthrottled** in the baseline, captured as evidence **before** the impact
  analysis. **After-state** — the same traffic is throttled and the response **names the aggregate tier** without
  naming the creator. The per-code and per-creator-creation tiers are proven **unregressed**. Redirect latency is
  **re-measured** against PVT-001 with the ownership lookup in place.
- **Evidence**: impact analysis with ordering proof, retry two-signature records, compensation record, before/after
  throttling results, the unregressed per-code tier, and the re-measured latency.
- **Terminal outcome**: `COMPLETED`.
- **Superseded candidate subjects, all retained for provenance** — recorded because the path to this subject is part of
  the reasoning, and hiding it would make the final choice look easier than it was:
  1. *"Redirect analytics must not lose events under concurrent load."* **Voided by its own resolution** — ADR-014
     decided it and Slice 3 (T050) implements it, so by Phase 8 there would have been no defect left to analyse.
  2. *Archival retention of records past their bounds.* **Withdrawn** when the owner simplified retention to
     indefinite retention with a documented production recommendation (CR-017, Decision H): with no archival job in
     scope, the subject had nothing to implement.
  3. *Custom short aliases.* **Rejected as out of scope** — EX-002 and AS-002 exclude vanity codes, so building one
     would have expanded approved scope rather than filled a gap inside it.
```

### Edit 2 — §14 slice table, Slice 7 and a new note (`plan.md:751-753`)

**OLD**

```
| 7 Observability | Audit events, correlation IDs, metrics, `failure_event` capture, MTTR calculation | **Day 3 AM** | Yes |
```

**NEW**

```
| 7 Observability | Audit events, correlation IDs, metrics, `failure_event` capture, MTTR calculation. **No retention work: this demonstration retains everything indefinitely** (NFR-AUD-003, CR-017) | **Day 3 AM** | Yes |
```

and appended beneath the slice table:

```
**Deliberate baseline omission.** Slice 3 builds **two of the three** rate-limit tiers — per-creator creation
(PVT-012) and per-code redirect (PVT-013) — and **defers the per-creator aggregate redirect tier** (PVT-014). That
deferred tier is **the brownfield scenario subject**, implemented under governance in Slice 8.

This is a scheduled omission with a named closure, not deferred scope: **FR-URL-016 is binding in full and is satisfied
at release readiness by the brownfield run evidence.** If that run does not happen, the omission becomes a real gap and
release readiness must report it as one — it may not be relabelled as a design choice after the fact.
```

### Edit 3 — §14 backlog, explicit exclusion (`plan.md:771-774`)

Appended to the backlog paragraph:

```
**The aggregate redirect tier is NOT a backlog item.** It is deliberately deferred from the baseline and implemented
by the brownfield run in Slice 8. Listing it as backlog would be exactly the artifacts-disagreeing defect CHK228 exists
to catch, and would let a scheduled omission read as an accepted one.

**Retention is not a backlog item either, for a different reason**: there is no retention work at all. This
demonstration retains every record indefinitely, and production archival is a **recorded recommendation** rather than
deferred scope (NFR-AUD-003, CR-017, Decision H).
```

### Edit 4 — §3 per-node table, `Timeout & retry` column bound to PVT-016 as **escalation thresholds**

Per-row replacement of the qualitative timeout with **PVT-016**'s thresholds (added to `spec.md` by CR-010 under owner
**Decision A**). Closes **M3**. These are **not kill-timers**: a breach escalates to the human with elapsed-versus-expected
and last-observed activity, offering **keep waiting** or **kill the node**, and silence suspends. The column header itself
becomes `Threshold & retry`.

| Node | OLD timeout text | NEW timeout text |
|---|---|---|
| S1 | `Short; `INTERNAL` permanent` | `**PVT-016: 5 s**; `INTERNAL` permanent` |
| S2 | `PVT-007; `TIMEOUT` retryable (idempotent)` | `**PVT-016: 120 s**; PVT-007 attempts; `TIMEOUT` retryable (idempotent)` |
| S3 | `PVT-007; retryable` | `**PVT-016: 120 s**; PVT-007 attempts; retryable` |
| S4 | `Gate wait PVT-006 → `SAFE_STOP`; no retry` | `**No execution threshold** (PVT-016: N/A) — already waiting on a human; its threshold *is* the gate wait PVT-006 → `SAFE_STOP`; no retry` |
| S5 | `PVT-007; retryable` | `**PVT-016: 180 s**; PVT-007 attempts; retryable` |
| S6 | `PVT-007; retryable` | `**PVT-016: 300 s**; PVT-007 attempts; retryable` |
| S7 | `PVT-007; `TIMEOUT` **not** retryable (non-idempotent git effect)` | `**PVT-016: 600 s per node**; PVT-007 attempts; `TIMEOUT` **not** retryable (non-idempotent git effect)` |
| S8 | `Long timeout; retry only on `UNAVAILABLE`` | `**PVT-016: 1800 s** threshold — the real suite plus Testcontainers startup; retry only on `UNAVAILABLE`` |
| S9 | `PVT-007; retryable` | `**PVT-016: 180 s**; PVT-007 attempts; retryable` |
| S10 | `Retry on `UNAVAILABLE` only` | `**PVT-016: 600 s** — dominated by the dependency-vulnerability scan; retry on `UNAVAILABLE` only` |
| S11 | `Gate wait PVT-006 → `SAFE_STOP`` | `**PVT-016: 30 s** evaluation, then gate wait PVT-006 → `SAFE_STOP`` |
| S12 | `Short` | `**PVT-016: 60 s**` |

and the table's preamble sentence:

**OLD** `default timeout is per-stage and classified per CL-006 rule 4.`
**NEW** `per-node elapsed-time **escalation thresholds** are **PVT-016**'s schedule. Breaching one does not fail the
node: it **escalates to the human** with elapsed-versus-expected and last-observed activity, offering **keep waiting**
or **kill the node** (FR-ORC-014 rule 6). A kill fails the node by overrun, after which CL-006 rule 4 and the node's
design-time idempotency declaration decide retry eligibility — **the threshold sets duration, never eligibility** — and
an unanswered escalation follows the gate-wait deadline to suspension.`

### Edit 5 — §6 Timeout row, recast as threshold escalation (`plan.md:387`)

**OLD**

```
| Timeout | Per-stage. Retryable **only** where the stage's design-time contract declares the effect idempotent/repeat-safe — never executor self-certification (S7 is explicitly not retryable on timeout). |
```

**NEW**

```
| Timeout / overrun | Per-node **escalation thresholds** are **PVT-016**'s schedule (§3). A breach **asks the human** — keep waiting, or kill the node — and never kills on the orchestrator's own authority (FR-ORC-014 rule 6). Liveness comes from existing state-transition, trace and audit records; **no watchdog component is introduced**. A kill fails the node by overrun; retry is then gated exactly as before — retryable **only** where the design-time contract declares the effect idempotent/repeat-safe, never executor self-certification (S7 is explicitly not retryable). Duration and eligibility stay separate decisions: PVT-016 sets the first, CL-006 rule 4 the second. An unanswered escalation suspends. |
```

### Edit 6 — §3 graph topology, node keys and the S7 join node (`plan.md:276-281`)

**OLD**

```
                                                    ┌──── FAN-OUT per task ────┐
                                                    ▼         ▼         ▼
                                              S7.1 Impl   S7.2 Impl  S7.n Impl
                                                    └──── JOIN (all must succeed) ──┐
```

**NEW**

```
                                                    ┌──── FAN-OUT per task ────┐
                                                    ▼         ▼         ▼
                                              S7.1 Impl   S7.2 Impl  S7.n Impl
                                                    └──► S7.join (ALL must succeed) ──┐
```

and appended beneath the diagram:

```
**Node identity.** Nodes are keyed by string, not by stage number (owner Decision 2, CR-011/CR-013): `"S1".."S12"`
for a singleton or fan-out-parent stage, `"S7.1".."S7.n"` for fan-out children, `"S7.join"` for the join that gates
S8. A freshly materialised run holds thirteen nodes — eleven singletons, the S7 parent, and its join — and children
are appended when S6 yields tasks, so **there is no fixed node count per run**. Edges address node keys, which is
what makes a replanned instance's topology queryable and lets a replan invalidate one fan-out child while leaving
its siblings intact (FR-ORC-019). The S9∥S10 pair needs no join node: S11 simply carries two incoming `ALL` edges.
```

### Edit 7 — Constitution Check, Principle III row (`plan.md:74`)

Closes **M2**, an off-by-one that T064's own guard calls a silent skip: *"a gate class present in the plan but absent
from the enum."* The count is **ten**, not nine: the plan's table already held nine, and owner **Decision A** adds the
node-overrun class (Edit 13).

**OLD** `| III. Human Governance | **PASS** | §5 defines 8 mandatory gate classes with the five outcomes;`
**NEW** `| III. Human Governance | **PASS** | §5 defines **10** mandatory gate classes with the five outcomes;`

### Edit 8 — §12 heading and lede (`plan.md:620-623`)

Closes **M4**.

**OLD**

```
## 12. Technology Decisions — PROPOSED, pending ADR gate

Presented as candidate ADRs. **None is selected.** `DECISION-LOG` entry #1's Java intent is treated as
a candidate, not settled, per Gate 1's explicit carry-forward.
```

**NEW**

```
## 12. Technology Decisions — the evaluation that produced ADR-001..005 *(historical record)*

**All of these were decided at Gate 4, 2026-09-20**, and the decisions of record are ADR-001..ADR-014 in
`docs/governance/adr/`, not this section. It is retained **unaltered below** as the evaluation the ADRs came from —
the options, criteria and counter-cases as they stood before the gate — because an ADR that cites its evaluation is
weaker if the evaluation is edited afterwards to agree with it.

Read the section in that light: where the text below says *"recommended"* or *"pending"*, it is describing the state
at authoring time. **Nothing here should be read as an open question.**
```

### Edit 9 — Post-Design Re-Check principle count (`plan.md:864`)

Closes **L4**.

**OLD** `**all twelve principles remain `PASS`.**`
**NEW** `**all eleven principles remain `PASS`, as does the CN-002 technology-neutrality row** (twelve rows, eleven principles).`

### Edit 10 — §1 Actors and trust boundaries, Decision 3 (`plan.md:101-131`)

Actors table — three rows gain their boundary:

| Actor | OLD Authority | NEW Authority |
|---|---|---|
| Engineer | `Submit requirements, create and inspect runs, trigger replanning.` | `Submit requirements, create and inspect runs, trigger replanning. Reached under the **machine-access** boundary — **never a creator credential** (CN-012).` |
| Reviewer / Approver | `Decide gates. Cannot be substituted by an agent at any autonomy setting.` | `Decide gates. Cannot be substituted by an agent at any autonomy setting. Records decisions through the governance surface under the machine-access boundary; holds **no** creator credential (CN-012).` |
| Release owner | `Release readiness. Never self-certified by an agent (FR-ORC-025).` | `Release readiness. Never self-certified by an agent (FR-ORC-025). Same boundary as Reviewer (CN-012).` |

Trust boundary 2 is scoped, and a sixth is added:

**OLD** `2. **Creator client → creation/analytics API.** API key presented; validated against stored hash.`
**NEW** `2. **Creator client → creation/analytics API.** API key presented; validated against stored hash. **This
boundary covers the shortener only.** A creator credential grants nothing in the control plane (CN-012).`

**NEW boundary 6**

```
6. **Operator/engineer/reviewer shell → orchestrator governance surfaces.** Run creation, run inspection and
   gate-decision recording carry **no credential**: the ability to reach the process *is* the boundary, the same one
   drawn for creator provisioning (FR-URL-019). The two identity models are provably separate — an architecture test
   asserts no control-plane package reads a creator credential — and the boundary itself is enforced by deployment,
   not by code. Stated in `docs/LIMITATIONS.md` in exactly those terms: **separation is proven, reachability is
   not.** A reviewer credential class is the production answer and is recorded as future work (CN-012).
```

### Edit 11 — §2 versioned deliverables, contract versions (`plan.md:200-202`)

`contracts/openapi.yaml` and `contracts/workflow-state.schema.json` version cells → **2.0.0 (CR-013)**; the
workflow-state row's Validation cell gains *"…and the fan-out node model; node-key uniqueness and the join
invariant are asserted by T074's test, which JSON Schema cannot express."*

### Edit 12 — §4 lineage table, node-keyed edges (`plan.md:339`)

**OLD** `| Workflow instance state | `workflow_run`, `stage_node`, `dependency_edge` | Current state + full transition history |`
**NEW** `| Workflow instance state | `workflow_run`, `stage_node` (node-keyed), `dependency_edge` (node-to-node) | Current state + full transition history. Node keys, not stage numbers, so a replanned topology and a per-task fan-out are both queryable (CR-011/CR-013) |`

and a new row appended, recording the *absence* rather than a component:

```
| Retention | **No tables.** Every record is retained indefinitely; there is no purge, archival or deletion path anywhere (NFR-AUD-003, Decision H). Production archival is a recorded recommendation, not a component |
```

### Edit 13 — §5 gains a tenth gate class: node overrun

Owner **Decision A** makes a breached escalation threshold a human decision point with a deadline and a silence
path. That is what a gate is, so it needs a class — otherwise it cannot inherit the deadline-and-suspension
machinery the decision depends on.

**NEW row**, inserted in §5's table before the release-readiness row:

```
| **Node overrun** | A node's PVT-016 escalation threshold is breached | That node only; siblings and unaffected paths continue | five standard, plus two named choices: **keep waiting** (re-arm the threshold) / **kill the node** (fail by overrun, then normal classification and idempotency-gated retry) |
```

and appended to §5's **Uniform semantics** paragraph:

```
The **node-overrun** class is the one gate the orchestrator raises about itself rather than about an artifact. It
exists because a slow node is not a failed node: the orchestrator can observe that expected progress has not
happened, but only a human can decide whether that means *wait* or *stop*. Killing on a timer would be the
orchestrator deciding, which is the authority boundary this whole design keeps on the other side of the line.
```

*This edit and Edit 7's count move together: the plan's table held nine classes, this makes ten, and T064's enum
test asserts the count against the table so the two cannot drift.*

### Edit 14 — `policy-set-1.0.0` → `policy-set-1.1.0`, with `POL-CHG-003`

Owner **Decision D** requires that the demonstrations' policy checks *"can actually enforce"* the major-version rule.
They cannot today: `POL-CHG-001` checks only that a contract or schema change **has a change-control record**, so a
breaking change with a record and no major bump passes.

**NEW policy row** in §9's table:

```
| `POL-CHG-003` | Change control | **Mandatory** | A contract or schema change classified MAJOR carries a new major version → PASS/FAIL. Evaluated against the served baseline recorded in `contracts/README.md`; `NOT-APPLICABLE` before first service, **armed during scenario demonstrations** (CR-013 Edit 15) |
```

**`POL-AUD-002` REMOVED** in the same edit, under owner **Decision H** (reasoning owned by **CR-017** Edit 9):

```
| `POL-AUD-002` | Audit retention | Advisory | Retention configured within declared bounds → PASS/FAIL |
```

With no retention path and no configured bounds, this check can never evaluate meaningfully, and Decision H is explicit
that a policy which can never meaningfully evaluate must not stay. It is removed rather than left returning
`NOT-APPLICABLE` forever, which would be noise in every run's policy record.

**Net result**: `policy-set-1.1.0` holds **twelve checks, all mandatory** — eleven surviving mandatory checks plus
`POL-CHG-003`. `POL-AUD-002` was the set's **only advisory check**, so the mandatory/advisory split remains defined but
has no advisory instance. That is honest, and better than keeping a check alive to populate a category.

and the version bumped in three places:

| Location | OLD | NEW |
|---|---|---|
| Front matter | `**Policy version evaluated**: `policy-set-1.0.0` (defined in §9 of this plan)` | `**Policy version evaluated**: `policy-set-1.1.0` (defined in §9 of this plan)` |
| §9 heading | `### `policy-set-1.0.0`` | `### `policy-set-1.1.0`` |
| §9 closing line | `Every run records the policy set version it evaluated.` | `Every run records the policy set version it evaluated. **v1.1.0 adds `POL-CHG-003`** (CR-013/CR-015); `v1.0.0` remains the version of record for any run executed before this amendment — an amended policy set is never applied retroactively to claim compliance (Constitution §Amendment procedure). |

and the Constitution Check's Principle VI row:

**OLD** `| VI. Compliance and Change-Control | **PASS** | §9 defines `policy-set-1.0.0` across 7 domains with mandatory/advisory split, four outcomes, exception workflow, and the 9 release-blocking conditions. |`
**NEW** `| VI. Compliance and Change-Control | **PASS** | §9 defines `policy-set-1.1.0` across 7 domains, four outcomes, exception workflow, and the 9 release-blocking conditions. **Twelve checks, all mandatory**: `POL-CHG-003` added so the major-version rule is enforced rather than described (CR-013), and `POL-AUD-002` removed because with no retention path it could never meaningfully evaluate (CR-017). The mandatory/advisory split remains defined and currently has no advisory instance. |`

**Retroactivity is the point of the closing line.** A run that recorded `policy-set-1.0.0` evaluated twelve checks,
and relabelling it would be exactly the amendment-applied-retroactively move the constitution forbids.

### Edit 15 — two further `policy-set-1.0.0` occurrences, **found by verification during application**

Edit 14 enumerated three locations for the version bump. **V25 — *"the bump is complete, not partial"* — failed on
application, returning two more**:

| Location | OLD | NEW |
|---|---|---|
| §Constitution Check preamble | `Enumerated against **v1.1.0**, policy version `policy-set-1.0.0`, per the Amendment 001 carry-forward.` | `…policy version `policy-set-1.1.0`…` |
| §Project Structure comment | `├── policy/              # policy-set-1.0.0 evaluation, exceptions, release readiness` | `# policy-set-1.1.0 evaluation, exceptions, release readiness` |

**Applied, and the failure is the point.** Edit 14's list was narrower than V25's requirement, and a partial version
bump is precisely the defect that makes a version number untrustworthy: a run could cite §9's `1.1.0` while the
constitution check next to it claimed `1.0.0` was evaluated. **The check caught what the drafting missed** — which is
the second time in this package that has happened, and the reason the per-edit verification table exists at all.

The `v1.0.0` reference in §9's closing line is **deliberately retained**: it states that runs executed before this
amendment keep `1.0.0` as their version of record, which is the anti-retroactivity rule.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR.** Edit 1 replaces a scenario subject — a change of obligation. Edit 2 records a deliberate baseline omission with a scheduled closure. Edits 4–6, 10–12 and 13 add obligations (escalation thresholds, node identity, a trust boundary, a tenth gate class). Edit 14 bumps the policy set, which every run records. Edits 7–9 are PATCH corrections. Nothing is removed; the timebox, the checkpoints, the stop conditions and Gate 5's time-attitude amendment are **untouched**. |
| **Backward-compatibility impact** | None. |
| **Affected consumers** | `tasks.md` T055, T135, T136, T137, T128, T147, T064, T074, T076, T086, T067, T097, T098 and thirteen new tasks (**CR-016**). `quickstart.md` §4 (**CR-012**). `spec.md` PVT-016 and rule 6 (**CR-010**), CN-012 (**CR-014**), retention posture (**CR-017**). |
| **Affected tests** | **New:** the archival move with the run-termination clock, EC-043 across four run states, EC-044 crash safety, before/after counts in live tables **and** archive (CR-016); threshold-breach escalation for both choices plus a silence case; the tenth gate class in T064's enum test; `POL-CHG-003` in T098's completeness test; the credential-boundary architecture test. **Retired:** the analytics-regression before-state, which T050 makes unreachable; **and the replay procedure, struck under Decision F.** |
| **Affected documentation** | `docs/LIMITATIONS.md` gains the deferred-tier record, the credential-boundary asymmetry, Decision A's stall-rather-than-self-heal consequence, and **the production archival recommendation plus unbounded table growth** (CR-017); `docs/delivery/*` registers reflect the Slice 3/8 split. **No replay documentation** (Decision F). |
| **Rollout / migration** | None. The aggregate tier is application code over counters the per-code tier already needs; no schema migration, and no retention tables at all. |
| **Required approval** | Human owner. The §11 wording is reserved for her confirmation at re-review, as is the re-proposed gate class (§Re-proposed gate class) and the policy-set bump. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

| # | Edit | Verification command (from repo root, `P=specs/001-agentic-sdlc-url-shortener/plan.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'must not lose events under concurrent load' $P` | `1` — **only** inside the retained "Superseded subject" note | EXECUTED: 1 hit, line 656, inside the "Voided by its own resolution" provenance note. **HOLDS** |
| V2 | 1 | `grep -c 'limited in aggregate' $P` | `1` — the noisy-neighbour subject is stated | **EXECUTED: 1** — HOLDS |
| V3 | 1 | `grep -c 'Superseded subject, retained for provenance' $P` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V4 | 2 | `grep -c 'defers the per-creator aggregate redirect tier' $P` | `≥ 1` | **EXECUTED: 1** — HOLDS |
| V5 | 2 | `grep -c 'Deliberate baseline omission' $P` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V6 | 3 | `grep -ci 'a backlog item' $P` — **case-insensitive** | `2` — the deferred tier (*"is NOT a backlog item"*) and retention (*"is not a backlog item either"*), for different reasons | **EXECUTED: 2** — HOLDS |
| V7 | 4 | `grep -c 'PVT-016' $P` | `≥ 13` — twelve node rows plus the preamble | **EXECUTED: 16** — HOLDS |
| V8 | 4 | `grep -c 'Long timeout' $P` | `0` | **EXECUTED: 0** — HOLDS |
| V9 | 5 | `grep -c 'schedule sets duration, never eligibility' $P` | `≥ 1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V10 | 6 | `grep -c 'S7.join' $P` | `≥ 1` | **EXECUTED: 2** — HOLDS |
| V11 | 6 | `grep -c 'no fixed node count per run' $P` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V12 | 7 | `grep -c '§5 defines 8 mandatory' $P` | `0` | **EXECUTED: 0** — HOLDS |
| V12b | 7 | `grep -c 'defines \*\*10\*\* mandatory gate classes' $P` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V13 | 7, 13 | `python3 -c "import re;t=open('$P').read();s=t.split('## 5. Human-in-the-Loop')[1].split('**Uniform semantics**')[0];print(s.count('five standard'))"` | `10` — the table and the count agree | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V14 | 8 | `grep -c 'None is selected' $P` | `0` | **EXECUTED: 0** — HOLDS |
| V15 | 8 | `grep -c 'pending ADR gate' $P` | `0` | **EXECUTED: 0** — HOLDS |
| V16 | 9 | `grep -c 'all twelve principles' $P` | `0` | **EXECUTED: 0** — HOLDS |
| V17 | 10 | `grep -c 'CN-012' $P` | `≥ 4` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V18 | 10 | `grep -c 'separation is proven, reachability is' $P` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V19 | 11 | `grep -c '2.0.0' $P` | `≥ 2` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V20 | 12 | `grep -c 'retention_policy' $P` | `0` — no retention tables anywhere (Decision H) | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V21 | all | `grep -c 'ESCALATE' $P` | `≥ 3` — Gate 5's time-attitude amendment **untouched** | **EXECUTED: 4** — HOLDS |
| V22 | all | `grep -c 'non-waivable' $P` | `≥ 3` — the fabrication halt **untouched** | **EXECUTED: 3** — HOLDS |
| V23 | 13 | `grep -c 'Node overrun' $P` | `1` | **EXECUTED: 1** — HOLDS |
| V24 | 13 | `grep -c 'keep waiting' $P` | `≥ 2` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V25 | 14 | `grep -c 'policy-set-1.0.0' $P` | `0` — the bump is complete, not partial | **EXECUTED: 0** — HOLDS |
| V26 | 14 | `grep -c 'POL-CHG-003' $P` | `≥ 2` | **EXECUTED: 3** — HOLDS |
| V27 | 14 | `grep -c 'never applied retroactively' $P` | `1` — an amended policy set does not relabel past runs | **EXECUTED: 1** — HOLDS |
| V28 | Decision F | `grep -c -E 'replay\|pre-fix commit' $P` | `0` — no replay content anywhere | **EXECUTED: 0** — HOLDS |
| V29 | Decision H | `grep -c -E 'archive\|archival' $P` | `2` — both inside the production-recommendation wording, never a component | EXECUTED: 4 hits (lines 369, 658, 659, 859), every one describing the **absence** of archival or the production recommendation. None describes a component. **HOLDS** |
| V30 | Decision H | `grep -c '^| `POL-AUD-002`' $P` — the **table row** only | `0` — the row is removed, not left permanently not-applicable. Two prose references survive **deliberately**: the Constitution Check row and §9's closing line, both of which state *why* it was removed | **EXECUTED: 0** — HOLDS |
| V31 | Decision H | `grep -c 'all mandatory' $P` | `≥ 1` — twelve checks, no advisory instance | **EXECUTED: 1** — HOLDS |
| V32 | Decision I | `grep -c 'PVT-014' $P` | `≥ 2` | **EXECUTED: 4** — HOLDS |
| V33 | Decision I | `grep -c 'security-sensitive' $P` | `≥ 2` — §11 and §5 agree on the class | **EXECUTED: 2** — HOLDS |


**Two check corrections recorded, 2026-09-20.** V6 was case-sensitive and matched only one of the two statements —
the other reads *"is not a backlog item either"*. V30 was a whole-file grep and matched the two places that
**explain the removal**, which are deliberate. Both are now precise. Neither edit was wrong; both checks were.

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V21 and V22 are regression checks on Gate 5's amendment**: this CR edits §14, which is where that
amendment lives, and the failure mode is a §14 edit quietly undoing it.

## Gate class — owner **Decision E**, settled under the new subject

Draft 1 gave the brownfield task a **destructive/irreversible** gate while `plan.md` §11 said **security-sensitive** —
a disagreement the owner caught. Under Decision I the question resolves cleanly: **security-sensitive**, in both places.

Grounds:

1. **The change is an abuse control.** A rate limit exists to stop a caller consuming capacity that is not theirs.
   §5's security-sensitive class covers changes touching auth, the scheme list, credentials and telemetry redaction —
   abuse controls are the same family, and this one has a **disclosure** requirement inside it: the throttled response
   must name the tier without naming the creator to a public follower.
2. **Nothing is destroyed, so the destructive class does not apply** — and under Decision H there is no retention
   deletion anywhere in the system either, so that question is moot rather than deferred.
3. **The risk is a wrong boundary and a leaked identity, not lost data.** Both are security properties.

**No counter-case survives.** At revision 2 the archival subject had a genuine argument for the destructive class,
because it removed rows from live tables. The aggregate-tier subject has no such argument: it adds a counter and a
lookup. The plan block and the gate task now name the same class, which was the real defect the owner found.

## Open questions for the owner

1. **The §11 wording** — drafted above in full, reserved for her confirmation.
2. **The policy-set bump to `1.1.0`**, which now both **adds** `POL-CHG-003` and **removes** `POL-AUD-002`. Adding is a
   MINOR-shaped change; removing a check is MAJOR-shaped in character. I classified the set as **1.1.0** on the ground
   that **no run has ever evaluated `policy-set-1.0.0`** — nothing is implemented — so no audit trail is invalidated and
   nothing is relabelled retroactively. **If she would rather call it `2.0.0`, that is defensible** and costs only the
   version string.
3. **Revision 2's two archive questions are moot** — Decision H removed the archive, so the audit-table grants stay
   exactly as approved and there is no archive location to choose.

## Residual risk

**The deliberate omission is a real risk and must be named as one.** Between Slice 7 and DS-B's completion, the
build genuinely does not enforce PVT-010. If DS-B is cut, deferred, or fails, the system ships without retention
enforcement, and the omission's status flips from *scheduled* to *undisclosed gap* at the moment release readiness
is evaluated.

Three controls: Edit 2 states that release readiness must report it as a gap rather than relabel it; `POL-AUD-002`
evaluates the configured bounds from Slice 7 so the *declaration* is checked even before the enforcement exists; and
DS-B sits on the critical path (§14 Slice 8) rather than in the optional tail. Recorded here because a scheduled
omission that nobody is watching is indistinguishable from an oversight, and the difference is the schedule.
