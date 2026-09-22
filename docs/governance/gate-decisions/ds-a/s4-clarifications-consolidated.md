# DS-A live run — S4 `UNRESOLVED_AMBIGUITY` gate decisions, consolidated

**Scope note**, restated from the file this one supersedes: this directory (`docs/governance/gate-decisions/
ds-a/`) records decisions at **product-internal** orchestration-run gates (DS-A's own S4/S6/etc.), never at
this repository's own numbered SpecKit lifecycle gates (`gate-01` … `gate-07`, directly under
`docs/governance/gate-decisions/`) — a different thing entirely.

**Why this file exists**: DS-A's real, live S4 gate does not always find the same residual question twice.
Across independent live attempts against the identical `GET /v1/version` requirement text, S3 found three
genuinely different single `MATERIAL_PENDING` findings — never the same one twice, never zero. Rather than
one gate-decision file per question (which would force a fresh file, and a fresh `repositoryRecordPath`
reference in code, every time a run happens to surface a different subset), this file consolidates every
question the owner has ratified a real answer for so far. Whichever subset an actual live run's S4 gate
matches, this is the one record its `GateDecision` cites.

| Field | Value |
|---|---|
| **Gate** | S4 — `UNRESOLVED_AMBIGUITY`, DS-A scenario, live run T132 (requirement: `GET /v1/version`, CR-052's wording) |
| **Outcome** | `APPROVED` |
| **Deciding human** | Pravallika Veeravalli |
| **Decision date** | 2026-09-22 |
| **Artifact approved** | Three clarifications, each resolving a real, live `MATERIAL_PENDING` finding this scenario's S4 gate has actually produced, across separate live attempts: `Content-Type` header-value conformance, `Cache-Control` header-value conformance, and observability (logging/metrics/tracing) scope |
| **Where else recorded** | `clarification_decision`/`ambiguity_record` tables (via `LineageStore.resolveWithClarification`); `docs/evidence/ds-a/run-snapshot-ATTEMPT-*.md` (each attempt's own real findings); `docs/evidence/ds-a/pending-gate-s4-context-v1-version.md` |

## Reason / verification performed

Three questions, each answered by the owner directly, in her own terms, at the live gate that raised it:

1. **`Content-Type`** (attempt 7): "the response media type is `application/json`; a standard `charset`
   parameter (e.g. `; charset=UTF-8`) is acceptable and conformant. Conformance is asserted on the media
   type being `application/json`, charset-agnostic — not an exact byte-for-byte string match."
2. **`Cache-Control`** (attempt 7): "the response is not cached; `Cache-Control: no-store` satisfies this,
   and additional standard non-caching directives (`no-cache`, `max-age=0`) are acceptable. Conformance is
   asserted on the response being non-cacheable (a `no-store` directive present), not an exact string
   match."
3. **Observability scope** (attempt 9): "No. Standard framework request-logging and metrics are ambient
   infrastructure cross-cutting concerns — not application 'dependency checks' (which mean downstream
   service calls such as the database) and not application data persistence. The endpoint makes no
   downstream calls and writes no application data; ambient infrastructure logging is out of scope of that
   clause."

None of the three is a restatement of pre-existing system behavior verifiable against source — each is the
owner's own genuine choice about what this new endpoint's requirement means. This agent performed no
independent verification of any of them, consistent with `ActorAuthority`/FR-ORC-021: the deciding actor is
the human, not the agent.

**A fourth real attempt (attempt 8) surfaced a still-different question** (path-segment routing behavior)
that the owner has not answered; it is **not** covered by this decision, and any future live run that
surfaces it again will still correctly stop and report rather than being approved past silently — this
file's own governing code (`DsALiveRun.applyOwnerClarificationAndResume`) only ever applies an answer to a
finding that actually matches one of the three questions above.

## Conditions attached to the approval

1. **S7's implementation and S8's test suite for the real `GET /v1/version` endpoint (if any run proceeds
   that far) MUST assert semantic conformance** on `Content-Type`/`Cache-Control` (not exact string
   equality), and **MUST NOT treat ambient request logging, metrics, or tracing as a violation** of the
   no-dependency-checks/no-persisted-data clauses.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If a future live S4 gate on this same requirement surfaces yet another genuinely new question (as attempt 8's routing finding already did once), it must be reported and answered on the record the same way, not silently folded into this file's existing answers | Whenever a future DS-A attempt runs |
| T139's own use of the `ClarificationDecision` mechanism (DS-C) follows this same precedent | T139 |
