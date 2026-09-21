package agentic.shortener.audit;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One failure and, if it recovered, the full story of that recovery. Task T115. FR-ORC-024, plan §7.
 *
 * <p>The nine fields plan §7 names, {@code runId} treated as the correlation identifier the same way
 * {@link AuditEvent} treats it (not counted among the nine, always required): {@code failureCategory},
 * {@code failureDetectedAt}, {@code recoveryStartedAt}, {@code recoveryCompletedAt},
 * {@code individualRecoveryDuration}, {@code humanWaitDuration}, {@code recoveryMechanism},
 * {@code recovered}, {@code stageId}.
 *
 * <p><strong>Written exactly once, fully formed.</strong> V3's {@code REVOKE UPDATE} on {@code
 * failure_event} means there is no later statement that could fill in a recovery outcome after an initial
 * "detected" row — so, unlike a live system that would record detection and completion as separate events,
 * this type is only ever constructed once the failure's fate is already settled: either {@link #recovered}
 * with the full recovery timing, or {@link #unrecovered} with whatever was attempted before it was given
 * up on. A caller building this record too early, before the fate is known, has nowhere to put the
 * eventual outcome.
 *
 * <p><strong>{@code individualRecoveryDuration} excludes human wait by construction</strong>, not by
 * convention — the two factory methods below are the only way to build a recovered instance, and both
 * compute it as {@code (recoveryCompletedAt - failureDetectedAt) - humanWaitDuration}. The compact
 * constructor re-derives and checks it, so a caller cannot pass an inconsistent value even by using the
 * canonical constructor directly.
 */
public record FailureEvent(Long failureEventId, UUID runId, String stageId, String failureCategory,
                            Instant failureDetectedAt, Instant recoveryStartedAt, Instant recoveryCompletedAt,
                            RecoveryMechanism recoveryMechanism, Duration humanWaitDuration,
                            Duration individualRecoveryDuration, boolean recovered, String detail) {

    /** V3's {@code failure_category_values} CHECK — the Java side of the same closed set. */
    private static final Set<String> FAILURE_CATEGORIES = Set.of(
            "TIMEOUT", "UNAVAILABLE", "RATE_LIMITED", "INVALID_INPUT", "INTERNAL", "UNKNOWN");

    public FailureEvent {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(stageId, "stageId");
        if (stageId.isBlank()) {
            throw new IllegalArgumentException("stageId must not be blank");
        }
        Objects.requireNonNull(failureCategory, "failureCategory");
        if (!FAILURE_CATEGORIES.contains(failureCategory)) {
            throw new IllegalArgumentException(
                    "unrecognized failureCategory: '" + failureCategory + "'");
        }
        Objects.requireNonNull(failureDetectedAt, "failureDetectedAt");
        Objects.requireNonNull(humanWaitDuration, "humanWaitDuration");
        if (humanWaitDuration.isNegative()) {
            throw new IllegalArgumentException("humanWaitDuration must not be negative");
        }
        Objects.requireNonNull(detail, "detail");
        if (detail.isBlank()) {
            throw new IllegalArgumentException(
                    "detail must not be blank — an unexplained failure_event row is not actionable "
                            + "(matches V3's own NOT NULL detail column)");
        }
        if (recoveryStartedAt != null && recoveryStartedAt.isBefore(failureDetectedAt)) {
            throw new IllegalArgumentException("recoveryStartedAt cannot precede failureDetectedAt");
        }

        boolean actuallyRecovered = recoveryCompletedAt != null;
        if (recovered != actuallyRecovered) {
            throw new IllegalArgumentException(
                    "recovered must equal (recoveryCompletedAt != null) — matches V3's "
                            + "failure_event_recovered_matches_recovered_at CHECK");
        }
        if (actuallyRecovered) {
            if (recoveryCompletedAt.isBefore(failureDetectedAt)) {
                throw new IllegalArgumentException("recoveryCompletedAt cannot precede failureDetectedAt");
            }
            Objects.requireNonNull(individualRecoveryDuration,
                    "a recovered event must carry its computed duration");
            Duration expected = Duration.between(failureDetectedAt, recoveryCompletedAt)
                    .minus(humanWaitDuration);
            if (!expected.equals(individualRecoveryDuration)) {
                throw new IllegalArgumentException(
                        "individualRecoveryDuration must equal (recoveryCompletedAt - failureDetectedAt) "
                                + "- humanWaitDuration; expected " + expected + " but got "
                                + individualRecoveryDuration);
            }
        } else if (individualRecoveryDuration != null) {
            throw new IllegalArgumentException(
                    "an unrecovered event has no completion time to measure a duration from — "
                            + "matches V3's failure_event_duration_present_iff_recovered CHECK");
        }
    }

    /**
     * A failure that recovered. {@code individualRecoveryDuration} is computed here, not accepted as a
     * parameter — the only way to get it wrong would be to pass it in.
     */
    public static FailureEvent recovered(UUID runId, String stageId, String failureCategory,
            Instant failureDetectedAt, Instant recoveryStartedAt, Instant recoveryCompletedAt,
            RecoveryMechanism recoveryMechanism, Duration humanWaitDuration, String detail) {
        Objects.requireNonNull(recoveryCompletedAt, "recoveryCompletedAt");
        Objects.requireNonNull(humanWaitDuration, "humanWaitDuration");
        Duration individual = Duration.between(failureDetectedAt, recoveryCompletedAt).minus(humanWaitDuration);
        return new FailureEvent(null, runId, stageId, failureCategory, failureDetectedAt, recoveryStartedAt,
                recoveryCompletedAt, recoveryMechanism, humanWaitDuration, individual, true, detail);
    }

    /**
     * A failure given up on — ruled permanent, or a bound exhausted, with no successful recovery.
     * {@code recoveryMechanism} is {@code null} when no recovery was ever attempted (e.g. a permanent
     * failure that stopped after one attempt), or the mechanism that was tried and did not succeed.
     */
    public static FailureEvent unrecovered(UUID runId, String stageId, String failureCategory,
            Instant failureDetectedAt, Instant recoveryStartedAt, RecoveryMechanism recoveryMechanism,
            Duration humanWaitDuration, String detail) {
        return new FailureEvent(null, runId, stageId, failureCategory, failureDetectedAt, recoveryStartedAt,
                null, recoveryMechanism, humanWaitDuration, null, false, detail);
    }
}
