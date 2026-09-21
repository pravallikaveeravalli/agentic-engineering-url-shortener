package agentic.shortener.audit;

import java.util.List;

/**
 * The four orphan classes T124's Artifact clause names, each a list of human-readable findings — empty
 * when clean. Task T123, T124. FR-ORC-027, SC-010.
 *
 * <p><strong>Declared scope, not implied</strong>: this directly proves requirement → task → code → test,
 * five of the ten-link chain's links. {@code design} and {@code adr} are read from {@code spec.md}'s row
 * but not independently verified (CR-006 already requires both non-blank, and this parser would surface a
 * row missing either as an orphan requirement only if no task references it at all — a genuinely blank
 * Design/ADR cell on a referenced row is not separately checked). {@code documentation} and {@code
 * evidence} — the matrix's own {@code Evidence} column, e.g. {@code docs/evidence/}, CI GREEN — are parsed
 * but not verified to exist or to be current; doing so mechanically would mean resolving free-text
 * evidence claims against the filesystem, which this class does not yet attempt. Stated here rather than
 * implied, matching this project's own artifact-coverage-sweep discipline: an unasserted clause is a
 * finding, not a pass.
 *
 * @param orphanRequirements a requirement row in {@code spec.md}'s matrix that no task in {@code tasks.md}
 *                            references at all — a planning gap: the requirement was specified but nothing
 *                            was ever tasked against it
 * @param orphanTasks         a task in {@code tasks.md} whose {@code **Req**:} field names nothing
 *                            recognizable — no requirement, policy, ADR, or constitution reference at all
 * @param orphanImplementations a task marked done ({@code [x]}) whose own title-line artifact path does
 *                                not exist on disk — claimed delivered, not actually there
 * @param orphanTests          a task marked done whose block text names a test class
 *                              ({@code SomeNameTest}/{@code SomeNameIT}) that does not exist as a
 *                              {@code .java} file anywhere under {@code src/test/java}
 */
public record TraceabilityReport(List<String> orphanRequirements, List<String> orphanTasks,
                                  List<String> orphanImplementations, List<String> orphanTests) {

    public boolean isClean() {
        return orphanRequirements.isEmpty() && orphanTasks.isEmpty() && orphanImplementations.isEmpty()
                && orphanTests.isEmpty();
    }
}
