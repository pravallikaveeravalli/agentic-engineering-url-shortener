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
     * Extracts JSON from a model's answer, tolerating a markdown code fence around it — models asked for
     * "JSON, no prose" commonly wrap it in {@code ```json ... ```} anyway, and often prepend a sentence of
     * reasoning before the fence even when told not to (verified against a real live call for T073d: the
     * model prefixed <em>"Now I have enough context. Here is the design output."</em> before its own fence).
     * Refusing either shape outright would fail a real, usable answer for a formatting habit rather than a
     * genuine defect, so the fence is located ANYWHERE in the text, not only at its start.
     *
     * @throws MalformedProviderOutputException if the (defenced) content does not parse as JSON at all
     */
    static JsonNode parseJson(String content) {
        String text = content.strip();
        int fenceStart = text.indexOf("```");
        if (fenceStart >= 0) {
            // Skip past the fence marker and an optional language tag (letters only — "json") immediately
            // after it. NOT anchored on finding a newline: a real live answer for T073d put its closing
            // fence on the SAME line as its content ("```json {...} ```"), and requiring a newline there
            // refused a perfectly usable answer.
            int contentStart = fenceStart + 3;
            while (contentStart < text.length() && Character.isLetter(text.charAt(contentStart))) {
                contentStart++;
            }
            int lastFence = text.lastIndexOf("```");
            if (lastFence > contentStart) {
                text = text.substring(contentStart, lastFence).strip();
            }
        } else if (!text.isEmpty() && text.charAt(0) != '{' && text.charAt(0) != '[') {
            // No fence at all, but the answer does not open on JSON either — a leading sentence before a
            // bare (unfenced) object or array. Trim to the first JSON-opening character, if there is one;
            // if there is none, the text is left as-is and fails the parse below with the full content in
            // the refusal, which is the honest outcome for an answer that never contained JSON.
            int firstBrace = text.indexOf('{');
            int firstBracket = text.indexOf('[');
            int start = firstBrace < 0 ? firstBracket
                    : firstBracket < 0 ? firstBrace : Math.min(firstBrace, firstBracket);
            if (start > 0) {
                text = text.substring(start).strip();
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
