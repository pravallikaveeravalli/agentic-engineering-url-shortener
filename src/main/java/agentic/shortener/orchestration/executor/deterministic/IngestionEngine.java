package agentic.shortener.orchestration.executor.deterministic;

import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.FailureEnvelope;

import java.util.List;

/**
 * S1 — requirement ingestion. Task T071. FR-ORC-029.
 *
 * <p>Deterministic because admitting a requirement should mean the same thing every time. The only work is
 * normalization, and normalization here is <strong>presentational only</strong>: line endings unified,
 * surrounding whitespace trimmed, trailing blank lines dropped. Nothing is discarded, merged or reinterpreted
 * — a requirement that came in as two sentences leaves as the same two sentences.
 *
 * <p>That restraint is the point rather than laziness. Semantic normalization is S2's work and it is
 * AI-capable for a reason; an intake stage that "tidied" a requirement would be doing S2's job badly and
 * invisibly, and the reviewer would have no way to tell what the submitter actually wrote.
 */
public final class IngestionEngine implements StageExecutor {

    static final String INPUT_KEY = "submission";
    static final String OUTPUT_KEY = "requirement.intake";

    @Override
    public StageOutcome execute(StageInput input) {
        String submission = input.inputArtifacts().get(INPUT_KEY);

        if (submission == null || submission.isBlank()) {
            // Plan §3, S1: permanent on malformed input. INVALID_INPUT rather than INTERNAL, because
            // nothing on our side is broken — and resubmitting the same blank text cannot change the answer,
            // which is what INVALID_INPUT means.
            return StageOutcome.failed(new FailureEnvelope(FailureCategory.INVALID_INPUT,
                    submission == null
                            ? "no '" + INPUT_KEY + "' artifact to admit"
                            : "the submitted requirement is blank",
                    false));
        }

        return StageOutcome.succeeded(
                List.of(new ProducedArtifact(OUTPUT_KEY, normalize(submission), List.of(INPUT_KEY))),
                ExecutorKind.DETERMINISTIC);
    }

    /** Presentational only, and idempotent: normalizing twice gives what normalizing once gave. */
    private static String normalize(String submission) {
        return submission.replace("\r\n", "\n").replace('\r', '\n')
                .lines()
                .map(String::strip)
                .toList()
                .stream()
                .reduce((a, b) -> a + "\n" + b)
                .orElse("")
                .strip();
    }
}
