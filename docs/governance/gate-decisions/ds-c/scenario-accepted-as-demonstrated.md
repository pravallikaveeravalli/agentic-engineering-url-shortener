# DS-C (ambiguous scenario) — accepted as demonstrated; the trusted-partner feature is not built

**Scope note**: a **product-internal** orchestration-scenario decision, never this repository's own numbered
SpecKit lifecycle gates.

| Field | Value |
|---|---|
| **Scenario** | DS-C — ambiguous requirement (the canonical contradiction: expire after a week / retain analytics indefinitely / still redirect for trusted partners) |
| **Outcome** | `ACCEPTED AS DEMONSTRATED` — the scenario's orchestration is proven; the trusted-partner feature itself is deliberately **not built** |
| **Deciding human** | Pravallika Veeravalli |
| **Decision date** | 2026-09-22 |
| **Artifact approved** | The full DS-C evidence chain: `docs/evidence/ds-c/silence.json` (T138 — detection before implementation, unsafe implementation prevented, suspension recorded), `docs/evidence/ds-c/pending-gate-s4-context.md` and `docs/governance/gate-decisions/ds-c/s4-central-contradiction-clarification.md` (the owner's real clarification), `docs/evidence/ds-c/replan.json` (T139 — clarification recorded, resumed for real to S6), `docs/evidence/ds-c/rejection.json` (T140 — the rejection variant, deterministic termination, zero downstream artifacts) |
| **Where else recorded** | `specs/001-agentic-sdlc-url-shortener/tasks.md` — T138/T139/T140 |

## Reason / verification performed

The owner's own decision, stated directly: DS-C's scenario exists to prove the orchestrator's own
detect → clarify → replan → resume mechanism, and the rejection variant's deterministic termination —
**not** to deliver a trusted-partner redirect-bypass feature as a product capability. That mechanism is now
genuinely proven, end to end, with real live evidence at every step (T138, T139, T140, all `[x]` in
`tasks.md`). Building the trusted-partner feature itself would add product surface the scenario's own purpose
never called for.

**DS-C's own run (T139) stays intentionally suspended at S6** — this is not "incomplete," it is the accepted
final state: the scenario's own demonstration value (detection, clarification, resumption) was already fully
realized by the time S6 opened; nothing about the feature actually being built adds to that proof.

## What this decision does NOT claim

**T139's own replan evidence is honestly scoped, not overclaimed here.** DS-C's own clarification arrived
*before* any downstream stage had executed (T138's own proof: S5 through S12 were all `BLOCKED` going into
the clarification), so its own "downstream impact" was genuinely empty — `ReplanService`'s real
downstream-invalidation walk (invalidating already-executed work, voiding already-standing approvals) was
never exercised by this scenario, and this decision does not claim it was. **That genuine demonstration —
a late-emerging ambiguity invalidating work that has already run — is carried by T133 (DS-A's own
late-ambiguity escalation task), not by DS-C.** T133 remains open, a separate, later piece of work, not
implicitly closed by this decision.

## Conditions attached to the approval

1. **The trusted-partner feature is explicitly out of scope going forward** unless a future, separate
   decision reopens it as a real product requirement — this decision does not leave it as a silently deferred
   backlog item.
2. **T133's own, different replan demonstration remains genuinely open** and is not satisfied by this
   decision or by T139's own evidence.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If a future turn conflates T139's empty-downstream-impact replan with T133's own genuine late-ambiguity replan, that would misrepresent what each actually proved — this record exists to prevent that | Whenever DS-C or T133's own evidence is next cited |
