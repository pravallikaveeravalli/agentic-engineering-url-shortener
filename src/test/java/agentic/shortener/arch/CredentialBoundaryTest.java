package agentic.shortener.arch;

import agentic.shortener.arch.fixture.CreatorCredentialLeakFixture;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T063a — the credential boundary. CN-012, FR-URL-018, FR-ORC-008, NFR-AUT-001. ADR-006, ADR-013.
 *
 * <p><strong>The two identity models must be provably separate, and this is the proof.</strong> The
 * owner's ruling is that creators are a URL-shortener concept that must not leak into the orchestrator
 * (CN-012) — the run submitter, the gate decider and the review are all unauthenticated by design on the
 * control plane, and a creator credential arriving there would be either meaningless or a boundary breach
 * depending on what it was used for. Neither should be possible to write.
 *
 * <p><strong>What this test cannot prove</strong>, stated so the green result is not over-read: that the
 * governance surfaces are unreachable. They are unauthenticated by design, and that asymmetry is disclosed
 * in T147, not papered over here.
 */
@DisplayName("T063a — orchestration, policy and audit never reference a creator credential")
class CredentialBoundaryTest {

    private static final String ROOT = "agentic.shortener";

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    private static ArchRule credentialBoundaryRule() {
        return noClasses()
                .that().resideInAnyPackage(ROOT + ".orchestration..", ROOT + ".policy..", ROOT + ".audit..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "agentic.shortener.domain.creator.CreatorCredential")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "agentic.shortener.delivery.auth.CreatorAuthFilter")
                .because("CN-012: creators are a URL-shortener concept with no standing in the "
                        + "orchestrator's actor model. Control-plane operations carry no credential");
    }

    @Test
    @DisplayName("the rule passes clean against production code")
    void ruleIsCleanAgainstProduction() {
        credentialBoundaryRule().check(productionClasses());
    }

    @Test
    @DisplayName("no orchestration/policy/audit class reads the Authorization header literal")
    void noAuthorizationHeaderLiteralOutsideDelivery() throws Exception {
        // ArchUnit's dependency check catches TYPE references (a field, a parameter, an import); it
        // cannot catch a bare string literal "Authorization" used to read a header without ever naming
        // CreatorAuthFilter's type. A source scan closes that gap the same way T069's demonstration-input
        // check does — over string literals, comments stripped, so documentation may name the header
        // while code may not read it.
        java.util.List<String> offenders = new java.util.ArrayList<>();
        for (String pkg : java.util.List.of("orchestration", "policy", "audit")) {
            java.nio.file.Path root = java.nio.file.Path.of("src/main/java/agentic/shortener/" + pkg);
            if (!java.nio.file.Files.isDirectory(root)) {
                continue;
            }
            try (var walk = java.nio.file.Files.walk(root)) {
                for (var file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String body = java.nio.file.Files.readString(file);
                    String stripped = body.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
                    if (stripped.contains("\"Authorization\"")) {
                        offenders.add(file.toString());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "no control-plane package may read the Authorization header, even without naming "
                        + "CreatorAuthFilter's type: " + offenders);
    }

    @Test
    @DisplayName("no control-plane controller declares a security requirement")
    void noControlPlaneControllerDeclaresASecurityRequirement() throws Exception {
        // "Declares a security requirement" is read structurally: a Spring @PreAuthorize/@Secured
        // annotation, or a dependency on the auth filter/credential types (already covered above). None
        // of the orchestration.api controllers (CR-038) exist yet, so this is armed early, the same
        // pattern T014's gate-decision-path rule uses — it bites the moment they land.
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".orchestration.api..")
                .should().beAnnotatedWith("org.springframework.security.access.prepost.PreAuthorize")
                .orShould().beAnnotatedWith("org.springframework.security.access.annotation.Secured")
                .because("CN-012: control-plane surfaces carry no credential and declare no security "
                        + "requirement; they are unauthenticated by design")
                .allowEmptyShould(true);

        rule.check(productionClasses());
    }

    // ==============================================================================================
    // falsifiability, in the shape of T015
    // ==============================================================================================

    @Test
    @DisplayName("the boundary rule FAILS when a creator-credential reference is deliberately injected")
    void ruleFailsOnADeliberateCredentialReference() {
        // The SAME rule shape credentialBoundaryRule() applies, aimed at the fixture package instead of
        // orchestration/policy/audit. Reusing credentialBoundaryRule() itself here would be a bug the
        // test looks like it caught: its subject clause names orchestration/policy/audit, so pointed at
        // fixtureClasses — which live in arch.fixture, none of those — it would match nothing and
        // PASS vacuously, proving nothing about whether the rule can ever fail. T015's own falsifiability
        // tests build a fresh rule instance with the fixture package as the subject for exactly this
        // reason.
        String fixturePackage = CreatorCredentialLeakFixture.class.getPackageName();
        ArchRule ruleAimedAtFixture = noClasses()
                .that().resideInAPackage(fixturePackage + "..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "agentic.shortener.domain.creator.CreatorCredential")
                .because("T063a: this must FAIL, or the clean result above carries no information");

        JavaClasses fixtureClasses = new ClassFileImporter().importPackages(fixturePackage);

        AssertionError failure = assertThrows(AssertionError.class,
                () -> ruleAimedAtFixture.check(fixtureClasses),
                "the rule did NOT fail on a class that deliberately references the credential type — "
                        + "which would mean the clean result above carries no information");

        assertTrue(failure.getMessage().contains("Architecture Violation"), failure.getMessage());
        assertTrue(failure.getMessage().contains("CreatorCredentialLeakFixture"), failure.getMessage());
    }
}
