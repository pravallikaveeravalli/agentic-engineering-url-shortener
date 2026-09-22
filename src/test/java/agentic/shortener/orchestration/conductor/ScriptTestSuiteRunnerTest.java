package agentic.shortener.orchestration.conductor;

import agentic.shortener.orchestration.executor.deterministic.TestSuiteReport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T131c, RED-FIRST. FR-ORC-029.
 *
 * <p>Real {@code git worktree} checkout against a small fixture repository. The test command is injected as
 * a tiny script (never a shell string — an explicit, tracked file this test writes and invokes by path) so
 * these tests exercise {@link ScriptTestSuiteRunner}'s own report-parsing logic without depending on this
 * project's own Maven toolchain.
 */
@DisplayName("T131c — ScriptTestSuiteRunner: real git checkout, real Surefire-report parsing")
class ScriptTestSuiteRunnerTest {

    @TempDir
    Path repo;

    private String branchRef;

    @BeforeEach
    void setUp() throws Exception {
        run(repo, "git", "init", "-q", "-b", "main");
        run(repo, "git", "config", "user.email", "fixture@example.com");
        run(repo, "git", "config", "user.name", "Fixture");
        Files.writeString(repo.resolve("widget.txt"), "content\n", StandardCharsets.UTF_8);
        run(repo, "git", "add", "-A");
        run(repo, "git", "commit", "-q", "-m", "initial");
        run(repo, "git", "branch", "under-test");
        branchRef = "under-test";
    }

    @Test
    @DisplayName("a real surefire-reports directory is summed correctly, failures included")
    void summarizesRealSurefireReports() throws Exception {
        // A script that fabricates exactly the report shape a real Surefire run produces — the same
        // "Tests run: N, Failures: N, Errors: N, Skipped: N" line SummaryAssembler (T129) already parses
        // the identical way, reused here rather than a second ad-hoc format.
        String script = "#!/bin/sh\n"
                + "mkdir -p target/surefire-reports\n"
                + "cat > target/surefire-reports/fixture.OneTest.txt <<'EOF'\n"
                + "Tests run: 3, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s -- in fixture.OneTest\n"
                + "EOF\n"
                + "cat > target/surefire-reports/fixture.TwoTest.txt <<'EOF'\n"
                + "Tests run: 2, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 0.01 s -- in fixture.TwoTest\n"
                + "EOF\n"
                + "exit 1\n"; // the overall build fails because a test failed -- realistic for Surefire

        ScriptTestSuiteRunner runner = new ScriptTestSuiteRunner(repo, testCommand(script));

        TestSuiteReport report = runner.run(branchRef);

        assertEquals(5, report.total(), "3 + 2 across the two report files");
        assertEquals(2, report.failed(), "1 failure + 1 error, both count against the suite");
        assertTrue(report.report().contains("2 fast-tier test class report"));
    }

    @Test
    @DisplayName("CR-065: a passing suite's real executedBehaviors are derived from Surefire's own XML "
            + "testcase records")
    void executedBehaviorsDerivedFromRealXmlReportsWhenSuitePasses() throws Exception {
        String script = "#!/bin/sh\n"
                + "mkdir -p target/surefire-reports\n"
                + "cat > target/surefire-reports/fixture.OneTest.txt <<'EOF'\n"
                + "Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.01 s -- in fixture.OneTest\n"
                + "EOF\n"
                + "cat > target/surefire-reports/TEST-fixture.OneTest.xml <<'EOF'\n"
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<testsuite name=\"fixture.OneTest\" tests=\"2\" failures=\"0\" errors=\"0\" skipped=\"0\">\n"
                + "  <testcase name=\"firstBehavior\" classname=\"fixture.OneTest\" time=\"0.001\"/>\n"
                + "  <testcase name=\"secondBehavior\" classname=\"fixture.OneTest\" time=\"0.001\"/>\n"
                + "</testsuite>\n"
                + "EOF\n"
                + "exit 0\n";

        ScriptTestSuiteRunner runner = new ScriptTestSuiteRunner(repo, testCommand(script));

        TestSuiteReport report = runner.run(branchRef);

        assertEquals(2, report.total());
        assertEquals(0, report.failed());
        assertEquals(List.of("fixture.OneTest.firstBehavior", "fixture.OneTest.secondBehavior"),
                report.executedBehaviors(),
                "each real <testcase> must become 'ClassName.methodName' in executedBehaviors");
        assertTrue(report.report().contains("2 tests run"),
                "the existing human-readable summary must still be present, unchanged, alongside the new "
                        + "list -- CR-065 adds, it does not replace");
    }

    @Test
    @DisplayName("CR-065 REGRESSION: a FAILING suite reports NO executedBehaviors -- only a genuinely "
            + "passing run's own tests are ever named as verified")
    void failingSuiteReportsNoExecutedBehaviors() throws Exception {
        String script = "#!/bin/sh\n"
                + "mkdir -p target/surefire-reports\n"
                + "cat > target/surefire-reports/fixture.OneTest.txt <<'EOF'\n"
                + "Tests run: 2, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.01 s -- in fixture.OneTest\n"
                + "EOF\n"
                + "cat > target/surefire-reports/TEST-fixture.OneTest.xml <<'EOF'\n"
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<testsuite name=\"fixture.OneTest\" tests=\"2\" failures=\"1\" errors=\"0\" skipped=\"0\">\n"
                + "  <testcase name=\"passingBehavior\" classname=\"fixture.OneTest\" time=\"0.001\"/>\n"
                + "  <testcase name=\"failingBehavior\" classname=\"fixture.OneTest\" time=\"0.001\"><failure "
                + "message=\"boom\"/></testcase>\n"
                + "</testsuite>\n"
                + "EOF\n"
                + "exit 1\n";

        ScriptTestSuiteRunner runner = new ScriptTestSuiteRunner(repo, testCommand(script));

        TestSuiteReport report = runner.run(branchRef);

        assertEquals(1, report.failed());
        assertTrue(report.executedBehaviors().isEmpty(),
                "a failing suite's own passing test must not be claimed as a verified behaviour -- "
                        + "TestingEngine never reaches its succeeded path here anyway, but the report "
                        + "itself must not pre-populate a list nothing will ever read as trustworthy");
    }

    @Test
    @DisplayName("no surefire-reports directory produced: reported as a real, non-invented outcome")
    void noReportsDirectoryIsReportedHonestly() throws Exception {
        String script = "#!/bin/sh\nexit 1\n"; // fails before ever reaching Surefire

        ScriptTestSuiteRunner runner = new ScriptTestSuiteRunner(repo, testCommand(script));

        TestSuiteReport report = runner.run(branchRef);

        assertTrue(report.report().contains("no surefire-reports directory was produced"),
                "must not invent a passing or a specific failing count when nothing was ever reported: "
                        + report.report());
        assertEquals(1, report.total());
        assertEquals(1, report.failed());
    }

    /** An absolute path, deliberately — the test command runs with the checked-out WORKTREE as its
     * working directory, a different directory from {@link #repo}, so the script itself must be found by
     * an absolute path rather than committed into the branch under test. */
    private List<String> testCommand(String scriptBody) throws Exception {
        Path script = Files.createTempFile("script-", ".sh");
        Files.writeString(script, scriptBody, StandardCharsets.UTF_8);
        return List.of("sh", script.toAbsolutePath().toString());
    }

    private static void run(Path dir, String... argv) throws Exception {
        ProcessRunner.Result result = ProcessRunner.run(List.of(argv), dir, java.time.Duration.ofSeconds(30));
        if (!result.succeeded()) {
            throw new IllegalStateException("fixture command failed: " + List.of(argv) + ": " + result.stderr());
        }
    }
}
