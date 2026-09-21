package agentic.shortener.policy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Whether a set of policy results blocks progression. Task T101, T102. FR-ORC-022.
 *
 * <p><strong>A compliance failure that does not block is a detection target</strong> (T102's own Guard
 * clause) — this is the one place "blocking" is computed, so every caller (the engine, the readiness
 * evaluator, a test) gets the identical answer rather than each re-deriving its own notion of "counts as
 * failing."
 *
 * <p>Blocks when, for any <strong>mandatory</strong> result:
 * <ul>
 *   <li>the outcome is {@link PolicyOutcome#FAIL} — including every check EC-025 could not evaluate at
 *       all, which is recorded {@code FAIL}, never {@code PASS}, before this class ever sees it (T101);</li>
 *   <li>the outcome is {@link PolicyOutcome#EXCEPTION_REQUESTED} with no {@link PolicyException} attached —
 *       "unapproved" (T104): {@link PolicyException} cannot exist un-approved, so a null exception here
 *       IS the unapproved case, not a separate flag to check;</li>
 *   <li>the outcome is {@link PolicyOutcome#EXCEPTION_REQUESTED} with an exception that has expired as of
 *       {@code now} — evaluated <strong>at use</strong>, not only at approval (T105, EC-027).</li>
 * </ul>
 *
 * <p>{@link PolicyOutcome#NOT_APPLICABLE} never blocks, mandatory or not — it is not a failure to evaluate
 * against a subject that does not apply.
 */
public final class BlockingEnforcer {

    private BlockingEnforcer() {
    }

    public static boolean isBlocking(List<PolicyCheckResult> results, Instant now) {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(now, "now");
        return results.stream().anyMatch(r -> r.mandatory() && blocksAlone(r, now));
    }

    private static boolean blocksAlone(PolicyCheckResult result, Instant now) {
        return switch (result.outcome()) {
            case FAIL -> true;
            case EXCEPTION_REQUESTED -> result.exception() == null || result.exception().isExpired(now);
            case PASS, NOT_APPLICABLE -> false;
        };
    }
}
