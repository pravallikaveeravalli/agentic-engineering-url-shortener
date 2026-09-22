# T136 (DS-B executed run with injected retry and compensation) — status: genuinely blocked on T136a

**Investigated, not assumed.** T136's own `Artifact` field states its retry/compensation evidence is captured
"**both inside the aggregate-tier run**" — i.e., inside the same live orchestration run that builds
`AggregateRedirectLimiter` (T136a). That code does not exist yet, and T136a is explicitly gated on the
owner's own security-sensitive approval, not available this turn.

## Why the pre-authorized "unit-level" reduction does not close this cleanly

`plan.md`'s own delivery-sequence checkpoint (§14, "End Day 2 PM") pre-authorizes cutting "DS-B's injected
compensation case to a unit-level proof; keep the scenario" if needed. Checked directly whether that
reduction is genuinely available right now, rather than assuming it is:

- **`RetryPolicy`** (T085, already built and already unit-tested — `RetryPolicyTest`, `RetryRulingTest`,
  `RetryPolicyTelemetryTest`) is shaped specifically around `StageExecutor`/`StageInput`/`StageOutcome` — the
  orchestration pipeline's own stage-retry mechanism. Exercising it meaningfully against "a transient
  `UNAVAILABLE` during the aggregate-tier's own work" requires that work — i.e., a real `StageExecutor` doing
  something recognisably DS-B's own subject — to actually exist.
- **`CompensationHandler`** (T090, already built and already unit-tested — `CompensationHandlerIT`)
  compensates exactly three effect classes: a gate decision, an audit record, or a created short link. It has
  no generic "compensate an arbitrary effect" path, and the aggregate-tier's own natural irreversible effect
  (an aggregate rate-limit decision or counter state) is not one of the three — because that effect, and the
  code that would produce it, do not exist yet either.

**Conclusion, reached honestly rather than worked around**: a "unit-level proof" attempted right now would
have to either (a) exercise `RetryPolicy`/`CompensationHandler` against an unrelated existing effect (a gate
decision, an audit record, a short link) and *label* it as DS-B's own evidence, which would not be genuine
DS-B evidence — the exact kind of manufactured demonstration this project's own Guard clauses have
consistently refused throughout every prior scenario — or (b) wait for T136a's real code to give the
demonstration a real, DS-B-shaped effect to retry and compensate. Option (b) is correct. **T136 is genuinely
blocked on T136a**, not merely inconvenient to attempt now.

## What is already true and does not need re-proving

`RetryPolicy` and `CompensationHandler` are both already real, already delivered, and already thoroughly
unit-tested in isolation (T085/T090, both `[x]` in `tasks.md`) — the mechanisms T136 will exercise are sound;
what is missing is a genuine DS-B-shaped scenario to exercise them against, which only exists once T136a's
real code does.

## What happens next

Once the owner clears T136a's security gate and its real code lands, T136's own evidence (two-vote retry
records, a labelled compensation case, both inside that same run) becomes genuinely capturable — see
`docs/evidence/ds-b/t136a-security-gate-context.md` for what that gate decision actually asks the owner to
approve.
