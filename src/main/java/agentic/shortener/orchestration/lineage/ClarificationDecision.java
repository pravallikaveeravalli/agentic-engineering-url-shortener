package agentic.shortener.orchestration.lineage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** KE-09: a human answer to an {@link AmbiguityRecord}. Task T082. FR-ORC-011. */
public record ClarificationDecision(UUID id, String actor, String question, String answer,
                                    Instant decidedAt) {

    public ClarificationDecision {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(decidedAt, "decidedAt");
        if (question.isBlank() || answer.isBlank()) {
            throw new IllegalArgumentException("question and answer must both be non-blank");
        }
    }
}
