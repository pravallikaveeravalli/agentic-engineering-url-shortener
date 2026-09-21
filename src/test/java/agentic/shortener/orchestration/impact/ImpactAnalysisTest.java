package agentic.shortener.orchestration.impact;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T107 — the seven-dimension brownfield impact analysis. FR-ORC-020, SC-008. DS-B.
 *
 * <p>FR-ORC-020's own negative clause: "an analysis MUST NOT omit a dimension silently." Proven here by
 * omitting each of the seven, one at a time, and confirming every single one is refused on its own —
 * omitting six of seven and leaving one blank must still fail, not merely "most dimensions missing" as a
 * generic refusal.
 */
@DisplayName("T107 — seven dimensions, none omitted silently")
class ImpactAnalysisTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID RUN_ID = UUID.randomUUID();
    private static final Instant RECORDED_AT = Instant.parse("2026-09-21T12:00:00Z");

    private static ImpactAnalysis complete() {
        return new ImpactAnalysis(ID, RUN_ID, RECORDED_AT,
                "redirect controller and rate-limit module; code -> creator resolution added",
                "no public interface change; internal ownership lookup added to the resolution path",
                "the ownership lookup enters the hot path; counters keyed per creator as well as per code",
                "the per-code tier's tests are the regression surface; a new multi-link aggregate case added",
                "threat model's noisy-neighbour trade-off (NFR-SEC-003) documented",
                "per-code and per-creator-creation tiers must remain unregressed",
                "the tier is disableable without redeploying the redirect path");
    }

    @Test
    @DisplayName("all seven dimensions present builds successfully")
    void allSevenPresentBuildsSuccessfully() {
        assertDoesNotThrow(ImpactAnalysisTest::complete);
    }

    @Test
    @DisplayName("recordedAt is carried, and is what an ordering claim against a later modification compares")
    void recordedAtSupportsTheOrderingClaim() {
        ImpactAnalysis analysis = complete();
        Instant firstCodeModification = RECORDED_AT.plusSeconds(60);

        assertTrue(analysis.recordedAt().isBefore(firstCodeModification),
                "FR-ORC-020: the analysis must precede the first code modification");
    }

    @Test
    @DisplayName("OMISSION 1/7: impactedComponents blank is refused")
    void impactedComponentsBlankIsRefused() {
        assertOmissionRefused("impactedComponents",
                () -> new ImpactAnalysis(ID, RUN_ID, RECORDED_AT, " ",
                        "interfaces", "data flows", "tests", "docs", "risks", "rollout"));
    }

    @Test
    @DisplayName("OMISSION 2/7: impactedInterfaces blank is refused")
    void impactedInterfacesBlankIsRefused() {
        assertOmissionRefused("impactedInterfaces",
                () -> new ImpactAnalysis(ID, RUN_ID, RECORDED_AT, "components",
                        null, "data flows", "tests", "docs", "risks", "rollout"));
    }

    @Test
    @DisplayName("OMISSION 3/7: impactedDataFlows blank is refused")
    void impactedDataFlowsBlankIsRefused() {
        assertOmissionRefused("impactedDataFlows",
                () -> new ImpactAnalysis(ID, RUN_ID, RECORDED_AT, "components",
                        "interfaces", "", "tests", "docs", "risks", "rollout"));
    }

    @Test
    @DisplayName("OMISSION 4/7: impactedTests blank is refused")
    void impactedTestsBlankIsRefused() {
        assertOmissionRefused("impactedTests",
                () -> new ImpactAnalysis(ID, RUN_ID, RECORDED_AT, "components",
                        "interfaces", "data flows", " ", "docs", "risks", "rollout"));
    }

    @Test
    @DisplayName("OMISSION 5/7: documentation blank is refused")
    void documentationBlankIsRefused() {
        assertOmissionRefused("documentation",
                () -> new ImpactAnalysis(ID, RUN_ID, RECORDED_AT, "components",
                        "interfaces", "data flows", "tests", null, "risks", "rollout"));
    }

    @Test
    @DisplayName("OMISSION 6/7: regressionRisks blank is refused")
    void regressionRisksBlankIsRefused() {
        assertOmissionRefused("regressionRisks",
                () -> new ImpactAnalysis(ID, RUN_ID, RECORDED_AT, "components",
                        "interfaces", "data flows", "tests", "docs", "", "rollout"));
    }

    @Test
    @DisplayName("OMISSION 7/7: rolloutRollback blank is refused")
    void rolloutRollbackBlankIsRefused() {
        assertOmissionRefused("rolloutRollback",
                () -> new ImpactAnalysis(ID, RUN_ID, RECORDED_AT, "components",
                        "interfaces", "data flows", "tests", "docs", "risks", " "));
    }

    private static void assertOmissionRefused(String dimension, Supplier<ImpactAnalysis> attempt) {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, attempt::get);
        assertTrue(refused.getMessage().contains(dimension),
                "the refusal must name the omitted dimension, or a caller cannot tell which one: "
                        + refused.getMessage());
    }
}
