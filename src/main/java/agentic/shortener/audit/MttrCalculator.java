package agentic.shortener.audit;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * MTTR = Σ(recovery_completed_at − failure_detected_at) over RECOVERED events ÷ count(RECOVERED events),
 * with human wait excluded from each duration. Task T118. FR-ORC-024, NFR-REC-002, plan §7.
 *
 * <p><strong>The formula is mandated; this implements it exactly.</strong> {@link
 * FailureEvent#individualRecoveryDuration()} already IS {@code (recovery_completed_at − failure_detected_at)
 * − human_wait_duration} — enforced structurally by {@link FailureEvent}'s own compact constructor, which
 * is what {@code recovered()} and {@code unrecovered()} are the only way to construct — so this class does
 * not recompute the subtraction; it averages the field that already carries it. An approximation (rounding
 * mid-calculation, excluding outliers, a different denominator) would be a different formula wearing this
 * one's name.
 */
public final class MttrCalculator {

    private MttrCalculator() {
    }

    /**
     * @throws IllegalArgumentException if no event in {@code events} is recovered — a mean over zero
     *                                   RECOVERED events is not a number, and returning one anyway (zero, or
     *                                   an empty Duration) would be a bare figure with no denominator behind
     *                                   it, exactly what T120/T121 forbid reporting
     */
    public static MttrResult calculate(List<FailureEvent> events) {
        Objects.requireNonNull(events, "events");
        List<FailureEvent> recovered = events.stream().filter(FailureEvent::recovered).toList();
        int unrecoveredCount = events.size() - recovered.size();

        if (recovered.isEmpty()) {
            throw new IllegalArgumentException(
                    "MTTR is undefined over zero RECOVERED events — " + unrecoveredCount
                            + " unrecovered event(s) cannot be averaged over a denominator of zero");
        }

        long totalMillis = recovered.stream()
                .mapToLong(e -> e.individualRecoveryDuration().toMillis())
                .sum();
        Duration mttr = Duration.ofMillis(totalMillis / recovered.size());
        return new MttrResult(mttr, recovered.size(), unrecoveredCount);
    }
}
