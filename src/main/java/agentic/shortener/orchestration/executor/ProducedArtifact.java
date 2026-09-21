package agentic.shortener.orchestration.executor;

import java.util.List;
import java.util.Objects;

/**
 * One artifact a stage produced, with what it was derived from. Task T069. FR-ORC-005.
 *
 * <p>{@code derivedFrom} is carried here rather than inferred later, because the executor is the only party
 * that knows which of its inputs it actually used. Reconstructing it afterwards from "everything the node
 * could see" would make provenance a statement about availability rather than about derivation, and
 * {@code ArtifactProvenanceQuery} would then follow a chain nobody built.
 */
public record ProducedArtifact(String artifactKey, String content, List<String> derivedFrom) {

    public ProducedArtifact {
        Objects.requireNonNull(artifactKey, "artifactKey");
        Objects.requireNonNull(content, "content");
        if (artifactKey.isBlank()) {
            throw new IllegalArgumentException("an artifact key is required; the store refuses a blank one");
        }
        derivedFrom = List.copyOf(derivedFrom);
    }
}
