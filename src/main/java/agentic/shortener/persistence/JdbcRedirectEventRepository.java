package agentic.shortener.persistence;

import agentic.shortener.domain.analytics.RedirectEvent;
import agentic.shortener.domain.analytics.RedirectEventRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JDBC implementation of {@link RedirectEventRepository}. Task T026. FR-URL-010. ADR-014.
 *
 * <p>Insert and select only. <strong>There is no update and no delete statement in this class</strong>,
 * matching the interface\u2019s deliberate omission: CR-017 retains every record indefinitely, and T027\u2019s
 * privilege grants make that structural rather than conventional.
 *
 * <p>ADR-014\u2019s failure isolation \u2014 a failed append must not fail the redirect (EC-012) \u2014 belongs to
 * the caller that owns the separate transaction, not here: this class reports the failure honestly and
 * the redirect path decides to count rather than propagate it. Swallowing it here would hide the
 * failure from PVT-009\u2019s counter, which is the thing that makes the ceiling observable at all.
 */
public final class JdbcRedirectEventRepository implements RedirectEventRepository {

    private final ConnectionSource connections;

    public JdbcRedirectEventRepository(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections, "connections");
    }

    @Override
    public RedirectEvent append(RedirectEvent event) {
        Objects.requireNonNull(event, "event");
        String sql = "INSERT INTO redirect_event (short_code, occurred_at) VALUES (?, ?)";
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, event.shortCode());
            ps.setTimestamp(2, Timestamp.from(event.occurredAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0L;
                return RedirectEvent.of(id, event.shortCode(), event.occurredAt());
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "failed to append a redirect event for " + event.shortCode(), e);
        }
    }

    @Override
    public List<RedirectEvent> findByShortCodeBetween(String shortCode, Instant from, Instant to) {
        Objects.requireNonNull(shortCode, "shortCode");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        String sql = "SELECT redirect_event_id, short_code, occurred_at FROM redirect_event "
                + "WHERE short_code = ? AND occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at";
        List<RedirectEvent> found = new ArrayList<>();
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, shortCode);
            ps.setTimestamp(2, Timestamp.from(from));
            ps.setTimestamp(3, Timestamp.from(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.add(RedirectEvent.of(
                            rs.getLong("redirect_event_id"),
                            rs.getString("short_code"),
                            rs.getTimestamp("occurred_at").toInstant()));
                }
            }
            return List.copyOf(found);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read redirect events for " + shortCode, e);
        }
    }

    @Override
    public long countByShortCode(String shortCode) {
        Objects.requireNonNull(shortCode, "shortCode");
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM redirect_event WHERE short_code = ?")) {
            ps.setString(1, shortCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to count redirect events for " + shortCode, e);
        }
    }
}
