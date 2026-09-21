package agentic.shortener.orchestration.reliability;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T083 — the standard failure envelope. FR-ORC-014 (CL-006).
 *
 * <p>The envelope is the only thing the orchestrator reads about a failure, so two things have to hold at
 * once: the category set is <strong>closed</strong>, and translating a provider's own error shape into it
 * happens <strong>in the plugin</strong>. T083's guard is that the core never learns a vendor taxonomy —
 * which is why the translation tests below live with the translator and assert on standard categories, and
 * why nothing in the core names an exception type.
 */
@DisplayName("T083 — the standard failure envelope and its category set")
class FailureEnvelopeTest {

    @Test
    @DisplayName("six categories, exactly — the closed set a retryable set is expressed over")
    void sixCategoriesExactly() {
        assertEquals(
                List.of("TIMEOUT", "UNAVAILABLE", "RATE_LIMITED", "INVALID_INPUT", "INTERNAL", "UNKNOWN"),
                EnumSet.allOf(FailureCategory.class).stream().map(Enum::name).toList(),
                "a stage's declared retryable set is expressed over standard categories ONLY. A seventh "
                        + "category is a category some stage's declared set does not mention, and an "
                        + "unmentioned category rules permanent — so adding one silently narrows retry");

        assertEquals(EnumSet.allOf(FailureCategory.class), FailureCategory.declared(),
                "declared() is what other code checks membership against; it must not drift from the enum");
    }

    @Test
    @DisplayName("UNKNOWN is the default-deny category, and it is never retryable")
    void unknownIsNeverRetryable() {
        // EC-031/EC-032 in their envelope form. The ruling is T084's, but the envelope must not be able to
        // carry a proposal that contradicts it: an executor proposing "retry this, I don't know what it
        // is" is exactly the unbounded-retry path FR-ORC-014 forbids.
        assertThrows(IllegalArgumentException.class,
                () -> new FailureEnvelope(FailureCategory.UNKNOWN, "something went wrong", true),
                "an unrecognized failure proposed as retryable is how default-deny becomes default-allow");

        FailureEnvelope unknown = new FailureEnvelope(FailureCategory.UNKNOWN, "something", false);
        assertFalse(unknown.executorProposesRetryable());
    }

    @Test
    @DisplayName("the detail is required and must say something")
    void detailIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new FailureEnvelope(FailureCategory.TIMEOUT, "  ", true),
                "a blank detail leaves a failure_event row nobody can act on");
        assertThrows(NullPointerException.class,
                () -> new FailureEnvelope(null, "detail", false));
    }

    @Test
    @DisplayName("the envelope carries the executor's PROPOSAL, not a decision")
    void envelopeCarriesAProposalNotADecision() {
        FailureEnvelope envelope = new FailureEnvelope(FailureCategory.TIMEOUT, "provider timed out", true);

        // Named executorProposesRetryable rather than retryable: the field is one of two votes, and a
        // name like isRetryable() would invite a caller to treat the executor's diagnosis as the ruling.
        // T084 holds the other vote and the decision.
        assertTrue(envelope.executorProposesRetryable());
        List<String> accessors = java.util.Arrays.stream(FailureEnvelope.class.getRecordComponents())
                .map(c -> c.getName()).toList();
        assertEquals(List.of("category", "detail", "executorProposesRetryable"), accessors,
                "the envelope must not grow a field that looks like a verdict");
    }

    // ==============================================================================================
    // translation — in the plugin, over at least two provider error shapes
    // ==============================================================================================

    @Test
    @DisplayName("a subprocess provider's error shapes translate to standard categories")
    void subprocessProviderErrorsTranslate() {
        ProviderFailureTranslator translator = ProviderFailureTranslator.forSubprocessProvider();

        assertEquals(FailureCategory.TIMEOUT,
                translator.translate(new TimeoutException("waited 120s")).category());
        assertEquals(FailureCategory.UNAVAILABLE,
                translator.translate(new IOException("Cannot run program \"claude\"")).category());
        assertEquals(FailureCategory.INTERNAL,
                translator.translate(new MalformedProviderOutputException("output was not JSON")).category(),
                "non-JSON output is INTERNAL and permanent — T073 assertion 4");
        assertEquals(FailureCategory.UNKNOWN,
                translator.translate(new Throwable("no idea")).category(),
                "an error the plugin does not recognise must arrive as UNKNOWN, which rules permanent — "
                        + "never guessed into a retryable category");
        assertEquals(FailureCategory.UNKNOWN,
                translator.translate(new IllegalStateException("something broke")).category(),
                "IllegalStateException is OUR generic type, not a provider's. Mapping it to INTERNAL is "
                        + "what made a wrapped socket timeout permanent — see "
                        + "MalformedProviderOutputException");
    }

    @Test
    @DisplayName("a store provider's error shapes translate to standard categories")
    void storeProviderErrorsTranslate() {
        ProviderFailureTranslator translator = ProviderFailureTranslator.forStoreProvider();

        assertEquals(FailureCategory.UNAVAILABLE,
                translator.translate(new SQLException("connection refused", "08001")).category());
        assertEquals(FailureCategory.TIMEOUT,
                translator.translate(new SQLException("canceling statement", "57014")).category());
        assertEquals(FailureCategory.INVALID_INPUT,
                translator.translate(new SQLException("violates check constraint", "23514")).category());
        assertEquals(FailureCategory.UNKNOWN,
                translator.translate(new SQLException("who knows", "XX000")).category());
    }

    @Test
    @DisplayName("a translated UNKNOWN never proposes retry")
    void translatedUnknownNeverProposesRetry() {
        assertFalse(ProviderFailureTranslator.forSubprocessProvider()
                .translate(new Throwable("mystery")).executorProposesRetryable());
        assertFalse(ProviderFailureTranslator.forStoreProvider()
                .translate(new SQLException("mystery", "XX000")).executorProposesRetryable());
    }

    /** Packages a provider's own error types come from. Not ours; the boundary is the point. */
    private static final List<String> PROVIDER_TYPE_PACKAGES =
            List.of("java.sql..", "java.net..", "java.io..", "java.util.concurrent..");

    @Test
    @DisplayName("the CORE learns no vendor taxonomy — only the translator DEPENDS ON provider types")
    void coreLearnsNoVendorTaxonomy() {
        // T083's guard, asserted rather than intended. The reason the repaired static-list option was
        // rejected is that provider knowledge leaks upward one exception name at a time; naming the single
        // class allowed to hold it is what stops the first leak rather than the tenth.
        //
        // Expressed as a DEPENDENCY rule rather than a text scan. The first version read the source and
        // flagged FailureCategory's javadoc for the word "IOException" — prose explaining why the taxonomy
        // lives elsewhere, which is documentation doing its job. The property was never about the text; it
        // is about which classes reference which types, and ArchUnit says exactly that.
        ArchRule rule = noClasses()
                .that().resideInAPackage("agentic.shortener.orchestration.reliability..")
                .and().doNotHaveFullyQualifiedName(ProviderFailureTranslator.class.getName())
                .should().dependOnClassesThat()
                .resideInAnyPackage(PROVIDER_TYPE_PACKAGES.toArray(String[]::new))
                .because("T083: provider knowledge stays in the plugin. A vendor taxonomy in the core "
                        + "leaks upward one exception name at a time, and every step looks harmless")
                .allowEmptyShould(true);

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("agentic.shortener.orchestration.reliability"));
    }

    @Test
    @DisplayName("the taxonomy check is falsifiable — the exempted class really does hold the taxonomy")
    void taxonomyCheckIsFalsifiable() {
        // The exemption above is one class name, so the check's value depends on that class ACTUALLY
        // depending on provider types. If it did not, the exemption would be hiding nothing and
        // coreLearnsNoVendorTaxonomy would pass for the wrong reason — every class clean because none of
        // them classifies anything.
        JavaClasses translatorOnly = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("agentic.shortener.orchestration.reliability");

        List<String> providerPackagesDependedOn = translatorOnly.stream()
                .filter(c -> c.getFullName().startsWith(ProviderFailureTranslator.class.getName()))
                .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
                .map(d -> d.getTargetClass().getPackageName())
                .filter(p -> p.startsWith("java.sql") || p.startsWith("java.io")
                        || p.startsWith("java.util.concurrent") || p.startsWith("java.net"))
                .distinct().sorted().toList();

        assertTrue(providerPackagesDependedOn.containsAll(
                        List.of("java.io", "java.sql", "java.util.concurrent")),
                "the one exempted class must be the one that really holds the vendor taxonomy; it "
                        + "depends on: " + providerPackagesDependedOn);
    }

    @Test
    @DisplayName("a nested provider cause is translated, not swallowed")
    void nestedCauseIsTranslated() {
        // Real provider failures arrive wrapped — Hikari wraps SQLException, a subprocess helper wraps
        // IOException. Translating only the outer type would classify nearly everything UNKNOWN, which
        // would be default-deny working correctly and retry never happening at all.
        FailureEnvelope wrapped = ProviderFailureTranslator.forStoreProvider().translate(
                new RuntimeException("failed to read run",
                        new SQLException("connection refused", "08001")));
        assertEquals(FailureCategory.UNAVAILABLE, wrapped.category());

        FailureEnvelope deeplyWrapped = ProviderFailureTranslator.forSubprocessProvider().translate(
                new IllegalStateException("stage failed",
                        new RuntimeException("io", new SocketTimeoutException("read timed out"))));
        assertEquals(FailureCategory.TIMEOUT, deeplyWrapped.category());
    }

    @Test
    @DisplayName("a self-referential cause chain terminates")
    void selfReferentialCauseChainTerminates() {
        // Contrived, and cheap to make impossible. An exception whose cause is itself is legal Java, and a
        // while (cause != null) walk over one does not return — a hang rather than a failure, in the code
        // path that runs when something has already gone wrong.
        RuntimeException loop = new RuntimeException("loop");
        loop.initCause(new ConnectException("refused") {
            @Override
            public synchronized Throwable getCause() {
                return loop;
            }
        });
        FailureEnvelope envelope = ProviderFailureTranslator.forStoreProvider().translate(loop);
        assertEquals(FailureCategory.UNKNOWN, envelope.category(),
                "an unrecognised outer type over a loop still has to return something");
    }
}
