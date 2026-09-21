package agentic.shortener.domain.validation;

import java.util.regex.Pattern;

/**
 * Credentials in a submitted destination. Task T037. FR-URL-017 (<strong>non-waivable</strong>), EC-007.
 *
 * <p><strong>Two obligations, not one.</strong> EC-007 traces to FR-URL-002 in the spec's matrix, so a
 * destination carrying credentials is <em>refused</em>. FR-URL-017 separately requires that such material
 * never reach a log, a trace or an error response — which still matters after a refusal, because the
 * refusal is precisely the thing that gets logged.
 *
 * <p><strong>A bare username counts.</strong> {@code https://trusted.example@evil.example} carries no
 * password and is the oldest URL phishing trick there is: the eye reads the part before the {@code @}.
 * The userinfo component is credential material whether or not it contains a colon.
 *
 * <p><strong>Why redaction operates on arbitrary text.</strong> Telemetry is sentences, stack traces and
 * JSON, not tidy URLs, and the leak this guards against is not {@code log.info(destination)} — it is a
 * framework doing it on somebody's behalf. A redactor that only handled a well-formed URL would be
 * bypassed by the first exception message that quoted what it could not parse.
 *
 * <p><strong>The leak this task actually found.</strong> Spring MVC logs the deserialized request body at
 * {@code DEBUG} through the DTO's {@code toString}, so the credential reached the log before any
 * validation ran and a refusal alone would not have stopped it. {@code LinkController.CreateLinkRequest}
 * therefore overrides {@code toString} to redact, and {@code CredentialTelemetryIT} reads every event the
 * application emits to prove the leak is closed.
 *
 * <p>Pure domain: no framework, no persistence, no control-plane import.
 */
public final class CredentialRedactor {

    /** What replaces the userinfo. Fixed, so a redacted line is greppable and unmistakable. */
    public static final String REDACTED = "<redacted>";

    /**
     * The userinfo component: everything between a scheme's {@code ://} and the {@code @} that ends the
     * authority. The character class excludes {@code /}, {@code ?}, {@code #} and whitespace so an
     * {@code @} in a path, a query or a fragment is not mistaken for a credential — an over-refusing
     * control gets switched off, which helps nobody.
     */
    private static final Pattern USERINFO =
            Pattern.compile("([A-Za-z][A-Za-z0-9+.\\-]*://)([^/?#\\s@]*)@");

    private static final String NO_CREDENTIALS =
            "A destination must not carry credentials in its authority (EC-007, FR-URL-017).";

    private CredentialRedactor() {
    }

    /** Whether the destination's authority carries a userinfo component. Total; never throws. */
    public static boolean carriesCredentials(String destination) {
        return destination != null && USERINFO.matcher(destination).find();
    }

    /**
     * Refuses a destination carrying credentials.
     *
     * @throws IllegalArgumentException with a message built from a constant — <strong>never</strong>
     *                                  from the destination. This is the single most likely leak path in
     *                                  the feature: the message is built from the very string that must
     *                                  not be reproduced, it goes into a 400 body, and it is logged on
     *                                  the way out.
     */
    public static void requireNoCredentials(String destination) {
        if (carriesCredentials(destination)) {
            throw new IllegalArgumentException(NO_CREDENTIALS);
        }
    }

    /**
     * Replaces any userinfo with {@link #REDACTED}, leaving everything else byte for byte.
     *
     * <p>Total and idempotent: it handles {@code null}, blank text, several occurrences in one line and
     * its own output. The host survives, because the host is not the secret and a log line with no host
     * left in it is useless for the operator who has to act on it.
     */
    public static String redact(String value) {
        if (value == null) {
            return null;
        }
        return USERINFO.matcher(value).replaceAll("$1" + REDACTED + "@");
    }
}
