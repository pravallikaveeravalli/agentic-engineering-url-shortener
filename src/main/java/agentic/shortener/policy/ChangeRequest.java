package agentic.shortener.policy;

import java.util.List;
import java.util.Objects;

/**
 * A material change to an approved artifact, with its impact analysis. Task T106. Constitution VI,
 * {@code POL-CHG-001}, NFR-CHG-001.
 *
 * <p><strong>Material change implemented without impact analysis is a detection target; the policy check
 * is the detector</strong> (T106's own Guard clause) — see {@link #changedArtifactsMissingARecord}, which
 * {@code POL-CHG-001} calls to find exactly this.
 *
 * @param id                 e.g. {@code CR-043}
 * @param artifactPath       the changed artifact's repo-relative path
 * @param owner              who raised the change
 * @param versionImpact      e.g. "MINOR — additive only", matching this project's own CR classification
 *                           convention
 * @param compatibilityImpact what a consumer relying on the previous shape would see
 * @param affectedConsumers  who or what reads this artifact
 * @param tests              the tests that verify this specific change
 * @param documentation      where this change's own documentation lives (this record itself, typically)
 * @param rolloutSteps       the applied/verification steps, in order
 * @param approval           who or what approved it, and how — "mechanical completion of CR-NNN" is a
 *                           legitimate value here, matching CR-043's own precedent
 */
public record ChangeRequest(String id, String artifactPath, String owner, String versionImpact,
                             String compatibilityImpact, List<String> affectedConsumers, List<String> tests,
                             String documentation, List<String> rolloutSteps, String approval) {

    public ChangeRequest {
        requireNonBlank(id, "id");
        requireNonBlank(artifactPath, "artifactPath");
        requireNonBlank(owner, "owner");
        requireNonBlank(versionImpact, "versionImpact");
        requireNonBlank(compatibilityImpact, "compatibilityImpact");
        Objects.requireNonNull(affectedConsumers, "affectedConsumers");
        if (affectedConsumers.isEmpty()) {
            throw new IllegalArgumentException(
                    "affectedConsumers must name at least one consumer, or 'none' explicitly — a change "
                            + "with no stated consumers is unanalyzed, not consumer-free");
        }
        Objects.requireNonNull(tests, "tests");
        if (tests.isEmpty()) {
            throw new IllegalArgumentException("tests must name at least one verifying test");
        }
        requireNonBlank(documentation, "documentation");
        Objects.requireNonNull(rolloutSteps, "rolloutSteps");
        if (rolloutSteps.isEmpty()) {
            throw new IllegalArgumentException("rolloutSteps must name at least one step");
        }
        requireNonBlank(approval, "approval");
        affectedConsumers = List.copyOf(affectedConsumers);
        tests = List.copyOf(tests);
        rolloutSteps = List.copyOf(rolloutSteps);
    }

    /**
     * {@code POL-CHG-001}'s detector: every path in {@code changedArtifactPaths} that no {@code
     * ChangeRequest} in {@code onRecord} names, returned so the check can name what is missing rather than
     * just report a count.
     */
    public static List<String> changedArtifactsMissingARecord(List<String> changedArtifactPaths,
                                                                List<ChangeRequest> onRecord) {
        Objects.requireNonNull(changedArtifactPaths, "changedArtifactPaths");
        Objects.requireNonNull(onRecord, "onRecord");
        var recorded = onRecord.stream().map(ChangeRequest::artifactPath).collect(java.util.stream.Collectors.toSet());
        return changedArtifactPaths.stream().filter(path -> !recorded.contains(path)).toList();
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
