package agentic.shortener.policy;

import java.util.List;
import java.util.Objects;

/**
 * All nine conditions, evaluated together. Task T109. FR-ORC-025.
 *
 * <p>{@code ready()} is never accepted from a caller, only derived — the same discipline {@link
 * PolicyEvaluationResult#blocking()} already applies to the twelve policy checks, here applied to the nine
 * constitutional conditions: a caller cannot construct a {@code ready=true} result while an unmet condition
 * is present.
 */
public record ReleaseReadinessResult(List<ReadinessCondition> conditions) {

    public ReleaseReadinessResult {
        Objects.requireNonNull(conditions, "conditions");
        if (conditions.size() != 9) {
            throw new IllegalArgumentException(
                    "all nine constitutional conditions must be evaluated together, got " + conditions.size());
        }
        List<Integer> numbers = conditions.stream().map(ReadinessCondition::number).sorted().toList();
        if (!numbers.equals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9))) {
            throw new IllegalArgumentException("conditions must be numbered 1..9 exactly once each, got "
                    + numbers);
        }
        conditions = List.copyOf(conditions);
    }

    public boolean ready() {
        return conditions.stream().allMatch(ReadinessCondition::met);
    }

    public List<ReadinessCondition> unmet() {
        return conditions.stream().filter(c -> !c.met()).toList();
    }

    /** Every unmet condition, named by number — never merely a count (FR-ORC-025's own "naming" clause). */
    public String summary() {
        List<ReadinessCondition> unmet = unmet();
        if (unmet.isEmpty()) {
            return "all nine constitutional conditions met — see FR-ORC-013: this is an evaluation, not "
                    + "the release owner's decision";
        }
        StringBuilder sb = new StringBuilder("NOT READY — ").append(unmet.size()).append(" condition(s) unmet: ");
        for (ReadinessCondition c : unmet) {
            sb.append("[condition ").append(c.number()).append(": ").append(c.detail()).append("] ");
        }
        return sb.toString().strip();
    }
}
