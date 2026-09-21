package agentic.shortener.audit;

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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Mechanically generates a {@link TraceabilityReport} over the ten-link chain — requirement → scenario →
 * design → ADR → task → code → test → validation → documentation → evidence — by parsing {@code spec.md}'s
 * Traceability matrix and {@code tasks.md}, and cross-referencing both against the real files on disk.
 * Task T123. FR-ORC-027, {@code POL-TRC-001}, ADR-006.
 *
 * <p><strong>Why this does not trust {@code spec.md}'s own Task/Test/Evidence columns for {@code
 * FR-ORC-*}</strong>: those columns were seeded at Specify time as the literal placeholder text {@code
 * *Tasks stage*} / {@code *Implement stage*}, and — correctly, per this project's own change-control
 * discipline — {@code spec.md} is never hand-edited afterward to fill them in; that would be exactly the
 * "analysis MUST NOT be reconstructed after the change" DS-B forbids, applied to the spec instead of an
 * impact analysis. So the real Task/Test/Evidence state lives in {@code tasks.md} and on disk, and this
 * class reads it from there — which is what makes the report <em>mechanical</em> (T123's Validate clause)
 * rather than a hand-transcription of one document into another.
 *
 * <p>{@code FR-URL-*} rows are the opposite case: they already carry real Task/Test/Evidence text in {@code
 * spec.md} (written once, when the application plane was delivered, before the orchestration plane's own
 * task list existed) — this class does not re-derive those; {@link #report} cross-references every row the
 * same way regardless, so a stale {@code FR-URL-*} cell would surface as an orphan exactly like a
 * never-filled {@code FR-ORC-*} one.
 */
public final class TraceabilityReporter {

    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|\\s*$");
    private static final Pattern REQUIREMENT_ROW_START = Pattern.compile("^\\|\\s*(FR-[A-Za-z0-9-]+)");

    private static final Pattern TASK_TITLE = Pattern.compile(
            "^- \\[([ xX])] (T\\d+[a-z]?)\\b(.*)$");
    private static final Pattern REQ_FIELD = Pattern.compile(
            "\\*\\*Req\\*\\*:\\s*(.*?)\\s*·\\s*\\*\\*Scn\\*\\*:");
    private static final Pattern REQUIREMENT_TOKEN = Pattern.compile(
            "\\b(FR-[A-Za-z]+-\\d+|NFR-[A-Za-z]+-\\d+|PVT-\\d+|CN-\\d+|DF-\\d+|POL-[A-Za-z]+-\\d+|SC-\\d+"
                    + "|KE-\\d+|EC-\\d+|ADR-\\d+[A-Za-z0-9-]*)\\b");
    private static final Pattern BACKTICK_PATH = Pattern.compile("`([^`]+)`");
    private static final Pattern TEST_CLASS_NAME = Pattern.compile("\\b([A-Z][A-Za-z0-9]*(?:Test|IT))\\b");

    /** Where {@code specs/001-agentic-sdlc-url-shortener}-relative artifact paths (e.g. {@code
     * contracts/README.md}, meant relative to the feature directory) also resolve from. */
    private static final String FEATURE_DIR = "specs/001-agentic-sdlc-url-shortener";

    private final Path repoRoot;

    public TraceabilityReporter(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    // ==============================================================================================
    // Parsing
    // ==============================================================================================

    public List<SpecRequirementRow> parseSpecTable(Path specMd) {
        List<String> lines = readLines(specMd);
        List<SpecRequirementRow> rows = new ArrayList<>();
        for (String line : lines) {
            if (!REQUIREMENT_ROW_START.matcher(line).find()) {
                continue;
            }
            Matcher rowMatcher = TABLE_ROW.matcher(line);
            if (!rowMatcher.matches()) {
                continue;
            }
            String[] cells = rowMatcher.group(1).split("\\|", -1);
            if (cells.length < 9) {
                continue;
            }
            rows.add(new SpecRequirementRow(
                    baseRequirementId(cells[0].trim()),
                    cells[1].trim(), cells[2].trim(), cells[3].trim(),
                    cells[4].trim(), cells[5].trim(), cells[6].trim(), cells[7].trim(), cells[8].trim()));
        }
        return rows;
    }

    /** Strips a trailing parenthetical/italic annotation, e.g. {@code "FR-ORC-015 *(RETIRED — CR-032)*"}. */
    private static String baseRequirementId(String cell) {
        Matcher m = Pattern.compile("^(FR-[A-Za-z0-9-]+)").matcher(cell);
        return m.find() ? m.group(1) : cell;
    }

    public List<TaskEntry> parseTasks(Path tasksMd) {
        List<String> lines = readLines(tasksMd);
        List<TaskEntry> tasks = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            Matcher titleMatcher = TASK_TITLE.matcher(lines.get(i));
            if (!titleMatcher.matches()) {
                i++;
                continue;
            }
            boolean done = titleMatcher.group(1).equalsIgnoreCase("x");
            String taskId = titleMatcher.group(2);
            String titleRest = titleMatcher.group(3);

            StringBuilder block = new StringBuilder(lines.get(i));
            int j = i + 1;
            while (j < lines.size() && !TASK_TITLE.matcher(lines.get(j)).matches()
                    && !lines.get(j).startsWith("#") && !lines.get(j).equals("---")) {
                block.append('\n').append(lines.get(j));
                j++;
            }
            String blockText = block.toString();

            List<String> artifactPaths = extractArtifactPaths(titleRest);
            List<String> requirementRefs = extractRequirementRefs(blockText);
            tasks.add(new TaskEntry(taskId, done, artifactPaths, requirementRefs, blockText));
            i = j;
        }
        return tasks;
    }

    /**
     * Backtick-quoted, file-shaped tokens from AFTER the title line's em dash, if it has one. A glob or
     * placeholder token (containing {@code *} or {@code [}, e.g. {@code [X]Repository.java}) is dropped —
     * it names a shape of file, not one this reporter could ever confirm existing or missing.
     */
    private static List<String> extractArtifactPaths(String titleRest) {
        int dash = titleRest.indexOf('—');
        if (dash < 0) {
            return List.of();
        }
        String afterDash = titleRest.substring(dash + 1);
        List<String> paths = new ArrayList<>();
        Matcher m = BACKTICK_PATH.matcher(afterDash);
        while (m.find()) {
            String candidate = m.group(1);
            boolean fileShaped = candidate.contains("/") || candidate.matches(".*\\.[a-zA-Z0-9]{1,5}$");
            boolean isGlobOrPlaceholder = candidate.contains("*") || candidate.contains("[");
            if (fileShaped && !isGlobOrPlaceholder) {
                paths.add(candidate);
            }
        }
        return paths;
    }

    private static List<String> extractRequirementRefs(String blockText) {
        Matcher reqField = REQ_FIELD.matcher(blockText);
        if (!reqField.find()) {
            return List.of();
        }
        String reqText = reqField.group(1);
        Set<String> refs = new LinkedHashSet<>();
        Matcher tokens = REQUIREMENT_TOKEN.matcher(reqText);
        while (tokens.find()) {
            refs.add(tokens.group(1));
        }
        // Free-text justifications this reporter also accepts as "not orphaned", even without a coded id.
        if (reqText.contains("Constitution") || reqText.contains("plan §") || reqText.contains("plan section")) {
            refs.add("(narrative reference: " + reqText.strip() + ")");
        }
        return List.copyOf(refs);
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + path, e);
        }
    }

    // ==============================================================================================
    // Cross-referencing — the mechanical report itself
    // ==============================================================================================

    public TraceabilityReport report(Path specMd, Path tasksMd) {
        List<SpecRequirementRow> specRows = parseSpecTable(specMd);
        List<TaskEntry> tasks = parseTasks(tasksMd);

        Set<String> matrixRequirementIds = new LinkedHashSet<>();
        for (SpecRequirementRow row : specRows) {
            matrixRequirementIds.add(row.requirementId());
        }

        Map<String, List<TaskEntry>> tasksByRequirement = new LinkedHashMap<>();
        for (TaskEntry task : tasks) {
            for (String ref : task.requirementRefs()) {
                tasksByRequirement.computeIfAbsent(ref, k -> new ArrayList<>()).add(task);
            }
        }

        // 1) Orphan requirements: no task references this matrix row's id at all.
        List<String> orphanRequirements = new TreeSet<>(matrixRequirementIds).stream()
                .filter(id -> !tasksByRequirement.containsKey(id))
                .map(id -> id + ": no task in tasks.md references it")
                .toList();

        // 2) Orphan tasks: nothing recognizable in the Req field.
        List<String> orphanTasks = tasks.stream()
                .filter(t -> t.requirementRefs().isEmpty())
                .map(t -> t.taskId() + ": Req field names no requirement, policy, ADR, or constitution "
                        + "reference")
                .toList();

        // 3) Orphan implementations: done, but NONE of the title-line artifact paths exist. A task naming
        //    several paths (an "or" between alternatives, or a set of files delivered together) is not an
        //    orphan merely because one renamed or unbuilt member is missing — only if the whole claim is
        //    empty. This is deliberately permissive in that one direction; T124's own reporting still
        //    names each individually-missing path so a reviewer sees the partial claim, not just the verdict.
        List<String> orphanImplementations = new ArrayList<>();
        for (TaskEntry task : tasks) {
            if (!task.done()) {
                continue;
            }
            List<String> repoPaths = task.artifactPaths().stream()
                    .filter(TraceabilityReporter::looksLikeRepoPath)
                    .toList();
            if (repoPaths.isEmpty()) {
                continue;
            }
            boolean anyExists = repoPaths.stream().anyMatch(this::existsUnderEitherRoot);
            if (!anyExists) {
                orphanImplementations.add(task.taskId() + ": claims " + repoPaths + ", none of which exist "
                        + "on disk");
            }
        }

        // 4) Orphan tests: done, names a *Test/*IT class in its block, but no such file exists anywhere
        //    under src/test/java. A token preceded by `*` (a Maven -Dtest glob, e.g. `*RepositoryIT`) is a
        //    SUFFIX to match against, not one exact class name — `-Dtest=*RepositoryIT` legitimately
        //    matches several real classes at once, and is exactly as real a claim as a literal name.
        Set<String> existingTestClassNames = existingTestClassNames();
        List<String> orphanTests = new ArrayList<>();
        for (TaskEntry task : tasks) {
            if (!task.done()) {
                continue;
            }
            Matcher m = TEST_CLASS_NAME.matcher(task.rawBlock());
            Set<String> mentioned = new LinkedHashSet<>();
            Set<String> mentionedAsSuffixGlob = new LinkedHashSet<>();
            while (m.find()) {
                boolean isGlobSuffix = m.start() > 0 && task.rawBlock().charAt(m.start() - 1) == '*';
                (isGlobSuffix ? mentionedAsSuffixGlob : mentioned).add(m.group(1));
            }
            for (String className : mentioned) {
                if (!existingTestClassNames.contains(className)) {
                    orphanTests.add(task.taskId() + ": mentions " + className
                            + ", which is not a .java file anywhere under src/test/java");
                }
            }
            for (String suffix : mentionedAsSuffixGlob) {
                boolean anyMatch = existingTestClassNames.stream().anyMatch(name -> name.endsWith(suffix));
                if (!anyMatch) {
                    orphanTests.add(task.taskId() + ": mentions *" + suffix
                            + ", which matches no .java file anywhere under src/test/java");
                }
            }
        }

        return new TraceabilityReport(orphanRequirements, orphanTasks, orphanImplementations, orphanTests);
    }

    private static boolean looksLikeRepoPath(String candidate) {
        return candidate.contains("/") && !candidate.startsWith("http");
    }

    /** A task's artifact path is written relative to the repo root OR the feature directory — both are
     * real conventions this project uses (e.g. {@code contracts/README.md} means {@code
     * specs/001-agentic-sdlc-url-shortener/contracts/README.md}), so both are checked before a path is
     * reported missing. */
    private boolean existsUnderEitherRoot(String path) {
        return Files.exists(repoRoot.resolve(path)) || Files.exists(repoRoot.resolve(FEATURE_DIR).resolve(path));
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
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
