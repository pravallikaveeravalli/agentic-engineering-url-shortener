package agentic.shortener.orchestration.reliability;

import java.io.IOException;
import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * The one place a provider's own error shape becomes a standard category. Task T083. FR-ORC-014.
 *
 * <p><strong>This file is the vendor taxonomy, and it is the only file allowed to be.</strong>
 * {@code FailureEnvelopeTest} asserts that no other class in this package names a provider exception type,
 * and — because an exemption by filename is worth nothing if the exempted file holds nothing — also asserts
 * that this one really does. T083's guard is that the core never learns a vendor taxonomy; the reason the
 * repaired static-list option was rejected is that such knowledge leaks upward one exception name at a
 * time, and every single step looks harmless.
 *
 * <h2>Causes are walked, and the walk cannot loop</h2>
 *
 * <p>Real provider failures arrive wrapped: a pool wraps {@link SQLException}, a subprocess helper wraps
 * {@link IOException}. Translating only the outermost type would classify almost everything
 * {@link FailureCategory#UNKNOWN} — default-deny working exactly as designed, and retry never happening at
 * all. So the chain is walked.
 *
 * <p>An exception whose cause chain loops is legal Java, and a {@code while (cause != null)} walk over one
 * does not return. That would be a hang in the code path that runs when something has already gone wrong,
 * so the walk carries an identity-based visited set. Same reasoning as {@code CycleDetector}'s: the shape
 * that cannot terminate is the one to make structurally impossible.
 */
public final class ProviderFailureTranslator {

    /**
     * Ordered, because a provider's exception hierarchy overlaps.
     *
     * <p>{@code SocketTimeoutException} is an {@code IOException}, so a most-specific-first list is the only
     * ordering that gives TIMEOUT rather than UNAVAILABLE. A map keyed by class would lose the ordering and
     * the answer would depend on iteration order — correct by accident until the JDK changed.
     */
    private record Rule(Class<? extends Throwable> type,
                        Function<Throwable, FailureCategory> classify) {
    }

    /** SQLSTATE classes, which is what a store actually speaks. */
    private static final Map<String, FailureCategory> SQL_STATES = Map.of(
            "08", FailureCategory.UNAVAILABLE,      // connection exception
            "57", FailureCategory.TIMEOUT,          // operator intervention, incl. query_canceled 57014
            "53", FailureCategory.UNAVAILABLE,      // insufficient resources
            "23", FailureCategory.INVALID_INPUT,    // integrity constraint violation
            "22", FailureCategory.INVALID_INPUT,    // data exception
            "40", FailureCategory.TIMEOUT,          // transaction rollback, incl. deadlock/serialization
            "42", FailureCategory.INTERNAL);        // syntax or access rule violation — our defect

    private static final List<Rule> STORE_RULES = List.of(
            new Rule(SQLException.class, failure -> fromSqlState((SQLException) failure)));

    private static final List<Rule> SUBPROCESS_RULES = List.of(
            new Rule(TimeoutException.class, failure -> FailureCategory.TIMEOUT),
            new Rule(java.net.SocketTimeoutException.class, failure -> FailureCategory.TIMEOUT),
            // Malformed output. T073 assertion 4: non-JSON output is INTERNAL and permanent, never retried.
            // Its own type, not IllegalStateException — see MalformedProviderOutputException for what the
            // generic version cost.
            new Rule(MalformedProviderOutputException.class, failure -> FailureCategory.INTERNAL),
            // A provider that recognized and reported its own rate limit or quota exhaustion. T131d, found
            // live: a response can be well-formed and still arrive alongside a 429/RESOURCE_EXHAUSTED
            // signal, which is not the same fact as a response the provider could not even parse.
            new Rule(ProviderRateLimitedException.class, failure -> FailureCategory.RATE_LIMITED),
            // A missing or unexecutable binary. UNAVAILABLE rather than INTERNAL: the provider is not
            // there, which is a deployment fact, and T073 requires it to route to bounded retry and then
            // suspension — there is no fallback (ADR-004-A2).
            new Rule(IOException.class, failure -> FailureCategory.UNAVAILABLE));

    private final List<Rule> rules;

    private ProviderFailureTranslator(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** For the Claude Code CLI transport and anything else invoked as a child process (T073). */
    public static ProviderFailureTranslator forSubprocessProvider() {
        return new ProviderFailureTranslator(SUBPROCESS_RULES);
    }

    /** For the PostgreSQL store. */
    public static ProviderFailureTranslator forStoreProvider() {
        return new ProviderFailureTranslator(STORE_RULES);
    }

    /**
     * Classifies a failure, walking its cause chain.
     *
     * <p>Never throws and never returns null: this runs on the failure path, and a translator that could
     * fail would turn a classified failure into an unclassified one at the worst moment.
     */
    public FailureEnvelope translate(Throwable failure) {
        Objects.requireNonNull(failure, "failure");

        Set<Throwable> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cursor = failure;
        while (cursor != null && seen.add(cursor)) {
            for (Rule rule : rules) {
                if (rule.type().isInstance(cursor)) {
                    FailureCategory category = rule.classify().apply(cursor);
                    return new FailureEnvelope(category, describe(failure, cursor),
                            proposesRetryable(category));
                }
            }
            cursor = cursor.getCause();
        }
        // Unrecognized. UNKNOWN, and never proposed retryable — FailureEnvelope would refuse it anyway,
        // which is the belt the braces are checked against.
        return new FailureEnvelope(FailureCategory.UNKNOWN, describe(failure, null), false);
    }

    /**
     * What the executor <em>proposes</em>. The orchestrator still has to agree (T084).
     *
     * <p>A veto, never a grant: this says only that retrying <em>could</em> help. Whether it is allowed
     * depends on the stage's declared retryable set, the attempts already consumed and whether the effect
     * is repeat-safe — none of which a translator knows.
     */
    private static boolean proposesRetryable(FailureCategory category) {
        return switch (category) {
            case TIMEOUT, UNAVAILABLE, RATE_LIMITED -> true;
            case INVALID_INPUT, INTERNAL, UNKNOWN -> false;
        };
    }

    private static FailureCategory fromSqlState(SQLException failure) {
        String state = failure.getSQLState();
        if (state == null || state.length() < 2) {
            return FailureCategory.UNKNOWN;
        }
        return SQL_STATES.getOrDefault(state.substring(0, 2), FailureCategory.UNKNOWN);
    }

    /**
     * The detail, naming the outer failure and the cause that decided the category.
     *
     * <p>Both, because the outer message is what a reader recognises and the inner one is what the ruling
     * turned on — a detail carrying only one of them sends the reader back to the logs.
     */
    private static String describe(Throwable outer, Throwable classified) {
        String head = outer.getClass().getSimpleName() + ": " + messageOf(outer);
        if (classified == null || classified == outer) {
            return head;
        }
        return head + " (classified from " + classified.getClass().getSimpleName() + ": "
                + messageOf(classified) + ")";
    }

    /** A message is optional on a {@code Throwable}, and a blank detail is refused by the envelope. */
    private static String messageOf(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "no message" : message;
    }
}
