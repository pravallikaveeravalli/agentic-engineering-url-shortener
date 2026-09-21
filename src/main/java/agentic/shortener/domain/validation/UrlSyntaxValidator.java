package agentic.shortener.domain.validation;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * URL syntax and length validation. Task T033. FR-URL-002, NFR-SEC-001. EC-006.
 *
 * <p><strong>Each rejection class carries its own reason.</strong> That is T033's Done condition, and it
 * is a stronger requirement than refusing bad input. A validator that answered <em>"invalid"</em> to
 * everything would refuse correctly and be useless: an over-length destination and a missing host are
 * different mistakes with different fixes, and a caller who cannot tell them apart cannot make either.
 *
 * <p><strong>A reason never contains the submitted destination.</strong> FR-URL-002's negative clause
 * forbids echoing rejected input in a way that enables injection, and these reasons reach an
 * unauthenticated caller through a 400 body. Quoting the URL back would make every error response a
 * reflection point the moment anything rendered it — an error page, a log viewer, a terminal. The reasons
 * are therefore fixed strings attached to the enum, and they stay actionable by naming the
 * <em>bound or rule</em> rather than the value that broke it.
 *
 * <p><strong>What this class does not do.</strong> Scheme allow-listing is {@link SchemeAllowList}'s, and
 * normalization runs before validation ({@link DestinationNormalizer}). This checks shape and size only.
 *
 * <p>Pure domain: no framework, no persistence, no control-plane import.
 */
public final class UrlSyntaxValidator {

    /** FR-URL-001. EC-006 is the case at and beyond this bound. */
    public static final int MAX_DESTINATION_LENGTH = 2048;

    /**
     * The rejection classes. One reason each, distinct from every other — asserted, not assumed, because
     * two classes sharing a reason silently undoes T033's Done condition.
     */
    public enum Rejection {

        EMPTY("A destination is required."),

        TOO_LONG("A destination must be no longer than " + MAX_DESTINATION_LENGTH + " characters."),

        CONTROL_CHARACTERS("A destination must not contain control characters."),

        NOT_ABSOLUTE("A destination must be an absolute URL beginning with a scheme."),

        NO_HOST("A destination must name a host."),

        MALFORMED("A destination must be a well-formed URL.");

        private final String reason;

        Rejection(String reason) {
            this.reason = reason;
        }

        /** The caller-facing reason. Names the rule; never the input. */
        public String reason() {
            return reason;
        }
    }

    /**
     * The outcome. {@code rejection} is {@code null} and {@code reason} empty when valid, so a caller
     * cannot accidentally surface a reason for something that was accepted.
     */
    public record Result(boolean valid, Rejection rejection, String reason) {

        static Result accepted() {
            return new Result(true, null, "");
        }

        static Result refused(Rejection rejection) {
            return new Result(false, rejection, rejection.reason());
        }
    }

    /** Validates shape and size. Total: any input, including {@code null}, produces a result. */
    public Result validate(String destination) {
        if (destination == null || destination.isBlank()) {
            return Result.refused(Rejection.EMPTY);
        }

        // Checked before length, deliberately. A control character is a response-splitting and
        // log-forging vector, and naming that is more useful to the caller than naming the size of a
        // string that also happens to be too long.
        if (containsControlCharacter(destination)) {
            return Result.refused(Rejection.CONTROL_CHARACTERS);
        }

        if (destination.length() > MAX_DESTINATION_LENGTH) {
            return Result.refused(Rejection.TOO_LONG);
        }

        URI parsed;
        try {
            parsed = new URI(destination);
        } catch (URISyntaxException e) {
            // The exception's own message quotes the offending input, which is exactly what must not
            // reach a caller. It is dropped here rather than wrapped.
            return Result.refused(Rejection.MALFORMED);
        }

        if (parsed.getScheme() == null) {
            return Result.refused(Rejection.NOT_ABSOLUTE);
        }
        if (parsed.getHost() == null) {
            // A destination with no host cannot be redirected to. This also catches the opaque forms
            // (`mailto:`, `javascript:`) that parse cleanly but name no target; SchemeAllowList refuses
            // them on their own grounds as well.
            return Result.refused(Rejection.NO_HOST);
        }

        return Result.accepted();
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
