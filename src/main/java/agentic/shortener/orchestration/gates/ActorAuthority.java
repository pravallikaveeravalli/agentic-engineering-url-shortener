package agentic.shortener.orchestration.gates;

import java.util.Objects;

/**
 * Actor-authority check. Task T063. FR-ORC-013, FR-ORC-021, NFR-AUT-001, CN-012. EC-024.
 *
 * <p><strong>What this proves and what it cannot, stated so the evidence is not over-read.</strong> It
 * proves that no actor lacking recorded authority for a gate decision is treated as authorized by this
 * predicate. It does <strong>not</strong> prove that a caller asserting {@code actorType: human} is human:
 * the surface is unauthenticated by design (CN-012), the field is a declaration, and an impersonating
 * caller with process access is undetectable. Verified actor identity is out of scope by owner decision,
 * 2026-09-20; an operator-issued per-decision token was considered and declined.
 *
 * <p><strong>The recorded authority is simple because the system is: only a human decides any gate, for
 * any class, for any outcome.</strong> {@code system}'s only recorded authority anywhere in this system is
 * deadline expiry and retention-driven abandonment (T091, T092), and neither of those is a
 * {@link GateDecision} — {@code SafeStopHandler} and {@code RetentionPolicy} write {@code audit_record}
 * directly, never {@code gate_decision}. So there is no gate class or outcome for which {@code system} has
 * recorded authority here; the per-class, per-outcome parameters exist so a caller states which decision it
 * is asking about, not because the answer varies by them today.
 *
 * <p><strong>The real control is architectural, not this predicate.</strong> {@code isAuthorized} is a
 * convenience a caller may consult; the control that actually matters is that no executor package can reach
 * {@link GateStore} or {@link GateDecision} at all, asserted separately as an ArchUnit rule — removing the
 * code path, not checking a field a caller supplies. {@link GateDecision}'s own constructor is the backstop
 * that does not depend on a caller consulting this class first.
 */
public final class ActorAuthority {

    private ActorAuthority() {
    }

    /**
     * Whether {@code actor} has recorded authority to decide a gate of {@code gateClass} with
     * {@code outcome}.
     *
     * <p>{@code gateClass} and {@code outcome} are accepted rather than ignored, even though today's
     * answer does not vary by them: a future per-class authority model (explicitly out of scope by owner
     * decision, but not foreclosed) would extend this method's body without changing its signature or
     * any caller.
     */
    public static boolean isAuthorized(Actor actor, GateClass gateClass, GateOutcome outcome) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(gateClass, "gateClass");
        Objects.requireNonNull(outcome, "outcome");
        return "human".equals(actor.actorType());
    }
}
