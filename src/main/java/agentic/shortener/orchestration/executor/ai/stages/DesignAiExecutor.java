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
 *
 * <h2>Materiality classification — plan §5's own conditional architecture gate (CR-057)</h2>
 *
 * <p>Plan §5's own Human-in-the-Loop table states the architecture-approval trigger as "S6 produces
 * <strong>material</strong> design decisions" — the same conditional shape as S3's own "S3 finds material
 * ambiguity" trigger for S4, never unconditional. This class classifies materiality itself (CR-007's own
 * definition, reused verbatim), the same way {@link AmbiguityDetectionAiExecutor} classifies an ambiguity's
 * materiality; {@link agentic.shortener.orchestration.conductor.Conductor} reads the resulting
 * {@code materialDesignDecisions} array to decide whether to open the gate at all, never this class.
 */
public final class DesignAiExecutor implements StageExecutor {

    static final String INPUT_KEY = "tasks";
    static final String OUTPUT_KEY = "design";

    /** Same threshold and rationale as {@link AmbiguityDetectionAiExecutor#MIN_SUBSTANTIVE_LENGTH}: a
     * placeholder materiality decision or justification is not a substantive answer (CR-007). */
    private static final int MIN_SUBSTANTIVE_LENGTH = 20;

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
                + "Separately, list every EXISTING repository file this design requires the implementation "
                + "stage to MODIFY (not create) under \"existingFilesToModify\": an array of "
                + "repository-relative paths (e.g. \"pom.xml\", "
                + "\"specs/001-agentic-sdlc-url-shortener/contracts/openapi.yaml\"). The implementation "
                + "stage cannot see any file's real content on its own; this list is how the orchestration "
                + "knows which files to show it. Omit or leave empty for a purely new-file change — never "
                + "list a file this design only creates, only one it requires editing in place.\n\n"
                + "You MUST ALSO classify whether this design contains any MATERIAL design decisions — this "
                + "decides whether a human architect is actually consulted before implementation begins, "
                + "so apply the predicate precisely rather than by impression: a design decision is "
                + "MATERIAL if and only if choosing differently could alter an approved obligation (any "
                + "functional or non-functional requirement, a gate condition, a stated scope boundary, the "
                + "security posture, or a binding validation target), or determines which of two "
                + "behaviours the system MUST exhibit — a genuine fork among viable alternatives, not a "
                + "single forced or idiomatic choice with no real alternative (e.g. \"which class holds a "
                + "getter\" is never material; \"whether a new endpoint requires authentication\" always "
                + "is). Non-materiality must be affirmatively shown, never assumed: when classification is "
                + "uncertain, treat it as material. List every material decision, each a substantive "
                + "sentence naming the decision and why it is a genuine fork, under "
                + "\"materialDesignDecisions\": an array of strings, empty if none. If — and only if — that "
                + "array is empty, ALSO supply \"nonMaterialityJustification\": a substantive string "
                + "stating specifically why every choice in this design was forced or idiomatic, with no "
                + "viable alternative that would change required behaviour; omit that field when the array "
                + "is non-empty.\n\n"
                + "Respond with ONLY JSON, no prose: {\"design\": string, \"contractImpact\": string, "
                + "\"existingFilesToModify\": [string, ...], "
                + "\"materialDesignDecisions\": [string, ...], "
                + "\"nonMaterialityJustification\": string (only when materialDesignDecisions is empty), "
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

        // Passed through verbatim so S7 (via Conductor's own pre-fetch) knows which real files to read --
        // an absent or empty list means a purely new-file change, the same as omitting the field entirely.
        com.fasterxml.jackson.databind.node.ArrayNode existingFiles = JSON.createArrayNode();
        JsonNode namedPaths = root.get("existingFilesToModify");
        if (namedPaths != null && namedPaths.isArray()) {
            for (JsonNode path : namedPaths) {
                if (path.isTextual() && !path.asText().isBlank()) {
                    existingFiles.add(path.asText());
                }
            }
        }
        out.set("existingFilesToModify", existingFiles);

        // Plan §5's own trigger for the architecture gate is "S6 produces MATERIAL design decisions", not
        // unconditionally -- mirrors S4's own conditional gate on S3's materiality signal exactly. A
        // claimed material decision must itself be substantive (CR-007 discipline, the same
        // anti-placeholder guard AmbiguityDetectionAiExecutor already applies) -- a decision entry too
        // short to be real content is simply dropped, not trusted, which naturally falls through to the
        // safe default below rather than hard-failing the whole design. CR-007's own uncertainty default
        // ("when classification is uncertain, treat as material") means an ABSENT or insufficiently
        // justified empty list must default to MATERIAL (open the gate), never to a hard parse failure --
        // an older or non-compliant model answer that omits this classification entirely still produces a
        // usable design, just one a human reviews, exactly as CR-007 intends.
        com.fasterxml.jackson.databind.node.ArrayNode materialDecisions = JSON.createArrayNode();
        JsonNode namedDecisions = root.get("materialDesignDecisions");
        if (namedDecisions != null && namedDecisions.isArray()) {
            for (JsonNode decision : namedDecisions) {
                if (decision.isTextual() && decision.asText().strip().length() >= MIN_SUBSTANTIVE_LENGTH) {
                    materialDecisions.add(decision.asText());
                }
            }
        }
        if (materialDecisions.isEmpty()) {
            JsonNode justificationNode = root.get("nonMaterialityJustification");
            String justification = justificationNode != null && justificationNode.isTextual()
                    ? justificationNode.asText() : "";
            if (justification.strip().length() >= MIN_SUBSTANTIVE_LENGTH) {
                out.put("nonMaterialityJustification", justification);
            } else {
                materialDecisions.add("non-materiality was not affirmatively shown (the model's answer "
                        + "omitted materialDesignDecisions, or supplied an empty list with no substantive "
                        + "nonMaterialityJustification) -- treated as material per CR-007's own uncertainty "
                        + "default rather than assumed safe");
            }
        }
        out.set("materialDesignDecisions", materialDecisions);

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
