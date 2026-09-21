package agentic.shortener.orchestration.lineage;

/**
 * What kind of ambiguity was found. Task T082. FR-ORC-010.
 *
 * <p>The first six are spec.md's own structural alternative — the classes a deterministic checker could
 * decide, named in "What ambiguity detection is, and what it is not": missing acceptance criteria,
 * undefined term, unbounded quantifier, missing actor, self-referential constraint, and contradictory
 * bounds on the same named field.
 *
 * <p>{@link #SEMANTIC_CONTRADICTION} is the seventh, and it is the reason S3 is AI-capable rather than a
 * deterministic checker: DS-A's own demonstration input — expire after a week, retain analytics
 * indefinitely, still redirect for trusted partners — is a contradiction between different concepts, and
 * "no two clauses bound the same field, so no structural rule would fire on it". The six structural classes
 * could not decide it; this class exists because S3 must be able to record that it did.
 */
public enum AmbiguityClass {
    MISSING_ACCEPTANCE_CRITERIA,
    UNDEFINED_TERM,
    UNBOUNDED_QUANTIFIER,
    MISSING_ACTOR,
    SELF_REFERENTIAL_CONSTRAINT,
    CONTRADICTORY_BOUNDS,
    SEMANTIC_CONTRADICTION
}
