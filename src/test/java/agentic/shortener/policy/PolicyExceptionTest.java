package agentic.shortener.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T103 — the policy exception workflow's seven required fields (plus policyId and the materialization
 * path). Constitution §Exception procedure, KE-18.
 */
@DisplayName("T103 — PolicyException: schema rejects a missing field, especially the compensating control")
class PolicyExceptionTest {

    private static final Instant APPROVED = Instant.parse("2026-09-21T12:00:00Z");
    private static final Instant EXPIRES = APPROVED.plus(Duration.ofDays(30));

    private static PolicyException valid() {
        return new PolicyException("POL-SEC-003", "dependency vulnerability scan",
                "no CVE feed integrated yet", "S10 evaluation only", "Pravallika Veeravalli",
                "manual quarterly dependency review", "low — no known active exploitation vector",
                APPROVED, EXPIRES, "docs/governance/exceptions/EX-001-example.md");
    }

    @Test
    @DisplayName("a fully-formed exception constructs")
    void fullyFormedExceptionConstructs() {
        PolicyException exception = valid();
        assertFalse(exception.isExpired(APPROVED.plusSeconds(1)));
    }

    @Test
    @DisplayName("NEGATIVE: a missing compensating control is rejected — the schema is the control")
    void missingCompensatingControlIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PolicyException("POL-SEC-003", "clause",
                "reason", "scope", "authority", " ", "residual risk", APPROVED, EXPIRES,
                "docs/governance/exceptions/EX-002.md"));
    }

    @Test
    @DisplayName("NEGATIVE: each of the other required fields is rejected when blank")
    void eachRequiredFieldRejectedWhenBlank() {
        assertThrows(IllegalArgumentException.class, () -> new PolicyException(" ", "c", "r", "s", "a",
                "cc", "rr", APPROVED, EXPIRES, "docs/governance/exceptions/EX-003.md"));
        assertThrows(IllegalArgumentException.class, () -> new PolicyException("id", " ", "r", "s", "a",
                "cc", "rr", APPROVED, EXPIRES, "docs/governance/exceptions/EX-003.md"));
        assertThrows(IllegalArgumentException.class, () -> new PolicyException("id", "c", " ", "s", "a",
                "cc", "rr", APPROVED, EXPIRES, "docs/governance/exceptions/EX-003.md"));
        assertThrows(IllegalArgumentException.class, () -> new PolicyException("id", "c", "r", " ", "a",
                "cc", "rr", APPROVED, EXPIRES, "docs/governance/exceptions/EX-003.md"));
        assertThrows(IllegalArgumentException.class, () -> new PolicyException("id", "c", "r", "s", " ",
                "cc", "rr", APPROVED, EXPIRES, "docs/governance/exceptions/EX-003.md"));
        assertThrows(IllegalArgumentException.class, () -> new PolicyException("id", "c", "r", "s", "a",
                "cc", " ", APPROVED, EXPIRES, "docs/governance/exceptions/EX-003.md"));
    }

    @Test
    @DisplayName("NEGATIVE: a repositoryRecordPath outside docs/governance/exceptions/ is rejected")
    void repositoryRecordPathMustMatchTheSchemaPattern() {
        assertThrows(IllegalArgumentException.class, () -> new PolicyException("id", "c", "r", "s", "a",
                "cc", "rr", APPROVED, EXPIRES, "docs/elsewhere/EX-004.md"));
        assertThrows(IllegalArgumentException.class, () -> new PolicyException("id", "c", "r", "s", "a",
                "cc", "rr", APPROVED, EXPIRES, "docs/governance/exceptions/EX-004.txt"));
    }

    @Test
    @DisplayName("NEGATIVE: expiresAt not after approvedAt is rejected")
    void expiresAtMustBeAfterApprovedAt() {
        assertThrows(IllegalArgumentException.class, () -> new PolicyException("id", "c", "r", "s", "a",
                "cc", "rr", APPROVED, APPROVED, "docs/governance/exceptions/EX-005.md"));
    }

    @Test
    @DisplayName("EC-027: isExpired is evaluated against the caller's now, not fixed at approval time")
    void isExpiredEvaluatedAgainstCallersNow() {
        PolicyException exception = valid();
        assertFalse(exception.isExpired(EXPIRES.minusSeconds(1)), "not yet expired, one second before");
        assertTrue(exception.isExpired(EXPIRES), "expired exactly at the boundary");
        assertTrue(exception.isExpired(EXPIRES.plusSeconds(1)), "expired well after");
    }
}
