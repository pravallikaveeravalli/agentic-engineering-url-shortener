# Contracts

Versioned interface deliverables for feature 001. Each is a **source of truth**, not documentation
generated from code — drift must be detectable, which is why validation runs against these files
rather than deriving them (ADR-005).

| File | Version | What validates it |
|---|---|---|
| `openapi.yaml` | 1.0.0 | Contract tests assert live responses conform to this document. A deliberate drift must fail the build. |
| `workflow-state.schema.json` | 1.0.0 | Persisted run snapshots validated against it, including the terminal/suspended conditionals |
| `approval.schema.json` | 1.0.0 | Gate decision records validated; `repositoryRecordPath` pattern enforces materialization |
| `audit-event.schema.json` | 1.0.0 | Every audit record validated for all six mandatory fields (`POL-AUD-001`) |
| `policy-evaluation.schema.json` | 1.0.0 | S10 output validated for exactly four outcome values and complete exception fields |

Persistence schema lives separately as Flyway migrations (`db/migration/V*.sql`), forward-only.

## Compatibility rules

| Change | Classification | Requirement |
|---|---|---|
| New optional field, new endpoint, new enum value in a response | MINOR | Change-control record |
| Removing/narrowing a field, renaming, changing a status code, new required request field | MAJOR | New path version (`/v2`) + change-control record + migration plan |
| Wording, description, example fixes | PATCH | Change-control record only if a consumer-visible meaning shifts |

Database migrations are forward-only. A column is deprecated across two versions before removal.

## Required change-impact record

Every change to any file here records, under `docs/governance/change-control/`: owner, version impact,
backward-compatibility impact, affected consumers, tests to update, documentation to update,
rollout/migration steps, and the required change approval (`POL-CHG-001`).

## Open items affecting these contracts

- **DF-002** — the redirect status code in `openapi.yaml` is `307` as the plan's **provisional**
  position. Not settled; a Plan-gate decision for permanent redirects changes it to `308`/`301` and
  forces a re-examination of analytics counting and expiry enforcement.
- **DF-001** — whether the redirect's event append is synchronous and transactional is not settled;
  it does not change the wire contract but changes what the analytics tests may assert.
