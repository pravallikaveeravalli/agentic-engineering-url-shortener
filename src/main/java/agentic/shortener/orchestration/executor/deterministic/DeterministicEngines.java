package agentic.shortener.orchestration.executor.deterministic;

import agentic.shortener.orchestration.executor.StageExecutor;

import java.util.List;
import java.util.Objects;

/**
 * The five, and only the five. Task T071. ADR-004-A2.
 *
 * <p><strong>These are not fallbacks and never were.</strong> Decision J removed every deterministic
 * counterpart to an AI-capable stage, so there is no engine here standing in for AI work. S1, S8, S10, S11 and
 * S12 are the stages whose value <em>is</em> repeatability (CL-003).
 *
 * <p>A sixth entry would therefore be a real change rather than an addition: it would mean an AI-capable stage
 * had acquired a deterministic counterpart, which is precisely what ADR-004-A2 struck.
 * {@code DeterministicEnginesTest} asserts the list, so that change cannot arrive quietly.
 */
public final class DeterministicEngines {

    private static final List<Integer> STAGE_NUMBERS = List.of(1, 8, 10, 11, 12);

    private DeterministicEngines() {
    }

    public static List<Integer> stageNumbers() {
        return STAGE_NUMBERS;
    }

    /**
     * @param ports the collaborators the three delegating engines need. S1 and S12 ignore them: their work is
     *              entirely over workflow data, which is why they have nothing to inject
     */
    public static StageExecutor forStage(int stageNumber, EnginePorts ports) {
        Objects.requireNonNull(ports, "ports");
        return switch (stageNumber) {
            case 1 -> new IngestionEngine();
            case 8 -> new TestingEngine(ports.testSuiteRunner());
            case 10 -> new PolicyEvaluationEngine(ports.policyEvaluator());
            case 11 -> new ReleaseReadinessEngine(ports.readinessEvaluator());
            case 12 -> new SummaryEngine();
            default -> throw new IllegalArgumentException(
                    "stage " + stageNumber + " has no deterministic engine. The AI-capable stages have no "
                            + "deterministic counterpart (ADR-004-A2); asking for one here is asking for "
                            + "the fallback Decision J removed");
        };
    }
}
