package agentic.shortener.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Base class providing a real PostgreSQL 16 per suite. Task T016.
 *
 * <p>ADR-011 (Accepted): a real store for data paths, injected fakes for reliability proofs. Mocks
 * and in-memory substitutes are prohibited where the property needs a real mechanism — a unique
 * constraint under contention (FR-URL-006) and a store restart (CL-009) cannot be proven against H2
 * or a mock, because neither has the mechanism being tested.
 *
 * <p>Named {@code *IntegrationTest} so Failsafe claims it and Surefire excludes it (T017). The fast
 * tier must stay container-free: a red-green loop that needs Docker gets abandoned, and then TDD
 * becomes a claim rather than a practice.
 *
 * <h2>Why {@link #awaitReachableFromTestProcess()} exists</h2>
 *
 * <p>Testcontainers' wait strategy confirms the database is ready <em>inside</em> the container. On a
 * Linux-VM runtime (Colima, Lima, Rancher Desktop) the published port is then forwarded to the host
 * by a separate process, and that forwarder can lag container readiness by a second or more. The
 * result is a test that connects to the mapped port and gets {@code Connection refused} against a
 * database that is perfectly healthy.
 *
 * <p>The property a test actually needs is not "the store is ready" but <strong>"the store is
 * reachable from this process"</strong>. That is what this waits for. It is a real gap in the
 * harness rather than a workaround for one machine: any runtime with an out-of-process forwarder has
 * the same race, and a harness that passes only when the forwarder happens to win is flaky by
 * construction.
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

    private static final Duration REACHABILITY_TIMEOUT = Duration.ofSeconds(60);

    @BeforeAll
    static void awaitReachableFromTestProcess() {
        String host = POSTGRES.getHost();
        int port = POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
        long deadline = System.nanoTime() + REACHABILITY_TIMEOUT.toNanos();
        Exception last = null;

        while (System.nanoTime() < deadline) {
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress(host, port), 2_000);
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    fail("interrupted while waiting for " + host + ":" + port, ie);
                }
            }
        }
        fail("the store never became reachable from this process at " + host + ":" + port
                + " within " + REACHABILITY_TIMEOUT.toSeconds() + "s. The container may be healthy "
                + "while the host port forward is not — see this class's javadoc.", last);
    }

    protected static Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /**
     * T016's own validation: a trivial round-trip against the container. Deliberately a test rather
     * than a claim in a comment — the harness proving it works is the artifact T016 asks for.
     */
    @Test
    void trivialRoundTripAgainstARealStore() throws Exception {
        assertTrue(POSTGRES.isRunning(), "the container must actually be running");

        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS harness_round_trip");
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
