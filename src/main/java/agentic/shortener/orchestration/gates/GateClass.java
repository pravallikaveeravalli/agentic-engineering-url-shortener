package agentic.shortener.orchestration.gates;

import java.util.List;

/**
 * The ten mandatory-gate classes. Task T064. Plan §5. CR-040.
 *
 * <p><strong>Ten, not eight.</strong> {@code approval.schema.json}'s own persisted {@code gateClass}
 * enum went from eight to ten values under CR-040, because plan §5's table had always named
 * {@link #NO_CHANGE_PLAN} and {@link #NODE_OVERRUN} and the contract had not caught up. Both this enum
 * and the contract now agree, and {@code GateClassTest} reads plan §5's table directly so the two cannot
 * drift apart silently — a class present in the plan but absent here would be a silent skip.
 *
 * <p>Eight of the ten decide only the five standard outcomes (`APPROVED` / `REJECTED` /
 * `CHANGES_REQUESTED` / `ESCALATED`, plus deadline-driven `TIMED_OUT` — never submitted, see
 * {@code GateOutcomeHandler}). {@link #NO_CHANGE_PLAN} and {@link #NODE_OVERRUN} each carry
 * {@link #namedChoices()} beyond that, because a bare APPROVED/REJECTED cannot express "proceed with no
 * implementation, governance-only" or "keep waiting versus kill this node" — those are decisions with
 * their own vocabulary, not a standard outcome wearing extra clothes.
 */
public enum GateClass {

    UNRESOLVED_AMBIGUITY("Unresolved ambiguity", "S5 onward, affected path only"),
    ARCHITECTURE_APPROVAL("Architecture approval", "S7"),
    SECURITY_SENSITIVE_ACTION("Security-sensitive action", "Merge of that change"),
    DESTRUCTIVE_OR_IRREVERSIBLE_ACTION("Destructive/irreversible action", "That action"),
    CONSTITUTIONAL_EXCEPTION("Constitutional exception", "Downstream progression"),
    MATERIAL_RISK_ACCEPTANCE("Material risk acceptance", "Release readiness"),

    /**
     * FR-ORC-031, CR-001. Triggered when deterministic S7 is reached with no change plan for the
     * requirement. {@link #namedChoices()}: governance-only run, human-implemented, or abandon (T065).
     */
    NO_CHANGE_PLAN("No change plan at implementation", "S8 onward",
            "GOVERNANCE_ONLY", "HUMAN_IMPLEMENTED", "ABANDON"),

    /**
     * FR-ORC-014 rule 6, PVT-016. Triggered when a node's execution threshold is breached.
     * {@link #namedChoices()}: keep waiting (re-arm the threshold) or kill the node. Waits the uniform
     * PVT-006 gate deadline — CR-023's proposed operational deadline (PVT-017) was withdrawn before
     * application and was never minted; this class carries no deadline of its own.
     */
    NODE_OVERRUN("Node overrun", "That node only; siblings and unaffected paths continue",
            "KEEP_WAITING", "KILL_THE_NODE"),

    RELEASE_READINESS("Release readiness", "Submission"),
    FINAL_SUBMISSION("Final submission", "Submission");

    private final String planLabel;
    private final String blockedScope;
    private final List<String> namedChoices;

    GateClass(String planLabel, String blockedScope, String... namedChoices) {
        this.planLabel = planLabel;
        this.blockedScope = blockedScope;
        this.namedChoices = List.of(namedChoices);
    }

    /** The exact label plan §5's table uses for this row, bold markers stripped. */
    public String planLabel() {
        return planLabel;
    }

    /** What this gate class blocks while pending, per plan §5's own "Blocks" column. */
    public String blockedScope() {
        return blockedScope;
    }

    /**
     * Choices beyond the five standard outcomes, or empty for the eight classes that need none.
     *
     * <p>Not an outcome enum of its own: a decision at {@link #NO_CHANGE_PLAN} or {@link #NODE_OVERRUN}
     * still records one of the five standard {@code outcome} values (T065 maps each named choice onto
     * one), and this list is what constrains which named choice a decision-maker may additionally select.
     */
    public List<String> namedChoices() {
        return namedChoices;
    }
}
