# DS-A live run — S4 `UNRESOLVED_AMBIGUITY` gate decision (header-conformance clarification)

**Scope note, to avoid any confusion with this repository's own SpecKit lifecycle gates**: the
`gate-0N-<stage-slug>.md` files directly under `docs/governance/gate-decisions/` record decisions at
*this repository's own* seven SpecKit development gates (constitution, specify, clarify, ADR, task-plan,
scope-control, contract-baseline) — a fixed numbering, already in use (e.g. `gate-04-adr.md` is Gate 4 in
that sequence). This file is a **different thing**: a gate decision recorded live, by the human owner,
inside a **product-internal** orchestration run this repository builds and demonstrates — DS-A's own T132
scenario, at its S4 stage (`UNRESOLVED_AMBIGUITY`, one of the orchestration pipeline's twelve stages, not one
of the seven SpecKit gates). It is nested under `ds-a/` specifically so it is never read as, or confused
with, a numbered SpecKit gate record. `GateDecision.repositoryRecordPath` (the orchestration product's own
schema, `contracts/openapi.yaml`) requires a path under `docs/governance/`, which this satisfies.

| Field | Value |
|---|---|
| **Gate** | S4 — `UNRESOLVED_AMBIGUITY`, DS-A scenario, live run T132 (requirement: `GET /v1/version`, CR-052's wording) |
| **Outcome** | `APPROVED` |
| **Deciding human** | Pravallika Veeravalli |
| **Decision date** | 2026-09-22 |
| **Artifact approved** | Two clarifications resolving the run's own real, live `MATERIAL_PENDING` findings (captured verbatim in `docs/evidence/ds-a/run-snapshot-ATTEMPT-7-STOPPED-AT-S4-post-CR052-sharpening.md`): the `Content-Type: application/json` and `Cache-Control: no-store` header-value conformance semantics for the `GET /v1/version` endpoint |
| **Where else recorded** | `clarification_decision`/`ambiguity_record` tables (via `LineageStore.resolveWithClarification`, the same mechanism T139 will use for DS-C); `docs/evidence/ds-a/run-snapshot-ATTEMPT-8-...md` (the resumed run's own evidence, this attempt); `docs/evidence/ds-a/pending-gate-s4-context-v1-version.md` (the two findings this decision answers) |

## Reason / verification performed

The two remaining `MATERIAL_PENDING` findings from attempt 7 (real, live `claude-sonnet-5` output, not
fabricated — see `run-snapshot-ATTEMPT-7-...md`) both asked the same shape of question: does this
requirement demand an *exact string match* on a response header's value, or a *semantic* match tolerant of
standard, conformant additions a real HTTP framework commonly makes? The owner answered both, in her own
terms:

1. **`Content-Type`**: "the response media type is `application/json`; a standard `charset` parameter (e.g.
   `; charset=UTF-8`) is acceptable and conformant. Conformance is asserted on the media type being
   `application/json`, charset-agnostic — not an exact byte-for-byte string match."
2. **`Cache-Control`**: "the response is not cached; `Cache-Control: no-store` satisfies this, and additional
   standard non-caching directives (`no-cache`, `max-age=0`) are acceptable. Conformance is asserted on the
   response being non-cacheable (a `no-store` directive present), not an exact string match."

Both resolutions were reached by the human owner directly, at the live gate — this agent performed no
independent verification of them (there is no existing artifact to check them against; they are the owner's
own, genuine choices about what this new endpoint's requirement means, not restatements of pre-existing
system behaviour), consistent with `ActorAuthority`/FR-ORC-021: the deciding actor is the human, not the
agent, and the agent's role here is limited to recording the decision faithfully and resuming the run.

## Conditions attached to the approval

1. **S7's implementation and S8's test suite for the real `GET /v1/version` endpoint (if this run proceeds
   that far) MUST assert semantic conformance** — media-type equality for `Content-Type` (charset parameter
   ignored), and directive-presence for `Cache-Control` (a `no-store` directive present, additional standard
   directives tolerated) — **not exact string equality** on either header's full value.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| T139's own use of `LineageStore`'s `ClarificationDecision` mechanism (DS-C's eventual clarification) should follow this same precedent: real, live findings persisted via `recordRequirement`/`recordAmbiguity`, resolved via `resolveWithClarification`, with the human actor's real name recorded, before the governing `GateDecision` is recorded and the run resumed | T139, when the owner's DS-C clarification decision is recorded |
| If S7 (or any later stage) implements `Content-Type`/`Cache-Control` handling as an exact string match rather than the semantic match this decision specifies, that is a real conformance defect against this recorded decision, not a style choice | S7 onward, this same run, if it proceeds |
