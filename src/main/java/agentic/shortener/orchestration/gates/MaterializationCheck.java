package agentic.shortener.orchestration.gates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Gate-record materialization enforcement. Task T062. FR-ORC-013 (CR-001).
 *
 * <p><strong>Looks at the filesystem, not at the string.</strong> {@link GateDecision#repositoryRecordPath}
 * being a required, shape-checked field proves only that the value looks like a path under
 * {@code docs/governance/}; it does not prove a reviewer could open the file and read a real decision.
 * This class is the difference between those two claims.
 *
 * <p><strong>Existing is not the same as substantive.</strong> A zero-byte file at the right path would
 * pass a bare {@link Files#exists}, and it carries nothing a reviewer could act on — so the content is
 * read and checked for anything beyond whitespace, not merely stat'd.
 *
 * <p><strong>Deliberately has no method that writes anything.</strong> T062's own guard is that writing the
 * record is not itself "work the decision authorizes", so the rule cannot deadlock — a class that could
 * both check and create the record would blur the two actions this task keeps apart. Whatever writes the
 * governance record does so through an entirely separate path (a human, or an agent under §15 discipline,
 * committing a file); this class only ever reads.
 */
public final class MaterializationCheck {

    private MaterializationCheck() {
    }

    /**
     * @throws UnmaterializedDecisionException if the record does not exist, cannot be read, or is blank.
     *                                         Authorized work cannot begin until this passes
     */
    public static void requireMaterialized(GateDecision decision) {
        Objects.requireNonNull(decision, "decision");
        Path recordPath = Path.of(decision.repositoryRecordPath());

        if (!Files.exists(recordPath)) {
            throw new UnmaterializedDecisionException(
                    "decision " + decision.decisionId() + " on gate '" + decision.gateId()
                            + "' names repositoryRecordPath '" + decision.repositoryRecordPath()
                            + "', which does not exist in the working tree. A decision existing only in "
                            + "the database does not satisfy the gate (CLAUDE.md, FR-ORC-013)");
        }

        String content;
        try {
            content = Files.readString(recordPath);
        } catch (IOException e) {
            throw new UnmaterializedDecisionException(
                    "decision " + decision.decisionId() + "'s record at '"
                            + decision.repositoryRecordPath() + "' could not be read: " + e.getMessage());
        }

        if (content.isBlank()) {
            throw new UnmaterializedDecisionException(
                    "decision " + decision.decisionId() + "'s record at '"
                            + decision.repositoryRecordPath() + "' is empty. A file existing at the right "
                            + "path is not the same claim as a reviewer being able to read a decision in "
                            + "it");
        }
    }
}
