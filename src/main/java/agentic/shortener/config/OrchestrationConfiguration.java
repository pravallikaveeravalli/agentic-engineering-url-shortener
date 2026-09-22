package agentic.shortener.config;

import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.api.RunInspectionQuery;
import agentic.shortener.orchestration.conductor.Conductor;
import agentic.shortener.orchestration.conductor.ExistingFileReader;
import agentic.shortener.orchestration.conductor.FanOutPlanner;
import agentic.shortener.orchestration.conductor.GitWorktreeBranchApplier;
import agentic.shortener.orchestration.conductor.RepoExistingFileReader;
import agentic.shortener.orchestration.conductor.ScriptTestSuiteRunner;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.ai.ClaudeCodeCliStageAiProvider;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.executor.ai.stages.AmbiguityDetectionAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.BranchApplier;
import agentic.shortener.orchestration.executor.ai.stages.DecompositionAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.DesignAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.DocumentationAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.ImplementationAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.NormalizationAiExecutor;
import agentic.shortener.orchestration.executor.deterministic.DeterministicEngines;
import agentic.shortener.orchestration.executor.deterministic.EnginePorts;
import agentic.shortener.orchestration.executor.deterministic.PolicyEvaluator;
import agentic.shortener.orchestration.executor.deterministic.ReadinessEvaluator;
import agentic.shortener.orchestration.executor.deterministic.TestSuiteRunner;
import agentic.shortener.orchestration.gates.GateOutcomeHandler;
import agentic.shortener.orchestration.gates.GateRequestPresenter;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.ArtifactWriteGuard;
import agentic.shortener.orchestration.reliability.Backoff;
import agentic.shortener.orchestration.reliability.RetryPolicy;
import agentic.shortener.orchestration.state.RetentionPolicy;
import agentic.shortener.orchestration.state.SafeStopHandler;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.policy.PolicySetEvaluator;
import agentic.shortener.policy.ReleaseReadinessEvaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Control-plane wiring. Task T067.
 *
 * <p>A separate class from {@link PersistenceConfiguration}, not an addition to it, because that class's
 * own javadoc states the two planes stay separable only if the wiring reflects the boundary too — one
 * {@code @Configuration} class per plane is what keeps "the shortener is demonstrable with the
 * orchestration engine switched off" (T014's rule, ADR-006) true of the wiring itself, not only of the
 * code it wires.
 *
 * <p>{@code config} is not one of T014's four forbidden packages (domain, application, delivery,
 * persistence) — it is the one place allowed to know both planes, the same role
 * {@link PersistenceConfiguration} already plays for the application plane's own repositories. Reuses the
 * {@link ConnectionSource} bean that class already exposes, bridging Spring's {@code DataSource} once for
 * both planes rather than a second time here.
 */
@Configuration
public class OrchestrationConfiguration {

    @Bean
    public RunInspectionQuery runInspectionQuery(ConnectionSource connections) {
        return new RunInspectionQuery(connections);
    }

    @Bean
    public JdbcRunStore jdbcRunStore(ConnectionSource connections, Clock clock) {
        return new JdbcRunStore(connections, clock);
    }

    @Bean
    public GateStore gateStore(ConnectionSource connections, Clock clock) {
        return new GateStore(connections, clock);
    }

    @Bean
    public GateOutcomeHandler gateOutcomeHandler(JdbcRunStore jdbcRunStore, GateStore gateStore,
                                                 ConnectionSource connections) {
        return new GateOutcomeHandler(jdbcRunStore, gateStore, connections);
    }

    // ==============================================================================================
    // T131a/T131b/T131c — the Conductor and everything it composes. This is production wiring only:
    // the same classes the live DS-A/DS-B/DS-C demonstration runs use, constructed directly (not
    // through Spring) so those runs are provably identical to what T073a-f's demos already established
    // rather than a parallel path the wiring never actually exercises.
    // ==============================================================================================

    /**
     * The repository root every filesystem-reading collaborator below needs (policy/readiness
     * evaluation, the git-worktree branch applier and test runner). Resolved from the process's own
     * working directory, matching every other place this codebase already does the same resolution
     * (e.g. {@code SummaryAssemblerMain}) rather than inventing a new configuration key for a value a
     * correctly-launched process already knows.
     */
    @Bean
    public Path repoRoot() {
        return Paths.get("").toAbsolutePath();
    }

    /** ADR-004-A1's documented primary/production adapter. Config keys already governed by {@code
     * SecureDefaultsTest} — see {@code application.yml}'s own {@code shortener.claude-cli} comment. */
    @Bean
    public StageAiProvider stageAiProvider(@Value("${shortener.claude-cli.command}") String command,
            @Value("${shortener.claude-cli.pinned-model}") String pinnedModel) {
        return new ClaudeCodeCliStageAiProvider(command, pinnedModel);
    }

    @Bean
    public NormalizationAiExecutor normalizationAiExecutor(StageAiProvider provider) {
        return new NormalizationAiExecutor(provider);
    }

    @Bean
    public AmbiguityDetectionAiExecutor ambiguityDetectionAiExecutor(StageAiProvider provider) {
        return new AmbiguityDetectionAiExecutor(provider);
    }

    @Bean
    public DecompositionAiExecutor decompositionAiExecutor(StageAiProvider provider) {
        return new DecompositionAiExecutor(provider);
    }

    @Bean
    public DesignAiExecutor designAiExecutor(StageAiProvider provider, Clock clock) {
        return new DesignAiExecutor(provider, clock);
    }

    /** The real implementation T073e's own live-demo class left unbuilt (T131c). */
    @Bean
    public BranchApplier branchApplier(Path repoRoot) {
        return new GitWorktreeBranchApplier(repoRoot, "main");
    }

    /** S7's existing-file fix: reads real content for whatever files S6's design names, so the
     * orchestration -- never the executor -- can inject it into S7's own {@code StageInput}. */
    @Bean
    public ExistingFileReader existingFileReader(Path repoRoot) {
        return new RepoExistingFileReader(repoRoot);
    }

    @Bean
    public ImplementationAiExecutor implementationAiExecutor(StageAiProvider provider,
            BranchApplier branchApplier) {
        return new ImplementationAiExecutor(provider, branchApplier);
    }

    @Bean
    public DocumentationAiExecutor documentationAiExecutor(StageAiProvider provider) {
        return new DocumentationAiExecutor(provider);
    }

    /** The real implementation T071's own javadoc calls for (T131c). */
    @Bean
    public TestSuiteRunner testSuiteRunner(Path repoRoot) {
        return new ScriptTestSuiteRunner(repoRoot);
    }

    /** {@code policy-set-1.1.0}'s evaluator (T100), reused unmodified for S10. */
    @Bean
    public PolicyEvaluator policyEvaluator(ConnectionSource connections, Path repoRoot, Clock clock) {
        return new PolicySetEvaluator(connections, repoRoot, clock);
    }

    /** The nine constitutional blocking conditions (T109), reused unmodified for S11. */
    @Bean
    public ReadinessEvaluator readinessEvaluator(ConnectionSource connections, Path repoRoot, Clock clock) {
        return new ReleaseReadinessEvaluator(connections, repoRoot, clock);
    }

    @Bean
    public EnginePorts enginePorts(TestSuiteRunner testSuiteRunner, PolicyEvaluator policyEvaluator,
            ReadinessEvaluator readinessEvaluator) {
        return new EnginePorts(testSuiteRunner, policyEvaluator, readinessEvaluator);
    }

    /** The one dispatch table {@link Conductor} needs: stage number to executor, deterministic stages
     * via {@link DeterministicEngines#forStage}, AI-capable stages via the six beans above. */
    @Bean
    public Function<Integer, StageExecutor> executorsByStageNumber(EnginePorts ports,
            NormalizationAiExecutor s2, AmbiguityDetectionAiExecutor s3, DecompositionAiExecutor s5,
            DesignAiExecutor s6, ImplementationAiExecutor s7, DocumentationAiExecutor s9) {
        return stageNumber -> switch (stageNumber) {
            case 1, 8, 10, 11, 12 -> DeterministicEngines.forStage(stageNumber, ports);
            case 2 -> s2;
            case 3 -> s3;
            case 5 -> s5;
            case 6 -> s6;
            case 7 -> s7;
            case 9 -> s9;
            default -> throw new IllegalArgumentException("no executor registered for stage " + stageNumber);
        };
    }

    @Bean
    public ArtifactWriteGuard artifactWriteGuard(ConnectionSource connections, Clock clock) {
        return new ArtifactWriteGuard(connections, clock);
    }

    @Bean
    public AuditWriter auditWriter(ConnectionSource connections) {
        return new AuditWriter(connections);
    }

    @Bean
    public RetentionPolicy retentionPolicy() {
        return new RetentionPolicy();
    }

    @Bean
    public SafeStopHandler safeStopHandler(ConnectionSource connections, Clock clock,
            RetentionPolicy retentionPolicy) {
        return new SafeStopHandler(connections, clock, retentionPolicy);
    }

    /** PVT-006's real 24-hour gate wait — the production default, distinct from a demonstration run's
     * own deliberately compressed, disclosed period (AS-007), which is never this bean. */
    @Bean
    public GateRequestPresenter gateRequestPresenter(Clock clock, RetentionPolicy retentionPolicy) {
        return new GateRequestPresenter(clock, retentionPolicy);
    }

    @Bean
    public RetryPolicy retryPolicy(StageTelemetry telemetry) {
        return new RetryPolicy(Backoff.sleeping(), telemetry);
    }

    @Bean
    public ExecutorService conductorDispatchPool() {
        return Executors.newFixedThreadPool(4);
    }

    @Bean
    public Conductor conductor(JdbcRunStore runStore, GateStore gateStore,
            GateRequestPresenter gateRequestPresenter, ArtifactWriteGuard artifactWriteGuard,
            AuditWriter auditWriter, StageTelemetry telemetry, SafeStopHandler safeStopHandler,
            RetryPolicy retryPolicy, Function<Integer, StageExecutor> executorsByStageNumber, Clock clock,
            ExecutorService conductorDispatchPool, ExistingFileReader existingFileReader) {
        return new Conductor(runStore, gateStore, gateRequestPresenter, artifactWriteGuard, auditWriter,
                telemetry, safeStopHandler, retryPolicy, executorsByStageNumber, FanOutPlanner.singleChild(),
                clock, conductorDispatchPool, existingFileReader);
    }

    /** T082a: {@code RunSubmissionController} fires {@link Conductor#advance} here rather than on the
     * request thread — a distinct pool from {@link #conductorDispatchPool()}, which is {@link Conductor}'s
     * own internal per-round dispatch concern, not the "don't block this HTTP response" concern this one
     * exists for. */
    @Bean
    public ExecutorService advancePool() {
        return Executors.newFixedThreadPool(2);
    }
}
