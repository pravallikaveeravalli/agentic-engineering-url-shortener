package agentic.shortener.orchestration.reliability;

/**
 * A provider recognized and reported that it is rate-limited or its quota is exhausted. Task T131d.
 * FR-ORC-014.
 *
 * <p><strong>A named type rather than folding this into {@link MalformedProviderOutputException}</strong>,
 * for the exact reason that class's own javadoc gives for existing at all: a generic type pressed into
 * carrying two different meanings gets classified by whichever meaning the translator picked, and the other
 * one silently gets the wrong category. A response a provider could not even parse and a response the
 * provider understood perfectly well but that arrived alongside an explicit rate-limit signal are different
 * facts — the first is permanent (retrying an unparseable response changes nothing), the second is exactly
 * what {@link FailureCategory#RATE_LIMITED} exists to classify as retryable.
 *
 * <p>Unchecked, matching {@link MalformedProviderOutputException} — for the adapter's own internals, caught
 * and translated before the outcome is built.
 */
public final class ProviderRateLimitedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProviderRateLimitedException(String message) {
        super(message);
    }
}
