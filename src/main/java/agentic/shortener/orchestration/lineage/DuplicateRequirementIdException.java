package agentic.shortener.orchestration.lineage;

/**
 * Two requirements shared one {@code external_id} within a run. Task T082. FR-ORC-012.
 *
 * <p>A named type rather than a generic {@code IllegalStateException}, because the caller's correct
 * response is specific: the submitted requirement set itself is defective (a duplicate id), not a store
 * failure — the same reasoning that gave {@link agentic.shortener.orchestration.reliability.CompensationAlreadyAppliedException}
 * its own type.
 */
public final class DuplicateRequirementIdException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateRequirementIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
