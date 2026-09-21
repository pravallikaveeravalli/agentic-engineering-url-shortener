package agentic.shortener.orchestration.executor.ai;

import agentic.shortener.orchestration.reliability.MalformedProviderOutputException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The implemented {@link StageAiProvider}: a subprocess invocation of the locally installed,
 * already-authenticated Claude Code CLI in headless mode. Task T073. ADR-004-A1.
 *
 * <pre>claude -p "&lt;stage prompt&gt;" --model &lt;pinned model&gt; --output-format json</pre>
 *
 * <h2>The argv-not-shell rule (assertion 1, the security-critical one)</h2>
 *
 * <p>The stage prompt is <strong>untrusted content originating in submitted requirements</strong>. This
 * class builds the subprocess command as a {@code List<String>} passed straight to
 * {@link ProcessBuilder}'s constructor, which hands the array to the OS's {@code exec} family directly —
 * there is no {@code /bin/sh -c} anywhere in this class, and there must never be one. A shell-string
 * invocation with an interpolated prompt would be a remote-code-execution path from a requirement field.
 * {@code ClaudeCodeCliStageAiProviderTest} proves this with a prompt containing a command substitution and
 * checking that the substitution's side effect never happened — the only proof that actually tests the
 * property, rather than reading the source and trusting it.
 *
 * <h2>The CLI's own JSON shape, verified against a real installed CLI (2026-09-21)</h2>
 *
 * <p>This class's own javadoc originally disclosed an UNVERIFIED assumption — a top-level {@code "model"}
 * field — and asked a reviewer running against a real CLI to check it first. That check has now happened,
 * against the real Claude Code CLI: there is no top-level {@code "model"} field at all. The model that
 * actually answered is named under {@code "modelUsage"}, an object keyed by model id, each entry carrying
 * its own {@code "canonicalModel"} string that restates the same id as a field rather than a key — this
 * class reads {@code canonicalModel} rather than the key itself, since parsing a JSON key as data is more
 * fragile than reading a named field. <strong>Exactly one entry is required</strong>: a response naming
 * zero or more than one model in {@code modelUsage} is refused rather than guessed at, matching FR-ORC-029's
 * "read from the response, never assumed" rule — picking the first of several would be exactly the guess
 * that rule forbids. The answer text is read from a top-level {@code "result"} string field, which the
 * original assumption got right.
 *
 * <p>A response is treated as successful whenever it parses as JSON and carries a resolvable model id and a
 * non-blank {@code "result"}, <strong>regardless of the process's exit code</strong>. T073's five named
 * assertions say nothing about a distinct exit-code-based failure path, and inventing one untested would be
 * scope the task did not ask for.
 *
 * <h2>Stdout and stderr are drained concurrently</h2>
 *
 * <p>Reading one stream fully before starting the other risks a classic {@link ProcessBuilder} deadlock: if
 * the CLI writes enough to the stream not yet being read to fill the OS pipe buffer, it blocks on that
 * write while this process blocks on a read of the other stream, and neither side ever proceeds. Both
 * streams are drained on background threads from the moment the process starts.
 */
public final class ClaudeCodeCliStageAiProvider implements StageAiProvider {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * A generous ceiling against a hung subprocess, not a stage-level timeout.
     *
     * <p>ADR-004-A1's risk table assigns subprocess-hang handling to the stage timeout apparatus
     * (T086/T086a), which wraps this call from outside. This bound exists only so a single broken
     * invocation cannot block a thread forever; hitting it is reported as {@link TimeoutException}, which
     * {@code ProviderFailureTranslator} classifies {@code TIMEOUT} — the same category a stage-level
     * timeout would use, so the two compose rather than conflict.
     */
    private static final Duration PROCESS_WAIT_CEILING = Duration.ofMinutes(10);

    private final String command;
    private final String pinnedModel;

    /**
     * @param command     the {@code claude} executable — a bare name resolved via {@code PATH} in
     *                    production, or an absolute path to a test stub. Never combined with other argv
     *                    elements into a single string
     * @param pinnedModel the model pinned in configuration (ADR-004-A1). Sent as the {@code --model}
     *                    argument; the id actually used is read back from the response, never assumed to
     *                    equal this value
     */
    public ClaudeCodeCliStageAiProvider(String command, String pinnedModel) {
        this.command = Objects.requireNonNull(command, "command");
        this.pinnedModel = Objects.requireNonNull(pinnedModel, "pinnedModel");
        if (command.isBlank() || pinnedModel.isBlank()) {
            throw new IllegalArgumentException("command and pinnedModel must both be non-blank");
        }
    }

    @Override
    public AiResponse invoke(String prompt) throws Exception {
        Objects.requireNonNull(prompt, "prompt");

        // The argv array, and ONLY the argv array — see the class javadoc. Each element is its own list
        // entry; nothing here is ever joined into a command string.
        List<String> argv = List.of(command, "-p", prompt, "--model", pinnedModel, "--output-format", "json");
        ProcessBuilder builder = new ProcessBuilder(argv);

        Process process = builder.start();

        // Drained concurrently from the moment the process starts — see the class javadoc on why
        // sequential reads risk a pipe-buffer deadlock.
        CompletableFuture<String> stdout = readFully(process.getInputStream());
        CompletableFuture<String> stderr = readFully(process.getErrorStream());

        boolean finished = process.waitFor(PROCESS_WAIT_CEILING.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException(
                    "the Claude Code CLI did not exit within " + PROCESS_WAIT_CEILING.toSeconds() + "s");
        }

        String out = stdout.get();
        String errText = stderr.get();

        return parse(out, errText, process.exitValue());
    }

    private static CompletableFuture<String> readFully(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(stream.readAllBytes());
            } catch (IOException e) {
                // Folded into the returned text rather than propagated: a stream-read failure after the
                // process has already started is not the caller's IOException to translate as "CLI
                // unavailable" — that classification belongs to ProcessBuilder#start() failing, which this
                // method never runs after.
                return "";
            }
        });
    }

    /**
     * Turns the process's raw output into a response, or refuses it.
     *
     * <p>Deliberately independent of exit code — see the class javadoc.
     */
    private AiResponse parse(String stdout, String stderr, int exitCode) {
        JsonNode root;
        try {
            root = JSON.readTree(stdout);
        } catch (Exception e) {
            throw new MalformedProviderOutputException(
                    "the Claude Code CLI's stdout did not parse as JSON (exit " + exitCode + "). stdout: "
                            + truncate(stdout) + (stderr.isBlank() ? "" : "; stderr: " + truncate(stderr)),
                    e);
        }
        if (root == null || !root.isObject()) {
            throw new MalformedProviderOutputException(
                    "the Claude Code CLI's stdout parsed but was not a JSON object (exit " + exitCode
                            + "). stdout: " + truncate(stdout));
        }

        String modelId = modelIdFrom(root, exitCode, stdout);

        JsonNode resultNode = root.get("result");
        if (resultNode == null || !resultNode.isTextual()) {
            throw new MalformedProviderOutputException(
                    "the Claude Code CLI's response has no usable 'result' field (exit " + exitCode
                            + "). stdout: " + truncate(stdout));
        }

        return new AiResponse(modelId, resultNode.asText());
    }

    /**
     * Reads the actually-used model id from {@code modelUsage}, an object keyed by model id (verified
     * against a real installed CLI — see the class javadoc). Exactly one entry is required; zero or several
     * is refused rather than guessed at (FR-ORC-029).
     */
    private static String modelIdFrom(JsonNode root, int exitCode, String stdout) {
        JsonNode modelUsage = root.get("modelUsage");
        if (modelUsage == null || !modelUsage.isObject() || modelUsage.isEmpty()) {
            throw new MalformedProviderOutputException(
                    "the Claude Code CLI's response has no usable 'modelUsage' object (exit " + exitCode
                            + "). FR-ORC-029 requires the model id read FROM the response; a missing field "
                            + "cannot be guessed. stdout: " + truncate(stdout));
        }
        if (modelUsage.size() > 1) {
            throw new MalformedProviderOutputException(
                    "the Claude Code CLI's response names " + modelUsage.size() + " models in "
                            + "'modelUsage' (exit " + exitCode + ") — this adapter sent one prompt and "
                            + "expects one answering model; picking one of several would be exactly the "
                            + "guess FR-ORC-029 forbids. stdout: " + truncate(stdout));
        }

        java.util.Map.Entry<String, JsonNode> entry = modelUsage.fields().next();
        JsonNode canonical = entry.getValue().get("canonicalModel");
        if (canonical != null && canonical.isTextual() && !canonical.asText().isBlank()) {
            return canonical.asText();
        }
        // canonicalModel absent or unusable: the modelUsage KEY is itself the model id, still a reading
        // of the response rather than a guess.
        return entry.getKey();
    }

    /** Keeps a malformed-output detail readable rather than dumping an unbounded response into it. */
    private static String truncate(String text) {
        String oneLine = text.strip().replace('\n', ' ');
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...(truncated)";
    }
}
