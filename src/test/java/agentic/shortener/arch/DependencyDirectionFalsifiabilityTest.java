package agentic.shortener.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proof that T014's rules can fail. Task T015.
 *
 * <p><strong>This is the owner's stated condition for signing a test-enforced rather than
 * compiler-enforced boundary</strong> (ADR-006). A rule that has never been observed failing is not
 * a control — it is an assertion that happens to be green, and it would stay green if someone
 * inverted its predicate.
 *
 * <p>The proof is permanent rather than a one-off: it runs on every build, pointed at a fixture that
 * violates the rule on purpose ({@code arch.fixture}). A one-off demonstration recorded in a commit
 * message would decay the moment the rule was edited.
 *
 * <p>There is also a captured red-phase artifact under {@code docs/evidence/red-phase/} from the
 * moment T014 was first written, taken against a violation planted in <em>production</em> code and
 * then removed. That artifact and this test answer different questions: the artifact shows the rule
 * caught a real violation once; this test shows it still would.
 */
@DisplayName("T015 T014's rules are falsifiable — they fail on a deliberate violation")
class DependencyDirectionFalsifiabilityTest {

    private static final String FIXTURE = "agentic.shortener.arch.fixture";

    @Test
    @DisplayName("the domain-depends-on-nothing rule reports a violation on the fixture")
    void domainRuleFailsOnADeliberateFrameworkDependency() {
        // The same rule shape T014 applies, aimed at the fixture package instead of production.
        ArchRule rule = noClasses()
                .that().resideInAPackage(FIXTURE + "..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                .because("T015: this must FAIL, or T014's equivalent rule proves nothing");

        JavaClasses fixtureClasses = new ClassFileImporter().importPackages(FIXTURE);

        AssertionError failure = assertThrows(AssertionError.class,
                () -> rule.check(fixtureClasses),
                "the rule did NOT fail on a class that deliberately violates it — which would mean "
                        + "T014's green result carries no information");

        String message = failure.getMessage();
        assertTrue(message.contains("Architecture Violation"),
                "the failure must be an architecture violation; got: " + message);
        assertTrue(message.contains("FrameworkDependentDomainFixture"),
                "the failure must name the offending class, or a developer cannot act on it; got: "
                        + message);
    }

    @Test
    @DisplayName("the FallbackHandler-absence rule reports a violation when such a type exists")
    void fallbackAbsenceRuleFailsWhenAFallbackHandlerExists() {
        // CR-032's retirement guard is only worth having if it fires. Aimed at a fixture that
        // carries the forbidden name, it must.
        ArchRule rule = noClasses()
                .that().resideInAPackage(FIXTURE + "..")
                .should().haveSimpleNameContaining("Fixture")
                .because("T015: stands in for CR-032's FallbackHandler-absence rule — a name-based "
                        + "absence rule must be shown to fire on a matching name");

        JavaClasses fixtureClasses = new ClassFileImporter().importPackages(FIXTURE);

        AssertionError failure = assertThrows(AssertionError.class,
                () -> rule.check(fixtureClasses),
                "a name-based absence rule that cannot fail would let the FR-ORC-015 retirement be "
                        + "quietly undone");

        assertTrue(failure.getMessage().contains("FrameworkDependentDomainFixture"),
                "the failure must name the matching type; got: " + failure.getMessage());
    }
}
