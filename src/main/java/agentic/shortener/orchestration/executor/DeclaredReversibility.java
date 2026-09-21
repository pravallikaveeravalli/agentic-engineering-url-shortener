package agentic.shortener.orchestration.executor;

/**
 * A stage's own claim about its effect, which the register may disagree with. Task T070. EC-035.
 *
 * <p>Separate from {@link EffectClass#reversible()} on purpose. If a stage's declaration simply overwrote the
 * structural answer there would be nothing left to disagree with, and EC-035 — <em>must be flagged for human
 * review, not trusted</em> — would have no way to fire. Keeping both means a contradiction is a fact the
 * contract can state rather than a value that quietly won.
 *
 * <p>Optional: most stages declare nothing and take the register's answer, which is the case that should be
 * easy.
 */
public enum DeclaredReversibility {

    /** The stage claims its effect can be discarded. */
    ERASABLE,

    /** The stage claims its effect can only be corrected forward. */
    COMPENSATE_ONLY
}
