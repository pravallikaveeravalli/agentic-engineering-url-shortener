# Traceability matrix — attempt 26

T134 artifact component. Requirement → task → design decision → implementation → contract, built from this
run's own real S2/S5/S6/S7 outputs (`docs/evidence/ds-a/run-snapshot-ATTEMPT-26-S7-S8-S9-S10-SUCCEEDED-S11-real-not-ready.md`);
no entry here states anything the run's own captured output does not.

| Req | Statement (S2) | Task (S5) | Design decision (S6) | Implementation |
|---|---|---|---|---|
| 1a | GET /v1/version SHALL exist | task-01-create-endpoint | `@RestController`/`@GetMapping("/v1/version")` | `VersionController.java` |
| 1b | Public, no auth required | task-02-no-auth | Public by omission from `AuthConfiguration.AUTHENTICATED_PATTERNS` (no code change) | `VersionController.java` (no auth annotation); `AuthConfiguration.java` unmodified |
| 1c | No path/query params; extras ignored | task-03-ignore-params | Bare `@GetMapping`, no `@PathVariable`/`@RequestParam` declared — Spring's own default | `VersionController.java` |
| 1d | HTTP 200 on success | task-04-return-200 | Normal return, no thrown exception | `VersionController.java` |
| 1e | Content-Type: application/json | task-05-content-type-header | Default `MappingJackson2HttpMessageConverter` negotiation, no explicit header | `VersionController.java` (implicit) |
| 1f | Body `{"version": "<current version>"}` | task-06-json-body-version | `BuildProperties` bean, sourced from `pom.xml`'s own `<version>` via `spring-boot-maven-plugin`'s `build-info` goal, verbatim (no stripping/transform) | `VersionController.java`; `pom.xml` (`build-info` execution) |
| 1g | Cache-Control: no-store | task-07-cache-control-header | Explicit `ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")` | `VersionController.java` |

**Contract**: `specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml` — new `/v1/version` path (tag
`operations`, `security: []`) and new `Version` schema component, additive/MINOR per the document's own
versioning rule.

**Bookkeeping**: `ContractFilesLintTest.openApiDocumentParsesAndDeclaresItsVersions` path-count assertion
bumped 8 → 9 in the same change (CR-064's own precedent from attempt 21's finding, applied correctly this
time via the deterministic bookkeeping follow-up).

**Landed**: all of the above is now the real code in this repository (commit `d4e3ccf`), not merely cited
on an evidence branch.
