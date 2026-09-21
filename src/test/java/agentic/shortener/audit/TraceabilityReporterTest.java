package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T123 — TraceabilityReporter's parsing and cross-referencing, proven against small fixtures rather
 * than the live, ever-changing {@code spec.md}/{@code tasks.md} — so this test's expectations do not drift
 * out from under it as the real documents grow. {@code ZeroOrphanTest} (T124) runs the same reporter
 * against the real files.
 */
@DisplayName("T123 — TraceabilityReporter: parses spec.md/tasks.md and cross-references them mechanically")
class TraceabilityReporterTest {

    private static final String SPEC_HEADER = """
            ## Traceability

            | Requirement | Journey | Scenario | Edge cases | Design | ADR | Task | Test | Evidence |
            |---|---|---|---|---|---|---|---|---|
            """;

    private static Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    @DisplayName("parseSpecTable extracts every column, and strips a retired-row annotation from the id")
    void parseSpecTableExtractsColumns(@TempDir Path dir) throws IOException {
        Path spec = write(dir, "spec.md", SPEC_HEADER
                + "| FR-URL-001 | US-1 | DS-A | — | Plan §2 | ADR-007 | T039, T042 | UrlShortenerAcceptanceIT | `docs/evidence/` |\n"
                + "| FR-ORC-015 *(RETIRED — CR-032)* | US-3 | DS-B | — | Plan §6 | ADR-003 | T147 | — | `docs/LIMITATIONS.md` |\n");

        List<SpecRequirementRow> rows = new TraceabilityReporter(dir).parseSpecTable(spec);

        assertEquals(2, rows.size());
        assertEquals("FR-URL-001", rows.get(0).requirementId());
        assertEquals("ADR-007", rows.get(0).adr());
        assertEquals("T039, T042", rows.get(0).taskCell());
        assertEquals("FR-ORC-015", rows.get(1).requirementId(), "the *(RETIRED — CR-032)* suffix is stripped");
    }

    @Test
    @DisplayName("parseTasks extracts taskId, done state, artifact paths, and requirement references")
    void parseTasksExtractsFields(@TempDir Path dir) throws IOException {
        Path tasks = write(dir, "tasks.md",
                "- [x] T111 [US5] Audit writer with six mandatory fields — `src/main/java/agentic/shortener/audit/AuditWriter.java`\n"
                + "  - **Req**: **FR-ORC-023**, NFR-AUD-001 · **Scn**: all · **ADR**: **ADR-010** · **Pre**: T027\n"
                + "  - **Deps**: T027 · **Par**: no · **Artifact**: an append-only writer\n"
                + "- [ ] T199 [P] [US5] Not yet done — `src/main/java/agentic/shortener/audit/Nope.java`\n"
                + "  - **Req**: FR-ORC-999 · **Scn**: all · **ADR**: — · **Pre**: T111\n");

        List<TaskEntry> entries = new TraceabilityReporter(dir).parseTasks(tasks);

        assertEquals(2, entries.size());
        TaskEntry first = entries.get(0);
        assertEquals("T111", first.taskId());
        assertTrue(first.done());
        assertEquals(List.of("src/main/java/agentic/shortener/audit/AuditWriter.java"), first.artifactPaths());
        assertTrue(first.requirementRefs().contains("FR-ORC-023"));
        assertTrue(first.requirementRefs().contains("NFR-AUD-001"));

        TaskEntry second = entries.get(1);
        assertEquals("T199", second.taskId());
        assertTrue(second.done() == false);
    }

    @Test
    @DisplayName("a clean fixture — every requirement tasked, every artifact real, every test present — "
            + "produces zero orphans in all four classes")
    void cleanFixtureIsOrphanFree(@TempDir Path dir) throws IOException {
        Path testDir = dir.resolve("src/test/java/agentic/shortener/audit");
        Files.createDirectories(testDir);
        Files.writeString(testDir.resolve("WidgetTest.java"), "class WidgetTest {}");
        Path mainDir = dir.resolve("src/main/java/agentic/shortener/audit");
        Files.createDirectories(mainDir);
        Files.writeString(mainDir.resolve("Widget.java"), "class Widget {}");

        Path spec = write(dir, "spec.md", SPEC_HEADER
                + "| FR-URL-099 | US-1 | DS-A | — | Plan §2 | ADR-007 | T900 | WidgetTest | `docs/evidence/` |\n");
        Path tasks = write(dir, "tasks.md",
                "- [x] T900 [US1] Widget — `src/main/java/agentic/shortener/audit/Widget.java`\n"
                + "  - **Req**: FR-URL-099 · **Scn**: DS-A · **ADR**: ADR-007 · **Pre**: —\n"
                + "  - **Validate**: WidgetTest passes\n");

        TraceabilityReport report = new TraceabilityReporter(dir).report(spec, tasks);
        assertTrue(report.isClean(), report.toString());
    }

    @Test
    @DisplayName("ORPHAN REQUIREMENT: a matrix row no task references")
    void detectsOrphanRequirement(@TempDir Path dir) throws IOException {
        Path spec = write(dir, "spec.md", SPEC_HEADER
                + "| FR-URL-100 | US-1 | DS-A | — | Plan §2 | ADR-007 | *Tasks stage* | *Tasks stage* | *Implement stage* |\n");
        Path tasks = write(dir, "tasks.md", "");

        TraceabilityReport report = new TraceabilityReporter(dir).report(spec, tasks);
        assertEquals(1, report.orphanRequirements().size());
        assertTrue(report.orphanRequirements().get(0).contains("FR-URL-100"));
        assertTrue(report.orphanTasks().isEmpty());
    }

    @Test
    @DisplayName("ORPHAN TASK: a task whose Req field names nothing recognizable")
    void detectsOrphanTask(@TempDir Path dir) throws IOException {
        Path spec = write(dir, "spec.md", SPEC_HEADER);
        Path tasks = write(dir, "tasks.md",
                "- [x] T901 [US1] A task with no traceable requirement\n"
                + "  - **Req**: TBD · **Scn**: — · **ADR**: — · **Pre**: —\n");

        TraceabilityReport report = new TraceabilityReporter(dir).report(spec, tasks);
        assertEquals(1, report.orphanTasks().size());
        assertTrue(report.orphanTasks().get(0).contains("T901"));
    }

    @Test
    @DisplayName("ORPHAN IMPLEMENTATION: a done task whose artifact path does not exist on disk")
    void detectsOrphanImplementation(@TempDir Path dir) throws IOException {
        Path spec = write(dir, "spec.md", SPEC_HEADER
                + "| FR-URL-101 | US-1 | DS-A | — | Plan §2 | ADR-007 | T902 | — | — |\n");
        Path tasks = write(dir, "tasks.md",
                "- [x] T902 [US1] Claims a file that was never written — `src/main/java/nowhere/Ghost.java`\n"
                + "  - **Req**: FR-URL-101 · **Scn**: DS-A · **ADR**: ADR-007 · **Pre**: —\n");

        TraceabilityReport report = new TraceabilityReporter(dir).report(spec, tasks);
        assertEquals(1, report.orphanImplementations().size());
        assertTrue(report.orphanImplementations().get(0).contains("T902"));
        assertTrue(report.orphanImplementations().get(0).contains("Ghost.java"));
    }

    @Test
    @DisplayName("ORPHAN TEST: a done task names a test class that does not exist on disk")
    void detectsOrphanTest(@TempDir Path dir) throws IOException {
        Path mainDir = dir.resolve("src/main/java/agentic/shortener/audit");
        Files.createDirectories(mainDir);
        Files.writeString(mainDir.resolve("Gadget.java"), "class Gadget {}");

        Path spec = write(dir, "spec.md", SPEC_HEADER
                + "| FR-URL-102 | US-1 | DS-A | — | Plan §2 | ADR-007 | T903 | GadgetTest | `docs/evidence/` |\n");
        Path tasks = write(dir, "tasks.md",
                "- [x] T903 [US1] Gadget — `src/main/java/agentic/shortener/audit/Gadget.java`\n"
                + "  - **Req**: FR-URL-102 · **Scn**: DS-A · **ADR**: ADR-007 · **Pre**: —\n"
                + "  - **Validate**: GadgetTest passes, but nobody ever wrote it\n");

        TraceabilityReport report = new TraceabilityReporter(dir).report(spec, tasks);
        assertEquals(1, report.orphanTests().size());
        assertTrue(report.orphanTests().get(0).contains("GadgetTest"));
        assertTrue(report.orphanImplementations().isEmpty(), "Gadget.java DOES exist — only the test is missing");
    }

    @Test
    @DisplayName("a task not yet done is never flagged as an orphan implementation or test, even if its "
            + "artifact does not exist yet — it is unstarted work, not a broken claim")
    void unstartedTaskIsNotAnOrphan(@TempDir Path dir) throws IOException {
        Path spec = write(dir, "spec.md", SPEC_HEADER
                + "| FR-URL-103 | US-1 | DS-A | — | Plan §2 | ADR-007 | T904 | FutureTest | — |\n");
        Path tasks = write(dir, "tasks.md",
                "- [ ] T904 [US1] Not started yet — `src/main/java/agentic/shortener/audit/Future.java`\n"
                + "  - **Req**: FR-URL-103 · **Scn**: DS-A · **ADR**: ADR-007 · **Pre**: —\n"
                + "  - **Validate**: FutureTest\n");

        TraceabilityReport report = new TraceabilityReporter(dir).report(spec, tasks);
        assertTrue(report.orphanImplementations().isEmpty());
        assertTrue(report.orphanTests().isEmpty());
        assertTrue(report.orphanRequirements().isEmpty(), "FR-URL-103 IS referenced, by T904, even if unstarted");
    }
}
