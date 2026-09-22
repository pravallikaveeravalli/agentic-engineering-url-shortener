# DS-A live run, `FixedWindowCounter` test-addition subject (CR-053) — pending gate context for the owner

**This is the turn's single, hard-capped attempt, per the owner's own explicit instruction: if a material
finding surfaced even on this zero-runtime-behaviour subject, stop, do not re-run, do not switch subjects
again.** It did. This is that stop.

`runId`: `7801e8dc-7f2e-4407-a581-1f40b466408e`. S1–S3 all genuinely succeeded (real `claude-sonnet-5` calls,
~210s). Full response captured, not truncated:
`docs/evidence/ds-a/run-snapshot-ATTEMPT-11-STOPPED-AT-S4-fixedwindowcounter-test-subject.md`.

## The result — decisive, and better than every prior attempt, but still not clean

Of eight real findings, **six correctly resolved `NOT_MATERIAL`**, several explicitly reasoning "this is
characterization testing of existing, unmodified behaviour, so the existing artifact already fixes the
answer" — real, positive evidence the sharpened predicate (CR-052) is working precisely as intended on this
subject. **Two remain `MATERIAL_PENDING`**:

1. **Per-`key` or per-`(key, tier)` state tracking?** The requirement text names both `key` and `tier`
   (`check(key, tier)`) without stating which one indexes the counter's internal window/count state. (The
   real code tracks state by `key` alone — `tier` is only echoed into the output decision — but the
   requirement text as submitted did not say so.)
2. **The general fractional-second rounding rule for `retryAfterSeconds`.** The requirement fixed the
   *sub*-1-second edge case explicitly ("floored at a minimum of 1 second") but never stated the conversion
   rule (floor/ceil/round) for the general case above one second. (The real code uses `Duration.toSeconds()`,
   which truncates/floors — again, a fact the requirement text never stated.)

## Why this is decisive evidence, not a failure to design around

Both remaining findings trace to the **same root cause** that has run through this entire arc: S3 cannot see
the codebase, so a fact true of the existing implementation is invisible to it unless the requirement text
states it directly. What is new and decisive here: this subject was chosen *specifically* to eliminate any
runtime-behavioural fork, and it still surfaced two real findings — because the object being described (an
existing class's own internal implementation detail) still has facts a text-only reader cannot access any
other way. See `docs/evidence/ds-a/requirement-completeness-ceiling-finding.md`'s new "Addendum (2026-09-22,
CR-053)" section for the full analysis.

## What was NOT done, per the owner's own explicit hard cap

- No further wording revision was attempted.
- No further subject switch was attempted.
- No re-run was attempted hoping for a cleaner roll.
- No `GateDecision` was recorded — the run remains `RUNNING`, paused at S4, exactly as designed.

## DS-A's settled state for this turn

Per the owner's own framing: "Either way, DS-A is settled this turn — a genuine clean pass if it clears, the
clarification path if not." It did not clear. DS-A's settled record for this turn is the **full, honest
multi-subject arc**: the expiry endpoint (CR-048–050), the `GET /v1/version` endpoint across its own minimal
and sharpened wordings (CR-051/052, attempts 6–10, three of which — Content-Type, Cache-Control,
observability scope — were resolved by real owner clarifications through the governed path, while three
further genuinely new items across attempts 8/9/10 were not), and now this zero-behaviour test-addition
subject (CR-053, attempt 11). No subject, across eleven live attempts, produced a silent clean pass to S6.
Three subjects' worth of real, honest evidence — not a defect, a genuine finding about what a real,
non-deterministic, codebase-blind AI ambiguity detector actually does when asked to certify natural-language
completeness, now the headline of `requirement-completeness-ceiling-finding.md`.
