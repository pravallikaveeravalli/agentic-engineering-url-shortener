package agentic.shortener.orchestration.summary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates {@code docs/ENGINEERING-SUMMARY.md} by running {@link SummaryAssembler} against the repository
 * root and writing its {@link EngineeringSummary#toMarkdown()} rendering to disk. Task T129.
 *
 * <p>Run once, deliberately, from the repository root (not wired into {@code scripts/ci.sh}): the committed
 * {@code docs/ENGINEERING-SUMMARY.md} is a snapshot produced by exactly this code path, not hand-written —
 * regenerating it is {@code java -cp target/classes agentic.shortener.orchestration.summary.SummaryAssemblerMain}.
 */
public final class SummaryAssemblerMain {

    private SummaryAssemblerMain() {
    }

    public static void main(String[] args) throws IOException {
        Path repoRoot = args.length > 0 ? Paths.get(args[0]).toAbsolutePath() : Paths.get("").toAbsolutePath();
        EngineeringSummary summary = new SummaryAssembler(repoRoot).assemble();
        Path target = repoRoot.resolve("docs/ENGINEERING-SUMMARY.md");
        Files.writeString(target, summary.toMarkdown());
        System.out.println("wrote " + target + " (" + summary.allClaims().size() + " traced claims)");
    }
}
