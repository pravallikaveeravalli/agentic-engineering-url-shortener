package agentic.shortener.contract;

import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import agentic.shortener.domain.creator.CreatorRepository;
import agentic.shortener.support.TestCredentials;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T012's Done condition: the harness validates LIVE responses. NFR-TST-001. ADR-005.
 *
 * <p>{@link OpenApiConformanceTest} is the harness and T013 proves it can fail; this drives it against
 * responses the running application actually produced. Validating a hand-written string would prove the
 * harness works and say nothing about the implementation.
 *
 * <p>T012's Artifact names three orchestration operations — {@code createRun}, {@code inspectRun},
 * {@code recordGateDecision}. The harness resolves any declared path/method/status, so it covers them by
 * construction, but <strong>they are not exercised here because those endpoints do not exist until Phase
 * 5</strong>. That is stated rather than implied: a test named for coverage it does not have would be the
 * overclaim the Gate 7 sweep exists to catch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T012 live responses conform to the frozen contract")
class ContractConformanceIT extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final OpenApiConformanceTest harness = new OpenApiConformanceTest();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * T052 made creation and analytics authenticated. FR-URL-018 says creation MUST NOT succeed
     * anonymously, so this test presents a credential exactly as a caller would — the requirement
     * working, not a testing inconvenience.
     */
    @Autowired
    private CreatorRepository creatorsForAuth;

    @Autowired
    private java.time.Clock clockForAuth;

    private TestCredentials.Provisioned caller;

    @org.junit.jupiter.api.BeforeEach
    void provisionCaller() {
        caller = TestCredentials.provision(creatorsForAuth, clockForAuth);
    }

    /** A GET carrying the caller's credential. Analytics is authenticated since T052. */
    private org.springframework.http.ResponseEntity<String> getAuthenticated(String fullUrl) {
        HttpHeaders authorized = new HttpHeaders();
        authorized.set("Authorization", caller.header());
        return rest.exchange(fullUrl, HttpMethod.GET, new HttpEntity<>(authorized), String.class);
    }

    @Test
    @DisplayName("POST /v1/links 201 conforms")
    void createConforms() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", caller.header());
        ResponseEntity<String> response = rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"https://example.com/conformance\"}", headers),
                String.class);

        assertEquals(201, response.getStatusCode().value(), "body: " + response.getBody());
        harness.assertConforms("/v1/links", "post", 201, response.getBody());
    }

    @Test
    @DisplayName("POST /v1/links 400 conforms to the Error shape")
    void createRefusalConforms() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", caller.header());
        ResponseEntity<String> response = rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"javascript:alert(1)\"}", headers), String.class);

        // The error path is the one most likely to drift, because it is written once and rarely read.
        assertEquals(400, response.getStatusCode().value());
        harness.assertConforms("/v1/links", "post", 400, response.getBody());
    }

    @Test
    @DisplayName("GET analytics 200 and 404 both conform")
    void analyticsConforms() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", caller.header());
        ResponseEntity<String> created = rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"https://example.com/an\"}", headers), String.class);
        String code = created.getBody().replaceAll(".*\"shortCode\":\"([^\"]+)\".*", "$1");

        ResponseEntity<String> ok = getAuthenticated(url("/v1/links/" + code + "/analytics"));
        assertEquals(200, ok.getStatusCode().value());
        harness.assertConforms("/v1/links/{shortCode}/analytics", "get", 200, ok.getBody());

        ResponseEntity<String> missing = getAuthenticated(url("/v1/links/nosuchcode/analytics"));
        assertEquals(404, missing.getStatusCode().value());
        harness.assertConforms("/v1/links/{shortCode}/analytics", "get", 404, missing.getBody());
    }

    @Test
    @DisplayName("both health responses conform")
    void healthConforms() {
        ResponseEntity<String> live = rest.getForEntity(url("/health/live"), String.class);
        assertEquals(200, live.getStatusCode().value());
        harness.assertConforms("/health/live", "get", 200, live.getBody());

        ResponseEntity<String> ready = rest.getForEntity(url("/health/ready"), String.class);
        assertEquals(200, ready.getStatusCode().value());
        harness.assertConforms("/health/ready", "get", 200, ready.getBody());
    }

    @Test
    @DisplayName("the harness is strict: every walking-skeleton response rejects unexpected fields")
    void skeletonResponsesAreStrict() {
        // T012's guard requires the validation to reject additionalProperties. Asserted against the
        // contract for each response this slice can reach, so a later loosening of the document is
        // caught here rather than discovered when a drifted response slips through.
        for (String[] r : new String[][]{
                {"/v1/links", "post", "201"},
                {"/v1/links", "post", "400"},
                {"/v1/links/{shortCode}/analytics", "get", "200"},
                {"/health/live", "get", "200"}}) {
            assertTrue(harness.rejectsUnexpectedFields(r[0], r[1], Integer.parseInt(r[2])),
                    r[1].toUpperCase() + " " + r[0] + " " + r[2]
                            + " must declare additionalProperties: false, or an unexpected field "
                            + "cannot be caught");
        }
    }
}
