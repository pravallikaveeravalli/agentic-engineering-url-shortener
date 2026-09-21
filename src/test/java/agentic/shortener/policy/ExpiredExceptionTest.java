package agentic.shortener.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T105 — an approved exception passing its expiry mid-run blocks progression. EC-027.
 *
 * <p>Clock-controlled: the same {@link PolicyException} is evaluated against three different {@code now}
 * values, proving expiry is checked <strong>at use</strong> rather than only recorded once at approval
 * time — an exception approved at 09:00 with a 30-day expiry is not "safe forever" just because it passed
 * one check on day one.
 */
@DisplayName("T105 — an approved exception blocks once it expires mid-run (EC-027)")
class ExpiredExceptionTest {

    private static final Instant APPROVED_AT = Instant.parse("2026-09-21T09:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-21T09:00:00Z");
    private static final PolicyDefinition MANDATORY =
            new PolicyDefinition("POL-SEC-003", "security", true, "test");

    private static PolicyCheckResult resultWithException() {
        PolicyException exception = new PolicyException("POL-SEC-003", "dependency vulnerability scan",
                "no CVE feed integrated yet", "S10 evaluation only", "Pravallika Veeravalli",
                "manual quarterly dependency review", "low", APPROVED_AT, EXPIRES_AT,
                "docs/governance/exceptions/EX-001-example.md");
        return PolicyCheckResult.exceptionRequested(MANDATORY, "approved exception", exception);
    }

    @Test
    @DisplayName("before expiry: does not block")
    void beforeExpiryDoesNotBlock() {
        Instant beforeExpiry = EXPIRES_AT.minusSeconds(3600);
        assertFalse(BlockingEnforcer.isBlocking(List.of(resultWithException()), beforeExpiry));
    }

    @Test
    @DisplayName("EC-027: at and after expiry — evaluated at use, mid-run — blocks")
    void atAndAfterExpiryBlocks() {
        assertTrue(BlockingEnforcer.isBlocking(List.of(resultWithException()), EXPIRES_AT),
                "expired exactly at the boundary");
        assertTrue(BlockingEnforcer.isBlocking(List.of(resultWithException()),
                EXPIRES_AT.plusSeconds(3600)), "expired well after");
    }

    @Test
    @DisplayName("the SAME exception object blocks or not purely as a function of when it is checked")
    void sameExceptionDifferentOutcomesAtDifferentTimes() {
        PolicyCheckResult result = resultWithException();
        boolean earlyBlocking = BlockingEnforcer.isBlocking(List.of(result), APPROVED_AT.plusSeconds(1));
        boolean lateBlocking = BlockingEnforcer.isBlocking(List.of(result), EXPIRES_AT.plusSeconds(1));

        assertFalse(earlyBlocking, "not blocking the day after approval");
        assertTrue(lateBlocking, "blocking the moment after expiry — nothing about the run changed except "
                + "the clock, which is exactly EC-027's point");
    }
}
