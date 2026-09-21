package agentic.shortener.policy;

import agentic.shortener.policy.definitions.PolicyDefinitions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T098 — twelve policy definitions, all mandatory, matching plan §9's table exactly. FR-ORC-022.
 */
@DisplayName("T098 — PolicyDefinitions: twelve, all mandatory, matching plan §9")
class PolicyDefinitionsTest {

    /** plan §9's table, ordered as written there. */
    private static final List<String> EXPECTED_IDS = List.of(
            "POL-SEC-001", "POL-SEC-002", "POL-SEC-003", "POL-PRIV-001", "POL-AUD-001", "POL-DEP-001",
            "POL-LIC-001", "POL-CHG-001", "POL-CHG-002", "POL-TST-001", "POL-TRC-001", "POL-CHG-003");

    @Test
    @DisplayName("exactly twelve definitions")
    void exactlyTwelve() {
        assertEquals(12, PolicyDefinitions.DEFAULT.definitions().size());
    }

    @Test
    @DisplayName("matches plan §9's table exactly, as a set — no id invented, none missing")
    void matchesPlanTableExactly() {
        Set<String> actual = PolicyDefinitions.DEFAULT.definitions().stream()
                .map(PolicyDefinition::id).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.copyOf(EXPECTED_IDS), actual);
    }

    @Test
    @DisplayName("NEGATIVE: no advisory instance exists — all twelve are mandatory")
    void noAdvisoryInstanceExists() {
        List<PolicyDefinition> advisory = PolicyDefinitions.DEFAULT.definitions().stream()
                .filter(d -> !d.mandatory()).toList();
        assertTrue(advisory.isEmpty(), "advisory policies found, but plan §9's table carries none: "
                + advisory);
    }

    @Test
    @DisplayName("POL-CHG-003 is present (CR-013) and POL-AUD-002 is absent (CR-017)")
    void versionOneOneOneDelta() {
        assertTrue(PolicyDefinitions.DEFAULT.definitions().stream()
                .anyMatch(d -> d.id().equals("POL-CHG-003")), "POL-CHG-003 must be present — CR-013");
        assertTrue(PolicyDefinitions.DEFAULT.definitions().stream()
                .noneMatch(d -> d.id().equals("POL-AUD-002")), "POL-AUD-002 must be absent — CR-017");
    }

    @Test
    @DisplayName("every definition carries a non-blank domain and description")
    void everyDefinitionCarriesDomainAndDescription() {
        for (PolicyDefinition definition : PolicyDefinitions.DEFAULT.definitions()) {
            assertTrue(!definition.domain().isBlank(), definition.id() + " has a blank domain");
            assertTrue(!definition.description().isBlank(), definition.id() + " has a blank description");
        }
    }

    @Test
    @DisplayName("the policy set version is policy-set-1.1.0")
    void versionIsOneOneOne() {
        assertEquals("policy-set-1.1.0", PolicyDefinitions.DEFAULT.version());
    }
}
