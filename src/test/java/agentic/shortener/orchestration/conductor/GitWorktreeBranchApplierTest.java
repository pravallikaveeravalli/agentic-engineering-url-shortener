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
 * Task T131c, RED-FIRST. FR-ORC-014, ADR-004. CR-060: real {@code git} subprocesses, against a small fixture
 * repository under a fresh {@code @TempDir} — never the real project repository. The buildability check is
 * injected as a real POSIX utility or {@code javac} (never a shell string standing in for one) so these tests
 * exercise every branch of {@link GitWorktreeBranchApplier}'s own logic, including a REAL compile, without
 * depending on this project's own Maven toolchain.
 *
 * <p>CR-060's own reason for existing: real, live attempts (docs/evidence/ds-a's attempts 14, 15, 17, 19)
 * repeatedly showed a unified diff — asking the model to invent exact line numbers for a file it never
 * opened — corrupt-patching even once shown real file content. This class now proves the REPLACEMENT
 * mechanism (JSON change set: CREATE full-content, or EDIT search/replace pairs, applied deterministically)
 * genuinely works, including the one capability that was broken: editing an EXISTING file correctly.
 */
@DisplayName("T131c — GitWorktreeBranchApplier: real git, isolated worktrees, disclosed branch fate (CR-060: "
        + "JSON change set, search/replace, not a unified diff)")
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
    @DisplayName("REGRESSION: a clean CREATE change set that builds: branch created, commit present, "
            + "ApplyResult.buildable() true")
    void cleanCreateThatBuildsSucceeds() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("true"));

        String changeSet = "{\"files\":[{\"path\":\"newfile.txt\",\"action\":\"CREATE\","
                + "\"content\":\"brand new content\"}]}";

        BranchApplier.ApplyResult result = applier.apply("T900", changeSet);

        assertTrue(result.buildable(), "expected a clean, buildable CREATE to succeed: " + result.detail());
        assertNotNull(result.branchRef());
        assertTrue(result.branchRef().startsWith("ds-run/T900-"));

        String committedContent = run(repo, "git", "show", result.branchRef() + ":newfile.txt").stdout();
        assertEquals("brand new content", committedContent);

        String log = run(repo, "git", "log", result.branchRef(), "-1", "--format=%s").stdout();
        assertTrue(log.contains("T900"), "expected the commit message to name the task: " + log);
    }

    @Test
    @DisplayName("THE KEY RESULT: a change set editing an EXISTING pom.xml-shaped file AND an EXISTING .java "
            + "file, via search/replace, applies correctly and the result genuinely compiles")
    void editingTwoExistingFilesAppliesCorrectlyAndCompiles() throws Exception {
        // A fixture shaped like the real pom.xml's own spring-boot-maven-plugin block -- the exact kind
        // of existing-file edit that corrupt-patched as a unified diff in attempts 14/15/17/19.
        String originalPom = "<project>\n"
                + "  <build>\n"
                + "    <plugins>\n"
                + "      <plugin>\n"
                + "        <groupId>org.example</groupId>\n"
                + "        <artifactId>example-maven-plugin</artifactId>\n"
                + "      </plugin>\n"
                + "    </plugins>\n"
                + "  </build>\n"
                + "</project>\n";
        Files.writeString(repo.resolve("pom.xml"), originalPom, StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("Foo.java"),
                "public class Foo {\n    public static void main(String[] args) {\n"
                        + "        System.out.println(\"v1\");\n    }\n}\n", StandardCharsets.UTF_8);
        run(repo, "git", "add", "-A");
        run(repo, "git", "commit", "-q", "-m", "add pom.xml and Foo.java fixtures");

        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("javac", "Foo.java"));

        String changeSet = "{\"files\":["
                + "{\"path\":\"pom.xml\",\"action\":\"EDIT\",\"edits\":["
                + "{\"search\":\"        <artifactId>example-maven-plugin</artifactId>\\n      </plugin>\","
                + "\"replace\":\"        <artifactId>example-maven-plugin</artifactId>\\n        "
                + "<executions>\\n          <execution>\\n            <goals>\\n              "
                + "<goal>build-info</goal>\\n            </goals>\\n          </execution>\\n        "
                + "</executions>\\n      </plugin>\"}]},"
                + "{\"path\":\"Foo.java\",\"action\":\"EDIT\",\"edits\":["
                + "{\"search\":\"System.out.println(\\\"v1\\\");\","
                + "\"replace\":\"System.out.println(\\\"v2\\\");\"}]}"
                + "]}";

        BranchApplier.ApplyResult result = applier.apply("T903", changeSet);

        assertTrue(result.buildable(), "expected the edited Foo.java to genuinely compile via javac: "
                + result.detail());
        assertNotNull(result.branchRef());

        String committedPom = run(repo, "git", "show", result.branchRef() + ":pom.xml").stdout();
        String expectedPom = "<project>\n"
                + "  <build>\n"
                + "    <plugins>\n"
                + "      <plugin>\n"
                + "        <groupId>org.example</groupId>\n"
                + "        <artifactId>example-maven-plugin</artifactId>\n"
                + "        <executions>\n"
                + "          <execution>\n"
                + "            <goals>\n"
                + "              <goal>build-info</goal>\n"
                + "            </goals>\n"
                + "          </execution>\n"
                + "        </executions>\n"
                + "      </plugin>\n"
                + "    </plugins>\n"
                + "  </build>\n"
                + "</project>";
        assertEquals(expectedPom, committedPom, "the committed pom.xml must be exactly the original content "
                + "with the search text replaced -- no corruption, no fabricated content");

        String committedJava = run(repo, "git", "show", result.branchRef() + ":Foo.java").stdout();
        assertTrue(committedJava.contains("System.out.println(\"v2\");"),
                "the committed Foo.java must contain the real replacement");
        assertFalse(committedJava.contains("\"v1\""),
                "the original text must be genuinely gone, not merely appended to");
    }

    @Test
    @DisplayName("NEGATIVE: an EDIT whose search text does not match the real current content is refused, "
            + "no branch left")
    void editWithNonMatchingSearchTextIsRefused() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("true"));

        String changeSet = "{\"files\":[{\"path\":\"widget.txt\",\"action\":\"EDIT\",\"edits\":["
                + "{\"search\":\"this text does not exist in widget.txt\",\"replace\":\"anything\"}]}]}";

        BranchApplier.ApplyResult result = applier.apply("T904", changeSet);

        assertFalse(result.buildable());
        assertNull(result.branchRef(), "a change set that did not apply must not report a branch as if "
                + "something landed");
        assertTrue(result.detail().toLowerCase().contains("apply"),
                "expected the failure detail to say the change did not apply: " + result.detail());
        String branches = run(repo, "git", "branch", "--list", "ds-run/T904-*").stdout();
        assertTrue(branches.isBlank(), "a failed apply must not leave a branch behind, found: " + branches);
    }

    @Test
    @DisplayName("NEGATIVE: an EDIT whose search text matches MORE THAN ONCE is refused as ambiguous, never "
            + "applied to the wrong occurrence")
    void editWithAmbiguousSearchTextIsRefused() throws Exception {
        Files.writeString(repo.resolve("dup.txt"), "same line\nsame line\n", StandardCharsets.UTF_8);
        run(repo, "git", "add", "-A");
        run(repo, "git", "commit", "-q", "-m", "add dup.txt");

        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("true"));
        String changeSet = "{\"files\":[{\"path\":\"dup.txt\",\"action\":\"EDIT\",\"edits\":["
                + "{\"search\":\"same line\",\"replace\":\"changed line\"}]}]}";

        BranchApplier.ApplyResult result = applier.apply("T905", changeSet);

        assertFalse(result.buildable());
        assertNull(result.branchRef());
        assertTrue(result.detail().contains("2 time(s)"),
                "expected the failure to name the real occurrence count: " + result.detail());
    }

    @Test
    @DisplayName("NEGATIVE: a CREATE naming a file that already exists is refused -- never silently "
            + "overwritten")
    void createOnExistingFileIsRefused() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("true"));

        String changeSet = "{\"files\":[{\"path\":\"widget.txt\",\"action\":\"CREATE\","
                + "\"content\":\"replacement content\"}]}";

        BranchApplier.ApplyResult result = applier.apply("T906", changeSet);

        assertFalse(result.buildable());
        assertNull(result.branchRef());
        assertTrue(result.detail().toLowerCase().contains("already exists"), result.detail());
    }

    @Test
    @DisplayName("NEGATIVE: an EDIT naming a file that does not exist is refused")
    void editOnMissingFileIsRefused() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("true"));

        String changeSet = "{\"files\":[{\"path\":\"does-not-exist.txt\",\"action\":\"EDIT\",\"edits\":["
                + "{\"search\":\"anything\",\"replace\":\"anything else\"}]}]}";

        BranchApplier.ApplyResult result = applier.apply("T907", changeSet);

        assertFalse(result.buildable());
        assertNull(result.branchRef());
        assertTrue(result.detail().toLowerCase().contains("does not exist"), result.detail());
    }

    @Test
    @DisplayName("NEGATIVE: a path attempting to escape the worktree is refused")
    void pathTraversalIsRefused() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("true"));

        String changeSet = "{\"files\":[{\"path\":\"../outside.txt\",\"action\":\"CREATE\","
                + "\"content\":\"must never be written\"}]}";

        BranchApplier.ApplyResult result = applier.apply("T908", changeSet);

        assertFalse(result.buildable());
        assertNull(result.branchRef());
        assertTrue(result.detail().toLowerCase().contains("outside"), result.detail());
    }

    @Test
    @DisplayName("a change set that is not valid JSON is refused, no branch left")
    void garbageChangeSetLeavesNoBranch() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("true"));

        String garbage = "this is not JSON at all\nnonsense\n";

        BranchApplier.ApplyResult result = applier.apply("T901", garbage);

        assertFalse(result.buildable());
        assertNull(result.branchRef(), "an unapplied change set must not report a branch as if something "
                + "landed");
        assertTrue(result.detail().toLowerCase().contains("apply"),
                "expected the failure detail to say the change set did not apply: " + result.detail());

        String branches = run(repo, "git", "branch", "--list", "ds-run/T901-*").stdout();
        assertTrue(branches.isBlank(), "a failed apply must not leave a branch behind, found: " + branches);
    }

    @Test
    @DisplayName("a clean change set that fails the build check: branch KEPT, buildable() false")
    void changeSetThatAppliesButFailsBuildKeepsTheBranch() throws Exception {
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main", List.of("false"));

        String changeSet = "{\"files\":[{\"path\":\"widget.txt\",\"action\":\"EDIT\",\"edits\":["
                + "{\"search\":\"line two\",\"replace\":\"a change that applies cleanly but 'fails to "
                + "build' per the injected check\"}]}]}";

        BranchApplier.ApplyResult result = applier.apply("T902", changeSet);

        assertFalse(result.buildable());
        assertNotNull(result.branchRef(), "a change that applied and committed keeps its branch even when "
                + "the build check fails — a reviewer may want to see exactly what failed");
        assertTrue(result.detail().toLowerCase().contains("compile")
                || result.detail().toLowerCase().contains("build"),
                "expected the failure detail to describe a build failure: " + result.detail());

        String branches = run(repo, "git", "branch", "--list", result.branchRef()).stdout();
        assertFalse(branches.isBlank(), "expected the branch to still exist: " + result.branchRef());
    }

    @Test
    @DisplayName("CR-068: a build failure's own real diagnostic text reaches the failure detail even when "
            + "the injected build command writes it to STDOUT, never stderr -- exactly how Maven's real "
            + "compile errors print under -q")
    void buildFailureDetailCapturesStdoutNotOnlyStderr() throws Exception {
        // A shell command writing its own diagnostic to stdout and exiting non-zero -- the same shape a
        // real `./scripts/build.sh -q compile` failure has (confirmed empirically, this same turn: a real
        // Maven compile error under -q writes ~2.4KB to stdout and ZERO bytes to stderr).
        GitWorktreeBranchApplier applier = new GitWorktreeBranchApplier(repo, "main",
                List.of("sh", "-c", "echo 'cannot find symbol: class ThisDoesNotExist' && exit 1"));

        String changeSet = "{\"files\":[{\"path\":\"widget.txt\",\"action\":\"EDIT\",\"edits\":["
                + "{\"search\":\"line two\",\"replace\":\"a real compile-error shape, on stdout only\"}]}]}";

        BranchApplier.ApplyResult result = applier.apply("T909", changeSet);

        assertFalse(result.buildable());
        assertTrue(result.detail().contains("cannot find symbol: class ThisDoesNotExist"),
                "the real diagnostic text (written to stdout by the injected command, exactly as Maven's "
                        + "own compile errors are under -q) must reach the failure detail, not be silently "
                        + "dropped because it wasn't on stderr: " + result.detail());
    }

    private static ProcessRunner.Result run(Path dir, String... argv) throws Exception {
        ProcessRunner.Result result = ProcessRunner.run(List.of(argv), dir, java.time.Duration.ofSeconds(30));
        if (!result.succeeded()) {
            throw new IllegalStateException("fixture command failed: " + List.of(argv) + ": " + result.stderr());
        }
        return new ProcessRunner.Result(result.exitCode(), result.stdout().strip(), result.stderr());
    }
}
