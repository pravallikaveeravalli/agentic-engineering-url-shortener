package agentic.shortener.domain.validation;

import java.util.Locale;
import java.util.Set;

/**
 * The scheme allow-list. Task T035. FR-URL-004, <strong>non-waivable</strong> under Constitution V.
 *
 * <p><strong>An allow-list, not a deny-list.</strong> Refusing the three schemes FR-URL-004 names and
 * permitting everything else would leave {@code vbscript}, {@code blob}, {@code filesystem},
 * {@code intent}, {@code view-source} and whatever a browser ships next. The default answer here is
 * "no", and a scheme nobody has thought of is refused for the same reason {@code javascript} is.
 *
 * <p><strong>Not configurable, by construction.</strong> FR-URL-004's negative clause is that no
 * configuration, override or exception may permit a non-allow-listed scheme. This class therefore reads
 * nothing: no property, no variable, no file, no injected binding. It holds one {@code static final}
 * immutable set and offers no way to change it. A configurable allow-list is not an allow-list, and a
 * requirement marked non-waivable cannot be left to a deployment to honour.
 *
 * <p><strong>The single definition.</strong> {@code ShortLink} held its own copy of this set until this
 * task; it now delegates here. Two copies of a security control drift, and the copy nobody tests is the
 * one that stays wrong. {@code SchemeAllowListTest} asserts that no other source file defines a second.
 *
 * <p><strong>Refusals name the rule and never the input.</strong> FR-URL-002's negative clause forbids
 * echoing a rejected destination in a way that enables injection, and this refusal reaches the caller
 * through the 400 body. The previous version of this check — inside {@code ShortLink} — concatenated the
 * raw destination into its message, which {@code LinkController}'s marker allow-list then passed
 * straight through to the response. That was a live defect; not repeating it is why the messages below
 * are constants.
 *
 * <p>Pure domain: no framework, no persistence, no control-plane import.
 */
public final class SchemeAllowList {

    /**
     * FR-URL-004. {@code javascript} and {@code data} turn a redirect into code execution in the
     * follower's browser; {@code file} turns it into a local-file read. Those three are named in the
     * requirement's evidence line for exactly that reason — and they are refused here by absence, not
     * by enumeration.
     */
    private static final Set<String> PERMITTED = Set.of("http", "https");

    /**
     * RFC 3986 §3.1. Checked against the <em>raw</em> candidate before any trimming or case folding: a
     * scheme carrying whitespace or a control character is a smuggling attempt, because a parser that
     * strips those sees a different scheme from one that does not, and normalising first would hand the
     * attempt a pass.
     */
    private static final String SCHEME_GRAMMAR = "[A-Za-z][A-Za-z0-9+.-]*";

    private static final String NOT_ALLOW_LISTED =
            "The destination's scheme is not allow-listed; only http and https destinations are "
                    + "accepted (FR-URL-004, non-waivable).";

    private static final String NO_USABLE_SCHEME =
            "A destination must begin with an allow-listed scheme (FR-URL-004, non-waivable).";

    private SchemeAllowList() {
    }

    /** The permitted schemes. Immutable: a caller holding this set cannot widen or narrow it. */
    public static Set<String> permitted() {
        return PERMITTED;
    }

    /** Whether one scheme is permitted. Case-insensitive, because a scheme is (RFC 3986 §3.1). */
    public static boolean permits(String scheme) {
        return scheme != null && PERMITTED.contains(scheme.toLowerCase(Locale.ROOT));
    }

    /**
     * Refuses a destination whose scheme is not allow-listed.
     *
     * @throws IllegalArgumentException with a message that names the rule and never the input
     */
    public static void requirePermitted(String destination) {
        if (destination == null) {
            throw new IllegalArgumentException(NO_USABLE_SCHEME);
        }
        int colon = destination.indexOf(':');
        if (colon <= 0) {
            // No colon at all is a relative or scheme-relative reference; a colon at position zero is
            // an empty scheme. Neither names a scheme, so neither can be on the list.
            throw new IllegalArgumentException(NO_USABLE_SCHEME);
        }
        String candidate = destination.substring(0, colon);
        if (!candidate.matches(SCHEME_GRAMMAR)) {
            throw new IllegalArgumentException(NO_USABLE_SCHEME);
        }
        if (!permits(candidate)) {
            throw new IllegalArgumentException(NOT_ALLOW_LISTED);
        }
    }
}
