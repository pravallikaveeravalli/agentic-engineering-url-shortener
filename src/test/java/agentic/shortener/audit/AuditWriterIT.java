package agentic.shortener.audit;

import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T111 — the audit writer, validated against {@code contracts/audit-event.schema.json}. FR-ORC-023,
 * NFR-AUD-001. KE-19.
 */
@DisplayName("T111 — AuditWriter: schema conformance, six fields enforced, corrections appended")
class AuditWriterIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonSchema AUDIT_SCHEMA = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(readSchema());

    private static JsonNode readSchema() {
        try {
            return JSON.readTree(Files.readString(
                    Path.of("specs/001-agentic-sdlc-url-shortener/contracts/audit-event.schema.json")));
        } catch (Exception e) {
            throw new IllegalStateException("could not read audit-event.schema.json", e);
        }
    }

    private ConnectionSource connections;
    private AuditWriter writer;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        writer = new AuditWriter(connections);
        runId = UUID.randomUUID();
    }

    private AuditEvent event(String action, String affectedArtifact, String result, String reason) {
        return new AuditEvent(null, runId, "system", action, T0, affectedArtifact, result, reason,
                null, null, null);
    }

    @Test
    @DisplayName("a written event round-trips and conforms to contracts/audit-event.schema.json")
    void writtenEventConformsToTheContract() {
        long id = writer.write(event("RUN_CREATED", "workflow_run", "SUCCESS", "run materialized"));
        AuditEvent reread = writer.byId(id).orElseThrow();

        Set<ValidationMessage> errors = AUDIT_SCHEMA.validate(reread.toJson());
        assertTrue(errors.isEmpty(), "schema violations: " + errors);
    }

    @Test
    @DisplayName("an event carrying stageNumber and executorKindUsed also conforms")
    void aStageExecutionEventConforms() {
        AuditEvent stageEvent = new AuditEvent(null, runId, "agent", "EXECUTOR_DISPATCHED", T0,
                "S2", "SUCCESS", "normalization dispatched", 2, "AI", null);
        long id = writer.write(stageEvent);
        AuditEvent reread = writer.byId(id).orElseThrow();

        Set<ValidationMessage> errors = AUDIT_SCHEMA.validate(reread.toJson());
        assertTrue(errors.isEmpty(), "schema violations: " + errors);
        assertEquals(2, reread.stageNumber());
        assertEquals("AI", reread.executorKindUsed());
    }

    // ==============================================================================================
    // a record missing any (mandatory) field is rejected — structurally, before it ever reaches the
    // store, matching this codebase's established discipline (GateDecision, ImpactAnalysis, ...)
    // ==============================================================================================

    @Test
    @DisplayName("NEGATIVE: a blank reason is rejected — an unexplained record is not auditable")
    void blankReasonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditEvent(null, runId, "system", "RUN_CREATED", T0, "workflow_run",
                        "SUCCESS", " ", null, null, null));
    }

    @Test
    @DisplayName("NEGATIVE: an unrecognized actorType is rejected")
    void unrecognizedActorTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditEvent(null, runId, "robot", "RUN_CREATED", T0, "workflow_run",
                        "SUCCESS", "x", null, null, null));
    }

    @Test
    @DisplayName("NEGATIVE: an unrecognized action is rejected — the schema's action vocabulary is closed")
    void unrecognizedActionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditEvent(null, runId, "system", "SOMETHING_MADE_UP", T0, "workflow_run",
                        "SUCCESS", "x", null, null, null));
    }

    @Test
    @DisplayName("NEGATIVE: an unrecognized result is rejected")
    void unrecognizedResultIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditEvent(null, runId, "system", "RUN_CREATED", T0, "workflow_run",
                        "MAYBE", "x", null, null, null));
    }

    @Test
    @DisplayName("NEGATIVE: a blank affectedArtifact is rejected")
    void blankAffectedArtifactIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuditEvent(null, runId, "system", "RUN_CREATED", T0, " ",
                        "SUCCESS", "x", null, null, null));
    }

    @Test
    @DisplayName("NEGATIVE: a missing runId is rejected — every event this writer produces is run-scoped")
    void missingRunIdIsRejected() {
        assertThrows(NullPointerException.class,
                () -> new AuditEvent(null, null, "system", "RUN_CREATED", T0, "workflow_run",
                        "SUCCESS", "x", null, null, null));
    }

    // ==============================================================================================
    // corrections are appended, never edits
    // ==============================================================================================

    @Test
    @DisplayName("Done: a correction is APPENDED with correctsEventId — the original is never edited")
    void correctionIsAppendedNeverEdits() {
        long originalId = writer.write(event("POLICY_EVALUATED", "policy-set-1.1.0", "FAILURE",
                "recorded with the wrong result by a bug"));

        AuditEvent correction = new AuditEvent(null, runId, "system", "POLICY_EVALUATED",
                T0.plusSeconds(60), "policy-set-1.1.0", "SUCCESS",
                "correcting record " + originalId + ": the original mis-recorded the outcome",
                null, null, originalId);
        long correctionId = writer.write(correction);

        // The original is untouched — this class has no UPDATE method, and the privilege grant (T028)
        // backstops that structurally even for a caller that tried to bypass it.
        AuditEvent original = writer.byId(originalId).orElseThrow();
        assertEquals("FAILURE", original.result(), "the original record must be untouched");

        AuditEvent reread = writer.byId(correctionId).orElseThrow();
        assertEquals(Long.valueOf(originalId), reread.correctsEventId());
        Set<ValidationMessage> errors = AUDIT_SCHEMA.validate(reread.toJson());
        assertTrue(errors.isEmpty(), "schema violations: " + errors);
    }

    @Test
    @DisplayName("NEGATIVE: an event cannot correct itself")
    void anEventCannotCorrectItself() {
        // eventId is null before recording, so self-correction can only be attempted with an ALREADY
        // recorded id supplied as both — the realistic shape a caller bug would produce.
        long id = writer.write(event("RUN_CREATED", "workflow_run", "SUCCESS", "x"));
        AuditEvent recorded = writer.byId(id).orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> new AuditEvent(id, recorded.runId(), recorded.actorType(), recorded.action(),
                        recorded.occurredAt(), recorded.affectedArtifact(), recorded.result(),
                        recorded.reason(), null, null, id));
    }

    // ==============================================================================================
    // POL-AUD-001 — "every audit record has all six fields" (plan §7), proven as a real query
    // ==============================================================================================

    @Test
    @DisplayName("POL-AUD-001: every written record has all six mandatory fields, queried directly")
    void polAud001AllSixFieldsPresent() {
        writer.write(event("RUN_CREATED", "workflow_run", "SUCCESS", "run materialized"));
        writer.write(event("GATE_REQUESTED", "S4", "NOT_APPLICABLE", "material ambiguity found"));
        writer.write(event("RUN_SUSPENDED", "workflow_run", "SUSPENDED", "gate timed out"));

        try (var c = connections.get(); var ps = c.prepareStatement(
                "SELECT COUNT(*) FROM audit_record WHERE run_id = ? AND ("
                        + "actor_type IS NULL OR btrim(actor_type) = '' "
                        + "OR action IS NULL OR btrim(action) = '' "
                        + "OR occurred_at IS NULL "
                        + "OR affected_artifact IS NULL OR btrim(affected_artifact) = '' "
                        + "OR result IS NULL OR btrim(result) = '' "
                        + "OR reason IS NULL OR btrim(reason) = '')")) {
            ps.setObject(1, runId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1),
                        "POL-AUD-001: every audit record must have all six mandatory fields");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ==============================================================================================
    // T112 — the correlation identifier (runId) is present on 100% of a sampled run's audit rows
    // ==============================================================================================

    @Test
    @DisplayName("T112: the correlation identifier (runId) is present on 100% of a sampled run's rows")
    void correlationIdentifierPresentOnEveryRow() {
        writer.write(event("RUN_CREATED", "workflow_run", "SUCCESS", "run materialized"));
        writer.write(event("STAGE_ENTERED", "S1", "SUCCESS", "S1 entered"));
        writer.write(event("STAGE_EXITED", "S1", "SUCCESS", "S1 exited"));
        writer.write(event("REPLAN_EXECUTED", "S3", "SUCCESS", "requirement changed"));
        writer.write(event("RUN_COMPLETED", "workflow_run", "SUCCESS", "run finished"));

        try (var c = connections.get(); var ps = c.prepareStatement(
                "SELECT COUNT(*) AS total, COUNT(run_id) AS with_run_id, "
                        + "COUNT(CASE WHEN run_id = correlation_id THEN 1 END) AS matching "
                        + "FROM audit_record WHERE run_id = ?")) {
            ps.setObject(1, runId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                int total = rs.getInt("total");
                assertTrue(total >= 5, "the sample must actually contain the rows just written");
                assertEquals(total, rs.getInt("with_run_id"),
                        "the correlation identifier must be present on 100% of this run's rows");
                assertEquals(total, rs.getInt("matching"),
                        "runId IS the correlation identifier for every row this writer produces — "
                                + "see AuditEvent's own javadoc for why no separate value is carried");
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
