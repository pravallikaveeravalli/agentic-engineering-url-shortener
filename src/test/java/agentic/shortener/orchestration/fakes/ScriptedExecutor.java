package agentic.shortener.orchestration.fakes;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stage executor that does exactly what it was told, every time. Task T072. FR-ORC-030.
 *
 * <p><strong>Every reliability proof in T085–T096 runs against one of these, never against the live
 * provider.</strong> That is not a convenience: FR-ORC-030's negative clause is that reliability proofs MUST
 * NOT depend on AI availability, AI variability, or network access. A retry test driven by a real provider
 * proves that the provider failed twice that afternoon — it does not prove the bound holds, because nothing
 * made the provider fail exactly twice.
 *
 * <h2>Deliberately able to violate the contract</h2>
 *
 * <p>{@link #malformed()} throws instead of returning, and {@link #returnsNull()} returns null. Both break
 * {@code StageExecutorContract}, which is the point: the orchestrator has to survive an executor that
 * misbehaves, and the only way to prove that is to have one. {@code EveryExecutorHasAContractTest} scans
 * production classes only, so these are out of its reach by construction rather than by exemption.
 *
 * <p><strong>Stateful, and each script is its own instance.</strong> "Fail twice then succeed" needs to count,
 * so the counter lives here; sharing one instance across tests would make the third test see the first test's
 * attempts. {@link #invocations()} is exposed so a test can assert the bound was actually reached rather than
 * inferring it from the outcome.
 */
public final class ScriptedExecutor implements StageExecutor {

    /** What the script does on one invocation. */
    private interface Step {
        StageOutcome apply(StageInput input, int invocation);
    }

    private final String name;
    private final Step step;
    private final AtomicInteger invocations = new AtomicInteger();
    private final List<StageInput> seen = java.util.Collections.synchronizedList(new ArrayList<>());

    private ScriptedExecutor(String name, Step step) {
        this.name = Objects.requireNonNull(name);
        this.step = Objects.requireNonNull(step);
    }

    @Override
    public StageOutcome execute(StageInput input) {
        seen.add(input);
        return step.apply(input, invocations.incrementAndGet());
    }

    /** How many times it has been called. The bound is asserted on this, not inferred. */
    public int invocations() {
        return invocations.get();
    }

    /** Every input it was handed, in order — so a test can assert the attempt number was propagated. */
    public List<StageInput> seen() {
        return List.copyOf(seen);
    }

    public String scriptName() {
        return name;
    }

    // ==============================================================================================
    // the four scripts T072 names
    // ==============================================================================================

    /**
     * Fails {@code n} times, then succeeds. T072's first named script.
     *
     * <p>The failures are {@code UNAVAILABLE}, proposed retryable — a category every stage's declared set
     * contains, so the test exercises the retry machinery rather than the declared-set intersection. A script
     * that failed with a category the stage does not retry would pass a retry test by never retrying.
     */
    public static ScriptedExecutor failsThenSucceeds(int failures) {
        if (failures < 0) {
            throw new IllegalArgumentException("failures must not be negative");
        }
        return new ScriptedExecutor("fails " + failures + " then succeeds", (input, invocation) ->
                invocation <= failures
                        ? StageOutcome.failed(new FailureEnvelope(FailureCategory.UNAVAILABLE,
                                "scripted failure " + invocation + " of " + failures, true))
                        : StageOutcome.succeeded(
                                List.of(new ProducedArtifact(input.nodeKey() + "/output",
                                        "produced on attempt " + invocation,
                                        List.copyOf(input.inputArtifacts().keySet()))),
                                ExecutorKind.DETERMINISTIC));
    }

    /**
     * Always times out. T072's second named script.
     *
     * <p>Proposed retryable, because a timeout genuinely might be: whether it is <em>allowed</em> is the
     * stage's declared set and T084's ruling, and that is the decision under test. A script that proposed
     * "not retryable" would settle the question before the ruling ran.
     */
    public static ScriptedExecutor timesOut() {
        return new ScriptedExecutor("times out", (input, invocation) ->
                StageOutcome.failed(new FailureEnvelope(FailureCategory.TIMEOUT,
                        "scripted timeout on attempt " + invocation, true)));
    }

    /**
     * Breaks the contract by throwing. T072's third named script — "returns a malformed envelope".
     *
     * <p>Throwing <em>is</em> the malformation, and it is the only one available: {@code FailureEnvelope}
     * refuses to be constructed in a contradictory state, so an executor cannot hand back a malformed one. An
     * executor that escapes with an exception hands the orchestrator a failure with no category at all, which
     * is the case EC-032 says must be treated as permanent.
     */
    public static ScriptedExecutor malformed() {
        return new ScriptedExecutor("throws instead of returning", (input, invocation) -> {
            throw new IllegalStateException(
                    "scripted contract violation on attempt " + invocation + ": an executor must RETURN a "
                            + "classified failure, never throw one");
        });
    }

    /** Breaks the contract the other way. Nothing at all is not a classification either. */
    public static ScriptedExecutor returnsNull() {
        return new ScriptedExecutor("returns null", (input, invocation) -> null);
    }

    /**
     * Proposes retry for a category the stage has not declared retryable. T072's fourth named script.
     *
     * <p>The executor's half of the two-vote rule, arriving in good faith and still being refused. This is the
     * script that distinguishes a real intersection from a rubber stamp: if the ruling took the executor's
     * word, a stage's declared set would be documentation.
     */
    public static ScriptedExecutor proposesRetryForUndeclaredCategory(FailureCategory undeclared) {
        Objects.requireNonNull(undeclared, "undeclared");
        if (undeclared == FailureCategory.UNKNOWN) {
            // FailureEnvelope refuses this pairing outright, so a script asking for it would fail on
            // construction rather than at the ruling, and the test would prove the wrong control.
            throw new IllegalArgumentException(
                    "UNKNOWN cannot be proposed retryable at all; use another category to exercise the "
                            + "declared-set intersection");
        }
        return new ScriptedExecutor("proposes retry for undeclared " + undeclared, (input, invocation) ->
                StageOutcome.failed(new FailureEnvelope(undeclared,
                        "scripted: executor proposes " + undeclared + " is transient", true)));
    }

    /** Always succeeds. Not one of T072's four, but every test that needs a working stage needs this. */
    public static ScriptedExecutor alwaysSucceeds() {
        return new ScriptedExecutor("always succeeds", (input, invocation) ->
                StageOutcome.succeeded(
                        List.of(new ProducedArtifact(input.nodeKey() + "/output", "produced",
                                List.copyOf(input.inputArtifacts().keySet()))),
                        ExecutorKind.DETERMINISTIC));
    }
}
