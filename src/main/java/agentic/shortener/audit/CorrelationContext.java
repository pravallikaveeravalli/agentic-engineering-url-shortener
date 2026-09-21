package agentic.shortener.audit;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Carries a run's correlation identifier across a call, so every audit row, log line, metric label and
 * trace span a run's work produces can attach the SAME id without every call site threading it through by
 * hand. Task T112. NFR-OBS-001.
 *
 * <p><strong>{@code runId} is the correlation identifier</strong> — see {@link AuditEvent}'s own javadoc
 * for why this codebase does not carry a second, separate value. {@link AuditWriter} does not consult this
 * class today (every {@link AuditEvent} already requires an explicit {@code runId} from its caller, who
 * has it); this class exists as the shared carrier T114's structured logs, metrics and trace spans will
 * read from, so those three do not each invent their own propagation mechanism.
 *
 * <h2>Thread-local, and scoped rather than set-and-forget</h2>
 *
 * <p>{@link #runWithin} is the only way in: it sets the context, runs the caller's work, and restores
 * whatever was there before — even on an exception. A bare {@code set}/{@code clear} pair invites the
 * "forgot to clear" leak that then attaches one run's correlation id to another run's audit rows on a
 * reused thread (a real risk in any pooled-thread server), which is exactly the failure this class exists
 * to make structurally impossible rather than a discipline to remember.
 */
public final class CorrelationContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    /** The active run id, if this thread is currently within a {@link #runWithin} scope. */
    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * The active run id, or throws — for a call site that has no honest fallback and would rather fail
     * loudly than silently attach nothing.
     */
    public static UUID require() {
        return current().orElseThrow(() -> new IllegalStateException(
                "no correlation context is active on this thread — call site must run within "
                        + "CorrelationContext.runWithin(runId, ...)"));
    }

    /**
     * Runs {@code action} with {@code runId} as the active correlation id, restoring the previous value
     * (possibly none) afterward — nested scopes compose correctly, an inner run's id never leaks back out
     * to an outer one once the inner scope ends.
     */
    public static <T> T runWithin(UUID runId, Callable<T> action) throws Exception {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(action, "action");
        UUID previous = CURRENT.get();
        CURRENT.set(runId);
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** {@link #runWithin(UUID, Callable)} for an action that returns nothing and throws nothing checked. */
    public static void runWithin(UUID runId, Runnable action) {
        Objects.requireNonNull(action, "action");
        try {
            runWithin(runId, () -> {
                action.run();
                return null;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Runnable#run declares no checked exception, so none can genuinely reach here — Callable's
            // signature is simply wider than what this overload accepts.
            throw new IllegalStateException("unexpected checked exception from a Runnable", e);
        }
    }
}
