package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T124 — zero orphan requirements, tasks, implementations, and tests, in both directions, over the
 * REAL {@code spec.md} and {@code tasks.md} — not a fixture. FR-ORC-027, SC-010.
 *
 * <p>{@code TraceabilityReporterTest} (T123) proves the parsing and cross-referencing mechanism is
 * correct against small, controlled fixtures. This is the mechanism actually run: orphan detection is a
 * mechanism, not a prohibition (T124's own Guard clause) — a class that only ever ran against fixtures
 * would prove the mechanism works, never that the project it is pointed at is clean.
 *
 * <p><strong>This test found and fixed five real findings before it could pass</strong>, none of which
 * were code defects: T002's {@code DF-004} reference used a requirement-id prefix this reporter did not
 * yet recognize; T019/T025/T029/T030's artifact paths were a glob placeholder, a feature-directory-relative
 * path, and two more of the same, which the reporter treated as missing rather than resolving correctly;
 * and five tasks (T010, T026, T054, T080, T081) named a class or file that was genuinely renamed during
 * implementation without the task line being updated to match — real work exists under a different name
 * in every case, confirmed by reading the actual source before any tasks.md line was touched. T147's
 * {@code **Req**} field was missing an explicit {@code FR-ORC-015} reference its own body text already
 * discusses at length. All five task-line corrections are visible in this commit's diff to {@code
 * tasks.md}; none altered a task's Deps, Done, Guard, or Approval content.
 */
@DisplayName("T124 — ZeroOrphanTest: zero orphans across the real spec.md and tasks.md")
class ZeroOrphanTest {

    private static final Path REPO_ROOT = Path.of(".");
    private static final Path SPEC_MD = Path.of("specs/001-agentic-sdlc-url-shortener/spec.md");
    private static final Path TASKS_MD = Path.of("specs/001-agentic-sdlc-url-shortener/tasks.md");

    @Test
    @DisplayName("zero orphan requirements: every matrix row is referenced by at least one task")
    void zeroOrphanRequirements() {
        TraceabilityReport report = new TraceabilityReporter(REPO_ROOT).report(SPEC_MD, TASKS_MD);
        assertTrue(report.orphanRequirements().isEmpty(), report.orphanRequirements().toString());
    }

    @Test
    @DisplayName("zero orphan tasks: every task's Req field names a real requirement, policy, ADR, or "
            + "constitution reference")
    void zeroOrphanTasks() {
        TraceabilityReport report = new TraceabilityReporter(REPO_ROOT).report(SPEC_MD, TASKS_MD);
        assertTrue(report.orphanTasks().isEmpty(), report.orphanTasks().toString());
    }

    @Test
    @DisplayName("zero orphan implementations: every DONE task's claimed artifact exists on disk")
    void zeroOrphanImplementations() {
        TraceabilityReport report = new TraceabilityReporter(REPO_ROOT).report(SPEC_MD, TASKS_MD);
        assertTrue(report.orphanImplementations().isEmpty(), report.orphanImplementations().toString());
    }

    @Test
    @DisplayName("zero orphan tests: every DONE task's claimed test class exists on disk")
    void zeroOrphanTests() {
        TraceabilityReport report = new TraceabilityReporter(REPO_ROOT).report(SPEC_MD, TASKS_MD);
        assertTrue(report.orphanTests().isEmpty(), report.orphanTests().toString());
    }

    @Test
    @DisplayName("the report as a whole is clean")
    void reportIsClean() {
        TraceabilityReport report = new TraceabilityReporter(REPO_ROOT).report(SPEC_MD, TASKS_MD);
        assertTrue(report.isClean(), report.toString());
    }
}
