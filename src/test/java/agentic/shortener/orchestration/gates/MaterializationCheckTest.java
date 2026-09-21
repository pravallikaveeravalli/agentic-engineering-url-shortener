package agentic.shortener.orchestration.gates;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T062 — gate-record materialization enforcement. FR-ORC-013 (CR-001).
 *
 * <p><strong>A decision existing only in the database does not satisfy the gate.</strong> {@code CLAUDE.md}
 * requires the repository record to exist in the working tree, and {@code repository_record_path} being a
 * required, shape-checked column is not the same claim as the file actually being there — a caller could
 * name a path to a record it never wrote. This class is what closes that gap: it looks at the filesystem,
 * not at the string.
 *
 * <p>Deliberately its own class rather than folded into {@link GateOutcomeHandler}: T062 is {@code [P]},
 * depends only on T058, and its own {@code Validate} clause is narrow — a decision without a materialized
 * record cannot authorize downstream work. Whatever eventually calls both this check and the outcome
 * handler composes them; this class does not assume it is the only caller.
 */
@DisplayName("T062 — a decision's record must exist in the working tree to take effect")
class MaterializationCheckTest {

    private Path plantedFile;

    @AfterEach
    void cleanUp() throws Exception {
        if (plantedFile != null) {
            Files.deleteIfExists(plantedFile);
        }
    }

    private GateDecision decisionAt(String repositoryRecordPath) {
        return new GateDecision(UUID.randomUUID(), "S4", null, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.APPROVED, new Actor("human", "the owner"), Instant.parse("2026-09-21T12:00:00Z"),
                "resolved", repositoryRecordPath, List.of(), null, null);
    }

    @Test
    @DisplayName("a decision whose record file EXISTS is materialized")
    void materializedWhenTheFileExists() throws Exception {
        plantedFile = Files.createTempFile("gate-decision-", ".md");
        Files.writeString(plantedFile, "# a real gate decision record\n");

        assertDoesNotThrow(() -> MaterializationCheck.requireMaterialized(
                decisionAt(plantedFile.toString())));
    }

    @Test
    @DisplayName("a decision naming a path that does not exist on disk is REFUSED")
    void refusedWhenTheFileDoesNotExist() {
        GateDecision decision = decisionAt("docs/governance/gate-decisions/does-not-exist-"
                + UUID.randomUUID() + ".md");

        UnmaterializedDecisionException refused = assertThrows(UnmaterializedDecisionException.class,
                () -> MaterializationCheck.requireMaterialized(decision));
        assertTrue(refused.getMessage().contains(decision.repositoryRecordPath()),
                "the refusal must name the missing path, or a caller cannot act on it: "
                        + refused.getMessage());
    }

    @Test
    @DisplayName("a decision naming an EMPTY file is refused too — existing is not the same as substantive")
    void refusedWhenTheFileIsEmpty() throws Exception {
        // A record path that resolves to a zero-byte file is not meaningfully different from one that
        // does not exist: neither carries a decision a reviewer could read. Files.exists() alone would
        // pass this, which is why the check reads the file rather than merely stat-ing it.
        plantedFile = Files.createTempFile("gate-decision-empty-", ".md");

        assertThrows(UnmaterializedDecisionException.class,
                () -> MaterializationCheck.requireMaterialized(decisionAt(plantedFile.toString())));
    }

    @Test
    @DisplayName("writing the record is not itself “work the decision authorizes”")
    void writingTheRecordDoesNotItselfRequireAuthorization() throws Exception {
        // T062's own guard, made concrete: nothing about MaterializationCheck gates the ACT of writing a
        // file to disk — there is no method here that could. The rule cannot deadlock because the
        // action it constrains (accepting a decision's effect) and the action that satisfies it (writing
        // the file) are structurally different operations; this test is what makes that a checked fact
        // rather than an assertion in a comment.
        assertTrue(java.lang.reflect.Modifier.isFinal(MaterializationCheck.class.getModifiers()));

        long writeLikeMethods = java.util.Arrays.stream(MaterializationCheck.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getName().toLowerCase().contains("write")
                        || m.getName().toLowerCase().contains("create")
                        || m.getName().toLowerCase().contains("record"))
                .count();
        assertTrue(writeLikeMethods == 0,
                "this class must have no method that WRITES a record — only one that checks whether "
                        + "one already exists, or the two actions this task keeps separate would blur");
    }
}
