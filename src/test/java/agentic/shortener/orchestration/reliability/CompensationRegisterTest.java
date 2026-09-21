package agentic.shortener.orchestration.reliability;

import agentic.shortener.orchestration.executor.EffectClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T088 — the Compensation Register's resolution half. FR-ORC-016 (CL-007), KE-28.
 *
 * <p>The recording half needs the store and lives in {@code CompensationRegisterIT}. Splitting them is not
 * bookkeeping: resolution is the part that must never be wrong, and keeping it container-free means it can be
 * exercised freely rather than once per integration run.
 */
@DisplayName("T088 — resolution is structural, and the default is the safe one")
class CompensationRegisterTest {

    private static final CompensationRegister REGISTER = new CompensationRegister(null, null);

    @Test
    @DisplayName("the class is derived from WHERE the effect landed, not from what a stage claims")
    void classificationIsStructural() {
        assertEquals(EffectClass.WORKING_TREE_WRITE, REGISTER.resolve("working-tree:docs/summary.md").effectClass());
        assertEquals(EffectClass.LOCAL_BRANCH_COMMIT, REGISTER.resolve("branch:run/aa/S7.1").effectClass());
        assertEquals(EffectClass.RECORDED_GATE_DECISION, REGISTER.resolve("gate-decision:41").effectClass());
        assertEquals(EffectClass.AUDIT_RECORD, REGISTER.resolve("audit:912").effectClass());
        assertEquals(EffectClass.CREATED_SHORT_LINK, REGISTER.resolve("short-link:Ab3dEfG").effectClass());
        assertEquals(EffectClass.RECORDED_REDIRECT_EVENT, REGISTER.resolve("redirect-event:77").effectClass());
        assertEquals(EffectClass.AI_PROVIDER_INVOCATION, REGISTER.resolve("ai-invocation:S5/1").effectClass());
    }

    @Test
    @DisplayName("an UNCLASSIFIED effect is compensate-only, never erasable")
    void unclassifiedIsCompensateOnly() {
        // FR-ORC-016 rule 4. The default has to run toward the answer that is safe when wrong: treating an
        // unknown effect as erasable would discard something on the guess that it could be discarded, and
        // the cases where that guess is wrong are exactly the ones where it cannot be undone.
        for (String unknown : List.of("something-new:1", "", "no-prefix", "WORKING-TREE:x")) {
            CompensationRegister.Resolution resolution = REGISTER.resolve(unknown);
            assertEquals(RecoveryKind.COMPENSATION, resolution.kind(),
                    "effect key was: '" + unknown + "'");
            assertFalse(resolution.erasable(), "effect key was: '" + unknown + "'");
            assertTrue(resolution.action().contains("no register row"), resolution.action());
        }
    }

    @Test
    @DisplayName("the seven rows map to the three recovery kinds the register actually has")
    void sevenRowsThreeKinds() {
        assertEquals(List.of(EffectClass.WORKING_TREE_WRITE, EffectClass.LOCAL_BRANCH_COMMIT),
                EnumSet.allOf(EffectClass.class).stream()
                        .filter(c -> REGISTER.kindOf(c) == RecoveryKind.ROLLBACK).toList(),
                "local un-pushed work is the only erasable class (CL-004)");

        assertEquals(List.of(EffectClass.AI_PROVIDER_INVOCATION),
                EnumSet.allOf(EffectClass.class).stream()
                        .filter(c -> REGISTER.kindOf(c) == RecoveryKind.NOTHING_TO_COMPENSATE).toList(),
                "an AI call cannot be un-called and changed no external state");

        assertEquals(4, EnumSet.allOf(EffectClass.class).stream()
                        .filter(c -> REGISTER.kindOf(c) == RecoveryKind.COMPENSATION).count(),
                "gate decisions, audit records, short links, redirect events");
    }

    @Test
    @DisplayName("ROLLING_BACK on an immutable-store effect is REFUSED")
    void rollbackOnImmutableStoreIsRefused() {
        List<String> accepted = new ArrayList<>();
        for (EffectClass immutable : EnumSet.allOf(EffectClass.class)) {
            if (immutable.reversible()) {
                continue;
            }
            try {
                REGISTER.requireRollbackPermitted(immutable);
                accepted.add(immutable.name());
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("corrected forward"), expected.getMessage());
            }
        }
        assertEquals(List.of(), accepted,
                "rollback against an append-only store is not a risky operation, it is an impossible one; "
                        + "a claim that it happened would be false in the run history: " + accepted);

        // And the two that ARE erasable must be permitted, or the refusal is just a blanket no.
        REGISTER.requireRollbackPermitted(EffectClass.WORKING_TREE_WRITE);
        REGISTER.requireRollbackPermitted(EffectClass.LOCAL_BRANCH_COMMIT);
    }

    @Test
    @DisplayName("the short-link correction is EXPIRY, never deletion")
    void shortLinkIsExpiredNotDeleted() {
        String action = REGISTER.resolve("short-link:Ab3dEfG").action();

        // Asserted as what the action PRESCRIBES, not as the absence of the word "delete". The first version
        // of this check forbade the word outright and failed on "set to expired, never delete" — the clause
        // that states the prohibition. Same trap the Slice 3 provenance prose set: an absence assertion
        // tripping over the sentence that forbids the thing. The property is that expiry is the action and
        // deletion is explicitly ruled out, and that is what is checked.
        assertTrue(action.contains("expired"), action);
        assertTrue(action.contains("never delete"),
                "links are never deleted (EX-003, T-13): a deleted code leaves dangling history and can be "
                        + "re-issued to someone else, which is a redirect hijack. The register row has to "
                        + "say so, because the tempting action for an unwanted link is to remove it: "
                        + action);
        assertFalse(action.matches("(?i).*\\b(delete|remove|drop) the (link|row|record)\\b.*"),
                "and it must not prescribe deletion anywhere: " + action);
    }

    @Test
    @DisplayName("where no compensating action is known: touch nothing, and say so")
    void noKnownActionTouchesNothing() {
        // T088's guard. The register's honest answer for an effect it cannot correct is "I cannot", and the
        // caller's response is suspension (T091) — not a best-effort attempt at something plausible, which
        // is how a recovery path makes an incident worse.
        CompensationRegister.Resolution resolution = REGISTER.resolveWithoutKnownAction("mystery:1");

        assertEquals(RecoveryKind.NONE_KNOWN, resolution.kind());
        assertTrue(resolution.requiresSuspension(),
                "an effect with no known compensating action is a safe-stop trigger (FR-ORC-017)");
        assertThrows(IllegalStateException.class, () -> resolution.requireActionable(),
                "asking for an action that does not exist must fail loudly rather than return a plausible "
                        + "string somebody then runs");
    }

    @Test
    @DisplayName("NOTHING_TO_COMPENSATE is not the same as NONE_KNOWN")
    void nothingToCompensateIsNotIgnorance() {
        // The distinction a reader needs and a single "no action" value would destroy: one says the effect
        // changed nothing that needs correcting, the other says we do not know how to correct it. The first
        // is fine and the second must suspend.
        CompensationRegister.Resolution aiCall = REGISTER.resolve("ai-invocation:S5/1");
        assertEquals(RecoveryKind.NOTHING_TO_COMPENSATE, aiCall.kind());
        assertFalse(aiCall.requiresSuspension());

        assertTrue(REGISTER.resolveWithoutKnownAction("mystery:1").requiresSuspension());
    }
}
