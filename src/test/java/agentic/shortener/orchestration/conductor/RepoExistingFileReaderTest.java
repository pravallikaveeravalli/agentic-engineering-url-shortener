package agentic.shortener.orchestration.conductor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real, filesystem-backed half of the S7 existing-file fix: {@link Conductor}'s own {@code
 * withExistingFileContent} calls this to turn the repository-relative paths S6's design names into real
 * content. Two things this class alone is responsible for, neither of which the AI-facing classes should
 * ever need to know about: a requested path that does not exist yet means "new file", not an error (S6 may
 * legitimately mis-list a file, or the model may ask about a file the design intends to create); and a path
 * is never allowed to escape {@code repoRoot} (a design output is AI-authored content, effectively untrusted
 * input to this reader).
 */
@DisplayName("RepoExistingFileReader -- the real filesystem read behind the S7 existing-file fix")
class RepoExistingFileReaderTest {

    @TempDir
    Path repoRoot;

    @Test
    @DisplayName("a real existing file's exact content is returned, keyed by the requested relative path")
    void readsRealFileContentByRelativePath() throws Exception {
        Files.writeString(repoRoot.resolve("pom.xml"), "<project>exact-content</project>",
                StandardCharsets.UTF_8);

        Map<String, String> content = new RepoExistingFileReader(repoRoot).read(List.of("pom.xml"));

        assertEquals(Map.of("pom.xml", "<project>exact-content</project>"), content);
    }

    @Test
    @DisplayName("a requested path that does not exist is simply omitted, never an error -- it means 'new file'")
    void nonExistentPathIsOmittedNotAnError() throws Exception {
        Map<String, String> content = new RepoExistingFileReader(repoRoot).read(List.of("does-not-exist.txt"));

        assertTrue(content.isEmpty());
    }

    @Test
    @DisplayName("a mix of existing and non-existent paths returns only the existing ones")
    void mixOfExistingAndMissingReturnsOnlyExisting() throws Exception {
        Files.writeString(repoRoot.resolve("real.txt"), "here", StandardCharsets.UTF_8);

        Map<String, String> content =
                new RepoExistingFileReader(repoRoot).read(List.of("real.txt", "missing.txt"));

        assertEquals(Map.of("real.txt", "here"), content);
    }

    @Test
    @DisplayName("NEGATIVE: a path attempting to escape repoRoot via '../' is refused, not read")
    void pathTraversalOutsideRepoRootIsRefused() throws Exception {
        Path outside = repoRoot.getParent().resolve("outside-secret-" + System.nanoTime() + ".txt");
        Files.writeString(outside, "must never be read", StandardCharsets.UTF_8);
        try {
            Map<String, String> content = new RepoExistingFileReader(repoRoot)
                    .read(List.of("../" + outside.getFileName()));

            assertFalse(content.containsValue("must never be read"),
                    "a path-traversal attempt must never surface content from outside repoRoot");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("an empty request list returns an empty map")
    void emptyRequestReturnsEmptyMap() throws Exception {
        Map<String, String> content = new RepoExistingFileReader(repoRoot).read(List.of());

        assertTrue(content.isEmpty());
    }
}
