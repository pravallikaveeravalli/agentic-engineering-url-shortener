package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.EffectClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T089 — rollback of erasable effects. FR-ORC-016, CL-004, CN-009.
 *
 * <p>Fast tier: the git operations are behind {@link WorkingTree}, so the handler's own decisions — which
 * effects it will touch, which it refuses, and how it labels what it did — are testable without a repository.
 * The port's production implementation shells out; what is asserted here is that the handler asks it to do the
 * right thing and refuses to ask for the wrong one.
 */
@DisplayName("T089 — rollback discards local un-pushed work, and nothing else")
class RollbackHandlerTest {

    /** Records what it was asked to do, rather than doing it. */
    private static final class RecordingWorkingTree implements WorkingTree {
        private final List<String> operations = new ArrayList<>();

        @Override
        public void discard(String path) {
            operations.add("discard " + path);
        }

        @Override
        public void resetBranch(String branchRef) {
            operations.add("reset " + branchRef);
        }

        List<String> operations() {
            return List.copyOf(operations);
        }
    }

    private final RecordingWorkingTree workingTree = new RecordingWorkingTree();
    private final CompensationRegister register = new CompensationRegister(null, null);
    private final RollbackHandler handler = new RollbackHandler(workingTree, register);

    @Test
    @DisplayName("a working-tree write is discarded")
    void discardsAWorkingTreeWrite() {
        RecoveryOutcome outcome = handler.rollback("working-tree:docs/summary.md");

        assertEquals(List.of("discard docs/summary.md"), workingTree.operations());
        assertEquals(RecoveryKind.ROLLBACK, outcome.kind());
        assertTrue(outcome.action().startsWith("ROLLBACK"),
                "the label is what lets run history distinguish this from a compensation: " + outcome.action());
    }

    @Test
    @DisplayName("a local branch commit is reset")
    void resetsALocalBranch() {
        RecoveryOutcome outcome = handler.rollback("branch:run/aa/S7.1");

        assertEquals(List.of("reset run/aa/S7.1"), workingTree.operations());
        assertEquals(RecoveryKind.ROLLBACK, outcome.kind());
    }

    @Test
    @DisplayName("every effect the register calls compensate-only is REFUSED, and nothing is touched")
    void refusesEverythingElse() {
        List<String> wronglyAccepted = new ArrayList<>();
        for (EffectClass immutable : EnumSet.allOf(EffectClass.class)) {
            if (immutable.reversible()) {
                continue;
            }
            String effectKey = keyFor(immutable);
            try {
                handler.rollback(effectKey);
                wronglyAccepted.add(immutable.name());
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("corrected forward"), expected.getMessage());
            }
        }
        assertEquals(List.of(), wronglyAccepted,
                "local un-pushed work is the ONLY erasable class (CL-004). A rollback claim against an "
                        + "append-only store would be false in the run history: " + wronglyAccepted);
        assertEquals(List.of(), workingTree.operations(),
                "and a refused rollback must not have touched the working tree on its way to refusing");
    }

    @Test
    @DisplayName("an UNCLASSIFIED effect is refused too — the default is compensate-only")
    void refusesUnclassified() {
        // FR-ORC-016 rule 4 reaching the handler. An unknown effect discarded on the guess that it was
        // discardable fails worst exactly where the guess is wrong.
        assertThrows(IllegalStateException.class, () -> handler.rollback("something-new:1"));
        assertEquals(List.of(), workingTree.operations());
    }

    @Test
    @DisplayName("the outcome is never labelled COMPENSATION")
    void neverMislabelled() {
        // T089/T090's shared guard. A reviewer reading run history has to be able to tell "we undid it" from
        // "we corrected forward and the original is still there" — different claims about the world.
        for (String erasable : List.of("working-tree:x", "branch:y")) {
            assertEquals(RecoveryKind.ROLLBACK, handler.rollback(erasable).kind());
        }
    }

    @Test
    @DisplayName("a working-tree key with no path is refused rather than discarding everything")
    void emptyPathIsRefused() {
        // "working-tree:" with nothing after it would become `discard ""`, and a discard of the empty path is
        // the kind of thing that means "all of it" to a shell. Refused before the port is reached.
        assertThrows(IllegalArgumentException.class, () -> handler.rollback("working-tree:"));
        assertThrows(IllegalArgumentException.class, () -> handler.rollback("branch:   "));
        assertEquals(List.of(), workingTree.operations());
    }

    private static String keyFor(EffectClass effectClass) {
        return switch (effectClass) {
            case RECORDED_GATE_DECISION -> "gate-decision:41";
            case AUDIT_RECORD -> "audit:912";
            case CREATED_SHORT_LINK -> "short-link:Ab3dEfG";
            case RECORDED_REDIRECT_EVENT -> "redirect-event:77";
            case AI_PROVIDER_INVOCATION -> "ai-invocation:S5/1";
            case WORKING_TREE_WRITE -> "working-tree:docs/x.md";
            case LOCAL_BRANCH_COMMIT -> "branch:run/aa/S7";
        };
    }
}
