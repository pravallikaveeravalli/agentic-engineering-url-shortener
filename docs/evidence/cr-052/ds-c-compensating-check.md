# CR-052 compensating check — DS-C's canonical contradiction, re-run against the sharpened predicate

Non-negotiable, per the owner's own instruction for this turn: before any DS-A re-run against the sharpened
materiality predicate, DS-C's canonical contradiction (`spec.md`'s own demonstration text, verbatim — "expire
after a week / retain analytics indefinitely / still redirect for trusted partners") MUST still produce at
least one `MATERIAL_PENDING` finding and fire the gate. If it stopped tripping, the sharpening would have
gone too far and would need to be treated as a real regression, not tuned further.

**Result: PASS.** Run live against the real Claude CLI adapter
(`AmbiguityDetectionMaterialityCompensatingCheck`, not a fixture), three times across this turn's iteration
(once immediately after the prompt sharpening, once after also fixing the blank-`noClarificationReason`
parsing bug found live during the second run, and the final confirming run below). All three real,
independent runs produced the core `SEMANTIC_CONTRADICTION` finding — R1's "expire" vs. R3's "still
redirect for trusted partners" — classified `MATERIAL_PENDING` every time, alongside five to six further
genuine `MATERIAL_PENDING` findings per run (trusted-partner definition, missing actor for partner trust,
the expiry reference-point/anchor ambiguity, the missing expired-link response contract) and, in each run,
one or two findings correctly resolved `NOT_MATERIAL` with substantive, specific reasoning (e.g., "indefinite
retention" and "redirect analytics field scope" both correctly reasoned as having no behavioural fork any
stated obligation constrains).

**Final confirming run** (`target/surefire-reports/...AmbiguityDetectionMaterialityCompensatingCheck.txt`):
`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` — 7 findings, 6 `MATERIAL_PENDING` (including the core
contradiction), 1 `NOT_MATERIAL` with substantive reasoning:

```
SEMANTIC_CONTRADICTION — R1 ("expire after a week") vs R3 ("still redirect for trusted partners") — MATERIAL_PENDING
MISSING_ACTOR — no actor defined for granting/revoking "trusted partner" status — MATERIAL_PENDING
UNDEFINED_TERM — "after a week" has no stated reference point or duration semantics — MATERIAL_PENDING
MISSING_ACCEPTANCE_CRITERIA — no stated response for a non-trusted caller on an expired link — MATERIAL_PENDING
UNBOUNDED_QUANTIFIER — the trusted-partner exception itself has no stated end condition — MATERIAL_PENDING
MISSING_ACCEPTANCE_CRITERIA — "redirect analytics" field scope unstated — NOT_MATERIAL
  reason: "No requirement in this set imposes any obligation on the specific fields captured by redirect
  analytics... any conformant implementation that logs redirect events satisfies R2 equally; the ordinary
  meaning of 'redirect analytics' is sufficient and there is no behavioral fork any stated obligation cares
  about."
```

**A genuine, real bug found live during this control's second run, unrelated to the sharpening itself**: the
model returned an empty string (`"noClarificationReason":""`) for a `MATERIAL_PENDING` record — a third
variant of "no reason given" beyond the omitted-key and explicit-JSON-null cases CR-049 already handled.
`AmbiguityDetectionAiExecutor`'s own parsing rejected it as malformed even though
`AmbiguityRecord`'s own constructor already treats blank the same as null. Fixed by extending the same
blank-check to the adapter's own parsing (`!reasonNode.asText().isBlank()`), proven by a fixture regression
test needing no live call — see `AmbiguityDetectionAiExecutorTest.emptyStringNoClarificationReasonOnMaterialIsAccepted`
and the RED-phase capture at `docs/evidence/red-phase/20260922T022710Z-CR052-blank-noClarificationReason-rejected.txt`.

**Conclusion**: the sharpened predicate did not blunt detection. The genuine contradiction still fires,
every time, across three independent live runs. The predicate is also now correctly distinguishing several
additional genuine behavioural forks (partner definition, actor, reference point, response contract, the
exception's own bound) from at least one genuinely non-material dimension (analytics field scope) with
substantive reasoning in each case.
