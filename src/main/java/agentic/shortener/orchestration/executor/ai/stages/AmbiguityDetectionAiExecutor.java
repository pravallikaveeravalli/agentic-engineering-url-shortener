package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.lineage.AmbiguityClass;
import agentic.shortener.orchestration.lineage.AmbiguityRecord;
import agentic.shortener.orchestration.lineage.ResolutionState;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;
import agentic.shortener.orchestration.reliability.MalformedProviderOutputException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * S3 — ambiguity detection. Task T073b. FR-ORC-010, FR-ORC-029. ADR-004.
 *
 * <p>Semantic and AI-backed: the six structural ambiguity classes could be found by a deterministic
 * checker, but {@link AmbiguityClass#SEMANTIC_CONTRADICTION} — a contradiction between different concepts,
 * with no two clauses bound to the same field — cannot, which is why this stage exists at all rather than
 * a deterministic one. Output feeds a human gate (S4), so a false positive costs only a question; a MISS is
 * the real risk, and it is indistinguishable from "nothing to find" unless a substantive
 * {@code no_clarification_reason} is recorded and readable — which is why this class requires the model to
 * ALWAYS answer with at least one record, never an empty result standing in for "all clear".
 */
public final class AmbiguityDetectionAiExecutor implements StageExecutor {

    static final String INPUT_KEY = "requirements";
    static final String OUTPUT_KEY = "ambiguities";

    /**
     * Below this length, a {@code qualityChecksPerformed} or {@code noClarificationReason} is refused as a
     * placeholder rather than accepted as substantive (CR-007's definition; {@link AmbiguityRecord}'s own
     * constructor enforces non-blank, not substantive — this adapter's own guard is stricter).
     */
    private static final int MIN_SUBSTANTIVE_LENGTH = 20;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StageAiProvider provider;

    public AmbiguityDetectionAiExecutor(StageAiProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public StageOutcome execute(StageInput input) {
        String requirements = input.inputArtifacts().get(INPUT_KEY);
        if (requirements == null || requirements.isBlank()) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "no '" + INPUT_KEY + "' artifact to check — S2 must run first", false));
        }

        return AiStageSupport.run(provider, buildPrompt(requirements), AmbiguityDetectionAiExecutor::parse);
    }

    private static String buildPrompt(String requirements) {
        return "You are the ambiguity-detection stage of a software requirement pipeline. Examine the "
                + "normalized requirements below for: missing acceptance criteria, undefined terms, "
                + "unbounded quantifiers, missing actors, self-referential constraints, contradictory "
                + "bounds on the same field, and SEMANTIC CONTRADICTIONS between different concepts (the "
                + "kind no structural rule alone would catch). You MUST NOT resolve any ambiguity you "
                + "find — only detect and record it; a human decides.\n\n"
                + "Respond with ONLY a JSON array, no prose. If you find nothing, the array MUST still "
                + "contain exactly one element with resolutionState \"NOT_MATERIAL\" naming, "
                + "substantively, what checks you actually performed — never an empty array standing in "
                + "for \"all clear\". Each element: {\"ambiguityClass\": one of MISSING_ACCEPTANCE_CRITERIA"
                + "|UNDEFINED_TERM|UNBOUNDED_QUANTIFIER|MISSING_ACTOR|SELF_REFERENTIAL_CONSTRAINT|"
                + "CONTRADICTORY_BOUNDS|SEMANTIC_CONTRADICTION, \"affectedPath\": string naming the "
                + "specific requirement(s) or field(s) involved, \"resolutionState\": \"MATERIAL_PENDING\" "
                + "or \"NOT_MATERIAL\", \"qualityChecksPerformed\": substantive string, "
                + "\"noClarificationReason\": substantive string, REQUIRED when resolutionState is "
                + "NOT_MATERIAL, omitted otherwise}.\n\nNormalized requirements:\n" + requirements;
    }

    private static List<ProducedArtifact> parse(AiResponse response) {
        JsonNode array = AiStageSupport.parseJson(response.content());
        if (!array.isArray() || array.isEmpty()) {
            throw new MalformedProviderOutputException(
                    "ambiguity detection must answer with a non-empty JSON array — an empty array cannot "
                            + "distinguish \"checked and found nothing\" from \"did not check\", which is "
                            + "exactly the miss this stage exists to make impossible. got: "
                            + (array.isArray() ? "an empty array" : array.getNodeType()));
        }

        ArrayNode out = JSON.createArrayNode();
        for (JsonNode element : array) {
            AmbiguityRecord record = toRecord(element);
            ObjectNode node = JSON.createObjectNode();
            node.put("id", record.id().toString());
            node.put("ambiguityClass", record.ambiguityClass().name());
            node.put("affectedPath", record.affectedPath());
            node.put("resolutionState", record.resolutionState().name());
            node.put("qualityChecksPerformed", record.qualityChecksPerformed());
            if (record.noClarificationReason() != null) {
                node.put("noClarificationReason", record.noClarificationReason());
            }
            out.add(node);
        }
        return List.of(new ProducedArtifact(OUTPUT_KEY, out.toString(), List.of(INPUT_KEY)));
    }

    private static AmbiguityRecord toRecord(JsonNode element) {
        AmbiguityClass ambiguityClass;
        try {
            ambiguityClass = AmbiguityClass.valueOf(textOrThrow(element, "ambiguityClass"));
        } catch (IllegalArgumentException e) {
            throw new MalformedProviderOutputException(
                    "unrecognized ambiguityClass: " + element.path("ambiguityClass").asText());
        }

        ResolutionState resolutionState;
        String rawState = textOrThrow(element, "resolutionState");
        if ("RESOLVED".equals(rawState)) {
            // S3 detects; only S4 (human clarification) may ever resolve. A detector claiming a
            // resolution invents a decision it has no authority to make.
            throw new MalformedProviderOutputException(
                    "ambiguity detection produced resolutionState=RESOLVED — only S4's human "
                            + "clarification may resolve an ambiguity; detection may only report "
                            + "MATERIAL_PENDING or NOT_MATERIAL");
        }
        try {
            resolutionState = ResolutionState.valueOf(rawState);
        } catch (IllegalArgumentException e) {
            throw new MalformedProviderOutputException("unrecognized resolutionState: " + rawState);
        }

        String affectedPath = substantiveOrThrow(element, "affectedPath");
        String qualityChecksPerformed = substantiveOrThrow(element, "qualityChecksPerformed");
        String noClarificationReason = element.has("noClarificationReason")
                ? substantiveOrThrow(element, "noClarificationReason") : null;

        // Structural validation (non-blank, reason-iff-NOT_MATERIAL) reuses AmbiguityRecord's own
        // constructor guards rather than duplicating them here.
        return new AmbiguityRecord(UUID.randomUUID(), ambiguityClass, affectedPath, resolutionState,
                qualityChecksPerformed, noClarificationReason);
    }

    private static String textOrThrow(JsonNode element, String field) {
        JsonNode value = element.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new MalformedProviderOutputException(
                    "an ambiguity record is missing a usable '" + field + "' field: " + element);
        }
        return value.asText();
    }

    /** CR-007: a placeholder ("n/a", "none") is not a substantive answer, even though it is non-blank. */
    private static String substantiveOrThrow(JsonNode element, String field) {
        String value = textOrThrow(element, field);
        if (value.strip().length() < MIN_SUBSTANTIVE_LENGTH) {
            throw new MalformedProviderOutputException(
                    "'" + field + "' is too short to be substantive (" + value.strip().length()
                            + " chars, need >= " + MIN_SUBSTANTIVE_LENGTH + "): \"" + value + "\" — a "
                            + "placeholder here defeats the reason the field exists (CR-007)");
        }
        return value;
    }
}
