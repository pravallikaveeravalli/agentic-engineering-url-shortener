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

import java.util.List;
import java.util.Objects;

/**
 * S7 — implementation (fan-out). Task T073e. FR-ORC-031, FR-ORC-029. ADR-004, ADR-004-A1, ADR-004-A2.
 *
 * <p>Three collaborators, matching this task's own Artifact line exactly: <strong>AI authors</strong> the
 * change from the design output (this class's own prompt/parse half), <strong>the engine applies it on a
 * branch</strong> ({@link BranchApplier}, this class's port — see that interface's own javadoc for why S8's
 * real suite is NOT re-run here), and <strong>the real suite verifies</strong> separately, as S8
 * ({@code TestingEngine}, already built) — S7's own plan-table postcondition is "patch applied on branch,
 * buildable", nothing further.
 *
 * <h2>AI output is never executed as text</h2>
 *
 * <p>The model's answer is a unified diff. It is handed to {@link BranchApplier}, which applies it with
 * {@code git apply} (or equivalent) and reports whether the result builds — never interpreted, evaluated,
 * or run as a script. A failing patch being caught by that pipeline is the governance working as designed,
 * not a failed demonstration (this task's own Guard).
 *
 * <h2>Why a build failure is {@code INVALID_INPUT}, not {@code INTERNAL}</h2>
 *
 * <p>{@code INTERNAL} in this package means "the model's answer could not even be understood" (malformed
 * JSON, a missing field). A patch that parses fine as a diff but does not build is a different kind of
 * fact — a defect in content this class understood perfectly — and the plan table's own Failure class
 * column calls this case "conflict = permanent": {@code INVALID_INPUT}, never retried on the identical
 * patch, routing back to S7 for a fresh attempt rather than proposed as a transient failure.
 */
public final class ImplementationAiExecutor implements StageExecutor {

    static final String INPUT_TASK_KEY = "task";
    static final String INPUT_DESIGN_KEY = "design";
    static final String OUTPUT_KEY = "branchCommit";

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

        AiResponse response;
        try {
            response = provider.invoke(buildPrompt(task, design));
        } catch (Exception e) {
            // TIMEOUT is EXCLUDED from S7's own declared retryable set (EC-033: the git effect is
            // non-idempotent), unlike every other AI-capable stage. The translator still classifies it
            // TIMEOUT; it is the ORCHESTRATOR's declared-retryable-set check (T070) that then refuses to
            // retry it — this executor does not re-implement that veto, it only must not propose around it.
            return StageOutcome.failed(TRANSLATOR.translate(e));
        }

        String patch;
        try {
            patch = extractPatch(response.content());
        } catch (Exception e) {
            return StageOutcome.failed(TRANSLATOR.translate(e));
        }

        BranchApplier.ApplyResult result;
        try {
            result = branchApplier.apply(taskId(task), patch);
        } catch (Exception e) {
            return StageOutcome.failed(TRANSLATOR.translate(e));
        }

        if (!result.buildable()) {
            // Routes back to S7 within the bound — a caught failure, not a thrown one, and never
            // retryable on the identical patch (plan table: "conflict = permanent").
            return StageOutcome.failed(
                    new FailureEnvelope(FailureCategory.INVALID_INPUT, result.detail(), false));
        }

        return StageOutcome.succeeded(
                List.of(new ProducedArtifact(OUTPUT_KEY, result.branchRef(),
                        List.of(INPUT_TASK_KEY, INPUT_DESIGN_KEY))),
                ExecutorKind.AI);
    }

    private static String buildPrompt(String task, String design) {
        return "You are the implementation stage of a software requirement pipeline. Author a change that "
                + "implements the task below, following the design. Respond with ONLY a unified diff (git "
                + "apply-compatible), no prose, no markdown fence, no explanation before or after it.\n\n"
                + "Task:\n" + task + "\n\nDesign:\n" + design;
    }

    /** Tolerates a markdown fence around the diff, the same accommodation every other adapter makes. */
    private static String extractPatch(String content) {
        String text = content.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).strip();
            }
        }
        if (text.isBlank() || !(text.contains("--- ") || text.contains("diff --git"))) {
            throw new MalformedProviderOutputException(
                    "the model's answer does not look like a unified diff (no '--- ' or 'diff --git' "
                            + "header found). AI output is never executed as text, so an answer that is not "
                            + "recognizably a patch is refused rather than applied speculatively. content: "
                            + truncate(content));
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
