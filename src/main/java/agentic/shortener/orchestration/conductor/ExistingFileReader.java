package agentic.shortener.orchestration.conductor;

import java.util.List;
import java.util.Map;

/**
 * Reads the current, real content of named existing repository files, for {@link Conductor} to inject into
 * S7's own {@code StageInput} before dispatch. Task (S7 existing-file fix).
 *
 * <p><strong>Why this lives in the orchestration, not in {@code ImplementationAiExecutor}.</strong> S7's own
 * "AI authors blind" design (T073e) deliberately gives the model no tool access and no file I/O of its own
 * — a real, live finding (this class's own javadoc predecessor, {@code ImplementationAiExecutor}'s own
 * comment on why) showed that letting an agentic CLI use tools headlessly can leave it stuck in an
 * uncompletable tool-use loop. That constraint is preserved exactly: the model still never reads or writes a
 * file itself. What changes is that the model no longer has to work <em>blind</em> for a file that already
 * exists — the orchestration reads it first, and hands the content to the model as plain prompt text, the
 * same way {@code "design"} or {@code "task"} content already arrives. {@link ImplementationAiExecutor}
 * stays a pure function over its {@code StageInput}; nothing about this port changes that.
 *
 * <p>A missing key for a requested path is not an error — it means the path does not exist yet (a genuinely
 * new file), which the model is told to create rather than diff.
 */
@FunctionalInterface
public interface ExistingFileReader {

    /**
     * @param relativePaths repository-relative paths (as named by S6's own design output); never null,
     *                      may be empty
     * @return the real, current content of every path that exists, keyed by the exact path requested. A
     *         path that does not exist on disk is simply absent from the result — never an exception, since
     *         "does not exist yet" is the expected, common case for a genuinely new file
     */
    Map<String, String> read(List<String> relativePaths) throws Exception;
}
