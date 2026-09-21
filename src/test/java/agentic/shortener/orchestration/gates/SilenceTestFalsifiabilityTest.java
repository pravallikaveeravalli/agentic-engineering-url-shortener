package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.gates.fixture.SilenceAsApprovalFixture;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RetentionPolicy;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.SafeStopHandler;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.state.SuspensionTrigger;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T061a — silence-test falsifiability proof. FR-ORC-013, NFR-AUT-002, SC-005.
 *
 * <p>{@link SilenceTest}'s own Guard: "this is the single most important negative test in the suite. If it
 * passes because the run advanced, the governance claim is void." Four other harnesses in this plan carry a
 * falsifiability fixture on exactly that reasoning (T015, T049, T063a, T071's determinism check); the one
 * guarding silence-is-never-approval did not, until now.
 *
 * <p>The fixture ({@link SilenceAsApprovalFixture}) is a test-scope override that cannot reach production
 * configuration — it lives under {@code orchestration.gates.fixture} and calls
 * {@link JdbcRunStore#transitionNode} directly, simulating a caller that bypassed the (non-existent)
 * TIMED_OUT submission path entirely, which is the only way this defect could ever occur in this design.
 */
@DisplayName("T061a — T061's assertion is falsifiable: it FAILS when a gate advances on silence")
class SilenceTestFalsifiabilityTest extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private JdbcRunStore runStore;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        runStore = new JdbcRunStore(connections, clock);
        runId = runStore.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");

        runStore.transitionNode(runId, "S1", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S1", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S2", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S2", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S2", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S3", StageState.BLOCKED, StageState.READY, "x");
        runStore.transitionNode(runId, "S3", StageState.READY, StageState.RUNNING, "x");
        runStore.transitionNode(runId, "S3", StageState.RUNNING, StageState.SUCCEEDED, "x");
        runStore.transitionNode(runId, "S4", StageState.BLOCKED, StageState.AWAITING_APPROVAL,
                "material ambiguity found");
    }

    /**
     * T061's own assertion, extracted so it can be pointed at two different actions: the real suspension
     * path, and the fixture's broken one. Failing on the fixture and passing on the real path is the whole
     * proof.
     */
    private void assertSilenceProducedSuspension() {
        RunState state = runStore.run(runId).orElseThrow().state();
        assertEquals(RunState.SAFE_STOP, state,
                "silence must produce SAFE_STOP; the run is " + state);
    }

    @Test
    @DisplayName("WITH the fixture active: T061's assertion FAILS, and names the advanced stage")
    void fixtureActiveMakesTheAssertionFail() {
        SilenceAsApprovalFixture.advanceAsIfApproved(runStore, runId, "S4");

        AssertionFailedError failure = assertThrows(AssertionFailedError.class,
                this::assertSilenceProducedSuspension,
                "T061's own assertion did NOT fail against a run that advanced on silence — which "
                        + "would mean the governance claim it makes is void");

        assertEquals(RunState.RUNNING, runStore.run(runId).orElseThrow().state(),
                "confirms WHY it failed: the run kept running instead of suspending");
        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S4").orElseThrow().state(),
                "and S4 — the advanced stage — is exactly what a reader needs to act on this "
                        + "failure");
    }

    @Test
    @DisplayName("WITH the fixture REMOVED (the real suspension path): T061's assertion PASSES")
    void fixtureRemovedMeansTheAssertionPasses() {
        // The real path, not the fixture: SafeStopHandler.suspend, exactly as SilenceTest itself proves.
        new SafeStopHandler(connections, Clock.fixed(T0, ZoneOffset.UTC), new RetentionPolicy())
                .suspend(runId, SuspensionTrigger.GATE_TIMEOUT, "no decision within the wait");

        assertSilenceProducedSuspension();
    }

    @Test
    @DisplayName("the fixture is a test-scope override that cannot reach production configuration")
    void fixtureCannotReachProduction() {
        // Structural: no production class may depend on the fixture package at all — it can only ever
        // be reached from a test.
        var production = new com.tngtech.archunit.core.importer.ClassFileImporter()
                .withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined
                        .DO_NOT_INCLUDE_TESTS)
                .importPackages("agentic.shortener");

        var rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAPackage("agentic.shortener..")
                .should().dependOnClassesThat().resideInAPackage(
                        SilenceAsApprovalFixture.class.getPackageName() + "..")
                .because("T061a: the fixture must be a test-scope override, unreachable from production");

        rule.check(production);
    }
}
