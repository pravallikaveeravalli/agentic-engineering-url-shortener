package agentic.shortener.orchestration.summary;

import agentic.shortener.audit.TaskEntry;
import agentic.shortener.audit.TraceabilityReport;
import agentic.shortener.audit.TraceabilityReporter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministically assembles an {@link EngineeringSummary} from recorded repository evidence only — no
 * text here is composed by this class; every claim is either a fact read directly off a file (a task's own
 * artifact path, an ADR's own Status line and option headers, a test report's own summary line) or the bare
 * existence of a named evidence file. Task T129. FR-ORC-026, CN-001, ADR-010.
 *
 * <p><strong>Why "creativity is a liability" shapes every method here</strong>: each section method only
 * ever emits a claim once it has independently confirmed, by reading the filesystem, that the fact is true
 * right now — a done task whose claimed artifact does not exist produces no claim rather than an optimistic
 * one; a missing {@code target/failsafe-reports} directory produces no integration-tier claim rather than
 * one that assumes the tier ran. {@code evidencePath()} on every claim this class produces is a path this
 * class has already resolved and confirmed exists under {@code repoRoot} — {@code SummaryAssemblerTest} and
 * {@code SummaryAssemblerIT} both re-check that independently, which is the actual enforcement of T129's
 * Validate clause; this class earns that pass by never emitting a claim it has not itself verified.
 */
public final class SummaryAssembler {

    private static final String SPEC_MD = "specs/001-agentic-sdlc-url-shortener/spec.md";
    private static final String TASKS_MD = "specs/001-agentic-sdlc-url-shortener/tasks.md";
    private static final String FEATURE_DIR = "specs/001-agentic-sdlc-url-shortener";

    private static final Pattern STATUS_HEADING = Pattern.compile("^##\\s+Status\\s*$");
    private static final Pattern DECISION_HEADING = Pattern.compile("^##\\s+Decision\\s*$");
    private static final Pattern OPTION_HEADING = Pattern.compile(
            "^###\\s+Option\\s+([A-Z])\\s*[—-]\\s*(.+?)\\s*$");
    private static final Pattern CHOSEN_OPTION = Pattern.compile("\\*\\*Option\\s+([A-Z])\\.\\*\\*");
    private static final Pattern TEST_SUMMARY_LINE = Pattern.compile(
            "Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");

    private final Path repoRoot;

    public SummaryAssembler(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    public EngineeringSummary assemble() {
        return new EngineeringSummary(
                whatWasBuilt(),
                decisionsAndRejectedAlternatives(),
                executedValidationAndResults(),
                residualRisksAndLimitations(),
                aiAssistedProcess());
    }

    // ==============================================================================================
    // What was built
    // ==============================================================================================

    private List<EvidencedClaim> whatWasBuilt() {
        Path specMd = repoRoot.resolve(SPEC_MD);
        Path tasksMd = repoRoot.resolve(TASKS_MD);
        if (!Files.exists(specMd) || !Files.exists(tasksMd)) {
            return List.of();
        }
        TraceabilityReporter reporter = new TraceabilityReporter(repoRoot);
        TraceabilityReport report = reporter.report(specMd, tasksMd);
        Set<String> tasksWithMissingArtifacts = report.orphanImplementations().stream()
                .map(finding -> finding.substring(0, finding.indexOf(':')))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<EvidencedClaim> claims = new ArrayList<>();
        for (TaskEntry task : reporter.parseTasks(tasksMd)) {
            if (!task.done() || tasksWithMissingArtifacts.contains(task.taskId())) {
                continue;
            }
            for (String rawPath : task.artifactPaths()) {
                String resolved = resolveRepoRelative(rawPath);
                if (resolved == null) {
                    continue;
                }
                claims.add(new EvidencedClaim(task.taskId() + " delivered " + rawPath, resolved));
            }
        }
        return claims;
    }

    /** A task artifact path is written relative to the repo root OR the feature directory (both real
     * conventions this project uses); resolves to whichever repo-relative form actually exists, or
     * {@code null} if neither does. */
    private String resolveRepoRelative(String rawPath) {
        if (Files.exists(repoRoot.resolve(rawPath))) {
            return rawPath;
        }
        String underFeature = FEATURE_DIR + "/" + rawPath;
        if (Files.exists(repoRoot.resolve(underFeature))) {
            return underFeature;
        }
        return null;
    }

    // ==============================================================================================
    // Decisions and rejected alternatives
    // ==============================================================================================

    private List<EvidencedClaim> decisionsAndRejectedAlternatives() {
        Path adrDir = repoRoot.resolve("docs/governance/adr");
        if (!Files.isDirectory(adrDir)) {
            return List.of();
        }
        List<Path> adrFiles;
        try (Stream<Path> list = Files.list(adrDir)) {
            adrFiles = list.filter(p -> p.getFileName().toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equalsIgnoreCase("README.md"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<EvidencedClaim> claims = new ArrayList<>();
        for (Path adrFile : adrFiles) {
            List<String> lines = readLines(adrFile);
            String evidencePath = repoRoot.relativize(adrFile).toString();

            String status = firstNonBlankAfter(lines, STATUS_HEADING);
            if (status != null) {
                claims.add(new EvidencedClaim(adrFile.getFileName() + " — Status: " + status, evidencePath));
            }

            Map<String, String> options = optionHeadings(lines);
            String chosen = firstMatchAfter(lines, DECISION_HEADING, CHOSEN_OPTION);
            if (chosen != null && options.containsKey(chosen)) {
                for (Map.Entry<String, String> option : options.entrySet()) {
                    if (option.getKey().equals(chosen)) {
                        continue;
                    }
                    claims.add(new EvidencedClaim(
                            adrFile.getFileName() + " rejected Option " + option.getKey() + " ("
                                    + option.getValue() + ") in favour of Option " + chosen + " ("
                                    + options.get(chosen) + ")",
                            evidencePath));
                }
            }
        }
        return claims;
    }

    private static String firstNonBlankAfter(List<String> lines, Pattern heading) {
        for (int i = 0; i < lines.size(); i++) {
            if (heading.matcher(lines.get(i).strip()).matches()) {
                for (int j = i + 1; j < lines.size(); j++) {
                    String candidate = lines.get(j).strip();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static String firstMatchAfter(List<String> lines, Pattern heading, Pattern target) {
        boolean afterHeading = false;
        for (String line : lines) {
            if (heading.matcher(line.strip()).matches()) {
                afterHeading = true;
                continue;
            }
            if (afterHeading && (line.startsWith("## ") || line.startsWith("# "))) {
                break;
            }
            if (afterHeading) {
                Matcher m = target.matcher(line);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return null;
    }

    private static Map<String, String> optionHeadings(List<String> lines) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher m = OPTION_HEADING.matcher(line.strip());
            if (m.matches()) {
                options.put(m.group(1), m.group(2));
            }
        }
        return options;
    }

    // ==============================================================================================
    // Executed validation and results
    // ==============================================================================================

    private List<EvidencedClaim> executedValidationAndResults() {
        List<EvidencedClaim> claims = new ArrayList<>();
        Set<String> currentTestClassNames = existingTestClassNames();
        addTierClaim(claims, "fast tier", "target/surefire-reports", false, currentTestClassNames);
        addTierClaim(claims, "integration tier", "target/failsafe-reports", true, currentTestClassNames);

        Path mttrMethod = repoRoot.resolve("docs/evidence/mttr-method.md");
        if (Files.exists(mttrMethod)) {
            claims.add(new EvidencedClaim(
                    "every measured figure this project reports is labelled MEASURED-with-conditions or "
                            + "PROPOSED-with-basis rather than a bare number (T121), and the MTTR method's "
                            + "declared population and exclusions are recorded",
                    "docs/evidence/mttr-method.md"));
        }
        return claims;
    }

    private void addTierClaim(List<EvidencedClaim> claims, String tierName, String relativeDir,
            boolean integrationTier, Set<String> currentTestClassNames) {
        Path dir = repoRoot.resolve(relativeDir);
        TestTierSummary summary = summarizeTier(dir, integrationTier, currentTestClassNames);
        if (summary == null) {
            return;
        }
        claims.add(new EvidencedClaim(
                tierName + ": " + summary.testsRun() + " tests run, " + summary.failures() + " failures, "
                        + summary.errors() + " errors, " + summary.skipped() + " skipped",
                relativeDir));
    }

    private record TestTierSummary(int testsRun, int failures, int errors, int skipped) {}

    /**
     * Sums only reports whose class both (a) still exists under {@code src/test/java} and (b) belongs to
     * this tier under {@code pom.xml}'s own Surefire-exclude / Failsafe-include pattern ({@code *IT} /
     * {@code *IntegrationTest} run under Failsafe, everything else under Surefire). Without this a report
     * left behind by a deleted or renamed test class — or one run standalone with an explicit
     * {@code -Dtest=} that bypassed the tier's own filter — silently keeps contributing its old numbers to
     * every summary generated afterwards, no matter how long ago it ran or whether it still reflects
     * anything real. Found exactly this way, against the real repository: a since-deleted throwaway
     * debugging test and two deliberate red-phase sabotage captures were still sitting in {@code target/}
     * and would otherwise have been reported as current failures.
     */
    private TestTierSummary summarizeTier(Path dir, boolean integrationTier, Set<String> currentTestClassNames) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        List<Path> reportFiles;
        try (Stream<Path> list = Files.list(dir)) {
            reportFiles = list.filter(p -> p.getFileName().toString().endsWith(".txt")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int totalRun = 0;
        int totalFailures = 0;
        int totalErrors = 0;
        int totalSkipped = 0;
        boolean any = false;
        for (Path report : reportFiles) {
            // A @Nested test class reports under "Outer$Nested"; pom.xml's own Surefire/Failsafe patterns,
            // and the source file that must exist, are both keyed on the OUTER (top-level, file-named)
            // class, so that is what both checks below use — not the report's own full name.
            String topLevelClassName = topLevelClassNameOf(simpleClassNameOf(report.getFileName().toString()));
            if (!currentTestClassNames.contains(topLevelClassName)) {
                continue;
            }
            if (isIntegrationTierClassName(topLevelClassName) != integrationTier) {
                continue;
            }
            for (String line : readLines(report)) {
                Matcher m = TEST_SUMMARY_LINE.matcher(line);
                if (m.find()) {
                    any = true;
                    totalRun += Integer.parseInt(m.group(1));
                    totalFailures += Integer.parseInt(m.group(2));
                    totalErrors += Integer.parseInt(m.group(3));
                    totalSkipped += Integer.parseInt(m.group(4));
                    break;
                }
            }
        }
        return any ? new TestTierSummary(totalRun, totalFailures, totalErrors, totalSkipped) : null;
    }

    private static String simpleClassNameOf(String reportFileName) {
        String withoutExtension = reportFileName.endsWith(".txt")
                ? reportFileName.substring(0, reportFileName.length() - ".txt".length())
                : reportFileName;
        int lastDot = withoutExtension.lastIndexOf('.');
        return lastDot >= 0 ? withoutExtension.substring(lastDot + 1) : withoutExtension;
    }

    /** Mirrors {@code pom.xml}'s own Failsafe {@code <include>} patterns exactly. */
    private static boolean isIntegrationTierClassName(String simpleClassName) {
        return simpleClassName.endsWith("IT") || simpleClassName.endsWith("IntegrationTest");
    }

    /** {@code "Outer$Nested"} (a JUnit {@code @Nested} class's own report name) becomes {@code "Outer"} —
     * the top-level, file-named class the nested class actually lives inside. A name with no {@code $}
     * returns unchanged. */
    private static String topLevelClassNameOf(String simpleClassName) {
        int dollar = simpleClassName.indexOf('$');
        return dollar >= 0 ? simpleClassName.substring(0, dollar) : simpleClassName;
    }

    private Set<String> existingTestClassNames() {
        Path testRoot = repoRoot.resolve("src/test/java");
        if (!Files.isDirectory(testRoot)) {
            return Set.of();
        }
        try (Stream<Path> walk = Files.walk(testRoot)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> p.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ==============================================================================================
    // Residual risks and limitations
    // ==============================================================================================

    private List<EvidencedClaim> residualRisksAndLimitations() {
        List<EvidencedClaim> claims = new ArrayList<>();
        addFileClaimIfExists(claims,
                "indefinitely deferred scope is recorded in the delivery backlog",
                "docs/delivery/backlog.md");
        addFileClaimIfExists(claims,
                "scheduled baseline omissions, each with a named closing task, are recorded",
                "docs/delivery/baseline-omissions.md");
        return claims;
    }

    private void addFileClaimIfExists(List<EvidencedClaim> claims, String text, String relativePath) {
        if (Files.exists(repoRoot.resolve(relativePath))) {
            claims.add(new EvidencedClaim(text, relativePath));
        }
    }

    // ==============================================================================================
    // AI-assisted process, including deviations from plan
    // ==============================================================================================

    private List<EvidencedClaim> aiAssistedProcess() {
        List<EvidencedClaim> claims = new ArrayList<>();
        claims.addAll(listDirectoryAsClaims(
                "docs/evidence/ai-demos",
                name -> "a live AI-stage demonstration is recorded: " + name));
        claims.addAll(listDirectoryAsClaims(
                "docs/evidence/red-phase",
                name -> "a deliberate red-phase capture is recorded (a real failure, then the fix): "
                        + name));
        return claims;
    }

    private List<EvidencedClaim> listDirectoryAsClaims(String relativeDir,
            java.util.function.Function<String, String> textFor) {
        Path dir = repoRoot.resolve(relativeDir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<EvidencedClaim> claims = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            claims.add(new EvidencedClaim(textFor.apply(name), relativeDir + "/" + name));
        }
        return claims;
    }

    // ==============================================================================================

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + path, e);
        }
    }
}
