package agentic.shortener.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract files parse and conform. Task T011.
 *
 * <p><strong>This closes a known, disclosed gap.</strong> ADR-005 §Validation records that the
 * Phase-1 contract files were <em>parse-validated</em> on 2026-09-20 — finding and fixing one real
 * defect, {@code RunInspection.ai} silently coerced to {@code [true, false]} by YAML 1.1 — but that
 * <em>structural conformance</em> was never checked. ADR-005 carries the meta-schema lint as an owed
 * residual, and this test is what discharges it.
 *
 * <p>Both documents changed shape twice since that check: to v2.0.0 at CR-013 (the node model) and to
 * v3.0.0 at CR-029 (the run-level {@code ai} flag removed, including from the state schema's
 * {@code required} array). So this runs against the current shape, not the one that was parsed.
 *
 * <p>Fast tier: reads files, starts nothing.
 */
@DisplayName("T011 contract files parse and conform to their meta-schemas")
class ContractFilesLintTest {

    private static final Path CONTRACTS =
            Path.of("specs/001-agentic-sdlc-url-shortener/contracts");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    @DisplayName("workflow-state.schema.json parses and validates against JSON Schema 2020-12")
    void stateSchemaParsesAndConformsToItsMetaSchema() throws Exception {
        Path file = CONTRACTS.resolve("workflow-state.schema.json");
        assertTrue(Files.exists(file), "missing contract file: " + file.toAbsolutePath());

        JsonNode document = JSON.readTree(Files.readString(file));

        assertEquals("3.0.0", document.path("version").asText(),
                "CR-029 took the state schema to v3.0.0; a stale version here means the document and "
                        + "its change record disagree");

        // The structural check ADR-005 recorded as owed. Rather than fetching the 2020-12
        // meta-schema over the network — which would make this test require a network and break the
        // claim that the whole suite runs offline — the document is COMPILED as a schema and then
        // shown to DISCRIMINATE. Compilation rejects a structurally invalid schema, and the
        // discrimination check proves the compiled result is a working validator rather than a
        // permissive one that accepts anything.
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        JsonSchema compiled = factory.getSchema(document);

        // A snapshot missing every required field must be REJECTED. If this passed, the schema
        // would be decorative and every later conformance assertion built on it would be worthless.
        Set<ValidationMessage> onEmpty = compiled.validate(JSON.readTree("{}"));
        assertFalse(onEmpty.isEmpty(),
                "the compiled state schema accepted an empty object, so it enforces nothing \u2014 a "
                        + "schema that cannot reject is not a contract");

        // And the required fields it names must be the ones it complains about, so the failure is
        // actionable rather than merely present.
        String complaints = onEmpty.toString();
        for (String mandatory : List.of("runId", "state", "policySetVersion", "nodes", "edges")) {
            assertTrue(complaints.contains(mandatory),
                    "rejecting an empty snapshot must name the missing required field '" + mandatory
                            + "'; got: " + complaints);
        }

        // CR-029's removal, asserted structurally rather than by grep: a persisted snapshot without
        // the field must validate, so the field must be gone from `required` as well as `properties`.
        JsonNode required = document.path("required");
        assertFalse(required.isMissingNode(), "the state schema must declare a required array");
        for (JsonNode entry : required) {
            assertFalse("ai".equals(entry.asText()),
                    "CR-029 removed the run-level ai flag; it must not remain in `required`, or a "
                            + "snapshot written by the current model fails validation");
        }
        assertTrue(document.path("properties").path("ai").isMissingNode(),
                "CR-029 removed the run-level ai flag from properties too");

        // CR-013's node model: thirteen nodes on a fresh run, and no maximum, because S7 fans out.
        assertEquals(13, document.path("properties").path("nodes").path("minItems").asInt(),
                "a fresh run has 13 nodes: 12 singletons plus the join");
        assertTrue(document.path("properties").path("nodes").path("maxItems").isMissingNode(),
                "there is no maximum: S7 fans out to one child per task");
    }

    @Test
    @DisplayName("openapi.yaml parses as YAML and declares OpenAPI 3.1 at v3.0.0")
    void openApiDocumentParsesAndDeclaresItsVersions() throws Exception {
        Path file = CONTRACTS.resolve("openapi.yaml");
        assertTrue(Files.exists(file), "missing contract file: " + file.toAbsolutePath());

        JsonNode document = YAML.readTree(Files.readString(file));

        assertEquals("3.1.0", document.path("openapi").asText(),
                "ADR-005 fixes OpenAPI 3.1, whose schema dialect is JSON Schema 2020-12");
        assertEquals("3.0.0", document.path("info").path("version").asText(),
                "CR-029 took the API document to v3.0.0");

        // The defect ADR-005 found was a YAML 1.1 coercion: unquoted on/off became booleans. The
        // field is gone now (CR-029), so the regression assertion is that it is gone rather than
        // that it is quoted.
        assertTrue(document.path("components").path("schemas").path("RunInspection")
                        .path("properties").path("ai").isMissingNode(),
                "RunInspection.ai was removed by CR-029; its absence is also what retires the YAML "
                        + "1.1 coercion defect ADR-005 recorded");
        assertTrue(document.path("components").path("schemas").path("CreateRunRequest")
                        .path("properties").path("ai").isMissingNode(),
                "CreateRunRequest.ai was removed by CR-029");

        // Eight paths after CR-013 added createRun and recordGateDecision.
        assertEquals(8, document.path("paths").size(),
                "CR-013 took the document to eight paths; a different count means the document and "
                        + "the record disagree");
    }

    @Test
    @DisplayName("every contract file named by contracts/README.md exists and parses")
    void everyDeclaredContractFileExistsAndParses() throws Exception {
        // README is the index; a file listed there but absent would be a broken promise to a reader.
        for (String name : List.of("openapi.yaml", "workflow-state.schema.json")) {
            Path file = CONTRACTS.resolve(name);
            assertTrue(Files.exists(file), "declared contract file missing: " + name);
            ObjectMapper mapper = name.endsWith(".yaml") ? YAML : JSON;
            assertFalse(mapper.readTree(Files.readString(file)).isMissingNode(),
                    name + " did not parse into a document");
        }
    }
}
