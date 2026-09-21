package agentic.shortener.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T101, T102 — a mandatory FAIL blocks; an unevaluable check is recorded FAIL and blocks the same way
 * (EC-025); an advisory-shaped (non-mandatory) FAIL never blocks. FR-ORC-022.
 */
@DisplayName("T101/T102 — BlockingEnforcer: mandatory FAIL blocks, non-mandatory never does")
class BlockingEnforcerTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final PolicyDefinition MANDATORY =
            new PolicyDefinition("POL-TEST-001", "security", true, "test");
    private static final PolicyDefinition ADVISORY =
            new PolicyDefinition("POL-TEST-002", "security", false, "test — hypothetical only");

    @Test
    @DisplayName("a mandatory FAIL blocks progression")
    void mandatoryFailBlocks() {
        var results = List.of(PolicyCheckResult.of(MANDATORY, PolicyOutcome.FAIL, "failed"));
        assertTrue(BlockingEnforcer.isBlocking(results, NOW));
    }

    @Test
    @DisplayName("EC-025: an unevaluable check recorded FAIL blocks exactly like any other mandatory FAIL")
    void unevaluableCheckRecordedAsFailBlocks() {
        var results = List.of(PolicyCheckResult.of(MANDATORY, PolicyOutcome.FAIL,
                "could not evaluate (EC-025: unevaluable is never PASS): connection refused"));
        assertTrue(BlockingEnforcer.isBlocking(results, NOW));
    }

    @Test
    @DisplayName("a mandatory PASS does not block")
    void mandatoryPassDoesNotBlock() {
        var results = List.of(PolicyCheckResult.of(MANDATORY, PolicyOutcome.PASS, "ok"));
        assertFalse(BlockingEnforcer.isBlocking(results, NOW));
    }

    @Test
    @DisplayName("NOT_APPLICABLE never blocks, mandatory or not")
    void notApplicableNeverBlocks() {
        var results = List.of(PolicyCheckResult.of(MANDATORY, PolicyOutcome.NOT_APPLICABLE, "n/a"));
        assertFalse(BlockingEnforcer.isBlocking(results, NOW));
    }

    @Test
    @DisplayName("a non-mandatory (advisory-shaped) FAIL never blocks")
    void nonMandatoryFailNeverBlocks() {
        var results = List.of(PolicyCheckResult.of(ADVISORY, PolicyOutcome.FAIL, "failed but advisory"));
        assertFalse(BlockingEnforcer.isBlocking(results, NOW));
    }

    @Test
    @DisplayName("one mandatory FAIL among many PASSes still blocks the whole evaluation")
    void oneMandatoryFailAmongManyPassesBlocks() {
        var results = List.of(
                PolicyCheckResult.of(MANDATORY, PolicyOutcome.PASS, "ok"),
                PolicyCheckResult.of(new PolicyDefinition("POL-TEST-003", "security", true, "t"),
                        PolicyOutcome.PASS, "also ok"),
                PolicyCheckResult.of(new PolicyDefinition("POL-TEST-004", "security", true, "t"),
                        PolicyOutcome.FAIL, "this one failed"));
        assertTrue(BlockingEnforcer.isBlocking(results, NOW));
    }
}
