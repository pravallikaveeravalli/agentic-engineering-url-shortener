package agentic.shortener.orchestration.conductor;

import agentic.shortener.audit.AuditEvent;
import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.CorrelationContext;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.gates.ApprovalGate;
import agentic.shortener.orchestration.gates.GateClass;
import agentic.shortener.orchestration.gates.GateRequestPresenter;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.ArtifactWriteGuard;
import agentic.shortener.orchestration.graph.DependencyEdge;
import agentic.shortener.orchestration.graph.JoinSemantics;
import agentic.shortener.orchestration.graph.NodeRole;
import agentic.shortener.orchestration.graph.StageNode;
import agentic.shortener.orchestration.reliability.FailureCategory;
import agentic.shortener.orchestration.reliability.RetryOutcome;
import agentic.shortener.orchestration.reliability.RetryPolicy;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.state.SafeStopHandler;
import agentic.shortener.orchestration.state.StageCriteria;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.state.SuspensionTrigger;
import agentic.shortener.orchestration.store.ArtifactWritePolicy;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.orchestration.store.PersistedNode;
import agentic.shortener.orchestration.store.PersistedRun;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * The run-orchestration driver — the missing core this task plan never built before T131a: the loop that
 * actually walks a requirement through S1&#8211;S12, dispatching each stage's executor, persisting state and
 * audit atomically at every transition, pausing at a gate rather than polling for one, and reaching a
 * governed terminal outcome. Task T131a. FR-ORC-001, FR-ORC-004, FR-ORC-006, FR-ORC-013, FR-ORC-017.
 *
 * <p><strong>This class composes what Phases 1&#8211;7 already built; it invents none of their logic.</strong>
 * Sequencing and parallel dispatch are the one genuinely new piece — nothing in the codebase before this
 * task combined {@link agentic.shortener.orchestration.graph.StageTemplate}, {@link JdbcRunStore}, and
 * {@link StageCriteria#mayEnter} into an actual readiness computation ({@code StageCriteria.mayEnter} had
 * zero production callers before this class). Everything downstream of "which nodes may run now" is a
 * direct call into an existing, already-tested class: {@link RetryPolicy} for attempts and backoff,
 * {@link GateStore}/{@link GateRequestPresenter} to open a gate, {@link SafeStopHandler} to suspend,
 * {@link ArtifactWriteGuard} to persist an artifact, {@link AuditWriter} for the six-field governance
 * record.
 *
 * <h2>The dispatch loop, in one paragraph</h2>
 *
 * <p>Each round: reload the run's persisted nodes and edges, compute which not-yet-dispatched nodes have
 * every dependency in a join-satisfying state ({@link #readyNodes}), and dispatch all of them
 * <strong>concurrently</strong> — this is what makes S9 and S10 (or a genuine multi-child S7 fan-out) run in
 * parallel rather than merely being declared parallel in the graph. The round completes when every
 * dispatched node has finished; the loop then recomputes readiness and goes again. It stops when nothing is
 * ready: either everything reachable has finished (the run is then given its terminal transition), or
 * something is waiting on a human — a gate, in {@link StageState#AWAITING_APPROVAL} — in which case this
 * call simply returns. Nothing here polls or blocks waiting for a gate decision; {@link #advance} is called
 * again later, once a decision exists, by whoever applied it.
 *
 * <h2>Two Conductor-level policies that no single existing class owned</h2>
 *
 * <ul>
 *   <li><strong>S4's conditional clarification gate.</strong> S4 has no executor of its own
 *       ({@code StageEffectContracts}' own declaration for stage 4). This class reads S3's own
 *       {@code "ambiguities"} artifact (the same JSON {@code AmbiguityDetectionAiExecutor} produces) and
 *       decides: any {@code MATERIAL_PENDING} record opens an {@link GateClass#UNRESOLVED_AMBIGUITY} gate;
 *       otherwise S4 is marked {@link StageState#SKIPPED} with the recorded {@code noClarificationReason}
 *       as its transition reason, satisfying DS-A's own requirement that a non-detection be recorded, not
 *       silent.
 *   <li><strong>S6 and S11's approval-required stages.</strong> Plan &sect;5's table names an architecture
 *       approval after S6 and a release-readiness approval after S11, and neither engine (
 *       {@code DesignAiExecutor}, {@code ReleaseReadinessEngine}) opens a gate itself — both javadocs say so
 *       explicitly ("This is an evaluation, not a release decision"). So this class holds those two node
 *       keys at {@link StageState#AWAITING_APPROVAL} after a successful execution, rather than letting them
 *       reach {@link StageState#SUCCEEDED} directly, and opens the matching gate.
 * </ul>
 *
 * <h2>Fan-out, and the one Conductor-owned extension point</h2>
 *
 * <p>{@code StageTemplate.standard()} deliberately does not create S7's children — "a template that guessed
 * a child count would be asserting a decomposition nobody has made." This class calls an injected
 * {@link FanOutPlanner} exactly once, when the fan-out parent node ({@code "S7"}) itself becomes ready,
 * appends the children and their edges to {@code "S7.join"} via {@link JdbcRunStore#appendNode}, and marks
 * the parent {@link StageState#SKIPPED} — a pure control node, dispatched no differently from S4. {@code
 * "S7.join"} is handled the same way once every child satisfies the join: a control node with nothing of
 * its own to execute.
 *
 * <h2>Artifact threading: an accumulated map, not a per-edge lookup</h2>
 *
 * <p>{@link StageInput#inputArtifacts()} needs to see everything a stage might read, not only what its
 * direct graph predecessor produced — S5 depends on S3/S4 in the graph (they gate whether S5 may proceed at
 * all) but reads {@code "requirements"}, which is S2's own output. So this class keeps one growing
 * {@code Map&lt;String,String&gt;} of every artifact key any node has produced so far and hands every node
 * the whole thing; each executor already only reads the specific keys its own contract names. Three key
 * names genuinely differ between what a producer calls its output and what a consumer expects
 * ({@code "branchCommit"}&#8594;{@code "branch"}/{@code "change"}, {@code "test-results"}&#8594;
 * {@code "results"}, {@code "tasks"}&#8594;{@code "task"}) — {@link #ARTIFACT_ALIASES} adds the alias as a
 * second map entry alongside the original rather than renaming it, so both spellings stay readable (S12
 * itself requires the original {@code "test-results"} key by name). The {@code "tasks"}&#8594;{@code "task"}
 * alias was the last one found, and found live: {@code DecompositionAiExecutor}'s own output key is
 * {@code "tasks"} (plural), but {@code ImplementationAiExecutor}'s own input key is {@code "task"}
 * (singular) — with {@link FanOutPlanner#singleChild()} (the whole decomposition implemented as one child,
 * no further split), S7's single child never received the decomposition under the key its own contract
 * names, so every real S7 dispatch behind {@code singleChild()} failed {@code INVALID_INPUT} even though S5
 * had genuinely succeeded. No prior test in this codebase exercised the real {@code ImplementationAiExecutor}
 * downstream of a real {@code DecompositionAiExecutor} output — {@code ConductorIT}'s own S7 coverage uses a
 * stub that never reads either key by name — so this went unnoticed until DS-A's first real S7 dispatch
 * (T132). See {@code ArtifactAliasingIT}.
 */
public final class Conductor {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Stages whose successful execution is held at {@link StageState#AWAITING_APPROVAL} pending a human
     * gate decision, rather than proceeding straight to {@link StageState#SUCCEEDED}. Plan &sect;5. */
    private static final Map<Integer, GateClass> APPROVAL_REQUIRED_STAGES = Map.of(
            6, GateClass.ARCHITECTURE_APPROVAL,
            11, GateClass.RELEASE_READINESS);

    /** A produced artifact key, and every additional name a downstream consumer expects it under. Neither
     * alias replaces the original — both remain readable in the accumulated artifacts map. */
    private static final Map<String, List<String>> ARTIFACT_ALIASES = Map.of(
            "branchCommit", List.of("branch", "change"),
            "test-results", List.of("results"),
            "tasks", List.of("task"));

    private final JdbcRunStore runStore;
    private final GateStore gateStore;
    private final GateRequestPresenter gateRequestPresenter;
    private final ArtifactWriteGuard artifactWriteGuard;
    private final AuditWriter auditWriter;
    private final StageTelemetry telemetry;
    private final SafeStopHandler safeStopHandler;
    private final RetryPolicy retryPolicy;
    private final Function<Integer, StageExecutor> executorsByStageNumber;
    private final FanOutPlanner fanOutPlanner;
    private final Clock clock;
    private final ExecutorService dispatchPool;

    /**
     * Every artifact this Conductor instance has seen produced for a run, by run id. {@link
     * ArtifactWriteGuard} durably persists the same content (governance evidence, replayable by anyone with
     * database access), but exposes no read accessor today — this map is this instance's own working
     * memory of it, rebuilt as each artifact is produced.
     *
     * <p><strong>Disclosed scope boundary</strong>: this map does not survive a process restart. A run
     * resumed by a fresh {@code Conductor} instance after a crash (the same gap {@link
     * agentic.shortener.orchestration.recovery.ResumeService}'s own javadoc discloses — "there is no live
     * engine loop yet that dispatches executors on its own") would need its already-produced artifacts
     * re-read from {@code artifact_version} before {@link #advance} could correctly feed a node dispatched
     * after the restart. That reader does not exist yet; building one is future work, not silently assumed
     * here.
     */
    private final Map<UUID, Map<String, String>> artifactsByRun = new ConcurrentHashMap<>();

    public Conductor(JdbcRunStore runStore, GateStore gateStore, GateRequestPresenter gateRequestPresenter,
            ArtifactWriteGuard artifactWriteGuard, AuditWriter auditWriter, StageTelemetry telemetry,
            SafeStopHandler safeStopHandler, RetryPolicy retryPolicy,
            Function<Integer, StageExecutor> executorsByStageNumber, FanOutPlanner fanOutPlanner, Clock clock,
            ExecutorService dispatchPool) {
        this.runStore = Objects.requireNonNull(runStore, "runStore");
        this.gateStore = Objects.requireNonNull(gateStore, "gateStore");
        this.gateRequestPresenter = Objects.requireNonNull(gateRequestPresenter, "gateRequestPresenter");
        this.artifactWriteGuard = Objects.requireNonNull(artifactWriteGuard, "artifactWriteGuard");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.safeStopHandler = Objects.requireNonNull(safeStopHandler, "safeStopHandler");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.executorsByStageNumber = Objects.requireNonNull(executorsByStageNumber, "executorsByStageNumber");
        this.fanOutPlanner = Objects.requireNonNull(fanOutPlanner, "fanOutPlanner");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.dispatchPool = Objects.requireNonNull(dispatchPool, "dispatchPool");
    }

    // ==============================================================================================
    // Submission
    // ==============================================================================================

    /**
     * Materializes a new run from a raw requirement and transitions it to {@link RunState#RUNNING}.
     * FR-ORC-001, FR-ORC-007.
     *
     * <p><strong>Deliberately does not call {@link #advance} itself.</strong> T082a's own Validate clause
     * requires the run this method hands back to still show the thirteen freshly-materialized nodes with
     * S4 {@link StageState#BLOCKED} — i.e. a caller (in practice, {@code RunSubmissionController}) needs a
     * fast, bounded response, not one that blocks for however long a real AI-capable stage takes. Driving
     * the run is the caller's own, separate decision: {@code RunSubmissionController} advances it on a
     * background thread after replying; {@link #submit} itself only ever does the fast, synchronous,
     * deterministic half.
     */
    public UUID submit(agentic.shortener.orchestration.graph.StageTemplate template, String policySetVersion,
            String requirementText) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(policySetVersion, "policySetVersion");
        if (requirementText == null || requirementText.isBlank()) {
            throw new IllegalArgumentException("a blank requirement cannot be admitted — S1 refuses it "
                    + "anyway, but a run should not be created for one at all");
        }
        UUID correlationId = UUID.randomUUID();
        UUID runId = runStore.createRun(template, policySetVersion, correlationId);

        CorrelationContext.runWithin(runId, () -> {
            auditWriter.write(new AuditEvent(null, runId, "system", "RUN_CREATED", clock.instant(),
                    "workflow_run:" + runId, "SUCCESS", "requirement submitted", null, null, null));

            String entryNodeKey = template.nodes().stream()
                    .filter(n -> template.incomingEdges(n.nodeKey()).isEmpty())
                    .findFirst()
                    .map(StageNode::nodeKey)
                    .orElseThrow(() -> new IllegalStateException("template has no entry node"));

            artifactWriteGuard.write(runId, "submission", requirementText, entryNodeKey, List.of(),
                    ArtifactWritePolicy.REJECT_SECOND);
            putArtifact(runId, "submission", requirementText);

            runStore.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "advancing from submission");
        });
        return runId;
    }

    // ==============================================================================================
    // The dispatch loop
    // ==============================================================================================

    /**
     * Drives {@code runId} forward until nothing is ready to run — because the run finished, or because
     * something is now waiting on a human. Safe and idempotent to call on a run that is not
     * {@link RunState#RUNNING}: it returns immediately. This is the method a gate-decision handler calls
     * after applying an outcome, to resume a run a gate had paused.
     */
    public void advance(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        CorrelationContext.runWithin(runId, () -> advanceInternal(runId));
    }

    private void advanceInternal(UUID runId) {
        while (true) {
            PersistedRun run = runStore.run(runId)
                    .orElseThrow(() -> new IllegalStateException("no such run: " + runId));
            if (run.state() != RunState.RUNNING) {
                return;
            }

            List<PersistedNode> nodes = runStore.persistedNodes(runId);
            List<DependencyEdge> edges = runStore.edges(runId);
            Map<String, String> artifacts = accumulatedArtifacts(runId);

            List<PersistedNode> ready = readyNodes(nodes, edges);
            if (ready.isEmpty()) {
                if (everyNodeSettled(nodes)) {
                    completeRun(runId, nodes);
                }
                return;
            }

            dispatchRound(runId, ready, artifacts);
        }
    }

    /** A node whose own state is {@link StageState#BLOCKED} or {@link StageState#READY} (never dispatched
     * yet), and whose every incoming edge's source node is in a join-satisfying state. */
    private List<PersistedNode> readyNodes(List<PersistedNode> nodes, List<DependencyEdge> edges) {
        Map<String, StageState> stateByKey = new LinkedHashMap<>();
        for (PersistedNode n : nodes) {
            stateByKey.put(n.nodeKey(), n.state());
        }
        StageCriteria criteria = new StageCriteria();
        List<PersistedNode> ready = new ArrayList<>();
        for (PersistedNode n : nodes) {
            if (n.state() != StageState.BLOCKED && n.state() != StageState.READY) {
                continue;
            }
            List<StageState> dependencyStates = edges.stream()
                    .filter(e -> e.toNodeKey().equals(n.nodeKey()))
                    .map(e -> stateByKey.get(e.fromNodeKey()))
                    .toList();
            if (criteria.mayEnter(dependencyStates)) {
                ready.add(n);
            }
        }
        return ready;
    }

    private boolean everyNodeSettled(List<PersistedNode> nodes) {
        return nodes.stream().allMatch(n -> n.state().terminal());
    }

    private void completeRun(UUID runId, List<PersistedNode> nodes) {
        boolean anyUnsatisfied = nodes.stream().anyMatch(n -> !n.state().satisfiesJoin());
        if (anyUnsatisfied) {
            // A node finished FAILED or INVALIDATED with nothing further ready to run, and the run was not
            // already suspended by the dispatch path that produced it — this is a defensive backstop, not
            // the primary path (dispatchNode already suspends on a permanent/exhausted failure).
            return;
        }
        runStore.transitionRun(runId, RunState.RUNNING, RunState.COMPLETED, "every node succeeded or skipped");
        auditWriter.write(new AuditEvent(null, runId, "system", "RUN_COMPLETED", clock.instant(),
                "workflow_run:" + runId, "SUCCESS", "all nodes reached a join-satisfying terminal state",
                null, null, null));
    }

    // ==============================================================================================
    // One round: dispatch every ready node concurrently, wait for all of them
    // ==============================================================================================

    private void dispatchRound(UUID runId, List<PersistedNode> ready, Map<String, String> artifacts) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (PersistedNode node : ready) {
            futures.add(CompletableFuture.runAsync(
                    () -> CorrelationContext.runWithin(runId, () -> dispatchOne(runId, node, artifacts)),
                    dispatchPool));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Every branch below already turns a classified stage failure into a graceful suspension ({@link
     * #onStageFailed}). This wrapper is for what none of them anticipates — a run whose earlier state
     * did not actually come from this (or any) {@link Conductor} instance's own dispatch (a hand-built
     * fixture, or a run resumed after a crash with no artifact cache to rebuild from — the disclosed
     * scope boundary in this class's own javadoc). Without it, a precondition this class enforces with an
     * {@code IllegalStateException} (e.g. {@link #handleClarificationGate}'s required {@code
     * "ambiguities"} artifact) propagates uncaught through {@link #dispatchRound}'s {@code
     * CompletableFuture}, out through {@link #advance}, and into whatever called it — an HTTP 500 for a
     * caller that did nothing wrong, rather than the same governed suspension a classified failure gets.
     */
    private void dispatchOne(UUID runId, PersistedNode node, Map<String, String> artifacts) {
        String nodeKey = node.nodeKey();
        try {
            if (node.role() == NodeRole.FAN_OUT_PARENT) {
                handleFanOutParent(runId, node, artifacts);
            } else if (node.role() == NodeRole.JOIN) {
                handleJoinNode(runId, node);
            } else if (nodeKey.equals("S4")) {
                handleClarificationGate(runId, node, artifacts);
            } else {
                dispatchExecutor(runId, node, artifacts);
            }
        } catch (RuntimeException e) {
            suspendOnUnexpectedFailure(runId, nodeKey, e);
        }
    }

    private void suspendOnUnexpectedFailure(UUID runId, String nodeKey, RuntimeException e) {
        String detail = nodeKey + ": " + e.getClass().getSimpleName() + ": " + e.getMessage();
        auditWriter.write(new AuditEvent(null, runId, "system", "STAGE_EXITED", clock.instant(),
                "stage_node:" + nodeKey, "FAILURE", detail, null, null, null));
        try {
            safeStopHandler.suspend(runId, SuspensionTrigger.UNRECOGNIZED_FAILURE_CLASSIFICATION, detail);
        } catch (RuntimeException suspendFailed) {
            // The run may already be SAFE_STOP (a concurrently-dispatched sibling node hit its own
            // failure first) — SafeStopHandler's own idempotency is not assumed here; a second suspend
            // attempt failing is not itself escalated further.
        }
        auditWriter.write(new AuditEvent(null, runId, "system", "RUN_SUSPENDED", clock.instant(),
                "workflow_run:" + runId, "SUSPENDED",
                SuspensionTrigger.UNRECOGNIZED_FAILURE_CLASSIFICATION.name() + ": " + detail, null, null, null));
    }

    // ==============================================================================================
    // Ordinary stage dispatch (S1, S2, S3, S5, every S7 child, S8, S9, S10, S12 — and S6/S11's own
    // execution, before the approval-gate check)
    // ==============================================================================================

    private void dispatchExecutor(UUID runId, PersistedNode node, Map<String, String> artifacts) {
        String nodeKey = node.nodeKey();
        int stageNumber = node.stageNumber();

        ready(runId, nodeKey, node.state());
        runStore.transitionNode(runId, nodeKey, StageState.READY, StageState.RUNNING, "dispatched by conductor");
        auditWriter.write(new AuditEvent(null, runId, "system", "STAGE_ENTERED", clock.instant(),
                "stage_node:" + nodeKey, "SUCCESS", "stage " + stageNumber + " dispatched", stageNumber,
                null, null));

        StageExecutor executor = executorsByStageNumber.apply(stageNumber);
        if (executor == null) {
            throw new IllegalStateException("no executor registered for stage " + stageNumber
                    + " (node " + nodeKey + ")");
        }
        StageInput input = new StageInput(runId, nodeKey, stageNumber, 1, Map.copyOf(artifacts));

        RetryOutcome outcome = telemetry.stageExecution(runId, stageNumber,
                id -> retryPolicy.execute(stageNumber, executor, input),
                RetryOutcome::succeeded);

        if (outcome.succeeded()) {
            onStageSucceeded(runId, nodeKey, stageNumber, outcome, artifacts);
        } else {
            onStageFailed(runId, nodeKey, stageNumber, outcome);
        }
    }

    private void onStageSucceeded(UUID runId, String nodeKey, int stageNumber, RetryOutcome outcome,
            Map<String, String> artifacts) {
        List<ProducedArtifact> produced =
                outcome.attempts().get(outcome.attempts().size() - 1).outcome().producedArtifacts();
        for (ProducedArtifact artifact : produced) {
            artifactWriteGuard.write(runId, artifact.artifactKey(), artifact.content(), nodeKey,
                    artifact.derivedFrom(), ArtifactWritePolicy.SERIALIZE);
            putArtifact(runId, artifact.artifactKey(), artifact.content());
        }
        // executor_kind_used is NOT written here: T066's own architecture test (HumanExecutorKindTest)
        // reserves JdbcRunStore.recordExecutorKind exclusively for NoPlanGate's HUMAN override. Every
        // other node's kind is already known statically from executor_class (fixed at node creation —
        // JdbcRunStore.executorClassOf), which is what a reader consults for the normal case; this local
        // value is used only for this audit event's own optional field, a separate concern from that
        // column.
        ExecutorKind executorKind =
                outcome.attempts().get(outcome.attempts().size() - 1).outcome().executorKind();

        GateClass gateClass = APPROVAL_REQUIRED_STAGES.get(stageNumber);
        if (gateClass != null) {
            runStore.transitionNode(runId, nodeKey, StageState.RUNNING, StageState.AWAITING_APPROVAL,
                    "succeeded; pending human " + gateClass + " decision");
            requestGate(runId, nodeKey, gateClass);
        } else {
            runStore.transitionNode(runId, nodeKey, StageState.RUNNING, StageState.SUCCEEDED,
                    "stage " + stageNumber + " succeeded");
        }
        auditWriter.write(new AuditEvent(null, runId, "system", "STAGE_EXITED", clock.instant(),
                "stage_node:" + nodeKey, "SUCCESS", "stage " + stageNumber + " produced " + produced.size()
                        + " artifact(s)", stageNumber, executorKind.name(), null));
    }

    private void onStageFailed(UUID runId, String nodeKey, int stageNumber, RetryOutcome outcome) {
        runStore.transitionNode(runId, nodeKey, StageState.RUNNING, StageState.FAILED,
                "exhausted=" + outcome.exhausted() + ": " + outcome.finalFailure().detail());
        auditWriter.write(new AuditEvent(null, runId, "system", "STAGE_EXITED", clock.instant(),
                "stage_node:" + nodeKey, "FAILURE", outcome.finalFailure().detail(), stageNumber, null, null));

        FailureCategory category = outcome.finalFailure().category();
        SuspensionTrigger trigger = category == FailureCategory.UNKNOWN
                ? SuspensionTrigger.UNRECOGNIZED_FAILURE_CLASSIFICATION
                : SuspensionTrigger.UNRECOVERABLE_FAILURE;
        safeStopHandler.suspend(runId, trigger, "stage " + stageNumber + " (" + nodeKey + ") failed "
                + "permanently: " + outcome.finalFailure().detail());
        auditWriter.write(new AuditEvent(null, runId, "system", "RUN_SUSPENDED", clock.instant(),
                "workflow_run:" + runId, "SUSPENDED", trigger.name(), stageNumber, null, null));
    }

    private void ready(UUID runId, String nodeKey, StageState current) {
        if (current == StageState.BLOCKED) {
            runStore.transitionNode(runId, nodeKey, StageState.BLOCKED, StageState.READY,
                    "dependencies satisfied");
        }
    }

    // ==============================================================================================
    // S4: the one conditional gate with no executor of its own
    // ==============================================================================================

    private void handleClarificationGate(UUID runId, PersistedNode s4, Map<String, String> artifacts) {
        ready(runId, "S4", s4.state());

        String ambiguitiesJson = artifacts.get("ambiguities");
        if (ambiguitiesJson == null || ambiguitiesJson.isBlank()) {
            throw new IllegalStateException("S4 became ready with no 'ambiguities' artifact from S3 — "
                    + "S3's own contract requires it to always answer with at least one record");
        }

        JsonNode array;
        try {
            array = JSON.readTree(ambiguitiesJson);
        } catch (Exception e) {
            throw new IllegalStateException("S3's 'ambiguities' artifact is not valid JSON", e);
        }

        boolean materialPending = false;
        String firstNotMaterialReason = null;
        for (JsonNode element : array) {
            String state = element.path("resolutionState").asText("");
            if ("MATERIAL_PENDING".equals(state)) {
                materialPending = true;
            } else if (firstNotMaterialReason == null && element.hasNonNull("noClarificationReason")) {
                firstNotMaterialReason = element.get("noClarificationReason").asText();
            }
        }

        if (materialPending) {
            runStore.transitionNode(runId, "S4", StageState.READY, StageState.AWAITING_APPROVAL,
                    "material ambiguity detected; awaiting human clarification");
            requestGate(runId, "S4", GateClass.UNRESOLVED_AMBIGUITY);
        } else {
            String reason = firstNotMaterialReason != null
                    ? firstNotMaterialReason
                    : "no material ambiguity found";
            runStore.transitionNode(runId, "S4", StageState.READY, StageState.SKIPPED,
                    "no_clarification_reason: " + reason);
        }
        auditWriter.write(new AuditEvent(null, runId, "system", "CRITERIA_EVALUATED", clock.instant(),
                "stage_node:S4", "SUCCESS", materialPending ? "material ambiguity found" : "not material",
                4, null, null));
    }

    // ==============================================================================================
    // Fan-out parent and join: control nodes with no executor of their own
    // ==============================================================================================

    private void handleFanOutParent(UUID runId, PersistedNode parent, Map<String, String> artifacts) {
        ready(runId, parent.nodeKey(), parent.state());

        List<String> childKeys = fanOutPlanner.plan(runId, parent.nodeKey(), Map.copyOf(artifacts));
        if (childKeys.isEmpty()) {
            throw new IllegalStateException("FanOutPlanner returned no children for " + parent.nodeKey()
                    + " — a fan-out with nothing to fan out to would leave its join waiting forever");
        }
        String joinKey = parent.nodeKey() + ".join";
        for (String childKey : childKeys) {
            StageNode child = StageNode.fanOutChild(childKey, parent.stageNumber(), parent.nodeKey(),
                    parent.name() + " (" + childKey + ")");
            List<DependencyEdge> childEdges = List.of(
                    new DependencyEdge(parent.nodeKey(), childKey, JoinSemantics.ALL),
                    new DependencyEdge(childKey, joinKey, JoinSemantics.ALL));
            runStore.appendNode(runId, child, childEdges);
        }
        runStore.transitionNode(runId, parent.nodeKey(), StageState.READY, StageState.SKIPPED,
                childKeys.size() + " child task(s) appended: " + childKeys);
        auditWriter.write(new AuditEvent(null, runId, "system", "STAGE_EXITED", clock.instant(),
                "stage_node:" + parent.nodeKey(), "SUCCESS",
                "fanned out to " + childKeys, parent.stageNumber(), null, null));
    }

    private void handleJoinNode(UUID runId, PersistedNode join) {
        ready(runId, join.nodeKey(), join.state());
        runStore.transitionNode(runId, join.nodeKey(), StageState.READY, StageState.SKIPPED,
                "join point — every branch satisfied the join, nothing of its own to execute");
    }

    // ==============================================================================================
    // Gates
    // ==============================================================================================

    private void requestGate(UUID runId, String nodeKey, GateClass gateClass) {
        String gateId = UUID.randomUUID().toString();
        ApprovalGate gate = gateRequestPresenter.request(gateId, runId, nodeKey, gateClass);
        gateStore.requestGate(gate);
        auditWriter.write(new AuditEvent(null, runId, "system", "GATE_REQUESTED", clock.instant(),
                "stage_node:" + nodeKey, "SUCCESS", gateClass + " gate requested, wait deadline "
                        + gate.waitDeadline(), null, null, null));
    }

    // ==============================================================================================
    // Artifact accumulation
    // ==============================================================================================

    /** This instance's working memory of every artifact produced for {@code runId} so far — see {@link
     * #artifactsByRun}'s own javadoc for what this does and does not survive. */
    private Map<String, String> accumulatedArtifacts(UUID runId) {
        return artifactsByRun.computeIfAbsent(runId, id -> new ConcurrentHashMap<>());
    }

    private void putArtifact(UUID runId, String key, String content) {
        Map<String, String> artifacts = artifactsByRun.computeIfAbsent(runId, id -> new ConcurrentHashMap<>());
        artifacts.put(key, content);
        for (String alias : ARTIFACT_ALIASES.getOrDefault(key, List.of())) {
            artifacts.put(alias, content);
        }
    }
}
