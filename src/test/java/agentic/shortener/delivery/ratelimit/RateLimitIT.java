package agentic.shortener.delivery.ratelimit;

import agentic.shortener.contract.OpenApiConformanceTest;
import agentic.shortener.domain.creator.CreatorRepository;
import agentic.shortener.support.PostgresIntegrationTest;
import agentic.shortener.support.TestCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T054 and T055 through HTTP. FR-URL-016. EC-013.
 *
 * <p>The unit tests prove the counters. This proves the limits are <strong>on the path</strong> — the
 * distinction that mattered in Group D, where {@code AbuseGuard} had fourteen passing tests and nothing
 * ever called it. A limiter nobody invokes is not a limit.
 *
 * <p><strong>The limits are lowered for this class only</strong>, through configuration rather than by
 * touching the limiter. Sending 601 requests to observe PVT-013 would prove the same thing and add
 * seconds to every build; the numbers themselves are asserted against the approved targets in
 * {@code RateLimiterTest}, which reads the shipped {@code application.yml}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "shortener.ratelimit.creation-per-creator-per-minute=5",
                "shortener.ratelimit.redirect-per-code-per-minute=4",
                "shortener.ratelimit.aggregate-per-creator-per-minute=6"
        })
@DisplayName("T054/T055/T136a rate limiting through HTTP")
class RateLimitIT extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OpenApiConformanceTest harness = new OpenApiConformanceTest();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CreatorRepository creators;

    @Autowired
    private Clock clock;

    private TestCredentials.Provisioned caller;

    @BeforeEach
    void provision() {
        // The client must NOT retry. Apache HttpClient honours Retry-After on a 429 by default, and it
        // slept out the whole sixty-second window, retried, and handed this test the reset budget — three
        // tests failed with "expected 429 but was 201" after exactly 60.06 seconds each. The throttling
        // was working; the harness was hiding it. See RestFixtures.
        agentic.shortener.support.RestFixtures.withoutRetries(rest);
        // A fresh creator per test, so one test's spent budget is never another's failure.
        caller = TestCredentials.provision(creators, clock);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> create(String destination) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", caller.header());
        return rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"" + destination + "\"}", headers), String.class);
    }

    @Test
    @DisplayName("creation is throttled at the configured limit, with 429 and the tier named")
    void creationIsThrottled() throws Exception {
        for (int i = 1; i <= 5; i++) {
            assertEquals(201, create("https://example.com/t054/" + i).getStatusCode().value(),
                    "creation " + i + " is within the limit");
        }

        ResponseEntity<String> throttled = create("https://example.com/t054/over");
        assertEquals(429, throttled.getStatusCode().value(), throttled.getBody());
        harness.assertConforms("/v1/links", "post", 429, throttled.getBody());

        JsonNode body = JSON.readTree(throttled.getBody());
        assertEquals("RATE_LIMITED", body.get("code").asText());
        assertTrue(body.get("detail").asText().contains(CreationRateLimiter.TIER),
                "the throttled outcome must name which tier was exceeded (FR-URL-016): " + body);
        assertNotNull(throttled.getHeaders().getFirst("Retry-After"),
                "a refusal without a retry hint is a dead end");
    }

    @Test
    @DisplayName("one creator's spent budget does not throttle another")
    void creatorsAreIndependentThroughHttp() {
        for (int i = 1; i <= 6; i++) {
            create("https://example.com/t054/exhaust/" + i);
        }
        assertEquals(429, create("https://example.com/t054/exhausted").getStatusCode().value());

        caller = TestCredentials.provision(creators, clock);
        assertEquals(201, create("https://example.com/t054/other-creator").getStatusCode().value(),
                "the tier is PER CREATOR: a second creator must start with a full budget");
    }

    @Test
    @DisplayName("redirects are throttled per code, with 429 and the per-code tier named")
    void redirectsAreThrottledPerCode() throws Exception {
        String code = JSON.readTree(create("https://example.com/t055/hot").getBody())
                .get("shortCode").asText();

        for (int i = 1; i <= 4; i++) {
            assertEquals(307, rest.getForEntity(url("/" + code), String.class).getStatusCode().value(),
                    "follow " + i + " is within the limit");
        }

        ResponseEntity<String> throttled = rest.getForEntity(url("/" + code), String.class);
        assertEquals(429, throttled.getStatusCode().value(), throttled.getBody());
        harness.assertConforms("/{shortCode}", "get", 429, throttled.getBody());

        JsonNode body = JSON.readTree(throttled.getBody());
        assertEquals("RATE_LIMITED", body.get("code").asText());
        assertTrue(body.get("detail").asText().contains("per-code"),
                "the follower must be told which tier, and only which tier: " + body);
    }

    @Test
    @DisplayName("GUARD: a throttled follower is told nothing about the owning creator")
    void throttledFollowerLearnsNothingAboutTheCreator() throws Exception {
        // FR-URL-016's negative clause. The follower is anonymous by design (FR-URL-018), and a 429 that
        // named the owner would turn hotspot protection into an ownership oracle.
        String code = JSON.readTree(create("https://example.com/t055/private-owner").getBody())
                .get("shortCode").asText();
        for (int i = 0; i < 5; i++) {
            rest.getForEntity(url("/" + code), String.class);
        }

        ResponseEntity<String> throttled = rest.getForEntity(url("/" + code), String.class);
        assertEquals(429, throttled.getStatusCode().value());

        String text = String.valueOf(throttled.getBody());
        assertFalse(text.contains(caller.creatorId().toString()),
                "the owning creator's id reached a public follower: " + text);
        assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains("creator"),
                "not even the word: the tier name is per-code for exactly this reason: " + text);
        // Every header too — Retry-After is fine, an owner hint is not.
        throttled.getHeaders().forEach((name, values) ->
                assertFalse(String.join(",", values).contains(caller.creatorId().toString()),
                        "header " + name + " leaked the creator id"));
    }

    @Test
    @DisplayName("hotspot protection is per code: one hammered link does not take another down")
    void oneHammeredLinkDoesNotAffectAnother() throws Exception {
        String hot = JSON.readTree(create("https://example.com/t055/hammered").getBody())
                .get("shortCode").asText();
        String quiet = JSON.readTree(create("https://example.com/t055/quiet").getBody())
                .get("shortCode").asText();

        for (int i = 0; i < 6; i++) {
            rest.getForEntity(url("/" + hot), String.class);
        }
        assertEquals(429, rest.getForEntity(url("/" + hot), String.class).getStatusCode().value());
        assertEquals(307, rest.getForEntity(url("/" + quiet), String.class).getStatusCode().value(),
                "a per-code tier must not become a global one");
    }

    @Test
    @DisplayName("T136a AFTER-STATE: traffic spread across links IS throttled once the aggregate limit is hit")
    void aggregateTrafficIsThrottledAfterT136a() throws Exception {
        // Was aggregateTrafficPassesUnthrottled, DS-B's own before-state
        // (docs/delivery/baseline-omissions.md entry 1, closed by T136a). The aggregate limit is lowered
        // to 6 for this class only (see the properties above), the same pattern already used for the
        // other two tiers.
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            codes.add(JSON.readTree(create("https://example.com/t055/spread/" + i).getBody())
                    .get("shortCode").asText());
        }

        // Six follows, spread so no single code approaches its own per-code limit of four -- only the
        // AGGREGATE tier, shared across all four codes by the same creator, can be what throttles the 7th.
        int[] rotation = {0, 1, 2, 3, 0, 1};
        for (int i = 0; i < 6; i++) {
            assertEquals(307, rest.getForEntity(url("/" + codes.get(rotation[i])), String.class)
                    .getStatusCode().value(), "follow " + (i + 1) + " of 6 is within the aggregate limit");
        }

        ResponseEntity<String> throttled = rest.getForEntity(url("/" + codes.get(2)), String.class);
        assertEquals(429, throttled.getStatusCode().value(), throttled.getBody());
        harness.assertConforms("/{shortCode}", "get", 429, throttled.getBody());

        JsonNode body = JSON.readTree(throttled.getBody());
        assertEquals("RATE_LIMITED", body.get("code").asText());
        // Unlike the per-code tier (whose own non-disclosure test below refuses even the WORD "creator"),
        // this tier's own name legitimately names the DIMENSION it throttles on -- "per-creator-aggregate"
        // -- exactly as the already-existing, already-authenticated CreationRateLimiter.TIER
        // ("per-creator-creation") already does. Naming which rule fired is what FR-URL-016 requires
        // (1f); it is a different fact from disclosing anything ABOUT a specific creator (1g), which is
        // what the assertion below actually tests.
        assertTrue(body.get("detail").asText().contains("per-creator-aggregate"),
                "the follower must be told which tier: " + body);
        String text = String.valueOf(throttled.getBody());
        assertFalse(text.contains(caller.creatorId().toString()),
                "the owning creator's id reached a public follower: " + text);
    }

    @Test
    @DisplayName("the register that discloses the deferral is present, as T055a requires")
    void theDisclosureExistsBeforeTheSweep() {
        // Asserted here as well as in RateLimiterTest, because this is the class that demonstrates the
        // gap. A test that exhibits an omission and a record that discloses it must not be separable.
        assertTrue(java.nio.file.Files.exists(
                        java.nio.file.Path.of("docs/delivery/baseline-omissions.md")),
                "the before-state above would otherwise be an undisclosed gap (T055a)");
    }
}
