package agentic.shortener.link;

import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import agentic.shortener.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import agentic.shortener.domain.creator.CreatorRepository;
import agentic.shortener.support.TestCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T047 — an expired link never redirects. FR-URL-008. EC-009, EC-042. CR-003.
 *
 * <p>FR-URL-008's negative clause is unusually absolute: an expired link must not redirect
 * <strong>under any retry, cache, or race</strong>. Each of the three gets a test, because each fails
 * differently.
 *
 * <p><strong>The cache case is the interesting one, and it is not testable the way it sounds.</strong>
 * EC-042 says a client caching a redirect and following it after expiry is <em>impossible under the
 * temporary class</em>. No test here can observe somebody else's browser cache. What a test <em>can</em>
 * do is assert the property that makes the caching impossible: the response is temporary-class, and it
 * carries no caching headers that would invite a follower to reuse it. CR-003 recorded that FR-URL-008's
 * enforceability <em>depends</em> on that class, so this is the dependency asserted rather than assumed.
 *
 * <p>Nothing here sleeps. Expiry is reached by expiring the link, not by waiting for a 30-day TTL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T047 an expired link never redirects")
class ExpiredLinkIT extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationContext context;

    private String url(String path) {
        return "http://localhost:" + port + path;
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

    private ResponseEntity<String> follow(String code) {
        return rest.getForEntity(url("/" + code), String.class);
    }

    private String expiredCode(String destination) throws Exception {
        String code = createLink(destination);
        ShortLinkRepository links = context.getBean(ShortLinkRepository.class);
        ShortLink live = links.findByShortCode(code).orElseThrow();
        links.expire(live);
        return code;
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
    @DisplayName("an expired link answers 410 and never 3xx")
    void expiredNeverRedirects() throws Exception {
        String code = expiredCode("https://example.com/t047/expired");
        ResponseEntity<String> response = follow(code);

        assertEquals(410, response.getStatusCode().value(), response.getBody());
        assertFalse(response.getStatusCode().is3xxRedirection(), "an expired link must not redirect");
        assertNull(response.getHeaders().getFirst("Location"), "and must carry no Location at all");
    }

    @Test
    @DisplayName("expired is distinguishable from never-issued")
    void distinguishableFromNeverIssued() throws Exception {
        String expired = expiredCode("https://example.com/t047/distinguish");

        assertEquals(410, follow(expired).getStatusCode().value());
        assertEquals(404, follow("Wwwwwww").getStatusCode().value());
    }

    @Test
    @DisplayName("UNDER RETRY: twenty follows of an expired link all answer 410")
    void expiredUnderRetry() throws Exception {
        // "MUST NOT redirect under any retry". A resolver holding state — a cache, a memoized lookup —
        // could answer 410 once and 307 the next time. Twenty identical answers is what rules that out.
        String code = expiredCode("https://example.com/t047/retry");
        for (int i = 0; i < 20; i++) {
            assertEquals(410, follow(code).getStatusCode().value(), "attempt " + (i + 1));
        }
    }

    @Test
    @DisplayName("UNDER RACE: fifty concurrent follows of an expired link all answer 410")
    void expiredUnderRace() throws Exception {
        // "MUST NOT redirect under any race", and EC-009 by construction: the link is expired before any
        // of these threads starts, so if any of them redirects, the decision is not being taken per
        // request. Barrier-released, not staggered by sleeps.
        String code = expiredCode("https://example.com/t047/race");

        int clients = 50;
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        Set<Integer> statuses = new HashSet<>();
        try {
            List<Future<Integer>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < clients; i++) {
                Callable<Integer> task = () -> follow(code).getStatusCode().value();
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "threads did not reach the barrier");
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "follows did not finish");
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(Set.of(410), statuses,
                "every concurrent follow of an expired link must answer 410; got " + statuses);
    }

    @Test
    @DisplayName("EC-009: expiring between two follows flips the answer on the very next one")
    void midFlightExpiryTakesEffectImmediately() throws Exception {
        // The mechanism EC-009 depends on: the decision is taken per request, from storage, every time.
        // If resolution cached anything, the first answer would outlive the expiry.
        String code = createLink("https://example.com/t047/mid-flight");
        assertEquals(307, follow(code).getStatusCode().value(), "alive first");

        ShortLinkRepository links = context.getBean(ShortLinkRepository.class);
        links.expire(links.findByShortCode(code).orElseThrow());

        assertEquals(410, follow(code).getStatusCode().value(),
                "the next follow must see the expiry, with no restart and no cache flush");
    }

    @Test
    @DisplayName("EC-042: the temporary class is what makes a cached bypass impossible")
    void ec042TemporaryClassPreventsCaching() throws Exception {
        // EC-042 is not observable from here — no test can read a follower's browser cache. What is
        // asserted is the property CR-003 says FR-URL-008's enforceability DEPENDS on: the class is
        // temporary, so every follow reaches the service, and nothing in the response invites reuse.
        String code = createLink("https://example.com/t047/ec042");
        ResponseEntity<String> response = follow(code);

        assertEquals(307, response.getStatusCode().value(),
                "307 is temporary-class. Under 301 or 308 a browser would cache the mapping and expiry "
                        + "would be unenforceable BY CONSTRUCTION rather than by defect (CR-003)");

        String cacheControl = response.getHeaders().getFirst("Cache-Control");
        assertTrue(cacheControl == null || cacheControl.contains("no-store")
                        || cacheControl.contains("no-cache") || cacheControl.contains("max-age=0"),
                "a redirect that invited caching would undo the temporary class; Cache-Control was: "
                        + cacheControl);
        assertNull(response.getHeaders().getFirst("Expires"),
                "an Expires header would license a follower to reuse this redirect");

        // And the same link, once expired, is refused on the next follow — which is only possible
        // because the follow reached the service at all.
        ShortLinkRepository links = context.getBean(ShortLinkRepository.class);
        links.expire(links.findByShortCode(code).orElseThrow());
        assertEquals(410, follow(code).getStatusCode().value());
    }

    @Test
    @DisplayName("an expired link's destination is never disclosed, on any path")
    void expiredDestinationStaysPrivate() throws Exception {
        String code = expiredCode("https://private.example/t047/campaign-secret");
        ResponseEntity<String> response = follow(code);

        assertEquals(410, response.getStatusCode().value());
        assertFalse(String.valueOf(response.getBody()).contains("private.example"),
                "the dead link's destination leaked: " + response.getBody());
        assertFalse(String.valueOf(response.getBody()).contains("campaign-secret"),
                "the dead link's destination leaked: " + response.getBody());
    }
}
