package agentic.shortener.orchestration.conductor;

import agentic.shortener.orchestration.executor.deterministic.TestSuiteReport;
import agentic.shortener.orchestration.executor.deterministic.TestSuiteRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The real {@link TestSuiteRunner} T071's own javadoc calls for — "the production wiring still runs the
 * actual suite." Task T131c. FR-ORC-029.
 *
 * <p>Checks out {@link GitWorktreeBranchApplier}'s branch into its own isolated {@code git worktree} — the
 * same isolation reasoning, independently: this process's own working tree is never switched to the branch
 * under test. Runs the <strong>fast tier only</strong> ({@code scripts/build.sh test}) — container-free and
 * fast enough for a live demonstration run's own {@code PVT-016} threshold; the full suite (including the
 * PostgreSQL-backed integration tier) is what {@code scripts/ci.sh} already proves for THIS repository on
 * every commit, and re-running it per demonstration node would make a single scenario run take as long as a
 * full CI pass for no additional signal about the branch under test specifically.
 */
public final class ScriptTestSuiteRunner implements TestSuiteRunner {

    private static final Duration CHECKOUT_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration TEST_TIMEOUT = Duration.ofMinutes(10);

    private static final Pattern SUMMARY_LINE = Pattern.compile(
            "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");

    private static final List<String> DEFAULT_TEST_COMMAND =
            List.of("./scripts/build.sh", "-q", "-DfailIfNoTests=false", "test");

    private final Path repoRoot;
    private final List<String> testCommand;

    public ScriptTestSuiteRunner(Path repoRoot) {
        this(repoRoot, DEFAULT_TEST_COMMAND);
    }

    /** @param testCommand the fast-tier test invocation, run inside the checked-out worktree — injectable
     *                     for the same reason {@link GitWorktreeBranchApplier}'s build command is */
    public ScriptTestSuiteRunner(Path repoRoot, List<String> testCommand) {
        this.repoRoot = Objects.requireNonNull(repoRoot, "repoRoot");
        this.testCommand = List.copyOf(Objects.requireNonNull(testCommand, "testCommand"));
        if (this.testCommand.isEmpty()) {
            throw new IllegalArgumentException("testCommand must not be empty");
        }
    }

    @Override
    public TestSuiteReport run(String branchRef) throws Exception {
        Objects.requireNonNull(branchRef, "branchRef");
        Path worktree = Files.createTempDirectory("conductor-s8-");
        try {
            ProcessRunner.Result checkout = ProcessRunner.run(
                    List.of("git", "worktree", "add", "--detach", worktree.toString(), branchRef),
                    repoRoot, CHECKOUT_TIMEOUT);
            if (!checkout.succeeded()) {
                throw new IllegalStateException("could not check out " + branchRef + " for testing: "
                        + checkout.stderr());
            }

            ProcessRunner.Result test = ProcessRunner.run(testCommand, worktree, TEST_TIMEOUT);

            return summarize(worktree, test);
        } finally {
            removeWorktree(worktree);
        }
    }

    private TestSuiteReport summarize(Path worktree, ProcessRunner.Result testRun) throws Exception {
        Path reportsDir = worktree.resolve("target/surefire-reports");
        int totalRun = 0;
        int totalFailedOrErrored = 0;
        StringBuilder report = new StringBuilder();

        if (Files.isDirectory(reportsDir)) {
            List<Path> reportFiles;
            try (Stream<Path> list = Files.list(reportsDir)) {
                reportFiles = list.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted().toList();
            }
            for (Path file : reportFiles) {
                for (String line : Files.readAllLines(file)) {
                    Matcher m = SUMMARY_LINE.matcher(line);
                    if (m.find()) {
                        totalRun += Integer.parseInt(m.group(1));
                        totalFailedOrErrored += Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(3));
                        break;
                    }
                }
            }
            report.append(reportFiles.size()).append(" fast-tier test class report(s), ")
                    .append(totalRun).append(" tests run, ").append(totalFailedOrErrored).append(" failed.");
        } else {
            // The build itself never ran a single test class (e.g. compile failure before Surefire) — the
            // subprocess's own exit code decides whether this is UNAVAILABLE-retryable or a real 0-test
            // report; TestingEngine's own contract only accepts a genuine TestSuiteReport, so a build that
            // never reached Surefire is reported as a report of zero, letting TestingEngine's INVALID_INPUT
            // branch on total==0/failed==0 look identical to a real "nothing ran" state rather than this
            // class inventing a different outcome shape.
            report.append("no surefire-reports directory was produced (exit ").append(testRun.exitCode())
                    .append("); stderr: ").append(truncate(testRun.stderr()));
            if (!testRun.succeeded()) {
                totalFailedOrErrored = 1;
                totalRun = 1;
            }
        }

        return new TestSuiteReport(totalRun, totalFailedOrErrored, report.toString());
    }

    private void removeWorktree(Path worktree) {
        try {
            ProcessRunner.run(List.of("git", "worktree", "remove", worktree.toString(), "--force"),
                    repoRoot, CHECKOUT_TIMEOUT);
        } catch (Exception ignored) {
            // best-effort cleanup
        }
        try (Stream<Path> walk = Files.exists(worktree) ? Files.walk(worktree) : Stream.<Path>empty()) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort cleanup; git worktree remove already did the real work
                }
            });
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    private static String truncate(String text) {
        String oneLine = text.strip().replace('\n', ' ');
        return oneLine.length() <= 300 ? oneLine : oneLine.substring(0, 300) + "...(truncated)";
    }
}
