package agentic.shortener.orchestration.executor.ai.stages;

/**
 * Applies an AI-authored change set on a branch and reports whether the result builds. Task T073e's port for
 * S7.
 *
 * <p>A port rather than {@link ImplementationAiExecutor} invoking git and a build tool directly, for the
 * same reason {@code TestSuiteRunner} exists for S8 (T071): the executor's own decision logic — did the
 * change apply and build, so does the stage succeed or route back — is unit-testable without a real git
 * checkout or a real build running inside a unit test, while production wiring uses a real implementation.
 *
 * <p><strong>This is deliberately NOT the real test suite.</strong> S7's own plan-table postcondition is
 * "change applied on branch, buildable" — compiles, nothing more. Running the REAL suite is S8's job
 * ({@code TestingEngine}, already built), a separate stage the graph runs after S7 joins. Conflating the
 * two here would duplicate S8's own responsibility and let S7 report a false "verified" before S8 ever ran.
 *
 * <p><strong>CR-060: {@code changeSet} is a JSON change set (per-file CREATE full-content or EDIT
 * search/replace pairs), never a unified diff</strong> — {@link ImplementationAiExecutor}'s own javadoc
 * explains why. A real implementation applies it deterministically: writes a CREATE's content verbatim,
 * and for an EDIT, requires each search string to match the current file content EXACTLY ONCE before
 * replacing it — never fuzzily, and never partially applying a file whose edits do not all match.
 *
 * <p>{@code throws Exception} because a real implementation fails in whatever way git or the build tool
 * fails; {@link ImplementationAiExecutor} translates through the standard
 * {@code ProviderFailureTranslator}, the same seam every other adapter in this package uses.
 */
@FunctionalInterface
public interface BranchApplier {

    ApplyResult apply(String taskId, String changeSet) throws Exception;

    /**
     * @param buildable whether the change set applied cleanly AND the result builds. {@code false} with a
     *                  non-blank {@code detail} is a routine outcome (S7's Guard: "a failing AI change being
     *                  caught is the governance working, not a failed demonstration"), never an exception
     * @param branchRef the branch the change set was applied to, present only when {@code buildable}
     * @param detail    what happened — the apply error, or the build failure output
     */
    record ApplyResult(boolean buildable, String branchRef, String detail) {

        public ApplyResult {
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException(
                        "an apply result without a detail leaves a route-back report nobody can act on");
            }
            if (buildable && (branchRef == null || branchRef.isBlank())) {
                throw new IllegalArgumentException(
                        "a buildable result must name the branch the change set was applied to");
            }
        }
    }
}
