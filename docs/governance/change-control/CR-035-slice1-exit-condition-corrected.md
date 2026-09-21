# Change Request CR-035 — Slice 1's Exit Condition Corrected to Plan §14's Own Wording

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-21. Approved by Pravallika Veeravalli; applied and verified the same day. **All 8 rows HOLD on first execution.** |
| Raised by | **The register's own author, self-flagged** at the Slice 1 boundary — the over-promise was mine, found when the dependency graph made it unreachable |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-21** |
| Source decision | Owner decision, 2026-09-21. Her word, verbatim: **"Correct it."** |
| Affected artifact | `docs/delivery/scope-register.md` (T001's artifact, **gate-acknowledged at Gate 6**) |
| Governing constitution | v1.1.0 — **not amended**. `POL-CHG-001` |
| Application order | Sole record. Applies to the state left by CR-021..CR-034. |

## Owner approval and provenance

**Her decision, verbatim: "Correct it."**

**Provenance, recorded because it matters who got this wrong.** Plan §14 — approved at the Gate 4
closing package — describes Slice 1 as *"Build, layout, Flyway, OpenAPI skeleton, contract-test
harness, CI-equivalent local script."* It says **contract-test harness**. It does not ask for the
drift-failure demonstration.

**I added the drift proof to the exit condition when writing T001's register**, wording it as *"the
contract harness has been demonstrated **failing** on injected drift."* That was my addition, not the
plan's, and it made the control stricter than the artifact it was derived from.

**The owner corrected a gate-acknowledged control back to the approved plan's own wording.** The
register was acknowledged at Gate 6, so this is not an editorial tidy — it changes an operative
control, which is why it goes through change control on her recorded decision rather than being
quietly edited by the person who wrote the error.

## Why the over-promise was unreachable, not merely strict

The drift proof is **T013**. `T013 → T012 → T032`, and **T032 is in Phase 2** — it is the walking
skeleton, because a conformance harness needs a live endpoint to validate a response against.

So Slice 1 could never satisfy the condition I wrote: not because the work was hard, but because the
task plan's dependency graph places the proof one slice later. **The exit condition was
unsatisfiable by construction**, and it went unnoticed until implementation reached it.

**What the plan asks for at Slice 1, and what is now the condition**: the harness *exists and runs*.
The drift-failure demonstration lands with T013 in Phase 2, where the graph puts it.

## Applied edit

### Edit 1 — `scope-register.md`, Slice 1's exit-condition cell

**OLD**

```
The CI-equivalent script runs green from a clean checkout, **and** the contract harness has been demonstrated **failing** on injected drift. A harness that has never failed is unvalidated
```

**NEW**

```
The CI-equivalent script runs green from a clean checkout, **and** the contract-test harness **exists and runs** — plan §14's own wording for this slice. **The drift-failure demonstration is T013, in Slice 2**, because it depends on T012 which depends on T032's live endpoint: a conformance harness has nothing to validate a response against until an endpoint exists. *(Corrected by CR-035 on the owner's decision. The earlier wording required the drift proof here, which the dependency graph makes unreachable — it was this register author's addition, not the plan's.)* The principle it was reaching for is not abandoned: **a harness that has never failed is unvalidated**, and T013 discharges it one slice later — exactly as T015 discharges it for the architecture rule within this slice
```

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **PATCH.** No requirement, task, architecture or obligation changes. A register's exit condition is corrected to match the approved plan it was derived from. |
| **Direction of change** | **This relaxes a control**, and that is worth stating plainly rather than burying: Slice 1 can now close without the drift proof. It is nonetheless the correct direction, because the stricter version was **unsatisfiable**, and an unsatisfiable gate is not a stronger control — it is one that will be ignored or worked around the first time it bites. |
| **What is NOT relaxed** | The falsifiability principle. T015 discharges it for the architecture rule **inside** Slice 1, and T013 discharges it for the contract harness in Slice 2. The register now says so, so the reader sees the obligation deferred rather than dropped. |
| **Affected consumers** | None. `checkpoints.md` names Slice 1's cuttables and does not cite this cell; `milestones.md` carries no exit conditions. |
| **Affected tests** | None. T013's own validation is unchanged and still requires the injected drift to be detected. |
| **Required approval** | Human owner — **given: "Correct it."** The register is gate-acknowledged, so the author of the error may not correct it alone. |

## Post-application verification

| # | Command (`SR=docs/delivery/scope-register.md`) | Expected | Result |
|---|---|---|---|
| V1 | `grep -c 'demonstrated \*\*failing\*\* on injected drift' $SR` | `0` — the unreachable condition is gone | **EXECUTED: 0** — HOLD |
| V2 | `grep -c 'exists and runs' $SR` | `1` | **EXECUTED: 1** — HOLD |
| V3 | `grep -c 'drift-failure demonstration is T013' $SR` | `1` — deferred, with its destination named | **EXECUTED: 1** — HOLD |
| V4 | `grep -c 'a harness that has never failed is unvalidated' $SR` | `1` — the principle survives the correction | **EXECUTED: 1** — HOLD |
| V5 | `grep -c 'CR-035' $SR` | `1` | **EXECUTED: 1** — HOLD |
| V6 | inv | nine slice rows still present | `9` | **EXECUTED: 9** — HOLD |
| V7 | inv | Slice 1's Requirements cell unchanged (NFR-MNT-001/002 + five ADRs) | present | **EXECUTED: present** — HOLD |
| V8 | regression | the 51-criterion Phase 0 suite | all pass | **EXECUTED: 51 criteria, 0 FAIL** — HOLD. No inversion needed, as predicted. |

## Cross-record orphan check

| Question | Result |
|---|---|
| Does the quoted `OLD` string still exist? | **Asserted at application.** |
| Does another record edit this cell? | **No.** CR-021..CR-034 do not touch `scope-register.md`; CR-034 edited `checkpoints.md` and `backlog.md` only. |
| Does any **applied** record assert what this falsifies? | **No.** The Gate 6 record acknowledges the registers as operative controls without quoting this cell, so it stays true. **Worth stating because it was the risk**: had Gate 6 quoted the exit condition, this correction would have falsified a filed gate record and needed a forward correction there too. |
| Do application orders agree? | Sole record. |
| Unclaimed survivals? | **V1 is that assertion.** |
| Is the validation suite inside the search space? | **V8.** The Phase 0 suite checks nine slice rows each with a non-empty exit condition; it does not assert this cell's content, so it needs no inversion — checked rather than assumed, after CR-034's lesson. |

## Residual risk

**A register whose author also writes its verification is the structural weakness here, and this
record is evidence of it.** The over-promise survived writing, 51 executed criteria, and a gate,
because every check asked *"does each slice have an exit condition?"* and none asked *"is each exit
condition reachable given the task graph?"*

That second question is now worth asking of the other eight slices. It is **not** done in this
record — the owner's instruction was to correct Slice 1 — and it is recorded here as a known gap
rather than left for the next slice boundary to discover the same way this one did.
