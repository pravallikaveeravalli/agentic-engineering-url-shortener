package agentic.shortener.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T035 — the scheme allow-list. FR-URL-004, <strong>non-waivable</strong> under Constitution V.
 *
 * <p>T035's guard is the unusual part: <em>"no configuration, override, or exception may widen this. A
 * test asserts that a config attempt to add a scheme does not take effect."</em> A matrix test alone
 * cannot discharge that — it proves today's list behaves, not that tomorrow's deployment cannot change
 * it. So this file asserts the guard structurally as well as behaviourally: the list is a compile-time
 * constant, the set it exposes cannot be mutated, the class offers no mutator, its source reads no
 * configuration at all, and a property set at runtime changes nothing.
 *
 * <p><strong>Why a deny-list would be wrong here.</strong> Refusing {@code javascript}, {@code data} and
 * {@code file} and permitting the rest would leave {@code vbscript}, {@code blob}, {@code filesystem},
 * {@code intent}, {@code view-source} and whatever a browser adds next. This project has now found four
 * separate places where a deny-list was the wrong control; this is the one where the requirement says so
 * outright.
 *
 * <p>Fast tier: pure domain plus a source read.
 */
@DisplayName("T035 scheme allow-list (non-waivable)")
class SchemeAllowListTest {

    private static final Path SOURCE =
            Path.of("src/main/java/agentic/shortener/domain/validation/SchemeAllowList.java");

    @Test
    @DisplayName("matrix: exactly http and https are permitted, in any letter case")
    void permittedSchemes() {
        for (String ok : List.of("http", "https", "HTTP", "HTTPS", "HttpS")) {
            assertTrue(SchemeAllowList.permits(ok), ok + " must be permitted");
        }
        assertEquals(Set.of("http", "https"), SchemeAllowList.permitted(),
                "the permitted set is exactly two schemes; anything else is a widening");
    }

    @Test
    @DisplayName("matrix: javascript, data and file are refused — FR-URL-004's named three")
    void theNamedThreeAreRefused() {
        // Named in FR-URL-004's evidence line because of what each one does: javascript and data turn a
        // redirect into code execution in the follower's browser, file into a local-file read.
        for (String bad : List.of("javascript", "data", "file")) {
            assertFalse(SchemeAllowList.permits(bad), bad + " must be refused");
            assertThrows(IllegalArgumentException.class,
                    () -> SchemeAllowList.requirePermitted(bad + ":whatever"),
                    bad + ": must be refused as a destination");
        }
    }

    @Test
    @DisplayName("matrix: every other scheme is refused because it is not on the list")
    void everythingElseIsRefused() {
        // Not a deny-list. These are here to show the default answer is "no" — a scheme nobody thought
        // about is refused for the same reason javascript is.
        for (String bad : List.of("ftp", "sftp", "mailto", "tel", "ws", "wss", "blob", "vbscript",
                "about", "chrome", "chrome-extension", "jar", "gopher", "ldap", "view-source",
                "filesystem", "resource", "intent", "android-app", "ms-msdt", "search-ms",
                "httpx", "https+unix", "htt", "")) {
            assertFalse(SchemeAllowList.permits(bad),
                    "'" + bad + "' is not on the allow-list and must be refused");
        }
    }

    @Test
    @DisplayName("smuggling shapes are refused: no scheme, whitespace, control characters, mixed case")
    void smugglingShapesAreRefused() {
        // A parser that strips whitespace before reading the scheme sees something different from a
        // parser that does not. Refusing the shape closes the gap rather than betting on which one runs.
        for (String bad : List.of(
                "//example.com",                 // scheme-relative
                "example.com/a",                 // no scheme at all
                ":no-scheme",                    // empty scheme
                " javascript:alert(1)",          // leading space
                "HTTPS\t://example.com",         // tab inside the scheme
                "java\nscript:alert(1)",         // newline inside the scheme
                "http\u0000s://example.com",     // NUL inside the scheme
                "ht tp://example.com")) {        // space inside the scheme
            assertThrows(IllegalArgumentException.class,
                    () -> SchemeAllowList.requirePermitted(bad),
                    "must refuse: " + bad.replace("\n", "\\n"));
        }
    }

    @Test
    @DisplayName("GUARD: a configuration attempt to add a scheme does not take effect")
    void configurationCannotWiden() {
        // The guard's own words. Several shapes of "configuration" are tried, because the point is that
        // NO external input reaches the list -- not that one particular key is ignored.
        List<String> keys = List.of(
                "shortener.url.allowed-schemes",
                "shortener.allowed-schemes",
                "SHORTENER_ALLOWED_SCHEMES",
                "allowed.schemes",
                "agentic.shortener.domain.validation.SchemeAllowList.permitted");
        List<String> restore = new ArrayList<>();
        try {
            for (String key : keys) {
                restore.add(key);
                System.setProperty(key, "http,https,javascript,data,file,ftp");
            }

            assertEquals(Set.of("http", "https"), SchemeAllowList.permitted(),
                    "a property naming extra schemes must not widen the list");
            for (String bad : List.of("javascript", "data", "file", "ftp")) {
                assertFalse(SchemeAllowList.permits(bad),
                        bad + " must still be refused with configuration asking for it");
            }
            assertThrows(IllegalArgumentException.class,
                    () -> SchemeAllowList.requirePermitted("javascript:alert(1)"),
                    "FR-URL-004 is non-waivable: configuration does not get a vote");
        } finally {
            restore.forEach(System::clearProperty);
        }
    }

    @Test
    @DisplayName("GUARD: the exposed set cannot be mutated, and the field is a compile-time constant")
    void theListIsStructurallyImmutable() throws Exception {
        Set<String> permitted = SchemeAllowList.permitted();
        assertThrows(UnsupportedOperationException.class, () -> permitted.add("javascript"),
                "a caller holding the set must not be able to add to it");
        assertThrows(UnsupportedOperationException.class, () -> permitted.remove("https"),
                "nor to remove from it — narrowing by accident is a availability bug");

        // Every field static final: there is no per-instance list to swap, and no field to reassign.
        for (Field field : SchemeAllowList.class.getDeclaredFields()) {
            assertTrue(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()),
                    "field '" + field.getName() + "' must be static final, or the list has state that "
                            + "something can change at runtime");
        }
    }

    @Test
    @DisplayName("GUARD: the class exposes no mutator of any shape")
    void noMutatorExists() {
        // A method named add/set/register/configure would be an override path even if nothing called it
        // today. Asserted by shape rather than by a list of known-bad names, so a new one is caught.
        for (Method method : SchemeAllowList.class.getDeclaredMethods()) {
            String name = method.getName();
            assertFalse(name.matches("^(set|add|put|remove|register|configure|enable|allow|widen|with|"
                            + "load|reload|init|override)[A-Z].*")
                            || name.matches("^(set|add|put|remove|register|configure|enable|allow|widen)$"),
                    "method '" + name + "' has the shape of a mutator; the allow-list must have none");
        }
    }

    @Test
    @DisplayName("GUARD: the source reads no configuration at all")
    void sourceReadsNoConfiguration() throws Exception {
        assertTrue(Files.exists(SOURCE), "missing " + SOURCE.toAbsolutePath());
        // Comment lines are stripped first. This class's own javadoc explains why it is not
        // configurable, and that explanation names the very constructs being forbidden — the fifth time
        // in this project that provenance prose has tripped an absence assertion. The right assertion is
        // "no ACTIVE code reads configuration", not "the string appears nowhere".
        String active = Files.readString(SOURCE).lines()
                .map(String::stripLeading)
                .filter(line -> !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*"))
                .reduce("", (a, b) -> a + "\n" + b);

        for (String forbidden : List.of("System.getProperty", "System.getenv", "@Value",
                "@ConfigurationProperties", "Environment", "Files.read", "getResource")) {
            assertFalse(active.contains(forbidden),
                    "SchemeAllowList must not read configuration; found '" + forbidden + "'. "
                            + "A configurable allow-list is not an allow-list (FR-URL-004)");
        }
    }

    /**
     * A second scheme <em>membership decision</em> — a collection of scheme names, or a direct comparison
     * against one. Not merely a mention: {@link DestinationNormalizer} names {@code http} and
     * {@code https} in its default-port table, and a port lookup decides nothing about permission.
     *
     * <p>The first version of this check flagged any file containing both names and caught that table on
     * its first green run. Exempting the file by name would have defeated the check; narrowing it to the
     * property it was always meant to express is the fix.
     */
    private static final List<Pattern> SECOND_DECISION = List.of(
            Pattern.compile("(Set|List)\\.of\\([^;]*\"https?\""),
            Pattern.compile("Arrays\\.asList\\([^;]*\"https?\""),
            Pattern.compile("contains\\(\\s*\"https?\"\\s*\\)"),
            Pattern.compile("equals(IgnoreCase)?\\(\\s*\"https?\"\\s*\\)"));

    @Test
    @DisplayName("GUARD: the allow-list is defined in exactly ONE place")
    void singleSourceOfTruth() throws Exception {
        // Two copies of a security control drift, and the copy nobody tests is the one that stays wrong.
        // ShortLink held its own copy until this task; it now delegates here.
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.endsWith("SchemeAllowList.java")) {
                    continue;
                }
                String active = Files.readString(file).lines()
                        .map(String::stripLeading)
                        .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                        .reduce("", (a, b) -> a + "\n" + b);
                for (Pattern pattern : SECOND_DECISION) {
                    if (pattern.matcher(active).find()) {
                        offenders.add(file + " matches /" + pattern + "/");
                    }
                }
            }
        }
        assertEquals(List.of(), offenders,
                "these files decide scheme membership for themselves: " + offenders
                        + ". There must be one allow-list, and SchemeAllowList is it");
    }

    @Test
    @DisplayName("the single-source check can FAIL — proved against a planted second decision")
    void singleSourceCheckIsFalsifiable() {
        // A structural check that has never failed is unvalidated, the same argument T013 makes about
        // the conformance harness. Proved here against planted text rather than a planted file, so the
        // proof leaves nothing behind to forget about.
        String planted = "private static final Set<String> MINE = Set.of(\"http\", \"https\");";
        assertTrue(SECOND_DECISION.stream().anyMatch(p -> p.matcher(planted).find()),
                "the check must catch a second allow-list; it did not match: " + planted);

        String handRolled = "if (scheme.equals(\"https\")) { return true; }";
        assertTrue(SECOND_DECISION.stream().anyMatch(p -> p.matcher(handRolled).find()),
                "a hand-rolled membership test is a second decision too: " + handRolled);

        String portTable = "case \"https\" -> 443;";
        assertFalse(SECOND_DECISION.stream().anyMatch(p -> p.matcher(portTable).find()),
                "a default-port table is not a membership decision and must not be flagged");
    }

    @Test
    @DisplayName("a refusal names the rule but never echoes the submitted destination")
    void refusalDoesNotEchoInput() {
        // FR-URL-002's negative clause applies to this refusal too: a rejected request must not echo
        // input in a way that enables injection. The message that reaches a caller is this one.
        String hostile = "javascript:alert(document.cookie)//<script>evil.test</script>";
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> SchemeAllowList.requirePermitted(hostile));

        assertFalse(thrown.getMessage().contains("evil.test"), thrown.getMessage());
        assertFalse(thrown.getMessage().contains("script"), thrown.getMessage());
        assertFalse(thrown.getMessage().contains("document.cookie"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("allow-listed"),
                "the message must still name the rule so the caller can act: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("FR-URL-004"),
                "and cite it, because this refusal is non-waivable: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a permitted destination passes through requirePermitted unchanged")
    void permittedDestinationPasses() {
        // The baseline. A checker that refused everything would satisfy every assertion above.
        for (String ok : List.of("https://example.com", "http://example.com/a?b=c#d",
                "HTTPS://Example.com/", "https://user@example.com/x")) {
            SchemeAllowList.requirePermitted(ok);
        }
    }
}
