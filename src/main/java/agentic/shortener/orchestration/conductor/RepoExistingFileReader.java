package agentic.shortener.orchestration.conductor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The real {@link ExistingFileReader}: reads a named path's real content from the repository's own working
 * tree (the same {@code repoRoot} {@link GitWorktreeBranchApplier} and {@link ScriptTestSuiteRunner} already
 * use — the base branch S7's own change set will eventually be applied against, so the content shown to the
 * model is the same content its EDIT search strings will actually be matched against, CR-060).
 */
public final class RepoExistingFileReader implements ExistingFileReader {

    private final Path repoRoot;

    public RepoExistingFileReader(Path repoRoot) {
        this.repoRoot = Objects.requireNonNull(repoRoot, "repoRoot");
    }

    @Override
    public Map<String, String> read(List<String> relativePaths) throws IOException {
        Objects.requireNonNull(relativePaths, "relativePaths");
        Map<String, String> content = new LinkedHashMap<>();
        for (String relativePath : relativePaths) {
            Path resolved = repoRoot.resolve(relativePath).normalize();
            // Refuses to read outside the repository root even if a design output named a path that
            // tried to escape it -- the model's own text is never trusted as a filesystem boundary.
            if (!resolved.startsWith(repoRoot.normalize())) {
                continue;
            }
            if (Files.isRegularFile(resolved)) {
                content.put(relativePath, Files.readString(resolved, StandardCharsets.UTF_8));
            }
            // A path that does not exist is simply omitted -- the expected shape for a genuinely new
            // file the design named alongside real existing ones.
        }
        return content;
    }
}
