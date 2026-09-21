package agentic.shortener.orchestration.executor.ai;

/**
 * The one seam through which every AI-capable stage reaches a model. Task T073. ADR-004.
 *
 * <p>ADR-004's own reason for this interface: reaching the model is a reversible choice, and reversibility
 * is only real if it lives behind exactly one boundary. {@link ClaudeCodeCliStageAiProvider} is the
 * implemented adapter (ADR-004-A1); the Anthropic API/SDK adapter is recorded as the alternative
 * implementation behind this same interface, built only if time permits (backlog T002) — and swapping
 * transport requires no change to this interface, to the stage definitions, to the executor-kind labels,
 * or to any requirement.
 *
 * <p><strong>This is a transport interface, not the executor interface.</strong> {@code StageExecutor}
 * (T069) is the one contract every stage runs behind, deterministic and AI-capable alike; a
 * {@code StageAiProvider} is what an AI-capable stage's own {@code StageExecutor} implementation uses
 * internally to reach a model. The two are not the same seam and must not be conflated: an AI-capable
 * stage's executor owns prompt construction and output parsing for ITS artifact type, while this interface
 * owns only "send a prompt, get back a response or a failure".
 *
 * <p>A failure is thrown, not returned as a value — unlike {@code StageExecutor}, which must never
 * throw. The asymmetry is deliberate: {@link agentic.shortener.orchestration.reliability.ProviderFailureTranslator}
 * exists precisely to convert a provider's own failure shape (an exception) into the classified
 * {@code FailureEnvelope} a stage executor returns, and a provider that already returned a classified
 * envelope would have nothing left for the translator to translate.
 */
public interface StageAiProvider {

    /**
     * Sends one prompt and returns the model's response.
     *
     * @param prompt untrusted content originating in a submitted requirement. An implementation MUST NOT
     *               interpolate it into a shell command; see {@link ClaudeCodeCliStageAiProvider}'s
     *               argv-not-shell rule
     * @throws Exception on any failure — the provider unavailable, a malformed response, a timeout.
     *                    Callers translate through {@code ProviderFailureTranslator}, so this interface
     *                    deliberately does not narrow the exception type: narrowing it here would be the
     *                    first leak of a vendor taxonomy the translator exists to contain (T083)
     */
    AiResponse invoke(String prompt) throws Exception;
}
