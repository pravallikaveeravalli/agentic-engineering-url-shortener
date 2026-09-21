package agentic.shortener.orchestration.executor.deterministic;

import java.util.Objects;

/**
 * The collaborators the three delegating engines need. Task T071.
 *
 * <p>One record rather than three parameters, so {@link DeterministicEngines#forStage} has a single signature
 * for all five stages — including S1 and S12, which need none of them. A per-stage signature would push the
 * question "which stage needs what" out to every caller, and the answer would then be encoded in a dozen
 * places instead of one.
 */
public record EnginePorts(TestSuiteRunner testSuiteRunner, PolicyEvaluator policyEvaluator,
                          ReadinessEvaluator readinessEvaluator) {

    public EnginePorts {
        Objects.requireNonNull(testSuiteRunner, "testSuiteRunner");
        Objects.requireNonNull(policyEvaluator, "policyEvaluator");
        Objects.requireNonNull(readinessEvaluator, "readinessEvaluator");
    }
}
