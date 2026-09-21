package agentic.shortener.orchestration.graph;

/**
 * How a node's incoming dependencies combine. Task T074, narrowed by CR-039.
 *
 * <p><strong>One value. {@code ANY} is not supported.</strong> Owner ruling, 2026-09-21: the Slice 4
 * artifact-coverage sweep found that {@code join_semantics} was stored and never read —
 * {@link agentic.shortener.orchestration.state.StageCriteria#mayEnter} takes dependency states with no edge
 * information and requires all of them, so an {@code ANY} edge was writable and would have been evaluated as
 * {@code ALL}. A value a caller can set and nothing honours is worse than one nothing can set: CR-032 retired
 * {@code FALLBACK}'s handler on the reasoning that an unemittable enum value is a claim rather than a
 * classification, and this was the same defect with the failure pointing outward.
 *
 * <p><strong>Why a single-value enum rather than no field at all.</strong> Dropping the column would need the
 * two-version deprecation cycle in {@code contracts/README.md} and would leave it in place throughout this
 * assessment anyway, while pinning already makes {@code ANY} inexpressible at all three layers: this enum has
 * no such constant, the contract's enum is {@code ["ALL"]}, and V6 narrows the store's CHECK to
 * {@code join_semantics = 'ALL'}. Keeping the field also keeps the join concept named, which is what S7's
 * explicit join and S11's two incoming edges are demonstrating.
 *
 * <p>The obvious objection — a one-value enum invites a second value — is answered by {@code JoinSemanticsTest},
 * which asserts the count and cites CR-039, so restoring {@code ANY} fails a test rather than passing review.
 */
public enum JoinSemantics {

    /**
     * The target does not proceed while <em>any</em> required source is incomplete or failed (EC-018).
     *
     * <p>The only semantic there is, and the only one ever implemented.
     */
    ALL
}
