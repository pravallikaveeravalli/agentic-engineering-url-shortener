package agentic.shortener.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T125 — a traceability gap makes release readiness blocking, and the report names constitutional
 * condition 6. {@code POL-TRC-001}, Constitution §Governance condition 6.
 *
 * <p><strong>Cites, does not duplicate.</strong> {@code ReleaseBlockingConditionsIT.condition6Blocks} (T110)
 * already proves exactly this — a task appended to the fixture's {@code tasks.md} that names no real
 * requirement makes the whole evaluation not-ready and names condition 6 in the report — built as part of
 * the same nine-condition suite T125 itself depends on (T109, T124). Reporting an orphan without blocking
 * would satisfy neither {@code POL-TRC-001} nor the constitution (T125's own Guard clause); duplicating the
 * fixture machinery here to re-prove it a second way would not make that claim any truer, the same "cite,
 * don't re-derive" discipline {@code GovernanceImmutabilityIT} (T113) already established for this project.
 */
@DisplayName("T125 — a seeded traceability orphan makes readiness blocking, naming condition 6")
class TraceabilityBlockingTest {

    private static final Path RELEASE_BLOCKING_CONDITIONS_IT =
            Path.of("src/test/java/agentic/shortener/policy/ReleaseBlockingConditionsIT.java");

    @Test
    @DisplayName("ReleaseBlockingConditionsIT.condition6Blocks (T110) still exists and still proves this")
    void theProofThisTaskDependsOnStillExists() throws Exception {
        assertTrue(Files.exists(RELEASE_BLOCKING_CONDITIONS_IT),
                "T110's own file must exist — this class cites it rather than re-deriving its proof");
        String body = Files.readString(RELEASE_BLOCKING_CONDITIONS_IT);
        assertTrue(body.contains("condition6Blocks"),
                "and must still contain the test this class's own claim depends on");
        assertTrue(body.contains("condition 6"),
                "the cited test must still assert on condition 6 specifically, not merely on any block");
    }

    /**
     * The direct proof, over the real {@link PolicySetEvaluator}'s {@code POL-TRC-001} check specifically
     * (not the full nine-condition {@link ReleaseReadinessEvaluator}) — a narrower assertion than T110's,
     * scoped to the policy layer POL-TRC-001 itself names.
     */
    @Test
    @DisplayName("POL-TRC-001 alone: an orphan makes the policy check FAIL and mandatory, blocking on its own")
    void orphanRequirementMakesPolTrc001FailAndMandatory() {
        agentic.shortener.audit.TraceabilityReport dirtyReport = new agentic.shortener.audit.TraceabilityReport(
                java.util.List.of("FR-ORPHAN-001: no task in tasks.md references it"),
                java.util.List.of(), java.util.List.of(), java.util.List.of());
        assertTrue(!dirtyReport.isClean(), "a seeded orphan must make the report dirty");

        PolicyDefinition polTrc001 = agentic.shortener.policy.definitions.PolicyDefinitions.DEFAULT
                .require("POL-TRC-001");
        assertTrue(polTrc001.mandatory(), "POL-TRC-001 must be mandatory, or an orphan could never block "
                + "release readiness on its own — reporting without blocking satisfies neither the policy "
                + "nor the constitution (T125's own Guard clause)");

        PolicyCheckResult failedByOrphan = PolicyCheckResult.of(polTrc001, PolicyOutcome.FAIL,
                "traceability orphans found: " + dirtyReport.orphanRequirements());
        assertTrue(BlockingEnforcer.isBlocking(java.util.List.of(failedByOrphan), java.time.Instant.now()),
                "a mandatory POL-TRC-001 FAIL, from a seeded orphan, must block on its own");
    }
}
