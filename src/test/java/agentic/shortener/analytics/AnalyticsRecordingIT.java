package agentic.shortener.analytics;

import agentic.shortener.domain.analytics.AnalyticsRecordingPort;
import agentic.shortener.domain.analytics.RedirectEvent;
import agentic.shortener.domain.analytics.RedirectEventRepository;
import agentic.shortener.contract.OpenApiConformanceTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T050's two proofs through the running application. FR-URL-010, EC-012. ADR-014.
 *
 * <p><strong>EC-012.</strong> The redirect must succeed while the analytics append is forced to fail.
 * This is the inversion the task exists to prevent: a resolvable link going dark because a counter could
 * not be written. Proved by making the event store throw and then following a live link.
 *
 * <p><strong>Durability across a store restart is {@code AnalyticsDurabilityIT}'s</strong>, and it is a
 * separate class for a reason found here: restarting this class's container left the Spring connection
 * pool holding handles to a server that no longer answered on that address, and the next test in the
 * class failed with 503 for a reason that had nothing to do with it. Durability is a property of the
 * store, so it is proved against the store directly, with no pool in the way.
 *
 * <p><strong>This class owns its own container</strong> so that its injected fault cannot affect any
 * other test class — the same reason {@code ReadinessDegradationIT} owns its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T050 analytics recording: EC-012 and durability")
class AnalyticsRecordingIT {

    /** This class's own store, so restarting it cannot affect any other test class. */
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
    }

    private static final AtomicBoolean APPEND_BROKEN = new AtomicBoolean(false);

    /**
     * ADR-011's sanctioned injected fake: the real repository until the switch is thrown, so only the
     * reliability proof runs against a substitute.
     */
    @TestConfiguration
    static class FaultInjection {

        @Bean
        @Primary
        RedirectEventRepository failableEvents(
                agentic.shortener.persistence.ConnectionSource connections) {
            RedirectEventRepository real =
                    new agentic.shortener.persistence.JdbcRedirectEventRepository(connections);
            return new RedirectEventRepository() {
                @Override
                public RedirectEvent append(RedirectEvent event) {
                    if (APPEND_BROKEN.get()) {
                        throw new IllegalStateException("failed to append redirect event");
                    }
                    return real.append(event);
                }

                @Override
                public List<RedirectEvent> findByShortCode(String shortCode) {
                    return real.findByShortCode(shortCode);
                }

                @Override
                public List<RedirectEvent> findByShortCodeBetween(String c, Instant f, Instant t) {
                    return real.findByShortCodeBetween(c, f, t);
                }

                @Override
                public long countByShortCode(String shortCode) {
                    return real.countByShortCode(shortCode);
                }
            };
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OpenApiConformanceTest harness = new OpenApiConformanceTest();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AnalyticsRecordingPort recorder;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String createLink(String destination) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"" + destination + "\"}", headers), String.class);
        assertEquals(201, response.getStatusCode().value(), response.getBody());
        return JSON.readTree(response.getBody()).get("shortCode").asText();
    }

    private JsonNode analytics(String code) throws Exception {
        ResponseEntity<String> response =
                rest.getForEntity(url("/v1/links/" + code + "/analytics"), String.class);
        assertEquals(200, response.getStatusCode().value(), response.getBody());
        harness.assertConforms("/v1/links/{shortCode}/analytics", "get", 200, response.getBody());
        return JSON.readTree(response.getBody());
    }

    @Test
    @DisplayName("a follow appends one event, and the owner can read it")
    void aFollowIsRecorded() throws Exception {
        String code = createLink("https://example.com/t050/recorded");
        assertEquals(0, analytics(code).get("totalRedirects").asLong(), "nothing followed yet");

        assertEquals(307, rest.getForEntity(url("/" + code), String.class).getStatusCode().value());
        assertEquals(307, rest.getForEntity(url("/" + code), String.class).getStatusCode().value());

        JsonNode view = analytics(code);
        assertEquals(2, view.get("totalRedirects").asLong(), "one event per redirect (FR-URL-010)");
        assertEquals(2, view.get("events").size());
        assertTrue(view.get("events").get(0).has("occurredAt"));
        assertEquals(1, view.get("events").get(0).size(),
                "timestamp only — an event carries exactly one field (CL-002, NFR-SEC-005)");
    }

    @Test
    @DisplayName("EC-012: the redirect SUCCEEDS while the append is forced to fail")
    void ec012RedirectSurvivesAppendFailure() throws Exception {
        String code = createLink("https://example.com/t050/ec012");
        long failedBefore = recorder.failed();

        APPEND_BROKEN.set(true);
        try {
            ResponseEntity<String> response = rest.getForEntity(url("/" + code), String.class);

            assertEquals(307, response.getStatusCode().value(),
                    "the redirect must still work: a resolvable link going dark because a counter "
                            + "could not be written is the inversion EC-012 forbids");
            assertEquals("https://example.com/t050/ec012", response.getHeaders().getFirst("Location"),
                    "and it must still point at the right place");
        } finally {
            APPEND_BROKEN.set(false);
        }

        assertEquals(failedBefore + 1, recorder.failed(),
                "the failure must be COUNTED, not swallowed — PVT-009 permits counted failures and no "
                        + "silent ones (ADR-014)");
    }

    @Test
    @DisplayName("EC-012: nothing about the failure reaches the follower")
    void appendFailureIsInvisibleToTheFollower() throws Exception {
        String code = createLink("https://example.com/t050/invisible");

        ResponseEntity<String> healthy = rest.getForEntity(url("/" + code), String.class);
        APPEND_BROKEN.set(true);
        ResponseEntity<String> degraded;
        try {
            degraded = rest.getForEntity(url("/" + code), String.class);
        } finally {
            APPEND_BROKEN.set(false);
        }

        // Byte-identical, because a follower is not entitled to know the service's counters are having
        // trouble — and a difference here would be a signal an attacker could use to detect degradation.
        assertEquals(healthy.getStatusCode(), degraded.getStatusCode());
        assertEquals(healthy.getHeaders().getFirst("Location"),
                degraded.getHeaders().getFirst("Location"));
        assertEquals(String.valueOf(healthy.getBody()), String.valueOf(degraded.getBody()));
    }

    @Test
    @DisplayName("the analytics response never carries a follower-identifying field")
    void noFollowerIdentifyingField() throws Exception {
        String code = createLink("https://example.com/t050/minimal");
        rest.getForEntity(url("/" + code), String.class);

        String body = rest.getForEntity(url("/v1/links/" + code + "/analytics"), String.class)
                .getBody();
        for (String forbidden : List.of("ip", "userAgent", "user-agent", "referrer", "referer",
                "device", "geo", "country", "session")) {
            assertFalse(String.valueOf(body).toLowerCase(java.util.Locale.ROOT).contains(forbidden),
                    "'" + forbidden + "' appears in the analytics response: " + body);
        }
    }

}
