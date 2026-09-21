package agentic.shortener.delivery;

import agentic.shortener.contract.OpenApiConformanceTest;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import agentic.shortener.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import agentic.shortener.domain.creator.CreatorRepository;
import agentic.shortener.support.TestCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T044 and T045 — the public redirect surface. FR-URL-018, FR-URL-014, FR-URL-008. CR-003.
 *
 * <p><strong>"A short link that requires login is not a short link."</strong> That is T044's guard,
 * quoted because it is the whole point: the creator authenticates, the follower never does. Two things
 * are asserted — that an anonymous request succeeds, and that a request carrying a credential gets a
 * <em>byte-identical</em> answer. The second still holds now that T052 has added authentication, and it
 * fails the moment a filter starts having an opinion about this path.
 *
 * <p><strong>Three distinguishable outcomes</strong> (T045): never-issued is 404, expired is 410, and a
 * store failure for a code that may well exist is 503. The damaging collapse is the third into the first:
 * a 404 is a definite statement that the code does not exist, and a broken store cannot make that
 * statement. A creator told 404 would reasonably conclude their link was gone.
 *
 * <p>The store failure is injected rather than staged by stopping a container — ADR-011's rule is a real
 * store for data paths and <em>injected fakes for reliability proofs</em>, and this is a reliability
 * proof. It is also deterministic, which a container being killed is not.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T044/T045 public redirect")
class RedirectIT extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** Set for one test, cleared immediately. Static because the bean outlives a single method. */
    private static final AtomicBoolean STORE_BROKEN = new AtomicBoolean(false);

    /**
     * ADR-011's sanctioned fake: it delegates to the real repository until the switch is thrown, so
     * every other test in this class runs against real PostgreSQL and only the reliability proof does
     * not.
     */
    @TestConfiguration
    static class FaultInjection {

        @Bean
        @Primary
        ShortLinkRepository failableLinks(
                agentic.shortener.persistence.ConnectionSource connections) {
            ShortLinkRepository real = new agentic.shortener.persistence
                    .JdbcShortLinkRepository(connections);
            return new ShortLinkRepository() {
                @Override
                public ShortLink save(ShortLink link) {
                    return real.save(link);
                }

                @Override
                public Optional<ShortLink> findByShortCode(String shortCode) {
                    if (STORE_BROKEN.get()) {
                        // Exactly what JdbcShortLinkRepository raises when the store is unreachable.
                        throw new IllegalStateException("failed to read link " + shortCode);
                    }
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

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OpenApiConformanceTest harness = new OpenApiConformanceTest();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationContext context;

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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** A client that does NOT follow redirects, or the 307 would be invisible. */
    private ResponseEntity<String> follow(String code, HttpHeaders headers) {
        return rest.exchange(url("/" + code), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private String createLink(String destination) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", caller.header());
        ResponseEntity<String> response = rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"" + destination + "\"}", headers), String.class);
        assertEquals(201, response.getStatusCode().value(), response.getBody());
        return JSON.readTree(response.getBody()).get("shortCode").asText();
    }

    @Test
    @DisplayName("307: an ANONYMOUS follower is redirected to the exact stored destination")
    void anonymousRedirect() throws Exception {
        String destination = "https://example.com/A/b%2Fc?z=1&a=2#Frag";
        String code = createLink(destination);

        ResponseEntity<String> response = follow(code, new HttpHeaders());

        assertEquals(307, response.getStatusCode().value(),
                "temporary class is required (CR-003): " + response.getBody());
        assertEquals(destination, response.getHeaders().getFirst("Location"),
                "the Location must equal the stored destination byte for byte (FR-URL-007)");
        harness.assertConforms("/{shortCode}", "get", 307, response.getBody());
    }

    @Test
    @DisplayName("GUARD: the redirect class is TEMPORARY — 301 and 308 are prohibited")
    void temporaryClassOnly() throws Exception {
        String code = createLink("https://example.com/temporary-class");
        int status = follow(code, new HttpHeaders()).getStatusCode().value();

        assertTrue(status == 307 || status == 302,
                "the redirect must be temporary-class; got " + status);
        assertFalse(status == 301 || status == 308,
                "a permanent-class redirect is cached by browsers, after which clicks never reach the "
                        + "service — analytics cannot count them (FR-URL-010) and expiry cannot be "
                        + "enforced (FR-URL-008). CR-003 prohibits it");
    }

    @Test
    @DisplayName("GUARD: a credential changes NOTHING on the redirect path")
    void credentialsAreIrrelevant() throws Exception {
        // The assertion that keeps holding after T052. If an auth filter ever starts inspecting this
        // path, these two answers stop matching.
        String code = createLink("https://example.com/no-login-needed");

        ResponseEntity<String> anonymous = follow(code, new HttpHeaders());

        HttpHeaders withCredential = new HttpHeaders();
        withCredential.set("Authorization", "Bearer crk_NOTAREALKEYTESTONLY0000000000");
        ResponseEntity<String> credentialed = follow(code, withCredential);

        HttpHeaders withGarbage = new HttpHeaders();
        withGarbage.set("Authorization", "Basic not-even-base64");
        ResponseEntity<String> garbage = follow(code, withGarbage);

        assertEquals(anonymous.getStatusCode(), credentialed.getStatusCode(),
                "a valid-looking credential must not change the outcome");
        assertEquals(anonymous.getStatusCode(), garbage.getStatusCode(),
                "a malformed credential must not change the outcome either — a short link that can be "
                        + "broken by sending a bad header is not a short link");
        assertEquals(anonymous.getHeaders().getFirst("Location"),
                credentialed.getHeaders().getFirst("Location"));
        assertEquals(anonymous.getHeaders().getFirst("Location"),
                garbage.getHeaders().getFirst("Location"));
    }

    @Test
    @DisplayName("GUARD: no filter of ours is auto-registered for every path")
    void noFilterIsAutoRegistered() {
        // Group E asserted there were no filters at all, and said this test MUST FAIL when T052 arrived
        // — which it did. The obligation it was standing in for is now assertable directly: a Filter
        // exposed as a BEAN is auto-registered by Spring Boot for /*, which would put authentication on
        // the redirect path with an internal opt-out. An opt-out inside a filter that runs on every
        // request is one edit away from not opting out, so the filter must not be a bean at all.
        List<String> autoRegistered = context.getBeansOfType(Filter.class).entrySet().stream()
                .filter(e -> e.getValue().getClass().getName().startsWith("agentic.shortener"))
                .map(Map.Entry::getKey)
                .toList();

        assertEquals(List.of(), autoRegistered,
                "these filters are beans and therefore run on GET /{shortCode}: " + autoRegistered
                        + ". Register them through a FilterRegistrationBean with explicit URL patterns "
                        + "instead (T044)");
    }

    @Test
    @DisplayName("GUARD: no registered URL pattern can match a short code")
    void noRegisteredPatternMatchesAShortCode() {
        // The other half, and the one that survives future filters: whatever is registered, none of its
        // patterns may match a single-segment seven-character path. Read from the registrations
        // themselves rather than from a list of names somebody has to remember to update.
        List<String> offending = new java.util.ArrayList<>();
        context.getBeansOfType(FilterRegistrationBean.class).forEach((name, registration) -> {
            for (Object pattern : registration.getUrlPatterns()) {
                if (matchesASevenCharacterRootPath(String.valueOf(pattern))) {
                    offending.add(name + " -> " + pattern);
                }
            }
        });

        assertEquals(List.of(), offending,
                "these registrations would run on the redirect path: " + offending
                        + ". A short link that requires login is not a short link (T044)");
    }

    /** Whether a servlet URL pattern would match {@code /Abc1234}. */
    private static boolean matchesASevenCharacterRootPath(String pattern) {
        if (pattern.equals("/*") || pattern.equals("/**")) {
            return true;
        }
        // A prefix pattern matches only if the prefix itself is empty at the root.
        return pattern.endsWith("/*") && pattern.length() == 2;
    }

    @Test
    @DisplayName("GUARD: the redirect controller reads no credential")
    void redirectControllerReadsNoCredential() throws Exception {
        Path source = Path.of("src/main/java/agentic/shortener/delivery/RedirectController.java");
        assertTrue(Files.exists(source), "missing " + source.toAbsolutePath());
        String active = Files.readString(source).lines()
                .map(String::stripLeading)
                .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                .reduce("", (a, b) -> a + "\n" + b);

        for (String forbidden : List.of("Authorization", "RequestHeader", "Principal",
                "Authentication", "CreatorCredential", "apiKey")) {
            assertFalse(active.contains(forbidden),
                    "the redirect path must not read, require or be affected by credentials; found '"
                            + forbidden + "'");
        }
    }

    @Test
    @DisplayName("404: a never-issued code, with the contract's Error shape")
    void neverIssuedIs404() {
        ResponseEntity<String> response = follow("Zzzzzzz", new HttpHeaders());

        assertEquals(404, response.getStatusCode().value(), response.getBody());
        harness.assertConforms("/{shortCode}", "get", 404, response.getBody());
        assertFalse(String.valueOf(response.getBody()).contains("Zzzzzzz"),
                "the refusal must not echo the requested code back: " + response.getBody());
    }

    @Test
    @DisplayName("410: an expired link, distinguishable from never-issued and carrying no destination")
    void expiredIs410() throws Exception {
        // Created with a one-second life and then resolved after it, using a caller-supplied expiry so
        // no clock has to be manipulated and nothing has to sleep for 30 days.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", caller.header());
        String body = "{\"destination\":\"https://secret.example/expired-campaign\","
                + "\"expiresAt\":\"2026-09-21T00:00:01Z\"}";
        ResponseEntity<String> created = rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);

        // The expiry is in the past relative to the real clock, so creation must be REFUSED (EC-014) —
        // which means an already-expired link cannot be made this way at all. That is the requirement
        // working, so the test creates a live link and expires it through the repository instead.
        assertEquals(400, created.getStatusCode().value(),
                "EC-014: a past expiry must be refused at creation: " + created.getBody());

        String code = createLink("https://secret.example/to-be-expired");
        ShortLinkRepository links = context.getBean(ShortLinkRepository.class);
        links.expire(links.findByShortCode(code).orElseThrow());

        ResponseEntity<String> response = follow(code, new HttpHeaders());

        assertEquals(410, response.getStatusCode().value(), response.getBody());
        harness.assertConforms("/{shortCode}", "get", 410, response.getBody());
        assertFalse(String.valueOf(response.getBody()).contains("secret.example"),
                "a 410 must not disclose the dead link's destination: " + response.getBody());
        assertNull(response.getHeaders().getFirst("Location"),
                "an expired link must carry no Location header at all");
    }

    @Test
    @DisplayName("EC-011: a store failure is 503 — NOT 404, and with no storage internals")
    void storeFailureIs503() throws Exception {
        String code = createLink("https://example.com/store-failure");
        STORE_BROKEN.set(true);
        try {
            ResponseEntity<String> response = follow(code, new HttpHeaders());

            assertEquals(503, response.getStatusCode().value(),
                    "a store failure must not be reported as a definite not-found: " + response.getBody());
            harness.assertConforms("/{shortCode}", "get", 503, response.getBody());

            // The guard: no storage internals, no secrets, no exception text.
            String text = String.valueOf(response.getBody());
            for (String leak : List.of("SQL", "Exception", "postgres", "jdbc", "short_link",
                    "IllegalState", "failed to read")) {
                assertFalse(text.contains(leak), "storage internals leaked ('" + leak + "'): " + text);
            }
        } finally {
            STORE_BROKEN.set(false);
        }
    }

    @Test
    @DisplayName("the three outcomes are mutually distinguishable, asserted together")
    void threeOutcomesDiffer() throws Exception {
        // T045's Done condition asserted directly rather than inferred from three separate tests, so a
        // future change that collapsed two of them is caught here even if each test still passes.
        String live = createLink("https://example.com/three-outcomes");
        ShortLinkRepository links = context.getBean(ShortLinkRepository.class);
        String dead = createLink("https://example.com/three-outcomes-dead");
        links.expire(links.findByShortCode(dead).orElseThrow());

        int redirect = follow(live, new HttpHeaders()).getStatusCode().value();
        int expired = follow(dead, new HttpHeaders()).getStatusCode().value();
        int missing = follow("Qqqqqqq", new HttpHeaders()).getStatusCode().value();

        STORE_BROKEN.set(true);
        int broken;
        try {
            broken = follow(live, new HttpHeaders()).getStatusCode().value();
        } finally {
            STORE_BROKEN.set(false);
        }

        assertEquals(4, java.util.Set.of(redirect, expired, missing, broken).size(),
                "redirect/expired/not-found/unavailable must all differ; got "
                        + List.of(redirect, expired, missing, broken));
    }

    @Test
    @DisplayName("EC-008: a code differing only by case is a DIFFERENT code")
    void caseSensitiveCodes() throws Exception {
        // Codes are case-sensitive (ADR-007), so this is not a near-miss to be helpfully corrected. A
        // service that matched case-insensitively would resolve two different codes to one link.
        String code = createLink("https://example.com/case-sensitive");
        String flipped = code.chars()
                .mapToObj(c -> Character.isUpperCase(c)
                        ? String.valueOf(Character.toLowerCase(c))
                        : String.valueOf(Character.toUpperCase(c)))
                .reduce("", String::concat);

        if (flipped.equals(code)) {
            return;   // an all-digit code has no case to flip; nothing to assert
        }
        assertEquals(404, follow(flipped, new HttpHeaders()).getStatusCode().value(),
                "a case-flipped code must not resolve: " + flipped + " vs " + code);
    }
}
