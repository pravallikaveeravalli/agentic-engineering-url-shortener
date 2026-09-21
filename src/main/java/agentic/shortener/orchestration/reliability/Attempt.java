package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.StageOutcome;

/**
 * One execution attempt, recorded on its own. Task T085. FR-ORC-014, KE-15.
 *
 * <p>PVT-007 requires each attempt individually recorded, not a final outcome with a count beside it. The
 * difference shows up during an incident: three attempts that failed for three different reasons and three
 * that failed identically need different responses, and a count cannot tell them apart.
 *
 * @param number  1-based, matching the {@code attempt} the executor was handed
 * @param ruling  the retry decision made <em>after</em> this attempt, or {@code null} when there was nothing to
 *                rule on because the attempt succeeded. Recording a ruling for a successful attempt would put
 *                a retry decision in evidence that nobody made
 */
public record Attempt(int number, StageOutcome outcome, Ruling ruling) {

    public Attempt {
        if (number < 1) {
            throw new IllegalArgumentException("attempts are 1-based, got " + number);
        }
        if (outcome != null && outcome.succeeded() && ruling != null) {
            throw new IllegalArgumentException(
                    "a successful attempt was never ruled on; a ruling here would be a retry decision "
                            + "nobody made");
        }
        // outcome is deliberately nullable: an executor that threw or returned nothing produced no outcome,
        // and the attempt still happened and still has to appear in the record.
    }
}
