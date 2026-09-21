package agentic.shortener.provisioning;

import agentic.shortener.domain.creator.CreatorCredential;
import agentic.shortener.persistence.JdbcCreatorRepository;
import agentic.shortener.persistence.MigrationSupport;
import agentic.shortener.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T053 — the provisioning script against a real store. FR-URL-019.
 *
 * <p>The script is <em>run</em>, not read. T053's Validate asks for four things and two of them are
 * about what lands in the database: {@code never} yields a null expiry and nothing else does, and only
 * the hash is stored. Neither is checkable by reading the script.
 *
 * <p><strong>It needs store connectivity but not a running application</strong>, which is exactly how it
 * is exercised here: no Spring context, just the container and the script.
 *
 * <p><strong>psql is not installed on every machine</strong>, including this one. The script therefore
 * honours a {@code PSQL} override, and this test points it at a wrapper that runs psql <em>inside</em>
 * the container — where it certainly exists. The script itself is unchanged, and the path under test is
 * the real one: generate, hash, execute, store.
 */
@DisplayName("T053 provisioning against a real store")
class ProvisionCreatorIT extends PostgresIntegrationTest {

    private static final Path SCRIPT = Path.of("scripts/provision-creator.sh");

    private Path psqlWrapper;

    @BeforeEach
    void migrateAndWriteWrapper() throws Exception {
        MigrationSupport.migrate(POSTGRES, "public");

        // A psql that runs in the container. `docker exec -i` feeds the script's SQL to a client that
        // is guaranteed to be present, which keeps the test honest on a host without one.
        psqlWrapper = Path.of("target/psql-in-container.sh");
        Files.createDirectories(psqlWrapper.getParent());
        Files.writeString(psqlWrapper, "#!/usr/bin/env bash\nexec docker exec -i "
                + POSTGRES.getContainerId()
                + " psql -U shortener -d shortener \"$@\"\n");
        psqlWrapper.toFile().setExecutable(true);
    }

    /** Runs the script and returns exit code plus combined output. */
    private Result run(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(SCRIPT.toAbsolutePath().toString()));
        command.addAll(List.of(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.environment().put("PSQL", psqlWrapper.toAbsolutePath().toString());
        // Pointless for the wrapper, which connects inside the container, but set so the script's own
        // defaults are never silently relied on.
        builder.environment().put("PGHOST", POSTGRES.getHost());
        builder.environment().put("PGPORT",
                String.valueOf(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)));

        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "the script did not finish");
        return new Result(process.exitValue(), output);
    }

    private record Result(int exitCode, String output) {
    }

    @Test
    @DisplayName("the script EXITS NON-ZERO without --expires, and stores nothing")
    void refusesWithoutExpires() throws Exception {
        long before = countCredentials();

        Result result = run("--name", "no-expiry-given");

        assertNotEquals(0, result.exitCode(),
                "FR-URL-019: a credential MUST NOT be provisioned without an explicit expiry decision");
        assertTrue(result.output().contains("NO DEFAULT"),
                "and the refusal should say why: " + result.output());
        assertEquals(before, countCredentials(), "nothing may have been stored");
        assertFalse(result.output().contains("crk_"),
                "and no key may have been generated or shown: " + result.output());
    }

    @Test
    @DisplayName("`never` yields a NULL expires_at")
    void neverYieldsNull() throws Exception {
        Result result = run("--name", "never-expires-creator", "--expires", "never");
        assertEquals(0, result.exitCode(), result.output());

        Map<String, Object> row = latestCredential();
        assertNull(row.get("expires_at"),
                "a far-future sentinel would make 'never' indistinguishable from 'expires in 2999', "
                        + "which is the distinction CR-002 keeps (" + row + ")");
    }

    @Test
    @DisplayName("and NOTHING ELSE yields NULL — a duration is stored as a real instant")
    void nothingElseYieldsNull() throws Exception {
        Result result = run("--name", "ninety-day-creator", "--expires", "90d");
        assertEquals(0, result.exitCode(), result.output());

        Map<String, Object> row = latestCredential();
        assertNotEquals(null, row.get("expires_at"), "a duration must produce a real expiry");

        Instant expiry = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        Instant created = ((java.sql.Timestamp) row.get("created_at")).toInstant();
        long days = java.time.Duration.between(created, expiry).toDays();
        assertEquals(90, days, "90d must mean ninety days, not an approximation");
    }

    @Test
    @DisplayName("ONLY THE HASH is stored — the key appears nowhere in the database")
    void onlyTheHashIsStored() throws Exception {
        Result result = run("--name", "hash-only-creator", "--expires", "24h");
        assertEquals(0, result.exitCode(), result.output());

        String key = extractKey(result.output());
        assertTrue(key.startsWith("crk_"), "the script must show the key once: " + result.output());
        assertTrue(key.length() >= 47, "crk_ plus at least 43 base64url characters: " + key.length());

        // The stored digest must be the hash of the FULL presented string, which is what the domain
        // computes. If the script hashed the suffix alone, this would not match and no key would ever
        // authenticate — a defect that a read of the script cannot rule out.
        String expected = CreatorCredential.hashOf(key);
        assertEquals(expected, String.valueOf(latestCredential().get("key_hash")).trim(),
                "the stored hash must equal CreatorCredential.hashOf(presented key)");

        // And the key itself must be nowhere. Every text column of both tables is searched.
        assertEquals(List.of(), occurrencesOf(key),
                "the key was found in the database; only its hash may be stored (FR-URL-019)");

        // The provisioned credential actually authenticates, which is the end-to-end property all of
        // the above exists to support.
        Optional<CreatorCredential> loaded =
                new JdbcCreatorRepository(() -> connection()).findByKeyHash(expected);
        assertTrue(loaded.isPresent(), "the credential must be findable by the hash the domain computes");
        assertTrue(loaded.get().matches(key), "and must verify the key the operator was shown");
    }

    @Test
    @DisplayName("a malformed duration is refused rather than guessed at")
    void malformedDurationRefused() throws Exception {
        for (String bad : List.of("90", "ninety-days", "0d", "-5d", "9x9d", "forever")) {
            long before = countCredentials();
            Result result = run("--name", "bad-duration", "--expires", bad);
            assertNotEquals(0, result.exitCode(), "'" + bad + "' must be refused: " + result.output());
            assertEquals(before, countCredentials(), "'" + bad + "' must store nothing");
        }
    }

    @Test
    @DisplayName("two runs produce two different keys")
    void keysAreNotReused() throws Exception {
        String first = extractKey(run("--name", "creator-a", "--expires", "1d").output());
        String second = extractKey(run("--name", "creator-b", "--expires", "1d").output());
        assertNotEquals(first, second, "a CSPRNG that repeated itself would be the whole story");
    }

    private static String extractKey(String output) {
        return output.lines()
                .map(String::strip)
                .filter(line -> line.startsWith("crk_"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no key in the output: " + output));
    }

    private long countCredentials() throws Exception {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM creator_credential");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Map<String, Object> latestCredential() throws Exception {
        String sql = "SELECT credential_id, creator_id, key_hash, created_at, expires_at, revoked_at "
                + "FROM creator_credential ORDER BY created_at DESC, credential_id DESC LIMIT 1";
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "no credential was stored");
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
            }
            return row;
        }
    }

    /** Every place the key appears in either table's text columns. Should be nowhere. */
    private List<String> occurrencesOf(String key) throws Exception {
        List<String> found = new ArrayList<>();
        String sql = "SELECT 'creator.name' AS where_, creator_id::text AS id FROM creator "
                + "WHERE name LIKE ? "
                + "UNION ALL SELECT 'creator.api_key_hash', creator_id::text FROM creator "
                + "WHERE api_key_hash LIKE ? "
                + "UNION ALL SELECT 'credential.key_hash', credential_id::text FROM creator_credential "
                + "WHERE key_hash LIKE ?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            String like = "%" + key + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.add(rs.getString("where_") + " " + rs.getString("id"));
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("the script never writes the key to a file")
    void keyIsNotWrittenToDisk() throws IOException {
        // Operator terminal output is not application logging (FR-URL-019's Gate 2 rationale), but a
        // file would be neither — it would be key material at rest that nobody agreed to.
        String script = Files.readString(SCRIPT);
        for (String forbidden : List.of("> /tmp", ">> ", "tee ", "logger ")) {
            assertFalse(script.contains(forbidden),
                    "the key must reach the terminal and nothing else; found '" + forbidden + "'");
        }
    }
}
