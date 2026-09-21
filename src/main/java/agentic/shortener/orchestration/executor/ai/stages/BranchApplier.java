package agentic.shortener.orchestration.executor.ai.stages;

/**
 * Applies an AI-authored patch on a branch and reports whether the result builds. Task T073e's port for S7.
 *
 * <p>A port rather than {@link ImplementationAiExecutor} invoking git and a build tool directly, for the
 * same reason {@code TestSuiteRunner} exists for S8 (T071): the executor's own decision logic — did the
 * patch apply and build, so does the stage succeed or route back — is unit-testable without a real git
 * checkout or a real build running inside a unit test, while production wiring uses a real implementation.
 *
 * <p><strong>This is deliberately NOT the real test suite.</strong> S7's own plan-table postcondition is
 * "patch applied on branch, buildable" — compiles, nothing more. Running the REAL suite is S8's job
 * ({@code TestingEngine}, already built), a separate stage the graph runs after S7 joins. Conflating the
 * two here would duplicate S8's own responsibility and let S7 report a false "verified" before S8 ever ran.
 *
 * <p>{@code throws Exception} because a real implementation fails in whatever way git or the build tool
 * fails; {@link ImplementationAiExecutor} translates through the standard
 * {@code ProviderFailureTranslator}, the same seam every other adapter in this package uses.
 */
@FunctionalInterface
public interface BranchApplier {

    ApplyResult apply(String taskId, String patch) throws Exception;

    /**
     * @param buildable whether the patch applied cleanly AND the result builds. {@code false} with a
     *                  non-blank {@code detail} is a routine outcome (S7's Guard: "a failing AI patch being
     *                  caught is the governance working, not a failed demonstration"), never an exception
     * @param branchRef the branch the patch was applied to, present only when {@code buildable}
     * @param detail    what happened — the patch-apply error, or the build failure output
     */
    record ApplyResult(boolean buildable, String branchRef, String detail) {

        public ApplyResult {
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException(
                        "an apply result without a detail leaves a route-back report nobody can act on");
            }
            if (buildable && (branchRef == null || branchRef.isBlank())) {
                throw new IllegalArgumentException(
                        "a buildable result must name the branch the patch was applied to");
            }
        }
    }
}
