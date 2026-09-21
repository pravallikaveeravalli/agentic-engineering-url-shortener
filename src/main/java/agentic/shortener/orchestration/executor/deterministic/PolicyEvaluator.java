package agentic.shortener.orchestration.executor.deterministic;

import java.util.Map;

/**
 * Evaluates {@code policy-set-1.1.0}. Task T071's port for S10; implemented by T100.
 *
 * <p>The engine hands over the run's artifacts and takes back a verdict. Keeping the evaluation behind a port
 * is what stops a twelve-check policy engine growing inside the executor package, where the plane-boundary
 * tests would have no reason to look at it.
 */
@FunctionalInterface
public interface PolicyEvaluator {

    PolicyVerdict evaluate(Map<String, String> artifacts) throws Exception;
}
