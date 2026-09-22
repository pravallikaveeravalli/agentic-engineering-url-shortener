# CR-057 — Architecture gate made conditional on S6's own materiality classification, matching plan §5's own
trigger; clarification-gate sharpening deliberately NOT done as a code change

| Field | Value |
|---|---|
| **Change request** | CR-057 |
| **Title** | `Conductor` gated S6 unconditionally, contradicting plan §5's own stated trigger ("S6 produces material design decisions"); fixed to mirror S4's own conditional-gate shape exactly |
| **Raised by** | Owner's own direction, this session (2026-09-22): "the architecture gate is firing on EVERY run, contradicting plan.md §5's own trigger... Fix it — same class of fix already done for the ambiguity gate" |
| **Decided by** | Pravallika Veeravalli |
| **Decision** | **APPROVED — PART 1 implemented and verified as directed. PART 2 (clarification-gate "sharpening") deliberately NOT implemented as a code change — see the dedicated section below explaining why, a considered judgment call flagged for the owner's own review, not silently substituted.** |
| **Decision date** | 2026-09-22 |
| **Classification** | **MEDIUM** — production behavioural change to a mandatory human gate's own trigger condition; verified against the constitution and plan.md before implementation, not assumed from the owner's own paraphrase |
| **Artifacts changed** | `src/main/java/agentic/shortener/orchestration/executor/ai/stages/DesignAiExecutor.java`, `src/main/java/agentic/shortener/orchestration/conductor/Conductor.java` |
| **Non-approved files changed** | `DsALiveRun.java` (S6 handled conditionally, mirroring its own existing S4 pattern), `ArtifactAliasingIT.java`/`ExistingFileInjectionIT.java` (stub S6 designs given explicit `materialDesignDecisions` so their own S6-gate assertions keep exercising a real gate), new tests: `ArchitectureGateConditionalityIT.java`, `DesignAiExecutorTest.java` additions |

---

## Verified before acting, not assumed

Before touching a mandatory human gate, the constitution's own text was read directly (`.specify/memory/
constitution.md` §III Human Governance: "A mandatory gate MUST NOT be skipped, auto-satisfied, back-dated, or
bypassed") and plan.md §5's own Human-in-the-Loop Controls table was read directly. **The owner's claim is
exactly correct, verbatim**: the table's own trigger column reads "S6 produces **material** design decisions"
for the architecture-approval gate — the identical conditional shape as "S3 finds **material** ambiguity" for
the unresolved-ambiguity gate, which `Conductor` already implements conditionally (`handleClarificationGate`).
`Conductor.APPROVAL_REQUIRED_STAGES` gated S6 **unconditionally** — every design, however trivial, opened a
human gate — a real, verifiable divergence from the plan's own specification, not a weakening of a
constitutional obligation: the constitution requires humans to own architecture, and CR-057 satisfies that by
requiring a human decision precisely when a design contains a genuine choice, mirroring how the ambiguity gate
already satisfies "humans own requirement interpretation" only for material ambiguities.

No ADR asserts unconditional S6 gating (searched every `docs/governance/adr/*.md` for "architecture gate",
"ARCHITECTURE_APPROVAL"; no hits outside this fix's own new references) — no ADR amendment needed.

## The fix

`DesignAiExecutor` (S6) now classifies its own design's materiality, using CR-007's definition verbatim
(reused, not reinvented — the same predicate `AmbiguityDetectionAiExecutor` already applies to ambiguities):
`"materialDesignDecisions": [string, ...]` (each entry a substantive, CR-007-anchored justification of a
genuine fork), and — only when that array is empty — `"nonMaterialityJustification"` (substantive, required).
**Safe default, not a hard failure**: an omitted or insufficiently justified classification does NOT fail the
design stage (that would be a worse regression than the bug being fixed) — it defaults to a synthesized
material-decision entry, so the design still succeeds but the gate still opens, per CR-007's own "uncertainty
means material" rule. `Conductor.onDesignStageSucceeded` reads this signal and independently re-applies the
SAME safe default (never trusting `DesignAiExecutor` alone to have applied it) before deciding whether to open
`ARCHITECTURE_APPROVAL` — a stub, fixture, or future producer of a `"design"` artifact cannot silently skip
the gate merely by omitting the field. `APPROVAL_REQUIRED_STAGES` now names only stage 11
(`RELEASE_READINESS`), matching plan §5's own unconditional "S11" trigger — S11 itself received no other
change.

## Why PART 2 (clarification-gate sharpening) was NOT implemented

The instruction asked for a code change making "genuinely non-behavioral details... resolve NOT_MATERIAL"
more readily, small and principled, per CR-007. **Investigated directly against real, live evidence before
writing any code** (`docs/evidence/ds-a/run-snapshot.md`, the killed run's own real S3 output, captured before
this turn began): S3 found exactly two `MATERIAL_PENDING` items for the new-files-only `/v1/version` subject —
(1) what determines the actual returned version *value*, given the requirement explicitly ruled out the
standard `pom.xml`/build-info mechanism with no replacement default named, and (2) what the endpoint must do
if the new resource file is missing or malformed at runtime. **Both are genuinely material under CR-007's own
definition** — each is a real fork with an observably different returned value or response on an error path,
not a "purely internal, no external difference" detail. They were not flagged by an over-eager classifier;
they were flagged because **this session's own prior turn added a constraint to the requirement text**
("must not modify pom.xml... only new files") that removed the well-established default (Spring Boot's own
`build-info` convention) without naming a replacement — a genuine ambiguity this session itself introduced,
confirmed by comparing against earlier, less-constrained attempts of the same subject where S3 never flagged
version-sourcing as material at all.

**Weakening `AmbiguityDetectionAiExecutor`'s own classifier to suppress these two specific findings would be
manufacturing a false negative** — exactly the "silent ambiguity resolution" failure class this whole
engagement has consistently refused to do, and the opposite of CR-007's own explicit bias ("non-materiality
must be affirmatively shown, never assumed"). No real, generalizable gap was found in the current prompt's own
NOT_MATERIAL guidance that would need a legitimate, scenario-independent sharpening; the current wording
already includes "a dimension already settled by a reasonable, uncontested default," which is precisely the
clause that correctly stopped applying once the default was removed by this session's own added constraint.

**The correct fix is at the true source**: PART 4's own requirement text (below) is rewritten to name a
concrete version-sourcing mechanism and a concrete missing-file behaviour, so the ambiguity is genuinely
resolved rather than suppressed. This is flagged here explicitly as a deliberate deviation from the literal
instruction, for the owner's own review — not silently substituted.

## Scope

**What changed**: S6's own gate trigger is now conditional; `DsALiveRun`'s own driver handles both real
outcomes (mirroring its existing S4 handling); two Conductor-level stub fixtures updated to keep exercising a
real gate.

**What did not change**: S11's own unconditional gate; `AmbiguityDetectionAiExecutor`'s own classifier
(deliberately, see above); `StageInput`'s own closed field list (the new signal flows through the existing
open `inputArtifacts`/produced-artifact JSON, the same pattern CR-055 already established needs no contract
change).

## Conditions attached to the approval

1. **No weakening of a real materiality finding to force a clean demonstration** — honoured; see the PART 2
   section above.
2. **Safe default on an ambiguous/missing signal, never a hard failure** — honoured, verified by
   `omittedMaterialityClassificationDefaultsToMaterialNotFailure` and
   `placeholderJustificationDefaultsToMaterialNotFailure`.
3. **S11 stays unconditional** — honoured, verified live by `releaseReadinessGateStaysUnconditional`.

## Carried-forward enforcement points

| Item | Due at |
|---|---|
| If a future requirement text again removes a well-established default without naming a replacement, expect S3 to correctly flag it material — that is the classifier working, not a defect to route around | Whenever this driver's own `REQUIREMENT` constant is next edited |

## Addendum (2026-09-22) — the live compensating check, both halves, PASSED

**Ambiguity gate (S4), real, live**: re-ran `DsCClarificationRun#driveDsCThroughClarificationAndReplan` live
against DS-C's own canonical contradiction text. S3's real output again found real `MATERIAL_PENDING`
findings and S4's gate opened (`S4 state=AWAITING_APPROVAL`) — the ambiguity gate did not go soft. The run
itself did not complete end-to-end: this live pass found **two genuinely novel** material findings beyond the
two the driver's own hardcoded answer set covers (trusted-partner bypass duration/revocation; what fields
constitute an "analytics record"). Per the owner's own standing delegation rule, an agent never invents an
answer nobody gave — the driver's own safety valve correctly failed loudly, naming both, rather than forcing
past them. **This is not a CR-057 regression**: it is live model non-determinism producing additional real
ambiguity on this pass, and the ambiguity gate's own firing was already confirmed before that point.

**Architecture gate (S6), real, live**: rather than re-running the full pipeline a second time (risking
further novel S4 findings on the way, and costing several more minutes), a scoped, single live call —
`ArchitectureGateMaterialityCompensatingCheck` (new class, run explicitly, not part of the fast or integration
tiers) — invoked the real `DesignAiExecutor` with DS-C's own **real**, previously-captured S5 output
(`docs/evidence/ds-c/replan.json`'s own `s5RealResponse`, from an earlier turn of this same engagement, reused
verbatim rather than fabricated). **Result: PASSED.** The real model returned four substantive, genuinely
material design decisions — among them: whether `/{shortCode}`'s redirect path becomes credential-aware at
all (a real fork against `RedirectController`'s own tested "credential-blind" architectural invariant, T044's
guard); whether trusted-partner status extends the existing `Creator`/`CreatorCredential` domain or introduces
a separate partner-credential type (different blast radii, different security postures); whether the new
168-hour expiry rule rejects or clamps a caller-supplied `expiresAt` (two different API-boundary behaviours).
Every one is a real, CR-007-grounded fork with observable consequences — not a scenario-tuned or forced result.

**Conclusion**: both gates verified live, real, against genuinely material content. CR-057's own fix does not
weaken either control.
