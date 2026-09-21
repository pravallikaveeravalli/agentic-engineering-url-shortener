# Change Request CR-014 — Credential-Domain Separation and the Orchestrator Governance Surface

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, from `/speckit-analyze` findings **H9** and **H7** |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"go ahead."* |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/spec.md` |
| Governing constitution | v1.1.0; `POL-CHG-001`. Touches **Principle V** (trust boundaries documented explicitly) and **Principle III** (authority) |
| Source decision | Owner **Decision 3** on the analyze findings, 2026-09-20 |
| Application order | **Second in the package, after CR-010.** Both edit `spec.md`; the locations are disjoint but the verification counts are not, so order matters. |

## Why this change exists

`/speckit-analyze` found the one existing control-plane operation secured with `creatorApiKey` — the link-creator
credential. CL-001's whole decision was that **creators and followers are two populations that must be treated
differently**; the orchestrator's audience is a third, and it had been quietly folded into the first. Nothing
provisions an engineer or a reviewer identity (FR-URL-019 provisions *link creators*), and no run-scoped
authorization exists anywhere, so the design implied either that any creator may read any run or that a reviewer
must hold link-creation keys.

The specification never stated a rule either way. It has FR-URL-018 scoping creator authentication to *creation and
analytics*, and it has orchestration requirements that name no credential at all — so the gap was an absence, not a
contradiction, which is exactly the kind of thing that gets resolved by whoever writes the code first.

## Owner decision recorded — Decision 3, verbatim

> I don't think creators matter in orchestrator it is a url shortner concept, don't leak those into orchestrator.

## Owner approval — 2026-09-20

Approved by **Pravallika Veeravalli**: *"go ahead."*

Her approval followed a three-item list and carries all three, recorded as relayed:

1. **The package as revised**, including the §11 brownfield wording.
2. **PVT-010 explicitly withdrawn** — the binding 30/90-day deletion target is formally retired and replaced by the
   documented production recommendation, **by her explicit decision, not by implication**.
3. **Policy set `1.1.0`**, on the ground she herself set for the API contract: **version discipline binds from first
   real use**, and no run has ever evaluated `1.0.0`.

## Applied edits

### Edit 1 — new **CN-012** appended to §Constraints (after CN-011, `spec.md:1327`)

**NEW**

```
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
  `docs/LIMITATIONS.md`.
  *(Owner Decision 3 on the `/speckit-analyze` findings, 2026-09-20.)*
```

### Edit 2 — FR-ORC-007, the submission surface made explicit (`spec.md:604-608`)

**OLD**

```
- **FR-ORC-007** — *Workflow creation.* **[Confirmed]** An engineer MUST be able to create a
  workflow run from a submitted requirement and receive a durable run identifier.
  **Accept**: the run identifier appears in all subsequent logs, metrics, evidence, and state.
  **Reject (negative)**: work MUST NOT be executed outside a created, identified run.
  **Evidence**: run creation tests; identifier propagation check.
```

**NEW**

```
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
```

*Ground for the surface clause: this requirement previously had zero tasks and zero contract operations
(analyze finding H7) precisely because nothing in its text obliged an externally addressable surface to exist.*

### Edit 3 — FR-ORC-008, credential exclusion (`spec.md:610-616`)

**OLD**

```
  **Reject (negative)**: inspection MUST NOT report a stage as complete whose exit criteria were
  not satisfied.
```

**NEW**

```
  **Reject (negative)**: inspection MUST NOT report a stage as complete whose exit criteria were
  not satisfied; inspection MUST NOT require, consume, or be affected by a creator credential (CN-012).
```

### Edit 4 — FR-ORC-013, the decision-recording surface (`spec.md:668-669`)

Appended to FR-ORC-013's **Accept** list, before its **Reject (negative)**:

```
; the decision-recording surface is declared in the API contract, accepts only the four **submittable** outcomes —
`APPROVED`, `REJECTED`, `CHANGES-REQUESTED`, `ESCALATED` — and **cannot express `TIMED-OUT`**, which is produced
only by deadline expiry, so silence is not merely rejected as an input but is **inexpressible** as one; the surface
requires the decision's repository record path and refuses a decision that exists only in workflow state; and it
sits under the machine-access trust boundary with no creator credential accepted (CN-012)
```

*Ground: making `TIMED-OUT` unsubmittable moves Constitution III's "silence is never approval" from a checked rule
to a structural property of the interface. A rule that cannot be expressed cannot be violated by a caller.*

### Edit 5 — KE-23 Creator, scope closed (`spec.md:998-1000`)

**OLD**

```
- **KE-23 Creator**: a provisioned identity permitted to create links and read its own links'
  analytics. Owns zero or more ShortLinks. Provisioned by operator script, never self-service
  (FR-URL-019).
```

**NEW**

```
- **KE-23 Creator**: a provisioned identity permitted to create links and read its own links'
  analytics — **and nothing else**. Owns zero or more ShortLinks. Provisioned by operator script, never
  self-service (FR-URL-019). A Creator is a **URL-shortener concept only** and has no standing in the
  orchestrator's actor model (CN-012).
```

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR.** CN-012 is a new constraint and Edits 2–4 add obligations: an externally addressable submission surface, an unsubmittable `TIMED-OUT`, and a credential exclusion on three surfaces. Nothing is removed or weakened. |
| **Backward-compatibility impact** | None. No implementation, no consumer. |
| **Affected consumers** | `contracts/openapi.yaml` (**CR-013**, which removes the credential and adds both operations). `plan.md` §1 actors and trust boundaries (**CR-015**). `tasks.md` T063a, T067, T067a, T082a, T147 (**CR-016**). |
| **Affected tests** | **New:** an architecture test asserting no orchestrator package reads a creator credential (T063a), in the shape of T049's port-bypass test — a rule demonstrated to **fail** on a deliberate violation, not merely asserted. Contract conformance on the two new operations (T067a, T082a). An absence test on the submission surface. |
| **Affected documentation** | `docs/LIMITATIONS.md` (T147) must state the unauthenticated-governance-surface posture plainly; `docs/REVIEWER-GUIDE.md` (T128) must tell a reviewer they need host access, not a key. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner. Decision 3 is given; approval here confirms CN-012's exact wording, since a constraint binds every downstream stage. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

| # | Edit | Verification command (from repo root, `S=specs/001-agentic-sdlc-url-shortener/spec.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'CN-012' $S` | `≥ 5` — the constraint plus its four cross-references | **EXECUTED: 6** — HOLDS |
| V2 | 1 | `grep -c 'machine-access trust boundary' $S` | `≥ 2` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V3 | 1 | `grep -c "don't leak those into orchestrator" $S` | **`2`** — the owner's ruling recorded verbatim in **two** places: CN-012's rationale, and CR-014's entry in the §Clarification Log change-control list | **EXECUTED: 2** — HOLDS |
| V4 | 1 | `grep -c 'Alternative considered and not taken' $S` | `1` — the rejected reviewer-credential class is on the record | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V5 | 2 | `grep -c 'addressable submission surface' $S` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V6 | 3 | `grep -c 'affected by a creator credential' $S` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V7 | 4 | `grep -c 'inexpressible' $S` | `1` | **EXECUTED: 1** — HOLDS |
| V8 | 5 | `grep -c 'no standing in the' $S` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V9 | all | `python3 -c "import re,pathlib;t=pathlib.Path('$S').read_text();print(len(set(re.findall(r'CN-\d{3}',t))))"` | `12` — CN-001..CN-012 | **EXECUTED: 12** — HOLDS |
| V10 | all | `grep -c '^- \*\*FR-' $S` | `51` — **unchanged**; this CR adds a constraint, not a requirement | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V11 | cross | `grep -c 'creatorApiKey' specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml` | `3` — scheme + the two shortener operations only, proving **CR-013** landed with this record | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V12 | cross | after CR-010 and this record: `grep -c 'Still open' $S` | `0` — proves CR-010's Edit 5 survived this record's edits to the same file | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |


**Check correction recorded, 2026-09-20.** V3 was first written expecting **1** occurrence of the owner's ruling. On
execution it returned **2**: CN-012's rationale carries it, and so does CR-014's entry in the §Clarification Log,
which CR-017's Edit 8 adds. Both are intended — a constraint states its own grounds, and the change log states what
the change was for. **The edit was correct and the expected count was wrong.** Corrected to 2, which is now the
discriminating value: 1 would mean one of the two records lost the ruling.

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V12 exists because two change requests now edit `spec.md` in sequence**, and the failure mode being
guarded against is the second application reverting or masking the first.

## Residual risk

CN-012 states a boundary that is, in this build, **enforced by deployment rather than by code**: the surfaces are
unauthenticated, so the protection is that nothing untrusted can reach the port. An architecture test can prove no
creator credential leaks across the boundary — it cannot prove the boundary itself holds, because there is no
authentication there to test.

That asymmetry must be described accurately and not dressed up. The honest claim is: *the two identity models are
provably separate, and the orchestrator is provably reachable by anyone who can reach the port.* Both halves go in
`docs/LIMITATIONS.md`. Recording the rejected reviewer-credential class in CN-012 itself is what keeps the second
half from reading as a discovered gap later.
