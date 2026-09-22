# T136a — how the tier was actually built: directly, not by the orchestrator, and why

**Stated plainly, because this project's whole discipline is never claiming orchestrator-authorship a
change does not have.**

`AggregateRedirectLimiter` and the `RedirectController` post-lookup checkpoint (commit `576f9bf`) were
**built directly** — hand-authored, real, tested code — not produced by a live AI dispatch through
`Conductor`/`ImplementationAiExecutor` (S7), unlike every DS-A greenfield artifact this repository landed.

## Why, honestly

Three genuine, live, capped orchestrator attempts were already made against this exact subject in a prior
turn (`docs/evidence/ds-b/attempts-1-3-finding.md`): attempts 1–2 stopped at S4 on real, substantive
ambiguity findings (all answered under the owner's standing delegation); attempt 3 stopped at S3 itself on
a genuinely new live-AI defect (a `ResolutionState` value used as an `ambiguityClass` value), fixed as
CR-070. **Retry** (S3's real two-attempt recovery, live) and **compensation** (T090's real machinery
against a real effect) were both already genuinely demonstrated live in that same turn — T136's own
evidence requirement is met by that prior work and is not re-demonstrated here.

This turn, the owner directed — explicitly, in these terms — that the tier itself be built directly rather
than risk a further live orchestrator attempt: retry/compensation and the impact analysis (T135) are the
pieces this scenario exists to demonstrate about the *orchestrator*, and re-running the full pipeline a
fourth time to also get the tier's own code from it would trade a real, already-banked result (three
honest, disclosed attempts, two real reliability mechanisms proven live) for renewed live-AI variance risk
on a design already fully specified and already security-gate-approved. That is a legitimate call the
owner is entitled to make, and it is recorded here rather than left for a reader to infer from the code's
own silence.

## What this means for the DS-B demonstration as a whole

DS-B's own four pieces, and how each was actually produced:

| Piece | How produced | Evidence |
|---|---|---|
| Impact analysis (T135) | Written pre-change, by this session, before any code existed | `docs/evidence/ds-b/impact-analysis.md` |
| Injected retry (T136) | Live, real, through the orchestrator — twice | `docs/evidence/ds-b/attempts-1-3-finding.md` |
| Compensation (T136) | Live, real, through the orchestrator (a separate, minimal proof per plan.md's own pre-authorized reduction, since no attempt reached S7) | `docs/evidence/ds-b/t136-compensation-only.md` |
| The tier itself (T136a) | **Built directly**, this turn, per the owner's own explicit direction | `src/main/java/agentic/shortener/delivery/ratelimit/AggregateRedirectLimiter.java`, commit `576f9bf` |
| Before/after tests (T137) | Real, both states, against the directly-built tier | `docs/evidence/ds-b/test-results/before-after.md` |

**Not claimed**: that `AggregateRedirectLimiter` is AI-authored, or that this turn's own build ran through
`GitWorktreeBranchApplier`/S7. It did not. The design it implements is exactly the one three separate real
artifacts already fixed before this turn wrote a line of it: `docs/evidence/ds-b/impact-analysis.md` (T135,
written before any code existed), `docs/governance/gate-decisions/ds-b/t136a-security-gate-approved.md`
(the owner's own real security-sensitive approval), and this turn's own DS-B requirement text (embedded in
`DsBLiveRun.java`, carried over unchanged as the specification this direct build also follows) — so while
the code's own authorship is direct, the design it encodes is not this session's own invention.
