package agentic.shortener.orchestration.conductor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-061. Proves, directly and without standing up a live run, that {@link DsALiveRun}'s own two real,
 * live-found substring-matching collisions are fixed by whole-word matching, and that legitimate matches
 * this delegation's own standing answers exist to catch still work.
 *
 * <p>Attempt 19 (docs/evidence/ds-a/run-snapshot-ATTEMPT-19-...): a real {@code SEMANTIC_CONTRADICTION}
 * about "release version" vs. {@code pom.xml}'s real {@code SNAPSHOT} was misrouted to
 * {@code VERSION_VALUE_CLARIFICATION} because its own text contained the word "literal" — fixed at the
 * class level in {@code applyOwnerClarificationAndResume} (a {@code SEMANTIC_CONTRADICTION} is never
 * matched at all), not by this word-boundary fix; not re-proven here.
 *
 * <p>Attempt 20 (docs/evidence/ds-a/run-snapshot-ATTEMPT-20-...): a real {@code UNDEFINED_TERM} about what
 * "honestly-disclosed" means was misrouted the same way, because its own text contained the word "resource",
 * and {@code String.contains("source")} matches inside "re<strong>source</strong>". THIS is what
 * word-boundary matching fixes, proven below.
 */
@DisplayName("CR-061 — DsALiveRun's own clarification matcher: whole-word, not substring")
class DsALiveRunMatcherTest {

    @Test
    @DisplayName("attempt 20's real collision is fixed: 'resource' no longer matches the word 'source'")
    void resourceDoesNotMatchSourceAsAWord() {
        String real20Text = ("Requirement 1f permits the new classpath resource file to contain 'a static, "
                + "honestly-disclosed placeholder version value,' but 'honestly-disclosed' is not defined "
                + "anywhere in the requirement set").toLowerCase();

        assertFalse(DsALiveRun.containsWord(real20Text, "source"),
                "the real attempt-20 finding text contains 'resource', not the standalone word 'source' -- "
                        + "word-boundary matching must not treat the former as the latter");
    }

    @Test
    @DisplayName("REGRESSION: a real finding genuinely asking about the version's SOURCE still matches, as "
            + "a whole word")
    void genuineSourceQuestionStillMatchesAsAWholeWord() {
        String genuineText = "what is the exact source of the version field's value".toLowerCase();

        assertTrue(DsALiveRun.containsWord(genuineText, "version"));
        assertTrue(DsALiveRun.containsWord(genuineText, "source"),
                "a real finding that genuinely asks about the version's source, as its own standalone "
                        + "word, must still match");
    }

    @Test
    @DisplayName("REGRESSION: 'sourced from build metadata' phrasing still matches via the word-form list")
    void sourcedVerbFormStillMatches() {
        String text = "the version value should be sourced from build metadata".toLowerCase();

        assertTrue(DsALiveRun.containsWord(text, "version"));
        assertTrue(DsALiveRun.containsAnyWord(text, "source", "sourced", "sourcing", "build", "built",
                "hardcoded", "hardcoding", "hardcode", "computed", "manifest"),
                "the verb form 'sourced' must still be recognized, not only the noun 'source'");
    }

    @Test
    @DisplayName("'auth' as a real standalone word still matches, and so do its real inflections")
    void authWordFormsStillMatch() {
        assertTrue(DsALiveRun.containsAnyWord("no authentication required".toLowerCase(), "auth",
                "authentication", "authorization", "authenticated", "authorized", "unauthenticated",
                "unauthorized"));
        assertTrue(DsALiveRun.containsAnyWord("must the caller be authorized".toLowerCase(), "auth",
                "authentication", "authorization", "authenticated", "authorized", "unauthenticated",
                "unauthorized"));
    }

    @Test
    @DisplayName("'author' does NOT match 'auth' as a word -- the exact class of collision this fix exists "
            + "to prevent")
    void authorDoesNotMatchAuthAsAWord() {
        assertFalse(DsALiveRun.containsWord("the author of this document".toLowerCase(), "auth"));
    }

    @Test
    @DisplayName("a plain, unrelated finding still falls through to no match at all")
    void unrelatedFindingMatchesNothing() {
        String text = "whether the response should include a trace identifier header".toLowerCase();

        assertFalse(DsALiveRun.containsWord(text, "version"));
        assertFalse(DsALiveRun.containsAnyWord(text, "logging", "metrics", "tracing", "observability"),
                "'trace identifier' must not match 'tracing' -- different word, no boundary collision");
    }

    @Test
    @DisplayName("CR-063: the real clean-requirement sanity check's own output found a genuine regression -- "
            + "the plural 'parameters' must still match, not only the singular 'parameter'")
    void pluralParametersStillMatches() {
        // Verbatim from the real, live sanity-check output (docs/evidence/ds-a's own sanity-check record).
        String realText = ("Requirement 1a states the endpoint 'accepts no path parameters and no query "
                + "parameters' but does not state the required behavior when a client nonetheless sends "
                + "query parameters").toLowerCase();

        assertFalse(DsALiveRun.containsWord(realText, "parameter"),
                "the singular word 'parameter' alone (CR-061's own fix) does NOT match this real text, "
                        + "which only uses the plural -- this was the real, live regression CR-063 found");
        assertTrue(DsALiveRun.containsAnyWord(realText, "parameter", "parameters", "credential",
                        "credentials"),
                "the plural form must be matched too, or a real routine finding falls through to "
                        + "'unanswered' unnecessarily");
    }

    @Test
    @DisplayName("CR-063: a credential-related finding matches even when the word 'access' is not itself "
            + "present in its own text")
    void credentialFindingMatchesWithoutTheWordAccess() {
        // Verbatim from the real, live sanity-check output.
        String realText = ("Requirement 1b specifies only that 'requests without credentials SHALL NOT be "
                + "rejected on authentication grounds' -- it does not state the required behavior when a "
                + "request DOES include credentials and those credentials are invalid or malformed").toLowerCase();

        assertFalse(DsALiveRun.containsWord(realText, "access"),
                "this real finding's own text does not use the word 'access' at all");
        assertTrue(DsALiveRun.containsAnyWord(realText, "parameter", "parameters", "credential",
                        "credentials"),
                "'credential'/'credentials' must be its own standalone trigger -- ACCESS_CLARIFICATION's "
                        + "own substance ('no credential required, no error-input handling beyond the "
                        + "framework default') already answers this without needing 'access' to co-occur");
    }
}
