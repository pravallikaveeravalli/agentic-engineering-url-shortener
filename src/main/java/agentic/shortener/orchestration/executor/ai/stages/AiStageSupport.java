package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.reliability.MalformedProviderOutputException;
import agentic.shortener.orchestration.reliability.ProviderFailureTranslator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.function.Function;

/**
 * The shape every T073a-T073f adapter shares. Not itself a {@code StageExecutor} — each of the six IS its
 * own {@code StageExecutor}, with its own prompt and its own record type — but the invoke-then-translate
 * wrapper, and the JSON extraction a model's prose-wrapped answer needs, are identical six times over, so
 * they live here once.
 *
 * <p><strong>Every failure path reuses {@link ProviderFailureTranslator#forSubprocessProvider()}</strong> —
 * the same translator T073's own adapter uses, and the same one {@code ClaudeCodeCliStageAiProviderTest}
 * proves classifies a provider exception correctly. A parse-time guard violation (an adapter's own
 * structural contract, not the CLI's) is raised as a {@link MalformedProviderOutputException} rather than a
 * new exception type, and routed through the SAME translator: T073's assertion 4 ("non-JSON output is
 * INTERNAL and permanent, never retried") and a stage's own "the model invented scope" guard are the same
 * kind of fact — the model's answer failed to be usable — and deserve the same classification.
 */
final class AiStageSupport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ProviderFailureTranslator TRANSLATOR =
            ProviderFailureTranslator.forSubprocessProvider();

    private AiStageSupport() {
    }

    /**
     * Invokes {@code provider}, then hands a successful response to {@code parse}. Any exception from
     * either step — the provider's own failure, or {@code parse} rejecting what came back — is translated
     * into a {@link StageOutcome#failed} value; nothing here ever throws past this method.
     */
    static StageOutcome run(StageAiProvider provider, String prompt,
                            Function<AiResponse, List<ProducedArtifact>> parse) {
        AiResponse response;
        try {
            response = provider.invoke(prompt);
        } catch (Exception e) {
            return StageOutcome.failed(TRANSLATOR.translate(e));
        }
        try {
            List<ProducedArtifact> artifacts = parse.apply(response);
            return StageOutcome.succeeded(artifacts,
                    agentic.shortener.orchestration.executor.ExecutorKind.AI);
        } catch (Exception e) {
            return StageOutcome.failed(TRANSLATOR.translate(e));
        }
    }

    /**
     * Extracts JSON from a model's answer, tolerating a single markdown code fence around it — models
     * asked for "JSON" commonly wrap it in {@code ```json ... ```} even when told not to, and refusing that
     * shape outright would fail every real answer for a formatting habit rather than a genuine defect.
     *
     * @throws MalformedProviderOutputException if the (defenced) content does not parse as JSON at all
     */
    static JsonNode parseJson(String content) {
        String text = content.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).strip();
            }
        }
        try {
            JsonNode node = JSON.readTree(text);
            if (node == null || node.isMissingNode()) {
                throw new MalformedProviderOutputException(
                        "the model's answer did not parse as JSON. content: " + truncate(content));
            }
            return node;
        } catch (MalformedProviderOutputException e) {
            throw e;
        } catch (Exception e) {
            throw new MalformedProviderOutputException(
                    "the model's answer is not valid JSON: " + e.getMessage() + ". content: "
                            + truncate(content), e);
        }
    }

    private static String truncate(String text) {
        String oneLine = text.strip().replace('\n', ' ');
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...(truncated)";
    }
}
