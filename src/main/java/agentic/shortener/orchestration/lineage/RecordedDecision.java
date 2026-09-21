package agentic.shortener.orchestration.lineage;

import java.time.Instant;
import java.util.Objects;

/**
 * A gate decision as provenance sees it. Task T081. FR-ORC-013, CR-013.
 *
 * <p>Carries {@code repositoryRecordPath} because CR-013 makes a decision unusable until its repository
 * record exists. Provenance that named a decision without saying where the record is would send a reader
 * looking for it, which is most of the way to a decision nobody can find.
 *
 * <p>There is no {@code TIMED_OUT} outcome to represent, by construction — the store's CHECK admits four
 * outcomes and a deadline expiring is not one of them.
 */
public record RecordedDecision(String gateId, String outcome, String actorName, Instant decidedAt,
                               String repositoryRecordPath) {

    public RecordedDecision {
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(actorName, "actorName");
        Objects.requireNonNull(decidedAt, "decidedAt");
        Objects.requireNonNull(repositoryRecordPath, "repositoryRecordPath");
    }
}
