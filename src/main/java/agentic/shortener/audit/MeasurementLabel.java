package agentic.shortener.audit;

import java.util.Objects;
import java.util.Set;

/**
 * A figure that carries whether it was measured, and under what conditions, or is merely proposed. Task
 * T121. NFR-AUT-003, Constitution IX, SC-012.
 *
 * <p><strong>Structural, not conventional.</strong> {@code RunMetrics} (T122) — the layer that actually
 * exposes figures for reporting — returns every measure wrapped in this type rather than a bare {@code
 * Duration} or {@code double}, so "zero unlabelled figures across the demonstration corpus" (SC-012) is a
 * property of that return type, not a scan that has to catch every call site that forgot to label
 * something. {@link MttrCalculator} itself stays unwrapped (a {@code MttrResult} of raw values) —
 * intermediate computation, not yet a reported figure; {@code RunMetrics} is where a raw result becomes
 * something a reviewer is handed.
 *
 * <p><strong>{@code MEASURED} vs. {@code PROPOSED}</strong>: a demonstration measurement presented as a
 * production statistic is release-blocking condition 9 (T121's own Guard clause) — this is precisely the
 * distinction the label exists to make impossible to elide. {@code conditions} is required either way: for
 * {@code MEASURED} it states how (see {@code docs/evidence/mttr-method.md}'s declared population,
 * exclusions and limitations — single developer machine, injected faults, small populations, possibly
 * compressed time parameters); for {@code PROPOSED} it states the basis for the estimate. Neither status
 * tolerates a figure with nothing said about where it came from.
 */
public record MeasurementLabel<T>(T value, String status, String conditions) {

    public static final String MEASURED = "MEASURED";
    public static final String PROPOSED = "PROPOSED";

    private static final Set<String> STATUSES = Set.of(MEASURED, PROPOSED);

    public MeasurementLabel {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(status, "status");
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be one of " + STATUSES + ", got '" + status + "'");
        }
        Objects.requireNonNull(conditions, "conditions");
        if (conditions.isBlank()) {
            throw new IllegalArgumentException(
                    "conditions must not be blank — a label with nothing said about how the figure was "
                            + "obtained is a bare number wearing a status field");
        }
    }

    /** A real measurement, with the conditions it was measured under. */
    public static <T> MeasurementLabel<T> measured(T value, String conditions) {
        return new MeasurementLabel<>(value, MEASURED, conditions);
    }

    /** A target or an estimate, not yet measured, with the basis for the estimate. */
    public static <T> MeasurementLabel<T> proposed(T value, String basis) {
        return new MeasurementLabel<>(value, PROPOSED, basis);
    }

    public boolean isMeasured() {
        return MEASURED.equals(status);
    }
}
