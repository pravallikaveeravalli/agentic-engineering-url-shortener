package agentic.shortener.persistence;

import agentic.shortener.domain.link.LinkState;
import agentic.shortener.domain.link.ShortCodeCollisionException;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link ShortLinkRepository}. Task T026. FR-URL-006, FR-URL-014. ADR-002.
 *
 * <p><strong>Uniqueness is the database's, not this class's.</strong> {@link #save} inserts and
 * translates the constraint violation; it never selects first to see whether the code is free. A
 * check-then-insert would pass a single-threaded test and fail under contention, because another
 * writer commits between the two statements. {@code ShortLinkRepositoryIT} runs eight real threads at
 * one code to make that difference visible rather than arguable.
 *
 * <p>Plain JDBC rather than JPA: the repository interface is the seam ADR-002 relies on, and the
 * mapping here is small enough that an ORM would add a second model without removing any work.
 */
public final class JdbcShortLinkRepository implements ShortLinkRepository {

    /** PostgreSQL SQLSTATE for unique_violation. This is the value FR-URL-006 rests on. */
    private static final String UNIQUE_VIOLATION = "23505";
    /** SQLSTATE for foreign_key_violation — a link whose creator does not exist (KE-01). */
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private final ConnectionSource connections;

    public JdbcShortLinkRepository(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public ShortLink save(ShortLink link) {
        Objects.requireNonNull(link, "link");
        String sql = "INSERT INTO short_link "
                + "(short_code, destination, creator_id, created_at, expires_at, state) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, link.shortCode());
            ps.setString(2, link.destination());
            ps.setObject(3, link.creatorId());
            ps.setTimestamp(4, Timestamp.from(link.createdAt()));
            ps.setTimestamp(5, Timestamp.from(link.expiresAt()));
            ps.setString(6, link.state().name());
            ps.executeUpdate();
            return link;
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                // Translated into a domain exception so ADR-007's retry caller can act on it without
                // importing the persistence framework, which NFR-MNT-002 forbids.
                throw new ShortCodeCollisionException(link.shortCode(), e);
            }
            if (FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
                throw new IllegalStateException(
                        "no creator " + link.creatorId() + " exists: KE-01 has no state for a link "
                                + "without an owner", e);
            }
            throw new IllegalStateException("failed to save link " + link.shortCode(), e);
        } catch (Exception e) {
            throw new IllegalStateException("failed to save link " + link.shortCode(), e);
        }
    }

    @Override
    public Optional<ShortLink> findByShortCode(String shortCode) {
        Objects.requireNonNull(shortCode, "shortCode");
        String sql = "SELECT short_code, destination, creator_id, created_at, expires_at, state "
                + "FROM short_link WHERE short_code = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, shortCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read link " + shortCode, e);
        }
    }

    @Override
    public ShortLink expire(ShortLink link) {
        Objects.requireNonNull(link, "link");
        ShortLink expired = link.expire();
        // Guarded by the current state so the transition can only ever run ACTIVE -> EXPIRED, even if
        // two callers compensate the same link concurrently. The reverse update is unreachable.
        String sql = "UPDATE short_link SET state = ? WHERE short_code = ? AND state = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, LinkState.EXPIRED.name());
            ps.setString(2, link.shortCode());
            ps.setString(3, LinkState.ACTIVE.name());
            ps.executeUpdate();
            return expired;
        } catch (Exception e) {
            throw new IllegalStateException("failed to expire link " + link.shortCode(), e);
        }
    }

    @Override
    public long countByCreator(UUID creatorId) {
        Objects.requireNonNull(creatorId, "creatorId");
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM short_link WHERE creator_id = ?")) {
            ps.setObject(1, creatorId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to count links for " + creatorId, e);
        }
    }

    private static ShortLink map(ResultSet rs) throws SQLException {
        // getTimestamp(...).toInstant() rather than getObject(..., Instant.class): the former is
        // explicit about the conversion, and an instant read back through a different default zone is
        // a classic silent corruption. The IT compares instants for exactly that reason.
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
        return ShortLink.rehydrate(
                rs.getString("short_code"),
                rs.getString("destination"),
                rs.getObject("creator_id", UUID.class),
                createdAt,
                expiresAt,
                LinkState.valueOf(rs.getString("state")));
    }
}
