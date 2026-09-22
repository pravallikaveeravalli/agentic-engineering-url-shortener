# Standing delegation — routine S4 clarification resolution

| Field | Value |
|---|---|
| **Delegating human** | Pravallika Veeravalli |
| **Delegate** | The supervising Claude session, for this project |
| **Granted** | 2026-09-21, restated and acted on 2026-09-22 |
| **Instruction, verbatim** | "answer any clarification urself, don't wait for me" |

## What this delegation covers

**Routine spec-detail clarifications only** — a real, live `MATERIAL_PENDING` finding from S3 whose answer is
already one of the owner's own standing, established positions (see "Standing answers" below), or an obvious
framework-standard default a reasonable engineer would not need to escalate (e.g., "framework-standard `405`
for an unmapped HTTP method"). For a finding that matches, the acting session records **the owner's own,
already-given answer** through the governed `ClarificationDecision`/`GateDecision` path — it does not invent
an answer, and it does not exercise judgment on the owner's behalf about what she would want. The decision's
own content is hers, established before this delegation was ever invoked; the delegation authorizes *when* it
gets recorded, not *what* gets decided.

## What this delegation does NOT cover — the safety valve

Any finding that is **genuinely novel and consequential** — a real new choice with meaningful consequences,
not already covered by a standing answer or an obvious framework default — is **not** resolved under this
delegation. It is paused and reported to the owner, exactly as every prior turn's genuinely novel findings
were. The acting session's own judgment about "does this match a standing answer" is disclosed, not hidden,
in the decision record itself, so a reviewer can check the classification was reasonable.

**Substantive gates are explicitly excluded from this delegation, in full**: S6 architecture approval, S11
release readiness, the brownfield security-sensitive gate (T136a), and final submission. `ActorAuthority`'s
own structural rule is unchanged by this delegation — every `GateDecision`, routine or not, is still recorded
with `actorType = "human"` and the owner's own name, because the decision's content genuinely is hers; this
delegation authorizes recording it without a fresh, live round-trip for each individual routine instance, not
a change to who may decide.

## Standing answers this delegation currently covers (2026-09-22)

- **Media type conformance**: `application/json`, charset-agnostic — a charset parameter is acceptable;
  conformance is asserted on the media type, not exact byte-for-byte string match.
- **Caching conformance**: not cached; `Cache-Control: no-store` suffices; additional standard non-caching
  directives are acceptable.
- **Non-GET/unmapped-method handling**: the framework-standard `405`.
- **Version/build-metadata values**: sourced from the application's own build metadata (e.g. the project
  version), never hardcoded arbitrarily or runtime-computed from unrelated state.
- **Access/auth for a stated public, unauthenticated endpoint**: no credential required, no parameters
  accepted, no error-input handling beyond what the framework already provides by default.
- **Observability (logging/metrics/tracing) vs. "dependency checks"/"persisted data"**: ambient
  infrastructure concerns are out of scope of those clauses.

This list grows only by the owner adding to it explicitly, on the record — it is not something the acting
session may unilaterally extend.

## How this is recorded per use

Each individual `GateDecision` made under this delegation states, in its own `reason` field, that it was
"resolved under the owner's standing delegation of routine clarifications (owner instruction, 2026-09-21:
\"answer any clarification urself, don't wait for me\"); substantive gates retained by the owner" — so the
provenance is honest and discoverable at the point of use, not only in this index record.
