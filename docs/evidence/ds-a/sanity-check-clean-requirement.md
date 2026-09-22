# Greenfield clean-requirement sanity check (CR-062), before the full live run

Real, live S1→S2→S3 only (`GreenfieldCleanRequirementSanityCheck`, no `Conductor`, no database), against the
clean requirement (CR-062):

> Add a public endpoint GET /v1/version that requires no authentication and takes no path or query
> parameters, returning HTTP 200 with Content-Type: application/json, body {"version": "<the application's
> version>"}, and header Cache-Control: no-store.

## S3's real output, in full

3 `MATERIAL_PENDING`, 4 `NOT_MATERIAL`. Full raw JSON captured in the sanity-check's own console output;
summarized here:

**MATERIAL_PENDING:**

1. **UNDEFINED_TERM** — the `"version"` field's format/type and which version it denotes (semver string, raw
   build number, git commit SHA, structured object; release version vs. the `/v1/version` path's own API
   version) is unspecified.
2. **MISSING_ACCEPTANCE_CRITERIA** — no requirement states what happens if a client sends unexpected query
   parameters (reject with 400, or ignore and proceed) — the model explicitly checked and confirmed no
   single uncontested framework default exists here (frameworks genuinely differ), unlike the method-not-
   allowed case below, which it correctly treated differently for exactly that reason.
3. **MISSING_ACCEPTANCE_CRITERIA** — requirement 1b only covers the credential-ABSENT case ("without
   credentials SHALL NOT be rejected"); it says nothing about a request that DOES present credentials, and
   whether invalid/malformed ones may be rejected.

**NOT_MATERIAL** (all four with substantive `noClarificationReason`, none disputed): a self-referential
restatement of `no-store`'s own RFC 9111 semantics; the framework-standard 405 for non-GET methods (an
uncontested default, correctly distinguished from finding #2 above); the response object's openness to extra
fields (plain-language "containing" reading); which caller types "no authentication" applies to (plain-
language reading covers all callers).

## Judgment: genuinely material, and all three routine (already covered by the standing delegation)

All three `MATERIAL_PENDING` findings are real, CR-007-grounded behavioural forks — not the detector
over-firing on a non-behavioural detail. Examined against the six standing delegated answers
(`docs/governance/delegations/routine-clarification-delegation.md`):

1. → **`VERSION_VALUE_CLARIFICATION`** ("the application's own build version string, sourced from build
   metadata... never a hardcoded arbitrary literal") directly answers the format/source question. Routine.
2. → **`ACCESS_CLARIFICATION`** ("no parameters accepted, no error-input handling beyond framework default")
   directly answers the unexpected-query-parameter question. Routine.
3. → **`ACCESS_CLARIFICATION`**, same answer — the endpoint has no credential-checking mechanism at all, so
   "invalid credentials" cannot be distinguished from "no credentials"; the framework default (nothing
   inspects the header) applies uniformly. Routine.

**A real bug was found and fixed as a direct result of this sanity check** (the whole reason PART 1 exists):
finding #2's own real text used the plural "query parameters", which CR-061's own word-boundary fix
(`\bparameter\b`) does NOT match — a genuine regression in last turn's own fix, caught here before it could
stall the live run. Finding #3's own real text never uses the word "access" at all, so the matcher's own
`access`+`auth` compound condition would not have caught it either. Both fixed as CR-063 (`parameter`/
`parameters`/`credential`/`credentials` now all standalone triggers), verified by two new regression tests
built directly from this sanity check's own real output, before the full run was attempted.

## Conclusion

Proceeding to PART 2 (the full live run) with justified confidence: the requirement is genuinely clean (no
NOT_MATERIAL finding was wrongly suppressed; all NOT_MATERIAL findings have real, substantive reasons), every
MATERIAL_PENDING finding is real and already covered by the owner's own standing delegation, and the matcher
that will apply those answers during the full run has now been verified against this exact real output.
