package agentic.shortener.orchestration.summary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T129, RED-FIRST. FR-ORC-026, CN-001, ADR-010.
 *
 * <p>Builds a small, self-contained repository under a fresh {@code @TempDir} and asserts two things about
 * {@link SummaryAssembler}: that it actually reads the recorded evidence (a done task, an accepted ADR with
 * a rejected option, an executed test-report tier, a residual-risk file, an AI-demo file all show up as
 * claims), and — the property T129's own Validate clause names — that {@code evidencePath()} on every single
 * claim it produces resolves to a real file under the fixture root. The second assertion is deliberately not
 * scoped to individual sections: T129's Guard clause treats an untraceable claim as a defect regardless of
 * which section it would have landed in.
 */
@DisplayName("T129 — SummaryAssembler reads only recorded evidence, every claim traces")
class SummaryAssemblerTest {

    @TempDir
    Path root;

    @BeforeEach
    void setUp() throws IOException {
        buildFixture(root);
    }

    @Test
    @DisplayName("every claim's evidencePath exists on disk under the fixture root")
    void everyClaimTraces() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        List<EvidencedClaim> all = summary.allClaims();
        assertFalse(all.isEmpty(), "the fixture seeds real evidence in every section; an empty result "
                + "means the assembler read nothing");
        for (EvidencedClaim claim : all) {
            assertTrue(Files.exists(root.resolve(claim.evidencePath())),
                    "claim \"" + claim.text() + "\" cites " + claim.evidencePath()
                            + ", which does not exist under the fixture root — exactly the untraceable "
                            + "claim T129's Guard clause forbids");
        }
    }

    @Test
    @DisplayName("what was built: a done task with an existing artifact path becomes a claim")
    void doneTaskWithRealArtifactBecomesClaim() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.whatWasBuilt().stream()
                        .anyMatch(c -> c.text().contains("T900") && c.evidencePath().endsWith("Fixture.java")),
                "expected a claim citing T900's real artifact, found: " + summary.whatWasBuilt());
    }

    @Test
    @DisplayName("what was built: a done task whose claimed artifact does not exist produces no claim")
    void doneTaskWithMissingArtifactProducesNoClaim() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.whatWasBuilt().stream().noneMatch(c -> c.text().contains("T901")),
                "T901 claims a path that does not exist on disk; asserting it anyway would be exactly the "
                        + "fabricated claim this task exists to prevent — found: " + summary.whatWasBuilt());
    }

    @Test
    @DisplayName("decisions: an accepted ADR with one rejected option is reported as both")
    void adrDecisionAndRejectedOptionBothReported() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.decisionsAndRejectedAlternatives().stream()
                        .anyMatch(c -> c.text().contains("Accepted") && c.evidencePath().endsWith("ADR-900-fixture-decision.md")),
                "expected the fixture ADR's Status line, found: " + summary.decisionsAndRejectedAlternatives());
        assertTrue(summary.decisionsAndRejectedAlternatives().stream()
                        .anyMatch(c -> c.text().contains("Option B") && c.text().toLowerCase().contains("reject")),
                "expected Option B named as rejected, found: " + summary.decisionsAndRejectedAlternatives());
    }

    @Test
    @DisplayName("validation: an executed test-report tier with a real summary line becomes a claim")
    void executedTestTierBecomesClaim() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.executedValidationAndResults().stream()
                        .anyMatch(c -> c.text().contains("5 tests run") && c.text().contains("0 failures")),
                "expected the fixture's fast-tier summary (3 from FixtureTest + 2 from its @Nested class, "
                        + "0 failures), found: " + summary.executedValidationAndResults());
    }

    @Test
    @DisplayName("validation: a @Nested test class's own report is counted under its outer class")
    void nestedTestClassReportIsCounted() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.executedValidationAndResults().stream()
                        .noneMatch(c -> c.text().startsWith("fast tier") && c.text().contains("3 tests run")),
                "if fixture.FixtureTest$Contract's report were wrongly excluded (its report name has no "
                        + "matching .java file, only its OUTER class FixtureTest.java does), the total would "
                        + "stay at 3 instead of 5 -- found: " + summary.executedValidationAndResults());
    }

    @Test
    @DisplayName("validation: no failsafe-reports directory means no integration-tier claim, not a fabricated one")
    void noIntegrationReportsMeansNoClaim() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.executedValidationAndResults().stream()
                        .noneMatch(c -> c.text().contains("integration tier")),
                "the fixture has no target/failsafe-reports directory at all; claiming an integration tier "
                        + "ran would be invented, found: " + summary.executedValidationAndResults());
    }

    @Test
    @DisplayName("validation: a report for a since-deleted test class is not counted")
    void staleReportForDeletedClassIsNotCounted() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.executedValidationAndResults().stream()
                        .filter(c -> c.text().startsWith("fast tier"))
                        .allMatch(c -> !c.text().contains("4 tests run")),
                "the fixture seeds a report for fixture.GhostTest, which has no matching .java file under "
                        + "src/test/java (it was 'deleted'); counting it would report 4 tests instead of the "
                        + "real 3, found: " + summary.executedValidationAndResults());
    }

    @Test
    @DisplayName("validation: a report left in the wrong tier's directory is not counted there")
    void reportInWrongTierDirectoryIsNotCounted() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.executedValidationAndResults().stream()
                        .noneMatch(c -> c.text().startsWith("fast tier") && c.text().contains("failures")
                                && !c.text().contains("0 failures")),
                "the fixture seeds a failing report for fixture.MisfiledIT under target/surefire-reports "
                        + "(an IT class does not belong there per pom.xml's own Failsafe include pattern); "
                        + "it must not contaminate the fast-tier count, found: "
                        + summary.executedValidationAndResults());
    }

    @Test
    @DisplayName("residual risks: a named backlog file becomes a claim")
    void residualRiskFileBecomesClaim() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.residualRisksAndLimitations().stream()
                        .anyMatch(c -> c.evidencePath().equals("docs/delivery/backlog.md")),
                "expected a claim citing the fixture's backlog.md, found: " + summary.residualRisksAndLimitations());
    }

    @Test
    @DisplayName("AI-assisted process: an ai-demo file becomes a claim")
    void aiDemoFileBecomesClaim() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();

        assertTrue(summary.aiAssistedProcess().stream()
                        .anyMatch(c -> c.evidencePath().endsWith("T900-fixture-demo.txt")),
                "expected a claim citing the fixture's ai-demo file, found: " + summary.aiAssistedProcess());
    }

    @Test
    @DisplayName("toMarkdown renders every claim's text somewhere in the document")
    void markdownRendersEveryClaim() {
        EngineeringSummary summary = new SummaryAssembler(root).assemble();
        String markdown = summary.toMarkdown();

        for (EvidencedClaim claim : summary.allClaims()) {
            assertTrue(markdown.contains(claim.text()), "markdown is missing claim: " + claim.text());
        }
        assertEquals(5, countSections(markdown), "expected exactly the five sections T129's Artifact "
                + "clause names");
    }

    private static long countSections(String markdown) {
        return markdown.lines().filter(l -> l.startsWith("## ")).count();
    }

    // ==============================================================================================
    // Fixture
    // ==============================================================================================

    private static void buildFixture(Path root) throws IOException {
        Path featureDir = root.resolve("specs/001-agentic-sdlc-url-shortener");
        Files.createDirectories(featureDir);
        write(featureDir.resolve("spec.md"), """
                ## Traceability

                | Requirement | Journey | Scenario | Edge cases | Design | ADR | Task | Test | Evidence |
                |---|---|---|---|---|---|---|---|---|
                | FR-TEST-900 | US-1 | DS-A | — | Plan §1 | ADR-900 | T900 | FixtureTest | `docs/evidence/fixture-evidence.md` |
                """);
        write(featureDir.resolve("tasks.md"), """
                - [x] T900 [US1] Fixture task with a real artifact — `src/main/java/fixture/Fixture.java`
                  - **Req**: FR-TEST-900 · **Scn**: DS-A · **ADR**: ADR-900 · **Pre**: —
                  - **Validate**: FixtureTest passes
                - [x] T901 [US1] Fixture task claiming a nonexistent artifact — `src/main/java/fixture/Ghost.java`
                  - **Req**: FR-TEST-900 · **Scn**: DS-A · **ADR**: ADR-900 · **Pre**: —
                  - **Validate**: n/a
                - [ ] T902 [US1] Not-yet-done fixture task — `src/main/java/fixture/NotYet.java`
                  - **Req**: FR-TEST-900 · **Scn**: DS-A · **ADR**: ADR-900 · **Pre**: —
                  - **Validate**: n/a
                """);

        Path fixtureMain = root.resolve("src/main/java/fixture");
        Files.createDirectories(fixtureMain);
        write(fixtureMain.resolve("Fixture.java"), "package fixture; class Fixture {}");

        Path adrDir = root.resolve("docs/governance/adr");
        Files.createDirectories(adrDir);
        write(adrDir.resolve("ADR-900-fixture-decision.md"), """
                # ADR-900: Fixture Decision

                ## Status

                **Accepted** -- 2026-09-21 by Fixture Owner.

                ## Options Considered

                ### Option A -- Chosen approach

                Description of A.

                ### Option B -- Rejected approach

                Description of B.

                ## Decision

                **Option A.**
                """);

        Path deliveryDir = root.resolve("docs/delivery");
        Files.createDirectories(deliveryDir);
        write(deliveryDir.resolve("backlog.md"), "# Backlog\n\n- Fixture backlog entry.\n");
        write(deliveryDir.resolve("baseline-omissions.md"), "# Baseline omissions\n\n- Fixture omission.\n");

        Path aiDemoDir = root.resolve("docs/evidence/ai-demos");
        Files.createDirectories(aiDemoDir);
        write(aiDemoDir.resolve("T900-fixture-demo.txt"), "model: fixture-model\nprompt: fixture\nresponse: ok\n");

        Path redPhaseDir = root.resolve("docs/evidence/red-phase");
        Files.createDirectories(redPhaseDir);
        write(redPhaseDir.resolve("20260921T000000Z-T900-fixture-deviation.txt"),
                "deliberate sabotage, real failure, real fix -- fixture red-phase capture\n");

        Path fixtureTest = root.resolve("src/test/java/fixture");
        Files.createDirectories(fixtureTest);
        write(fixtureTest.resolve("FixtureTest.java"), "package fixture; class FixtureTest {}");
        write(fixtureTest.resolve("MisfiledIT.java"), "package fixture; class MisfiledIT {}");

        Path surefireDir = root.resolve("target/surefire-reports");
        Files.createDirectories(surefireDir);
        write(surefireDir.resolve("fixture.FixtureTest.txt"), """
                -------------------------------------------------------------------------------
                Test set: fixture.FixtureTest
                -------------------------------------------------------------------------------
                Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.01 s -- in fixture.FixtureTest
                """);
        // A @Nested class's report name has no matching .java file of its own -- only its OUTER class
        // (FixtureTest.java, seeded above) does. Must still be counted.
        write(surefireDir.resolve("fixture.FixtureTest$Contract.txt"), """
                -------------------------------------------------------------------------------
                Test set: fixture.FixtureTest$Contract
                -------------------------------------------------------------------------------
                Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.01 s -- in fixture.FixtureTest$Contract
                """);
        // Stale: GhostTest has no matching .java file anywhere under src/test/java (it was deleted after
        // this report was written). Must not contribute to the summed count.
        write(surefireDir.resolve("fixture.GhostTest.txt"), """
                -------------------------------------------------------------------------------
                Test set: fixture.GhostTest
                -------------------------------------------------------------------------------
                Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.01 s -- in fixture.GhostTest
                """);
        // Misfiled: MisfiledIT is a real, current class, but an IT belongs under Failsafe, not Surefire
        // (pom.xml's own include/exclude patterns) -- e.g. left here by an ad-hoc -Dtest= run that bypassed
        // the exclude. Must not contribute to the fast-tier count even though the class itself is real.
        write(surefireDir.resolve("fixture.MisfiledIT.txt"), """
                -------------------------------------------------------------------------------
                Test set: fixture.MisfiledIT
                -------------------------------------------------------------------------------
                Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s <<< FAILURE! -- in fixture.MisfiledIT
                """);
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }
}
