package agentic.shortener.orchestration.lineage;

import agentic.shortener.orchestration.graph.ArtifactWriteGuard;
import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.state.RunState;
import agentic.shortener.orchestration.store.ArtifactWritePolicy;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Artifact provenance — the query half of task T081. FR-ORC-005.
 *
 * <p>T081's Artifact field asks for three things and {@code ArtifactWriteGuardIT} covers only the first:
 * <em>content hash plus producing stage</em>, then <em>from which inputs</em>, then <em>under which
 * decisions</em>. Its Validate clause asks for a provenance query over a completed run returning a
 * <strong>complete chain</strong>, and its Guard for no orphan artifacts. Writing provenance down is not
 * the same claim as being able to follow it, so this is where the second claim is made.
 *
 * <p><strong>"Under which decisions" is read as the decisions already recorded when the artifact was
 * produced.</strong> Not every decision the run ever makes: a gate approved afterwards did not govern a
 * write that had already happened, and including it would let provenance imply an authorisation that did
 * not exist at the time.
 */
@DisplayName("T081 — artifact provenance is followable, and no artifact lacks it")
class ArtifactProvenanceIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private ArtifactProvenanceQuery query;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        query = new ArtifactProvenanceQuery(connections);
    }

    private JdbcRunStore storeAt(Instant when) {
        return new JdbcRunStore(connections, Clock.fixed(when, ZoneOffset.UTC));
    }

    private ArtifactWriteGuard guardAt(Instant when) {
        return new ArtifactWriteGuard(connections, Clock.fixed(when, ZoneOffset.UTC));
    }

    /**
     * A run with a three-link chain: the spec derived from the brief, the plan from the spec.
     *
     * <p>Each write is stamped at a different instant, so "which decisions were in force" is a question
     * with different answers for different links rather than one answer that happens to fit.
     */
    private UUID runWithAChain() {
        JdbcRunStore store = storeAt(T0);
        UUID runId = store.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        store.transitionRun(runId, RunState.PENDING, RunState.RUNNING, "started");

        guardAt(T0).write(runId, "brief", "the original request", "S1",
                List.of(), ArtifactWritePolicy.REJECT_SECOND);
        guardAt(T0.plusSeconds(60)).write(runId, "spec", "requirements", "S2",
                List.of("brief"), ArtifactWritePolicy.REJECT_SECOND);
        guardAt(T0.plusSeconds(120)).write(runId, "plan", "the design", "S5",
                List.of("spec"), ArtifactWritePolicy.SERIALIZE);
        return runId;
    }

    @Test
    @DisplayName("the chain from an artifact back to its roots is complete and ordered")
    void chainIsCompleteAndOrdered() {
        UUID runId = runWithAChain();

        ArtifactProvenanceQuery.Chain chain = query.chainFor(runId, "plan");

        assertTrue(chain.complete(),
                "every input named by a link must resolve to a link: " + chain.unresolvedInputs());
        assertEquals(List.of("plan", "spec", "brief"),
                chain.links().stream().map(ArtifactProvenance::artifactKey).toList(),
                "ordered from the artifact asked about back to its root, so a reader can follow it");

        ArtifactProvenance plan = chain.links().get(0);
        assertEquals("S5", plan.producedByNodeKey());
        assertEquals(List.of("spec"), plan.inputArtifactKeys());
        assertEquals(1, plan.version());
        assertEquals(64, plan.contentHash().length(), "SHA-256 hex");
        assertEquals(T0.plusSeconds(120), plan.producedAt());

        ArtifactProvenance brief = chain.links().get(2);
        assertTrue(brief.inputArtifactKeys().isEmpty(), "a root artifact has no inputs");
    }

    @Test
    @DisplayName("the chain follows the LATEST version of each link, and says which version that is")
    void chainFollowsTheLatestVersion() {
        UUID runId = runWithAChain();

        // A second node revises the plan. SERIALIZE means it lands behind the first rather than over it.
        guardAt(T0.plusSeconds(200)).write(runId, "plan", "the revised design", "S6",
                List.of("spec"), ArtifactWritePolicy.SERIALIZE);

        ArtifactProvenance plan = query.chainFor(runId, "plan").links().get(0);
        assertEquals(2, plan.version(), "the current plan is version 2");
        assertEquals("S6", plan.producedByNodeKey());

        // Version 1 is still there and still followable — that is what append-only buys.
        ArtifactProvenance original = query.versionOf(runId, "plan", 1).orElseThrow();
        assertEquals("S5", original.producedByNodeKey());
    }

    @Test
    @DisplayName("an unresolved input makes the chain INCOMPLETE rather than quietly shorter")
    void unresolvedInputIsReportedNotHidden() {
        UUID runId = runWithAChain();

        // An artifact declaring an input nobody ever wrote. The store cannot refuse this — inputs are a
        // list of keys, not foreign keys — so the query has to be the thing that notices.
        guardAt(T0.plusSeconds(180)).write(runId, "report", "findings", "S9",
                List.of("plan", "test-results"), ArtifactWritePolicy.SERIALIZE);

        ArtifactProvenanceQuery.Chain chain = query.chainFor(runId, "report");

        assertFalse(chain.complete(),
                "a chain missing a named input is incomplete; returning it as though it were whole is "
                        + "how provenance comes to assert a lineage it cannot substantiate");
        assertEquals(List.of("test-results"), chain.unresolvedInputs());
        // The part that IS known is still returned — a broken link is not a reason to report nothing.
        assertTrue(chain.links().stream().map(ArtifactProvenance::artifactKey).toList()
                        .containsAll(List.of("report", "plan", "spec", "brief")),
                "actual: " + chain.links().stream().map(ArtifactProvenance::artifactKey).toList());
    }

    @Test
    @DisplayName("a chain whose artifacts reference each other terminates instead of looping")
    void chainTerminatesOnACycle() {
        JdbcRunStore store = storeAt(T0);
        UUID runId = store.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());

        // Artifact inputs are not a validated DAG — nothing stops a later version of A from naming B while
        // B names A. The graph's CycleDetector governs NODES, not artifact keys, so the walk must be the
        // thing that cannot loop. Left to a naive recursion this test would hang rather than fail, which
        // is why it exists.
        guardAt(T0).write(runId, "a", "first", "S1", List.of("b"), ArtifactWritePolicy.SERIALIZE);
        guardAt(T0.plusSeconds(10)).write(runId, "b", "second", "S2", List.of("a"),
                ArtifactWritePolicy.SERIALIZE);

        ArtifactProvenanceQuery.Chain chain = query.chainFor(runId, "a");
        assertEquals(List.of("a", "b"), chain.links().stream()
                .map(ArtifactProvenance::artifactKey).toList());
        assertTrue(chain.complete(), "both links resolve, so nothing is unresolved");
    }

    @Test
    @DisplayName("provenance carries the decisions already recorded when the artifact was written")
    void decisionsInForceAreThoseAlreadyRecorded() throws Exception {
        UUID runId = runWithAChain();

        recordGateDecision(runId, "S4", T0.plusSeconds(30));    // before the plan was written
        recordGateDecision(runId, "S11", T0.plusSeconds(300));  // after it

        ArtifactProvenance plan = query.chainFor(runId, "plan").links().get(0);

        assertEquals(List.of("S4"),
                plan.decisionsInForce().stream().map(RecordedDecision::gateId).toList(),
                "a gate decided after the write did not govern it; including it would let provenance "
                        + "imply an authorisation that did not exist at the time");
        RecordedDecision decision = plan.decisionsInForce().get(0);
        assertEquals("APPROVED", decision.outcome());
        assertEquals("the owner", decision.actorName());
        assertTrue(decision.repositoryRecordPath().startsWith("docs/governance/"),
                "CR-013: a decision is not usable until its repository record exists");
    }

    @Test
    @DisplayName("an artifact naming a producer the run does not have is INEXPRESSIBLE, not merely absent")
    void artifactCannotNameAProducerOutsideTheRun() {
        UUID runId = runWithAChain();

        // T081's guard: an artifact MUST NOT exist in a run without recorded provenance. The foreign key
        // on (run_id, produced_by_node_key) makes a producer the run does not have impossible to record —
        // a stronger guarantee than a query that looks for orphans and happens to find none today.
        //
        // S7.99 is a WELL-FORMED node key, so the CHECK on key shape passes and the foreign key is what
        // refuses it. A malformed key would prove the wrong constraint.
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> guardAt(T0).write(runId, "stray", "content", "S7.99",
                        List.of(), ArtifactWritePolicy.SERIALIZE));
        assertTrue(refused.getMessage().contains("stray"), refused.getMessage());

        assertEquals(List.of(), query.artifactsWithoutProvenance(runId),
                "the refused write must have left nothing behind");
    }

    @Test
    @DisplayName("a COMPLETED run returns a complete chain, and has no artifact without provenance")
    void completedRunHasACompleteChainAndNoOrphans() {
        UUID runId = runWithAChain();
        JdbcRunStore store = storeAt(T0.plusSeconds(600));
        store.transitionRun(runId, RunState.RUNNING, RunState.COMPLETED, "all stages done");

        assertEquals(RunState.COMPLETED, store.run(runId).orElseThrow().state());

        // T081's Validate clause is specifically "a provenance query over a COMPLETED run returns a complete
        // chain", and the first version of this class only checked orphans here while proving the chain
        // against a still-running run. Surfaced by the Slice 4 artifact-coverage sweep reading the task's
        // Artifact and Validate fields rather than re-reading the test: a clause that happens to be true but
        // is unasserted means a future change can break it in silence.
        ArtifactProvenanceQuery.Chain chain = query.chainFor(runId, "plan");
        assertTrue(chain.complete(), "unresolved: " + chain.unresolvedInputs());
        assertEquals(List.of("plan", "spec", "brief"),
                chain.links().stream().map(ArtifactProvenance::artifactKey).toList());

        assertEquals(List.of(), query.artifactsWithoutProvenance(runId),
                "T081's Done clause: no orphan artifacts in a completed run");
    }

    @Test
    @DisplayName("the orphan check is falsifiable — it reports an artifact whose producer is not a node")
    void orphanCheckIsFalsifiable() throws Exception {
        UUID runId = runWithAChain();
        assertEquals(List.of(), query.artifactsWithoutProvenance(runId), "clean to begin with");

        // INJECTED (AS-007). The application cannot reach this state — the foreign key prevents it and the
        // application role has no DELETE — so it is injected here purely to show that
        // artifactsWithoutProvenance() would SAY SO if the invariant were ever broken. An always-empty
        // result and a correct result are indistinguishable until one of them is disturbed.
        //
        // Done by disabling foreign-key triggers FOR THIS SESSION ONLY, so no schema is altered: the
        // container is shared with every other test in this tier, and a dropped constraint left behind
        // would weaken tests that have nothing to do with this one.
        try (Connection c = connections.get()) {
            try (PreparedStatement ps = c.prepareStatement("SET session_replication_role = replica")) {
                ps.execute();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO artifact_version (run_id, artifact_key, version, content_hash, "
                            + "produced_by_node_key, input_artifact_keys, produced_at) "
                            + "VALUES (?, 'injected-orphan', 1, repeat('a', 64), 'S7.99', '', now())")) {
                ps.setObject(1, runId);
                ps.executeUpdate();
            }
        }

        try {
            assertEquals(List.of("injected-orphan"), query.artifactsWithoutProvenance(runId),
                    "an artifact whose producer is not a node of the run must be REPORTED; a check that "
                            + "cannot fail is not a check");
        } finally {
            try (Connection c = connections.get();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM artifact_version WHERE run_id = ? "
                                 + "AND artifact_key = 'injected-orphan'")) {
                ps.setObject(1, runId);
                ps.executeUpdate();
            }
        }
    }

    private void recordGateDecision(UUID runId, String gateId, Instant decidedAt) throws Exception {
        String sql = "INSERT INTO gate_decision (run_id, gate_id, outcome, actor_type, actor_name, "
                + "reason, decided_at, repository_record_path) "
                + "VALUES (?, ?, 'APPROVED', 'human', 'the owner', ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, gateId);
            ps.setString(3, "approved at " + gateId);
            ps.setTimestamp(4, Timestamp.from(decidedAt));
            ps.setString(5, "docs/governance/gate-decisions/gate-" + gateId + ".md");
            ps.executeUpdate();
        }
    }
}
