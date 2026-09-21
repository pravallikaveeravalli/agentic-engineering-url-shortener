package agentic.shortener.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three secure defaults are what {@code application.yml} actually says. Task T018.
 *
 * <p>plan §8 names three defaults; this asserts each one. A default written in a comment and not
 * asserted is a claim, and the point of T018 is that the shipped configuration is safe rather than
 * documented as safe.
 *
 * <p><strong>Three, not four.</strong> The former {@code ai: off} default is gone with the flag
 * itself (ADR-004 Amendment 02 / CR-028), so this test also asserts its ABSENCE — a stray mode flag
 * reappearing in configuration would contradict Decision J silently.
 *
 * <p>Fast tier: reads the file, starts no Spring context. Booting the framework to read its own
 * configuration would move this out of the container-free tier for no additional assurance.
 */
@DisplayName("T018 secure configuration defaults")
class SecureDefaultsTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static JsonNode config() throws Exception {
        Path file = Path.of("src/main/resources/application.yml");
        assertTrue(Files.exists(file), "missing " + file.toAbsolutePath());
        return YAML.readTree(Files.readString(file));
    }

    @Test
    @DisplayName("default 1 of 3: throttling is on")
    void throttlingIsOnByDefault() throws Exception {
        JsonNode rateLimit = config().path("shortener").path("ratelimit");
        assertTrue(rateLimit.path("enabled").asBoolean(false),
                "FR-URL-016: throttling must be ON by default, not opt-in");
        assertTrue(rateLimit.path("creation-per-creator-per-minute").isInt(),
                "the per-creator creation tier (PVT-012) must have a configured bound");
        assertTrue(rateLimit.path("redirect-per-code-per-minute").isInt(),
                "the per-code redirect tier (PVT-013) must have a configured bound");
    }

    @Test
    @DisplayName("default 2 of 3: verbose error detail is off on every channel")
    void verboseErrorDetailIsOff() throws Exception {
        JsonNode error = config().path("server").path("error");
        assertEquals("never", error.path("include-stacktrace").asText(),
                "a stack trace on an error response leaks internals to an unauthenticated caller");
        assertEquals("never", error.path("include-message").asText());
        assertEquals("never", error.path("include-binding-errors").asText());
        assertFalse(error.path("include-exception").asBoolean(true));

        // Actuator is the other leak path: an open env or configprops endpoint exposes the
        // datasource credential, so exposure is narrowed to health alone.
        assertEquals("health",
                config().path("management").path("endpoints").path("web")
                        .path("exposure").path("include").asText(),
                "only health may be exposed; env and configprops would leak the datasource password");
        assertEquals("never",
                config().path("management").path("endpoint").path("health").path("show-details").asText());
    }

    @Test
    @DisplayName("default 3 of 3: readiness fails closed")
    void readinessFailsClosed() throws Exception {
        assertTrue(config().path("shortener").path("readiness").path("fail-closed").asBoolean(false),
                "failing open would make a broken instance look healthy, which is the failure mode "
                        + "this default exists to prevent");
        assertTrue(config().path("management").path("endpoint").path("health").path("probes")
                        .path("enabled").asBoolean(false),
                "liveness and readiness probes must be distinguishable for the readiness rule to mean "
                        + "anything");
    }

    @Test
    @DisplayName("no mode flag exists in configuration (ADR-004-A2)")
    void noModeFlagExists() throws Exception {
        // Comment lines are stripped before this check, because application.yml's own header
        // explains that the former `ai: off` default is gone — and that explanation is legitimate
        // provenance, exactly as retired requirements keep their text elsewhere in this project.
        // The right assertion is "no ACTIVE key sets a mode", not "the string appears nowhere".
        // (This test caught my own comment on its first run, which is the fourth time in this
        // project that explanatory prose has tripped an absence assertion. Scoping the assertion to
        // active configuration is the fix; weakening it would not be.)
        String active = Files.readString(Path.of("src/main/resources/application.yml")).lines()
                .filter(line -> !line.stripLeading().startsWith("#"))
                .reduce("", (a, b) -> a + "\n" + b);

        assertFalse(active.contains("ai: on"), "no ACTIVE run-level ai key may exist (CR-028)");
        assertFalse(active.contains("ai: off"), "no ACTIVE run-level ai key may exist (CR-028)");
        assertTrue(config().path("shortener").path("ai").isMissingNode(),
                "orchestration always uses AI; a configuration key would reintroduce the mode the "
                        + "owner removed");

        // Schema ownership: Flyway owns it, Hibernate may only validate. `ddl-auto: update` would
        // let the ORM silently diverge from the migration chain.
        assertEquals("validate",
                config().path("spring").path("jpa").path("hibernate").path("ddl-auto").asText(),
                "Flyway owns the schema (ADR-002); Hibernate must never create or alter it");
    }
}
