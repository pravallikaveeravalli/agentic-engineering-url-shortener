# CR-050 — DS-A requirement made fully self-contained (supersedes CR-048's wording)

| Field | Value |
|---|---|
| **Change request** | CR-050 |
| **Title** | Fold the owner's four open-item decisions (attempt 4) into a fully self-contained DS-A requirement, superseding CR-048's wording |
| **Raised by** | Attempt 4's four `MATERIAL_PENDING` findings, reported in `docs/evidence/ds-a/pending-gate-s4-context.md` |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | Conversational approval of a requirement revision — verbatim: "Go with those." **This is a requirement clarification, not a formal gate decision** (per CLAUDE.md's gate-decision trigger rule, conversational assent does not produce a `docs/governance/gate-decisions/` record; none is filed for this CR) |
| **Decision date** | 2026-09-21 |
| **Classification** | **LOW** — no change to any implemented behavior, policy, architecture, or approved artifact; a requirement-text-only revision to DS-A's own subject, the scenario input under evaluation |
| **Artifacts changed** | `docs/evidence/ds-a/design.md` (revision history + current requirement) |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`REQUIREMENT` constant only) |
| **Applied** | 2026-09-21 |

---

## Why this CR exists

Attempt 4 (CR-049-calibrated S3, CR-048's wording) confirmed the materiality calibration works, and also
surfaced the structural reason CR-048's wording still could not clear S3: **S3 has no codebase visibility —
`StageInput`'s own closed field list gives it only the normalized requirement text, never repository
access.** A fact this agent (or a human) knows to be true of the delivered system — `ExpiryPolicy`'s
boundary rule, the repository's absence of any delete method — is, from S3's own vantage point,
indistinguishable from a fact that is not true at all, unless the requirement text states it directly.
CR-048 closed six gaps by restating facts already true of the system; four more remained because they were
never restated: the boundary instant, the rounding rule, link retention after expiry, and malformed-`{code}`
handling. The owner resolved all four on the record (quoted in full below); this CR folds them in and
restates every such fact directly, closing the structural gap rather than trading one wording round for
another.

## The owner's four decisions, as given, each marked by kind

| # | Decision (owner's own words) | Kind |
|---|---|---|
| 1 | **Rounding of `secondsRemaining`: floor** — "never over-report the time left" | **Genuine choice.** Nothing in the delivered system fixes a rounding rule; the owner is deciding one, not restating an existing fact. Recorded as the owner's decision, not an inferred default. |
| 2 | **`now == expiresAt`: EXPIRED**, `secondsRemaining = 0` | **Restates existing behavior.** `ExpiryPolicy` (`src/main/java/agentic/shortener/domain/link/ExpiryPolicy.java`, T046) already states "at `expiresAt` the link is expired." Not a new rule — S3 simply cannot see that file, so the requirement text must say so itself. |
| 3 | **Expired-and-aged-out link: never purged** | **Restates existing behavior.** Verified below. |
| 4 | **Malformed `{code}`: same not-found response as an unknown code** | **Restates existing behavior.** Verified below. |

## Verification performed before folding #3 and #4 in — real source, not the owner's proposed wording asserted

Per the owner's explicit instruction: verify against real source; if verification reveals a different real
answer, use the real one and report the discrepancy rather than forcing the proposed wording. Both checked
out exactly as proposed — no discrepancy to report.

### #3 — never purged

- `src/main/java/agentic/shortener/persistence/JdbcShortLinkRepository.java` and
  `src/main/java/agentic/shortener/domain/link/ShortLinkRepository.java`: grepped for any delete/purge
  method — **none exists**. The only state transition available for a short link is to expired.
- `src/test/java/agentic/shortener/audit/GovernanceImmutabilityIT.java`: its `RETAINED_TABLES` list is
  scoped to governance/audit tables only (`audit_record`, `state_transition`, `gate_decision`,
  `failure_event`, `policy_check_result`, `compensation_record`, `redirect_event`) — `short_link` is not in
  it, confirming retention there is a governance-record concern, not evidence of a link-purge path elsewhere.
- `specs/001-agentic-sdlc-url-shortener/spec.md`, three independent confirmations:
  - Line 780: "an unwanted short link is corrected by setting it to expired, never deleted."
  - Line 1143 (Compensation Register): "Created short links | No | Compensate — set to expired, never
    delete | EX-003 excludes deletion. Rationale: dangling history, short-code reuse hijack, repeat-safe
    cleanup."
  - Line 2043 (Exclusions): "EX-003: Link deletion, editing, or bulk management (AS-010)."
- **Verdict: CONFIRMED as proposed.** The owner's wording is not just plausible, it is the literal text of
  an already-approved artifact (spec.md's own Compensation Register).

### #4 — malformed `{code}` gets the same not-found response as an unknown code

- `src/main/java/agentic/shortener/delivery/RedirectController.java`:
  `@GetMapping("/{shortCode:[A-Za-z0-9]{7}}")` — a **regex-constrained** route (exactly 7 alphanumeric
  characters). A malformed code would not even match this specific route.
- `src/main/java/agentic/shortener/delivery/LinkController.java`, the actual analytics endpoint:
  `@GetMapping("/v1/links/{shortCode}/analytics")` — **no regex constraint**. Any string, including a
  malformed one, reaches the handler, and `GetAnalyticsUseCase`'s lookup-miss produces the identical `404`
  "no link with that code" response for a malformed code and for a well-formed-but-unissued code alike.
- The new expiry endpoint (`GET /v1/links/{code}/expiry`) follows the same `/v1/links/{shortCode}/...`
  family pattern as analytics, not `RedirectController`'s differently-constrained route.
- **Verdict: CONFIRMED as proposed**, with the precise mechanism now on record: the route itself imposes no
  format constraint, so a malformed code and an unissued code both fall through to the same use-case-level
  lookup-miss, which is the source of the shared `404` — this is existing behavior of the delivered pattern
  the new endpoint follows, not a new rule being introduced for it.

## The final, self-contained requirement (supersedes CR-048's wording)

See `docs/evidence/ds-a/design.md`, "The input (revised, CR-050 — current)" for the full text as submitted
to T132. It states, directly and exhaustively, in coherent prose rather than a checklist:

- Response shape, unit, and type: `secondsRemaining` — whole seconds, integer ≥ 0; `expiresAt` — ISO-8601
  UTC or `null`.
- Non-expiring link: both fields `null`.
- Boundary instant `now == expiresAt`: expired (restated directly, since S3 cannot see `ExpiryPolicy`).
- Rounding: floored (owner's genuine choice, with its own stated reason).
- Expired-link shape: `secondsRemaining = 0`; `expiresAt` continues to report the original, unaltered stored
  timestamp (this closes attempt 3's own finding #1, which CR-048's wording never answered).
- Retention: never purged, queryable indefinitely in the expired shape, never falling into not-found on
  account of having expired.
- Owning-creator identity: stated as an explicit domain invariant (creator recorded at creation time; no
  ownership-transfer concept in this system, so creator and owner always name the same principal).
- Delivery medium: HTTP `GET` returning JSON.
- Refusal shapes: `401` for an unauthenticated caller (unchanged from CR-048); `404`, identical to an
  unknown code, for every other non-owner case — a different owner's code, an unissued code, or a
  syntactically malformed code (the new addition this CR makes explicit).

## Scope

**What changed**: the DS-A scenario's own input text (`docs/evidence/ds-a/design.md`), and the string
literal `DsALiveRun.REQUIREMENT` that feeds it into the real `Conductor` for the live run. No production
code, no policy, no architecture, no approved spec/plan/tasks artifact changes.

**What did not change**: `ExpiryPolicy`, `LinkController`, `RedirectController`, `ShortLinkRepository`, and
every other file cited above — all read for verification, none modified. CR-048's split 401/404 refusal
shape is carried forward unchanged. CR-049's materiality calibration is carried forward unchanged; this CR
does not touch S3 itself, only the text S3 evaluates.

## Conditions attached to the approval

1. **#3 and #4 verified against real source before folding in**, not asserted from the owner's proposed
   wording — honoured, both sections above.
2. **Requirement made exhaustive** — every dimension all four attempts collectively surfaced is answered by
   name in the final wording, not just the four newest items — honoured, see the table in
   `docs/evidence/ds-a/design.md`'s "1. Complete" section.
3. **No gate-decision record** — honoured; this file is the only record of this revision, filed under
   `docs/governance/change-control/`, not `docs/governance/gate-decisions/`.
4. **Prior attempts kept, not deleted** — honoured; attempts 1–4's run snapshots and
   `pending-gate-s4-context.md` remain in place, unmodified, as evidence.

## Cross-record orphan check

- CR-048 is not edited in place; its wording is retained in `design.md` under a clearly labelled
  "superseded, kept as evidence" heading, and this CR's own field table states plainly that it supersedes
  CR-048's wording specifically.
- No other applied record asserts DS-A's requirement text is CR-048's wording going forward — checked
  directly: `plan.md` and `tasks.md` reference the DS-A scenario by name and criteria, not by quoting the
  requirement's literal text, so neither is falsified by this change.
- CR-049 (the S3 materiality calibration) is unaffected and not re-decided; this CR changes only the text S3
  evaluates, not how S3 evaluates it.
- `pending-gate-s4-context.md`'s attempt 1–4 sections remain accurate as filed — each documents what that
  specific attempt's wording actually produced at the time; nothing here retroactively changes those facts.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If DS-A's fifth attempt still finds a genuinely material and open item despite this revision, that is a new, real, distinct finding for the owner — not evidence this CR was wrong — and per the owner's own explicit instruction it is reported once, not reworded a sixth time | T132's own re-run, immediately following this CR |
| If a future scenario's requirement text relies on a fact only visible in the codebase and not restated in the text itself, the same class of gap this CR closes could recur there too | Whenever such a requirement is next authored or audited |
