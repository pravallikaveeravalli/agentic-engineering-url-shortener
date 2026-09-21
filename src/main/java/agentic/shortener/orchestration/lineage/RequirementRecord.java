package agentic.shortener.orchestration.lineage;

import java.util.Objects;
import java.util.UUID;

/**
 * KE-07: a normalized, identified requirement. Task T082. FR-ORC-009.
 *
 * @param externalId the submitter-chosen or S2-assigned id a task references it by (FR-ORC-012). Unique
 *                   within a run, so decomposition can address it unambiguously
 */
public record RequirementRecord(UUID id, String externalId, RequirementType type, String statement,
                                RequirementStatus status) {

    public RequirementRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(statement, "statement");
        Objects.requireNonNull(status, "status");
        if (externalId.isBlank()) {
            throw new IllegalArgumentException("externalId must not be blank");
        }
        if (statement.isBlank()) {
            throw new IllegalArgumentException(
                    "a blank statement is not a normalized requirement — normalization MUST NOT "
                            + "discard a submitted requirement down to nothing");
        }
    }
}
