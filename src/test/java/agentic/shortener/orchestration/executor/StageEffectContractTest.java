package agentic.shortener.orchestration.executor;

import agentic.shortener.orchestration.reliability.FailureCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T070 — per-stage effect contracts. FR-ORC-014 rule 4, FR-ORC-016 rule 3.
 *
 * <p>The centrepiece is {@link #declaredRetryableSetsMatchThePlanTable()}: the twelve declared sets are read
 * out of <strong>plan §3's own table</strong> and compared with what the code loads. A contract declaration
 * and a test I write from the same reading of the plan would agree with each other whatever the plan says —
 * which is the defect the artifact-coverage sweep exists to catch. Reading the plan itself is the only
 * version of this assertion that can fail for the right reason.
 */
@DisplayName("T070 — stage effect contracts, checked against the plan")
class StageEffectContractTest {

    private static final Path PLAN = Path.of("specs/001-agentic-sdlc-url-shortener/plan.md");

    /** Plan §6: the declared operand is drawn from these three; the other three retry nowhere. */
    private static final Set<FailureCategory> EVER_RETRYABLE = EnumSet.of(
            FailureCategory.TIMEOUT, FailureCategory.UNAVAILABLE, FailureCategory.RATE_LIMITED);

    @Test
    @DisplayName("twelve contracts, one per stage, and no thirteenth")
    void twelveContracts() {
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
                StageEffectContracts.all().stream().map(StageEffectContract::stageNumber).sorted().toList());

        assertThrows(IllegalArgumentException.class, () -> StageEffectContracts.forStage(13),
                "a stage with no contract has no declared retryable set, and an absent declared operand "
                        + "makes the intersection empty — retry would be nominally present and actually dead");
    }

    @Test
    @DisplayName("every declared retryable set matches plan §3's own column, exactly")
    void declaredRetryableSetsMatchThePlanTable() throws Exception {
        Map<Integer, Set<FailureCategory>> fromPlan = declaredSetsFromPlan();

        assertEquals(12, fromPlan.size(),
                "the plan's §3 table must still have twelve node rows; got " + fromPlan.keySet());

        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<Integer, Set<FailureCategory>> row : fromPlan.entrySet()) {
            Set<FailureCategory> loaded = StageEffectContracts.forStage(row.getKey()).retryableCategories();
            if (!loaded.equals(row.getValue())) {
                mismatches.add("S" + row.getKey() + ": plan says " + row.getValue() + ", code loads " + loaded);
            }
        }
        assertEquals(List.of(), mismatches,
                "CR-022 enumerated these twelve sets in the plan so that retry is a design decision rather "
                        + "than a default. A drifted declaration silently narrows or widens what retries: "
                        + mismatches);
    }

    @Test
    @DisplayName("the plan-table reader is falsifiable — it really parses cells, not a hardcoded answer")
    void planTableReaderIsFalsifiable() {
        // If declaredSetsFromPlan() returned a constant, the assertion above would pass for ever and say
        // nothing. Run the same parser over a table with a deliberately different cell.
        String table = """
                | # | Node | Purpose | Inputs → Outputs | Pre / Post | Actor | Timeout & retry | Declared retryable set | Failure class | Fallback |
                |---|---|---|---|---|---|---|---|---|---|
                | S1 | Ingestion | x | y | z | Deterministic | 5 s | `{RATE_LIMITED}` | p | None |
                | S4 | Human | x | y | z | **Human** | none | `{}` — **empty**; no executor | N/A | None |
                """;
        Map<Integer, Set<FailureCategory>> parsed = parseDeclaredSets(table);

        assertEquals(Set.of(FailureCategory.RATE_LIMITED), parsed.get(1),
                "the parser must read the cell it was given, not the one the code declares");
        assertEquals(Set.of(), parsed.get(4), "an empty set is a real declaration, not a missing one");
        assertEquals(2, parsed.size());
    }

    @Test
    @DisplayName("no contract declares a category that is never retryable anywhere")
    void noContractRetriesAPermanentCategory() {
        for (StageEffectContract contract : StageEffectContracts.all()) {
            // noneOf + addAll rather than copyOf: S4's declared set is legitimately empty, and
            // EnumSet.copyOf refuses an empty non-EnumSet because it has no element to read the type from.
            Set<FailureCategory> permanent = EnumSet.noneOf(FailureCategory.class);
            permanent.addAll(contract.retryableCategories());
            permanent.removeAll(EVER_RETRYABLE);
            assertTrue(permanent.isEmpty(),
                    "S" + contract.stageNumber() + " declares " + permanent + " retryable. INVALID_INPUT, "
                            + "INTERNAL and UNKNOWN are never retryable anywhere (plan §6): retrying "
                            + "identical input cannot change the answer, and retrying our own defect "
                            + "cannot either");
        }
    }

    @Test
    @DisplayName("TIMEOUT is declared retryable ONLY where the effect is declared idempotent (EC-033)")
    void timeoutImpliesIdempotent() {
        for (StageEffectContract contract : StageEffectContracts.all()) {
            if (contract.retryableCategories().contains(FailureCategory.TIMEOUT)) {
                assertTrue(contract.effectIdempotent(),
                        "S" + contract.stageNumber() + " retries TIMEOUT without declaring its effect "
                                + "idempotent. FR-ORC-014 rule 4: a timed-out non-idempotent effect may "
                                + "already have happened, so retrying applies it twice (EC-033)");
            }
        }

        // An implication, NOT a biconditional — and S8 is why. Its effect is repeat-safe, yet TIMEOUT is
        // excluded deliberately: at a 1800 s threshold a timeout means something is wrong rather than slow,
        // so retrying spends another half hour to fail the same way. A biconditional here would have forced
        // S8 to either retry a timeout it should not or declare an idempotence it has.
        StageEffectContract testing = StageEffectContracts.forStage(8);
        assertTrue(testing.effectIdempotent(), "running the real suite again is repeat-safe");
        assertFalse(testing.retryableCategories().contains(FailureCategory.TIMEOUT),
                "plan §3, S8: TIMEOUT excluded deliberately");
    }

    // ==============================================================================================
    // EC-034 and EC-035
    // ==============================================================================================

    @Test
    @DisplayName("EC-034: an irreversible effect with no named compensating action FAILS TO LOAD")
    void irreversibleWithoutCompensationFailsToLoad() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new StageEffectContract(7, Set.of(FailureCategory.UNAVAILABLE), false,
                        EffectClass.RECORDED_GATE_DECISION, null),
                "EC-034, FR-ORC-016 rule 3");

        assertTrue(refused.getMessage().contains("compensating action"), refused.getMessage());

        // Fails to LOAD, not at the moment of failure. A contract that only breaks when something goes
        // wrong is discovered during an incident, which is the worst time to find out that the recovery
        // path was never named.
        assertThrows(IllegalArgumentException.class,
                () -> new StageEffectContract(7, Set.of(), false, EffectClass.CREATED_SHORT_LINK, "   "),
                "a blank action names nothing");
    }

    @Test
    @DisplayName("EC-034 does not fire where there is genuinely nothing to compensate")
    void nothingToCompensateNeedsNoAction() {
        // AI provider invocations cannot be un-called and change no external state. Requiring an action
        // here would force an author to invent one, and an invented compensating action is worse than a
        // declared absence: it would run.
        StageEffectContract contract = new StageEffectContract(2,
                Set.of(FailureCategory.TIMEOUT), true, EffectClass.AI_PROVIDER_INVOCATION, null);
        assertEquals(null, contract.compensatingAction());
        assertFalse(contract.effectClass().reversible());
    }

    @Test
    @DisplayName("EC-034 does not fire on an erasable effect — rollback is the action")
    void erasableNeedsNoNamedAction() {
        StageEffectContract contract = new StageEffectContract(9,
                Set.of(FailureCategory.TIMEOUT), true, EffectClass.WORKING_TREE_WRITE, null);
        assertTrue(contract.effectClass().reversible());
    }

    @Test
    @DisplayName("EC-035: a declaration contradicting the structural rule is FLAGGED, never silently trusted")
    void contradictingDeclarationIsFlaggedForReview() {
        // The register is the structural table FR-ORC-016 resolves against; stages append declared
        // overrides and never contradict a row without human review. So this is neither accepted nor
        // thrown away: it loads, and it carries a review flag that something has to answer for.
        StageEffectContract contradicting = new StageEffectContract(11,
                Set.of(FailureCategory.UNAVAILABLE), false, EffectClass.RECORDED_GATE_DECISION,
                "write a superseding record", DeclaredReversibility.ERASABLE);

        assertTrue(contradicting.contradictsStructuralRule(),
                "a gate decision declared erasable contradicts CLAUDE.md immutability");
        assertNotNull(contradicting.reviewFlag());
        assertTrue(contradicting.reviewFlag().contains("RECORDED_GATE_DECISION"), contradicting.reviewFlag());
        assertTrue(contradicting.reviewFlag().contains("human review"), contradicting.reviewFlag());

        // And the run must not act on the contradicting declaration. Trusting it is exactly what EC-035
        // forbids: the structural rule wins until a human says otherwise.
        assertFalse(contradicting.effectivelyReversible(),
                "the structural rule stands until reviewed; the declaration does not override it");
    }

    @Test
    @DisplayName("an agreeing declaration is not flagged")
    void agreeingDeclarationIsNotFlagged() {
        StageEffectContract agreeing = new StageEffectContract(7, Set.of(), false,
                EffectClass.LOCAL_BRANCH_COMMIT, null, DeclaredReversibility.ERASABLE);
        assertFalse(agreeing.contradictsStructuralRule());
        assertEquals(null, agreeing.reviewFlag());
        assertTrue(agreeing.effectivelyReversible());
    }

    @Test
    @DisplayName("none of the twelve loaded contracts contradicts the register")
    void noLoadedContractContradictsTheRegister() {
        List<String> flagged = StageEffectContracts.all().stream()
                .filter(StageEffectContract::contradictsStructuralRule)
                .map(c -> "S" + c.stageNumber() + ": " + c.reviewFlag())
                .toList();
        assertEquals(List.of(), flagged,
                "a shipped contract carrying an unanswered review flag is a declaration nobody adjudicated");
    }

    @Test
    @DisplayName("the seven Compensation Register rows, with reversibility as the register states it")
    void sevenEffectClasses() {
        assertEquals(
                List.of("WORKING_TREE_WRITE", "LOCAL_BRANCH_COMMIT", "RECORDED_GATE_DECISION",
                        "AUDIT_RECORD", "CREATED_SHORT_LINK", "RECORDED_REDIRECT_EVENT",
                        "AI_PROVIDER_INVOCATION"),
                EnumSet.allOf(EffectClass.class).stream().map(Enum::name).toList(),
                "spec §Compensation Register, confirmed at Gate 3 under CL-007");

        assertEquals(List.of(EffectClass.WORKING_TREE_WRITE, EffectClass.LOCAL_BRANCH_COMMIT),
                EnumSet.allOf(EffectClass.class).stream().filter(EffectClass::reversible).toList(),
                "two erasable rows, and only because nothing is pushed (CL-004)");
    }

    @Test
    @DisplayName("effect idempotence is a DESIGN-TIME declaration an executor cannot reach")
    void idempotenceIsNeverExecutorSelfCertified() {
        // T070's artifact says "design-time, never executor self-certified". Structural rather than
        // conventional: StageOutcome — the only thing an executor returns — has no component that could
        // carry it, so there is no route from a running executor to this field.
        List<String> outcomeComponents = java.util.Arrays.stream(StageOutcome.class.getRecordComponents())
                .map(c -> c.getName()).toList();
        assertEquals(List.of("succeeded", "producedArtifacts", "executorKind", "failure"), outcomeComponents,
                "a component here that spoke about idempotence or reversibility would let the executor "
                        + "certify its own effect, which is the one thing this declaration must not be");

        List<String> envelopeComponents = java.util.Arrays.stream(
                        agentic.shortener.orchestration.reliability.FailureEnvelope.class.getRecordComponents())
                .map(c -> c.getName()).toList();
        assertFalse(envelopeComponents.stream().anyMatch(n -> n.toLowerCase().contains("idempot")
                        || n.toLowerCase().contains("revers")),
                "nor may the failure path carry it: " + envelopeComponents);
    }

    // ==============================================================================================
    // reading plan §3
    // ==============================================================================================

    private static Map<Integer, Set<FailureCategory>> declaredSetsFromPlan() throws Exception {
        return parseDeclaredSets(Files.readString(PLAN));
    }

    private static final Pattern NODE_ROW = Pattern.compile("^\\|\\s*S(\\d{1,2})\\s*\\|");
    private static final Pattern BRACED = Pattern.compile("\\{([^}]*)\\}");

    /**
     * Reads the {@code Declared retryable set} column out of the §3 node table.
     *
     * <p>The column is found <strong>by its header name</strong> rather than by position: a column inserted
     * before it would otherwise silently shift the reader onto a neighbouring cell, and the assertion would
     * then compare the code against the wrong thing while still passing or failing convincingly.
     */
    private static Map<Integer, Set<FailureCategory>> parseDeclaredSets(String markdown) {
        Map<Integer, Set<FailureCategory>> declared = new LinkedHashMap<>();
        int column = -1;

        for (String line : markdown.lines().toList()) {
            if (column < 0 && line.contains("| Declared retryable set |")) {
                String[] headers = line.split("\\|", -1);
                for (int i = 0; i < headers.length; i++) {
                    if (headers[i].trim().equals("Declared retryable set")) {
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
                // The table ends at the first non-node row after it started.
                if (!line.startsWith("|") && !declared.isEmpty()) {
                    break;
                }
                continue;
            }
            String[] cells = line.split("\\|", -1);
            if (column >= cells.length) {
                continue;
            }
            Matcher braced = BRACED.matcher(cells[column]);
            if (!braced.find()) {
                throw new IllegalStateException(
                        "S" + node.group(1) + "'s declared-set cell has no {...}: " + cells[column]);
            }
            declared.put(Integer.parseInt(node.group(1)), toCategories(braced.group(1)));
        }
        return declared;
    }

    private static Set<FailureCategory> toCategories(String inside) {
        if (inside.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(inside.split(","))
                .map(s -> s.replace("`", "").replace("*", "").trim())
                .filter(s -> !s.isEmpty())
                .map(FailureCategory::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
