package agentic.shortener.orchestration.graph;

import agentic.shortener.orchestration.state.StageState;
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
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T076a and T081 — concurrent downstream-artifact writes. FR-ORC-003, FR-ORC-005. EC-017.
 *
 * <p><strong>This is the one synchronization edge case the plan named and nothing covered</strong> —
 * analyze finding M7. FR-ORC-003's negative criterion is explicit: <em>"parallel branches MUST NOT
 * corrupt a shared downstream artifact."</em> T076's join-blocking test (EC-018) proves ordering
 * <em>at the join</em> and says nothing about concurrent writes <em>before</em> it.
 *
 * <p><strong>Both dispositions are proved, because T076a requires both.</strong> The choice is declared
 * per artifact class, and the two are genuinely different promises:
 *
 * <ul>
 *   <li>{@link ArtifactWritePolicy#SERIALIZE} — the second write lands <em>behind</em> the first as a
 *       new version. Correct where both contributions matter.
 *   <li>{@link ArtifactWritePolicy#REJECT_SECOND} — the second write is refused, and the refusal is
 *       recorded with the losing node named. Correct where a second author means somebody has
 *       misunderstood the topology.
 * </ul>
 *
 * <p>What must never happen under either policy is <strong>both appearing to succeed</strong> while one
 * write is silently discarded. That is last-write-wins, and the run would carry on holding an artifact
 * neither author recognises.
 *
 * <p>Integration tier, with real threads against a real unique index. ADR-011: a substitute with no
 * constraint cannot be the thing under test, and the race is decided inside PostgreSQL.
 */
@DisplayName("T076a/T081 concurrent artifact writes")
class ArtifactWriteGuardIT extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ConnectionSource connections;
    private JdbcRunStore store;
    private ArtifactWriteGuard guard;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        store = new JdbcRunStore(connections, CLOCK);
        guard = new ArtifactWriteGuard(connections, CLOCK);

        runId = store.createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
        // Two fan-out children under S7, which is the real shape EC-017 describes.
        for (int child = 1; child <= 2; child++) {
            store.appendNode(runId,
                    StageNode.fanOutChild("S7." + child, 7, "S7", "Implementation " + child),
                    List.of(new DependencyEdge("S7", "S7." + child, JoinSemantics.ALL),
                            new DependencyEdge("S7." + child, "S7.join", JoinSemantics.ALL)));
        }
    }

    // ==============================================================================================
    // T081 — provenance
    // ==============================================================================================

    @Test
    @DisplayName("T081: a written artifact records its producing node and its inputs")
    void provenanceIsRecorded() throws Exception {
        guard.write(runId, "design.md", "content one", "S7.1", List.of("tasks.md"),
                ArtifactWritePolicy.SERIALIZE);

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT version, content_hash, produced_by_node_key, input_artifact_keys "
                             + "FROM artifact_version WHERE run_id = ? AND artifact_key = ?")) {
            ps.setObject(1, runId);
            ps.setString(2, "design.md");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "the write must be recorded");
                assertEquals(1, rs.getInt("version"), "the first version is 1");
                assertEquals("S7.1", rs.getString("produced_by_node_key"));
                assertEquals("tasks.md", rs.getString("input_artifact_keys"));
                assertTrue(rs.getString("content_hash").matches("[0-9a-f]{64}"),
                        "the content hash is what identifies what was actually written");
            }
        }
    }

    @Test
    @DisplayName("T081's guard: an artifact cannot exist without recorded provenance")
    void noArtifactWithoutProvenance() {
        // produced_by_node_key is NOT NULL and foreign-keyed to a node in THIS run, so an artifact with
        // no producer, or one attributed to a node that does not exist, cannot be written at all.
        assertThrows(IllegalStateException.class,
                () -> guard.write(runId, "orphan.md", "content", "S9.7", List.of(),
                        ArtifactWritePolicy.SERIALIZE),
                "S9.7 is not a node in this run; attributing an artifact to it must fail");
    }

    @Test
    @DisplayName("T081: sequential writes by one node produce an ordered version chain")
    void versionsIncrementSequentially() {
        guard.write(runId, "design.md", "v1", "S7.1", List.of(), ArtifactWritePolicy.SERIALIZE);
        guard.write(runId, "design.md", "v2", "S7.1", List.of(), ArtifactWritePolicy.SERIALIZE);
        guard.write(runId, "design.md", "v3", "S7.1", List.of(), ArtifactWritePolicy.SERIALIZE);

        assertEquals(List.of(1, 2, 3), versionsOf("design.md"),
                "a provenance query must return a complete chain, not a set of unordered rows");
        assertEquals(3, distinctHashesOf("design.md").size(),
                "three different contents, three different hashes");
    }

    // ==============================================================================================
    // T076a — EC-017, disposition 1: SERIALIZE
    // ==============================================================================================

    @Test
    @DisplayName("EC-017 SERIALIZE: two concurrent writers both land, as two versions, in some order")
    void ec017Serialize() throws Exception {
        List<Outcome> outcomes = writeConcurrently("design.md", ArtifactWritePolicy.SERIALIZE);

        assertEquals(2, outcomes.stream().filter(Outcome::accepted).count(),
                "under SERIALIZE both contributions matter, so both must land: " + outcomes);

        // The versions are 1 and 2 — never both 1, which is the lost update.
        assertEquals(List.of(1, 2), versionsOf("design.md"),
                "exactly one content hash per logical artifact PER WRITE (T076a)");
        assertEquals(2, distinctHashesOf("design.md").size(),
                "and both writers' content survives; neither was overwritten");
    }

    @Test
    @DisplayName("EC-017 SERIALIZE: the second write is behind the first, never on top of it")
    void serializedWritesDoNotOverwrite() throws Exception {
        writeConcurrently("design.md", ArtifactWritePolicy.SERIALIZE);

        // The defining property. Under last-write-wins there would be ONE row and one author would
        // never know their work had gone.
        assertEquals(2, versionsOf("design.md").size(),
                "two writes, two rows. One row would mean a lost update");
    }

    // ==============================================================================================
    // T076a — EC-017, disposition 2: REJECT_SECOND
    // ==============================================================================================

    @Test
    @DisplayName("EC-017 REJECT_SECOND: exactly one write lands and the other is refused")
    void ec017RejectSecond() throws Exception {
        List<Outcome> outcomes = writeConcurrently("policy.md", ArtifactWritePolicy.REJECT_SECOND);

        assertEquals(1, outcomes.stream().filter(Outcome::accepted).count(),
                "exactly one write lands: " + outcomes);
        assertEquals(1, outcomes.stream().filter(o -> !o.accepted()).count(),
                "and the other is REFUSED, not silently dropped: " + outcomes);

        assertEquals(List.of(1), versionsOf("policy.md"), "one version only");
    }

    @Test
    @DisplayName("EC-017 REJECT_SECOND: the refusal is RECORDED with the losing node named")
    void rejectionIsRecordedWithTheLoser() throws Exception {
        List<Outcome> outcomes = writeConcurrently("policy.md", ArtifactWritePolicy.REJECT_SECOND);

        String loser = outcomes.stream().filter(o -> !o.accepted()).findFirst().orElseThrow().nodeKey();
        String winner = outcomes.stream().filter(Outcome::accepted).findFirst().orElseThrow().nodeKey();

        List<String> audits = auditReasonsFor("policy.md");
        assertEquals(1, audits.size(), "one refusal, one audit row: " + audits);
        assertTrue(audits.get(0).contains(loser),
                "T076a: the outcome must be recorded with the LOSING node named. Reason: " + audits.get(0));
        assertTrue(audits.get(0).contains(winner),
                "and naming the winner is what makes the record actionable: " + audits.get(0));
    }

    @Test
    @DisplayName("EC-017: under NEITHER policy do both writes appear to succeed with one discarded")
    void neverLastWriteWins() throws Exception {
        // The property both dispositions exist to guarantee, asserted across both so a future change
        // that broke one policy cannot hide behind the other passing.
        for (ArtifactWritePolicy policy : ArtifactWritePolicy.values()) {
            String key = "shared-" + policy.name().toLowerCase(java.util.Locale.ROOT) + ".md";
            List<Outcome> outcomes = writeConcurrently(key, policy);

            long accepted = outcomes.stream().filter(Outcome::accepted).count();
            int versions = versionsOf(key).size();

            assertEquals(accepted, versions,
                    policy + ": the number of writes that reported success must equal the number of "
                            + "rows. Anything else is a write that appeared to succeed and vanished");
            assertTrue(accepted >= 1, policy + ": at least one write must land");
        }
    }

    @Test
    @DisplayName("the policy is declared PER ARTIFACT CLASS, not globally")
    void policyIsPerArtifact() throws Exception {
        // T076a's Done: "with the choice declared per artifact class". A global setting would force one
        // answer on artifacts whose contributions have opposite meanings — a design document many nodes
        // append to, and a policy verdict only one node may write.
        writeConcurrently("appendable.md", ArtifactWritePolicy.SERIALIZE);
        writeConcurrently("single-author.md", ArtifactWritePolicy.REJECT_SECOND);

        assertEquals(2, versionsOf("appendable.md").size());
        assertEquals(1, versionsOf("single-author.md").size());
    }

    @Test
    @DisplayName("a sequential second write under REJECT_SECOND is refused too")
    void rejectSecondIsNotOnlyAboutRaces() throws Exception {
        // The policy is about how many authors an artifact may have, not about timing. A second write
        // that arrives a minute later is the same violation and must get the same answer.
        guard.write(runId, "verdict.md", "first", "S7.1", List.of(),
                ArtifactWritePolicy.REJECT_SECOND);

        ArtifactWriteGuard.Result second = guard.write(runId, "verdict.md", "second", "S7.2",
                List.of(), ArtifactWritePolicy.REJECT_SECOND);

        assertFalse(second.accepted());
        assertTrue(second.reason().contains("S7.1"), "the winner must be named: " + second.reason());
        assertEquals(List.of(1), versionsOf("verdict.md"));
    }

    // ==============================================================================================
    // helpers
    // ==============================================================================================

    private record Outcome(String nodeKey, boolean accepted, String reason) {
    }

    /** Two fan-out children writing one artifact at the same instant, released by one latch. */
    private List<Outcome> writeConcurrently(String artifactKey, ArtifactWritePolicy policy)
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int child = 1; child <= 2; child++) {
                String nodeKey = "S7." + child;
                String content = "content from " + nodeKey;
                Callable<Outcome> task = () -> {
                    ArtifactWriteGuard.Result result =
                            guard.write(runId, artifactKey, content, nodeKey, List.of(), policy);
                    return new Outcome(nodeKey, result.accepted(), result.reason());
                };
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "threads did not reach the barrier");
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "writes did not finish");

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Integer> versionsOf(String artifactKey) {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT version FROM artifact_version WHERE run_id = ? AND artifact_key = ? "
                             + "ORDER BY version")) {
            ps.setObject(1, runId);
            ps.setString(2, artifactKey);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> versions = new ArrayList<>();
                while (rs.next()) {
                    versions.add(rs.getInt(1));
                }
                return versions;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read versions", e);
        }
    }

    private Set<String> distinctHashesOf(String artifactKey) {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT content_hash FROM artifact_version WHERE run_id = ? AND artifact_key = ?")) {
            ps.setObject(1, runId);
            ps.setString(2, artifactKey);
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> hashes = new java.util.HashSet<>();
                while (rs.next()) {
                    hashes.add(rs.getString(1));
                }
                return hashes;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read hashes", e);
        }
    }

    private List<String> auditReasonsFor(String artifactKey) {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT reason FROM audit_record WHERE run_id = ? AND affected_artifact = ? "
                             + "AND result = 'REJECTED' ORDER BY audit_record_id")) {
            ps.setObject(1, runId);
            ps.setString(2, artifactKey);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> reasons = new ArrayList<>();
                while (rs.next()) {
                    reasons.add(rs.getString(1));
                }
                return reasons;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read audit records", e);
        }
    }
}
