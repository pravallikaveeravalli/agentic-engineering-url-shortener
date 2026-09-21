package agentic.shortener.audit;

import agentic.shortener.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * T028 — an UPDATE against an audit table is refused by the store.
 *
 * <p>T028's guard is the sharpest in Phase 2: <strong>immutability must be a control, not a claim. If
 * this test passes because the UPDATE succeeded, the grant is missing.</strong> So the tests here
 * assert that the statement <em>throws</em>, and they assert on the <em>reason</em> — an insufficient-
 * privilege SQLSTATE — because an UPDATE that failed for some unrelated reason would be a false pass.
 *
 * <p>The connection matters. Testcontainers' default user owns the schema and is effectively
 * superuser, so a privilege test run as that user would pass trivially and prove nothing. These tests
 * connect as {@code shortener_app}, the role V3 grants INSERT and SELECT to and never UPDATE or
 * DELETE. That is the role the application will run as, so it is the one whose privileges are worth
 * testing.
 */
@DisplayName("T028 audit immutability is enforced by privilege, not convention")
class AuditImmutabilityIT extends PostgresIntegrationTest {

    /** PostgreSQL SQLSTATE 42501: insufficient_privilege. The specific reason the UPDATE must fail. */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private static final String APP_ROLE = "shortener_app";
    private static final String APP_PASSWORD = "shortener_app_it";

    /** The six tables V3 grants INSERT and SELECT only. */
    private static final List<String> GOVERNANCE_TABLES = List.of(
            "audit_record", "state_transition", "gate_decision",
            "failure_event", "policy_check_result", "compensation_record");

    @BeforeEach
    void migrateAndPrepareRole() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("public")
                .locations("classpath:db/migration")
                .createSchemas(true)
                .load()
                .migrate();

        // V3 creates the role NOLOGIN, because production wires it without a password. For this test
        // it needs to connect, so it is given LOGIN and a password here rather than in the migration —
        // a migration that set a password would put a credential in the repository.
        try (Connection owner = connection(); Statement s = owner.createStatement()) {
            s.execute("ALTER ROLE " + APP_ROLE + " WITH LOGIN PASSWORD '" + APP_PASSWORD + "'");
            s.execute("GRANT CONNECT ON DATABASE " + POSTGRES.getDatabaseName() + " TO " + APP_ROLE);
            s.execute("GRANT USAGE ON SCHEMA public TO " + APP_ROLE);
            // Seed one row per governance table as the OWNER, so the UPDATE tests have a target. If
            // they had nothing to update, an UPDATE affecting zero rows would succeed and the test
            // would pass for the wrong reason.
            s.execute("INSERT INTO audit_record (actor_type, action, occurred_at, "
                    + "affected_artifact, result, reason, correlation_id) VALUES "
                    + "('human', 'seed', now(), 'audit_record', 'OK', 'T028 seed row', '"
                    + UUID.randomUUID() + "')");
        }
    }

    private Connection asApplicationRole() throws SQLException {
        String url = POSTGRES.getJdbcUrl();
        return DriverManager.getConnection(url, APP_ROLE, APP_PASSWORD);
    }

    @Test
    @DisplayName("the application role CAN insert and select — the grant is not simply absent")
    void insertAndSelectAreGranted() throws Exception {
        // Checked first, deliberately. If the role could do nothing at all, the UPDATE tests below
        // would pass for the wrong reason and the suite would look green while proving nothing.
        try (Connection app = asApplicationRole(); Statement s = app.createStatement()) {
            s.execute("INSERT INTO audit_record (actor_type, action, occurred_at, "
                    + "affected_artifact, result, reason, correlation_id) VALUES "
                    + "('system', 'append', now(), 'audit_record', 'OK', 'granted insert', '"
                    + UUID.randomUUID() + "')");

            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM audit_record")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 2, "the role must be able to read what it wrote");
            }
        }
    }

    @Test
    @DisplayName("an UPDATE against audit_record is REFUSED for insufficient privilege")
    void updateIsRefused() throws Exception {
        try (Connection app = asApplicationRole(); Statement s = app.createStatement()) {
            SQLException thrown = assertThrows(SQLException.class,
                    () -> s.execute("UPDATE audit_record SET reason = 'tampered'"),
                    "if this does not throw, the grant is missing and the audit log is mutable");

            assertEquals(INSUFFICIENT_PRIVILEGE, thrown.getSQLState(),
                    "the refusal must be a PRIVILEGE refusal (42501). Any other SQLSTATE means the "
                            + "statement failed for an unrelated reason and this test would be a "
                            + "false pass. Got: " + thrown.getSQLState() + " / " + thrown.getMessage());
        }
    }

    @Test
    @DisplayName("a DELETE against audit_record is REFUSED for insufficient privilege")
    void deleteIsRefused() throws Exception {
        try (Connection app = asApplicationRole(); Statement s = app.createStatement()) {
            SQLException thrown = assertThrows(SQLException.class,
                    () -> s.execute("DELETE FROM audit_record"),
                    "an append-only log that can be emptied is not append-only");
            assertEquals(INSUFFICIENT_PRIVILEGE, thrown.getSQLState(),
                    "expected 42501, got " + thrown.getSQLState());
        }
    }

    @Test
    @DisplayName("every one of the six governance tables refuses UPDATE and DELETE")
    void allSixGovernanceTablesAreAppendOnly() throws Exception {
        List<String> mutable = new ArrayList<>();
        try (Connection app = asApplicationRole()) {
            for (String table : GOVERNANCE_TABLES) {
                for (String statement : List.of(
                        "UPDATE " + table + " SET run_id = run_id",
                        "DELETE FROM " + table)) {
                    try (Statement s = app.createStatement()) {
                        s.execute(statement);
                        // Reached only if the store ACCEPTED it, which is the failure.
                        mutable.add(table + " accepted: " + statement);
                    } catch (SQLException expected) {
                        if (!INSUFFICIENT_PRIVILEGE.equals(expected.getSQLState())) {
                            mutable.add(table + " failed with " + expected.getSQLState()
                                    + " rather than a privilege refusal: " + statement);
                        }
                    }
                }
            }
        }
        assertTrue(mutable.isEmpty(),
                "every governance table must refuse UPDATE and DELETE for insufficient privilege. "
                        + "Problems: " + mutable);
    }

    @Test
    @DisplayName("redirect_event is append-only too, though it is analytics rather than audit")
    void redirectEventIsAppendOnly() throws Exception {
        // ADR-010 scopes redirect_event OUT of "audit" — its failure semantics come from ADR-014. But
        // CR-017 retains it indefinitely, so it carries the same privilege shape, and that is worth a
        // test because the reasoning differs from the six above.
        try (Connection app = asApplicationRole(); Statement s = app.createStatement()) {
            SQLException thrown = assertThrows(SQLException.class,
                    () -> s.execute("DELETE FROM redirect_event"),
                    "CR-017 retains click records indefinitely; a DELETE grant would be the first "
                            + "step of a retention job this demonstration does not have");
            assertEquals(INSUFFICIENT_PRIVILEGE, thrown.getSQLState());
        }
    }

    @Test
    @DisplayName("the application-plane tables DO permit UPDATE — the grants are not blanket-deny")
    void applicationPlaneTablesRemainMutable() throws Exception {
        // Links expire and creators deactivate, so a blanket REVOKE would break the domain. Checking
        // this stops the privilege model from being "deny everything", which would pass every test
        // above while making the application non-functional.
        try (Connection app = asApplicationRole(); Statement s = app.createStatement()) {
            s.execute("UPDATE short_link SET state = state WHERE false");
            s.execute("UPDATE creator SET active = active WHERE false");
        } catch (SQLException e) {
            fail("the application plane must remain mutable: links expire (ACTIVE -> EXPIRED) and "
                    + "creators deactivate. Got " + e.getSQLState() + " / " + e.getMessage());
        }
    }
}
