package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.lineage.RequirementRecord;
import agentic.shortener.orchestration.lineage.RequirementStatus;
import agentic.shortener.orchestration.lineage.RequirementType;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;
import agentic.shortener.orchestration.reliability.MalformedProviderOutputException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * S2 — normalization. Task T073a. FR-ORC-009, FR-ORC-029. ADR-004, ADR-004-A1.
 *
 * <p>Turns a raw, as-submitted requirement into identified, typed, testable {@link RequirementRecord}s.
 * AI-capable because typing and testability judgment is semantic, unlike S1's presentational-only intake
 * ({@code IngestionEngine}) — this is genuinely the first stage that reads what a requirement MEANS.
 *
 * <h2>The guard this class exists to enforce</h2>
 *
 * <p><strong>Normalization MUST NOT discard, merge, or silently reinterpret a submitted requirement.</strong>
 * One raw submission may legitimately normalize into several typed records — DS-A's own design normalizes
 * one submission into a functional requirement plus its non-functional latency constraint — so the guard is
 * not "one in, one out"; it is that every raw input item is traceable to at least one output record, and the
 * model producing FEWER output records than raw input items is refused rather than silently accepted as "no
 * further requirements found."
 */
public final class NormalizationAiExecutor implements StageExecutor {

    /** Matches {@code IngestionEngine.OUTPUT_KEY} — S1's produced artifact is S2's own input. */
    static final String INPUT_KEY = "requirement.intake";
    static final String OUTPUT_KEY = "requirements";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StageAiProvider provider;

    public NormalizationAiExecutor(StageAiProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public StageOutcome execute(StageInput input) {
        String raw = input.inputArtifacts().get(INPUT_KEY);
        if (raw == null || raw.isBlank()) {
            // Contract term (StageExecutorContract): a failure is RETURNED, never thrown — an exception
            // escaping here would arrive at the orchestrator with no category to rule retry on.
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "no '" + INPUT_KEY + "' artifact to normalize — S1 must run first", false));
        }
        List<String> rawItems = splitIntoItems(raw);

        return AiStageSupport.run(provider, buildPrompt(rawItems),
                response -> parse(response, rawItems.size()));
    }

    private static String buildPrompt(List<String> rawItems) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the normalization stage of a software requirement pipeline. For EACH of the ")
                .append(rawItems.size())
                .append(" raw requirement item(s) below, produce one or more identified, typed, testable ")
                .append("requirement statements. Never discard, merge, or reinterpret an item away — every ")
                .append("item below must be covered by at least one output statement.\n\n")
                .append("EVERY output record's externalId MUST be UNIQUE across the whole array you return — ")
                .append("never reuse the same externalId for two different statements, even if they trace ")
                .append("to the same raw input item. A downstream stage addresses one specific requirement ")
                .append("by its externalId alone; two records sharing one id become indistinguishable to it. ")
                .append("Use a distinguishing suffix per statement (e.g. \"1a\", \"1b\", \"1c\") when one raw ")
                .append("item normalizes into several records.\n\n")
                .append("Respond with ONLY a JSON array, no prose, where each element is: ")
                .append("{\"externalId\": string, \"type\": \"FUNCTIONAL\" or \"NON_FUNCTIONAL\", ")
                .append("\"statement\": string}.\n\nRaw requirement item(s):\n");
        for (int i = 0; i < rawItems.size(); i++) {
            prompt.append(i + 1).append(". ").append(rawItems.get(i)).append('\n');
        }
        return prompt.toString();
    }

    private static List<ProducedArtifact> parse(AiResponse response, int rawItemCount) {
        JsonNode array = AiStageSupport.parseJson(response.content());
        if (!array.isArray()) {
            throw new MalformedProviderOutputException(
                    "normalization must answer with a JSON array of requirement records; got: "
                            + array.getNodeType());
        }
        if (array.size() < rawItemCount) {
            throw new MalformedProviderOutputException(
                    "normalization produced " + array.size() + " requirement record(s) for "
                            + rawItemCount + " raw input item(s) — fewer records than inputs means an item "
                            + "was silently discarded, which the guard refuses rather than accepting as an "
                            + "empty requirement set");
        }

        ArrayNode out = JSON.createArrayNode();
        List<RequirementRecord> records = new ArrayList<>();
        java.util.Set<String> seenExternalIds = new java.util.LinkedHashSet<>();
        for (JsonNode element : array) {
            RequirementRecord record = toRecord(element);
            // Structural uniqueness guard: a downstream stage (S5) addresses one specific requirement by
            // its externalId alone -- two records sharing one id are indistinguishable to it, which
            // surfaced live as S5's own no-invented-scope guard refusing a real, correctly-addressed task
            // the model could no longer express (T132's first real S5 dispatch). Refused here, at the
            // source, rather than left for a downstream consumer to discover the hard way.
            if (!seenExternalIds.add(record.externalId())) {
                throw new MalformedProviderOutputException(
                        "normalization produced two or more requirement records sharing externalId '"
                                + record.externalId() + "' — a downstream stage cannot address one specific "
                                + "requirement when its id is not unique; every output record's externalId "
                                + "must be unique across the whole array");
            }
            records.add(record);
            ObjectNode node = JSON.createObjectNode();
            node.put("id", record.id().toString());
            node.put("externalId", record.externalId());
            node.put("type", record.type().name());
            node.put("statement", record.statement());
            node.put("status", record.status().name());
            out.add(node);
        }

        return List.of(new ProducedArtifact(OUTPUT_KEY, out.toString(), List.of(INPUT_KEY)));
    }

    private static RequirementRecord toRecord(JsonNode element) {
        String externalId = textOrThrow(element, "externalId");
        String statement = textOrThrow(element, "statement");
        RequirementType type;
        try {
            type = RequirementType.valueOf(textOrThrow(element, "type"));
        } catch (IllegalArgumentException e) {
            throw new MalformedProviderOutputException(
                    "requirement record '" + externalId + "' has an unrecognized type: "
                            + element.path("type").asText());
        }
        // Structural validation reuses RequirementRecord's own constructor guards (blank statement, etc.)
        // rather than duplicating them here.
        return new RequirementRecord(UUID.randomUUID(), externalId, type, statement,
                RequirementStatus.NORMALIZED);
    }

    private static String textOrThrow(JsonNode element, String field) {
        JsonNode value = element.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new MalformedProviderOutputException(
                    "a requirement record is missing a usable '" + field + "' field: " + element);
        }
        return value.asText();
    }

    /**
     * S1 hands S2 one normalized text blob ({@code IngestionEngine}'s own normalization is presentational
     * only). One blank line separates distinct raw items when the submitter provided several; a submission
     * with no blank line is treated as a single raw item.
     */
    private static List<String> splitIntoItems(String raw) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            if (line.isBlank()) {
                if (!current.isEmpty()) {
                    items.add(current.toString().strip());
                    current.setLength(0);
                }
            } else {
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        if (!current.isEmpty()) {
            items.add(current.toString().strip());
        }
        return items.isEmpty() ? List.of(raw.strip()) : items;
    }
}
