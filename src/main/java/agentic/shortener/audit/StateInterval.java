package agentic.shortener.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * One span of time a run or node spent in a given state, read from {@code state_transition}. Task T117.
 *
 * <p>Deliberately state-name-only rather than typed to {@code StageState} or a run-level state enum:
 * {@link HumanWaitTracker} only ever compares this against two literal state names ({@code
 * AWAITING_APPROVAL}, a node state, and {@code SAFE_STOP}, a run state) that live in different enums with
 * no common supertype, and importing either would pull {@code audit} toward {@code orchestration.state} —
 * the same package-cycle risk {@link agentic.shortener.audit.telemetry.StageTelemetry}'s javadoc already
 * documents for {@code orchestration.reliability}.
 */
public record StateInterval(String state, Instant enteredAt, Instant exitedAt) {

    public StateInterval {
        Objects.requireNonNull(state, "state");
        if (state.isBlank()) {
            throw new IllegalArgumentException("state must not be blank");
        }
        Objects.requireNonNull(enteredAt, "enteredAt");
        Objects.requireNonNull(exitedAt, "exitedAt");
        if (exitedAt.isBefore(enteredAt)) {
            throw new IllegalArgumentException("exitedAt cannot precede enteredAt");
        }
    }
}
