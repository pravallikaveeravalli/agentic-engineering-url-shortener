package agentic.shortener.orchestration.executor;

import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T069 — one executor interface for all twelve stages. FR-ORC-028.
 *
 * <p>Three kinds of assertion, deliberately separated: the contract applied to a real implementation, the
 * <em>shape</em> of the interface and its data, and the negative guard CN-010 asks for.
 */
@DisplayName("T069 — the single stage-executor interface")
class StageExecutorContractTest {

    private static final Path EXECUTOR_SOURCES =
            Path.of("src/main/java/agentic/shortener/orchestration/executor");

    /**
     * A generic engine standing in for any implementation: it copies its inputs forward under a new key.
     *
     * <p>Deliberately a <em>generic</em> engine rather than a canned response — the contract is being
     * applied to something that behaves like the real thing, and a stub returning a constant would pass
     * the contract while demonstrating nothing about it.
     */
    private static final class PassThroughEngine implements StageExecutor {

        @Override
        public StageOutcome execute(StageInput input) {
            if (input.inputArtifacts().isEmpty()) {
                return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                        "no input artifacts to work from", false));
            }
            List<ProducedArtifact> produced = input.inputArtifacts().entrySet().stream()
                    .map(e -> new ProducedArtifact(
                            input.nodeKey() + "/" + e.getKey(), e.getValue(), List.of(e.getKey())))
                    .toList();
            return StageOutcome.succeeded(produced, ExecutorKind.DETERMINISTIC);
        }
    }

    @Nested
    @DisplayName("the contract, applied to a generic engine")
    class AppliedToAGenericEngine extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new PassThroughEngine();
        }

        @Override
        protected StageInput validInput() {
            return new StageInput(UUID.randomUUID(), "S2", 2, 1,
                    Map.of("brief", "shorten some urls"));
        }
    }

    // ==============================================================================================
    // the shape of the interface
    // ==============================================================================================

    @Test
    @DisplayName("ONE interface: StageExecutor declares exactly one abstract method")
    void oneInterfaceForEveryStage() {
        List<Method> abstractMethods = Arrays.stream(StageExecutor.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isAbstract(m.getModifiers()))
                .toList();

        assertEquals(1, abstractMethods.size(),
                "FR-ORC-028 is one interface for all twelve stages. A second abstract method is how an "
                        + "interface becomes twelve interfaces wearing one name: " + abstractMethods);
        assertEquals("execute", abstractMethods.get(0).getName());
    }

    @Test
    @DisplayName("the input is workflow DATA — a closed component set with nothing naming a scenario")
    void inputIsWorkflowDataOnly() {
        // FR-ORC-028: an executor's behaviour must be a function of workflow data, not of which
        // requirement is being processed. Asserted as an EXACT set rather than a "does not contain"
        // check, because the field that would break this has not been thought of yet — a closed set
        // fails on any addition and makes the author justify it, which is the point.
        List<String> components = Arrays.stream(StageInput.class.getRecordComponents())
                .map(c -> c.getName()).toList();

        assertEquals(List.of("runId", "nodeKey", "stageNumber", "attempt", "inputArtifacts"), components,
                "a new component here is a new thing an executor can branch on. Adding one is allowed; "
                        + "adding one without revisiting FR-ORC-028 is not");
    }

    @Test
    @DisplayName("an outcome cannot be constructed in a state the orchestrator would have to reject")
    void outcomeStatesAreStructurallyConstrained() {
        // EC-029, made inexpressible rather than detectable.
        assertThrows(IllegalArgumentException.class,
                () -> StageOutcome.succeeded(List.of(), ExecutorKind.DETERMINISTIC),
                "success with no artifact is EC-029's failure; the constructor is where it should die");

        assertThrows(IllegalArgumentException.class,
                () -> StageOutcome.succeeded(
                        List.of(new ProducedArtifact("k", "v", List.of())), ExecutorKind.HUMAN),
                "FR-ORC-031: HUMAN is never selectable by an executor");

        assertThrows(NullPointerException.class,
                () -> StageOutcome.failed(null),
                "a failure with no envelope is a failure the orchestrator cannot rule on");
    }

    @Test
    @DisplayName("a failed outcome exposes no artifacts, and a succeeded one no failure")
    void thetwoDispositionsDoNotOverlap() {
        StageOutcome failed = StageOutcome.failed(
                new FailureEnvelope(FailureCategory.TIMEOUT, "took too long", true));
        assertTrue(failed.producedArtifacts().isEmpty(),
                "artifacts from a failed stage would be work the run must not build on");

        StageOutcome succeeded = StageOutcome.succeeded(
                List.of(new ProducedArtifact("spec", "text", List.of("brief"))),
                ExecutorKind.DETERMINISTIC);
        assertEquals(null, succeeded.failure(), "a succeeded outcome has nothing to classify");
    }

    // ==============================================================================================
    // CN-010 — no executor recognises a demonstration input
    // ==============================================================================================

    /**
     * Phrases from the three demonstration scenarios, and the scenario identifiers themselves.
     *
     * <p>Checked against <strong>string literals only</strong>, not against comments: a javadoc explaining
     * that a stage serves DS-B is documentation, while a literal {@code "DS-B"} inside executor logic is a
     * branch on which demonstration is running. Exempting comments is what makes the check precise enough
     * to keep rather than water down later.
     */
    private static final List<String> DEMONSTRATION_MARKERS = List.of(
            "DS-A", "DS-B", "DS-C",
            "expire after a week", "retain analytics indefinitely", "trusted partners",
            "per-creator aggregate", "noisy neighbor", "noisy-neighbor");

    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    @DisplayName("CN-010: no executor's string literals name a demonstration scenario or input")
    void noExecutorRecognisesADemonstrationInput() throws Exception {
        List<String> findings = new ArrayList<>();

        for (Path source : executorSources()) {
            String body = Files.readString(source);
            Matcher literals = STRING_LITERAL.matcher(stripComments(body));
            while (literals.find()) {
                String literal = literals.group(1);
                for (String marker : DEMONSTRATION_MARKERS) {
                    if (literal.toLowerCase().contains(marker.toLowerCase())) {
                        findings.add(source + " contains the literal \"" + literal + "\"");
                    }
                }
            }
        }

        assertEquals(List.of(), findings,
                "FR-ORC-028 and CN-010: an executor that recognises a demonstration input is a rigged "
                        + "demonstration. The engine does not know the build; the pipeline definition "
                        + "does. Findings: " + findings);
    }

    @Test
    @DisplayName("the CN-010 check is falsifiable — it catches a planted marker")
    void demonstrationMarkerCheckIsFalsifiable() {
        // Without this, an empty findings list could mean "nothing to find" or "not looking". The probe
        // is run through the same extraction the real check uses, over text rather than a planted file:
        // writing a violation into src/main to prove a test works would leave one behind on any failure.
        String planted = """
                class Rigged {
                    // A comment mentioning DS-B must NOT be a finding.
                    String scenario = "DS-B";
                }
                """;
        List<String> found = new ArrayList<>();
        Matcher literals = STRING_LITERAL.matcher(stripComments(planted));
        while (literals.find()) {
            for (String marker : DEMONSTRATION_MARKERS) {
                if (literals.group(1).toLowerCase().contains(marker.toLowerCase())) {
                    found.add(literals.group(1));
                }
            }
        }
        assertEquals(List.of("DS-B"), found,
                "the extraction must catch the literal and ignore the comment; if it caught the comment "
                        + "too, the real check would be unusable and would get relaxed");
    }

    private static List<Path> executorSources() throws Exception {
        if (!Files.isDirectory(EXECUTOR_SOURCES)) {
            // Armed before the package exists, on purpose: the check bites the moment an engine lands
            // rather than being added afterwards by whoever remembers to.
            return List.of();
        }
        try (Stream<Path> tree = Files.walk(EXECUTOR_SOURCES)) {
            return tree.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** Block and line comments removed, so documentation may say what code may not do. */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
