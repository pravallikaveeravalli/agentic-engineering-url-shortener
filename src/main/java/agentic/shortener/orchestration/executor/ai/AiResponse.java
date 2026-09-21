package agentic.shortener.orchestration.executor.ai;

import java.util.Objects;

/**
 * What a {@link StageAiProvider} returns for one successful invocation. Task T073. FR-ORC-029.
 *
 * @param modelId the model that actually produced this response, read from the provider's own response —
 *                not from configuration. FR-ORC-029 requires the model id recorded in run evidence to be
 *                the one actually used, which is a stronger claim than recording the pin alone: a
 *                misconfigured pin, or a provider that silently substitutes a model, is caught by this
 *                rather than hidden by it
 * @param content the raw text of the model's answer. Parsing it into a stage-specific record
 *                ({@code RequirementRecord}, {@code AmbiguityRecord}, ...) is each AI-capable stage's own
 *                job (T073a–T073f); this class carries only the transport-level response
 */
public record AiResponse(String modelId, String content) {

    public AiResponse {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(content, "content");
        if (modelId.isBlank()) {
            throw new IllegalArgumentException(
                    "a blank model id is not a reading of the response — it is a guess, and FR-ORC-029's "
                            + "evidence trail must never be built on one");
        }
    }
}
