package agentic.shortener.delivery.auth;

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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T052 through HTTP. FR-URL-018, FR-URL-019. EC-041.
 *
 * <p><strong>Authenticated versus anonymous creation</strong>, which is FR-URL-018's Accept and Reject
 * clauses in one pair, and <strong>EC-041's byte-identity</strong> measured on what a caller actually
 * receives rather than on what the filter returns internally. The unit test asserts the decision is one
 * value; this asserts the response is one response — status, body and headers.
 *
 * <p><strong>Ownership is the other half of FR-URL-018.</strong> Every created link records the creator
 * that made it, so a second creator must not see the first's analytics. That is asserted here rather than
 * only at the use case, because before T052 there was no way to be a second creator through HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T052 authentication through HTTP")
class AuthenticationIT extends PostgresIntegrationTest {

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

    private TestCredentials.Provisioned alice;

    @BeforeEach
    void provision() {
        alice = TestCredentials.provision(creators, clock);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> create(String authorization, String destination) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorization != null) {
            headers.set("Authorization", authorization);
        }
        return rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"" + destination + "\"}", headers), String.class);
    }

    @Test
    @DisplayName("an authenticated creator can create; an anonymous caller cannot")
    void authenticatedVersusAnonymous() {
        ResponseEntity<String> authenticated =
                create(alice.header(), "https://example.com/t052/authenticated");
        assertEquals(201, authenticated.getStatusCode().value(), authenticated.getBody());

        ResponseEntity<String> anonymous =
                create(null, "https://example.com/t052/anonymous");
        assertEquals(401, anonymous.getStatusCode().value(),
                "FR-URL-018: creation MUST NOT succeed anonymously: " + anonymous.getBody());
        harness.assertConforms("/v1/links", "post", 401, anonymous.getBody());
    }

    @Test
    @DisplayName("analytics is unreachable without a credential")
    void analyticsRequiresACredential() throws Exception {
        String code = JSON.readTree(create(alice.header(), "https://example.com/t052/analytics")
                .getBody()).get("shortCode").asText();

        ResponseEntity<String> anonymous =
                rest.getForEntity(url("/v1/links/" + code + "/analytics"), String.class);
        assertEquals(401, anonymous.getStatusCode().value(),
                "FR-URL-011: retrieval MUST NOT be reachable without creator authentication");
        harness.assertConforms("/v1/links/{shortCode}/analytics", "get", 401, anonymous.getBody());
    }

    @Test
    @DisplayName("EC-041: an expired credential's refusal is byte-identical to a revoked one's")
    void ec041ExpiredAndRevokedAreByteIdentical() {
        TestCredentials.Provisioned expired = TestCredentials.provisionExpired(creators, clock);
        TestCredentials.Provisioned revoked = TestCredentials.provisionRevoked(creators, clock);

        ResponseEntity<String> withExpired = create(expired.header(), "https://example.com/t052/e");
        ResponseEntity<String> withRevoked = create(revoked.header(), "https://example.com/t052/r");

        assertEquals(401, withExpired.getStatusCode().value());
        assertEquals(401, withRevoked.getStatusCode().value());
        assertEquals(String.valueOf(withExpired.getBody()), String.valueOf(withRevoked.getBody()),
                "the two bodies must be identical, or the distinction is disclosed (EC-041, CR-002)");
        assertEquals(withExpired.getHeaders().getContentType(),
                withRevoked.getHeaders().getContentType());

        // And neither may say which it was.
        for (String leak : List.of("expire", "revok", "Expire", "Revok")) {
            assertFalse(String.valueOf(withExpired.getBody()).contains(leak),
                    "the refusal names its cause: " + withExpired.getBody());
        }
    }

    @Test
    @DisplayName("every refusal shape is identical, including for a key that never existed")
    void everyRefusalIsIdentical() {
        // Wider than EC-041 states, for the same reason: if a guessed key produced a different refusal
        // from a revoked one, an attacker could confirm which guesses correspond to real credentials.
        TestCredentials.Provisioned expired = TestCredentials.provisionExpired(creators, clock);

        List<String> bodies = List.of(
                String.valueOf(create(null, "https://example.com/t052/a").getBody()),
                String.valueOf(create("", "https://example.com/t052/b").getBody()),
                String.valueOf(create("Basic anything", "https://example.com/t052/c").getBody()),
                String.valueOf(create("Bearer crk_NEVEREXISTEDTESTONLY00000", "https://example.com/t052/d")
                        .getBody()),
                String.valueOf(create(expired.header(), "https://example.com/t052/e").getBody()));

        for (String body : bodies) {
            assertEquals(bodies.get(0), body, "refusal bodies differ: " + bodies);
        }
    }

    @Test
    @DisplayName("FR-URL-018: the created link records the creator that made it")
    void ownershipIsRecorded() throws Exception {
        TestCredentials.Provisioned bob = TestCredentials.provision(creators, clock);

        String aliceCode = JSON.readTree(create(alice.header(), "https://example.com/t052/alice")
                .getBody()).get("shortCode").asText();

        // Alice can read her own analytics.
        HttpHeaders asAlice = new HttpHeaders();
        asAlice.set("Authorization", alice.header());
        assertEquals(200, rest.exchange(url("/v1/links/" + aliceCode + "/analytics"),
                HttpMethod.GET, new HttpEntity<>(asAlice), String.class).getStatusCode().value());

        // Bob cannot, and cannot tell that from a code that never existed.
        HttpHeaders asBob = new HttpHeaders();
        asBob.set("Authorization", bob.header());
        ResponseEntity<String> bobOnAlices = rest.exchange(url("/v1/links/" + aliceCode + "/analytics"),
                HttpMethod.GET, new HttpEntity<>(asBob), String.class);
        ResponseEntity<String> bobOnNothing = rest.exchange(url("/v1/links/Zzzzzzz/analytics"),
                HttpMethod.GET, new HttpEntity<>(asBob), String.class);

        assertEquals(404, bobOnAlices.getStatusCode().value());
        assertEquals(404, bobOnNothing.getStatusCode().value());
        assertEquals(String.valueOf(bobOnAlices.getBody()), String.valueOf(bobOnNothing.getBody()),
                "no ownership oracle: a link somebody else owns must look exactly like one that does "
                        + "not exist (FR-URL-011)");
    }

    @Test
    @DisplayName("two creators' links are distinct and independently owned")
    void creatorsAreIsolated() throws Exception {
        TestCredentials.Provisioned bob = TestCredentials.provision(creators, clock);
        assertNotEquals(alice.creatorId(), bob.creatorId());

        String same = "https://example.com/t052/shared-destination";
        String aliceCode = JSON.readTree(create(alice.header(), same).getBody())
                .get("shortCode").asText();
        String bobCode = JSON.readTree(create(bob.header(), same).getBody())
                .get("shortCode").asText();

        assertNotEquals(aliceCode, bobCode,
                "the same destination from two creators must mint two codes — there is no "
                        + "destination-based deduplication at any scope (CL-008)");
    }

    @Test
    @DisplayName("the redirect path is untouched by all of this")
    void redirectRemainsPublic() throws Exception {
        String code = JSON.readTree(create(alice.header(), "https://example.com/t052/public")
                .getBody()).get("shortCode").asText();

        ResponseEntity<String> anonymous = rest.getForEntity(url("/" + code), String.class);
        assertEquals(307, anonymous.getStatusCode().value(),
                "a short link that requires login is not a short link (T044, FR-URL-018)");
        assertTrue(anonymous.getHeaders().getFirst("Location").endsWith("/t052/public"));
    }

    @Test
    @DisplayName("the health probes stay reachable without a credential")
    void healthRemainsPublic() {
        // An orchestrator cannot hold a creator credential. A probe behind authentication would report a
        // healthy service as unhealthy and get it restarted.
        assertEquals(200, rest.getForEntity(url("/health/live"), String.class)
                .getStatusCode().value());
        assertEquals(200, rest.getForEntity(url("/health/ready"), String.class)
                .getStatusCode().value());
    }
}
