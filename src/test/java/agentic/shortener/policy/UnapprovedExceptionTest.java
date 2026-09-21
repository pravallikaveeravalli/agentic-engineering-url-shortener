package agentic.shortener.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T104 — EXCEPTION_REQUESTED without recorded approval evaluates as FAIL and blocks. FR-ORC-022.
 *
 * <p>{@link PolicyException} cannot exist un-approved (see its own javadoc), so "unapproved" is
 * represented here by {@code EXCEPTION_REQUESTED} with {@code exception == null} — the realistic shape a
 * policy check produces when it recognizes a requested-but-not-yet-approved exception without inventing a
 * partial approval record for it.
 */
@DisplayName("T104 — an unapproved exception must not silently function as a waiver")
class UnapprovedExceptionTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final PolicyDefinition MANDATORY =
            new PolicyDefinition("POL-SEC-003", "security", true, "test");

    @Test
    @DisplayName("EXCEPTION_REQUESTED with no exception object attached blocks progression")
    void unapprovedExceptionBlocks() {
        PolicyCheckResult result = new PolicyCheckResult(MANDATORY.id(), MANDATORY.domain(),
                MANDATORY.mandatory(), PolicyOutcome.EXCEPTION_REQUESTED,
                "exception requested but not yet approved", null);

        assertTrue(BlockingEnforcer.isBlocking(List.of(result), NOW),
                "an unapproved exception must not silently function as a waiver");
    }

    @Test
    @DisplayName("once approved (a real PolicyException attached, not expired), the same policy does not "
            + "block on this alone")
    void approvedAndUnexpiredExceptionDoesNotBlockAlone() {
        PolicyException approved = new PolicyException("POL-SEC-003", "dependency vulnerability scan",
                "no CVE feed integrated yet", "S10 evaluation only", "Pravallika Veeravalli",
                "manual quarterly dependency review", "low", NOW.minusSeconds(60),
                NOW.plusSeconds(86_400), "docs/governance/exceptions/EX-001-example.md");
        PolicyCheckResult result = PolicyCheckResult.exceptionRequested(MANDATORY, "approved exception",
                approved);

        assertTrue(!BlockingEnforcer.isBlocking(List.of(result), NOW),
                "an approved, unexpired exception is the one case EXCEPTION_REQUESTED does not itself block");
    }
}
