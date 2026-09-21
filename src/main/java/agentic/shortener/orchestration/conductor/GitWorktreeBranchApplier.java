package agentic.shortener.orchestration.conductor;

import agentic.shortener.orchestration.executor.ai.stages.BranchApplier;

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
 * <p>Matches T073's own three-way split exactly: <strong>AI authors</strong> the diff (this class receives
 * it already produced, by {@code ImplementationAiExecutor}); <strong>this class applies it</strong>, in an
 * isolated {@code git worktree} rather than the process's own working tree, and only checks that the result
 * <em>compiles</em> — never the real test suite, which stays S8's job exactly as {@code
 * ImplementationAiExecutor}'s own javadoc requires ("S8's real suite is NOT re-run here").
 *
 * <h2>Why a worktree, not the live checkout</h2>
 *
 * <p>This process's own working tree may hold uncommitted work from the session driving it. Applying an
 * AI-authored patch there, even briefly, risks corrupting or losing that work. {@code git worktree add}
 * gives a second, fully independent checkout sharing the same object database — the patch lands somewhere
 * that can be inspected, built, and torn down without ever touching a file the operator is looking at.
 *
 * <h2>What survives a failure, and what does not</h2>
 *
 * <ul>
 *   <li><strong>The patch does not apply</strong> ({@code git apply} itself fails): the branch that was
 *       about to hold it is removed too — nothing was ever committed to it, so there is nothing worth
 *       keeping.
 *   <li><strong>The patch applies but the result does not compile</strong>: the branch is kept (a real
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
    public ApplyResult apply(String taskId, String patch) throws Exception {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(patch, "patch");

        String branchName = "ds-run/" + sanitize(taskId) + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path worktree = Files.createTempDirectory("conductor-s7-");

        ProcessRunner.Result addResult = ProcessRunner.run(
                List.of("git", "worktree", "add", "-b", branchName, worktree.toString(), baseBranch),
                repoRoot, APPLY_TIMEOUT);
        if (!addResult.succeeded()) {
            return new ApplyResult(false, null, "could not create an isolated worktree for " + branchName
                    + ": " + addResult.stderr());
        }

        Path patchFile = worktree.resolve(".conductor-s7.patch");
        Files.writeString(patchFile, patch, StandardCharsets.UTF_8);

        ProcessRunner.Result applyResult = ProcessRunner.run(
                List.of("git", "apply", "--whitespace=nowarn", patchFile.getFileName().toString()),
                worktree, APPLY_TIMEOUT);
        Files.deleteIfExists(patchFile);
        if (!applyResult.succeeded()) {
            removeWorktreeAndBranch(worktree, branchName);
            return new ApplyResult(false, null, "patch did not apply cleanly: " + applyResult.stderr());
        }

        ProcessRunner.Result addAll = ProcessRunner.run(List.of("git", "add", "-A"), worktree, APPLY_TIMEOUT);
        if (!addAll.succeeded()) {
            removeWorktreeAndBranch(worktree, branchName);
            return new ApplyResult(false, null, "git add failed after a clean patch apply: " + addAll.stderr());
        }
        ProcessRunner.Result commit = ProcessRunner.run(
                List.of("git", "commit", "-m", "S7: " + taskId, "--no-verify"), worktree, APPLY_TIMEOUT);
        if (!commit.succeeded()) {
            removeWorktreeAndBranch(worktree, branchName);
            return new ApplyResult(false, null, "git commit failed after a clean patch apply: " + commit.stderr());
        }

        ProcessRunner.Result build = ProcessRunner.run(buildCommand, worktree, BUILD_TIMEOUT);
        removeWorktree(worktree);
        if (!build.succeeded()) {
            // Branch kept deliberately — see class javadoc.
            return new ApplyResult(false, branchName, "patch applied and committed on " + branchName
                    + " but does not compile: " + build.stderr());
        }

        return new ApplyResult(true, branchName, "applied, committed on " + branchName + ", compiles cleanly");
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
