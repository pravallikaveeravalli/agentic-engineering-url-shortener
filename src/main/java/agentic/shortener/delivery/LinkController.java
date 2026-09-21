package agentic.shortener.delivery;

import agentic.shortener.application.LinkService;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.validation.CredentialRedactor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The walking skeleton's HTTP surface. Task T032. FR-URL-015. ADR-001, ADR-012.
 *
 * <p>Two operations: create a link, and read its analytics. Creation now honours CL-008's three marker
 * behaviours (T042). Redirect resolution, authentication and rate limiting are later tasks — T043–T045,
 * T052, T054–T055 — and are deliberately absent rather than stubbed, because a stub that returns a
 * plausible answer is worse than an endpoint that does not exist yet.
 *
 * <p><strong>Responses conform to `contracts/openapi.yaml`, which Gate 7 froze.</strong> The field names
 * and shapes here match the `Link`, `Analytics` and `Error` component schemas; T012's conformance harness
 * will assert that against the live responses rather than trusting this comment.
 */
@RestController
public class LinkController {

    private final LinkService links;

    public LinkController(LinkService links) {
        this.links = links;
    }

    /**
     * Request body matching the `CreateLinkRequest` schema.
     *
     * <p><strong>{@code toString} redacts, and that is not cosmetic.</strong> Spring MVC logs the
     * deserialized body at {@code DEBUG} — {@code Read "application/json" to [CreateLinkRequest[...]]} —
     * which calls this method. A credential-bearing destination therefore reached the log <em>before</em>
     * any validation ran, so refusing it later would not have stopped the leak. FR-URL-017 is
     * non-waivable, and {@code CredentialTelemetryIT} found this by reading what the application actually
     * wrote rather than by reasoning about what it ought to write.
     */
    public record CreateLinkRequest(String destination, String expiresAt) {

        @Override
        public String toString() {
            return "CreateLinkRequest[destination=" + CredentialRedactor.redact(destination)
                    + ", expiresAt=" + expiresAt + "]";
        }
    }

    /**
     * {@code POST /v1/links}. Task T042. FR-URL-001, FR-URL-002, FR-URL-012.
     *
     * <p>Three outcomes, and exactly the three CL-008 defines: <strong>201</strong> for a mint,
     * <strong>200</strong> for a replay — success, not an error, and labelled {@code replay: true} —
     * and <strong>409</strong> for the same marker with different content.
     *
     * <p>The {@code Idempotency-Key} header is optional, and its absence means <em>always mint</em>.
     * There is no destination-based deduplication at any scope, ever.
     */
    @PostMapping("/v1/links")
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody CreateLinkRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String marker) {
        if (request == null || request.destination() == null) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "A destination is required.");
        }

        Instant expiresAt = null;
        if (request.expiresAt() != null && !request.expiresAt().isBlank()) {
            try {
                expiresAt = Instant.parse(request.expiresAt());
            } catch (Exception e) {
                // The parse failure detail is not echoed: T018 keeps verbose error detail off, and an
                // exception message can carry internals a caller has no business seeing.
                return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                        "expiresAt must be an ISO-8601 instant.");
            }
        }

        // The demonstration creator is a deliberate scaffold until authentication lands; LinkService
        // names it as one. KE-01 forbids a link without an owner, so the skeleton needs some owner.
        LinkService.CreationOutcome outcome =
                links.create(links.demonstrationCreator(), marker, request.destination(), expiresAt);

        return switch (outcome.kind()) {
            case MINT -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(linkBody(outcome.link().orElseThrow(), false));
            case REPLAY -> ResponseEntity.ok(linkBody(outcome.link().orElseThrow(), true));
            case CONFLICT -> error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", outcome.reason());
        };
    }

    @GetMapping("/v1/links/{shortCode}/analytics")
    public ResponseEntity<Map<String, Object>> analytics(@PathVariable String shortCode) {
        return links.find(shortCode)
                .<ResponseEntity<Map<String, Object>>>map(link -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("shortCode", link.shortCode());
                    body.put("totalRedirects", links.redirectCount(link.shortCode()));
                    // Timestamp-only events (CL-002). The walking skeleton records no redirects yet,
                    // so the list is empty rather than fabricated.
                    body.put("events", List.of());
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "No link with that code."));
    }

    /**
     * Translates a domain rule violation into 400.
     *
     * <p><strong>This is the boundary that must not lose the refusal.</strong> {@link ShortLink} refuses
     * a non-allow-listed scheme (FR-URL-004, non-waivable), and a controller that swallowed the
     * exception and returned 201 would pass every domain test while shipping the vulnerability.
     * {@code WalkingSkeletonIT} asserts the 400 through HTTP for exactly that reason.
     */
    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<Map<String, Object>> onDomainRefusal(RuntimeException e) {
        // The message is the domain's own and names the rule, not an internal: "scheme 'javascript' is
        // not allow-listed" is actionable, and it is not a stack trace or an exception class name.
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", safeMessage(e));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> onStoreProblem(IllegalStateException e) {
        // Readiness fails closed and so does this: a store problem is 503, not 500, and the detail
        // stays out of the body (T018).
        return error(HttpStatus.SERVICE_UNAVAILABLE, "STORE_UNAVAILABLE",
                "The link store is not reachable.");
    }

    /** The generic refusal, used whenever a message is not provably a domain rule’s own. */
    private static final String GENERIC_REFUSAL = "The request was refused by a validation rule.";

    /**
     * Phrases the domain’s own refusals use. An <strong>allow-list, not a deny-list</strong>, and the
     * difference is a real defect this replaced.
     *
     * <p>The first version filtered out messages containing {@code Exception} or a newline and passed
     * everything else. {@code UUID.fromString} then failed on an internal literal and its message —
     * <em>"Error at index 10 in: ..."</em> — went straight into a 400 body, because it contained
     * neither. {@code WalkingSkeletonIT} caught it. A deny-list cannot work here: it would have to
     * anticipate every internal message format, and the JDK’s are not ours to predict.
     */
    private static final List<String> DOMAIN_REFUSAL_MARKERS = List.of(
            "allow-listed", "FR-URL-", "destination", "expiry", "short code", "creatorId",
            "scheme", "marker", "ISO-8601");

    private static String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank() || message.contains("\n")) {
            return GENERIC_REFUSAL;
        }
        // Only a message that names a rule the caller can act on gets through. Anything else is an
        // internal detail, and T018 keeps those out of responses.
        boolean namesARule = DOMAIN_REFUSAL_MARKERS.stream().anyMatch(message::contains);
        return namesARule && !message.contains("Exception") ? message : GENERIC_REFUSAL;
    }

    private static Map<String, Object> linkBody(ShortLink link, boolean replay) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shortCode", link.shortCode());
        body.put("destination", link.destination());
        body.put("createdAt", link.createdAt().toString());
        body.put("expiresAt", link.expiresAt().toString());
        body.put("state", link.state().name());
        body.put("replay", replay);
        return body;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code,
                                                             String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
