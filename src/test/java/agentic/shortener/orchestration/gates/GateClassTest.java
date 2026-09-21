package agentic.shortener.orchestration.gates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T064 — the ten gate classes, checked against plan §5's own table. CR-040.
 *
 * <p>The centrepiece, matching the pattern T070 and T086 already established for a plan-drift check:
 * the row count and each row's label are read out of {@code plan.md} itself, not re-typed here. A test
 * that listed the same ten labels as {@link GateClass} would agree with it whatever the plan said —
 * exactly the defect the artifact-coverage sweep exists to catch.
 *
 * <p>CR-040 is the record of how this went from eight to ten: {@code approval.schema.json}'s persisted
 * {@code gateClass} enum had not caught up to plan §5's own table, which already named
 * {@code NO_CHANGE_PLAN} and {@code NODE_OVERRUN}. This test is what keeps the two from drifting apart
 * again.
 */
@DisplayName("T064 — ten gate classes, matching plan §5 exactly")
class GateClassTest {

    private static final Path PLAN = Path.of("specs/001-agentic-sdlc-url-shortener/plan.md");

    @Test
    @DisplayName("exactly ten constants, none more, none fewer")
    void tenConstantsExactly() {
        assertEquals(10, GateClass.values().length,
                "plan §5's table has ten rows; a gate class present in the plan but absent from the "
                        + "enum is a silent skip");
    }

    @Test
    @DisplayName("every constant's label matches a row plan §5 actually has, and every row has a constant")
    void everyRowHasAConstantAndViceVersa() throws Exception {
        List<String> planLabels = gateClassLabelsFromPlan();
        assertEquals(10, planLabels.size(), "plan §5's own row count: " + planLabels);

        List<String> enumLabels = List.of(GateClass.values()).stream()
                .map(GateClass::planLabel).sorted().toList();
        List<String> sortedPlanLabels = planLabels.stream().sorted().toList();

        assertEquals(sortedPlanLabels, enumLabels,
                "the enum and the plan's own table must name exactly the same classes");
    }

    @Test
    @DisplayName("the plan-table reader is falsifiable — it parses rows, not a hardcoded answer")
    void planTableReaderIsFalsifiable() {
        String table = """
                | Gate class | Trigger | Blocks | Outcomes |
                |---|---|---|---|
                | Unresolved ambiguity | x | y | five standard |
                | **Node overrun** | x | y | five standard, plus two named choices |
                """;
        assertEquals(List.of("Node overrun", "Unresolved ambiguity"),
                parseGateClassLabels(table).stream().sorted().toList(),
                "the parser must read the cells it was given, bold markers stripped");
    }

    @Test
    @DisplayName("NO_CHANGE_PLAN and NODE_OVERRUN each carry their named choices beyond the five standard")
    void theTwoNewClassesCarryTheirNamedChoices() {
        assertEquals(List.of("GOVERNANCE_ONLY", "HUMAN_IMPLEMENTED", "ABANDON"),
                GateClass.NO_CHANGE_PLAN.namedChoices(),
                "plan §5: five standard, plus three named choices (CR-001)");
        assertEquals(List.of("KEEP_WAITING", "KILL_THE_NODE"),
                GateClass.NODE_OVERRUN.namedChoices(),
                "plan §5: five standard, plus two named choices");

        List<String> withoutExtraChoices = new ArrayList<>();
        for (GateClass gateClass : EnumSet.complementOf(EnumSet.of(GateClass.NO_CHANGE_PLAN,
                GateClass.NODE_OVERRUN))) {
            if (!gateClass.namedChoices().isEmpty()) {
                withoutExtraChoices.add(gateClass.name());
            }
        }
        assertEquals(List.of(), withoutExtraChoices,
                "every OTHER class is five standard outcomes only, per plan §5: " + withoutExtraChoices);
    }

    @Test
    @DisplayName("every class names what it blocks, and no scope is blank")
    void everyClassNamesItsBlockedScope() {
        List<String> blank = new ArrayList<>();
        for (GateClass gateClass : GateClass.values()) {
            if (gateClass.blockedScope() == null || gateClass.blockedScope().isBlank()) {
                blank.add(gateClass.name());
            }
        }
        assertEquals(List.of(), blank, "T064's Done clause: each names its blocked scope: " + blank);
    }

    @Test
    @DisplayName("NODE_OVERRUN blocks only that node — siblings and unaffected paths continue")
    void nodeOverrunBlocksOnlyItsOwnNode() {
        assertTrue(GateClass.NODE_OVERRUN.blockedScope().toLowerCase().contains("that node only"),
                "the whole point of this gate class is that a slow node does not stall the run: "
                        + GateClass.NODE_OVERRUN.blockedScope());
    }

    @Test
    @DisplayName("gateClass round-trips through approval.schema.json's ten-value enum")
    void everyClassIsInTheContractEnum() throws Exception {
        List<String> contractEnum = contractGateClassEnum();
        List<String> missing = new ArrayList<>();
        for (GateClass gateClass : GateClass.values()) {
            if (!contractEnum.contains(gateClass.name())) {
                missing.add(gateClass.name());
            }
        }
        assertEquals(List.of(), missing,
                "CR-040: approval.schema.json's gateClass enum must name every class this registry does: "
                        + missing);
        assertEquals(10, contractEnum.size());
    }

    // ==============================================================================================

    private static List<String> gateClassLabelsFromPlan() throws Exception {
        return parseGateClassLabels(Files.readString(PLAN));
    }

    private static final Pattern ROW = Pattern.compile("^\\|\\s*(.+?)\\s*\\|");

    /** Reads the {@code Gate class} column's own rows out of plan §5's table, bold markers stripped. */
    private static List<String> parseGateClassLabels(String markdown) {
        boolean inTable = false;
        List<String> labels = new ArrayList<>();
        for (String line : markdown.lines().toList()) {
            if (!inTable) {
                if (line.contains("| Gate class | Trigger | Blocks | Outcomes |")) {
                    inTable = true;
                }
                continue;
            }
            if (line.startsWith("|---")) {
                continue;
            }
            Matcher row = ROW.matcher(line);
            if (!row.find()) {
                if (!labels.isEmpty()) {
                    break; // the table ended
                }
                continue;
            }
            String label = row.group(1).replace("**", "").trim();
            labels.add(label);
        }
        return labels;
    }

    /** The screaming-snake-case name a plan-table label maps to. Used only by this test, not production. */
    private static List<String> contractGateClassEnum() throws Exception {
        Path contract = Path.of("specs/001-agentic-sdlc-url-shortener/contracts/approval.schema.json");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = mapper.readTree(Files.readString(contract));
        var enumNode = node.path("properties").path("gateClass").path("enum");
        List<String> values = new ArrayList<>();
        enumNode.forEach(n -> values.add(n.asText()));
        return values;
    }
}
