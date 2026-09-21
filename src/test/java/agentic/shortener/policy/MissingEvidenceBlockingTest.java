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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T110a — an evidence artifact missing or unreadable at readiness evaluation produces a blocking
 * determination naming constitutional condition 9, never a pass and never a warning. FR-ORC-025,
 * FR-ORC-023, SC-009. EC-028.
 *
 * <p>Two seeds, matching EC-028's own wording exactly: a completed stage whose evidence artifact is
 * <strong>deleted</strong>, then one whose artifact is <strong>present but unparseable</strong> (here:
 * blank — the readable-but-empty shape a truncated or half-written file takes). Both block; both name
 * condition 9 and the specific artifact, not merely a count.
 */
@DisplayName("T110a — EC-028: missing or unreadable evidence blocks and names the artifact")
class MissingEvidenceBlockingTest extends PostgresIntegrationTest {

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
        buildFixtureWithNamedEvidenceFile(root);
    }

    /** The same shared fixture shape as T110's own, but with an EVIDENCE FILE spec.md names by path,
     * rather than a bare directory reference — EC-028 needs a specific artifact to be able to go missing. */
    private static void buildFixtureWithNamedEvidenceFile(Path root) throws IOException {
        Path featureDir = root.resolve("specs/001-agentic-sdlc-url-shortener");
        write(featureDir.resolve("spec.md"), """
                ## Traceability

                | Requirement | Journey | Scenario | Edge cases | Design | ADR | Task | Test | Evidence |
                |---|---|---|---|---|---|---|---|---|
                | FR-TEST-001 | US-1 | DS-A | — | Plan §1 | ADR-001 | T900 | FixtureTest | `docs/evidence/completed-stage.md` |
                """);
        write(featureDir.resolve("tasks.md"), """
                - [x] T900 [US1] Fixture task — `src/main/java/fixture/Fixture.java`
                  - **Req**: FR-TEST-001 · **Scn**: DS-A · **ADR**: ADR-001 · **Pre**: —
                  - **Validate**: FixtureTest passes
                """);

        write(root.resolve("src/main/java/fixture/Fixture.java"), "package fixture; class Fixture {}");
        write(root.resolve("src/test/java/fixture/FixtureTest.java"), "package fixture; class FixtureTest {}");
        write(root.resolve("src/test/java/fixture/FixtureIT.java"), "package fixture; class FixtureIT {}");
        write(root.resolve("pom.xml"), "<project><dependencies></dependencies></project>");

        write(root.resolve("docs/governance/change-control/CR-FIXTURE.md"), "Changes `pom.xml`.\n");
        write(root.resolve("docs/governance/gate-decisions/gate-01-fixture.md"), "| outcome | APPROVED |\n");

        for (String archTest : java.util.List.of("DependencyDirectionTest",
                "DependencyDirectionFalsifiabilityTest", "CredentialBoundaryTest", "AnalyticsPortBypassTest")) {
            write(root.resolve("target/surefire-reports/agentic.shortener.arch." + archTest + ".txt"),
                    "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0\n");
        }
        write(root.resolve("target/surefire-reports/fixture.FixtureTest.txt"),
                "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0\n");
        write(root.resolve("target/failsafe-reports/fixture.FixtureIT.txt"),
                "Tests run: 1, Failures: 0, Errors: 0, Skipped: 0\n");

        write(root.resolve("target/telemetry/captured.log"), "no secrets here\n");
        write(root.resolve("target/dependency-list.txt"), "no dependencies\n");

        // The evidence artifact spec.md's Evidence column names by path — THIS is what T110a's two seeds
        // will delete and then blank, respectively.
        write(root.resolve("docs/evidence/completed-stage.md"), "# Completed stage evidence\n\nReal content.\n");

        write(root.resolve("README.md"), "# Fixture project\n");
        write(root.resolve("docs/LIMITATIONS.md"), "# Limitations\n\nNone in this fixture.\n");
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private ReleaseReadinessEvaluator evaluator() {
        return new ReleaseReadinessEvaluator(connections, root, clock);
    }

    @Test
    @DisplayName("the fixture, with its named evidence file intact, is ready")
    void fixtureWithIntactEvidenceIsReady() throws Exception {
        ReadinessVerdict verdict = evaluator().evaluate(Map.of());
        assertTrue(verdict.ready(), verdict.report());
    }

    @Test
    @DisplayName("EC-028 seed 1: the evidence artifact is DELETED — blocks, names condition 9 and the path")
    void deletedEvidenceArtifactBlocksAndNamesItself() throws Exception {
        Files.delete(root.resolve("docs/evidence/completed-stage.md"));

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());

        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 9"), verdict.report());
        assertTrue(verdict.report().contains("FR-TEST-001"), verdict.report());
        assertTrue(verdict.report().contains("docs/evidence/completed-stage.md"), verdict.report());
        assertTrue(verdict.report().contains("does not exist"), verdict.report());
    }

    @Test
    @DisplayName("EC-028 seed 2: the evidence artifact is PRESENT but unparseable (blank) — blocks, names "
            + "condition 9 and the path")
    void unparseableEvidenceArtifactBlocksAndNamesItself() throws Exception {
        Files.writeString(root.resolve("docs/evidence/completed-stage.md"), "");

        ReadinessVerdict verdict = evaluator().evaluate(Map.of());

        assertFalse(verdict.ready());
        assertTrue(verdict.report().contains("condition 9"), verdict.report());
        assertTrue(verdict.report().contains("FR-TEST-001"), verdict.report());
        assertTrue(verdict.report().contains("docs/evidence/completed-stage.md"), verdict.report());
        assertTrue(verdict.report().contains("unreadable"), verdict.report());
    }

    @Test
    @DisplayName("neither seed is treated as absent-and-therefore-not-applicable — both genuinely block, "
            + "not merely warn")
    void bothSeedsGenuinelyBlockNotMerelyWarn() throws Exception {
        Files.delete(root.resolve("docs/evidence/completed-stage.md"));
        ReadinessVerdict deleted = evaluator().evaluate(Map.of());
        assertFalse(deleted.ready(), "a missing evidence artifact must BLOCK, not merely be noted");

        Files.writeString(root.resolve("docs/evidence/completed-stage.md"), "");
        ReadinessVerdict blank = evaluator().evaluate(Map.of());
        assertFalse(blank.ready(), "an unparseable evidence artifact must BLOCK, not merely be noted");
    }
}
