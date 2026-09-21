package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;
import agentic.shortener.orchestration.reliability.MalformedProviderOutputException;
import agentic.shortener.orchestration.reliability.ProviderFailureTranslator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * S5 — decomposition. Task T073c. FR-ORC-012, FR-ORC-029. ADR-004.
 *
 * <p>Turns normalized requirements into dependency-ordered {@code TaskRecord}s (KE-12), each tracing to at
 * least one requirement — matching {@code LineageStore.recordTask}'s own refusal of a zero-requirement task
 * (T082, FR-ORC-012). This class enforces the SAME rule one layer earlier, before the AI's own invented
 * task ever reaches that store.
 *
 * <h2>Two guards, both Constitution I: decomposition MUST NOT invent scope</h2>
 *
 * <ul>
 *   <li><strong>Orphan-task rejection.</strong> A task with zero requirement references is refused, not
 *       stored — the same shape {@code LineageStore.recordTask} already refuses at the persistence layer.
 *   <li><strong>No-invented-scope check.</strong> A task referencing a requirement id absent from the
 *       INPUT requirement set is refused — that id was invented, not decomposed from what was given.
 * </ul>
 */
public final class DecompositionAiExecutor implements StageExecutor {

    static final String INPUT_KEY = "requirements";
    static final String OUTPUT_KEY = "tasks";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StageAiProvider provider;

    public DecompositionAiExecutor(StageAiProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public StageOutcome execute(StageInput input) {
        String requirements = input.inputArtifacts().get(INPUT_KEY);
        if (requirements == null || requirements.isBlank()) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "no '" + INPUT_KEY + "' artifact to decompose — S2 must run first", false));
        }

        Set<String> knownRequirementIds;
        try {
            knownRequirementIds = requirementExternalIds(requirements);
        } catch (Exception e) {
            return StageOutcome.failed(ProviderFailureTranslator.forSubprocessProvider().translate(e));
        }

        return AiStageSupport.run(provider, buildPrompt(requirements),
                response -> parse(response, knownRequirementIds));
    }

    private static Set<String> requirementExternalIds(String requirements) {
        JsonNode array = AiStageSupport.parseJson(requirements);
        Set<String> ids = new LinkedHashSet<>();
        if (array.isArray()) {
            for (JsonNode element : array) {
                JsonNode externalId = element.get("externalId");
                if (externalId != null && externalId.isTextual()) {
                    ids.add(externalId.asText());
                }
            }
        }
        if (ids.isEmpty()) {
            throw new MalformedProviderOutputException(
                    "the '" + INPUT_KEY + "' artifact carried no requirement external ids — S2's own "
                            + "output is malformed, which is an upstream defect, not this stage's own");
        }
        return ids;
    }

    private static String buildPrompt(String requirements) {
        return "You are the decomposition stage of a software requirement pipeline. Break the requirements "
                + "below into dependency-ordered tasks. Every task MUST trace to at least one of the given "
                + "requirement external ids — you MUST NOT invent scope: never reference a requirement id "
                + "that is not in the list below, and never produce a task with zero requirement "
                + "references.\n\n"
                + "Respond with ONLY a JSON array, no prose. Each element: {\"taskId\": string, "
                + "\"requirementIds\": [at least one of the given external ids], \"dependsOn\": [task ids "
                + "from THIS same array that must complete first, or empty]}.\n\nRequirements:\n"
                + requirements;
    }

    private static List<ProducedArtifact> parse(AiResponse response, Set<String> knownRequirementIds) {
        JsonNode array = AiStageSupport.parseJson(response.content());
        if (!array.isArray() || array.isEmpty()) {
            throw new MalformedProviderOutputException(
                    "decomposition must answer with a non-empty JSON array of tasks; got: "
                            + (array.isArray() ? "an empty array" : array.getNodeType()));
        }

        Set<String> taskIds = new LinkedHashSet<>();
        ArrayNode out = JSON.createArrayNode();
        for (JsonNode element : array) {
            String taskId = textOrThrow(element, "taskId");
            List<String> requirementIds = stringArrayOrThrow(element, "requirementIds");

            // Orphan-task rejection (FR-ORC-012, Constitution I).
            if (requirementIds.isEmpty()) {
                throw new MalformedProviderOutputException(
                        "task '" + taskId + "' references ZERO requirements — an orphan task is refused, "
                                + "not stored (FR-ORC-012, matching LineageStore.recordTask's own rule)");
            }
            // No-invented-scope check (Constitution I).
            for (String requirementId : requirementIds) {
                if (!knownRequirementIds.contains(requirementId)) {
                    throw new MalformedProviderOutputException(
                            "task '" + taskId + "' references requirement '" + requirementId + "', which "
                                    + "is not in the input requirement set " + knownRequirementIds
                                    + " — decomposition MUST NOT invent scope (Constitution I)");
                }
            }

            List<String> dependsOn = element.has("dependsOn")
                    ? stringArrayOrThrow(element, "dependsOn") : List.of();

            taskIds.add(taskId);
            ObjectNode node = JSON.createObjectNode();
            node.put("id", UUID.randomUUID().toString());
            node.put("taskId", taskId);
            ArrayNode reqIds = JSON.createArrayNode();
            requirementIds.forEach(reqIds::add);
            node.set("requirementIds", reqIds);
            ArrayNode deps = JSON.createArrayNode();
            dependsOn.forEach(deps::add);
            node.set("dependsOn", deps);
            out.add(node);
        }

        // A dependency naming a task id outside this same batch is also invented scope: nothing this
        // stage produced could complete such a dependency.
        for (JsonNode element : out) {
            for (JsonNode dep : element.get("dependsOn")) {
                if (!taskIds.contains(dep.asText())) {
                    throw new MalformedProviderOutputException(
                            "task '" + element.get("taskId").asText() + "' depends on '" + dep.asText()
                                    + "', which is not one of the tasks this stage produced: " + taskIds);
                }
            }
        }

        return List.of(new ProducedArtifact(OUTPUT_KEY, out.toString(), List.of(INPUT_KEY)));
    }

    private static String textOrThrow(JsonNode element, String field) {
        JsonNode value = element.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new MalformedProviderOutputException(
                    "a task record is missing a usable '" + field + "' field: " + element);
        }
        return value.asText();
    }

    private static List<String> stringArrayOrThrow(JsonNode element, String field) {
        JsonNode value = element.get(field);
        if (value == null || !value.isArray()) {
            throw new MalformedProviderOutputException(
                    "a task record's '" + field + "' must be a JSON array: " + element);
        }
        List<String> items = new java.util.ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new MalformedProviderOutputException(
                        "a task record's '" + field + "' contains a non-string or blank entry: " + element);
            }
            items.add(item.asText());
        }
        return items;
    }
}
