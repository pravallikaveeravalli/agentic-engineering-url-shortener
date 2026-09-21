package agentic.shortener.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T101 — a check that cannot be evaluated at all records {@code FAIL}, never {@code PASS}. EC-025.
 *
 * <p>{@code PolicySetEvaluatorIT.unevaluableAuditCheckIsRecordedFailNeverPass} proves this against a real
 * unreachable store (T100's real integration). This proves the same rule at the type level: {@link
 * PolicyOutcome} and {@link BlockingEnforcer} give a caller no way to represent "could not evaluate" as
 * anything other than {@code FAIL} on a mandatory check — the precedent the whole default-deny family
 * rests on (T101's own Guard clause).
 */
@DisplayName("T101 — EC-025: unevaluable never defaults to PASS")
class UnevaluableCheckTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final PolicyDefinition MANDATORY =
            new PolicyDefinition("POL-AUD-001", "audit_retention", true, "test");

    @Test
    @DisplayName("PolicyOutcome has no UNKNOWN or SKIPPED value an unevaluable check could hide behind")
    void noUnknownOrSkippedValueExists() {
        List<String> names = java.util.Arrays.stream(PolicyOutcome.values()).map(Enum::name).toList();
        assertTrue(names.contains("PASS"));
        assertTrue(names.contains("FAIL"));
        assertTrue(!names.contains("UNKNOWN"));
        assertTrue(!names.contains("SKIPPED"));
        assertEquals(4, names.size(), "exactly four values — the closed set IS the enforcement");
    }

    @Test
    @DisplayName("a check recorded FAIL because it could not be evaluated blocks exactly like any other "
            + "mandatory FAIL — there is no lesser severity for 'could not check' than for 'checked and "
            + "it failed'")
    void unevaluableRecordedAsFailBlocksTheSameAsAGenuineFailure() {
        PolicyCheckResult unevaluable = PolicyCheckResult.of(MANDATORY, PolicyOutcome.FAIL,
                "could not evaluate (EC-025: unevaluable is never PASS): connection refused");
        PolicyCheckResult genuineFailure = PolicyCheckResult.of(MANDATORY, PolicyOutcome.FAIL,
                "evaluated: three audit_record rows missing a mandatory field");

        assertEquals(BlockingEnforcer.isBlocking(List.of(unevaluable), NOW),
                BlockingEnforcer.isBlocking(List.of(genuineFailure), NOW),
                "EC-025's whole point: an unevaluable check must carry the same weight as a real failure, "
                        + "never a lighter one");
        assertTrue(BlockingEnforcer.isBlocking(List.of(unevaluable), NOW));
    }
}
