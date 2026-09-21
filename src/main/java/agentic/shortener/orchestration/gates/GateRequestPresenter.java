package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.state.RetentionPolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Deadline disclosure in the gate request. Task T060. FR-ORC-013, FR-ORC-011. CL-005 addendum.
 *
 * <p><strong>The person being asked must see the deadline in the ask</strong>, not discover it by
 * inspecting the run. {@link ApprovalGate}'s own constructor (T058) already requires both deadlines and
 * validates their ordering; what this class adds is computing them <em>correctly, at the moment the gate is
 * raised</em>, so nothing downstream can construct a gate request with an invented or stale value.
 *
 * <p>This is additive to the inspectable field, not a substitute for it — T067's run inspection surfaces
 * the same two deadlines a reviewer would otherwise have to trust were computed right when the gate was
 * first raised.
 */
public final class GateRequestPresenter {

    /** PVT-006, the uniform gate-wait deadline every one of the ten gate classes uses (CR-040). */
    private static final Duration DEFAULT_WAIT_PERIOD = Duration.ofHours(24);

    private final Clock clock;
    private final RetentionPolicy retentionPolicy;
    private final Duration waitPeriod;

    public GateRequestPresenter(Clock clock, RetentionPolicy retentionPolicy) {
        this(clock, retentionPolicy, DEFAULT_WAIT_PERIOD);
    }

    /**
     * @param waitPeriod PVT-006's own row permits a compressed value for demonstration runs, labelled per
     *                   AS-007 by the caller that passes one
     */
    public GateRequestPresenter(Clock clock, RetentionPolicy retentionPolicy, Duration waitPeriod) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retentionPolicy = Objects.requireNonNull(retentionPolicy, "retentionPolicy");
        this.waitPeriod = Objects.requireNonNull(waitPeriod, "waitPeriod");
    }

    /**
     * Raises a gate, computing both deadlines from this moment.
     *
     * <p>{@code disclosedAutoAbandonAt} is derived from <em>this request's own instant</em>, not from
     * whatever {@code last_activity_at} the run happened to carry before — raising a gate IS activity
     * (T091/T092's own rule: the idle clock resets on activity), so the ask discloses the deadline that
     * activity produces, not a value that would already be stale by the time it is shown.
     */
    public ApprovalGate request(String gateId, UUID runId, String nodeKey, GateClass gateClass) {
        Instant now = clock.instant();
        return new ApprovalGate(gateId, runId, nodeKey, gateClass, now.plus(waitPeriod),
                retentionPolicy.autoAbandonAt(now), now);
    }
}
