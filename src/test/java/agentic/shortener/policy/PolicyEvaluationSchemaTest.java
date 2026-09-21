package agentic.shortener.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T099 — S10's output validated against {@code contracts/policy-evaluation.schema.json} (CR-043:
 * synced to {@code policy-set-1.1.0}, twelve ids). FR-ORC-022.
 */
@DisplayName("T099 — PolicyEvaluationResult conforms to contracts/policy-evaluation.schema.json")
class PolicyEvaluationSchemaTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonSchema SCHEMA = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(readSchema());

    private static JsonNode readSchema() {
        try {
            return JSON.readTree(Files.readString(
                    Path.of("specs/001-agentic-sdlc-url-shortener/contracts/policy-evaluation.schema.json")));
        } catch (Exception e) {
            throw new IllegalStateException("could not read policy-evaluation.schema.json", e);
        }
    }

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @Test
    @DisplayName("a full twelve-check evaluation, all PASS, conforms")
    void fullEvaluationConforms() {
        List<PolicyCheckResult> results = agentic.shortener.policy.definitions.PolicyDefinitions.DEFAULT
                .definitions().stream()
                .map(d -> PolicyCheckResult.of(d, PolicyOutcome.PASS, d.id() + " passes")).toList();
        PolicyEvaluationResult result = PolicyEvaluationResult.of(UUID.randomUUID(),
                PolicySet.CURRENT_VERSION, NOW, results);

        Set<ValidationMessage> errors = SCHEMA.validate(result.toJson());
        assertTrue(errors.isEmpty(), "schema violations: " + errors);
    }

    @Test
    @DisplayName("a result carrying an approved exception object conforms")
    void resultWithExceptionConforms() {
        PolicyDefinition def = agentic.shortener.policy.definitions.PolicyDefinitions.DEFAULT
                .require("POL-SEC-003");
        PolicyException exception = new PolicyException("POL-SEC-003", "dependency vulnerability scan",
                "no CVE feed integrated yet", "S10 evaluation only", "Pravallika Veeravalli",
                "manual quarterly dependency review", "low", NOW.minusSeconds(60), NOW.plusSeconds(86_400),
                "docs/governance/exceptions/EX-001-example.md");
        PolicyCheckResult withException = PolicyCheckResult.exceptionRequested(def, "requested", exception);

        PolicyEvaluationResult result = PolicyEvaluationResult.of(UUID.randomUUID(),
                PolicySet.CURRENT_VERSION, NOW, List.of(withException));

        Set<ValidationMessage> errors = SCHEMA.validate(result.toJson());
        assertTrue(errors.isEmpty(), "schema violations: " + errors);
    }

    @Test
    @DisplayName("exactly four outcome values accepted; a fifth is rejected by the schema")
    void exactlyFourOutcomeValuesAccepted() {
        for (PolicyOutcome outcome : PolicyOutcome.values()) {
            PolicyDefinition def = agentic.shortener.policy.definitions.PolicyDefinitions.DEFAULT
                    .require("POL-TRC-001");
            PolicyCheckResult r = outcome == PolicyOutcome.EXCEPTION_REQUESTED
                    ? PolicyCheckResult.of(def, PolicyOutcome.PASS, "placeholder — not this branch")
                    : PolicyCheckResult.of(def, outcome, "checking " + outcome);
            PolicyEvaluationResult result = PolicyEvaluationResult.of(UUID.randomUUID(),
                    PolicySet.CURRENT_VERSION, NOW, List.of(r));
            Set<ValidationMessage> errors = SCHEMA.validate(result.toJson());
            assertTrue(errors.isEmpty(), outcome + ": " + errors);
        }
        assertEquals(4, PolicyOutcome.values().length,
                "no UNKNOWN or SKIPPED — exactly four values exist in the type itself");

        // The fifth, invented value — injected directly at the JSON level since PolicyOutcome's own enum
        // cannot represent it (that closure IS T099's Guard clause: "there is no UNKNOWN or SKIPPED
        // outcome; the schema is the enforcement").
        var mapper = new ObjectMapper();
        JsonNode fifth = mapper.createObjectNode()
                .put("runId", UUID.randomUUID().toString())
                .put("policySetVersion", PolicySet.CURRENT_VERSION)
                .put("evaluatedAt", NOW.toString())
                .put("blocking", false);
        var resultsArray = ((com.fasterxml.jackson.databind.node.ObjectNode) fifth).putArray("results");
        resultsArray.addObject()
                .put("policyId", "POL-TRC-001").put("domain", "compliance").put("mandatory", true)
                .put("outcome", "UNKNOWN").put("reason", "a fifth, invented outcome");

        Set<ValidationMessage> errors = SCHEMA.validate(fifth);
        assertFalse(errors.isEmpty(), "a fifth outcome value ('UNKNOWN') must be rejected by the schema");
    }

    @Test
    @DisplayName("the policyId enum has exactly twelve values, matching PolicyDefinitions.DEFAULT")
    void policyIdEnumMatchesTwelveDefinitions() throws Exception {
        JsonNode schema = readSchema();
        JsonNode enumNode = schema.at("/properties/results/items/properties/policyId/enum");
        assertTrue(enumNode.isArray());
        assertEquals(12, enumNode.size());
        Set<String> schemaIds = new java.util.HashSet<>();
        enumNode.forEach(n -> schemaIds.add(n.asText()));
        Set<String> definitionIds = agentic.shortener.policy.definitions.PolicyDefinitions.DEFAULT
                .definitions().stream().map(PolicyDefinition::id).collect(java.util.stream.Collectors.toSet());
        assertEquals(definitionIds, schemaIds);
    }

    @Test
    @DisplayName("policySetVersion const is policy-set-1.1.0 (CR-043)")
    void policySetVersionConstIsOneOneOne() throws Exception {
        JsonNode schema = readSchema();
        assertEquals("policy-set-1.1.0",
                schema.at("/properties/policySetVersion/const").asText());
    }
}
