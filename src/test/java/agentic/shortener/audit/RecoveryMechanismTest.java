package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T116 — recovery-mechanism classification: five values, {@code fallback} unreachable. FR-ORC-024,
 * CR-032.
 */
@DisplayName("T116 — RecoveryMechanism: five values, each independently classified, fallback unreachable")
class RecoveryMechanismTest {

    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-0000000000dd");
    private static final Instant DETECTED = Instant.parse("2026-09-21T12:00:00Z");

    /**
     * The scripted scenario: one recovered {@link FailureEvent} per mechanism, each independently
     * classified — proving the five values are not a single bucket dressed up as five names.
     */
    @Test
    @DisplayName("each of the five mechanisms produces its own classification, recorded distinctly")
    void eachMechanismProducesItsOwnClassification() {
        for (RecoveryMechanism mechanism : RecoveryMechanism.values()) {
            FailureEvent event = FailureEvent.recovered(RUN_ID, "S2", "UNAVAILABLE", DETECTED,
                    DETECTED.plusSeconds(1), DETECTED.plusSeconds(11), mechanism, Duration.ZERO,
                    "recovered via " + mechanism);
            assertEquals(mechanism, event.recoveryMechanism());
        }
    }

    @Test
    @DisplayName("rollback and compensation stay distinguishable, not merged into one bucket")
    void rollbackAndCompensationAreDistinct() {
        FailureEvent rollback = FailureEvent.recovered(RUN_ID, "S9", "INTERNAL", DETECTED, DETECTED,
                DETECTED.plusSeconds(1), RecoveryMechanism.ROLLBACK, Duration.ZERO, "reversed erasable work");
        FailureEvent compensation = FailureEvent.recovered(RUN_ID, "S9", "INTERNAL", DETECTED, DETECTED,
                DETECTED.plusSeconds(1), RecoveryMechanism.COMPENSATION, Duration.ZERO,
                "forward-corrected an irreversible effect");

        assertEquals(RecoveryMechanism.ROLLBACK, rollback.recoveryMechanism());
        assertEquals(RecoveryMechanism.COMPENSATION, compensation.recoveryMechanism());
        assertFalse(rollback.recoveryMechanism() == compensation.recoveryMechanism());
    }

    @Test
    @DisplayName("NEGATIVE: exactly five values exist — fallback is not among them")
    void exactlyFiveValuesNoFallback() {
        List<String> names = Arrays.stream(RecoveryMechanism.values()).map(Enum::name).toList();
        assertEquals(5, names.size(), names.toString());
        assertFalse(names.contains("FALLBACK"),
                "FR-ORC-015 was retired (Decision K, CR-032) — an enum value no code path can emit is a "
                        + "claim, not a classification");
    }

    @Test
    @DisplayName("NEGATIVE: 'fallback' is rejected by fromDbValue — the schema's own refusal, mirrored")
    void fallbackDbValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RecoveryMechanism.fromDbValue("fallback"));
    }

    @Test
    @DisplayName("dbValue/fromDbValue round-trip for every real value")
    void dbValueRoundTrips() {
        for (RecoveryMechanism mechanism : RecoveryMechanism.values()) {
            assertEquals(mechanism, RecoveryMechanism.fromDbValue(mechanism.dbValue()));
            assertTrue(mechanism.dbValue().equals(mechanism.dbValue().toLowerCase()),
                    "must match V3's lowercase CHECK constraint values");
        }
    }
}
