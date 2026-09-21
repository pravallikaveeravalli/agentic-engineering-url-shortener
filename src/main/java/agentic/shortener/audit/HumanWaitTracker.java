package agentic.shortener.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Time spent in {@code AWAITING_APPROVAL} or {@code SAFE_STOP}, measured separately from recovery
 * duration. Task T117. plan §7's declared exclusion.
 *
 * <p><strong>Why this is excluded, not merely tracked</strong>: including human wait would make MTTR a
 * function of when a person was at a keyboard, measuring reviewer latency rather than system recovery. The
 * exclusion is declared here — a named computation with its own test — never silent, which is what would
 * happen if a caller simply omitted gate-wait time from a hand-rolled duration calculation with nothing
 * asserting that it did.
 *
 * <p>Pure over {@link StateInterval}s a caller reads from {@code state_transition}; this class issues no
 * query of its own; see {@link StateInterval}'s own javadoc for why.
 */
public final class HumanWaitTracker {

    /** {@code StageState.AWAITING_APPROVAL} (a node state) and the run-level {@code SAFE_STOP} state. */
    private static final Set<String> HUMAN_WAIT_STATES = Set.of("AWAITING_APPROVAL", "SAFE_STOP");

    private HumanWaitTracker() {
    }

    /**
     * The total time, within {@code [windowStart, windowEnd]}, that any interval in {@code intervals} spent
     * in a human-wait state. Intervals outside the window, or in another state, contribute nothing; an
     * interval that only partially overlaps the window is clipped to its overlapping portion.
     */
    public static Duration humanWaitWithin(List<StateInterval> intervals, Instant windowStart,
                                            Instant windowEnd) {
        Objects.requireNonNull(intervals, "intervals");
        Objects.requireNonNull(windowStart, "windowStart");
        Objects.requireNonNull(windowEnd, "windowEnd");
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("windowEnd cannot precede windowStart");
        }

        Duration total = Duration.ZERO;
        for (StateInterval interval : intervals) {
            if (!HUMAN_WAIT_STATES.contains(interval.state())) {
                continue;
            }
            Instant overlapStart = maxOf(interval.enteredAt(), windowStart);
            Instant overlapEnd = minOf(interval.exitedAt(), windowEnd);
            if (overlapEnd.isAfter(overlapStart)) {
                total = total.plus(Duration.between(overlapStart, overlapEnd));
            }
        }
        return total;
    }

    private static Instant maxOf(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant minOf(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
