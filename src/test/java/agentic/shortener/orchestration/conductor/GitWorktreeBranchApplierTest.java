package agentic.shortener.orchestration.conductor;

import agentic.shortener.orchestration.executor.ai.stages.BranchApplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T131c, RED-FIRST. FR-ORC-014, ADR-004.
 *
 * <p>Real {@code git} subprocesses, against a small fixture repository under a fresh {@code @TempDir} —
 * never the real project repository. The buildability check is injected as {@code true}/{@code false} (real
 * POSIX utilities, not a shell string) so these tests exercise every branch of {@link
 * GitWorktreeBranchApplier}'s own logic without depending on this project's Maven toolchain.
 */
@DisplayName("T131c — GitWorktreeBranchApplier: real git, isolated worktrees, disclosed branch fate")
class GitWorktreeBranchApplierTest {

    @TempDir
    Path repo;

    @BeforeEach
    void setUp() throws Exception {
        run(repo, "git", "init", "-q", "-b", "main");
        run(repo, "git", "config", "user.email", "fixture@example.com");
        run(repo, "git", "config", "user.name", "Fixture");
        Files.writeString(repo.resolve("widget.txt"), "line one\nline two\n", StandardCharsets.UTF_8);
        run(repo, "git", "add", "-A");
        run(repo, "git", "commit", "-q", "-m", "initial");
    }

    @Test
    @DisplayName("a clean patch that builds: branch created, commit present, ApplyResult.buildable() true")
    void cleanPatchThatBuildsSucceeds() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("true"));

        String patch = """
                --- a/widget.txt
                +++ b/widget.txt
                @@ -1,2 +1,3 @@
                 line one
                 line two
                +line three
                """;

        BranchApplier.ApplyResult result = applier.apply("T900", patch);

        assertTrue(result.buildable(), "expected a clean, buildable patch to succeed: " + result.detail());
        assertNotNull(result.branchRef());
        assertTrue(result.branchRef().startsWith("ds-run/T900-"));

        String committedContent = run(repo, "git", "show", result.branchRef() + ":widget.txt").stdout();
        assertEquals("line one\nline two\nline three", committedContent);

        String log = run(repo, "git", "log", result.branchRef(), "-1", "--format=%s").stdout();
        assertTrue(log.contains("T900"), "expected the commit message to name the task: " + log);
    }

    @Test
    @DisplayName("a patch that does not apply: no branch left behind, buildable() false, branchRef null")
    void patchThatDoesNotApplyLeavesNoBranch() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("true"));

        String garbage = "this is not a unified diff at all\nnonsense\n";

        BranchApplier.ApplyResult result = applier.apply("T901", garbage);

        assertFalse(result.buildable());
        assertNull(result.branchRef(), "an unapplied patch must not report a branch as if something landed");
        assertTrue(result.detail().toLowerCase().contains("apply"),
                "expected the failure detail to say the patch did not apply: " + result.detail());

        String branches = run(repo, "git", "branch", "--list", "ds-run/T901-*").stdout();
        assertTrue(branches.isBlank(), "a failed apply must not leave a branch behind, found: " + branches);
    }

    @Test
    @DisplayName("a clean patch that fails the build check: branch KEPT, buildable() false")
    void patchThatAppliesButFailsBuildKeepsTheBranch() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("false"));

        String patch = """
                --- a/widget.txt
                +++ b/widget.txt
                @@ -1,2 +1,3 @@
                 line one
                 line two
                +a change that applies cleanly but "fails to build" per the injected check
                """;

        BranchApplier.ApplyResult result = applier.apply("T902", patch);

        assertFalse(result.buildable());
        assertNotNull(result.branchRef(), "a patch that applied and committed keeps its branch even when "
                + "the build check fails — a reviewer may want to see exactly what failed");
        assertTrue(result.detail().toLowerCase().contains("compile")
                || result.detail().toLowerCase().contains("build"),
                "expected the failure detail to describe a build failure: " + result.detail());

        String branches = run(repo, "git", "branch", "--list", result.branchRef()).stdout();
        assertFalse(branches.isBlank(), "expected the branch to still exist: " + result.branchRef());
    }

    private static ProcessRunner.Result run(Path dir, String... argv) throws Exception {
        ProcessRunner.Result result = ProcessRunner.run(List.of(argv), dir, java.time.Duration.ofSeconds(30));
        if (!result.succeeded()) {
            throw new IllegalStateException("fixture command failed: " + List.of(argv) + ": " + result.stderr());
        }
        return new ProcessRunner.Result(result.exitCode(), result.stdout().strip(), result.stderr());
    }
}
