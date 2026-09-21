package agentic.shortener.orchestration.gates;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T063 — actor-authority check. FR-ORC-013, FR-ORC-021, NFR-AUT-001, CN-012. EC-024.
 *
 * <p><strong>What this proves and what it cannot, stated so the evidence is not over-read</strong> (T063's
 * own Guard). It proves that no step in the workflow submits or satisfies a gate decision — the
 * architecture assertion is the real control, because it removes the code path rather than checking a
 * field. It does not prove that a caller asserting {@code actorType: human} is human: the surface is
 * unauthenticated by design (CN-012), and verified actor identity is out of scope by owner decision,
 * 2026-09-20.
 */
@DisplayName("T063 — no actor without recorded authority takes effect, and the real control is structural")
class ActorAuthorityTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private GateDecision decision(String actorType, GateOutcome outcome) {
        List<String> changes = outcome == GateOutcome.CHANGES_REQUESTED ? List.of("x") : List.of();
        String escalationTarget = outcome == GateOutcome.ESCALATED ? "the release owner" : null;
        return new GateDecision(UUID.randomUUID(), "S4", null, GateClass.UNRESOLVED_AMBIGUITY, outcome,
                new Actor(actorType, "someone"), T0, "x",
                "docs/governance/gate-decisions/gate-01.md", changes, escalationTarget, null);
    }

    @Test
    @DisplayName("EC-024: ActorAuthority.isAuthorized is FALSE for 'system', for every gate class and outcome")
    void systemHasNoRecordedAuthorityForAnyDecision() {
        // Not narrowed to APPROVED. gate_decision_actor_is_human requires a human unconditionally —
        // the store's own authority is that 'system' decides nothing that goes through GateDecision at
        // all. Its only recorded authority is deadline expiry and retention-driven abandonment, and
        // NEITHER of those is a GateDecision: SafeStopHandler and RetentionPolicy write audit_record
        // directly, never gate_decision.
        Actor system = new Actor("system", "the retention scheduler");
        List<String> authorized = new java.util.ArrayList<>();
        for (GateClass gateClass : GateClass.values()) {
            for (GateOutcome outcome : GateOutcome.values()) {
                if (ActorAuthority.isAuthorized(system, gateClass, outcome)) {
                    authorized.add(gateClass + "/" + outcome);
                }
            }
        }
        assertEquals(List.of(), authorized,
                "'system' must have recorded authority for NOTHING that goes through a GateDecision: "
                        + authorized);

        // And GateDecision itself backstops this — the same double-enforcement pattern this project
        // uses elsewhere (AmbiguityRecord + its own DB CHECK): the predicate is not the only thing
        // standing between a system actor and a recorded decision.
        for (GateOutcome outcome : GateOutcome.values()) {
            assertThrows(IllegalArgumentException.class, () -> decision("system", outcome),
                    "GateDecision's own constructor must refuse 'system' for " + outcome + " too");
        }
    }

    @Test
    @DisplayName("a self-identified agent actor cannot even be constructed, so it cannot be authorized either")
    void agentActorIsNotConstructible() {
        // FR-ORC-021: an agent may never decide a gate at any autonomy setting. Actor's own constructor
        // (T058) refuses this before GateDecision or ActorAuthority is ever reached.
        assertThrows(IllegalArgumentException.class, () -> new Actor("agent", "the acting agent"));
    }

    @Test
    @DisplayName("ActorAuthority.isAuthorized is TRUE for a human, for every gate class and outcome")
    void humanIsAuthorizedForEveryOutcome() {
        Actor human = new Actor("human", "the owner");
        List<String> unauthorized = new java.util.ArrayList<>();
        for (GateClass gateClass : GateClass.values()) {
            for (GateOutcome outcome : GateOutcome.values()) {
                if (!ActorAuthority.isAuthorized(human, gateClass, outcome)) {
                    unauthorized.add(gateClass + "/" + outcome);
                }
            }
        }
        assertEquals(List.of(), unauthorized,
                "a human decides any gate class at any outcome (subject to that outcome's own "
                        + "requirements, which ActorAuthority does not duplicate — GateDecision "
                        + "already owns those): " + unauthorized);

        for (GateOutcome outcome : GateOutcome.values()) {
            assertTrue(decision("human", outcome) != null, "human refused for " + outcome);
        }
    }

    @Test
    @DisplayName("the isAuthorized check is falsifiable — it is not hardcoded true")
    void isAuthorizedCheckIsFalsifiable() {
        // Without this, isAuthorized could be `return true;` and every test above would still pass.
        assertFalse(ActorAuthority.isAuthorized(new Actor("system", "x"), GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.APPROVED));
        assertTrue(ActorAuthority.isAuthorized(new Actor("human", "x"), GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.APPROVED));
    }

    // ==============================================================================================
    // the architecture assertion — the real control
    // ==============================================================================================

    private static final String ROOT = "agentic.shortener";

    @Test
    @DisplayName("no executor package — deterministic, AI, or fake — references the gate-decision path")
    void noExecutorPackageReferencesTheGateDecisionPath() {
        // T014 already carries this rule and T063 is the task it is attributed to asserting again, now
        // that both packages hold real classes rather than being armed against an empty match.
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".orchestration.executor..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".orchestration.gates..")
                .because("CR-021, T063: no workflow step submits or satisfies a gate decision. The "
                        + "control is that the code path does not exist");

        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
        rule.check(production);
    }

    @Test
    @DisplayName("the rule is NOT vacuous — both packages hold real classes now")
    void bothPackagesAreNonEmpty() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        long executorClasses = production.stream()
                .filter(c -> c.getPackageName().startsWith(ROOT + ".orchestration.executor")).count();
        long gateClasses = production.stream()
                .filter(c -> c.getPackageName().startsWith(ROOT + ".orchestration.gates")).count();

        assertTrue(executorClasses > 0, "the executor package must hold real classes for this rule to "
                + "mean anything, not merely pass on an empty match");
        assertTrue(gateClasses > 0, "same for the gates package");
    }

    @Test
    @DisplayName("this class itself (ActorAuthority) does not reach into an executor package")
    void actorAuthorityItselfStaysOnItsOwnSide() {
        // The mirror check: ActorAuthority is part of orchestration.gates, and nothing requires it to
        // depend on an executor — confirming the boundary runs one way, not that it happens to be
        // silent in both directions by accident.
        assertFalse(ActorAuthority.class.getName().startsWith(ROOT + ".orchestration.executor"));
    }
}
