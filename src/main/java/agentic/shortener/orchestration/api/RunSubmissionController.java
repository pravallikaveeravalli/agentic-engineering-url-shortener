package agentic.shortener.orchestration.api;

import agentic.shortener.orchestration.conductor.Conductor;
import agentic.shortener.orchestration.graph.StageTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * The run creation and requirement submission surface. Task T082a. FR-ORC-007, FR-ORC-001, NFR-OBS-001,
 * CN-012, SC-016. CR-013, ADR-005, ADR-008.
 *
 * <pre>POST /v1/runs</pre>
 *
 * <p><strong>FR-ORC-007 had zero tasks and zero contract operations before this task</strong> — a Confirmed
 * requirement with no way to satisfy it (analyze finding H7). No previously-built endpoint could admit a
 * requirement of a reviewer's own choosing, which meant SC-016 was unreachable through any documented
 * interface. The requirement text is untrusted content the same way every submitted requirement is: it
 * reaches AI stages as prompt input, so {@link Conductor} threads it to the CLI adapter as an argv element,
 * never as a shell string (ADR-004-A1, T073) — this controller does nothing to it beyond passing it through.
 *
 * <h2>Fast, bounded response — the run is not fully driven before this method returns</h2>
 *
 * <p>{@link Conductor#submit} deliberately only performs the run's fast, deterministic materialization
 * (thirteen nodes persisted, S1 admitted). Actually driving the run — S2 onward, including real AI calls
 * that can each take minutes (plan &sect;3's own {@code PVT-016} thresholds) — is kicked off on a
 * background thread <em>after</em> the response is already being built, exactly the shape
 * {@code contracts/openapi.yaml}'s own {@code runCreated} example shows: S1 {@code SUCCEEDED}, S2 already
 * {@code RUNNING}, everything else still {@code BLOCKED}. A caller that blocked an HTTP request for however
 * long the whole pipeline takes would be a design nobody could use; polling {@code GET /v1/runs/{runId}}
 * afterward is the intended shape (T067's own surface).
 *
 * <p><strong>Carries no credential</strong> (CN-012), matching {@link RunInspectionController} and {@link
 * GateDecisionController} exactly — no {@code Authorization} header is read or required.
 */
@RestController
public final class RunSubmissionController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Conductor conductor;
    private final RunInspectionQuery inspectionQuery;
    private final ExecutorService advancePool;

    public RunSubmissionController(Conductor conductor, RunInspectionQuery inspectionQuery,
            ExecutorService advancePool) {
        this.conductor = Objects.requireNonNull(conductor, "conductor");
        this.inspectionQuery = Objects.requireNonNull(inspectionQuery, "inspectionQuery");
        this.advancePool = Objects.requireNonNull(advancePool, "advancePool");
    }

    /** The wire shape {@code CreateRunRequest} defines — a single required field, matched exactly
     * ({@code additionalProperties: false} in the contract). */
    public record CreateRunRequest(String requirement) {
    }

    @PostMapping("/v1/runs")
    public ResponseEntity<JsonNode> create(@RequestBody(required = false) CreateRunRequest request) {
        if (request == null || request.requirement() == null || request.requirement().isBlank()) {
            return badRequest("A requirement is required to create a run.");
        }

        UUID runId;
        try {
            runId = conductor.submit(StageTemplate.standard(), "policy-set-1.1.0", request.requirement());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (RuntimeException e) {
            // A run is not created unless its state is durable (FR-ORC-004) — createRun's own transaction
            // already guarantees nothing partial lands if this throws; this path is reached only when the
            // store itself could not be reached at all.
            return storeUnavailable("Run state cannot be persisted.");
        }

        // Fire-and-forget: this request already has its durable run id and its fast half persisted. The
        // rest of the pipeline's own progress is inspectable afterward via GET /v1/runs/{runId}, exactly
        // as a gate-decision resumption is (GateDecisionController).
        advancePool.execute(() -> conductor.advance(runId));

        JsonNode inspection = inspectionQuery.inspect(runId).orElseThrow(
                () -> new IllegalStateException("just-created run " + runId + " is not inspectable"));
        return ResponseEntity.status(HttpStatus.CREATED).body(inspection);
    }

    private static ResponseEntity<JsonNode> badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
    }

    private static ResponseEntity<JsonNode> storeUnavailable(String message) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "STORE_UNAVAILABLE", message);
    }

    private static ResponseEntity<JsonNode> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(JSON.valueToTree(body));
    }
}
