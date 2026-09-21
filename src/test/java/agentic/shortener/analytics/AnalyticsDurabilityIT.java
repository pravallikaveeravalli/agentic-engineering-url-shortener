package agentic.shortener.analytics;

import agentic.shortener.domain.analytics.AnalyticsRecordingPort;
import agentic.shortener.domain.analytics.RedirectEvent;
import agentic.shortener.domain.creator.Creator;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.JdbcCreatorRepository;
import agentic.shortener.persistence.JdbcRedirectEventRepository;
import agentic.shortener.persistence.JdbcShortLinkRepository;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.persistence.analytics.TransactionalAnalyticsRecorder;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * T050's durability half: <strong>an accepted event survives a store restart.</strong> FR-URL-010, ADR-014.
 *
 * <p>The recorder increments {@code accepted()} only after the append returns. That is a claim about
 * durability, and this is where the claim is checked rather than reasoned about: the container is actually
 * restarted, and the event is actually read back over a connection opened afterwards.
 *
 * <p><strong>No Spring, and that is the point.</strong> This began inside
 * {@code AnalyticsRecordingIT} and did not work there. Restarting the container left the Hikari pool
 * holding handles to an address the server no longer answered on, so the read afterwards returned 503 and
 * so did <em>the next test in the class</em>. Durability is a property of the store, so it is proved
 * against the store directly. The pool's own recovery behaviour is a different question and does not
 * belong in the same assertion.
 *
 * <p><strong>Restarting the container rather than the server process.</strong> {@code pg_ctl restart} was
 * tried first: PostgreSQL is PID 1 in this image, so shutting the server down kills the container and the
 * exec returns 137. A container restart keeps the data volume and gives a genuinely new postmaster reading
 * the same files from disk, which is the property under test. The host port can move across a restart, so
 * the new mapping is read back from Docker rather than assumed.
 */
@DisplayName("T050 an accepted analytics event survives a store restart")
class AnalyticsDurabilityIT {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    /** This class's own store, because it restarts it. */
    private static final PostgreSQLContainer<?> OWN_STORE =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("shortener")
                    .withUsername("shortener")
                    .withPassword("shortener");

    static {
        OWN_STORE.start();
    }

    @AfterAll
    static void stopOwnStore() {
        OWN_STORE.stop();
    }

    /**
     * The host address the container is published on <em>right now</em>.
     *
     * <p>Read from Docker on every call rather than cached. Testcontainers snapshots the container's
     * inspection at start, and a restart can republish the port elsewhere — a cached mapping would make
     * every post-restart connection fail for a reason that looks like lost data.
     */
    private static String currentJdbcUrl() {
        InspectContainerResponse info = OWN_STORE.getDockerClient()
                .inspectContainerCmd(OWN_STORE.getContainerId()).exec();
        Ports.Binding[] bindings = info.getNetworkSettings().getPorts().getBindings()
                .get(new ExposedPort(PostgreSQLContainer.POSTGRESQL_PORT));
        assertTrue(bindings != null && bindings.length > 0,
                "the container publishes no host port for PostgreSQL");
        return "jdbc:postgresql://" + OWN_STORE.getHost() + ":"
                + bindings[0].getHostPortSpec() + "/shortener";
    }

    private static ConnectionSource connections() {
        return () -> DriverManager.getConnection(currentJdbcUrl(), "shortener", "shortener");
    }

    @Test
    @DisplayName("an event the recorder accepted is still there after the store restarts")
    void acceptedEventSurvivesARestart() throws Exception {
        // The same wait PostgresIntegrationTest documents, needed here for the same reason: Testcontainers
        // confirms readiness INSIDE the container, and on a Linux-VM runtime the host port forward can lag
        // that by a second or more. This class does not extend that harness, so it does its own waiting —
        // without it the migration below fails with "connection refused" against a healthy database.
        awaitReachable();
        MigrationSupport.migrate(OWN_STORE, "public");

        ConnectionSource connections = connections();
        UUID creator = UUID.randomUUID();
        new JdbcCreatorRepository(connections).save(Creator.create(creator, "durability creator", NOW));
        String code = "Durable";
        new JdbcShortLinkRepository(connections).save(ShortLink.create(
                code, "https://example.com/t050/durability", creator, NOW, NOW.plusSeconds(86_400)));

        JdbcRedirectEventRepository events = new JdbcRedirectEventRepository(connections);
        AnalyticsRecordingPort recorder = new TransactionalAnalyticsRecorder(events);

        recorder.record(code, NOW.plusSeconds(1));
        recorder.record(code, NOW.plusSeconds(2));
        assertEquals(2, recorder.accepted(), "the recorder must have accepted both");
        assertEquals(0, recorder.failed());
        assertEquals(2, events.findByShortCode(code).size(), "visible before the restart");

        // The restart. A new postmaster, the same files.
        OWN_STORE.getDockerClient().restartContainerCmd(OWN_STORE.getContainerId()).exec();
        awaitReachable();

        // Read over a connection opened after the restart, through a repository built after it too, so
        // nothing in this assertion can be served from something cached before the store went away.
        List<RedirectEvent> after = new JdbcRedirectEventRepository(connections()).findByShortCode(code);
        assertEquals(2, after.size(),
                "an event the recorder counted as accepted did NOT survive the restart, which would "
                        + "make accepted() a claim the store never honoured");
        assertEquals(NOW.plusSeconds(1), after.get(0).occurredAt(), "and in time order");
        assertEquals(NOW.plusSeconds(2), after.get(1).occurredAt());

        // And the store is writable again, not merely readable.
        recorder.record(code, NOW.plusSeconds(3));
        assertEquals(3, new JdbcRedirectEventRepository(connections()).findByShortCode(code).size());
    }

    private static void awaitReachable() {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                String url = currentJdbcUrl();
                String host = url.substring(url.indexOf("//") + 2, url.lastIndexOf(':'));
                int port = Integer.parseInt(
                        url.substring(url.lastIndexOf(':') + 1, url.lastIndexOf('/')));
                try (Socket probe = new Socket()) {
                    probe.connect(new InetSocketAddress(host, port), 2_000);
                }
                // A reachable socket is not an accepting database: PostgreSQL may still be replaying WAL.
                // The query is the real readiness check, so it is what this waits for.
                try (Connection c = DriverManager.getConnection(url, "shortener", "shortener")) {
                    c.createStatement().execute("SELECT 1");
                    return;
                }
            } catch (IOException | RuntimeException | java.sql.SQLException e) {
                last = e;
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    fail("interrupted while waiting for the restarted store", ie);
                }
            }
        }
        fail("the store never accepted a query again after the restart", last);
    }
}
