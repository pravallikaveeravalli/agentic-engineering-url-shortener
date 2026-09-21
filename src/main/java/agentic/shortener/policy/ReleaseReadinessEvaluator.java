package agentic.shortener.policy;

import agentic.shortener.audit.TraceabilityReport;
import agentic.shortener.audit.TraceabilityReporter;
import agentic.shortener.orchestration.executor.deterministic.ReadinessEvaluator;
import agentic.shortener.orchestration.executor.deterministic.ReadinessVerdict;
import agentic.shortener.persistence.ConnectionSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * T109's real implementation of {@link ReadinessEvaluator} — the nine constitutional blocking conditions
 * (Constitution §Governance), each evaluated for real against the repository, the test-run reports, and the
 * real {@link PolicySetEvaluator}. FR-ORC-025, ADR-011.
 *
 * <p><strong>Readiness MUST NOT be self-certified by an agent</strong> (T109's own Guard clause) — building
 * this evaluator is not that. {@link agentic.shortener.orchestration.executor.deterministic
 * .ReleaseReadinessEngine} (T071) already appends its own {@code NOT_A_DECISION} sentence to every report,
 * both ready and not; this class produces the evaluation the release owner reads before deciding, never the
 * decision itself.
 *
 * <p><strong>Conditions 1 and 2 share one input.</strong> This project has no separately mechanized
 * "principle check" registry distinct from the twelve {@code policy-set-1.1.0} checks — Constitution VI's
 * own principles are what the policy set operationalizes. Condition 1 is scoped narrowly to this
 * repository's own constitutional-boundary tests (the {@code agentic.shortener.arch} package — dependency
 * direction, credential boundary, analytics port bypass); condition 2 is the full policy-set verdict.
 * Declared here rather than implied, matching this class's own subject matter.
 */
public final class ReleaseReadinessEvaluator implements ReadinessEvaluator {

    private static final Path SPEC_MD = Path.of("specs/001-agentic-sdlc-url-shortener/spec.md");
    private static final Path TASKS_MD = Path.of("specs/001-agentic-sdlc-url-shortener/tasks.md");

    private static final List<String> ARCH_TEST_CLASSES = List.of(
            "DependencyDirectionTest", "DependencyDirectionFalsifiabilityTest", "CredentialBoundaryTest",
            "AnalyticsPortBypassTest");

    private static final Pattern SUREFIRE_SUMMARY = Pattern.compile(
            "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");

    private static final Pattern BACKTICK_PATH = Pattern.compile("`([^`]+)`");

    private final ConnectionSource connections;
    private final Path repoRoot;
    private final Clock clock;

    public ReleaseReadinessEvaluator(ConnectionSource connections, Path repoRoot, Clock clock) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.repoRoot = Objects.requireNonNull(repoRoot, "repoRoot");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ReadinessVerdict evaluate(Map<String, String> artifacts) throws Exception {
        PolicySetEvaluator policyEvaluator = new PolicySetEvaluator(connections, repoRoot, clock);
        var policyVerdict = policyEvaluator.evaluate(Map.of());

        List<ReadinessCondition> conditions = new ArrayList<>();
        conditions.add(condition1ConstitutionalBoundaryTests());
        conditions.add(condition2PolicyChecks(policyVerdict));
        conditions.add(condition3GateDecisionsMaterialized());
        conditions.add(condition4RequiredTestsExecuted());
        conditions.add(condition5SecurityScansRun());
        conditions.add(condition6TraceabilityComplete());
        conditions.add(condition7DocumentationReflectsDelivered());
        conditions.add(condition8ResidualRisksDisclosed());
        conditions.add(condition9EvidenceVerifiable());

        ReleaseReadinessResult result = new ReleaseReadinessResult(conditions);
        return new ReadinessVerdict(result.ready(), result.summary());
    }

    // ==============================================================================================
    // Condition 1 — a mandatory PRINCIPLE check is FAIL with no approved exception
    // ==============================================================================================

    private ReadinessCondition condition1ConstitutionalBoundaryTests() {
        List<String> failing = ARCH_TEST_CLASSES.stream().filter(this::surefireReportShowsFailure).toList();
        List<String> missing = ARCH_TEST_CLASSES.stream()
                .filter(name -> !surefireReportExists(name)).toList();
        if (!missing.isEmpty()) {
            return new ReadinessCondition(1, "mandatory principle check FAIL with no approved exception",
                    false, "constitutional-boundary test(s) never executed: " + missing);
        }
        if (!failing.isEmpty()) {
            return new ReadinessCondition(1, "mandatory principle check FAIL with no approved exception",
                    false, "constitutional-boundary test(s) failing: " + failing);
        }
        return new ReadinessCondition(1, "mandatory principle check FAIL with no approved exception", true,
                "all constitutional-boundary tests (" + ARCH_TEST_CLASSES + ") executed and passing");
    }

    // ==============================================================================================
    // Condition 2 — a mandatory POLICY check is FAIL, or an exception is unapproved or expired
    // ==============================================================================================

    private ReadinessCondition condition2PolicyChecks(
            agentic.shortener.orchestration.executor.deterministic.PolicyVerdict policyVerdict) {
        return new ReadinessCondition(2, "mandatory policy check FAIL, or an exception unapproved/expired",
                !policyVerdict.blocking(), policyVerdict.report());
    }

    // ==============================================================================================
    // Condition 3 — a mandatory human gate lacks a recorded outcome, or is not materialized
    // ==============================================================================================

    private ReadinessCondition condition3GateDecisionsMaterialized() {
        Path dir = repoRoot.resolve("docs/governance/gate-decisions");
        if (!Files.isDirectory(dir)) {
            return new ReadinessCondition(3,
                    "mandatory human gate lacks a recorded outcome or repository materialization", false,
                    "no gate-decisions directory at " + dir);
        }
        List<String> incomplete = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, 1)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".md")).toList()) {
                if (!Files.readString(file).toLowerCase().contains("outcome")) {
                    incomplete.add(file.getFileName().toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!incomplete.isEmpty()) {
            return new ReadinessCondition(3,
                    "mandatory human gate lacks a recorded outcome or repository materialization", false,
                    "gate-decision record(s) missing a recorded outcome: " + incomplete);
        }
        return new ReadinessCondition(3,
                "mandatory human gate lacks a recorded outcome or repository materialization", true,
                "every filed gate-decision record states its outcome (DECLARED LIMITATION: structural "
                        + "completeness of what is filed, not a live cross-check against every "
                        + "gate_decision database row across every run)");
    }

    // ==============================================================================================
    // Condition 4 — required tests have not been executed, or executed tests fail without acceptance
    // ==============================================================================================

    private ReadinessCondition condition4RequiredTestsExecuted() {
        Path surefire = repoRoot.resolve("target/surefire-reports");
        Path failsafe = repoRoot.resolve("target/failsafe-reports");
        boolean surefireRan = hasReports(surefire);
        boolean failsafeRan = hasReports(failsafe);
        if (!surefireRan || !failsafeRan) {
            return new ReadinessCondition(4,
                    "required tests not executed, or executed tests failing without recorded acceptance",
                    false, "unit tests executed=" + surefireRan + ", integration tests executed=" + failsafeRan);
        }
        List<String> failing = new ArrayList<>();
        failing.addAll(reportsWithFailures(surefire));
        failing.addAll(reportsWithFailures(failsafe));
        if (!failing.isEmpty()) {
            return new ReadinessCondition(4,
                    "required tests not executed, or executed tests failing without recorded acceptance",
                    false, "test report(s) showing a failure or error, with no recorded acceptance "
                            + "mechanism in this project: " + failing);
        }
        return new ReadinessCondition(4,
                "required tests not executed, or executed tests failing without recorded acceptance", true,
                "both unit and integration tiers executed; zero failures or errors across all reports");
    }

    // ==============================================================================================
    // Condition 5 — dependency vulnerability or secret scanning not run, or unaddressed findings
    // ==============================================================================================

    private ReadinessCondition condition5SecurityScansRun() {
        Path telemetry = repoRoot.resolve("target/telemetry");
        boolean telemetryScanned = Files.isDirectory(telemetry) && hasAnyFile(telemetry);
        Path inventory = repoRoot.resolve("target/dependency-list.txt");
        boolean dependencyScanned = Files.exists(inventory);
        if (!telemetryScanned || !dependencyScanned) {
            return new ReadinessCondition(5,
                    "dependency vulnerability or secret scanning not run, or has unaddressed findings",
                    false, "secret scan over captured telemetry run=" + telemetryScanned
                            + ", dependency inventory present=" + dependencyScanned);
        }
        return new ReadinessCondition(5,
                "dependency vulnerability or secret scanning not run, or has unaddressed findings", true,
                "captured telemetry scanned, dependency inventory present (DECLARED LIMITATION: no live "
                        + "CVE feed integrated, see PolicySetEvaluator's POL-SEC-003)");
    }

    // ==============================================================================================
    // Condition 6 — traceability is incomplete for any delivered requirement
    // ==============================================================================================

    private ReadinessCondition condition6TraceabilityComplete() {
        // SPEC_MD/TASKS_MD are relative, and Files.readAllLines resolves a relative Path against the JVM's
        // working directory, not against repoRoot — resolving here first is what lets this evaluator be
        // pointed at a repository other than the real one (a test fixture) without silently reading the
        // real spec.md/tasks.md instead.
        TraceabilityReport report = new TraceabilityReporter(repoRoot)
                .report(repoRoot.resolve(SPEC_MD), repoRoot.resolve(TASKS_MD));
        return new ReadinessCondition(6, "traceability incomplete for any delivered requirement",
                report.isClean(), report.isClean() ? "zero traceability orphans (T124)"
                        : "orphans found: " + report.orphanRequirements().size() + " requirement(s), "
                                + report.orphanTasks().size() + " task(s), "
                                + report.orphanImplementations().size() + " implementation(s), "
                                + report.orphanTests().size() + " test(s)");
    }

    // ==============================================================================================
    // Condition 7 — documentation does not reflect delivered behavior
    // ==============================================================================================

    private ReadinessCondition condition7DocumentationReflectsDelivered() {
        Path readme = repoRoot.resolve("README.md");
        boolean exists = Files.exists(readme);
        if (!exists) {
            return new ReadinessCondition(7, "documentation does not reflect delivered behavior", false,
                    "no README.md at repository root (DECLARED LIMITATION: this checks README.md's "
                            + "presence and non-blankness only — not that its content actually matches "
                            + "delivered behaviour, which T126 is the task for)");
        }
        boolean nonBlank;
        try {
            nonBlank = !Files.readString(readme).isBlank();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ReadinessCondition(7, "documentation does not reflect delivered behavior", nonBlank,
                nonBlank ? "README.md present and non-blank (DECLARED LIMITATION: presence only, not "
                        + "content-matches-behaviour verification)"
                        : "README.md exists but is blank");
    }

    // ==============================================================================================
    // Condition 8 — residual risks and known limitations are not disclosed
    // ==============================================================================================

    private ReadinessCondition condition8ResidualRisksDisclosed() {
        Path limitations = repoRoot.resolve("docs/LIMITATIONS.md");
        boolean exists = Files.exists(limitations);
        if (!exists) {
            return new ReadinessCondition(8, "residual risks and known limitations are not disclosed",
                    false, "no docs/LIMITATIONS.md (T147 is the task that creates it)");
        }
        boolean nonBlank;
        try {
            nonBlank = !Files.readString(limitations).isBlank();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ReadinessCondition(8, "residual risks and known limitations are not disclosed", nonBlank,
                nonBlank ? "docs/LIMITATIONS.md present and non-blank" : "docs/LIMITATIONS.md exists but is "
                        + "blank");
    }

    // ==============================================================================================
    // Condition 9 — any evidence presented is unverifiable, or demonstration presented as production
    // ==============================================================================================

    /**
     * EC-028: a completed stage's evidence artifact, missing or unreadable at readiness evaluation, blocks
     * and names the artifact — never treated as absent-and-therefore-not-applicable (the same {@code
     * EC-025} default-deny family {@code POL-AUD-001} already applies). Every backtick-quoted, file-shaped
     * (has an extension) reference under {@code docs/evidence/} in {@code spec.md}'s Traceability matrix
     * Evidence column is checked individually — a directory-only reference (e.g. {@code `docs/evidence/`})
     * falls back to the directory's own presence and non-emptiness, since it names no specific file to be
     * missing or unreadable.
     */
    private ReadinessCondition condition9EvidenceVerifiable() {
        List<agentic.shortener.audit.SpecRequirementRow> rows;
        try {
            rows = new TraceabilityReporter(repoRoot).parseSpecTable(repoRoot.resolve(SPEC_MD));
        } catch (Exception e) {
            return new ReadinessCondition(9,
                    "evidence unverifiable, or demonstration evidence presented as production measurement",
                    false, "could not parse spec.md's Traceability matrix to find evidence references: " + e);
        }

        List<String> missingOrUnreadable = new ArrayList<>();
        boolean anyFileReference = false;
        for (var row : rows) {
            Matcher m = BACKTICK_PATH.matcher(row.evidenceCell());
            while (m.find()) {
                String candidate = m.group(1);
                if (!candidate.startsWith("docs/evidence/") || !candidate.matches(".*\\.[a-zA-Z0-9]{1,6}$")) {
                    continue;
                }
                anyFileReference = true;
                Path file = repoRoot.resolve(candidate);
                if (!Files.exists(file)) {
                    missingOrUnreadable.add(row.requirementId() + "'s evidence file " + candidate
                            + " does not exist");
                    continue;
                }
                try {
                    if (Files.readString(file).isBlank()) {
                        missingOrUnreadable.add(row.requirementId() + "'s evidence file " + candidate
                                + " is unreadable (blank)");
                    }
                } catch (IOException e) {
                    missingOrUnreadable.add(row.requirementId() + "'s evidence file " + candidate
                            + " is unreadable: " + e.getMessage());
                }
            }
        }

        if (!missingOrUnreadable.isEmpty()) {
            return new ReadinessCondition(9,
                    "evidence unverifiable, or demonstration evidence presented as production measurement",
                    false, "evidence artifact(s) missing or unreadable: " + missingOrUnreadable);
        }
        if (anyFileReference) {
            return new ReadinessCondition(9,
                    "evidence unverifiable, or demonstration evidence presented as production measurement",
                    true, "every file-shaped evidence reference in spec.md's Traceability matrix exists "
                            + "and is readable (DECLARED LIMITATION: presence and readability only — "
                            + "MeasurementLabel, T121, structurally prevents an unlabelled figure from "
                            + "RunMetrics specifically, but this condition does not scan file CONTENT for "
                            + "a bare, unlabelled figure written by hand)");
        }

        // Fallback: no row named a specific evidence FILE (every Evidence cell is directory-only, e.g.
        // `docs/evidence/`), so the check falls back to the directory's own presence.
        Path evidence = repoRoot.resolve("docs/evidence");
        boolean exists = Files.isDirectory(evidence) && hasAnyFile(evidence);
        return new ReadinessCondition(9,
                "evidence unverifiable, or demonstration evidence presented as production measurement",
                exists, exists ? "docs/evidence/ present and non-empty; no row named a specific evidence "
                        + "file to check individually"
                        : "no evidence at docs/evidence/, or it is empty, and no row named a specific file");
    }

    // ==============================================================================================

    private boolean hasReports(Path dir) {
        return Files.isDirectory(dir) && hasAnyFile(dir);
    }

    private boolean hasAnyFile(Path dir) {
        try (Stream<Path> walk = Files.walk(dir, 1)) {
            return walk.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean surefireReportExists(String simpleClassName) {
        return findSurefireReport(simpleClassName) != null;
    }

    private boolean surefireReportShowsFailure(String simpleClassName) {
        Path report = findSurefireReport(simpleClassName);
        if (report == null) {
            return false;
        }
        return reportShowsFailure(report);
    }

    private Path findSurefireReport(String simpleClassName) {
        Path dir = repoRoot.resolve("target/surefire-reports");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> walk = Files.walk(dir, 1)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(simpleClassName + ".txt"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> reportsWithFailures(Path dir) {
        List<String> failing = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, 1)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".txt")).toList()) {
                if (reportShowsFailure(file)) {
                    failing.add(file.getFileName().toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return failing;
    }

    private boolean reportShowsFailure(Path report) {
        try {
            String content = Files.readString(report);
            Matcher m = SUREFIRE_SUMMARY.matcher(content);
            while (m.find()) {
                int failures = Integer.parseInt(m.group(2));
                int errors = Integer.parseInt(m.group(3));
                if (failures > 0 || errors > 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
