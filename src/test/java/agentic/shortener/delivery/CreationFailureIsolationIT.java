package agentic.shortener.delivery;

import agentic.shortener.contract.OpenApiConformanceTest;
import agentic.shortener.domain.creator.CreatorRepository;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import agentic.shortener.support.PostgresIntegrationTest;
import agentic.shortener.support.TestCredentials;
import org.junit.jupiter.api.BeforeEach;
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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EC-010 — the persistence layer is unavailable <strong>during creation</strong>. T045. FR-URL-014.
 *
 * <p><strong>Raised by the Slice 3 boundary artifact-coverage sweep.</strong> T045's Validate field names
 * EC-010 <em>and</em> EC-011, and its Done condition says "nothing persisted on EC-010". Only EC-011 was
 * covered — {@code RedirectIT} fails the store during <em>resolution</em>. The creation-side case, and the
 * "nothing persisted" assertion that goes with it, had no test at all.
 *
 * <p>That is the same defect shape the sweep exists to catch, and it was found the same way: by reading
 * the task's own fields rather than the code. A store failure during resolution and one during creation
 * are different code paths with different consequences, and covering one reads as covering both.
 *
 * <p><strong>EC-010's requirement is narrow and strong: no PARTIAL link may become visible.</strong> Not
 * "the request fails cleanly" — that a caller can see. The thing that must not happen is a row existing
 * for a code the caller was never told about, or a code being returned for a row that was never written.
 * Either one breaks FR-URL-001's negative clause, which forbids returning a code that cannot subsequently
 * be resolved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("EC-010 store unavailable during creation")
class CreationFailureIsolationIT extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Fails only the write. Reads keep working, which is what makes "nothing persisted" checkable. */
    private static final AtomicBoolean SAVE_BROKEN = new AtomicBoolean(false);

    @TestConfiguration
    static class FaultInjection {

        @Bean
        @Primary
        ShortLinkRepository failableOnSave(agentic.shortener.persistence.ConnectionSource connections) {
            ShortLinkRepository real =
                    new agentic.shortener.persistence.JdbcShortLinkRepository(connections);
            return new ShortLinkRepository() {
                @Override
                public ShortLink save(ShortLink link) {
                    if (SAVE_BROKEN.get()) {
                        // Exactly what JdbcShortLinkRepository raises when the store cannot be written.
                        throw new IllegalStateException("failed to save link " + link.shortCode());
                    }
                    return real.save(link);
                }

                @Override
                public Optional<ShortLink> findByShortCode(String shortCode) {
                    return real.findByShortCode(shortCode);
                }

                @Override
                public ShortLink expire(ShortLink link) {
                    return real.expire(link);
                }

                @Override
                public long countByCreator(UUID creatorId) {
                    return real.countByCreator(creatorId);
                }
            };
        }
    }

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
        caller = TestCredentials.provision(creators, clock);
    }

    private ResponseEntity<String> create(String destination) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", caller.header());
        return rest.exchange("http://localhost:" + port + "/v1/links", HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"" + destination + "\"}", headers), String.class);
    }

    private long countLinks() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM short_link")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("EC-010: creation fails with 503, and NOTHING is persisted")
    void creationFailureLeavesNothingBehind() throws Exception {
        long before = countLinks();

        SAVE_BROKEN.set(true);
        ResponseEntity<String> response;
        try {
            response = create("https://example.com/ec010/never-stored");
        } finally {
            SAVE_BROKEN.set(false);
        }

        assertEquals(503, response.getStatusCode().value(),
                "a store that cannot be written is unavailable, not a bad request: " + response.getBody());
        harness.assertConforms("/v1/links", "post", 503, response.getBody());
        assertEquals(before, countLinks(), "no partial link may become visible (EC-010)");
    }

    @Test
    @DisplayName("EC-010: no code is returned, so none can be unresolvable")
    void noCodeIsReturnedOnFailure() throws Exception {
        SAVE_BROKEN.set(true);
        ResponseEntity<String> response;
        try {
            response = create("https://example.com/ec010/no-code");
        } finally {
            SAVE_BROKEN.set(false);
        }

        // FR-URL-001's negative clause: a code MUST NOT be returned that cannot subsequently be resolved.
        // The strongest form of that guarantee is returning no code at all.
        String body = String.valueOf(response.getBody());
        assertFalse(body.contains("shortCode"),
                "a failed creation must not return a code: " + body);
        assertFalse(body.contains("ec010/no-code"),
                "nor echo the destination back as though something had happened: " + body);
    }

    @Test
    @DisplayName("EC-010: the failure discloses no storage internals")
    void failureDisclosesNoInternals() {
        SAVE_BROKEN.set(true);
        ResponseEntity<String> response;
        try {
            response = create("https://example.com/ec010/quiet");
        } finally {
            SAVE_BROKEN.set(false);
        }

        String body = String.valueOf(response.getBody());
        for (String leak : List.of("SQL", "Exception", "postgres", "jdbc", "short_code", "short_link",
                "failed to save", "IllegalState")) {
            assertFalse(body.contains(leak),
                    "storage internals leaked ('" + leak + "'): " + body);
        }
    }

    @Test
    @DisplayName("EC-010: the service recovers — the next creation succeeds and is resolvable")
    void recoveryIsClean() throws Exception {
        SAVE_BROKEN.set(true);
        try {
            create("https://example.com/ec010/during-outage");
        } finally {
            SAVE_BROKEN.set(false);
        }

        // Nothing is left in a state that poisons the next request. A failed creation that consumed a
        // code, a marker or a connection would show up here.
        ResponseEntity<String> after = create("https://example.com/ec010/after-outage");
        assertEquals(201, after.getStatusCode().value(), after.getBody());

        String code = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(after.getBody()).get("shortCode").asText();
        assertEquals(307, rest.getForEntity("http://localhost:" + port + "/" + code, String.class)
                .getStatusCode().value(), "and the code it returned resolves");
    }

    @Test
    @DisplayName("the injected failure is real — the baseline succeeds")
    void baselineSucceeds() {
        // Without this, a test that always got 503 for an unrelated reason would satisfy every assertion
        // above. The switch is off here, and creation works.
        assertEquals(201, create("https://example.com/ec010/baseline").getStatusCode().value());
        assertFalse(SAVE_BROKEN.get(), "and the switch must be left off for the next test");
    }

    @Test
    @DisplayName("EC-010 and EC-011 are different paths, and both are covered")
    void ec010AndEc011AreBothCovered() throws Exception {
        // Stated as an assertion rather than as a comment, because the coverage gap this class closes was
        // exactly the belief that covering one covered the other. RedirectIT fails the store during
        // RESOLUTION; this class fails it during CREATION. The two produce 503 from different handlers.
        assertTrue(java.nio.file.Files.exists(
                        java.nio.file.Path.of("src/test/java/agentic/shortener/delivery/RedirectIT.java")),
                "EC-011's coverage must exist alongside this class's EC-010 coverage");
        assertTrue(java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/test/java/agentic/shortener/delivery/RedirectIT.java")).contains("EC-011"),
                "and must still name EC-011, so a future removal is visible here too");
    }
}
