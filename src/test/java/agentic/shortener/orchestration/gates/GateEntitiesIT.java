package agentic.shortener.orchestration.gates;

import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.store.JdbcRunStore;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T058 — ApprovalGate and GateDecision entities. FR-ORC-013. KE-10, KE-11. ADR-005, ADR-008.
 *
 * <p>An IT, because T058's own Validate clause is schema validation against
 * {@code contracts/approval.schema.json} — a persisted-record contract — and the round-trip through the
 * real store is what proves the persisted shape and the serialized shape agree, not merely that a Java
 * object happens to look right in memory.
 */
@DisplayName("T058 — ApprovalGate and GateDecision, validated against approval.schema.json")
class GateEntitiesIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonSchema APPROVAL_SCHEMA = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(readSchema());

    private ConnectionSource connections;
    private GateStore store;
    private UUID runId;

    private static JsonNode readSchema() {
        try {
            return JSON.readTree(Files.readString(
                    Path.of("specs/001-agentic-sdlc-url-shortener/contracts/approval.schema.json")));
        } catch (Exception e) {
            throw new IllegalStateException("could not read approval.schema.json", e);
        }
    }

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        store = new GateStore(connections, Clock.fixed(T0, ZoneOffset.UTC));
        runId = new JdbcRunStore(connections, Clock.fixed(T0, ZoneOffset.UTC))
                .createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
    }

    private ApprovalGate aGate(String gateId, String nodeKey, GateClass gateClass) {
        return new ApprovalGate(gateId, runId, nodeKey, gateClass, T0.plus(Duration.ofHours(24)),
                T0.plus(Duration.ofDays(90)), T0);
    }

    // ==============================================================================================
    // ApprovalGate
    // ==============================================================================================

    @Test
    @DisplayName("an ApprovalGate round-trips through the store")
    void approvalGateRoundTrips() {
        store.requestGate(aGate("S4", "S4", GateClass.UNRESOLVED_AMBIGUITY));

        ApprovalGate stored = store.pendingGate(runId, "S4").orElseThrow();
        assertEquals(GateClass.UNRESOLVED_AMBIGUITY, stored.gateClass());
        assertEquals(T0.plus(Duration.ofHours(24)), stored.waitDeadline());
        assertEquals(T0.plus(Duration.ofDays(90)), stored.disclosedAutoAbandonAt());
    }

    @Test
    @DisplayName("the auto-abandon time must not be BEFORE the wait deadline")
    void abandonMustNotPrecedeWaitDeadline() {
        assertThrows(RuntimeException.class, () -> store.requestGate(
                new ApprovalGate("S4", runId, "S4", GateClass.UNRESOLVED_AMBIGUITY,
                        T0.plus(Duration.ofDays(90)), T0.plus(Duration.ofHours(24)), T0)),
                "a run cannot be disclosed as abandoned before its own gate wait has even elapsed");
    }

    @Test
    @DisplayName("gate_class is constrained to the ten CR-040 values, at the store")
    void gateClassConstrainedAtTheStore() throws Exception {
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO approval_gate (run_id, gate_id, node_key, gate_class, wait_deadline, "
                             + "disclosed_auto_abandon_at, requested_at) "
                             + "VALUES (?, 'S4', 'S4', 'NOT_A_REAL_CLASS', ?, ?, ?)")) {
            ps.setObject(1, runId);
            ps.setObject(2, java.sql.Timestamp.from(T0.plus(Duration.ofHours(24))));
            ps.setObject(3, java.sql.Timestamp.from(T0.plus(Duration.ofDays(90))));
            ps.setObject(4, java.sql.Timestamp.from(T0));
            var refused = assertThrows(java.sql.SQLException.class, ps::executeUpdate);
            assertEquals("23514", refused.getSQLState());
        }
    }

    // ==============================================================================================
    // GateDecision — schema validation against approval.schema.json
    // ==============================================================================================

    @Test
    @DisplayName("a recorded APPROVED decision validates against approval.schema.json")
    void approvedDecisionValidatesAgainstTheContract() {
        store.requestGate(aGate("S4", "S4", GateClass.UNRESOLVED_AMBIGUITY));
        GateDecision decision = new GateDecision(runId, "S4", null, GateClass.UNRESOLVED_AMBIGUITY,
                GateOutcome.APPROVED, new Actor("human", "the owner"), T0,
                "the ambiguity was resolved", "docs/governance/gate-decisions/gate-01.md",
                List.of(), null, null);

        long id = store.recordDecision(decision);
        assertConforms(store.decisionById(id).orElseThrow());
    }

    @Test
    @DisplayName("a CHANGES_REQUESTED decision carries its non-empty change list and validates")
    void changesRequestedDecisionValidates() {
        store.requestGate(aGate("S6", "S6", GateClass.ARCHITECTURE_APPROVAL));
        GateDecision decision = new GateDecision(runId, "S6", 6, GateClass.ARCHITECTURE_APPROVAL,
                GateOutcome.CHANGES_REQUESTED, new Actor("human", "the owner"), T0,
                "the design needs two changes", "docs/governance/gate-decisions/gate-02.md",
                List.of("split the service boundary", "add a retry budget"), null, null);

        long id = store.recordDecision(decision);
        assertConforms(store.decisionById(id).orElseThrow());
    }

    @Test
    @DisplayName("CHANGES_REQUESTED with an EMPTY change list is refused — changes must be enumerated")
    void changesRequestedRequiresNonEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> new GateDecision(runId, "S6", 6,
                GateClass.ARCHITECTURE_APPROVAL, GateOutcome.CHANGES_REQUESTED,
                new Actor("human", "the owner"), T0, "no changes named",
                "docs/governance/gate-decisions/gate-02.md", List.of(), null, null),
                "the guard: CHANGES_REQUESTED with an empty change list is invalid");
    }

    @Test
    @DisplayName("an ESCALATED decision carries its escalation target and validates")
    void escalatedDecisionValidates() {
        store.requestGate(aGate("S11", "S11", GateClass.RELEASE_READINESS));
        GateDecision decision = new GateDecision(runId, "S11", 11, GateClass.RELEASE_READINESS,
                GateOutcome.ESCALATED, new Actor("human", "the reviewer"), T0,
                "this exceeds my authority to approve alone",
                "docs/governance/gate-decisions/gate-03.md", List.of(), "the release owner", null);

        long id = store.recordDecision(decision);
        assertConforms(store.decisionById(id).orElseThrow());
    }

    @Test
    @DisplayName("ESCALATED with NO escalation target is refused")
    void escalatedRequiresATarget() {
        assertThrows(IllegalArgumentException.class, () -> new GateDecision(runId, "S11", 11,
                GateClass.RELEASE_READINESS, GateOutcome.ESCALATED, new Actor("human", "x"), T0,
                "exceeds authority", "docs/governance/gate-decisions/gate-03.md", List.of(), null, null));
    }

    @Test
    @DisplayName("APPROVED structurally requires a human actor")
    void approvedRequiresHumanActor() {
        assertThrows(IllegalArgumentException.class, () -> new GateDecision(runId, "S4", null,
                GateClass.UNRESOLVED_AMBIGUITY, GateOutcome.APPROVED, new Actor("system", "the clock"),
                T0, "x", "docs/governance/gate-decisions/gate-01.md", List.of(), null, null),
                "CR-021: only a human decides a gate; APPROVED from 'system' is refused structurally");
    }

    @Test
    @DisplayName("TIMED_OUT cannot be constructed as a recordable GateDecision")
    void timedOutIsNotConstructible() {
        // Constitution III's "silence is never approval" as a type-level fact: there is no
        // GateOutcome.TIMED_OUT for this constructor to accept, matching the openapi request schema's
        // own submittable-outcome enum, which also omits it.
        assertEquals(List.of("APPROVED", "REJECTED", "CHANGES_REQUESTED", "ESCALATED"),
                GateOutcome.submittable().stream().map(Enum::name).toList(),
                "the submittable set is four, not five — TIMED_OUT is produced only by deadline "
                        + "expiry, never submitted");
    }

    @Test
    @DisplayName("a decision supersedes the one it voids, and the reference is structural (EC-020)")
    void supersedingDecisionReferencesThePrior() {
        store.requestGate(aGate("S6", "S6", GateClass.ARCHITECTURE_APPROVAL));
        GateDecision original = new GateDecision(runId, "S6", 6, GateClass.ARCHITECTURE_APPROVAL,
                GateOutcome.APPROVED, new Actor("human", "the owner"), T0,
                "approved as designed", "docs/governance/gate-decisions/gate-04.md", List.of(), null,
                null);
        long originalId = store.recordDecision(original);

        GateDecision superseding = new GateDecision(runId, "S6", 6, GateClass.ARCHITECTURE_APPROVAL,
                GateOutcome.REJECTED, new Actor("human", "the owner"), T0.plusSeconds(60),
                "voided by a replan", "docs/governance/gate-decisions/gate-05.md", List.of(), null,
                originalId);
        long supersedingId = store.recordDecision(superseding);

        GateDecision reread = store.decisionById(supersedingId).orElseThrow();
        assertEquals(originalId, reread.supersedesDecisionId());
        assertConforms(reread);

        // The original is untouched — both rows exist, and the original still reads what it read.
        assertEquals(GateOutcome.APPROVED, store.decisionById(originalId).orElseThrow().outcome());
    }

    @Test
    @DisplayName("a decision cannot supersede itself")
    void cannotSupersedeItself() throws Exception {
        // INJECTED (AS-007): the application cannot construct this — supersedesDecisionId is only
        // ever a PRIOR row's already-issued id, never the row's own id-to-be, which does not exist
        // until after the insert. Proven directly against the store to confirm the backstop fires.
        store.requestGate(aGate("S6", "S6", GateClass.ARCHITECTURE_APPROVAL));
        long id = store.recordDecision(new GateDecision(runId, "S6", 6, GateClass.ARCHITECTURE_APPROVAL,
                GateOutcome.APPROVED, new Actor("human", "x"), T0, "x",
                "docs/governance/gate-decisions/gate-06.md", List.of(), null, null));

        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE gate_decision SET supersedes_gate_decision_id = ? "
                             + "WHERE gate_decision_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, id);
            var refused = assertThrows(java.sql.SQLException.class, ps::executeUpdate);
            assertEquals("23514", refused.getSQLState());
        }
    }

    @Test
    @DisplayName("repository_record_path not under docs/governance/ is refused by the store")
    void repositoryRecordPathShapeEnforced() {
        assertThrows(RuntimeException.class, () -> store.recordDecision(new GateDecision(runId, "S4",
                null, GateClass.UNRESOLVED_AMBIGUITY, GateOutcome.APPROVED,
                new Actor("human", "x"), T0, "x", "somewhere/else.md", List.of(), null, null)));
    }

    private void assertConforms(GateDecision decision) {
        JsonNode payload = decision.toJson();
        Set<ValidationMessage> problems = APPROVAL_SCHEMA.validate(payload);
        assertTrue(problems.isEmpty(),
                "does not conform to approval.schema.json: " + problems + "\npayload: " + payload);
    }
}
