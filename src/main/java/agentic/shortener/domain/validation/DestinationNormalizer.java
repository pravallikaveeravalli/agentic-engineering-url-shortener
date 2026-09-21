package agentic.shortener.domain.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Destination normalization. Task T034. FR-URL-003, NFR-SEC-001.
 *
 * <p>Two properties, and they pull against each other. Normalization must be <strong>idempotent</strong>
 * — {@code normalize(normalize(x))} equals {@code normalize(x)} — and it must <strong>not alter the
 * effective destination</strong>. The first alone is satisfied by a function that discards its input; the
 * second alone by the identity function. Every rule below has to hold both.
 *
 * <p><strong>Applied before validation, so it must survive input validation will reject.</strong> This
 * function is total: null, blank, over-length, control-character-bearing and unparseable input all return
 * rather than throw. A normalizer that threw would have to run <em>after</em> validation to be safe,
 * inverting the order FR-URL-003 states.
 *
 * <p><strong>Not a repair step.</strong> Input this cannot parse is returned as it arrived, for
 * {@link UrlSyntaxValidator} to refuse. Guessing at what a malformed destination meant would turn an
 * input FR-URL-002 must reject into one that is accepted.
 *
 * <p><strong>What is normalized</strong>, each equivalence-preserving under RFC 3986 §6.2.2: surrounding
 * whitespace; scheme case; host case; a port equal to the scheme's default; an empty path becoming
 * {@code /}; dot segments; percent-encodings of unreserved characters, decoded; the hex digits of every
 * surviving percent-encoding, upper-cased.
 *
 * <p><strong>What is deliberately left alone</strong>, because each would change where the follower
 * lands: path case, the query string in full — value, order and encoding — the fragment, a non-default
 * port, a trailing slash, and {@code %2F}, which is not a path separator.
 *
 * <p><strong>Two traps worth naming.</strong> First, {@code %2E} is <em>not</em> decoded even though
 * {@code .} is an unreserved character: decoding it would create dot segments that the next step then
 * collapses, so {@code /a/%2E%2E/b} — a literal {@code ..} segment somebody encoded on purpose — would
 * become {@code /b}. That is both a changed destination and a broken idempotency, since the collapse
 * would happen on the second pass and not the first. Second, credentials in the authority are
 * <em>kept</em>: EC-007 refuses them, and quietly deleting them here would turn a destination that must
 * be rejected into one that works.
 *
 * <p>Pure domain: no framework, no persistence, no control-plane import.
 */
public final class DestinationNormalizer {

    /** RFC 3986 §2.3, less {@code .} — see the class note on {@code %2E}. */
    private static final String DECODABLE_UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_~";

    /**
     * Normalizes a destination, or returns it unchanged when it cannot be parsed.
     *
     * @param raw the submitted destination; may be {@code null}
     * @return the normalized form, {@code null} for {@code null} input
     */
    public String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return trimmed;
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            // Not an absolute URL with a host. There is nothing here that can be normalized without
            // guessing, so it is passed through for validation to refuse.
            return trimmed;
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);

        int port = uri.getPort();
        if (port == defaultPortFor(scheme)) {
            port = -1;
        }

        // Percent-encoding first, dot segments second. The other order would decode an encoding into a
        // dot segment that this pass then leaves alone and the next pass collapses — a fixed point
        // reached on the third call rather than the second.
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        path = removeDotSegments(normalizePercentEncoding(path));
        if (path.isEmpty()) {
            path = "/";
        }

        StringBuilder out = new StringBuilder(scheme).append("://");
        if (uri.getRawUserInfo() != null) {
            out.append(uri.getRawUserInfo()).append('@');
        }
        out.append(host);
        if (port != -1) {
            out.append(':').append(port);
        }
        out.append(path);
        if (uri.getRawQuery() != null) {
            out.append('?').append(normalizePercentEncoding(uri.getRawQuery()));
        }
        if (uri.getRawFragment() != null) {
            out.append('#').append(normalizePercentEncoding(uri.getRawFragment()));
        }
        return out.toString();
    }

    /** -1 for any scheme without a default, so nothing is dropped from one we do not know. */
    private static int defaultPortFor(String scheme) {
        return switch (scheme) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }

    /**
     * RFC 3986 §6.2.2.2 and §6.2.2.1. Decodes the unreserved characters and upper-cases the hex digits
     * of everything else. Reserved encodings — {@code %2F}, {@code %3F}, {@code %23}, {@code %25} and
     * the rest — are preserved, because decoding them would change the URL's structure rather than its
     * spelling. {@code %25} in particular must survive, or {@code %2541} would decode to {@code %41} on
     * one pass and to {@code A} on the next.
     */
    private static String normalizePercentEncoding(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 2 < value.length()
                    && isHexDigit(value.charAt(i + 1)) && isHexDigit(value.charAt(i + 2))) {
                char decoded = (char) Integer.parseInt(value.substring(i + 1, i + 3), 16);
                if (DECODABLE_UNRESERVED.indexOf(decoded) >= 0) {
                    out.append(decoded);
                } else {
                    out.append('%')
                            .append(Character.toUpperCase(value.charAt(i + 1)))
                            .append(Character.toUpperCase(value.charAt(i + 2)));
                }
                i += 2;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * RFC 3986 §5.2.4, written to preserve what the algorithm's usual shortcuts lose.
     *
     * <p><strong>Empty segments are kept.</strong> A split-and-drop-empties implementation turns
     * {@code /a//b} into {@code /a/b}, and {@code //} is a real path on servers that use it. Only
     * {@code .} and {@code ..} segments are removed, and a trailing one leaves the trailing slash
     * behind — {@code /a/.} is {@code /a/}, not {@code /a}.
     */
    private static String removeDotSegments(String path) {
        if (path.indexOf('.') < 0) {
            return path;   // nothing a dot segment could hide in
        }
        String[] segments = path.split("/", -1);
        List<String> kept = new ArrayList<>(segments.length);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            boolean last = i == segments.length - 1;
            if (segment.equals(".")) {
                if (last) {
                    kept.add("");
                }
                continue;
            }
            if (segment.equals("..")) {
                // A leading ".." is discarded rather than escaping the root, as §5.2.4 requires. The
                // single empty segment representing the leading slash is not something to pop.
                boolean onlyTheLeadingSlash = kept.size() == 1 && kept.get(0).isEmpty();
                if (!kept.isEmpty() && !onlyTheLeadingSlash) {
                    kept.remove(kept.size() - 1);
                }
                if (last) {
                    kept.add("");
                }
                continue;
            }
            kept.add(segment);
        }
        return String.join("/", kept);
    }
}
