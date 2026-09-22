package agentic.shortener.orchestration.conductor;

import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.ClaudeCodeCliStageAiProvider;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.executor.ai.stages.AmbiguityDetectionAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.NormalizationAiExecutor;
import agentic.shortener.orchestration.executor.deterministic.DeterministicEngines;
import agentic.shortener.orchestration.executor.deterministic.EnginePorts;
import agentic.shortener.orchestration.gates.ApprovalGate;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T138 — DS-C's real, live executed run: ambiguity detection and the silence/no-unsafe-implementation
 * path. Driven through {@link Conductor} exactly as {@code RunSubmissionController} would, against real AI
 * calls (the Claude Code CLI). NOT named {@code *Test}/{@code *IT}; run explicitly:
 *
 * <pre>./scripts/build.sh -q -Dtest=DsCLiveRun -DfailIfNoTests=false test</pre>
 *
 * <p>DS-C's own canonical contradiction (spec.md's own demonstration text, verbatim, already proven live by
 * the CR-049/CR-052 compensating checks to reliably classify {@code MATERIAL_PENDING} under the calibrated
 * and sharpened S3): "Short links expire after a week" / "Redirect analytics are retained indefinitely" /
 * "Expired links should still redirect for trusted partners" — no two clauses bound to the same field, so no
 * structural rule alone catches it, which is exactly why {@code SEMANTIC_CONTRADICTION} exists.
 *
 * <p><strong>This class STOPS at S4's clarification gate and holds for the owner</strong> — {@code
 * ActorAuthority} structurally refuses an agent-identified approving actor (FR-ORC-021), and T139's own
 * clarification/replan is a separate task requiring the owner's real decision first, not this agent's. It
 * writes T138's own required artifact, {@code docs/evidence/ds-c/silence.json}, proving three things
 * machine-readably: the ambiguity was detected before any implementation, no unsafe implementation was
 * produced (S5 onward stayed {@code BLOCKED}), and the suspension itself was recorded.
 */
class DsCLiveRun extends PostgresIntegrationTest {

    private static final String CLI = "claude";
    private static final String MODEL = "claude-sonnet-5";
    private static final Path REPO_ROOT = Paths.get("").toAbsolutePath();

    // spec.md's own canonical DS-C demonstration text, verbatim -- the same input CR-049's and CR-052's
    // own compensating checks used, both live, both real: 6-7 findings, always including the core
    // SEMANTIC_CONTRADICTION as MATERIAL_PENDING.
    private static final String REQUIREMENT =
            "Short links expire after a week. Redirect analytics are retained indefinitely. Expired links "
                    + "should still redirect for trusted partners.";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, AiResponse> lastResponseByStage = new ConcurrentHashMap<>();

    @Test
    void driveDsCToTheClarificationGate() throws Exception {
        // Same defensive drop as DsALiveRun -- robust to running this class alone via -Dtest=DsCLiveRun.
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

        StageAiProvider s2Provider = recording("S2", new ClaudeCodeCliStageAiProvider(CLI, MODEL));
        StageAiProvider s3Provider = recording("S3", new ClaudeCodeCliStageAiProvider(CLI, MODEL));
        NormalizationAiExecutor s2 = new NormalizationAiExecutor(s2Provider);
        AmbiguityDetectionAiExecutor s3 = new AmbiguityDetectionAiExecutor(s3Provider);

        EnginePorts ports = new EnginePorts(new ScriptTestSuiteRunner(REPO_ROOT),
                new PolicySetEvaluator(connections, REPO_ROOT, clock),
                new ReleaseReadinessEvaluator(connections, REPO_ROOT, clock));

        // Only S1-S4 can ever be dispatched on this run -- S4's gate opens on the real, expected
        // SEMANTIC_CONTRADICTION and everything downstream stays BLOCKED, so no executor beyond S3 is
        // ever invoked. Naming that explicitly (rather than wiring S5-S12 unused) IS this task's own
        // "unsafe implementation prevented" property, made structural in the test itself.
        java.util.function.Function<Integer, StageExecutor> executors = stageNumber -> switch (stageNumber) {
            case 1 -> DeterministicEngines.forStage(stageNumber, ports);
            case 2 -> s2;
            case 3 -> s3;
            default -> throw new IllegalStateException("stage " + stageNumber + " was dispatched, but "
                    + "DS-C's own negative acceptance is that S4's gate prevents ANY downstream stage from "
                    + "running before human clarification -- this executor deliberately supports only "
                    + "S1-S3, so reaching here is itself a test failure, not a missing wiring");
        };

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter, artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler, retryPolicy, executors,
                FanOutPlanner.singleChild(), clock, Executors.newFixedThreadPool(4));

        System.out.println("DS-C LIVE RUN: submitting requirement: " + REQUIREMENT);
        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", REQUIREMENT);
        System.out.println("DS-C LIVE RUN: runId=" + runId);

        conductor.advance(runId);

        List<PersistedNode> nodes = runStore.persistedNodes(runId);
        for (PersistedNode node : nodes) {
            System.out.println("DS-C LIVE RUN: node " + node.nodeKey() + " -> " + node.state());
        }

        RunState runState = runStore.run(runId).orElseThrow().state();
        System.out.println("DS-C LIVE RUN: run state=" + runState);

        StageState s4State = runStore.node(runId, "S4").orElseThrow().state();
        System.out.println("DS-C LIVE RUN: S4 state=" + s4State);

        // T138's own three-part validation, asserted structurally, not merely printed:
        assertEquals(RunState.RUNNING, runState,
                "the run must be RUNNING (paused at a gate), not suspended or terminal");
        assertEquals(StageState.AWAITING_APPROVAL, s4State,
                "ambiguity detection must have found the real contradiction material and opened S4's gate "
                        + "-- a SKIPPED S4 here would mean the canonical contradiction went undetected, a "
                        + "real regression, not a pass");
        for (String downstream : List.of("S5", "S6", "S7", "S8", "S9", "S10", "S11", "S12")) {
            StageState state = runStore.node(runId, downstream).orElseThrow().state();
            assertTrue(state == StageState.BLOCKED,
                    "DS-C's own negative acceptance: no downstream stage may run before human "
                            + "clarification -- " + downstream + " is " + state + ", not BLOCKED");
        }

        JsonNode ambiguities = JSON.readTree(stripFence(lastResponseByStage.get("S3").content()));
        boolean semanticContradictionFound = false;
        for (JsonNode element : ambiguities) {
            if ("SEMANTIC_CONTRADICTION".equals(element.path("ambiguityClass").asText())
                    && "MATERIAL_PENDING".equals(element.path("resolutionState").asText())) {
                semanticContradictionFound = true;
            }
        }
        assertTrue(semanticContradictionFound, "expected at least one real, live SEMANTIC_CONTRADICTION "
                + "classified MATERIAL_PENDING -- the core finding this scenario exists to prove is "
                + "detected, not merely 'some ambiguity or other'");

        Path evidenceDir = Paths.get("docs/evidence/ds-c");
        Files.createDirectories(evidenceDir);
        writeSilenceEvidence(evidenceDir, runId, runStore, gateStore, nodes);

        System.out.println("DS-C LIVE RUN: ambiguity detected before implementation, unsafe implementation "
                + "prevented (S5 onward BLOCKED), suspension recorded. Held at S4 for the owner -- see "
                + "docs/evidence/ds-c/silence.json. T139 (clarification/replan) is NOT attempted here.");
    }

    private StageAiProvider recording(String stageKey, StageAiProvider delegate) {
        return prompt -> {
            AiResponse response = delegate.invoke(prompt);
            lastResponseByStage.put(stageKey, response);
            return response;
        };
    }

    private static String stripFence(String content) {
        String text = content.strip();
        int fenceStart = text.indexOf("```");
        if (fenceStart < 0) {
            return text;
        }
        int contentStart = fenceStart + 3;
        while (contentStart < text.length() && Character.isLetter(text.charAt(contentStart))) {
            contentStart++;
        }
        int lastFence = text.lastIndexOf("```");
        return lastFence > contentStart ? text.substring(contentStart, lastFence).strip() : text;
    }

    /** T138's own required artifact: {@code docs/evidence/ds-c/silence.json}. */
    private void writeSilenceEvidence(Path evidenceDir, UUID runId, JdbcRunStore runStore, GateStore gateStore,
                                       List<PersistedNode> nodes) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.put("task", "T138");
        root.put("scenario", "DS-C");
        root.put("runId", runId.toString());
        root.put("requirement", REQUIREMENT);
        root.put("runState", runStore.run(runId).orElseThrow().state().name());

        ObjectNode validation = JSON.createObjectNode();
        validation.put("ambiguityDetectedBeforeImplementation", true);
        validation.put("unsafeImplementationPrevented", true);
        validation.put("suspensionRecorded", true);
        root.set("negativeAcceptanceValidated", validation);

        ArrayNode nodesArray = JSON.createArrayNode();
        for (PersistedNode node : nodes) {
            ObjectNode n = JSON.createObjectNode();
            n.put("nodeKey", node.nodeKey());
            n.put("stageNumber", node.stageNumber());
            n.put("state", node.state().name());
            n.put("executorClass", node.executorClass().name());
            nodesArray.add(n);
        }
        root.set("nodes", nodesArray);

        ArrayNode transitions = JSON.createArrayNode();
        try (var c = connection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT node_key, from_state, to_state, reason FROM state_transition "
                     + "WHERE run_id = '" + runId + "' ORDER BY state_transition_id")) {
            while (rs.next()) {
                ObjectNode t = JSON.createObjectNode();
                t.put("nodeKey", rs.getString("node_key"));
                t.put("fromState", rs.getString("from_state"));
                t.put("toState", rs.getString("to_state"));
                t.put("reason", rs.getString("reason"));
                transitions.add(t);
            }
        }
        root.set("stateTransitions", transitions);

        root.set("s3RealResponse", JSON.readTree(stripFence(lastResponseByStage.get("S3").content())));
        root.set("s2RealResponse", JSON.readTree(stripFence(lastResponseByStage.get("S2").content())));

        ArrayNode pendingGate = JSON.createArrayNode();
        for (ApprovalGate gate : List.of(gateStore.pendingGate(runId,
                approvalGateIdFor(runId, "S4")).orElseThrow())) {
            ObjectNode g = JSON.createObjectNode();
            g.put("gateId", gate.gateId());
            g.put("nodeKey", gate.nodeKey());
            g.put("gateClass", gate.gateClass().name());
            g.put("waitDeadline", gate.waitDeadline().toString());
            pendingGate.add(g);
        }
        root.set("pendingS4Gate", pendingGate);

        Files.writeString(evidenceDir.resolve("silence.json"),
                root.toPrettyString(), StandardCharsets.UTF_8);
    }

    private String approvalGateIdFor(UUID runId, String nodeKey) throws Exception {
        try (var c = connection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT gate_id FROM approval_gate WHERE run_id = '" + runId
                     + "' AND node_key = '" + nodeKey + "'")) {
            if (!rs.next()) {
                throw new IllegalStateException("no approval_gate row for " + nodeKey + " on run " + runId);
            }
            return rs.getString("gate_id");
        }
    }
}
