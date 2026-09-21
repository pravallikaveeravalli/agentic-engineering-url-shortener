package agentic.shortener.orchestration.store;

import agentic.shortener.orchestration.state.RunState;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A run as the store holds it. Task T074, T077.
 *
 * <p>The two conditionals FR-ORC-017 (CL-005) fixes are invariants of this type as well as CHECK
 * constraints in V4: {@code terminalState} is non-null iff terminal, and {@code autoAbandonAt} plus
 * {@code suspensionReason} are non-null iff suspended. Asserting them here means a reload that violated
 * them fails at the boundary rather than downstream, which matters because FR-ORC-004's negative clause
 * forbids recovery reporting a state it cannot substantiate (EC-016).
 */
public record PersistedRun(UUID runId, RunState state, RunState terminalState,
                           String policySetVersion, Instant submittedAt, Instant lastActivityAt,
                           Instant autoAbandonAt, String suspensionReason, UUID correlationId) {

    public PersistedRun {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(policySetVersion, "policySetVersion");
        Objects.requireNonNull(correlationId, "correlationId");

        if (state.requiresTerminalState() != (terminalState != null)) {
            throw new IllegalArgumentException(
                    "terminalState is non-null iff the run is terminal (CL-005): state=" + state
                            + " terminalState=" + terminalState);
        }
        if (state.requiresAutoAbandonAt() != (autoAbandonAt != null)) {
            throw new IllegalArgumentException(
                    "autoAbandonAt is non-null iff the run is suspended (CL-005): state=" + state);
        }
        if (state.requiresSuspensionReason() != (suspensionReason != null
                && !suspensionReason.isBlank())) {
            throw new IllegalArgumentException(
                    "suspensionReason is non-null iff the run is suspended (CL-005): state=" + state);
        }
    }
}
