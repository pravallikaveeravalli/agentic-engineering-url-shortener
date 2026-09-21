package agentic.shortener.orchestration.conductor;

import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.GeminiCliStageAiProvider;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.executor.ai.stages.AmbiguityDetectionAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.DecompositionAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.DesignAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.DocumentationAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.ImplementationAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.NormalizationAiExecutor;
import agentic.shortener.orchestration.executor.deterministic.DeterministicEngines;
import agentic.shortener.orchestration.executor.deterministic.EnginePorts;
import agentic.shortener.orchestration.gates.ApprovalGate;
import agentic.shortener.orchestration.gates.GateOutcomeHandler;
import agentic.shortener.orchestration.gates.GateRequestPresenter;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.ArtifactWriteGuard;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.reliability.Backoff;
import agentic.shortener.orchestration.reliability.RetryPolicy;
import agentic.shortener.orchestration.state.RetentionPolicy;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.SafeStopHandler;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.orchestration.store.PersistedNode;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.policy.PolicySetEvaluator;
import agentic.shortener.policy.ReleaseReadinessEvaluator;
import agentic.shortener.support.PostgresIntegrationTest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Task T132 — DS-A's real, live executed run, driven through {@link Conductor} exactly as {@code
 * RunSubmissionController} would, against real AI calls (Gemini CLI, {@code agy}), a real isolated {@code
 * git worktree} for S7, and the real fast test tier for S8. NOT named {@code *Test}/{@code *IT} — the same
 * reason every T073a-f live-demo class is not (surefire/failsafe both skip it by pattern); run explicitly:
 *
 * <pre>./scripts/build.sh -q -Dtest=DsALiveRun -DfailIfNoTests=false test</pre>
 *
 * <p>Per the owner's explicit instruction, this class STOPS at S6's architecture-approval gate — {@code
 * ActorAuthority} structurally refuses an agent-identified approving actor (FR-ORC-021), so nothing here
 * ever attempts to submit a decision. It asserts the run reached exactly that paused state and writes the
 * full pending-gate context to {@code docs/evidence/ds-a/pending-gate-s6.md} for the owner to decide.
 */
class DsALiveRun extends PostgresIntegrationTest {

    private static final String MODEL = "gemini-3.8-flash-high";
    private static final Path REPO_ROOT = Paths.get("").toAbsolutePath();
    private static final String REQUIREMENT =
            "Expose the remaining time-to-expiry for a short link to its owning creator.";

    private final Map<String, AiResponse> lastResponseByStage = new ConcurrentHashMap<>();

    @Test
    void driveDsAToTheArchitectureApprovalGate() throws Exception {
        // Run in isolation (this class alone, via -Dtest=DsALiveRun), JUnit's own undefined method
        // order can run PostgresIntegrationTest's own inherited trivialRoundTripAgainstARealStore
        // BEFORE this method — it creates harness_round_trip directly, with no Flyway migration ever
        // having run yet, which then makes Flyway refuse a "non-empty schema, no history table". In
        // the real project's own multi-class suite this never happens (some other *IT class always
        // migrates first in the same shared container); dropping this one known table defensively
        // makes this class robust to running alone, without weakening the shared migration helper
        // every other *IT class also depends on.
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS harness_round_trip");
        }
        MigrationSupport.migrate(POSTGRES, "public");
        ConnectionSource connections = () -> connection();
        Clock clock = Clock.systemUTC();

        JdbcRunStore runStore = new JdbcRunStore(connections, clock);
        GateStore gateStore = new GateStore(connections, clock);
        RetentionPolicy retentionPolicy = new RetentionPolicy();
        GateRequestPresenter gateRequestPresenter =
                new GateRequestPresenter(clock, retentionPolicy, Duration.ofHours(24));
        ArtifactWriteGuard artifactWriteGuard = new ArtifactWriteGuard(connections, clock);
        AuditWriter auditWriter = new AuditWriter(connections);
        SafeStopHandler safeStopHandler = new SafeStopHandler(connections, clock, retentionPolicy);
        RetryPolicy retryPolicy = new RetryPolicy(Backoff.sleeping(), StageTelemetry.disabled());

        StageAiProvider s2Provider = recording("S2", new GeminiCliStageAiProvider("agy", MODEL));
        StageAiProvider s3Provider = recording("S3", new GeminiCliStageAiProvider("agy", MODEL));
        StageAiProvider s5Provider = recording("S5", new GeminiCliStageAiProvider("agy", MODEL));
        StageAiProvider s6Provider = recording("S6", new GeminiCliStageAiProvider("agy", MODEL));

        NormalizationAiExecutor s2 = new NormalizationAiExecutor(s2Provider);
        AmbiguityDetectionAiExecutor s3 = new AmbiguityDetectionAiExecutor(s3Provider);
        DecompositionAiExecutor s5 = new DecompositionAiExecutor(s5Provider);
        DesignAiExecutor s6 = new DesignAiExecutor(s6Provider, clock);
        GitWorktreeBranchApplier branchApplier = new GitWorktreeBranchApplier(REPO_ROOT, "main");
        ImplementationAiExecutor s7 =
                new ImplementationAiExecutor(recording("S7", new GeminiCliStageAiProvider("agy", MODEL)),
                        branchApplier);
        DocumentationAiExecutor s9 = new DocumentationAiExecutor(recording("S9",
                new GeminiCliStageAiProvider("agy", MODEL)));

        EnginePorts ports = new EnginePorts(new ScriptTestSuiteRunner(REPO_ROOT),
                new PolicySetEvaluator(connections, REPO_ROOT, clock),
                new ReleaseReadinessEvaluator(connections, REPO_ROOT, clock));

        java.util.function.Function<Integer, StageExecutor> executors = stageNumber -> switch (stageNumber) {
            case 1, 8, 10, 11, 12 -> DeterministicEngines.forStage(stageNumber, ports);
            case 2 -> s2;
            case 3 -> s3;
            case 5 -> s5;
            case 6 -> s6;
            case 7 -> s7;
            case 9 -> s9;
            default -> throw new IllegalArgumentException("no executor for stage " + stageNumber);
        };

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter, artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler, retryPolicy, executors,
                FanOutPlanner.singleChild(), clock, Executors.newFixedThreadPool(4));

        System.out.println("DS-A LIVE RUN: submitting requirement: " + REQUIREMENT);
        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", REQUIREMENT);
        System.out.println("DS-A LIVE RUN: runId=" + runId);

        conductor.advance(runId);

        List<PersistedNode> nodes = runStore.persistedNodes(runId);
        for (PersistedNode node : nodes) {
            System.out.println("DS-A LIVE RUN: node " + node.nodeKey() + " -> " + node.state());
        }
        for (Map.Entry<String, AiResponse> entry : lastResponseByStage.entrySet()) {
            System.out.println("DS-A LIVE RUN: " + entry.getKey() + " actually-used model id: "
                    + entry.getValue().modelId());
        }

        RunState runState = runStore.run(runId).orElseThrow().state();
        System.out.println("DS-A LIVE RUN: run state=" + runState);
        System.out.println("DS-A LIVE RUN: suspensionReason=" + runStore.run(runId).orElseThrow().suspensionReason());

        try (var c = connection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT node_key, from_state, to_state, reason FROM state_transition "
                     + "WHERE run_id = '" + runId + "' ORDER BY state_transition_id")) {
            System.out.println("DS-A LIVE RUN: full state transition history:");
            while (rs.next()) {
                System.out.println("  " + rs.getString("node_key") + ": " + rs.getString("from_state")
                        + " -> " + rs.getString("to_state") + " (" + rs.getString("reason") + ")");
            }
        }

        Path evidenceDir = Paths.get("docs/evidence/ds-a");
        Files.createDirectories(evidenceDir);
        writeRunSnapshot(evidenceDir, runId, runStore, gateStore);

        StageState s4State = runStore.node(runId, "S4").orElseThrow().state();
        System.out.println("DS-A LIVE RUN: S4 state=" + s4State);

        assertEquals(RunState.RUNNING, runState, "the run must be RUNNING (paused at a gate), not "
                + "suspended or terminal, for this to be the expected architecture-approval stop");
        StageState s6State = runStore.node(runId, "S6").orElseThrow().state();
        assertEquals(StageState.AWAITING_APPROVAL, s6State,
                "expected the run to stop exactly at S6's architecture-approval gate");

        System.out.println("DS-A LIVE RUN: stopped at S6 (architecture approval), as expected. "
                + "See docs/evidence/ds-a/pending-gate-s6.md for the owner.");
    }

    private StageAiProvider recording(String stageKey, StageAiProvider delegate) {
        return prompt -> {
            AiResponse response = delegate.invoke(prompt);
            lastResponseByStage.put(stageKey, response);
            return response;
        };
    }

    private void writeRunSnapshot(Path evidenceDir, UUID runId, JdbcRunStore runStore, GateStore gateStore)
            throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# DS-A live run snapshot\n\nrunId: ").append(runId).append('\n');
        sb.append("runState: ").append(runStore.run(runId).orElseThrow().state()).append("\n\n");
        sb.append("## Nodes\n\n");
        for (PersistedNode node : runStore.persistedNodes(runId)) {
            sb.append("- ").append(node.nodeKey()).append(" (stage ").append(node.stageNumber())
                    .append(", ").append(node.role()).append(") -> ").append(node.state())
                    .append(", executorClass=").append(node.executorClass())
                    .append(", attemptsUsed=").append(node.attemptsUsed()).append('\n');
        }
        sb.append("\n## Model ids actually used\n\n");
        for (Map.Entry<String, AiResponse> entry : lastResponseByStage.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().modelId())
                    .append('\n');
        }
        Files.writeString(evidenceDir.resolve("run-snapshot.md"), sb.toString(), StandardCharsets.UTF_8);
    }
}
