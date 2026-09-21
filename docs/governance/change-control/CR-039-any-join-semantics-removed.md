# CR-039 — `ANY` join semantics removed; `ALL` is the only join semantic

| Field | Value |
|---|---|
| **Change request** | CR-039 |
| **Title** | Remove `ANY` from `join_semantics` everywhere; pin the field to its single legal value `ALL` |
| **Raised by** | Acting agent (Slice 4 artifact-coverage sweep finding) |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED** — ruling 2, 2026-09-21 |
| **Decision date** | 2026-09-21 |
| **Classification** | **MAJOR** — narrows an enum in an approved contract, and narrows a database CHECK |
| **Artifacts changed** | `contracts/workflow-state.schema.json` (`joinSemantics` enum); `data-model.md` (DependencyEdge row); `tasks.md` (T074 `Artifact` wording) |
| **Non-approved files changed** | `V6__join_semantics_all_only.sql` (new); `JoinSemantics.java`; `StageTemplateTest.java`; `JoinSemanticsIT.java` (new) |
| **Applied** | 2026-09-21 |

---

## Reason

The Slice 4 artifact-coverage sweep found that `join_semantics` was **stored and never read**. T074's
`Artifact` field names it as `(ALL | ANY)`, V4's CHECK accepts both, the contract requires it with a two-value
enum — and nothing in `src/main` consults it to decide anything. `StageCriteria.mayEnter` takes a list of
dependency *states* with no edge information at all and hardcodes `allMatch`, so it cannot honour `ANY` even
in principle. An `ANY` edge was writable and would have been evaluated as `ALL`.

That is CR-032's own reasoning running the other way. CR-032 retired `FALLBACK`'s handler because *"an enum
value that can never be emitted is a claim, not a classification"*; here the value **can** be emitted and is
never honoured, which is worse — it is a claim a caller could act on.

**The owner's ruling, in her words:**

> Just don't support ANY altogether.

`ALL` is the only join semantic. Nothing currently emits `ANY`: the standard template emits `ALL` on all
fourteen edges, and the single occurrence of `JoinSemantics.ANY` in the repository was an incidental value in
an immutability assertion, where any constant would have done.

## The implementation chosen — pin the field, do not drop it

The owner offered both options and left the choice here, to be stated. **The field is pinned to `ALL`. It is
not dropped.** Three reasons, in order of weight:

1. **Dropping the column is forbidden in one step.** `contracts/README.md` classifies *"dropping or renaming
   a column"* as MAJOR and requires deprecation **across two versions**: version *n* stops reading and
   writing it and gains a comment naming the version that will drop it; version *n+1* drops it. The rule
   spells out why — *"a single migration doing both is a MAJOR change pretending to be one step: a rollback
   to version n would then find the data already gone"* — and the repository already has a live example in
   V1's `api_key_hash`. A drop here would need two migrations and would leave the column in place for the
   whole of this assessment regardless. Pinning reaches the ruling's goal in one step without touching that
   rule at all.

2. **Pinning already makes `ANY` inexpressible at every layer.** Not merely rejected — absent:
   - **The type**: `JoinSemantics` has one constant, so `JoinSemantics.ANY` does not compile.
   - **The contract**: the enum is `["ALL"]`, so a payload carrying `ANY` fails validation.
   - **The store**: V6 narrows the CHECK to `join_semantics = 'ALL'`.

   A drop would add nothing to this. Both options refuse `ANY`; only one of them also fights the
   deprecation rule.

3. **The join concept stays named, which is worth keeping.** S7's explicit `S7.join` and the two incoming
   edges on S11 are the synchronization demonstration, and an edge that says `ALL` says what it does.
   `NodeRole.JOIN` carries join-ness for the node; the edge field carries it for the dependency.

**The objection to a single-value enum, and the answer.** A field with one legal value invites a second one
without anybody revisiting the design. That is a real risk and the reason it is answered directly:
`JoinSemanticsIT` asserts the enum holds **exactly one constant** and cites this record, so adding `ANY`
back fails a test rather than passing review. The same device pins the six failure categories and the
three-member terminal run-state set.

## Scope

Explicitly unchanged:

- **`StageState.FALLBACK`.** The owner's ruling is explicit: *"Do not touch `StageState.FALLBACK`."* It stays
  as the already-disclosed unreachable value — disclosed in the enum's own javadoc and in V4's comment, and
  guarded by T014's assertion that no `FallbackHandler` type exists. That case is settled and different: it
  is a value the contract retains and nothing emits, versus a value that was emittable and unhonoured.
- **`StageCriteria.mayEnter`'s signature and behaviour.** It requires every dependency state to satisfy the
  join, which is now the only rule there is, stated in one place. It gains no edge parameter, because there
  is no longer a second semantic to distinguish.
- The fourteen edges of the standard template, all of which already read `ALL`.
- EC-018, FR-ORC-002, FR-ORC-003 — every clause. A join still does not proceed while any required branch is
  incomplete or failed; that is what `ALL` means and it is what was always implemented.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | `grep -rn "JoinSemantics.ANY" src/` before the change | one occurrence, incidental, in an immutability test | **EXECUTED — HOLDS: 1** |
| 2 | Verify no persisted row holds `ANY` | template emits `ALL` on all 14 edges; nothing else writes edges | **EXECUTED — HOLDS** |
| 3 | `JoinSemantics` reduced to one constant | `ANY` removed; javadoc cites CR-039 | **EXECUTED — HOLDS** |
| 4 | `StageTemplateTest` immutability assertion | uses `ALL`; still asserts `UnsupportedOperationException` | **EXECUTED — HOLDS** |
| 5 | `V6__join_semantics_all_only.sql` | CHECK narrowed to `join_semantics = 'ALL'`; V4 not edited | **EXECUTED — HOLDS** |
| 6 | `workflow-state.schema.json` `joinSemantics` enum | `["ALL"]` | **EXECUTED — HOLDS** |
| 7 | `data-model.md` DependencyEdge row | reads `join_semantics` (`ALL`) | **EXECUTED — HOLDS** |
| 8 | `tasks.md` T074 `Artifact` | reads `join_semantics` (`ALL`) | **EXECUTED — HOLDS** |
| 9 | Search the three approved artifacts for a live `ANY` | the contract enum is `["ALL"]`; `data-model.md` and `tasks.md` hold none. **One textual occurrence remains, in the contract's own `description`**, recording that `ANY` was removed and why | **EXECUTED — HOLDS** |
| 10 | `JoinSemanticsIT` | exactly one constant; template emits only `ALL`; store rejects `'ANY'` with SQLSTATE 23514; store still accepts `'ALL'` | **EXECUTED — HOLDS: 4/4** |
| 11 | Fast tier + integration tier + `scripts/ci.sh` | green | **EXECUTED — HOLDS: 413 / 228 / GREEN** |

Step 10's second half is the one that matters most: narrowing a Java enum is checked by the compiler, but the
CHECK constraint is only checked by trying it. The test inserts an `ANY` edge directly and requires the store
to refuse — so the three layers are each proven separately rather than inferred from one another.

**On step 9's one remaining occurrence.** The word `ANY` still appears in the contract, inside the
`joinSemantics` description, in the sentence saying it was removed. That is deliberate: a reader meeting a
single-value enum should find the reason next to it rather than in a search of the change-control folder. It is
also a reminder that a text search for a removed value is the wrong check — this project has tripped over that
three times now, most recently in a test that forbade the word "delete" and failed on "never delete". **The
enum is the check**, and step 6 is where it is made.

## Conditions attached to the approval

1. **Remove `ANY` from everywhere it appears** — the V4 CHECK (via V6), the enum, the contract, the data
   model, and T074's wording. Verification steps 3, 5, 6, 7, 8, 9.
2. **State which implementation was chosen and why.** Done above, including the rejected alternative and the
   deprecation rule that decided it.
3. **Verify nothing currently emits `ANY`.** Steps 1 and 2.
4. **Do not touch `StageState.FALLBACK`.** Honoured; see Scope.

## Cross-record orphan check — two findings, corrected forward

Neither applied record is edited. An applied record's quotations are the record of what was approved at the
time; these paragraphs are the forward corrections the procedure requires.

**Finding 1 — CR-011 quotes the two-value form, twice.**
`CR-011-data-model-alignment.md` gives both its OLD and NEW DependencyEdge rows as
`join_semantics` (`ALL` \| `ANY`).

> As of CR-039 (2026-09-21), the `(ALL | ANY)` in both of CR-011's DependencyEdge rows is superseded. The
> only join semantic is `ALL`. CR-011's text remains accurate as a record of the data model at the time it
> was applied — and its actual subject, the move from stage-keyed to node-keyed edges, is untouched by this
> record.

**Finding 2 — CR-013 quotes `joinSemantics` in the contract's `required` array.**
`CR-013-orchestration-contract.md` records
`**NEW** "required": ["fromNodeKey", "toNodeKey", "joinSemantics"],`.

> As of CR-039 (2026-09-21), that `required` array is **still accurate** — `joinSemantics` remains a required
> edge field. What changed is its enum, from `["ALL", "ANY"]` to `["ALL"]`. CR-013's quoted line is not
> falsified by this record, and is noted here only because a reader checking CR-013 against the current
> contract will find the enum narrowed and should find the reason without having to search for it.

**Order agreement.** CR-038 and CR-039 touch disjoint text in `tasks.md` — CR-038 the `Artifact` paths of
T067/T067a/T082a and T076a's `Deps`, CR-039 the `Artifact` wording of T074 — so neither depends on the other's
order and neither removes text the other quotes. Verified by applying them in sequence with each
verification table run after its own step.

**Survival of the thing being removed.** `join_semantics` the *column* survives this change deliberately, and
the survival is claimed here: it is pinned rather than dropped, for the reasons in **The implementation
chosen**, and its eventual removal is not scheduled. That is a decision, not an oversight.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| `JoinSemantics` stays a single-constant enum; reintroducing `ANY` requires a new record superseding this one | Any change to the graph model |
| A readiness evaluator consuming edges must not reintroduce a per-edge semantic decision — there is one rule, in `StageCriteria.mayEnter` | The engine loop (T093 onward) |
| If the column is ever dropped, it goes through the two-version deprecation rule in `contracts/README.md` | Not scheduled |
