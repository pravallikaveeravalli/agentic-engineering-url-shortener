package agentic.shortener.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T034 — destination normalization. FR-URL-003, NFR-SEC-001.
 *
 * <p>T034's Done condition names <strong>two</strong> properties, and they pull against each other:
 * normalization must be <em>idempotent</em> and it must <em>not alter the effective destination</em>. The
 * first alone is satisfied by a function that throws everything away and returns {@code "https://x/"};
 * the second alone is satisfied by the identity function. Only asserting both says anything.
 *
 * <p>The second property is asserted in the form that can actually catch an over-eager normalizer:
 * <strong>destinations that differ must stay different</strong>. Lower-casing a path, dropping a
 * fragment, sorting a query or trimming a trailing slash each look tidy and each changes where the
 * follower lands. Those are the mistakes, so those are the tests.
 *
 * <p>The tests do not re-derive a canonical form of their own. That would make this file a second
 * implementation written from the same reading as the first, and the Gate 7 sweep exists because
 * verification written that way inherits the implementation's misunderstanding. They assert
 * <em>properties</em> instead: components preserved verbatim, distinctions preserved, fixed point reached.
 *
 * <p>Fast tier: pure domain.
 */
@DisplayName("T034 destination normalization")
class DestinationNormalizerTest {

    private final DestinationNormalizer normalizer = new DestinationNormalizer();

    /**
     * A corpus spanning well-formed, hostile and unparseable input. Used by the idempotency and
     * totality properties, which must hold for <em>every</em> input rather than for chosen ones.
     */
    private static final List<String> CORPUS = List.of(
            "https://example.com",
            "https://example.com/",
            "HTTPS://Example.COM/Path",
            "https://example.com:443/x",
            "http://example.com:80/x",
            "https://example.com:8443/x",
            "https://example.com/a/./b/../c",
            "https://example.com/%7Euser/%2Fslash",
            "https://example.com/a%2fb?Q=One&r=Two#Frag",
            "  https://example.com/spaced  ",
            "https://user:secret@example.com/creds",
            "https://example.com/?",
            "https://example.com/#",
            "/not/absolute",
            "example.com/schemeless",
            "javascript:alert(1)",
            "https://exa mple.com/malformed",
            "",
            "   ");

    @Test
    @DisplayName("property 1 of 2 — idempotent: normalize(normalize(x)) == normalize(x)")
    void idempotent() {
        // T034's Validate field names this property test explicitly. It holds across the whole corpus,
        // including the inputs normalization cannot parse: a function that is idempotent only on the
        // happy path is not idempotent.
        for (String input : CORPUS) {
            String once = normalizer.normalize(input);
            String twice = normalizer.normalize(once);
            assertEquals(once, twice,
                    "normalization is not a fixed point for '" + input + "': once='" + once
                            + "' twice='" + twice + "'");
        }
    }

    @Test
    @DisplayName("normalization is TOTAL — it never throws, so it can run before validation")
    void totalFunction() {
        // T034's Artifact says normalization is applied BEFORE validation. A normalizer that threw on
        // malformed input could not be: validation would have to run first to protect it, inverting the
        // order. Totality is the half of that ordering claim assertable without a composition point;
        // the composition itself is asserted at T039, where CreateLinkUseCase first has one.
        for (String input : CORPUS) {
            assertDoesNotThrow(() -> normalizer.normalize(input), "threw on '" + input + "'");
        }
        assertDoesNotThrow(() -> normalizer.normalize(null), "null must be handled, not thrown on");
        assertDoesNotThrow(() -> normalizer.normalize("https://e.com/" + "x".repeat(5000)),
                "an over-length destination reaches the normalizer first and must survive it");
        assertDoesNotThrow(() -> normalizer.normalize("https://e.com/" + (char) 0x00),
                "a control character reaches the normalizer first too");
    }

    @Test
    @DisplayName("destinations differing only in normalizable respects reach the same stored form")
    void equivalentFormsCollapse() {
        // FR-URL-003's Accept clause. Each pair differs in a respect RFC 3986 §6.2.2 declares
        // equivalent, so collapsing them changes nothing about where the follower lands.
        assertSame("HTTPS://Example.COM/Path", "https://example.com/Path");   // scheme + host case
        assertSame("https://example.com", "https://example.com/");            // empty path is "/"
        assertSame("https://example.com:443/x", "https://example.com/x");     // default port for https
        assertSame("http://example.com:80/x", "http://example.com/x");        // default port for http
        assertSame("https://example.com/a/./b/../c", "https://example.com/a/c");   // dot segments
        assertSame("https://example.com/%7Euser", "https://example.com/~user");    // unreserved decode
        assertSame("https://example.com/a%2fb", "https://example.com/a%2Fb");      // hex digit case
        assertSame("  https://example.com/x  ", "https://example.com/x");          // surrounding space
    }

    @Test
    @DisplayName("property 2 of 2 — destinations that DIFFER stay different")
    void distinctionsArePreserved() {
        // The guard, in the form that catches the real mistake. Every pair below is two different
        // destinations, and a normalizer that merged either one would silently send followers elsewhere.
        assertDistinct("https://example.com/A", "https://example.com/a",
                "a path is case-sensitive; lower-casing it is the classic over-normalization");
        assertDistinct("https://example.com/?a=1", "https://example.com/?a=2", "query values matter");
        assertDistinct("https://example.com/?a=1&b=2", "https://example.com/?b=2&a=1",
                "query parameter order is not ours to reorder — some servers depend on it");
        assertDistinct("https://example.com/x", "https://example.com/x#section",
                "a fragment selects a position in the page; dropping it is a visible change");
        assertDistinct("https://example.com/a/", "https://example.com/a",
                "a trailing slash names a different resource on many servers");
        assertDistinct("https://example.com:8443/x", "https://example.com/x",
                "a non-default port must survive");
        assertDistinct("http://example.com/x", "https://example.com/x",
                "downgrading or upgrading the scheme would change the security of the redirect");
        assertDistinct("https://example.com/a%2Fb", "https://example.com/a/b",
                "%2F is not a path separator; decoding it changes the path structure");
        assertDistinct("https://example.com/x", "https://other.example.com/x", "the host is the target");
    }

    @Test
    @DisplayName("each preserved component survives VERBATIM, not merely equivalently")
    void componentsSurviveVerbatim() throws Exception {
        // Stated as a property of the components rather than of a canonical string, so this test does
        // not become a second implementation of the normalizer.
        String input = "https://example.com/Mixed/Case%2Fpath?Q=One&r=Two%2B#Frag-ment";
        URI before = new URI(input);
        URI after = new URI(normalizer.normalize(input));

        assertEquals(before.getRawQuery(), after.getRawQuery(),
                "the query must survive byte for byte — case, order and encoding");
        assertEquals(before.getRawFragment(), after.getRawFragment(), "the fragment must survive");
        assertEquals(before.getRawPath(), after.getRawPath(),
                "this path contains nothing normalizable, so it must come back unchanged");
        assertTrue(before.getHost().equalsIgnoreCase(after.getHost()));
        assertTrue(before.getScheme().equalsIgnoreCase(after.getScheme()));
    }

    @Test
    @DisplayName("normalization does not strip credentials — that is T037's refusal, not a silent edit")
    void credentialsAreNotSilentlyRemoved() {
        // EC-007 treats credentials in the authority as something to REFUSE. Quietly deleting them here
        // would turn a destination the creator asked for into a different one that happens to work, and
        // would hide the input that should have been rejected.
        String withCredentials = "https://user:secret@example.com/x";
        String normalized = normalizer.normalize(withCredentials);
        assertTrue(normalized.contains("user"),
                "the authority's userinfo must reach the validator so EC-007 can refuse it; got "
                        + normalized.replace("secret", "<redacted>"));
    }

    @Test
    @DisplayName("unparseable input is returned for the validator to refuse, not repaired")
    void unparseableInputIsPassedThrough() {
        // Normalization is not a repair step. Guessing at what a malformed destination meant would turn
        // an input FR-URL-002 must reject into one that is accepted.
        for (String bad : List.of("/not/absolute", "example.com/schemeless", "https://exa mple.com/x")) {
            assertEquals(bad, normalizer.normalize(bad),
                    "normalization must leave '" + bad + "' for validation to refuse");
        }
        assertEquals("", normalizer.normalize("   "), "blank collapses to empty; EMPTY is a rejection");
    }

    private void assertSame(String a, String b) {
        assertEquals(normalizer.normalize(b), normalizer.normalize(a),
                "'" + a + "' and '" + b + "' differ only in a normalizable respect and must reach the "
                        + "same stored form (FR-URL-003)");
    }

    private void assertDistinct(String a, String b, String why) {
        assertNotEquals(normalizer.normalize(a), normalizer.normalize(b),
                "'" + a + "' and '" + b + "' are DIFFERENT destinations: " + why);
    }
}
