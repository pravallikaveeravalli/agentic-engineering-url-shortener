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
 *
 * <h2>CR-067: {@code qualityChecksPerformed} is required unconditionally — this is prompt hardening, never a
 * relaxed guard</h2>
 *
 * <p>Two real, live, independent occurrences (docs/evidence/ds-a's own attempts 18 and 27) showed the model
 * omitting {@code qualityChecksPerformed} on a {@code NOT_MATERIAL} record, apparently treating its own
 * substantive {@code noClarificationReason} as sufficient on its own. {@link AmbiguityRecord}'s own
 * constructor requires the field unconditionally BY DESIGN (T082's own domain invariant — the field is what
 * lets a reviewer tell "nothing was checked" from "checked and found nothing," a distinction that matters for
 * a {@code MATERIAL_PENDING} finding as much as a {@code NOT_MATERIAL} one), and that requirement is NOT
 * relaxed here. The fix is entirely on the prompt side: {@link #buildPrompt} now states explicitly that the
 * two fields are never redundant with each other, gives a worked example showing both present and genuinely
 * different for the same element, and repeats "required on every element, no exception" at the point the
 * field is first named — never trusting a single mention buried among several other field descriptions.
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
                + "For every ambiguity you detect, you MUST ALSO classify its MATERIALITY, using exactly "
                + "this predicate — it decides whether a human is actually consulted, so apply it "
                + "precisely rather than by impression:\n\n"
                + "An ambiguity is MATERIAL_PENDING if and only if resolving it would change a behaviour "
                + "the system MUST exhibit: a genuine fork, with BUILDABLE, OBSERVABLE consequences, where "
                + "two conformant implementations would actually behave differently in a way some approved "
                + "obligation (any functional or non-functional requirement, a gate condition, a stated "
                + "scope boundary, the security posture, or a binding validation target) cares about — AND "
                + "that fork is not already fixed by an existing approved artifact (a schema, a domain "
                + "invariant, an existing policy, or an already-defined contract).\n\n"
                + "An ambiguity is NOT_MATERIAL when it is ONLY a LINGUISTIC IMPERFECTION with no "
                + "behavioural fork behind it — for example: an undefined term whose ordinary, "
                + "plain-language meaning already suffices to implement correctly; a self-referential or "
                + "purely summarizing clause whose deletion would change no required behaviour; an "
                + "unbounded or unquantified phrasing that no stated obligation actually constrains; or a "
                + "dimension already settled by a reasonable, uncontested default (such as a framework's "
                + "own standard behaviour for an unaddressed case). Record every such item, with a "
                + "substantive noClarificationReason explaining specifically why no behavioural fork "
                + "exists — never drop or omit it — but do not open a gate on it. Non-materiality must be "
                + "demonstrated, never assumed: WHEN CLASSIFICATION IS UNCERTAIN, THE ITEM MUST BE TREATED "
                + "AS MATERIAL_PENDING. 'Uncertain' means genuinely unsure which required behaviour "
                + "applies once the requirement is implemented — it does NOT mean the wording could be "
                + "phrased more precisely, more formally, or more rigorously. Precision of wording alone, "
                + "with no behavioural fork behind it, is NEVER material. This default is not optional — "
                + "the burden of proof runs toward materiality, not away from it, for every genuine "
                + "behavioural fork.\n\n"
                + "Respond with ONLY a JSON array, no prose. If you find nothing, the array MUST still "
                + "contain exactly one element with resolutionState \"NOT_MATERIAL\" naming, "
                + "substantively, what checks you actually performed — never an empty array standing in "
                + "for \"all clear\". Each element: {\"ambiguityClass\": one of MISSING_ACCEPTANCE_CRITERIA"
                + "|UNDEFINED_TERM|UNBOUNDED_QUANTIFIER|MISSING_ACTOR|SELF_REFERENTIAL_CONSTRAINT|"
                + "CONTRADICTORY_BOUNDS|SEMANTIC_CONTRADICTION, \"affectedPath\": a SUBSTANTIVE description "
                + "(at least one full sentence, not a bare field reference like \"R1.statement\") of "
                + "exactly which requirement(s) or clause(s) are involved and why, \"resolutionState\": "
                + "\"MATERIAL_PENDING\" or \"NOT_MATERIAL\" per the predicate above, "
                + "\"qualityChecksPerformed\": substantive string — REQUIRED ON EVERY ELEMENT, regardless "
                + "of resolutionState, with NO exception, \"noClarificationReason\": substantive string "
                + "stating SPECIFICALLY which existing approved artifact already fixes the answer, or why "
                + "every conformant choice satisfies every stated obligation equally — REQUIRED when "
                + "resolutionState is NOT_MATERIAL, omitted otherwise}.\n\n"
                + "CR-067: qualityChecksPerformed and noClarificationReason are TWO DIFFERENT fields with "
                + "two different jobs, and a NOT_MATERIAL element ALWAYS carries BOTH, never one standing "
                + "in for the other. qualityChecksPerformed answers \"what did I actually look at or "
                + "compare to reach this classification\" (e.g. which other requirements you cross-checked, "
                + "which existing artifact you consulted) — it is the proof that a check happened at all, "
                + "for EVERY element, MATERIAL_PENDING or NOT_MATERIAL alike. noClarificationReason answers "
                + "a narrower, separate question that only applies to NOT_MATERIAL: \"specifically why does "
                + "no behavioural fork exist.\" Do not merge them, and do not omit qualityChecksPerformed "
                + "because noClarificationReason already reads as substantive — they are never redundant "
                + "with each other, and an element missing qualityChecksPerformed is refused outright, no "
                + "matter how substantive its own noClarificationReason is. Worked example for a "
                + "NOT_MATERIAL element, both fields present and genuinely different: "
                + "{\"qualityChecksPerformed\": \"Checked all six normalized requirements for any other "
                + "clause governing this dimension; checked whether a framework-standard default already "
                + "settles it.\", \"noClarificationReason\": \"No stated obligation constrains this "
                + "dimension, and the framework's own standard behaviour for an unaddressed case is "
                + "uncontested, so every conformant choice satisfies every stated requirement equally.\"}."
                + "\n\nNormalized requirements:\n" + requirements;
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
        // A model may omit the key, include it as an explicit JSON null, or include it as an empty/blank
        // string to mean "no reason given" -- all three are the same fact and must be treated identically,
        // matching AmbiguityRecord's own constructor (hasReason = non-null && !isBlank()). This adapter's
        // parsing must not be stricter than the record it builds. The null case was found live via the
        // CR-049 compensating check; the blank-string case via the CR-052 compensating check re-run --
        // neither hypothesized.
        JsonNode reasonNode = element.get("noClarificationReason");
        String noClarificationReason = (reasonNode != null && !reasonNode.isNull()
                && !reasonNode.asText().isBlank())
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
