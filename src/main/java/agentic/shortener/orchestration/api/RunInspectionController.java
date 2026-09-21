package agentic.shortener.orchestration.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The gate inspection view's HTTP surface. Task T067. FR-ORC-008, FR-ORC-013.
 *
 * <pre>GET /v1/runs/{runId}</pre>
 *
 * <p>A thin wrapper — every real decision lives in {@link RunInspectionQuery}, which is what
 * {@code RunInspectionQueryIT} proves conforms to {@code contracts/openapi.yaml}'s {@code RunInspection}
 * schema. This class exists only to translate a path variable into a lookup and a result into an HTTP
 * response.
 *
 * <p><strong>Carries no credential</strong> (CN-012): no {@code @RequestHeader Authorization}, no
 * security annotation, nothing that could consult a creator's identity. Run inspection is an orchestrator
 * governance surface, and creators have no standing in it (owner ruling, 2026-09-20).
 *
 * <p>{@code declaresASecurityRequirement} in {@code CredentialBoundaryTest} (T063a) asserts no class in
 * {@code orchestration.api} is annotated {@code @PreAuthorize} or {@code @Secured} — this class is what
 * that assertion is aimed at, and it stays clean by never having a reason to carry one.
 */
@RestController
public class RunInspectionController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RunInspectionQuery query;

    public RunInspectionController(RunInspectionQuery query) {
        this.query = query;
    }

    @GetMapping("/v1/runs/{runId}")
    public ResponseEntity<JsonNode> inspect(@PathVariable String runId) {
        UUID id;
        try {
            id = UUID.fromString(runId);
        } catch (IllegalArgumentException e) {
            return notFound();
        }

        return query.inspect(id)
                .map(ResponseEntity::ok)
                .orElseGet(RunInspectionController::notFound);
    }

    private static ResponseEntity<JsonNode> notFound() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "NOT_FOUND");
        body.put("message", "No run with that identifier.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(JSON.valueToTree(body));
    }
}
