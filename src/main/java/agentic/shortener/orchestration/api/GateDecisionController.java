package agentic.shortener.orchestration.api;

import agentic.shortener.orchestration.conductor.Conductor;
import agentic.shortener.orchestration.gates.ActorAuthority;
import agentic.shortener.orchestration.gates.Actor;
import agentic.shortener.orchestration.gates.ApprovalGate;
import agentic.shortener.orchestration.gates.GateDecision;
import agentic.shortener.orchestration.gates.GateOutcome;
import agentic.shortener.orchestration.gates.GateOutcomeHandler;
import agentic.shortener.orchestration.gates.GateStore;
import agentic.shortener.orchestration.gates.MaterializationCheck;
import agentic.shortener.orchestration.gates.UnmaterializedDecisionException;
import agentic.shortener.orchestration.store.JdbcRunStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The gate-decision recording surface. Task T067a. FR-ORC-013, FR-ORC-021, CN-012, SC-005. CR-013,
 * ADR-005, ADR-008.
 *
 * <pre>POST /v1/runs/{runId}/gates/{gateId}/decision</pre>
 *
 * <p><strong>{@code TIMED_OUT} is deliberately inexpressible here</strong> — {@code GateOutcome} itself has
 * no such value (T058), so {@link GateOutcome#valueOf} throwing on it is the interface's own refusal, not a
 * check this class has to remember. Silence is never an input (Constitution III); the only thing that
 * produces {@code TIMED_OUT} is the gate-wait deadline expiring, which never reaches this endpoint at all.
 *
 * <p><strong>Carries no credential</strong> (CN-012): no {@code Authorization} header is read or required.
 * Actor authority is asserted from the DECLARED actor in the request body — {@code actorType} and
 * {@code actorName} are recorded, not verified against any registry, matching {@link ActorAuthority}'s own
 * disclosed limitation.
 *
 * <p><strong>Resumes the run after applying the decision</strong> (T131a/T131b): {@link
 * GateOutcomeHandler#apply} only ever changes the gated node's and, for {@code REJECTED}, the run's own
 * state — nothing in that call drives the run any further. Before {@link Conductor} existed, nothing here
 * called anything that would; a decision landed and the run simply never progressed past it. {@link
 * Conductor#advance} is called unconditionally after every applied decision, regardless of outcome —
 * {@code advance} itself is a no-op on a run that is not {@link agentic.shortener.orchestration.state.RunState#RUNNING},
 * so this is always safe: a {@code REJECTED} run is already terminal by the time this call happens, an
 * {@code ESCALATED} decision changed nothing for {@code advance} to act on, and {@code APPROVED}/{@code
 * CHANGES_REQUESTED} are exactly the two outcomes that leave something newly ready to run.
 *
 * <h2>Why the 403/400 split for actorType is deliberate, not incidental</h2>
 *
 * <p>{@code contracts/openapi.yaml}'s {@code GateDecisionRequest.actorType} enum is {@code [human]} alone,
 * but a non-human {@code actorType} is answered {@code 403 FORBIDDEN} here — an autonomy violation recorded
 * under EC-024/FR-ORC-021 — rather than a generic {@code 400} schema mismatch. The distinction matters: an
 * agent attempting to approve its own work is a security-relevant event, not a caller typo.
 */
@RestController
public final class GateDecisionController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final GateStore gateStore;
    private final GateOutcomeHandler outcomeHandler;
    private final JdbcRunStore runStore;
    private final RunInspectionQuery inspectionQuery;
    private final Clock clock;
    private final Conductor conductor;

    public GateDecisionController(GateStore gateStore, GateOutcomeHandler outcomeHandler,
                                  JdbcRunStore runStore, RunInspectionQuery inspectionQuery, Clock clock,
                                  Conductor conductor) {
        this.gateStore = Objects.requireNonNull(gateStore, "gateStore");
        this.outcomeHandler = Objects.requireNonNull(outcomeHandler, "outcomeHandler");
        this.runStore = Objects.requireNonNull(runStore, "runStore");
        this.inspectionQuery = Objects.requireNonNull(inspectionQuery, "inspectionQuery");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.conductor = Objects.requireNonNull(conductor, "conductor");
    }

    /**
     * The wire shape {@code GateDecisionRequest} defines. {@code noPlanOption} is accepted (the schema
     * permits it) but not acted on here — {@code NO_CHANGE_PLAN}'s three named choices are a separate
     * mechanism ({@code NoPlanGate}, T065), never routed through a standard {@code GateOutcome} decision.
     */
    public record GateDecisionRequest(String outcome, String actorType, String actorName, String reason,
                                      String repositoryRecordPath, List<String> changes,
                                      String escalationTarget, String noPlanOption) {
    }

    @PostMapping("/v1/runs/{runId}/gates/{gateId}/decision")
    public ResponseEntity<JsonNode> decide(@PathVariable String runId, @PathVariable String gateId,
                                           @RequestBody(required = false) GateDecisionRequest request) {
        UUID id;
        try {
            id = UUID.fromString(runId);
        } catch (IllegalArgumentException e) {
            return notFound("No such run.");
        }

        Optional<ApprovalGate> gate = gateStore.pendingGate(id, gateId);
        if (gate.isEmpty()) {
            return notFound("No open gate with that identifier on this run.");
        }
        if (gateStore.decisionRecorded(id, gateId)) {
            return conflict("That gate already carries a recorded decision.");
        }
        if (request == null) {
            return badRequest("A decision request body is required.");
        }

        GateOutcome outcome;
        try {
            outcome = GateOutcome.valueOf(String.valueOf(request.outcome()));
        } catch (Exception e) {
            return badRequest("outcome must be one of "
                    + GateOutcome.submittable().stream().map(Enum::name).toList()
                    + "; TIMED_OUT is never submitted, it is produced only by the gate-wait deadline");
        }

        // EC-024, FR-ORC-021: checked BEFORE constructing anything, and answered 403 — an autonomy
        // violation, not a generic bad request. actorType accepts only 'human' (CR-021).
        if (!"human".equals(request.actorType())) {
            return forbidden("Only a human actor may record a gate decision.");
        }
        if (isBlank(request.actorName())) {
            return badRequest("actorName is required.");
        }
        if (isBlank(request.reason())) {
            return badRequest("reason is required. A decision without a reason is not a gate record.");
        }
        if (isBlank(request.repositoryRecordPath())) {
            return badRequest("repositoryRecordPath is required and must begin with docs/governance/.");
        }

        Actor actor = new Actor(request.actorType(), request.actorName());
        if (!ActorAuthority.isAuthorized(actor, gate.get().gateClass(), outcome)) {
            return forbidden("Only a human actor may record a gate decision.");
        }

        List<String> changes = request.changes() == null ? List.of() : request.changes();
        Integer stageNumber = runStore.node(id, gate.get().nodeKey()).map(n -> (Integer) n.stageNumber())
                .orElse(null);

        GateDecision decision;
        try {
            decision = new GateDecision(id, gateId, stageNumber, gate.get().gateClass(), outcome, actor,
                    clock.instant(), request.reason(), request.repositoryRecordPath(), changes,
                    request.escalationTarget(), null);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }

        try {
            // CR-001, FR-ORC-013: checked BEFORE the decision is persisted — an unmaterialized decision
            // never reaches the store at all, rather than being written and then found wanting.
            MaterializationCheck.requireMaterialized(decision);
        } catch (UnmaterializedDecisionException e) {
            return badRequest(e.getMessage());
        }

        long decisionId = gateStore.recordDecision(decision);
        GateDecision recorded = gateStore.decisionById(decisionId).orElseThrow();
        outcomeHandler.apply(id, gate.get().nodeKey(), recorded);
        conductor.advance(id);

        return ResponseEntity.ok(inspectionQuery.inspect(id).orElseThrow());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseEntity<JsonNode> notFound(String message) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private static ResponseEntity<JsonNode> conflict(String message) {
        return error(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    private static ResponseEntity<JsonNode> forbidden(String message) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private static ResponseEntity<JsonNode> badRequest(String message) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
    }

    private static ResponseEntity<JsonNode> error(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(JSON.valueToTree(body));
    }
}
