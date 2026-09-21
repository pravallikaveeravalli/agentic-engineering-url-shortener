package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.impact.ImpactAnalysis;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;
import agentic.shortener.orchestration.reliability.MalformedProviderOutputException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * S6 — architecture and design. Task T073d. FR-ORC-020, FR-ORC-029. ADR-004, ADR-006. T107 sits in this
 * phase deliberately — see this class's dependency on {@link ImpactAnalysis}.
 *
 * <h2>Why this adapter cannot be told "this is brownfield" and enforces completeness instead</h2>
 *
 * <p>{@link StageInput} carries no scenario label and no flag (FR-ORC-028) — nothing this class receives
 * says "existing code is affected, produce the seven-dimension analysis." So the rule this class enforces
 * is not "an impact analysis is always required"; it is the one the plan's Guard actually states: <strong>an
 * impact analysis that IS offered must never be partial.</strong> If the model's answer omits the
 * {@code impactAnalysis} object entirely, that is read as "no existing code is impacted" and accepted with
 * design output alone. If the object is present, {@link ImpactAnalysis}'s own constructor — the same seven
 * required, non-blank dimensions T107 built — is what enforces completeness; a response naming six of seven
 * dimensions is refused exactly as a response naming zero of seven design fields would be.
 */
public final class DesignAiExecutor implements StageExecutor {

    static final String INPUT_KEY = "tasks";
    static final String OUTPUT_KEY = "design";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StageAiProvider provider;
    private final Clock clock;

    public DesignAiExecutor(StageAiProvider provider, Clock clock) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public StageOutcome execute(StageInput input) {
        String tasks = input.inputArtifacts().get(INPUT_KEY);
        if (tasks == null || tasks.isBlank()) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "no '" + INPUT_KEY + "' artifact to design — S5 must run first", false));
        }

        return AiStageSupport.run(provider, buildPrompt(tasks),
                response -> parse(response, input.runId()));
    }

    private static String buildPrompt(String tasks) {
        return "You are the architecture-and-design stage of a software requirement pipeline. Produce a "
                + "design for the tasks below, and identify any impacted API contracts or schemas. If, and "
                + "only if, this change touches EXISTING code, ALSO produce a seven-dimension impact "
                + "analysis covering: impacted components, impacted interfaces, impacted data flows, "
                + "impacted tests, documentation, regression risks, and rollout/rollback considerations. "
                + "Every one of the seven MUST be substantive if you include the analysis at all — never "
                + "omit one silently.\n\n"
                + "Respond with ONLY JSON, no prose: {\"design\": string, \"contractImpact\": string, "
                + "\"impactAnalysis\": {\"impactedComponents\": string, \"impactedInterfaces\": string, "
                + "\"impactedDataFlows\": string, \"impactedTests\": string, \"documentation\": string, "
                + "\"regressionRisks\": string, \"rolloutRollback\": string} OR omit \"impactAnalysis\" "
                + "entirely for a purely new (greenfield) change}.\n\nTasks:\n" + tasks;
    }

    private List<ProducedArtifact> parse(AiResponse response, UUID runId) {
        JsonNode root = AiStageSupport.parseJson(response.content());
        if (!root.isObject()) {
            throw new MalformedProviderOutputException(
                    "design must answer with a JSON object, got: " + root.getNodeType());
        }
        String design = textOrThrow(root, "design");
        String contractImpact = root.has("contractImpact") ? root.get("contractImpact").asText() : "";

        ObjectNode out = JSON.createObjectNode();
        out.put("design", design);
        out.put("contractImpact", contractImpact);

        if (root.has("impactAnalysis") && !root.get("impactAnalysis").isNull()) {
            JsonNode ia = root.get("impactAnalysis");
            // Completeness is ImpactAnalysis's own constructor guard (T107) — the seven-dimension rule is
            // enforced ONCE, by the domain object, not re-implemented here. Its IllegalArgumentException is
            // recategorized to MalformedProviderOutputException so an incomplete analysis fails the same
            // way (INTERNAL, permanent) every other guard violation in this package does.
            ImpactAnalysis analysis;
            try {
                analysis = new ImpactAnalysis(UUID.randomUUID(), runId, clock.instant(),
                        dimensionOrThrow(ia, "impactedComponents"),
                        dimensionOrThrow(ia, "impactedInterfaces"),
                        dimensionOrThrow(ia, "impactedDataFlows"), dimensionOrThrow(ia, "impactedTests"),
                        dimensionOrThrow(ia, "documentation"), dimensionOrThrow(ia, "regressionRisks"),
                        dimensionOrThrow(ia, "rolloutRollback"));
            } catch (IllegalArgumentException e) {
                throw new MalformedProviderOutputException(
                        "design's impact analysis is incomplete: " + e.getMessage(), e);
            }

            ObjectNode iaOut = JSON.createObjectNode();
            iaOut.put("recordedAt", analysis.recordedAt().toString());
            iaOut.put("impactedComponents", analysis.impactedComponents());
            iaOut.put("impactedInterfaces", analysis.impactedInterfaces());
            iaOut.put("impactedDataFlows", analysis.impactedDataFlows());
            iaOut.put("impactedTests", analysis.impactedTests());
            iaOut.put("documentation", analysis.documentation());
            iaOut.put("regressionRisks", analysis.regressionRisks());
            iaOut.put("rolloutRollback", analysis.rolloutRollback());
            out.set("impactAnalysis", iaOut);
        }

        return List.of(new ProducedArtifact(OUTPUT_KEY, out.toString(), List.of(INPUT_KEY)));
    }

    /**
     * A dimension missing from the JSON object (as opposed to present-but-blank) is translated into
     * {@link ImpactAnalysis}'s own "must not be blank" refusal, so a caller sees one consistent failure
     * message regardless of whether the model omitted the field or supplied an empty string for it.
     */
    private static String dimensionOrThrow(JsonNode impactAnalysis, String field) {
        JsonNode value = impactAnalysis.get(field);
        return value == null || !value.isTextual() ? "" : value.asText();
    }

    private static String textOrThrow(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new MalformedProviderOutputException(
                    "design output is missing a usable '" + field + "' field: " + root);
        }
        return value.asText();
    }
}
