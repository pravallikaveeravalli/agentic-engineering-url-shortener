package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.EffectClass;
import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The Compensation Register, made operational. Task T088. FR-ORC-016 (CL-007), KE-28, EC-022.
 *
 * <p>The seven rows themselves are {@link EffectClass}, because {@code StageEffectContract} needs them to load.
 * What lives here is the part the spec's table implies but does not state: <strong>how an effect gets
 * classified in the first place, and what happens when it cannot be.</strong>
 *
 * <h2>Classification is structural — from where the effect landed</h2>
 *
 * <p>An effect key names its own location: {@code working-tree:docs/summary.md},
 * {@code short-link:Ab3dEfG}, {@code gate-decision:41}. The class follows from the prefix, so a stage cannot
 * talk its effect into a different class by declaring one — the spec's rule is that stages append declared
 * overrides and never contradict a register row without human review (EC-035), and a classifier that read the
 * stage's claim would make that review unreachable.
 *
 * <h2>Unclassified is compensate-only</h2>
 *
 * <p>FR-ORC-016 rule 4. The default has to run toward the answer that is safe when it is wrong: treating an
 * unknown effect as erasable would discard something on the guess that it could be discarded, and the cases
 * where that guess is wrong are exactly the cases where it cannot be taken back. Compensating an effect that
 * did not need it appends a redundant correction — recoverable, and legible.
 *
 * <h2>Where no compensating action is known: touch nothing</h2>
 *
 * <p>{@link RecoveryKind#NONE_KNOWN} cannot be recorded as applied, and {@link Resolution#requireActionable()}
 * throws rather than returning a plausible string somebody then runs. A best-effort attempt at an unknown
 * recovery is how an incident gets worse; the honest answer is suspension (FR-ORC-017, T091).
 */
public final class CompensationRegister {

    /**
     * Effect-key prefix to register row.
     *
     * <p>{@code LinkedHashMap} rather than {@code Map.of}: the order is the register table's own order, and a
     * reader comparing this to the spec should be able to go down both in step.
     */
    private static final Map<String, EffectClass> BY_PREFIX = new LinkedHashMap<>();

    static {
        BY_PREFIX.put("working-tree:", EffectClass.WORKING_TREE_WRITE);
        BY_PREFIX.put("branch:", EffectClass.LOCAL_BRANCH_COMMIT);
        BY_PREFIX.put("gate-decision:", EffectClass.RECORDED_GATE_DECISION);
        BY_PREFIX.put("audit:", EffectClass.AUDIT_RECORD);
        BY_PREFIX.put("short-link:", EffectClass.CREATED_SHORT_LINK);
        BY_PREFIX.put("redirect-event:", EffectClass.RECORDED_REDIRECT_EVENT);
        BY_PREFIX.put("ai-invocation:", EffectClass.AI_PROVIDER_INVOCATION);
    }

    private static final String UNCLASSIFIED_ACTION =
            "compensate — append a correction. This effect matched no register row, and FR-ORC-016 rule 4 "
                    + "makes an unclassified effect compensate-only: discarding something on the guess that "
                    + "it was discardable fails worst exactly where the guess is wrong";

    /**
     * What to do about one effect.
     *
     * @param effectClass the register row, or {@code null} when nothing matched
     * @param action      the correct action, or the reason there is none
     */
    public record Resolution(EffectClass effectClass, RecoveryKind kind, String action) {

        public Resolution {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(action, "action");
        }

        /** True only for the two local, un-pushed classes. */
        public boolean erasable() {
            return kind == RecoveryKind.ROLLBACK;
        }

        /** An effect nobody knows how to correct is a safe-stop trigger (FR-ORC-017). */
        public boolean requiresSuspension() {
            return kind == RecoveryKind.NONE_KNOWN;
        }

        /**
         * @throws IllegalStateException when there is no action to take. Loud rather than returning something
         *                               plausible: the caller's next move would be to run it
         */
        public String requireActionable() {
            if (kind == RecoveryKind.NONE_KNOWN) {
                throw new IllegalStateException(
                        "no compensating action is known for this effect; the correct response is to touch "
                                + "nothing and suspend (FR-ORC-017), not to attempt something plausible");
            }
            return action;
        }
    }

    private final ConnectionSource connections;
    private final Clock clock;

    /**
     * @param connections may be {@code null} for a resolution-only register. Resolution touches no store, and
     *                    requiring one would make the pure half of this class untestable without a container —
     *                    which is the half that must never be wrong
     */
    public CompensationRegister(ConnectionSource connections, Clock clock) {
        this.connections = connections;
        this.clock = clock;
    }

    // ==============================================================================================
    // resolution
    // ==============================================================================================

    public Resolution resolve(String effectKey) {
        EffectClass matched = classify(effectKey);
        if (matched == null) {
            return new Resolution(null, RecoveryKind.COMPENSATION, UNCLASSIFIED_ACTION);
        }
        RecoveryKind kind = kindOf(matched);
        String action = kind == RecoveryKind.NOTHING_TO_COMPENSATE
                ? "nothing to compensate — the effect cannot be un-called and changed no external state"
                : matched.registerAction();
        return new Resolution(matched, kind, action);
    }

    /**
     * The register's answer when the caller already knows no action exists for this effect.
     *
     * <p>Separate from {@link #resolve} on purpose. Resolution reports what the register <em>says</em>; this
     * reports that the caller has an effect the register cannot help with, which is a different fact and has a
     * different consequence — suspension rather than a correction.
     */
    public Resolution resolveWithoutKnownAction(String effectKey) {
        return new Resolution(classify(effectKey), RecoveryKind.NONE_KNOWN,
                "no compensating action is known for effect '" + effectKey + "'");
    }

    /** Derived from the register row's own reversibility and action, never from a second table. */
    public RecoveryKind kindOf(EffectClass effectClass) {
        Objects.requireNonNull(effectClass, "effectClass");
        if (effectClass.reversible()) {
            return RecoveryKind.ROLLBACK;
        }
        return effectClass.nothingToCompensate()
                ? RecoveryKind.NOTHING_TO_COMPENSATE
                : RecoveryKind.COMPENSATION;
    }

    /**
     * @throws IllegalStateException for any effect the register places in an immutable store
     */
    public void requireRollbackPermitted(EffectClass effectClass) {
        if (!effectClass.reversible()) {
            // Not a risky operation — an impossible one. The privileges remove DELETE, so a claim that a
            // rollback happened would be false in the run history, and a false recovery record is worse than
            // no recovery: it tells a reviewer the state was restored when it was not.
            throw new IllegalStateException(
                    "rollback is impossible against " + effectClass + ": it lives in an append-only store "
                            + "and is corrected forward by COMPENSATING instead (FR-ORC-018)");
        }
    }

    private static EffectClass classify(String effectKey) {
        if (effectKey == null) {
            return null;
        }
        // Case-sensitive. A key differing only in case is not the same key, and treating it as one would let a
        // typo pick up a classification by accident.
        return BY_PREFIX.entrySet().stream()
                .filter(entry -> effectKey.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    // ==============================================================================================
    // recording
    // ==============================================================================================

    /**
     * Records a recovery that was actually applied.
     *
     * <p>The kind is written into {@code compensating_action} as a prefix, so the run history distinguishes a
     * rollback from a compensation without a reader inferring it from wording. T089 and T090 share that guard
     * and this is where it is met.
     *
     * @throws CompensationAlreadyAppliedException if this effect already has a record (EC-022). The store
     *                                             decides, not a read-then-check here — which would be a race
     *                                             in the code that exists to keep a recovery path safe
     */
    public void recordApplied(UUID runId, String nodeKey, String effectKey, RecoveryKind kind,
                              String action, String reason) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(effectKey, "effectKey");
        Objects.requireNonNull(reason, "reason");
        if (kind == RecoveryKind.NONE_KNOWN) {
            throw new IllegalArgumentException(
                    "NONE_KNOWN is not a recovery that was applied; a row for it would read as a correction "
                            + "that happened. Suspend instead (FR-ORC-017)");
        }
        if (connections == null) {
            throw new IllegalStateException(
                    "this register was constructed for resolution only and has no store to record into");
        }

        String sql = "INSERT INTO compensation_record (run_id, node_key, effect_key, "
                + "compensating_action, applied_at, reason) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            ps.setString(3, effectKey);
            ps.setString(4, kind.name() + ": " + action);
            ps.setTimestamp(5, Timestamp.from(clock.instant()));
            ps.setString(6, reason);
            ps.executeUpdate();
        } catch (Exception e) {
            if (e instanceof SQLException sql23505 && "23505".equals(sql23505.getSQLState())) {
                throw new CompensationAlreadyAppliedException(
                        "effect '" + effectKey + "' in run " + runId + " is already compensated; a second "
                                + "correction is refused by compensation_once_per_effect (EC-022)", e);
            }
            throw new IllegalStateException(
                    "failed to record a " + kind + " for effect '" + effectKey + "'", e);
        }
    }
}
