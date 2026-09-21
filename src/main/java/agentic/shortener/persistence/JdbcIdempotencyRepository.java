package agentic.shortener.persistence;

import agentic.shortener.domain.idempotency.IdempotencyRecord;
import agentic.shortener.domain.idempotency.IdempotencyRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link IdempotencyRepository}. Task T026. FR-URL-012, CL-008.
 *
 * <p>Both statements key on {@code (creator_id, marker)} together, matching V2's composite primary
 * key. Keying on the marker alone would let one creator's string collide with another\u2019s \u2014 a
 * correctness bug and a cross-tenant leak at once (KE-03).
 */
public final class JdbcIdempotencyRepository implements IdempotencyRepository {

    private final ConnectionSource connections;

    public JdbcIdempotencyRepository(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public IdempotencyRecord save(IdempotencyRecord record) {
        Objects.requireNonNull(record, "record");
        String sql = "INSERT INTO idempotency_record "
                + "(creator_id, marker, request_fingerprint, short_code, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, record.creatorId());
            ps.setString(2, record.marker());
            ps.setString(3, record.requestFingerprint());
            ps.setString(4, record.shortCode());
            ps.setTimestamp(5, Timestamp.from(record.createdAt()));
            ps.executeUpdate();
            return record;
        } catch (Exception e) {
            throw new IllegalStateException("failed to save idempotency record " + record.scopedKey(), e);
        }
    }

    @Override
    public Optional<IdempotencyRecord> find(UUID creatorId, String marker) {
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(marker, "marker");
        String sql = "SELECT creator_id, marker, request_fingerprint, short_code, created_at "
                + "FROM idempotency_record WHERE creator_id = ? AND marker = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, creatorId);
            ps.setString(2, marker);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(IdempotencyRecord.rehydrate(
                        rs.getObject("creator_id", UUID.class),
                        rs.getString("marker"),
                        rs.getString("request_fingerprint").trim(),
                        rs.getString("short_code"),
                        rs.getTimestamp("created_at").toInstant()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read idempotency record", e);
        }
    }
}
