package agentic.shortener.policy;

import agentic.shortener.orchestration.executor.deterministic.ReadinessVerdict;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T110 — nine negative tests, one per constitutional blocking condition, each seeded individually.
 * FR-ORC-025, SC-009.
 *
 * <p>{@code buildPassingFixture} constructs a small, self-contained repository under a fresh {@code
 * @TempDir} where all nine conditions independently pass — every test starts from the SAME clean baseline
 * and breaks exactly one thing, so a failure in one test can never be explained by another test's leftover
 * state (JUnit gives each {@code @TempDir}-annotated field a fresh directory per test method).
 */
@DisplayName("T110 — nine negative release-readiness tests, each naming its own condition")
class ReleaseBlockingConditionsIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    @TempDir
    Path root;

    private ConnectionSource connections;
    private Clock clock;

    @BeforeEach
    void setUp() throws Exception {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        clock = Clock.fixed(T0, ZoneOffset.UTC);
        buildPassingFixture(root);
    }

    private ReleaseReadinessEvaluator evaluator() {
        return new ReleaseReadinessEvaluator(connections, root, clock);
    }

    // ==============================================================================================
    // The shared, fully-passing fixture every test starts from.
    // ==============================================================================================

    private static void buildPassingFixture(Path root) throws IOException {
        Path featureDir = root.resolve("specs/001-agentic-sdlc-url-shortener");
        Files.createDirectories(featureDir);
        write(featureDir.resolve("spec.md"), """
                ## Traceability

                | Requirement | Journey | Scenario | Edge cases | Design | ADR | Task | Test | Evidence |
                |---|---|---|---|---|---|---|---|---|
                | FR-TEST-001 | US-1 | DS-A | — | Plan §1 | ADR-001 | T900 | FixtureTest | `docs/evidence/fixture-evidence.md` |
                """);
        write(featureDir.resolve("tasks.md"), """
                - [x] T900 [US1] Fixture task — `src/main/java/fixture/Fixture.java`
                  - **Req**: FR-TEST-001 · **Scn**: DS-A · **ADR**: ADR-001 · **Pre**: —
                  - **Validate**: FixtureTest passes
                """);

        Path mainDir = root.resolve("src/main/java/fixture");
        Files.createDirectories(mainDir);
        write(mainDir.resolve("Fixture.java"), "package fixture; class Fixture {}");

        Path testDir = root.resolve("src/test/java/fixture");
        Files.createDirectories(testDir);
        write(testDir.resolve("FixtureTest.java"), "package fixture; class FixtureTest {}");
        write(testDir.resolve("FixtureIT.java"), "package fixture; class FixtureIT {}");

        write(root.resolve("pom.xml"), "<project><dependencies></dependencies></project>");

        Path changeControl = root.resolve("docs/governance/change-control");
        Files.createDirectories(changeControl);
        write(changeControl.resolve("CR-FIXTURE.md"), "# CR-FIXTURE\n\nChanges `pom.xml`.\n");

        Path gateDecisions = root.resolve("docs/governance/gate-decisions");
        Files.createDirectories(gateDecisions);
        write(gateDecisions.resolve("gate-01-fixture.md"), "| outcome | APPROVED |\n");

        Path surefire = root.resolve("target/surefire-reports");
        Files.createDirectories(surefire);
        for (String archTest : List_of("DependencyDirectionTest", "DependencyDirectionFalsifiabilityTest",
                "CredentialBoundaryTest", "AnalyticsPortBypassTest")) {
            write(surefire.resolve("agentic.shortener.arch." + archTest + ".txt"),
                    "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0\n");
        }
        write(surefire.resolve("fixture.FixtureTest.txt"), "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0\n");

        Path failsafe = root.resolve("target/failsafe-reports");
        Files.createDirectories(failsafe);
        write(failsafe.resolve("fixture.FixtureIT.txt"), "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0\n");

        Path telemetry = root.resolve("target/telemetry");
        Files.createDirectories(telemetry);
        write(telemetry.resolve("captured.log"), "no secrets here\n");

        write(root.resolve("target/dependency-list.txt"), "no dependencies\n");

        Path evidence = root.resolve("docs/evidence");
        Files.createDirectories(evidence);
        write(evidence.resolve("fixture-evidence.md"), "# Fixture evidence\n");

        write(root.resolve("README.md"), "# Fixture project\n\nDescribes delivered behaviour.\n");
        write(root.resolve("docs/LIMITATIONS.md"), "# Limitations\n\nNone in this fixture.\n");
    }

    private static java.util.List<String> List_of(String... values) {
        return java.util.List.of(values);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    // ==============================================================================================

    @Test
    @DisplayName("the fixture itself is ready — every subsequent test breaks exactly one condition from here")
    void fixtureIsReady() throws Exception {
        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertTrue(verdict.ready(), verdict.report());
    }

    @Test
    @DisplayName("condition 1: a constitutional-boundary test report shows a failure")
    void condition1Blocks() throws Exception {
        write(root.resolve("target/surefire-reports/agentic.shortener.arch.CredentialBoundaryTest.txt"),
                "Tests run: 3, Failures: 1, Errors: 0, Skipped: 0\n");

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 1"), verdict.report());
    }

    @Test
    @DisplayName("condition 2: a mandatory policy check fails (a planted secret under src/)")
    void condition2Blocks() throws Exception {
        // Built from two literals rather than one contiguous string, so this SOURCE FILE (itself under
        // src/test/java, and so itself in scope for POL-SEC-002's real scan) never contains a string that
        // matches the secret pattern — only the file WRITTEN to the temp fixture at runtime does.
        String plantedSecret = "crk_" + "aQ7zR2mV9xL4bN6kD1sF8wT3yH5jC0pG";
        write(root.resolve("src/main/java/fixture/Leaky.java"),
                "class Leaky { String key = \"" + plantedSecret + "\"; }");

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 2"), verdict.report());
    }

    @Test
    @DisplayName("condition 3: a gate-decision record with no recorded outcome")
    void condition3Blocks() throws Exception {
        write(root.resolve("docs/governance/gate-decisions/gate-02-incomplete.md"),
                "a record with no recorded result field at all\n");

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 3"), verdict.report());
    }

    @Test
    @DisplayName("condition 4: an executed test is failing")
    void condition4Blocks() throws Exception {
        write(root.resolve("target/surefire-reports/fixture.FixtureTest.txt"),
                "Tests run: 1, Failures: 1, Errors: 0, Skipped: 0\n");

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 4"), verdict.report());
    }

    @Test
    @DisplayName("condition 5: security scanning has not been run (no captured telemetry)")
    void condition5Blocks() throws Exception {
        Files.delete(root.resolve("target/telemetry/captured.log"));

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 5"), verdict.report());
    }

    @Test
    @DisplayName("condition 6: traceability is incomplete — a task referencing an unmatched requirement")
    void condition6Blocks() throws Exception {
        Files.writeString(root.resolve("specs/001-agentic-sdlc-url-shortener/tasks.md"), """
                - [x] T900 [US1] Fixture task — `src/main/java/fixture/Fixture.java`
                  - **Req**: FR-TEST-001 · **Scn**: DS-A · **ADR**: ADR-001 · **Pre**: —
                  - **Validate**: FixtureTest passes
                - [x] T901 [US1] An orphan task with no traceable requirement
                  - **Req**: TBD · **Scn**: — · **ADR**: — · **Pre**: —
                """);

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 6"), verdict.report());
    }

    @Test
    @DisplayName("condition 7: documentation does not reflect delivered behaviour (no README.md)")
    void condition7Blocks() throws Exception {
        Files.delete(root.resolve("README.md"));

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 7"), verdict.report());
    }

    @Test
    @DisplayName("condition 8: residual risks and known limitations are not disclosed")
    void condition8Blocks() throws Exception {
        Files.delete(root.resolve("docs/LIMITATIONS.md"));

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 8"), verdict.report());
    }

    @Test
    @DisplayName("condition 9: evidence is unverifiable (no docs/evidence/ at all)")
    void condition9Blocks() throws Exception {
        Files.delete(root.resolve("docs/evidence/fixture-evidence.md"));

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 9"), verdict.report());
    }
}
