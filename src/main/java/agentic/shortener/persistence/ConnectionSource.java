package agentic.shortener.persistence;

import java.sql.Connection;

/**
 * Supplies a JDBC connection. Task T026.
 *
 * <p>A one-method interface rather than a {@code DataSource} parameter, so the repository
 * implementations stay constructible from a test without a Spring context. The framework wires a
 * {@code DataSource::getConnection} in production (T032); a test passes a lambda over the container.
 *
 * <p>This lives in {@code persistence} rather than {@code domain}: it is a persistence concern, and
 * the domain's repository interfaces must not know that connections exist (NFR-MNT-002).
 */
@FunctionalInterface
public interface ConnectionSource {

    Connection get() throws Exception;
}
