package agentic.shortener.orchestration.executor;

/**
 * The seven rows of the Compensation Register. Task T070. FR-ORC-016 (CL-007).
 *
 * <p>Confirmed at Gate 3. This is <strong>the structural table FR-ORC-016 resolves against</strong>: stages
 * append declared overrides, and they never contradict a row here without human review (EC-035). T088 builds
 * the runtime register on top of these; the classification itself lives here because {@link
 * StageEffectContract} needs it to load, which is earlier than any compensation runs.
 *
 * <p><strong>Two reversible rows, and only because nothing is pushed.</strong> CL-004 is what makes a local
 * branch commit erasable; the moment anything is pushed that row stops being true, which is why the basis is
 * recorded next to the value rather than left as an assumption.
 *
 * <p>Any effect not matching a row is treated as compensate-only (FR-ORC-016 rule 4) — the default runs
 * toward the safer answer, as it does for policy verdicts (EC-025) and for retry (EC-031).
 */
public enum EffectClass {

    /** Docs, summary, plan. Discardable before commit. */
    WORKING_TREE_WRITE(true, "rollback — discard the working-tree change"),

    /** Stage 7. Erasable only because nothing is pushed (CL-004). */
    LOCAL_BRANCH_COMMIT(true, "rollback — reset or delete the branch"),

    /** {@code CLAUDE.md} immutability. EC-020's voided approval is a superseding record, never a deletion. */
    RECORDED_GATE_DECISION(false, "compensate — write a superseding record referencing the prior one"),

    /** NFR-AUD-001 immutability. */
    AUDIT_RECORD(false, "compensate — append a corrective entry"),

    /** EX-003 excludes deletion: dangling history, short-code reuse hijack, repeat-safe cleanup. */
    CREATED_SHORT_LINK(false, "compensate — set to expired, never delete"),

    /** Append-only analytics (FR-URL-010). */
    RECORDED_REDIRECT_EVENT(false, "compensate — append a correction"),

    /**
     * Cannot be un-called, and no external state changed.
     *
     * <p>Irreversible with genuinely nothing to compensate, which is why {@link StageEffectContract} does not
     * demand a named action for it. Requiring one would force an author to invent an action, and an invented
     * compensating action is worse than a declared absence: it would run.
     */
    AI_PROVIDER_INVOCATION(false, null);

    private final boolean reversible;
    private final String registerAction;

    EffectClass(boolean reversible, String registerAction) {
        this.reversible = reversible;
        this.registerAction = registerAction;
    }

    /** The structural answer. A stage declaration disagreeing with it is flagged, never trusted (EC-035). */
    public boolean reversible() {
        return reversible;
    }

    /** The register's own "correct action" column; {@code null} where there is nothing to compensate. */
    public String registerAction() {
        return registerAction;
    }

    /** True where the register says there is nothing to undo — not the same as "already handled". */
    public boolean nothingToCompensate() {
        return !reversible && registerAction == null;
    }
}
