# Baseline omissions register

**Task T055a.** Governs: FR-URL-016, PVT-014, CN-007.
Referenced by: T055's `Artifact` field, T136a, T147 (`docs/LIMITATIONS.md`), plan §14.

---

## What this file is, and what it is not

This register holds **deliberate omissions from the baseline implementation that have a named run to
close them**. Nothing else belongs here.

The distinction is the whole point, and it is easy to blur:

| | Where it lives | What it means |
|---|---|---|
| **Scheduled omission** | **this file** | Built later, by a **named** run. The requirement stays binding in full. |
| **Indefinite deferral** | `docs/delivery/backlog.md` (T002) | Nobody has committed to building it. Not promised, not scheduled. |
| **Gap** | **neither** — reported as a gap | An entry that has **lost its closing run**. It stops being a scheduled omission the moment nobody owns closing it. |

**An entry without a named closing run is not an entry.** It is a gap, and it must be reported as one
rather than kept here where it would read as planned work.

## Why this file exists at all

Deferring part of a **binding** requirement is only defensible if the omission is written down, with the
requirement still named as binding and a specific run committed to closing it. Without that, a deferral
is indistinguishable from an oversight — to a reviewer, to the release-readiness report, and in six
months to the person who made it.

Plan §14 promises that release readiness reports this gap. **This file is the artifact behind that
promise.** If it is missing, the promise has nothing underneath it.

---

## Entry 1 — Per-creator aggregate redirect rate limit (PVT-014)

| Field | Value |
|---|---|
| **Omitted from** | Slice 3 (core URL behaviour) |
| **Status** | **Closed** — 2026-09-22, at T136a |
| **Recorded** | 2026-09-21, at T055a |

### 1. What is omitted

The **third** of FR-URL-016's three rate-limit tiers: redirects limited **per creator, aggregated across
all of that creator's links**, at **PVT-014 — 3,000 requests per minute**.

Two tiers **are** built and enforced:

- **PVT-012** — creation, 60/minute per creator (T054).
- **PVT-013** — redirects, 600/minute **per short code** (T055).

The omitted tier is the noisy-neighbour control. PVT-014 sits **deliberately below the sum of the
per-code limits** — five links at 600 each would be 3,000, and a creator with fifty links could put
30,000 requests a minute through the service while every individual link stayed inside PVT-013. That gap
between "each link is fine" and "this account is not" is exactly what the aggregate tier exists to close,
and it is what is currently open.

There is **no configuration key** for this tier and **no partial implementation** of it. Both are
deliberate and both are asserted by `RateLimiterTest`: a key with no enforcement behind it reads as a
working control, and a half-built tier would look present in a review while enforcing nothing.

### 2. Which requirement it belongs to

**FR-URL-016** — *Rate limiting, two-sided and two-tier* — together with **PVT-014**, the owner-approved
target for this tier, and **EC-013**.

### 3. The requirement remains binding in full

**FR-URL-016 is binding in full. It is not amended, narrowed, or partially satisfied by this
register.**

Nothing here reduces what the requirement demands. The service does not currently meet FR-URL-016 in
full, and saying so plainly is the point of the entry. This is a statement about **when** the third tier
is built, never about **whether** it is required.

In particular: the accepted trade-off FR-URL-016 records — that followers of a popular creator's links
may be throttled through no fault of their own — remains the accepted trade-off for the tier **when it is
built**. It is not a reason the tier is absent.

### 4. The run that closes it

**T136a**, in the brownfield scenario (DS-B).

This omission is not incidental to that scenario; it **is** its subject. DS-B demonstrates changing a
system that already exists, and it needs a real before-state to change. The before-state is this: the
multi-link aggregate case passes **unthrottled** against the service as Slice 3 leaves it. T136a then
adds the tier and the same case is throttled.

That is why the tier is deferred rather than simply built now — and it is also why the deferral has to be
written down. A before-state that nobody recorded in advance is indistinguishable from a bug discovered
later and relabelled.

**Ordering that matters:** this entry must exist **before T057's acceptance sweep** records the
multi-link case as passing unthrottled. Reversed, the sweep would document a gap with no disclosure
behind it, and the record would be written to fit the finding rather than the finding assessed against
the record.

### 5. What release readiness must report if T136a does not happen

If T136a does not run, or runs and does not close this tier, then **release readiness must report
FR-URL-016 as NOT MET**, in these terms:

> FR-URL-016 requires three rate-limit tiers. Two are implemented and enforced (PVT-012, PVT-013). The
> per-creator aggregate redirect tier (PVT-014) is **not implemented**. A creator with many links, each
> individually within PVT-013, can exceed PVT-014 without being throttled. This is a **known,
> disclosed gap**, recorded before the fact in `docs/delivery/baseline-omissions.md`, and it is **not**
> an accepted risk: no authority has accepted it, and none has been asked to.

It must **not** be reported as "substantially met", "met with a minor exception", or "met for the
demonstration scope". Two of three is not a rounding error on a requirement whose whole purpose is to
bound traffic, and the tier that is missing is the one that catches the traffic shape the other two are
designed to let through.

If the omission is ever to become permanent, that is a **different decision** requiring the exception
procedure in the constitution's §Exception procedure — an approving authority, a compensating control, a
residual-risk statement and an expiry. This register is not that, and must not be read as that.

### 6. Closure

**T136a ran and closed this tier, 2026-09-22.** `AggregateRedirectLimiter`
(`src/main/java/agentic/shortener/delivery/ratelimit/AggregateRedirectLimiter.java`) enforces PVT-014
(3,000 requests/minute per creator, aggregated across every link that creator owns), independently of the
existing per-code tier, using the same in-process fixed-window counter mechanism. Built directly rather
than authored by a live AI dispatch through the orchestrator — three genuine, live orchestrator attempts
were made in prior turns (real retry and compensation evidence captured, `docs/evidence/ds-b/`), and the
owner directed this turn that the tier itself be built directly, reliably, rather than risk a further
live-AI variance round; that choice, and the reasoning behind it, is recorded honestly in
`docs/evidence/ds-b/` rather than presented as orchestrator output. FR-URL-016 is now MET in full — all
three tiers implemented and enforced. Section 5 above remains as written: it was true of the state before
this closure and stays in the record as the honest statement of what would have had to be reported had
T136a not landed.

---

## Register integrity

These properties are asserted rather than trusted. A register nobody checks is a comment.

| Property | Asserted by |
|---|---|
| This file exists, and names PVT-014, FR-URL-016, "binding", T136a and release readiness | `RateLimiterTest.theDeferredTierIsDisclosed` |
| This entry's own status reads Closed, now that T136a has landed | `RateLimiterTest.theAggregateTierIsNowBuiltAndDisclosedAsClosed` |
| The configuration key for the now-built tier carries the approved target (3000) | `RateLimiterTest.throttlingIsOnByDefault` |
| Every entry has a named closing run | `BaselineOmissionsTest.everyEntryNamesAClosingRun` |
| This register and `docs/LIMITATIONS.md` do not disagree | T147, when `LIMITATIONS.md` is written |

**Current entries: 1. Entries without a named closing run: 0.**
