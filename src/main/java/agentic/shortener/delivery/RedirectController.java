package agentic.shortener.delivery;

import agentic.shortener.application.ResolveLinkUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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

    public RedirectController(ResolveLinkUseCase resolve) {
        this.resolve = Objects.requireNonNull(resolve, "resolve");
    }

    @GetMapping("/{shortCode:[A-Za-z0-9]{7}}")
    public ResponseEntity<Map<String, Object>> follow(@PathVariable String shortCode) {
        ResolveLinkUseCase.Resolution resolution = resolve.resolve(shortCode);

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

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code,
                                                             String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
