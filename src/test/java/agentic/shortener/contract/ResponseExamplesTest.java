package agentic.shortener.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T030 — every response code carries an example, and every example validates against its own schema.
 *
 * <p>T030's guard: <strong>examples that do not validate against their schema are worse than
 * none.</strong> A reader trusts an example more than prose, so a wrong one actively misleads — and it
 * is the thing most likely to drift, because changing a schema does not break an example the way it
 * breaks code.
 *
 * <p>Two assertions, deliberately separate so a failure says which problem it is:
 * <ol>
 *   <li>Every response with a JSON body declares at least one example.
 *   <li>Every declared example validates against the schema its own response points at.
 * </ol>
 *
 * <p>Fast tier: reads the document, starts nothing.
 */
@DisplayName("T030 response examples exist and validate")
class ResponseExamplesTest {

    private static final Path OPENAPI =
            Path.of("specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml");

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static JsonNode document() throws Exception {
        return YAML.readTree(Files.readString(OPENAPI));
    }

    /** One response body: where it lives, what schema it claims, and what examples it offers. */
    private record ResponseBody(String where, JsonNode schema, JsonNode examples) {
    }

    private static List<ResponseBody> jsonResponseBodies(JsonNode doc) {
        List<ResponseBody> bodies = new ArrayList<>();
        JsonNode paths = doc.path("paths");
        for (Iterator<Map.Entry<String, JsonNode>> pathIt = paths.fields(); pathIt.hasNext(); ) {
            Map.Entry<String, JsonNode> pathEntry = pathIt.next();
            for (Iterator<Map.Entry<String, JsonNode>> opIt = pathEntry.getValue().fields();
                 opIt.hasNext(); ) {
                Map.Entry<String, JsonNode> opEntry = opIt.next();
                JsonNode responses = opEntry.getValue().path("responses");
                if (responses.isMissingNode()) {
                    continue;
                }
                for (Iterator<Map.Entry<String, JsonNode>> rIt = responses.fields(); rIt.hasNext(); ) {
                    Map.Entry<String, JsonNode> rEntry = rIt.next();
                    JsonNode json = rEntry.getValue().path("content").path("application/json");
                    if (json.isMissingNode()) {
                        // A body-less response (a 307 redirect carries headers only) cannot have an
                        // example of a body, and requiring one would be nonsense rather than rigour.
                        continue;
                    }
                    bodies.add(new ResponseBody(
                            pathEntry.getKey() + " " + opEntry.getKey().toUpperCase() + " "
                                    + rEntry.getKey(),
                            json.path("schema"),
                            json.path("examples")));
                }
            }
        }
        return bodies;
    }

    @Test
    @DisplayName("every JSON response body declares at least one example")
    void everyJsonResponseHasAnExample() throws Exception {
        List<String> missing = new ArrayList<>();
        for (ResponseBody body : jsonResponseBodies(document())) {
            boolean hasExamples = body.examples().isObject() && body.examples().size() > 0;
            if (!hasExamples) {
                missing.add(body.where());
            }
        }
        assertTrue(missing.isEmpty(),
                "these JSON responses declare no example, so a reader has only prose to go on: "
                        + missing);
    }

    @Test
    @DisplayName("every declared example VALIDATES against the schema its response points at")
    void everyExampleValidatesAgainstItsOwnSchema() throws Exception {
        JsonNode doc = document();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        List<String> problems = new ArrayList<>();
        for (ResponseBody body : jsonResponseBodies(doc)) {
            if (!body.examples().isObject()) {
                continue;
            }
            JsonNode resolved = resolve(doc, body.schema());
            if (resolved == null) {
                problems.add(body.where() + ": schema $ref does not resolve");
                continue;
            }

            // Inline the component schemas so $ref inside the resolved schema still resolves. Without
            // this a nested $ref would silently validate nothing and the test would be decorative.
            JsonNode withComponents = ((com.fasterxml.jackson.databind.node.ObjectNode)
                    resolved.deepCopy()).set("components", doc.path("components"));

            SchemaValidatorsConfig config = new SchemaValidatorsConfig();
            JsonSchema schema;
            try {
                schema = factory.getSchema(withComponents, config);
            } catch (RuntimeException e) {
                problems.add(body.where() + ": schema did not compile: " + e.getMessage());
                continue;
            }

            for (Iterator<Map.Entry<String, JsonNode>> exIt = body.examples().fields();
                 exIt.hasNext(); ) {
                Map.Entry<String, JsonNode> example = exIt.next();
                JsonNode value = example.getValue().path("value");
                if (value.isMissingNode()) {
                    problems.add(body.where() + " example '" + example.getKey() + "': no value");
                    continue;
                }
                Set<ValidationMessage> messages = schema.validate(value);
                if (!messages.isEmpty()) {
                    problems.add(body.where() + " example '" + example.getKey() + "': " + messages);
                }
            }
        }

        assertTrue(problems.isEmpty(),
                "an example that does not validate against its own schema is worse than no example, "
                        + "because a reader trusts it more than the prose. Problems: " + problems);
    }

    /** Follows a local {@code $ref} into {@code #/components/schemas/...}, or returns the inline schema. */
    private static JsonNode resolve(JsonNode doc, JsonNode schema) {
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
        JsonNode node = doc;
        for (String segment : pointer.substring(2).split("/")) {
            node = node.path(segment);
            if (node.isMissingNode()) {
                return null;
            }
        }
        return node;
    }
}
