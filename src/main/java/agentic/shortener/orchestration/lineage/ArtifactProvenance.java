package agentic.shortener.orchestration.lineage;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One link in an artifact's provenance chain. Task T081. FR-ORC-005.
 *
 * <p>Answers T081's three questions about one version of one logical artifact: which stage produced it,
 * from which inputs, and under which decisions.
 *
 * @param decisionsInForce the gate decisions <strong>already recorded when this version was
 *                         produced</strong>. Deliberately not every decision the run ever makes: a gate
 *                         approved afterwards did not govern a write that had already happened, and
 *                         including it would let provenance imply an authorisation that did not exist
 */
public record ArtifactProvenance(String artifactKey, int version, String contentHash,
                                 String producedByNodeKey, List<String> inputArtifactKeys,
                                 Instant producedAt, List<RecordedDecision> decisionsInForce) {

    public ArtifactProvenance {
        Objects.requireNonNull(artifactKey, "artifactKey");
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(producedByNodeKey, "producedByNodeKey");
        Objects.requireNonNull(producedAt, "producedAt");
        inputArtifactKeys = List.copyOf(inputArtifactKeys);
        decisionsInForce = List.copyOf(decisionsInForce);
    }
}
