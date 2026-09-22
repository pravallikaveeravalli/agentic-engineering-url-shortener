package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageExecutorContract;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.reliability.FailureCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T073b — S3 ambiguity-detection AI adapter. FR-ORC-010, FR-ORC-029.
 *
 * <p>DS-C's conflicting-input case and DS-A's clean-input case, both against a fake {@link StageAiProvider}
 * answering a recorded fixture — no live CLI (FR-ORC-030).
 */
@DisplayName("T073b — S3 ambiguity-detection adapter: DS-C conflict, DS-A clean, and the no-empty-array guard")
class AmbiguityDetectionAiExecutorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static StageInput inputWith(String requirements) {
        return new StageInput(UUID.randomUUID(), "S3", 3, 1,
                Map.of(AmbiguityDetectionAiExecutor.INPUT_KEY, requirements));
    }

    private static StageAiProvider fixedResponse(String json) {
        return prompt -> new AiResponse("claude-test-fixture", json);
    }

    @Nested
    @DisplayName("the StageExecutorContract, applied to AmbiguityDetectionAiExecutor")
    class Contract extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new AmbiguityDetectionAiExecutor(fixedResponse("[{"
                    + "\"ambiguityClass\":\"MISSING_ACCEPTANCE_CRITERIA\","
                    + "\"affectedPath\":\"the whole requirement set was reviewed for gaps\","
                    + "\"resolutionState\":\"NOT_MATERIAL\","
                    + "\"qualityChecksPerformed\":\"checked all six structural classes and semantic "
                    + "contradiction\","
                    + "\"noClarificationReason\":\"the requirement is already fully specified in full\""
                    + "}]"));
        }

        @Override
        protected StageInput validInput() {
            return inputWith("[{\"statement\":\"a fully specified requirement\"}]");
        }
    }

    @Test
    @DisplayName("DS-C: conflicting input is detected, with the conflicting elements named")
    void dsCConflictIsDetectedAndNamed() throws Exception {
        StageAiProvider provider = fixedResponse("[{"
                + "\"ambiguityClass\":\"SEMANTIC_CONTRADICTION\","
                + "\"affectedPath\":\"expiry (7 days) contradicts indefinite analytics retention clause\","
                + "\"resolutionState\":\"MATERIAL_PENDING\","
                + "\"qualityChecksPerformed\":\"cross-checked every retention and expiry clause pairwise\""
                + "}]");

        StageOutcome outcome = new AmbiguityDetectionAiExecutor(provider).execute(
                inputWith("[{\"statement\":\"expire after 7 days\"},"
                        + "{\"statement\":\"retain analytics indefinitely\"}]"));

        assertTrue(outcome.succeeded());
        JsonNode records = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertEquals(1, records.size());
        assertEquals("MATERIAL_PENDING", records.get(0).get("resolutionState").asText());
        assertTrue(records.get(0).get("affectedPath").asText().contains("expiry"),
                "the conflicting elements must be named, not merely flagged");
    }

    @Test
    @DisplayName("DS-A: clean input produces NO ambiguity AND a substantive no_clarification_reason")
    void dsACleanInputProducesSubstantiveReason() throws Exception {
        StageAiProvider provider = fixedResponse("[{"
                + "\"ambiguityClass\":\"MISSING_ACCEPTANCE_CRITERIA\","
                + "\"affectedPath\":\"n/a - no material ambiguity found in this requirement set\","
                + "\"resolutionState\":\"NOT_MATERIAL\","
                + "\"qualityChecksPerformed\":\"checked all six structural classes plus semantic "
                + "contradiction across every clause pair; requirement is fully specified\","
                + "\"noClarificationReason\":\"FR-URL-016 already states the tier, its limit and its "
                + "negative criteria explicitly, leaving nothing ambiguous to clarify\""
                + "}]");

        StageOutcome outcome = new AmbiguityDetectionAiExecutor(provider)
                .execute(inputWith("[{\"statement\":\"a fully specified requirement\"}]"));

        assertTrue(outcome.succeeded());
        JsonNode records = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertEquals("NOT_MATERIAL", records.get(0).get("resolutionState").asText());
        assertTrue(records.get(0).get("noClarificationReason").asText().length() >= 20,
                "the reason must be substantive, not a placeholder");
    }

    @Test
    @DisplayName("NEGATIVE: an EMPTY array is refused — cannot distinguish \"checked\" from \"did not check\"")
    void emptyArrayIsRefused() {
        StageAiProvider provider = fixedResponse("[]");

        StageOutcome outcome = new AmbiguityDetectionAiExecutor(provider).execute(inputWith("[]"));

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
        assertFalse(outcome.failure().executorProposesRetryable());
    }

    @Test
    @DisplayName("NEGATIVE: a placeholder no_clarification_reason (\"n/a\") is refused as not substantive")
    void placeholderReasonIsRefused() {
        StageAiProvider provider = fixedResponse("[{"
                + "\"ambiguityClass\":\"MISSING_ACCEPTANCE_CRITERIA\","
                + "\"affectedPath\":\"the whole requirement set was reviewed for gaps\","
                + "\"resolutionState\":\"NOT_MATERIAL\","
                + "\"qualityChecksPerformed\":\"checked all six structural classes and semantic "
                + "contradiction\","
                + "\"noClarificationReason\":\"n/a\""
                + "}]");

        StageOutcome outcome = new AmbiguityDetectionAiExecutor(provider).execute(inputWith("[...]"));

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }

    // ==============================================================================================
    // CR-049: the materiality-classification step. The predicate itself is stated generally, in
    // CR-007's own terms — a reviewer reading only the prompt must see a general predicate, not a
    // DS-A-specific answer key. These tests capture the REAL prompt text and check both directions.
    // ==============================================================================================

    @Test
    @DisplayName("CR-049: the prompt states CR-007's materiality predicate, generally")
    void promptStatesTheMaterialityPredicateGenerally() throws Exception {
        String[] capturedPrompt = new String[1];
        StageAiProvider capturing = prompt -> {
            capturedPrompt[0] = prompt;
            return new AiResponse("claude-test-fixture", "[{"
                    + "\"ambiguityClass\":\"MISSING_ACCEPTANCE_CRITERIA\","
                    + "\"affectedPath\":\"n/a\",\"resolutionState\":\"NOT_MATERIAL\","
                    + "\"qualityChecksPerformed\":\"checked\","
                    + "\"noClarificationReason\":\"already fixed by an existing approved artifact\"}]");
        };

        new AmbiguityDetectionAiExecutor(capturing).execute(inputWith("[{\"statement\":\"x\"}]"));

        String prompt = capturedPrompt[0];
        String lower = prompt.toLowerCase();
        assertTrue(lower.contains("material"), "the prompt must name materiality at all");
        assertTrue(lower.contains("approved obligation") || lower.contains("alter an approved"),
                "expected CR-007's own 'alter an approved obligation' language: " + prompt);
        assertTrue(lower.contains("already fixed by an existing approved artifact")
                        || lower.contains("existing approved artifact"),
                "expected CR-007's own 'not already fixed by an existing approved artifact' language: "
                        + prompt);
        assertTrue(lower.contains("uncertain"),
                "expected CR-007's own closing default to be stated: uncertainty routes to material: "
                        + prompt);
    }

    @Test
    @DisplayName("CR-049: the predicate contains ZERO DS-A-specific vocabulary — general, not an answer key")
    void promptContainsNoDsASpecificVocabulary() throws Exception {
        String[] capturedPrompt = new String[1];
        StageAiProvider capturing = prompt -> {
            capturedPrompt[0] = prompt;
            return new AiResponse("claude-test-fixture", "[{"
                    + "\"ambiguityClass\":\"MISSING_ACCEPTANCE_CRITERIA\","
                    + "\"affectedPath\":\"n/a\",\"resolutionState\":\"NOT_MATERIAL\","
                    + "\"qualityChecksPerformed\":\"checked\","
                    + "\"noClarificationReason\":\"already fixed by an existing approved artifact\"}]");
        };

        new AmbiguityDetectionAiExecutor(capturing).execute(inputWith("[{\"statement\":\"x\"}]"));

        String lower = capturedPrompt[0].toLowerCase();
        List<String> forbidden = List.of("expiry", "expires", "expiresat", "rounding", "round", "401",
                "creator", "owner", "secondsremaining", "link");
        for (String term : forbidden) {
            assertFalse(lower.contains(term),
                    "the prompt must state the materiality predicate generally, with zero reference to "
                            + "any specific DS-A finding — found DS-A-specific term '" + term + "' in: "
                            + capturedPrompt[0]);
        }
    }

    @Test
    @DisplayName("an explicit JSON null for noClarificationReason on a MATERIAL_PENDING record is accepted, "
            + "same as an omitted key — found live via the CR-049 compensating check")
    void explicitNullNoClarificationReasonOnMaterialIsAccepted() throws Exception {
        // A real model may include the key with an explicit JSON null rather than omitting it entirely —
        // both mean "no reason given", and MATERIAL_PENDING correctly has none to give.
        StageAiProvider provider = fixedResponse("[{"
                + "\"ambiguityClass\":\"UNDEFINED_TERM\","
                + "\"affectedPath\":\"the term 'trusted partner' is used without any definition anywhere\","
                + "\"resolutionState\":\"MATERIAL_PENDING\","
                + "\"qualityChecksPerformed\":\"searched the requirement set for a definition; found none\","
                + "\"noClarificationReason\":null"
                + "}]");

        StageOutcome outcome = new AmbiguityDetectionAiExecutor(provider).execute(inputWith("[...]"));

        assertTrue(outcome.succeeded(), outcome.succeeded() ? "" : "unexpected failure: " + outcome.failure());
        JsonNode records = JSON.readTree(outcome.producedArtifacts().get(0).content());
        assertEquals("MATERIAL_PENDING", records.get(0).get("resolutionState").asText());
        assertFalse(records.get(0).has("noClarificationReason"),
                "an inapplicable field must not resurface in the produced artifact either");
    }

    @Test
    @DisplayName("NEGATIVE: resolutionState=RESOLVED from the detector is refused — only S4 may resolve")
    void detectorClaimingResolvedIsRefused() {
        StageAiProvider provider = fixedResponse("[{"
                + "\"ambiguityClass\":\"UNDEFINED_TERM\","
                + "\"affectedPath\":\"the term 'trusted partner' used without definition anywhere\","
                + "\"resolutionState\":\"RESOLVED\","
                + "\"qualityChecksPerformed\":\"checked every term against the glossary section\""
                + "}]");

        StageOutcome outcome = new AmbiguityDetectionAiExecutor(provider).execute(inputWith("[...]"));

        assertFalse(outcome.succeeded());
        assertEquals(FailureCategory.INTERNAL, outcome.failure().category());
    }
}
