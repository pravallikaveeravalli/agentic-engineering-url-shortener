package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.state.RetentionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T060 — deadline disclosure in the gate request. FR-ORC-013, FR-ORC-011. CL-005 addendum.
 *
 * <p><strong>The person being asked must see the deadline in the ask</strong>, not discover it by
 * inspecting the run. {@link ApprovalGate} (T058) already REQUIRES both deadlines and validates their
 * ordering; this class is what COMPUTES them correctly at the moment a gate is raised, so a caller cannot
 * construct one with an invented or stale value. Additive to the inspectable field, not a substitute — T067
 * will surface the same two deadlines through run inspection.
 */
@DisplayName("T060 — every gate request states both deadlines, correctly computed")
class GateRequestPresenterTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private final GateRequestPresenter presenter =
            new GateRequestPresenter(java.time.Clock.fixed(T0, ZoneOffset.UTC), new RetentionPolicy());

    @Test
    @DisplayName("PVT-006: the wait deadline is 24h from the moment the gate is raised")
    void waitDeadlineIsPvt006FromNow() {
        ApprovalGate gate = presenter.request("S4", java.util.UUID.randomUUID(), "S4",
                GateClass.UNRESOLVED_AMBIGUITY);

        assertEquals(T0.plus(Duration.ofHours(24)), gate.waitDeadline(),
                "PVT-006: 24h, configurable, demonstration runs may compress it (labelled per AS-007)");
    }

    @Test
    @DisplayName("the disclosed auto-abandon-at is computed from THIS request's own moment, not left stale")
    void autoAbandonComputedFromRequestMoment() {
        ApprovalGate gate = presenter.request("S4", java.util.UUID.randomUUID(), "S4",
                GateClass.UNRESOLVED_AMBIGUITY);

        assertEquals(new RetentionPolicy().autoAbandonAt(T0), gate.disclosedAutoAbandonAt(),
                "PVT-015: 90 days from the moment the run last had activity, and raising a gate IS "
                        + "activity");
    }

    @Test
    @DisplayName("both deadlines are present in the SAME request — the ask states both, together")
    void bothDeadlinesInTheSameAsk() {
        ApprovalGate gate = presenter.request("S4", java.util.UUID.randomUUID(), "S4",
                GateClass.UNRESOLVED_AMBIGUITY);

        assertTrue(gate.waitDeadline() != null && gate.disclosedAutoAbandonAt() != null,
                "the person being asked must see the expiry consequence IN THE ASK, not discover it by "
                        + "inspecting the run separately");
    }

    @Test
    @DisplayName("a compressed wait period is honoured, for demonstration runs, labelled per AS-007")
    void compressedWaitIsHonoured() {
        // INJECTED (AS-007): PVT-006's own row explicitly permits demonstration runs to use a
        // compressed value, so a presenter constructed with a shorter period is not a deviation from the
        // spec — it is the spec's own accommodation, exercised and labelled as such.
        GateRequestPresenter compressed = new GateRequestPresenter(
                java.time.Clock.fixed(T0, ZoneOffset.UTC), new RetentionPolicy(), Duration.ofMinutes(5));

        ApprovalGate gate = compressed.request("S4", java.util.UUID.randomUUID(), "S4",
                GateClass.UNRESOLVED_AMBIGUITY);

        assertEquals(T0.plus(Duration.ofMinutes(5)), gate.waitDeadline());
    }

    @Test
    @DisplayName("the gate id equals the node key for a stage-level gate, so a caller can correlate the two")
    void gateIdEqualsNodeKeyForAStageLevelGate() {
        ApprovalGate gate = presenter.request("S4", java.util.UUID.randomUUID(), "S4",
                GateClass.UNRESOLVED_AMBIGUITY);
        assertEquals("S4", gate.gateId());
        assertEquals("S4", gate.nodeKey());
    }
}
