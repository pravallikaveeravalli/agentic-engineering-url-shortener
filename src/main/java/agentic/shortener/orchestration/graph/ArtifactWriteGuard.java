package agentic.shortener.orchestration.graph;

import agentic.shortener.orchestration.store.ArtifactWritePolicy;
import agentic.shortener.persistence.ConnectionSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Serializes or refuses concurrent writes to one downstream artifact. Task T076a, T081.
 * FR-ORC-003, FR-ORC-005. EC-017.
 *
 * <p><strong>The one synchronization edge case the plan named and nothing covered</strong> — analyze
 * finding M7. FR-ORC-003's negative criterion is that parallel branches MUST NOT corrupt a shared
 * downstream artifact, and T076's join-blocking test proves ordering <em>at the join</em> while saying
 * nothing about concurrent writes <em>before</em> it.
 *
 * <p><strong>The unique constraint decides the race, not this class.</strong> Both writers read the
 * current version, both compute the next one, and both attempt to insert it;
 * {@code UNIQUE (run_id, artifact_key, version)} means exactly one succeeds. That is the same shape as
 * the short-code constraint in V1: the database is the authority and the application reacts to its
 * answer. A read-then-check here would be a race in the code that exists to prevent one.
 *
 * <p><strong>What neither policy permits is last-write-wins.</strong> Under {@code SERIALIZE} the loser
 * retries onto the next version so both contributions survive; under {@code REJECT_SECOND} the loser is
 * refused and the refusal is recorded with its own node named. What cannot happen is both reporting
 * success while one write is discarded — the run would then hold an artifact neither author recognises.
 *
 * <p><strong>Every refusal is recorded.</strong> An {@code audit_record} row carries the six mandatory
 * fields, and its reason names both the losing and the winning node. A refusal nobody can find is
 * indistinguishable from a write that never happened, and T076a requires the outcome recorded with the
 * losing node named.
 */
public final class ArtifactWriteGuard {

    /**
     * How many times a {@code SERIALIZE} write re-reads and retries.
     *
     * <p>Bounded for the same reason the code generator's retry is: a systematic problem — many writers
     * hammering one artifact — should surface as an error rather than as a process that never returns.
     */
    private static final int MAX_SERIALIZE_ATTEMPTS = 8;

    /**
     * @param version  the version that landed, or the version that was already there on a refusal
     * @param reason   empty when accepted; on a refusal it names the losing and the winning node
     */
    public record Result(boolean accepted, int version, String reason) {
    }

    private final ConnectionSource connections;
    private final Clock clock;

    public ArtifactWriteGuard(ConnectionSource connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Writes one version of a logical artifact, honouring the policy declared for its class.
     *
     * @throws IllegalStateException if the write cannot be attempted at all — an unknown producing node,
     *                               an unreachable store. A <em>refused</em> write is not an exception:
     *                               it is an outcome, and the caller is told which node won
     */
    public Result write(UUID runId, String artifactKey, String content, String producingNodeKey,
                        List<String> inputArtifactKeys, ArtifactWritePolicy policy) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(artifactKey, "artifactKey");
        Objects.requireNonNull(producingNodeKey, "producingNodeKey");
        Objects.requireNonNull(policy, "policy");

        String contentHash = sha256Hex(content);
        String inputs = String.join(",", inputArtifactKeys);

        for (int attempt = 1; attempt <= MAX_SERIALIZE_ATTEMPTS; attempt++) {
            int next = currentVersion(runId, artifactKey) + 1;

            if (policy == ArtifactWritePolicy.REJECT_SECOND && next > 1) {
                // Not only about races. The policy is about how many authors an artifact may have, so a
                // second write arriving a minute later is the same violation and gets the same answer.
                return refuse(runId, artifactKey, producingNodeKey, next - 1);
            }

            try {
                insertVersion(runId, artifactKey, next, contentHash, producingNodeKey, inputs);
                return new Result(true, next, "");
            } catch (Exception e) {
                // ConnectionSource.get() throws Exception, so the narrow catch is not available. The
                // discrimination that matters is still exact: a unique violation is the constraint doing
                // its job, and anything else is a failure.
                if (!(e instanceof SQLException sql && isUniqueViolation(sql))) {
                    throw new IllegalStateException(
                            "failed to write artifact " + artifactKey + " for node "
                                    + producingNodeKey, e);
                }
                // Somebody else took this version. Which is not an error — it is the constraint doing
                // the job this class exists to delegate to it.
                if (policy == ArtifactWritePolicy.REJECT_SECOND) {
                    return refuse(runId, artifactKey, producingNodeKey, next);
                }
                // SERIALIZE: go round again and land behind the winner.
            }
        }
        throw new IllegalStateException("could not serialize a write to " + artifactKey + " in "
                + MAX_SERIALIZE_ATTEMPTS + " attempts; suspect a hot artifact rather than bad luck");
    }

    /** PostgreSQL SQLSTATE for unique_violation — the value this whole class rests on. */
    private static boolean isUniqueViolation(SQLException e) {
        return "23505".equals(e.getSQLState());
    }

    private int currentVersion(UUID runId, String artifactKey) {
        String sql = "SELECT COALESCE(MAX(version), 0) FROM artifact_version "
                + "WHERE run_id = ? AND artifact_key = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, artifactKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read the current version of " + artifactKey, e);
        }
    }

    private void insertVersion(UUID runId, String artifactKey, int version, String contentHash,
                               String producingNodeKey, String inputs) throws Exception {
        String sql = "INSERT INTO artifact_version (run_id, artifact_key, version, content_hash, "
                + "produced_by_node_key, input_artifact_keys, produced_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, artifactKey);
            ps.setInt(3, version);
            ps.setString(4, contentHash);
            ps.setString(5, producingNodeKey);
            ps.setString(6, inputs);
            ps.setTimestamp(7, Timestamp.from(clock.instant()));
            ps.executeUpdate();
        }
    }

    /**
     * Refuses the write and records it, naming both nodes.
     *
     * <p>The audit row is what makes this an outcome rather than a silent drop. T076a requires the
     * outcome recorded with the losing node named; the winner is named too, because a record that says
     * only who lost leaves the reader to work out who they lost to.
     */
    private Result refuse(UUID runId, String artifactKey, String losingNodeKey, int existingVersion) {
        String winner = producerOf(runId, artifactKey, existingVersion);
        String reason = "artifact write refused: node " + losingNodeKey + " lost to node " + winner
                + " for artifact " + artifactKey + " at version " + existingVersion
                + "; the declared policy for this artifact class is REJECT_SECOND (EC-017, FR-ORC-003)";

        recordRefusal(runId, artifactKey, reason);
        return new Result(false, existingVersion, reason);
    }

    private String producerOf(UUID runId, String artifactKey, int version) {
        String sql = "SELECT produced_by_node_key FROM artifact_version "
                + "WHERE run_id = ? AND artifact_key = ? AND version = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            ps.setString(2, artifactKey);
            ps.setInt(3, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "unknown";
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to identify the winning producer", e);
        }
    }

    private void recordRefusal(UUID runId, String artifactKey, String reason) {
        String sql = "INSERT INTO audit_record (actor_type, action, occurred_at, affected_artifact, "
                + "result, reason, run_id, correlation_id) "
                + "VALUES ('system', 'ARTIFACT_WRITE', ?, ?, 'REJECTED', ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(clock.instant()));
            ps.setString(2, artifactKey);
            ps.setString(3, reason);
            ps.setObject(4, runId);
            ps.setObject(5, correlationIdOf(runId));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("failed to record an artifact-write refusal", e);
        }
    }

    /** The run's correlation id, so the refusal ties to the same logs, metrics and traces. */
    private UUID correlationIdOf(UUID runId) {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT correlation_id FROM workflow_run WHERE run_id = ?")) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("no such run: " + runId);
                }
                return rs.getObject(1, UUID.class);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read the run's correlation id", e);
        }
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(String.valueOf(content).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }
}
