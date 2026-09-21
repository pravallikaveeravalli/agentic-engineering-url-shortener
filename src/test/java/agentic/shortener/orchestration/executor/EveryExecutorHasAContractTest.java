package agentic.shortener.orchestration.executor;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every production executor is held to {@link StageExecutorContract}. Task T069. FR-ORC-028.
 *
 * <p>The contract is only worth what it is applied to. An abstract test class that a new engine simply
 * does not extend is a rule nobody broke and nobody followed, so this closes that route: a production
 * {@link StageExecutor} with no contract test is a failure here.
 *
 * <p><strong>Deliberately armed before the executors exist.</strong> Both sets are empty today and the
 * assertion passes vacuously. That is the same choice T014 made for the
 * {@code noExecutorReferencesTheGateDecisionPath} rule and for the same reason — a check added after the
 * code it governs is a check somebody had to remember, and this one bites the moment T071's engines land.
 */
@DisplayName("T069 — no executor escapes the contract")
class EveryExecutorHasAContractTest {

    private static final String ROOT = "agentic.shortener";

    @Test
    @DisplayName("every production StageExecutor implementation has a contract test")
    void everyExecutorHasAContractTest() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
        JavaClasses tests = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(ROOT);

        List<String> executors = production.stream()
                .filter(c -> c.isAssignableTo(StageExecutor.class))
                .filter(c -> !c.isInterface() && !c.getModifiers().contains(
                        com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT))
                .map(JavaClass::getSimpleName)
                .sorted()
                .toList();

        List<String> contractTests = tests.stream()
                .filter(c -> c.isAssignableTo(StageExecutorContract.class))
                .map(JavaClass::getSimpleName)
                .toList();

        // The convention is that the contract test's name names what it tests. A nested class inside
        // FooEngineTest is enough, which is how StageExecutorContractTest applies the contract.
        List<String> uncovered = executors.stream()
                .filter(executor -> contractTests.stream().noneMatch(
                        test -> test.contains(executor) || nestedOwnerContains(tests, test, executor)))
                .toList();

        assertEquals(List.of(), uncovered,
                "these executors are not held to StageExecutorContract, so nothing asserts they return "
                        + "failures instead of throwing them, or that success carries an artifact: "
                        + uncovered);
    }

    /** A nested contract test is named after its enclosing class, so look there too. */
    private static boolean nestedOwnerContains(JavaClasses tests, String testSimpleName, String executor) {
        return tests.stream()
                .filter(c -> c.getSimpleName().equals(testSimpleName))
                .anyMatch(c -> c.getEnclosingClass()
                        .map(owner -> owner.getSimpleName().contains(executor))
                        .orElse(false));
    }
}
