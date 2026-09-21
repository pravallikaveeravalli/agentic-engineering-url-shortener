package agentic.shortener.orchestration.state;

import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Suspension, resumption and automatic abandonment. Tasks T091, T092. FR-ORC-017, FR-ORC-032.
 *
 * <h2>Why this writes the run row itself rather than going through the store's transitionRun</h2>
 *
 * <p>V4 makes {@code auto_abandon_at} and {@code suspension_reason} non-null <strong>iff</strong> the state is
 * {@code SAFE_STOP}. {@code JdbcRunStore.transitionRun} writes only the state, so it can neither enter
 * SAFE_STOP (the columns would still be null) nor leave it (they would still be set) — the CHECK constraints
 * refuse both. That is the schema working: entering suspension without disclosing when the run will be
 * abandoned, or resuming while still advertising a reaping, are exactly the halfway states it forbids. So the
 * transitions that cross the SAFE_STOP boundary belong here, where all three columns move together, and
 * {@code transitionRun} now refuses them by name rather than failing on a constraint.
 *
 * <h2>Leaving SAFE_STOP has exactly two authorities</h2>
 *
 * <p>{@link #resumeOnHumanDecision} and {@link #abandonIfExpired}. FR-ORC-017 allows no third, and
 * {@link TransitionRules} already refuses a resume that carries neither authority — the difference here is that
 * this class is the only code that <em>can</em> write the row, so the rule is enforced by what exists rather
 * than by what remembers to check.
 *
 * <p>{@link #recordActivity} deliberately does not resume. It moves the deadline, because the deadline is
 * derived from last activity, and that is all it does: a reviewer asking a question on a gate is attention, not
 * a decision, and treating it as one would let a run restart without anybody approving anything.
 */
public final class SafeStopHandler {

    private final ConnectionSource connections;
    private final Clock clock;
    private final RetentionPolicy retention;

    public SafeStopHandler(ConnectionSource connections, Clock clock, RetentionPolicy retention) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retention = Objects.requireNonNull(retention, "retention");
    }

    // ==============================================================================================
    // T091 — suspension
    // ==============================================================================================

    /** Suspends the run, disclosing both the trigger and when it will be abandoned. */
    public void suspend(UUID runId, SuspensionTrigger trigger, String detail) {
        Objects.requireNonNull(trigger, "trigger");
        // The trigger name leads the reason, because the reason is what a triaging human reads first and the
        // five classes need different responses.
        String reason = trigger.name() + ": " + Objects.requireNonNull(detail, "detail");
        suspendWithReason(runId, reason, null);
    }

    /**
     * Suspends because an applied effect has no known compensating action.
     *
     * <p>T091's guard is that safe-stop MUST NOT leave partially applied effects unrecorded, and
     * {@code CompensationRegister} correctly refuses to log a NONE_KNOWN as a compensation — nothing was
     * applied. So the effect is named in the audit row's {@code affected_artifact}, where a reviewer can find
     * what was left half-applied without reading prose.
     */
    public void suspendForUncompensatedEffect(UUID runId, String nodeKey, String effectKey, String detail) {
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(effectKey, "effectKey");
        String reason = SuspensionTrigger.EFFECT_WITH_NO_COMPENSATING_ACTION.name()
                + ": node " + nodeKey + " applied effect '" + effectKey
                + "' and there is no known compensating action for it — " + detail
                + ". Nothing was attempted; the run stops with the effect recorded (FR-ORC-016 rule 4)";
        suspendWithReason(runId, reason, effectKey);
    }

    private void suspendWithReason(UUID runId, String reason, String affectedEffectKey) {
        Objects.requireNonNull(runId, "runId");
        Instant now = clock.instant();

        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                // One statement moves state, reason and deadline together. Anything less would leave a row
                // the CHECK constraints reject, which is the schema refusing a halfway suspension.
                String sql = "UPDATE workflow_run SET state = 'SAFE_STOP', suspension_reason = ?, "
                        + "auto_abandon_at = ?, last_activity_at = ? "
                        + "WHERE run_id = ? AND state = 'RUNNING'";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, reason);
                    ps.setTimestamp(2, Timestamp.from(retention.autoAbandonAt(now)));
                    ps.setTimestamp(3, Timestamp.from(now));
                    ps.setObject(4, runId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "run " + runId + " is not RUNNING, so it cannot be suspended");
                    }
                }
                insertTransition(c, runId, "RUNNING", "SAFE_STOP", reason, now);
                insertAudit(c, runId, "SUSPENSION",
                        affectedEffectKey != null ? affectedEffectKey : "run:" + runId,
                        "SUSPENDED", reason, now);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw asIllegalState("failed to suspend run " + runId, e);
        }
    }

    /** One of the two authorities FR-ORC-017 permits for leaving SAFE_STOP. */
    public void resumeOnHumanDecision(UUID runId, String reason) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(reason, "reason");
        Instant now = clock.instant();

        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                // Both suspension columns are cleared in the same statement that changes the state, or the
                // run would be RUNNING while still advertising a reaping — which the constraint refuses.
                String sql = "UPDATE workflow_run SET state = 'RUNNING', suspension_reason = NULL, "
                        + "auto_abandon_at = NULL, last_activity_at = ? "
                        + "WHERE run_id = ? AND state = 'SAFE_STOP'";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setTimestamp(1, Timestamp.from(now));
                    ps.setObject(2, runId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "run " + runId + " is not suspended, so there is nothing to resume");
                    }
                }
                insertTransition(c, runId, "SAFE_STOP", "RUNNING",
                        "resumed on a recorded human decision: " + reason, now);
                insertAudit(c, runId, "RESUMPTION", "run:" + runId, "RESUMED",
                        "resumed on a recorded human decision: " + reason, now);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw asIllegalState("failed to resume run " + runId, e);
        }
    }

    // ==============================================================================================
    // T092 — retention
    // ==============================================================================================

    /**
     * Records that the run received attention, which moves the disclosed deadline.
     *
     * <p>EC-036. Does <strong>not</strong> resume: attention is not a decision, and a run that restarted
     * because somebody looked at it would be advancing past a gate nobody answered.
     */
    public void recordActivity(UUID runId, String what) {
        Objects.requireNonNull(runId, "runId");
        Instant now = clock.instant();

        try (Connection c = connections.get()) {
            // auto_abandon_at is recomputed here rather than left alone, because it is a DERIVED disclosure:
            // the stored value exists so a reviewer can be told the date, and a stored date that no longer
            // matches last_activity_at would be a disclosure that has quietly become false.
            String sql = "UPDATE workflow_run SET last_activity_at = ?, "
                    + "auto_abandon_at = CASE WHEN state = 'SAFE_STOP' THEN ? ELSE auto_abandon_at END "
                    + "WHERE run_id = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setTimestamp(2, Timestamp.from(retention.autoAbandonAt(now)));
                ps.setObject(3, runId);
                if (ps.executeUpdate() != 1) {
                    throw new IllegalStateException("no such run: " + runId);
                }
            }
            insertAudit(c, runId, "ACTIVITY", "run:" + runId, "RECORDED",
                    "activity recorded, resetting the idle clock (EC-036): " + what, now);
        } catch (Exception e) {
            throw asIllegalState("failed to record activity on run " + runId, e);
        }
    }

    /**
     * Abandons the run if its disclosed deadline has passed. The other authority for leaving SAFE_STOP.
     *
     * @return true if the run was abandoned
     * @throws IllegalStateException if the run is not suspended. A RUNNING run has no disclosed deadline —
     *                               the constraint forbids one — so reaping it on age would mean inventing a
     *                               deadline nobody was told about
     */
    public boolean abandonIfExpired(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        Instant now = clock.instant();

        try (Connection c = connections.get()) {
            Instant deadline = suspendedDeadline(c, runId);
            if (!retention.expired(deadline, now)) {
                return false;
            }

            c.setAutoCommit(false);
            try {
                String reason = "idle retention expired. Abandoned under " + RetentionPolicy.VERSION
                        + " by actor type system, on the authority of the pre-approved retention policy. "
                        + "This is a run-lifecycle transition and not a data purge: every record is kept "
                        + "(CR-017). It is not an approval and satisfies no pending gate (EC-037)";

                // Terminal, so terminal_state is set and both suspension columns are cleared — a terminal run
                // still advertising a future reaping is nonsense, and the constraints say so.
                String sql = "UPDATE workflow_run SET state = 'ABANDONED', terminal_state = 'ABANDONED', "
                        + "suspension_reason = NULL, auto_abandon_at = NULL, last_activity_at = ? "
                        + "WHERE run_id = ? AND state = 'SAFE_STOP'";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setTimestamp(1, Timestamp.from(now));
                    ps.setObject(2, runId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "run " + runId + " stopped being suspended before it could be abandoned");
                    }
                }
                insertTransition(c, runId, "SAFE_STOP", "ABANDONED", reason, now);
                insertAudit(c, runId, "AUTO_ABANDON", "run:" + runId, "ABANDONED", reason, now);
                c.commit();
                return true;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw asIllegalState("failed to evaluate retention for run " + runId, e);
        }
    }

    private static Instant suspendedDeadline(Connection c, UUID runId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT state, auto_abandon_at FROM workflow_run WHERE run_id = ?")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("no such run: " + runId);
                }
                String state = rs.getString("state");
                Timestamp deadline = rs.getTimestamp("auto_abandon_at");
                if (!"SAFE_STOP".equals(state) || deadline == null) {
                    throw new IllegalStateException(
                            "run " + runId + " is " + state + ", not suspended, so it has no disclosed "
                                    + "abandonment time. Reaping it on age would mean inventing a deadline "
                                    + "nobody was told about");
                }
                return deadline.toInstant();
            }
        }
    }

    // ==============================================================================================

    /** Filed against no node: a run transition belongs to the run. V4 drops the NOT NULL for this. */
    private static void insertTransition(Connection c, UUID runId, String from, String to, String reason,
                                        Instant now) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO state_transition (run_id, node_key, from_state, to_state, occurred_at, reason) "
                        + "VALUES (?, NULL, ?, ?, ?, ?)")) {
            ps.setObject(1, runId);
            ps.setString(2, from);
            ps.setString(3, to);
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setString(5, reason);
            ps.executeUpdate();
        }
    }

    private static void insertAudit(Connection c, UUID runId, String action, String affectedArtifact,
                                    String result, String reason, Instant now) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_record (actor_type, action, occurred_at, affected_artifact, result, "
                        + "reason, run_id, correlation_id) VALUES ('system', ?, ?, ?, ?, ?, ?, "
                        + "(SELECT correlation_id FROM workflow_run WHERE run_id = ?))")) {
            ps.setString(1, action);
            ps.setTimestamp(2, Timestamp.from(now));
            ps.setString(3, affectedArtifact);
            ps.setString(4, result);
            ps.setString(5, reason);
            ps.setObject(6, runId);
            ps.setObject(7, runId);
            ps.executeUpdate();
        }
    }

    /** Keeps a deliberate refusal readable instead of burying it under a generic wrapper. */
    private static IllegalStateException asIllegalState(String context, Exception e) {
        if (e instanceof IllegalStateException already) {
            return already;
        }
        return new IllegalStateException(context, e);
    }
}
