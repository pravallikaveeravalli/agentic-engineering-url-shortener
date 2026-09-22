# DS-A live run snapshot

runId: 4330d700-b4a1-449f-92a0-f5a36e4eb2d3
runState: SAFE_STOP

## Nodes

- S1 (stage 1, SINGLETON) -> SUCCEEDED, executorClass=DETERMINISTIC, attemptsUsed=0
- S2 (stage 2, SINGLETON) -> SUCCEEDED, executorClass=AI_CAPABLE, attemptsUsed=0
- S3 (stage 3, SINGLETON) -> SUCCEEDED, executorClass=AI_CAPABLE, attemptsUsed=0
- S4 (stage 4, SINGLETON) -> SUCCEEDED, executorClass=HUMAN_GATE, attemptsUsed=0
- S5 (stage 5, SINGLETON) -> SUCCEEDED, executorClass=AI_CAPABLE, attemptsUsed=0
- S6 (stage 6, SINGLETON) -> SUCCEEDED, executorClass=AI_CAPABLE, attemptsUsed=0
- S7 (stage 7, FAN_OUT_PARENT) -> SKIPPED, executorClass=AI_CAPABLE, attemptsUsed=0
- S7.1 (stage 7, FAN_OUT_CHILD) -> FAILED, executorClass=AI_CAPABLE, attemptsUsed=0
- S7.join (stage 7, JOIN) -> BLOCKED, executorClass=AI_CAPABLE, attemptsUsed=0
- S8 (stage 8, SINGLETON) -> BLOCKED, executorClass=DETERMINISTIC, attemptsUsed=0
- S9 (stage 9, SINGLETON) -> BLOCKED, executorClass=AI_CAPABLE, attemptsUsed=0
- S10 (stage 10, SINGLETON) -> BLOCKED, executorClass=DETERMINISTIC, attemptsUsed=0
- S11 (stage 11, SINGLETON) -> BLOCKED, executorClass=HUMAN_GATE, attemptsUsed=0
- S12 (stage 12, SINGLETON) -> BLOCKED, executorClass=DETERMINISTIC, attemptsUsed=0

## Model ids and raw responses actually used

### S3 — model claude-sonnet-5

```
```json
[
  {
    "ambiguityClass": "MISSING_ACCEPTANCE_CRITERIA",
    "affectedPath": "R1c ('On a valid request, GET /v1/version SHALL respond with HTTP status code 200') is conditioned on the request being 'valid', but R1a ('accepts no path parameters and no query parameters') is the only nearby clause that could define validity, and it does not say what SHALL happen if a client nonetheless sends query parameters, an unexpected path segment, or a non-GET method to this route.",
    "resolutionState": "MATERIAL_PENDING",
    "qualityChecksPerformed": "Cross-referenced R1c's conditional trigger ('a valid request') against R1a's parameter-acceptance clause and against R1b, R1d, R1e, R1f for any fallback/error-path statement; found no requirement in this set that defines what constitutes an invalid request for this endpoint or what status/body/headers an invalid request receives.",
    "noClarificationReason": null
  },
  {
    "ambiguityClass": "UNDEFINED_TERM",
    "affectedPath": "R1d ('The GET /v1/version response SHALL include a Content-Type header with value application/json') does not state whether the header value must match the literal string 'application/json' exactly or whether a value carrying an additional parameter such as 'application/json; charset=utf-8' also satisfies 'value application/json'.",
    "resolutionState": "MATERIAL_PENDING",
    "qualityChecksPerformed": "Checked whether an existing schema, contract, or stated validation target in the provided requirement set fixes header-comparison semantics (exact string equality vs. media-type/parameter-tolerant comparison); none of R1a-R1f addresses this, and this is exactly the kind of assertion a conformance test would need to encode as one or the other.",
    "noClarificationReason": null
  },
  {
    "ambiguityClass": "UNDEFINED_TERM",
    "affectedPath": "R1e ('a string representing the application's build version') does not define the format of the version string (semantic version, git commit hash, build timestamp, or other scheme).",
    "resolutionState": "NOT_MATERIAL",
    "qualityChecksPerformed": "Checked R1a-R1f and the field's own type constraint ('a string') for any format, pattern, or comparison obligation (e.g. semver ordering, regex, length bound) that would make one format choice behave differently from another under any stated requirement.",
    "noClarificationReason": "No requirement in this set consumes, parses, compares, or validates the version string's internal structure -- the sole obligation is that the JSON field 'version' hold a string representing the build version, and every plain-language reading of 'build version' (semver, hash, timestamp, etc.) satisfies that obligation identically; there is no behavioral fork to resolve."
  },
  {
    "ambiguityClass": "MISSING_ACCEPTANCE_CRITERIA",
    "affectedPath": "None of R1a-R1f specifies the system's behavior when a request uses a method other than GET (e.g. POST, PUT, DELETE, HEAD, OPTIONS) against /v1/version.",
    "resolutionState": "NOT_MATERIAL",
    "qualityChecksPerformed": "Checked whether any requirement binds a specific status code or body for non-GET requests to this route, or whether method-not-allowed handling is otherwise constrained; none is present in this set.",
    "noClarificationReason": "Handling of an unsupported HTTP method on a defined route is already settled by the standard, uncontested default behavior of virtually every HTTP framework and routing layer (respond 404/405 for an unmatched method/route), and no stated obligation here (functional, non-functional, or gate condition) distinguishes between the framework's default choices -- so no conformant implementation's default handling conflicts with any requirement in this set."
  },
  {
    "ambiguityClass": "SELF_REFERENTIAL_CONSTRAINT",
    "affectedPath": "R1f's trailing clause ('so that responses are never cached by clients or intermediaries') is a purpose/rationale statement layered onto the actual SHALL, which only binds the implementation to setting one specific header value ('Cache-Control: no-store').",
    "resolutionState": "NOT_MATERIAL",
    "qualityChecksPerformed": "Parsed R1f into its operative SHALL clause (include Cache-Control: no-store) versus its 'so that' rationale clause, and checked whether the rationale clause introduces any testable obligation beyond the header value itself (e.g. an implicit requirement to also set Pragma or Expires headers for legacy-proxy compatibility).",
    "noClarificationReason": "The rationale clause is purely summarizing/explanatory -- the only enforceable, buildable obligation in R1f is the literal header-value requirement, and deleting the 'so that' clause would not change what a conformant implementation must do (set Cache-Control: no-store); it names the intent behind an already-fully-specified action rather than adding a new one."
  },
  {
    "ambiguityClass": "UNDEFINED_TERM",
    "affectedPath": "R1b ('publicly accessible and SHALL NOT require authentication or authorization of any kind') leaves 'publicly accessible' undefined as a term of art.",
    "resolutionState": "NOT_MATERIAL",
    "qualityChecksPerformed": "Checked for any competing security-posture requirement in this set (e.g. a blanket auth-on-all-endpoints rule) that 'publicly accessible' might conflict with, and checked whether the term's ordinary meaning leaves room for divergent conformant implementations.",
    "noClarificationReason": "The clause is immediately and completely disambiguated by its own second half ('SHALL NOT require authentication or authorization of any kind'), which is an unambiguous, directly testable obligation; 'publicly accessible' is redundant plain-language framing of that same requirement, not an independent term needing its own resolution."
  }
]
```
```

### S5 — model claude-sonnet-5

```
[{"taskId":"T1","requirementIds":["1a"],"dependsOn":[]},{"taskId":"T2","requirementIds":["1b"],"dependsOn":["T1"]},{"taskId":"T3","requirementIds":["1c"],"dependsOn":["T1"]},{"taskId":"T4","requirementIds":["1d"],"dependsOn":["T3"]},{"taskId":"T5","requirementIds":["1e"],"dependsOn":["T3"]},{"taskId":"T6","requirementIds":["1f"],"dependsOn":["T3"]}]
```

### S6 — model claude-sonnet-5

```
Confirmed the design touches no test surface tied to `management.endpoints.web.exposure`, so `SecureDefaultsTest` stays green untouched. I have what I need to write the design.

```json
{"design":"Requirements 1a-1f define one new HTTP surface: an unauthenticated GET /v1/version that returns {\"version\": <string>} with Content-Type application/json and Cache-Control: no-store. The codebase already has an established pattern for exactly this shape of endpoint -- HealthController (src/main/java/agentic/shortener/delivery/HealthController.java) is a plain @RestController in the application-plane 'delivery' package with no security annotation, relying on AuthConfiguration's allow-list model (only /v1/links and /v1/links/* require a credential; everything else, including health, is public by default). Task decomposition: T1 (1a, route shape: GET, no path/query params) and T3 (1c, 200 on valid request) are the same unit of work -- a new VersionController with a single @GetMapping(\"/v1/version\") method that takes no parameters and returns 200 by default; T1 has no separate deliverable, T2 depends on it only because 1b (public access) is verified against the same route once it exists. T2 (1b, public) requires NO code change: the endpoint is public by construction because AuthConfiguration's AUTHENTICATED_PATTERNS is an allow-list and /v1/version is not on it -- T2's deliverable is the conformance test proving that, not a config edit. T4 (1d, Content-Type: application/json) is satisfied by returning a Map body from a Spring MVC @RestController, which serializes via the already-configured Jackson message converter with that content type by default -- no explicit header code needed, verified by the contract test rather than hand-set. T5 (1e, version field) is the one requirement needing new build wiring: rather than hardcoding a version string (which would drift from pom.xml's <version>), the controller takes a constructor-injected org.springframework.boot.info.BuildProperties and returns Map.of(\"version\", buildProperties.getVersion()). BuildProperties auto-configures only when a META-INF/build-info.properties file is on the classpath, which requires adding a build-info execution to the existing spring-boot-maven-plugin declaration in pom.xml -- this is a build-time source (project.version, currently 0.1.0-SNAPSHOT), never a request-time computation, so it cannot vary per-request or leak anything beyond the artifact version already public in the repo. This does NOT touch the actuator 'info' endpoint or management.endpoints.web.exposure (which SecureDefaultsTest pins to 'health' only) -- BuildProperties is consumed directly by our own controller, not exposed via actuator, so the existing actuator allow-list posture is unchanged. T6 (1f, Cache-Control: no-store) is set explicitly in the controller via ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, \"no-store\").body(...), mirroring HealthController's use of ResponseEntity for response-level control. All three code-bearing tasks (T4/T5/T6) land in one new file, VersionController.java, since they are three properties of the same single response, not three separable units -- splitting them into separate controllers or endpoints would misrepresent one requirement as three. The S3 ambiguity review already dispositioned the two remaining open questions as NOT_MATERIAL or out of this requirement set's scope: non-GET methods on /v1/version fall through to the framework's ordinary 404/405 default (no explicit handling needed, consistent with how /health/live and /health/ready already behave), and the version string's internal format is unconstrained (BuildProperties.getVersion() naturally returns the Maven project version string, which satisfies 'a string representing the build version' under any plain reading).","contractImpact":"ADR-005 makes specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml the source of truth, tested against live responses (ContractConformanceIT) and against its own internal consistency (OpenApiConformanceTest, ResponseExamplesTest, ContractDriftDetectionTest, ContractFilesLintTest) -- so this change is a contract change, not just a code change. Additions required: (1) a new path /v1/version under the existing 'operations' tag (shared with /health/live and /health/ready), method GET, security: [] (public, matching the redirect and health patterns), a 200 response with content application/json referencing a new component schema, and at least one example (required by ResponseExamplesTest's 'every JSON response body declares at least one example' assertion, which walks paths.*.*.responses.*.content.application/json generically -- no test-harness change needed for that discovery, only the document content). (2) a new components/schemas/Version schema: type object, required: [version], additionalProperties: false, properties.version: {type: string}, matching the Health schema's minimalism and additionalProperties:false discipline. (3) a documented Cache-Control response header on that 200 (headers: Cache-Control: required, schema: {type: string, enum: [no-store]}), the same pattern already used for the Location header on the redirect's 307. No existing path, schema, or security scheme changes -- this is purely additive (MINOR per the document's own versioning policy in info.description), so the openapi info.version bump, if the project's convention requires one for additive paths, is a minor-version bump only, never a new path prefix. ResponseExamplesTest and OpenApiConformanceTest both discover paths generically by walking the document, so they require no code change to pick up the new path -- but ContractConformanceIT is a hand-written per-endpoint test class (each existing endpoint has its own @Test method invoking the shared harness), so it needs a new test method asserting a live GET /v1/version returns 200 and validates against the new schema, exactly like its existing healthConforms() test.","existingFilesToModify":["pom.xml","specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml","src/test/java/agentic/shortener/contract/ContractConformanceIT.java"],"impactAnalysis":{"impactedComponents":"Application-plane delivery layer only. One new component (VersionController, new file in the existing src/main/java/agentic/shortener/delivery package, sibling to HealthController/LinkController/RedirectController) plus one build-time addition to the Spring Boot Maven plugin's execution list in pom.xml (the build-info goal) to make org.springframework.boot.info.BuildProperties available for injection. No existing controller, service, repository, or domain class is modified -- LinkController, RedirectController, HealthController, AuthConfiguration, and the persistence/application/domain packages are all untouched. The orchestration/control-plane packages (separate plane per ADR-006/CR-038) are entirely unaffected; DependencyDirectionTest's plane-separation rules are not at risk since VersionController sits in the same delivery package and plane as HealthController, not in a control-plane path.","impactedInterfaces":"One new public HTTP interface: GET /v1/version, unauthenticated, no request parameters, 200 response with a JSON body ({\"version\": string}) and a Cache-Control: no-store header. It adds a new surface to the API contract (openapi.yaml) but changes no existing operation's request or response shape -- POST /v1/links, GET /{shortCode}, GET /v1/links/{shortCode}/analytics, GET /health/live, GET /health/ready, and the /v1/runs orchestration surface are all unchanged. AuthConfiguration.AUTHENTICATED_PATTERNS (the one existing interface a new public route could in principle interact with) needs no edit because it is an allow-list of authenticated paths and /v1/version is simply absent from it, which is the intended default per that class's own documented design ('a new public endpoint added later is public by default').","impactedDataFlows":"Minimal and read-only. The only data flow is: build time (Maven, spring-boot-maven-plugin's build-info goal) writes project.version into META-INF/build-info.properties -> Spring Boot autoconfiguration reads that file into a BuildProperties bean at application startup -> VersionController reads BuildProperties.getVersion() per request and serializes it into the response body. No database, no persistence layer, no request-derived input, and no write path is touched -- this is the same class of flow as HealthController's liveness check (process-local state, no store dependency), except even simpler since it has no external dependency to check at all (unlike readiness, which queries ConnectionSource).","impactedTests":"New test needed: a unit/slice test for VersionController (new file, e.g. VersionControllerTest) asserting the 200 status, JSON content type, response body shape, and Cache-Control header in isolation -- this is a new file, not a modification. Existing test file requiring a real edit: ContractConformanceIT.java gets one new @Test method (versionConforms()) exercising the live endpoint through TestRestTemplate and validating it against the OpenApiConformanceTest harness, following the exact shape of its existing healthConforms() test. ResponseExamplesTest, OpenApiConformanceTest, and ContractDriftDetectionTest require no changes: the first two walk the OpenAPI document's paths generically and will pick up /v1/version automatically once the document is edited; ContractDriftDetectionTest's existing assertions are scoped to /v1/links as a harness self-test and are unaffected by an unrelated new path. SecureDefaultsTest needs no change because this design does not touch application.yml or the actuator exposure allow-list. DependencyDirectionTest and CredentialBoundaryTest need no change because VersionController introduces no new package-boundary or credential-handling surface beyond patterns those tests already permit for HealthController.","documentation":"The OpenAPI contract (specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml) IS the documentation of record for this API per ADR-005's 'contract documents are the source of truth' -- its update is both the contract-impact item above and the documentation deliverable; no separate README or docs/delivery surface enumerates individual endpoints (checked: no non-evidence, non-governance doc under docs/ references /health/live or /health/ready), so there is no second document to keep in sync. No ADR is implicated -- this is an additive, non-architectural endpoint using an existing, already-accepted pattern (public health-style controller in the delivery package), not a new architectural decision.","regressionRisks":"Low. The change is purely additive at the routing level (a new path, no existing path touched) and the one build-file edit (pom.xml's build-info execution) only adds a new goal execution to an existing plugin declaration -- it does not change any existing dependency version, plugin configuration, or the surefire/failsafe test-tier split, so it cannot regress the existing fast/integration test separation. The main latent risk is BuildProperties: if the build-info goal is misconfigured (e.g. placed in a profile that doesn't run in the default build, or the plugin execution is malformed), BuildProperties.getVersion() would be unavailable and Spring would fail to autowire it into VersionController at context-startup, which fails LOUD (ApplicationContext refresh failure) rather than silently -- so this risk cannot manifest as a passing-but-wrong deployment, only as a build/startup failure caught immediately by any test that boots the Spring context (several already exist). Secondary risk: BuildProperties also carries a build timestamp field alongside version; the controller must serialize only the 'version' field (Map.of(\"version\", ...), never the whole BuildProperties object) to avoid incidentally exposing build time or artifact/group id beyond what 1e actually asks for -- the additionalProperties:false schema in openapi.yaml plus the contract conformance test both catch an accidental extra field if this discipline slips.","rolloutRollback":"Rollout is a normal code deploy with no migration, no schema change, no configuration flag, and no feature toggle -- the endpoint exists the moment the new build is running, consistent with the project's stated absence of an on/off mode flag (CR-028, Decision J) which this design does not reintroduce. No data is created or mutated, so there is no forward-only concern (unlike the Flyway migration chain) and no backfill. Rollback is simply redeploying the prior artifact version; because /v1/version is new and unauthenticated with no client-visible prior behavior to preserve, removing it has zero blast radius on any existing caller. The only rollback-adjacent consideration is the openapi.yaml contract itself: per its own versioning policy (info.description), an additive path is a MINOR contract change, so if this endpoint were later removed that would need to go through the document's stated MAJOR/new-path-prefix discipline -- not a concern for THIS rollout, but noted so a future rollback of this specific feature is not treated as a routine revert once the contract has shipped and been consumed."}}
```
```

### S7 — model claude-sonnet-5

```
```diff
diff --git a/pom.xml b/pom.xml
index 0000000..0000000 100644
--- a/pom.xml
+++ b/pom.xml
@@ -123,11 +123,18 @@
   </dependencies>
 
   <build>
     <plugins>
       <plugin>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-maven-plugin</artifactId>
-      </plugin>
+        <executions>
+          <execution>
+            <goals>
+              <goal>build-info</goal>
+            </goals>
+          </execution>
+        </executions>
+      </plugin>
 
       <!--
         T017: two-tier separation. The fast tier must run container-free, because a red-green loop
diff --git a/specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml b/specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml
index 0000000..0000000 100644
--- a/specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml
+++ b/specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml
@@ -330,8 +330,32 @@
                 notReady:
                   value:
                     status: "DOWN"
                     checks:
                       store: "DOWN"
 
+  /v1/version:
+    get:
+      tags: [operations]
+      summary: Report the running build version
+      operationId: getVersion
+      description: >
+        Public and unauthenticated (CN-012). Returns the build-time project version, never a
+        request-time computation.
+      security: []
+      responses:
+        '200':
+          description: Build version
+          headers:
+            Cache-Control:
+              required: true
+              schema: { type: string, enum: [no-store] }
+          content:
+            application/json:
+              schema: { $ref: '#/components/schemas/Version' }
+              examples:
+                version:
+                  value:
+                    version: "0.1.0-SNAPSHOT"
+
   /v1/runs:
     post:
@@ -712,8 +736,14 @@
         status: { type: string, enum: [UP, DOWN] }
         checks:
           type: object
           additionalProperties: { type: string, enum: [UP, DOWN] }
           description: Dependency names and states only. Never connection details.
 
+    Version:
+      type: object
+      required: [version]
+      additionalProperties: false
+      properties:
+        version: { type: string }
+
     RunInspection:
       type: object
diff --git a/src/test/java/agentic/shortener/contract/ContractConformanceIT.java b/src/test/java/agentic/shortener/contract/ContractConformanceIT.java
index 0000000..0000000 100644
--- a/src/test/java/agentic/shortener/contract/ContractConformanceIT.java
+++ b/src/test/java/agentic/shortener/contract/ContractConformanceIT.java
@@ -138,8 +138,16 @@
         ResponseEntity<String> ready = rest.getForEntity(url("/health/ready"), String.class);
         assertEquals(200, ready.getStatusCode().value());
         harness.assertConforms("/health/ready", "get", 200, ready.getBody());
     }
 
+    @Test
+    @DisplayName("GET /v1/version 200 conforms")
+    void versionConforms() {
+        ResponseEntity<String> response = rest.getForEntity(url("/v1/version"), String.class);
+        assertEquals(200, response.getStatusCode().value());
+        harness.assertConforms("/v1/version", "get", 200, response.getBody());
+    }
+
     @Test
     @DisplayName("the harness is strict: every walking-skeleton response rejects unexpected fields")
     void skeletonResponsesAreStrict() {
diff --git a/src/main/java/agentic/shortener/delivery/VersionController.java b/src/main/java/agentic/shortener/delivery/VersionController.java
new file mode 100644
index 0000000..0000000
--- /dev/null
+++ b/src/main/java/agentic/shortener/delivery/VersionController.java
@@ -0,0 +1,25 @@
+package agentic.shortener.delivery;
+
+import org.springframework.boot.info.BuildProperties;
+import org.springframework.http.HttpHeaders;
+import org.springframework.http.ResponseEntity;
+import org.springframework.web.bind.annotation.GetMapping;
+import org.springframework.web.bind.annotation.RestController;
+
+import java.util.Map;
+
+@RestController
+public class VersionController {
+
+    private final BuildProperties buildProperties;
+
+    public VersionController(BuildProperties buildProperties) {
+        this.buildProperties = buildProperties;
+    }
+
+    @GetMapping("/v1/version")
+    public ResponseEntity<Map<String, String>> version() {
+        return ResponseEntity.ok()
+                .header(HttpHeaders.CACHE_CONTROL, "no-store")
+                .body(Map.of("version", buildProperties.getVersion()));
+    }
+}
diff --git a/src/test/java/agentic/shortener/delivery/VersionControllerTest.java b/src/test/java/agentic/shortener/delivery/VersionControllerTest.java
new file mode 100644
index 0000000..0000000
--- /dev/null
+++ b/src/test/java/agentic/shortener/delivery/VersionControllerTest.java
@@ -0,0 +1,32 @@
+package agentic.shortener.delivery;
+
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.boot.info.BuildProperties;
+import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
+import org.springframework.boot.test.mock.mockito.MockBean;
+import org.springframework.test.web.servlet.MockMvc;
+
+import static org.mockito.Mockito.when;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
+
+@WebMvcTest(VersionController.class)
+class VersionControllerTest {
+
+    @Autowired
+    private MockMvc mockMvc;
+
+    @MockBean
+    private BuildProperties buildProperties;
+
+    @Test
+    void returnsVersionWithNoStoreCacheControl() throws Exception {
+        when(buildProperties.getVersion()).thenReturn("0.1.0-SNAPSHOT");
+
+        mockMvc.perform(get("/v1/version"))
+                .andExpect(status().isOk())
+                .andExpect(content().contentType("application/json"))
+                .andExpect(header().string("Cache-Control", "no-store"))
+                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"));
+    }
+}
```
```

### S2 — model claude-sonnet-5

```
```json
[
  {"externalId": "1a", "type": "FUNCTIONAL", "statement": "The system SHALL expose a GET /v1/version endpoint that accepts no path parameters and no query parameters."},
  {"externalId": "1b", "type": "FUNCTIONAL", "statement": "The GET /v1/version endpoint SHALL be publicly accessible and SHALL NOT require authentication or authorization of any kind."},
  {"externalId": "1c", "type": "FUNCTIONAL", "statement": "On a valid request, GET /v1/version SHALL respond with HTTP status code 200."},
  {"externalId": "1d", "type": "FUNCTIONAL", "statement": "The GET /v1/version response SHALL include a Content-Type header with value application/json."},
  {"externalId": "1e", "type": "FUNCTIONAL", "statement": "The GET /v1/version response body SHALL be a JSON object containing exactly one field, \"version\", whose value is a string representing the application's build version."},
  {"externalId": "1f", "type": "NON_FUNCTIONAL", "statement": "The GET /v1/version response SHALL include a Cache-Control header with value no-store, so that responses are never cached by clients or intermediaries."}
]
```
```

