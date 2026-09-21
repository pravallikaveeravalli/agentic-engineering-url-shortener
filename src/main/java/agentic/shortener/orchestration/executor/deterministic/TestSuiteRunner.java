package agentic.shortener.orchestration.executor.deterministic;

/**
 * Executes the real test suite. Task T071's port for S8.
 *
 * <p>A port rather than an inlined process call, for one reason that matters: S8's engine can then be unit
 * tested without running Maven inside a unit test, while the production wiring still runs the actual suite.
 * Plan §3 says S8 is deterministic and <strong>real</strong>, and a port does not weaken that — what it
 * removes is the engine knowing how a build is invoked.
 *
 * <p>{@code throws Exception} because a runner fails in whatever way its transport fails. The engine
 * translates through {@code ProviderFailureTranslator} rather than learning those types itself, which is what
 * keeps the vendor taxonomy in one place (T083).
 */
@FunctionalInterface
public interface TestSuiteRunner {

    TestSuiteReport run(String branchRef) throws Exception;
}
