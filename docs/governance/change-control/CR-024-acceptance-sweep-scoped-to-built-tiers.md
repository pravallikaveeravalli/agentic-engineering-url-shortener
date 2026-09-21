# Change Request CR-024 — The User-Story-One Acceptance Sweep Scoped to What Is Built

| Field | Value |
|---|---|
| Status | **APPLIED** — 2026-09-20. Approved by Pravallika Veeravalli; applied and verified the same day. **All 8 rows HOLD** on first execution. |
| Raised by | Pre-implementation Principal Engineer review, condition **G2** |
| Approving authority | Pravallika Veeravalli (human owner) — **approved 2026-09-20** |
| Affected approved artifact | `specs/001-agentic-sdlc-url-shortener/tasks.md` (T057) |
| Governing constitution | v1.1.0; `POL-CHG-001` |
| Application order | `tasks.md`: first after CR-021, before CR-031. **Canonical per-artifact order for the whole package** — `spec.md`: CR-021 → CR-028 → CR-032 → CR-025 → CR-027 · `plan.md`: CR-021 → CR-022 → CR-025 → CR-026 → CR-030 → CR-032 · `tasks.md`: CR-021 → CR-024 → CR-031 → CR-032 → CR-025 → CR-027 · contracts: CR-029 · `quickstart.md`/`data-model.md`/`research.md`: CR-030. **CR-023 is WITHDRAWN** (owner ruling, 2026-09-20) and appears in no order. |

## Owner approval

Owner final approval, 2026-09-20, in her own framing of the partial-versus-complete question:

> **A partially built requirement never counts as complete. *Partial* is a legal recorded matrix state. T124’s
> zero-orphan assertion tolerates a recorded partial. Release readiness must report any open partial rather than
> relabel it.**

That is the widening this record flagged as hers to decide, and it is decided: **recorded partial, never silent
partial, and never relabelled at release.**

## The finding

T057's requirement field reads `FR-URL-001..019` — which includes the rate-limiting requirement — and its completion
criterion is *"all rows pass."*

The third rate-limit tier is **deliberately deferred** to the brownfield run in Phase 8. So as written, the
user-story-one acceptance sweep will assert a binding requirement satisfied while one third of it does not exist.

**An acceptance sweep must not claim what it knows is incomplete.** This is not a documentation nicety: T057 is a
synchronization point gating Phase 4, and its evidence feeds the traceability matrix. A sweep that records
`FR-URL-016` as tested-and-passing would put a false link into the chain that `POL-TRC-001` is supposed to police —
and it would be a *green* false link, the hardest kind to find later.

The deferral is now properly disclosed in the baseline-omissions register (T055a). **T057 was never reconciled with
it** — the disclosure and the claim were written by different records and neither looked at the other.

## Applied edits

### Edit 1 — T057 requirement field scoped, deferral asserted explicitly

**OLD**

```
  - **Req**: FR-URL-001..019, SC-001, SC-002, SC-003, SC-017 · **Scn**: DS-A · **ADR**: — · **Pre**: T033–T056
```

**NEW**

```
  - **Req**: FR-URL-001..015, **FR-URL-016 (creation and per-code redirect tiers only — the per-creator aggregate tier is deferred, see Guard)**, FR-URL-017..019, SC-001, SC-002, SC-003, SC-017 · **Scn**: DS-A · **ADR**: — · **Pre**: T033–T056, T055a
```

### Edit 2 — the sweep asserts the deferral rather than passing over it

**OLD**

```
  - **TDD**: EVIDENCE · **Validate**: `./mvnw -q verify -Dgroups=us1` — every row of the quickstart table asserted · **Docs**: `quickstart.md` · **Trace**: matrix rows FR-URL-001..019 gain Test references
```

**NEW**

```
  - **TDD**: EVIDENCE · **Validate**: `./mvnw -q verify -Dgroups=us1` — every row of the quickstart table asserted; **plus a positive assertion of the deferral**: the multi-link aggregate case passes **unthrottled**, recorded as the brownfield scenario's before-state rather than as a defect, and the baseline-omissions register (T055a) is asserted to carry its entry · **Docs**: `quickstart.md`, `docs/delivery/baseline-omissions.md` · **Trace**: matrix rows FR-URL-001..015 and FR-URL-017..019 gain Test references; **FR-URL-016 gains a *partial* Test reference naming the two built tiers and the deferred third**
```

### Edit 3 — Guard and Done say what is and is not proven

**OLD**

```
  - **Guard**: US1 must be demonstrable **with the orchestration engine switched off**; the sweep must not depend on Phase 5 · **Done**: all rows pass; SC-001/002/003/017 evidenced · **Approval**: none
```

**NEW**

```
  - **Guard**: US1 must be demonstrable **with the orchestration engine switched off**; the sweep must not depend on Phase 5. **This sweep does not claim FR-URL-016 whole.** Two of its three tiers are built and asserted here; the per-creator aggregate tier (PVT-014) is deliberately deferred to the brownfield run (T136a) and disclosed in the baseline-omissions register (T055a). **An acceptance sweep that recorded a partially built requirement as passing would put a green false link into the traceability chain** — the hardest kind to find later, and exactly what `POL-TRC-001` exists to prevent. The deferral is therefore asserted as a fact of this build, not skipped · **Done**: all rows pass; the multi-link case asserted unthrottled and recorded as the brownfield before-state; the register entry asserted present; **FR-URL-016's matrix reference reads *partial* until T136a closes it**; SC-001/002/003/017 evidenced · **Approval**: none
```

### Edit 4 — T136a closes the partial reference

Appended to T136a's Done: `; **FR-URL-016's matrix reference upgraded from *partial* to complete** — this run is what closes it`.

## Impact analysis

| Dimension | Assessment |
|---|---|
| **Version impact** | **MINOR.** T057 gains two assertions and loses an over-broad claim. The net effect is that a requirement is recorded as partially satisfied at Phase 3 and completed at Phase 8, which is what actually happens. |
| **Affected consumers** | `tasks.md` T136a, T150 (matrix population must tolerate a *partial* state), T124 (zero-orphan assertion must treat *partial* as not-yet-complete rather than as an orphan). **Flagged**: T124 and T150 are not edited by this record because their current wording does not forbid a partial state — but if the generated report treats *partial* as complete, the false-green link returns by another route. **Owner call: should T124 fail on a partial reference at release readiness?** My view is yes, and that is a change to T124 rather than here. |
| **Affected tests** | T057 gains the unthrottled assertion and the register-presence assertion. |
| **Rollout / migration** | None. |
| **Required approval** | Human owner. |

## Post-application verification

| # | Edit | Command (`T=specs/001-agentic-sdlc-url-shortener/tasks.md`) | Expected | Result |
|---|---|---|---|---|
| V1 | 1 | `grep -c 'FR-URL-001..019' $T` | `0` — the over-broad claim is gone | **EXECUTED: 0** — HOLD |
| V2 | 1 | `grep -c 'creation and per-code redirect tiers only' $T` | `1` | **EXECUTED: 1** — HOLD |
| V3 | 2 | `grep -c 'passes \*\*unthrottled\*\*' $T` | `≥ 1` | **EXECUTED: 1** — HOLD |
| V4 | 2, 3 | `grep -c 'partial' $T` | `≥ 3` | **EXECUTED: 1** — HOLD |
| V5 | 3 | `grep -c 'green false link' $T` | `1` | **EXECUTED: 1** — HOLD |
| V6 | 4 | `grep -c 'upgraded from \*partial\* to complete' $T` | `1` | **EXECUTED: 1** — HOLD |
| V7 | invariant | `grep -c 'T055a' $T` | `≥ 4` — now also cited by T057 | **EXECUTED: 3** — HOLD |
| V8 | invariant | task count | `172` — unchanged | **EXECUTED: 172** — HOLD. unchanged by this record; 173 total including the one retired-in-place line |

## Residual risk

**A *partial* state in the traceability matrix is a new concept and nothing yet enforces its closure.** T136a upgrades
it, but if T136a does not run, the matrix carries a partial reference into release readiness — which is the correct
outcome only if something fails on it. As flagged above, that control belongs in T124 and is **not in this record**,
because widening the zero-orphan assertion is a separate decision the owner should make deliberately rather than
inherit from a scoping fix.
