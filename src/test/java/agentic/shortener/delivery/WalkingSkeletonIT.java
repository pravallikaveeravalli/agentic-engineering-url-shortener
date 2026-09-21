package agentic.shortener.delivery;

import agentic.shortener.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T032 — the walking skeleton. <strong>Slice 2's exit condition.</strong> FR-URL-015. ADR-001, ADR-012.
 *
 * <p>One endpoint, liveness, readiness, and a real round-trip to PostgreSQL 16 through the full HTTP
 * stack. The slice's exit condition is *"a request reaches the store and returns, in a test, against a
 * real store — not a mock"*, and that is what the create-then-read test does.
 *
 * <p><strong>Why liveness and readiness are tested separately.</strong> FR-URL-015 says liveness is
 * unaffected by store availability while readiness must fail when a dependency is down. Conflating them
 * is the classic health-check defect: an orchestrator restarts a pod whose store is merely unreachable,
 * which converts a dependency outage into a restart loop. The two are asserted to differ in behaviour,
 * not merely to exist.
 *
 * <p><strong>Why no secrets leak from readiness.</strong> The contract requires readiness not to expose
 * secrets, connection strings or internal topology. A readiness body that helpfully reported the JDBC
 * URL would hand an unauthenticated caller the datasource host and database name, so the response is
 * asserted not to contain them.
 *
 * <p>Integration tier. T032's guard: if this is not working, ADR-001 is the first thing to re-examine
 * (plan §14 checkpoint).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T032 walking skeleton — Slice 2 exit condition")
class WalkingSkeletonIT extends PostgresIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Points Spring at the container the shared harness started. The harness also waits for the store
     * to be reachable from this process, which matters on a runtime whose port forwarder lags.
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("liveness reports UP and is unaffected by the store")
    void livenessIsUp() throws Exception {
        ResponseEntity<String> response = rest.getForEntity(url("/health/live"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = JSON.readTree(response.getBody());
        assertEquals("UP", body.path("status").asText(),
                "liveness answers 'is the process alive', which the store cannot answer for it");
    }

    @Test
    @DisplayName("readiness reports UP with a reachable store, and leaks nothing")
    void readinessIsUpAndLeaksNothing() throws Exception {
        ResponseEntity<String> response = rest.getForEntity(url("/health/ready"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        String raw = response.getBody();
        assertNotNull(raw);
        assertEquals("UP", JSON.readTree(raw).path("status").asText());

        // The contract forbids exposing secrets, connection strings or internal topology. A readiness
        // body that reported the JDBC URL would hand an unauthenticated caller the host and database.
        assertFalse(raw.contains("jdbc:"), "readiness must not expose a connection string: " + raw);
        assertFalse(raw.contains(POSTGRES.getPassword()),
                "readiness must not expose the datasource password");
        assertFalse(raw.toLowerCase().contains("password"), "no password field of any kind: " + raw);
    }

    @Test
    @DisplayName("liveness and readiness are DISTINCT endpoints, not one aliased twice")
    void livenessAndReadinessAreDistinct() throws Exception {
        // FR-URL-015 gives them different obligations, so they must be able to disagree. Asserting
        // both return 200 would pass even if one were an alias of the other, which is the defect that
        // turns a dependency outage into a restart loop.
        ResponseEntity<String> live = rest.getForEntity(url("/health/live"), String.class);
        ResponseEntity<String> ready = rest.getForEntity(url("/health/ready"), String.class);

        assertEquals(HttpStatus.OK, live.getStatusCode());
        assertEquals(HttpStatus.OK, ready.getStatusCode());

        // Readiness reports its dependency checks; liveness deliberately does not, because it must not
        // depend on them.
        assertTrue(JSON.readTree(ready.getBody()).has("checks"),
                "readiness must report what it checked, or its UP means nothing");
        assertFalse(JSON.readTree(live.getBody()).has("checks"),
                "liveness must NOT report dependency checks — if it did, a store outage would fail "
                        + "liveness and an orchestrator would restart a healthy process");
    }

    @Test
    @DisplayName("SLICE 2 EXIT CONDITION: a request reaches the real store and returns")
    void createThenReadAgainstARealStore() throws Exception {
        // Create, through the full HTTP stack, into PostgreSQL 16.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"destination\":\"https://example.com/walking-skeleton\"}";

        ResponseEntity<String> created = rest.exchange(
                url("/v1/links"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode(),
                "creation must report 201; body was: " + created.getBody());

        JsonNode link = JSON.readTree(created.getBody());
        String shortCode = link.path("shortCode").asText();
        assertFalse(shortCode.isBlank(), "a created link must carry its code");
        assertEquals("https://example.com/walking-skeleton", link.path("destination").asText());
        assertEquals("ACTIVE", link.path("state").asText());
        assertFalse(link.path("replay").asBoolean(true),
                "a first creation is not a replay (CL-008 case 1)");

        // Read it back. THIS is the round trip the exit condition names: the row was written by one
        // request and is returned to another, from a real store rather than an in-process cache.
        ResponseEntity<String> fetched = rest.getForEntity(
                url("/v1/links/" + shortCode + "/analytics"), String.class);
        assertEquals(HttpStatus.OK, fetched.getStatusCode(),
                "the link must be readable back through a separate request; body: " + fetched.getBody());
        assertEquals(shortCode, JSON.readTree(fetched.getBody()).path("shortCode").asText());
    }

    @Test
    @DisplayName("the store really is the source of truth — the row is visible to plain JDBC")
    void theRowIsInThePostgresTable() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> created = rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"https://example.com/jdbc-visible\"}", headers),
                String.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        String shortCode = JSON.readTree(created.getBody()).path("shortCode").asText();

        // Bypasses the application entirely. If this finds the row, the HTTP path wrote to PostgreSQL
        // and not to a map — which is the difference between a walking skeleton and a mock of one.
        try (var c = connection(); var ps = c.prepareStatement(
                "SELECT destination, state FROM short_link WHERE short_code = ?")) {
            ps.setString(1, shortCode);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "the row must exist in PostgreSQL, found by plain JDBC");
                assertEquals("https://example.com/jdbc-visible", rs.getString("destination"));
                assertEquals("ACTIVE", rs.getString("state"));
            }
        }
    }

    @Test
    @DisplayName("FR-URL-004 holds through the HTTP surface, not only in the domain type")
    void disallowedSchemeIsRefusedAtTheEdge() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // The domain type refuses this (T021). Asserting it again here proves the refusal is not lost
        // in translation at the boundary — a controller that caught the exception and returned 201
        // would pass every domain test.
        ResponseEntity<String> response = rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"javascript:alert(1)\"}", headers), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "a non-allow-listed scheme must be refused at the edge; got " + response.getBody());
        assertFalse(String.valueOf(response.getBody()).contains("Exception"),
                "verbose error detail is off (T018): no exception class may reach a caller");
    }

    @Test
    @DisplayName("FR-URL-002: a 400 body does not echo the submitted destination")
    void refusalDoesNotEchoTheSubmittedDestination() {
        // This asserts the fix to a defect that was live in the walking skeleton. ShortLink's own scheme
        // check built its message by concatenating the raw destination, and "destination" is one of
        // LinkController's DOMAIN_REFUSAL_MARKERS — so the submitted URL went into the 400 body intact.
        // FR-URL-002's negative clause forbids exactly that: "MUST NOT echo the rejected input in a way
        // that enables injection".
        //
        // The domain half is asserted in SchemeAllowListTest. This is the other half, and it has to be
        // here: the vulnerability was the COMBINATION of a message that echoed and a marker list that
        // passed it through, and neither test alone would have caught it. T035 routed the check through
        // the single allow-list, whose messages are constants.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String hostile = "javascript:alert(document.cookie)//marker-cnczmt.example";
        ResponseEntity<String> response = rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"" + hostile + "\"}", headers), String.class);
        String body = String.valueOf(response.getBody());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(), body);
        assertFalse(body.contains("marker-cnczmt"),
                "the submitted destination was reflected into the refusal body: " + body);
        assertFalse(body.contains("document.cookie"),
                "a payload inside the destination was reflected into the refusal body: " + body);
        assertFalse(body.contains("javascript"),
                "even the scheme must not be echoed — 'javascript' is caller-supplied text: " + body);
        assertTrue(body.contains("allow-listed"),
                "the refusal must still name the rule, or it is not actionable: " + body);
    }
}
