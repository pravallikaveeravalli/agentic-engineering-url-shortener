# CR-053 — DS-A clean-pass subject switched to a zero-runtime-behaviour test-addition

| Field | Value |
|---|---|
| **Change request** | CR-053 |
| **Title** | Retire the `GET /v1/version` endpoint from DS-A's clean-pass role; demonstrate the clean pass on a test-addition subject with no runtime behaviour to fork |
| **Raised by** | Owner's own insight after ten-plus live DS-A attempts across three requirement revisions, all genuinely, honestly held at real gates |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — switch subjects.** Conversational instruction to change the demonstration subject — **not a formal gate decision**; no `docs/governance/gate-decisions/` record is produced (CLAUDE.md's own trigger rule) |
| **Decision date** | 2026-09-22 |
| **Classification** | **LOW** — no change to any implemented behaviour, policy, architecture, or approved spec/plan/tasks artifact; changes only which requirement DS-A's live demonstration evaluates, and adds a genuine, real test-coverage gap fill |
| **Artifacts changed** | `docs/evidence/ds-a/design.md` (new "Current subject (CR-053)" section) |
| **Non-approved files changed** | `src/test/java/agentic/shortener/orchestration/conductor/DsALiveRun.java` (`REQUIREMENT` constant only); `src/test/java/agentic/shortener/delivery/ratelimit/FixedWindowCounterTest.java` (new, if S7 is ever reached — not expected this run, since DS-A stops at S6) |

---

## Why the version endpoint is retired from this role

Ten-plus live attempts across three requirement revisions of `GET /v1/version` (CR-051's original minimal
wording, CR-052's simplified wording pre- and post-sharpening) each genuinely, honestly reached a real gate —
either S4 (a real finding) or, after the owner's ratified clarifications, S6. Every one of those attempts is
real evidence, none forced, all preserved. The pattern across them, now the headline of
`docs/evidence/ds-a/requirement-completeness-ceiling-finding.md`: a real, non-deterministic AI detector,
sharpened to gate only on a genuine behavioural fork (CR-052), will still find *some* new material-looking
item on almost any requirement that describes runtime behaviour — because runtime behaviour, however minimal,
still has edges (header exactness, auth scope, code-boundary questions, and so on) a thorough model can keep
finding. This is not a flaw in the detector or a defect in the requirement; it is what CR-052's own bar
(`does resolving this change a behaviour the system MUST exhibit?`) will keep finding real answers to as long
as there is behaviour to describe.

**The owner's insight, and this CR's action on it**: pick a greenfield change with structurally **no runtime
behaviour at all**. Adding unit tests to an already-behaving, currently-untested class fits exactly — a test
verifies behaviour that already exists in already-approved, already-delivered code; it does not introduce or
change any behaviour the system exhibits, so there is no behavioural fork for CR-052's own sharpened
predicate to ever find material, by construction rather than by luck.

## The chosen subject, and why it is a genuine gap, not a contrived one

**`FixedWindowCounter`** (`src/main/java/agentic/shortener/delivery/ratelimit/FixedWindowCounter.java`,
T054, FR-URL-016/EC-013) — a package-private, 85-line per-key fixed-window rate counter shared by both
`CreationRateLimiter` and `RedirectRateLimiter`. Verified genuinely untested, not assumed: no
`FixedWindowCounterTest`/`IT` exists anywhere in `src/test/`, and a repo-wide search for direct construction
(`new FixedWindowCounter(...)`) or any other direct reference to the class by name outside its own package
returns nothing. Its behaviour is exercised only *indirectly*, as a private implementation detail, through
`RateLimiterTest`'s own tests of the two wrapper classes — which do assert some of its behaviour incidentally
(the positive-limit constructor guard, one retry-after value) but never test the class's own direct contract,
and never exercise its pruning behaviour (the 10,000-entry threshold) at all, since no existing test
constructs anywhere near that many distinct keys. This is a real, if narrow, coverage gap: a core piece of
rate-limiting logic has never been given a test that owns its own contract.

Its behaviour is also **fully determined by its own existing code**, with no interpretation required:
constructor validation, the count-vs-limit decision, window-rollover timing, retry-after computation (floored
at one second), and the prune threshold are all already written, already reviewed, already delivered. Adding
tests for them forks nothing.

## The final requirement

> Add unit tests for `FixedWindowCounter`
> (`src/main/java/agentic/shortener/delivery/ratelimit/FixedWindowCounter.java`), a package-private per-key
> fixed-window rate counter, covering its existing, already-defined behaviour across its public surface: the
> constructor's positive-limit validation (throws `IllegalArgumentException` for a non-positive limit, per
> its own existing message); the `check(key, tier)` decision (returns an allowed `RateLimitDecision` while a
> key's count within the current one-minute window is at or below the configured limit, and a throttled
> `RateLimitDecision` once the count exceeds it, with `retryAfterSeconds` computed from the remaining time in
> the window and floored at one second); window rollover (a key's window resets to a fresh count of one once
> the prior window's one-minute duration has elapsed); and the pruning behaviour (an entry whose window has
> already expired is removed once the map's size exceeds the existing 10,000-entry threshold). No
> production-code change; this adds test coverage only, using an injected `Clock` to control time
> deterministically, matching this codebase's own existing pattern for testing time-dependent logic.

## Scope

**What changed**: DS-A's own scenario subject (`docs/evidence/ds-a/design.md`) and the string literal
`DsALiveRun.REQUIREMENT`. No production code changes — the requirement itself states this explicitly, and it
is true: `FixedWindowCounter` is read for verification, not modified.

**What did not change**: the version-endpoint arc (CR-051/CR-052) and its own evidence are unmodified,
retained in full as real evidence of the completeness-ceiling finding.

## Conditions attached to the approval

1. **The version-endpoint saga preserved intact, not deleted** — honoured; all prior evidence remains.
2. **The replacement chosen for a genuine, verified coverage gap**, not a contrived placeholder — honoured;
   verified against real source (no existing direct test, behaviour fully determined by existing code).
3. **No gate-decision record** — honoured.
4. **A hard cap on this turn's own attempt, per the owner's own explicit instruction**: if S3 still surfaces
   a material finding even on this zero-behaviour subject, this agent stops, does not chase a clean re-run,
   and does not switch subjects again — the clarification-path greenfield run already completed
   (`docs/evidence/ds-a/pending-gate-s4-context-v1-version.md`) stands as the settled DS-A outcome either way.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If DS-A's live run against this subject still finds a material item, that is decisive evidence worth recording plainly: the sharpened detector cannot yield a silent clean pass at all, on any subject tried so far — a stronger, more specific claim than the completeness-ceiling finding's own current wording, worth its own dated addendum | Immediately following this run, whichever way it goes |
