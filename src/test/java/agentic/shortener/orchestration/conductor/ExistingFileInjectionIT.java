package agentic.shortener.orchestration.conductor;

import agentic.shortener.audit.AuditWriter;
import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.orchestration.executor.ExecutorKind;
import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.gates.Actor;
import agentic.shortener.orchestration.gates.ApprovalGate;
import agentic.shortener.orchestration.gates.GateDecision;
import agentic.shortener.orchestration.gates.GateOutcome;
import agentic.shortener.orchestration.gates.GateOutcomeHandler;
import agentic.shortener.orchestration.gates.GateRequestPresenter;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.graph.ArtifactWriteGuard;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.reliability.Backoff;
import agentic.shortener.orchestration.reliability.RetryPolicy;
import agentic.shortener.orchestration.state.RetentionPolicy;
import agentic.shortener.orchestration.state.StageState;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The S7 existing-file fix (owner directive, 2026-09-22): the orchestration -- not the executor -- reads an
 * existing file's real content and injects it into S7's own {@code StageInput}, so {@link
 * agentic.shortener.orchestration.executor.ai.stages.ImplementationAiExecutor} can produce a correct diff for
 * a file it can never itself read (it is a pure function over its input, deliberately given no tool access --
 * see that class's own javadoc). This is the end-to-end proof {@link ArtifactAliasingIT} models: a real
 * {@link RepoExistingFileReader} against a real file on disk, wired through the real {@link Conductor}
 * constructor, driven by a real S6 design output naming that file under {@code "existingFilesToModify"} --
 * proving S7's own {@code StageInput.inputArtifacts()} actually carries that file's real content under
 * {@code "existingFiles"}, not merely that the unit-level pieces compile against each other.
 */
@DisplayName("S7 existing-file fix -- Conductor pre-fetches a real file named by S6's design and injects it "
        + "into S7's own StageInput")
class ExistingFileInjectionIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-22T12:00:00Z");

    @TempDir
    Path repoRoot;

    @Test
    @DisplayName("a design naming a real file results in that file's real content reaching S7 under "
            + "'existingFiles'")
    void existingFileNamedByDesignReachesS7WithRealContent() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS harness_round_trip");
        }
        MigrationSupport.migrate(POSTGRES, "public");

        String realPomContent = "<project>REAL-ON-DISK-CONTENT-" + UUID.randomUUID() + "</project>";
        Files.writeString(repoRoot.resolve("pom.xml"), realPomContent, StandardCharsets.UTF_8);

        ConnectionSource connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

        JdbcRunStore runStore = new JdbcRunStore(connections, clock);
        GateStore gateStore = new GateStore(connections, clock);
        RetentionPolicy retentionPolicy = new RetentionPolicy();
        GateRequestPresenter gateRequestPresenter =
                new GateRequestPresenter(clock, retentionPolicy, Duration.ofHours(24));
        ArtifactWriteGuard artifactWriteGuard = new ArtifactWriteGuard(connections, clock);
        AuditWriter auditWriter = new AuditWriter(connections);
        agentic.shortener.orchestration.state.SafeStopHandler safeStopHandler =
                new agentic.shortener.orchestration.state.SafeStopHandler(connections, clock, retentionPolicy);
        RetryPolicy retryPolicy = new RetryPolicy(Backoff.sleeping(), StageTelemetry.disabled());

        AtomicReference<String> capturedExistingFiles = new AtomicReference<>();

        java.util.function.Function<Integer, StageExecutor> executors = stageNumber -> switch (stageNumber) {
            case 1 -> input -> succeeded("normalized", "req");
            case 2 -> input -> succeeded("requirements", "[{\"externalId\":\"1\"}]");
            case 3 -> input -> succeeded("ambiguities", "[{\"ambiguityClass\":\"UNDEFINED_TERM\","
                    + "\"affectedPath\":\"n/a\",\"resolutionState\":\"NOT_MATERIAL\","
                    + "\"qualityChecksPerformed\":\"n/a\",\"noClarificationReason\":\"nothing to check here\"}]");
            case 5 -> input -> succeeded("tasks",
                    "[{\"taskId\":\"T1\",\"requirementIds\":[\"1\"],\"dependsOn\":[]}]");
            // S6's own real output shape (DesignAiExecutor.parse()): existingFilesToModify names a real,
            // on-disk file -- exactly what this fix reads to know what to pre-fetch.
            case 6 -> input -> succeeded("design",
                    "{\"design\":\"add build-info\",\"contractImpact\":\"none\","
                            + "\"existingFilesToModify\":[\"pom.xml\"],"
                            + "\"materialDesignDecisions\":[\"stub material decision, kept the S6 gate "
                            + "exercised so approveGate has a pending gate to approve\"]}");
            case 7 -> input -> {
                capturedExistingFiles.set(input.inputArtifacts().get("existingFiles"));
                return succeeded("branchCommit", "stub/" + input.nodeKey());
            };
            default -> input -> succeeded("stub-output-S" + stageNumber, "stub content for S" + stageNumber);
        };

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter, artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler, retryPolicy, executors,
                FanOutPlanner.singleChild(), clock, Executors.newFixedThreadPool(2),
                new RepoExistingFileReader(repoRoot));

        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", "a test requirement");
        conductor.advance(runId);

        approveGate(conductor, runId, runStore, gateStore, connections, clock, "S6", 6);

        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S7.1").orElseThrow().state(),
                "S7.1 should have succeeded with a stub branch commit");
        assertNotNull(capturedExistingFiles.get(),
                "S7 must receive an 'existingFiles' input key once S6's design names a real existing file");
        assertTrue(capturedExistingFiles.get().contains(realPomContent),
                "the content shown to S7 must be the REAL, current on-disk content of pom.xml, byte-accurate "
                        + "-- not fabricated or guessed");
    }

    @Test
    @DisplayName("REGRESSION: a design naming NO existing files (greenfield) leaves S7 with no 'existingFiles' "
            + "key at all -- new-file creation is unaffected by this fix")
    void designNamingNoExistingFilesLeavesS7Unaugmented() throws Exception {
        try (var c = connection(); var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS harness_round_trip");
        }
        MigrationSupport.migrate(POSTGRES, "public");

        ConnectionSource connections = () -> connection();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);

        JdbcRunStore runStore = new JdbcRunStore(connections, clock);
        GateStore gateStore = new GateStore(connections, clock);
        RetentionPolicy retentionPolicy = new RetentionPolicy();
        GateRequestPresenter gateRequestPresenter =
                new GateRequestPresenter(clock, retentionPolicy, Duration.ofHours(24));
        ArtifactWriteGuard artifactWriteGuard = new ArtifactWriteGuard(connections, clock);
        AuditWriter auditWriter = new AuditWriter(connections);
        agentic.shortener.orchestration.state.SafeStopHandler safeStopHandler =
                new agentic.shortener.orchestration.state.SafeStopHandler(connections, clock, retentionPolicy);
        RetryPolicy retryPolicy = new RetryPolicy(Backoff.sleeping(), StageTelemetry.disabled());

        AtomicReference<String> capturedExistingFiles = new AtomicReference<>();
        AtomicReference<Boolean> keyWasPresent = new AtomicReference<>();

        java.util.function.Function<Integer, StageExecutor> executors = stageNumber -> switch (stageNumber) {
            case 1 -> input -> succeeded("normalized", "req");
            case 2 -> input -> succeeded("requirements", "[{\"externalId\":\"1\"}]");
            case 3 -> input -> succeeded("ambiguities", "[{\"ambiguityClass\":\"UNDEFINED_TERM\","
                    + "\"affectedPath\":\"n/a\",\"resolutionState\":\"NOT_MATERIAL\","
                    + "\"qualityChecksPerformed\":\"n/a\",\"noClarificationReason\":\"nothing to check here\"}]");
            case 5 -> input -> succeeded("tasks",
                    "[{\"taskId\":\"T1\",\"requirementIds\":[\"1\"],\"dependsOn\":[]}]");
            // Greenfield: no existingFilesToModify at all -- the field is absent, matching a real S6
            // response that never mentioned it (DesignAiExecutorTest's own
            // existingFilesToModifyDefaultsToEmptyArrayWhenOmitted still produces an EMPTY array, which
            // this branch covers too since both mean "nothing to pre-fetch").
            case 6 -> input -> succeeded("design", "{\"design\":\"a wholly new controller\","
                    + "\"contractImpact\":\"none\",\"existingFilesToModify\":[],"
                    + "\"materialDesignDecisions\":[\"stub material decision, kept the S6 gate exercised "
                    + "so approveGate has a pending gate to approve\"]}");
            case 7 -> input -> {
                keyWasPresent.set(input.inputArtifacts().containsKey("existingFiles"));
                capturedExistingFiles.set(input.inputArtifacts().get("existingFiles"));
                return succeeded("branchCommit", "stub/" + input.nodeKey());
            };
            default -> input -> succeeded("stub-output-S" + stageNumber, "stub content for S" + stageNumber);
        };

        Conductor conductor = new Conductor(runStore, gateStore, gateRequestPresenter, artifactWriteGuard,
                auditWriter, StageTelemetry.disabled(), safeStopHandler, retryPolicy, executors,
                FanOutPlanner.singleChild(), clock, Executors.newFixedThreadPool(2),
                new RepoExistingFileReader(repoRoot));

        UUID runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", "a test requirement");
        conductor.advance(runId);

        approveGate(conductor, runId, runStore, gateStore, connections, clock, "S6", 6);

        assertEquals(StageState.SUCCEEDED, runStore.node(runId, "S7.1").orElseThrow().state());
        assertFalse(Boolean.TRUE.equals(keyWasPresent.get()),
                "with an empty existingFilesToModify, no 'existingFiles' key should be added at all");
        assertEquals(null, capturedExistingFiles.get());
    }

    private static StageOutcome succeeded(String key, String content) {
        return StageOutcome.succeeded(List.of(new ProducedArtifact(key, content, List.of())), ExecutorKind.AI);
    }

    /** Same shape as {@code ArtifactAliasingIT}'s own {@code approveGate} helper. */
    private void approveGate(Conductor conductor, UUID runId, JdbcRunStore runStore, GateStore gateStore,
                             ConnectionSource connections, Clock clock, String nodeKey, int stageNumber)
            throws Exception {
        String gateId;
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT gate_id FROM approval_gate WHERE run_id = ? AND node_key = ?")) {
            ps.setObject(1, runId);
            ps.setString(2, nodeKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("no pending gate for " + nodeKey);
                }
                gateId = rs.getString("gate_id");
            }
        }
        GateDecision decision = new GateDecision(runId, gateId, stageNumber,
                agentic.shortener.orchestration.gates.GateClass.ARCHITECTURE_APPROVAL, GateOutcome.APPROVED,
                new Actor("human", "test-owner"), clock.instant(), "approved for ExistingFileInjectionIT",
                "docs/governance/gate-decisions/test-fixture.md", List.of(), null, null);
        long decisionId = gateStore.recordDecision(decision);
        GateDecision recorded = gateStore.decisionById(decisionId).orElseThrow();
        GateOutcomeHandler outcomeHandler = new GateOutcomeHandler(runStore, gateStore, connections);
        outcomeHandler.apply(runId, nodeKey, recorded);
        conductor.advance(runId);
    }
}
