# Change Request CR-012 — Quickstart: Resolved Findings Stated as Resolved; Executor-Kind Vocabulary; DS-B Subject

| Field | Value |
|---|---|
| Status | **APPROVED and APPLIED** — 2026-09-20. Every verification row executed and holding. |
| Raised by | Executing agent, from `/speckit-analyze` findings **H3**, **L6**, and the brownfield-subject cascade |
| Revision | **Draft 3**, 2026-09-20 — owner **Decision F** struck the replay demo path; owner **Decision H** withdrew archival retention; owner **Decision I** re-pointed the brownfield subject to the noisy-neighbour aggregate redirect tier |
| Approving authority | Pravallika Veeravalli (human owner) — **APPROVED 2026-09-20**, *"go ahead."* |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/quickstart.md` — *"Derived design artifact. Approved indirectly via `plan.md`'s approval… Changes pass through change control."* |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decisions | ADR-014 (DF-001), **CR-003** (DF-002), **CR-004** (DF-003), **CR-001** (executor-kind vocabulary), Gate 4 ADR acceptance (ADR-001), owner **Decisions F, H, I** |
| Application order | Fourth. Independent of CR-010, CR-011, CR-013. Its Edit 4 depends on **CR-015**, which changes DS-B's subject in `plan.md`. |

## Why this change exists

This is **the document a reviewer reads first**, and it currently tells them that three approved decisions are
unsettled.

`quickstart.md` §Honest limitations states that DF-001, DF-002 and DF-003 *"are unresolved at the time of
writing"* and that *"where this guide shows behavior touching them, it reflects the plan's **provisional**
position, not an approved decision."* All three were resolved on 2026-09-20 — by ADR-014, CR-003 and CR-004. A
reviewer following the guide would therefore distrust the redirect class, the analytics model and the retention
boundary, and would be right to, on the artifact's own word.

Its header additionally states that *"exact invocations depend on ADR-001, which is not yet decided (CN-002)"* —
ADR-001 was accepted at Gate 4.

**CR-008 edited this file** for AI transport wording on the same day and did not touch either paragraph. That is
the finding, not an accusation: a CR's scope is its edit list, so staleness outside that list survives untouched
unless something looks for it. `/speckit-analyze` is that something, and this is the first thing it found.

## Owner approval — 2026-09-20

Approved by **Pravallika Veeravalli**: *"go ahead."*

Her approval followed a three-item list and carries all three, recorded as relayed:

1. **The package as revised**, including the §11 brownfield wording.
2. **PVT-010 explicitly withdrawn** — the binding 30/90-day deletion target is formally retired and replaced by the
   documented production recommendation, **by her explicit decision, not by implication**.
3. **Policy set `1.1.0`**, on the ground she herself set for the API contract: **version discipline binds from first
   real use**, and no run has ever evaluated `1.0.0`.

## Applied edits

### Edit 1 — §Honest limitations, third bullet (`quickstart.md:306-308`)

**OLD**

```
- Deferred findings **DF-001** (analytics exactness), **DF-002** (redirect permanence), and **DF-003**
  (retention boundary) are unresolved at the time of writing. Where this guide shows behavior touching
  them, it reflects the plan's **provisional** position, not an approved decision.
```

**NEW**

```
- Deferred findings **DF-001** (analytics exactness), **DF-002** (redirect permanence) and **DF-003** (retention
  boundary) were **all resolved on 2026-09-20** — by ADR-014, CR-003 and CR-004 respectively. The behaviour this
  guide shows reflects those **approved** decisions: the analytics append is synchronous in its own transaction with
  failure isolated and counted (PVT-009 as an observable failure ceiling); and redirects are temporary, never
  permanent. **Retention was then simplified**: this demonstration retains every record indefinitely, and the
  production archival job — including CR-004's run-termination clock rule — is recorded as a **recommendation** rather
  than built (NFR-AUD-003, CR-017). **DF-004** — the run-level retry circuit breaker and expiry notification —
  remains open by design and out of scope.
```

### Edit 2 — header, ADR-001 status (`quickstart.md:9-11`)

**OLD**

```
How a reviewer proves the system works, without reading the source. Commands are shown in the shape
they will take; exact invocations depend on ADR-001, which is not yet decided (CN-002). Nothing here
has been executed — this is the validation design, not a record of results.
```

**NEW**

```
How a reviewer proves the system works, without reading the source. Commands are shown in the shape they will
take; exact invocations follow **ADR-001 — Java 21 + Spring Boot 3, Accepted 2026-09-20** — and are finalised
when **T127** executes this guide end to end on a clean machine. Nothing here has been executed yet: this is the
validation design, not a record of results.
```

### Edit 3 — §4 evidence-bundle labelling (`quickstart.md:260-261`)

**OLD**

```
Each emits an evidence bundle with per-stage **executor mode labels**. Deterministic executions are
labelled as such and never presented as AI work.
```

**NEW**

```
Each emits an evidence bundle carrying a per-node **executor-kind label** — `DETERMINISTIC`, `AI` or `HUMAN`
(FR-ORC-029; "kind", not "mode", since **CR-001/CR-005**). Deterministic executions are labelled as such and never
presented as AI work.
```

### Edit 4 — §4 DS-B row (`quickstart.md:257`)

Follows owner **Decision I**. Depends on CR-015.

**OLD**

```
| **DS-B** | The seven-dimension impact analysis exists and its timestamp **precedes** the first code modification. Retry and compensation records present. |
```

**NEW**

```
| **DS-B** | The subject is the **per-creator aggregate redirect limit** — FR-URL-016's third tier (PVT-014), deliberately deferred from the baseline, which builds only the per-creator creation tier and the per-code redirect tier. Confirm the **before-state** first: traffic spread across several links, each staying **under** the per-code limit, passes **unthrottled**. Then the seven-dimension impact analysis whose timestamp **precedes** the first code modification. Then the governed change: the same traffic is throttled, the response **names the aggregate tier without naming the creator**, the per-code tier is unregressed, and redirect latency is re-measured against PVT-001 with the ownership lookup now in the hot path. Retry and compensation records present. |
```

### Edit 5 — §5 reconstruction questions, retention addition

Appended as a sixth question in the §5 list, because the brownfield scenario makes archival retention a governed
change with its own evidence trail, and a reviewer should be able to reconstruct it from the committed record:

```
6. What did the baseline deliberately omit, which run implemented it, and where is that run's evidence?
```

*The earlier draft asked whether the run could be **replayed**. Struck under owner **Decision F**: the deliverable is
the committed evidence of one governed run per scenario, and nothing beyond what the assessment asks for.*

### Edit 6 — a second stale DF-002 statement, **found by verification during application**

**OLD**

```
→ redirect to the destination. **Note**: status code is provisional pending DF-002.
```

**NEW**

```
→ redirect to the destination. **Note**: the class is **temporary, never permanent** (FR-URL-007, DF-002 resolved by
CR-003); the exact code within that class is an implementation detail (CN-006).
```

**This edit was not in the approved list.** It was found when V2 — *"no `provisional` statement remains"* — failed on
application, returning one hit this record's enumerated edits did not cover. The edit list under-enumerated what the
verification row already required.

It is applied rather than deferred because it is **squarely inside this record's approved subject**: a reviewer-facing
statement asserting that an approved decision is still pending, which is the exact defect (analyze finding **H3**) the
record exists to fix. It expands no scope and introduces no new obligation — it corrects the same class of staleness in
the same file, on the same decision.

**It is also the control working.** This is the second time in this project that a change-record's edit list proved
narrower than its own intent, and the first time the mismatch was caught **before** the record could claim to be
applied rather than afterwards.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **PATCH** for Edits 1–3 and 5: they correct statements about decisions made elsewhere and change no obligation. **MINOR** for Edit 4, which changes what a reviewer is told to verify in DS-B. |
| **Backward-compatibility impact** | None. Documentation. |
| **Affected consumers** | Reviewers, directly. `tasks.md` T127, which executes this guide (**CR-016**). |
| **Affected tests** | T127 executes every command in this guide; Edits 2 and 4 change what it must execute. No code test. |
| **Affected documentation** | This file. `plan.md` §11 carries the matching DS-B change (**CR-015**). |
| **Rollout / migration** | None. |
| **Required approval** | Human owner, because the file's own status line requires change control for any edit. Edits 1–3 correct the record to match decisions already taken; Edit 4 implements Decision 4. |

## Post-application verification — MANDATORY before this record may be marked APPLIED

| # | Edit | Verification command (from repo root) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'unresolved at the time of writing' specs/001-agentic-sdlc-url-shortener/quickstart.md` | `0` | **EXECUTED: 0** — HOLDS |
| V2 | 1, **6** | `grep -c 'provisional' specs/001-agentic-sdlc-url-shortener/quickstart.md` | `0` — requires Edit 6, which this row is what found | **EXECUTED: 0** — HOLDS |
| V3 | 1 | `grep -c 'all resolved on 2026-09-20' specs/001-agentic-sdlc-url-shortener/quickstart.md` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V4 | 2 | `grep -c 'not yet decided' specs/001-agentic-sdlc-url-shortener/quickstart.md` | `0` | **EXECUTED: 0** — HOLDS |
| V5 | 3 | `grep -oE 'executor mode[^l]' specs/001-agentic-sdlc-url-shortener/quickstart.md` — the label term only | `0`. **Two narrower forms were tried and both were wrong**: unbounded `executor mode` matches inside `executor model`, and word-bounded `mode` matches the owner's own approved phrasing *"AI mode"* and *"what each mode demonstrates"*. Only the **label term** `executor mode` was renamed | **EXECUTED: 0** — HOLDS |
| V6 | 3 | `grep -c 'executor-kind label' specs/001-agentic-sdlc-url-shortener/quickstart.md` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V7 | 4 | `grep -c 'aggregate redirect limit' specs/001-agentic-sdlc-url-shortener/quickstart.md` | `≥ 1` | **EXECUTED: 1** — HOLDS |
| V7b | 1, 4 | `grep -c -E 'archive\|archival' specs/001-agentic-sdlc-url-shortener/quickstart.md` | `1` — the single occurrence is the **production recommendation** in §Honest limitations, never a behaviour | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V8 | 4 | `grep -c 'pre-fix commit' specs/001-agentic-sdlc-url-shortener/quickstart.md` | `0` — **Decision F**: no replay path anywhere | **EXECUTED: 0** — HOLDS |
| V9 | 5 | `grep -c '^6\. What did the baseline' specs/001-agentic-sdlc-url-shortener/quickstart.md` | `1` | **EXECUTED — HOLDS** (covered by the consolidated harness run, 2026-09-20) |
| V10 | whole file | `grep -nc 'DF-00' specs/001-agentic-sdlc-url-shortener/quickstart.md` then read each hit | every DF reference states its resolution status | EXECUTED: 3 hits, all stating resolution — line 171 "DF-002 resolved by CR-003", lines 309-315 "all resolved on 2026-09-20", DF-004 "open by design". **HOLDS** |


**Check correction recorded, 2026-09-20 — and it took two attempts, which is worth stating.** V5 was first written
as an unbounded `grep -c 'executor mode'`. It returned **1**: the phrase *"the executor model has three kinds, not
two"*, where the pattern matches only as a substring of `model`. Word-bounding `mode` then returned **5**, all of them
correct text — *"AI mode is optional"*, *"What each mode demonstrates"*, *"headless mode"* — and the first two are the
**owner's own approved phrasing** from her Gate-4 capability-framing instruction.

The edit only ever renamed the **label term** `executor mode` → `executor-kind label`. The check now says exactly
that. **The edit was right twice while the check was wrong twice**, which is the honest summary: in this package my
absence-greps were consistently looser than the edits they were meant to verify.

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V10 deliberately cannot be satisfied by a count** — it requires the applier to read each hit, because the
defect being fixed was a true-looking sentence, and no grep distinguishes a correct DF statement from a stale one.

## Residual risk

Edit 2 removes the *"not yet decided"* hedge from the command shapes, which slightly raises the cost of getting
them wrong: a reviewer will now read `./mvnw …` invocations as authoritative. The guide still says plainly that
nothing here has been executed, and **T127 is the task that makes the commands real** — it executes the guide on a
clean machine and commits the corrections. Until T127 runs, the strongest honest claim about this file is that it
is a design, and Edit 2 preserves that claim while removing a false statement about ADR-001.
