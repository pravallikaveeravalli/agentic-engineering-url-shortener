package agentic.shortener.orchestration.gates;

import java.util.Objects;
import java.util.Set;

/**
 * Who decided, or who is deciding. Task T058. FR-ORC-021, CN-012.
 *
 * <p>{@code actorType} is {@code human} or {@code system} — never {@code agent}, and this record refuses
 * one outright: FR-ORC-021 is that an agent may never decide a gate at any autonomy setting, so there is
 * no value here for it to hold. {@code system} is legitimate for the actor field itself; whether a
 * particular {@code system} decision is allowed depends on the outcome it accompanies, which
 * {@link GateDecision} enforces.
 */
public record Actor(String actorType, String identity) {

    private static final Set<String> VALID_TYPES = Set.of("human", "system");

    public Actor {
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(identity, "identity");
        if (!VALID_TYPES.contains(actorType)) {
            throw new IllegalArgumentException(
                    "actorType must be 'human' or 'system', got '" + actorType + "'. FR-ORC-021: an "
                            + "agent may never decide a gate at any autonomy setting, so there is no "
                            + "third value for this field to hold");
        }
        if (identity.isBlank()) {
            throw new IllegalArgumentException("identity must not be blank");
        }
    }
}
