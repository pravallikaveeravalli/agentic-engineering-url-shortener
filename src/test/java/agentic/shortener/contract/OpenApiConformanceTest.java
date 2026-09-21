package agentic.shortener.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The response-conformance harness. Task T012. NFR-TST-001, plan §2. ADR-005.
 *
 * <p>Validates a <em>live</em> response body against the schema {@code contracts/openapi.yaml} declares
 * for that path, method and status. Gate 7 froze the document, so this is the mechanism that keeps the
 * implementation honest to it — a document nothing checks is a wish.
 *
 * <p><strong>Deliberately not a test class.</strong> This is the harness; {@link ContractConformanceIT}
 * drives it against real responses and {@code ContractDriftDetectionTest} (T013) proves it can fail. A
 * harness that has never failed is unvalidated, which is why T013 exists at all.
 *
 * <p><strong>additionalProperties is respected, not relaxed.</strong> T012's guard requires the
 * validation to reject an unexpected field: a permissive configuration gives false confidence, and the
 * whole point is to catch a response that has drifted <em>beyond</em> the contract as well as one that
 * has fallen short of it.
 *
 * <p>The three orchestration operations T012's Artifact names — {@code createRun}, {@code inspectRun},
 * {@code recordGateDecision} — are covered by this harness by construction: it resolves any declared
 * path/method/status. They are not <em>exercised</em> until Phase 5 builds those endpoints, and
 * pretending otherwise would be the kind of claim this project records rather than makes.
 */
public final class OpenApiConformanceTest {

    private static final Path OPENAPI =
            Path.of("specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml");

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonNode document;
    private final JsonSchemaFactory factory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public OpenApiConformanceTest() {
        try {
            this.document = YAML.readTree(Files.readString(OPENAPI));
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + OPENAPI.toAbsolutePath(), e);
        }
    }

    /** Raised when a live response does not conform. Carries the location so a failure is actionable. */
    public static final class ConformanceViolation extends AssertionError {
        private static final long serialVersionUID = 1L;

        ConformanceViolation(String message) {
            super(message);
        }
    }

    /**
     * Validates one response body against its declared schema.
     *
     * @param path       the OpenAPI path template, e.g. {@code /v1/links}
     * @param method     lower-case method, e.g. {@code post}
     * @param status     the status code as declared, e.g. {@code 201}
     * @param jsonBody   the live response body
     * @throws ConformanceViolation if the document declares no such response, or the body fails it
     */
    public void assertConforms(String path, String method, int status, String jsonBody) {
        String where = method.toUpperCase() + " " + path + " " + status;

        JsonNode declared = document.path("paths").path(path).path(method)
                .path("responses").path(String.valueOf(status));
        if (declared.isMissingNode()) {
            // An undeclared response is itself drift: the implementation is answering in a way the
            // contract does not describe.
            throw new ConformanceViolation(
                    "the contract declares no response for " + where
                            + ". An undeclared status is drift, not an omission in this test.");
        }

        JsonNode schemaRef = declared.path("content").path("application/json").path("schema");
        if (schemaRef.isMissingNode()) {
            if (jsonBody == null || jsonBody.isBlank()) {
                return;   // a declared body-less response, correctly empty
            }
            throw new ConformanceViolation(
                    where + " declares no JSON body, but a body was returned: " + jsonBody);
        }

        JsonNode schemaNode = resolve(schemaRef);
        if (schemaNode == null) {
            throw new ConformanceViolation(where + ": schema $ref does not resolve");
        }

        // Carry components across so nested $refs resolve. Without this a nested reference would
        // validate nothing and the harness would pass everything.
        ObjectNode withComponents = (ObjectNode) schemaNode.deepCopy();
        withComponents.set("components", document.path("components"));

        JsonNode body;
        try {
            body = JSON.readTree(jsonBody == null ? "null" : jsonBody);
        } catch (Exception e) {
            throw new ConformanceViolation(where + ": response body is not JSON: " + jsonBody);
        }

        JsonSchema schema = factory.getSchema(withComponents);
        Set<ValidationMessage> problems = schema.validate(body);
        if (!problems.isEmpty()) {
            throw new ConformanceViolation(where + " does not conform: " + problems
                    + "  body was: " + jsonBody);
        }
    }

    /**
     * True when the document declares {@code additionalProperties: false} for the schema behind this
     * response. T012's guard requires unexpected fields to be rejected; this lets a test assert that the
     * contract is written strictly enough for that to be possible at all.
     */
    public boolean rejectsUnexpectedFields(String path, String method, int status) {
        JsonNode schemaRef = document.path("paths").path(path).path(method).path("responses")
                .path(String.valueOf(status)).path("content").path("application/json").path("schema");
        JsonNode resolved = resolve(schemaRef);
        return resolved != null
                && resolved.path("additionalProperties").isBoolean()
                && !resolved.path("additionalProperties").asBoolean();
    }

    /** Every path/method/status the document declares with a JSON body. */
    public Map<String, JsonNode> declaredJsonResponses() {
        Map<String, JsonNode> found = new java.util.LinkedHashMap<>();
        JsonNode paths = document.path("paths");
        for (Iterator<Map.Entry<String, JsonNode>> pit = paths.fields(); pit.hasNext(); ) {
            Map.Entry<String, JsonNode> path = pit.next();
            for (Iterator<Map.Entry<String, JsonNode>> oit = path.getValue().fields(); oit.hasNext(); ) {
                Map.Entry<String, JsonNode> op = oit.next();
                JsonNode responses = op.getValue().path("responses");
                if (responses.isMissingNode()) {
                    continue;
                }
                for (Iterator<Map.Entry<String, JsonNode>> rit = responses.fields(); rit.hasNext(); ) {
                    Map.Entry<String, JsonNode> r = rit.next();
                    if (!r.getValue().path("content").path("application/json").isMissingNode()) {
                        found.put(op.getKey().toUpperCase() + " " + path.getKey() + " " + r.getKey(),
                                r.getValue());
                    }
                }
            }
        }
        return found;
    }

    private JsonNode resolve(JsonNode schema) {
        if (schema.isMissingNode()) {
            return null;
        }
        JsonNode ref = schema.path("$ref");
        if (ref.isMissingNode()) {
            return schema;
        }
        String pointer = ref.asText();
        if (!pointer.startsWith("#/")) {
            return null;
        }
        JsonNode node = document;
        for (String segment : pointer.substring(2).split("/")) {
            node = node.path(segment);
            if (node.isMissingNode()) {
                return null;
            }
        }
        return node;
    }
}
