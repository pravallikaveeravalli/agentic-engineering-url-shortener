package agentic.shortener.support;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Stops the test HTTP client from hiding a rate limit. Support for T054/T055.
 *
 * <p><strong>The failure this exists to prevent was not obvious.</strong> Three rate-limit tests failed
 * with {@code expected: <429> but was: <201>} — and each took <strong>60.06 seconds</strong>. That number
 * is the rate-limit window. Apache HttpClient honours {@code Retry-After} on a 429 by default: it slept
 * out the entire window, retried, found the budget reset, and handed the test the retried success. The
 * throttling worked perfectly and the harness concealed it.
 *
 * <p>The {@code Retry-After} header is correct HTTP and stays. What has to change is the test client,
 * because a client that retries is not a client that can observe a refusal.
 *
 * <p><strong>Redirect handling stays disabled</strong> too. Replacing the factory would otherwise undo
 * what {@code TestRestTemplate} arranges by default, and a followed redirect makes a 307 invisible —
 * which would silently gut every FR-URL-007 assertion in the suite.
 */
public final class RestFixtures {

    private RestFixtures() {
    }

    /**
     * Points the template at a client that neither retries nor follows redirects.
     *
     * <p>Needed by any test that asserts a 429. Call once per test instance.
     */
    public static void withoutRetries(TestRestTemplate rest) {
        CloseableHttpClient client = HttpClients.custom()
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .build();
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory(client));
    }
}
