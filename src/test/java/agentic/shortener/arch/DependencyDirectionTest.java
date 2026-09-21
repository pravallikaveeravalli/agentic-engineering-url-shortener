package agentic.shortener.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Dependency direction and plane separation. Task T014.
 *
 * <p>ADR-006 (Accepted) chose a single project with an enforced package split rather than separate
 * modules. The owner's stated condition for signing a <em>test-enforced</em> rather than
 * compiler-enforced boundary was that the test itself be proven capable of failing — that proof is
 * T015, and without it this file is a comment with a green tick.
 *
 * <p>This is a <strong>fast-tier</strong> test: ArchUnit reads bytecode, so it needs no container
 * and no Spring context. That matters because a red-green loop requiring Docker gets abandoned
 * (T017).
 *
 * <p>Three rule groups, from three different records:
 * <ul>
 *   <li><strong>NFR-MNT-001 / NFR-MNT-002</strong> — the domain depends on nothing, and the
 *       application plane does not import the control plane.
 *   <li><strong>CR-021</strong> — no executor package references the gate-decision path. The
 *       control against an agent satisfying its own gate is that <em>the code path does not
 *       exist</em>; a field check would only test what a caller says.
 *   <li><strong>CR-032</strong> — no {@code FallbackHandler} type exists. FR-ORC-015 is retired
 *       (Decision K), and this assertion makes the retirement enforced rather than merely recorded,
 *       so a later implementer cannot quietly reintroduce it.
 * </ul>
 */
@DisplayName("T014 dependency direction, plane separation, and two retirement guards")
class DependencyDirectionTest {

    private static final String ROOT = "agentic.shortener";

    /** Production bytecode only. Test fixtures deliberately contain violations (T015). */
    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("domain depends on no framework, persistence, delivery, or control-plane package")
    void domainDependsOnNothing() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        ROOT + ".persistence..",
                        ROOT + ".delivery..",
                        ROOT + ".orchestration..",
                        ROOT + ".policy..",
                        ROOT + ".audit..",
                        ROOT + ".config..")
                .because("NFR-MNT-001: the domain model must be testable and reasonable about "
                        + "without a framework, a store, or the control plane");

        rule.check(productionClasses());
    }

    @Test
    @DisplayName("the application plane does not import the control plane")
    void applicationPlaneDoesNotImportControlPlane() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        ROOT + ".domain..",
                        ROOT + ".application..",
                        ROOT + ".delivery..",
                        ROOT + ".persistence..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".orchestration..")
                .because("NFR-MNT-002 and ADR-006: two planes in one deployable stay separable only "
                        + "if the dependency runs one way. The shortener must be demonstrable with "
                        + "the orchestration engine switched off");

        rule.check(productionClasses());
    }

    @Test
    @DisplayName("CR-021: no executor package references the gate-decision path")
    void noExecutorReferencesTheGateDecisionPath() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".orchestration.executor..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".orchestration.gates..")
                .because("CR-021: the defensible claim is that NO WORKFLOW STEP submits or satisfies "
                        + "a gate decision. The control is that the code path does not exist — "
                        + "removing the caller, not checking a field a caller supplies")
                // Both packages arrive in Phase 5. ArchUnit fails a rule that matches nothing, and
                // this rule is deliberately ARMED EARLY so it bites the moment the executor package
                // lands rather than being added afterwards by someone who remembers to. Allowing the
                // empty match is what lets it be armed before the code exists — the cost is that it
                // is vacuously true until then, which is why T063 also asserts it at the point the
                // gate-decision path is built.
                .allowEmptyShould(true);

        rule.check(productionClasses());
    }

    @Test
    @DisplayName("CR-032: no FallbackHandler type exists — FR-ORC-015 is retired")
    void noFallbackHandlerTypeExists() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + "..")
                .should().haveSimpleNameContaining("FallbackHandler")
                .because("CR-032 / Decision K: FR-ORC-015 is retired and bounded retry then safe "
                        + "suspension is the entire degradation story. Asserting the type's absence "
                        + "makes the retirement enforced rather than merely recorded, so it cannot "
                        + "be quietly undone");

        rule.check(productionClasses());
    }

    @Test
    @DisplayName("no cyclic dependencies between planes")
    void noCyclesBetweenTopLevelPackages() {
        ArchRule rule = slices()
                .matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .because("ADR-006: a cycle between packages means the split is nominal");

        rule.check(productionClasses());
    }
}
