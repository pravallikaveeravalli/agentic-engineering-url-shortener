package agentic.shortener.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T032's unproven half: <strong>readiness degrades when the store is stopped; liveness does not.</strong>
 *
 * <p>Raised by the overclaim sweep ordered at Gate 7. T032's Done condition names this behaviour, and
 * {@code WalkingSkeletonIT} asserted only that the two probes differ in <em>shape</em> while the store
 * was up. The degradation itself — the half that actually matters operationally — was unproven, and
 * T032 was marked complete and used to close Slice 2 without it.
 *
 * <p><strong>This class owns its own container and stops it.</strong> The shared harness container is
 * static and reused across the suite, so stopping that one would break every later class. Isolating
 * the damage is the only way to test a store outage without making the rest of the suite
 * order-dependent.
 *
 * <p>Why this matters more than a shape assertion: if liveness consulted the store, a store outage
 * would fail liveness and an orchestrator would restart a process that was working perfectly. That is
 * the failure FR-URL-015 separates the two probes to prevent, and it can only be demonstrated by
 * taking the store away.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T032 readiness degrades on store outage; liveness does not")
class ReadinessDegradationIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** This class's own store, so stopping it cannot affect any other test class. */
    private static final PostgreSQLContainer<?> OWN_STORE =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("shortener")
                    .withUsername("shortener")
                    .withPassword("shortener");

    static {
        OWN_STORE.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", OWN_STORE::getJdbcUrl);
        registry.add("spring.datasource.username", OWN_STORE::getUsername);
        registry.add("spring.datasource.password", OWN_STORE::getPassword);
        // Fail fast rather than block: a readiness probe that waits 30 seconds for a dead store is
        // useless to an orchestrator, which will have given up long before.
        registry.add("spring.datasource.hikari.connection-timeout", () -> "3000");
        registry.add("spring.datasource.hikari.initialization-fail-timeout", () -> "-1");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("store stopped: readiness goes DOWN with 503, liveness stays UP with 200")
    void readinessDegradesButLivenessDoesNot() throws Exception {
        // Baseline, so a later DOWN cannot be mistaken for never having been UP.
        ResponseEntity<String> readyBefore = rest.getForEntity(url("/health/ready"), String.class);
        assertEquals(HttpStatus.OK, readyBefore.getStatusCode(),
                "readiness must be UP before the store is stopped, or this test proves nothing");
        assertEquals("UP", JSON.readTree(readyBefore.getBody()).path("status").asText());

        OWN_STORE.stop();

        try {
            // Readiness must now fail closed (T018): report unavailable rather than accept traffic it
            // cannot serve.
            ResponseEntity<String> readyAfter = rest.getForEntity(url("/health/ready"), String.class);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, readyAfter.getStatusCode(),
                    "readiness must report 503 with the store stopped; got "
                            + readyAfter.getStatusCode() + " body " + readyAfter.getBody());
            assertEquals("DOWN", JSON.readTree(readyAfter.getBody()).path("status").asText());
            assertEquals("DOWN",
                    JSON.readTree(readyAfter.getBody()).path("checks").path("store").asText(),
                    "the named check must say which dependency failed");

            // And the assertion this class exists for: liveness is UNAFFECTED.
            ResponseEntity<String> liveAfter = rest.getForEntity(url("/health/live"), String.class);
            assertEquals(HttpStatus.OK, liveAfter.getStatusCode(),
                    "liveness must stay 200 with the store stopped. If it fails here, an orchestrator "
                            + "would restart a healthy process on a dependency outage — the exact "
                            + "failure FR-URL-015 separates the two probes to prevent");
            assertEquals("UP", JSON.readTree(liveAfter.getBody()).path("status").asText());

            // The two must actually disagree, which is the whole point.
            assertNotEquals(readyAfter.getStatusCode(), liveAfter.getStatusCode(),
                    "with the store down the two probes MUST differ; identical answers mean one is an "
                            + "alias of the other");

            // Still no leak, even on the failure path — the path most likely to over-share.
            String raw = String.valueOf(readyAfter.getBody());
            assertTrue(!raw.contains("jdbc:"), "a failing readiness must not expose a connection string");
            assertTrue(!raw.contains("Exception"), "no exception class may reach the response (T018)");
            assertTrue(!raw.toLowerCase().contains("password"), "no credential may appear: " + raw);
        } finally {
            // Nothing else uses this container, but leaving a stopped one behind would be untidy and
            // Ryuk will reap it either way.
            OWN_STORE.close();
        }
    }
}
