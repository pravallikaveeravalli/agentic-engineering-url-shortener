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

/**
 * S9 — documentation. Task T073f. FR-ORC-029, Constitution X. ADR-004.
 *
 * <h2>The guard against drift, applied at the point of authorship</h2>
 *
 * <p>Documentation MUST NOT describe behaviour the run did not deliver — this is T149's drift check,
 * enforced here BEFORE authorship rather than only caught later at audit. {@code results} (S8's real test
 * outcomes) names the behaviours actually exercised; the model's own {@code behaviorsDescribed} list is
 * cross-checked against it, and any entry not present there is refused. A documented behaviour with no
 * corresponding executed test is not merely unverified — it is invented, and Constitution I forbids
 * inventing scope as much for prose as for code.
 */
public final class DocumentationAiExecutor implements StageExecutor {

    static final String INPUT_CHANGE_KEY = "change";
    static final String INPUT_RESULTS_KEY = "results";
    static final String OUTPUT_KEY = "documentation";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StageAiProvider provider;

    public DocumentationAiExecutor(StageAiProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public StageOutcome execute(StageInput input) {
        String change = input.inputArtifacts().get(INPUT_CHANGE_KEY);
        String results = input.inputArtifacts().get(INPUT_RESULTS_KEY);
        if (change == null || change.isBlank() || results == null || results.isBlank()) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "both '" + INPUT_CHANGE_KEY + "' and '" + INPUT_RESULTS_KEY
                            + "' are required — S7 and S8 must run first", false));
        }

        Set<String> executedBehaviors;
        try {
            executedBehaviors = executedBehaviors(results);
        } catch (Exception e) {
            return StageOutcome.failed(ProviderFailureTranslator.forSubprocessProvider().translate(e));
        }

        return AiStageSupport.run(provider, buildPrompt(change, results),
                response -> parse(response, executedBehaviors));
    }

    private static Set<String> executedBehaviors(String results) {
        JsonNode root = AiStageSupport.parseJson(results);
        Set<String> behaviors = new LinkedHashSet<>();
        JsonNode list = root.get("executedBehaviors");
        if (list != null && list.isArray()) {
            for (JsonNode item : list) {
                if (item.isTextual()) {
                    behaviors.add(item.asText());
                }
            }
        }
        if (behaviors.isEmpty()) {
            throw new MalformedProviderOutputException(
                    "the '" + INPUT_RESULTS_KEY + "' artifact carried no executed behaviours — S8's own "
                            + "output is malformed, which is an upstream defect, not this stage's own");
        }
        return behaviors;
    }

    private static String buildPrompt(String change, String results) {
        return "You are the documentation stage of a software requirement pipeline. Update documentation "
                + "to reflect ONLY the behaviour delivered and verified in THIS run — you MUST NOT describe "
                + "any behaviour that was not actually executed by a test in the results below.\n\n"
                + "Respond with ONLY JSON, no prose: {\"documentation\": string, \"behaviorsDescribed\": "
                + "[every behaviour name from the results below that your documentation text describes]}."
                + "\n\nChange:\n" + change + "\n\nTest results:\n" + results;
    }

    private static List<ProducedArtifact> parse(AiResponse response, Set<String> executedBehaviors) {
        JsonNode root = AiStageSupport.parseJson(response.content());
        if (!root.isObject()) {
            throw new MalformedProviderOutputException(
                    "documentation must answer with a JSON object, got: " + root.getNodeType());
        }
        String documentation = textOrThrow(root, "documentation");
        List<String> described = stringArrayOrThrow(root, "behaviorsDescribed");

        for (String behavior : described) {
            if (!executedBehaviors.contains(behavior)) {
                throw new MalformedProviderOutputException(
                        "documentation describes behaviour '" + behavior + "', which has no corresponding "
                            + "executed test in this run's results " + executedBehaviors + " — a documented "
                            + "behaviour the run did not deliver is refused (Constitution X, T149's drift "
                            + "check applied at authorship)");
            }
        }

        ObjectNode out = JSON.createObjectNode();
        out.put("documentation", documentation);
        ArrayNode describedOut = JSON.createArrayNode();
        described.forEach(describedOut::add);
        out.set("behaviorsDescribed", describedOut);

        return List.of(new ProducedArtifact(OUTPUT_KEY, out.toString(),
                List.of(INPUT_CHANGE_KEY, INPUT_RESULTS_KEY)));
    }

    private static String textOrThrow(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new MalformedProviderOutputException(
                    "documentation output is missing a usable '" + field + "' field: " + root);
        }
        return value.asText();
    }

    private static List<String> stringArrayOrThrow(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray()) {
            throw new MalformedProviderOutputException(
                    "documentation output's '" + field + "' must be a JSON array: " + root);
        }
        List<String> items = new java.util.ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw new MalformedProviderOutputException(
                        "documentation output's '" + field + "' contains a non-string or blank entry: "
                                + root);
            }
            items.add(item.asText());
        }
        return items;
    }
}
