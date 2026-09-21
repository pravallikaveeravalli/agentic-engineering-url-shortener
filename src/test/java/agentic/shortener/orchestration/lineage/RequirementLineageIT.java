package agentic.shortener.orchestration.lineage;

import agentic.shortener.orchestration.graph.StageTemplate;
import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T082 — requirement, ambiguity, clarification, and task records. FR-ORC-009..012. KE-07..KE-12.
 *
 * <p>Two things this task's Validate and Done clauses ask for, and this class proves both: an orphan-task
 * test — a {@code TaskRecord} with zero requirement ids is rejected — and that normalization preserves
 * every submitted requirement, meaning N requirements in produces N distinct {@code RequirementRecord}s
 * out, never merged or silently dropped.
 */
@DisplayName("T082 — requirement lineage: normalization preserves every requirement; no orphan tasks")
class RequirementLineageIT extends PostgresIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private LineageStore store;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        store = new LineageStore(connections, Clock.fixed(T0, ZoneOffset.UTC));
        runId = new JdbcRunStore(connections, Clock.fixed(T0, ZoneOffset.UTC))
                .createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
    }

    // ==============================================================================================
    // RequirementRecord — normalization preserves every submitted requirement
    // ==============================================================================================

    @Test
    @DisplayName("N requirements submitted produces N RequirementRecords, none merged or dropped")
    void normalizationPreservesEverySubmittedRequirement() {
        List<RequirementRecord> submitted = List.of(
                new RequirementRecord(UUID.randomUUID(), "REQ-1", RequirementType.FUNCTIONAL,
                        "Shorten a URL", RequirementStatus.NORMALIZED),
                new RequirementRecord(UUID.randomUUID(), "REQ-2", RequirementType.FUNCTIONAL,
                        "Redirect a shortened URL", RequirementStatus.NORMALIZED),
                new RequirementRecord(UUID.randomUUID(), "REQ-3", RequirementType.NON_FUNCTIONAL,
                        "Redirect latency under 100ms at p99", RequirementStatus.NORMALIZED));

        for (RequirementRecord r : submitted) {
            store.recordRequirement(runId, r);
        }

        List<RequirementRecord> stored = store.requirementsFor(runId);
        assertEquals(3, stored.size(),
                "three submitted, three stored — normalization MUST NOT discard, merge or silently "
                        + "reinterpret a submitted requirement");
        assertEquals(
                submitted.stream().map(RequirementRecord::externalId).sorted().toList(),
                stored.stream().map(RequirementRecord::externalId).sorted().toList(),
                "every submitted external id must survive, unmerged");
        assertEquals(
                submitted.stream().map(RequirementRecord::statement).sorted().toList(),
                stored.stream().map(RequirementRecord::statement).sorted().toList(),
                "and every statement must survive unaltered");
    }

    @Test
    @DisplayName("an external id is unique per run, so a task can address a requirement unambiguously")
    void externalIdUniquePerRun() {
        UUID first = UUID.randomUUID();
        store.recordRequirement(runId, new RequirementRecord(first, "REQ-1",
                RequirementType.FUNCTIONAL, "first", RequirementStatus.NORMALIZED));

        assertThrows(DuplicateRequirementIdException.class,
                () -> store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                        RequirementType.FUNCTIONAL, "a different statement, same external id",
                        RequirementStatus.NORMALIZED)),
                "FR-ORC-012: task decomposition addresses a requirement by this id, so two requirements "
                        + "sharing one id in the same run would make that addressing ambiguous");
    }

    @Test
    @DisplayName("the same external id in a DIFFERENT run is a different requirement")
    void externalIdScopedPerRun() {
        UUID other = new JdbcRunStore(connections, Clock.fixed(T0, ZoneOffset.UTC))
                .createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());

        store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "in the first run", RequirementStatus.NORMALIZED));
        store.recordRequirement(other, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "in the second run", RequirementStatus.NORMALIZED));

        assertEquals(1, store.requirementsFor(runId).size());
        assertEquals(1, store.requirementsFor(other).size());
    }

    // ==============================================================================================
    // TaskRecord — the orphan-task test (T082's own named Validate clause)
    // ==============================================================================================

    @Test
    @DisplayName("a TaskRecord with ZERO requirement ids is REJECTED")
    void orphanTaskIsRejected() {
        // The named test. Nothing must be written for a rejected attempt — a partially-written orphan
        // task would be the same defect with extra steps.
        assertThrows(IllegalArgumentException.class,
                () -> store.recordTask(runId, TaskValidationStatus.PENDING, List.of(), List.of()),
                "FR-ORC-012: every task traces to at least one requirement; decomposition MUST NOT "
                        + "invent scope (Constitution I)");

        assertEquals(0, countTaskRecords(),
                "the rejected attempt must leave nothing behind, not a row with zero children");
    }

    @Test
    @DisplayName("a task with at least one requirement id is accepted and the link is a real foreign key")
    void taskWithRequirementIsAccepted() {
        UUID reqId = store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "shorten a url", RequirementStatus.NORMALIZED));

        UUID taskId = store.recordTask(runId, TaskValidationStatus.PENDING, List.of(reqId), List.of());

        assertEquals(List.of(reqId), store.requirementIdsFor(taskId),
                "the stored association must be exactly the ids given");
    }

    @Test
    @DisplayName("a task referencing a requirement id that does not exist is refused — a REAL foreign key")
    void taskCannotReferenceANonexistentRequirement() {
        // Normalized into its own join table rather than an array column precisely so each reference is
        // a real foreign key: an array of ids could name a requirement that was never recorded, and this
        // project's standing rule is structural enforcement over detection (the same reasoning
        // ArtifactWriteGuard and JdbcRunStore's edges apply).
        UUID nonexistent = UUID.randomUUID();
        assertThrows(RuntimeException.class,
                () -> store.recordTask(runId, TaskValidationStatus.PENDING, List.of(nonexistent), List.of()));
        assertEquals(0, countTaskRecords(), "the refused attempt leaves nothing behind either");
    }

    @Test
    @DisplayName("dependency-ordered tasks: a task may declare which tasks it depends on")
    void tasksCanDeclareDependencies() {
        UUID reqId = store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "shorten a url", RequirementStatus.NORMALIZED));
        UUID first = store.recordTask(runId, TaskValidationStatus.PENDING, List.of(reqId), List.of());
        UUID second = store.recordTask(runId, TaskValidationStatus.PENDING, List.of(reqId), List.of(first));

        assertEquals(List.of(first), store.dependenciesOf(second));
        assertEquals(List.of(), store.dependenciesOf(first));
    }

    @Test
    @DisplayName("a task cannot depend on itself")
    void taskCannotDependOnItself() {
        UUID reqId = store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "shorten a url", RequirementStatus.NORMALIZED));

        // Refused at the store's boundary, before the id it would self-reference even exists to be
        // inserted against — a genuine self-loop is only constructible by naming an ALREADY-persisted
        // task's own id back to it, proven separately below.
        UUID taskId = store.recordTask(runId, TaskValidationStatus.PENDING, List.of(reqId), List.of());
        assertThrows(RuntimeException.class,
                () -> store.appendDependency(taskId, taskId),
                "the same structural refusal dependency_edge already applies to graph edges");
    }

    // ==============================================================================================
    // AmbiguityRecord and ClarificationDecision
    // ==============================================================================================

    @Test
    @DisplayName("DS-A: a NOT_MATERIAL ambiguity MUST carry a substantive no_clarification_reason")
    void notMaterialRequiresAReason() {
        UUID reqId = store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "shorten a url", RequirementStatus.NORMALIZED));

        // The Guard, made structural: NOT_MATERIAL without a reason is refused by the store, not merely
        // discouraged by convention — the whole point of no_clarification_reason is that a non-detection
        // must be inspectable rather than silent, and an optional field a caller can skip would not be.
        assertThrows(RuntimeException.class, () -> store.recordAmbiguity(reqId,
                new AmbiguityRecord(UUID.randomUUID(), AmbiguityClass.MISSING_ACCEPTANCE_CRITERIA,
                        "requirement text", ResolutionState.NOT_MATERIAL,
                        "checked for missing criteria, undefined terms, unbounded quantifiers", null)));
    }

    @Test
    @DisplayName("a MATERIAL_PENDING ambiguity must NOT carry a no_clarification_reason")
    void materialPendingMustNotCarryAReason() {
        UUID reqId = store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "shorten a url", RequirementStatus.NORMALIZED));

        // The mirror direction of the same constraint: a reason present on a pending ambiguity would
        // claim the non-detection inspection happened for something that was, in fact, detected.
        assertThrows(RuntimeException.class, () -> store.recordAmbiguity(reqId,
                new AmbiguityRecord(UUID.randomUUID(), AmbiguityClass.SEMANTIC_CONTRADICTION,
                        "expiry vs retention vs redirect entitlement", ResolutionState.MATERIAL_PENDING,
                        "semantic cross-check", "should not be here")));
    }

    @Test
    @DisplayName("a material ambiguity resolved by a ClarificationDecision moves to RESOLVED")
    void clarificationResolvesAMaterialAmbiguity() {
        UUID reqId = store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "shorten a url", RequirementStatus.NORMALIZED));
        UUID ambiguityId = store.recordAmbiguity(reqId,
                new AmbiguityRecord(UUID.randomUUID(), AmbiguityClass.SEMANTIC_CONTRADICTION,
                        "expiry vs retention vs redirect entitlement", ResolutionState.MATERIAL_PENDING,
                        "semantic cross-check", null));

        store.resolveWithClarification(ambiguityId,
                new ClarificationDecision(UUID.randomUUID(), "the owner",
                        "should redirects still work for trusted partners after expiry?", "no", T0));

        assertEquals(ResolutionState.RESOLVED, store.ambiguityById(ambiguityId).resolutionState(),
                "once a human has answered, the ambiguity is no longer pending");
    }

    @Test
    @DisplayName("a clean requirement records its quality checks even with no ambiguity found")
    void notMaterialRecordsWhatWasChecked() {
        UUID reqId = store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "shorten a url with an expiry", RequirementStatus.NORMALIZED));

        UUID ambiguityId = store.recordAmbiguity(reqId,
                new AmbiguityRecord(UUID.randomUUID(), AmbiguityClass.MISSING_ACCEPTANCE_CRITERIA,
                        "shorten a url with an expiry", ResolutionState.NOT_MATERIAL,
                        "checked missing acceptance criteria, undefined terms, unbounded quantifiers, "
                                + "missing actor, self-referential constraints, contradictory bounds",
                        "well-formed; no material ambiguity found across six structural checks and a "
                                + "semantic cross-check"));

        AmbiguityRecord stored = store.ambiguityById(ambiguityId);
        assertTrue(stored.qualityChecksPerformed().length() > 20,
                "the quality-checks field is what makes a non-detection inspectable (DS-A); a placeholder "
                        + "would defeat the whole point of requiring it");
        assertEquals("well-formed; no material ambiguity found across six structural checks and a "
                + "semantic cross-check", stored.noClarificationReason());
    }

    @Test
    @DisplayName("the DB-level reason-iff-not-material CHECK is a real, independently-firing backstop")
    void dbLevelReasonConstraintFiresIndependently() throws Exception {
        // AmbiguityRecord's own constructor already refuses this combination, which makes the store's
        // path to ambiguity_record_reason_iff_not_material unreachable from normal application code.
        // That is not the same situation as CR-032's FALLBACK or CR-039's ANY: those were values NO code
        // path could ever produce, misdescribing a capability the system did not have. Here two layers
        // enforce one real invariant redundantly — the constructor for fail-fast, the CHECK as a
        // backstop against anything that bypasses this specific constructor (a future caller, a direct
        // migration, a bug reached through reflection). INJECTED (AS-007): this row is written with raw
        // SQL specifically to prove that backstop is not decorative SQL that looks protective but never
        // actually fires.
        UUID reqId = store.recordRequirement(runId, new RequirementRecord(UUID.randomUUID(), "REQ-1",
                RequirementType.FUNCTIONAL, "shorten a url", RequirementStatus.NORMALIZED));

        try (var c = connections.get();
             var ps = c.prepareStatement(
                     "INSERT INTO ambiguity_record (ambiguity_record_id, requirement_record_id, "
                             + "ambiguity_class, affected_path, resolution_state, "
                             + "quality_checks_performed, no_clarification_reason) "
                             + "VALUES (?, ?, 'UNDEFINED_TERM', 'path', 'NOT_MATERIAL', 'checked', NULL)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, reqId);
            var refused = assertThrows(java.sql.SQLException.class, ps::executeUpdate);
            assertEquals("23514", refused.getSQLState(),
                    "a CHECK violation specifically, not some other failure: " + refused.getMessage());
        }
    }

    private int countTaskRecords() {
        try (var c = connections.get();
             var ps = c.prepareStatement("SELECT COUNT(*) FROM task_record WHERE run_id = ?")) {
            ps.setObject(1, runId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException("count failed", e);
        }
    }
}
