package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.EffectClass;

import java.util.Objects;

/**
 * Discards local un-pushed work. Task T089. FR-ORC-016, CL-004, CN-009.
 *
 * <p><strong>Local un-pushed work is the only erasable class</strong>, and everything this handler does follows
 * from that. It asks {@link CompensationRegister} what an effect is and refuses anything the register places in
 * an append-only store — not because the operation would be risky, but because it is impossible: the privileges
 * remove DELETE, so a run history claiming a rollback happened would be false, and a false recovery record is
 * worse than no recovery. It tells a reviewer the state was restored when it was not.
 *
 * <p>The refusal happens <strong>before</strong> the working tree is touched. A handler that half-acted and then
 * objected would leave exactly the partially applied effect FR-ORC-017 requires to be recorded.
 */
public final class RollbackHandler {

    private final WorkingTree workingTree;
    private final CompensationRegister register;

    public RollbackHandler(WorkingTree workingTree, CompensationRegister register) {
        this.workingTree = Objects.requireNonNull(workingTree, "workingTree");
        this.register = Objects.requireNonNull(register, "register");
    }

    /**
     * Rolls back one effect.
     *
     * @throws IllegalStateException    if the register places the effect in an append-only store, or cannot
     *                                  classify it at all — the unclassified default is compensate-only
     * @throws IllegalArgumentException if the key carries no target. {@code "working-tree:"} would become a
     *                                  discard of the empty path, which to a shell means all of it
     */
    public RecoveryOutcome rollback(String effectKey) {
        Objects.requireNonNull(effectKey, "effectKey");

        CompensationRegister.Resolution resolution = register.resolve(effectKey);
        if (resolution.effectClass() == null) {
            throw new IllegalStateException(
                    "effect '" + effectKey + "' matches no register row, so it is compensate-only by default "
                            + "(FR-ORC-016 rule 4) and must be corrected forward rather than discarded");
        }
        // Delegated rather than re-decided: the register is the structural table FR-ORC-016 resolves against,
        // and a second opinion here is a second place for the answer to be wrong.
        register.requireRollbackPermitted(resolution.effectClass());

        String target = targetOf(effectKey);
        return switch (resolution.effectClass()) {
            case WORKING_TREE_WRITE -> {
                workingTree.discard(target);
                yield RecoveryOutcome.of(RecoveryKind.ROLLBACK, effectKey,
                        "discarded the working-tree change at " + target);
            }
            case LOCAL_BRANCH_COMMIT -> {
                workingTree.resetBranch(target);
                yield RecoveryOutcome.of(RecoveryKind.ROLLBACK, effectKey,
                        "reset the local branch " + target + "; nothing was pushed (CL-004)");
            }
            // Unreachable: requireRollbackPermitted has already thrown for every other class. Kept exhaustive
            // rather than defaulted, so adding an eighth EffectClass fails to compile here and the author has
            // to say which side of the erasable line it falls on.
            case RECORDED_GATE_DECISION, AUDIT_RECORD, CREATED_SHORT_LINK, RECORDED_REDIRECT_EVENT,
                 AI_PROVIDER_INVOCATION -> throw new IllegalStateException(
                    "rollback is impossible against " + resolution.effectClass()
                            + "; it is corrected forward by COMPENSATING instead (FR-ORC-018)");
        };
    }

    /** The part after the prefix — the path or the branch ref. */
    private static String targetOf(String effectKey) {
        String target = effectKey.substring(effectKey.indexOf(':') + 1).trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException(
                    "effect key '" + effectKey + "' names no target. An empty path is not 'nothing' to a "
                            + "shell — it is everything");
        }
        return target;
    }

    /** Exposed so a caller can ask before acting, rather than catching a refusal as control flow. */
    public boolean canRollBack(String effectKey) {
        EffectClass effectClass = register.resolve(effectKey).effectClass();
        return effectClass != null && effectClass.reversible();
    }
}
