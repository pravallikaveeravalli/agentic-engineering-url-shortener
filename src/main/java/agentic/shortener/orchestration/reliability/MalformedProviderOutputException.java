package agentic.shortener.orchestration.reliability;

/**
 * A provider returned something that is not the shape it promised. Task T083. FR-ORC-014.
 *
 * <p><strong>A named type rather than an {@code IllegalStateException}.</strong> The first version of
 * {@link ProviderFailureTranslator} mapped {@code IllegalStateException} to {@code INTERNAL} to cover T073's
 * "non-JSON output is permanent" rule, and {@code FailureEnvelopeTest} caught what that costs: a socket
 * timeout wrapped in an {@code IllegalStateException} — the ordinary shape of a wrapped failure — classified
 * as a permanent internal defect. A legitimately retryable failure, silently made permanent.
 *
 * <p>The general lesson is the same one T083's guard states about vendor taxonomies, running the other way: a
 * generic JDK type pressed into carrying a specific meaning will also be thrown by code that means something
 * else. So the meaning gets its own type, and the translator maps that.
 *
 * <p>Unchecked, because a stage executor must return a failure rather than throw one
 * ({@code StageExecutorContract}); this is for the adapter's own internals, where it is caught and translated
 * before the outcome is built.
 */
public final class MalformedProviderOutputException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MalformedProviderOutputException(String message) {
        super(message);
    }

    public MalformedProviderOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
