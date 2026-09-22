# CR-054 — DS-A clean-pass subject switched to a genuinely new feature; routine clarifications delegated

| Field | Value |
|---|---|
| **Change request** | CR-054 |
| **Title** | Retire the `FixedWindowCounter` test-addition subject; demonstrate DS-A's clean-pass property on a genuinely new feature (`GET /v1/version`), with routine S4 clarifications resolved under the owner's standing delegation |
| **Raised by** | Owner's own observation: a test-addition blurs the greenfield/brownfield distinction — a new capability is the correct greenfield subject; the brownfield scenario (DS-B) is the one that changes existing behaviour |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — switch subjects, and delegate routine clarification resolution.** Conversational instruction — **not a formal gate decision**; no `docs/governance/gate-decisions/` record is produced for the subject switch itself (CLAUDE.md's own trigger rule). The delegation of routine clarifications is recorded separately, as its own standing artifact: `docs/governance/delegations/routine-clarification-delegation.md` |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — no change to any implemented behaviour, policy, architecture, or approved artifact; changes which requirement DS-A's live demonstration evaluates, and how routine (not substantive) gate decisions are recorded |
| **Artifacts changed** | `docs/evidence/ds-a/design.md` (new "Current subject (CR-054)" section) |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`REQUIREMENT` constant and the clarification-matching logic) |

---

## Why the test-addition subject is retired

CR-053's `FixedWindowCounter` test-addition subject was chosen to eliminate runtime-behavioural surface from
DS-A's own demonstration, and it worked as evidence (a decisive, sharper data point for
`requirement-completeness-ceiling-finding.md`) — but the owner's own observation, on reflection, is
structural: **DS-A is specifically the *greenfield* scenario — a genuinely new capability.** A change that
adds test coverage to an existing, unmodified class is not a new capability; it sits closer to DS-B's own
territory (a change against existing code), and using it for DS-A blurs the very distinction the three
scenarios (DS-A greenfield, DS-B brownfield, DS-C ambiguous) exist to keep separate. This is a genuine
correction, not a reversal under pressure — `requirement-completeness-ceiling-finding.md`'s own CR-053
addendum stands, unmodified, as real evidence; this CR does not retract or weaken it.

## The new subject: `GET /v1/version`, a genuinely new feature

> Add a public endpoint `GET /v1/version` that requires no authentication, takes no path or query
> parameters, and returns HTTP `200` with `Content-Type: application/json`, body
> `{"version": "<the application build version string>"}`, and header `Cache-Control: no-store`.

Deliberately less exhaustively pre-specified than CR-051/052/053's own wordings — this is the point: real
routine ambiguities are expected, and are resolved through the governed clarification path under the owner's
own standing delegation (below), not pre-answered into the requirement text to manufacture a clean pass.

## The delegation, and why recording it this way is honest

Full record: `docs/governance/delegations/routine-clarification-delegation.md`. In summary: the owner
authorized the acting session to record her own, already-established answers to **routine** S4 findings
without a fresh round-trip for each one, while keeping every **substantive** gate (S6, S11, the brownfield
security gate, final submission) entirely her own. This does not change `ActorAuthority`'s structural rule —
every `GateDecision` recorded under this delegation still carries `actorType = "human"` and her own name,
because the decision's content genuinely is hers, established on the record before this run. What changes is
*when* it is recorded, not *who* decided it or *what* was decided. A **safety valve** is structural, not just
stated: any finding not matching one of the delegation's own standing answers is left unresolved and reported
to the owner, exactly as every prior turn's genuinely novel findings were — the delegation cannot be
stretched to cover a finding it does not actually name.

## A related investigation: the suspected "resume re-runs S3" defect

The owner's instruction asked this CR's own driver change to fix, red-first, any defect where resuming a run
past S4 re-executes S3. **Investigated directly against `Conductor.java`'s real dispatch logic — no such
defect exists.** `Conductor.readyNodes()` (the sole source of what `advance()` ever dispatches) selects only
nodes whose own state is `BLOCKED` or `READY`; S3, once `SUCCEEDED`, is a terminal state and is structurally
never reselected, regardless of how many times `advance()` is called afterward. This is independently
confirmed by `docs/evidence/ds-c/replan.json` (T139's own live evidence): S3 ran exactly once, S4 was then
approved, and S5/S6 ran afterward with no second S3 invocation anywhere in that run's own state-transition
history.

**What actually produced "new findings on resume" across prior DS-A turns was a different, disclosed fact,
not a bug**: each live attempt was a *separate* JVM process against a *fresh*, ephemeral Testcontainers
Postgres instance (this project's own established, deliberate test-isolation pattern —
`PostgresIntegrationTest`'s own javadoc), so each attempt necessarily **resubmitted the run from S1**, with a
genuinely new live S2/S3 call — real, live model non-determinism across *separate* runs, never one run's own
resume path re-executing anything. No code change was needed or made; this section documents the
investigation so the distinction is on the record rather than assumed.

## Scope

**What changed**: DS-A's own scenario subject and the driver's clarification-matching logic (extended to
recognize the delegation's own standing answers, generalized from the prior turn's exact-question matching).
No production code changed.

**What did not change**: CR-053's own subject, evidence, and addendum remain in place, retained as evidence,
not deleted or retracted.

## Conditions attached to the approval

1. **The delegation's safety valve must be structural, not advisory** — honoured; the driver's own matching
   logic still fails loudly, naming the finding, for anything that does not match a standing answer.
2. **Substantive gates remain entirely the owner's own** — honoured; nothing in this CR or its driver
   attempts to decide S6, S11, or any security-sensitive gate.
3. **The resume-defect claim verified against real code before acting on it**, not assumed — honoured, see
   the investigation section above.
4. **No gate-decision record for the subject switch itself** — honoured; the delegation has its own, separate
   standing record.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If this run still finds a genuinely novel, consequential item the delegation does not cover, it is reported to the owner plainly, not resolved under an over-broad reading of the delegation | This run's own live attempt |
| The delegation's own standing-answers list grows only by the owner's own explicit addition, on the record | Whenever a future routine clarification is proposed for delegation |
