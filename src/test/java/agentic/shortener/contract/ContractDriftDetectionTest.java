package agentic.shortener.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proof that the conformance harness can fail. Task T013. ADR-005 §Validation.
 *
 * <p><strong>A harness that has never failed is unvalidated.</strong> T012's harness returning green
 * proves nothing on its own — a harness that accepted everything would look identical. This is the
 * falsifiability proof ADR-005 requires, and it is the same discipline T015 applies to the architecture
 * rule and T061a to the silence test.
 *
 * <p>CR-035 placed this task's work here rather than in Slice 1: the harness needs a live endpoint to
 * validate against, and that endpoint is T032's, so the drift proof could not exist before Slice 2.
 *
 * <p>Five drift shapes, each a different way a response can diverge. They are separate tests because a
 * harness might catch one and miss another, and a single combined assertion would hide which.
 *
 * <p>Fast tier: the harness reads the document and validates a string. No server, no store.
 */
@DisplayName("T013 the conformance harness detects drift")
class ContractDriftDetectionTest {

    private final OpenApiConformanceTest harness = new OpenApiConformanceTest();

    /** A body that conforms to the frozen `Link` schema, used as the baseline for each mutation. */
    private static final String CONFORMING_LINK = """
            {"shortCode":"aB3xK9p","destination":"https://example.com/a",
             "createdAt":"2026-09-21T12:00:00Z","expiresAt":"2026-10-21T12:00:00Z",
             "state":"ACTIVE","replay":false}""";

    @Test
    @DisplayName("baseline: a conforming body PASSES, so a later failure means something")
    void conformingBodyPasses() {
        // Checked first and deliberately. If the harness rejected everything, every drift test below
        // would pass for the wrong reason and this file would be worthless.
        assertDoesNotThrow(() -> harness.assertConforms("/v1/links", "post", 201, CONFORMING_LINK),
                "a body matching the frozen Link schema must pass, or the harness is broken rather "
                        + "than strict");
    }

    @Test
    @DisplayName("drift 1: a MISSING required field is detected")
    void missingRequiredFieldIsDetected() {
        String noState = """
                {"shortCode":"aB3xK9p","destination":"https://example.com/a",
                 "createdAt":"2026-09-21T12:00:00Z","expiresAt":"2026-10-21T12:00:00Z",
                 "replay":false}""";

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> harness.assertConforms("/v1/links", "post", 201, noState));
        assertTrue(thrown.getMessage().contains("state"),
                "the failure must name the missing field so a developer can act on it; got: "
                        + thrown.getMessage());
    }

    @Test
    @DisplayName("drift 2: an UNEXPECTED field is detected — additionalProperties is not relaxed")
    void unexpectedFieldIsDetected() {
        // T012's guard: validation must reject additionalProperties. A permissive configuration gives
        // false confidence, and a response that has grown a field is drift just as much as one that
        // has lost one.
        assertTrue(harness.rejectsUnexpectedFields("/v1/links", "post", 201),
                "the contract must declare additionalProperties: false for this response, or an "
                        + "unexpected field cannot be caught at all");

        String extraField = """
                {"shortCode":"aB3xK9p","destination":"https://example.com/a",
                 "createdAt":"2026-09-21T12:00:00Z","expiresAt":"2026-10-21T12:00:00Z",
                 "state":"ACTIVE","replay":false,"clickCount":42}""";

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> harness.assertConforms("/v1/links", "post", 201, extraField));
        assertTrue(thrown.getMessage().contains("clickCount"),
                "the failure must name the unexpected field; got: " + thrown.getMessage());
    }

    @Test
    @DisplayName("drift 3: a WRONG TYPE is detected")
    void wrongTypeIsDetected() {
        String replayAsString = """
                {"shortCode":"aB3xK9p","destination":"https://example.com/a",
                 "createdAt":"2026-09-21T12:00:00Z","expiresAt":"2026-10-21T12:00:00Z",
                 "state":"ACTIVE","replay":"false"}""";

        // A string "false" where a boolean belongs is the classic serialisation drift, and it is exactly
        // the kind a human reading the JSON would skim past.
        assertThrows(AssertionError.class,
                () -> harness.assertConforms("/v1/links", "post", 201, replayAsString),
                "a string where a boolean is declared must be caught");
    }

    @Test
    @DisplayName("drift 4: a VALUE OUTSIDE AN ENUM is detected")
    void invalidEnumValueIsDetected() {
        String badState = """
                {"shortCode":"aB3xK9p","destination":"https://example.com/a",
                 "createdAt":"2026-09-21T12:00:00Z","expiresAt":"2026-10-21T12:00:00Z",
                 "state":"DELETED","replay":false}""";

        // LinkState has exactly ACTIVE and EXPIRED — there is no DELETED, because KE-01 says a link is
        // never deleted. A response inventing one would contradict the data model, so the contract must
        // refuse it.
        AssertionError thrown = assertThrows(AssertionError.class,
                () -> harness.assertConforms("/v1/links", "post", 201, badState));
        assertTrue(thrown.getMessage().contains("state") || thrown.getMessage().contains("DELETED"),
                "the failure should point at the offending value; got: " + thrown.getMessage());
    }

    @Test
    @DisplayName("drift 5: an UNDECLARED STATUS CODE is detected")
    void undeclaredStatusIsDetected() {
        // Answering 418 where the contract declares 201/200/400/401/409/429/503 is drift even if the
        // body is immaculate. A harness that only validated declared statuses would miss it entirely.
        AssertionError thrown = assertThrows(AssertionError.class,
                () -> harness.assertConforms("/v1/links", "post", 418, CONFORMING_LINK));
        assertTrue(thrown.getMessage().contains("declares no response"),
                "an undeclared status must be reported as such; got: " + thrown.getMessage());
    }

    @Test
    @DisplayName("removing the drift makes conformance pass again (T013's Done condition)")
    void removingDriftRestoresConformance() {
        // T013's Done: "drift detected; removing the drift makes conformance pass again". Asserted as a
        // pair so the harness is shown to discriminate rather than merely to reject.
        assertThrows(AssertionError.class, () -> harness.assertConforms("/v1/links", "post", 201,
                CONFORMING_LINK.replace("\"state\":\"ACTIVE\"", "\"state\":\"BOGUS\"")));
        assertDoesNotThrow(() -> harness.assertConforms("/v1/links", "post", 201, CONFORMING_LINK));
    }
}
