package agentic.shortener.audit;

import java.util.List;
import java.util.Objects;

/**
 * One task from {@code tasks.md}, parsed as-is. Task T123.
 *
 * @param taskId          e.g. {@code T115}
 * @param done            the checkbox state — {@code [x]} vs {@code [ ]}
 * @param artifactPaths   backtick-quoted, file-shaped tokens from the task's OWN title line (the text
 *                        after the em dash), not from the prose {@code Artifact} field below it — the
 *                        title line is structured data; the field below it is prose that happens to
 *                        sometimes repeat a path and sometimes doesn't
 * @param requirementRefs recognized requirement/policy/ADR/constitution tokens pulled from the task's
 *                        {@code **Req**:} field
 * @param rawBlock        the full multi-line task block, kept so {@link TraceabilityReporter}'s orphan-test
 *                        check can search it for a mentioned test class name without a second file read
 */
public record TaskEntry(String taskId, boolean done, List<String> artifactPaths, List<String> requirementRefs,
                         String rawBlock) {

    public TaskEntry {
        Objects.requireNonNull(taskId, "taskId");
        artifactPaths = List.copyOf(artifactPaths);
        requirementRefs = List.copyOf(requirementRefs);
        Objects.requireNonNull(rawBlock, "rawBlock");
    }
}
