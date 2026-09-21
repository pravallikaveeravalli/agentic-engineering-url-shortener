package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.StageEffectContracts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T084 — the two-vote retry ruling. FR-ORC-014 (CL-006), NFR-REL-002.
 *
 * <p>{@code retries = declared retryable set ∩ executor proposal}. Four cases, and the two asymmetric ones
 * are the whole point: <strong>the executor holds a veto, never a grant.</strong> It diagnoses; the
 * orchestrator rules, because only the orchestrator knows attempts consumed, compensation already issued,
 * replan invalidation and blocking state.
 */
@DisplayName("T084 — two votes, and either one vetoes")
class RetryRulingTest {

    private static final RetryRuling RULING = new RetryRuling();

    /** S2: declared set is {TIMEOUT, UNAVAILABLE, RATE_LIMITED}. */
    private static final int AI_STAGE = 2;

    /** S7: declared set is {UNAVAILABLE, RATE_LIMITED} — TIMEOUT excluded by EC-033. */
    private static final int IMPLEMENTATION = 7;

    @Test
    @DisplayName("case 1 of 4 — both yes: retry")
    void bothVotesYes() {
        Ruling ruling = RULING.rule(AI_STAGE,
                new FailureEnvelope(FailureCategory.TIMEOUT, "provider timed out", true));

        assertTrue(ruling.retry());
        assertTrue(ruling.declaredRetryable(), "S2 declares TIMEOUT retryable");
        assertTrue(ruling.executorProposedRetryable());
    }

    @Test
    @DisplayName("case 2 of 4 — declared yes, executor no: NO retry (the executor's veto)")
    void declaredOnly() {
        Ruling ruling = RULING.rule(AI_STAGE,
                new FailureEnvelope(FailureCategory.TIMEOUT, "timed out after the commit landed", false));

        assertFalse(ruling.retry(),
                "the stage's declared set says TIMEOUT may be retried in general; the executor says not "
                        + "this one. The executor is closer to the effect and its no is final");
        assertTrue(ruling.declaredRetryable());
        assertFalse(ruling.executorProposedRetryable());
    }

    @Test
    @DisplayName("case 3 of 4 — executor yes, declared no: NO retry (the declared set's veto)")
    void executorOnly() {
        Ruling ruling = RULING.rule(IMPLEMENTATION,
                new FailureEnvelope(FailureCategory.TIMEOUT, "apply timed out", true));

        assertFalse(ruling.retry(),
                "S7 excludes TIMEOUT because the git effect is non-idempotent (EC-033). An executor "
                        + "proposing retry cannot grant itself one, or the declared set would be "
                        + "documentation");
        assertFalse(ruling.declaredRetryable());
        assertTrue(ruling.executorProposedRetryable());
    }

    @Test
    @DisplayName("case 4 of 4 — UNKNOWN: permanent (EC-031)")
    void unknownIsPermanent() {
        Ruling ruling = RULING.rule(AI_STAGE,
                new FailureEnvelope(FailureCategory.UNKNOWN, "no idea what happened", false));

        assertFalse(ruling.retry());
        assertFalse(ruling.declaredRetryable(),
                "UNKNOWN is in no stage's declared set, so default-deny falls out of the intersection "
                        + "rather than needing a special case");
    }

    @Test
    @DisplayName("case 4 of 4, the other half — a MALFORMED failure is permanent (EC-032)")
    void malformedIsPermanent() {
        // An executor that threw or returned null gives the orchestrator no envelope at all. Ruling on
        // nothing must be possible and must be permanent: the alternative is an exception on the failure
        // path, which turns one stage's bad behaviour into a run that cannot even record why it stopped.
        Ruling ruling = RULING.rule(AI_STAGE, null);

        assertFalse(ruling.retry());
        assertFalse(ruling.declaredRetryable());
        assertFalse(ruling.executorProposedRetryable());
        assertTrue(ruling.reason().contains("no classified failure"), ruling.reason());
        assertEquals(FailureCategory.UNKNOWN, ruling.category(),
                "an absent classification is recorded AS UNKNOWN rather than as null, so a reader of the "
                        + "ruling never has to wonder which kind of nothing it was");
    }

    @Test
    @DisplayName("every ruling records BOTH signatures and the reason")
    void everyRulingRecordsBothSignatures() {
        // T084's artifact: "every ruling recorded with both signatures". A record saying only "no retry"
        // cannot be audited — the interesting question afterwards is always WHICH vote refused.
        for (FailureCategory category : FailureCategory.declared()) {
            boolean proposable = category != FailureCategory.UNKNOWN;
            Ruling ruling = RULING.rule(AI_STAGE,
                    new FailureEnvelope(category, "detail for " + category, proposable));

            assertEquals(category, ruling.category());
            assertEquals(AI_STAGE, ruling.stageNumber());
            assertFalse(ruling.reason().isBlank(), category + " produced a blank reason");
            assertTrue(ruling.reason().contains("declared"), ruling.reason());
            assertTrue(ruling.reason().contains("executor"), ruling.reason());
        }
    }

    @Test
    @DisplayName("the ruling is exactly the intersection, checked across every stage and category")
    void rulingIsTheIntersectionEverywhere() {
        // The four named cases are examples; this is the property. Written as a sweep so a stage whose
        // declared set changes later cannot pass the four examples while breaking somewhere else.
        List<String> wrong = new java.util.ArrayList<>();
        for (int stage = 1; stage <= 12; stage++) {
            var declared = StageEffectContracts.forStage(stage).retryableCategories();
            for (FailureCategory category : FailureCategory.declared()) {
                if (category == FailureCategory.UNKNOWN) {
                    continue;
                }
                for (boolean proposal : List.of(true, false)) {
                    Ruling ruling = RULING.rule(stage,
                            new FailureEnvelope(category, "d", proposal));
                    boolean expected = declared.contains(category) && proposal;
                    if (ruling.retry() != expected) {
                        wrong.add("S" + stage + " " + category + " proposal=" + proposal
                                + " ruled " + ruling.retry() + ", expected " + expected);
                    }
                }
            }
        }
        assertEquals(List.of(), wrong, "the ruling must be the intersection and nothing else: " + wrong);
    }

    @Test
    @DisplayName("nothing defaults to retryable — no category retries at a stage that declares none")
    void defaultDeny() {
        // S4 has no executor and an empty declared set. Every category must rule permanent there, however
        // the proposal arrives. An unrecognized failure never defaults to retryable, mirroring EC-025's
        // rule for policy checks.
        for (FailureCategory category : FailureCategory.declared()) {
            boolean proposable = category != FailureCategory.UNKNOWN;
            assertFalse(RULING.rule(4, new FailureEnvelope(category, "d", proposable)).retry(),
                    category + " retried at S4, whose declared set is empty");
        }
    }

    @Test
    @DisplayName("a stage with no contract cannot be ruled on at all")
    void unknownStageIsRefused() {
        // Not "permanent by default": a stage number the contracts do not know is a programming error, and
        // silently ruling permanent would hide it behind behaviour that looks deliberate.
        assertThrows(IllegalArgumentException.class,
                () -> RULING.rule(99, new FailureEnvelope(FailureCategory.TIMEOUT, "d", true)));
    }
}
