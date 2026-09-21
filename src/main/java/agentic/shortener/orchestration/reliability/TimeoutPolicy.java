package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.StageEffectContracts;

import java.time.Duration;
import java.util.Map;

/**
 * PVT-016's threshold schedule, and whether a timeout may be retried. Task T086.
 * FR-ORC-014 rule 4, FR-ORC-018.
 *
 * <p><strong>Two separate decisions, deliberately not merged.</strong> How long a node is expected to take is
 * a question about duration; whether a timeout may be retried is a question about the effect. Merging them
 * would make one number carry both, and changing an expected duration would silently change retry behaviour.
 *
 * <p><strong>This class decides nothing about consequences.</strong> T086a owns what a breach <em>does</em>,
 * and the owner's ruling there is that the orchestrator may never kill a node on its own authority: a slow node
 * is not a failed node, and killing on a timer would put that decision back inside the governed system. So
 * there is no method here that fails, kills or cancels anything — {@code TimeoutPolicyTest} asserts the absence
 * over this file's own source, because an absence nobody checks is an absence that grows a method.
 *
 * <h2>Eligibility is read, never held</h2>
 *
 * <p>{@link #timeoutIsRetryable(int)} asks {@link StageEffectContracts} rather than keeping its own table. A
 * second source of truth about idempotence would drift toward whichever copy was easier to change, and the
 * contract is the design-time declaration precisely so that no runtime component holds an opinion about it.
 */
public final class TimeoutPolicy {

    /**
     * PVT-016, transcribed from plan §3's {@code Timeout & retry} column.
     *
     * <p><strong>S4 is absent, not zero.</strong> The human-clarification stage has no execution threshold: it
     * is already waiting on a person, and its deadline is the gate wait, which produces {@code SAFE_STOP}
     * rather than an overrun. A default here would hand S4 an execution deadline it does not have, and T086a
     * would then raise a node-overrun gate against a node that is correctly waiting for a human.
     *
     * <p>{@code TimeoutPolicyTest} reads these eleven figures out of the plan and compares, so a drifted value
     * fails a test rather than changing behaviour quietly.
     */
    private static final Map<Integer, Duration> THRESHOLDS = Map.ofEntries(
            Map.entry(1, Duration.ofSeconds(5)),
            Map.entry(2, Duration.ofSeconds(120)),
            Map.entry(3, Duration.ofSeconds(120)),
            Map.entry(5, Duration.ofSeconds(180)),
            Map.entry(6, Duration.ofSeconds(300)),
            // Per node, not per stage: S7 fans out, and a shared budget would let one slow child consume the
            // allowance of its siblings.
            Map.entry(7, Duration.ofSeconds(600)),
            // The real suite plus Testcontainers startup, which is why this is the largest by far.
            Map.entry(8, Duration.ofSeconds(1800)),
            Map.entry(9, Duration.ofSeconds(180)),
            // Dominated by the dependency-vulnerability scan.
            Map.entry(10, Duration.ofSeconds(600)),
            Map.entry(11, Duration.ofSeconds(30)),
            Map.entry(12, Duration.ofSeconds(60)));

    private TimeoutPolicy() {
    }

    /**
     * @throws IllegalArgumentException for a stage with no execution threshold. Refused rather than defaulted:
     *                                  see {@link #THRESHOLDS} on S4
     */
    public static Duration thresholdFor(int stageNumber) {
        Duration threshold = THRESHOLDS.get(stageNumber);
        if (threshold == null) {
            throw new IllegalArgumentException(
                    "stage " + stageNumber + " has no execution threshold (PVT-016). S4 is waiting on a "
                            + "human, and its deadline is the gate wait, which suspends rather than overruns");
        }
        return threshold;
    }

    public static boolean hasExecutionThreshold(int stageNumber) {
        return THRESHOLDS.containsKey(stageNumber);
    }

    /**
     * EC-033. A timeout means completion is <strong>unknown</strong>, and replaying a maybe-completed
     * non-idempotent effect violates exactly-once — which is why S7 excludes TIMEOUT even though a timed-out
     * apply is exactly the case where retrying feels most obviously right.
     */
    public static boolean timeoutIsRetryable(int stageNumber) {
        return StageEffectContracts.forStage(stageNumber).retryableCategories()
                .contains(FailureCategory.TIMEOUT);
    }
}
