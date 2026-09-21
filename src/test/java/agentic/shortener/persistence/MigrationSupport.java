package agentic.shortener.persistence;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Applies the migration chain to a container schema. Test support for T026 onward.
 *
 * <p>Idempotent by construction: Flyway skips versions already applied, so a test calling this in
 * {@code @BeforeEach} pays the cost once per container rather than once per test.
 *
 * <p>Does not call {@code clean()}. Flyway 10 disables it by default, and depending on it would mean
 * these tests could only run against a database they are permitted to destroy \u2014 a property worth not
 * depending on.
 */
final class MigrationSupport {

    private MigrationSupport() {
    }

    static void migrate(PostgreSQLContainer<?> container, String schema) {
        Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .schemas(schema)
                .locations("classpath:db/migration")
                .createSchemas(true)
                .load()
                .migrate();
    }
}
