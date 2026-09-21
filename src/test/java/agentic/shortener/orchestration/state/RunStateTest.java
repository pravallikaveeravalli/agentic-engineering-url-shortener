package agentic.shortener.orchestration.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T077 — the run state machine. FR-ORC-017 (CL-005), NFR-REL-001.
 *
 * <p><strong>A state cannot be both terminal and resumable.</strong> That was a contradiction in the
 * approved specification until CL-005 resolved it, and this file is what keeps it resolved. The terminal
 * set is exactly {@code COMPLETED}, {@code REJECTED}, {@code ABANDONED}; {@code SAFE_STOP} is
 * <em>suspended</em> and non-terminal, because a suspended run is one a human can still decide about.
 *
 * <p>The two schema conditionals are asserted here as properties of the type, and again in the database
 * as CHECK constraints (V4), and again against the contract document itself — three levels, because the
 * thing being prevented is a run that claims to be finished and is not, or vice versa.
 *
 * <p>Fast tier.
 */
@DisplayName("T077 run state machine")
class RunStateTest {

    private static final Path SCHEMA =
            Path.of("specs/001-agentic-sdlc-url-shortener/contracts/workflow-state.schema.json");

    @Test
    @DisplayName("six run states, exactly as the contract declares them")
    void sixStates() {
        assertEquals(6, RunState.values().length, "FR-ORC-017 fixes six: " + List.of(RunState.values()));
        assertEquals(
                Set.of("PENDING", "RUNNING", "SAFE_STOP", "COMPLETED", "REJECTED", "ABANDONED"),
                EnumSet.allOf(RunState.class).stream().map(Enum::name)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    @DisplayName("the terminal set is EXACTLY three")
    void terminalSetIsExactlyThree() {
        Set<RunState> terminal = EnumSet.allOf(RunState.class).stream()
                .filter(RunState::terminal)
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(RunState.class)));

        assertEquals(EnumSet.of(RunState.COMPLETED, RunState.REJECTED, RunState.ABANDONED), terminal,
                "a fourth terminal state would be a way for a run to end that nothing reports on");
    }

    @Test
    @DisplayName("SAFE_STOP is NON-terminal — the whole of CL-005")
    void safeStopIsNotTerminal() {
        assertFalse(RunState.SAFE_STOP.terminal(),
                "a suspended run is one a human can still decide about. Terminal-and-resumable is the "
                        + "contradiction CL-005 resolved, and this assertion is what keeps it resolved");
        assertTrue(RunState.SAFE_STOP.suspended());
    }

    @Test
    @DisplayName("SAFE_STOP is the only suspended state")
    void safeStopIsTheOnlySuspendedState() {
        List<RunState> suspended = EnumSet.allOf(RunState.class).stream()
                .filter(RunState::suspended).toList();
        assertEquals(List.of(RunState.SAFE_STOP), suspended);
    }

    @Test
    @DisplayName("no state is both terminal and suspended")
    void noStateIsBoth() {
        // Asserted directly rather than inferred from the two tests above, because the property that
        // matters is the CONJUNCTION — and a future state could satisfy each test separately.
        for (RunState state : RunState.values()) {
            assertFalse(state.terminal() && state.suspended(),
                    state + " is both terminal and suspended, which is the contradiction CL-005 fixed");
        }
    }

    @Test
    @DisplayName("CONDITIONAL 1: terminalState is non-null IFF the run is terminal")
    void terminalStateIffTerminal() {
        for (RunState state : RunState.values()) {
            assertEquals(state.terminal(), state.requiresTerminalState(),
                    state + ": a terminal run with no terminalState is unexplained, and a live run "
                            + "carrying one has already been declared finished");
        }
    }

    @Test
    @DisplayName("CONDITIONAL 2: autoAbandonAt and suspensionReason are non-null IFF suspended")
    void autoAbandonIffSuspended() {
        for (RunState state : RunState.values()) {
            assertEquals(state.suspended(), state.requiresAutoAbandonAt(),
                    state + ": a suspended run with no disclosed abandonment time is one nobody can "
                            + "be told about");
            assertEquals(state.suspended(), state.requiresSuspensionReason(),
                    state + ": and one with no reason is a suspension nobody can act on");
        }
    }

    @Test
    @DisplayName("the enum and the CONTRACT agree, so neither can drift alone")
    void enumMatchesTheContract() throws Exception {
        // The contract was frozen at Gate 7. Reading it here means a change to either side fails this
        // test rather than being discovered when a response stops conforming.
        JsonNode schema = new ObjectMapper().readTree(Files.readString(SCHEMA));

        List<String> contractStates = new ArrayList<>();
        schema.get("properties").get("state").get("enum").forEach(n -> contractStates.add(n.asText()));
        List<String> enumStates = EnumSet.allOf(RunState.class).stream().map(Enum::name).toList();
        assertEquals(contractStates, enumStates, "run states must match the contract, in order");

        List<String> contractTerminal = new ArrayList<>();
        schema.get("properties").get("terminalState").get("enum").forEach(
                n -> { if (!n.isNull()) { contractTerminal.add(n.asText()); } });
        List<String> enumTerminal = EnumSet.allOf(RunState.class).stream()
                .filter(RunState::terminal).map(Enum::name).toList();
        assertEquals(contractTerminal, enumTerminal, "and so must the terminal set");
    }

    @Test
    @DisplayName("the V4 migration enforces both conditionals in the database too")
    void migrationEnforcesTheConditionals() throws Exception {
        // Three levels for one property. The type can be bypassed by a direct write, and the contract
        // only governs responses; the CHECK constraint is the one nothing gets past.
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V4__orchestration_graph.sql"));

        assertTrue(migration.contains("run_terminal_state_iff_terminal"),
                "the terminal conditional must be a CHECK constraint, not only a Java invariant");
        assertTrue(migration.contains("run_auto_abandon_iff_safe_stop"));
        assertTrue(migration.contains("run_suspension_reason_iff_safe_stop"));
    }

    @Test
    @DisplayName("PENDING and RUNNING are neither terminal nor suspended")
    void liveStatesAreLive() {
        for (RunState live : List.of(RunState.PENDING, RunState.RUNNING)) {
            assertFalse(live.terminal(), live + " must not be terminal");
            assertFalse(live.suspended(), live + " must not be suspended");
        }
    }
}
