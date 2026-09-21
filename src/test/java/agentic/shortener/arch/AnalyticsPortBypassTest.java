package agentic.shortener.arch;

import agentic.shortener.domain.analytics.RedirectEventRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No append reaches the store except through the port. Task T049. FR-URL-010. ADR-014 Condition 2.
 *
 * <p><strong>This test is what makes T048's port real.</strong> Condition 2 says the port must not be
 * bypassed anywhere; without a rule that fires, "must not" is a wish. And ADR-014's evolution ladder —
 * Postgres table now, a queue or stream later — is reachable only if the seam holds, so this is the
 * mechanism that keeps the later rungs available rather than theoretical.
 *
 * <p><strong>Shown to fail on a deliberate bypass</strong>, which is T049's Artifact in so many words. A
 * rule that has never failed is an assertion that happens to be green, and it would stay green if
 * somebody inverted its predicate. The fixture in {@code arch.fixture} bypasses the port on purpose and
 * this test asserts the rule catches it.
 *
 * <p>Two rules, because there are two ways to bypass a port. One is to <em>depend on</em> the store
 * interface from somewhere that should not; the other is to call {@code append} from somewhere that
 * legitimately holds the interface for reading. ArchUnit catches the first; a source-level check catches
 * the second.
 *
 * <p>Fast tier: bytecode and source text. No container, no Spring context.
 */
@DisplayName("T049 no analytics append bypasses the port")
class AnalyticsPortBypassTest {

    private static final String ROOT = "agentic.shortener";
    private static final String FIXTURE = ROOT + ".arch.fixture";

    /**
     * The only production types permitted to know {@link RedirectEventRepository} exists.
     *
     * <p>Narrow on purpose. The recorder is the port's implementation; the Jdbc class <em>is</em> the
     * repository; the configuration wires them; {@code GetAnalyticsUseCase} and {@code LinkService} hold
     * it for <strong>reading</strong> only, which the source-level rule below then pins down.
     */
    private static final List<String> PERMITTED = List.of(
            "RedirectEventRepository",
            "JdbcRedirectEventRepository",
            "TransactionalAnalyticsRecorder",
            "PersistenceConfiguration",
            "GetAnalyticsUseCase",
            "LinkService");

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    private static ArchRule onlyPermittedTypesKnowTheStore(String packageScope) {
        // Built by chaining doNotHaveSimpleName, because this ArchUnit version has no
        // haveSimpleNameNotIn. Chaining is equivalent and keeps the allow-list in one place.
        GivenClassesConjunction scoped = noClasses().that().resideInAPackage(packageScope);
        for (String permitted : PERMITTED) {
            scoped = scoped.and().doNotHaveSimpleName(permitted);
        }
        return scoped
                .should().dependOnClassesThat().areAssignableTo(RedirectEventRepository.class)
                .because("FR-URL-010 and ADR-014 Condition 2: every append passes through "
                        + "AnalyticsRecordingPort, so nothing else may reach the event store");
    }

    @Test
    @DisplayName("production: nothing outside the permitted set depends on the event store")
    void noProductionBypass() {
        onlyPermittedTypesKnowTheStore(ROOT + "..").check(productionClasses());
    }

    @Test
    @DisplayName("the resolution and delivery paths reach analytics ONLY through the port")
    void resolutionPathUsesThePortOnly() {
        // The specific bypass Condition 2 is worried about: a redirect handler appending directly
        // because it is right there and one call is cheaper than an interface.
        noClasses()
                .that().resideInAnyPackage(ROOT + ".delivery..", ROOT + ".orchestration..")
                .should().dependOnClassesThat().areAssignableTo(RedirectEventRepository.class)
                .because("the redirect path must go through the port, or ADR-014's later rungs stop "
                        + "being reachable")
                .check(productionClasses());
    }

    @Test
    @DisplayName("FALSIFIABLE: the rule reports a violation on the deliberate bypass fixture")
    void ruleFailsOnTheBypassFixture() {
        // Aimed at the fixture instead of production. If this does not throw, the green result above
        // carries no information at all.
        JavaClasses fixtureClasses = new ClassFileImporter().importPackages(FIXTURE);

        AssertionError failure = assertThrows(AssertionError.class,
                () -> onlyPermittedTypesKnowTheStore(FIXTURE + "..").check(fixtureClasses),
                "the rule did NOT fail on a class that bypasses the port on purpose — which would "
                        + "mean T048's port is advisory");

        assertTrue(failure.getMessage().contains("Architecture Violation"),
                "the failure must be an architecture violation; got: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("AnalyticsPortBypassFixture"),
                "the failure must name the offending class, or a developer cannot act on it; got: "
                        + failure.getMessage());
    }

    @Test
    @DisplayName("only the recorder CALLS append — the read-side holders never do")
    void onlyTheRecorderAppends() throws Exception {
        // The second bypass shape. GetAnalyticsUseCase and LinkService legitimately hold the repository
        // for reading, so an ArchUnit dependency rule cannot tell an append from a count. This reads the
        // source instead and names the one file allowed to append.
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.endsWith("TransactionalAnalyticsRecorder.java")
                        || file.endsWith("JdbcRedirectEventRepository.java")
                        || file.endsWith("RedirectEventRepository.java")) {
                    continue;
                }
                String active = Files.readString(file).lines()
                        .map(String::stripLeading)
                        .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                        .reduce("", (a, b) -> a + "\n" + b);
                if (appendsAnEvent(active)) {
                    offenders.add(file.toString());
                }
            }
        }
        assertEquals(List.of(), offenders,
                "these files append to the event store directly: " + offenders
                        + ". Every append goes through AnalyticsRecordingPort (ADR-014 Condition 2)");
    }

    /**
     * Whether this source appends a <em>redirect event</em>.
     *
     * <p>The first version of this check looked for {@code .append(} alone, and on its first run it
     * flagged {@code ShortCodeGenerator} and {@code DestinationNormalizer} — both of which append to a
     * {@link StringBuilder}. Exempting those two files by name would have hollowed the check out;
     * requiring the file to name a redirect event as well is the property it was always meant to express.
     * Every append to the event store has to mention the type it is appending.
     */
    private static boolean appendsAnEvent(String activeSource) {
        return activeSource.contains(".append(") && activeSource.contains("RedirectEvent");
    }

    @Test
    @DisplayName("FALSIFIABLE: the source-level append check can fail too")
    void appendCheckIsFalsifiable() {
        // Same argument, applied to the check that is not ArchUnit's. Proved against planted text rather
        // than a planted file, so the proof leaves nothing behind to forget about.
        assertTrue(appendsAnEvent("events.append(RedirectEvent.unsaved(code, now));"),
                "the check must catch a direct append to the event store");
        assertTrue(appendsAnEvent("RedirectEvent e = build(); store.append(e);"),
                "including one that appends a variable rather than an inline construction");

        assertFalse(appendsAnEvent("code.append(ALPHABET.charAt(i));"),
                "a StringBuilder append is not an event append — this is the false positive that the "
                        + "first version of this check produced");
        assertFalse(appendsAnEvent("events.countByShortCode(code);"),
                "and a legitimate read must not match either");
    }
}
