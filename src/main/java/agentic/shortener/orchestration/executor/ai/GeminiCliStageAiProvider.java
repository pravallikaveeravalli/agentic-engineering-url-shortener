package agentic.shortener.orchestration.executor.ai;

import agentic.shortener.orchestration.reliability.MalformedProviderOutputException;
import agentic.shortener.orchestration.reliability.ProviderRateLimitedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A second {@link StageAiProvider}: a subprocess invocation of the locally installed, already-authenticated
 * Gemini CLI ({@code agy}) in headless mode. ADR-004 Amendment 03 (CR-042).
 *
 * <pre>agy -p "&lt;stage prompt&gt;" --model gemini-3.8-flash-high --output-format json &lt;/dev/null</pre>
 *
 * <h2>Why this class exists alongside {@link ClaudeCodeCliStageAiProvider}, not instead of it</h2>
 *
 * <p>{@link ClaudeCodeCliStageAiProvider} remains the documented primary/production adapter (ADR-004-A1),
 * unit-tested exactly as before. This class exists because a Claude Code build agent cannot spawn the
 * {@code claude} CLI as a nested subprocess (a deliberate recursive-invocation guard, not a bug), while it
 * CAN spawn {@code agy} — proven by a real call before this class was written. ADR-004 Amendment 03 records
 * the resulting decision: T073a-f's live demonstration runs (AS-007) use THIS adapter, purely for the
 * practical reason that it is the one this environment can actually invoke; nothing about which adapter is
 * "correct" has changed, and this class demonstrates exactly what {@code StageAiProvider} promises —
 * swapping the transport requires no change to any executor, any stage definition, or any requirement.
 *
 * <h2>The argv-not-shell rule (same security-critical property as the Claude adapter)</h2>
 *
 * <p>The stage prompt is untrusted content originating in submitted requirements. The subprocess command is
 * built as a {@code List<String>} passed straight to {@link ProcessBuilder}'s constructor — no
 * {@code /bin/sh -c} anywhere in this class.
 *
 * <h2>agy's own JSON shape, verified against a real installed CLI (2026-09-21)</h2>
 *
 * <p>Different from the Claude CLI's shape in three ways, each verified live before being assumed:
 *
 * <ul>
 *   <li>The answer text is in a top-level {@code "response"} string field (Claude's is {@code "result"}).
 *   <li>Success is signalled by a top-level {@code "status"} field equal to {@code "SUCCESS"} — there is no
 *       exit-code-independent free-form success test the way Claude's {@code model}/{@code result}
 *       presence check works, because agy's shape gives an explicit status field to read instead.
 *   <li><strong>agy's JSON does not echo a resolved model id anywhere</strong> — no field comparable to
 *       Claude's {@code modelUsage}. FR-ORC-029 asks for the model id to be "read from the response, never
 *       assumed", and this CLI gives this adapter nothing to read. The honest choice, stated plainly rather
 *       than worked around: this adapter records the PINNED request model id
 *       ({@code gemini-3.8-flash-high}) as the model used, with this fact disclosed here and in every
 *       {@link AiResponse} this class returns being traceable to a pin, not a verified reading. A future CLI
 *       version that adds a resolved-model field should be read from, the same way the Claude adapter reads
 *       {@code modelUsage} — this class does not become the permanent shape of the seam, only its current
 *       honest state.
 * </ul>
 *
 * <h2>stdin is explicitly closed</h2>
 *
 * <p>Redirected from {@code /dev/null} rather than left to inherit this JVM's own stdin: a headless
 * subprocess invocation must never be able to block waiting for interactive input it will never receive.
 *
 * <h2>Stdout and stderr are drained concurrently</h2>
 *
 * <p>Same pipe-buffer-deadlock reasoning as {@link ClaudeCodeCliStageAiProvider} — both streams are drained
 * on background threads from the moment the process starts.
 */
public final class GeminiCliStageAiProvider implements StageAiProvider {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Same rationale as {@link ClaudeCodeCliStageAiProvider}'s own ceiling — a generous backstop only. */
    private static final Duration PROCESS_WAIT_CEILING = Duration.ofMinutes(10);

    private final String command;
    private final String pinnedModel;

    /**
     * @param command     the {@code agy} executable — a bare name resolved via {@code PATH} in production,
     *                    or an absolute path to a test stub
     * @param pinnedModel the model pinned in configuration. Sent as the {@code --model} argument AND
     *                    recorded as the model used, since agy's own response names no resolved model id to
     *                    read instead (see the class javadoc)
     */
    public GeminiCliStageAiProvider(String command, String pinnedModel) {
        this.command = Objects.requireNonNull(command, "command");
        this.pinnedModel = Objects.requireNonNull(pinnedModel, "pinnedModel");
        if (command.isBlank() || pinnedModel.isBlank()) {
            throw new IllegalArgumentException("command and pinnedModel must both be non-blank");
        }
    }

    @Override
    public AiResponse invoke(String prompt) throws Exception {
        Objects.requireNonNull(prompt, "prompt");

        List<String> argv =
                List.of(command, "-p", prompt, "--model", pinnedModel, "--output-format", "json");
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectInput(new File("/dev/null"));

        Process process = builder.start();

        CompletableFuture<String> stdout = readFully(process.getInputStream());
        CompletableFuture<String> stderr = readFully(process.getErrorStream());

        boolean finished = process.waitFor(PROCESS_WAIT_CEILING.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException(
                    "the Gemini CLI (agy) did not exit within " + PROCESS_WAIT_CEILING.toSeconds() + "s");
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
                return "";
            }
        });
    }

    private AiResponse parse(String stdout, String stderr, int exitCode) {
        JsonNode root;
        try {
            root = JSON.readTree(stdout);
        } catch (Exception e) {
            throw new MalformedProviderOutputException(
                    "the Gemini CLI's stdout did not parse as JSON (exit " + exitCode + "). stdout: "
                            + truncate(stdout) + (stderr.isBlank() ? "" : "; stderr: " + truncate(stderr)),
                    e);
        }
        if (root == null || !root.isObject()) {
            throw new MalformedProviderOutputException(
                    "the Gemini CLI's stdout parsed but was not a JSON object (exit " + exitCode
                            + "). stdout: " + truncate(stdout));
        }

        JsonNode statusNode = root.get("status");
        if (statusNode == null || !statusNode.isTextual() || !"SUCCESS".equals(statusNode.asText())) {
            String errorText = errorFieldOf(root);
            if (isRateLimitSignal(errorText)) {
                // T131d, found live during a real DS-A run: agy can answer status=ERROR carrying a fully
                // formed, substantive response ALONGSIDE an "error" field naming a genuine Gemini API
                // quota exhaustion (RESOURCE_EXHAUSTED / HTTP 429). That is not a malformed response —
                // it is the provider correctly reporting it is rate-limited, and RATE_LIMITED is exactly
                // the category S3's own declared retryable set names for it. The response content itself
                // is still discarded: this remains a failure, not a partial success, matching the
                // no-partial-credit shape every other branch here already uses.
                throw new ProviderRateLimitedException(
                        "the Gemini CLI reported a rate limit / quota exhaustion (exit " + exitCode
                                + "): " + truncate(errorText));
            }
            throw new MalformedProviderOutputException(
                    "the Gemini CLI's response status was not SUCCESS (exit " + exitCode + ", status="
                            + (statusNode == null ? "<absent>" : statusNode.asText()) + "). stdout: "
                            + truncate(stdout));
        }

        JsonNode responseNode = root.get("response");
        if (responseNode == null || !responseNode.isTextual()) {
            throw new MalformedProviderOutputException(
                    "the Gemini CLI's response has no usable 'response' field (exit " + exitCode
                            + "). stdout: " + truncate(stdout));
        }

        // The pinned request model, recorded as the model used — agy's own JSON names no resolved model
        // id to read instead. See the class javadoc's third disclosed difference.
        return new AiResponse(pinnedModel, responseNode.asText());
    }

    private static String truncate(String text) {
        String oneLine = text.strip().replace('\n', ' ');
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...(truncated)";
    }

    /** {@code ""} rather than {@code null} when absent — every caller of this method treats blank and
     * absent identically, and a non-null return keeps them from having to. */
    private static String errorFieldOf(JsonNode root) {
        JsonNode errorNode = root.get("error");
        return errorNode != null && errorNode.isTextual() ? errorNode.asText() : "";
    }

    /**
     * Recognizes agy's own rate-limit/quota-exhaustion wording, verified against a real captured response
     * rather than guessed: {@code "RESOURCE_EXHAUSTED"} and an HTTP {@code 429} code both appear together
     * in the one instance this adapter has actually observed. Either alone is accepted, since a future
     * response naming only one is still describing the same condition.
     */
    private static boolean isRateLimitSignal(String errorText) {
        if (errorText.isBlank()) {
            return false;
        }
        String upper = errorText.toUpperCase(Locale.ROOT);
        return upper.contains("RESOURCE_EXHAUSTED") || upper.contains("429");
    }
}
