package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.StageEffectContracts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T086 — timeout gating on idempotency, and the PVT-016 threshold schedule.
 * FR-ORC-014 rule 4, FR-ORC-018.
 *
 * <p>Two separate decisions, kept separate on purpose: <strong>how long</strong> a node is expected to take
 * (the threshold) and <strong>whether a timeout may be retried</strong> (eligibility). T086a decides what a
 * breach <em>does</em>; nothing here kills anything.
 *
 * <p>The thresholds are read out of <strong>plan §3's own table</strong> for the same reason T070's declared
 * sets are: a schedule and a test written from one reading of the plan agree with each other whatever the
 * plan says.
 */
@DisplayName("T086 — thresholds from PVT-016, eligibility from the design-time contract")
class TimeoutPolicyTest {

    private static final Path PLAN = Path.of("specs/001-agentic-sdlc-url-shortener/plan.md");

    @Test
    @DisplayName("every configured threshold matches PVT-016 in plan §3, exactly")
    void thresholdsMatchThePlan() throws Exception {
        Map<Integer, Duration> fromPlan = thresholdsFromPlan();

        // S4 has no execution threshold at all (PVT-016: N/A) — it is already waiting on a human, and its
        // "timeout" is the gate wait, which produces SAFE_STOP rather than an overrun. So the plan has
        // eleven figures, not twelve.
        assertEquals(11, fromPlan.size(), "eleven stages carry an execution threshold: " + fromPlan.keySet());
        assertFalse(fromPlan.containsKey(4), "S4's threshold is the gate wait, not an execution threshold");

        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<Integer, Duration> row : fromPlan.entrySet()) {
            Duration configured = TimeoutPolicy.thresholdFor(row.getKey());
            if (!configured.equals(row.getValue())) {
                mismatches.add("S" + row.getKey() + ": plan says " + row.getValue()
                        + ", configuration holds " + configured);
            }
        }
        assertEquals(List.of(), mismatches,
                "a drifted threshold must fail a test rather than change behaviour silently: " + mismatches);
    }

    @Test
    @DisplayName("the threshold reader is falsifiable — it parses cells rather than answering from memory")
    void thresholdReaderIsFalsifiable() {
        String table = """
                | # | Node | Purpose | Inputs → Outputs | Pre / Post | Actor | Timeout & retry | Declared retryable set | Failure class | Fallback |
                |---|---|---|---|---|---|---|---|---|---|
                | S1 | Ingestion | x | y | z | Deterministic | **PVT-016: 7 s**; `INTERNAL` permanent | `{UNAVAILABLE}` | p | None |
                | S4 | Human | x | y | z | **Human** | **No execution threshold** (PVT-016: N/A) | `{}` | N/A | None |
                """;
        Map<Integer, Duration> parsed = parseThresholds(table);

        assertEquals(Duration.ofSeconds(7), parsed.get(1), "it must read the cell it was given");
        assertFalse(parsed.containsKey(4), "N/A is not a duration and must not be invented as one");
    }

    @Test
    @DisplayName("S4 has no execution threshold, and asking for one is refused rather than defaulted")
    void stageFourHasNoExecutionThreshold() {
        // Returning a default here would give the human-clarification stage an execution deadline it does
        // not have, and T086a would then raise a node-overrun gate against a node that is correctly waiting
        // for a person.
        assertThrows(IllegalArgumentException.class, () -> TimeoutPolicy.thresholdFor(4));
        assertFalse(TimeoutPolicy.hasExecutionThreshold(4));
        assertTrue(TimeoutPolicy.hasExecutionThreshold(7));
    }

    @Test
    @DisplayName("EC-033: a timeout on S7 is NOT retried — the git effect is non-idempotent")
    void timeoutOnSevenIsNotRetried() {
        assertFalse(TimeoutPolicy.timeoutIsRetryable(7),
                "a timeout means completion is UNKNOWN. Replaying a maybe-completed commit violates "
                        + "exactly-once, which is why S7's declared set excludes TIMEOUT (EC-033)");

        Ruling ruling = new RetryRuling().rule(7,
                new FailureEnvelope(FailureCategory.TIMEOUT, "apply timed out", true));
        assertFalse(ruling.retry(), "and the ruling must agree; two answers here would be worse than one");
    }

    @Test
    @DisplayName("a timeout on an idempotent stage IS retried")
    void timeoutOnAnIdempotentStageIsRetried() {
        assertTrue(TimeoutPolicy.timeoutIsRetryable(2));
        assertTrue(new RetryRuling().rule(2,
                new FailureEnvelope(FailureCategory.TIMEOUT, "provider timed out", true)).retry());
    }

    @Test
    @DisplayName("eligibility is read from the design-time contract, never self-certified")
    void eligibilityComesFromTheContract() {
        // Asserted as an equivalence across all twelve rather than on two examples: a TimeoutPolicy with its
        // own opinion about idempotence would be a second source of truth, and the two would drift in the
        // direction of whichever was easier to change.
        List<String> disagreements = new ArrayList<>();
        for (int stage = 1; stage <= 12; stage++) {
            boolean fromContract = StageEffectContracts.forStage(stage).retryableCategories()
                    .contains(FailureCategory.TIMEOUT);
            if (TimeoutPolicy.timeoutIsRetryable(stage) != fromContract) {
                disagreements.add("S" + stage);
            }
        }
        assertEquals(List.of(), disagreements,
                "TimeoutPolicy must not hold an opinion of its own: " + disagreements);
    }

    @Test
    @DisplayName("nothing here decides what a breach DOES")
    void thresholdsDecideNothingByThemselves() throws Exception {
        // T086a owns the consequence, and the owner's ruling is that the orchestrator may never kill a node
        // on its own authority. A method here that failed or killed a node would put that decision back
        // inside the governed system, so its absence is asserted rather than assumed.
        String source = Files.readString(
                Path.of("src/main/java/agentic/shortener/orchestration/reliability/TimeoutPolicy.java"));
        List<String> forbidden = List.of("kill", "abort", "cancel", "terminate");
        List<String> found = forbidden.stream()
                .filter(word -> source.toLowerCase().replaceAll("(?s)/\\*.*?\\*/", "")
                        .replaceAll("(?m)//.*$", "").contains(word))
                .toList();
        assertEquals(List.of(), found,
                "a slow node is not a failed node: only a human decides wait or stop (T086a). " + found);
    }

    // ==============================================================================================

    private static Map<Integer, Duration> thresholdsFromPlan() throws Exception {
        return parseThresholds(Files.readString(PLAN));
    }

    private static final Pattern NODE_ROW = Pattern.compile("^\\|\\s*S(\\d{1,2})\\s*\\|");
    private static final Pattern PVT_016 = Pattern.compile("PVT-016:\\s*(\\d+)\\s*s");

    /** Reads the {@code Timeout & retry} column, located by its header name rather than its position. */
    private static Map<Integer, Duration> parseThresholds(String markdown) {
        Map<Integer, Duration> thresholds = new LinkedHashMap<>();
        int column = -1;

        for (String line : markdown.lines().toList()) {
            if (column < 0 && line.contains("| Timeout & retry |")) {
                String[] headers = line.split("\\|", -1);
                for (int i = 0; i < headers.length; i++) {
                    if (headers[i].trim().equals("Timeout & retry")) {
                        column = i;
                    }
                }
                continue;
            }
            if (column < 0) {
                continue;
            }
            Matcher node = NODE_ROW.matcher(line);
            if (!node.find()) {
                if (!line.startsWith("|") && !thresholds.isEmpty()) {
                    break;
                }
                continue;
            }
            String[] cells = line.split("\\|", -1);
            if (column >= cells.length) {
                continue;
            }
            Matcher seconds = PVT_016.matcher(cells[column]);
            if (seconds.find()) {
                thresholds.put(Integer.parseInt(node.group(1)),
                        Duration.ofSeconds(Long.parseLong(seconds.group(1))));
            }
        }
        return thresholds;
    }
}
