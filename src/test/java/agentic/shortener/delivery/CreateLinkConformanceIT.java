package agentic.shortener.delivery;

import agentic.shortener.contract.OpenApiConformanceTest;
import agentic.shortener.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T042 — {@code POST /v1/links} conformance across every declared response. FR-URL-001, FR-URL-002.
 *
 * <p><strong>Which codes are proved live, and which are not.</strong> The contract declares 201, 200,
 * 400, 401, 409, 429 and 503 for this operation. Five are reachable now — 401 became reachable when T052
 * added authentication — and are driven against the running application. Two are not, and saying so is
 * the point:
 *
 * <ul>
 *   <li><strong>429</strong> needs the rate-limit tiers, which are T054 and T055.
 *   <li><strong>503</strong> needs the store to go away mid-suite. {@code ReadinessDegradationIT} owns
 *       that, with its own container, because stopping the shared one would break every other test in
 *       the tier.
 * </ul>
 *
 * <p>For those three this asserts what <em>can</em> be asserted now — that the contract declares an
 * {@code Error} body for each, so the shape is fixed before the behaviour arrives. A test claiming
 * coverage it does not have would be the overclaim the Gate 7 sweep exists to catch.
 *
 * <p>T042's guard: <em>"a code MUST NOT be returned that cannot subsequently be resolved — persist
 * durably first."</em> Asserted by reading every returned code back through a separate request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T042 POST /v1/links conformance")
class CreateLinkConformanceIT extends PostgresIntegrationTest {

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

    private ResponseEntity<String> post(String destination, String marker, String expiresAt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", caller.header());
        if (marker != null) {
            headers.set("Idempotency-Key", marker);
        }
        String body = expiresAt == null
                ? "{\"destination\":\"" + destination + "\"}"
                : "{\"destination\":\"" + destination + "\",\"expiresAt\":\"" + expiresAt + "\"}";
        return rest.exchange("http://localhost:" + port + "/v1/links", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private String marker() {
        return "m-" + UUID.randomUUID();
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
    @DisplayName("201: a new link conforms, and is resolvable afterwards")
    void created() throws Exception {
        ResponseEntity<String> response = post("https://example.com/t042/new", null, null);
        assertEquals(201, response.getStatusCode().value(), response.getBody());
        harness.assertConforms("/v1/links", "post", 201, response.getBody());

        JsonNode body = JSON.readTree(response.getBody());
        assertFalse(body.get("replay").asBoolean(), "a fresh creation is not a replay");

        // T042's guard: the code must be resolvable. Persisted durably before the response, or this
        // read comes back empty.
        String code = body.get("shortCode").asText();
        ResponseEntity<String> analytics = getAuthenticated(
                "http://localhost:" + port + "/v1/links/" + code + "/analytics");
        assertEquals(200, analytics.getStatusCode().value(),
                "a returned code that cannot be resolved breaks FR-URL-001's negative clause");
    }

    @Test
    @DisplayName("200: CL-008 case 2 — same marker, identical request, replay: true")
    void replay() throws Exception {
        String marker = marker();
        String expiry = "2026-12-01T10:00:00Z";

        ResponseEntity<String> first = post("https://example.com/t042/replay", marker, expiry);
        assertEquals(201, first.getStatusCode().value(), first.getBody());
        JsonNode created = JSON.readTree(first.getBody());

        ResponseEntity<String> second = post("https://example.com/t042/replay", marker, expiry);
        assertEquals(200, second.getStatusCode().value(),
                "a replay is SUCCESS, not an error (CL-008 case 2): " + second.getBody());
        harness.assertConforms("/v1/links", "post", 200, second.getBody());

        JsonNode replayed = JSON.readTree(second.getBody());
        assertTrue(replayed.get("replay").asBoolean(), "the replay must be labelled as one");
        assertEquals(created.get("shortCode").asText(), replayed.get("shortCode").asText(),
                "the ORIGINAL link must come back");
        assertEquals(created.get("createdAt").asText(), replayed.get("createdAt").asText(),
                "zero new side effects: even the creation time is the original's");
        assertEquals(created.get("expiresAt").asText(), replayed.get("expiresAt").asText(),
                "a replay MUST NOT alter the existing link's expiry");
    }

    @Test
    @DisplayName("409: CL-008 case 3 — same marker, different content, nothing minted")
    void conflict() throws Exception {
        String marker = marker();
        ResponseEntity<String> first =
                post("https://example.com/t042/conflict", marker, "2026-12-01T10:00:00Z");
        assertEquals(201, first.getStatusCode().value(), first.getBody());

        ResponseEntity<String> second =
                post("https://example.com/t042/conflict", marker, "2026-12-02T10:00:00Z");
        assertEquals(409, second.getStatusCode().value(),
                "a changed expiry under the same marker is a conflict: " + second.getBody());
        harness.assertConforms("/v1/links", "post", 409, second.getBody());

        // Nothing minted past a detected caller error, and nothing changed.
        JsonNode created = JSON.readTree(first.getBody());
        ResponseEntity<String> stillThere = getAuthenticated("http://localhost:" + port
                + "/v1/links/" + created.get("shortCode").asText() + "/analytics");
        assertEquals(200, stillThere.getStatusCode().value());
    }

    @Test
    @DisplayName("400: each refusal class conforms to the Error shape")
    void refusals() {
        for (String destination : List.of(
                "javascript:alert(1)",                          // FR-URL-004
                "https://user:secret@example.com/x",            // EC-007
                "https://127.0.0.1/x",                          // EC-004
                "not-a-url")) {                                 // FR-URL-002
            ResponseEntity<String> response = post(destination, null, null);
            assertEquals(400, response.getStatusCode().value(),
                    destination + " must be refused: " + response.getBody());
            harness.assertConforms("/v1/links", "post", 400, response.getBody());
        }
    }

    @Test
    @DisplayName("CL-008 case 1: no marker always mints, even for an identical request")
    void noMarkerAlwaysMints() throws Exception {
        String destination = "https://example.com/t042/no-marker";
        String first = JSON.readTree(post(destination, null, null).getBody())
                .get("shortCode").asText();
        String second = JSON.readTree(post(destination, null, null).getBody())
                .get("shortCode").asText();

        assertNotEquals(first, second,
                "the same destination twice without a marker must produce two codes — there is no "
                        + "destination-based deduplication at any scope, ever (CL-008)");
    }

    @Test
    @DisplayName("T034's deferred clause: normalization runs BEFORE validation and BEFORE storage")
    void normalizationPrecedesValidationAndStorage() throws Exception {
        // T034's Artifact says normalization is "applied before validation and storage". Group B could
        // only assert the necessary condition — that the normalizer is total — because there was no
        // composition point yet. LinkService.vet is that point, and this is the assertion that was owed.
        //
        // BEFORE VALIDATION: surrounding whitespace makes the URL unparseable, so UrlSyntaxValidator
        // refuses it outright. A 201 here is only possible if normalization stripped it first.
        ResponseEntity<String> spaced = post("  https://example.com/t034/order  ", null, null);
        assertEquals(201, spaced.getStatusCode().value(),
                "whitespace must be normalized away before validation sees it: " + spaced.getBody());

        // BEFORE STORAGE: what comes back — and therefore what was stored — is the canonical form, not
        // what was sent. Scheme case, host case and the default port are all normalizable.
        ResponseEntity<String> mixed = post("HTTPS://EXAMPLE.COM:443/t034/Canonical", null, null);
        assertEquals(201, mixed.getStatusCode().value(), mixed.getBody());
        assertEquals("https://example.com/t034/Canonical",
                JSON.readTree(mixed.getBody()).get("destination").asText(),
                "the stored destination must be the normalized form; the path's case must survive");
    }

    @Test
    @DisplayName("EC-004 and EC-005 are WIRED, not merely implemented")
    void abuseControlsAreReachableThroughHttp() {
        // This test's earlier version caught AbuseGuard being built and never connected: a loopback
        // destination returned 201. A control that exists and is not on the path is not a control.
        for (String destination : List.of(
                "https://127.0.0.1/x",              // loopback
                "https://10.0.0.1/x",               // private
                "https://169.254.169.254/latest",   // link-local, cloud metadata
                "https://localhost/x")) {           // internal name
            ResponseEntity<String> response = post(destination, null, null);
            assertEquals(400, response.getStatusCode().value(),
                    destination + " must be refused through HTTP: " + response.getBody());
            harness.assertConforms("/v1/links", "post", 400, response.getBody());
        }
    }

    @Test
    @DisplayName("401: an unauthenticated create conforms (reachable since T052)")
    void unauthenticated() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(
                "http://localhost:" + port + "/v1/links", HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"https://example.com/t042/anon\"}", headers),
                String.class);

        assertEquals(401, response.getStatusCode().value(), response.getBody());
        harness.assertConforms("/v1/links", "post", 401, response.getBody());
    }

    @Test
    @DisplayName("429 and 503 have their Error shape fixed in the contract already")
    void notYetReachableCodesAreDeclared() {
        // Stated as what it is: a document assertion, not a live one. It fixes the shape now so the
        // behaviour arriving at T054/T055 and in ReadinessDegradationIT cannot quietly invent a
        // different one. 401 has moved out of this list because T052 made it reachable, and it is now
        // asserted live above.
        for (int status : List.of(429, 503)) {
            assertTrue(harness.rejectsUnexpectedFields("/v1/links", "post", status),
                    "POST /v1/links " + status + " must declare a strict Error schema, so the "
                            + "response cannot drift when the behaviour lands");
        }
    }
}
