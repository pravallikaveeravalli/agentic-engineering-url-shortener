package agentic.shortener.orchestration.lineage;

import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Follows an artifact back to its roots. Task T081. FR-ORC-005.
 *
 * <p>{@link agentic.shortener.orchestration.graph.ArtifactWriteGuard} writes provenance down; this reads
 * it back. T081 asks for both, and they are different claims: a run can record a producing node for every
 * artifact and still be unable to answer <em>where did this plan come from</em>, which is the question
 * FR-ORC-005 actually poses.
 *
 * <h2>A missing link is reported, never smoothed over</h2>
 *
 * <p>{@code input_artifact_keys} is a list of keys, not a set of foreign keys — a stage can name an input
 * that was never written, and the store has no way to refuse it. So the walk collects
 * {@link Chain#unresolvedInputs()} and the chain reports itself {@link Chain#complete() incomplete}. The
 * alternative — returning the part that resolved, as though that were the whole lineage — is how a
 * provenance record comes to assert a chain it cannot substantiate, which is the same failure
 * {@code RunStateDurabilityIT} guards against for run state.
 *
 * <h2>The walk cannot loop</h2>
 *
 * <p>Artifact keys are not a validated DAG. {@code CycleDetector} governs <em>nodes</em>, and nothing
 * stops a later version of A from naming B as an input while B names A. A recursive descent would hang
 * rather than fail, so this is breadth-first over an explicit queue with a visited set — the same reason
 * {@code CycleDetector} is iterative.
 */
public final class ArtifactProvenanceQuery {

    private static final String SELECT_LATEST =
            "SELECT artifact_key, version, content_hash, produced_by_node_key, input_artifact_keys, "
                    + "produced_at FROM artifact_version WHERE run_id = ? AND artifact_key = ? "
                    + "ORDER BY version DESC LIMIT 1";

    private static final String SELECT_VERSION =
            "SELECT artifact_key, version, content_hash, produced_by_node_key, input_artifact_keys, "
                    + "produced_at FROM artifact_version WHERE run_id = ? AND artifact_key = ? "
                    + "AND version = ?";

    /**
     * Decisions recorded at or before the moment of the write.
     *
     * <p>{@code <=} rather than {@code <}: a decision recorded in the same instant as the write is one the
     * write could have been made under, and a fixed-clock run makes that the common case rather than a
     * curiosity.
     */
    private static final String SELECT_DECISIONS_IN_FORCE =
            "SELECT gate_id, outcome, actor_name, decided_at, repository_record_path "
                    + "FROM gate_decision WHERE run_id = ? AND decided_at <= ? ORDER BY decided_at";

    /**
     * Artifacts whose producing node is not a node of the run.
     *
     * <p>Under the foreign key this can only ever return nothing, and that is the point: T081's guard is
     * that an artifact MUST NOT exist without recorded provenance, and the constraint makes the violation
     * unreachable rather than merely unobserved. The query exists so the invariant is
     * <em>checkable</em> — a guarantee nothing can report on is a guarantee nobody can audit — and
     * {@code ArtifactProvenanceIT} injects a violation to prove it would be reported.
     */
    private static final String SELECT_ORPHANS =
            "SELECT DISTINCT av.artifact_key FROM artifact_version av "
                    + "WHERE av.run_id = ? AND NOT EXISTS ("
                    + "  SELECT 1 FROM stage_node sn "
                    + "  WHERE sn.run_id = av.run_id AND sn.node_key = av.produced_by_node_key) "
                    + "ORDER BY av.artifact_key";

    private final ConnectionSource connections;

    public ArtifactProvenanceQuery(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    /**
     * The chain, ordered from the artifact asked about back towards its roots.
     *
     * @param links            one entry per resolved artifact, breadth-first from {@code artifactKey}
     * @param unresolvedInputs input keys named by some link that no {@code artifact_version} row matches
     */
    public record Chain(List<ArtifactProvenance> links, List<String> unresolvedInputs) {

        public Chain {
            links = List.copyOf(links);
            unresolvedInputs = List.copyOf(unresolvedInputs);
        }

        /** True when every input named by every link resolved to a link of its own. */
        public boolean complete() {
            return unresolvedInputs.isEmpty();
        }
    }

    public Chain chainFor(UUID runId, String artifactKey) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(artifactKey, "artifactKey");

        List<ArtifactProvenance> links = new ArrayList<>();
        // LinkedHashSet for both: a stable order makes the result reproducible, and a set is what stops
        // the walk revisiting a key it has already followed.
        Set<String> visited = new LinkedHashSet<>();
        Set<String> unresolved = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();

        pending.add(artifactKey);
        visited.add(artifactKey);

        while (!pending.isEmpty()) {
            String key = pending.removeFirst();
            Optional<ArtifactProvenance> link = latestVersionOf(runId, key);
            if (link.isEmpty()) {
                // The artifact asked about may itself not exist; that is an unresolved input too, and
                // reporting an empty chain as complete would be the exact dishonesty this guards against.
                unresolved.add(key);
                continue;
            }
            links.add(link.get());
            for (String input : link.get().inputArtifactKeys()) {
                if (visited.add(input)) {
                    pending.addLast(input);
                }
            }
        }
        return new Chain(links, List.copyOf(unresolved));
    }

    /** One named version, so a superseded artifact stays followable after a revision lands behind it. */
    public Optional<ArtifactProvenance> versionOf(UUID runId, String artifactKey, int version) {
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(SELECT_VERSION)) {
            ps.setObject(1, runId);
            ps.setString(2, artifactKey);
            ps.setInt(3, version);
            return readOne(runId, ps);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to read version " + version + " of " + artifactKey, e);
        }
    }

    private Optional<ArtifactProvenance> latestVersionOf(UUID runId, String artifactKey) {
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(SELECT_LATEST)) {
            ps.setObject(1, runId);
            ps.setString(2, artifactKey);
            return readOne(runId, ps);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read provenance for " + artifactKey, e);
        }
    }

    private Optional<ArtifactProvenance> readOne(UUID runId, PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            Instant producedAt = rs.getTimestamp("produced_at").toInstant();
            return Optional.of(new ArtifactProvenance(
                    rs.getString("artifact_key"),
                    rs.getInt("version"),
                    rs.getString("content_hash"),
                    rs.getString("produced_by_node_key"),
                    parseInputs(rs.getString("input_artifact_keys")),
                    producedAt,
                    decisionsInForceAt(runId, producedAt)));
        }
    }

    /**
     * The stored form is a comma-separated list, and an empty column means no inputs.
     *
     * <p>{@code String.split} on an empty string yields one empty element rather than none, which would
     * turn a root artifact into one with a nameless input — and then into an unresolved link.
     */
    private static List<String> parseInputs(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return List.of(stored.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private List<RecordedDecision> decisionsInForceAt(UUID runId, Instant producedAt) {
        List<RecordedDecision> decisions = new ArrayList<>();
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(SELECT_DECISIONS_IN_FORCE)) {
            ps.setObject(1, runId);
            ps.setTimestamp(2, java.sql.Timestamp.from(producedAt));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    decisions.add(new RecordedDecision(
                            rs.getString("gate_id"),
                            rs.getString("outcome"),
                            rs.getString("actor_name"),
                            rs.getTimestamp("decided_at").toInstant(),
                            rs.getString("repository_record_path")));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read the decisions in force for run " + runId, e);
        }
        return decisions;
    }

    /** Empty in a sound run. See {@link #SELECT_ORPHANS} for why it is asked at all. */
    public List<String> artifactsWithoutProvenance(UUID runId) {
        List<String> orphans = new ArrayList<>();
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(SELECT_ORPHANS)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orphans.add(rs.getString(1));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to check run " + runId + " for orphan artifacts", e);
        }
        return List.copyOf(orphans);
    }
}
