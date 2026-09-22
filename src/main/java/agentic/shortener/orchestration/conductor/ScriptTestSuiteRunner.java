package agentic.shortener.orchestration.conductor;

import agentic.shortener.orchestration.executor.deterministic.TestSuiteReport;
import agentic.shortener.orchestration.executor.deterministic.TestSuiteRunner;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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
        List<String> executedBehaviors = new ArrayList<>();

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

            // CR-065: the .txt siblings read above never carry individual test names, only per-class
            // aggregate counts -- the real, per-test record lives in Surefire's own .xml reports instead.
            // Read only when the suite genuinely passed: a failing run never reaches TestingEngine's own
            // succeeded path (report.failed() > 0 routes back to S7 first), so there is no reader for this
            // list in that case, and reading it anyway would report behaviours from files that may include
            // a real failure's own test alongside its passing siblings.
            if (totalFailedOrErrored == 0) {
                executedBehaviors.addAll(executedBehaviorsFromXmlReports(reportsDir));
            }
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

        return new TestSuiteReport(totalRun, totalFailedOrErrored, report.toString(), executedBehaviors);
    }

    /** CR-065: {@code <testcase name="..." classname="...">} is Surefire's own real, per-test record —
     * every entry here genuinely ran and passed, since this is only called once the suite's own aggregate
     * failure count is zero. Malformed or unreadable XML for one file is skipped rather than failing the
     * whole report — the aggregate pass/fail count (already computed from the .txt siblings) is the
     * authoritative signal TestingEngine itself acts on; this list is supplementary. */
    private static List<String> executedBehaviorsFromXmlReports(Path reportsDir) throws Exception {
        List<Path> xmlFiles;
        try (Stream<Path> list = Files.list(reportsDir)) {
            xmlFiles = list.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList();
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        List<String> behaviors = new ArrayList<>();
        for (Path xmlFile : xmlFiles) {
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                NodeList testcases = builder.parse(xmlFile.toFile()).getElementsByTagName("testcase");
                for (int i = 0; i < testcases.getLength(); i++) {
                    Element testcase = (Element) testcases.item(i);
                    String className = testcase.getAttribute("classname");
                    String methodName = testcase.getAttribute("name");
                    if (!className.isBlank() && !methodName.isBlank()) {
                        behaviors.add(className + "." + methodName);
                    }
                }
            } catch (Exception ignored) {
                // Best-effort: one malformed report never invalidates the real, already-computed pass/fail
                // count this class' own summary is built from.
            }
        }
        return behaviors;
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
