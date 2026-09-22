# Finding — requirement completeness does not converge for a feature with real surface

## Why a clean greenfield pass was so hard — in plain language, first

A reviewer's first question is fair: why did the simplest possible demonstration — submit a well-written
requirement, watch it sail through with no gate — take this many live attempts? Five points explain it, and
none of them is a defect in what was built.

1. **The ambiguity detector is a real AI stage, on purpose, and that makes it non-deterministic.** `spec.md`
   deliberately chose an AI-backed stage for S3 rather than a rule-based checker, because only a real model
   can catch a *semantic* contradiction — a conflict between two ideas, not two clauses bound to the same
   field, which is exactly DS-C's own scenario. The same choice that gives S3 this real capability also
   means it is not a fixed function of its input: **the identical requirement text, submitted on three
   separate live runs, produced three different single findings** (a header-value exactness question, then
   a path-segment routing question, then an observability-scope question — never the same one twice, never
   zero). This is a documented, accepted property of choosing a real model for real semantic judgment, not a
   flaky test or an implementation bug.
2. **S3 reads only the requirement text — it has no access to the codebase.** `StageInput`'s own closed
   field list (proven by `StageExecutorContractTest`) gives it nothing else. Any fact that is "obviously"
   already true of the delivered system — an existing auth filter's ordering, an existing retention policy,
   an existing rounding convention — reads to S3 as *unstated*, because from its own vantage point it has no
   way to distinguish a true fact it cannot see from a false one. Every revision in this arc worked by
   finding the next such fact and writing it directly into the requirement text.
3. **Requirement completeness against a thorough detector has a real ceiling.** A feature with any surface
   at all — even a handful of clauses — tends to have one more boundary, default, or cross-reference a
   careful reader could still ask about. This was not unique to a rich feature: even a maximally minimal,
   authentication-free, input-free, fixed-output version endpoint drew real findings on its first several
   attempts.
4. **The detector was calibrated twice, and both times the fix was proven correct, not just applied.**
   CR-049 added the missing materiality step (a detected ambiguity now has to actually matter before it
   opens a gate); CR-052 sharpened that step further, after evidence showed it was still over-weighing
   linguistic nitpicks that changed no real behavior. After each fix, the same non-negotiable check was run
   live: does the detector *still* catch a genuine, textbook contradiction? It did, every time — the DS-C
   compensating checks and the live ambiguous-scenario run (T138) all still trip the gate on the real
   contradiction, proving the fixes made the detector more accurate, not softer.
5. **Therefore, the honest greenfield demonstration is not a single clean run — it is the governed
   clarification path itself.** The detector surfaces a genuine, real question; the human owner resolves it
   on the record, with a real question, a real answer, and a real timestamp; the run proceeds. That is
   exactly DS-A's own negative acceptance criterion — **a gate must never fire *merely to demonstrate gates
   exist*** — satisfied by construction, because every gate that opened here opened on a real finding, not a
   manufactured one. It also draws the contrast with the ambiguous scenario (DS-C) cleanly: DS-C's
   contradiction is a *fundamental* one that forces a replan of the affected work; DS-A's findings are
   narrower conformance questions the owner answers once and the run simply continues past. Both are the
   same governed mechanism working correctly on two different kinds of real ambiguity — this is a genuine
   finding about how AI-based requirements analysis actually behaves, worth presenting as an asset, not
   something to explain away.

---

Status: **CLOSED as a documented finding, retired from the DS-A clean-pass role.** The expiry endpoint is no
longer DS-A's demonstration subject (see `docs/governance/change-control/CR-051-...md` for the replacement
and the full rationale for retiring it). This document is the honest record of what five live attempts, two
independent models, and one calibration fix actually showed — preserved as an asset for a reviewer, not a
loose end. **See the "Correction (2026-09-22, CR-052)" section below**: part of what this document originally
concluded was an irreducible completeness ceiling was, on later evidence, a correctable detector over-fire —
corrected forward here, not edited out of the original text, per this repository's own governance discipline.

## Correction (2026-09-22, CR-052)

**What was right, and remains right, unretracted**: the five-attempt expiry arc below is real, and the two
structural facts in "The root cause, stated plainly" are both still true and still explain most of that arc
— S3 genuinely has no repository access, and its job genuinely is requirement completeness rather than
system completeness.

**What this document did not yet know when first written**: CR-051's own replacement subject — a maximally
minimal, auth-free, input-free, fixed-output `GET /v1/version` endpoint, chosen specifically to test whether
the ceiling was real or an artifact of feature richness — **also** stopped at S4 (attempt 6,
`docs/evidence/ds-a/pending-gate-s4-context-v1-version.md`), with 3 of its 4 findings tracing to a single
closing, summarizing clause and changing no actual required behaviour. That result showed the pattern was
not purely about feature surface, as this document's original text concluded — part of it was S3's
materiality classification itself over-firing on linguistic imperfection (an undefined term with an ordinary
meaning, a clause's own internal self-reference, an unbounded phrasing no obligation constrained) rather than
applying CR-007's actual bar (does resolving this change a required, buildable behaviour?).

**The fix**: `docs/governance/change-control/CR-052-...md` sharpened S3's own materiality predicate to state
that bar explicitly and generally, removed the non-behavioural clause from the minimal subject that invited
those three findings, and proved — via three live re-runs of the DS-C compensating check
(`docs/evidence/cr-052/ds-c-compensating-check.md`) — that the sharpening did not blunt genuine detection.

**What remains true after the correction**: detection itself is thorough (reconfirmed, not weakened, by
CR-052's own compensating checks), and S3's lack of repository access is an unchanged structural fact,
orthogonal to materiality classification — restating a fact directly in the requirement text is still the
only way to make it visible to S3, and that part of the expiry arc's own lesson stands. What changed is
narrower: some of what looked like irreducible *completeness* pressure was, in fact, correctable
*materiality-classification* over-firing — a real, different bug, now fixed, not evidence against the
completeness-ceiling finding's remaining, narrower claim.

## The arc, in one line

Five live attempts against the expiry-endpoint requirement, across two independently-trained models, each
one closing every gap the last one found and surfacing new, genuine ones — with the materiality calibration
itself independently proven sound partway through. Full primary evidence, kept intact, nothing deleted:

| Attempt | Wording | Result | Evidence |
|---|---|---|---|
| 1 | Original one-sentence form, reproduced directly against Gemini (quota-exhausted mid-pipeline) | 6 gaps found outside the pipeline | `run-snapshot-ATTEMPT-1-BLOCKED-gemini-quota-exhausted.md` |
| 2 | Same original wording, live through `Conductor` against Claude | Corroborated the same class of gaps; stopped at S4 | `run-snapshot-ATTEMPT-2-STOPPED-AT-S4-ambiguity-gate.md` |
| 3 | CR-048's revision — closed the 6 gaps attempts 1–2 found | 5 new, different findings (expired-link `expiresAt`, boundary instant, rounding, unbounded 401 cross-reference, creator/owner predicate asymmetry) | `run-snapshot-ATTEMPT-3-STOPPED-AT-S4-ambiguity-gate-revised-wording.md` |
| 4 | Same CR-048 wording, first run against CR-049's calibrated S3 | Calibration proven working (2 genuine `NOT_MATERIAL` resolutions); 4 items remained `MATERIAL_PENDING` (2 recurring: rounding, boundary instant; 2 new: retention, malformed-code) | `run-snapshot-ATTEMPT-4-STOPPED-AT-S4-post-calibration.md` |
| 5 | CR-050's fully self-contained revision — closed all 4 of attempt 4's items | All 4 gone, none recurred; 3 new findings, 1 (null-owner) correctly resolved `NOT_MATERIAL`, 2 (401/404 precedence under a joint condition; caching/freshness) remained genuinely open | `run-snapshot-ATTEMPT-5-STOPPED-AT-S4-post-CR050.md` |

Full per-attempt narrative, the owner's own decisions at each juncture, and the "decision this agent cannot
make" sections are in `docs/evidence/ds-a/pending-gate-s4-context.md` — this document does not repeat that
detail, it draws the conclusion from it.

## The root cause, stated plainly

Two independent facts compound, and either alone would already explain the pattern:

**1. S3 has no repository access.** `StageInput`'s own closed field list (proven by
`StageExecutorContractTest`) gives S3 only the normalized requirement text — never the codebase. CR-007's
own materiality predicate has a clause for exactly this situation ("not already fixed by an existing
approved artifact") — but S3 can only apply that clause to a fact the requirement text states, never to a
fact only true of the delivered system. An artifact this agent (or any human reading the code) knows exists
is, from S3's own vantage point, indistinguishable from one that does not exist, unless the requirement text
says so. Every revision in this arc (CR-048, CR-050) worked by finding the next fact S3 could not see and
restating it directly in the text — and each time, restating the facts already known closed exactly those
gaps and surfaced a *different* set, never all of them at once.

**2. S3's job, per its own governing requirement, is requirement completeness — not system completeness.**
FR-ORC-010 states S3 must "detect incomplete, unclear, conflicting, or untestable requirements." A
requirement can be genuinely, correctly incomplete on a dimension that the surrounding architecture happens
to resolve (e.g., `CreatorAuthFilter`'s real, structural precedence over the handler chain) — S3's job is not
to know that the architecture resolves it, only to notice that the *requirement text* does not say so. That
is a correct application of its own stated scope, not a defect in the requirement author's effort or in the
detector's tuning.

Together, these two facts mean that for **any feature with real, non-trivial surface** — enough distinct
obligations, edge cases, and cross-references that a human author would reasonably expect to iterate on the
wording — there is very likely **one more dimension** the text has not yet stated, because the set of "facts
S3 cannot see, stated or not" is large and only discovered one revision at a time, empirically, by running the
detector and reading what it says. Nothing in this arc suggests the set is infinite or that convergence is
theoretically impossible — attempt 5 closed 4 of 4 prior items outright — but five attempts across two models
did not reach zero, and each success at closing prior gaps came with new ones found on the same pass.

## Why this is a correct behaviour, and a genuine insight — not a defect

- **The detector is not trigger-happy.** The DS-C compensating check
  (`docs/evidence/cr-049/ds-c-compensating-check.md`) ran spec.md's own canonical contradiction, verbatim,
  through the identical calibrated S3, and got 7/7 `MATERIAL_PENDING`, zero `NOT_MATERIAL` — proof the
  materiality filter does not wave through genuine contradictions to avoid friction.
- **The detector is not blind to what it should let through.** Attempts 4 and 5 both produced real,
  substantively-reasoned `NOT_MATERIAL` classifications — the creator/ownership domain invariant, the
  reference-clock instant, and the null-owner impossibility were each resolved correctly, with reasoning this
  agent verified independently matches the real system. A detector that only ever said `MATERIAL_PENDING`
  regardless of content would not have done this.
- **CR-049's calibration was the right, necessary fix regardless of this finding.** The finding here is
  orthogonal to whether S3 applies CR-007's predicate correctly (it does, proven above) — it is about the
  ceiling on how much of a *rich* feature's surface can be pinned down in a single normalized-text pass, no
  matter how well the predicate is applied.
- **This matches, rather than contradicts, DS-A's own stated premise.** `spec.md`'s DS-A section requires a
  *complete, consistent, testable* input to proceed without a gate — it does not claim every feature
  requirement can be authored to satisfy that bar on the first (or fifth) attempt. The insight this arc
  surfaces is that "complete" is a real, achievable bar for a **small-surface** feature and a much harder,
  possibly asymptotic one for a **rich** feature evaluated this thoroughly — which is exactly the distinction
  the owner's decision (CR-051) now acts on: keep the expiry feature as documented evidence of the ceiling,
  and demonstrate DS-A's clean-pass property honestly, on a feature small enough that the bar is actually
  reachable.

## What this finding recommends, and does not recommend

- **Recommends**: treating "requirement completeness" as a property that scales with feature surface, and
  expecting — not treating as a defect — that a rich feature's first specification attempt(s) will need real
  iteration against a genuinely thorough ambiguity detector, exactly as a human requirements review would.
- **Does not recommend**: weakening S3, CR-007's predicate, or the materiality calibration to force a rich
  feature through in fewer attempts — that would reintroduce the exact rigged-demonstration failure mode
  FR-ORC-028/CN-010 exist to prevent, and the DS-C compensating check exists specifically to catch.
- **Does not conclude**: that convergence is impossible in principle — only that it was not reached in five
  honest attempts on this specific feature, and that continuing to attempt it via wording revision alone,
  without new information, was assessed (by the owner) as unlikely to be the best use of further iteration.

## Provenance

All primary evidence for this finding predates this document and is unmodified by it:
`docs/evidence/ds-a/pending-gate-s4-context.md` (the full attempt-by-attempt record and the owner's own
decisions at each stop), the five `run-snapshot-ATTEMPT-N-...md` files (real, uncut model responses),
`docs/evidence/cr-049/ds-c-compensating-check.md` (the compensating verification), and
`docs/governance/change-control/CR-048-...md`, `CR-049-...md`, `CR-050-...md` (the three revisions this arc
produced). Nothing in that evidence trail is deleted, edited in place, or superseded by this document — this
document only states the conclusion the trail as a whole supports.
