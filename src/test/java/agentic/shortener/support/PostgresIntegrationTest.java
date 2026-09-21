package agentic.shortener.support;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base class providing a real PostgreSQL 16 per suite. Task T016.
 *
 * <p>ADR-011 (Accepted): a real store for data paths, injected fakes for reliability proofs. Mocks
 * and in-memory substitutes are prohibited where the property needs a real mechanism — a unique
 * constraint under contention (FR-URL-006) and a store restart (CL-009) cannot be proven against
 * H2 or a mock, because neither has the mechanism being tested.
 *
 * <p>This class is named {@code *IntegrationTest} so Failsafe claims it and Surefire excludes it
 * (T017). The fast tier must stay container-free: a red-green loop that needs Docker gets
 * abandoned, and then TDD becomes a claim rather than a practice.
 *
 * <p>The container is {@code static}, so one PostgreSQL starts per suite rather than per test.
 */
@Testcontainers
public abstract class PostgresIntegrationTest {

    /** ADR-002 fixes PostgreSQL 16. The tag is pinned, not floating, so the tier is reproducible. */
    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("shortener")
                    .withUsername("shortener")
                    .withPassword("shortener");

    protected static Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /**
     * T016's own validation: a trivial round-trip against the container. This is deliberately a
     * test on the base class rather than a claim in a comment — the harness proving it works is
     * the artifact T016 is asked for.
     */
    @Test
    void trivialRoundTripAgainstARealStore() throws Exception {
        assertTrue(POSTGRES.isRunning(), "the container must actually be running");

        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE harness_round_trip (id INT PRIMARY KEY, note TEXT NOT NULL)");
            s.execute("INSERT INTO harness_round_trip (id, note) VALUES (1, 'real store')");

            try (ResultSet rs = s.executeQuery("SELECT note FROM harness_round_trip WHERE id = 1")) {
                assertTrue(rs.next(), "the row written must be readable back");
                assertEquals("real store", rs.getString("note"));
            }

            // Prove it is the store ADR-002 chose, not whatever happened to be available.
            try (ResultSet rs = s.executeQuery("SELECT version()")) {
                assertTrue(rs.next());
                String version = rs.getString(1);
                assertTrue(version.contains("PostgreSQL 16"),
                        "ADR-002 fixes PostgreSQL 16; got: " + version);
            }
        }
    }
}
