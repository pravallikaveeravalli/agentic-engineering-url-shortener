package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.EffectClass;
import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Corrects irreversible effects forward. Task T090. FR-ORC-016, KE-16, EC-022.
 *
 * <p>Three actions, one per compensable register row, and <strong>none of them deletes or rewrites
 * anything</strong>:
 *
 * <ul>
 *   <li><strong>A recorded gate decision</strong> gets a superseding record that names it. EC-020: the prior
 *       decision is superseded by reference, never deleted or edited — a reviewer has to be able to see that an
 *       approval was given and then voided, which is a different history from one that was never given.
 *   <li><strong>An audit record</strong> gets an appended correction, because the grants make anything else
 *       impossible: the application role holds INSERT and SELECT and never UPDATE or DELETE.
 *   <li><strong>A created short link</strong> is set to {@code EXPIRED}. Never deleted (EX-003, T-13): a deleted
 *       code leaves dangling history and can be re-issued to someone else, which is a redirect hijack. And the
 *       <em>state</em> moves rather than the timestamp, because V1's {@code short_link_expiry_after_creation}
 *       refuses to let a link claim it expired before it was created — the schema will not permit the
 *       retroactive rewrite, which is the right answer.
 * </ul>
 *
 * <p><strong>An erasable effect is refused here.</strong> Compensating a discardable working-tree change would
 * append a correction for something that should simply have been discarded, and would put COMPENSATION in a
 * history where ROLLBACK was the truth. T089 and T090 each refuse the other's work for the same reason: the
 * shared guard is that a reviewer can tell which occurred.
 *
 * <h2>Reserving the record and applying the correction are ONE transaction</h2>
 *
 * <p>The first version of this class applied the correction, then called
 * {@link CompensationRegister#recordApplied}, which opens and commits its own connection. That ordering has a
 * window: a second caller for the same effect — the exact EC-022 scenario, a retry racing a late compensation —
 * could run its own correction before either caller reached the uniqueness check, so a "refused" second attempt
 * still left a second audit row behind. {@code CompensationHandlerIT.atMostOneCorrectionPerEffect} caught this
 * during the green cycle: the constraint refused the second {@code compensation_record} row exactly as
 * designed, and the test still failed, because the damage the constraint exists to prevent had already been
 * done by the time it ran.
 *
 * <p>The fix is the same pattern {@code JdbcRunStore.transitionNode} and {@code ArtifactWriteGuard} already
 * use for exactly this class of problem: the row that the unique constraint protects is written <em>inside the
 * same transaction</em> as the effect it authorises, so the database decides atomically whether this caller may
 * proceed at all — there is no window between "reserved" and "acted" for a second caller to occupy.
 */
public final class CompensationHandler {

    private final ConnectionSource connections;
    private final Clock clock;
    private final CompensationRegister register;

    public CompensationHandler(ConnectionSource connections, Clock clock, CompensationRegister register) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.register = Objects.requireNonNull(register, "register");
    }

    /**
     * Compensates one effect and records that it was compensated, atomically.
     *
     * @throws CompensationAlreadyAppliedException if this effect already has a record (EC-022) — including the
     *                                             case where a later retry succeeded and something tried again.
     *                                             Nothing is applied when this is thrown: the reservation and
     *                                             the correction share one transaction, so a refused
     *                                             reservation means the correction never ran
     * @throws IllegalStateException               if the effect is erasable; that is {@link RollbackHandler}'s
     *                                             work
     */
    public RecoveryOutcome compensate(UUID runId, String nodeKey, String effectKey, String reason) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(effectKey, "effectKey");
        Objects.requireNonNull(reason, "reason");

        CompensationRegister.Resolution resolution = register.resolve(effectKey);
        if (resolution.erasable()) {
            throw new IllegalStateException(
                    "effect '" + effectKey + "' is erasable, so the honest action is ROLLBACK, not a "
                            + "compensation. Recording a correction for something that should have been "
                            + "discarded would misdescribe what happened (FR-ORC-018)");
        }

        RecoveryKind kind = resolution.kind();
        // Computed before any I/O: for every branch below, the description depends only on the effect key and
        // the resolution, never on a value the correction itself reads back — so the record written to reserve
        // the row and the action actually taken are guaranteed to agree.
        String what = describeCorrection(effectKey, resolution);

        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                // Reserve first. If a second caller already holds this effect, the unique constraint refuses
                // this INSERT before either connection has touched the correction's tables at all.
                register.recordApplied(c, runId, nodeKey, effectKey, kind, what, reason);

                if (kind == RecoveryKind.COMPENSATION && resolution.effectClass() != null) {
                    applyCorrection(c, runId, effectKey, resolution.effectClass());
                }
                // NOTHING_TO_COMPENSATE and the unclassified-COMPENSATION case both reach here with nothing
                // further to do: the reservation above IS the whole correction for them, which is deliberate
                // (see the class javadoc on FR-ORC-016 rule 4 and the AI-invocation row).

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw CompensationRegister.translateRecordFailure(e, effectKey, runId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("failed to compensate effect '" + effectKey + "'", e);
        }

        return RecoveryOutcome.of(kind, effectKey, what);
    }

    /** Pure: no I/O, so it can run before the reservation and be guaranteed to match what actually happens. */
    private static String describeCorrection(String effectKey, CompensationRegister.Resolution resolution) {
        if (resolution.kind() == RecoveryKind.NOTHING_TO_COMPENSATE) {
            return "nothing to compensate — the effect cannot be un-called and changed no external state";
        }
        if (resolution.effectClass() == null) {
            // FR-ORC-016 rule 4: compensate-only by default. There is no specific correction to append for an
            // effect nobody classified, and inventing one would be worse than recording the fact — so the
            // compensation_record row IS the whole correction, and it says as much.
            return "recorded a correction for an unclassified effect; no register row names a specific action, "
                    + "and FR-ORC-016 rule 4 makes the default compensate-only";
        }
        String identifier = identifierOf(effectKey);
        return switch (resolution.effectClass()) {
            case RECORDED_GATE_DECISION -> "wrote a superseding gate record referencing decision " + identifier;
            case AUDIT_RECORD, RECORDED_REDIRECT_EVENT ->
                    "appended a corrective audit entry for " + effectKey;
            case CREATED_SHORT_LINK ->
                    "set short link " + identifier + " to EXPIRED; links are never deleted (EX-003)";
            case WORKING_TREE_WRITE, LOCAL_BRANCH_COMMIT, AI_PROVIDER_INVOCATION ->
                    throw new IllegalStateException("unreachable: erasable effects are refused before this "
                            + "method is called, and AI_PROVIDER_INVOCATION is NOTHING_TO_COMPENSATE");
        };
    }

    /** The actual write, against the SAME connection as the reservation. Nothing here commits or rolls back. */
    private void applyCorrection(Connection c, UUID runId, String effectKey, EffectClass effectClass)
            throws SQLException {
        String identifier = identifierOf(effectKey);
        switch (effectClass) {
            case RECORDED_GATE_DECISION -> supersedeGateDecision(c, identifier);
            case AUDIT_RECORD, RECORDED_REDIRECT_EVENT -> appendAuditCorrection(c, runId, effectKey);
            case CREATED_SHORT_LINK -> expireShortLink(c, identifier);
            case WORKING_TREE_WRITE, LOCAL_BRANCH_COMMIT, AI_PROVIDER_INVOCATION ->
                    throw new IllegalStateException("unreachable: see describeCorrection");
        }
    }

    /**
     * EC-020. A superseding record, not an edit.
     *
     * <p>Written as {@code REJECTED} because a voided approval is not itself an approval, and as
     * {@code actor_type = 'human'} because the store admits nothing else (CR-021) — a system-authored void would
     * have to lie about its actor to be written at all. That is a real limitation and it is the store's
     * position, not this class's: a void follows a human decision to replan, so the human whose replan voided it
     * is the honest actor.
     */
    private void supersedeGateDecision(Connection c, String supersededId) throws SQLException {
        String supersedingReason = "supersedes gate_decision " + supersededId
                + ", voided by replan. The prior decision is referenced, never deleted or edited (EC-020).";

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO gate_decision (run_id, gate_id, outcome, actor_type, actor_name, reason, "
                        + "decided_at, repository_record_path) "
                        + "SELECT run_id, gate_id, 'REJECTED', 'human', actor_name, ?, ?, "
                        + "repository_record_path FROM gate_decision WHERE gate_decision_id = ?")) {
            ps.setString(1, supersedingReason);
            ps.setTimestamp(2, Timestamp.from(clock.instant()));
            ps.setLong(3, Long.parseLong(supersededId));
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("no gate decision " + supersededId + " to supersede");
            }
        }
    }

    /**
     * The only correction an append-only table can take.
     *
     * <p>{@code run_id} is a parameter, not derived from {@code effect_key} — {@code compensation_record}'s
     * uniqueness is {@code (run_id, effect_key)}, so the same effect key string is legitimately reused across
     * different runs (T088's {@code scopedPerRun} proves it), and a lookup keyed on effect key alone would risk
     * picking up another run's row. {@code correlation_id} is read from {@code workflow_run} by {@code run_id},
     * the same pattern {@link CompensationRegister} and {@code SafeStopHandler} use for the same reason.
     */
    private void appendAuditCorrection(Connection c, UUID runId, String effectKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_record (actor_type, action, occurred_at, affected_artifact, result, "
                        + "reason, run_id, correlation_id) VALUES ('system', 'COMPENSATION', ?, ?, "
                        + "'CORRECTED', ?, ?, (SELECT correlation_id FROM workflow_run WHERE run_id = ?))")) {
            ps.setTimestamp(1, Timestamp.from(clock.instant()));
            ps.setString(2, effectKey);
            ps.setString(3, "appended a correction for " + effectKey);
            ps.setObject(4, runId);
            ps.setObject(5, runId);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("no such run: " + runId);
            }
        }
    }

    /** Expired, never deleted. The state moves; the promised expiry stays as the record of what was promised. */
    private void expireShortLink(Connection c, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE short_link SET state = 'EXPIRED' WHERE short_code = ?")) {
            ps.setString(1, code);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("no short link " + code + " to expire");
            }
        }
    }

    private static String identifierOf(String effectKey) {
        String identifier = effectKey.substring(effectKey.indexOf(':') + 1).trim();
        if (identifier.isEmpty()) {
            throw new IllegalArgumentException("effect key '" + effectKey + "' names no target");
        }
        return identifier;
    }
}
