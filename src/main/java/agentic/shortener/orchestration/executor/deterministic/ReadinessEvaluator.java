package agentic.shortener.orchestration.executor.deterministic;

import java.util.Map;

/**
 * Evaluates the nine blocking release conditions. Task T071's port for S11; implemented by T109.
 */
@FunctionalInterface
public interface ReadinessEvaluator {

    ReadinessVerdict evaluate(Map<String, String> artifacts) throws Exception;
}
