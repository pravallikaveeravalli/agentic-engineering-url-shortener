package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;
import agentic.shortener.orchestration.reliability.MalformedProviderOutputException;
import agentic.shortener.orchestration.reliability.ProviderFailureTranslator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

/**
 * S7 — implementation (fan-out). Task T073e. FR-ORC-031, FR-ORC-029. ADR-004, ADR-004-A1, ADR-004-A2.
 *
 * <p>Three collaborators, matching this task's own Artifact line exactly: <strong>AI authors</strong> the
 * change from the design output (this class's own prompt/parse half), <strong>the engine applies it on a
 * branch</strong> ({@link BranchApplier}, this class's port — see that interface's own javadoc for why S8's
 * real suite is NOT re-run here), and <strong>the real suite verifies</strong> separately, as S8
 * ({@code TestingEngine}, already built) — S7's own plan-table postcondition is "change applied on branch,
 * buildable", nothing further.
 *
 * <h2>Search/replace and full-file content, not a unified diff (CR-060)</h2>
 *
 * <p>Earlier versions of this class asked the model for a unified diff (line-numbered {@code @@} hunks).
 * That format requires the model to invent exact line numbers and surrounding context for a file it has
 * never itself opened — CR-055 gave it the real file content to read, but real, live attempts (docs/evidence/
 * ds-a's own attempts 14, 15, 17, 19) still produced hunks {@code git apply} rejected as corrupt, even with
 * accurate content shown. Real coding agents do not ask a model to author line-numbered patches blind; they
 * use a format the model is good at — an exact snippet to find, and its replacement — and apply it
 * deterministically, never fuzzily. This class now asks for exactly that: per file, either the full content
 * of a NEW file, or one or more search/replace pairs against an EXISTING file (whose real content it was
 * already shown, via the same {@link #INPUT_EXISTING_FILES_KEY} CR-055 introduced). {@link BranchApplier}
 * performs the actual string search/replace or file write, deterministically, in the isolated worktree — this
 * class still authors nothing to disk itself and still never executes AI output as text.
 *
 * <h2>Why a build failure — or a search block that does not match — is {@code INVALID_INPUT}, not
 * {@code INTERNAL}</h2>
 *
 * <p>{@code INTERNAL} in this package means "the model's answer could not even be understood" (malformed
 * JSON, a missing field, an unrecognized {@code action}). A change set that parses fine but whose search text
 * does not match the real file content exactly once, or that does not build once applied, is a different kind
 * of fact — content this class understood perfectly, that turned out to be wrong — and the plan table's own
 * Failure class column calls this case "conflict = permanent": {@code INVALID_INPUT}, never retried on the
 * identical change set, routing back to S7 for a fresh attempt rather than proposed as a transient failure.
 */
public final class ImplementationAiExecutor implements StageExecutor {

    static final String INPUT_TASK_KEY = "task";
    static final String INPUT_DESIGN_KEY = "design";
    /** Optional: a JSON object (path -&gt; real current content) Conductor pre-fetches for whatever files
     * S6's own design named under {@code "existingFilesToModify"}. Absent or blank means a purely new-file
     * change — never required, unlike {@link #INPUT_TASK_KEY}/{@link #INPUT_DESIGN_KEY}. */
    static final String INPUT_EXISTING_FILES_KEY = "existingFiles";
    static final String OUTPUT_KEY = "branchCommit";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ProviderFailureTranslator TRANSLATOR =
            ProviderFailureTranslator.forSubprocessProvider();

    private final StageAiProvider provider;
    private final BranchApplier branchApplier;

    public ImplementationAiExecutor(StageAiProvider provider, BranchApplier branchApplier) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.branchApplier = Objects.requireNonNull(branchApplier, "branchApplier");
    }

    @Override
    public StageOutcome execute(StageInput input) {
        String task = input.inputArtifacts().get(INPUT_TASK_KEY);
        String design = input.inputArtifacts().get(INPUT_DESIGN_KEY);
        if (task == null || task.isBlank() || design == null || design.isBlank()) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "both '" + INPUT_TASK_KEY + "' and '" + INPUT_DESIGN_KEY
                            + "' are required — S5 and S6 must run first", false));
        }
        // Optional, pre-fetched by Conductor (never read directly by this class): the real, current
        // content of whatever existing files the design named. Absent/blank means a purely new-file
        // change -- exactly as before this key existed.
        String existingFiles = input.inputArtifacts().get(INPUT_EXISTING_FILES_KEY);

        AiResponse response;
        try {
            response = provider.invoke(buildPrompt(task, design, existingFiles));
        } catch (Exception e) {
            // TIMEOUT is EXCLUDED from S7's own declared retryable set (EC-033: the git effect is
            // non-idempotent), unlike every other AI-capable stage. The translator still classifies it
            // TIMEOUT; it is the ORCHESTRATOR's declared-retryable-set check (T070) that then refuses to
            // retry it — this executor does not re-implement that veto, it only must not propose around it.
            return StageOutcome.failed(TRANSLATOR.translate(e));
        }

        String changeSet;
        try {
            changeSet = extractChangeSet(response.content());
        } catch (Exception e) {
            return StageOutcome.failed(TRANSLATOR.translate(e));
        }

        BranchApplier.ApplyResult result;
        try {
            result = branchApplier.apply(taskId(task), changeSet);
        } catch (Exception e) {
            return StageOutcome.failed(TRANSLATOR.translate(e));
        }

        if (!result.buildable()) {
            // Routes back to S7 within the bound — a caught failure, not a thrown one, and never
            // retryable on the identical change set (plan table: "conflict = permanent").
            return StageOutcome.failed(
                    new FailureEnvelope(FailureCategory.INVALID_INPUT, result.detail(), false));
        }

        return StageOutcome.succeeded(
                List.of(new ProducedArtifact(OUTPUT_KEY, result.branchRef(),
                        List.of(INPUT_TASK_KEY, INPUT_DESIGN_KEY))),
                ExecutorKind.AI);
    }

    /**
     * The "do not use tools" sentence is load-bearing, not decorative. T073e's first two live attempts
     * against the Gemini CLI adapter both returned {@code status:SUCCESS} with an empty {@code response} —
     * an agentic coding CLI, asked to author a change in a domain it can also directly edit files in, appears
     * to enter its own tool-use loop instead of answering in text; headless (stdin from {@code /dev/null}, no
     * interactive terminal), that loop cannot complete, and the empty string is what a CLI reports back
     * when nothing else was said. Adding this sentence — verified against a real live call, not assumed —
     * fixed it twice in a row, at the same pinned model, with no other change.
     */
    private static String buildPrompt(String task, String design, String existingFiles) {
        StringBuilder prompt = new StringBuilder()
                .append("You are the implementation stage of a software requirement pipeline. Author a "
                        + "change that implements the task below, following the design. Do not use any "
                        + "tools. Do not read or write any files yourself. Produce your answer as plain "
                        + "text JSON in your final reply only, no prose before or after it.\n\n"
                        + "Respond with ONLY this JSON shape, no markdown fence: {\"files\": [ "
                        + "{\"path\": string, \"action\": \"CREATE\", \"content\": string} "
                        + "-- for a brand-new file, content is the file's ENTIRE content, OR "
                        + "{\"path\": string, \"action\": \"EDIT\", \"edits\": [ "
                        + "{\"search\": string, \"replace\": string}, ... ]} "
                        + "-- for an EXISTING file, each 'search' string MUST be copied EXACTLY, "
                        + "character-for-character (including whitespace and indentation), from the real "
                        + "file content shown to you below -- never paraphrased, never guessed, never "
                        + "including line numbers. Each 'search' MUST occur exactly once in the file; if "
                        + "the same snippet appears more than once, include enough surrounding context in "
                        + "'search' to make it unique. Edits within one file are applied in the order "
                        + "listed, each against the result of the previous one. Never include line numbers "
                        + "or diff/patch syntax anywhere -- this is search-and-replace, not a unified "
                        + "diff.\n\n");

        if (existingFiles != null && !existingFiles.isBlank() && !"{}".equals(existingFiles.strip())) {
            prompt.append("The orchestration has already read the following existing files for you -- "
                            + "every EDIT action's own 'search' text must be an exact substring of the "
                            + "content shown here. For any file NOT listed here, use action CREATE.\n\n")
                    .append("Existing file content (JSON object, path -> exact current content):\n")
                    .append(existingFiles).append("\n\n");
        }

        prompt.append("Task:\n").append(task).append("\n\nDesign:\n").append(design);
        return prompt.toString();
    }

    /**
     * Validates the model's answer is a well-formed change set (INTERNAL, permanent, if not — "could not
     * even be understood"); does NOT validate that an EDIT's own search text actually matches anything, or
     * that the result builds — those are {@link BranchApplier}'s own job, and their failure is
     * {@code INVALID_INPUT}, a different kind of fact (content understood, but wrong).
     */
    private static String extractChangeSet(String content) {
        String text = content.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).strip();
            }
        }
        if (text.isBlank()) {
            throw new MalformedProviderOutputException(
                    "the model's answer is blank -- AI output is never executed as text, so an empty "
                            + "answer is refused rather than applied speculatively");
        }

        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (Exception e) {
            throw new MalformedProviderOutputException(
                    "the model's answer does not parse as JSON. content: " + truncate(content), e);
        }
        JsonNode files = root.get("files");
        if (!root.isObject() || files == null || !files.isArray() || files.isEmpty()) {
            throw new MalformedProviderOutputException(
                    "the model's answer must be a JSON object with a non-empty 'files' array. content: "
                            + truncate(content));
        }
        for (JsonNode file : files) {
            String path = file.path("path").asText("");
            if (path.isBlank()) {
                throw new MalformedProviderOutputException(
                        "a 'files' entry is missing a non-blank 'path': " + file);
            }
            String action = file.path("action").asText("");
            if ("CREATE".equals(action)) {
                if (!file.has("content") || !file.get("content").isTextual()) {
                    throw new MalformedProviderOutputException(
                            "a CREATE entry for '" + path + "' is missing a textual 'content' field: " + file);
                }
            } else if ("EDIT".equals(action)) {
                JsonNode edits = file.get("edits");
                if (edits == null || !edits.isArray() || edits.isEmpty()) {
                    throw new MalformedProviderOutputException(
                            "an EDIT entry for '" + path + "' is missing a non-empty 'edits' array: " + file);
                }
                for (JsonNode edit : edits) {
                    if (!edit.has("search") || !edit.get("search").isTextual()
                            || edit.get("search").asText().isEmpty()) {
                        throw new MalformedProviderOutputException(
                                "an edit for '" + path + "' is missing a non-empty 'search' string: " + edit);
                    }
                    if (!edit.has("replace") || !edit.get("replace").isTextual()) {
                        throw new MalformedProviderOutputException(
                                "an edit for '" + path + "' is missing a textual 'replace' string: " + edit);
                    }
                }
            } else {
                throw new MalformedProviderOutputException(
                        "a 'files' entry for '" + path + "' has an unrecognized action (must be CREATE or "
                                + "EDIT): " + file);
            }
        }
        return text;
    }

    private static String taskId(String task) {
        // The task artifact's own external id, if it carries one recognizably; otherwise the raw task
        // text is a fine identifier for a single-task branch name.
        int idx = task.indexOf("\"taskId\"");
        if (idx < 0) {
            return "task-" + Integer.toHexString(task.hashCode());
        }
        int colon = task.indexOf(':', idx);
        int quoteStart = task.indexOf('"', colon + 1);
        int quoteEnd = task.indexOf('"', quoteStart + 1);
        return quoteStart >= 0 && quoteEnd > quoteStart ? task.substring(quoteStart + 1, quoteEnd)
                : "task-" + Integer.toHexString(task.hashCode());
    }

    private static String truncate(String text) {
        String oneLine = text.strip().replace('\n', ' ');
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...(truncated)";
    }
}
