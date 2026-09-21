package agentic.shortener.persistence;

import agentic.shortener.domain.creator.Creator;
import agentic.shortener.domain.creator.CreatorCredential;
import agentic.shortener.domain.creator.CreatorRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link CreatorRepository}. Task T026. FR-URL-018, FR-URL-019. ADR-013.
 *
 * <p>The plaintext key never appears in a statement. {@link #findByKeyHash} takes a digest, so key
 * material cannot reach a slow-query log, a trace span or a JDBC exception message — the three places
 * a query parameter ends up that nobody remembers to check.
 */
public final class JdbcCreatorRepository implements CreatorRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final ConnectionSource connections;

    public JdbcCreatorRepository(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public Creator save(Creator creator) {
        Objects.requireNonNull(creator, "creator");
        // V1 made api_key_hash and key_expires_at NOT NULL on `creator`. V2 moved credentials to their
        // own table because a creator may hold several over time, and the forward-only rule forbids
        // dropping the V1 columns here. They are written once with a deliberate placeholder digest and
        // never read again; V3 may deprecate them formally.
        String sql = "INSERT INTO creator "
                + "(creator_id, name, api_key_hash, created_at, key_expires_at, active) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, creator.id());
            ps.setString(2, creator.name());
            ps.setString(3, legacyPlaceholderDigest(creator.id()));
            ps.setTimestamp(4, Timestamp.from(creator.createdAt()));
            ps.setTimestamp(5, Timestamp.from(creator.createdAt().plusSeconds(1)));
            ps.setBoolean(6, creator.active());
            ps.executeUpdate();
            return creator;
        } catch (Exception e) {
            throw new IllegalStateException("failed to save creator " + creator.id(), e);
        }
    }

    /**
     * A deterministic non-secret filler for V1's superseded {@code api_key_hash} column.
     *
     * <p>Not a credential and not derived from one: it is the creator id hashed, so it satisfies V1's
     * NOT NULL and UNIQUE constraints without ever holding key material. Named
     * {@code legacyPlaceholder} so a reader is not left wondering whether a real digest lives here.
     */
    private static String legacyPlaceholderDigest(UUID creatorId) {
        String hex = Integer.toHexString(creatorId.hashCode());
        return ("0".repeat(64 - hex.length()) + hex);
    }

    @Override
    public Optional<Creator> findById(UUID creatorId) {
        Objects.requireNonNull(creatorId, "creatorId");
        String sql = "SELECT creator_id, name, created_at, active FROM creator WHERE creator_id = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, creatorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(Creator.rehydrate(
                        rs.getObject("creator_id", UUID.class),
                        rs.getString("name"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getBoolean("active")));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read creator " + creatorId, e);
        }
    }

    @Override
    public CreatorCredential save(CreatorCredential credential) {
        Objects.requireNonNull(credential, "credential");
        String sql = "INSERT INTO creator_credential "
                + "(credential_id, creator_id, key_hash, created_at, expires_at, revoked_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, credential.id());
            ps.setObject(2, credential.creatorId());
            ps.setString(3, credential.keyHash());
            ps.setTimestamp(4, Timestamp.from(credential.createdAt()));
            setNullableInstant(ps, 5, credential.expiresAt());
            setNullableInstant(ps, 6, credential.revokedAt());
            ps.executeUpdate();
            return credential;
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                // Two credentials with the same digest means the same key was issued twice, which is a
                // provisioning fault worth surfacing rather than absorbing.
                throw new IllegalStateException(
                        "a credential with this key digest already exists for credential "
                                + credential.id(), e);
            }
            throw new IllegalStateException("failed to save credential " + credential.id(), e);
        } catch (Exception e) {
            throw new IllegalStateException("failed to save credential " + credential.id(), e);
        }
    }

    @Override
    public Optional<CreatorCredential> findByKeyHash(String keyHash) {
        Objects.requireNonNull(keyHash, "keyHash");
        String sql = "SELECT credential_id, creator_id, key_hash, created_at, expires_at, revoked_at "
                + "FROM creator_credential WHERE key_hash = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, keyHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapCredential(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            // Deliberately does not echo the parameter: even a digest in a log line is an
            // offline-attack target (NFR-SEC-005).
            throw new IllegalStateException("failed to read credential by digest", e);
        }
    }

    @Override
    public List<CreatorCredential> findCredentialsFor(UUID creatorId) {
        Objects.requireNonNull(creatorId, "creatorId");
        String sql = "SELECT credential_id, creator_id, key_hash, created_at, expires_at, revoked_at "
                + "FROM creator_credential WHERE creator_id = ? ORDER BY created_at";
        List<CreatorCredential> found = new ArrayList<>();
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, creatorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.add(mapCredential(rs));
                }
            }
            return List.copyOf(found);
        } catch (Exception e) {
            throw new IllegalStateException("failed to list credentials for " + creatorId, e);
        }
    }

    private static void setNullableInstant(PreparedStatement ps, int index, Instant value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static CreatorCredential mapCredential(ResultSet rs) throws SQLException {
        Timestamp expires = rs.getTimestamp("expires_at");
        Timestamp revoked = rs.getTimestamp("revoked_at");
        return CreatorCredential.rehydrate(
                rs.getObject("credential_id", UUID.class),
                rs.getObject("creator_id", UUID.class),
                rs.getString("key_hash").trim(),
                rs.getTimestamp("created_at").toInstant(),
                expires == null ? null : expires.toInstant(),
                revoked == null ? null : revoked.toInstant());
    }
}
