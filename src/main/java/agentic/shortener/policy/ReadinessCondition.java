package agentic.shortener.policy;

import java.util.Objects;

/**
 * One of the nine constitutional release-blocking conditions, evaluated. Task T109. Constitution
 * §Governance, FR-ORC-025.
 *
 * @param number  1..9, matching the constitution's own numbered list exactly — a blocking report names
 *                which condition failed by this number, not by a description a reader has to match by hand
 * @param met     {@code true} means this condition does NOT block — the constitution phrases all nine as
 *                things that MUST NOT hold, so "met" here means "does not hold," not "was satisfied" in the
 *                colloquial sense
 * @param detail  what was actually checked and what was found — a condition reported unmet with no detail
 *                is exactly the kind of unverifiable evidence condition 9 itself forbids
 */
public record ReadinessCondition(int number, String description, boolean met, String detail) {

    public ReadinessCondition {
        if (number < 1 || number > 9) {
            throw new IllegalArgumentException("number must be 1..9, got " + number);
        }
        Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        Objects.requireNonNull(detail, "detail");
        if (detail.isBlank()) {
            throw new IllegalArgumentException(
                    "detail must not be blank — an unexplained condition verdict is unverifiable, which "
                            + "is what condition 9 itself forbids");
        }
    }
}
