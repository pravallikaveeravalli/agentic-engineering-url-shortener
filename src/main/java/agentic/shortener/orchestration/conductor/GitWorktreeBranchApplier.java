package agentic.shortener.orchestration.conductor;

import agentic.shortener.orchestration.executor.ai.stages.BranchApplier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The real {@link BranchApplier} implementation T073e's own live-demo class explicitly left unbuilt —
 * "that production implementation is out of this task's scope" (its own javadoc). Task T131c. FR-ORC-014,
 * ADR-004.
 *
 * <p>Matches T073's own three-way split exactly: <strong>AI authors</strong> the change set (this class
 * receives it already produced, by {@code ImplementationAiExecutor}); <strong>this class applies it</strong>,
 * in an isolated {@code git worktree} rather than the process's own working tree, and only checks that the
 * result <em>compiles</em> — never the real test suite, which stays S8's job exactly as {@code
 * ImplementationAiExecutor}'s own javadoc requires ("S8's real suite is NOT re-run here").
 *
 * <h2>Why a worktree, not the live checkout</h2>
 *
 * <p>This process's own working tree may hold uncommitted work from the session driving it. Applying an
 * AI-authored change there, even briefly, risks corrupting or losing that work. {@code git worktree add}
 * gives a second, fully independent checkout sharing the same object database — the change lands somewhere
 * that can be inspected, built, and torn down without ever touching a file the operator is looking at.
 *
 * <h2>CR-060: search/replace and full-file writes, applied deterministically — never {@code git apply} on a
 * fabricated diff</h2>
 *
 * <p>{@link BranchApplier}'s own javadoc explains why: a unified diff asks the model to invent exact line
 * numbers and context for a file it never opened, which real, live attempts repeatedly produced as a
 * corrupt patch even once shown the real content (CR-055). This class now parses the model's own JSON change
 * set directly — a {@code CREATE} entry is written verbatim; an {@code EDIT} entry's every {@code search}
 * string is required to occur EXACTLY ONCE in the target file's current content (read fresh from the
 * worktree, never assumed from what the model was shown) before being replaced — never fuzzily, never
 * partially. Any mismatch (a missing file, a search string not found, or found more than once) fails the
 * WHOLE change set before anything is committed — the same "apply cleanly or not at all" guarantee {@code git
 * apply} gave, without asking the model to author line-numbered hunks.
 *
 * <h2>What survives a failure, and what does not</h2>
 *
 * <ul>
 *   <li><strong>The change set does not apply</strong> (malformed content, a missing target file, or a
 *       search string that does not match exactly once): the branch that was about to hold it is removed
 *       too — nothing was ever committed to it, so there is nothing worth keeping.
 *   <li><strong>The change set applies but the result does not compile</strong>: the branch is kept (a real
 *       commit exists, and a reviewer may want to see exactly what failed to build), only the worktree
 *       checkout is torn down.
 *   <li><strong>Success</strong>: the branch is kept, the worktree checkout is torn down. {@link
 *       ScriptTestSuiteRunner} checks the same branch back out independently for S8.
 * </ul>
 */
public final class GitWorktreeBranchApplier implements BranchApplier {

    private static final Duration APPLY_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);

    /** The real buildability check for this repository. */
    private static final List<String> DEFAULT_BUILD_COMMAND = List.of("./scripts/build.sh", "-q", "compile");

    private final Path repoRoot;
    private final String baseBranch;
    private final List<String> buildCommand;

    public GitWorktreeBranchApplier(Path repoRoot, String baseBranch) {
        this(repoRoot, baseBranch, DEFAULT_BUILD_COMMAND);
    }

    /** @param buildCommand the buildability check, run inside the worktree — injectable so a test can
     *                      substitute a trivial command against a fixture repo that carries no real build
     *                      tooling, without weakening what production actually runs */
    public GitWorktreeBranchApplier(Path repoRoot, String baseBranch, List<String> buildCommand) {
        this.repoRoot = Objects.requireNonNull(repoRoot, "repoRoot");
        this.baseBranch = Objects.requireNonNull(baseBranch, "baseBranch");
        this.buildCommand = List.copyOf(Objects.requireNonNull(buildCommand, "buildCommand"));
        if (baseBranch.isBlank()) {
            throw new IllegalArgumentException("baseBranch must not be blank");
        }
        if (this.buildCommand.isEmpty()) {
            throw new IllegalArgumentException("buildCommand must not be empty");
        }
    }

    @Override
    public ApplyResult apply(String taskId, String changeSet) throws Exception {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(changeSet, "changeSet");

        String branchName = "ds-run/" + sanitize(taskId) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path worktree = Files.createTempDirectory("conductor-s7-");

        ProcessRunner.Result addResult = ProcessRunner.run(
                List.of("git", "worktree", "add", "-b", branchName, worktree.toString(), baseBranch),
                repoRoot, APPLY_TIMEOUT);
        if (!addResult.succeeded()) {
            return new ApplyResult(false, null, "could not create an isolated worktree for " + branchName
                    + ": " + addResult.stderr());
        }

        try {
            applyChangeSet(worktree, changeSet);
        } catch (Exception e) {
            removeWorktreeAndBranch(worktree, branchName);
            return new ApplyResult(false, null, "change set did not apply cleanly: " + e.getMessage());
        }

        ProcessRunner.Result addAll = ProcessRunner.run(List.of("git", "add", "-A"), worktree, APPLY_TIMEOUT);
        if (!addAll.succeeded()) {
            removeWorktreeAndBranch(worktree, branchName);
            return new ApplyResult(false, null,
                    "git add failed after a clean change-set apply: " + addAll.stderr());
        }
        ProcessRunner.Result commit = ProcessRunner.run(
                List.of("git", "commit", "-m", "S7: " + taskId, "--no-verify"), worktree, APPLY_TIMEOUT);
        if (!commit.succeeded()) {
            removeWorktreeAndBranch(worktree, branchName);
            return new ApplyResult(false, null,
                    "git commit failed after a clean change-set apply: " + commit.stderr());
        }

        ProcessRunner.Result build = ProcessRunner.run(buildCommand, worktree, BUILD_TIMEOUT);
        removeWorktree(worktree);
        if (!build.succeeded()) {
            // Branch kept deliberately — see class javadoc.
            return new ApplyResult(false, branchName, "change set applied and committed on " + branchName
                    + " but does not compile: " + build.stderr());
        }

        return new ApplyResult(true, branchName, "applied, committed on " + branchName + ", compiles cleanly");
    }

    /**
     * CR-060: applies every {@code files} entry of the model's own JSON change set against the real
     * worktree, deterministically. Any single entry's failure (a path outside the worktree, an EDIT whose
     * target file does not exist, a search string that does not match the CURRENT content exactly once)
     * throws, leaving the worktree in a partially-applied state the caller discards entirely — this method
     * never reports partial success. {@link ImplementationAiExecutor#execute} already validated the JSON's
     * own shape before this is ever called; this method re-validates nothing about shape, only about
     * whether the change set is actually TRUE of the real files it names.
     */
    private static void applyChangeSet(Path worktree, String changeSet) throws IOException {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(changeSet);
        } catch (Exception e) {
            throw new IllegalArgumentException("change set is not valid JSON: " + e.getMessage(), e);
        }
        for (JsonNode file : root.get("files")) {
            String path = file.get("path").asText();
            Path resolved = resolveWithinWorktree(worktree, path);
            String action = file.get("action").asText();
            if ("CREATE".equals(action)) {
                if (Files.exists(resolved)) {
                    throw new IllegalArgumentException(
                            "CREATE for '" + path + "' names a file that already exists -- an existing file "
                                    + "must be changed via EDIT, never silently overwritten by CREATE");
                }
                if (resolved.getParent() != null) {
                    Files.createDirectories(resolved.getParent());
                }
                Files.writeString(resolved, file.get("content").asText(), StandardCharsets.UTF_8);
            } else {
                if (!Files.isRegularFile(resolved)) {
                    throw new IllegalArgumentException(
                            "EDIT for '" + path + "' names a file that does not exist in the real worktree "
                                    + "-- it must be created via CREATE, or the design's own "
                                    + "existingFilesToModify list is wrong");
                }
                String content = Files.readString(resolved, StandardCharsets.UTF_8);
                for (JsonNode edit : file.get("edits")) {
                    String search = edit.get("search").asText();
                    String replace = edit.get("replace").asText();
                    int occurrences = countOccurrences(content, search);
                    if (occurrences != 1) {
                        throw new IllegalArgumentException("EDIT for '" + path + "': search text occurs "
                                + occurrences + " time(s) in the real current content (must be exactly 1) -- "
                                + "refused rather than applied ambiguously or speculatively. search: \""
                                + truncateForError(search) + "\"");
                    }
                    content = content.replace(search, replace);
                }
                Files.writeString(resolved, content, StandardCharsets.UTF_8);
            }
        }
    }

    /** The same path-traversal guard {@code RepoExistingFileReader} already applies for reads — a change
     * set is AI-authored content, effectively untrusted input to this writer. */
    private static Path resolveWithinWorktree(Path worktree, String relativePath) {
        Path resolved = worktree.resolve(relativePath).normalize();
        if (!resolved.startsWith(worktree.normalize())) {
            throw new IllegalArgumentException(
                    "path '" + relativePath + "' resolves outside the worktree -- refused");
        }
        return resolved;
    }

    private static int countOccurrences(String haystack, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String truncateForError(String text) {
        String oneLine = text.strip().replace('\n', ' ');
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...(truncated)";
    }

    private void removeWorktreeAndBranch(Path worktree, String branchName) throws Exception {
        removeWorktree(worktree);
        ProcessRunner.run(List.of("git", "branch", "-D", branchName), repoRoot, APPLY_TIMEOUT);
    }

    private void removeWorktree(Path worktree) throws Exception {
        try {
            ProcessRunner.run(List.of("git", "worktree", "remove", worktree.toString(), "--force"),
                    repoRoot, APPLY_TIMEOUT);
        } finally {
            deleteRecursively(worktree);
        }
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup; git worktree remove already did the real work
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private static String sanitize(String taskId) {
        String cleaned = taskId.replaceAll("[^A-Za-z0-9._-]", "-");
        return cleaned.isBlank() ? "task" : cleaned;
    }
}
