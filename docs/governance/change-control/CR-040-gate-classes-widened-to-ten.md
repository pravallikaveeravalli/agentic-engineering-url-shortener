# CR-040 — Persisted gate-class vocabulary widened from eight to ten

| Field | Value |
|---|---|
| **Change request** | CR-040 |
| **Title** | Widen `GateDecision.gateClass` from eight values to the ten plan §5 already names |
| **Raised by** | Acting agent (material conflict reported at the T058–T068 boundary) |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — ruling 1**, 2026-09-21 |
| **Decision date** | 2026-09-21 |
| **Classification** | **MINOR** — widens an enum in an approved contract (adds values; nothing existing narrows or moves) |
| **Artifacts changed** | `contracts/approval.schema.json` (`gateClass` enum) |
| **Non-approved files changed** | `GateClass.java` (new), `GateClassTest.java` (new) |
| **Applied** | 2026-09-21 |

---

## Reason / the conflict this resolves

`approval.schema.json`'s persisted `GateDecision.gateClass` enum names eight values:

```
UNRESOLVED_AMBIGUITY, ARCHITECTURE_APPROVAL, SECURITY_SENSITIVE_ACTION,
DESTRUCTIVE_OR_IRREVERSIBLE_ACTION, CONSTITUTIONAL_EXCEPTION, MATERIAL_RISK_ACCEPTANCE,
RELEASE_READINESS, FINAL_SUBMISSION
```

Plan §5's own gate-class table names **ten** rows — the eight above, plus **No change plan at
implementation** and **Node overrun**. T064's own `Artifact` field is explicit about the count:
*"the ten plan-§5 classes, of which the no-change-plan and node-overrun classes are two, each with what
it blocks"*, with a `Validate` clause requiring *"an enum completeness test asserting the count against
plan §5's table row count, so the enum and the plan cannot drift apart silently."*

A `GateDecision` recorded at either of the two missing classes could not validate against the persisted
contract — a real conflict between two approved artifacts, reported rather than worked around, and now
resolved by the owner's ruling.

**The owner's ruling, in her words:**

> keep it at 10.

## What the two added values are, and why they are not new gates

Neither is a new KIND of gate; both are gate classes plan §5 already defined and the contract's enum
simply had not caught up to.

- **`NO_CHANGE_PLAN`** — the no-plan implementation gate (FR-ORC-031, CR-001). Triggered when S7 is
  reached deterministically with no change plan for the requirement. Blocks S8 onward. Its outcomes are
  the five standard ones **plus three named choices** — governance-only, human-implemented, abandon — which
  is why it could not simply be folded into an existing class.
- **`NODE_OVERRUN`** — the stage-overrun escalation gate (FR-ORC-014 rule 6, PVT-016). Triggered when a
  node's PVT-016 execution threshold is breached. Blocks that node only; siblings and unaffected paths
  continue. Its outcomes are the five standard ones **plus two named choices** — keep waiting (re-arm the
  threshold), kill the node (fail by overrun).

**The deadline this gate waits on is the uniform one, not a special one.** CR-023 proposed a distinct
30-minute operational deadline (`PVT-017`) for exactly this gate class, and that proposal was **withdrawn
before application** — `spec.md` records *"PVT-017 was never minted"* and CR-023's own verification table
marks every row **VOID — never executed; this record was withdrawn**. So `NODE_OVERRUN` waits **PVT-006**
(24h), the same deliberative deadline every other gate class uses. T086 already built the PVT-016
*execution*-threshold policy that decides when a node has overrun in the first place; T086a (not yet
built) is what raises the gate itself, and it raises it as this class, waiting the uniform deadline.

## Scope

Explicitly unchanged:

- The eight existing values, their meaning, and every decision already recorded against them.
- `GateDecisionRequest`'s own `outcome` enum in `openapi.yaml` (`APPROVED` / `REJECTED` /
  `CHANGES_REQUESTED` / `ESCALATED`) — unaffected. `gateClass` is not a submitted request field; it is
  read server-side from the `ApprovalGate` the decision answers (T058), so widening it changes what a
  *persisted* decision may declare itself to be against, not what a caller submits.
- `TIMED_OUT`'s absence — still inexpressible on the submission surface, still produced only by deadline
  expiry (Constitution III). This CR does not touch outcomes at all, only classes.
- PVT-006, PVT-016. Neither value nor threshold changes; PVT-017 stays exactly what it has been since
  CR-023 — never minted.

## Application order and verification

| # | Step | Expected | Outcome |
|---|---|---|---|
| 1 | `approval.schema.json` `gateClass` enum | ten values, the eight existing plus `NO_CHANGE_PLAN` and `NODE_OVERRUN` | **EXECUTED — HOLDS** |
| 2 | `GateClass.java` | ten constants, each carrying what it blocks | **EXECUTED — HOLDS** |
| 3 | Enum-completeness test against plan §5's table row count | `10` | **EXECUTED — HOLDS: 10/10** (see T064) |
| 4 | The eight pre-existing values are byte-identical to before | unchanged spelling, unchanged position in file order | **EXECUTED — HOLDS** |
| 5 | `grep -c 'PVT-017' specs/001-agentic-sdlc-url-shortener/*.md` | `0` — confirms the withdrawal still holds; this CR does not resurrect it | **EXECUTED — HOLDS: 0** |
| 6 | Fast tier + `scripts/ci.sh` | green | **EXECUTED — HOLDS** (see T064's own commit) |

## Conditions attached to the approval

1. **Keep it at ten** — no more, no fewer. Honoured; verification step 3.
2. **Verify T064's registry-vs-table check passes at 10.** Honoured; the completeness test reads plan
   §5's table directly, the same pattern T070/T086 already use for their own plan-table checks, so a
   future edit to the table is what the test tracks — not a number hardcoded twice.
3. **Keep the outcomes correct** — `NO_CHANGE_PLAN` carries its three named choices, `NODE_OVERRUN` its
   keep-waiting/kill choices. Built into `GateClass`'s own type (below), not left to a comment.

## Cross-record orphan check

**CR-023 is not touched**, and needs no forward correction: its own verification table already marks
every row VOID and its own text already states PVT-017 was never minted. This CR is consistent with that
withdrawal, not in tension with it — `NODE_OVERRUN` is added here as a gate *class* (which name a decision
may record), carrying no deadline value of its own; PVT-017 remains what CR-023 left it, absent.

No other applied record quotes `approval.schema.json`'s `gateClass` enum verbatim. Verified by search.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| `GateClass` stays checked against plan §5's table by count, not by a hardcoded literal | T064, and any future edit to the plan's gate-class table |
| `NO_CHANGE_PLAN`'s three named choices and `NODE_OVERRUN`'s two are built where the outcome is actually decided, not only documented | T065 (no-change-plan gate), T086a (node-overrun gate, not yet built) |
