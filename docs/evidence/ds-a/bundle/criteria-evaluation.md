# Per-stage criteria evaluation — attempt 26

T134 artifact component. What each stage's own guard actually evaluated and found, real, this run — source:
`docs/evidence/ds-a/run-snapshot-ATTEMPT-26-S7-S8-S9-S10-SUCCEEDED-S11-real-not-ready.md` and
`docs/evidence/ds-a/attempts-26-27-finding.md`.

| Stage | Criterion evaluated | Real result, this run |
|---|---|---|
| S3 | Every ambiguity element must carry a substantive `qualityChecksPerformed`, unconditionally (CR-007/T082) | 4 elements found; all 4 carried the field. See `quality-checks.md`. |
| S4 | Material ambiguities (`MATERIAL_PENDING`) require a human clarification before the run proceeds | 1 material element (version-string sourcing/format); resolved under the owner's standing delegation, gate APPROVED |
| S6 | `onDesignStageSucceeded` independently re-checks the design output for material design decisions (CR-057) — never trusts the AI executor's own materiality claim alone | 3 real material decisions found (BuildProperties sourcing vs. hardcoded constant; plain controller vs. widening the Actuator surface; verbatim vs. transformed version string). Gate opened, APPROVED under the existing S6 architecture approval. |
| S7.1 | The AI's own JSON change set must parse, every `EDIT` search string must match the real current file content exactly once, and the result must compile (`GitWorktreeBranchApplier`, CR-060) | Change set applied cleanly (3 files: `VersionController.java` CREATE, `pom.xml` EDIT, `openapi.yaml` EDIT); compiled |
| S8 | The real fast-tier suite, run against the applied branch in an isolated worktree, must pass with zero failures (`ScriptTestSuiteRunner`) | SUCCEEDED — no new failures introduced by the branch |
| S9 | Documentation output must describe only behaviors the real test suite actually executed (`executedBehaviors` allow-list, CR-065/066) | SUCCEEDED — confirmed the CR-064 path-count mapping against `ContractFilesLintTest.openApiDocumentParsesAndDeclaresItsVersions`, the one behavior actually exercised |
| S10 | Deterministic aggregation/traceability step | SUCCEEDED |
| S11 | `ReleaseReadinessEngine`'s real, deterministic `ReadinessEvaluator` — release-readiness conditions over the whole repository state, not just this change | FAILED on 2 real conditions this run: stale `target/surefire-reports/` residue (self-inflicted by direct test invocations across turns, since fixed by cleaning `target/`) and `docs/LIMITATIONS.md` not existing yet (T147, since written and committed). Both conditions are now individually resolved in this repository's current state; this exact run was not re-executed past S10 to confirm a single continuous pass reaches S11's open gate. |

S11's own FAILED result in this run is itself evidence the gate evaluates real repository state correctly,
not a defect: it named the exact two files and the exact missing artifact, both independently verifiable.
