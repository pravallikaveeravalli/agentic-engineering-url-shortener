package agentic.shortener.orchestration.reliability;

/**
 * A compensation for this effect was already applied. Task T088. EC-022.
 *
 * <p>Raised when the store refuses a second row for one effect. <strong>Not an error condition to be papered
 * over</strong> — it is the constraint doing the job it exists for, and the case EC-022 names is the ordinary
 * one: a stage failed and was compensated, a later retry succeeded, and something tried to compensate again.
 *
 * <p>A distinct type rather than a generic failure, because the caller's correct response is specific: the
 * effect is already corrected, so carry on. Buried inside an {@code IllegalStateException} it would be
 * indistinguishable from a store that is broken.
 */
public final class CompensationAlreadyAppliedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CompensationAlreadyAppliedException(String message, Throwable cause) {
        super(message, cause);
    }
}
