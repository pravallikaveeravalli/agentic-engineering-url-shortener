package agentic.shortener.delivery;

import agentic.shortener.application.ResolveLinkUseCase;
import agentic.shortener.delivery.ratelimit.RateLimitDecision;
import agentic.shortener.delivery.ratelimit.RedirectRateLimiter;
import agentic.shortener.domain.analytics.AnalyticsRecordingPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The public redirect surface. Tasks T044, T045. FR-URL-018, FR-URL-014, FR-URL-008, FR-URL-007. CR-003.
 *
 * <p><strong>"A short link that requires login is not a short link."</strong> This path reads no
 * credential, requires none, and must not be affected by one. A test asserts that an anonymous follow and
 * a follow carrying a credential produce byte-identical answers, which is the assertion that keeps holding
 * once T052 adds authentication — and stops holding the moment a filter starts having an opinion here.
 *
 * <p><strong>The path pattern is the code's own shape</strong>, {@code [A-Za-z0-9]{7}}, rather than a bare
 * {@code /{shortCode}}. A catch-all single-segment mapping would swallow {@code /actuator} and anything
 * else that happens to live at the root, and "it happens to work today" is not a routing decision. A
 * request that does not have the shape of a code cannot be a code this service issued.
 *
 * <p><strong>Temporary class, and nothing that invites caching.</strong> CR-003 prohibits the permanent
 * class: a cached permanent redirect outlives the link, so analytics could not count follows (FR-URL-010)
 * and expiry could not be enforced (FR-URL-008) — by construction rather than by defect. The
 * {@code Cache-Control: no-store} below is the same argument applied one level down: a temporary redirect
 * a follower was invited to cache would undo the class it was given.
 *
 * <p><strong>Three distinguishable outcomes</strong> (T045): 404 never-issued, 410 expired, 503 store
 * failure. The collapse that must not happen is 503 into 404, and it is prevented by not catching the
 * store's failure anywhere on the way here.
 */
@RestController
public class RedirectController {

    private final ResolveLinkUseCase resolve;
    private final AnalyticsRecordingPort analytics;
    private final RedirectRateLimiter rateLimiter;
    private final Clock clock;

    public RedirectController(ResolveLinkUseCase resolve, AnalyticsRecordingPort analytics,
                              RedirectRateLimiter rateLimiter, Clock clock) {
        this.resolve = Objects.requireNonNull(resolve, "resolve");
        this.analytics = Objects.requireNonNull(analytics, "analytics");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @GetMapping("/{shortCode:[A-Za-z0-9]{7}}")
    public ResponseEntity<Map<String, Object>> follow(@PathVariable String shortCode) {
        // Throttling FIRST, before the store is touched. A limiter that ran after the lookup would
        // still do the work the limit exists to prevent — PVT-013 is hotspot protection, and protecting
        // the store from a hammered link means not querying it.
        RateLimitDecision limit = rateLimiter.check(shortCode);
        if (!limit.allowed()) {
            return throttled(limit);
        }

        ResolveLinkUseCase.Resolution resolution = resolve.resolve(shortCode);

        if (resolution.outcome() == ResolveLinkUseCase.Outcome.REDIRECT) {
            // One event per redirect (FR-URL-010), through the port and never around it (ADR-014
            // Condition 2). This call cannot throw — that is the port's contract, and it is what keeps
            // EC-012 true: a resolvable link must not go dark because a counter could not be written.
            analytics.record(shortCode, clock.instant());
        }

        return switch (resolution.outcome()) {
            case REDIRECT -> ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                    // Set as a raw header rather than through location(URI), which would re-encode and
                    // break FR-URL-007's byte-for-byte requirement.
                    .header("Location", resolution.destination())
                    .header("Cache-Control", "no-store")
                    .build();

            case EXPIRED -> error(HttpStatus.GONE, "LINK_EXPIRED", "This link has expired.");

            // The code is not echoed. It is caller-supplied text, and FR-URL-002's negative clause
            // applies to every refusal, not only to creation's.
            case NOT_FOUND -> error(HttpStatus.NOT_FOUND, "CODE_NOT_FOUND", "No such short code.");
        };
    }

    /**
     * A store failure is 503, never 404.
     *
     * <p>The detail stays out of the body: T018 keeps verbose error detail off, and the exception's own
     * message names a table and a code. A public follower has no business seeing either.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> onStoreProblem(IllegalStateException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "STORE_UNAVAILABLE",
                "This link cannot be resolved right now.");
    }

    /**
     * 429, naming the tier and nothing else.
     *
     * <p>FR-URL-016 forbids a throttled response disclosing the owning creator's identity to a public
     * follower. The tier name is {@code per-code} and carries no creator, which is enforced one level
     * down: {@link RedirectRateLimiter} has no creator parameter to disclose.
     */
    private static ResponseEntity<Map<String, Object>> throttled(RateLimitDecision limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "RATE_LIMITED");
        body.put("message", "Too many requests.");
        body.put("detail", "Tier: " + limit.tier() + ".");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(limit.retryAfterSeconds()))
                .body(body);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code,
                                                             String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
