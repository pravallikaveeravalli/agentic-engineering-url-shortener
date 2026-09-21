package agentic.shortener.orchestration.executor;

import agentic.shortener.orchestration.reliability.FailureCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract every {@link StageExecutor} implementation must satisfy. Task T069. FR-ORC-028.
 *
 * <p>An abstract test rather than a checklist in a comment: a new executor extends this and inherits the
 * obligations, so the way to ship an executor that does not meet them is to delete a test class rather
 * than to forget a rule. {@code EveryExecutorHasAContractTest} closes the deletion route.
 *
 * <h2>What this contract deliberately does NOT require</h2>
 *
 * <p><strong>Determinism.</strong> Six of the twelve stages are AI-capable and their output varies by
 * design (spec §Stage Executor Model: <em>output feeds the human gate, so variability is safe</em>).
 * A contract demanding identical output for identical input would be satisfiable only by the five
 * deterministic engines, so it would either exclude the AI executors from the contract — the opposite of
 * FR-ORC-028's "one interface for every stage" — or quietly assert something false about them.
 * Determinism is asserted per engine by T071, where it is actually true.
 *
 * <h2>What it does require, and why each matters</h2>
 *
 * <ul>
 *   <li><strong>A failure is returned, never thrown.</strong> The orchestrator rules on retry from a
 *       classified envelope; an exception escaping an executor arrives with no category, and a category
 *       the orchestrator cannot read defaults — under T084 — to permanent. So a thrown failure is a
 *       retryable failure silently reclassified, which is why this is a contract term and not a style
 *       preference.
 *   <li><strong>Success carries an artifact.</strong> EC-029 in its executor form: a stage reporting
 *       success with nothing to show fails its exit criteria, and {@code TransitionRules} refuses the
 *       transition. An executor that could report empty success would push that refusal to runtime.
 *   <li><strong>The kind is never {@code HUMAN}.</strong> FR-ORC-031: {@code HUMAN} is the outcome of a
 *       no-plan-gate decision, never something an executor may claim about itself.
 * </ul>
 */
public abstract class StageExecutorContract {

    /** The implementation under test. A fresh instance per call, so one test cannot bleed into another. */
    protected abstract StageExecutor executorUnderTest();

    /** An input the implementation can actually do its work on. */
    protected abstract StageInput validInput();

    @Test
    @DisplayName("contract: execute returns an outcome and never null")
    void executeReturnsAnOutcome() {
        StageOutcome outcome = executorUnderTest().execute(validInput());
        assertNotNull(outcome, "an executor that returns null has reported nothing at all");
    }

    @Test
    @DisplayName("contract: a failure is RETURNED as a classified envelope, never thrown")
    void aFailureIsReturnedNeverThrown() {
        StageOutcome outcome = assertDoesNotThrow(() -> executorUnderTest().execute(validInput()),
                "an exception escaping an executor arrives at the orchestrator with no category, and an "
                        + "unclassifiable failure is ruled permanent — so throwing silently reclassifies "
                        + "a retryable failure (FR-ORC-014, T084)");

        if (!outcome.succeeded()) {
            assertNotNull(outcome.failure(), "a failed outcome must carry its envelope");
            assertTrue(FailureCategory.declared().contains(outcome.failure().category()),
                    "the category must come from the closed standard set, so a stage's declared "
                            + "retryable set can be expressed over it: " + outcome.failure().category());
        }
    }

    @Test
    @DisplayName("contract: a succeeded outcome carries at least one artifact (EC-029)")
    void succeededOutcomeCarriesAnArtifact() {
        StageOutcome outcome = executorUnderTest().execute(validInput());
        if (outcome.succeeded()) {
            assertFalse(outcome.producedArtifacts().isEmpty(),
                    "EC-029: a stage reporting success with no output artifact fails its exit criteria");
        }
    }

    @Test
    @DisplayName("contract: the executor never reports kind HUMAN")
    void executorNeverClaimsToBeHuman() {
        StageOutcome outcome = executorUnderTest().execute(validInput());
        if (outcome.succeeded()) {
            assertNotEquals(ExecutorKind.HUMAN, outcome.executorKind(),
                    "FR-ORC-031: HUMAN is a no-plan-gate outcome, never a claim an executor makes about "
                            + "itself");
        }
    }

    @Test
    @DisplayName("contract: the input's artifacts are not the executor's to modify")
    void inputArtifactsAreImmutable() {
        StageInput input = validInput();
        assertThrows(UnsupportedOperationException.class,
                () -> input.inputArtifacts().put("smuggled", "content"),
                "a stage that could mutate its input would let one node's work change what a sibling "
                        + "reads, which is the shared-state corruption FR-ORC-003 forbids");
    }

    @Test
    @DisplayName("contract: the same executor handles a stage it was not written for")
    void executorDoesNotRequireItsOwnStageNumber() {
        // FR-ORC-028's "a requirement outside the three demonstration scenarios flows through the same
        // path", in its structural form: invocation must not depend on the executor recognising its
        // caller. An executor is free to FAIL on work it cannot do — what it must not do is break the
        // interface, because then the orchestrator has no envelope to rule on.
        StageInput foreign = new StageInput(UUID.randomUUID(), "S12", 12, 1, Map.of("brief", "text"));
        StageOutcome outcome = assertDoesNotThrow(() -> executorUnderTest().execute(foreign));
        assertNotNull(outcome);
    }
}
