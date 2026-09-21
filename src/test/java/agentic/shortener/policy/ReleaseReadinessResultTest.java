package agentic.shortener.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T109 — ReleaseReadinessResult: all nine conditions evaluated together; the report names every
 * unmet condition by number, never merely a count. FR-ORC-025.
 */
@DisplayName("T109 — ReleaseReadinessResult: nine conditions, unmet ones named individually")
class ReleaseReadinessResultTest {

    private static ReadinessCondition met(int n) {
        return new ReadinessCondition(n, "condition " + n, true, "met");
    }

    private static ReadinessCondition unmet(int n, String detail) {
        return new ReadinessCondition(n, "condition " + n, false, detail);
    }

    private static List<ReadinessCondition> allNineMet() {
        return java.util.stream.IntStream.rangeClosed(1, 9).mapToObj(ReleaseReadinessResultTest::met).toList();
    }

    @Test
    @DisplayName("all nine met: ready")
    void allNineMetIsReady() {
        ReleaseReadinessResult result = new ReleaseReadinessResult(allNineMet());
        assertTrue(result.ready());
        assertTrue(result.unmet().isEmpty());
    }

    @Test
    @DisplayName("one unmet condition: not ready, named by number in the summary")
    void oneUnmetIsNotReady() {
        List<ReadinessCondition> conditions = new java.util.ArrayList<>(allNineMet());
        conditions.set(5, unmet(6, "traceability incomplete: FR-X-001 orphaned"));

        ReleaseReadinessResult result = new ReleaseReadinessResult(conditions);
        assertFalse(result.ready());
        assertEquals(1, result.unmet().size());
        assertEquals(6, result.unmet().get(0).number());
        assertTrue(result.summary().contains("condition 6"));
        assertTrue(result.summary().contains("FR-X-001 orphaned"), result.summary());
    }

    @Test
    @DisplayName("multiple unmet conditions are ALL named, not just the first or a count")
    void multipleUnmetAllNamed() {
        List<ReadinessCondition> conditions = new java.util.ArrayList<>(allNineMet());
        conditions.set(1, unmet(2, "mandatory policy FAIL: POL-SEC-002"));
        conditions.set(8, unmet(9, "evidence missing: docs/evidence/x.md"));

        ReleaseReadinessResult result = new ReleaseReadinessResult(conditions);
        assertFalse(result.ready());
        assertEquals(2, result.unmet().size());
        assertTrue(result.summary().contains("condition 2"));
        assertTrue(result.summary().contains("POL-SEC-002"));
        assertTrue(result.summary().contains("condition 9"));
        assertTrue(result.summary().contains("docs/evidence/x.md"));
    }

    @Test
    @DisplayName("NEGATIVE: fewer than nine conditions is rejected")
    void fewerThanNineRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReleaseReadinessResult(allNineMet().subList(0, 8)));
    }

    @Test
    @DisplayName("NEGATIVE: a duplicate condition number is rejected")
    void duplicateNumberRejected() {
        List<ReadinessCondition> conditions = new java.util.ArrayList<>(allNineMet().subList(0, 8));
        conditions.add(met(1)); // duplicate of condition 1, condition 9 never supplied
        assertThrows(IllegalArgumentException.class, () -> new ReleaseReadinessResult(conditions));
    }

    @Test
    @DisplayName("a ready summary states this is an evaluation, not the release owner's decision")
    void readySummaryStatesItIsNotADecision() {
        ReleaseReadinessResult result = new ReleaseReadinessResult(allNineMet());
        assertTrue(result.summary().toLowerCase().contains("not"),
                "the ready path must still not read as a decision: " + result.summary());
    }
}
