package agentic.shortener.orchestration.executor.deterministic;

import java.util.Map;

/**
 * Deterministic stand-ins for the three ports the delegating engines call. Task T071 test support.
 *
 * <p><strong>Why three of the five engines have a port at all.</strong> S8 executes the real test suite, S10
 * evaluates {@code policy-set-1.1.0} and S11 evaluates the nine blocking conditions. Those evaluators are
 * their own tasks — T100 and T109 — arriving in a much later slice, and the suite runner shells out. Building
 * them inside the engines would put a policy evaluator in the executor package and make S8's unit tests run
 * Maven.
 *
 * <p>So the engine's own work — mapping workflow data to the evaluator's question, and the answer back to a
 * provenance-carrying artifact — is real and complete here, and the evaluator behind the port is real when
 * T100 and T109 land. The engine is deterministic <em>given</em> a deterministic port, which is exactly what
 * {@code DeterministicEnginesTest} asserts, alongside a structural rule that no engine has any other hidden
 * input.
 */
final class TestPorts {

    private TestPorts() {
    }

    /** All three ports answering successfully, deterministically. */
    static EnginePorts working() {
        return new EnginePorts(
                branch -> new TestSuiteReport(42, 0, "42 tests, 0 failures against " + branch),
                artifacts -> new PolicyVerdict(false, "12 checks evaluated, 0 blocking"),
                artifacts -> new ReadinessVerdict(true, "9 blocking conditions evaluated, 0 unmet"));
    }

    /** A failing suite: the change under test is not acceptable, which is not an infrastructure failure. */
    static EnginePorts failingSuite() {
        return new EnginePorts(
                branch -> new TestSuiteReport(42, 3, "42 tests, 3 failures against " + branch),
                working().policyEvaluator(), working().readinessEvaluator());
    }

    /** The suite runner cannot run at all. */
    static EnginePorts unavailableSuite() {
        return new EnginePorts(
                branch -> {
                    throw new java.io.IOException("Cannot run program \"mvn\"");
                },
                working().policyEvaluator(), working().readinessEvaluator());
    }

    static EnginePorts blockingPolicy() {
        return new EnginePorts(working().testSuiteRunner(),
                artifacts -> new PolicyVerdict(true, "12 checks evaluated, 1 mandatory FAIL: SEC-002"),
                working().readinessEvaluator());
    }

    static EnginePorts notReady() {
        return new EnginePorts(working().testSuiteRunner(), working().policyEvaluator(),
                artifacts -> new ReadinessVerdict(false, "9 blocking conditions evaluated, 2 unmet"));
    }

    /** Input good enough for the named stage to succeed. */
    static Map<String, String> sampleArtifactsFor(int stageNumber) {
        return switch (stageNumber) {
            case 1 -> Map.of("submission", "Shorten URLs with an expiry.");
            case 8 -> Map.of("branch", "run/aa/S7");
            case 10 -> Map.of("branch", "run/aa/S7", "test-results", "42 tests, 0 failures");
            case 11 -> Map.of("test-results", "42 tests, 0 failures",
                    "policy-results", "12 checks evaluated, 0 blocking");
            case 12 -> Map.of("requirement.intake", "Shorten URLs with an expiry.",
                    "test-results", "42 tests, 0 failures",
                    "policy-results", "12 checks evaluated, 0 blocking",
                    "release-readiness", "9 blocking conditions evaluated, 0 unmet");
            default -> throw new IllegalArgumentException("no sample for stage " + stageNumber);
        };
    }
}
