# Change Request CR-031 — Keyless Mode Removed: Task Plan

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 14 rows HOLD.** V3 corrected as retained provenance. **Two defects in this record’s own text were found during application and corrected before it was applied**: Edit 5 said five of six adapters drop the T071 dependency when it is six of six, and Edit 10 claimed Wave 1 widens from four adapters to five when it does not — T071 sat in the same parallel group as T073, so the counterpart dependency was never binding. |
| Raised by | Owner **Decision J**; implements **ADR-004 Amendment 02** |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-20** |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/tasks.md` |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Application order | `tasks.md`: after CR-024, before CR-032. **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |
| Task count | **172 → 172.** No task is added and **no task is struck** — see §Counts, which corrects an earlier draft of this row. |

## Owner approval

Owner **Decision J**, 2026-09-20 — *"do it."* — with her explicit instruction that **what survives be stated as
plainly as what is struck**, because over-deletion here would remove real engineering.

Kept on that instruction: the injected fakes, the six AI adapters, the CLI transport, the no-plan gate, the `HUMAN`
executor kind, and five genuinely deterministic engines — which were never fallbacks.

## What is struck, and what is deliberately kept

The distinction the owner asked to be stated explicitly, because over-deletion here would remove real engineering:

| Task | Disposition |
|---|---|
| **T018** — secure configuration defaults | **Kept, one default removed.** `ai: off` goes; throttling-on, verbose-error-off and readiness-fails-closed stay. Its guard about the flag's naming is struck with the flag |
| **T066** — `HUMAN` executor kind | **Kept, one assertion struck.** The three kinds stay; `HUMAN` is still never selectable; the assertion that *the flag* cannot select it goes, because there is no flag. **The `executorClass`-excludes-`HUMAN` asymmetry survives** |
| **T071** — deterministic stage engines | **Revised, not struck.** Eleven engines → **five**: S1, S8, S10, S11, S12, the genuinely deterministic stages. The six counterparts for AI-capable stages are struck |
| **T072** — scriptable fakes | **Kept entirely.** Tests never call live AI; that was FR-ORC-030 and it never depended on the flag |
| **T073, T073a–T073f** — CLI adapter and six AI adapters | **Kept entirely.** ADR-004-A1 is untouched. **One assertion changes**: T073's third — *"CLI unavailable → fallback stamped `DETERMINISTIC`, run not failed"* — has no fallback to fall back to |
| **T065** — no-plan gate, three options | **Kept entirely.** It is now the *only* thing preventing an unimplemented change from advancing at S7 |
| **T087** — declared fallback handler | **Retired by Decision K — CR-032**, not by this record. Draft 1 left it pending the owner's FR-ORC-015 disposition; she chose option (a), a documented honest absence |
| **T116** — recovery-mechanism classification | **Handled by CR-032**, not here. Its `fallback` enum value is removed, leaving five — an enum value no code path can emit is a claim, not a classification |
| **T127** — quickstart verified end to end | **Kept, narrowed.** *"with no AI key and no network for the fast and integration tiers"* survives — that is true and is the surviving claim. The keyless *orchestration* run does not |
| **T141** — out-of-scenario generic-executor run | **Kept, premise changed.** It was *"submitted with AI off"*. It becomes *"submitted like any other run"* — and it still proves what it existed to prove, that executors are generic rather than input-aware, which never depended on the mode |
| **T142** — scenario evidence consolidation | **Kept, simplified.** The mode-labelling guard loses its AI-off contrast; per-node executor-kind labels and the pinned model id stay, and are now the whole of it |

## Applied edits

**Edit 1 — T018**: `ai: off` removed from Artifact and Done; the flag-naming guard replaced by `no mode flag exists (ADR-004-A2); the remaining defaults are unaffected`. Done: `four defaults` → `three defaults`.

**Edit 2 — T066**: Validate's flag-selection assertion → `test asserting `HUMAN` is recorded **only** as a no-plan-gate outcome and cannot be requested by any caller or configuration`. Guard keeps the `executorClass` asymmetry verbatim.

**Edit 3 — T071**: Artifact → `**five** deterministic stage engines — S1 ingestion, S8 testing, S10 security and policy, S11 release-readiness evaluation, S12 summary assembly — the genuinely deterministic stages. **No counterpart engine exists for any AI-capable stage** (ADR-004-A2)`. Validate's `full run with no AI key and no network (SC-014)` → `each of the five engines produces its declared output deterministically — identical input, identical output, asserted per engine. **Completion of a run is not evidence that an engine is correct**, which is the gap the pre-implementation review found in this task's previous validation`. Guard's first-class-counterpart framing struck; replaced by `these five are not fallbacks and never were — they are the real executors for stages whose value is repeatability (CL-003)`.

**Edit 3a — T071, Req and Trace** *(added on verification: Edit 3 rewrote T071's Artifact, Validate and Guard and left its citations pointing at retired identifiers)*: Req `**CN-011**, FR-ORC-015` → `FR-ORC-029` — **CN-011 is retired by CR-028** and this record removes its citation; FR-ORC-015's removal is CR-032's Edit 9, applied after this one. Trace `matrix FR-ORC-015, SC-014` → `matrix FR-ORC-029` — **SC-014 is retired by CR-028**, and a task may not trace to a retired criterion. Without this edit, V2's `SC-014 count = 0` assertion would have failed on T071's own Trace line, which is how it was found.

**Edit 4 — T073, assertion 3**: `CLI unavailable → `UNAVAILABLE` → fallback stamped `DETERMINISTIC`, run not failed` → `CLI unavailable → `UNAVAILABLE` → bounded retry per S7's declared set → **suspension on exhaustion**, with the reason recorded. **There is no fallback** (ADR-004-A2), so the run does not silently degrade; it stops and asks`.

**Edit 5 — T073a–T073f**: each adapter’s Pre/Deps drop `T071` where it was cited as the counterpart dependency — **all six, not five**.

> **Corrected during application.** Draft 2 said *"five of six — T073e keeps T071 because it also depends on T065"*, which confused two unrelated dependencies. **T073e has no reason to depend on T071 either**: T071 now builds engines for S1, S8, S10, S11 and S12, and **S7 is not among them** — its plan-applying counterpart is exactly what Decision J struck. T073e depends on **T065**, the no-plan gate, which is a different task. Applying the record as drafted would have left a dependency on an engine that no longer exists for that stage.

**Edit 6 — T127**: Validate narrowed to `the **fast and integration tiers** run with no AI key and no network; the orchestration run requires an authenticated CLI, and the guide says so before any run instruction`.

**Edit 7 — T141**: `submitted with **AI off**` → `submitted like any other run`. Guard gains `the generic-executor proof never depended on the mode — an executor that branched on recognising blessed inputs would be rigged whichever way it was invoked`.

**Edit 8 — T142**: guard's `recorded DS runs are AI-mode by the owner; the out-of-scenario run is AI-off` → `every run is AI-backed; per-node executor-kind labels distinguish what actually ran within each, and the pinned model id accompanies every `AI` execution`.

**Edit 9 — coverage map**: the group-11a row's six-adapter note and the AI-wiring rows keep their content; the deterministic-counterpart framing is removed.

**Edit 10 — the parallelism note**: `shares nothing but the transport and its stage's deterministic counterpart` → `shares nothing but the transport`. **Wave 1 stays at four adapters.**

> **Corrected during application, and it is a correction against my own favour.** Draft 2 claimed *"Wave 1 grows from four adapters to five"*. It does not. **T071 sat in the same Phase-5 parallel group as T073** (both unblock on T069), so an adapter waiting on `T071, T073` unblocked at the same moment as one waiting on `T073` alone — the counterpart dependency was never the binding constraint. T073d still waits on T107 and T073e on T065, so the three waves stand unchanged. The affordability argument is **unaffected but not improved**, and the plan now says so explicitly rather than booking a parallelism gain that does not exist. This is the third defect verification found in this record.

## Counts

| | Before | After |
|---|---|---|
| Tasks | **172** — the count in the committed `tasks.md` today | **172** |

**No task is struck by this record.** T071 is *revised* — five engines instead of eleven — which changes its content and
not the count. T087 and T116 were left to the owner's FR-ORC-015 disposition, which she gave as **Decision K** —
both are handled by **CR-032**, not here. Everything else in the struck-versus-kept table above is a narrowing of an existing task.

**Two count errors of my own, corrected here rather than left for the orphan check.** An earlier draft's header said
`173 → 172`, which was wrong twice over: it asserted a removal that does not happen, and it took 173 as the starting
point when **the committed file has 172 task lines** — 173 is the total only *after* CR-027 adds T061a, and CR-027
applies **after** this record in the stated order. The correct arithmetic across the whole package is:

| Point in the order | Count |
|---|---|
| Committed today | 172 |
| After CR-021, CR-024, **CR-031** (this record), CR-025 | 172 — none of the four adds or removes a task |
| After CR-027 (T061a added) | **173** |

**173 is therefore the package's final task count**, and it is the number CR-027's header and verification row assert.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MAJOR** in character: six engines and a declared fallback behaviour are removed from the plan of record. |
| **Affected consumers** | `docs/delivery/*` registers; plan §3 (**CR-030**). |
| **Affected tests** | T071's validation becomes per-engine determinism instead of run completion — **a strict improvement the review had already flagged**; T073's third assertion changes outcome; T066, T127, T141, T142 narrow. |
| **Required approval** | Human owner. |

## Post-application verification

| # | Command (`T=tasks.md`) | Expected | Result |
|---|---|---|---|
| V1 | `grep -c 'ai: off' $T` | `0` | **EXECUTED: 0** — HOLD |
| V2 | `grep -c 'SC-014' $T` | `0` — the retired criterion is no longer cited as live, **including on T071's own Trace line** (Edit 3a) | **EXECUTED: 0** — HOLD |
| V2a | `grep -c 'CN-011' $T` | `0` — same, for the retired constraint | **EXECUTED: 0** — HOLD |
| V3 | `grep -c 'deterministic counterpart' $T` | `0` | **EXECUTED: 4** — HOLD. *Expectation defect (retained provenance):* all four occurrences are statements that **no counterpart exists** — T087’s retirement, T145’s coverage ground, T147’s limitation, and the Wave-1 no-widening note. **Zero tasks depend on one.** |
| V4 | `grep -c 'five\\*\\* deterministic stage engines' $T` | `1` | **EXECUTED: 1** — HOLD |
| V5 | `grep -c 'Completion of a run is not evidence' $T` | `1` — the review's finding closed | **EXECUTED: 1** — HOLD |
| V6 | `grep -c 'fallback stamped `DETERMINISTIC`' $T` | `0` | **EXECUTED: 0** — HOLD |
| V7 | `grep -c 'There is no fallback' $T` | `≥ 1` | **EXECUTED: 1** — HOLD |
| V8 | `grep -c 'submitted with \\*\\*AI off\\*\\*' $T` | `0` | **EXECUTED: 0** — HOLD |
| V9 | `grep -c 'T072' $T` | `≥ 2` — fakes kept | **EXECUTED: 4** — HOLD |
| V10 | `grep -c 'T073a' $T` | `≥ 2` — adapters kept | **EXECUTED: 6** — HOLD |
| V11 | `grep -c 'no-plan gate' $T` | `≥ 3` — the S7 guard, now sole | **EXECUTED: 4** — HOLD |
| V12 | `grep -c '^- \[ \] T[0-9]' $T` | `172` — unchanged by this record; 173 only after CR-027 | **EXECUTED: 172** — HOLD |
| V13 | `grep -c 'executor-kind' $T` | `≥ 4` — labelling survives and is load-bearing | **EXECUTED: 6** — HOLD |
| V14 | `grep -c 'Wave 1' $T` | `1`, naming **five** adapters | **EXECUTED: 1** — HOLD. Wave 1 names four adapters and states that removing the counterparts did NOT widen it — corrected during application |

## Residual risk

**T071's revision is the one place this record makes the submission stronger**, and it is worth naming because the rest
is subtraction: its old validation was *"a keyless run completes"*, which the pre-implementation review correctly called
completion rather than correctness. Five engines each asserted deterministic on identical input is a real test where
there was none.

**The rest is honest subtraction**, and the largest piece of it — the empty Fallback column — was not this record's to
resolve. The owner resolved it as **Decision K**: FR-ORC-015 retired, a documented honest absence over a ceremonial
presence, carried by **CR-032**.

**Edit 3a is the second defect verification found in this record**, after the count error above. Both were of the same
kind — a citation or a number left pointing at something the package changes elsewhere — and both were found by asking
what a stated assertion would actually return rather than by re-reading the prose.
