# Per-stage executor-mode labels — attempt 26

T134 artifact component. `executorClass` as persisted by the real run (`JdbcRunStore`), verbatim — source:
`docs/evidence/ds-a/run-snapshot-ATTEMPT-26-S7-S8-S9-S10-SUCCEEDED-S11-real-not-ready.md`, node table.

| Node | executorClass |
|---|---|
| S1 | DETERMINISTIC |
| S2 | AI_CAPABLE (model: claude-sonnet-5) |
| S3 | AI_CAPABLE (model: claude-sonnet-5) |
| S4 | HUMAN_GATE |
| S5 | AI_CAPABLE (model: claude-sonnet-5) |
| S6 | AI_CAPABLE (model: claude-sonnet-5) |
| S7 (parent) | AI_CAPABLE |
| S7.1 | AI_CAPABLE (model: claude-sonnet-5) |
| S7.join | AI_CAPABLE |
| S8 | DETERMINISTIC |
| S9 | AI_CAPABLE (model: claude-sonnet-5) |
| S10 | DETERMINISTIC |
| S11 | HUMAN_GATE |
| S12 | DETERMINISTIC |

Model ids are the real, actually-used ids this run recorded (`S3 actually-used model id: claude-sonnet-5`,
etc.), not a configured default asserted without verification.
