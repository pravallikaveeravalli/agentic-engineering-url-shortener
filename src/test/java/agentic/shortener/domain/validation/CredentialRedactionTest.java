package agentic.shortener.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T037 — credentials in a submitted destination. FR-URL-017 (<strong>non-waivable</strong>), EC-007.
 *
 * <p>T037's guard is unlike the others: <em>"this clause is non-waivable; a failure here is not a bug to
 * schedule, it is a stop condition."</em> So this file is deliberately paranoid, and the assertions are
 * about what is <strong>absent</strong> from output rather than about what a function returns.
 *
 * <p><strong>Two obligations, not one.</strong> EC-007 traces to FR-URL-002 in the spec's matrix, so a
 * destination carrying credentials is <em>refused</em>. FR-URL-017 separately requires that such material
 * never reach a log or a trace — which still matters after a refusal, because a refusal is exactly the
 * thing that gets logged. Detection, refusal and redaction are therefore all here, and
 * {@code CredentialTelemetryIT} closes the loop over real captured output.
 *
 * <p><strong>A bare username counts.</strong> {@code https://trusted.example@evil.example} carries no
 * password and is the oldest URL phishing trick there is; the userinfo component is credential material
 * whether or not a colon appears in it.
 *
 * <p>Fast tier: pure domain.
 */
@DisplayName("T037 credential detection, refusal and redaction")
class CredentialRedactionTest {

    /** Distinctive enough that a fragment surviving anywhere is unmistakable. */
    private static final String SECRET = "hunter2-qzjxvm";

    @Test
    @DisplayName("EC-007: credentials in the authority are detected, in every shape")
    void credentialsDetected() {
        for (String destination : List.of(
                "https://user:" + SECRET + "@example.com/path",
                "https://user@example.com/path",
                "HTTPS://User:Pass@EXAMPLE.com/",
                "http://admin:admin@192.0.2.1/",
                "https://%75ser:pass@example.com/",
                "https://trusted.example@evil.example/",
                "https://@example.com/",
                "https://user:pass@example.com:8443/x?q=1#f")) {
            assertTrue(CredentialRedactor.carriesCredentials(destination),
                    "credentials not detected in: " + destination);
        }
    }

    @Test
    @DisplayName("an @ outside the authority is NOT a credential — no false refusals")
    void atSignElsewhereIsNotACredential() {
        // A validator that refused every URL containing '@' would refuse legitimate destinations, and
        // an over-refusing security control gets switched off. The '@' has to be in the authority.
        for (String destination : List.of(
                "https://example.com/a@b",
                "https://example.com/users/me@example.com/profile",
                "https://example.com/?to=me@example.com",
                "https://example.com/#me@example.com",
                "https://example.com/",
                "mailto:me@example.com")) {
            assertFalse(CredentialRedactor.carriesCredentials(destination),
                    "false positive on: " + destination);
        }
    }

    @Test
    @DisplayName("detection is total — no input makes it throw")
    void detectionIsTotal() {
        for (String odd : List.of("", "   ", "not a url", "https://", "://@", "@@@", "http://a@")) {
            assertDoesNotThrow(() -> CredentialRedactor.carriesCredentials(odd), "threw on: " + odd);
        }
        assertDoesNotThrow(() -> CredentialRedactor.carriesCredentials(null));
        assertFalse(CredentialRedactor.carriesCredentials(null));
    }

    @Test
    @DisplayName("EC-007: a credential-bearing destination is REFUSED")
    void credentialBearingDestinationIsRefused() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> CredentialRedactor.requireNoCredentials(
                        "https://user:" + SECRET + "@example.com/path"));
        assertTrue(thrown.getMessage().contains("credential"),
                "the refusal must name the rule: " + thrown.getMessage());

        // And a destination without them passes.
        CredentialRedactor.requireNoCredentials("https://example.com/path");
        CredentialRedactor.requireNoCredentials("https://example.com/a@b");
    }

    @Test
    @DisplayName("NON-WAIVABLE: the refusal itself carries no credential material")
    void theRefusalDoesNotLeakWhatItRefused() {
        // The most likely leak path in the whole feature. The refusal message is built from the very
        // string that must not be reproduced, it goes into a 400 body, and it is logged on the way.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> CredentialRedactor.requireNoCredentials(
                        "https://admin:" + SECRET + "@internal.example/db"));
        String message = thrown.getMessage();
        assertFalse(message.contains(SECRET), message);
        assertFalse(message.contains("hunter2"), message);
        assertFalse(message.contains("admin"), message);
        assertFalse(message.contains("internal.example"), message);
    }

    @Test
    @DisplayName("redaction removes the userinfo and nothing else")
    void redactionRemovesOnlyTheUserinfo() {
        assertEquals("https://<redacted>@example.com/path?q=1#f",
                CredentialRedactor.redact("https://user:" + SECRET + "@example.com/path?q=1#f"));
        assertEquals("https://<redacted>@example.com:8443/Path",
                CredentialRedactor.redact("https://user@example.com:8443/Path"));

        // Nothing to redact means nothing changes. A redactor that mangled ordinary URLs would make
        // every log line harder to read for no benefit.
        for (String clean : List.of("https://example.com/a@b", "https://example.com/", "", "plain text")) {
            assertEquals(clean, CredentialRedactor.redact(clean));
        }
    }

    @Test
    @DisplayName("redaction works on log lines, not just on bare URLs")
    void redactionWorksInsideLogText() {
        // Telemetry carries sentences, stack traces and JSON — not tidy single URLs. A redactor that
        // only handled a well-formed URL would be bypassed by the first exception message.
        String line = "2026-09-21T03:00:00Z ERROR refused destination https://u:" + SECRET
                + "@example.com/x and also https://a:" + SECRET + "@other.example/y (2 attempts)";
        String redacted = CredentialRedactor.redact(line);

        assertFalse(redacted.contains(SECRET), redacted);
        assertEquals(2, redacted.split("<redacted>", -1).length - 1,
                "both occurrences must be redacted, not just the first: " + redacted);
        assertTrue(redacted.contains("(2 attempts)"), "surrounding text must survive: " + redacted);
        assertTrue(redacted.contains("example.com") && redacted.contains("other.example"),
                "the host is not the secret and stays readable: " + redacted);
    }

    @Test
    @DisplayName("redaction is idempotent and total")
    void redactionIsIdempotentAndTotal() {
        for (String input : List.of(
                "https://user:" + SECRET + "@example.com/x",
                "https://example.com/x",
                "://@", "@@@", "http://a@", "", "   ", "not a url")) {
            String once = CredentialRedactor.redact(input);
            assertEquals(once, CredentialRedactor.redact(once),
                    "redaction is not a fixed point for: " + input);
            assertFalse(once.contains(SECRET), once);
        }
        assertDoesNotThrow(() -> CredentialRedactor.redact(null));
    }

    @Test
    @DisplayName("NON-WAIVABLE: no fragment of the secret survives redaction, in any shape")
    void noFragmentSurvives() {
        // Asserted against a corpus rather than one example, because the failure mode is a regex that
        // handles the common shape and leaves an uncommon one intact.
        for (String destination : List.of(
                "https://user:" + SECRET + "@example.com",
                "https://" + SECRET + "@example.com",
                "https://user:" + SECRET + "@example.com:443/p?q=" + 1 + "#frag",
                "HTTPS://USER:" + SECRET + "@EXAMPLE.COM/",
                "https://user:" + SECRET + "@example.com/path/https://second:" + SECRET + "@h.example",
                "  https://user:" + SECRET + "@example.com  ")) {
            String redacted = CredentialRedactor.redact(destination);
            assertFalse(redacted.contains(SECRET),
                    "the secret survived redaction of '" + destination + "' as '" + redacted + "'");
            assertFalse(redacted.contains("hunter2"), redacted);
            assertFalse(redacted.contains("qzjxvm"), redacted);
        }
    }

    @Test
    @DisplayName("ShortLink refuses a credential-bearing destination as a type invariant")
    void theTypeItselfRefuses() {
        // T021's Done condition is that invariants live in the type rather than in callers. A
        // destination carrying credentials is not a valid destination, so the type refuses it and no
        // caller has to remember to ask. This is what makes EC-007 live before T039 wires the use case.
        assertThrows(IllegalArgumentException.class,
                () -> agentic.shortener.domain.link.ShortLink.create("abc123",
                        "https://user:" + SECRET + "@example.com/x",
                        java.util.UUID.randomUUID(),
                        java.time.Instant.parse("2026-09-21T00:00:00Z"),
                        java.time.Instant.parse("2026-10-21T00:00:00Z")));
    }
}
