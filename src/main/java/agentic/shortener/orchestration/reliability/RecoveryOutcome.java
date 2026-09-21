package agentic.shortener.orchestration.reliability;

import java.util.Objects;

/**
 * What a recovery handler actually did. Tasks T089, T090. FR-ORC-016.
 *
 * <p>{@code action} carries its {@link RecoveryKind} as a prefix, matching what
 * {@link CompensationRegister#recordApplied} writes. T089 and T090 share one guard — <em>compensation MUST NOT
 * be described as rollback or vice versa; the run history must let a reviewer tell which occurred</em> — and a
 * label that lived only in the handler's own return value, not in the recorded row, would satisfy that guard in
 * memory and fail it in the audit.
 */
public record RecoveryOutcome(RecoveryKind kind, String effectKey, String action) {

    public RecoveryOutcome {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(effectKey, "effectKey");
        Objects.requireNonNull(action, "action");
        if (!action.startsWith(kind.name())) {
            throw new IllegalArgumentException(
                    "the action must be labelled with its kind, so run history distinguishes a rollback from "
                            + "a compensation without a reader inferring it from wording: got '" + action
                            + "' for " + kind);
        }
    }

    static RecoveryOutcome of(RecoveryKind kind, String effectKey, String what) {
        return new RecoveryOutcome(kind, effectKey, kind.name() + ": " + what);
    }
}
