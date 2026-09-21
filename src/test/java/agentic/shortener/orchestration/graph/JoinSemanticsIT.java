package agentic.shortener.orchestration.graph;

import agentic.shortener.orchestration.store.JdbcRunStore;
import agentic.shortener.persistence.ConnectionSource;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-039 — {@code ALL} is the only join semantic, at every layer.
 *
 * <p>Three layers, asserted separately rather than inferred from one another. Narrowing the Java enum is
 * checked by the compiler, so a test of it alone would prove only that the code compiles; the contract's enum
 * and the store's CHECK are independent statements and a change to one does not move the others.
 *
 * <p>An integration test because the third assertion needs a real store: the only way to know a CHECK
 * constraint refuses {@code 'ANY'} is to ask it to accept one.
 */
@DisplayName("CR-039 — ANY is not a join semantic, in the type, the contract or the store")
class JoinSemanticsIT extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    private ConnectionSource connections;
    private UUID runId;

    @BeforeEach
    void setUp() {
        MigrationSupport.migrate(POSTGRES, "public");
        connections = () -> connection();
        runId = new JdbcRunStore(connections, Clock.fixed(NOW, ZoneOffset.UTC))
                .createRun(StageTemplate.standard(), "policy-set-1.1.0", UUID.randomUUID());
    }

    @Test
    @DisplayName("the type holds exactly one constant")
    void exactlyOneConstant() {
        // The answer to the single-value-enum objection. A field with one legal value invites a second one
        // without anybody revisiting the design, so restoring ANY has to fail a test rather than pass review.
        // Same device as the six failure categories and the three-member terminal run-state set.
        assertEquals(List.of("ALL"), EnumSet.allOf(JoinSemantics.class).stream().map(Enum::name).toList(),
                "CR-039, owner ruling 2026-09-21: ANY is not supported. It was storable and never honoured — "
                        + "StageCriteria.mayEnter requires every dependency to satisfy the join and has no "
                        + "per-edge behaviour — so a caller could set a value nothing acted on");
    }

    @Test
    @DisplayName("every edge the standard template emits is ALL")
    void templateEmitsOnlyAll() {
        assertEquals(List.of(), StageTemplate.standard().edges().stream()
                        .filter(e -> e.joinSemantics() != JoinSemantics.ALL).toList(),
                "the fourteen edges were already all ALL, which is what made this ruling safe to apply "
                        + "without a data migration");
    }

    @Test
    @DisplayName("the STORE refuses 'ANY', which only trying it can prove")
    void storeRefusesAny() throws Exception {
        // V6 narrowed dependency_edge_semantics_known to `= 'ALL'`. Inserted directly rather than through
        // JdbcRunStore, because the store now cannot express ANY either — going through it would prove the
        // enum, which the compiler already did, and say nothing about the constraint.
        SQLException refused = assertThrows(SQLException.class, () -> {
            try (Connection c = connections.get();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO dependency_edge (run_id, from_node_key, to_node_key, join_semantics) "
                                 + "VALUES (?, 'S1', 'S3', 'ANY')")) {
                ps.setObject(1, runId);
                ps.executeUpdate();
            }
        });

        assertEquals("23514", refused.getSQLState(),
                "a CHECK violation, not a type error: " + refused.getMessage());
        assertTrue(refused.getMessage().contains("dependency_edge_semantics_known"), refused.getMessage());
    }

    @Test
    @DisplayName("and it still accepts 'ALL' — the narrowing is not a blanket refusal")
    void storeStillAcceptsAll() throws Exception {
        // Without this, storeRefusesAny would pass against a constraint that refused everything, and the
        // fourteen real edges would have been broken by the same change that looked like it worked.
        try (Connection c = connections.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO dependency_edge (run_id, from_node_key, to_node_key, join_semantics) "
                             + "VALUES (?, 'S1', 'S3', 'ALL')")) {
            ps.setObject(1, runId);
            assertEquals(1, ps.executeUpdate());
        }
    }
}
