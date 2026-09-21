package agentic.shortener.orchestration.summary;

import java.util.Objects;

/**
 * One claim, and the repo-relative path of the artifact that backs it. Task T129. FR-ORC-026.
 *
 * <p><strong>This is the whole mechanism.</strong> {@link SummaryAssembler} produces nothing but a list of
 * these; {@code SummaryAssemblerTest} asserts {@link #evidencePath()} exists on disk for every one — a
 * claim this record can be constructed with but whose file does not exist is exactly what T129's own Guard
 * clause calls a liability: "no unverifiable claims." The record itself cannot check existence (a
 * repository-relative path has no meaning without a root to resolve it against); that check is the test's
 * job, deliberately, so a passing test IS the trace-verification T129's Validate clause asks for.
 */
public record EvidencedClaim(String text, String evidencePath) {

    public EvidencedClaim {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("a claim with no text is not a claim");
        }
        Objects.requireNonNull(evidencePath, "evidencePath");
        if (evidencePath.isBlank()) {
            throw new IllegalArgumentException(
                    "a claim with no evidence path is unverifiable — exactly what this type exists to "
                            + "make impossible to construct");
        }
    }
}
