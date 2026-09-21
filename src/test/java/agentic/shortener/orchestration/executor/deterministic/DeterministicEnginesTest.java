package agentic.shortener.orchestration.executor.deterministic;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T071 — the five genuinely deterministic stage engines. FR-ORC-029, ADR-004-A2.
 *
 * <p>Cross-cutting properties here; each engine's own behaviour is in its own test, which is also where the
 * {@code StageExecutorContract} is applied to it.
 *
 * <p><strong>These five are not fallbacks and never were.</strong> ADR-004-A2 removed every deterministic
 * counterpart to an AI-capable stage, so there is no engine here that stands in for AI work. S1, S8, S10, S11
 * and S12 are stages whose value <em>is</em> repeatability: a policy verdict that varied between runs would
 * be an opinion, and a summary that varied would be a claim rather than a record.
 */
@DisplayName("T071 — the five deterministic engines")
class DeterministicEnginesTest {

    static StageInput input(String nodeKey, int stageNumber, Map<String, String> artifacts) {
        return new StageInput(UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                nodeKey, stageNumber, 1, artifacts);
    }

    /**
     * Runs an executor twice on equal-but-distinct inputs and asserts the outcomes are equal.
     *
     * <p>Two separately constructed inputs rather than one reused instance: a stage that cached by identity
     * would pass the reused-instance version of this test while being non-deterministic in every real run,
     * where the input is rebuilt from the store each time.
     */
    static void assertDeterministic(StageExecutor executor, Map<String, String> artifacts,
                                    String nodeKey, int stageNumber) {
        StageOutcome first = executor.execute(input(nodeKey, stageNumber, Map.copyOf(artifacts)));
        StageOutcome second = executor.execute(input(nodeKey, stageNumber, Map.copyOf(artifacts)));

        assertEquals(first, second,
                "identical input must give identical output. Completion of a run is not evidence that an "
                        + "engine is correct — that was the gap the pre-implementation review found in this "
                        + "task's previous validation");
    }

    @Test
    @DisplayName("exactly five engines, for exactly the five deterministic stages")
    void fiveEnginesForFiveStages() {
        assertEquals(List.of(1, 8, 10, 11, 12),
                DeterministicEngines.stageNumbers(),
                "S1 ingestion, S8 testing, S10 security and policy, S11 release readiness, S12 summary. "
                        + "A sixth engine would mean a deterministic counterpart to an AI-capable stage, "
                        + "which ADR-004-A2 removed");
    }

    @Test
    @DisplayName("every engine records DETERMINISTIC, never AI")
    void everyEngineRecordsItsKind() {
        // FR-ORC-029's negative clause: a deterministic execution MUST NOT be presented as AI work. The
        // label is the only thing distinguishing what ran now that there is a single runtime path, so it
        // carries more weight than it did when a run-level flag also existed.
        for (int stage : DeterministicEngines.stageNumbers()) {
            StageOutcome outcome = DeterministicEngines.forStage(stage, TestPorts.working())
                    .execute(input("S" + stage, stage, TestPorts.sampleArtifactsFor(stage)));
            assertTrue(outcome.succeeded(), "S" + stage + " should succeed on good input: " + outcome);
            assertEquals(ExecutorKind.DETERMINISTIC, outcome.executorKind(), "S" + stage);
        }
    }

    @Test
    @DisplayName("no engine reads a clock, a random source, or the environment")
    void enginesHaveNoHiddenInputs() {
        // The structural reason the determinism assertions above are worth anything. Two runs a millisecond
        // apart would agree even if an engine stamped Instant.now() into its output, so a behavioural test
        // alone cannot see this; and a wall clock or a random id is exactly how "identical input, identical
        // output" quietly stops being true in the demonstration run rather than in the test.
        ArchRule rule = noClasses()
                .that().resideInAPackage("agentic.shortener.orchestration.executor.deterministic..")
                .should().callMethod(java.time.Instant.class, "now")
                .orShould().callMethod(java.lang.System.class, "currentTimeMillis")
                .orShould().callMethod(java.lang.System.class, "nanoTime")
                .orShould().callMethod(java.util.UUID.class, "randomUUID")
                .orShould().callMethod(java.lang.System.class, "getenv", String.class)
                .orShould().dependOnClassesThat().haveFullyQualifiedName(java.util.Random.class.getName())
                .because("T071: each engine must produce its declared output deterministically. A hidden "
                        + "clock or random source makes that false in production while every test still "
                        + "passes")
                .allowEmptyShould(true);

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("agentic.shortener.orchestration.executor.deterministic"));
    }

    @Test
    @DisplayName("the hidden-input rule is falsifiable — it catches a class that does read a clock")
    void hiddenInputRuleIsFalsifiable() {
        // The rule above passes when the package is clean and also when it is empty or when ArchUnit is
        // matching nothing. Point the same rule at a package that definitely reads a clock, and it must fail.
        ArchRule rule = noClasses()
                .that().resideInAPackage("agentic.shortener.orchestration.store..")
                .should().callMethod(java.time.Clock.class, "instant")
                .allowEmptyShould(true);

        assertTrue(assertFails(rule), "JdbcRunStore reads its clock, so a rule forbidding that must fail; "
                + "if it passed, the rule form itself would be checking nothing");
    }

    private static boolean assertFails(ArchRule rule) {
        try {
            rule.check(new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("agentic.shortener.orchestration.store"));
            return false;
        } catch (AssertionError expected) {
            return true;
        }
    }
}
