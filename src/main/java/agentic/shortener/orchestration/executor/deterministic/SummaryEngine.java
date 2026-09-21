package agentic.shortener.orchestration.executor.deterministic;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;

import java.util.List;
import java.util.StringJoiner;

/**
 * S12 — final engineering summary. Task T071. FR-ORC-029, Constitution X.
 *
 * <p>An assembler, deliberately. Plan §3: <em>every claim must be traceable, so creativity is a liability
 * here.</em> This is the one stage where producing something a reader has not seen elsewhere would be a
 * defect, and it is the reason S12 is deterministic rather than AI-capable.
 *
 * <h2>Traceability is structural, not a rule the assembler follows</h2>
 *
 * <p>Every emitted line is {@code <artifact key> | <a line of that artifact>}. There is no code path that can
 * produce a sentence not drawn from an input, because there is no code path that produces a sentence at all —
 * only headings and citations. A summary that composed prose would need a separate check that each claim was
 * supported, and that check would have to be as good at reading as whatever wrote the prose.
 *
 * <h2>Missing evidence is permanent, and the gap is not written around</h2>
 *
 * <p>The tempting behaviour is to summarize what is present and stay quiet about what is not — which produces
 * a confident-looking summary of a run that never finished. So a missing required artifact fails the stage,
 * names what is missing, and proposes no retry: nothing about running the assembler again produces evidence
 * that was never recorded.
 */
public final class SummaryEngine implements StageExecutor {

    static final String OUTPUT_KEY = "engineering-summary";

    /**
     * What a summary is not allowed to be silent about.
     *
     * <p>The four that make a run's story complete: what was asked, what was tested, what policy said, and
     * whether it was ready. Anything else present is cited too — this is a floor, not a whitelist.
     */
    static final List<String> REQUIRED_EVIDENCE =
            List.of("requirement.intake", "test-results", "policy-results", "release-readiness");

    @Override
    public StageOutcome execute(StageInput input) {
        List<String> missing = REQUIRED_EVIDENCE.stream()
                .filter(key -> {
                    String value = input.inputArtifacts().get(key);
                    return value == null || value.isBlank();
                })
                .toList();

        if (!missing.isEmpty()) {
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    "cannot assemble a summary: missing evidence " + missing
                            + ". Summarizing what is present and staying quiet about what is not would "
                            + "produce a confident summary of a run that never finished",
                    false));
        }

        // Sorted, so the output does not depend on a Map's iteration order. Unsorted, the line order would
        // vary with hashing — stable within one JVM and not across them, which is the worst kind of
        // non-determinism: every test passes and two demonstration runs disagree.
        List<String> keys = input.inputArtifacts().keySet().stream().sorted().toList();

        StringJoiner summary = new StringJoiner("\n");
        summary.add("# Engineering summary for run " + input.runId());
        summary.add("");
        summary.add("# Every line below cites the artifact it came from. Nothing here is composed.");
        summary.add("");
        for (String key : keys) {
            for (String line : input.inputArtifacts().get(key).lines().toList()) {
                summary.add(key + " | " + line);
            }
        }

        return StageOutcome.succeeded(
                List.of(new ProducedArtifact(OUTPUT_KEY, summary.toString(), keys)),
                ExecutorKind.DETERMINISTIC);
    }
}
