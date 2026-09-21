package agentic.shortener.orchestration.lineage;

import agentic.shortener.persistence.ConnectionSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Persists the requirement/ambiguity/clarification/task lineage. Task T082. FR-ORC-009..012.
 *
 * <h2>The orphan-task rule is enforced before any I/O</h2>
 *
 * <p>{@link #recordTask} refuses an empty requirement-id list at the top of the method, the same shape
 * {@code StageOutcome.succeeded()} uses for EC-029: a rejected attempt leaves nothing behind, rather than
 * writing a {@code task_record} row and then discovering it has no children.
 *
 * <h2>Requirement links are a real foreign key, not an array</h2>
 *
 * <p>{@code task_requirement} is a join table rather than a {@code UUID[]} column on {@code task_record}.
 * An array could name a requirement id that was never recorded; a foreign key cannot. This is the same
 * "structural enforcement over detection" preference that governs {@code dependency_edge} and
 * {@code artifact_version}'s producing-node reference.
 */
public final class LineageStore {

    private final ConnectionSource connections;
    private final Clock clock;

    public LineageStore(ConnectionSource connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections, "connections");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ==============================================================================================
    // RequirementRecord
    // ==============================================================================================

    public UUID recordRequirement(UUID runId, RequirementRecord requirement) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(requirement, "requirement");

        String sql = "INSERT INTO requirement_record (requirement_record_id, run_id, external_id, "
                + "type, statement, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, requirement.id());
            ps.setObject(2, runId);
            ps.setString(3, requirement.externalId());
            ps.setString(4, requirement.type().name());
            ps.setString(5, requirement.statement());
            ps.setString(6, requirement.status().name());
            ps.executeUpdate();
            return requirement.id();
        } catch (Exception e) {
            if (e instanceof SQLException sql23505 && "23505".equals(sql23505.getSQLState())) {
                throw new DuplicateRequirementIdException(
                        "requirement '" + requirement.externalId() + "' is already recorded for run "
                                + runId + "; FR-ORC-012 requires unique addressing within a run", e);
            }
            throw new IllegalStateException(
                    "failed to record requirement '" + requirement.externalId() + "'", e);
        }
    }

    public List<RequirementRecord> requirementsFor(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        String sql = "SELECT requirement_record_id, external_id, type, statement, status "
                + "FROM requirement_record WHERE run_id = ? ORDER BY requirement_record_id";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RequirementRecord> requirements = new ArrayList<>();
                while (rs.next()) {
                    requirements.add(new RequirementRecord(
                            rs.getObject("requirement_record_id", UUID.class),
                            rs.getString("external_id"),
                            RequirementType.valueOf(rs.getString("type")),
                            rs.getString("statement"),
                            RequirementStatus.valueOf(rs.getString("status"))));
                }
                return List.copyOf(requirements);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read requirements for run " + runId, e);
        }
    }

    // ==============================================================================================
    // TaskRecord — the orphan-task rule
    // ==============================================================================================

    /**
     * @throws IllegalArgumentException if {@code requirementIds} is empty. FR-ORC-012: every task traces
     *                                  to at least one requirement, and decomposition MUST NOT invent
     *                                  scope (Constitution I) — refused before the transaction opens, so
     *                                  a refusal leaves no trace at all
     */
    public UUID recordTask(UUID runId, TaskValidationStatus status, List<UUID> requirementIds,
                           List<UUID> dependsOnTaskIds) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requirementIds, "requirementIds");
        Objects.requireNonNull(dependsOnTaskIds, "dependsOnTaskIds");
        if (requirementIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "a TaskRecord with zero requirement ids is an orphan task and is refused (FR-ORC-012, "
                            + "Constitution I: decomposition MUST NOT invent scope)");
        }

        UUID taskId = UUID.randomUUID();
        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO task_record (task_record_id, run_id, validation_status) "
                                + "VALUES (?, ?, ?)")) {
                    ps.setObject(1, taskId);
                    ps.setObject(2, runId);
                    ps.setString(3, status.name());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO task_requirement (task_record_id, requirement_record_id) "
                                + "VALUES (?, ?)")) {
                    for (UUID requirementId : requirementIds) {
                        ps.setObject(1, taskId);
                        ps.setObject(2, requirementId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                if (!dependsOnTaskIds.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO task_dependency (task_record_id, depends_on_task_record_id) "
                                    + "VALUES (?, ?)")) {
                        for (UUID dependsOn : dependsOnTaskIds) {
                            ps.setObject(1, taskId);
                            ps.setObject(2, dependsOn);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to record task", e);
        }
        return taskId;
    }

    public List<UUID> requirementIdsFor(UUID taskId) {
        return idColumn(taskId, "SELECT requirement_record_id FROM task_requirement "
                + "WHERE task_record_id = ? ORDER BY requirement_record_id", "requirement_record_id");
    }

    public List<UUID> dependenciesOf(UUID taskId) {
        return idColumn(taskId, "SELECT depends_on_task_record_id FROM task_dependency "
                + "WHERE task_record_id = ? ORDER BY depends_on_task_record_id",
                "depends_on_task_record_id");
    }

    /** Appends one dependency edge to an already-recorded task. */
    public void appendDependency(UUID taskId, UUID dependsOnTaskId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(dependsOnTaskId, "dependsOnTaskId");
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO task_dependency (task_record_id, depends_on_task_record_id) "
                             + "VALUES (?, ?)")) {
            ps.setObject(1, taskId);
            ps.setObject(2, dependsOnTaskId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("failed to append dependency", e);
        }
    }

    private List<UUID> idColumn(UUID taskId, String sql, String column) {
        Objects.requireNonNull(taskId, "taskId");
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getObject(column, UUID.class));
                }
                return List.copyOf(ids);
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read " + column + " for task " + taskId, e);
        }
    }

    // ==============================================================================================
    // AmbiguityRecord, ClarificationDecision
    // ==============================================================================================

    public UUID recordAmbiguity(UUID requirementId, AmbiguityRecord ambiguity) {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(ambiguity, "ambiguity");

        String sql = "INSERT INTO ambiguity_record (ambiguity_record_id, requirement_record_id, "
                + "ambiguity_class, affected_path, resolution_state, quality_checks_performed, "
                + "no_clarification_reason) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, ambiguity.id());
            ps.setObject(2, requirementId);
            ps.setString(3, ambiguity.ambiguityClass().name());
            ps.setString(4, ambiguity.affectedPath());
            ps.setString(5, ambiguity.resolutionState().name());
            ps.setString(6, ambiguity.qualityChecksPerformed());
            ps.setString(7, ambiguity.noClarificationReason());
            ps.executeUpdate();
            return ambiguity.id();
        } catch (Exception e) {
            throw new IllegalStateException("failed to record ambiguity", e);
        }
    }

    public AmbiguityRecord ambiguityById(UUID ambiguityId) {
        Objects.requireNonNull(ambiguityId, "ambiguityId");
        String sql = "SELECT ambiguity_class, affected_path, resolution_state, "
                + "quality_checks_performed, no_clarification_reason FROM ambiguity_record "
                + "WHERE ambiguity_record_id = ?";
        try (Connection c = connections.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, ambiguityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("no such ambiguity: " + ambiguityId);
                }
                return new AmbiguityRecord(ambiguityId,
                        AmbiguityClass.valueOf(rs.getString("ambiguity_class")),
                        rs.getString("affected_path"),
                        ResolutionState.valueOf(rs.getString("resolution_state")),
                        rs.getString("quality_checks_performed"),
                        rs.getString("no_clarification_reason"));
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to read ambiguity " + ambiguityId, e);
        }
    }

    /**
     * Records a human's answer and moves the ambiguity to {@link ResolutionState#RESOLVED}.
     *
     * <p>Both writes in one transaction: a {@code ClarificationDecision} with no resulting state change
     * would leave the ambiguity looking still-pending after it was, in fact, answered.
     */
    public void resolveWithClarification(UUID ambiguityId, ClarificationDecision decision) {
        Objects.requireNonNull(ambiguityId, "ambiguityId");
        Objects.requireNonNull(decision, "decision");

        try (Connection c = connections.get()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO clarification_decision (clarification_decision_id, "
                                + "ambiguity_record_id, actor, question, answer, decided_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, decision.id());
                    ps.setObject(2, ambiguityId);
                    ps.setString(3, decision.actor());
                    ps.setString(4, decision.question());
                    ps.setString(5, decision.answer());
                    ps.setTimestamp(6, Timestamp.from(decision.decidedAt()));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE ambiguity_record SET resolution_state = 'RESOLVED' "
                                + "WHERE ambiguity_record_id = ? AND resolution_state = 'MATERIAL_PENDING'")) {
                    ps.setObject(1, ambiguityId);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "ambiguity " + ambiguityId + " is not MATERIAL_PENDING, so it cannot be "
                                        + "resolved by a clarification");
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("failed to resolve ambiguity " + ambiguityId, e);
        }
    }
}
