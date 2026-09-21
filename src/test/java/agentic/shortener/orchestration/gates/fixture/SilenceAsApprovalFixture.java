package agentic.shortener.orchestration.gates.fixture;

import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;

import java.util.UUID;

/**
 * A DELIBERATE violation fixture. Task T061a.
 *
 * <p>Stands in for a gate handler that has been broken to treat deadline expiry as {@code APPROVED} —
 * exactly the defect Constitution III and T061's own Guard forbid: silence must never be expressible as an
 * input, and a governance suite that passed because the run advanced on it would be void.
 *
 * <p><strong>Lives in test scope</strong>, under {@code orchestration.gates.fixture}, same as T015's own
 * fixture and for the same two reasons: it must not be reachable from production code, and a reviewer
 * reading the production tree will not find a planted violation sitting in it. Unlike T015's fixture
 * (which violates an ArchUnit dependency rule by existing), this one violates a BEHAVIOURAL rule — it is a
 * caller that does the wrong thing, not a class that has the wrong shape — so what it does is call
 * {@link JdbcRunStore#transitionNode} directly instead of routing through {@code SafeStopHandler.suspend},
 * simulating exactly the bug this class exists to make catchable.
 */
public final class SilenceAsApprovalFixture {

    private SilenceAsApprovalFixture() {
    }

    /**
     * Advances the gated node to {@code SUCCEEDED} as if the deadline's expiry had been treated as an
     * approval — the broken behaviour, applied directly rather than through any real gate-decision path
     * (there is none for it to go through; {@code GateOutcome} has no {@code TIMED_OUT} value, so this
     * fixture calls the store directly to simulate a caller that bypassed that protection entirely).
     */
    public static void advanceAsIfApproved(JdbcRunStore runStore, UUID runId, String gateNodeKey) {
        runStore.transitionNode(runId, gateNodeKey, StageState.AWAITING_APPROVAL, StageState.SUCCEEDED,
                "FIXTURE (T061a, AS-007): deliberately treating deadline expiry as APPROVED, the "
                        + "defect this fixture exists to make catchable — silence must never be "
                        + "expressible as an input (Constitution III)");
    }
}
