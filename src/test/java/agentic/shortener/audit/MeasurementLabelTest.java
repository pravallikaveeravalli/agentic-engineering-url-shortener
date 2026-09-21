package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T121 — every figure carries MEASURED (with conditions) or PROPOSED. NFR-AUT-003, Constitution IX,
 * SC-012.
 */
@DisplayName("T121 — MeasurementLabel: MEASURED or PROPOSED, never a bare figure")
class MeasurementLabelTest {

    @Test
    @DisplayName("measured() carries the value and its conditions")
    void measuredCarriesConditions() {
        MeasurementLabel<Duration> label = MeasurementLabel.measured(Duration.ofSeconds(20),
                "single developer machine, injected faults, n=12 recovered events (docs/evidence/mttr-method.md)");

        assertEquals(Duration.ofSeconds(20), label.value());
        assertEquals(MeasurementLabel.MEASURED, label.status());
        assertTrue(label.isMeasured());
    }

    @Test
    @DisplayName("proposed() carries the value and its basis, and is not measured")
    void proposedCarriesBasis() {
        MeasurementLabel<Double> label = MeasurementLabel.proposed(0.95, "target from PVT-001's budget");

        assertEquals(0.95, label.value());
        assertEquals(MeasurementLabel.PROPOSED, label.status());
        assertFalse(label.isMeasured());
    }

    @Test
    @DisplayName("NEGATIVE: blank conditions are rejected for a MEASURED label")
    void blankConditionsRejectedForMeasured() {
        assertThrows(IllegalArgumentException.class, () -> MeasurementLabel.measured(Duration.ZERO, " "));
    }

    @Test
    @DisplayName("NEGATIVE: blank basis is rejected for a PROPOSED label")
    void blankBasisRejectedForProposed() {
        assertThrows(IllegalArgumentException.class, () -> MeasurementLabel.proposed(1, ""));
    }

    @Test
    @DisplayName("NEGATIVE: a null value is rejected — there is no such thing as a labelled absence")
    void nullValueIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new MeasurementLabel<Duration>(null, MeasurementLabel.MEASURED, "x"));
    }

    @Test
    @DisplayName("NEGATIVE: an unrecognized status is rejected — only MEASURED and PROPOSED exist")
    void unrecognizedStatusIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new MeasurementLabel<>(Duration.ZERO, "ESTIMATED", "x"));
    }
}
