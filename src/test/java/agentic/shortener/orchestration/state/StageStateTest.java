package agentic.shortener.orchestration.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T078 — the stage state machine, entry and exit criteria. FR-ORC-006. EC-029.
 *
 * <p>FR-ORC-006 has two halves and both are asserted: a stage with unmet <strong>entry</strong> criteria
 * does not execute, and one with unmet <strong>exit</strong> criteria is not marked complete. They fail
 * differently — the first wastes work on a stage that cannot succeed, the second reports work that was
 * never done.
 *
 * <p><strong>EC-029 is the exit criterion that matters most</strong>: a stage reporting success with
 * <em>no output artifact</em> must fail its exit criteria. That is the shape of every silently-skipped
 * step — the stage ran, nothing came out, and the run moved on believing it had.
 *
 * <p>Fast tier.
 */
@DisplayName("T078 stage state machine")
class StageStateTest {

    private static final Path SCHEMA =
            Path.of("specs/001-agentic-sdlc-url-shortener/contracts/workflow-state.schema.json");

    @Test
    @DisplayName("twelve stage states, matching the contract in order")
    void twelveStates() throws Exception {
        assertEquals(12, StageState.values().length,
                "T078 fixes twelve: " + List.of(StageState.values()));

        JsonNode schema = new ObjectMapper().readTree(Files.readString(SCHEMA));
        List<String> contract = new ArrayList<>();
        schema.get("properties").get("nodes").get("items").get("properties").get("state").get("enum")
                .forEach(n -> contract.add(n.asText()));

        assertEquals(contract, EnumSet.allOf(StageState.class).stream().map(Enum::name).toList(),
                "the enum and the frozen contract must not drift apart");
    }

    @Test
    @DisplayName("the terminal stage states are the four a node can finish in")
    void terminalStageStates() {
        // SUCCEEDED, FAILED, INVALIDATED and SKIPPED are ends; everything else is a node still in play.
        // SKIPPED counts as an end because the S3 conditional depends on it: an ALL join treats a
        // skipped branch as complete, which is what lets S5 proceed when S4 does not run.
        assertEquals(
                EnumSet.of(StageState.SUCCEEDED, StageState.FAILED, StageState.INVALIDATED,
                        StageState.SKIPPED),
                EnumSet.allOf(StageState.class).stream().filter(StageState::terminal)
                        .collect(java.util.stream.Collectors.toCollection(
                                () -> EnumSet.noneOf(StageState.class))));
    }

    @Test
    @DisplayName("SKIPPED satisfies an ALL join — the S3 conditional depends on it")
    void skippedSatisfiesAJoin() {
        assertTrue(StageState.SKIPPED.satisfiesJoin(),
                "S4 is skipped when there is no material ambiguity, and S5 must still become ready");
        assertTrue(StageState.SUCCEEDED.satisfiesJoin());

        assertFalse(StageState.FAILED.satisfiesJoin(),
                "EC-018: a join does not proceed while any required branch has FAILED");
        assertFalse(StageState.RUNNING.satisfiesJoin(), "nor while one is still running");
        assertFalse(StageState.INVALIDATED.satisfiesJoin(),
                "an invalidated branch produced nothing downstream can rely on");
    }

    @Test
    @DisplayName("ENTRY: a stage whose dependencies are unmet cannot execute")
    void entryCriteriaBlockExecution() {
        StageCriteria criteria = new StageCriteria();

        assertFalse(criteria.mayEnter(List.of(StageState.RUNNING)),
                "a dependency still running is the ordinary not-yet case");
        assertFalse(criteria.mayEnter(List.of(StageState.SUCCEEDED, StageState.FAILED)),
                "one failed dependency is enough to keep a stage out");
        assertFalse(criteria.mayEnter(List.of(StageState.BLOCKED)));

        assertTrue(criteria.mayEnter(List.of(StageState.SUCCEEDED)));
        assertTrue(criteria.mayEnter(List.of(StageState.SUCCEEDED, StageState.SKIPPED)),
                "succeeded and skipped together is exactly the S3/S4 conditional");
        assertTrue(criteria.mayEnter(List.of()),
                "a node with no dependencies is the entry point, and may always start");
    }

    @Test
    @DisplayName("EXIT / EC-029: a stage with NO output artifact cannot be marked complete")
    void ec029NoArtifactNoCompletion() {
        StageCriteria criteria = new StageCriteria();

        assertFalse(criteria.mayComplete(StageState.RUNNING, 0),
                "EC-029: a stage reporting success with no output artifact must fail its exit "
                        + "criteria. That is the shape of every silently-skipped step");
        assertTrue(criteria.mayComplete(StageState.RUNNING, 1));
        assertTrue(criteria.mayComplete(StageState.RUNNING, 7));
    }

    @Test
    @DisplayName("EXIT: a stage that was never running cannot complete")
    void onlyARunningStageCanComplete() {
        StageCriteria criteria = new StageCriteria();

        assertFalse(criteria.mayComplete(StageState.BLOCKED, 1),
                "a blocked stage completing would be a stage that reported work it never started");
        assertFalse(criteria.mayComplete(StageState.READY, 1));
        assertFalse(criteria.mayComplete(StageState.SUCCEEDED, 1),
                "and one already complete must not complete twice");
    }

    @Test
    @DisplayName("EXIT: SKIPPED needs no artifact, because nothing ran")
    void skippingNeedsNoArtifact() {
        // The one legitimate way to finish without output. Conflating it with completion is how a
        // silently-skipped stage gets recorded as a successful one.
        StageCriteria criteria = new StageCriteria();
        assertTrue(criteria.maySkip(StageState.BLOCKED));
        assertTrue(criteria.maySkip(StageState.READY));
        assertFalse(criteria.maySkip(StageState.RUNNING),
                "a stage that has started has already done something; skipping it would discard that");
        assertFalse(criteria.maySkip(StageState.SUCCEEDED));
    }

    @Test
    @DisplayName("both directions are asserted, which is what FR-ORC-006 actually asks for")
    void bothDirections() {
        // Stated as one assertion so a future change that covered entry and dropped exit is visible.
        StageCriteria criteria = new StageCriteria();
        assertFalse(criteria.mayEnter(List.of(StageState.FAILED)), "entry is enforced");
        assertFalse(criteria.mayComplete(StageState.RUNNING, 0), "and exit is enforced");
    }

    @Test
    @DisplayName("FALLBACK is currently unreachable, and that is recorded rather than asserted away")
    void fallbackIsRetainedButUnreachable() {
        // The contract declares twelve stage states including FALLBACK, and CR-032 did NOT remove it —
        // that record retired `fallback` from failure_event.recovery_mechanism, a different enum.
        //
        // But with FR-ORC-015 retired (Decision K), nothing currently drives a node INTO this state:
        // there is no automatic fallback to a different executor any more. It is kept because the frozen
        // contract keeps it, and flagged here because CR-032's own reasoning — "an enum value that can
        // never be emitted is a claim, not a classification" — applies to it as much as to the one that
        // record removed.
        //
        // This test asserts what is TRUE today. It does not assert that the situation is correct.
        assertTrue(EnumSet.allOf(StageState.class).contains(StageState.FALLBACK),
                "retained because the frozen contract retains it");
        assertFalse(StageState.FALLBACK.terminal(), "it is a waiting state, not an end");
        assertFalse(StageState.FALLBACK.satisfiesJoin(), "and a branch in it has not produced anything");
    }
}
