package agentic.shortener.persistence;

import agentic.shortener.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T010: the Flyway chain applies from empty.
 *
 * <p>Named {@code *IntegrationTest} so it runs in the Failsafe tier (T017) against a real
 * PostgreSQL 16 — a migration that "should" apply is not evidence; one that has applied to an empty
 * database is.
 *
 * <p>T010's Validate command is {@code ./mvnw -q -Dtest=MigrationFromEmptyTest test}; the class is
 * named {@code MigrationFromEmptyIntegrationTest} so the two-tier split can claim it. The naming
 * divergence is recorded in the Slice 1 report rather than silently reconciled.
 */
class MigrationFromEmptyIntegrationTest extends PostgresIntegrationTest {

    @Test
    void migrationChainAppliesToACompletelyEmptyDatabase() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                // A distinct schema keeps this test independent of the harness round-trip table.
                .schemas("migration_from_empty")
                .locations("classpath:db/migration")
                .load();

        // Empty means empty: nothing pre-created, no baseline-on-migrate shortcut.
        flyway.clean();
        var result = flyway.migrate();

        assertTrue(result.migrationsExecuted >= 1,
                "at least V1 must have been applied; executed=" + result.migrationsExecuted);

        List<String> applied = new ArrayList<>();
        for (MigrationInfo info : flyway.info().applied()) {
            applied.add(info.getVersion() + " " + info.getDescription());
        }
        assertTrue(applied.stream().anyMatch(v -> v.startsWith("1 ")),
                "V1 baseline must be among the applied migrations; applied=" + applied);
    }

    @Test
    void baselineCreatesTheThreeApplicationPlaneTablesAndTheirGuards() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("migration_shape")
                .locations("classpath:db/migration")
                .load();
        flyway.clean();
        flyway.migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("SET search_path TO migration_shape");

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = s.executeQuery(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = 'migration_shape' AND table_type = 'BASE TABLE' "
                            + "ORDER BY table_name")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            assertTrue(tables.contains("creator"), "creator table missing; got " + tables);
            assertTrue(tables.contains("short_link"), "short_link table missing; got " + tables);
            assertTrue(tables.contains("redirect_event"), "redirect_event table missing; got " + tables);

            // FR-URL-010 and CL-002: timestamp-only click records. The ABSENCE of any
            // follower-identifying column is the control, so it is asserted rather than assumed.
            List<String> eventColumns = new ArrayList<>();
            try (ResultSet rs = s.executeQuery(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'migration_shape' AND table_name = 'redirect_event'")) {
                while (rs.next()) {
                    eventColumns.add(rs.getString(1));
                }
            }
            assertEquals(3, eventColumns.size(),
                    "redirect_event must hold exactly id, short_code, occurred_at; got " + eventColumns);
            for (String forbidden : List.of("ip", "ip_address", "user_agent", "referrer", "referer",
                    "session_id", "follower_id")) {
                assertTrue(eventColumns.stream().noneMatch(col -> col.contains(forbidden)),
                        "redirect_event must hold no follower-identifying column (CL-002); found "
                                + forbidden + " in " + eventColumns);
            }
        }
    }
}
