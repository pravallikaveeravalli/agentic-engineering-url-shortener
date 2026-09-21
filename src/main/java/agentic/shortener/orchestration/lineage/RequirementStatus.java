package agentic.shortener.orchestration.lineage;

/**
 * A requirement's own lifecycle. Task T082.
 *
 * <p>One value, deliberately. T082's scope is S2's output — a requirement is admitted and normalized, and
 * that is the only status this task has any evidence for. Later slices that track a requirement through
 * decomposition or delivery add further values there, backed by their own tests, rather than this task
 * inventing a lifecycle nothing yet drives.
 */
public enum RequirementStatus {
    NORMALIZED
}
