package agentic.shortener.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T033 — URL syntax and length validation. FR-URL-002, NFR-SEC-001. EC-006.
 *
 * <p>T033's Done condition is that <strong>each rejection class returns a distinguishable reason</strong>,
 * which is stronger than "rejects bad input". A validator that answered *"invalid"* to everything would
 * reject correctly and be useless: a caller cannot fix what they cannot identify, and a support request
 * becomes a guessing game.
 *
 * <p>T033's guard adds three prohibitions a test can check: a rejected request must persist nothing,
 * consume no code, and <strong>not echo input in a way enabling injection</strong>. The last is the one
 * most easily missed — a reason string that quotes the offending URL back is a reflected-injection vector
 * the moment anything renders it.
 *
 * <p>Fast tier: pure domain.
 */
@DisplayName("T033 URL syntax and length validation")
class UrlSyntaxValidatorTest {

    private final UrlSyntaxValidator validator = new UrlSyntaxValidator();

    @Test
    @DisplayName("a well-formed absolute URL is accepted")
    void wellFormedIsAccepted() {
        for (String ok : List.of(
                "https://example.com",
                "https://example.com/path?query=1#fragment",
                "http://sub.example.co.uk:8080/a/b/c")) {
            UrlSyntaxValidator.Result result = validator.validate(ok);
            assertTrue(result.valid(), ok + " should be accepted but was refused: " + result.reason());
        }
    }

    @Test
    @DisplayName("EC-006: at and beyond the maximum length")
    void lengthBoundary() {
        // The boundary itself is legal. This is the half of a bound that gets forgotten, and getting it
        // wrong by one rejects valid input forever.
        String exactly2048 = "https://e.com/" + "x".repeat(2048 - "https://e.com/".length());
        assertEquals(2048, exactly2048.length());
        assertTrue(validator.validate(exactly2048).valid(),
                "exactly 2048 characters is within FR-URL-001's bound and must be accepted");

        String over = exactly2048 + "x";
        UrlSyntaxValidator.Result result = validator.validate(over);
        assertTrue(!result.valid(), "2049 characters must be refused (EC-006)");
        assertEquals(UrlSyntaxValidator.Rejection.TOO_LONG, result.rejection());
    }

    @Test
    @DisplayName("empty, blank and null are each refused with their own reason")
    void emptyAndBlank() {
        assertEquals(UrlSyntaxValidator.Rejection.EMPTY, validator.validate("").rejection());
        assertEquals(UrlSyntaxValidator.Rejection.EMPTY, validator.validate("   ").rejection());
        assertEquals(UrlSyntaxValidator.Rejection.EMPTY, validator.validate(null).rejection());
    }

    @Test
    @DisplayName("malformed shapes are refused, each with its own rejection class")
    void malformedShapes() {
        // Relative, schemeless and authority-less inputs are different mistakes with different fixes,
        // so they must not collapse into one reason.
        assertEquals(UrlSyntaxValidator.Rejection.NOT_ABSOLUTE,
                validator.validate("/just/a/path").rejection());
        assertEquals(UrlSyntaxValidator.Rejection.NOT_ABSOLUTE,
                validator.validate("example.com/a").rejection());
        assertEquals(UrlSyntaxValidator.Rejection.NO_HOST,
                validator.validate("https:///path").rejection());
        assertEquals(UrlSyntaxValidator.Rejection.MALFORMED,
                validator.validate("https://exa mple.com").rejection());
    }

    @Test
    @DisplayName("control characters and newlines are refused — header and log injection vectors")
    void controlCharacters() {
        // A newline in a destination is a response-splitting and log-forging vector the moment the value
        // reaches a header or a log line. NFR-SEC-001.
        // Written with char arithmetic rather than \\u escapes: Java resolves a unicode escape before
        // lexing, so an escaped control character in source is an invisible literal one.
        String nul = "https://example.com/" + (char) 0x00;
        String del = "https://example.com/" + (char) 0x7f;
        String tab = "https://example.com/" + (char) 0x09;
        for (String bad : List.of(
                "https://example.com/\nSet-Cookie: x=y",
                "https://example.com/\r\nLocation: https://evil.test",
                nul, del, tab)) {
            UrlSyntaxValidator.Result result = validator.validate(bad);
            assertTrue(!result.valid(), "control characters must be refused: " + bad.replace("\n", "\\n"));
            assertEquals(UrlSyntaxValidator.Rejection.CONTROL_CHARACTERS, result.rejection());
        }
    }

    @Test
    @DisplayName("T033 Done: every rejection class produces a DISTINGUISHABLE reason")
    void everyRejectionClassIsDistinguishable() {
        // The Done condition, asserted directly rather than inferred from the tests above: no two
        // rejection classes may share a reason string, or a caller cannot tell them apart.
        Set<String> reasons = new HashSet<>();
        for (UrlSyntaxValidator.Rejection rejection : UrlSyntaxValidator.Rejection.values()) {
            String reason = rejection.reason();
            assertTrue(reasons.add(reason),
                    "two rejection classes share the reason '" + reason + "'. A validator that answers "
                            + "the same thing to different problems is not actionable");
        }
        assertEquals(UrlSyntaxValidator.Rejection.values().length, reasons.size());
    }

    @Test
    @DisplayName("T033 guard: a reason NEVER echoes the submitted input")
    void reasonsDoNotEchoInput() {
        // The guard's third prohibition. A reason that quotes the URL back is a reflected-injection
        // vector as soon as anything renders it -- an error page, a log viewer, a terminal.
        String hostile = "https://evil.test/<script>alert(1)</script>?x=" + (char) 0x00;
        UrlSyntaxValidator.Result result = validator.validate(hostile);
        assertTrue(!result.valid());
        assertTrue(!result.reason().contains("evil.test"),
                "the reason must not echo the submitted destination: " + result.reason());
        assertTrue(!result.reason().contains("script"),
                "the reason must not echo submitted markup: " + result.reason());

        // And the same for an over-length input, where the temptation to report the value is strongest.
        String over = "https://e.com/" + "y".repeat(3000);
        String overReason = validator.validate(over).reason();
        assertTrue(overReason.length() < 200,
                "an over-length rejection must not carry the input; reason was "
                        + overReason.length() + " characters");
        assertTrue(!overReason.contains("yyyy"), "no fragment of the input may appear");
    }

    @Test
    @DisplayName("a rejection reports the bound rather than the value, so it is still actionable")
    void rejectionIsActionableWithoutEchoing() {
        // Not echoing input must not mean saying nothing useful. Naming the LIMIT tells a caller what to
        // do without reflecting what they sent.
        String reason = validator.validate("https://e.com/" + "z".repeat(3000)).reason();
        assertTrue(reason.contains("2048"),
                "the reason should name the bound so the caller knows the target: " + reason);
        assertNotEquals("", reason);
    }
}
