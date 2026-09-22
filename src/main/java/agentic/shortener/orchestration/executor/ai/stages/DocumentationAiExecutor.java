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
 *
 * <h2>CR-066: making the producer honest, never the guard weaker</h2>
 *
 * <p>A real, live attempt (docs/evidence/ds-a/attempts-24-25-finding.md) had the model name a
 * plausible-sounding test that was never in that run's own real results — the guard correctly refused it.
 * The fix is entirely on the prompt side: {@link #buildPrompt} now prints the allow-list of real executed
 * behaviours as its own explicit, bounded, "copy verbatim or not at all" list, separate from the raw {@code
 * results} JSON blob's own {@code total}/{@code failed}/{@code report} noise, rather than asking the model to
 * "consult the results below" as one instruction among several. The cross-check in {@link #parse} — the
 * actual guard — is untouched.
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

        return AiStageSupport.run(provider, buildPrompt(change, executedBehaviors),
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

    /** CR-066: the allow-list is printed explicitly and prominently, separate from the raw {@code results}
     * JSON blob it came from — burying it inside {@code total}/{@code failed}/{@code report} noise is
     * exactly the shape a real, live attempt drifted against (docs/evidence/ds-a/attempts-24-25-finding.md:
     * the model named a plausible-sounding test that was never in that run's own real results). The
     * instruction is verbatim, copy-paste selection from a visibly bounded list, not "consult the results
     * below" as one instruction among many. */
    private static String buildPrompt(String change, Set<String> executedBehaviors) {
        StringBuilder allowList = new StringBuilder();
        for (String behavior : executedBehaviors) {
            allowList.append("- ").append(behavior).append('\n');
        }
        return "You are the documentation stage of a software requirement pipeline. Update documentation "
                + "to reflect ONLY the behaviour delivered and verified in THIS run.\n\n"
                + "Below is the COMPLETE and ONLY list of behaviours this run actually verified. Every "
                + "entry in your own 'behaviorsDescribed' array MUST be copied VERBATIM, character for "
                + "character, from this list. You MUST NOT invent, generalize, paraphrase, combine, "
                + "abbreviate, or guess a name similar to one on this list — if a behaviour is not copied "
                + "EXACTLY from this list, it does not exist for the purposes of this run, even if you "
                + "believe it was probably also true or was tested in some other run. If nothing on this "
                + "list is worth documenting, describe fewer of them — never describe something not on it.\n\n"
                + "ALLOWED behaviours (copy exactly, or not at all):\n" + allowList
                + "\nRespond with ONLY JSON, no prose: {\"documentation\": string, \"behaviorsDescribed\": "
                + "[zero or more entries copied verbatim from the ALLOWED list above]}."
                + "\n\nChange:\n" + change;
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
