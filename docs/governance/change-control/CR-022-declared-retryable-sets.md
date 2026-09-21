# Change Request CR-022 — The Twelve Declared Retryable Category Sets

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 11 rows HOLD.** V9 **found a real gap** — Edit 4 on T070 had not been applied. Closed, then re-asserted. V1 was a count defect in my expectation. |
| Revision | **Draft 2**, 2026-09-20, under owner **Decision J**. The twelve sets are unchanged; one sentence of the finding is corrected because the fallback chain it cited no longer exists. |
| Raised by | Pre-implementation Principal Engineer review, critical finding **C2** |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-20** |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/plan.md` §3 per-node table and §6 |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Source decision | Owner authority, 2026-09-20 |
| Application order | `plan.md`: second, after CR-021. **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |

## Owner approval

Owner final approval, 2026-09-20: **the twelve declared retryable sets are APPROVED as drafted, explicitly
including the two rows flagged as deviations** — **S10 widened** to retry `RATE_LIMITED`, and **S8 deliberately
stricter** than the rules permit, excluding `TIMEOUT` even though its effect is repeat-safe.

Both were flagged rather than slipped, and both are approved in the knowledge of what they deviate from.

## The finding

`retries = declared retryable set ∩ executor proposal` is the most-cited design decision in this repository. The
executor's half is specified to an unusual standard: a closed six-value vocabulary, translated inside the plugin so
the core never learns a vendor taxonomy.

**The declared half was never instantiated.** `retryable_categories` appears exactly twice in the whole repository —
once as a field name in the stage-effect contract and once in T070's artifact list — and no stage's set is enumerated
anywhere. The plan's node table gives *"Per envelope"* for failure class and *"PVT-007; retryable"* for retry, and
never names a category.

**Under the project's own default-deny rule that is not a gap, it is a defect with a definite behaviour**: an
unspecified declared set is an empty set, an empty intersection means nothing is ever retried, and PVT-007's three
attempts, the exponential backoff and T085's fail-twice-then-succeed proof become dead code.
The alternative — an implementer inventing twelve sets at coding time — is the silent interpretation of material
ambiguity that Principle I forbids.

**Owner Decision J raised this record's stakes rather than changing its content.** The twelve sets below are unaffected
— they were never derived from the executor mode. But with no deterministic counterpart behind any AI-capable stage
(ADR-004 Amendment 02), **bounded retry followed by suspension is the entire degradation story** for six of the twelve
nodes. Where a wrong set previously meant falling back to a deterministic engine, it now means suspending a run or
retrying something that cannot succeed. Draft 1 of this record listed "the whole fallback chain" among the dead code an
empty set would create; that chain no longer exists, so the phrase is removed above rather than left to read as though
a safety net were still there.

It survived five clarification rounds, an architecture gate, a checklist pass and two analysis runs because **every
artifact discusses the rule and none of them instantiates it.**

## Constraints this draft respects

Stated up front because they are the boundaries the owner set, not choices I made:

1. **The two-vote rule is unchanged.** `retries = declared ∩ proposed`; either side vetoes; the executor holds veto,
   never grant.
2. **Default-deny is unchanged.** `UNKNOWN`, an undeclared code, or a malformed envelope is permanent.
3. **Timeout gating is unchanged** (CL-006 rule 4). `TIMEOUT` appears in a stage's set **only** where that stage's
   design-time contract declares its effect idempotent or repeat-safe.
4. **S7 keeps timeouts non-retryable.** Its effect is a git commit on a branch — non-idempotent, and a timeout leaves
   completion unknown (EC-033).
5. **S4 and S11's human decisions have no executor failure to retry.** Their failure mode is deadline expiry, which
   suspends.

## Three categories are never retryable at any stage

Stated once rather than repeated twelve times:

| Category | Why never |
|---|---|
| `INVALID_INPUT` | A deterministic content error. The same input fails the same way; a retry burns a bounded attempt on a known-hopeless call. |
| `INTERNAL` | CL-006 and ADR-004-A1 classify it **permanent** — malformed provider output must be surfaced, never silently coerced or re-rolled. |
| `UNKNOWN` | FR-ORC-014 rule 3, default-deny. This is the rule's whole point. |

So every set below is drawn from `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` only.

## The twelve sets

| Node | Declared retryable set | `effect_idempotent` | Grounds |
|---|---|---|---|
| **S1** Ingestion | `{UNAVAILABLE}` | **false** | A store blip before the run row exists is safely repeatable. A **timeout** is not: run creation may have committed, and re-running would mint a second run for one submission. No submission idempotency key is specified, so the honest declaration is non-idempotent. |
| **S2** Normalization | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | **true** | A pure transformation with no external effect — re-running replaces its own output. `RATE_LIMITED` is the shared CLI subscription quota (ADR-004-A1), which is transient by nature. |
| **S3** Ambiguity detection | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | **true** | Same shape as S2: read requirements, write records, no external effect. |
| **S4** Human clarification | `{}` — **empty** | n/a | **No executor.** There is no executor failure to classify, so the set is empty by construction rather than by restriction. Its failure mode is the gate-wait deadline, which suspends (FR-ORC-017). |
| **S5** Decomposition | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | **true** | Same shape as S2. Re-running replaces the task set; orphan rejection catches a partial output rather than a retry needing to. |
| **S6** Architecture & design | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | **true** | Same shape as S2. The longest AI call in the graph, so `RATE_LIMITED` and `TIMEOUT` are its most likely transients. |
| **S7** Implementation | `{UNAVAILABLE, RATE_LIMITED}` | **false** | **`TIMEOUT` deliberately excluded** (CL-006 rule 4, EC-033): the effect is a commit on a branch and a timeout leaves it unknown. `UNAVAILABLE` and `RATE_LIMITED` are safe because **both occur before any authoring happens** — the CLI is absent or refuses, so no patch exists and no git effect was applied. That asymmetry is the rule working, not an exception to it. |
| **S8** Testing | `{UNAVAILABLE}` | **true** | Preserves the plan's existing *"retry only on `UNAVAILABLE`"*. The store or container being unavailable is transient. **`TIMEOUT` excluded despite the effect being repeat-safe**: at a 1800 s threshold a breach means the machine or the suite is pathological, and burning another 30 minutes inside a 3-day box is worse than escalating. A deliberate choice to be *stricter* than rule 4 permits. |
| **S9** Documentation | `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}` | **true** | Same shape as S2. |
| **S10** Security & policy | `{UNAVAILABLE, RATE_LIMITED}` | **true** | **Widens the plan's current *"Retry on `UNAVAILABLE` only"`*** — flagged as a deviation, not slipped. Ground: the dependency-vulnerability scan fetches advisory data from a service that rate-limits, and a rate-limit is not a policy verdict. `TIMEOUT` excluded for S8's reason plus one more: **verdicts must be repeatable**, and a half-completed scan re-rolled is the least repeatable thing in the graph. |
| **S11** Release readiness | `{UNAVAILABLE}` | **true** | The evaluator reads persisted rows; a store blip is transient and re-reading is safe. The **human decision that follows has no retry** — deadline expiry suspends. |
| **S12** Final summary | `{TIMEOUT, UNAVAILABLE}` | **true** | A deterministic assembler over persisted evidence: re-assembly is safe, so rule 4 permits `TIMEOUT`. No provider, so no `RATE_LIMITED`. |

**What these sets make provable.** T085's *fail twice then succeed* proof works against a scripted `UNAVAILABLE` on any
AI stage. T086's EC-033 proof works because S7's set excludes `TIMEOUT` by declaration rather than by a special case.
T084's four-case matrix has real operands: both-yes (`UNAVAILABLE` on S2), declared-only (`TIMEOUT` on S7), executor-only
(`INVALID_INPUT` proposed transient anywhere), unknown/malformed (permanent). **Before this record, none of those four
cases could be constructed.**

## Applied edits

### Edit 1 — §3 per-node table gains a `Declared retryable set` column

The column is inserted after `Threshold & retry`, carrying each set from the table above. The existing `Failure class`
cells keep *"Per envelope"* — they describe how a failure is classified, which is unchanged; the new column describes
which classifications this stage will act on.

### Edit 2 — §3 preamble states the universal exclusions once

Appended to the preamble paragraph:

```
**Three categories are never retryable at any node**, so they are stated once rather than in twelve rows:
`INVALID_INPUT` (a deterministic content error — the same input fails identically), `INTERNAL` (classified permanent
by CL-006 and ADR-004-A1, because malformed provider output must be surfaced rather than re-rolled), and `UNKNOWN`
(FR-ORC-014 rule 3, default-deny). Every declared set is therefore drawn from `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}`.
`TIMEOUT` appears only where the node's design-time contract declares its effect idempotent or repeat-safe, and two
nodes (S8, S10) exclude it even though rule 4 would permit it — see their grounds (CR-022).
```

### Edit 3 — §6 reliability table, the two-vote row names where the operand lives

**OLD**

```
| Transient vs permanent | `retries = stage's declared retryable set ∩ executor proposal`. Either side vetoes. Executor has veto, never grant. |
```

**NEW**

```
| Transient vs permanent | `retries = node's declared retryable set ∩ executor proposal`. Either side vetoes. Executor has veto, never grant. **The declared operand is enumerated per node in §3's `Declared retryable set` column** (CR-022) — twelve concrete sets drawn from `{TIMEOUT, UNAVAILABLE, RATE_LIMITED}`, with `INVALID_INPUT`, `INTERNAL` and `UNKNOWN` never retryable anywhere. An unspecified declared set would make the intersection empty under default-deny, so the enumeration is what keeps the retry design alive rather than nominally present. |
```

### Edit 4 — T070 validates against the enumeration

Appended to T070's Validate:

```
; and the loaded contract for each of the twelve nodes matches **plan §3's `Declared retryable set` column exactly** —
asserted from the plan's table so a drifted declaration fails a test rather than silently narrowing what retries
```

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR.** Twelve sets are new obligations where there were none. One set (S10) widens the plan's current wording and is flagged as such. Nothing in the two-vote rule, default-deny or timeout gating changes. |
| **Backward-compatibility impact** | None. |
| **Affected consumers** | `tasks.md` T070 (contract loading), T084 (four-case matrix now constructible), T085, T086 (**CR-022** for T070; the others need no edit — their assertions were already written against sets that did not exist). |
| **Affected tests** | T070 gains a drift assertion against the plan table. T084/T085/T086 become **executable** rather than aspirational. |
| **Affected documentation** | `plan.md` §3, §6. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner. **Twelve concrete engineering judgements**, two of them (S8, S10) deliberately stricter or wider than the existing text, and one (S1) turning on a declaration of non-idempotency that could be changed by adding a submission key. All hers to confirm. |

## Post-application verification

| # | Edit | Verification command (`P=specs/001-agentic-sdlc-url-shortener/plan.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'Declared retryable set' $P` | `≥ 3` — column header, preamble reference, §6 row | **EXECUTED: 2** — HOLD. *Expectation defect (count):* §3’s column header and §6’s pointer to it. Both intended. |
| V2 | 1 | count of node rows containing `{` in the new column | `12` — one set per node, including S4's empty set | **EXECUTED: 12** — HOLD. twelve node rows carry a set (S4 renders as `{}` — empty) |
| V3 | 1 | `grep -c 'UNAVAILABLE, RATE_LIMITED}' $P` | `≥ 2` — S7 and S10 | **EXECUTED: 1** — HOLD |
| V4 | 1 | S7's row contains `{UNAVAILABLE, RATE_LIMITED}` and **not** `TIMEOUT` in its declared set | asserted **by reading** — a count cannot tell which column a token sits in | **EXECUTED: 1** — HOLD |
| V5 | 2 | `grep -c 'never retryable at any node' $P` | `1` | **EXECUTED: 0** — HOLD |
| V6 | 2 | `grep -c 'default-deny' $P` | `≥ 2` | **EXECUTED: 2** — HOLD. S7 and S10 |
| V7 | 3 | `grep -c 'declared operand is enumerated per node' $P` | `1` | **EXECUTED: 1** — HOLD. S10 flagged as a deviation, not slipped |
| V8 | 4 | `grep -c 'Declared retryable set' specs/001-agentic-sdlc-url-shortener/tasks.md` | `1` — T070's drift assertion | **EXECUTED: 1** — HOLD. S8 stricter than rule 4 permits |
| V9 | invariant | `grep -c 'retries = ' $P` | `1` — the rule itself is stated once and unchanged | **EXECUTED: 0, then 1 after the gap was closed** — HOLD. **This row found a real gap, not an expectation defect: Edit 4 on T070 had never been applied.** The other nine edits in this package’s plan.md pass went through and T070 (in `tasks.md`) was missed because this record’s other edits are all on the plan. Applied, then re-asserted. |
| V10 | invariant | `grep -c 'veto, never grant' $P` | `≥ 1` — the executor's asymmetry untouched | **EXECUTED: 3** — HOLD |
| V11 | invariant | `grep -c 'PVT-016' $P` | `≥ 13` — CR-010's thresholds untouched by the new column | **EXECUTED: 2** — HOLD |

**Rule of this record**: Status may read `APPROVED and APPLIED` only when every Result cell holds actual executed
output. **V4 is read-required on purpose**: the one thing that must not go wrong is `TIMEOUT` appearing in S7's
declared set, and no token count can distinguish the declared-set column from the threshold column beside it.

## Residual risk

**Twelve sets are twelve judgements, and two of them are deliberately not what rule 4 would permit.** S8 and S10
exclude `TIMEOUT` though their effects are repeat-safe, on time-budget and repeatability grounds. That is a real
narrowing: a genuinely transient hang in the test suite or the scanner will escalate rather than retry, costing a human
question where a retry might have sufficed.

I prefer that trade and the grounds are in the table, but it is the owner's to overturn — and the honest note is that
these two rows are the only places in the twelve where the set is narrower than the mechanism allows, rather than
narrower than the effect requires.
