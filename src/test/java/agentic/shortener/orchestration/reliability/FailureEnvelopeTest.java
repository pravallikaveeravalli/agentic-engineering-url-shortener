package agentic.shortener.orchestration.reliability;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeoutException;

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

    /**
     * The rule, minus its exemption, so both the rule and its falsifiability check use one definition.
     *
     * <p><strong>What "vendor taxonomy" actually means, arrived at the hard way.</strong> The first version of
     * this rule forbade every {@code reliability} class from touching {@code java.sql}, {@code java.io},
     * {@code java.net} or {@code java.util.concurrent} — and {@code CompensationRegister} broke it fifteen
     * times over by doing ordinary JDBC: opening a connection, inserting a row, and reading SQLSTATE 23505 to
     * recognise the unique violation EC-022 relies on. None of that is a taxonomy. It is the same
     * constraint-as-primitive pattern the short-code generator and {@code ArtifactWriteGuard} use, where the
     * database is the authority and the application reacts to its answer.
     *
     * <p>So the rule is scoped to the property T083 is actually about: <em>assigning a
     * {@link FailureCategory} from a provider's error</em>. A class that holds the classification vocabulary
     * <strong>and</strong> a provider exception type is a taxonomy; one that holds either alone is not. That
     * admits {@code CompensationRegister} (provider exceptions, no categories) and {@code RetryPolicy}
     * (categories, no provider exceptions), and still catches the leak the guard was written for.
     */
    /** The provider error types. Each exists to be classified and has no other use in this package. */
    private static final List<String> PROVIDER_ERROR_TYPES = List.of(
            "java.sql.SQLException", "java.io.IOException", "java.util.concurrent.TimeoutException",
            "java.net.SocketTimeoutException", "java.net.ConnectException");

    /** Classes holding BOTH the classification vocabulary and a provider error type — a taxonomy. */
    private static List<String> classesHoldingATaxonomy() {
        return reliabilityClasses().stream()
                .filter(c -> c.getEnclosingClass().isEmpty())
                .filter(c -> dependsOn(c, FailureCategory.class.getName()))
                .filter(c -> PROVIDER_ERROR_TYPES.stream().anyMatch(type -> dependsOn(c, type)))
                .map(JavaClass::getFullName)
                .sorted()
                .toList();
    }

    private static boolean dependsOn(JavaClass candidate, String targetFullName) {
        return candidate.getDirectDependenciesFromSelf().stream()
                .anyMatch(d -> d.getTargetClass().getFullName().equals(targetFullName));
    }

    @Test
    @DisplayName("the CORE learns no vendor taxonomy — only the translator maps provider errors to categories")
    void coreLearnsNoVendorTaxonomy() {
        // A DEPENDENCY check rather than a text scan. An earlier version read the source and flagged
        // FailureCategory's javadoc for the word "IOException" — prose explaining why the taxonomy lives
        // elsewhere, which is documentation doing its job. The property was never about text.
        assertEquals(List.of(ProviderFailureTranslator.class.getName()), classesHoldingATaxonomy(),
                "exactly one class may hold both. T083: provider knowledge stays in the plugin, and a "
                        + "vendor taxonomy in the core leaks upward one exception name at a time");
    }

    @Test
    @DisplayName("the taxonomy check is falsifiable — the translator really is the one class that holds one")
    void taxonomyCheckIsFalsifiable() {
        // An exact-match assertion is falsifiable in both directions at once, which a "should be empty" one is
        // not: an empty result would mean either "nothing leaks" or "nothing classifies anything", and only
        // the first is the property. Requiring the translator to be present rules the second out.
        assertTrue(classesHoldingATaxonomy().contains(ProviderFailureTranslator.class.getName()),
                "if the exempted class held no taxonomy, the check above would pass for the wrong reason");
        assertFalse(classesHoldingATaxonomy().isEmpty());
    }

    private static JavaClasses reliabilityClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("agentic.shortener.orchestration.reliability");
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
