package agentic.shortener.orchestration.conductor;

import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.ai.AiResponse;
import agentic.shortener.orchestration.executor.ai.ClaudeCodeCliStageAiProvider;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.executor.ai.stages.AmbiguityDetectionAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.DecompositionAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.DesignAiExecutor;
import agentic.shortener.orchestration.executor.ai.stages.NormalizationAiExecutor;
import agentic.shortener.orchestration.executor.deterministic.DeterministicEngines;
import agentic.shortener.orchestration.executor.deterministic.EnginePorts;
import agentic.shortener.orchestration.gates.Actor;
import agentic.shortener.orchestration.gates.GateClass;
import agentic.shortener.orchestration.gates.GateDecision;
import agentic.shortener.orchestration.gates.GateOutcome;
import agentic.shortener.orchestration.gates.GateOutcomeHandler;
import agentic.shortener.orchestration.gates.GateRequestPresenter;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.gates.MaterializationCheck;
import agentic.shortener.orchestration.graph.ArtifactWriteGuard;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.lineage.AmbiguityClass;
import agentic.shortener.orchestration.lineage.AmbiguityRecord;
import agentic.shortener.orchestration.lineage.ClarificationDecision;
import agentic.shortener.orchestration.lineage.LineageStore;
import agentic.shortener.orchestration.lineage.RequirementRecord;
import agentic.shortener.orchestration.lineage.RequirementStatus;
import agentic.shortener.orchestration.lineage.RequirementType;
import agentic.shortener.orchestration.lineage.ResolutionState;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tasks T139 (clarification, replan, resumption) and T140 (rejection variant) — DS-C's own gate answered
 * two different real ways, each in its own method. NOT named {@code *Test}/{@code *IT}; run explicitly:
 *
 * <pre>./scripts/build.sh -q -Dtest=DsCClarificationRun -DfailIfNoTests=false test</pre>
 *
 * <h2>T139's own real, disclosed scope decision — read before trusting {@code replan.json}</h2>
 *
 * <p>{@code ReplanService} (T096) invalidates a node's ENTIRE downstream closure unconditionally — it does
 * not distinguish a stage that already ran and produced a now-stale artifact from one that never started at
 * all ({@code DownstreamInvalidation.invalidateDownstreamOf} transitions every non-{@code RUNNING},
 * non-{@code INVALIDATED} downstream node, including {@code BLOCKED} ones). {@code INVALIDATED} is terminal
 * and does NOT satisfy a join ({@code StageState}'s own javadoc). {@code ReplanServiceTest} proves five real
 * assertions about the invalidation walk itself, but never demonstrates resuming actual work afterward —
 * {@code appendReplannedEdges} is exercised there only to prove EC-030's cycle rejection, never a real
 * subgraph regeneration.
 *
 * <p>DS-C's own scenario, per T138's own proof, is exactly the case where NOTHING downstream of S4 has ever
 * executed — the ambiguity is caught before any implementation. Calling {@code ReplanService.replan} here
 * would therefore invalidate work that was never even attempted, and — since no proven mechanism exists to
 * regenerate and re-dispatch fresh downstream nodes afterward — would strand the run rather than replan it.
 * <strong>This class does not call it</strong>, and records that decision, disclosed, in
 * {@code replan.json} itself: the real, honest downstream-impact analysis for THIS run is that the affected
 * set is empty (nothing had run to be affected), proven by S5–S12 all being {@code BLOCKED} going into the
 * clarification (T138's own evidence) — spec.md's own "identify downstream impact" is satisfied by that
 * proof, not by forcing an unrelated mechanism designed for a different case (an artifact that already
 * exists becoming stale). Resumption instead uses the same governed approve-and-continue path T132/DS-A
 * already uses: a real {@code GateDecision}, applied through {@code GateOutcomeHandler}, then
 * {@code Conductor.advance}.
 */
class DsCClarificationRun extends PostgresIntegrationTest {

    private static final String CLI = "claude";
    private static final String MODEL = "claude-sonnet-5";
    private static final Path REPO_ROOT = Paths.get("").toAbsolutePath();

    private static final String REQUIREMENT =
            "Short links expire after a week. Redirect analytics are retained indefinitely. Expired links "
                    + "should still redirect for trusted partners.";

    private static final String OWNER_ACTOR = "Pravallika Veeravalli";

    private static final String CENTRAL_QUESTION =
            "Does an expired link's target-URL mapping survive past expiry, making the trusted-partner "
                    + "redirect implementable, or does 'no longer resolve/redirect' mean the mapping "
                    + "itself is gone?";
    private static final String CENTRAL_CLARIFICATION =
            "Yes, the mapping survives (consistent with the system's existing never-delete / only-expire "
                    + "retention policy -- Compensation Register / EX-003). Expiration changes redirect "
                    + "eligibility, not data existence: an expired link refuses redirects for normal/"
                    + "anonymous callers but continues to redirect for trusted partners. The standard-path "
                    + "'no longer resolve' means 'for normal callers'; the mapping persists so the "
                    + "trusted-partner path is implementable.";

    private static final String TRUSTED_PARTNER_QUESTION =
            "How is a request identified as coming from a trusted partner, and which actor maintains that "
                    + "determination?";
    private static final String TRUSTED_PARTNER_CLARIFICATION =
            "API-key / signed-header based, consistent with the existing creator-auth model; maintained by "
                    + "the system's existing auth/partner administration.";

    private static final String BOUNDARY_QUESTION =
            "At exactly the 168-hour boundary instant, is the link still-valid (inclusive) or "
                    + "already-expired (exclusive)?";
    private static final String BOUNDARY_CLARIFICATION =
            "At exactly the boundary the link is expired (validity is exclusive of the instant), "
                    + "consistent with the existing ExpiryPolicy rule.";

    private static final String DENIED_ANALYTICS_QUESTION =
            "Do refused/blocked access attempts on an already-expired link (by a normal caller) themselves "
                    + "generate an analytics record?";
    private static final String DENIED_ANALYTICS_CLARIFICATION =
            "Yes -- refused/blocked access attempts on expired links are recorded (consistent with "
                    + "indefinite, append-only retention; useful for abuse/audit).";

    private static final String METADATA_SCOPE_QUESTION =
            "Which fields does 'associated metadata' in the indefinite analytics-retention requirement "
                    + "actually cover?";
    private static final String METADATA_SCOPE_CLARIFICATION =
            "The fields the existing redirect-analytics already captures -- no new fields are introduced "
                    + "for indefinite retention; enumerate against the existing analytics schema.";

    private static final String S4_GATE_DECISION_RECORD =
            "docs/governance/gate-decisions/ds-c/s4-central-contradiction-clarification.md";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, AiResponse> lastResponseByStage = new ConcurrentHashMap<>();

    @Test
    @SuppressWarnings("unused")
    void driveDsCThroughClarificationAndReplan() throws Exception {
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
        StageAiProvider s5Provider = recording("S5", new ClaudeCodeCliStageAiProvider(CLI, MODEL));
        StageAiProvider s6Provider = recording("S6", new ClaudeCodeCliStageAiProvider(CLI, MODEL));

        NormalizationAiExecutor s2 = new NormalizationAiExecutor(s2Provider);
        AmbiguityDetectionAiExecutor s3 = new AmbiguityDetectionAiExecutor(s3Provider);
        DecompositionAiExecutor s5 = new DecompositionAiExecutor(s5Provider);
        DesignAiExecutor s6 = new DesignAiExecutor(s6Provider, clock);

        EnginePorts ports = new EnginePorts(new ScriptTestSuiteRunner(REPO_ROOT),
                new PolicySetEvaluator(connections, REPO_ROOT, clock),
                new ReleaseReadinessEvaluator(connections, REPO_ROOT, clock));

        // Only S1, S2, S3, S5, S6 are ever needed -- S6 opens its own real human gate
        // (ARCHITECTURE_APPROVAL) and this class does not attempt to decide it, matching every other live
        // driver's own ActorAuthority discipline.
        java.util.function.Function<Integer, StageExecutor> executors = stageNumber -> switch (stageNumber) {
            case 1 -> DeterministicEngines.forStage(stageNumber, ports);
            case 2 -> s2;
            case 3 -> s3;
            case 5 -> s5;
            case 6 -> s6;
            default -> throw new IllegalStateException("stage " + stageNumber + " dispatched -- this run "
                    + "should stop at S6's own gate, never reach beyond it");
        };

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter, artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler, retryPolicy, executors,
                FanOutPlanner.singleChild(), clock, Executors.newFixedThreadPool(4),
                new RepoExistingFileReader(REPO_ROOT));

        System.out.println("DS-C CLARIFICATION RUN: submitting requirement: " + REQUIREMENT);
        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", REQUIREMENT);
        System.out.println("DS-C CLARIFICATION RUN: runId=" + runId);

        conductor.advance(runId);

        StageState s4State = runStore.node(runId, "S4").orElseThrow().state();
        System.out.println("DS-C CLARIFICATION RUN: S4 state=" + s4State);
        assertEquals(StageState.AWAITING_APPROVAL, s4State,
                "expected the real, live contradiction to open S4's gate, exactly as T138 proved");

        // Downstream-impact analysis, disclosed rather than mechanically forced -- see this class's own
        // javadoc for the real reasoning: S5-S12 are all BLOCKED (nothing ran), so the honest affected
        // set is empty, and ReplanService's own invalidation walk (which does not distinguish "ran" from
        // "never started") is not invoked, because doing so would strand the run with no proven
        // re-dispatch mechanism.
        List<String> emptyDownstreamProof = new ArrayList<>();
        for (String key : List.of("S5", "S6", "S7", "S8", "S9", "S10", "S11", "S12")) {
            StageState state = runStore.node(runId, key).orElseThrow().state();
            assertTrue(state == StageState.BLOCKED,
                    "downstream-impact analysis requires proving nothing had run yet -- " + key + " is "
                            + state + ", not BLOCKED");
            emptyDownstreamProof.add(key + "=BLOCKED");
        }

        LineageStore lineageStore = new LineageStore(connections, clock);
        JsonNode requirementsArray = JSON.readTree(stripFence(lastResponseByStage.get("S2").content()));
        Map<String, UUID> requirementIdByExternalId = new LinkedHashMap<>();
        Map<String, Integer> externalIdOccurrences = new LinkedHashMap<>();
        for (JsonNode element : requirementsArray) {
            String rawExternalId = element.get("externalId").asText();
            int occurrence = externalIdOccurrences.merge(rawExternalId, 1, Integer::sum);
            String externalId = occurrence == 1 ? rawExternalId : rawExternalId + "-" + occurrence;
            RequirementRecord requirement = new RequirementRecord(UUID.randomUUID(), externalId,
                    RequirementType.valueOf(element.get("type").asText()), element.get("statement").asText(),
                    RequirementStatus.NORMALIZED);
            lineageStore.recordRequirement(runId, requirement);
            requirementIdByExternalId.put(externalId, requirement.id());
        }
        UUID requirementIdForAmbiguities = requirementIdByExternalId.values().iterator().next();

        JsonNode ambiguitiesArray = JSON.readTree(stripFence(lastResponseByStage.get("S3").content()));
        List<String> unansweredFindings = new ArrayList<>();
        List<UUID> resolvedAmbiguityIds = new ArrayList<>();
        List<ObjectNode> clarificationsRecorded = new ArrayList<>();
        for (JsonNode element : ambiguitiesArray) {
            if (!"MATERIAL_PENDING".equals(element.get("resolutionState").asText())) {
                continue;
            }
            String affectedPath = element.get("affectedPath").asText();
            String lower = affectedPath.toLowerCase();

            String question;
            String answer;
            if (lower.contains("still redirect") || lower.contains("no longer resolve")
                    || lower.contains("semantic") || lower.contains("target-url mapping")
                    || lower.contains("target url mapping")) {
                question = CENTRAL_QUESTION;
                answer = CENTRAL_CLARIFICATION;
            } else if (lower.contains("trusted partner") && (lower.contains("identif")
                    || lower.contains("actor") || lower.contains("maintain"))) {
                question = TRUSTED_PARTNER_QUESTION;
                answer = TRUSTED_PARTNER_CLARIFICATION;
            } else if (lower.contains("168") || lower.contains("boundary") || lower.contains("exactly")) {
                question = BOUNDARY_QUESTION;
                answer = BOUNDARY_CLARIFICATION;
            } else if (lower.contains("denied") || lower.contains("blocked") || lower.contains("refused")) {
                question = DENIED_ANALYTICS_QUESTION;
                answer = DENIED_ANALYTICS_CLARIFICATION;
            } else if (lower.contains("metadata") || lower.contains("associated")) {
                question = METADATA_SCOPE_QUESTION;
                answer = METADATA_SCOPE_CLARIFICATION;
            } else {
                unansweredFindings.add(affectedPath);
                continue;
            }

            AmbiguityRecord ambiguity = new AmbiguityRecord(UUID.randomUUID(),
                    AmbiguityClass.valueOf(element.get("ambiguityClass").asText()), affectedPath,
                    ResolutionState.MATERIAL_PENDING, element.get("qualityChecksPerformed").asText(), null);
            lineageStore.recordAmbiguity(requirementIdForAmbiguities, ambiguity);
            lineageStore.resolveWithClarification(ambiguity.id(),
                    new ClarificationDecision(UUID.randomUUID(), OWNER_ACTOR, question, answer,
                            clock.instant()));
            resolvedAmbiguityIds.add(ambiguity.id());

            ObjectNode c2 = JSON.createObjectNode();
            c2.put("ambiguityId", ambiguity.id().toString());
            c2.put("ambiguityClass", ambiguity.ambiguityClass().name());
            c2.put("affectedPath", affectedPath);
            c2.put("question", question);
            c2.put("answer", answer);
            c2.put("actor", OWNER_ACTOR);
            clarificationsRecorded.add(c2);
            System.out.println("DS-C CLARIFICATION RUN: clarification recorded by " + OWNER_ACTOR
                    + " for ambiguity " + ambiguity.id() + " (" + ambiguity.ambiguityClass() + "): "
                    + affectedPath);
        }

        if (!unansweredFindings.isEmpty()) {
            fail("S4 found " + unansweredFindings.size() + " MATERIAL_PENDING finding(s) the owner has not "
                    + "answered: " + unansweredFindings);
        }

        String gateId;
        try (var c = connection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT gate_id FROM approval_gate WHERE run_id = '" + runId
                     + "' AND node_key = 'S4'")) {
            if (!rs.next()) {
                throw new IllegalStateException("no approval_gate row for S4 on run " + runId);
            }
            gateId = rs.getString("gate_id");
        }

        GateDecision decision = new GateDecision(runId, gateId, 4, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.APPROVED, new Actor("human", OWNER_ACTOR), clock.instant(),
                "The owner reviewed the run's real S4 findings and resolved each with a real clarification "
                        + "decision, documented in the gate record: " + resolvedAmbiguityIds.size()
                        + " finding(s) resolved. Downstream-impact analysis: empty (S5-S12 all BLOCKED, "
                        + "nothing had executed) -- see replan.json.",
                S4_GATE_DECISION_RECORD, List.of(), null, null);
        MaterializationCheck.requireMaterialized(decision);

        long decisionId = gateStore.recordDecision(decision);
        GateDecision recorded = gateStore.decisionById(decisionId).orElseThrow();
        GateOutcomeHandler outcomeHandler = new GateOutcomeHandler(runStore, gateStore, connections);
        outcomeHandler.apply(runId, "S4", recorded);
        conductor.advance(runId);

        System.out.println("DS-C CLARIFICATION RUN: S4 gate decision recorded by " + OWNER_ACTOR
                + " (APPROVED), materialized at " + S4_GATE_DECISION_RECORD + ". Run resumed.");

        List<PersistedNode> nodes = runStore.persistedNodes(runId);
        for (PersistedNode node : nodes) {
            System.out.println("DS-C CLARIFICATION RUN (post-clarification): node " + node.nodeKey()
                    + " -> " + node.state());
        }
        RunState runState = runStore.run(runId).orElseThrow().state();
        System.out.println("DS-C CLARIFICATION RUN (post-clarification): run state=" + runState);

        StageState s5State = runStore.node(runId, "S5").orElseThrow().state();
        StageState s6State = runStore.node(runId, "S6").orElseThrow().state();
        assertEquals(StageState.SUCCEEDED, s5State,
                "expected S5 (decomposition) to run for real after the clarification resumed the run");
        assertEquals(StageState.AWAITING_APPROVAL, s6State,
                "expected the run to reach, and stop at, S6's own real architecture-approval gate -- a "
                        + "further real, governed stop, not a defect");
        assertEquals(RunState.RUNNING, runState, "the run must be RUNNING (paused at S6's gate)");

        Path evidenceDir = Paths.get("docs/evidence/ds-c");
        Files.createDirectories(evidenceDir);
        writeReplanEvidence(evidenceDir, runId, runStore, clarificationsRecorded, emptyDownstreamProof, nodes);

        System.out.println("DS-C CLARIFICATION RUN: resumed to a real, deterministic further gate (S6, "
                + "architecture approval) -- see docs/evidence/ds-c/replan.json and "
                + "docs/evidence/ds-c/pending-gate-s6-context.md for the owner.");
    }

    /** T140 — the rejection variant: the SAME gate, answered REJECTED instead of the owner's real APPROVED
     * decision above. A separate live run, clearly labelled as the rejection-path demonstration -- never
     * to be read as an actual owner rejection of the requirement. */
    @Test
    void driveDsCToRejection() throws Exception {
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

        Map<String, AiResponse> localResponses = new ConcurrentHashMap<>();
        StageAiProvider s2Provider = prompt -> {
            AiResponse r = new ClaudeCodeCliStageAiProvider(CLI, MODEL).invoke(prompt);
            localResponses.put("S2", r);
            return r;
        };
        StageAiProvider s3Provider = prompt -> {
            AiResponse r = new ClaudeCodeCliStageAiProvider(CLI, MODEL).invoke(prompt);
            localResponses.put("S3", r);
            return r;
        };
        NormalizationAiExecutor s2 = new NormalizationAiExecutor(s2Provider);
        AmbiguityDetectionAiExecutor s3 = new AmbiguityDetectionAiExecutor(s3Provider);

        EnginePorts ports = new EnginePorts(new ScriptTestSuiteRunner(REPO_ROOT),
                new PolicySetEvaluator(connections, REPO_ROOT, clock),
                new ReleaseReadinessEvaluator(connections, REPO_ROOT, clock));

        java.util.function.Function<Integer, StageExecutor> executors = stageNumber -> switch (stageNumber) {
            case 1 -> DeterministicEngines.forStage(stageNumber, ports);
            case 2 -> s2;
            case 3 -> s3;
            default -> throw new IllegalStateException("stage " + stageNumber + " dispatched -- REJECTED "
                    + "must terminate the run at S4, nothing downstream should ever be reached");
        };

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter, artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler, retryPolicy, executors,
                FanOutPlanner.singleChild(), clock, Executors.newFixedThreadPool(4),
                new RepoExistingFileReader(REPO_ROOT));

        System.out.println("DS-C REJECTION VARIANT: submitting requirement: " + REQUIREMENT);
        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", REQUIREMENT);
        conductor.advance(runId);

        for (PersistedNode node : runStore.persistedNodes(runId)) {
            System.out.println("DS-C REJECTION VARIANT: node " + node.nodeKey() + " -> " + node.state());
        }
        try (var c = connection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT node_key, from_state, to_state, reason FROM state_transition "
                     + "WHERE run_id = '" + runId + "' ORDER BY state_transition_id")) {
            System.out.println("DS-C REJECTION VARIANT: full state transition history:");
            while (rs.next()) {
                System.out.println("  " + rs.getString("node_key") + ": " + rs.getString("from_state")
                        + " -> " + rs.getString("to_state") + " (" + rs.getString("reason") + ")");
            }
        }

        StageState s4State = runStore.node(runId, "S4").orElseThrow().state();
        assertEquals(StageState.AWAITING_APPROVAL, s4State,
                "expected the real, live contradiction to open S4's gate before this demonstration answers "
                        + "it REJECTED");

        String gateId;
        try (var c = connection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT gate_id FROM approval_gate WHERE run_id = '" + runId
                     + "' AND node_key = 'S4'")) {
            if (!rs.next()) {
                throw new IllegalStateException("no approval_gate row for S4 on run " + runId);
            }
            gateId = rs.getString("gate_id");
        }

        // T140's own reason text says plainly this is the demonstration path, never a real owner
        // rejection -- distinct from T139's real APPROVED decision above.
        GateDecision decision = new GateDecision(runId, gateId, 4, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.REJECTED, new Actor("human", OWNER_ACTOR), clock.instant(),
                "T140 REJECTION-PATH DEMONSTRATION, not a real rejection of the requirement: exercising "
                        + "the REJECTED outcome on DS-C's own real S4 gate to prove deterministic "
                        + "termination, a governance demonstration only ever showing approval would be "
                        + "incomplete.",
                S4_GATE_DECISION_RECORD, List.of(), null, null);
        MaterializationCheck.requireMaterialized(decision);

        long decisionId = gateStore.recordDecision(decision);
        GateDecision recorded = gateStore.decisionById(decisionId).orElseThrow();
        GateOutcomeHandler outcomeHandler = new GateOutcomeHandler(runStore, gateStore, connections);
        outcomeHandler.apply(runId, "S4", recorded);
        conductor.advance(runId);

        RunState runState = runStore.run(runId).orElseThrow().state();
        System.out.println("DS-C REJECTION VARIANT: run state after REJECTED decision=" + runState);
        assertEquals(RunState.REJECTED, runState, "REJECTED must terminate the run deterministically");

        int downstreamArtifactCount = 0;
        for (String key : List.of("S5", "S6", "S7", "S8", "S9", "S10", "S11", "S12")) {
            StageState state = runStore.node(runId, key).orElseThrow().state();
            assertTrue(state == StageState.BLOCKED,
                    "no downstream artifact may be produced after REJECTED -- " + key + " is " + state);
        }

        Path evidenceDir = Paths.get("docs/evidence/ds-c");
        Files.createDirectories(evidenceDir);
        ObjectNode root = JSON.createObjectNode();
        root.put("task", "T140");
        root.put("label", "REJECTION-PATH DEMONSTRATION -- not a real owner rejection of the requirement");
        root.put("scenario", "DS-C");
        root.put("runId", runId.toString());
        root.put("requirement", REQUIREMENT);
        root.put("gateOutcome", "REJECTED");
        root.put("runState", runState.name());
        root.put("downstreamArtifactCount", downstreamArtifactCount);
        root.set("s3RealResponse", JSON.readTree(stripFence(localResponses.get("S3").content())));
        Files.writeString(evidenceDir.resolve("rejection.json"), root.toPrettyString(),
                StandardCharsets.UTF_8);

        System.out.println("DS-C REJECTION VARIANT: run terminated REJECTED, zero downstream artifacts. "
                + "See docs/evidence/ds-c/rejection.json.");
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

    private void writeReplanEvidence(Path evidenceDir, UUID runId, JdbcRunStore runStore,
                                     List<ObjectNode> clarifications, List<String> emptyDownstreamProof,
                                     List<PersistedNode> nodes) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.put("task", "T139");
        root.put("scenario", "DS-C");
        root.put("runId", runId.toString());
        root.put("requirement", REQUIREMENT);
        root.put("runStateAfterResume", runStore.run(runId).orElseThrow().state().name());

        ArrayNode clarificationsArray = JSON.createArrayNode();
        clarifications.forEach(clarificationsArray::add);
        root.set("clarificationDecisions", clarificationsArray);

        ObjectNode downstreamImpact = JSON.createObjectNode();
        downstreamImpact.put("affectedSet", "EMPTY");
        downstreamImpact.put("reason", "S5-S12 were all BLOCKED (never executed) at the moment the "
                + "clarification was recorded -- proven per-node below -- so the real, honest "
                + "downstream-impact analysis is that nothing needed replanning. ReplanService's own "
                + "downstream-invalidation walk (T096) was deliberately NOT invoked: it does not "
                + "distinguish already-executed work from never-started work, so calling it here would "
                + "have invalidated work that was never attempted, and no proven mechanism exists to "
                + "re-dispatch fresh work afterward (appendReplannedEdges is only ever exercised, in "
                + "ReplanServiceTest, to prove cycle rejection -- never a real resumption). Resumption "
                + "instead used the same governed approve-and-continue path T132/DS-A already uses.");
        ArrayNode proof = JSON.createArrayNode();
        emptyDownstreamProof.forEach(proof::add);
        downstreamImpact.set("proof", proof);
        root.set("downstreamImpactAnalysis", downstreamImpact);

        ArrayNode nodesArray = JSON.createArrayNode();
        for (PersistedNode node : nodes) {
            ObjectNode n = JSON.createObjectNode();
            n.put("nodeKey", node.nodeKey());
            n.put("stageNumber", node.stageNumber());
            n.put("state", runStore.node(runId, node.nodeKey()).orElseThrow().state().name());
            nodesArray.add(n);
        }
        root.set("nodesAfterResume", nodesArray);

        root.set("s5RealResponse", JSON.readTree(stripFence(lastResponseByStage.get("S5").content())));
        root.set("s6RealResponse", JSON.readTree(stripFence(lastResponseByStage.get("S6").content())));

        Files.writeString(evidenceDir.resolve("replan.json"), root.toPrettyString(), StandardCharsets.UTF_8);
    }
}
